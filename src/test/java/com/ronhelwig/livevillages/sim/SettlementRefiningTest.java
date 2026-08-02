package com.ronhelwig.livevillages.sim;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SettlementRefiningTest {
	@Test
	void immediateSmeltingPrefersCoalAndConsumesRealInputAndFuel() {
		Map<String, Integer> stock = new LinkedHashMap<>(Map.of("raw_iron", 1, "coal", 1, "logs", 2, "planks", 2));

		assertTrue(SettlementRefining.consumeRefinedMaterial(stock, "iron_ingot"));
		assertEquals(0, stock.getOrDefault("raw_iron", 0));
		assertEquals(0, stock.getOrDefault("coal", 0));
		assertEquals(2, stock.get("logs"));
		assertEquals(2, stock.get("planks"));
	}

	@Test
	void immediateSmeltingFallsBackFromLogsToPlanks() {
		Map<String, Integer> withLog = new LinkedHashMap<>(Map.of("sand", 1, "logs", 1));
		assertTrue(SettlementRefining.consumeRefinedMaterial(withLog, "glass"));
		assertEquals(0, withLog.getOrDefault("logs", 0));

		Map<String, Integer> withPlank = new LinkedHashMap<>(Map.of("sand", 1, "planks", 1));
		assertTrue(SettlementRefining.consumeRefinedMaterial(withPlank, "glass"));
		assertEquals(0, withPlank.getOrDefault("planks", 0));
	}

	@Test
	void failedRefiningIsAtomic() {
		Map<String, Integer> stock = new LinkedHashMap<>(Map.of("raw_gold", 1));
		Map<String, Integer> before = Map.copyOf(stock);

		assertFalse(SettlementRefining.consumeRefinedMaterial(stock, "gold_ingot"));
		assertEquals(before, stock);
		assertFalse(SettlementRefining.canSupplyRefinedMaterial(stock, "glass"));
		assertEquals(before, stock);
	}

	@Test
	void directStockIsConsumedWithoutUnnecessaryRefining() {
		Map<String, Integer> stock = new LinkedHashMap<>(Map.of("iron_ingot", 1, "raw_iron", 1, "coal", 1));
		assertTrue(SettlementRefining.consumeRefinedMaterial(stock, "iron_ingot"));
		assertEquals(0, stock.getOrDefault("iron_ingot", 0));
		assertEquals(1, stock.get("raw_iron"));
		assertEquals(1, stock.get("coal"));
	}
}
