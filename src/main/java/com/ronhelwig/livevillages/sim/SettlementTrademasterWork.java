package com.ronhelwig.livevillages.sim;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;

import com.ronhelwig.livevillages.content.LiveVillagesVillagerProfessions;

public final class SettlementTrademasterWork {
	private static final double WORK_REACH_DISTANCE_SQUARED = 6.25D;
	private static final double WORK_WALK_SPEED = 0.75D;
	private static final long TRADEMASTER_WORK_DECIDE_INTERVAL_TICKS = 240L;
	private static final long TASK_MEMORY_TICKS = 80L;
	private static final long DAY_TICKS = 24_000L;
	private static final Map<String, TimedTask> ACTIVE_TASKS = new HashMap<>();
	private static final Map<String, Long> LAST_REPORTED_DAY = new HashMap<>();

	private SettlementTrademasterWork() {
	}

	public static void maintainLoadedTradeManagement(
		ServerLevel level,
		SettlementState settlement,
		List<SettlementBuildSite> buildSites
	) {
		if (SettlementVillagerWorkSchedule.shouldYieldForVillageSchedule(level)) {
			for (Villager trademaster : SettlementVillagers.nearbyAdultVillagers(level, settlement)) {
				if (!trademaster.getVillagerData().profession().is(LiveVillagesVillagerProfessions.TRADEMASTER)) {
					continue;
				}

				trademaster.getNavigation().stop();
				ACTIVE_TASKS.remove(trademaster.getUUID().toString());
			}

			return;
		}

		for (Villager trademaster : SettlementVillagers.nearbyAdultVillagers(level, settlement)) {
			if (!trademaster.getVillagerData().profession().is(LiveVillagesVillagerProfessions.TRADEMASTER)) {
				continue;
			}

			maintainTradeManagementTask(level, settlement, buildSites, trademaster);
		}
	}

	public static Optional<String> loadedTrademasterTaskKey(ServerLevel level, Villager villager) {
		TimedTask task = ACTIVE_TASKS.get(villager.getUUID().toString());

		if (task == null || level.getServer().getTickCount() - task.tick() > TASK_MEMORY_TICKS) {
			return Optional.empty();
		}

		return Optional.of(task.taskKey());
	}

	private static void maintainTradeManagementTask(
		ServerLevel level,
		SettlementState settlement,
		List<SettlementBuildSite> buildSites,
		Villager trademaster
	) {
		if (!SettlementVillagerWorkSchedule.shouldStartNewWork(
			level,
			trademaster,
			SettlementRoleKeys.TRADEMASTER,
			TRADEMASTER_WORK_DECIDE_INTERVAL_TICKS
		)) {
			return;
		}

		BlockPos workPos = SettlementStockAccess.findStockAccessPos(level, settlement, buildSites)
			.orElse(settlement.center());
		ACTIVE_TASKS.put(trademaster.getUUID().toString(), new TimedTask("managing_trades", level.getServer().getTickCount()));
		SettlementNavigation.moveToRoutineTarget(level, settlement, trademaster, workPos, WORK_WALK_SPEED);

		if (trademaster.blockPosition().distSqr(workPos) > WORK_REACH_DISTANCE_SQUARED) {
			SettlementProfessionDiagnostics.log(
				level,
				settlement,
				SettlementRoleKeys.TRADEMASTER,
				"moving_to_work",
				"villager=" + trademaster.getUUID() + " target=" + workPos.toShortString()
			);
			return;
		}

		recordDailyTradeReview(level, settlement, trademaster);
	}

	private static void recordDailyTradeReview(ServerLevel level, SettlementState settlement, Villager trademaster) {
		long currentDay = Math.floorDiv(level.getOverworldClockTime(), DAY_TICKS);
		String key = settlement.id() + "|" + trademaster.getUUID();

		if (LAST_REPORTED_DAY.getOrDefault(key, Long.MIN_VALUE) == currentDay) {
			return;
		}

		SettlementProfessionReports.recordAccomplished(
			level,
			settlement,
			SettlementRoleKeys.TRADEMASTER,
			trademaster,
			"reviewed settlement stock and trade priorities"
		);
		LAST_REPORTED_DAY.put(key, currentDay);
	}

	private record TimedTask(String taskKey, long tick) {
	}
}
