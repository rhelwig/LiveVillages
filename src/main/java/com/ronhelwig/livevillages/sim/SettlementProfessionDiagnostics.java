package com.ronhelwig.livevillages.sim;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.server.level.ServerLevel;

import com.ronhelwig.livevillages.LiveVillages;

public final class SettlementProfessionDiagnostics {
	private static final long DIAGNOSTIC_INTERVAL_TICKS = 1_200L;
	private static final Map<String, Long> LAST_LOG_TICK = new LinkedHashMap<>();

	private SettlementProfessionDiagnostics() {
	}

	public static void log(ServerLevel level, SettlementState settlement, String profession, String reason, String details) {
		if (level == null || settlement == null || profession == null || reason == null) {
			return;
		}

		long tick = level.getServer().getTickCount();
		String key = settlement.dimension().identifier() + "|" + settlement.id() + "|" + profession + "|" + reason;
		long previousTick = LAST_LOG_TICK.getOrDefault(key, Long.MIN_VALUE);
		if (previousTick != Long.MIN_VALUE && tick - previousTick < DIAGNOSTIC_INTERVAL_TICKS) {
			return;
		}

		LAST_LOG_TICK.put(key, tick);
		LiveVillages.LOGGER.info(
			"Profession diagnostic: settlement={} profession={} reason={} {}",
			settlement.id(),
			profession,
			reason,
			details == null || details.isBlank() ? "" : details
		);
	}
}
