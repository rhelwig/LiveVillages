package com.ronhelwig.livevillages.sim;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;

import com.ronhelwig.livevillages.LiveVillages;
import com.ronhelwig.livevillages.LiveVillagesGameRules;
import com.ronhelwig.livevillages.content.LiveVillagesVillagerProfessions;

public final class SettlementProfessionReports {
	private static final long DAY_TICKS = 24_000L;
	private static final long REPORT_WRITE_START_TICK = 23_000L;
	private static final Map<ReportKey, DailyReport> REPORTS = new LinkedHashMap<>();
	private static final Set<ReportKey> WRITTEN_REPORTS = new LinkedHashSet<>();
	private static final Map<SettlementReportKey, Long> LAST_OBSERVED_DAY = new LinkedHashMap<>();
	private static final List<ProfessionReportRole> PROFESSION_REPORT_ROLES = List.of(
		new ProfessionReportRole(SettlementRoleKeys.UNEMPLOYED, null),
		new ProfessionReportRole("nitwit", null),
		new ProfessionReportRole("child", null),
		new ProfessionReportRole("armorer", poiType -> poiType.is(PoiTypes.ARMORER)),
		new ProfessionReportRole(SettlementRoleKeys.BAKER, poiType -> poiType.is(LiveVillagesVillagerProfessions.BAKER_POI)),
		new ProfessionReportRole(SettlementRoleKeys.BEEKEEPER, poiType -> poiType.is(LiveVillagesVillagerProfessions.BEEKEEPER_POI)),
		new ProfessionReportRole(SettlementRoleKeys.BUTCHER, poiType -> poiType.is(PoiTypes.BUTCHER)),
		new ProfessionReportRole(SettlementRoleKeys.CARPENTER, poiType -> poiType.is(LiveVillagesVillagerProfessions.CARPENTER_POI)),
		new ProfessionReportRole(SettlementRoleKeys.CARTOGRAPHER, poiType -> poiType.is(PoiTypes.CARTOGRAPHER)),
		new ProfessionReportRole("cleric", poiType -> poiType.is(PoiTypes.CLERIC)),
		new ProfessionReportRole(SettlementRoleKeys.FARMER, poiType -> poiType.is(PoiTypes.FARMER)),
		new ProfessionReportRole(SettlementRoleKeys.FISHERMAN, poiType -> poiType.is(PoiTypes.FISHERMAN)),
		new ProfessionReportRole(SettlementRoleKeys.FLETCHER, poiType -> poiType.is(PoiTypes.FLETCHER)),
		new ProfessionReportRole(SettlementRoleKeys.FORESTER, poiType -> poiType.is(LiveVillagesVillagerProfessions.FORESTER_POI)),
		new ProfessionReportRole(SettlementRoleKeys.GARDENER, poiType -> poiType.is(LiveVillagesVillagerProfessions.GARDENER_POI)),
		new ProfessionReportRole(SettlementRoleKeys.GUARD, poiType -> poiType.is(LiveVillagesVillagerProfessions.GUARD_POI)),
		new ProfessionReportRole("leatherworker", poiType -> poiType.is(PoiTypes.LEATHERWORKER)),
		new ProfessionReportRole("librarian", poiType -> poiType.is(PoiTypes.LIBRARIAN)),
		new ProfessionReportRole(SettlementRoleKeys.MASON, poiType -> poiType.is(PoiTypes.MASON)),
		new ProfessionReportRole(SettlementRoleKeys.MINER, poiType -> poiType.is(LiveVillagesVillagerProfessions.MINER_POI)),
		new ProfessionReportRole(SettlementRoleKeys.PORTMASTER, poiType -> poiType.is(LiveVillagesVillagerProfessions.PORTMASTER_POI)),
		new ProfessionReportRole(SettlementRoleKeys.ROADWRIGHT, poiType -> poiType.is(LiveVillagesVillagerProfessions.ROADWRIGHT_POI)),
		new ProfessionReportRole(SettlementRoleKeys.SCRIBE, poiType -> poiType.is(LiveVillagesVillagerProfessions.SCRIBE_POI)),
		new ProfessionReportRole("shepherd", poiType -> poiType.is(PoiTypes.SHEPHERD)),
		new ProfessionReportRole("toolsmith", poiType -> poiType.is(PoiTypes.TOOLSMITH)),
		new ProfessionReportRole(SettlementRoleKeys.TRADEMASTER, poiType -> poiType.is(LiveVillagesVillagerProfessions.TRADEMASTER_POI)),
		new ProfessionReportRole("weaponsmith", poiType -> poiType.is(PoiTypes.WEAPONSMITH))
	);

	private SettlementProfessionReports() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register(SettlementProfessionReports::onAfterDeath);
	}

	public static void recordProduced(ServerLevel level, SettlementState settlement, String roleKey, String goodsKey, int amount) {
		recordGoods(level, settlement, roleKey, null, GoodsAction.PRODUCED, goodsKey, amount);
	}

	public static void recordProduced(ServerLevel level, SettlementState settlement, String roleKey, Villager villager, String goodsKey, int amount) {
		recordGoods(level, settlement, roleKey, villager, GoodsAction.PRODUCED, goodsKey, amount);
	}

	public static void recordProducedForWorkers(
		ServerLevel level,
		SettlementState settlement,
		String roleKey,
		List<Villager> workers,
		String goodsKey,
		int amount
	) {
		recordGoodsForWorkers(level, settlement, roleKey, workers, GoodsAction.PRODUCED, goodsKey, amount);
	}

	public static void recordMined(ServerLevel level, SettlementState settlement, String roleKey, Villager villager, String goodsKey, int amount) {
		recordGoods(level, settlement, roleKey, villager, GoodsAction.MINED, goodsKey, amount);
	}

	public static void recordMinedBlock(
		ServerLevel level,
		SettlementState settlement,
		String roleKey,
		Villager villager,
		BlockState blockState,
		Map<String, Integer> drops
	) {
		recordBlockWork(level, settlement, roleKey, villager, blockState, drops, true);
	}

	public static void recordHarvestedBlock(
		ServerLevel level,
		SettlementState settlement,
		String roleKey,
		Villager villager,
		BlockState blockState,
		Map<String, Integer> drops
	) {
		recordBlockWork(level, settlement, roleKey, villager, blockState, drops, false);
	}

	public static void recordMinedForWorkers(
		ServerLevel level,
		SettlementState settlement,
		String roleKey,
		List<Villager> workers,
		String goodsKey,
		int amount
	) {
		recordGoodsForWorkers(level, settlement, roleKey, workers, GoodsAction.MINED, goodsKey, amount);
	}

	public static void recordRecovered(ServerLevel level, SettlementState settlement, String roleKey, Villager villager, String goodsKey, int amount) {
		recordGoods(level, settlement, roleKey, villager, GoodsAction.RECOVERED, goodsKey, amount);
	}

	public static void recordConsumed(ServerLevel level, SettlementState settlement, String roleKey, String goodsKey, int amount) {
		recordGoods(level, settlement, roleKey, null, GoodsAction.CONSUMED, goodsKey, amount);
	}

	public static void recordConsumed(ServerLevel level, SettlementState settlement, String roleKey, Villager villager, String goodsKey, int amount) {
		recordGoods(level, settlement, roleKey, villager, GoodsAction.CONSUMED, goodsKey, amount);
	}

	public static void recordConsumedForWorkers(
		ServerLevel level,
		SettlementState settlement,
		String roleKey,
		List<Villager> workers,
		String goodsKey,
		int amount
	) {
		recordGoodsForWorkers(level, settlement, roleKey, workers, GoodsAction.CONSUMED, goodsKey, amount);
	}

	public static void recordConversion(
		ServerLevel level,
		SettlementState settlement,
		String roleKey,
		String inputGoodsKey,
		int inputAmount,
		String outputGoodsKey,
		int outputAmount
	) {
		recordConsumed(level, settlement, roleKey, inputGoodsKey, inputAmount);
		recordProduced(level, settlement, roleKey, outputGoodsKey, outputAmount);
	}

	public static void recordConversion(
		ServerLevel level,
		SettlementState settlement,
		String roleKey,
		Villager villager,
		String inputGoodsKey,
		int inputAmount,
		String outputGoodsKey,
		int outputAmount,
		String accomplishment
	) {
		recordConsumed(level, settlement, roleKey, villager, inputGoodsKey, inputAmount);
		recordProduced(level, settlement, roleKey, villager, outputGoodsKey, outputAmount);
		recordAccomplished(level, settlement, roleKey, villager, accomplishment);
	}

	public static void recordAccomplished(ServerLevel level, SettlementState settlement, String roleKey, Villager villager, String accomplishment) {
		if (!isValidWorkerRecord(level, settlement, roleKey, villager) || accomplishment == null || accomplishment.isBlank()) {
			return;
		}

		VillagerProgress progress = reportFor(level, settlement).villager(villager, roleKey);
		progress.observe(villager, roleKey, level.getServer().getTickCount());
		progress.accomplishments.add(accomplishment);
	}

	public static void recordConsumedDeltas(
		ServerLevel level,
		SettlementState settlement,
		String roleKey,
		Villager villager,
		Map<String, Integer> beforeStock,
		Map<String, Integer> afterStock
	) {
		recordStockDeltas(level, settlement, roleKey, villager, beforeStock, afterStock, null);
	}

	public static void recordStockDeltas(
		ServerLevel level,
		SettlementState settlement,
		String roleKey,
		Villager villager,
		Map<String, Integer> beforeStock,
		Map<String, Integer> afterStock,
		String gainedAction
	) {
		if (!isValidWorkerRecord(level, settlement, roleKey, villager) || beforeStock == null || afterStock == null) {
			return;
		}

		Set<String> goodsKeys = new LinkedHashSet<>();
		goodsKeys.addAll(beforeStock.keySet());
		goodsKeys.addAll(afterStock.keySet());

		for (String goodsKey : goodsKeys) {
			int before = beforeStock.getOrDefault(goodsKey, 0);
			int after = afterStock.getOrDefault(goodsKey, 0);
			int delta = after - before;

			if (delta > 0) {
				if ("mined".equals(gainedAction)) {
					recordMined(level, settlement, roleKey, villager, goodsKey, delta);
				} else if ("recovered".equals(gainedAction)) {
					recordRecovered(level, settlement, roleKey, villager, goodsKey, delta);
				} else if ("produced".equals(gainedAction)) {
					recordProduced(level, settlement, roleKey, villager, goodsKey, delta);
				}
			} else if (delta < 0) {
				recordConsumed(level, settlement, roleKey, villager, goodsKey, -delta);
			}
		}
	}

	public static void recordTradeBatch(ServerLevel level, SettlementState from, SettlementState to, List<TradeShipment> shipments) {
		if (level == null || !reportsEnabled(level) || from == null || to == null || shipments == null || shipments.isEmpty()) {
			return;
		}

		String fromSummary = tradeSummaryForSettlement(from.id(), shipments);
		String toSummary = tradeSummaryForSettlement(to.id(), shipments);

		if (!fromSummary.isBlank()) {
			reportFor(level, from).tradeSummaries.add(fromSummary);
		}

		if (!toSummary.isBlank() && !to.id().equals(from.id())) {
			reportFor(level, to).tradeSummaries.add(toSummary);
		}
	}

	public static void writeLoadedDailyReports(MinecraftServer server, Iterable<SettlementState> settlements) {
		for (SettlementState settlement : settlements) {
			ServerLevel level = server.getLevel(settlement.dimension());

			if (level == null
				|| !LiveVillagesGameRules.dailySettlementReportsEnabled(level)
				|| !level.isLoaded(settlement.center())
				|| !level.isPositionEntityTicking(settlement.center())) {
				continue;
			}

			observeAndWriteLoadedDailyReport(level, settlement);
		}
	}

	private static void observeAndWriteLoadedDailyReport(ServerLevel level, SettlementState settlement) {
		long clockTime = level.getOverworldClockTime();
		long dayTime = Math.floorMod(clockTime, DAY_TICKS);
		long currentDayIndex = dayIndex(level);
		SettlementReportKey settlementKey = settlementReportKey(level, settlement);
		Long previousObservedDayIndex = LAST_OBSERVED_DAY.get(settlementKey);

		if (previousObservedDayIndex != null && currentDayIndex > previousObservedDayIndex) {
			for (long reportDayIndex = previousObservedDayIndex; reportDayIndex < currentDayIndex; reportDayIndex++) {
				writeLoadedDailyReport(level, settlement, reportDayIndex);
			}
		}

		observeLoadedVillagers(level, settlement, currentDayIndex);
		LAST_OBSERVED_DAY.put(settlementKey, currentDayIndex);

		if (dayTime >= REPORT_WRITE_START_TICK) {
			writeLoadedDailyReport(level, settlement, currentDayIndex);
		}
	}

	private static void observeLoadedVillagers(ServerLevel level, SettlementState settlement, long currentDayIndex) {
		DailyReport report = reportFor(level, settlement, currentDayIndex);
		long tick = level.getServer().getTickCount();

		for (Villager villager : SettlementVillagers.reportableVillagers(level, settlement)) {
			String roleKey = roleKeyFor(villager);
			VillagerProgress progress = report.villager(villager, roleKey);
			progress.observe(villager, roleKey, tick);
		}
	}

	private static void writeLoadedDailyReport(ServerLevel level, SettlementState settlement, long reportDayIndex) {
		ReportKey key = reportKey(level, settlement, reportDayIndex);

		if (WRITTEN_REPORTS.contains(key)) {
			return;
		}

		DailyReport report = REPORTS.getOrDefault(key, DailyReport.EMPTY);

		try {
			Path exportPath = writeReportFile(level, settlement, key.dayIndex(), report);
			Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT);
			String relativePath = worldRoot.relativize(exportPath).toString().replace('\\', '/');
			LiveVillages.LOGGER.info("Wrote daily village report for {} day {} to {}", settlement.name(), dayNumber(key.dayIndex()), relativePath);
			WRITTEN_REPORTS.add(key);
			pruneOldReports(key.dayIndex());
		} catch (IOException exception) {
			LiveVillages.LOGGER.warn("Failed to write daily village report for {}: {}", settlement.id(), exception.getMessage());
		}
	}

	private static Path writeReportFile(ServerLevel level, SettlementState settlement, long dayIndex, DailyReport report) throws IOException {
		Path exportDir = level.getServer().getWorldPath(LevelResource.ROOT).resolve("livevillages_exports");
		Files.createDirectories(exportDir);
		Path exportPath = exportDir.resolve(sanitizeFileLabel(settlement.name()) + "-report-" + dayNumber(dayIndex) + ".txt");
		Files.writeString(exportPath, renderReport(level, settlement, dayIndex, report), StandardCharsets.UTF_8);
		return exportPath;
	}

	private static String renderReport(ServerLevel level, SettlementState settlement, long dayIndex, DailyReport report) {
		StringBuilder builder = new StringBuilder();
		builder.append("Live Villages daily report\n");
		builder.append("Settlement: ").append(settlement.name()).append('\n');
		builder.append("Kind: ").append(settlement.kind().getSerializedName()).append('\n');
		builder.append("Day: ").append(dayNumber(dayIndex)).append('\n');
		builder.append("Dimension: ").append(level.dimension().identifier()).append('\n');
		builder.append("Generated: ").append(LocalDateTime.now()).append("\n\n");

		if (settlement.kind() == SettlementKind.OUTPOST) {
			appendOutpostMemberSummary(builder, level, settlement);
		}

		if (!report.tradeSummaries.isEmpty()) {
			builder.append("Inter-settlement trades:\n");
			for (String tradeSummary : report.tradeSummaries) {
				builder.append("- ").append(tradeSummary).append('\n');
			}
			builder.append('\n');
		}

		List<VillagerProgress> villagers = report.progressByVillager.values().stream()
			.sorted(Comparator.comparing(SettlementProfessionReports::villagerSortLabel))
			.toList();
		Map<String, List<VillagerProgress>> villagersByRole = villagers.stream()
			.collect(java.util.stream.Collectors.groupingBy(progress -> progress.roleKey, LinkedHashMap::new, java.util.stream.Collectors.toList()));
		Set<String> roleKeys = new LinkedHashSet<>();
		PROFESSION_REPORT_ROLES.forEach(role -> roleKeys.add(role.roleKey()));
		report.progressByRole.keySet().stream().sorted().forEach(roleKeys::add);
		villagersByRole.keySet().stream().sorted().forEach(roleKeys::add);

		if (roleKeys.isEmpty()) {
			builder.append("No loaded villagers were observed for this village today.\n");
			return builder.toString();
		}

		builder.append("Profession reports:\n\n");

		for (String roleKey : roleKeys) {
			List<VillagerProgress> roleVillagers = villagersByRole.getOrDefault(roleKey, List.of());
			ProfessionReportRole role = professionReportRole(roleKey);
			builder.append(roleLabel(roleKey)).append(":\n");
			appendWorkstationLine(builder, level, settlement, role);

			if (roleVillagers.isEmpty()) {
				builder.append("  Villagers: none assigned.\n\n");
				continue;
			}

			builder.append("  Villagers: ").append(roleVillagers.size()).append('\n');

			for (VillagerProgress progress : roleVillagers) {
				builder.append("  ").append(villagerLabel(progress)).append('\n');
				appendBlockLine(builder, "Blocks mined", progress.minedBlocks, "    ");
				appendBlockLine(builder, "Blocks harvested", progress.harvestedBlocks, "    ");
				appendProgressLine(builder, "Produced", progress.produced, "    ");
				appendProgressLine(builder, "Mined", progress.mined, "    ");
				appendProgressLine(builder, "Recovered", progress.recovered, "    ");
				appendProgressLine(builder, "Consumed for work", progress.consumed, "    ");
				appendAccomplishments(builder, progress.accomplishments, "    ");
				appendStatus(builder, progress, "    ");

				if (!progress.hasReportableWork()) {
					builder.append("    No actual work recorded.\n");
				}
			}

			builder.append('\n');
		}

		return builder.toString();
	}

	private static void appendOutpostMemberSummary(StringBuilder builder, ServerLevel level, SettlementState settlement) {
		Map<String, Integer> population = OutpostSettlementWork.censusPopulation(level, settlement);
		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(level.getServer());
		OutpostRaidState raidState = savedData.outpostRaidState(settlement.id()).orElse(null);
		SettlementState raidTarget = raidState == null
			? null
			: savedData.getSettlement(raidState.targetSettlementId()).orElse(null);
		builder.append("Outpost raid status: ")
			.append(OutpostRaids.describeRaidState(raidState, raidTarget, OutpostRaids.currentRaidTick(level.getServer())))
			.append('\n');
		if (raidState != null && raidState.phase() == OutpostRaidPhase.COOLDOWN && !raidState.outcome().isBlank()) {
			if (raidState.lastLoot().isEmpty()) {
				builder.append("Last raid gains: none recorded\n");
			} else {
				builder.append("Last raid gains: ").append(summarizeGoods(raidState.lastLoot())).append('\n');
			}

			if (raidState.lastPlayerRewards().isEmpty()) {
				builder.append("Last raid player rewards: none recorded\n");
			} else {
				builder.append("Last raid player rewards: ").append(summarizePlayerRewards(raidState.lastPlayerRewards())).append('\n');
			}
		}

		if (population.isEmpty()) {
			builder.append("Outpost members observed: none\n\n");
			return;
		}

		String summary = population.entrySet().stream()
			.filter(entry -> entry.getValue() > 0)
			.sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
			.map(entry -> entry.getValue() + " " + roleLabel(entry.getKey()))
			.collect(java.util.stream.Collectors.joining(", "));
		builder.append("Outpost members observed: ").append(summary).append("\n\n");
	}

	private static void appendProgressLine(StringBuilder builder, String label, Map<String, Integer> goods) {
		appendProgressLine(builder, label, goods, "  ");
	}

	private static void appendProgressLine(StringBuilder builder, String label, Map<String, Integer> goods, String indent) {
		if (goods.isEmpty()) {
			return;
		}

		builder.append(indent).append(label).append(": ").append(summarizeGoods(goods)).append('\n');
	}

	private static void appendBlockLine(StringBuilder builder, String label, Map<String, Integer> blocks) {
		appendBlockLine(builder, label, blocks, "  ");
	}

	private static void appendBlockLine(StringBuilder builder, String label, Map<String, Integer> blocks, String indent) {
		if (blocks.isEmpty()) {
			return;
		}

		String summary = blocks.entrySet().stream()
			.sorted(Comparator.comparing(Map.Entry::getKey))
			.map(entry -> entry.getValue() + " " + blockLabel(entry.getKey()))
			.collect(java.util.stream.Collectors.joining(", "));
		builder.append(indent).append(label).append(": ").append(summary).append('\n');
	}

	private static void appendAccomplishments(StringBuilder builder, Set<String> accomplishments) {
		appendAccomplishments(builder, accomplishments, "  ");
	}

	private static void appendAccomplishments(StringBuilder builder, Set<String> accomplishments, String indent) {
		if (accomplishments.isEmpty()) {
			return;
		}

		builder.append(indent).append("Accomplished: ").append(String.join("; ", accomplishments)).append('\n');
	}

	private static void appendStatus(StringBuilder builder, VillagerProgress progress) {
		appendStatus(builder, progress, "  ");
	}

	private static void appendStatus(StringBuilder builder, VillagerProgress progress, String indent) {
		if (!progress.deathNote.isBlank()) {
			builder.append(indent).append("Status: ").append(progress.deathNote).append('\n');
		}
	}

	private static void appendWorkstationLine(StringBuilder builder, ServerLevel level, SettlementState settlement, ProfessionReportRole role) {
		if (role.poiPredicate() == null) {
			builder.append("  Workstations: none required.\n");
			return;
		}

		builder.append("  Workstations found: ").append(workstationCount(level, settlement, role)).append('\n');
	}

	private static long workstationCount(ServerLevel level, SettlementState settlement, ProfessionReportRole role) {
		if (role.poiPredicate() == null) {
			return 0L;
		}

		return level.getPoiManager().findAllClosestFirstWithType(
			role.poiPredicate(),
			pos -> true,
			settlement.center(),
			SettlementVillagers.settlementRadiusBlocks(settlement),
			net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY
		)
			.map(com.mojang.datafixers.util.Pair::getSecond)
			.map(BlockPos::immutable)
			.distinct()
			.count();
	}

	private static ProfessionReportRole professionReportRole(String roleKey) {
		return PROFESSION_REPORT_ROLES.stream()
			.filter(role -> role.roleKey().equals(roleKey))
			.findFirst()
			.orElse(new ProfessionReportRole(roleKey, null));
	}

	private static String tradeSummaryForSettlement(String settlementId, List<TradeShipment> shipments) {
		List<TradeShipment> outgoing = shipments.stream()
			.filter(shipment -> settlementId.equals(shipment.fromSettlementId()))
			.toList();
		List<TradeShipment> incoming = shipments.stream()
			.filter(shipment -> settlementId.equals(shipment.toSettlementId()))
			.toList();

		if (outgoing.isEmpty() && incoming.isEmpty()) {
			return "";
		}

		String settlementName = !outgoing.isEmpty() ? outgoing.get(0).fromSettlementName() : incoming.get(0).toSettlementName();
		List<String> parts = new ArrayList<>();

		if (!outgoing.isEmpty()) {
			parts.add(settlementName + " sent " + shipmentGoodsSummary(outgoing, true));
		}

		if (!incoming.isEmpty()) {
			String incomingPrefix = outgoing.isEmpty() ? settlementName + " received " : "received ";
			parts.add(incomingPrefix + shipmentGoodsSummary(incoming, false));
		}

		return parts.size() == 1
			? parts.get(0) + "."
			: parts.get(0) + " and " + parts.get(1) + ".";
	}

	private static String shipmentGoodsSummary(List<TradeShipment> shipments, boolean outgoing) {
		Map<String, Map<String, Integer>> goodsByPartner = new LinkedHashMap<>();

		for (TradeShipment shipment : shipments) {
			String partnerName = outgoing ? shipment.toSettlementName() : shipment.fromSettlementName();
			goodsByPartner
				.computeIfAbsent(partnerName, ignored -> new LinkedHashMap<>())
				.merge(shipment.goodsKey(), shipment.amount(), Integer::sum);
		}

		return goodsByPartner.entrySet().stream()
			.map(entry -> summarizeGoods(entry.getValue()) + (outgoing ? " to " : " from ") + entry.getKey())
			.collect(java.util.stream.Collectors.joining("; "));
	}

	private static void onAfterDeath(LivingEntity entity, DamageSource damageSource) {
		if (!(entity instanceof Villager villager) || !(entity.level() instanceof ServerLevel level) || !reportsEnabled(level)) {
			return;
		}

		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(level.getServer());
		SettlementState settlement = savedData.villagerSettlement(villager.getUUID())
			.flatMap(savedData::getSettlement)
			.or(() -> savedData.findSettlementForPosition(level.dimension(), villager.blockPosition(), SettlementVillagers::usesActualVillagers))
			.orElse(null);

		if (settlement == null || !SettlementVillagers.usesActualVillagers(settlement)) {
			return;
		}

		recordDeath(level, settlement, villager, damageSource);
	}

	private static void recordDeath(ServerLevel level, SettlementState settlement, Villager villager, DamageSource damageSource) {
		String roleKey = roleKeyFor(villager);
		VillagerProgress progress = reportFor(level, settlement).villager(villager, roleKey);
		progress.observe(villager, roleKey, level.getServer().getTickCount());
		boolean wasDefending = SettlementDefenseWork.loadedDefenseTaskKey(level, villager)
			.filter("defending_village"::equals)
			.isPresent();
		progress.deathNote = deathNote(damageSource, wasDefending);
	}

	private static String deathNote(DamageSource damageSource, boolean wasDefending) {
		Entity attacker = damageSource.getEntity();
		String sourceLabel = damageSource.getMsgId();

		if (attacker != null) {
			sourceLabel = attacker.getType().getDescription().getString();
		}

		if (attacker instanceof Monster) {
			if (wasDefending) {
				return "died while defending the village against " + sourceLabel;
			}

			return "died during a hostile attack from " + sourceLabel;
		}

		return "died from " + sourceLabel;
	}

	private static void recordGoods(
		ServerLevel level,
		SettlementState settlement,
		String roleKey,
		Villager villager,
		GoodsAction action,
		String goodsKey,
		int amount
	) {
		if (!isValidRecord(level, settlement, roleKey, goodsKey, amount)) {
			return;
		}

		RoleProgress roleProgress = reportFor(level, settlement).role(roleKey);
		action.goods(roleProgress).merge(goodsKey, amount, Integer::sum);

		if (villager != null) {
			VillagerProgress villagerProgress = reportFor(level, settlement).villager(villager, roleKey);
			villagerProgress.observe(villager, roleKey, level.getServer().getTickCount());
			action.goods(villagerProgress).merge(goodsKey, amount, Integer::sum);
		}
	}

	private static void recordBlockWork(
		ServerLevel level,
		SettlementState settlement,
		String roleKey,
		Villager villager,
		BlockState blockState,
		Map<String, Integer> drops,
		boolean mined
	) {
		if (!isValidWorkerRecord(level, settlement, roleKey, villager) || blockState == null || blockState.isAir()) {
			return;
		}

		VillagerProgress villagerProgress = reportFor(level, settlement).villager(villager, roleKey);
		villagerProgress.observe(villager, roleKey, level.getServer().getTickCount());
		String blockId = BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).toString();
		(mined ? villagerProgress.minedBlocks : villagerProgress.harvestedBlocks).merge(blockId, 1, Integer::sum);

		if (drops == null) {
			return;
		}

		for (Map.Entry<String, Integer> entry : drops.entrySet()) {
			if (mined) {
				recordMined(level, settlement, roleKey, villager, entry.getKey(), entry.getValue());
			} else {
				recordProduced(level, settlement, roleKey, villager, entry.getKey(), entry.getValue());
			}
		}
	}

	private static void recordGoodsForWorkers(
		ServerLevel level,
		SettlementState settlement,
		String roleKey,
		List<Villager> workers,
		GoodsAction action,
		String goodsKey,
		int amount
	) {
		if (!isValidRecord(level, settlement, roleKey, goodsKey, amount)) {
			return;
		}

		if (workers == null || workers.isEmpty()) {
			recordGoods(level, settlement, roleKey, null, action, goodsKey, amount);
			return;
		}

		List<Villager> orderedWorkers = workers.stream()
			.sorted(Comparator.comparing(villager -> villager.getUUID().toString()))
			.toList();
		int baseAmount = amount / orderedWorkers.size();
		int remainder = amount % orderedWorkers.size();

		for (int index = 0; index < orderedWorkers.size(); index++) {
			int workerAmount = baseAmount + (index < remainder ? 1 : 0);

			if (workerAmount > 0) {
				recordGoods(level, settlement, roleKey, orderedWorkers.get(index), action, goodsKey, workerAmount);
			}
		}
	}

	private static boolean isValidRecord(ServerLevel level, SettlementState settlement, String roleKey, String goodsKey, int amount) {
		return level != null
			&& reportsEnabled(level)
			&& settlement != null
			&& roleKey != null
			&& !roleKey.isBlank()
			&& goodsKey != null
			&& !goodsKey.isBlank()
			&& amount > 0;
	}

	private static boolean isValidWorkerRecord(ServerLevel level, SettlementState settlement, String roleKey, Villager villager) {
		return level != null
			&& reportsEnabled(level)
			&& settlement != null
			&& roleKey != null
			&& !roleKey.isBlank()
			&& villager != null;
	}

	private static boolean reportsEnabled(ServerLevel level) {
		return LiveVillagesGameRules.dailySettlementReportsEnabled(level);
	}

	private static DailyReport reportFor(ServerLevel level, SettlementState settlement) {
		ReportKey key = reportKey(level, settlement, dayIndex(level));
		return REPORTS.computeIfAbsent(key, ignored -> new DailyReport());
	}

	private static DailyReport reportFor(ServerLevel level, SettlementState settlement, long reportDayIndex) {
		ReportKey key = reportKey(level, settlement, reportDayIndex);
		return REPORTS.computeIfAbsent(key, ignored -> new DailyReport());
	}

	private static ReportKey reportKey(ServerLevel level, SettlementState settlement, long dayIndex) {
		return new ReportKey(level.dimension(), settlement.id(), dayIndex);
	}

	private static SettlementReportKey settlementReportKey(ServerLevel level, SettlementState settlement) {
		return new SettlementReportKey(level.dimension(), settlement.id());
	}

	private static long dayIndex(ServerLevel level) {
		return Math.floorDiv(level.getOverworldClockTime(), DAY_TICKS);
	}

	private static long dayNumber(long dayIndex) {
		return dayIndex + 1L;
	}

	private static void pruneOldReports(long currentDayIndex) {
		REPORTS.keySet().removeIf(key -> key.dayIndex() < currentDayIndex - 1L);
		WRITTEN_REPORTS.removeIf(key -> key.dayIndex() < currentDayIndex - 1L);
	}

	private static String villagerSortLabel(VillagerProgress progress) {
		return progress.nameLabel() + "|" + progress.villagerId;
	}

	private static String villagerLabel(VillagerProgress progress) {
		StringBuilder label = new StringBuilder("- ");
		label.append(progress.nameLabel());
		label.append(" (").append(roleLabel(progress.roleKey)).append(", id ").append(shortId(progress.villagerId)).append(")");
		if (progress.lastPos != null) {
			label.append(" @ ").append(progress.lastPos.getX()).append(',').append(progress.lastPos.getY()).append(',').append(progress.lastPos.getZ());
		}
		return label.toString();
	}

	private static String roleKeyFor(Villager villager) {
		if (villager.isBaby()) {
			return "child";
		}

		String roleKey = SettlementVillagers.reportProfessionKey(villager);
		return roleKey == null || roleKey.isBlank() ? "villager" : roleKey;
	}

	private static java.util.Optional<String> villagerName(Villager villager) {
		if (villager.hasCustomName() && villager.getCustomName() != null) {
			String name = villager.getCustomName().getString();

			if (!name.isBlank()) {
				return java.util.Optional.of(name);
			}
		}

		return java.util.Optional.empty();
	}

	private static String shortId(UUID uuid) {
		return uuid.toString().substring(0, 8);
	}

	private static String shortId(String uuid) {
		return uuid.length() <= 8 ? uuid : uuid.substring(0, 8);
	}

	private static String sanitizeFileLabel(String label) {
		StringBuilder builder = new StringBuilder();

		for (char character : label.toLowerCase(Locale.ROOT).toCharArray()) {
			if ((character >= 'a' && character <= 'z') || (character >= '0' && character <= '9')) {
				builder.append(character);
			} else if (character == '-' || character == '_' || Character.isWhitespace(character)) {
				if (!builder.isEmpty() && builder.charAt(builder.length() - 1) != '-') {
					builder.append('-');
				}
			}
		}

		String result = builder.toString();

		while (result.endsWith("-")) {
			result = result.substring(0, result.length() - 1);
		}

		return result.isBlank() ? "village" : result;
	}

	private static String roleLabel(String roleKey) {
		if (roleKey == null) {
			return "Villager";
		}

		return switch (roleKey) {
			case SettlementRoleKeys.BUTCHER -> "Butcher";
			case SettlementRoleKeys.FISHERMAN -> "Fisherman";
			case SettlementRoleKeys.FORESTER -> "Forester";
			case SettlementRoleKeys.CARPENTER -> "Carpenter";
			case SettlementRoleKeys.MASON -> "Mason";
			case SettlementRoleKeys.MINER -> "Miner";
			case SettlementRoleKeys.BAKER -> "Baker";
			case SettlementRoleKeys.BEEKEEPER -> "Beekeeper";
			case SettlementRoleKeys.FLETCHER -> "Fletcher";
			case SettlementRoleKeys.ROADWRIGHT -> "Roadwright";
			case SettlementRoleKeys.PORTMASTER -> "Portmaster";
			case SettlementRoleKeys.THRALL -> "Thrall";
			case SettlementRoleKeys.TRADEMASTER -> "Trademaster";
			case SettlementRoleKeys.UNEMPLOYED -> "Unemployed";
			case SettlementRoleKeys.GUARD -> "Guard";
			case SettlementRoleKeys.GARDENER -> "Gardener";
			case SettlementRoleKeys.SCRIBE -> "Scribe";
			case SettlementRoleKeys.PILLAGER -> "Pillager";
			case "armorer" -> "Armorer";
			case "child" -> "Children";
			case "cleric" -> "Cleric";
			case "leatherworker" -> "Leatherworker";
			case "librarian" -> "Librarian";
			case "nitwit" -> "Nitwit";
			case "shepherd" -> "Shepherd";
			case "toolsmith" -> "Toolsmith";
			case "weaponsmith" -> "Weaponsmith";
			default -> humanizeKey(roleKey);
		};
	}

	private static String goodsLabel(String goodsKey) {
		return switch (goodsKey) {
			case "beef" -> "beef";
			case "mutton" -> "mutton";
			case "pork" -> "pork";
			case "cod" -> "cod";
			case "leather" -> "leather";
			case "wool" -> "wool";
			case "logs" -> "logs";
			case "planks" -> "planks";
			case "stairs" -> "stairs";
			case "slab" -> "slabs";
			case "stick" -> "sticks";
			case "flint" -> "flint";
			case "feather" -> "feathers";
			case "arrow" -> "arrows";
			case "copperhead_arrow" -> "Copperhead Arrows";
			case "ironhead_arrow" -> "Ironhead Arrows";
			case "diamondhead_arrow" -> "Diamondhead Arrows";
			case "copper_nugget" -> "copper nuggets";
			case "iron_nugget" -> "iron nuggets";
			case "cobblestone" -> "cobblestone";
			case "milepost" -> "mileposts";
			case "wheat" -> "wheat";
			case "wheat_seeds" -> "wheat seeds";
			case "carrot" -> "carrots";
			case "potato" -> "potatoes";
			case "beetroot" -> "beetroot";
			case "beetroot_seeds" -> "beetroot seeds";
			case "bone_meal" -> "bone meal";
			case "leaf_litter" -> "leaf litter";
			case "ladder" -> "ladders";
			case "torch" -> "torches";
			case "copper_torch" -> "copper torches";
			case "dirt" -> "dirt";
			case "raw_iron" -> "raw iron";
			case "raw_copper" -> "raw copper";
			case "raw_gold" -> "raw gold";
			case "copper_ingot" -> "copper ingots";
			case "iron_ingot" -> "iron ingots";
			case "coal" -> "coal";
			case "redstone" -> "redstone";
			case "lapis" -> "lapis";
			case "diamond" -> "diamonds";
			case "emerald" -> "emeralds";
			case "sand" -> "sand";
			case "glass" -> "glass";
			case "lantern" -> "lanterns";
			case "apple" -> "apples";
			case "oak_sapling" -> "oak saplings";
			case "spruce_sapling" -> "spruce saplings";
			case "birch_sapling" -> "birch saplings";
			case "jungle_sapling" -> "jungle saplings";
			case "acacia_sapling" -> "acacia saplings";
			case "cherry_sapling" -> "cherry saplings";
			case "dark_oak_sapling" -> "dark oak saplings";
			case "pale_oak_sapling" -> "pale oak saplings";
			case "mangrove_propagule" -> "mangrove propagules";
			case "bread" -> "bread";
			case "baked_potato" -> "baked potatoes";
			case "cookie" -> "cookies";
			case "pumpkin_pie" -> "pumpkin pies";
			case "cake" -> "cakes";
			case "golden_apple" -> "golden apples";
			case "egg" -> "eggs";
			case "sugar" -> "sugar";
			case "pumpkin" -> "pumpkins";
			case "milk_bucket" -> "milk buckets";
			case "cocoa_beans" -> "cocoa beans";
			case "gold_ingot" -> "gold ingots";
			default -> humanizeKey(goodsKey).toLowerCase(Locale.ROOT);
		};
	}

	private static String blockLabel(String blockId) {
		int separator = blockId.indexOf(':');
		String path = separator >= 0 ? blockId.substring(separator + 1) : blockId;
		return humanizeKey(path).toLowerCase(Locale.ROOT);
	}

	private static String humanizeKey(String key) {
		if (key == null || key.isBlank()) {
			return "Villager";
		}

		String[] parts = key.split("_");
		StringBuilder result = new StringBuilder();

		for (String part : parts) {
			if (part.isEmpty()) {
				continue;
			}

			if (!result.isEmpty()) {
				result.append(' ');
			}

			result.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
			result.append(part.substring(1));
		}

		return result.toString();
	}

	private static String summarizeGoods(Map<String, Integer> goods) {
		return goods.entrySet().stream()
			.sorted(Comparator.comparing(Map.Entry::getKey))
			.map(entry -> entry.getValue() + " " + goodsLabel(entry.getKey()))
			.collect(java.util.stream.Collectors.joining(", "));
	}

	private static String summarizePlayerRewards(Map<String, Integer> rewards) {
		return rewards.entrySet().stream()
			.sorted(Comparator.comparing(Map.Entry::getKey))
			.map(entry -> entry.getKey() + " +" + entry.getValue() + " support")
			.collect(java.util.stream.Collectors.joining(", "));
	}

	private enum GoodsAction {
		PRODUCED {
			@Override
			Map<String, Integer> goods(GoodsProgress progress) {
				return progress.produced;
			}
		},
		MINED {
			@Override
			Map<String, Integer> goods(GoodsProgress progress) {
				return progress.mined;
			}
		},
		RECOVERED {
			@Override
			Map<String, Integer> goods(GoodsProgress progress) {
				return progress.recovered;
			}
		},
		CONSUMED {
			@Override
			Map<String, Integer> goods(GoodsProgress progress) {
				return progress.consumed;
			}
		};

		abstract Map<String, Integer> goods(GoodsProgress progress);
	}

	private record ReportKey(ResourceKey<Level> dimension, String settlementId, long dayIndex) {
	}

	private record SettlementReportKey(ResourceKey<Level> dimension, String settlementId) {
	}

	private record ProfessionReportRole(String roleKey, Predicate<Holder<PoiType>> poiPredicate) {
	}

	public record TradeShipment(
		String fromSettlementId,
		String fromSettlementName,
		String toSettlementId,
		String toSettlementName,
		String goodsKey,
		int amount
	) {
	}

	private static final class DailyReport {
		private static final DailyReport EMPTY = new DailyReport();
		private final Map<String, RoleProgress> progressByRole = new LinkedHashMap<>();
		private final Map<String, VillagerProgress> progressByVillager = new LinkedHashMap<>();
		private final List<String> tradeSummaries = new ArrayList<>();

		private RoleProgress role(String roleKey) {
			return progressByRole.computeIfAbsent(roleKey, ignored -> new RoleProgress());
		}

		private VillagerProgress villager(Villager villager, String roleKey) {
			return progressByVillager.computeIfAbsent(villager.getUUID().toString(), VillagerProgress::new);
		}

		private boolean isEmpty() {
			return progressByVillager.isEmpty() && tradeSummaries.isEmpty();
		}
	}

	private abstract static class GoodsProgress {
		protected final Map<String, Integer> produced = new LinkedHashMap<>();
		protected final Map<String, Integer> mined = new LinkedHashMap<>();
		protected final Map<String, Integer> recovered = new LinkedHashMap<>();
		protected final Map<String, Integer> consumed = new LinkedHashMap<>();
	}

	private static final class RoleProgress extends GoodsProgress {
	}

	private static final class VillagerProgress extends GoodsProgress {
		private static final VillagerProgress EMPTY = new VillagerProgress("");
		private final String villagerId;
		private final Set<String> accomplishments = new LinkedHashSet<>();
		private final Map<String, Integer> minedBlocks = new LinkedHashMap<>();
		private final Map<String, Integer> harvestedBlocks = new LinkedHashMap<>();
		private String customName = "";
		private String roleKey = "villager";
		private BlockPos lastPos;
		private long firstSeenTick = -1L;
		private long lastSeenTick = -1L;
		private String deathNote = "";

		private VillagerProgress(String villagerId) {
			this.villagerId = villagerId;
		}

		private void observe(Villager villager, String observedRoleKey, long tick) {
			if (firstSeenTick < 0L) {
				firstSeenTick = tick;
			}

			lastSeenTick = tick;
			roleKey = observedRoleKey == null || observedRoleKey.isBlank() ? roleKey : observedRoleKey;
			lastPos = villager.blockPosition().immutable();
			customName = villagerName(villager).orElse(customName);
		}

		private String nameLabel() {
			return customName == null || customName.isBlank()
				? "Villager " + shortId(villagerId)
				: customName;
		}

		private boolean hasReportableWork() {
			return !produced.isEmpty()
				|| !mined.isEmpty()
				|| !recovered.isEmpty()
				|| !consumed.isEmpty()
				|| !accomplishments.isEmpty()
				|| !minedBlocks.isEmpty()
				|| !harvestedBlocks.isEmpty()
				|| !deathNote.isBlank();
		}
	}
}
