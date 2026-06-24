package com.ronhelwig.livevillages.sim;

import java.util.Map;
import java.util.Optional;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;

public final class SettlementShepherdWork {
	private SettlementShepherdWork() {
	}

	public static boolean maintainLoadedShepherding(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, int routeCount) {
		return SettlementLivestockWork.maintainLoadedShepherding(level, settlement, stock, routeCount);
	}

	public static void applyLoadedShepherdWork(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		int shepherdCount,
		long previousTick,
		long currentTick
	) {
		SettlementLivestockWork.applyLoadedShepherdWork(level, settlement, stock, shepherdCount, previousTick, currentTick);
	}

	public static Optional<String> loadedShepherdTaskKey(ServerLevel level, Villager villager) {
		return SettlementLivestockWork.loadedShepherdTaskKey(level, villager);
	}
}
