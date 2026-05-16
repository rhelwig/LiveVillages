package com.ronhelwig.livevillages.sim;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.ronhelwig.livevillages.LiveVillages;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

public final class SettlementForesterWork {
	private static final double FORESTRY_WORK_REACH_DISTANCE_SQUARED = 9.0D;
	private static final double FORESTRY_WALK_SPEED = 0.75D;
	private static final int FORESTRY_EXTRA_RADIUS_BLOCKS = 64;
	private static final int FORESTRY_ITEM_SCAN_RADIUS_BLOCKS = 24;
	private static final int TREE_SCAN_STEP_BLOCKS = 2;
	private static final int PLANT_SCAN_STEP_BLOCKS = 4;
	private static final int TREE_SURFACE_SEARCH_DEPTH_BLOCKS = 14;
	private static final int TREE_PRESERVE_RADIUS_BLOCKS = 14;
	private static final int SAPLING_STRUCTURE_CLEARANCE_BLOCKS = 3;
	private static final int SAPLING_TO_SAPLING_CLEARANCE_BLOCKS = 3;
	private static final int SAPLING_TO_TREE_CLEARANCE_BLOCKS = 4;
	private static final int MAX_LOGS_PER_TREE = 48;
	private static final long FORESTRY_TASK_CACHE_TICKS = 200L;
	private static final long FORESTRY_DECIDE_INTERVAL_TICKS = 320L;
	private static final int BLOCK_UPDATE_FLAGS = 3;
	private static final Map<String, TimedTask> ACTIVE_TASKS = new HashMap<>();
	private static final Map<String, CachedForestryTask> FORESTRY_TASK_CACHE = new HashMap<>();

	private SettlementForesterWork() {
	}

	public static boolean maintainLoadedForestry(ServerLevel level, SettlementState settlement, Map<String, Integer> stock) {
		long methodStart = System.nanoTime();
		
		int workRadius = workRadius(settlement);
		boolean worldChanged = false;
		long tick = level.getServer().getTickCount();

		List<Villager> foresters = SettlementVillagers.nearbyForesters(level, settlement, workRadius);
		
		long entityScanTime = System.nanoTime() - methodStart;
		if (entityScanTime > 2_000_000) { // >2ms
			LiveVillages.LOGGER.warn("ForesterWork: entity scan took {} ms for {} foresters", Math.round(entityScanTime / 1_000_000.0D), foresters.size());
		}

		for (Villager forester : foresters) {
			if (!SettlementVillagerWorkSchedule.shouldStartNewWork(level, forester, "forestry", FORESTRY_DECIDE_INTERVAL_TICKS)) {
				if (SettlementVillagerWorkSchedule.isTakingBreak(level, forester)) {
					forester.getNavigation().stop();
				}

				continue;
			}

			Optional<ForestryTask> task = chooseForestryTask(level, settlement, stock, forester, tick);

			if (task.isEmpty()) {
				ACTIVE_TASKS.remove(forester.getUUID().toString());
				continue;
			}

			ForestryTask forestryTask = task.get();
			ACTIVE_TASKS.put(forester.getUUID().toString(), new TimedTask(forestryTask.taskKey(), tick));
			showForesterTool(forester, forestryTask.action());
			steerForesterTowardTask(forester, forestryTask.standPos());

			if (!isWithinWorkReach(forester, forestryTask.workPos())) {
				continue;
			}

			if (performForestryTask(level, settlement, stock, forester, forestryTask)) {
				forester.swing(InteractionHand.MAIN_HAND);
				worldChanged = true;
			}
		}

		long totalTime = System.nanoTime() - methodStart;
		if (totalTime > 10_000_000) { // >10ms
			LiveVillages.LOGGER.warn("ForesterWork: total work took {} ms for settlement {}", Math.round(totalTime / 1_000_000.0D), settlement.id());
		}

		return worldChanged;
	}

	public static Optional<String> loadedForestryTaskKey(ServerLevel level, Villager villager) {
		TimedTask task = ACTIVE_TASKS.get(villager.getUUID().toString());

		if (task == null || level.getServer().getTickCount() - task.tick() > 40L) {
			return Optional.empty();
		}

		return Optional.of(task.taskKey());
	}

	private static Optional<ForestryTask> chooseForestryTask(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Villager forester,
		long tick
	) {
		String cacheKey = forestryTaskCacheKey(settlement, forester);
		CachedForestryTask cachedTask = FORESTRY_TASK_CACHE.get(cacheKey);

		if (cachedTask != null && tick - cachedTask.tick() <= FORESTRY_TASK_CACHE_TICKS) {
			if (cachedTask.task().isEmpty()) {
				return Optional.empty();
			}

			if (isCachedForestryTaskStillUseful(level, stock, cachedTask.task().get())) {
				return cachedTask.task();
			}
		}

		Optional<ForestryTask> task = nearestCollectableItemTask(level, settlement, forester)
			.or(() -> nearestPlantTask(level, settlement, stock, forester, tick))
			.or(() -> nearestTreeTask(level, settlement, stock, forester, tick));
		FORESTRY_TASK_CACHE.put(cacheKey, new CachedForestryTask(task, tick));
		return task;
	}

	private static String forestryTaskCacheKey(SettlementState settlement, Villager forester) {
		return settlement.dimension().identifier() + "|" + settlement.id() + "|" + forester.getUUID();
	}

	private static boolean isCachedForestryTaskStillUseful(ServerLevel level, Map<String, Integer> stock, ForestryTask task) {
		return switch (task.action()) {
			case COLLECT_DROP -> level.getEntity(task.entityId()) instanceof ItemEntity itemEntity && !itemEntity.isRemoved();
			case PLANT_SEEDLING -> {
				BlockState seedlingState = seedlingBlockState(task.seedlingGoods());
				yield seedlingState != null && stock.getOrDefault(task.seedlingGoods(), 0) > seedlingReserve(task.seedlingGoods()) && canPlantSeedling(level, task.workPos(), seedlingState);
			}
			case CHOP_TREE -> level.hasChunkAt(task.workPos()) && isLog(level.getBlockState(task.workPos())) && stock.getOrDefault(task.seedlingGoods(), 0) >= (task.seedlingGoods().equals("dark_oak_sapling") ? 4 : 1);
		};
	}

	private static Optional<ForestryTask> nearestCollectableItemTask(ServerLevel level, SettlementState settlement, Villager forester) {
		AABB bounds = new AABB(forester.blockPosition()).inflate(FORESTRY_ITEM_SCAN_RADIUS_BLOCKS);
		ForestryTask bestTask = null;
		double bestDistanceSquared = Double.POSITIVE_INFINITY;

		for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, bounds, entity -> !entity.isRemoved() && isForestryDrop(entity))) {
			BlockPos itemPos = itemEntity.blockPosition();

			if (!isWithinForestryRange(settlement, itemPos)) {
				continue;
			}

			Optional<BlockPos> standPos = standableDropPickupPos(level, itemPos);

			if (standPos.isEmpty()) {
				continue;
			}

			double distanceSquared = standPos.get().distSqr(forester.blockPosition());

			if (bestTask == null || distanceSquared < bestDistanceSquared) {
				bestTask = new ForestryTask(
					ForestryAction.COLLECT_DROP,
					itemPos,
					standPos.get(),
					"",
					itemEntity.getId(),
					"collecting_forest_drops"
				);
				bestDistanceSquared = distanceSquared;
			}
		}

		return Optional.ofNullable(bestTask);
	}

	private static Optional<ForestryTask> nearestPlantTask(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Villager forester,
		long tick
	) {
		String seedlingGoods = seedlingToPlant(stock);

		if (seedlingGoods == null) {
			return Optional.empty();
		}

		BlockState seedlingState = seedlingBlockState(seedlingGoods);

		if (seedlingState == null) {
			return Optional.empty();
		}

		BlockPos origin = foresterSearchOrigin(level, settlement, forester);
		BlockPos bestPlantPos = null;
		BlockPos bestStandPos = null;
		double bestDistanceSquared = Double.POSITIVE_INFINITY;
		int radius = Math.min(112, workRadius(settlement));
		int offset = Math.floorMod((int) (tick / 20L), PLANT_SCAN_STEP_BLOCKS);
		List<PlantingStructureBounds> protectedStructureBounds = plantingStructureBounds(level, settlement);

		for (int x = origin.getX() - radius + offset; x <= origin.getX() + radius; x += PLANT_SCAN_STEP_BLOCKS) {
			for (int z = origin.getZ() - radius + offset; z <= origin.getZ() + radius; z += PLANT_SCAN_STEP_BLOCKS) {
				Optional<BlockPos> plantPos = surfacePlantPos(level, x, z, seedlingState);

				if (plantPos.isEmpty() || !isPreferredPlantingArea(settlement, plantPos.get())) {
					continue;
				}

				if (isTooCloseToStructure(plantPos.get(), protectedStructureBounds)
					|| hasNearbySapling(level, plantPos.get(), SAPLING_TO_SAPLING_CLEARANCE_BLOCKS)
					|| hasNearbyTreeBase(level, plantPos.get(), SAPLING_TO_TREE_CLEARANCE_BLOCKS)) {
					continue;
				}

				if (seedlingGoods.equals("dark_oak_sapling") && !canPlantDarkOakCluster(level, plantPos.get())) {
					continue;
				}

				if (nearbyTreeBaseCount(level, plantPos.get(), TREE_PRESERVE_RADIUS_BLOCKS) >= preferredTreeDensity(settlement, plantPos.get())) {
					continue;
				}

				Optional<BlockPos> standPos = standableAround(level, plantPos.get());

				if (standPos.isEmpty()) {
					continue;
				}

				double distanceSquared = standPos.get().distSqr(forester.blockPosition());

				if (bestPlantPos == null || distanceSquared < bestDistanceSquared) {
					bestPlantPos = plantPos.get();
					bestStandPos = standPos.get();
					bestDistanceSquared = distanceSquared;
				}
			}
		}

		return bestPlantPos == null
			? Optional.empty()
			: Optional.of(new ForestryTask(ForestryAction.PLANT_SEEDLING, bestPlantPos, bestStandPos, seedlingGoods, -1, "planting_seedlings"));
	}

	private static Optional<ForestryTask> nearestTreeTask(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Villager forester,
		long tick
	) {
		if (availableSeedlingGoods(stock) == null) {
			return Optional.empty();
		}

		BlockPos origin = foresterSearchOrigin(level, settlement, forester);
		BlockPos bestTreePos = null;
		BlockPos bestStandPos = null;
		String bestSeedlingGoods = "";
		double bestDistanceSquared = Double.POSITIVE_INFINITY;
		int radius = Math.min(128, workRadius(settlement));
		int offset = Math.floorMod((int) (tick / 20L), TREE_SCAN_STEP_BLOCKS);

		for (int x = origin.getX() - radius + offset; x <= origin.getX() + radius; x += TREE_SCAN_STEP_BLOCKS) {
			for (int z = origin.getZ() - radius + offset; z <= origin.getZ() + radius; z += TREE_SCAN_STEP_BLOCKS) {
				Optional<BlockPos> treeBase = surfaceTreeBase(level, x, z);

				if (treeBase.isEmpty() || !isCuttableTree(level, settlement, treeBase.get())) {
					continue;
				}

				String seedlingGoods = seedlingGoodsForLogState(level.getBlockState(treeBase.get()));
				if (seedlingGoods == null || stock.getOrDefault(seedlingGoods, 0) <= 0) {
					seedlingGoods = availableSeedlingGoods(stock);
				}

				if (seedlingGoods == null) {
					continue;
				}

				Optional<BlockPos> standPos = standableAround(level, treeBase.get());

				if (standPos.isEmpty()) {
					continue;
				}

				double distanceSquared = standPos.get().distSqr(forester.blockPosition());

				if (bestTreePos == null || distanceSquared < bestDistanceSquared) {
					bestTreePos = treeBase.get();
					bestStandPos = standPos.get();
					bestSeedlingGoods = seedlingGoods;
					bestDistanceSquared = distanceSquared;
				}
			}
		}

		return bestTreePos == null
			? Optional.empty()
			: Optional.of(new ForestryTask(ForestryAction.CHOP_TREE, bestTreePos, bestStandPos, bestSeedlingGoods, -1, "cutting_trees"));
	}

	private static boolean performForestryTask(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Villager forester,
		ForestryTask task
	) {
		return switch (task.action()) {
			case COLLECT_DROP -> collectDrop(level, forester, task.entityId());
			case PLANT_SEEDLING -> plantSeedling(level, stock, task.workPos(), task.seedlingGoods());
			case CHOP_TREE -> chopTree(level, settlement, stock, task.workPos(), task.seedlingGoods());
		};
	}

	private static boolean collectDrop(ServerLevel level, Villager forester, int entityId) {
		if (!(level.getEntity(entityId) instanceof ItemEntity itemEntity) || itemEntity.isRemoved()) {
			return false;
		}

		return SettlementVillagerItemPickupWork.carryItemEntity(level, forester, itemEntity);
	}

	private static boolean plantSeedling(ServerLevel level, Map<String, Integer> stock, BlockPos plantPos, String seedlingGoods) {
		BlockState seedlingState = seedlingBlockState(seedlingGoods);

		if (seedlingState == null || !canPlantSeedling(level, plantPos, seedlingState)) {
			return false;
		}

		if (seedlingGoods.equals("dark_oak_sapling")) {
			if (!canPlantDarkOakCluster(level, plantPos)) {
				return false;
			}

			if (!SettlementGoods.consumeGoods(stock, seedlingGoods, 4)) {
				return false;
			}

			for (int dx = 0; dx <= 1; dx++) {
				for (int dz = 0; dz <= 1; dz++) {
					level.setBlock(plantPos.offset(dx, 0, dz), seedlingState, BLOCK_UPDATE_FLAGS);
				}
			}

			return true;
		}

		if (!SettlementGoods.consumeGoods(stock, seedlingGoods, 1)) {
			return false;
		}

		level.setBlock(plantPos, seedlingState, BLOCK_UPDATE_FLAGS);
		return true;
	}

	private static boolean chopTree(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, BlockPos treeBase, String seedlingGoods) {
		if (!isCuttableTree(level, settlement, treeBase)) {
			return false;
		}

		if (!SettlementGoods.consumeGoods(stock, seedlingGoods, seedlingGoods.equals("dark_oak_sapling") ? 4 : 1)) {
			return false;
		}

		Set<BlockPos> logs = connectedTreeLogs(level, treeBase);

		if (logs.isEmpty()) {
			SettlementGoods.addGoods(stock, seedlingGoods, seedlingGoods.equals("dark_oak_sapling") ? 4 : 1);
			return false;
		}

		for (BlockPos logPos : logs) {
			BlockState logState = level.getBlockState(logPos);

			if (!isLog(logState)) {
				continue;
			}

			level.levelEvent(2001, logPos, Block.getId(logState));
			level.setBlock(logPos, Blocks.AIR.defaultBlockState(), BLOCK_UPDATE_FLAGS);
			SettlementGoods.addGoods(stock, "logs", 1);
		}

		if (!plantSeedlingAlreadyPaid(level, treeBase, seedlingGoods)) {
			SettlementGoods.addGoods(stock, seedlingGoods, seedlingGoods.equals("dark_oak_sapling") ? 4 : 1);
		}

		return true;
	}

	private static boolean plantSeedlingAlreadyPaid(ServerLevel level, BlockPos plantPos, String seedlingGoods) {
		BlockState seedlingState = seedlingBlockState(seedlingGoods);

		if (seedlingState == null || !canPlantSeedling(level, plantPos, seedlingState)) {
			return false;
		}

		if (seedlingGoods.equals("dark_oak_sapling")) {
			if (!canPlantDarkOakCluster(level, plantPos)) {
				return false;
			}

			for (int dx = 0; dx <= 1; dx++) {
				for (int dz = 0; dz <= 1; dz++) {
					level.setBlock(plantPos.offset(dx, 0, dz), seedlingState, BLOCK_UPDATE_FLAGS);
				}
			}

			return true;
		}

		level.setBlock(plantPos, seedlingState, BLOCK_UPDATE_FLAGS);
		return true;
	}

	private static Set<BlockPos> connectedTreeLogs(ServerLevel level, BlockPos treeBase) {
		Set<BlockPos> logs = new LinkedHashSet<>();
		ArrayDeque<BlockPos> open = new ArrayDeque<>();
		open.add(treeBase);

		while (!open.isEmpty() && logs.size() < MAX_LOGS_PER_TREE) {
			BlockPos current = open.removeFirst();

			if (logs.contains(current) || current.distSqr(treeBase) > 100.0D || !isLog(level.getBlockState(current))) {
				continue;
			}

			logs.add(current);

			for (Direction direction : Direction.values()) {
				open.add(current.relative(direction));
			}
		}

		return logs;
	}

	private static Optional<BlockPos> surfaceTreeBase(ServerLevel level, int x, int z) {
		if (!level.hasChunkAt(new BlockPos(x, level.getMinY(), z))) {
			return Optional.empty();
		}

		int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		int minY = Math.max(level.getMinY(), topY - TREE_SURFACE_SEARCH_DEPTH_BLOCKS);

		for (int y = topY; y >= minY; y--) {
			BlockPos scanPos = new BlockPos(x, y, z);

			if (!isLog(level.getBlockState(scanPos))) {
				continue;
			}

			BlockPos basePos = scanPos;

			while (basePos.getY() > level.getMinY() && isLog(level.getBlockState(basePos.below()))) {
				basePos = basePos.below();
			}

			return Optional.of(basePos);
		}

		return Optional.empty();
	}

	private static boolean isCuttableTree(ServerLevel level, SettlementState settlement, BlockPos treeBase) {
		BlockState baseState = level.getBlockState(treeBase);

		if (!isLog(baseState)
			|| !isNaturalTreeBase(level, treeBase)
			|| !hasNearbyLeaves(level, treeBase)
			|| hasNearbyBeeHome(level, treeBase)
			|| !isWithinForestryRange(settlement, treeBase)) {
			return false;
		}

		int nearbyTrees = nearbyTreeBaseCount(level, treeBase, TREE_PRESERVE_RADIUS_BLOCKS);
		return SettlementGreenspace.canCutTree(level, settlement, treeBase, nearbyTrees, preserveTreeCount(settlement, treeBase));
	}

	private static boolean isNaturalTreeBase(ServerLevel level, BlockPos treeBase) {
		BlockState belowState = level.getBlockState(treeBase.below());
		return belowState.is(BlockTags.DIRT) || belowState.is(Blocks.MOSS_BLOCK) || belowState.is(Blocks.MUD);
	}

	private static boolean hasNearbyLeaves(ServerLevel level, BlockPos treeBase) {
		for (BlockPos scanPos : BlockPos.betweenClosed(treeBase.offset(-3, 1, -3), treeBase.offset(3, 8, 3))) {
			if (level.getBlockState(scanPos).is(BlockTags.LEAVES)) {
				return true;
			}
		}

		return false;
	}

	private static boolean hasNearbyBeeHome(ServerLevel level, BlockPos treeBase) {
		for (BlockPos scanPos : BlockPos.betweenClosed(treeBase.offset(-4, 0, -4), treeBase.offset(4, 8, 4))) {
			BlockState state = level.getBlockState(scanPos);

			if (state.is(Blocks.BEE_NEST) || state.is(Blocks.BEEHIVE)) {
				return true;
			}
		}

		return false;
	}

	private static int nearbyTreeBaseCount(ServerLevel level, BlockPos center, int radius) {
		Set<BlockPos> bases = new HashSet<>();
		int radiusSquared = radius * radius;

		for (int x = center.getX() - radius; x <= center.getX() + radius; x += 4) {
			for (int z = center.getZ() - radius; z <= center.getZ() + radius; z += 4) {
				if (center.distToCenterSqr(x + 0.5D, center.getY() + 0.5D, z + 0.5D) > radiusSquared) {
					continue;
				}

				surfaceTreeBase(level, x, z)
					.filter(base -> base.distSqr(center) <= radiusSquared)
					.filter(base -> hasNearbyLeaves(level, base))
					.ifPresent(base -> bases.add(base.immutable()));
			}
		}

		return bases.size();
	}

	private static Optional<BlockPos> surfacePlantPos(ServerLevel level, int x, int z, BlockState seedlingState) {
		if (!level.hasChunkAt(new BlockPos(x, level.getMinY(), z))) {
			return Optional.empty();
		}

		int plantY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos plantPos = new BlockPos(x, plantY, z);
		return canPlantSeedling(level, plantPos, seedlingState) ? Optional.of(plantPos) : Optional.empty();
	}

	private static boolean canPlantSeedling(ServerLevel level, BlockPos plantPos, BlockState seedlingState) {
		BlockState currentState = level.getBlockState(plantPos);

		if (!currentState.isAir() && !currentState.canBeReplaced()) {
			return false;
		}

		return seedlingState.canSurvive(level, plantPos);
	}

	private static boolean canPlantDarkOakCluster(ServerLevel level, BlockPos plantPos) {
		BlockState seedlingState = Blocks.DARK_OAK_SAPLING.defaultBlockState();

		for (int dx = 0; dx <= 1; dx++) {
			for (int dz = 0; dz <= 1; dz++) {
				if (!canPlantSeedling(level, plantPos.offset(dx, 0, dz), seedlingState)) {
					return false;
				}
			}
		}

		return true;
	}

	private static Optional<BlockPos> standableAround(ServerLevel level, BlockPos workPos) {
		BlockPos fallback = null;
		double fallbackDistanceSquared = Double.POSITIVE_INFINITY;

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos candidate = workPos.relative(direction);

			if (isStandable(level, candidate)) {
				return Optional.of(candidate);
			}

			double distanceSquared = candidate.distSqr(workPos);

			if (isStandable(level, candidate.above()) && distanceSquared < fallbackDistanceSquared) {
				fallback = candidate.above();
				fallbackDistanceSquared = distanceSquared;
			}
		}

		return Optional.ofNullable(fallback);
	}

	private static Optional<BlockPos> standableDropPickupPos(ServerLevel level, BlockPos itemPos) {
		if (isStandable(level, itemPos)) {
			return Optional.of(itemPos);
		}

		if (isStandable(level, itemPos.below())) {
			return Optional.of(itemPos.below());
		}

		if (isStandable(level, itemPos.above())) {
			return Optional.of(itemPos.above());
		}

		Optional<BlockPos> sameLevelStandPos = standableAround(level, itemPos);

		if (sameLevelStandPos.isPresent()) {
			return sameLevelStandPos;
		}

		Optional<BlockPos> lowerStandPos = standableAround(level, itemPos.below());

		if (lowerStandPos.isPresent()) {
			return lowerStandPos;
		}

		return standableAround(level, itemPos.above());
	}

	private static boolean isStandable(ServerLevel level, BlockPos pos) {
		BlockState footState = level.getBlockState(pos);
		BlockState headState = level.getBlockState(pos.above());
		BlockState belowState = level.getBlockState(pos.below());
		return footState.isAir() && headState.isAir() && !belowState.isAir();
	}

	private static boolean isForestryDrop(ItemEntity entity) {
		String goodsKey = SettlementGoods.goodsKeyForItem(entity.getItem());
		return goodsKey != null && (
			SettlementGoods.isSeedlingGoods(goodsKey)
				|| goodsKey.equals("apple")
				|| goodsKey.equals("stick")
				|| goodsKey.equals("leaf_litter")
				|| goodsKey.equals("logs")
		);
	}

	private static boolean isWithinForestryRange(SettlementState settlement, BlockPos pos) {
		int radius = workRadius(settlement);
		return pos.distSqr(settlement.center()) <= radius * radius;
	}

	private static boolean isPreferredPlantingArea(SettlementState settlement, BlockPos pos) {
		double distanceSquared = pos.distSqr(settlement.center());
		int villageRadius = SettlementVillagers.settlementRadiusBlocks(settlement);
		double innerRadius = villageRadius * 0.65D;
		return distanceSquared >= innerRadius * innerRadius && distanceSquared <= workRadius(settlement) * workRadius(settlement);
	}

	private static List<PlantingStructureBounds> plantingStructureBounds(ServerLevel level, SettlementState settlement) {
		List<PlantingStructureBounds> bounds = new java.util.ArrayList<>();

		for (SettlementBuildSite buildSite : LiveVillagesSavedData.get(level.getServer()).getBuildSitesForSettlement(settlement.id())) {
			Integer minX = null;
			Integer maxX = null;
			Integer minZ = null;
			Integer maxZ = null;

			for (SettlementBuildBlockState block : buildSite.blocks()) {
				Optional<BlockPos> blockPos = SettlementConstruction.buildSiteBlockPos(buildSite, block);
				if (blockPos.isEmpty()) {
					continue;
				}

				BlockPos pos = blockPos.get();
				minX = minX == null ? pos.getX() : Math.min(minX, pos.getX());
				maxX = maxX == null ? pos.getX() : Math.max(maxX, pos.getX());
				minZ = minZ == null ? pos.getZ() : Math.min(minZ, pos.getZ());
				maxZ = maxZ == null ? pos.getZ() : Math.max(maxZ, pos.getZ());
			}

			if (minX != null && maxX != null && minZ != null && maxZ != null) {
				bounds.add(new PlantingStructureBounds(minX, maxX, minZ, maxZ));
			}
		}

		return bounds;
	}

	private static boolean isTooCloseToStructure(BlockPos plantPos, List<PlantingStructureBounds> structureBounds) {
		return structureBounds.stream()
			.anyMatch(bounds -> bounds.containsWithMargin(plantPos, SAPLING_STRUCTURE_CLEARANCE_BLOCKS));
	}

	private static boolean hasNearbySapling(ServerLevel level, BlockPos center, int radius) {
		for (BlockPos scanPos : BlockPos.betweenClosed(center.offset(-radius, -1, -radius), center.offset(radius, 2, radius))) {
			if (scanPos.equals(center)) {
				continue;
			}

			if (level.getBlockState(scanPos).getBlock() instanceof SaplingBlock) {
				return true;
			}
		}

		return false;
	}

	private static boolean hasNearbyTreeBase(ServerLevel level, BlockPos center, int radius) {
		for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
			for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
				Optional<BlockPos> treeBase = surfaceTreeBase(level, x, z);
				if (treeBase.isEmpty() || treeBase.get().equals(center)) {
					continue;
				}

				if (treeBase.get().distSqr(center) <= radius * radius && hasNearbyLeaves(level, treeBase.get())) {
					return true;
				}
			}
		}

		return false;
	}

	private static int preserveTreeCount(SettlementState settlement, BlockPos pos) {
		int villageRadius = SettlementVillagers.settlementRadiusBlocks(settlement);
		return pos.distSqr(settlement.center()) <= villageRadius * villageRadius ? 5 : 2;
	}

	private static int preferredTreeDensity(SettlementState settlement, BlockPos pos) {
		int villageRadius = SettlementVillagers.settlementRadiusBlocks(settlement);
		return pos.distSqr(settlement.center()) <= villageRadius * villageRadius ? 3 : 7;
	}

	private static int workRadius(SettlementState settlement) {
		return SettlementVillagers.settlementRadiusBlocks(settlement) + FORESTRY_EXTRA_RADIUS_BLOCKS;
	}

	private static BlockPos foresterSearchOrigin(ServerLevel level, SettlementState settlement, Villager forester) {
		return SettlementVillagers.foresterJobSite(level, forester).orElse(settlement.center());
	}

	private static String seedlingToPlant(Map<String, Integer> stock) {
		String bestGoods = null;
		int bestExcess = 0;

		for (String goodsKey : SettlementGoods.SEEDLING_GOODS) {
			int reserve = seedlingReserve(goodsKey);
			int excess = stock.getOrDefault(goodsKey, 0) - reserve;

			if (excess > bestExcess) {
				bestGoods = goodsKey;
				bestExcess = excess;
			}
		}

		return bestGoods;
	}

	private static String availableSeedlingGoods(Map<String, Integer> stock) {
		for (String goodsKey : SettlementGoods.SEEDLING_GOODS) {
			if (stock.getOrDefault(goodsKey, 0) >= (goodsKey.equals("dark_oak_sapling") ? 4 : 1)) {
				return goodsKey;
			}
		}

		return null;
	}

	private static int seedlingReserve(String goodsKey) {
		return goodsKey.equals("dark_oak_sapling") ? 8 : 4;
	}

	private static BlockState seedlingBlockState(String goodsKey) {
		Block block = switch (goodsKey) {
			case "oak_sapling" -> Blocks.OAK_SAPLING;
			case "spruce_sapling" -> Blocks.SPRUCE_SAPLING;
			case "birch_sapling" -> Blocks.BIRCH_SAPLING;
			case "jungle_sapling" -> Blocks.JUNGLE_SAPLING;
			case "acacia_sapling" -> Blocks.ACACIA_SAPLING;
			case "cherry_sapling" -> Blocks.CHERRY_SAPLING;
			case "dark_oak_sapling" -> Blocks.DARK_OAK_SAPLING;
			case "pale_oak_sapling" -> Blocks.PALE_OAK_SAPLING;
			case "mangrove_propagule" -> Blocks.MANGROVE_PROPAGULE;
			default -> Blocks.AIR;
		};

		return block instanceof SaplingBlock || block == Blocks.MANGROVE_PROPAGULE ? block.defaultBlockState() : null;
	}

	private static String seedlingGoodsForLogState(BlockState state) {
		if (state.is(Blocks.SPRUCE_LOG) || state.is(Blocks.SPRUCE_WOOD) || state.is(Blocks.STRIPPED_SPRUCE_LOG) || state.is(Blocks.STRIPPED_SPRUCE_WOOD)) {
			return "spruce_sapling";
		}

		if (state.is(Blocks.BIRCH_LOG) || state.is(Blocks.BIRCH_WOOD) || state.is(Blocks.STRIPPED_BIRCH_LOG) || state.is(Blocks.STRIPPED_BIRCH_WOOD)) {
			return "birch_sapling";
		}

		if (state.is(Blocks.JUNGLE_LOG) || state.is(Blocks.JUNGLE_WOOD) || state.is(Blocks.STRIPPED_JUNGLE_LOG) || state.is(Blocks.STRIPPED_JUNGLE_WOOD)) {
			return "jungle_sapling";
		}

		if (state.is(Blocks.ACACIA_LOG) || state.is(Blocks.ACACIA_WOOD) || state.is(Blocks.STRIPPED_ACACIA_LOG) || state.is(Blocks.STRIPPED_ACACIA_WOOD)) {
			return "acacia_sapling";
		}

		if (state.is(Blocks.CHERRY_LOG) || state.is(Blocks.CHERRY_WOOD) || state.is(Blocks.STRIPPED_CHERRY_LOG) || state.is(Blocks.STRIPPED_CHERRY_WOOD)) {
			return "cherry_sapling";
		}

		if (state.is(Blocks.DARK_OAK_LOG) || state.is(Blocks.DARK_OAK_WOOD) || state.is(Blocks.STRIPPED_DARK_OAK_LOG) || state.is(Blocks.STRIPPED_DARK_OAK_WOOD)) {
			return "dark_oak_sapling";
		}

		if (state.is(Blocks.PALE_OAK_LOG) || state.is(Blocks.PALE_OAK_WOOD) || state.is(Blocks.STRIPPED_PALE_OAK_LOG) || state.is(Blocks.STRIPPED_PALE_OAK_WOOD)) {
			return "pale_oak_sapling";
		}

		if (state.is(Blocks.MANGROVE_LOG) || state.is(Blocks.MANGROVE_WOOD) || state.is(Blocks.STRIPPED_MANGROVE_LOG) || state.is(Blocks.STRIPPED_MANGROVE_WOOD)) {
			return "mangrove_propagule";
		}

		if (state.is(Blocks.OAK_LOG) || state.is(Blocks.OAK_WOOD) || state.is(Blocks.STRIPPED_OAK_LOG) || state.is(Blocks.STRIPPED_OAK_WOOD)) {
			return "oak_sapling";
		}

		return null;
	}

	private static boolean isLog(BlockState state) {
		return state.is(BlockTags.LOGS);
	}

	private static void showForesterTool(Villager forester, ForestryAction action) {
		ItemStack held = forester.getMainHandItem();

		if (!held.isEmpty()) {
			return;
		}

		forester.setItemSlot(
			EquipmentSlot.MAINHAND,
			new ItemStack(action == ForestryAction.PLANT_SEEDLING ? Items.WOODEN_HOE : Items.WOODEN_AXE)
		);
	}

	private static void steerForesterTowardTask(Villager forester, BlockPos standPos) {
		forester.getNavigation().moveTo(standPos.getX() + 0.5D, standPos.getY(), standPos.getZ() + 0.5D, FORESTRY_WALK_SPEED);
	}

	private static boolean isWithinWorkReach(Villager forester, BlockPos workPos) {
		return forester.distanceToSqr(workPos.getX() + 0.5D, workPos.getY() + 0.5D, workPos.getZ() + 0.5D) <= FORESTRY_WORK_REACH_DISTANCE_SQUARED;
	}

	private enum ForestryAction {
		COLLECT_DROP,
		PLANT_SEEDLING,
		CHOP_TREE
	}

	private record ForestryTask(
		ForestryAction action,
		BlockPos workPos,
		BlockPos standPos,
		String seedlingGoods,
		int entityId,
		String taskKey
	) {
	}

	private record TimedTask(String taskKey, long tick) {
	}

	private record CachedForestryTask(Optional<ForestryTask> task, long tick) {
	}

	private record PlantingStructureBounds(int minX, int maxX, int minZ, int maxZ) {
		private boolean containsWithMargin(BlockPos pos, int margin) {
			return pos.getX() >= minX - margin
				&& pos.getX() <= maxX + margin
				&& pos.getZ() >= minZ - margin
				&& pos.getZ() <= maxZ + margin;
		}
	}
}
