package com.ronhelwig.livevillages.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SettlementCarpenterWorkTest {
	@Test
	void carpenterCraftsTrapdoorsFromStockedLogs() {
		Map<String, Integer> stock = new HashMap<>(Map.of("logs", 4));

		assertTrue(SettlementCarpenterWork.craftWoodOutput(stock, "trapdoor"));
		assertEquals(2, stock.getOrDefault("logs", 0));
		assertEquals(2, stock.getOrDefault("trapdoor", 0));
	}

	@Test
	void carpenterDoesNotCraftTrapdoorsWithoutEnoughLogs() {
		Map<String, Integer> stock = new HashMap<>(Map.of("logs", 1));

		assertFalse(SettlementCarpenterWork.craftWoodOutput(stock, "trapdoor"));
		assertEquals(1, stock.getOrDefault("logs", 0));
		assertEquals(0, stock.getOrDefault("trapdoor", 0));
	}

	@Test
	void carpenterCraftsEveryWoodOutputConsumedBySettlementWork() {
		assertCrafted("fence", 1, 3);
		assertCrafted("fence_gate", 1, 1);
		assertCrafted("door", 2, 3);
		assertCrafted("chest", 2, 1);
		assertCrafted("ladder", 1, 4);
	}

	private static void assertCrafted(String goodsKey, int logCost, int outputAmount) {
		Map<String, Integer> stock = new HashMap<>(Map.of("logs", 4));

		assertTrue(SettlementCarpenterWork.craftWoodOutput(stock, goodsKey));
		assertEquals(4 - logCost, stock.getOrDefault("logs", 0));
		assertEquals(outputAmount, stock.getOrDefault(goodsKey, 0));
	}
}
