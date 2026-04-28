package com.ronhelwig.livevillages.sim;

import java.util.HashMap;
import java.util.Map;

import com.ronhelwig.livevillages.LiveVillages;

final class SettlementPerformanceLog {
	private static final long WARN_THRESHOLD_NANOS = 15_000_000L;
	private static final long MIN_REPEAT_INTERVAL_TICKS = 200L;
	private static final Map<String, Long> LAST_WARNING_TICKS = new HashMap<>();

	private SettlementPerformanceLog() {
	}

	static long start() {
		return System.nanoTime();
	}

	static void warnIfSlow(String operation, SettlementState settlement, long startNanos, long tick) {
		long elapsedNanos = System.nanoTime() - startNanos;

		if (elapsedNanos < WARN_THRESHOLD_NANOS) {
			return;
		}

		String key = operation + "|" + settlement.id();
		long lastWarningTick = LAST_WARNING_TICKS.getOrDefault(key, Long.MIN_VALUE);

		if (tick - lastWarningTick < MIN_REPEAT_INTERVAL_TICKS) {
			return;
		}

		LAST_WARNING_TICKS.put(key, tick);
		LiveVillages.LOGGER.warn(
			"Slow Live Villages task: operation={} settlement={} center={} population={} tick={} elapsed_ms={}",
			operation,
			settlement.id(),
			settlement.center().toShortString(),
			settlement.population().values().stream().mapToInt(Integer::intValue).sum(),
			tick,
			Math.round(elapsedNanos / 1_000_000.0D)
		);
	}
}
