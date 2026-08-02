package com.ronhelwig.livevillages.menu;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import com.ronhelwig.livevillages.sim.SettlementEconomyRules;
import org.junit.jupiter.api.Test;

class TradeBoardLightingRulesTest {
	private static final Map<String, Integer> REQUIRED_TIERS = Map.ofEntries(
		Map.entry("torch", 1), Map.entry("lantern", 1),
		Map.entry("candle", 2), Map.entry("jack_o_lantern", 2), Map.entry("soul_lantern", 2),
		Map.entry("glowstone", 3), Map.entry("sea_lantern", 3),
		Map.entry("shroomlight", 4), Map.entry("froglight", 4), Map.entry("copper_bulb", 4),
		Map.entry("redstone_lamp", 4), Map.entry("end_rod", 4)
	);

	@Test
	void lightingGoodsAreTradeableAtAndAboveTheirRequiredTier() {
		REQUIRED_TIERS.forEach((goodsKey, requiredTier) -> {
			assertTrue(TradeBoardTradeRules.isTradeableGoods(goodsKey), goodsKey);
			assertEquals(requiredTier, SettlementEconomyRules.requiredTierForGoods(goodsKey), goodsKey);
			assertTrue(TradeBoardTradeRules.isUnlockedForSettlementTier(goodsKey, requiredTier), goodsKey);
			if (requiredTier > 1) {
				assertFalse(TradeBoardTradeRules.isUnlockedForSettlementTier(goodsKey, requiredTier - 1), goodsKey);
			}
		});
	}

	@Test
	void lightingProductionRecipesHavePositiveValues() {
		for (String goodsKey : new String[] {
			"torch", "candle", "jack_o_lantern", "lantern", "soul_lantern",
			"glowstone", "sea_lantern", "copper_bulb", "redstone_lamp", "end_rod"
		}) {
			assertTrue(TradeBoardTradeRules.bundleSize(goodsKey) > 0, goodsKey);
			assertTrue(TradeBoardTradeRules.productionCostItemValuePoints(goodsKey) > 0, goodsKey);
		}
	}
}
