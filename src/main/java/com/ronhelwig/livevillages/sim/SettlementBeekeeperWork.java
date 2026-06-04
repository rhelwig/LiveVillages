package com.ronhelwig.livevillages.sim;

import java.util.HashMap;
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
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

public final class SettlementBeekeeperWork {
	private static final int BLOCK_UPDATE_FLAGS = 3;
	private static final double SEPARATOR_REACH_DISTANCE_SQUARED = 6.25D;
	private static final double BEEKEEPER_WALK_SPEED = 0.75D;
	private static final long TASK_MEMORY_TICKS = 80L;
	private static final long BEEKEEPER_DECIDE_INTERVAL_TICKS = 600L;
	private static final long BEEKEEPER_WORK_INTERVAL_TICKS = 1_600L;
	private static final int APIARY_SCAN_RADIUS = 8;
	private static final int BEE_SCAN_RADIUS = 16;
	private static final int MAX_MANAGED_HIVES_PER_SEPARATOR = 3;
	private static final int MAX_APIARY_FLOWERS_PER_SEPARATOR = 6;
	private static final int FULL_HONEY_LEVEL = 5;
	private static final int BEE_TARGET_PER_HIVE = 3;
	private static final int MIN_VISIBLE_BEE_TARGET = 2;
	private static final Map<String, TimedTask> ACTIVE_TASKS = new HashMap<>();
	private static final Map<String, Long> LAST_WORK_TICKS = new HashMap<>();

	private SettlementBeekeeperWork() {
	}

	public static boolean maintainLoadedBeekeeping(ServerLevel level, SettlementState settlement, Map<String, Integer> stock) {
		List<Villager> beekeepers = SettlementVillagers.nearbyBeekeepers(level, settlement);

		if (beekeepers.isEmpty()) {
			SettlementProfessionDiagnostics.log(level, settlement, SettlementRoleKeys.BEEKEEPER, "no_beekeepers", "");
			return false;
		}

		List<BlockPos> separators = SettlementConstruction.findPlacedHoneySeparators(level, settlement);

		if (separators.isEmpty()) {
			SettlementProfessionDiagnostics.log(level, settlement, SettlementRoleKeys.BEEKEEPER, "no_honey_separators", "");
			return false;
		}

		boolean stockChanged = false;
		long tick = level.getServer().getTickCount();
		Map<BlockPos, BeekeeperWorkPlan> workPlans = new HashMap<>();

		for (Villager beekeeper : beekeepers) {
			showBeekeeperTool(beekeeper);

			if (!SettlementVillagerWorkSchedule.shouldStartNewWork(level, beekeeper, "beekeeping", BEEKEEPER_DECIDE_INTERVAL_TICKS)) {
				continue;
			}

			BlockPos separator = nearestSeparator(beekeeper.blockPosition(), separators);

			if (separator == null) {
				continue;
			}

			BeekeeperWorkPlan workPlan = workPlans.computeIfAbsent(separator, pos -> workPlanFor(level, stock, pos));
			String taskKey = workPlan.taskKey();
			BlockPos workPos = workPlan.workPos();
			ACTIVE_TASKS.put(beekeeper.getUUID().toString(), new TimedTask(taskKey, tick));

			if (beekeeper.blockPosition().distSqr(workPos) > SEPARATOR_REACH_DISTANCE_SQUARED) {
				SettlementNavigation.moveToRoutineTarget(level, settlement, beekeeper, workPos, BEEKEEPER_WALK_SPEED);
				continue;
			}

			if (!workCooldownReady(beekeeper, tick)) {
				continue;
			}

			boolean worked = performBeekeeperTask(level, settlement, stock, beekeeper, separator, taskKey);
			workPlans.remove(separator);

			if (worked) {
				LAST_WORK_TICKS.put(beekeeper.getUUID().toString(), tick);
				stockChanged = true;
			}
		}

		return stockChanged;
	}

	public static Optional<String> loadedBeekeeperTaskKey(ServerLevel level, Villager villager) {
		TimedTask task = ACTIVE_TASKS.get(villager.getUUID().toString());

		if (task == null || level.getServer().getTickCount() - task.tick() > TASK_MEMORY_TICKS) {
			return Optional.empty();
		}

		return Optional.of(task.taskKey());
	}

	private static boolean performBeekeeperTask(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Villager beekeeper,
		BlockPos separator,
		String taskKey
	) {
		if (taskKey.equals("placing_managed_hive")) {
			return placeManagedHive(level, settlement, stock, beekeeper, separator);
		}

		if (taskKey.equals("planting_apiary_flowers")) {
			return plantApiaryFlower(level, settlement, stock, beekeeper, separator);
		}

		if (taskKey.equals("breeding_bees")) {
			return breedNearbyBee(level, settlement, stock, beekeeper, separator);
		}

		if (taskKey.equals("maintaining_hive_smoke")) {
			return maintainManagedHiveSmoke(level, settlement, stock, beekeeper, separator);
		}

		if (taskKey.equals("harvesting_real_honey")) {
			return harvestFullHive(level, settlement, stock, beekeeper, separator, true);
		}

		if (taskKey.equals("harvesting_real_honeycomb")) {
			return harvestFullHive(level, settlement, stock, beekeeper, separator, false);
		}

		if (taskKey.equals("crafting_bee_hives")) {
			if (!SettlementGoods.consumeGoods(stock, "honeycomb", 3) || !SettlementGoods.consumeGoods(stock, "planks", 6)) {
				return false;
			}

			SettlementGoods.addGoods(stock, "bee_hive", 1);
			SettlementProfessionReports.recordConsumed(level, settlement, SettlementRoleKeys.BEEKEEPER, beekeeper, "honeycomb", 3);
			SettlementProfessionReports.recordConsumed(level, settlement, SettlementRoleKeys.BEEKEEPER, beekeeper, "planks", 6);
			SettlementProfessionReports.recordProduced(level, settlement, SettlementRoleKeys.BEEKEEPER, beekeeper, "bee_hive", 1);
			SettlementProfessionReports.recordAccomplished(level, settlement, SettlementRoleKeys.BEEKEEPER, beekeeper, "crafted bee hive");
			return true;
		}

		if (taskKey.equals("making_candles")) {
			if (!SettlementGoods.consumeGoods(stock, "honeycomb", 1)) {
				return false;
			}

			SettlementGoods.addGoods(stock, "candle", 1);
			SettlementProfessionReports.recordConsumed(level, settlement, SettlementRoleKeys.BEEKEEPER, beekeeper, "honeycomb", 1);
			SettlementProfessionReports.recordProduced(level, settlement, SettlementRoleKeys.BEEKEEPER, beekeeper, "candle", 1);
			SettlementProfessionReports.recordAccomplished(level, settlement, SettlementRoleKeys.BEEKEEPER, beekeeper, "made candles");
			return true;
		}

		if (taskKey.equals("harvesting_honey")) {
			if (!SettlementGoods.consumeGoods(stock, "glass_bottle", 1)) {
				return false;
			}

			SettlementGoods.addGoods(stock, "honey_bottle", 1);
			SettlementProfessionReports.recordConsumed(level, settlement, SettlementRoleKeys.BEEKEEPER, beekeeper, "glass_bottle", 1);
			SettlementProfessionReports.recordProduced(level, settlement, SettlementRoleKeys.BEEKEEPER, beekeeper, "honey_bottle", 1);
			SettlementProfessionReports.recordAccomplished(level, settlement, SettlementRoleKeys.BEEKEEPER, beekeeper, "harvested honey bottles");
			return true;
		}

		if (stock.getOrDefault("shears", 0) <= 0) {
			return false;
		}

		SettlementGoods.addGoods(stock, "honeycomb", 1);
		SettlementProfessionReports.recordProduced(level, settlement, SettlementRoleKeys.BEEKEEPER, beekeeper, "honeycomb", 1);
		SettlementProfessionReports.recordAccomplished(level, settlement, SettlementRoleKeys.BEEKEEPER, beekeeper, "harvested honeycomb");
		return true;
	}

	private static BeekeeperWorkPlan workPlanFor(ServerLevel level, Map<String, Integer> stock, BlockPos separator) {
		Optional<BlockPos> unlitCampfire = managedHiveNeedingRelight(level, separator);
		Optional<BlockPos> missingSmoke = unlitCampfire.isPresent() ? Optional.empty() : managedHiveNeedingSmoke(level, separator);

		if (unlitCampfire.isPresent() || (missingSmoke.isPresent() && stock.getOrDefault("campfire", 0) > 0)) {
			return new BeekeeperWorkPlan("maintaining_hive_smoke", unlitCampfire.orElseGet(missingSmoke::get));
		}

		Optional<BlockPos> fullHive = fullHarvestableHive(level, separator);

		if (fullHive.isPresent() && stock.getOrDefault("glass_bottle", 0) > 0) {
			return new BeekeeperWorkPlan("harvesting_real_honey", fullHive.get());
		}

		if (fullHive.isPresent() && stock.getOrDefault("shears", 0) > 0) {
			return new BeekeeperWorkPlan("harvesting_real_honeycomb", fullHive.get());
		}

		if (stock.getOrDefault("bee_hive", 0) > 0 && stock.getOrDefault("campfire", 0) > 0) {
			int managedHives = nearbyManagedHiveCount(level, separator);

			if (managedHives < MAX_MANAGED_HIVES_PER_SEPARATOR) {
				Optional<BlockPos> managedHivePlacement = managedHivePlacement(level, separator);

				if (managedHivePlacement.isPresent()) {
					return new BeekeeperWorkPlan("placing_managed_hive", managedHivePlacement.get());
				}
			}
		}

		if (stock.getOrDefault("flower", 0) > 0) {
			int apiaryFlowers = nearbyApiaryFlowerCount(level, separator);

			if (apiaryFlowers < MAX_APIARY_FLOWERS_PER_SEPARATOR) {
				Optional<BlockPos> apiaryFlowerPlacement = apiaryFlowerPlacement(level, separator);

				if (apiaryFlowerPlacement.isPresent()) {
					return new BeekeeperWorkPlan("planting_apiary_flowers", apiaryFlowerPlacement.get());
				}
			}
		}

		if (stock.getOrDefault("flower", 0) > 0 && canSupportMoreBees(level, separator)) {
			Optional<Animal> breedableBee = nearestBreedableBee(level, separator);

			if (breedableBee.isPresent()) {
				return new BeekeeperWorkPlan("breeding_bees", breedableBee.get().blockPosition());
			}
		}

		if (stock.getOrDefault("honeycomb", 0) >= 3 && stock.getOrDefault("planks", 0) >= 6 && stock.getOrDefault("bee_hive", 0) < 3) {
			return new BeekeeperWorkPlan("crafting_bee_hives", separator);
		}

		if (stock.getOrDefault("honeycomb", 0) > 0 && stock.getOrDefault("candle", 0) < 8) {
			return new BeekeeperWorkPlan("making_candles", separator);
		}

		return new BeekeeperWorkPlan(stock.getOrDefault("glass_bottle", 0) > 0 ? "harvesting_honey" : "harvesting_honeycomb", separator);
	}

	private static boolean placeManagedHive(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, Villager beekeeper, BlockPos separator) {
		if (stock.getOrDefault("bee_hive", 0) <= 0 || stock.getOrDefault("campfire", 0) <= 0) {
			return false;
		}

		Optional<BlockPos> campfirePos = managedHivePlacement(level, separator);

		if (campfirePos.isEmpty()) {
			return false;
		}

		BlockPos hivePos = campfirePos.get().above();
		Direction hiveFacing = hiveFacing(separator, hivePos);
		BlockState campfireState = Blocks.CAMPFIRE.defaultBlockState();
		BlockState hiveState = Blocks.BEEHIVE.defaultBlockState().setValue(BeehiveBlock.FACING, hiveFacing);

		if (!campfireState.canSurvive(level, campfirePos.get()) || !hiveState.canSurvive(level, hivePos)) {
			return false;
		}

		level.setBlock(campfirePos.get(), campfireState, BLOCK_UPDATE_FLAGS);
		level.setBlock(hivePos, hiveState, BLOCK_UPDATE_FLAGS);
		SettlementGoods.consumeGoods(stock, "campfire", 1);
		SettlementGoods.consumeGoods(stock, "bee_hive", 1);
		SettlementProfessionReports.recordConsumed(level, settlement, SettlementRoleKeys.BEEKEEPER, beekeeper, "campfire", 1);
		SettlementProfessionReports.recordConsumed(level, settlement, SettlementRoleKeys.BEEKEEPER, beekeeper, "bee_hive", 1);
		SettlementProfessionReports.recordAccomplished(level, settlement, SettlementRoleKeys.BEEKEEPER, beekeeper, "placed managed hive");
		return true;
	}

	private static boolean plantApiaryFlower(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, Villager beekeeper, BlockPos separator) {
		if (stock.getOrDefault("flower", 0) <= 0) {
			return false;
		}

		Optional<BlockPos> flowerPos = apiaryFlowerPlacement(level, separator);

		if (flowerPos.isEmpty()) {
			return false;
		}

		BlockState flowerState = apiaryFlowerState(level.getServer().getTickCount(), flowerPos.get());

		if (!flowerState.canSurvive(level, flowerPos.get()) || !SettlementGoods.consumeGoods(stock, "flower", 1)) {
			return false;
		}

		beekeeper.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.POPPY));
		level.setBlock(flowerPos.get(), flowerState, BLOCK_UPDATE_FLAGS);
		SettlementProfessionReports.recordConsumed(level, settlement, SettlementRoleKeys.BEEKEEPER, beekeeper, "flower", 1);
		SettlementProfessionReports.recordAccomplished(level, settlement, SettlementRoleKeys.BEEKEEPER, beekeeper, "planted apiary flowers");
		return true;
	}

	private static boolean breedNearbyBee(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Villager beekeeper,
		BlockPos separator
	) {
		if (!canSupportMoreBees(level, separator)) {
			return false;
		}

		Optional<Animal> targetBee = nearestBreedableBee(level, separator);

		if (targetBee.isEmpty()
			|| targetBee.get().blockPosition().distSqr(beekeeper.blockPosition()) > SEPARATOR_REACH_DISTANCE_SQUARED
			|| !SettlementGoods.consumeGoods(stock, "flower", 1)) {
			return false;
		}

		beekeeper.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.POPPY));
		targetBee.get().setInLove(null);
		SettlementProfessionReports.recordConsumed(level, settlement, SettlementRoleKeys.BEEKEEPER, beekeeper, "flower", 1);
		SettlementProfessionReports.recordAccomplished(level, settlement, SettlementRoleKeys.BEEKEEPER, beekeeper, "fed breeding bees");
		return true;
	}

	private static boolean maintainManagedHiveSmoke(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, Villager beekeeper, BlockPos separator) {
		Optional<BlockPos> unlitCampfire = managedHiveNeedingRelight(level, separator);

		if (unlitCampfire.isPresent()) {
			BlockState state = level.getBlockState(unlitCampfire.get());

			if (!state.hasProperty(CampfireBlock.LIT)) {
				return false;
			}

			level.setBlock(unlitCampfire.get(), state.setValue(CampfireBlock.LIT, true), BLOCK_UPDATE_FLAGS);
			SettlementProfessionReports.recordAccomplished(level, settlement, SettlementRoleKeys.BEEKEEPER, beekeeper, "relit hive smoke");
			return true;
		}

		Optional<BlockPos> campfirePos = managedHiveNeedingSmoke(level, separator);

		if (campfirePos.isEmpty() || !SettlementGoods.consumeGoods(stock, "campfire", 1)) {
			return false;
		}

		BlockState campfireState = Blocks.CAMPFIRE.defaultBlockState();

		if (!campfireState.canSurvive(level, campfirePos.get())) {
			SettlementGoods.addGoods(stock, "campfire", 1);
			return false;
		}

		level.setBlock(campfirePos.get(), campfireState, BLOCK_UPDATE_FLAGS);
		SettlementProfessionReports.recordConsumed(level, settlement, SettlementRoleKeys.BEEKEEPER, beekeeper, "campfire", 1);
		SettlementProfessionReports.recordAccomplished(level, settlement, SettlementRoleKeys.BEEKEEPER, beekeeper, "restored hive smoke");
		return true;
	}

	private static boolean harvestFullHive(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Villager beekeeper,
		BlockPos separator,
		boolean bottledHoney
	) {
		Optional<BlockPos> hivePos = fullHarvestableHive(level, separator);

		if (hivePos.isEmpty()) {
			return false;
		}

		BlockState hiveState = level.getBlockState(hivePos.get());

		if (!hiveState.hasProperty(BeehiveBlock.HONEY_LEVEL) || hiveState.getValue(BeehiveBlock.HONEY_LEVEL) < FULL_HONEY_LEVEL) {
			return false;
		}

		if (bottledHoney) {
			if (!SettlementGoods.consumeGoods(stock, "glass_bottle", 1)) {
				return false;
			}

			SettlementGoods.addGoods(stock, "honey_bottle", 1);
			SettlementProfessionReports.recordConsumed(level, settlement, SettlementRoleKeys.BEEKEEPER, beekeeper, "glass_bottle", 1);
			SettlementProfessionReports.recordProduced(level, settlement, SettlementRoleKeys.BEEKEEPER, beekeeper, "honey_bottle", 1);
			SettlementProfessionReports.recordAccomplished(level, settlement, SettlementRoleKeys.BEEKEEPER, beekeeper, "harvested full hive honey");
		} else {
			if (stock.getOrDefault("shears", 0) <= 0) {
				return false;
			}

			SettlementGoods.addGoods(stock, "honeycomb", 3);
			SettlementProfessionReports.recordProduced(level, settlement, SettlementRoleKeys.BEEKEEPER, beekeeper, "honeycomb", 3);
			SettlementProfessionReports.recordAccomplished(level, settlement, SettlementRoleKeys.BEEKEEPER, beekeeper, "sheared full hive honeycomb");
		}

		level.setBlock(hivePos.get(), hiveState.setValue(BeehiveBlock.HONEY_LEVEL, 0), BLOCK_UPDATE_FLAGS);
		return true;
	}

	private static Optional<BlockPos> managedHivePlacement(ServerLevel level, BlockPos separator) {
		BlockPos bestPos = null;
		int bestScore = Integer.MIN_VALUE;

		for (int radius = 2; radius <= APIARY_SCAN_RADIUS; radius++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
						continue;
					}

					for (int dy = 1; dy >= -2; dy--) {
						BlockPos campfirePos = separator.offset(dx, dy, dz);

						if (canPlaceManagedHiveAt(level, campfirePos)) {
							int score = managedHivePlacementScore(level, separator, campfirePos);

							if (score > bestScore) {
								bestScore = score;
								bestPos = campfirePos;
							}
						}
					}
				}
			}
		}

		return Optional.ofNullable(bestPos);
	}

	private static Optional<BlockPos> apiaryFlowerPlacement(ServerLevel level, BlockPos separator) {
		BlockPos bestPos = null;
		int bestScore = Integer.MIN_VALUE;

		for (int radius = 2; radius <= APIARY_SCAN_RADIUS; radius++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
						continue;
					}

					for (int dy = 1; dy >= -2; dy--) {
						BlockPos flowerPos = separator.offset(dx, dy, dz);

						if (canPlantApiaryFlowerAt(level, flowerPos)) {
							int score = apiaryFlowerPlacementScore(level, separator, flowerPos);

							if (score > bestScore) {
								bestScore = score;
								bestPos = flowerPos;
							}
						}
					}
				}
			}
		}

		return Optional.ofNullable(bestPos);
	}

	private static int managedHivePlacementScore(ServerLevel level, BlockPos separator, BlockPos campfirePos) {
		BlockPos hivePos = campfirePos.above();
		int distance = Math.abs(separator.getX() - campfirePos.getX()) + Math.abs(separator.getZ() - campfirePos.getZ());
		int score = APIARY_SCAN_RADIUS * 2 - distance;
		score += nearbyApiaryFlowerCount(level, hivePos, 5) * 5;
		score += nearbyHiveOrNestCount(level, hivePos, 4) * 2;

		if (hasOpenAirAbove(level, hivePos)) {
			score += 4;
		}

		return score;
	}

	private static int apiaryFlowerPlacementScore(ServerLevel level, BlockPos separator, BlockPos flowerPos) {
		int distance = Math.abs(separator.getX() - flowerPos.getX()) + Math.abs(separator.getZ() - flowerPos.getZ());
		int score = APIARY_SCAN_RADIUS * 2 - distance;
		score += nearbyHiveOrNestCount(level, flowerPos, 5) * 6;
		score += nearbyApiaryFlowerCount(level, flowerPos, 4) * 2;

		if (hasOpenAirAbove(level, flowerPos)) {
			score += 2;
		}

		return score;
	}

	private static int nearbyHiveOrNestCount(ServerLevel level, BlockPos origin, int radius) {
		int count = 0;

		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				for (int dy = -2; dy <= 4; dy++) {
					if (isHiveOrNest(level.getBlockState(origin.offset(dx, dy, dz)))) {
						count++;
					}
				}
			}
		}

		return count;
	}

	private static int nearbyApiaryFlowerCount(ServerLevel level, BlockPos origin, int radius) {
		int count = 0;

		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				for (int dy = -1; dy <= 2; dy++) {
					if (level.getBlockState(origin.offset(dx, dy, dz)).is(BlockTags.FLOWERS)) {
						count++;
					}
				}
			}
		}

		return count;
	}

	private static boolean hasOpenAirAbove(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos.above()).isAir()
			&& level.getBlockState(pos.above(2)).isAir();
	}

	private static boolean canPlantApiaryFlowerAt(ServerLevel level, BlockPos flowerPos) {
		if (!level.hasChunkAt(flowerPos)) {
			return false;
		}

		BlockState existingState = level.getBlockState(flowerPos);
		BlockPos supportPos = flowerPos.below();
		BlockState supportState = level.getBlockState(supportPos);

		return canReplaceForApiaryFlower(existingState)
			&& isApiaryFlowerSupport(level, supportPos, supportState)
			&& Blocks.POPPY.defaultBlockState().canSurvive(level, flowerPos);
	}

	private static boolean canPlaceManagedHiveAt(ServerLevel level, BlockPos campfirePos) {
		if (!level.hasChunkAt(campfirePos)) {
			return false;
		}

		BlockPos supportPos = campfirePos.below();
		BlockPos hivePos = campfirePos.above();
		BlockState supportState = level.getBlockState(supportPos);
		BlockState campfireState = level.getBlockState(campfirePos);
		BlockState hiveState = level.getBlockState(hivePos);

		return isApiarySupport(level, supportPos, supportState)
			&& canReplaceForApiaryCampfire(campfireState)
			&& canReplaceForApiaryHive(hiveState)
			&& Blocks.CAMPFIRE.defaultBlockState().canSurvive(level, campfirePos)
			&& Blocks.BEEHIVE.defaultBlockState().canSurvive(level, hivePos);
	}

	private static boolean isApiarySupport(ServerLevel level, BlockPos supportPos, BlockState supportState) {
		if (!supportState.isSolid()
			|| !supportState.getFluidState().isEmpty()
			|| supportState.is(Blocks.DIRT_PATH)
			|| supportState.is(Blocks.FARMLAND)
			|| supportState.is(BlockTags.LEAVES)
			|| supportState.is(BlockTags.LOGS)) {
			return false;
		}

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockState neighborState = level.getBlockState(supportPos.relative(direction));

			if (isRoadOrCropSurface(neighborState)) {
				return false;
			}
		}

		return true;
	}

	private static boolean canReplaceForApiaryCampfire(BlockState state) {
		return state.isAir()
			|| state.is(Blocks.SHORT_GRASS)
			|| state.is(Blocks.TALL_GRASS)
			|| state.is(Blocks.LEAF_LITTER)
			|| state.is(BlockTags.FLOWERS);
	}

	private static boolean canReplaceForApiaryHive(BlockState state) {
		return state.isAir()
			|| state.is(Blocks.SHORT_GRASS)
			|| state.is(Blocks.TALL_GRASS)
			|| state.is(Blocks.LEAF_LITTER)
			|| state.is(BlockTags.FLOWERS);
	}

	private static boolean canReplaceForApiaryFlower(BlockState state) {
		return state.isAir()
			|| state.is(Blocks.SHORT_GRASS)
			|| state.is(Blocks.TALL_GRASS)
			|| state.is(Blocks.LEAF_LITTER);
	}

	private static boolean isApiaryFlowerSupport(ServerLevel level, BlockPos supportPos, BlockState supportState) {
		if (!(supportState.is(Blocks.GRASS_BLOCK) || supportState.is(Blocks.DIRT))
			|| supportState.getFluidState().isEmpty() == false
			|| supportState.is(Blocks.DIRT_PATH)
			|| supportState.is(Blocks.FARMLAND)
			|| supportState.is(BlockTags.LEAVES)
			|| supportState.is(BlockTags.LOGS)) {
			return false;
		}

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockState neighborState = level.getBlockState(supportPos.relative(direction));

			if (isRoadOrCropSurface(neighborState)) {
				return false;
			}
		}

		return true;
	}

	private static boolean isRoadOrCropSurface(BlockState state) {
		return state.is(Blocks.DIRT_PATH)
			|| state.is(Blocks.FARMLAND)
			|| state.is(Blocks.WHEAT)
			|| state.is(Blocks.CARROTS)
			|| state.is(Blocks.POTATOES)
			|| state.is(Blocks.BEETROOTS);
	}

	private static int nearbyManagedHiveCount(ServerLevel level, BlockPos separator) {
		int count = 0;

		for (int dx = -APIARY_SCAN_RADIUS; dx <= APIARY_SCAN_RADIUS; dx++) {
			for (int dz = -APIARY_SCAN_RADIUS; dz <= APIARY_SCAN_RADIUS; dz++) {
				for (int dy = -2; dy <= 3; dy++) {
					BlockPos hivePos = separator.offset(dx, dy, dz);

					if (level.getBlockState(hivePos).is(Blocks.BEEHIVE)
						&& level.getBlockState(hivePos.below()).is(Blocks.CAMPFIRE)) {
						count++;
					}
				}
			}
		}

		return count;
	}

	private static boolean canSupportMoreBees(ServerLevel level, BlockPos separator) {
		int hiveCount = nearbyHiveOrNestCount(level, separator);

		if (hiveCount <= 0) {
			return false;
		}

		int targetBeeCount = Math.max(MIN_VISIBLE_BEE_TARGET, hiveCount * BEE_TARGET_PER_HIVE);
		return nearbyBeePopulationCount(level, separator) < targetBeeCount;
	}

	private static int nearbyHiveOrNestCount(ServerLevel level, BlockPos separator) {
		int count = 0;

		for (int dx = -APIARY_SCAN_RADIUS; dx <= APIARY_SCAN_RADIUS; dx++) {
			for (int dz = -APIARY_SCAN_RADIUS; dz <= APIARY_SCAN_RADIUS; dz++) {
				for (int dy = -2; dy <= 4; dy++) {
					if (isHiveOrNest(level.getBlockState(separator.offset(dx, dy, dz)))) {
						count++;
					}
				}
			}
		}

		return count;
	}

	private static int nearbyBeePopulationCount(ServerLevel level, BlockPos separator) {
		return nearbyVisibleBeeCount(level, separator) + nearbyHiveOccupantCount(level, separator);
	}

	private static int nearbyVisibleBeeCount(ServerLevel level, BlockPos origin) {
		double radiusSqr = BEE_SCAN_RADIUS * BEE_SCAN_RADIUS;
		AABB bounds = new AABB(origin).inflate(BEE_SCAN_RADIUS);
		int count = 0;

		for (Animal bee : level.getEntities(
			EntityTypeTest.forClass(Animal.class),
			bounds,
			animal -> animal.getType() == EntityType.BEE
				&& !animal.isRemoved()
				&& animal.isAlive()
				&& animal.blockPosition().distSqr(origin) <= radiusSqr
		)) {
			count++;
		}

		return count;
	}

	private static int nearbyHiveOccupantCount(ServerLevel level, BlockPos separator) {
		int count = 0;

		for (int dx = -APIARY_SCAN_RADIUS; dx <= APIARY_SCAN_RADIUS; dx++) {
			for (int dz = -APIARY_SCAN_RADIUS; dz <= APIARY_SCAN_RADIUS; dz++) {
				for (int dy = -2; dy <= 4; dy++) {
					BlockPos hivePos = separator.offset(dx, dy, dz);

					if (level.getBlockEntity(hivePos) instanceof BeehiveBlockEntity hive) {
						count += hive.getOccupantCount();
					}
				}
			}
		}

		return count;
	}

	private static int nearbyApiaryFlowerCount(ServerLevel level, BlockPos separator) {
		int count = 0;

		for (int dx = -APIARY_SCAN_RADIUS; dx <= APIARY_SCAN_RADIUS; dx++) {
			for (int dz = -APIARY_SCAN_RADIUS; dz <= APIARY_SCAN_RADIUS; dz++) {
				for (int dy = -2; dy <= 3; dy++) {
					BlockPos flowerPos = separator.offset(dx, dy, dz);

					if (level.getBlockState(flowerPos).is(BlockTags.FLOWERS)) {
						count++;
					}
				}
			}
		}

		return count;
	}

	private static Optional<BlockPos> fullHarvestableHive(ServerLevel level, BlockPos separator) {
		for (int dx = -APIARY_SCAN_RADIUS; dx <= APIARY_SCAN_RADIUS; dx++) {
			for (int dz = -APIARY_SCAN_RADIUS; dz <= APIARY_SCAN_RADIUS; dz++) {
				for (int dy = -2; dy <= 4; dy++) {
					BlockPos hivePos = separator.offset(dx, dy, dz);
					BlockState hiveState = level.getBlockState(hivePos);

					if (!isHiveOrNest(hiveState)
						|| !hiveState.hasProperty(BeehiveBlock.HONEY_LEVEL)
						|| hiveState.getValue(BeehiveBlock.HONEY_LEVEL) < FULL_HONEY_LEVEL
						|| !hasSafeSmoke(level, hivePos)) {
						continue;
					}

					return Optional.of(hivePos);
				}
			}
		}

		return Optional.empty();
	}

	private static Optional<Animal> nearestBreedableBee(ServerLevel level, BlockPos origin) {
		double radiusSqr = BEE_SCAN_RADIUS * BEE_SCAN_RADIUS;
		AABB bounds = new AABB(origin).inflate(BEE_SCAN_RADIUS);
		Animal bestBee = null;
		double bestDistance = Double.MAX_VALUE;

		for (Animal bee : level.getEntities(
			EntityTypeTest.forClass(Animal.class),
			bounds,
			animal -> animal.getType() == EntityType.BEE
				&& !animal.isRemoved()
				&& animal.isAlive()
				&& !animal.isBaby()
				&& animal.canFallInLove()
				&& animal.blockPosition().distSqr(origin) <= radiusSqr
		)) {
			double distance = bee.distanceToSqr(origin.getX() + 0.5D, origin.getY() + 0.5D, origin.getZ() + 0.5D);

			if (distance < bestDistance) {
				bestDistance = distance;
				bestBee = bee;
			}
		}

		return Optional.ofNullable(bestBee);
	}

	private static Optional<BlockPos> managedHiveNeedingRelight(ServerLevel level, BlockPos separator) {
		for (int dx = -APIARY_SCAN_RADIUS; dx <= APIARY_SCAN_RADIUS; dx++) {
			for (int dz = -APIARY_SCAN_RADIUS; dz <= APIARY_SCAN_RADIUS; dz++) {
				for (int dy = -2; dy <= 3; dy++) {
					BlockPos hivePos = separator.offset(dx, dy, dz);
					BlockState hiveState = level.getBlockState(hivePos);
					BlockPos campfirePos = hivePos.below();
					BlockState campfireState = level.getBlockState(campfirePos);

					if (hiveState.is(Blocks.BEEHIVE)
						&& campfireState.is(Blocks.CAMPFIRE)
						&& campfireState.hasProperty(CampfireBlock.LIT)
						&& !campfireState.getValue(CampfireBlock.LIT)) {
						return Optional.of(campfirePos);
					}
				}
			}
		}

		return Optional.empty();
	}

	private static Optional<BlockPos> managedHiveNeedingSmoke(ServerLevel level, BlockPos separator) {
		for (int dx = -APIARY_SCAN_RADIUS; dx <= APIARY_SCAN_RADIUS; dx++) {
			for (int dz = -APIARY_SCAN_RADIUS; dz <= APIARY_SCAN_RADIUS; dz++) {
				for (int dy = -2; dy <= 3; dy++) {
					BlockPos hivePos = separator.offset(dx, dy, dz);
					BlockState hiveState = level.getBlockState(hivePos);

					if (!hiveState.is(Blocks.BEEHIVE)) {
						continue;
					}

					BlockPos campfirePos = hivePos.below();
					BlockState campfireState = level.getBlockState(campfirePos);

					if (campfireState.is(Blocks.CAMPFIRE)) {
						continue;
					}

					if (canPlaceSmokeAt(level, campfirePos)) {
						return Optional.of(campfirePos);
					}
				}
			}
		}

		return Optional.empty();
	}

	private static boolean canPlaceSmokeAt(ServerLevel level, BlockPos campfirePos) {
		if (!level.hasChunkAt(campfirePos)) {
			return false;
		}

		BlockPos supportPos = campfirePos.below();
		BlockState supportState = level.getBlockState(supportPos);
		BlockState existingState = level.getBlockState(campfirePos);

		return isApiarySupport(level, supportPos, supportState)
			&& canReplaceForApiaryCampfire(existingState)
			&& Blocks.CAMPFIRE.defaultBlockState().canSurvive(level, campfirePos);
	}

	private static boolean isHiveOrNest(BlockState state) {
		return state.is(Blocks.BEEHIVE) || state.is(Blocks.BEE_NEST);
	}

	private static boolean hasSafeSmoke(ServerLevel level, BlockPos hivePos) {
		BlockState campfireState = level.getBlockState(hivePos.below());
		return campfireState.is(Blocks.CAMPFIRE)
			&& (!campfireState.hasProperty(CampfireBlock.LIT) || campfireState.getValue(CampfireBlock.LIT));
	}

	private static Direction hiveFacing(BlockPos separator, BlockPos hivePos) {
		int dx = hivePos.getX() - separator.getX();
		int dz = hivePos.getZ() - separator.getZ();

		if (Math.abs(dx) > Math.abs(dz)) {
			return dx > 0 ? Direction.WEST : Direction.EAST;
		}

		return dz > 0 ? Direction.NORTH : Direction.SOUTH;
	}

	private static BlockState apiaryFlowerState(long tick, BlockPos pos) {
		return switch ((int) Math.floorMod(tick + pos.asLong(), 5L)) {
			case 0 -> Blocks.DANDELION.defaultBlockState();
			case 1 -> Blocks.POPPY.defaultBlockState();
			case 2 -> Blocks.CORNFLOWER.defaultBlockState();
			case 3 -> Blocks.OXEYE_DAISY.defaultBlockState();
			default -> Blocks.ALLIUM.defaultBlockState();
		};
	}

	private static boolean workCooldownReady(Villager beekeeper, long tick) {
		Long lastWorkTick = LAST_WORK_TICKS.get(beekeeper.getUUID().toString());
		return lastWorkTick == null || tick - lastWorkTick >= BEEKEEPER_WORK_INTERVAL_TICKS;
	}

	private static BlockPos nearestSeparator(BlockPos origin, List<BlockPos> separators) {
		BlockPos bestPos = null;
		double bestDistance = Double.MAX_VALUE;

		for (BlockPos separator : separators) {
			double distance = separator.distSqr(origin);

			if (distance < bestDistance) {
				bestDistance = distance;
				bestPos = separator;
			}
		}

		return bestPos;
	}

	private static void showBeekeeperTool(Villager beekeeper) {
		if (beekeeper.getMainHandItem().isEmpty()) {
			beekeeper.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SHEARS));
		}
	}

	private record TimedTask(String taskKey, long tick) {
	}

	private record BeekeeperWorkPlan(String taskKey, BlockPos workPos) {
	}
}
