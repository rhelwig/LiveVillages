package com.ronhelwig.livevillages.sim;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class SettlementClock {
	private SettlementClock() {
	}

	public static long persistentTick(MinecraftServer server) {
		if (server == null) {
			return 0L;
		}

		ServerLevel overworld = server.getLevel(Level.OVERWORLD);
		return overworld == null ? server.getTickCount() : overworld.getOverworldClockTime();
	}

	public static long persistentTick(ServerLevel level) {
		return level == null ? 0L : persistentTick(level.getServer());
	}
}
