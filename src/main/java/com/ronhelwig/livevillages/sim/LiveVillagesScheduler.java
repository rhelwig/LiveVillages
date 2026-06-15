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
	private static final int SHARED_MAINTENANCE_OFFSET = 0;
	private static final int FARMER_MAINTENANCE_OFFSET = 3;
	private static final int RESOURCE_MAINTENANCE_OFFSET = 6;
	private static final int DEFENSE_MAINTENANCE_OFFSET = 10;
	private static final int CONSTRUCTION_MAINTENANCE_OFFSET = 14;
	private static final int REPORT_MAINTENANCE_OFFSET = 17;

	private LiveVillagesScheduler() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(LiveVillagesScheduler::onEndServerTick);
	}

	private static void onEndServerTick(MinecraftServer server) {
		int sessionTick = server.getTickCount();

		if (sessionTick <= 0) {
			return;
		}

		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(server);
		boolean economyCycleDue = (sessionTick % TICKS_BETWEEN_CYCLES) == 0;

		if (isLoadedMaintenancePhase(sessionTick, SHARED_MAINTENANCE_OFFSET)) {
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
		}

		if (isLoadedMaintenancePhase(sessionTick, FARMER_MAINTENANCE_OFFSET)) {
			long start = System.nanoTime();
			savedData.maintainLoadedFarmerState(server, TICKS_BETWEEN_FARMER_MAINTENANCE);
			long elapsed = System.nanoTime() - start;
			if (elapsed > 10_000_000) { // Log if > 10ms
				LiveVillages.LOGGER.warn("Farmer maintenance took {} ms", Math.round(elapsed / 1_000_000.0D));
			}
		}

		if (isLoadedMaintenancePhase(sessionTick, RESOURCE_MAINTENANCE_OFFSET)) {
			long start = System.nanoTime();
			savedData.maintainLoadedResourceState(server, TICKS_BETWEEN_RESOURCE_MAINTENANCE);
			long elapsed = System.nanoTime() - start;
			if (elapsed > 10_000_000) {
				LiveVillages.LOGGER.warn("Resource maintenance took {} ms", Math.round(elapsed / 1_000_000.0D));
			}
		}

		if (isLoadedMaintenancePhase(sessionTick, DEFENSE_MAINTENANCE_OFFSET)) {
			long start = System.nanoTime();
			savedData.maintainLoadedDefenseState(server);
			long elapsed = System.nanoTime() - start;
			if (elapsed > 10_000_000) {
				LiveVillages.LOGGER.warn("Defense maintenance took {} ms", Math.round(elapsed / 1_000_000.0D));
			}
		}

		if (isLoadedMaintenancePhase(sessionTick, CONSTRUCTION_MAINTENANCE_OFFSET)) {
			long start = System.nanoTime();
			savedData.maintainLoadedConstructionState(server, TICKS_BETWEEN_CONSTRUCTION_MAINTENANCE);
			long elapsed = System.nanoTime() - start;
			if (elapsed > 10_000_000) {
				LiveVillages.LOGGER.warn("Construction maintenance took {} ms", Math.round(elapsed / 1_000_000.0D));
			}
		}

		if (isLoadedMaintenancePhase(sessionTick, REPORT_MAINTENANCE_OFFSET)) {
			SettlementProfessionReports.writeLoadedDailyReports(server, savedData.getSettlements());
		}

		if (!economyCycleDue) {
			return;
		}

		long cycleStart = System.nanoTime();
		int settlementCount = savedData.settlementCount();
		int regionCount = savedData.regionCount();
		long economyTick = SettlementClock.persistentTick(server);
		LiveVillages.LOGGER.info("Starting economy cycle: {} settlements in {} regions, processing {} regions", settlementCount, regionCount, REGIONS_PER_CYCLE);
		
		savedData.advanceRoundRobin(server, economyTick, REGIONS_PER_CYCLE);
		VillageAutodetector.tick(server);
		savedData.ensureCustomSettlementVillagers(server, economyTick);
		SettlementProfessionReports.writeLoadedDailyReports(server, savedData.getSettlements());
		
		long cycleElapsed = System.nanoTime() - cycleStart;
		LiveVillages.LOGGER.info("Economy cycle completed in {} ms", Math.round(cycleElapsed / 1_000_000.0D));
	}

	private static boolean isLoadedMaintenancePhase(int currentTick, int offset) {
		return Math.floorMod(currentTick - offset, LOADED_MAINTENANCE_CHECK_INTERVAL) == 0;
	}
}
