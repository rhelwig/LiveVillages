package com.ronhelwig.livevillages.sim;

import java.util.Optional;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;

public final class SettlementVillagerWorkSchedule {
	private static final long DAY_TICKS = 24_000L;
	private static final long VILLAGE_GATHERING_START_TICK = 9_000L;
	private static final long[] BREAK_START_TICKS = {2_600L, 5_200L, 7_600L};
	private static final long BREAK_SPREAD_TICKS = 900L;
	private static final long BREAK_DURATION_TICKS = 240L;
	private static final long DEFAULT_DECIDE_WINDOW_TICKS = 320L;

	private SettlementVillagerWorkSchedule() {
	}

	public static boolean shouldStartNewWork(ServerLevel level, Villager villager, String workKey, long decideIntervalTicks) {
		return isProfessionWorkTime(level) && !isTakingBreak(level, villager) && isDecideTurn(level, villager, workKey, decideIntervalTicks);
	}

	public static boolean isProfessionWorkTime(ServerLevel level) {
		long dayTime = Math.floorMod(level.getOverworldClockTime(), DAY_TICKS);
		return dayTime < VILLAGE_GATHERING_START_TICK;
	}

	public static boolean shouldYieldForVillageSchedule(ServerLevel level) {
		long dayTime = Math.floorMod(level.getOverworldClockTime(), DAY_TICKS);
		return dayTime >= VILLAGE_GATHERING_START_TICK;
	}

	public static boolean isTakingBreak(ServerLevel level, Villager villager) {
		if (villager.isBaby() || villager.isSleeping()) {
			return false;
		}

		long dayTime = Math.floorMod(level.getOverworldClockTime(), DAY_TICKS);
		long offset = stableModulo(villager.getUUID() + "|break", BREAK_SPREAD_TICKS);

		for (long breakStartTick : BREAK_START_TICKS) {
			long start = Math.floorMod(breakStartTick + offset, DAY_TICKS);

			if (isWithinDayWindow(dayTime, start, BREAK_DURATION_TICKS)) {
				return true;
			}
		}

		return false;
	}

	public static Optional<String> breakTaskKey(ServerLevel level, Villager villager) {
		return isTakingBreak(level, villager) ? Optional.of("taking_break") : Optional.empty();
	}

	private static boolean isDecideTurn(ServerLevel level, Villager villager, String workKey, long decideIntervalTicks) {
		if (decideIntervalTicks <= DEFAULT_DECIDE_WINDOW_TICKS) {
			return true;
		}

		long tick = level.getServer().getTickCount();
		long phase = stableModulo(villager.getUUID() + "|" + workKey, decideIntervalTicks);
		return Math.floorMod(tick - phase, decideIntervalTicks) < DEFAULT_DECIDE_WINDOW_TICKS;
	}

	private static boolean isWithinDayWindow(long dayTime, long start, long duration) {
		long end = start + duration;

		if (end <= DAY_TICKS) {
			return dayTime >= start && dayTime < end;
		}

		return dayTime >= start || dayTime < Math.floorMod(end, DAY_TICKS);
	}

	private static long stableModulo(String key, long modulo) {
		return Math.floorMod(stableHash(key), modulo);
	}

	private static long stableHash(String key) {
		long hash = 1125899906842597L;

		for (int i = 0; i < key.length(); i++) {
			hash = (hash * 31L) + key.charAt(i);
		}

		return hash;
	}
}
