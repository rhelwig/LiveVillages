package com.ronhelwig.livevillages.sim;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class SettlementPortmasterWork {
	private static final double HARBOR_WALK_SPEED = 0.8D;
	private static final double HARBOR_REACH_DISTANCE_SQUARED = 9.0D;
	private static final long TASK_MEMORY_TICKS = 40L;
	private static final long HARBOR_DECIDE_INTERVAL_TICKS = 320L;
	private static final long DAY_TICKS = 24_000L;
	private static final long VILLAGE_WAKEUP_END_TICK = 2_000L;
	private static final long LIGHTHOUSE_RELIGHT_START_TICK = 8_400L;
	private static final long VILLAGE_GATHERING_START_TICK = 9_000L;
	private static final long WARNING_BELL_INTERVAL_TICKS = 200L;
	private static final int HARBOR_THREAT_SCAN_RADIUS_BLOCKS = 32;
	private static final Map<String, TimedTask> ACTIVE_TASKS = new HashMap<>();
	private static final Map<BlockPos, Long> LAST_WARNING_BELL_TICKS = new HashMap<>();

	private SettlementPortmasterWork() {
	}

	public static void maintainLoadedHarbor(ServerLevel level, SettlementState settlement) {
		List<Villager> portmasters = SettlementVillagers.nearbyPortmasters(level, settlement);

		if (portmasters.isEmpty()) {
			return;
		}

		List<BlockPos> docks = SettlementConstruction.findDockOrigins(level, settlement);
		List<BlockPos> lighthouses = SettlementConstruction.findLighthouseTops(level, settlement);
		long tick = level.getServer().getTickCount();

		if (SettlementVillagerWorkSchedule.shouldYieldForVillageSchedule(level)) {
			for (Villager portmaster : portmasters) {
				portmaster.getNavigation().stop();
				ACTIVE_TASKS.remove(portmaster.getUUID().toString());
			}

			return;
		}

		for (Villager portmaster : portmasters) {
			if (!SettlementVillagerWorkSchedule.shouldStartNewWork(level, portmaster, "harbor", HARBOR_DECIDE_INTERVAL_TICKS)) {
				if (SettlementVillagerWorkSchedule.isTakingBreak(level, portmaster)) {
					portmaster.getNavigation().stop();
				}

				continue;
			}

			HarborTask task = chooseHarborTask(level, settlement, portmaster, docks, lighthouses, tick);
			ACTIVE_TASKS.put(portmaster.getUUID().toString(), new TimedTask(task.taskKey(), tick));
			showPortmasterTool(portmaster);
			portmaster.getNavigation().moveTo(task.targetPos().getX() + 0.5D, task.targetPos().getY(), task.targetPos().getZ() + 0.5D, HARBOR_WALK_SPEED);

			if (portmaster.distanceToSqr(task.targetPos().getX() + 0.5D, task.targetPos().getY() + 0.5D, task.targetPos().getZ() + 0.5D) <= HARBOR_REACH_DISTANCE_SQUARED) {
				portmaster.getNavigation().stop();
				handleArrivedTask(level, settlement, portmaster, task, tick);
			}
		}
	}

	public static Optional<String> loadedPortmasterTaskKey(ServerLevel level, Villager villager) {
		TimedTask task = ACTIVE_TASKS.get(villager.getUUID().toString());

		if (task == null || level.getServer().getTickCount() - task.tick() > TASK_MEMORY_TICKS) {
			return Optional.empty();
		}

		return Optional.of(task.taskKey());
	}

	private static HarborTask chooseHarborTask(
		ServerLevel level,
		SettlementState settlement,
		Villager portmaster,
		List<BlockPos> docks,
		List<BlockPos> lighthouses,
		long tick
	) {
		BlockPos jobSite = SettlementVillagers.portmasterJobSite(level, portmaster).orElse(settlement.center());
		long dayTime = Math.floorMod(level.getOverworldClockTime(), DAY_TICKS);

		if (docks.isEmpty()) {
			return new HarborTask(jobSite, jobSite, "planning_docks");
		}

		Optional<BlockPos> warningTarget = threatenedLighthouse(level, settlement, portmaster, lighthouses);

		if (warningTarget.isPresent()) {
			return lighthouseTask(level, warningTarget.get(), "warning_harbor");
		}

		Optional<BlockPos> extinguishTarget = lighthouseNeedingLightState(level, lighthouses, false, dayTime < VILLAGE_WAKEUP_END_TICK);

		if (extinguishTarget.isPresent()) {
			return lighthouseTask(level, extinguishTarget.get(), "extinguishing_lighthouse");
		}

		Optional<BlockPos> relightTarget = lighthouseNeedingLightState(
			level,
			lighthouses,
			true,
			dayTime >= LIGHTHOUSE_RELIGHT_START_TICK && dayTime < VILLAGE_GATHERING_START_TICK
		);

		if (relightTarget.isPresent()) {
			return lighthouseTask(level, relightTarget.get(), "lighting_lighthouse");
		}

		if (!lighthouses.isEmpty() && shouldInspectLighthouse(portmaster, tick)) {
			return lighthouseTask(level, nearestTarget(portmaster, lighthouses).orElse(jobSite), "checking_lighthouse");
		}

		BlockPos dockTarget = nearestTarget(portmaster, docks).orElse(jobSite);
		return new HarborTask(dockTarget, dockTarget, "inspecting_docks");
	}

	private static boolean shouldInspectLighthouse(Villager portmaster, long tick) {
		long phase = Math.floorMod(portmaster.getUUID().hashCode(), 3);
		return Math.floorMod(tick / 240L + phase, 3L) == 0L;
	}

	private static void handleArrivedTask(ServerLevel level, SettlementState settlement, Villager portmaster, HarborTask task, long tick) {
		BlockState state = level.getBlockState(task.actionPos());

		if (!state.hasProperty(CampfireBlock.LIT)) {
			return;
		}

		switch (task.taskKey()) {
			case "lighting_lighthouse" -> setLighthouseLit(level, task.actionPos(), state, true);
			case "extinguishing_lighthouse" -> setLighthouseLit(level, task.actionPos(), state, false);
			case "warning_harbor" -> {
				setLighthouseLit(level, task.actionPos(), state, true);

				if (!nearbyHostiles(level, settlement, task.actionPos()).isEmpty()
					&& tick - LAST_WARNING_BELL_TICKS.getOrDefault(task.actionPos(), Long.MIN_VALUE) >= WARNING_BELL_INTERVAL_TICKS) {
					level.playSound(null, task.actionPos(), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.2F, 0.9F);
					LAST_WARNING_BELL_TICKS.put(task.actionPos().immutable(), tick);
				}
			}
			default -> {
			}
		}
	}

	private static HarborTask lighthouseTask(ServerLevel level, BlockPos campfirePos, String taskKey) {
		BlockPos targetPos = lighthouseAccessPos(level, campfirePos).orElse(campfirePos);
		return new HarborTask(targetPos, campfirePos, taskKey);
	}

	private static void setLighthouseLit(ServerLevel level, BlockPos pos, BlockState state, boolean lit) {
		if (!state.hasProperty(CampfireBlock.LIT) || state.getValue(CampfireBlock.LIT) == lit) {
			return;
		}

		level.setBlock(pos, state.setValue(CampfireBlock.LIT, lit), 3);
	}

	private static Optional<BlockPos> lighthouseNeedingLightState(ServerLevel level, List<BlockPos> lighthouses, boolean lit, boolean activeWindow) {
		if (!activeWindow) {
			return Optional.empty();
		}

		return lighthouses.stream()
			.filter(pos -> {
				BlockState state = level.getBlockState(pos);
				return state.hasProperty(CampfireBlock.LIT) && state.getValue(CampfireBlock.LIT) != lit;
			})
			.map(BlockPos::immutable)
			.findFirst();
	}

	private static Optional<BlockPos> threatenedLighthouse(ServerLevel level, SettlementState settlement, Villager portmaster, List<BlockPos> lighthouses) {
		return lighthouses.stream()
			.filter(pos -> !nearbyHostiles(level, settlement, pos).isEmpty())
			.min(Comparator.comparingDouble(pos -> pos.distSqr(portmaster.blockPosition())))
			.map(BlockPos::immutable);
	}

	private static Optional<BlockPos> lighthouseAccessPos(ServerLevel level, BlockPos campfirePos) {
		BlockPos baseCenter = campfirePos.below(8);

		for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
			BlockPos candidate = baseCenter.relative(direction, 2);
			BlockPos feetPos = new BlockPos(candidate.getX(), baseCenter.getY(), candidate.getZ());

			if (level.getBlockState(feetPos.below()).isSolid()
				&& level.getBlockState(feetPos).isAir()
				&& level.getBlockState(feetPos.above()).isAir()) {
				return Optional.of(feetPos.immutable());
			}
		}

		return Optional.empty();
	}

	private static List<Monster> nearbyHostiles(ServerLevel level, SettlementState settlement, BlockPos targetPos) {
		int settlementRadius = SettlementVillagers.settlementRadiusBlocks(settlement);
		int radius = Math.max(24, Math.min(HARBOR_THREAT_SCAN_RADIUS_BLOCKS, settlementRadius + 8));
		double radiusSqr = radius * radius;
		AABB bounds = new AABB(targetPos).inflate(radius);
		return level.getEntitiesOfClass(Monster.class, bounds, monster ->
			!monster.isRemoved()
				&& monster.isAlive()
				&& monster.blockPosition().distSqr(targetPos) <= radiusSqr
		);
	}

	private static Optional<BlockPos> nearestTarget(Villager villager, List<BlockPos> targets) {
		return targets.stream()
			.min(Comparator.comparingDouble(pos -> pos.distSqr(villager.blockPosition())))
			.map(BlockPos::immutable);
	}

	private static void showPortmasterTool(Villager portmaster) {
		if (!portmaster.getMainHandItem().is(Items.SPYGLASS)) {
			portmaster.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SPYGLASS));
		}
	}

	private record HarborTask(BlockPos targetPos, BlockPos actionPos, String taskKey) {
		private HarborTask {
			targetPos = targetPos.immutable();
			actionPos = actionPos.immutable();
		}
	}

	private record TimedTask(String taskKey, long tick) {
	}
}
