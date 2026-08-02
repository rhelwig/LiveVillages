package com.ronhelwig.livevillages.sim;

import java.util.Map;
import java.util.Set;

/**
 * Pure selection policy for semantic blueprint lighting. The caller persists
 * the returned material key with the build site so later knowledge or stock
 * changes cannot alter an in-progress fixture.
 */
final class SettlementLightingPlan {
	enum Role {
		GUARD,
		INTERIOR,
		STOREFRONT,
		FARM
	}

	private SettlementLightingPlan() {
	}

	static String selectMaterial(
		int tier,
		Role role,
		Set<String> knownRecipeIds,
		Set<String> knownResourceKeys,
		Map<String, Integer> stock
	) {
		if (role == Role.GUARD && tier >= 2 && knownRecipeIds.contains("minecraft:soul_lantern")) {
			return "soul_lantern";
		}

		if (role == Role.STOREFRONT && tier >= 4 && knownRecipeIds.contains("minecraft:redstone_lamp")) {
			return "redstone_lamp";
		}

		if (tier >= 4 && knownRecipeIds.contains("minecraft:copper_bulb")) {
			return "copper_bulb";
		}

		if (role == Role.INTERIOR && tier >= 4 && knownRecipeIds.contains("minecraft:end_rod")) {
			return "end_rod";
		}

		if (role == Role.STOREFRONT && tier >= 4) {
			if (knownResourceKeys.contains("froglight") && stock.getOrDefault("froglight", 0) > 0) {
				return "froglight";
			}

			if (knownResourceKeys.contains("shroomlight") && stock.getOrDefault("shroomlight", 0) > 0) {
				return "shroomlight";
			}
		}

		if (role == Role.STOREFRONT && tier >= 3 && knownRecipeIds.contains("minecraft:glowstone")) {
			return "glowstone";
		}

		if (role == Role.FARM && tier >= 2 && knownRecipeIds.contains("minecraft:jack_o_lantern")) {
			return "jack_o_lantern";
		}

		if (tier >= 2 && knownRecipeIds.contains("minecraft:candle")) {
			return "candle";
		}

		if (knownRecipeIds.contains("minecraft:lantern")
			&& (tier >= 2 || stock.getOrDefault("lantern", 0) > 0)) {
			return "lantern";
		}

		return "torch";
	}

	static String selectWallMaterial(int tier, boolean seriousSpace, Set<String> knownRecipeIds) {
		if (tier >= 3 && knownRecipeIds.contains("minecraft:copper_torch")) {
			return "copper_torch";
		}

		if (seriousSpace && tier >= 2 && knownRecipeIds.contains("minecraft:soul_torch")) {
			return "soul_torch";
		}

		return "torch";
	}

	static String selectGardenTrellisMaterial(int tier, Set<String> knownResourceKeys, Map<String, Integer> stock) {
		return tier >= 3
			&& knownResourceKeys.contains("glow_berries")
			&& stock.getOrDefault("glow_berries", 0) > 0
				? "glow_berries"
				: "";
	}
}
