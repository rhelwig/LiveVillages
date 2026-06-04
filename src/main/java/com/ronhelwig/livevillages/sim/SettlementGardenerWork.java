package com.ronhelwig.livevillages.sim;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import com.ronhelwig.livevillages.content.LiveVillagesItems;

public final class SettlementGardenerWork {
	private static final int BLOCK_UPDATE_FLAGS = 3;
	private static final double WORKSTATION_REACH_DISTANCE_SQUARED = 6.25D;
	private static final double GARDENER_WALK_SPEED = 0.75D;
	private static final long TASK_MEMORY_TICKS = 80L;
	private static final long GARDENER_DECIDE_INTERVAL_TICKS = 500L;
	private static final long GARDENER_WORK_INTERVAL_TICKS = 1_200L;
	private static final int GARDEN_SCAN_RADIUS = 8;
	private static final int CHICKEN_SCAN_RADIUS = 16;
	private static final int MAX_FLOWER_BEDS_PER_WORKSTATION = 6;
	private static final int CHICKEN_PEN_INTERIOR_SIZE = 3;
	private static final int CHICKEN_PEN_SEARCH_RADIUS = 10;
	private static final Map<String, TimedTask> ACTIVE_TASKS = new HashMap<>();
	private static final Map<String, Long> LAST_WORK_TICKS = new HashMap<>();

	private SettlementGardenerWork() {
	}

	public static boolean maintainLoadedGardening(ServerLevel level, SettlementState settlement, Map<String, Integer> stock) {
		List<Villager> gardeners = SettlementVillagers.nearbyGardeners(level, settlement);

		if (gardeners.isEmpty()) {
			SettlementProfessionDiagnostics.log(level, settlement, SettlementRoleKeys.GARDENER, "no_gardeners", "");
			return false;
		}

		List<BlockPos> workstations = SettlementConstruction.findPlacedGardenerWorkstations(level, settlement);

		if (workstations.isEmpty()) {
			SettlementProfessionDiagnostics.log(level, settlement, SettlementRoleKeys.GARDENER, "no_workstations", "");
			return false;
		}

		boolean stockChanged = false;
		long tick = level.getServer().getTickCount();
		Map<BlockPos, GardenerWorkPlan> workPlans = new HashMap<>();

		for (Villager gardener : gardeners) {
			showGardenerTool(gardener);

			if (!SettlementVillagerWorkSchedule.shouldStartNewWork(level, gardener, "gardening", GARDENER_DECIDE_INTERVAL_TICKS)) {
				continue;
			}

			BlockPos workstation = nearestWorkstation(gardener.blockPosition(), workstations);

			if (workstation == null) {
				continue;
			}

			GardenerWorkPlan workPlan = workPlans.computeIfAbsent(workstation, pos -> workPlanFor(level, stock, pos));
			String taskKey = workPlan.taskKey();
			BlockPos workPos = workPosForPlan(level, gardener, workstation, workPlan);
			ACTIVE_TASKS.put(gardener.getUUID().toString(), new TimedTask(taskKey, tick));

			if (gardener.blockPosition().distSqr(workPos) > WORKSTATION_REACH_DISTANCE_SQUARED) {
				SettlementNavigation.moveToRoutineTarget(level, settlement, gardener, workPos, GARDENER_WALK_SPEED);
				continue;
			}

			if (!workCooldownReady(gardener, tick)) {
				continue;
			}

			boolean worked = performGardenerTask(level, settlement, stock, gardener, workstation, taskKey);
			workPlans.remove(workstation);

			if (worked) {
				LAST_WORK_TICKS.put(gardener.getUUID().toString(), tick);
				stockChanged = true;
			}
		}

		return stockChanged;
	}

	public static Optional<String> loadedGardenerTaskKey(ServerLevel level, Villager villager) {
		TimedTask task = ACTIVE_TASKS.get(villager.getUUID().toString());

		if (task == null || level.getServer().getTickCount() - task.tick() > TASK_MEMORY_TICKS) {
			return Optional.empty();
		}

		return Optional.of(task.taskKey());
	}

	private static boolean performGardenerTask(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Villager gardener,
		BlockPos workstation,
		String taskKey
	) {
		if (taskKey.equals("tending_flower_beds") && placeFlowerBed(level, settlement, stock, gardener, workstation)) {
			return true;
		}

		if (taskKey.equals("feeding_chickens")) {
			return feedNearbyChicken(level, settlement, stock, gardener);
		}

		if (taskKey.equals("building_chicken_pen")) {
			return buildChickenPenBlock(level, settlement, stock, gardener, workstation);
		}

		if (taskKey.equals("herding_chickens")) {
			return herdChickenToPen(level, settlement, stock, gardener, workstation);
		}

		if (SettlementGoods.consumeGoods(stock, "bone_meal", 1)) {
			SettlementGoods.addGoods(stock, "wheat_seeds", 1);
			SettlementProfessionReports.recordConsumed(level, settlement, SettlementRoleKeys.GARDENER, gardener, "bone_meal", 1);
			SettlementProfessionReports.recordProduced(level, settlement, SettlementRoleKeys.GARDENER, gardener, "wheat_seeds", 1);
			SettlementProfessionReports.recordAccomplished(level, settlement, SettlementRoleKeys.GARDENER, gardener, "tended flower beds");
			return true;
		}

		return false;
	}

	private static GardenerWorkPlan workPlanFor(ServerLevel level, Map<String, Integer> stock, BlockPos workstation) {
		Optional<Animal> chicken = nearestChicken(level, workstation);
		Optional<ChickenPenPlan> chickenPenPlan = chickenPenPlan(level, workstation);

		if (chicken.isPresent() && chickenPenPlan.isPresent() && !isChickenPenBuilt(level, chickenPenPlan.get())) {
			ChickenPenBlock missingBlock = nearestMissingChickenPenBlock(level, chickenPenPlan.get(), workstation);

			if (missingBlock != null && canSupplyChickenPenMaterial(stock, missingBlock.materialKey())) {
				return new GardenerWorkPlan("building_chicken_pen", workstation, chickenPenPlan.get());
			}
		}

		if (stock.getOrDefault("wheat_seeds", 0) > 0
			&& chickenPenPlan.isPresent()
			&& isChickenPenBuilt(level, chickenPenPlan.get())) {
			Optional<Animal> strayChicken = nearestStrayChicken(level, workstation, chickenPenPlan.get());

			if (strayChicken.isPresent()) {
				return new GardenerWorkPlan("herding_chickens", strayChicken.get().blockPosition(), chickenPenPlan.get());
			}
		}

		if (stock.getOrDefault("dirt", 0) > 0
			&& stock.getOrDefault("bone_meal", 0) > 0
			&& nearbyFlowerBeds(level, workstation) < MAX_FLOWER_BEDS_PER_WORKSTATION) {
			return new GardenerWorkPlan("tending_flower_beds", flowerBedPlacement(level, workstation).orElse(workstation), null);
		}

		if (stock.getOrDefault("wheat_seeds", 0) > 0 && chicken.isPresent()) {
			return new GardenerWorkPlan("feeding_chickens", chicken.get().blockPosition(), null);
		}

		return new GardenerWorkPlan("tending_flower_beds", workstation, null);
	}

	private static BlockPos workPosForPlan(ServerLevel level, Villager gardener, BlockPos workstation, GardenerWorkPlan workPlan) {
		if (workPlan.taskKey().equals("building_chicken_pen") && workPlan.chickenPenPlan() != null) {
			ChickenPenBlock missingBlock = nearestMissingChickenPenBlock(level, workPlan.chickenPenPlan(), gardener.blockPosition());
			return missingBlock == null ? workstation : missingBlock.pos();
		}

		return workPlan.workPos();
	}

	private static boolean feedNearbyChicken(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, Villager gardener) {
		Optional<Animal> targetChicken = nearestChicken(level, gardener.blockPosition());

		if (targetChicken.isEmpty()
			|| targetChicken.get().blockPosition().distSqr(gardener.blockPosition()) > WORKSTATION_REACH_DISTANCE_SQUARED
			|| !SettlementGoods.consumeGoods(stock, "wheat_seeds", 1)) {
			return false;
		}

		if (targetChicken.get().canFallInLove()) {
			targetChicken.get().setInLove(null);
		}

		SettlementGoods.addGoods(stock, "egg", 1);
		SettlementProfessionReports.recordConsumed(level, settlement, SettlementRoleKeys.GARDENER, gardener, "wheat_seeds", 1);
		SettlementProfessionReports.recordProduced(level, settlement, SettlementRoleKeys.GARDENER, gardener, "egg", 1);
		SettlementProfessionReports.recordAccomplished(level, settlement, SettlementRoleKeys.GARDENER, gardener, "fed chickens");
		return true;
	}

	private static Optional<Animal> nearestChicken(ServerLevel level, BlockPos origin) {
		double radiusSqr = CHICKEN_SCAN_RADIUS * CHICKEN_SCAN_RADIUS;
		AABB bounds = new AABB(origin).inflate(CHICKEN_SCAN_RADIUS);
		Animal bestChicken = null;
		double bestDistance = Double.MAX_VALUE;

		for (Animal chicken : level.getEntities(
			EntityTypeTest.forClass(Animal.class),
			bounds,
			chicken -> chicken.getType() == EntityType.CHICKEN
				&& !chicken.isRemoved()
				&& chicken.isAlive()
				&& !chicken.isBaby()
				&& chicken.blockPosition().distSqr(origin) <= radiusSqr
		)) {
			double distance = chicken.distanceToSqr(origin.getX() + 0.5D, origin.getY() + 0.5D, origin.getZ() + 0.5D);

			if (distance < bestDistance) {
				bestDistance = distance;
				bestChicken = chicken;
			}
		}

		return Optional.ofNullable(bestChicken);
	}

	private static Optional<Animal> nearestStrayChicken(ServerLevel level, BlockPos origin, ChickenPenPlan penPlan) {
		double radiusSqr = CHICKEN_SCAN_RADIUS * CHICKEN_SCAN_RADIUS;
		AABB bounds = new AABB(origin).inflate(CHICKEN_SCAN_RADIUS);
		Animal bestChicken = null;
		double bestDistance = Double.MAX_VALUE;

		for (Animal chicken : level.getEntities(
			EntityTypeTest.forClass(Animal.class),
			bounds,
			animal -> animal.getType() == EntityType.CHICKEN
				&& !animal.isRemoved()
				&& animal.isAlive()
				&& !animal.isBaby()
				&& !penPlan.contains(animal.blockPosition())
				&& animal.blockPosition().distSqr(origin) <= radiusSqr
		)) {
			double distance = chicken.distanceToSqr(origin.getX() + 0.5D, origin.getY() + 0.5D, origin.getZ() + 0.5D);

			if (distance < bestDistance) {
				bestDistance = distance;
				bestChicken = chicken;
			}
		}

		return Optional.ofNullable(bestChicken);
	}

	private static boolean buildChickenPenBlock(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Villager gardener,
		BlockPos workstation
	) {
		Optional<ChickenPenPlan> plan = chickenPenPlan(level, workstation);

		if (plan.isEmpty() || isChickenPenBuilt(level, plan.get())) {
			return false;
		}

		ChickenPenBlock missingBlock = nearestMissingChickenPenBlock(level, plan.get(), gardener.blockPosition());

		if (missingBlock == null || !canSupplyChickenPenMaterial(stock, missingBlock.materialKey())) {
			return false;
		}

		BlockState currentState = level.getBlockState(missingBlock.pos());

		if (!canReplaceForChickenPen(currentState)) {
			return false;
		}

		Map<String, Integer> beforeStock = new HashMap<>(stock);
		SettlementConstructionMaterials.ConstructionMaterialResult materialResult = SettlementConstructionMaterials.consumeMaterial(
			stock,
			new LinkedHashMap<>(),
			missingBlock.materialKey()
		);

		if (!materialResult.supplied()) {
			return false;
		}

		level.setBlock(missingBlock.pos(), chickenPenState(missingBlock), BLOCK_UPDATE_FLAGS);
		SettlementProfessionReports.recordConsumedDeltas(level, settlement, SettlementRoleKeys.GARDENER, gardener, beforeStock, stock);
		SettlementProfessionReports.recordAccomplished(level, settlement, SettlementRoleKeys.GARDENER, gardener, "built chicken pen");
		return true;
	}

	private static boolean herdChickenToPen(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Villager gardener,
		BlockPos workstation
	) {
		Optional<ChickenPenPlan> plan = chickenPenPlan(level, workstation);

		if (plan.isEmpty() || !isChickenPenBuilt(level, plan.get()) || stock.getOrDefault("wheat_seeds", 0) <= 0) {
			return false;
		}

		Optional<Animal> chicken = nearestStrayChicken(level, workstation, plan.get());

		if (chicken.isEmpty()
			|| chicken.get().blockPosition().distSqr(gardener.blockPosition()) > WORKSTATION_REACH_DISTANCE_SQUARED) {
			return false;
		}

		gardener.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WHEAT_SEEDS));
		BlockPos target = plan.get().center();
		chicken.get().getNavigation().moveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, 1.05D);
		SettlementProfessionReports.recordAccomplished(level, settlement, SettlementRoleKeys.GARDENER, gardener, "herded chickens");
		return true;
	}

	private static Optional<ChickenPenPlan> chickenPenPlan(ServerLevel level, BlockPos workstation) {
		Optional<ChickenPenPlan> existingPen = existingChickenPenPlan(level, workstation);

		if (existingPen.isPresent()) {
			return existingPen;
		}

		ChickenPenPlan bestPlan = null;
		int bestScore = Integer.MIN_VALUE;

		for (int radius = 4; radius <= CHICKEN_PEN_SEARCH_RADIUS; radius += 2) {
			for (int dx = -radius; dx <= radius; dx += 2) {
				for (int dz = -radius; dz <= radius; dz += 2) {
					if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
						continue;
					}

					int x = workstation.getX() + dx;
					int z = workstation.getZ() + dz;
					int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
					ChickenPenPlan plan = ChickenPenPlan.create(new BlockPos(x, y, z), CHICKEN_PEN_INTERIOR_SIZE);

					if (isValidChickenPenPlan(level, plan)) {
						int score = chickenPenPlacementScore(level, workstation, plan);

						if (score > bestScore) {
							bestScore = score;
							bestPlan = plan;
						}
					}
				}
			}
		}

		return Optional.ofNullable(bestPlan);
	}

	private static Optional<ChickenPenPlan> existingChickenPenPlan(ServerLevel level, BlockPos workstation) {
		ChickenPenPlan bestPlan = null;
		int bestScore = Integer.MIN_VALUE;

		for (int radius = 4; radius <= CHICKEN_PEN_SEARCH_RADIUS; radius += 2) {
			for (int dx = -radius; dx <= radius; dx += 2) {
				for (int dz = -radius; dz <= radius; dz += 2) {
					if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
						continue;
					}

					int x = workstation.getX() + dx;
					int z = workstation.getZ() + dz;
					int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
					ChickenPenPlan plan = ChickenPenPlan.create(new BlockPos(x, y, z), CHICKEN_PEN_INTERIOR_SIZE);
					int matchingBlocks = matchingChickenPenBlocks(level, plan);

					if (matchingBlocks <= 0 || !isValidChickenPenPlan(level, plan)) {
						continue;
					}

					int score = matchingBlocks * 100 + chickenPenPlacementScore(level, workstation, plan);

					if (score > bestScore) {
						bestScore = score;
						bestPlan = plan;
					}
				}
			}
		}

		return Optional.ofNullable(bestPlan);
	}

	private static int chickenPenPlacementScore(ServerLevel level, BlockPos workstation, ChickenPenPlan plan) {
		int distance = Math.abs(workstation.getX() - plan.center().getX()) + Math.abs(workstation.getZ() - plan.center().getZ());
		int score = CHICKEN_PEN_SEARCH_RADIUS * 2 - distance;

		Optional<Animal> chicken = nearestChicken(level, workstation);

		if (chicken.isPresent()) {
			int chickenDistance = Math.abs(chicken.get().blockPosition().getX() - plan.center().getX())
				+ Math.abs(chicken.get().blockPosition().getZ() - plan.center().getZ());
			score += Math.max(0, CHICKEN_SCAN_RADIUS - chickenDistance);
		}

		if (nearBlock(level, plan.center(), 3, SettlementGardenerWork::isPathOrRoadSurface)) {
			score += 6;
		}

		if (nearBlock(level, plan.center(), 4, SettlementGardenerWork::isWindowBlock)) {
			score += 4;
		}

		return score;
	}

	private static int matchingChickenPenBlocks(ServerLevel level, ChickenPenPlan plan) {
		int matchingBlocks = 0;

		for (ChickenPenBlock block : plan.boundaryBlocks()) {
			if (isMatchingChickenPenMaterial(level.getBlockState(block.pos()), block.materialKey())) {
				matchingBlocks++;
			}
		}

		return matchingBlocks;
	}

	private static boolean isValidChickenPenPlan(ServerLevel level, ChickenPenPlan plan) {
		for (ChickenPenBlock block : plan.boundaryBlocks()) {
			BlockState state = level.getBlockState(block.pos());

			if (!level.hasChunkAt(block.pos())
				|| !level.getBlockState(block.pos().below()).isSolid()
				|| isPathOrRoadSurface(level.getBlockState(block.pos().below()))
				|| (!isMatchingChickenPenMaterial(state, block.materialKey()) && !canReplaceForChickenPen(state))) {
				return false;
			}
		}

		for (BlockPos pos : plan.interiorBlocks()) {
			if (!level.hasChunkAt(pos)
				|| !level.getBlockState(pos.below()).isSolid()
				|| isPathOrRoadSurface(level.getBlockState(pos.below()))
				|| !SettlementConstruction.isBuildSiteReplaceable(level.getBlockState(pos))
				|| !SettlementConstruction.isBuildSiteReplaceable(level.getBlockState(pos.above()))) {
				return false;
			}
		}

		return true;
	}

	private static boolean isChickenPenBuilt(ServerLevel level, ChickenPenPlan plan) {
		for (ChickenPenBlock block : plan.boundaryBlocks()) {
			if (!isMatchingChickenPenMaterial(level.getBlockState(block.pos()), block.materialKey())) {
				return false;
			}
		}

		return true;
	}

	private static ChickenPenBlock nearestMissingChickenPenBlock(ServerLevel level, ChickenPenPlan plan, BlockPos origin) {
		return plan.boundaryBlocks().stream()
			.filter(block -> !isMatchingChickenPenMaterial(level.getBlockState(block.pos()), block.materialKey()))
			.min(java.util.Comparator.comparingDouble(block -> block.pos().distSqr(origin)))
			.orElse(null);
	}

	private static boolean canSupplyChickenPenMaterial(Map<String, Integer> stock, String materialKey) {
		return SettlementConstructionMaterials.canSupplyBlock(stock, SettlementBuildBlockState.pending("", '?', materialKey)).supplied();
	}

	private static boolean canReplaceForChickenPen(BlockState state) {
		return SettlementConstruction.isBuildSiteReplaceable(state)
			&& !isFunctionalSurface(state)
			&& !isPathOrRoadSurface(state);
	}

	private static boolean isMatchingChickenPenMaterial(BlockState state, String materialKey) {
		return switch (materialKey) {
			case "fence" -> state.is(BlockTags.WOODEN_FENCES);
			case "fence_gate" -> state.is(BlockTags.FENCE_GATES);
			default -> false;
		};
	}

	private static BlockState chickenPenState(ChickenPenBlock block) {
		return switch (block.materialKey()) {
			case "fence_gate" -> Blocks.OAK_FENCE_GATE.defaultBlockState()
				.setValue(FenceGateBlock.FACING, Direction.SOUTH)
				.setValue(FenceGateBlock.OPEN, true);
			case "fence" -> Blocks.OAK_FENCE.defaultBlockState();
			default -> Blocks.AIR.defaultBlockState();
		};
	}

	private static boolean placeFlowerBed(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Villager gardener,
		BlockPos workstation
	) {
		if (stock.getOrDefault("dirt", 0) <= 0 || stock.getOrDefault("bone_meal", 0) <= 0) {
			return false;
		}

		Map<String, Integer> beforeStock = new HashMap<>(stock);
		Optional<BlockPos> bedPos = flowerBedPlacement(level, workstation);

		if (bedPos.isEmpty()) {
			return false;
		}

		BlockState flowerState = flowerStateFor(level.getServer().getTickCount(), bedPos.get());
		BlockPos flowerPos = bedPos.get().above();
		level.setBlock(bedPos.get(), Blocks.DIRT.defaultBlockState(), BLOCK_UPDATE_FLAGS);

		if (!flowerState.canSurvive(level, flowerPos)) {
			level.setBlock(bedPos.get(), Blocks.AIR.defaultBlockState(), BLOCK_UPDATE_FLAGS);
			return false;
		}

		level.setBlock(flowerPos, flowerState, BLOCK_UPDATE_FLAGS);
		if (!SettlementGoods.consumeGoods(stock, "dirt", 1) || !SettlementGoods.consumeGoods(stock, "bone_meal", 1)) {
			level.setBlock(flowerPos, Blocks.AIR.defaultBlockState(), BLOCK_UPDATE_FLAGS);
			level.setBlock(bedPos.get(), Blocks.AIR.defaultBlockState(), BLOCK_UPDATE_FLAGS);
			return false;
		}

		int edgedSides = placeFlowerBedEdging(level, stock, bedPos.get());
		SettlementProfessionReports.recordConsumedDeltas(level, settlement, SettlementRoleKeys.GARDENER, gardener, beforeStock, stock);
		SettlementProfessionReports.recordAccomplished(
			level,
			settlement,
			SettlementRoleKeys.GARDENER,
			gardener,
			edgedSides > 0 ? "planted raised flower bed with trapdoor edging" : "planted raised flower bed"
		);
		return true;
	}

	private static int placeFlowerBedEdging(ServerLevel level, Map<String, Integer> stock, BlockPos bedPos) {
		int placed = 0;

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos trimPos = bedPos.relative(direction);
			BlockState trapdoorState = Blocks.OAK_TRAPDOOR.defaultBlockState()
				.setValue(TrapDoorBlock.FACING, direction.getOpposite())
				.setValue(TrapDoorBlock.HALF, Half.BOTTOM)
				.setValue(TrapDoorBlock.OPEN, true);

			if (!canPlaceFlowerBedEdgingAt(level, trimPos, trapdoorState)) {
				continue;
			}

			SettlementConstructionMaterials.ConstructionMaterialResult materialResult = SettlementConstructionMaterials.consumeMaterial(
				stock,
				new LinkedHashMap<>(),
				"trapdoor"
			);

			if (!materialResult.supplied()) {
				continue;
			}

			level.setBlock(trimPos, trapdoorState, BLOCK_UPDATE_FLAGS);
			placed++;
		}

		return placed;
	}

	private static boolean canPlaceFlowerBedEdgingAt(ServerLevel level, BlockPos trimPos, BlockState trapdoorState) {
		if (!level.hasChunkAt(trimPos)) {
			return false;
		}

		BlockState currentState = level.getBlockState(trimPos);
		BlockState belowState = level.getBlockState(trimPos.below());

		return canReplaceForGardenTrim(currentState)
			&& !isPathOrRoadSurface(belowState)
			&& !isFunctionalSurface(currentState)
			&& !touchesDoor(level, trimPos)
			&& trapdoorState.canSurvive(level, trimPos);
	}

	private static Optional<BlockPos> flowerBedPlacement(ServerLevel level, BlockPos origin) {
		BlockPos bestPos = null;
		int bestScore = Integer.MIN_VALUE;

		for (int radius = 2; radius <= GARDEN_SCAN_RADIUS; radius++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
						continue;
					}

					BlockPos candidate = origin.offset(dx, 0, dz);
					Optional<BlockPos> bedPos = supportedRaisedBedPos(level, candidate);

					if (bedPos.isPresent()) {
						int score = flowerBedPlacementScore(level, origin, bedPos.get());

						if (score > bestScore) {
							bestScore = score;
							bestPos = bedPos.get();
						}
					}
				}
			}
		}

		return Optional.ofNullable(bestPos);
	}

	private static Optional<BlockPos> supportedRaisedBedPos(ServerLevel level, BlockPos candidate) {
		for (int dy = 1; dy >= -2; dy--) {
			BlockPos bedPos = candidate.offset(0, dy, 0);
			BlockPos flowerPos = bedPos.above();
			BlockState bedState = level.getBlockState(bedPos);
			BlockState flowerState = level.getBlockState(flowerPos);
			BlockState supportState = level.getBlockState(bedPos.below());

			if (!canReplaceForGardenBed(bedState)
				|| !canReplaceForGardenFlower(flowerState)
				|| !isGardenSupport(supportState)
				|| touchesDoor(level, bedPos)) {
				continue;
			}

			return Optional.of(bedPos);
		}

		return Optional.empty();
	}

	private static int flowerBedPlacementScore(ServerLevel level, BlockPos origin, BlockPos bedPos) {
		int distance = Math.abs(origin.getX() - bedPos.getX()) + Math.abs(origin.getZ() - bedPos.getZ());
		int score = GARDEN_SCAN_RADIUS * 2 - distance;

		if (nearBlock(level, bedPos, 2, SettlementGardenerWork::isPathOrRoadSurface)) {
			score += 12;
		}

		if (nearBlock(level, bedPos, 3, SettlementGardenerWork::isWindowBlock)) {
			score += 9;
		}

		if (isUnderWindow(level, bedPos)) {
			score += 18;
		}

		if (isBesideExteriorWindow(level, bedPos)) {
			score += 14;
		}

		if (isBesideExistingWall(level, bedPos)) {
			score += 8;
		}

		if (nearExteriorDoor(level, bedPos)) {
			score += 5;
		}

		if (nearBlock(level, bedPos, 3, SettlementGardenerWork::isPointOfInterestBlock)) {
			score += 7;
		}

		return score;
	}

	private static boolean isUnderWindow(ServerLevel level, BlockPos bedPos) {
		BlockPos flowerPos = bedPos.above();

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos windowPos = flowerPos.relative(direction);

			if (isWindowBlock(level.getBlockState(windowPos))
				&& isExistingWallBlock(level.getBlockState(windowPos.below()))) {
				return true;
			}
		}

		return false;
	}

	private static boolean isBesideExteriorWindow(ServerLevel level, BlockPos bedPos) {
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos wallPos = bedPos.relative(direction);

			if ((isWindowBlock(level.getBlockState(wallPos.above())) || isWindowBlock(level.getBlockState(wallPos.above(2))))
				&& isExistingWallBlock(level.getBlockState(wallPos))) {
				return true;
			}
		}

		return false;
	}

	private static boolean isBesideExistingWall(ServerLevel level, BlockPos bedPos) {
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos wallPos = bedPos.relative(direction);

			if (isExistingWallBlock(level.getBlockState(wallPos))
				&& isExistingWallBlock(level.getBlockState(wallPos.above()))) {
				return true;
			}
		}

		return false;
	}

	private static boolean nearExteriorDoor(ServerLevel level, BlockPos bedPos) {
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (Math.abs(dx) + Math.abs(dz) > 3) {
					continue;
				}

				BlockPos scanPos = bedPos.offset(dx, 0, dz);

				for (int dy = 0; dy <= 1; dy++) {
					if (level.getBlockState(scanPos.above(dy)).getBlock() instanceof DoorBlock) {
						return true;
					}
				}
			}
		}

		return false;
	}

	private static boolean nearBlock(ServerLevel level, BlockPos origin, int radius, java.util.function.Predicate<BlockState> matcher) {
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				for (int dy = -1; dy <= 2; dy++) {
					if (matcher.test(level.getBlockState(origin.offset(dx, dy, dz)))) {
						return true;
					}
				}
			}
		}

		return false;
	}

	private static boolean touchesDoor(ServerLevel level, BlockPos bedPos) {
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockState state = level.getBlockState(bedPos.relative(direction));

			if (state.getBlock() instanceof DoorBlock) {
				return true;
			}
		}

		return false;
	}

	private static int nearbyFlowerBeds(ServerLevel level, BlockPos workstation) {
		int count = 0;

		for (int dx = -GARDEN_SCAN_RADIUS; dx <= GARDEN_SCAN_RADIUS; dx++) {
			for (int dz = -GARDEN_SCAN_RADIUS; dz <= GARDEN_SCAN_RADIUS; dz++) {
				for (int dy = -1; dy <= 2; dy++) {
					BlockPos flowerPos = workstation.offset(dx, dy, dz);
					BlockState state = level.getBlockState(flowerPos);

					if (state.is(BlockTags.FLOWERS) && level.getBlockState(flowerPos.below()).is(Blocks.DIRT)) {
						count++;
					}
				}
			}
		}

		return count;
	}

	private static boolean canReplaceForGardenBed(BlockState state) {
		return state.isAir()
			|| state.is(Blocks.SHORT_GRASS)
			|| state.is(Blocks.TALL_GRASS)
			|| state.is(BlockTags.FLOWERS);
	}

	private static boolean canReplaceForGardenFlower(BlockState state) {
		return state.isAir()
			|| state.is(Blocks.SHORT_GRASS)
			|| state.is(Blocks.TALL_GRASS)
			|| state.is(BlockTags.FLOWERS);
	}

	private static boolean canReplaceForGardenTrim(BlockState state) {
		return state.isAir()
			|| state.is(Blocks.SHORT_GRASS)
			|| state.is(Blocks.TALL_GRASS)
			|| state.is(BlockTags.FLOWERS);
	}

	private static boolean isGardenSupport(BlockState state) {
		return state.is(BlockTags.DIRT)
			|| state.is(Blocks.MOSS_BLOCK)
			|| state.is(Blocks.ROOTED_DIRT)
			|| state.is(Blocks.COARSE_DIRT);
	}

	private static boolean isPathOrRoadSurface(BlockState state) {
		return state.is(Blocks.DIRT_PATH)
			|| state.is(Blocks.COBBLESTONE)
			|| state.is(Blocks.STONE_BRICKS)
			|| state.is(Blocks.BRICKS)
			|| state.is(Blocks.SMOOTH_STONE);
	}

	private static boolean isWindowBlock(BlockState state) {
		return state.is(Blocks.GLASS)
			|| state.is(Blocks.GLASS_PANE);
	}

	private static boolean isExistingWallBlock(BlockState state) {
		return state.isSolid()
			&& !state.is(BlockTags.DIRT)
			&& !state.is(BlockTags.LEAVES)
			&& !state.is(BlockTags.LOGS)
			&& !state.is(Blocks.DIRT_PATH)
			&& !state.is(Blocks.FARMLAND)
			&& !state.is(Blocks.GRASS_BLOCK)
			&& !state.is(Blocks.SAND)
			&& !state.is(Blocks.GRAVEL);
	}

	private static boolean isPointOfInterestBlock(BlockState state) {
		return state.is(Blocks.BELL)
			|| state.is(Blocks.BREWING_STAND)
			|| state.is(Blocks.CARTOGRAPHY_TABLE)
			|| state.is(Blocks.COMPOSTER)
			|| state.is(Blocks.FLETCHING_TABLE)
			|| state.is(Blocks.LECTERN)
			|| state.is(Blocks.LOOM)
			|| state.is(Blocks.SMOKER)
			|| state.is(Blocks.STONECUTTER);
	}

	private static boolean isFunctionalSurface(BlockState state) {
		return state.getBlock() instanceof DoorBlock
			|| state.is(Blocks.BELL)
			|| state.is(Blocks.BARREL)
			|| state.is(Blocks.CHEST)
			|| state.is(Blocks.FARMLAND)
			|| state.is(Blocks.WHEAT)
			|| state.is(Blocks.CARROTS)
			|| state.is(Blocks.POTATOES)
			|| state.is(Blocks.BEETROOTS)
			|| isPathOrRoadSurface(state)
			|| isPointOfInterestBlock(state);
	}

	private static BlockState flowerStateFor(long tick, BlockPos pos) {
		int index = Math.floorMod((int) (tick + pos.getX() * 31L + pos.getZ() * 17L), 4);
		return switch (index) {
			case 0 -> Blocks.DANDELION.defaultBlockState();
			case 1 -> Blocks.POPPY.defaultBlockState();
			case 2 -> Blocks.AZURE_BLUET.defaultBlockState();
			default -> Blocks.OXEYE_DAISY.defaultBlockState();
		};
	}

	private static boolean workCooldownReady(Villager gardener, long tick) {
		Long lastWorkTick = LAST_WORK_TICKS.get(gardener.getUUID().toString());
		return lastWorkTick == null || tick - lastWorkTick >= GARDENER_WORK_INTERVAL_TICKS;
	}

	private static BlockPos nearestWorkstation(BlockPos origin, List<BlockPos> workstations) {
		BlockPos bestPos = null;
		double bestDistance = Double.MAX_VALUE;

		for (BlockPos workstation : workstations) {
			double distance = workstation.distSqr(origin);

			if (distance < bestDistance) {
				bestDistance = distance;
				bestPos = workstation;
			}
		}

		return bestPos;
	}

	private static void showGardenerTool(Villager gardener) {
		if (gardener.getMainHandItem().isEmpty()) {
			gardener.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(LiveVillagesItems.SLING));
		}
	}

	private record TimedTask(String taskKey, long tick) {
	}

	private record GardenerWorkPlan(String taskKey, BlockPos workPos, ChickenPenPlan chickenPenPlan) {
	}

	private record ChickenPenBlock(BlockPos pos, String materialKey) {
	}

	private record ChickenPenPlan(BlockPos center, int interiorSize, List<ChickenPenBlock> boundaryBlocks, List<BlockPos> interiorBlocks) {
		private static ChickenPenPlan create(BlockPos center, int interiorSize) {
			int halfInterior = Math.max(1, interiorSize / 2);
			int minX = center.getX() - halfInterior - 1;
			int maxX = center.getX() + halfInterior + 1;
			int minZ = center.getZ() - halfInterior - 1;
			int maxZ = center.getZ() + halfInterior + 1;
			List<ChickenPenBlock> boundaryBlocks = new java.util.ArrayList<>();
			List<BlockPos> interiorBlocks = new java.util.ArrayList<>();

			for (int x = minX; x <= maxX; x++) {
				boundaryBlocks.add(new ChickenPenBlock(new BlockPos(x, center.getY(), minZ), x == center.getX() ? "fence_gate" : "fence"));
				boundaryBlocks.add(new ChickenPenBlock(new BlockPos(x, center.getY(), maxZ), "fence"));
			}

			for (int z = minZ + 1; z < maxZ; z++) {
				boundaryBlocks.add(new ChickenPenBlock(new BlockPos(minX, center.getY(), z), "fence"));
				boundaryBlocks.add(new ChickenPenBlock(new BlockPos(maxX, center.getY(), z), "fence"));
			}

			for (int x = center.getX() - halfInterior; x <= center.getX() + halfInterior; x++) {
				for (int z = center.getZ() - halfInterior; z <= center.getZ() + halfInterior; z++) {
					interiorBlocks.add(new BlockPos(x, center.getY(), z));
				}
			}

			return new ChickenPenPlan(center.immutable(), interiorSize, List.copyOf(boundaryBlocks), List.copyOf(interiorBlocks));
		}

		private boolean contains(BlockPos pos) {
			int halfInterior = Math.max(1, interiorSize / 2);
			return pos.getY() == center.getY()
				&& pos.getX() >= center.getX() - halfInterior
				&& pos.getX() <= center.getX() + halfInterior
				&& pos.getZ() >= center.getZ() - halfInterior
				&& pos.getZ() <= center.getZ() + halfInterior;
		}
	}
}
