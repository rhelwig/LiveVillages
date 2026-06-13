package com.ronhelwig.livevillages.menu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import net.minecraft.server.level.ServerLevel;

import com.ronhelwig.livevillages.sim.LiveVillagesSavedData;
import com.ronhelwig.livevillages.sim.OutpostRaidState;
import com.ronhelwig.livevillages.sim.OutpostRaids;
import com.ronhelwig.livevillages.sim.OutpostSettlementWork;
import com.ronhelwig.livevillages.sim.RouteState;
import com.ronhelwig.livevillages.sim.SettlementBuildBlockState;
import com.ronhelwig.livevillages.sim.SettlementBuildBlockStatus;
import com.ronhelwig.livevillages.sim.SettlementBuildSite;
import com.ronhelwig.livevillages.sim.SettlementBuildSiteType;
import com.ronhelwig.livevillages.sim.SettlementEconomyRules;
import com.ronhelwig.livevillages.sim.SettlementProject;
import com.ronhelwig.livevillages.sim.SettlementProjectType;
import com.ronhelwig.livevillages.sim.SettlementRefining;
import com.ronhelwig.livevillages.sim.SettlementState;
import com.ronhelwig.livevillages.sim.SettlementTiers;

public final class TradeBoardLogic {
	private static final int MAX_TRADE_ROWS = TradeBoardTrading.MAX_TRADE_ROWS;
	private static final int MAX_STOCK_ROWS = 5;
	private static final int MAX_ROUTE_ROWS = 3;
	private static final int MAX_PROJECT_ROWS = 3;

	private TradeBoardLogic() {
	}

	public static TradeBoardSettlementView createSettlementView(
		SettlementState settlement,
		List<RouteState> routes,
		Function<String, String> settlementNameResolver
	) {
		return createSettlementView(settlement, routes, settlementNameResolver, MAX_STOCK_ROWS, MAX_ROUTE_ROWS, MAX_PROJECT_ROWS, settlement.population());
	}

	public static TradeBoardSettlementView createSettlementView(
		SettlementState settlement,
		List<RouteState> routes,
		Function<String, String> settlementNameResolver,
		int maxStockRows,
		int maxRouteRows,
		int maxProjectRows
	) {
		return createSettlementView(settlement, routes, settlementNameResolver, maxStockRows, maxRouteRows, maxProjectRows, settlement.population());
	}

	public static TradeBoardSettlementView createSettlementView(
		SettlementState settlement,
		List<RouteState> routes,
		Function<String, String> settlementNameResolver,
		int maxStockRows,
		int maxRouteRows,
		int maxProjectRows,
		Map<String, Integer> rolePopulation
	) {
		return createSettlementView(settlement, routes, settlementNameResolver, maxStockRows, maxRouteRows, maxProjectRows, rolePopulation, Map.of());
	}

	public static TradeBoardSettlementView createSettlementView(
		SettlementState settlement,
		List<RouteState> routes,
		Function<String, String> settlementNameResolver,
		int maxStockRows,
		int maxRouteRows,
		int maxProjectRows,
		Map<String, Integer> rolePopulation,
		Map<String, Integer> constructionDemand
	) {
		return createSettlementView(
			settlement,
			routes,
			settlementNameResolver,
			maxStockRows,
			maxRouteRows,
			maxProjectRows,
			rolePopulation,
			constructionDemand,
			List.of()
		);
	}

	public static TradeBoardSettlementView createSettlementView(
		SettlementState settlement,
		List<RouteState> routes,
		Function<String, String> settlementNameResolver,
		int maxStockRows,
		int maxRouteRows,
		int maxProjectRows,
		Map<String, Integer> rolePopulation,
		Map<String, Integer> constructionDemand,
		List<SettlementBuildSite> buildSites
	) {
		return createSettlementView(
			null,
			settlement,
			routes,
			settlementNameResolver,
			maxStockRows,
			maxRouteRows,
			maxProjectRows,
			rolePopulation,
			constructionDemand,
			buildSites
		);
	}

	public static TradeBoardSettlementView createSettlementView(
		ServerLevel level,
		SettlementState settlement,
		List<RouteState> routes,
		Function<String, String> settlementNameResolver,
		int maxStockRows,
		int maxRouteRows,
		int maxProjectRows,
		Map<String, Integer> rolePopulation,
		Map<String, Integer> constructionDemand,
		List<SettlementBuildSite> buildSites
	) {
		int reportedPopulation = rolePopulation.values().stream().mapToInt(Integer::intValue).sum();
		int population = Math.max(1, reportedPopulation > 0 ? reportedPopulation : settlement.totalPopulation());
		List<TradeBoardGoodsView> shortages = new ArrayList<>();
		List<TradeBoardGoodsView> surpluses = new ArrayList<>();

		for (SettlementEconomyRules.TargetRule targetRule : SettlementEconomyRules.targetRules()) {
			if (targetRule.goodsKey().equals("emerald")) {
				continue;
			}

			if (!SettlementEconomyRules.isUnlockedForSettlementTier(targetRule.goodsKey(), SettlementTiers.unlockedTier(settlement))) {
				continue;
			}

			int target = SettlementEconomyRules.targetForGoods(settlement, targetRule.goodsKey());
			int current = effectiveCurrentForDisplay(settlement, targetRule.goodsKey());
			int tradePricePercent = SettlementEconomyRules.tradePricePercent(current, target);
			int tradeBundleSize = TradeBoardTradeRules.bundleSize(targetRule.goodsKey());
			int tradePriceEmeralds = level == null
				? TradeBoardTradeRules.bundlePriceEmeralds(targetRule.goodsKey(), tradePricePercent)
				: TradeBoardTradeRules.bundlePriceEmeralds(level, targetRule.goodsKey(), tradePricePercent);
			int tradeBundleValuePoints = level == null
				? TradeBoardTradeRules.bundleValuePoints(targetRule.goodsKey(), tradePricePercent)
				: TradeBoardTradeRules.bundleValuePoints(level, targetRule.goodsKey(), tradePricePercent);
			int tradeItemValuePoints = level == null
				? TradeBoardTradeRules.itemValuePoints(targetRule.goodsKey(), tradePricePercent)
				: TradeBoardTradeRules.itemValuePoints(level, targetRule.goodsKey(), tradePricePercent);

			if (current < target) {
				shortages.add(new TradeBoardGoodsView(
					targetRule.goodsKey(),
					humanizeKey(targetRule.goodsKey()),
					current,
					target,
					tradePricePercent,
					tradeBundleSize,
					tradePriceEmeralds,
					tradeBundleValuePoints,
					tradeItemValuePoints
				));
			} else if (current > target * 2 && (tradeBundleSize <= 0 || current >= tradeBundleSize)) {
				surpluses.add(new TradeBoardGoodsView(
					targetRule.goodsKey(),
					humanizeKey(targetRule.goodsKey()),
					current,
					target,
					tradePricePercent,
					tradeBundleSize,
					tradePriceEmeralds,
					tradeBundleValuePoints,
					tradeItemValuePoints
				));
			}
		}

		addConstructionDemandShortages(level, shortages, settlement, constructionDemand);
		surpluses.removeIf(surplus -> indexOfGoods(shortages, surplus.goodsKey()) >= 0);
		shortages.sort(Comparator.comparingInt((TradeBoardGoodsView entry) ->
			shortagePriority(settlement, constructionDemand, entry)
		).reversed());
		surpluses.sort(Comparator.comparingInt((TradeBoardGoodsView entry) -> entry.current() - entry.target()).reversed());

		List<TradeBoardGoodsView> stock = settlement.stock().entrySet().stream()
			.filter(entry -> entry.getValue() > 0)
			.sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
			.map(entry -> stockGoodsView(level, entry.getKey(), entry.getValue()))
			.toList();
		List<TradeBoardRoleView> roleCounts = createRoleCounts(rolePopulation);
		List<TradeBoardRouteView> routeViews = routes.stream()
			.sorted(
				Comparator.comparingLong(RouteState::lastTradeTick).reversed()
					.thenComparing(route -> route.lastTransferSummary().isBlank() ? 1 : 0)
					.thenComparing(route -> routePartnerName(route, settlement, settlementNameResolver))
			)
			.map(route -> new TradeBoardRouteView(
				routePartnerName(route, settlement, settlementNameResolver),
				routeSummary(route)
			))
			.toList();
		List<TradeBoardProjectView> projects = createProjectViews(
			settlement.projects(),
			buildSites,
			settlementNameResolver,
			maxProjectRows
		);

		return new TradeBoardSettlementView(
			settlement.id(),
			settlement.name(),
			settlement.kind(),
			SettlementTiers.unlockedTier(settlement),
			population,
			settlement.housingCapacity(),
			settlement.wealth().getOrDefault("emerald", 0),
			settlement.comfort(),
			settlement.security(),
			summarizeGrowth(settlement, population, rolePopulation),
			roleCounts,
			shortages,
			surpluses,
			stock.stream().limit(Math.max(0, maxStockRows)).toList(),
			routeViews,
			projects
		);
	}

	public static Map<String, Integer> constructionTradeDemand(List<SettlementBuildSite> buildSites) {
		LinkedHashMap<String, Integer> demand = new LinkedHashMap<>();

		for (SettlementBuildSite buildSite : buildSites) {
			if (buildSite.complete()) {
				continue;
			}

			for (SettlementBuildBlockState block : buildSite.blocks()) {
				if (!requiresUnplacedConstructionMaterial(block.status())) {
					continue;
				}

				addConstructionDemand(demand, block.expectedMaterialKey(), 1);

				if (block.expectedMaterialKey().equals("glass")) {
					addConstructionDemand(demand, "sand", 1);
				} else if (block.expectedMaterialKey().equals("bed")) {
					addConstructionDemand(demand, "wool", 3);
					addConstructionDemand(demand, "planks", 3);
				} else if (block.expectedMaterialKey().equals("torch")) {
					addConstructionDemand(demand, "coal", 1);
					addConstructionDemand(demand, "stick", 1);
					addConstructionDemand(demand, "planks", 1);
				} else if (block.expectedMaterialKey().equals("lantern")) {
					addConstructionDemand(demand, "torch", 1);
					addConstructionDemand(demand, "iron_ingot", 1);
				} else if (block.expectedMaterialKey().equals("chest")) {
					addConstructionDemand(demand, "planks", 8);
				}
			}
		}

		return Map.copyOf(demand);
	}

	private static boolean requiresUnplacedConstructionMaterial(SettlementBuildBlockStatus status) {
		return status == SettlementBuildBlockStatus.PENDING || status == SettlementBuildBlockStatus.MISSING_MATERIAL;
	}

	public static TradeBoardRaidView createRaidView(ServerLevel level, LiveVillagesSavedData savedData, SettlementState settlement) {
		if (level == null || savedData == null || settlement == null || settlement.kind() != com.ronhelwig.livevillages.sim.SettlementKind.OUTPOST) {
			return TradeBoardRaidView.EMPTY;
		}

		OutpostRaidState raidState = savedData.outpostRaidState(settlement.id()).orElse(null);
		SettlementState target = raidState == null
			? null
			: savedData.getSettlement(raidState.targetSettlementId()).orElse(null);

		if (raidState == null) {
			return new TradeBoardRaidView(
				OutpostRaids.describeRaidState(null, null, OutpostRaids.currentRaidTick(level.getServer())),
				"",
				"",
				"",
				0,
				List.of(),
				Map.of()
			);
		}

		List<TradeBoardGoodsView> loot = raidState.lastLoot().entrySet().stream()
			.sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
			.map(entry -> stockGoodsView(level, entry.getKey(), entry.getValue()))
			.toList();

		return new TradeBoardRaidView(
			OutpostRaids.describeRaidState(raidState, target, OutpostRaids.currentRaidTick(level.getServer())),
			target == null ? "" : target.name(),
			raidState.outcome(),
			humanizeKey(raidState.phase().getSerializedName()),
			raidState.partySize(),
			loot,
			raidState.lastPlayerRewards()
		);
	}

	private static void addConstructionDemandShortages(
		ServerLevel level,
		List<TradeBoardGoodsView> shortages,
		SettlementState settlement,
		Map<String, Integer> constructionDemand
	) {
		for (Map.Entry<String, Integer> demandEntry : constructionDemand.entrySet()) {
			String goodsKey = demandEntry.getKey();
			int demand = demandEntry.getValue();

			if (demand <= 0 || !TradeBoardTradeRules.isTradeableGoods(goodsKey)) {
				continue;
			}

			int existingIndex = indexOfGoods(shortages, goodsKey);
			int current = effectiveCurrentForDisplay(settlement, goodsKey);
			int target = demand;

			if (existingIndex >= 0) {
				TradeBoardGoodsView existing = shortages.get(existingIndex);
				current = existing.current();
				target = existing.target() + demand;
			}

			if (current >= target) {
				if (existingIndex >= 0) {
					shortages.remove(existingIndex);
				}

				continue;
			}

			if (existingIndex >= 0) {
				shortages.set(existingIndex, goodsView(level, goodsKey, current, target));
			} else {
				shortages.add(goodsView(level, goodsKey, current, target));
			}
		}
	}

	private static TradeBoardGoodsView goodsView(ServerLevel level, String goodsKey, int current, int target) {
		int tradePricePercent = SettlementEconomyRules.tradePricePercent(current, target);
		int tradePriceEmeralds = level == null
			? TradeBoardTradeRules.bundlePriceEmeralds(goodsKey, tradePricePercent)
			: TradeBoardTradeRules.bundlePriceEmeralds(level, goodsKey, tradePricePercent);
		int tradeBundleValuePoints = level == null
			? TradeBoardTradeRules.bundleValuePoints(goodsKey, tradePricePercent)
			: TradeBoardTradeRules.bundleValuePoints(level, goodsKey, tradePricePercent);
		int tradeItemValuePoints = level == null
			? TradeBoardTradeRules.itemValuePoints(goodsKey, tradePricePercent)
			: TradeBoardTradeRules.itemValuePoints(level, goodsKey, tradePricePercent);
		return new TradeBoardGoodsView(
			goodsKey,
			humanizeKey(goodsKey),
			current,
			target,
			tradePricePercent,
			TradeBoardTradeRules.bundleSize(goodsKey),
			tradePriceEmeralds,
			tradeBundleValuePoints,
			tradeItemValuePoints
		);
	}

	private static TradeBoardGoodsView stockGoodsView(ServerLevel level, String goodsKey, int current) {
		int bundleSize = TradeBoardTradeRules.bundleSize(goodsKey);
		int tradePriceEmeralds = level == null
			? TradeBoardTradeRules.bundlePriceEmeralds(goodsKey, 100)
			: TradeBoardTradeRules.bundlePriceEmeralds(level, goodsKey, 100);
		int tradeBundleValuePoints = level == null
			? TradeBoardTradeRules.bundleValuePoints(goodsKey, 100)
			: TradeBoardTradeRules.bundleValuePoints(level, goodsKey, 100);
		int tradeItemValuePoints = level == null
			? TradeBoardTradeRules.itemValuePoints(goodsKey, 100)
			: TradeBoardTradeRules.itemValuePoints(level, goodsKey, 100);
		return new TradeBoardGoodsView(
			goodsKey,
			TradeBoardTradeRules.displayLabel(goodsKey, humanizeKey(goodsKey)),
			current,
			0,
			100,
			bundleSize,
			tradePriceEmeralds,
			tradeBundleValuePoints,
			tradeItemValuePoints
		);
	}

	private static int indexOfGoods(List<TradeBoardGoodsView> entries, String goodsKey) {
		for (int index = 0; index < entries.size(); index++) {
			if (entries.get(index).goodsKey().equals(goodsKey)) {
				return index;
			}
		}

		return -1;
	}

	private static int shortagePriority(SettlementState settlement, Map<String, Integer> constructionDemand, TradeBoardGoodsView entry) {
		int priority = SettlementEconomyRules.shortagePriority(settlement, entry.goodsKey(), entry.current(), entry.target());

		if (constructionDemand.getOrDefault(entry.goodsKey(), 0) > 0) {
			priority += 1_000;
		}

		return priority;
	}

	private static void addConstructionDemand(Map<String, Integer> demand, String goodsKey, int amount) {
		if (goodsKey == null || goodsKey.isBlank() || amount <= 0) {
			return;
		}

		demand.merge(goodsKey, amount, Integer::sum);
	}

	private static List<TradeBoardRoleView> createRoleCounts(Map<String, Integer> rolePopulation) {
		return rolePopulation.entrySet().stream()
			.filter(entry -> entry.getValue() > 0)
			.sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
			.map(entry -> new TradeBoardRoleView(entry.getKey(), humanizeKey(entry.getKey()), entry.getValue()))
			.toList();
	}

	private static String summarizeGrowth(SettlementState settlement, int population, Map<String, Integer> rolePopulation) {
		if (population <= 0) {
			return "Empty";
		}

		int freeHousing = settlement.housingCapacity() - OutpostSettlementWork.bedUsingPopulation(settlement, rolePopulation);
		int storedFood = settlement.stock().getOrDefault("bread", 0)
			+ settlement.stock().getOrDefault("baked_potato", 0)
			+ settlement.stock().getOrDefault("cookie", 0)
			+ settlement.stock().getOrDefault("pumpkin_pie", 0)
			+ settlement.stock().getOrDefault("cake", 0)
			+ settlement.stock().getOrDefault("golden_apple", 0)
			+ settlement.stock().getOrDefault("beef", 0)
			+ settlement.stock().getOrDefault("mutton", 0)
			+ settlement.stock().getOrDefault("pork", 0)
			+ settlement.stock().getOrDefault("cod", 0)
			+ settlement.stock().getOrDefault("carrot", 0)
			+ settlement.stock().getOrDefault("potato", 0)
			+ settlement.stock().getOrDefault("beetroot", 0)
			+ settlement.stock().getOrDefault("wheat", 0);
		int targetFood = SettlementEconomyRules.targetForGoods(settlement, "bread")
			+ SettlementEconomyRules.targetForGoods(settlement, "baked_potato")
			+ SettlementEconomyRules.targetForGoods(settlement, "cookie")
			+ SettlementEconomyRules.targetForGoods(settlement, "pumpkin_pie")
			+ SettlementEconomyRules.targetForGoods(settlement, "cake")
			+ SettlementEconomyRules.targetForGoods(settlement, "golden_apple")
			+ SettlementEconomyRules.targetForGoods(settlement, "beef")
			+ SettlementEconomyRules.targetForGoods(settlement, "mutton")
			+ SettlementEconomyRules.targetForGoods(settlement, "pork")
			+ SettlementEconomyRules.targetForGoods(settlement, "cod")
			+ SettlementEconomyRules.targetForGoods(settlement, "carrot")
			+ SettlementEconomyRules.targetForGoods(settlement, "potato")
			+ SettlementEconomyRules.targetForGoods(settlement, "beetroot")
			+ SettlementEconomyRules.targetForGoods(settlement, "wheat");

		if (storedFood < Math.max(8, targetFood / 2)) {
			return "Hungry";
		}

		if (freeHousing <= 0) {
			return "Cramped";
		}

		if (settlement.comfort() >= 1.0D && settlement.security() >= 0.18D) {
			return "Growing";
		}

		if (settlement.comfort() < 0.85D || settlement.security() < 0.10D) {
			return "Wary";
		}

		return "Stable";
	}

	private static String projectLabel(SettlementProject project, Function<String, String> settlementNameResolver) {
		return switch (project.type()) {
			case TRADING_POST -> "Trading Post";
			case HOUSING -> "Housing Shelter";
			case CARPENTER_WORKSHOP -> "Carpenter's Workshop";
			case DOCK -> "Dock";
			case LIGHTHOUSE -> "Lighthouse";
			case DEFENSE -> "Defensive Works";
			case COMPOSTER -> "Composter";
			case ROAD -> {
				String targetName = settlementNameResolver.apply(project.targetSettlementId());
				yield targetName.isBlank() ? "Road Survey" : "Road to " + targetName;
			}
			case STORAGE -> "Storage Chest";
		};
	}

	private static List<TradeBoardProjectView> createProjectViews(
		List<SettlementProject> queuedProjects,
		List<SettlementBuildSite> buildSites,
		Function<String, String> settlementNameResolver,
		int maxProjectRows
	) {
		List<TradeBoardProjectView> combined = new ArrayList<>();
		combined.addAll(buildSiteProjectViews(buildSites));
		combined.addAll(queuedProjects.stream()
			.sorted(
				Comparator.comparingInt((SettlementProject project) -> project.type() == SettlementProjectType.TRADING_POST ? 0 : project.type() == SettlementProjectType.ROAD ? 1 : 2)
					.thenComparing(Comparator.comparingInt(SettlementProject::progressPercent).reversed())
			)
			.map(project -> new TradeBoardProjectView(projectLabel(project, settlementNameResolver), project.progressPercent()))
			.toList());
		return combined.stream()
			.limit(Math.max(0, maxProjectRows))
			.toList();
	}

	private static List<TradeBoardProjectView> buildSiteProjectViews(List<SettlementBuildSite> buildSites) {
		return buildSites.stream()
			.filter(buildSite -> !buildSite.complete())
			.sorted(
				Comparator.comparingInt(TradeBoardLogic::buildSitePriority)
					.thenComparingLong(SettlementBuildSite::createdTick)
					.thenComparing(buildSite -> buildSite.blueprintId().getSerializedName())
			)
			.map(buildSite -> new TradeBoardProjectView(
				buildSiteProjectLabel(buildSite),
				buildSiteProgressPercent(buildSite)
			))
			.toList();
	}

	private static int buildSitePriority(SettlementBuildSite buildSite) {
		return switch (buildSite.blueprintId()) {
			case TRADING_POST -> 0;
			case BAKERY, BEEKEEPER_APIARY -> 2;
			case DOCK, LIGHTHOUSE -> 1;
			case ROADWRIGHT_WORKSHOP, CARTOGRAPHER_HOUSE, SCRIBE_OFFICE, GARDENER_SHED, GUARD_POST -> 2;
			default -> 3;
		};
	}

	private static String buildSiteProjectLabel(SettlementBuildSite buildSite) {
		String status = buildSiteStatus(buildSite);
		return status.isBlank()
			? buildSiteTypeLabel(buildSite.blueprintId()) + " Site"
			: buildSiteTypeLabel(buildSite.blueprintId()) + " Site (" + status + ")";
	}

	private static int buildSiteProgressPercent(SettlementBuildSite buildSite) {
		if (buildSite.complete()) {
			return 100;
		}

		int totalBlocks = buildSite.blocks().size();

		if (totalBlocks <= 0) {
			return 0;
		}

		int placedBlocks = 0;

		for (SettlementBuildBlockState block : buildSite.blocks()) {
			if (block.status() == SettlementBuildBlockStatus.PLACED || block.status() == SettlementBuildBlockStatus.PLAYER_PLACED) {
				placedBlocks++;
			}
		}

		return Math.max(0, Math.min(100, (int) Math.round((placedBlocks * 100.0D) / totalBlocks)));
	}

	private static String buildSiteStatus(SettlementBuildSite buildSite) {
		int pendingBlocks = 0;
		int missingMaterialBlocks = 0;
		int blockedBlocks = 0;
		int placedBlocks = 0;

		for (SettlementBuildBlockState block : buildSite.blocks()) {
			switch (block.status()) {
				case PENDING -> pendingBlocks++;
				case MISSING_MATERIAL -> missingMaterialBlocks++;
				case BLOCKED -> blockedBlocks++;
				case PLACED, PLAYER_PLACED -> placedBlocks++;
			}
		}

		if (blockedBlocks > 0) {
			return "blocked";
		}

		if (missingMaterialBlocks > 0) {
			return "missing materials";
		}

		if (pendingBlocks > 0 && placedBlocks > 0) {
			return "under construction";
		}

		if (pendingBlocks > 0) {
			return "planned";
		}

		return "";
	}

	private static String buildSiteTypeLabel(SettlementBuildSiteType type) {
		return switch (type) {
			case BAKERY -> "Bakery";
			case BEEKEEPER_APIARY -> "Beekeeper's Apiary";
			case BUTCHER_SHOP -> "Butcher Shop";
			case CARTOGRAPHER_HOUSE -> "Cartographer's House";
			case CARPENTER_WORKSHOP -> "Carpenter's Workshop";
			case CLERIC_SHRINE -> "Cleric Shrine";
			case DOCK -> "Dock";
			case LEATHERWORKER_WORKSHOP -> "Leatherworker's Workshop";
			case LIBRARY -> "Library";
			case LIGHTHOUSE -> "Lighthouse";
			case MASON_WORKSHOP -> "Mason's Workshop";
			case MINE_ENTRANCE -> "Mine Entrance";
			case PALISADE_GATEHOUSE, COPPER_PALISADE_GATEHOUSE -> "Palisade Gatehouse";
			case PALISADE_WALL -> "Palisade Wall";
			case FLETCHER_HUT -> "Fletcher's Hut";
			case FORESTER_WORKSHOP -> "Forester's Workshop";
			case GARDENER_SHED -> "Gardener's Shed";
			case GUARD_POST -> "Guard Post";
			case HOUSING_SHELTER -> "Housing Shelter";
			case ROADWRIGHT_WORKSHOP -> "Roadwright's Workshop";
			case SCRIBE_OFFICE -> "Scribe Office";
			case SHEPHERD_HUT -> "Shepherd's Hut";
			case SIMPLE_HOUSING_SHELTER -> "Simple Housing Shelter";
			case SMITHY -> "Smithy";
			case TRADING_POST -> "Trade Post";
		};
	}

	private static int effectiveCurrentForDisplay(SettlementState settlement, String goodsKey) {
		if (goodsKey.equals("bread")) {
			int current = settlement.stock().getOrDefault(goodsKey, 0);
			int storedWheat = settlement.stock().getOrDefault("wheat", 0);
			int wheatReserve = SettlementEconomyRules.targetForGoods(settlement, "wheat");
			int spareWheat = Math.max(0, storedWheat - wheatReserve);
			return current + (spareWheat / 3);
		}

		if (SettlementRefining.supportsRefining(goodsKey)) {
			return SettlementRefining.effectiveGoodsCount(settlement, goodsKey);
		}

		return settlement.stock().getOrDefault(goodsKey, 0);
	}

	private static String routePartnerName(RouteState route, SettlementState settlement, Function<String, String> settlementNameResolver) {
		String partnerId = route.fromSettlementId().equals(settlement.id()) ? route.toSettlementId() : route.fromSettlementId();
		return settlementNameResolver.apply(partnerId);
	}

	private static String routeSummary(RouteState route) {
		if (!route.lastTransferSummary().isBlank() && !route.lastTransferSummary().equals("No trade")) {
			return route.lastTransferSummary();
		}

		if (route.lastTradeAttemptTick() > 0L) {
			return "Connected, waiting for goods";
		}

		return "Route surveyed, no trade yet";
	}

	private static String humanizeKey(String key) {
		String[] parts = key.split("_");
		StringBuilder result = new StringBuilder();

		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				result.append(' ');
			}

			String part = parts[i];

			if (part.isEmpty()) {
				continue;
			}

			result.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
			result.append(part.substring(1));
		}

		return result.toString();
	}
}
