package com.ronhelwig.livevillages.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class SettlementLightingPlanTest {
	@Test
	void interiorProgressesWithoutUsingStorefrontSpecialties() {
		assertEquals("torch", select(1, SettlementLightingPlan.Role.INTERIOR, Set.of(), Set.of(), Map.of()));
		assertEquals("lantern", select(1, SettlementLightingPlan.Role.INTERIOR, Set.of("minecraft:lantern"), Set.of(), Map.of("lantern", 1)));
		assertEquals("candle", select(2, SettlementLightingPlan.Role.INTERIOR, Set.of("minecraft:candle"), Set.of(), Map.of()));
		assertEquals("end_rod", select(4, SettlementLightingPlan.Role.INTERIOR, Set.of("minecraft:end_rod"), Set.of(), Map.of()));
		assertEquals("copper_bulb", select(4, SettlementLightingPlan.Role.INTERIOR, Set.of("minecraft:copper_bulb"), Set.of(), Map.of()));
	}

	@Test
	void storefrontUsesKnownSpecialtyResourcesOnlyWhenPossessed() {
		assertEquals("glowstone", select(3, SettlementLightingPlan.Role.STOREFRONT, Set.of("minecraft:glowstone"), Set.of(), Map.of()));
		assertEquals("torch", select(4, SettlementLightingPlan.Role.STOREFRONT, Set.of(), Set.of("froglight"), Map.of()));
		assertEquals("froglight", select(4, SettlementLightingPlan.Role.STOREFRONT, Set.of(), Set.of("froglight"), Map.of("froglight", 1)));
		assertEquals("shroomlight", select(4, SettlementLightingPlan.Role.STOREFRONT, Set.of(), Set.of("shroomlight"), Map.of("shroomlight", 1)));
		assertEquals("redstone_lamp", select(4, SettlementLightingPlan.Role.STOREFRONT, Set.of("minecraft:redstone_lamp"), Set.of(), Map.of()));
	}

	@Test
	void farmPrefersKnownJackOLanternAtTierTwo() {
		assertEquals("torch", select(1, SettlementLightingPlan.Role.FARM, Set.of("minecraft:jack_o_lantern"), Set.of(), Map.of()));
		assertEquals("jack_o_lantern", select(2, SettlementLightingPlan.Role.FARM, Set.of("minecraft:jack_o_lantern"), Set.of(), Map.of()));
	}

	@Test
	void guardUsesLearnedSoulLanternAccent() {
		assertEquals("torch", select(2, SettlementLightingPlan.Role.GUARD, Set.of(), Set.of(), Map.of()));
		assertEquals("soul_lantern", select(2, SettlementLightingPlan.Role.GUARD, Set.of("minecraft:soul_lantern"), Set.of(), Map.of()));
	}

	@Test
	void wallLightsRespectSeriousSpacesAndLearnedCopper() {
		assertEquals("torch", SettlementLightingPlan.selectWallMaterial(2, false, Set.of("minecraft:soul_torch")));
		assertEquals("soul_torch", SettlementLightingPlan.selectWallMaterial(2, true, Set.of("minecraft:soul_torch")));
		assertEquals("copper_torch", SettlementLightingPlan.selectWallMaterial(3, true, Set.of("minecraft:soul_torch", "minecraft:copper_torch")));
	}

	@Test
	void gardenTrellisRequiresTierDiscoveryAndCurrentStock() {
		assertEquals("", SettlementLightingPlan.selectGardenTrellisMaterial(2, Set.of("glow_berries"), Map.of("glow_berries", 1)));
		assertEquals("", SettlementLightingPlan.selectGardenTrellisMaterial(3, Set.of("glow_berries"), Map.of()));
		assertEquals("glow_berries", SettlementLightingPlan.selectGardenTrellisMaterial(3, Set.of("glow_berries"), Map.of("glow_berries", 1)));
	}

	private static String select(
		int tier,
		SettlementLightingPlan.Role role,
		Set<String> recipes,
		Set<String> resources,
		Map<String, Integer> stock
	) {
		return SettlementLightingPlan.selectMaterial(tier, role, recipes, resources, stock);
	}
}
