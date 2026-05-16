package com.ronhelwig.livevillages.sim;

import java.util.Map;

public final class SettlementTiers {
	public static final int MIN_TIER = 1;
	public static final int MAX_TIER = 4;
	private static final int TIER_TWO_EMERALDS = 500;
	private static final int TIER_TWO_POPULATION = 16;
	private static final int TIER_THREE_EMERALDS = 5_000;
	private static final int TIER_THREE_POPULATION = 32;
	private static final int TIER_FOUR_EMERALDS = 20_000;
	private static final int TIER_FOUR_POPULATION = 48;

	private SettlementTiers() {
	}

	public static int unlockedTier(SettlementState settlement) {
		return resolvedTier(settlement.tier(), settlement.wealth(), settlement.population());
	}

	public static int unlockedTier(Map<String, Integer> wealth, Map<String, Integer> population) {
		return eligibleTier(wealth, population);
	}

	public static int eligibleTier(Map<String, Integer> wealth, Map<String, Integer> population) {
		int emeraldWealth = wealth.getOrDefault("emerald", 0);
		int totalPopulation = population.values().stream()
			.mapToInt(Integer::intValue)
			.sum();

		if (emeraldWealth >= TIER_FOUR_EMERALDS && totalPopulation >= TIER_FOUR_POPULATION) {
			return 4;
		}

		if (emeraldWealth >= TIER_THREE_EMERALDS && totalPopulation >= TIER_THREE_POPULATION) {
			return 3;
		}

		if (emeraldWealth >= TIER_TWO_EMERALDS && totalPopulation >= TIER_TWO_POPULATION) {
			return 2;
		}

		return 1;
	}

	public static int resolvedTier(int recordedTier, Map<String, Integer> wealth, Map<String, Integer> population) {
		int normalizedRecordedTier = normalize(recordedTier);
		int eligibleTier = eligibleTier(wealth, population);

		if (eligibleTier > normalizedRecordedTier) {
			return eligibleTier;
		}

		if (eligibleTier < normalizedRecordedTier && !hasScribeSupport(population)) {
			return eligibleTier;
		}

		return normalizedRecordedTier;
	}

	public static int normalize(int tier) {
		return Math.max(MIN_TIER, Math.min(MAX_TIER, tier));
	}

	private static boolean hasScribeSupport(Map<String, Integer> population) {
		return population.getOrDefault(SettlementRoleKeys.SCRIBE, 0) > 0;
	}

	public static String clampStoneMaterialForTier(int tier, String stoneMaterial) {
		int normalizedTier = normalize(tier);

		return switch (stoneMaterial) {
			case "smooth_stone" -> normalizedTier >= 3 ? "smooth_stone" : "cobblestone";
			case "stone_bricks" -> normalizedTier >= 4 ? "stone_bricks" : normalizedTier >= 3 ? "smooth_stone" : "cobblestone";
			default -> stoneMaterial;
		};
	}
}
