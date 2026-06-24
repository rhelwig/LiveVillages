package com.ronhelwig.livevillages.sim;

import java.util.Map;
import java.util.Optional;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;

public final class SettlementButcherWork {
	private SettlementButcherWork() {
	}

	public static void maintainLoadedButchery(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, int routeCount) {
		SettlementLivestockWork.maintainLoadedButchery(level, settlement, stock, routeCount);
	}

	public static void applyLoadedButcherWork(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		int butcherCount,
		long previousTick,
		long currentTick
	) {
		SettlementLivestockWork.applyLoadedButcherWork(level, settlement, stock, butcherCount, previousTick, currentTick);
	}

	public static Optional<String> loadedButcherTaskKey(ServerLevel level, Villager villager) {
		return SettlementLivestockWork.loadedLivestockTaskKey(level, villager);
	}
}
