package com.ronhelwig.livevillages.sim;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

import com.ronhelwig.livevillages.LiveVillages;

public final class LiveVillagesScheduler {
	private static final int BASE_TICKS_BETWEEN_FARMER_MAINTENANCE = 160;
	private static final int BASE_TICKS_BETWEEN_RESOURCE_MAINTENANCE = 80;
	private static final int BASE_TICKS_BETWEEN_CONSTRUCTION_MAINTENANCE = 160;
	public static final int TICKS_BETWEEN_FARMER_MAINTENANCE = SettlementEconomyRules.scaledWorkerTickInterval(BASE_TICKS_BETWEEN_FARMER_MAINTENANCE);
	public static final int TICKS_BETWEEN_RESOURCE_MAINTENANCE = SettlementEconomyRules.scaledWorkerTickInterval(BASE_TICKS_BETWEEN_RESOURCE_MAINTENANCE);
	public static final int TICKS_BETWEEN_CONSTRUCTION_MAINTENANCE = SettlementEconomyRules.scaledWorkerTickInterval(BASE_TICKS_BETWEEN_CONSTRUCTION_MAINTENANCE);
	public static final int TICKS_BETWEEN_CYCLES = 200;
	public static final int LOADED_MAINTENANCE_CHECK_INTERVAL = 20;
	public static final int REGIONS_PER_CYCLE = 3;

	private LiveVillagesScheduler() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(LiveVillagesScheduler::onEndServerTick);
	}

	private static void onEndServerTick(MinecraftServer server) {
		int currentTick = server.getTickCount();

		if (currentTick <= 0) {
			return;
		}

		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(server);
		boolean economyCycleDue = (currentTick % TICKS_BETWEEN_CYCLES) == 0;

		if ((currentTick % LOADED_MAINTENANCE_CHECK_INTERVAL) == 0) {
			long start = System.nanoTime();
			savedData.maintainSharedMapKnowledge(server);
			long elapsed = System.nanoTime() - start;
			if (elapsed > 10_000_000) {
				LiveVillages.LOGGER.warn("Shared map maintenance took {} ms", Math.round(elapsed / 1_000_000.0D));
			}

			start = System.nanoTime();
			OutpostTrust.maintainServer(server, savedData);
			elapsed = System.nanoTime() - start;
			if (elapsed > 10_000_000) {
				LiveVillages.LOGGER.warn("Outpost trust maintenance took {} ms", Math.round(elapsed / 1_000_000.0D));
			}

			start = System.nanoTime();
			if (OutpostRaids.maintain(server, savedData, OutpostRaids.currentRaidTick(server))) {
				savedData.setDirty();
			}
			elapsed = System.nanoTime() - start;
			if (elapsed > 10_000_000) {
				LiveVillages.LOGGER.warn("Outpost raid maintenance took {} ms", Math.round(elapsed / 1_000_000.0D));
			}

			start = System.nanoTime();
			savedData.maintainLoadedFarmerState(server, TICKS_BETWEEN_FARMER_MAINTENANCE);
			elapsed = System.nanoTime() - start;
			if (elapsed > 10_000_000) { // Log if > 10ms
				LiveVillages.LOGGER.warn("Farmer maintenance took {} ms", Math.round(elapsed / 1_000_000.0D));
			}

			start = System.nanoTime();
			savedData.maintainLoadedResourceState(server, TICKS_BETWEEN_RESOURCE_MAINTENANCE);
			elapsed = System.nanoTime() - start;
			if (elapsed > 10_000_000) {
				LiveVillages.LOGGER.warn("Resource maintenance took {} ms", Math.round(elapsed / 1_000_000.0D));
			}

			start = System.nanoTime();
			savedData.maintainLoadedDefenseState(server);
			elapsed = System.nanoTime() - start;
			if (elapsed > 10_000_000) {
				LiveVillages.LOGGER.warn("Defense maintenance took {} ms", Math.round(elapsed / 1_000_000.0D));
			}

			start = System.nanoTime();
			savedData.maintainLoadedConstructionState(server, TICKS_BETWEEN_CONSTRUCTION_MAINTENANCE);
			elapsed = System.nanoTime() - start;
			if (elapsed > 10_000_000) {
				LiveVillages.LOGGER.warn("Construction maintenance took {} ms", Math.round(elapsed / 1_000_000.0D));
			}

			SettlementProfessionReports.writeLoadedDailyReports(server, savedData.getSettlements());
		}

		if (!economyCycleDue) {
			return;
		}

		long cycleStart = System.nanoTime();
		int settlementCount = savedData.settlementCount();
		int regionCount = savedData.regionCount();
		LiveVillages.LOGGER.info("Starting economy cycle: {} settlements in {} regions, processing {} regions", settlementCount, regionCount, REGIONS_PER_CYCLE);
		
		savedData.advanceRoundRobin(server, currentTick, REGIONS_PER_CYCLE);
		VillageAutodetector.tick(server);
		savedData.ensureCustomSettlementVillagers(server, currentTick);
		SettlementProfessionReports.writeLoadedDailyReports(server, savedData.getSettlements());
		
		long cycleElapsed = System.nanoTime() - cycleStart;
		LiveVillages.LOGGER.info("Economy cycle completed in {} ms", Math.round(cycleElapsed / 1_000_000.0D));
	}
}
