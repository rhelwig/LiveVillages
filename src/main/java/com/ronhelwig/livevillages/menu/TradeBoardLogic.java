package com.ronhelwig.livevillages.menu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import com.ronhelwig.livevillages.sim.RouteState;
import com.ronhelwig.livevillages.sim.SettlementBuildBlockState;
import com.ronhelwig.livevillages.sim.SettlementBuildBlockStatus;
import com.ronhelwig.livevillages.sim.SettlementBuildSite;
import com.ronhelwig.livevillages.sim.SettlementEconomyRules;
import com.ronhelwig.livevillages.sim.SettlementProject;
import com.ronhelwig.livevillages.sim.SettlementProjectType;
import com.ronhelwig.livevillages.sim.SettlementState;

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
		int population = Math.max(1, settlement.totalPopulation());
		List<TradeBoardGoodsView> shortages = new ArrayList<>();
		List<TradeBoardGoodsView> surpluses = new ArrayList<>();

		for (SettlementEconomyRules.TargetRule targetRule : SettlementEconomyRules.targetRules()) {
			if (targetRule.goodsKey().equals("emerald")) {
				continue;
			}

			int target = SettlementEconomyRules.targetForGoods(settlement, targetRule.goodsKey());
			int current = effectiveCurrentForDisplay(settlement, targetRule.goodsKey());
			int tradePricePercent = SettlementEconomyRules.tradePricePercent(current, target);
			int tradeBundleSize = TradeBoardTradeRules.bundleSize(targetRule.goodsKey());
			int tradePriceEmeralds = TradeBoardTradeRules.bundlePriceEmeralds(targetRule.goodsKey(), tradePricePercent);

			if (current < target) {
				shortages.add(new TradeBoardGoodsView(
					targetRule.goodsKey(),
					humanizeKey(targetRule.goodsKey()),
					current,
					target,
					tradePricePercent,
					tradeBundleSize,
					tradePriceEmeralds
				));
			} else if (current > target * 2 && (tradeBundleSize <= 0 || current >= tradeBundleSize)) {
				surpluses.add(new TradeBoardGoodsView(
					targetRule.goodsKey(),
					humanizeKey(targetRule.goodsKey()),
					current,
					target,
					tradePricePercent,
					tradeBundleSize,
					tradePriceEmeralds
				));
			}
		}

		addConstructionDemandShortages(shortages, settlement, constructionDemand);
		surpluses.removeIf(surplus -> indexOfGoods(shortages, surplus.goodsKey()) >= 0);
		shortages.sort(Comparator.comparingInt((TradeBoardGoodsView entry) ->
			shortagePriority(settlement, constructionDemand, entry)
		).reversed());
		surpluses.sort(Comparator.comparingInt((TradeBoardGoodsView entry) -> entry.current() - entry.target()).reversed());

		List<TradeBoardGoodsView> stock = settlement.stock().entrySet().stream()
			.filter(entry -> entry.getValue() > 0)
			.sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
			.map(entry -> new TradeBoardGoodsView(entry.getKey(), humanizeKey(entry.getKey()), entry.getValue(), 0, 100, 0, 0))
			.toList();
		List<TradeBoardRoleView> roleCounts = createRoleCounts(rolePopulation);
		List<TradeBoardRouteView> routeViews = routes.stream()
			.sorted(
				Comparator.comparingLong(RouteState::lastTradeTick).reversed()
					.thenComparing(route -> route.lastTransferSummary().isBlank() ? 1 : 0)
					.thenComparing(route -> routePartnerName(route, settlement, settlementNameResolver))
			)
			.limit(Math.max(0, maxRouteRows))
			.map(route -> new TradeBoardRouteView(
				routePartnerName(route, settlement, settlementNameResolver),
				routeSummary(route)
			))
			.toList();
		List<TradeBoardProjectView> projects = settlement.projects().stream()
			.sorted(
				Comparator.comparingInt((SettlementProject project) -> project.type() == SettlementProjectType.ROAD ? 0 : 1)
					.thenComparing(Comparator.comparingInt(SettlementProject::progressPercent).reversed())
			)
			.limit(Math.max(0, maxProjectRows))
			.map(project -> new TradeBoardProjectView(projectLabel(project, settlementNameResolver), project.progressPercent()))
			.toList();

		return new TradeBoardSettlementView(
			settlement.id(),
			settlement.name(),
			settlement.kind(),
			settlement.totalPopulation(),
			settlement.housingCapacity(),
			settlement.wealth().getOrDefault("emerald", 0),
			settlement.comfort(),
			settlement.security(),
			routes.size(),
			summarizeGrowth(settlement, population),
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
				if (block.status() != SettlementBuildBlockStatus.MISSING_MATERIAL) {
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
				} else if (block.expectedMaterialKey().equals("chest")) {
					addConstructionDemand(demand, "planks", 8);
				}
			}
		}

		return Map.copyOf(demand);
	}

	private static void addConstructionDemandShortages(
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

			int current = settlement.stock().getOrDefault(goodsKey, 0);
			int target = current + demand;
			int existingIndex = indexOfGoods(shortages, goodsKey);

			if (existingIndex >= 0) {
				TradeBoardGoodsView existing = shortages.get(existingIndex);
				target = Math.max(existing.target(), target);
				current = existing.current();
				shortages.set(existingIndex, goodsView(goodsKey, current, target));
			} else {
				shortages.add(goodsView(goodsKey, current, target));
			}
		}
	}

	private static TradeBoardGoodsView goodsView(String goodsKey, int current, int target) {
		int tradePricePercent = SettlementEconomyRules.tradePricePercent(current, target);
		return new TradeBoardGoodsView(
			goodsKey,
			humanizeKey(goodsKey),
			current,
			target,
			tradePricePercent,
			TradeBoardTradeRules.bundleSize(goodsKey),
			TradeBoardTradeRules.bundlePriceEmeralds(goodsKey, tradePricePercent)
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

	private static String summarizeGrowth(SettlementState settlement, int population) {
		if (population <= 0) {
			return "Empty";
		}

		int freeHousing = settlement.housingCapacity() - population;
		int storedFood = settlement.stock().getOrDefault("bread", 0)
			+ settlement.stock().getOrDefault("beef", 0)
			+ settlement.stock().getOrDefault("cod", 0)
			+ settlement.stock().getOrDefault("carrot", 0)
			+ settlement.stock().getOrDefault("potato", 0)
			+ settlement.stock().getOrDefault("beetroot", 0)
			+ settlement.stock().getOrDefault("wheat", 0);
		int targetFood = SettlementEconomyRules.targetForGoods("bread", population)
			+ SettlementEconomyRules.targetForGoods("beef", population)
			+ SettlementEconomyRules.targetForGoods("cod", population)
			+ SettlementEconomyRules.targetForGoods("carrot", population)
			+ SettlementEconomyRules.targetForGoods("potato", population)
			+ SettlementEconomyRules.targetForGoods("beetroot", population)
			+ SettlementEconomyRules.targetForGoods("wheat", population);

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

	private static int effectiveCurrentForDisplay(SettlementState settlement, String goodsKey) {
		int current = settlement.stock().getOrDefault(goodsKey, 0);

		if (!goodsKey.equals("bread")) {
			return current;
		}

		int storedWheat = settlement.stock().getOrDefault("wheat", 0);
		int wheatReserve = SettlementEconomyRules.targetForGoods(settlement, "wheat");
		int spareWheat = Math.max(0, storedWheat - wheatReserve);
		return current + (spareWheat / 3);
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
