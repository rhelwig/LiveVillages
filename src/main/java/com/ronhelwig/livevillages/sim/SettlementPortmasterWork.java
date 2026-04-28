package com.ronhelwig.livevillages.sim;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class SettlementPortmasterWork {
	private static final double HARBOR_WALK_SPEED = 0.8D;
	private static final double HARBOR_REACH_DISTANCE_SQUARED = 9.0D;
	private static final long TASK_MEMORY_TICKS = 40L;
	private static final long HARBOR_DECIDE_INTERVAL_TICKS = 320L;
	private static final Map<String, TimedTask> ACTIVE_TASKS = new HashMap<>();

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

		if (docks.isEmpty()) {
			return new HarborTask(jobSite, "planning_docks");
		}

		if (!lighthouses.isEmpty() && shouldInspectLighthouse(portmaster, tick)) {
			return new HarborTask(nearestTarget(portmaster, lighthouses).orElse(jobSite), "checking_lighthouse");
		}

		return new HarborTask(nearestTarget(portmaster, docks).orElse(jobSite), "inspecting_docks");
	}

	private static boolean shouldInspectLighthouse(Villager portmaster, long tick) {
		long phase = Math.floorMod(portmaster.getUUID().hashCode(), 3);
		return Math.floorMod(tick / 240L + phase, 3L) == 0L;
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

	private record HarborTask(BlockPos targetPos, String taskKey) {
		private HarborTask {
			targetPos = targetPos.immutable();
		}
	}

	private record TimedTask(String taskKey, long tick) {
	}
}
