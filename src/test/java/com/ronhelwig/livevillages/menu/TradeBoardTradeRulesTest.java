package com.ronhelwig.livevillages.menu;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TradeBoardTradeRulesTest {
	@Test
	void everyAdvertisedTradeableGoodHasAPositiveBundleAndValue() {
		for (String goodsKey : TradeBoardTradeRules.tradeableGoodsKeys()) {
			assertTrue(TradeBoardTradeRules.bundleSize(goodsKey) > 0, goodsKey);
			assertTrue(TradeBoardTradeRules.itemValuePoints(goodsKey, 100) > 0, goodsKey);
		}
	}

	@Test
	void productionPaymentsCoverDirectIngredientAndRejectInvalidRequests() {
		assertTrue(TradeBoardTradeRules.productionCostPaymentAmount("arrow", 8, "flint") >= 1);
		assertEquals(0, TradeBoardTradeRules.productionCostPaymentAmount(null, 8, "flint"));
		assertEquals(0, TradeBoardTradeRules.productionCostPaymentAmount("arrow", 0, "flint"));
		assertEquals(0, TradeBoardTradeRules.productionCostPaymentAmount("unknown", 8, "flint"));
	}

	@Test
	void valueRequirementsRoundUpAndUnknownGoodsCannotTrade() {
		int unitValue = TradeBoardTradeRules.itemValuePoints("wheat", 100);
		assertEquals(2, TradeBoardTradeRules.requiredItemsForValue("wheat", 100, unitValue + 1));
		assertEquals(0, TradeBoardTradeRules.requiredItemsForValue("unknown", 100, 100));
		assertFalse(TradeBoardTradeRules.isTradeableGoods("unknown"));
	}
}
