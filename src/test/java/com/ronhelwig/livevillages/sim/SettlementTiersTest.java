package com.ronhelwig.livevillages.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SettlementTiersTest {
	@Test
	void eligibilityRequiresBothWealthAndPopulationAtEachSpecifiedGate() {
		assertEquals(1, SettlementTiers.eligibleTier(Map.of("emerald", 499), Map.of("farmer", 16)));
		assertEquals(1, SettlementTiers.eligibleTier(Map.of("emerald", 500), Map.of("farmer", 15)));
		assertEquals(2, SettlementTiers.eligibleTier(Map.of("emerald", 500), Map.of("farmer", 10, "mason", 6)));
		assertEquals(3, SettlementTiers.eligibleTier(Map.of("emerald", 5_000), Map.of("farmer", 32)));
		assertEquals(4, SettlementTiers.eligibleTier(Map.of("emerald", 20_000), Map.of("farmer", 48)));
	}

	@Test
	void recordedTierPersistsOnlyWithScribeSupportWhenEligibilityFalls() {
		assertEquals(1, SettlementTiers.resolvedTier(3, Map.of(), Map.of("farmer", 12)));
		assertEquals(3, SettlementTiers.resolvedTier(3, Map.of(), Map.of(SettlementRoleKeys.SCRIBE, 1)));
		assertEquals(4, SettlementTiers.resolvedTier(2, Map.of("emerald", 20_000), Map.of("farmer", 48)));
	}

	@Test
	void stoneMaterialIsClampedToTheCurrentTier() {
		assertEquals("cobblestone", SettlementTiers.clampStoneMaterialForTier(2, "stone_bricks"));
		assertEquals("smooth_stone", SettlementTiers.clampStoneMaterialForTier(3, "stone_bricks"));
		assertEquals("stone_bricks", SettlementTiers.clampStoneMaterialForTier(4, "stone_bricks"));
	}
}
