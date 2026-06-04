package com.ronhelwig.livevillages.sim;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SettlementFishermanWork {
	private static final double FISHERMAN_WALK_SPEED = 0.75D;
	private static final double FISHING_REACH_DISTANCE_SQUARED = 6.25D;
	private static final double BOAT_TARGET_REACH_SQUARED = 4.0D;
	private static final double BOAT_MOVE_STEP_BLOCKS = 3.5D;
	private static final int SHORELINE_SEARCH_RADIUS_BLOCKS = 16;
	private static final int SHORELINE_Y_SEARCH_BLOCKS = 6;
	private static final long TASK_MEMORY_TICKS = 80L;
	private static final long FISHERMAN_DECIDE_INTERVAL_TICKS = 320L;
	private static final long DAY_TICKS = 24_000L;
	private static final long VILLAGE_WAKEUP_END_TICK = 2_000L;
	private static final long RETURN_TO_DOCK_START_TICK = 8_200L;
	private static final long VILLAGE_GATHERING_START_TICK = 9_000L;
	private static final long FISHING_CATCH_INTERVAL_TICKS = 1_200L;
	private static final int DOCK_LENGTH_BLOCKS = 8;
	private static final int BOAT_SCAN_RADIUS_BLOCKS = 10;
	private static final int WATER_THREAT_SCAN_RADIUS_BLOCKS = 18;
	private static final double WATER_DEFENSE_MELEE_RANGE_SQUARED = 4.0D;
	private static final double WATER_DEFENSE_SPEED = 1.0D;
	private static final long WATER_DEFENSE_ATTACK_COOLDOWN_TICKS = 20L;
	private static final Map<String, TimedTask> ACTIVE_TASKS = new HashMap<>();
	private static final Map<String, Long> LAST_CATCH_TICKS = new HashMap<>();
	private static final Map<String, Long> LAST_DEFENSE_ATTACK_TICKS = new HashMap<>();

	private SettlementFishermanWork() {
	}

	public static boolean maintainLoadedFishing(ServerLevel level, SettlementState settlement, Map<String, Integer> stock) {
		List<Villager> fishermen = SettlementVillagers.nearbyFishermen(level, settlement);

		if (fishermen.isEmpty()) {
			SettlementProfessionDiagnostics.log(level, settlement, "fisherman", "no_fishermen", "");
			return false;
		}

		List<BlockPos> docks = SettlementConstruction.findDockOrigins(level, settlement);
		long tick = level.getServer().getTickCount();
		long dayTime = Math.floorMod(level.getOverworldClockTime(), DAY_TICKS);
		boolean stockChanged = false;

		for (Villager fisherman : fishermen) {
			if (shouldRecallForVillageSchedule(dayTime)) {
				recallFisherman(level, fisherman, docks);
				ACTIVE_TASKS.remove(fisherman.getUUID().toString());
				continue;
			}

			Optional<Monster> waterThreat = nearestWaterThreat(level, settlement, fisherman);

			if (waterThreat.isPresent()) {
				ACTIVE_TASKS.put(fisherman.getUUID().toString(), new TimedTask("defending_waterfront", tick));
				engageWaterThreat(level, fisherman, waterThreat.get(), tick);
				continue;
			}

			if (!SettlementVillagerWorkSchedule.shouldStartNewWork(level, fisherman, "fishing", FISHERMAN_DECIDE_INTERVAL_TICKS)) {
				if (SettlementVillagerWorkSchedule.isTakingBreak(level, fisherman)) {
					fisherman.getNavigation().stop();
				}

				continue;
			}

			Optional<FishingTask> task = chooseFishingTask(level, settlement, fisherman, docks, dayTime);

			if (task.isEmpty()) {
				ACTIVE_TASKS.remove(fisherman.getUUID().toString());
				SettlementProfessionDiagnostics.log(level, settlement, "fisherman", "no_fishing_spot", "docks=" + docks.size());
				continue;
			}

			FishingTask fishingTask = task.get();
			ACTIVE_TASKS.put(fisherman.getUUID().toString(), new TimedTask(fishingTask.taskKey(), tick));
			showFishingTool(fisherman);

			if (fishingTask.boat().isPresent()) {
				stockChanged |= maintainBoatFishingTask(level, settlement, stock, fisherman, fishingTask, tick);
			} else {
				stockChanged |= maintainShoreFishingTask(level, settlement, stock, fisherman, fishingTask, tick);
			}
		}

		return stockChanged;
	}

	public static Optional<String> loadedFishermanTaskKey(ServerLevel level, Villager villager) {
		TimedTask task = ACTIVE_TASKS.get(villager.getUUID().toString());

		if (task == null || level.getServer().getTickCount() - task.tick() > TASK_MEMORY_TICKS) {
			return Optional.empty();
		}

		return Optional.of(task.taskKey());
	}

	public static double dailyCatchRate(int fishermen, SettlementConstruction.InfrastructureSurvey infrastructure, int activeDockBoats) {
		if (fishermen <= 0) {
			return 0.0D;
		}

		double catchRate = fishermen * 1.5D;

		if (infrastructure.available() && infrastructure.hasLargeWaterBody()) {
			catchRate += fishermen * 0.75D;

			if (infrastructure.docks() > 0) {
				catchRate *= 1.0D + Math.min(0.8D, infrastructure.docks() * 0.6D);
			}

			if (infrastructure.lighthouses() > 0) {
				catchRate += fishermen * scaledLighthouseCount(infrastructure.lighthouses()) * 0.65D;
			}

			if (infrastructure.docks() > 0 && activeDockBoats > 0) {
				catchRate += Math.min(fishermen, activeDockBoats) * 0.75D;
			}
		}

		return catchRate;
	}

	private static Optional<FishingTask> chooseFishingTask(
		ServerLevel level,
		SettlementState settlement,
		Villager fisherman,
		List<BlockPos> docks,
		long dayTime
	) {
		Optional<DockFishingSpot> dockSpot = nearestDockFishingSpot(level, fisherman, docks);

		if (dockSpot.isPresent()) {
			DockFishingSpot spot = dockSpot.get();

			if (spot.boat().isPresent() && (dayTime < RETURN_TO_DOCK_START_TICK || fisherman.getVehicle() == spot.boat().get())) {
				Boat boat = spot.boat().get();
				boolean returning = dayTime >= RETURN_TO_DOCK_START_TICK;
				String taskKey;

				if (fisherman.getVehicle() == boat) {
					taskKey = returning ? "returning_fishing_boat" : "rowing_out";
				} else {
					taskKey = "heading_to_boat";
				}

				if (!returning && boat.blockPosition().distSqr(spot.boatFishingPos()) <= BOAT_TARGET_REACH_SQUARED) {
					taskKey = "fishing_from_boat";
				}

				return Optional.of(new FishingTask(taskKey, spot.standPos(), spot.waterLookPos(), Optional.of(boat), Optional.of(spot)));
			}

			return Optional.of(new FishingTask("fishing_from_dock", spot.standPos(), spot.waterLookPos(), Optional.empty(), Optional.of(spot)));
		}

		return findShoreFishingSpot(level, fisherman, settlement)
			.map(spot -> new FishingTask("fishing_from_shore", spot.standPos(), spot.waterPos(), Optional.empty(), Optional.empty()));
	}

	private static boolean maintainBoatFishingTask(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Villager fisherman,
		FishingTask task,
		long tick
	) {
		DockFishingSpot dockSpot = task.dockSpot().orElse(null);
		Boat boat = task.boat().orElse(null);

		if (dockSpot == null || boat == null || !boat.isAlive() || boat.isRemoved()) {
			return false;
		}

		if (fisherman.getVehicle() != boat) {
			fisherman.getLookControl().setLookAt(boat.getX(), boat.getY(), boat.getZ());
			SettlementNavigation.moveToRoutineTarget(
				level,
				settlement,
				fisherman,
				dockSpot.standPos(),
				FISHERMAN_WALK_SPEED,
				fishingTravelRadius(settlement)
			);

			if (fisherman.distanceToSqr(boat) <= 6.25D) {
				fisherman.getNavigation().stop();
				fisherman.startRiding(boat, true, true);
			}

			return false;
		}

		fisherman.getNavigation().stop();
		BlockPos boatTarget = "returning_fishing_boat".equals(task.taskKey()) ? dockSpot.mooringPos() : dockSpot.boatFishingPos();
		moveBoatToward(boat, boatTarget);
		fisherman.getLookControl().setLookAt(task.lookPos().getX() + 0.5D, task.lookPos().getY() + 0.5D, task.lookPos().getZ() + 0.5D);

		if (boat.blockPosition().distSqr(boatTarget) <= BOAT_TARGET_REACH_SQUARED) {
			if ("returning_fishing_boat".equals(task.taskKey())) {
				releaseFishingBoat(fisherman, boat, dockSpot);
			} else {
				fisherman.swing(InteractionHand.MAIN_HAND);
				return maybeCatchFish(level, settlement, stock, fisherman, tick, "fished from boat");
			}
		}

		return false;
	}

	private static boolean maintainShoreFishingTask(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Villager fisherman,
		FishingTask task,
		long tick
	) {
		BlockPos standPos = task.standPos();
		fisherman.getLookControl().setLookAt(task.lookPos().getX() + 0.5D, task.lookPos().getY() + 0.5D, task.lookPos().getZ() + 0.5D);
		SettlementNavigation.moveToRoutineTarget(
			level,
			settlement,
			fisherman,
			standPos,
			FISHERMAN_WALK_SPEED,
			fishingTravelRadius(settlement)
		);

		if (fisherman.blockPosition().distSqr(standPos) <= FISHING_REACH_DISTANCE_SQUARED) {
			fisherman.getNavigation().stop();
			fisherman.swing(InteractionHand.MAIN_HAND);
			return maybeCatchFish(level, settlement, stock, fisherman, tick, "fished from " + ("fishing_from_dock".equals(task.taskKey()) ? "dock" : "shore"));
		}

		SettlementProfessionDiagnostics.log(level, settlement, "fisherman", "moving_to_work", "villager=" + fisherman.getUUID() + " target=" + standPos.toShortString());
		return false;
	}

	private static boolean maybeCatchFish(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Villager fisherman,
		long tick,
		String accomplishment
	) {
		String key = fisherman.getUUID().toString();
		long previousCatchTick = LAST_CATCH_TICKS.getOrDefault(key, Long.MIN_VALUE);
		if (previousCatchTick != Long.MIN_VALUE && tick - previousCatchTick < FISHING_CATCH_INTERVAL_TICKS) {
			return false;
		}

		LAST_CATCH_TICKS.put(key, tick);
		SettlementGoods.addGoods(stock, "cod", 1);
		SettlementProfessionReports.recordProduced(level, settlement, SettlementRoleKeys.FISHERMAN, fisherman, "cod", 1);
		SettlementProfessionReports.recordAccomplished(level, settlement, SettlementRoleKeys.FISHERMAN, fisherman, accomplishment);
		return true;
	}

	private static int fishingTravelRadius(SettlementState settlement) {
		return SettlementVillagers.professionWorkRadiusBlocks(settlement, SettlementRoleKeys.FISHERMAN);
	}

	private static void recallFisherman(ServerLevel level, Villager fisherman, List<BlockPos> docks) {
		if (!(fisherman.getVehicle() instanceof Boat boat)) {
			return;
		}

		nearestDockFishingSpot(level, fisherman, docks)
			.ifPresent(spot -> releaseFishingBoat(fisherman, boat, spot));
	}

	private static void releaseFishingBoat(Villager fisherman, Boat boat, DockFishingSpot spot) {
		boat.setDeltaMovement(Vec3.ZERO);
		boat.setPos(spot.mooringPos().getX() + 0.5D, spot.mooringPos().getY() + 0.1D, spot.mooringPos().getZ() + 0.5D);
		fisherman.stopRiding();
		fisherman.setPos(spot.standPos().getX() + 0.5D, spot.standPos().getY(), spot.standPos().getZ() + 0.5D);
		fisherman.getNavigation().stop();
	}

	private static Optional<DockFishingSpot> nearestDockFishingSpot(ServerLevel level, Villager fisherman, List<BlockPos> docks) {
		DockFishingSpot bestSpot = null;
		double bestDistance = Double.MAX_VALUE;

		for (BlockPos dockOrigin : docks) {
			Optional<Direction> facing = SettlementConstruction.dockFacing(level, dockOrigin);

			if (facing.isEmpty()) {
				continue;
			}

			Optional<DockFishingSpot> candidate = createDockFishingSpot(level, dockOrigin, facing.get(), fisherman);

			if (candidate.isEmpty()) {
				continue;
			}

			double distance = fisherman.blockPosition().distSqr(candidate.get().standPos());

			if (distance < bestDistance) {
				bestDistance = distance;
				bestSpot = candidate.get();
			}
		}

		return Optional.ofNullable(bestSpot);
	}

	private static Optional<DockFishingSpot> createDockFishingSpot(ServerLevel level, BlockPos dockOrigin, Direction facing, Villager fisherman) {
		BlockPos standPos = dockOrigin.relative(facing, DOCK_LENGTH_BLOCKS - 2).immutable();
		BlockPos waterLookPos = dockOrigin.relative(facing, DOCK_LENGTH_BLOCKS + 1).immutable();
		BlockPos mooringPos = dockOrigin.relative(facing, DOCK_LENGTH_BLOCKS + 1).immutable();
		BlockPos boatFishingPos = dockOrigin.relative(facing, DOCK_LENGTH_BLOCKS + 4).immutable();
		Optional<Boat> boat = findFishingBoat(level, fisherman, mooringPos);

		if (!isFishableWater(level, waterLookPos)) {
			return Optional.empty();
		}

		return Optional.of(new DockFishingSpot(standPos, waterLookPos, mooringPos, boatFishingPos, boat));
	}

	private static Optional<ShoreFishingSpot> findShoreFishingSpot(ServerLevel level, Villager fisherman, SettlementState settlement) {
		BlockPos anchor = SettlementVillagers.fishermanJobSite(level, fisherman)
			.orElse(settlement.center());
		ShoreFishingSpot bestSpot = null;
		double bestDistance = Double.MAX_VALUE;

		for (int dx = -SHORELINE_SEARCH_RADIUS_BLOCKS; dx <= SHORELINE_SEARCH_RADIUS_BLOCKS; dx++) {
			for (int dz = -SHORELINE_SEARCH_RADIUS_BLOCKS; dz <= SHORELINE_SEARCH_RADIUS_BLOCKS; dz++) {
				BlockPos standPos = surfaceStandPos(level, anchor.offset(dx, 0, dz));

				if (standPos == null) {
					continue;
				}

				for (Direction direction : Direction.Plane.HORIZONTAL) {
					BlockPos waterPos = standPos.relative(direction);

					if (!isFishableWater(level, waterPos)) {
						continue;
					}

					double distance = fisherman.blockPosition().distSqr(standPos);

					if (distance < bestDistance) {
						bestDistance = distance;
						bestSpot = new ShoreFishingSpot(standPos.immutable(), waterPos.immutable());
					}
				}
			}
		}

		return Optional.ofNullable(bestSpot);
	}

	private static BlockPos surfaceStandPos(ServerLevel level, BlockPos columnPos) {
		int centerY = columnPos.getY();

		for (int dy = SHORELINE_Y_SEARCH_BLOCKS; dy >= -SHORELINE_Y_SEARCH_BLOCKS; dy--) {
			BlockPos feetPos = new BlockPos(columnPos.getX(), centerY + dy, columnPos.getZ());
			BlockState belowState = level.getBlockState(feetPos.below());

			if (!belowState.isSolid()) {
				continue;
			}

			if (level.getBlockState(feetPos).isAir() && level.getBlockState(feetPos.above()).isAir()) {
				return feetPos;
			}
		}

		return null;
	}

	private static boolean isFishableWater(ServerLevel level, BlockPos pos) {
		return level.hasChunkAt(pos)
			&& level.getBlockState(pos).liquid()
			&& level.getBlockState(pos.above()).isAir();
	}

	private static Optional<Boat> findFishingBoat(ServerLevel level, Villager fisherman, BlockPos mooringPos) {
		AABB searchBounds = new AABB(mooringPos).inflate(BOAT_SCAN_RADIUS_BLOCKS);

		return level.getEntitiesOfClass(Boat.class, searchBounds, boat ->
			boat.isAlive()
				&& !boat.isRemoved()
				&& boat.getPassengers().stream().allMatch(passenger -> passenger == fisherman)
		).stream()
			.sorted((left, right) -> Double.compare(left.blockPosition().distSqr(mooringPos), right.blockPosition().distSqr(mooringPos)))
			.findFirst();
	}

	private static void moveBoatToward(Boat boat, BlockPos targetPos) {
		double targetX = targetPos.getX() + 0.5D;
		double targetZ = targetPos.getZ() + 0.5D;
		Vec3 current = boat.position();
		double dx = targetX - current.x;
		double dz = targetZ - current.z;
		double distance = Math.sqrt((dx * dx) + (dz * dz));

		if (distance <= 0.001D) {
			boat.setDeltaMovement(Vec3.ZERO);
			return;
		}

		double step = Math.min(BOAT_MOVE_STEP_BLOCKS, distance);
		double moveX = (dx / distance) * step;
		double moveZ = (dz / distance) * step;
		float yaw = (float) (Math.atan2(moveZ, moveX) * (180.0D / Math.PI)) - 90.0F;
		boat.setYRot(yaw);
		boat.setPos(current.x + moveX, targetPos.getY() + 0.1D, current.z + moveZ);
		boat.setDeltaMovement(moveX * 0.35D, 0.0D, moveZ * 0.35D);
	}

	private static void showFishingTool(Villager fisherman) {
		fisherman.setAggressive(false);
		fisherman.setTarget(null);

		if (!fisherman.getMainHandItem().is(Items.FISHING_ROD)) {
			fisherman.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.FISHING_ROD));
		}

		if (fisherman.getOffhandItem().isEmpty()) {
			fisherman.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.IRON_AXE));
		}
	}

	private static Optional<Monster> nearestWaterThreat(ServerLevel level, SettlementState settlement, Villager fisherman) {
		int settlementRadius = SettlementVillagers.settlementRadiusBlocks(settlement) + 8;
		double settlementRadiusSqr = (double) settlementRadius * settlementRadius;
		AABB bounds = new AABB(fisherman.blockPosition()).inflate(WATER_THREAT_SCAN_RADIUS_BLOCKS);
		return level.getEntitiesOfClass(Monster.class, bounds, monster ->
			!monster.isRemoved()
				&& monster.isAlive()
				&& monster.blockPosition().distSqr(settlement.center()) <= settlementRadiusSqr
				&& isWaterApproachThreat(level, monster.blockPosition())
		)
			.stream()
			.min(java.util.Comparator.comparingDouble(fisherman::distanceToSqr));
	}

	private static boolean isWaterApproachThreat(ServerLevel level, BlockPos pos) {
		if (level.getFluidState(pos).is(FluidTags.WATER) || level.getFluidState(pos.below()).is(FluidTags.WATER)) {
			return true;
		}

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			if (level.getFluidState(pos.relative(direction)).is(FluidTags.WATER)) {
				return true;
			}
		}

		return false;
	}

	private static void engageWaterThreat(ServerLevel level, Villager fisherman, Monster threat, long tick) {
		showFishermanAxe(fisherman);
		fisherman.setAggressive(true);
		fisherman.setTarget(threat);
		fisherman.lookAt(threat, 30.0F, 30.0F);

		if (fisherman.distanceToSqr(threat) > WATER_DEFENSE_MELEE_RANGE_SQUARED) {
			fisherman.getNavigation().moveTo(threat.getX(), threat.getY(), threat.getZ(), WATER_DEFENSE_SPEED);
			return;
		}

		fisherman.getNavigation().stop();

		if (!waterDefenseAttackReady(fisherman, tick)) {
			return;
		}

		fisherman.swing(InteractionHand.MAIN_HAND);
		threat.hurt(level.damageSources().mobAttack(fisherman), 5.0F);
		LAST_DEFENSE_ATTACK_TICKS.put(fisherman.getUUID().toString(), tick);
	}

	private static void showFishermanAxe(Villager fisherman) {
		if (!fisherman.getMainHandItem().is(Items.IRON_AXE)) {
			fisherman.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
		}
	}

	private static boolean waterDefenseAttackReady(Villager fisherman, long tick) {
		Long lastAttackTick = LAST_DEFENSE_ATTACK_TICKS.get(fisherman.getUUID().toString());
		return lastAttackTick == null || tick - lastAttackTick >= WATER_DEFENSE_ATTACK_COOLDOWN_TICKS;
	}

	private static boolean shouldRecallForVillageSchedule(long dayTime) {
		return dayTime < VILLAGE_WAKEUP_END_TICK || dayTime >= VILLAGE_GATHERING_START_TICK;
	}

	private static int scaledAmount(double dailyRate, double elapsedDays) {
		return (int) Math.round(dailyRate * elapsedDays * SettlementEconomyRules.WORKER_PRODUCTIVITY_MULTIPLIER);
	}

	private static double scaledLighthouseCount(int lighthouseCount) {
		if (lighthouseCount <= 0) {
			return 0.0D;
		}

		return 1.0D + Math.min(0.30D, Math.max(0, lighthouseCount - 1) * 0.10D);
	}

	private record TimedTask(String taskKey, long tick) {
	}

	private record FishingTask(
		String taskKey,
		BlockPos standPos,
		BlockPos lookPos,
		Optional<Boat> boat,
		Optional<DockFishingSpot> dockSpot
	) {
		private FishingTask {
			standPos = standPos.immutable();
			lookPos = lookPos.immutable();
		}
	}

	private record DockFishingSpot(
		BlockPos standPos,
		BlockPos waterLookPos,
		BlockPos mooringPos,
		BlockPos boatFishingPos,
		Optional<Boat> boat
	) {
		private DockFishingSpot {
			standPos = standPos.immutable();
			waterLookPos = waterLookPos.immutable();
			mooringPos = mooringPos.immutable();
			boatFishingPos = boatFishingPos.immutable();
		}
	}

	private record ShoreFishingSpot(BlockPos standPos, BlockPos waterPos) {
		private ShoreFishingSpot {
			standPos = standPos.immutable();
			waterPos = waterPos.immutable();
		}
	}
}
