package com.ronhelwig.livevillages.sim;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;

public final class SettlementEconomyRules {
	public static final double TICKS_PER_DAY = 24_000.0D;
	public static final long MIN_SIMULATION_TICKS = 6_000L;
	public static final double MAX_CATCH_UP_DAYS = 5.0D;
	// Temporary playtest tuning: keep visible worker loops moving faster until major systems settle down.
	public static final double WORKER_PRODUCTIVITY_MULTIPLIER = 2.0D;
	private static final List<String> FOOD_GOODS = List.of(
		"bread",
		"baked_potato",
		"cookie",
		"pumpkin_pie",
		"cake",
		"golden_apple",
		"beef",
		"mutton",
		"pork",
		"cod",
		"carrot",
		"potato",
		"beetroot",
		"wheat"
	);
	private static final List<TargetRule> TARGET_RULES = List.of(
		new TargetRule("bread", population -> 8 + population * 4),
		new TargetRule("baked_potato", population -> 4 + population),
		new TargetRule("cookie", population -> 3 + population / 3),
		new TargetRule("pumpkin_pie", population -> 2 + population / 4),
		new TargetRule("cake", population -> 1 + population / 12),
		new TargetRule("golden_apple", population -> 1 + population / 24),
		new TargetRule("beef", population -> 4 + population * 2),
		new TargetRule("mutton", population -> 3 + population),
		new TargetRule("pork", population -> 3 + population),
		new TargetRule("leather", population -> 0),
		new TargetRule("wheat_seeds", population -> 0),
		new TargetRule("cod", population -> 6 + population * 2),
		new TargetRule("wheat", population -> 16 + population * 6),
		new TargetRule("carrot", population -> 8 + population * 3),
		new TargetRule("potato", population -> 8 + population * 3),
		new TargetRule("beetroot", population -> 6 + population * 2),
		new TargetRule("wool", population -> 0),
		new TargetRule("paper", population -> 0),
		new TargetRule("book", population -> 2 + population / 4),
		new TargetRule("bookshelf", population -> 1 + population / 12),
		new TargetRule("glass_bottle", population -> 0),
		new TargetRule("nether_wart", population -> 0),
		new TargetRule("glistering_melon_slice", population -> 0),
		new TargetRule("honey_bottle", population -> 0),
		new TargetRule("honeycomb", population -> 0),
		new TargetRule("candle", population -> 0),
		new TargetRule("shears", population -> 0),
		new TargetRule("string", population -> 0),
		new TargetRule("sling", population -> 0),
		new TargetRule("crooked_staff", population -> 0),
		new TargetRule("scythe", population -> 0),
		new TargetRule("healing_potion", population -> 1 + population / 8),
		new TargetRule("leather_helmet", population -> 0),
		new TargetRule("leather_chestplate", population -> 1 + population / 8),
		new TargetRule("leather_leggings", population -> 0),
		new TargetRule("leather_boots", population -> 0),
		new TargetRule("iron_sword", population -> 1 + population / 8),
		new TargetRule("iron_pickaxe", population -> 1 + population / 8),
		new TargetRule("iron_helmet", population -> 0),
		new TargetRule("iron_chestplate", population -> 0),
		new TargetRule("iron_leggings", population -> 0),
		new TargetRule("iron_boots", population -> 0),
		new TargetRule("shield", population -> 0),
		new TargetRule("desecrated_enemy_banner", population -> 0),
		new TargetRule("logs", population -> 12 + population * 6),
		new TargetRule("planks", population -> 16 + population * 8),
		new TargetRule("stairs", population -> 0),
		new TargetRule("slab", population -> 0),
		new TargetRule("trapdoor", population -> 0),
		new TargetRule("stick", population -> 0),
		new TargetRule("flint", population -> 0),
		new TargetRule("feather", population -> 0),
		new TargetRule("arrow", population -> 0),
		new TargetRule("apple", population -> 0),
		new TargetRule("egg", population -> 0),
		new TargetRule("bone_meal", population -> 0),
		new TargetRule("milk_bucket", population -> 0),
		new TargetRule("sugar", population -> 0),
		new TargetRule("cocoa_beans", population -> 0),
		new TargetRule("pumpkin", population -> 0),
		new TargetRule("flower", population -> 0),
		new TargetRule("oak_sapling", population -> 0),
		new TargetRule("spruce_sapling", population -> 0),
		new TargetRule("birch_sapling", population -> 0),
		new TargetRule("jungle_sapling", population -> 0),
		new TargetRule("acacia_sapling", population -> 0),
		new TargetRule("cherry_sapling", population -> 0),
		new TargetRule("dark_oak_sapling", population -> 0),
		new TargetRule("pale_oak_sapling", population -> 0),
		new TargetRule("mangrove_propagule", population -> 0),
		new TargetRule("cobblestone", population -> 24 + population * 8),
		new TargetRule("dirt", population -> 0),
		new TargetRule("ladder", population -> 0),
		new TargetRule("milepost", population -> 0),
		new TargetRule("campfire", population -> 0),
		new TargetRule("bee_hive", population -> 0),
		new TargetRule("iron_ingot", population -> 4 + population * 2),
		new TargetRule("gold_ingot", population -> 0),
		new TargetRule("emerald", population -> 4 + population)
	);

	private SettlementEconomyRules() {
	}

	public static List<TargetRule> targetRules() {
		return TARGET_RULES;
	}

	public static int scaledWorkerTickInterval(int baseTicks) {
		if (baseTicks <= 0) {
			return 1;
		}

		return Math.max(20, (int) Math.round(baseTicks / WORKER_PRODUCTIVITY_MULTIPLIER));
	}

	public static int scaledWorkerDailyUnits(int baseUnits) {
		if (baseUnits <= 0) {
			return 0;
		}

		return Math.max(1, (int) Math.round(baseUnits * WORKER_PRODUCTIVITY_MULTIPLIER));
	}

	public static int scaledPeriodicAmount(String key, double dailyRate, long previousTick, long currentTick) {
		if (key == null || key.isBlank() || dailyRate <= 0.0D || currentTick <= previousTick) {
			return 0;
		}

		double phase = stableUnitPhase(key);
		double previousProgress = (Math.max(0L, previousTick) / TICKS_PER_DAY * dailyRate) + phase;
		double currentProgress = (Math.max(0L, currentTick) / TICKS_PER_DAY * dailyRate) + phase;
		return Math.max(0, (int) Math.floor(currentProgress) - (int) Math.floor(previousProgress));
	}

	public static double scaledWorkerDailyRate(double baseRate) {
		return baseRate <= 0.0D ? 0.0D : baseRate * WORKER_PRODUCTIVITY_MULTIPLIER;
	}

	private static double stableUnitPhase(String key) {
		return stableModulo(key, 1_000_000L) / 1_000_000.0D;
	}

	private static long stableModulo(String key, long modulo) {
		long hash = 1125899906842597L;

		for (int i = 0; i < key.length(); i++) {
			hash = (hash * 31L) + key.charAt(i);
		}

		return Math.floorMod(hash, modulo);
	}

	public static int targetForGoods(String goodsKey, int population) {
		for (TargetRule targetRule : TARGET_RULES) {
			if (targetRule.goodsKey().equals(goodsKey)) {
				return targetRule.targetForPopulation().applyAsInt(population);
			}
		}

		return 0;
	}

	public static int targetForGoods(SettlementState settlement, String goodsKey) {
		return targetForGoods(settlement, List.of(), goodsKey);
	}

	public static int targetForGoods(SettlementState settlement, Collection<SettlementBuildSite> buildSites, String goodsKey) {
		int baseTarget = isUnlockedForSettlementTier(goodsKey, SettlementTiers.unlockedTier(settlement))
			? targetForGoods(goodsKey, Math.max(1, settlement.totalPopulation()))
			: 0;
		return baseTarget
			+ professionalReserveForGoods(settlement, goodsKey)
			+ plannedDemandForGoods(settlement, goodsKey)
			+ activeBuildSiteDemandForGoods(buildSites, goodsKey);
	}

	public static int shortagePriority(SettlementState settlement, String goodsKey, int current, int target) {
		return shortagePriority(settlement, List.of(), goodsKey, current, target);
	}

	public static int shortagePriority(
		SettlementState settlement,
		Collection<SettlementBuildSite> buildSites,
		String goodsKey,
		int current,
		int target
	) {
		int shortage = Math.max(0, target - current);
		int priority = shortage;
		int plannedDemand = plannedDemandForGoods(settlement, goodsKey) + activeBuildSiteDemandForGoods(buildSites, goodsKey);

		if (plannedDemand > 0) {
			priority += 200;

			if (targetForGoods(goodsKey, Math.max(1, settlement.totalPopulation())) <= 0) {
				priority += 100;
			}
		}

		if (shouldPrioritizeFoodGrowth(settlement) && isFoodGoods(goodsKey)) {
			priority += 120;

			if (goodsKey.equals("beef") || goodsKey.equals("mutton") || goodsKey.equals("pork")) {
				priority += 20;
			}
		}

		return priority;
	}

	public static boolean isFoodGoods(String goodsKey) {
		return FOOD_GOODS.contains(goodsKey);
	}

	public static int requiredTierForGoods(String goodsKey) {
		return switch (goodsKey) {
			case "diamond", "redstone", "cookie", "pumpkin_pie" -> 2;
			case "cake" -> 3;
			case "golden_apple" -> 4;
			default -> 1;
		};
	}

	public static boolean isUnlockedForSettlementTier(String goodsKey, int settlementTier) {
		return SettlementTiers.normalize(settlementTier) >= requiredTierForGoods(goodsKey);
	}

	public static boolean shouldPrioritizeFoodGrowth(SettlementState settlement) {
		if (settlement.housingCapacity() <= settlement.totalPopulation()) {
			return false;
		}

		int storedFood = 0;
		int targetFood = 0;
		int population = Math.max(1, settlement.totalPopulation());

		for (String goodsKey : FOOD_GOODS) {
			storedFood += settlement.stock().getOrDefault(goodsKey, 0);
			targetFood += targetForGoods(goodsKey, population);
		}

		return storedFood < Math.max(8, targetFood);
	}

	public static int plannedDemandForGoods(SettlementState settlement, String goodsKey) {
		int demand = activeProjectDemandForGoods(settlement, goodsKey);

		if (settlement.kind() == SettlementKind.OUTPOST) {
			return demand + outpostPlannedDemandForGoods(settlement, goodsKey);
		}

		if (needsHousingProject(settlement) && !hasProjectType(settlement, SettlementProjectType.HOUSING)) {
			demand += SettlementProjectType.HOUSING.stockCost().getOrDefault(goodsKey, 0);
		}

		if (needsComposterProject(settlement) && !hasProjectType(settlement, SettlementProjectType.COMPOSTER)) {
			demand += SettlementProjectType.COMPOSTER.stockCost().getOrDefault(goodsKey, 0);
		}

		if (needsStorageProject(settlement) && !hasProjectType(settlement, SettlementProjectType.STORAGE)) {
			demand += SettlementProjectType.STORAGE.stockCost().getOrDefault(goodsKey, 0);
		}

		return demand;
	}

	private static int outpostPlannedDemandForGoods(SettlementState settlement, String goodsKey) {
		int demand = 0;

		if (needsOutpostStorageProject(settlement) && !hasProjectType(settlement, SettlementProjectType.STORAGE)) {
			demand += SettlementProjectType.STORAGE.stockCost().getOrDefault(goodsKey, 0);
		}

		if (needsOutpostDefenseProject(settlement) && !hasProjectType(settlement, SettlementProjectType.DEFENSE)) {
			demand += SettlementProjectType.DEFENSE.stockCost().getOrDefault(goodsKey, 0);
		}

		return demand;
	}

	public static int activeBuildSiteDemandForGoods(Collection<SettlementBuildSite> buildSites, String goodsKey) {
		if (buildSites.isEmpty()) {
			return 0;
		}

		int demand = 0;

		for (SettlementBuildSite buildSite : buildSites) {
			if (buildSite.complete()) {
				continue;
			}

			for (SettlementBuildBlockState block : buildSite.blocks()) {
				if (!goodsKey.equals(block.expectedMaterialKey())
					|| block.status() == SettlementBuildBlockStatus.PLACED
					|| block.status() == SettlementBuildBlockStatus.PLAYER_PLACED) {
					continue;
				}

				demand++;
			}
		}

		return demand;
	}

	private static int professionalReserveForGoods(SettlementState settlement, String goodsKey) {
		if (settlement.kind() == SettlementKind.OUTPOST) {
			int outpostReserve = outpostReserveForGoods(goodsKey);
			if (outpostReserve > 0) {
				return outpostReserve;
			}
		}

		int farmers = settlement.population().getOrDefault(SettlementRoleKeys.FARMER, 0);
		int gardeners = settlement.population().getOrDefault(SettlementRoleKeys.GARDENER, 0);
		int shepherds = settlement.population().getOrDefault(SettlementRoleKeys.SHEPHERD, 0);

		if (goodsKey.equals("sling")) {
			int slingUsers = gardeners + shepherds;
			return slingUsers > 0 ? Math.max(1, slingUsers) : 0;
		}

		if (goodsKey.equals("crooked_staff")) {
			return shepherds > 0 ? Math.max(1, shepherds) : 0;
		}

		if (goodsKey.equals("scythe")) {
			return farmers > 0 ? Math.max(1, farmers) : 0;
		}

		if (goodsKey.equals("string") && gardeners + shepherds > 0) {
			return 8;
		}

		if (settlement.population().getOrDefault(SettlementRoleKeys.FORESTER, 0) > 0) {
			if (SettlementGoods.isSeedlingGoods(goodsKey)) {
				return goodsKey.equals("dark_oak_sapling") ? 8 : 4;
			}

			if (goodsKey.equals("apple")) {
				return 8;
			}

			if (goodsKey.equals("stick")) {
				return 16;
			}
		}

		if (settlement.population().getOrDefault(SettlementRoleKeys.CARPENTER, 0) > 0) {
			if (goodsKey.equals("stairs")) {
				return 6;
			}

			if (goodsKey.equals("slab")) {
				return 12;
			}
		}

		if (settlement.population().getOrDefault(SettlementRoleKeys.FLETCHER, 0) > 0) {
			if (goodsKey.equals("arrow")) {
				return 32;
			}

			if (goodsKey.equals("flint") || goodsKey.equals("feather")) {
				return 8;
			}

			if (goodsKey.equals("copper_nugget")) {
				return 8;
			}
		}

		if (settlement.population().getOrDefault(SettlementRoleKeys.GARDENER, 0) > 0) {
			if (goodsKey.equals("wheat_seeds")) {
				return 16;
			}

			if (goodsKey.equals("bone_meal")) {
				return 8;
			}

			if (goodsKey.equals("dirt")) {
				return 12;
			}

			if (goodsKey.equals("trapdoor")) {
				return 8;
			}

			if (goodsKey.equals("egg")) {
				return 8;
			}
		}

		if (settlement.population().getOrDefault(SettlementRoleKeys.BEEKEEPER, 0) > 0) {
			if (goodsKey.equals("glass_bottle")) {
				return 12;
			}

			if (goodsKey.equals("shears")) {
				return 1;
			}

			if (goodsKey.equals("campfire")) {
				return 2;
			}

			if (goodsKey.equals("honey_bottle") || goodsKey.equals("honeycomb")) {
				return 8;
			}

			if (goodsKey.equals("candle")) {
				return 8;
			}

			if (goodsKey.equals("bee_hive")) {
				return 3;
			}

			if (goodsKey.equals("flower")) {
				return 8;
			}
		}

		if (settlement.population().getOrDefault("miner", 0) > 0) {
			if (goodsKey.equals("dirt")) {
				return 8;
			}

			if (goodsKey.equals("ladder")) {
				return 16;
			}

			if (goodsKey.equals("coal")) {
				return 4;
			}

			if (goodsKey.equals("copper_ingot")) {
				return 4;
			}
		}

		if (settlement.population().getOrDefault(SettlementRoleKeys.BAKER, 0) > 0) {
			int tier = SettlementTiers.unlockedTier(settlement);

			if (goodsKey.equals("wheat")) {
				return 24;
			}

			if (goodsKey.equals("potato")) {
				return 12;
			}

			if (tier >= 2) {
				if (goodsKey.equals("sugar")) {
					return 8;
				}

				if (goodsKey.equals("cocoa_beans")) {
					return 6;
				}

				if (goodsKey.equals("pumpkin")) {
					return 4;
				}

				if (goodsKey.equals("egg")) {
					return 6;
				}
			}

			if (tier >= 3 && goodsKey.equals("milk_bucket")) {
				return 3;
			}

			if (tier >= 4) {
				if (goodsKey.equals("apple")) {
					return 4;
				}

				if (goodsKey.equals("gold_ingot")) {
					return 8;
				}
			}
		}

		return 0;
	}

	private static int outpostReserveForGoods(String goodsKey) {
		return switch (goodsKey) {
			case "arrow" -> 48;
			case "iron_ingot" -> 16;
			case "raw_iron", "coal" -> 12;
			case "leather", "flint", "feather" -> 16;
			case "bread", "beef", "mutton", "pork", "cod" -> 18;
			case "wheat", "carrot", "potato", "beetroot" -> 24;
			case "logs", "planks", "cobblestone" -> 36;
			case "stick" -> 24;
			case "torch" -> 16;
			case "lantern" -> 8;
			case "chest", "ladder" -> 4;
			case "emerald" -> 8;
			default -> 0;
		};
	}

	private static int activeProjectDemandForGoods(SettlementState settlement, String goodsKey) {
		int demand = 0;

		for (SettlementProject project : settlement.projects()) {
			demand += project.type().stockCost().getOrDefault(goodsKey, 0);
		}

		return demand;
	}

	private static boolean needsHousingProject(SettlementState settlement) {
		return settlement.totalPopulation() > 0 && settlement.housingCapacity() < settlement.totalPopulation() + 1;
	}

	private static boolean needsComposterProject(SettlementState settlement) {
		return settlement.population().getOrDefault("farmer", 0) > 0 && settlement.housingCapacity() >= settlement.totalPopulation() + 1;
	}

	private static boolean needsStorageProject(SettlementState settlement) {
		if (settlement.housingCapacity() < settlement.totalPopulation() + 1) {
			return false;
		}

		int totalStock = settlement.stock().values().stream()
			.mapToInt(Integer::intValue)
			.sum();
		return totalStock >= 48;
	}

	private static boolean needsOutpostStorageProject(SettlementState settlement) {
		int totalStock = settlement.stock().values().stream()
			.mapToInt(Integer::intValue)
			.sum();
		return totalStock >= 24;
	}

	private static boolean needsOutpostDefenseProject(SettlementState settlement) {
		int population = Math.max(1, settlement.totalPopulation());
		int desiredDefense = Math.max(1, (int) Math.ceil(population / 4.0D));
		return settlement.defenseLevel() < desiredDefense;
	}

	private static boolean hasProjectType(SettlementState settlement, SettlementProjectType type) {
		return settlement.projects().stream()
			.anyMatch(project -> project.type() == type);
	}

	public static int tradePricePercent(int current, int target) {
		if (target <= 0) {
			return 100;
		}

		double ratio = current / (double) Math.max(1, target);

		if (ratio < 1.0D) {
			double shortagePressure = 1.0D - ratio;
			return clamp((int) Math.round(100.0D + shortagePressure * 120.0D), 100, 220);
		}

		double surplusPressure = ratio - 1.0D;
		return clamp((int) Math.round(100.0D - Math.min(45.0D, surplusPressure * 22.5D)), 55, 100);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	public record TargetRule(String goodsKey, IntUnaryOperator targetForPopulation) {
	}
}
