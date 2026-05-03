package com.ronhelwig.livevillages.sim;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class SettlementMinerWork {
	private static final long DAY_TICKS = 24_000L;
	private static final long VILLAGE_REST_START_TICK = 12_000L;
	private static final double MINING_WORK_REACH_DISTANCE_SQUARED = 6.25D;
	private static final double SHAFT_ENTRY_SNAP_DISTANCE_SQUARED = 25.0D;
	private static final double MINING_WALK_SPEED = 0.75D;
	private static final long TASK_MEMORY_TICKS = 320L;
	private static final long MINING_DECIDE_INTERVAL_TICKS = 80L;
	private static final int MAX_SHAFT_DEPTH_BLOCKS = 96;
	private static final int SHAFT_LIGHT_SPACING_LEVELS = 8;
	private static final int MAX_CAVE_SCAN_RADIUS_BLOCKS = 10;
	private static final int MAX_CAVE_SCAN_AIR_CELLS = 96;
	private static final int MAX_CAVE_STAND_POSITIONS = 24;
	private static final int CAVE_LIGHT_SPACING_BLOCKS = 7;
	private static final Map<String, TimedTask> ACTIVE_TASKS = new HashMap<>();

	private SettlementMinerWork() {
	}

	public static boolean maintainLoadedMining(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Collection<SettlementBuildSite> buildSites
	) {
		List<Villager> miners = SettlementVillagers.nearbyMiners(level, settlement);

		if (miners.isEmpty()) {
			return false;
		}

		List<SettlementBuildSite> mineEntrances = buildSites.stream()
			.filter(buildSite -> buildSite.blueprintId() == SettlementBuildSiteType.MINE_ENTRANCE)
			.filter(buildSite -> isOperationalMineEntrance(level, buildSite))
			.toList();

		if (mineEntrances.isEmpty()) {
			return false;
		}

		boolean changed = false;
		long tick = level.getServer().getTickCount();

		for (Villager miner : miners) {
			Optional<MineSite> mineSite = mineSiteFor(level, miner, mineEntrances);

			if (mineSite.isEmpty()) {
				ACTIVE_TASKS.remove(miner.getUUID().toString());
				continue;
			}

			if (isVillageRestTime(level)) {
				ACTIVE_TASKS.remove(miner.getUUID().toString());
				moveMinerTowardSurface(level, miner, mineSite.get());
				continue;
			}

			if (!SettlementVillagerWorkSchedule.shouldStartNewWork(level, miner, "mining", MINING_DECIDE_INTERVAL_TICKS)) {
				if (SettlementVillagerWorkSchedule.isTakingBreak(level, miner)) {
					moveMinerTowardSurface(level, miner, mineSite.get());
				}

				continue;
			}

			Optional<MinerTask> task = chooseTask(level, stock, miner, mineSite.get());

			if (task.isEmpty()) {
				ACTIVE_TASKS.remove(miner.getUUID().toString());
				continue;
			}

			MinerTask minerTask = task.get();
			showMinerTool(miner, minerTask.action());
			moveMinerTowardTask(level, miner, mineSite.get(), minerTask);
			ACTIVE_TASKS.put(miner.getUUID().toString(), new TimedTask(minerTask.taskKey(), tick));

			if (!isWithinWorkReach(miner, minerTask.targetPos())) {
				continue;
			}

			if (performTask(level, stock, mineSite.get(), minerTask)) {
				miner.swing(InteractionHand.MAIN_HAND);
				changed = true;
			}
		}

		return changed;
	}

	public static Optional<String> loadedMinerTaskKey(ServerLevel level, Villager villager) {
		TimedTask task = ACTIVE_TASKS.get(villager.getUUID().toString());

		if (task == null || level.getServer().getTickCount() - task.tick() > TASK_MEMORY_TICKS) {
			return Optional.empty();
		}

		return Optional.of(task.taskKey());
	}

	private static Optional<MineSite> mineSiteFor(ServerLevel level, Villager miner, List<SettlementBuildSite> mineEntrances) {
		Optional<BlockPos> jobSite = SettlementVillagers.minerJobSite(level, miner);
		SettlementBuildSite bestSite = null;
		double bestDistanceSquared = Double.POSITIVE_INFINITY;

		for (SettlementBuildSite buildSite : mineEntrances) {
			if (jobSite.isPresent() && buildSite.workstationPos().equals(jobSite.get())) {
				return createMineSite(level, buildSite);
			}

			double distanceSquared = buildSite.workstationPos().distSqr(miner.blockPosition());

			if (bestSite == null || distanceSquared < bestDistanceSquared) {
				bestSite = buildSite;
				bestDistanceSquared = distanceSquared;
			}
		}

		return bestSite == null ? Optional.empty() : createMineSite(level, bestSite);
	}

	private static Optional<MineSite> createMineSite(ServerLevel level, SettlementBuildSite buildSite) {
		ShaftLayout layout = shaftLayout(buildSite);

		if (layout.ladderColumns().isEmpty() || layout.openColumns().isEmpty()) {
			return Optional.empty();
		}

		BlockPos sampleLadderPos = worldPos(buildSite, layout.ladderColumns().getFirst(), -1);
		BlockState ladderState = findTemplateLadderState(level, buildSite, layout);
		Direction supportDirection = supportDirectionFor(level, sampleLadderPos, ladderState);
		return supportDirection == null
			? Optional.empty()
			: Optional.of(new MineSite(buildSite, layout, ladderState, supportDirection));
	}

	private static boolean isOperationalMineEntrance(ServerLevel level, SettlementBuildSite buildSite) {
		ShaftLayout layout = shaftLayout(buildSite);

		if (layout.ladderColumns().isEmpty() || layout.openColumns().isEmpty()) {
			return false;
		}

		boolean hasStarterLadder = false;

		for (ShaftColumn column : layout.ladderColumns()) {
			if (level.getBlockState(worldPos(buildSite, column, -1)).is(Blocks.LADDER)) {
				hasStarterLadder = true;
				break;
			}
		}

		if (!hasStarterLadder) {
			return false;
		}

		for (ShaftColumn column : layout.openColumns()) {
			if (level.getBlockState(worldPos(buildSite, column, -1)).isAir()
				|| canMine(level.getBlockState(worldPos(buildSite, column, -1)), level, worldPos(buildSite, column, -1))) {
				return true;
			}
		}

		return false;
	}

	private static Optional<MinerTask> chooseTask(ServerLevel level, Map<String, Integer> stock, Villager miner, MineSite mineSite) {
		int bottomUp = deepestCompleteShaftUp(level, mineSite);
		List<BlockPos> frontierCells = shaftFrontierCells(mineSite, bottomUp);
		Optional<MinerTask> caveTask = chooseCaveTask(level, stock, mineSite, frontierCells, bottomUp);

		if (caveTask.isPresent()) {
			return caveTask;
		}

		Optional<BlockPos> lightPos = desiredLightPos(level, mineSite, bottomUp);
		Optional<BlockPos> standPos = standPosFor(miner, mineSite, bottomUp);

		if (standPos.isEmpty()) {
			return Optional.empty();
		}

		if (lightPos.isPresent()) {
			BlockState lightState = preferredShaftLightState(stock, mineSite);

			if (lightState != null) {
				return Optional.of(new MinerTask(MinerAction.PLACE_LIGHT, lightPos.get(), lightState, standPos.get(), "lighting_mine_shaft"));
			}
		}

		Optional<BlockPos> stoneTarget = findExposedStoneTarget(level, mineSite, frontierCells);
		Optional<BlockPos> oreTarget = findExposedOreTarget(level, mineSite, frontierCells);

		if (oreTarget.isPresent()) {
			BlockPos targetPos = oreTarget.get();
			BlockState replacementState = null;

			if (requiresLadderSupportReplacement(targetPos, mineSite, bottomUp)) {
				replacementState = supportReplacementMaterial(stock);

				if (replacementState == null) {
					return stoneTarget.map(pos -> new MinerTask(MinerAction.MINE_STONE, pos, null, standPos.get(), "mining_shaft_stone"));
				}
			}

			return Optional.of(new MinerTask(MinerAction.MINE_ORE, targetPos, replacementState, standPos.get(), "mining_ore_vein"));
		}

		if (!canSupplyNextLadderLevel(stock)) {
			return stoneTarget.map(pos -> new MinerTask(MinerAction.MINE_STONE, pos, null, standPos.get(), "mining_shaft_stone"));
		}

		int nextUp = bottomUp - 1;

		if (worldPos(mineSite.buildSite(), mineSite.layout().openColumns().getFirst(), nextUp).getY() <= level.getMinY()) {
			return Optional.empty();
		}

		for (ShaftColumn column : mineSite.layout().ladderColumns()) {
			BlockPos supportPos = worldPos(mineSite.buildSite(), column, nextUp).relative(mineSite.supportDirection());
			BlockState supportState = level.getBlockState(supportPos);

			if (!isStableLadderSupport(supportState)) {
				BlockState replacementMaterial = supportReplacementMaterial(stock);

				if (replacementMaterial == null) {
					return stoneTarget.map(pos -> new MinerTask(MinerAction.MINE_STONE, pos, null, standPos.get(), "mining_shaft_stone"));
				}

				return Optional.of(new MinerTask(MinerAction.PLACE_SUPPORT, supportPos, replacementMaterial, standPos.get(), "reinforcing_shaft_support"));
			}
		}

		for (ShaftColumn column : mineSite.layout().ladderColumns()) {
			BlockPos targetPos = worldPos(mineSite.buildSite(), column, nextUp);
			BlockState targetState = level.getBlockState(targetPos);

			if (!targetState.isAir() && !targetState.is(Blocks.LADDER)) {
				return Optional.of(new MinerTask(MinerAction.DIG_SHAFT, targetPos, null, standPos.get(), "digging_mine_shaft"));
			}
		}

		for (ShaftColumn column : mineSite.layout().ladderColumns()) {
			BlockPos targetPos = worldPos(mineSite.buildSite(), column, nextUp);
			BlockState targetState = level.getBlockState(targetPos);

			if (!targetState.is(Blocks.LADDER)) {
				return Optional.of(new MinerTask(MinerAction.PLACE_LADDER, targetPos, mineSite.ladderStateTemplate(), standPos.get(), "placing_shaft_ladders"));
			}
		}

		for (ShaftColumn column : mineSite.layout().openColumns()) {
			BlockPos targetPos = worldPos(mineSite.buildSite(), column, nextUp);
			BlockState targetState = level.getBlockState(targetPos);

			if (!isClearOpenShaftCell(targetState)) {
				return Optional.of(new MinerTask(MinerAction.DIG_SHAFT, targetPos, null, standPos.get(), "digging_mine_shaft"));
			}
		}

		return stoneTarget.map(pos -> new MinerTask(MinerAction.MINE_STONE, pos, null, standPos.get(), "mining_shaft_stone"));
	}

	private static Optional<MinerTask> chooseCaveTask(
		ServerLevel level,
		Map<String, Integer> stock,
		MineSite mineSite,
		List<BlockPos> frontierCells,
		int bottomUp
	) {
		Optional<CavePocket> cavePocket = scanNearbyCave(level, mineSite, frontierCells);

		if (cavePocket.isEmpty()) {
			return Optional.empty();
		}

		BlockState caveLightState = preferredCaveLightState(stock);

		if (caveLightState != null) {
			for (BlockPos targetPos : cavePocket.get().lightTargets()) {
				if (hasNearbyCaveLight(level, targetPos, CAVE_LIGHT_SPACING_BLOCKS)) {
					continue;
				}

				Optional<BlockPos> standPos = adjacentCaveStandPos(targetPos, cavePocket.get().standPositions());

				if (standPos.isPresent()) {
					return Optional.of(new MinerTask(MinerAction.PLACE_LIGHT, targetPos, caveLightState, standPos.get(), "lighting_cave"));
				}
			}
		}

		for (BlockPos standPos : cavePocket.get().standPositions()) {
			Optional<BlockPos> oreTarget = exposedOreFromStandPos(level, standPos, cavePocket.get().airCells());

			if (oreTarget.isEmpty()) {
				continue;
			}

			BlockPos targetPos = oreTarget.get();
			BlockState replacementState = null;

			if (requiresLadderSupportReplacement(targetPos, mineSite, bottomUp)) {
				replacementState = supportReplacementMaterial(stock);

				if (replacementState == null) {
					continue;
				}
			}

			return Optional.of(new MinerTask(MinerAction.MINE_ORE, targetPos, replacementState, standPos, "mining_ore_vein"));
		}

		return Optional.empty();
	}

	private static boolean performTask(ServerLevel level, Map<String, Integer> stock, MineSite mineSite, MinerTask task) {
		return switch (task.action()) {
			case DIG_SHAFT -> mineBlockIntoStock(level, stock, task.targetPos(), null);
			case MINE_ORE -> mineBlockIntoStock(level, stock, task.targetPos(), task.replacementState());
			case MINE_STONE -> mineBlockIntoStock(level, stock, task.targetPos(), null);
			case PLACE_SUPPORT -> placeSupport(level, stock, task.targetPos(), task.replacementState());
			case PLACE_LADDER -> placeLadder(level, stock, task.targetPos(), task.replacementState(), mineSite.supportDirection());
			case PLACE_LIGHT -> placeLight(level, stock, task.targetPos(), task.replacementState());
		};
	}

	private static boolean placeLight(ServerLevel level, Map<String, Integer> stock, BlockPos targetPos, BlockState lightState) {
		if (lightState == null || !level.getBlockState(targetPos).isAir() || !consumeLightSource(stock, lightState)) {
			return false;
		}

		level.setBlock(targetPos, lightState, 3);
		return true;
	}

	private static boolean placeSupport(ServerLevel level, Map<String, Integer> stock, BlockPos targetPos, BlockState supportState) {
		if (supportState == null || !consumeSupportMaterial(stock, supportState)) {
			return false;
		}

		level.setBlock(targetPos, supportState, 3);
		return true;
	}

	private static boolean placeLadder(
		ServerLevel level,
		Map<String, Integer> stock,
		BlockPos targetPos,
		BlockState ladderState,
		Direction supportDirection
	) {
		if (ladderState == null || !canPlaceLadder(level, targetPos, supportDirection) || !consumeLadder(stock)) {
			return false;
		}

		level.setBlock(targetPos, ladderState, 3);
		return true;
	}

	private static boolean mineBlockIntoStock(ServerLevel level, Map<String, Integer> stock, BlockPos targetPos, BlockState replacementState) {
		BlockState targetState = level.getBlockState(targetPos);

		if (!canMine(targetState, level, targetPos)) {
			return false;
		}

		for (ItemStack drop : Block.getDrops(targetState, level, targetPos, null, null, new ItemStack(Items.IRON_PICKAXE))) {
			String goodsKey = SettlementGoods.goodsKeyForItem(drop);

			if (goodsKey != null) {
				SettlementGoods.addGoods(stock, goodsKey, drop.getCount());
			}
		}

		level.levelEvent(2001, targetPos, Block.getId(targetState));

		if (replacementState == null) {
			level.setBlock(targetPos, Blocks.AIR.defaultBlockState(), 3);
		} else {
			level.setBlock(targetPos, replacementState, 3);
		}

		return true;
	}

	private static boolean canMine(BlockState state, ServerLevel level, BlockPos pos) {
		return !state.isAir()
			&& !state.is(Blocks.BEDROCK)
			&& state.getDestroySpeed(level, pos) >= 0.0F
			&& state.getFluidState().isEmpty();
	}

	private static boolean canPlaceLadder(ServerLevel level, BlockPos targetPos, Direction supportDirection) {
		BlockState currentState = level.getBlockState(targetPos);

		if (!currentState.isAir()) {
			return false;
		}

		return isStableLadderSupport(level.getBlockState(targetPos.relative(supportDirection)));
	}

	private static Optional<BlockPos> findExposedOreTarget(ServerLevel level, MineSite mineSite, List<BlockPos> frontierCells) {
		Set<BlockPos> candidates = new LinkedHashSet<>();

		for (BlockPos shaftPos : frontierCells) {
			for (Direction direction : Direction.values()) {
				BlockPos candidatePos = shaftPos.relative(direction);

				if (frontierCells.contains(candidatePos) || !isUsefulOre(level.getBlockState(candidatePos))) {
					continue;
				}

				candidates.add(candidatePos.immutable());
			}
		}

		return candidates.stream()
			.sorted(Comparator.comparingDouble(candidate -> candidate.distSqr(mineSite.buildSite().workstationPos())))
			.findFirst();
	}

	private static Optional<BlockPos> findExposedStoneTarget(ServerLevel level, MineSite mineSite, List<BlockPos> frontierCells) {
		Set<BlockPos> candidates = new LinkedHashSet<>();

		for (BlockPos shaftPos : frontierCells) {
			for (Direction direction : Direction.values()) {
				BlockPos candidatePos = shaftPos.relative(direction);

				if (frontierCells.contains(candidatePos)
					|| candidatePos.equals(mineSite.buildSite().workstationPos())
					|| !isUsefulStone(level.getBlockState(candidatePos))
					|| isRequiredLadderSupport(candidatePos, mineSite)) {
					continue;
				}

				candidates.add(candidatePos.immutable());
			}
		}

		return candidates.stream()
			.sorted(Comparator.comparingDouble(candidate -> candidate.distSqr(mineSite.buildSite().workstationPos())))
			.findFirst();
	}

	private static Optional<BlockPos> desiredLightPos(ServerLevel level, MineSite mineSite, int bottomUp) {
		if (!shouldLightLevel(bottomUp)) {
			return Optional.empty();
		}

		List<ShaftColumn> openColumns = mineSite.layout().openColumns();

		if (openColumns.isEmpty()) {
			return Optional.empty();
		}

		int index = Math.floorMod(Math.abs(bottomUp) / SHAFT_LIGHT_SPACING_LEVELS, openColumns.size());
		BlockPos torchPos = worldPos(mineSite.buildSite(), openColumns.get(index), bottomUp);
		BlockState currentState = level.getBlockState(torchPos);
		return isShaftLight(currentState) ? Optional.empty() : Optional.of(torchPos);
	}

	private static Optional<CavePocket> scanNearbyCave(ServerLevel level, MineSite mineSite, List<BlockPos> frontierCells) {
		if (frontierCells.isEmpty()) {
			return Optional.empty();
		}

		Set<BlockPos> frontierSet = new HashSet<>(frontierCells);
		Set<BlockPos> airCells = new LinkedHashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		BlockPos explorationCenter = frontierCells.getLast();

		for (BlockPos frontierCell : frontierCells) {
			for (Direction direction : Direction.values()) {
				BlockPos candidatePos = frontierCell.relative(direction);

				if (frontierSet.contains(candidatePos)
					|| !level.getBlockState(candidatePos).isAir()
					|| candidatePos.distSqr(explorationCenter) > MAX_CAVE_SCAN_RADIUS_BLOCKS * MAX_CAVE_SCAN_RADIUS_BLOCKS) {
					continue;
				}

				if (airCells.add(candidatePos.immutable())) {
					queue.add(candidatePos.immutable());
				}
			}
		}

		while (!queue.isEmpty() && airCells.size() < MAX_CAVE_SCAN_AIR_CELLS) {
			BlockPos currentPos = queue.removeFirst();

			for (Direction direction : Direction.values()) {
				BlockPos nextPos = currentPos.relative(direction);

				if (frontierSet.contains(nextPos)
					|| airCells.contains(nextPos)
					|| !level.getBlockState(nextPos).isAir()
					|| nextPos.distSqr(explorationCenter) > MAX_CAVE_SCAN_RADIUS_BLOCKS * MAX_CAVE_SCAN_RADIUS_BLOCKS) {
					continue;
				}

				airCells.add(nextPos.immutable());
				queue.add(nextPos.immutable());
			}
		}

		if (airCells.isEmpty()) {
			return Optional.empty();
		}

		List<BlockPos> standPositions = airCells.stream()
			.filter(pos -> isStandableCaveCell(level, pos))
			.sorted(Comparator.comparingDouble(pos -> pos.distSqr(explorationCenter)))
			.limit(MAX_CAVE_STAND_POSITIONS)
			.toList();

		if (standPositions.isEmpty()) {
			return Optional.empty();
		}

		List<BlockPos> lightTargets = standPositions.stream()
			.filter(pos -> !frontierSet.contains(pos))
			.toList();

		return Optional.of(new CavePocket(Set.copyOf(airCells), standPositions, lightTargets));
	}

	private static List<BlockPos> shaftFrontierCells(MineSite mineSite, int bottomUp) {
		List<BlockPos> cells = new ArrayList<>();

		for (int up = Math.max(mineSite.layout().starterMinUp(), bottomUp); up <= bottomUp; up++) {
			for (ShaftColumn column : mineSite.layout().ladderColumns()) {
				cells.add(worldPos(mineSite.buildSite(), column, up));
			}

			for (ShaftColumn column : mineSite.layout().openColumns()) {
				cells.add(worldPos(mineSite.buildSite(), column, up));
			}
		}

		return cells;
	}

	private static int deepestCompleteShaftUp(ServerLevel level, MineSite mineSite) {
		int bottomUp = mineSite.layout().starterMinUp();

		for (int up = -1; up >= mineSite.layout().starterMinUp() - MAX_SHAFT_DEPTH_BLOCKS; up--) {
			if (!isCompleteShaftLevel(level, mineSite, up)) {
				break;
			}

			bottomUp = up;
		}

		return bottomUp;
	}

	private static boolean isCompleteShaftLevel(ServerLevel level, MineSite mineSite, int up) {
		for (ShaftColumn column : mineSite.layout().ladderColumns()) {
			if (!level.getBlockState(worldPos(mineSite.buildSite(), column, up)).is(Blocks.LADDER)) {
				return false;
			}
		}

		for (ShaftColumn column : mineSite.layout().openColumns()) {
			if (!isClearOpenShaftCell(level.getBlockState(worldPos(mineSite.buildSite(), column, up)))) {
				return false;
			}
		}

		return true;
	}

	private static boolean requiresLadderSupportReplacement(BlockPos targetPos, MineSite mineSite, int bottomUp) {
		for (int up = mineSite.layout().starterMinUp(); up <= bottomUp; up++) {
			for (ShaftColumn ladderColumn : mineSite.layout().ladderColumns()) {
				BlockPos supportPos = worldPos(mineSite.buildSite(), ladderColumn, up).relative(mineSite.supportDirection());

				if (supportPos.equals(targetPos)) {
					return true;
				}
			}
		}

		return false;
	}

	private static boolean isRequiredLadderSupport(BlockPos targetPos, MineSite mineSite) {
		for (int up = mineSite.layout().starterMinUp() - MAX_SHAFT_DEPTH_BLOCKS; up <= -1; up++) {
			for (ShaftColumn ladderColumn : mineSite.layout().ladderColumns()) {
				if (worldPos(mineSite.buildSite(), ladderColumn, up).relative(mineSite.supportDirection()).equals(targetPos)) {
					return true;
				}
			}
		}

		return false;
	}

	private static boolean canSupplyNextLadderLevel(Map<String, Integer> stock) {
		Map<String, Integer> stockCopy = new HashMap<>(stock);
		return consumeLadder(stockCopy) && consumeLadder(stockCopy);
	}

	private static boolean shouldLightLevel(int up) {
		return up < 0 && Math.floorMod(Math.abs(up), SHAFT_LIGHT_SPACING_LEVELS) == 0;
	}

	private static boolean consumeLadder(Map<String, Integer> stock) {
		return SettlementConstructionMaterials.consumeMaterial(stock, new HashMap<>(), "ladder").supplied();
	}

	private static boolean consumeLightSource(Map<String, Integer> stock, BlockState lightState) {
		if (lightState.is(Blocks.COPPER_WALL_TORCH) || lightState.is(Blocks.COPPER_TORCH)) {
			if (SettlementGoods.consumeGoods(stock, "copper_torch", 1)) {
				return true;
			}

			if (SettlementGoods.consumeGoods(stock, "copper_ingot", 1)
				&& SettlementConstructionMaterials.consumeMaterial(stock, new HashMap<>(), "torch").supplied()) {
				return true;
			}

			return false;
		}

		return SettlementConstructionMaterials.consumeMaterial(stock, new HashMap<>(), "torch").supplied();
	}

	private static boolean consumeSupportMaterial(Map<String, Integer> stock, BlockState state) {
		if (state.is(Blocks.COBBLESTONE)) {
			return SettlementGoods.consumeGoods(stock, "cobblestone", 1);
		}

		if (state.is(Blocks.DIRT)) {
			return SettlementGoods.consumeGoods(stock, "dirt", 1);
		}

		return false;
	}

	private static BlockState supportReplacementMaterial(Map<String, Integer> stock) {
		if (stock.getOrDefault("cobblestone", 0) > 0) {
			return Blocks.COBBLESTONE.defaultBlockState();
		}

		if (stock.getOrDefault("dirt", 0) > 0) {
			return Blocks.DIRT.defaultBlockState();
		}

		return null;
	}

	private static boolean isUsefulOre(BlockState state) {
		return state.is(Blocks.COAL_ORE)
			|| state.is(Blocks.DEEPSLATE_COAL_ORE)
			|| state.is(Blocks.COPPER_ORE)
			|| state.is(Blocks.DEEPSLATE_COPPER_ORE)
			|| state.is(Blocks.IRON_ORE)
			|| state.is(Blocks.DEEPSLATE_IRON_ORE)
			|| state.is(Blocks.GOLD_ORE)
			|| state.is(Blocks.DEEPSLATE_GOLD_ORE)
			|| state.is(Blocks.REDSTONE_ORE)
			|| state.is(Blocks.DEEPSLATE_REDSTONE_ORE)
			|| state.is(Blocks.LAPIS_ORE)
			|| state.is(Blocks.DEEPSLATE_LAPIS_ORE)
			|| state.is(Blocks.DIAMOND_ORE)
			|| state.is(Blocks.DEEPSLATE_DIAMOND_ORE)
			|| state.is(Blocks.EMERALD_ORE)
			|| state.is(Blocks.DEEPSLATE_EMERALD_ORE);
	}

	private static boolean isUsefulStone(BlockState state) {
		return state.is(Blocks.STONE)
			|| state.is(Blocks.DEEPSLATE)
			|| state.is(Blocks.COBBLESTONE)
			|| state.is(Blocks.COBBLED_DEEPSLATE)
			|| state.is(Blocks.TUFF)
			|| state.is(Blocks.ANDESITE)
			|| state.is(Blocks.DIORITE)
			|| state.is(Blocks.GRANITE);
	}

	private static boolean isShaftLight(BlockState state) {
		return state.is(Blocks.WALL_TORCH) || state.is(Blocks.COPPER_WALL_TORCH);
	}

	private static boolean isCaveLight(BlockState state) {
		return state.is(Blocks.TORCH)
			|| state.is(Blocks.COPPER_TORCH)
			|| state.is(Blocks.WALL_TORCH)
			|| state.is(Blocks.COPPER_WALL_TORCH);
	}

	private static boolean isClearOpenShaftCell(BlockState state) {
		return state.isAir() || isShaftLight(state);
	}

	private static boolean isStableLadderSupport(BlockState state) {
		return !state.isAir() && state.isSolid();
	}

	private static boolean isStandableCaveCell(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).isAir()
			&& level.getBlockState(pos.above()).isAir()
			&& level.getBlockState(pos.below()).isSolid();
	}

	private static boolean hasNearbyCaveLight(ServerLevel level, BlockPos center, int radiusBlocks) {
		for (int x = center.getX() - radiusBlocks; x <= center.getX() + radiusBlocks; x++) {
			for (int y = center.getY() - 2; y <= center.getY() + 2; y++) {
				for (int z = center.getZ() - radiusBlocks; z <= center.getZ() + radiusBlocks; z++) {
					BlockPos scanPos = new BlockPos(x, y, z);

					if (scanPos.distSqr(center) <= radiusBlocks * radiusBlocks && isCaveLight(level.getBlockState(scanPos))) {
						return true;
					}
				}
			}
		}

		return false;
	}

	private static BlockState preferredShaftLightState(Map<String, Integer> stock, MineSite mineSite) {
		Direction torchFacing = mineSite.supportDirection();

		if (stock.getOrDefault("copper_torch", 0) > 0
			|| (stock.getOrDefault("copper_ingot", 0) > 0 && stock.getOrDefault("coal", 0) > 0 && stock.getOrDefault("stick", 0) > 0)) {
			return Blocks.COPPER_WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, torchFacing);
		}

		if (stock.getOrDefault("torch", 0) > 0
			|| (stock.getOrDefault("coal", 0) > 0 && stock.getOrDefault("stick", 0) > 0)) {
			return Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, torchFacing);
		}

		return null;
	}

	private static BlockState preferredCaveLightState(Map<String, Integer> stock) {
		if (stock.getOrDefault("copper_torch", 0) > 0
			|| (stock.getOrDefault("copper_ingot", 0) > 0 && stock.getOrDefault("coal", 0) > 0 && stock.getOrDefault("stick", 0) > 0)) {
			return Blocks.COPPER_TORCH.defaultBlockState();
		}

		if (stock.getOrDefault("torch", 0) > 0
			|| (stock.getOrDefault("coal", 0) > 0 && stock.getOrDefault("stick", 0) > 0)) {
			return Blocks.TORCH.defaultBlockState();
		}

		return null;
	}

	private static Optional<BlockPos> standPosFor(Villager miner, MineSite mineSite, int targetUp) {
		for (int up = targetUp; up <= -1; up++) {
			for (ShaftColumn ladderColumn : mineSite.layout().ladderColumns()) {
				BlockPos candidate = worldPos(mineSite.buildSite(), ladderColumn, up);
				if (levelHasLadder(miner, candidate)) {
					return Optional.of(candidate);
				}
			}
		}

		return mineSite.layout().ladderColumns().isEmpty()
			? Optional.empty()
			: Optional.of(worldPos(mineSite.buildSite(), mineSite.layout().ladderColumns().getFirst(), -1));
	}

	private static boolean levelHasLadder(Villager miner, BlockPos pos) {
		return miner.level().getBlockState(pos).is(Blocks.LADDER);
	}

	private static void moveMinerTowardTask(ServerLevel level, Villager miner, MineSite mineSite, MinerTask task) {
		if (tryAdvanceMinerDownShaft(level, miner, mineSite, task.standPos())) {
			return;
		}

		miner.getNavigation().moveTo(
			task.standPos().getX() + 0.5D,
			task.standPos().getY(),
			task.standPos().getZ() + 0.5D,
			MINING_WALK_SPEED
		);
	}

	private static void moveMinerTowardSurface(ServerLevel level, Villager miner, MineSite mineSite) {
		BlockPos currentPos = miner.blockPosition();
		BlockPos surfaceLadderPos = worldPos(mineSite.buildSite(), mineSite.layout().ladderColumns().getFirst(), -1);

		if (currentPos.getY() >= surfaceLadderPos.getY()) {
			return;
		}

		Optional<BlockPos> currentLadderPos = currentLadderPos(level, mineSite, currentPos);

		if (currentLadderPos.isPresent()) {
			BlockPos topLadderPos = topLadderPosForTarget(mineSite, currentLadderPos.get());

			if (topLadderPos != null && currentLadderPos.get().getY() < topLadderPos.getY()) {
				BlockPos nextPos = currentLadderPos.get().above();

				if (level.getBlockState(nextPos).is(Blocks.LADDER)) {
					miner.getNavigation().stop();
					miner.setPos(nextPos.getX() + 0.5D, nextPos.getY(), nextPos.getZ() + 0.5D);
					return;
				}
			}

			return;
		}

		Optional<BlockPos> nearestLadderPos = nearestExistingLadderPos(level, mineSite, currentPos);

		if (nearestLadderPos.isPresent()) {
			miner.getNavigation().moveTo(
				nearestLadderPos.get().getX() + 0.5D,
				nearestLadderPos.get().getY(),
				nearestLadderPos.get().getZ() + 0.5D,
				MINING_WALK_SPEED
			);
		}
	}

	private static boolean tryAdvanceMinerDownShaft(ServerLevel level, Villager miner, MineSite mineSite, BlockPos targetStandPos) {
		if (!level.getBlockState(targetStandPos).is(Blocks.LADDER)) {
			return false;
		}

		BlockPos currentPos = miner.blockPosition();
		BlockPos currentLadderPos = currentLadderPos(level, mineSite, currentPos).orElse(null);
		BlockPos topLadderPos = topLadderPosForTarget(mineSite, targetStandPos);

		if (currentLadderPos == null) {
			if (topLadderPos == null || miner.distanceToSqr(topLadderPos.getX() + 0.5D, topLadderPos.getY(), topLadderPos.getZ() + 0.5D) > SHAFT_ENTRY_SNAP_DISTANCE_SQUARED) {
				return false;
			}

			miner.getNavigation().stop();
			miner.setPos(topLadderPos.getX() + 0.5D, topLadderPos.getY(), topLadderPos.getZ() + 0.5D);
			return true;
		}

		if (currentLadderPos.getX() != targetStandPos.getX() || currentLadderPos.getZ() != targetStandPos.getZ()) {
			return false;
		}

		if (currentLadderPos.getY() <= targetStandPos.getY()) {
			return true;
		}

		BlockPos nextPos = currentLadderPos.below();

		if (!level.getBlockState(nextPos).is(Blocks.LADDER)) {
			return false;
		}

		miner.getNavigation().stop();
		miner.setPos(nextPos.getX() + 0.5D, nextPos.getY(), nextPos.getZ() + 0.5D);
		return true;
	}

	private static Optional<BlockPos> currentLadderPos(ServerLevel level, MineSite mineSite, BlockPos currentPos) {
		for (int up = mineSite.layout().starterMinUp() - MAX_SHAFT_DEPTH_BLOCKS; up <= -1; up++) {
			for (ShaftColumn ladderColumn : mineSite.layout().ladderColumns()) {
				BlockPos ladderPos = worldPos(mineSite.buildSite(), ladderColumn, up);

				if (ladderPos.getX() == currentPos.getX()
					&& ladderPos.getZ() == currentPos.getZ()
					&& Math.abs(ladderPos.getY() - currentPos.getY()) <= 1
					&& level.getBlockState(ladderPos).is(Blocks.LADDER)) {
					return Optional.of(ladderPos);
				}
			}
		}

		return Optional.empty();
	}

	private static BlockPos topLadderPosForTarget(MineSite mineSite, BlockPos targetStandPos) {
		for (ShaftColumn ladderColumn : mineSite.layout().ladderColumns()) {
			BlockPos candidate = worldPos(mineSite.buildSite(), ladderColumn, -1);

			if (candidate.getX() == targetStandPos.getX() && candidate.getZ() == targetStandPos.getZ()) {
				return candidate;
			}
		}

		return null;
	}

	private static Optional<BlockPos> nearestExistingLadderPos(ServerLevel level, MineSite mineSite, BlockPos currentPos) {
		BlockPos bestPos = null;
		double bestDistanceSquared = Double.POSITIVE_INFINITY;

		for (int up = mineSite.layout().starterMinUp() - MAX_SHAFT_DEPTH_BLOCKS; up <= -1; up++) {
			for (ShaftColumn ladderColumn : mineSite.layout().ladderColumns()) {
				BlockPos ladderPos = worldPos(mineSite.buildSite(), ladderColumn, up);

				if (!level.getBlockState(ladderPos).is(Blocks.LADDER)) {
					continue;
				}

				double distanceSquared = ladderPos.distSqr(currentPos);

				if (bestPos == null || distanceSquared < bestDistanceSquared) {
					bestPos = ladderPos.immutable();
					bestDistanceSquared = distanceSquared;
				}
			}
		}

		return Optional.ofNullable(bestPos);
	}

	private static Optional<BlockPos> adjacentCaveStandPos(BlockPos targetPos, List<BlockPos> standPositions) {
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos candidatePos = targetPos.relative(direction);

			for (BlockPos standPos : standPositions) {
				if (standPos.equals(candidatePos)) {
					return Optional.of(standPos);
				}
			}
		}

		return Optional.empty();
	}

	private static Optional<BlockPos> exposedOreFromStandPos(ServerLevel level, BlockPos standPos, Set<BlockPos> caveAirCells) {
		for (BlockPos inspectOrigin : List.of(standPos, standPos.above())) {
			for (Direction direction : Direction.values()) {
				BlockPos candidatePos = inspectOrigin.relative(direction);

				if (caveAirCells.contains(candidatePos)) {
					continue;
				}

				if (isUsefulOre(level.getBlockState(candidatePos))) {
					return Optional.of(candidatePos.immutable());
				}
			}
		}

		return Optional.empty();
	}

	private static boolean isVillageRestTime(ServerLevel level) {
		long dayTime = Math.floorMod(level.getOverworldClockTime(), DAY_TICKS);
		return dayTime >= VILLAGE_REST_START_TICK;
	}

	private static BlockState findTemplateLadderState(ServerLevel level, SettlementBuildSite buildSite, ShaftLayout layout) {
		for (SettlementBuildBlockState block : buildSite.blocks()) {
			if (!"R".equals(block.blueprintSymbol())) {
				continue;
			}

			BuildRelativePos relativePos = parseRelativePos(block.position());

			if (relativePos == null) {
				continue;
			}

			BlockPos ladderPos = worldPos(buildSite, new ShaftColumn(relativePos.right(), relativePos.forward()), relativePos.up());
			BlockState state = level.getBlockState(ladderPos);

			if (state.is(Blocks.LADDER)) {
				return state;
			}
		}

		return Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, buildSite.facing());
	}

	private static Direction supportDirectionFor(ServerLevel level, BlockPos ladderPos, BlockState ladderState) {
		Direction facing = ladderState.hasProperty(LadderBlock.FACING) ? ladderState.getValue(LadderBlock.FACING) : Direction.NORTH;

		for (Direction candidate : List.of(facing, facing.getOpposite())) {
			if (isStableLadderSupport(level.getBlockState(ladderPos.relative(candidate)))) {
				return candidate;
			}
		}

		for (Direction candidate : Direction.Plane.HORIZONTAL) {
			if (isStableLadderSupport(level.getBlockState(ladderPos.relative(candidate)))) {
				return candidate;
			}
		}

		return null;
	}

	private static ShaftLayout shaftLayout(SettlementBuildSite buildSite) {
		Set<ShaftColumn> ladderColumns = new LinkedHashSet<>();
		Set<ShaftColumn> openColumns = new LinkedHashSet<>();
		int starterMinUp = -1;

		for (SettlementBuildBlockState block : buildSite.blocks()) {
			if (block.blueprintSymbol().isBlank()) {
				continue;
			}

			BuildRelativePos relativePos = parseRelativePos(block.position());

			if (relativePos == null || relativePos.up() >= 0) {
				continue;
			}

			char symbol = block.blueprintSymbol().charAt(0);

			if (symbol == 'R') {
				ladderColumns.add(new ShaftColumn(relativePos.right(), relativePos.forward()));
				starterMinUp = Math.min(starterMinUp, relativePos.up());
			} else if (symbol == 'E') {
				openColumns.add(new ShaftColumn(relativePos.right(), relativePos.forward()));
				starterMinUp = Math.min(starterMinUp, relativePos.up());
			}
		}

		List<ShaftColumn> sortedLadders = ladderColumns.stream()
			.sorted(Comparator.comparingInt(ShaftColumn::forward).thenComparingInt(ShaftColumn::right))
			.toList();
		List<ShaftColumn> sortedOpens = openColumns.stream()
			.sorted(Comparator.comparingInt(ShaftColumn::forward).thenComparingInt(ShaftColumn::right))
			.toList();
		return new ShaftLayout(sortedLadders, sortedOpens, starterMinUp);
	}

	private static BlockPos worldPos(SettlementBuildSite buildSite, ShaftColumn column, int up) {
		return offset(buildSite.origin(), buildSite.facing(), column.right(), column.forward(), up);
	}

	private static BlockPos offset(BlockPos origin, Direction facing, int right, int forward, int up) {
		return origin.relative(facing.getClockWise(), right).relative(facing, forward).above(up);
	}

	private static void showMinerTool(Villager miner, MinerAction action) {
		ItemStack held = miner.getMainHandItem();

		if (!held.isEmpty()) {
			return;
		}

		miner.setItemSlot(
			EquipmentSlot.MAINHAND,
			new ItemStack(action == MinerAction.PLACE_LADDER ? Items.LADDER : Items.IRON_PICKAXE)
		);
	}

	private static boolean isWithinWorkReach(Villager miner, BlockPos workPos) {
		return miner.distanceToSqr(workPos.getX() + 0.5D, workPos.getY() + 0.5D, workPos.getZ() + 0.5D) <= MINING_WORK_REACH_DISTANCE_SQUARED;
	}

	private static BuildRelativePos parseRelativePos(String position) {
		String[] parts = position.split(",");

		if (parts.length != 3) {
			return null;
		}

		try {
			return new BuildRelativePos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	private enum MinerAction {
		DIG_SHAFT,
		MINE_ORE,
		MINE_STONE,
		PLACE_SUPPORT,
		PLACE_LADDER,
		PLACE_LIGHT
	}

	private record TimedTask(String taskKey, long tick) {
	}

	private record BuildRelativePos(int right, int forward, int up) {
	}

	private record ShaftColumn(int right, int forward) {
	}

	private record ShaftLayout(List<ShaftColumn> ladderColumns, List<ShaftColumn> openColumns, int starterMinUp) {
	}

	private record MineSite(
		SettlementBuildSite buildSite,
		ShaftLayout layout,
		BlockState ladderStateTemplate,
		Direction supportDirection
	) {
	}

	private record CavePocket(Set<BlockPos> airCells, List<BlockPos> standPositions, List<BlockPos> lightTargets) {
	}

	private record MinerTask(MinerAction action, BlockPos targetPos, BlockState replacementState, BlockPos standPos, String taskKey) {
	}
}
