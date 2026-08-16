package com.ronhelwig.livevillages.sim;

import static org.junit.jupiter.api.Assertions.*;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SettlementConstructionMaterialsLightingTest {
	@Test
	void candleConsumesHoneycombAndString() {
		Map<String, Integer> stock = new LinkedHashMap<>(Map.of("honeycomb", 1, "string", 1));
		var result = SettlementConstructionMaterials.consumeMaterial(stock, new LinkedHashMap<>(), "candle");
		assertTrue(result.supplied());
		assertEquals(1, result.craftingSteps());
		assertTrue(stock.isEmpty());
	}

	@Test
	void failedCandleCraftIsAtomic() {
		Map<String, Integer> stock = stock("honeycomb", 1);
		Map<String, Integer> original = new LinkedHashMap<>(stock);
		var result = SettlementConstructionMaterials.consumeMaterial(stock, new LinkedHashMap<>(), "candle");
		assertFalse(result.supplied());
		assertEquals("string", result.missingMaterialKey());
		assertEquals(original, stock);
	}

	@Test
	void jackOLanternCanCraftItsTorchAndPreservesBatchRemainder() {
		Map<String, Integer> stock = new LinkedHashMap<>(Map.of("carved_pumpkin", 1, "stick", 1, "coal", 1));
		var result = SettlementConstructionMaterials.consumeMaterial(stock, new LinkedHashMap<>(), "jack_o_lantern");
		assertTrue(result.supplied());
		assertEquals(2, result.craftingSteps());
		assertEquals(3, stock.get("torch"));
		assertFalse(stock.containsKey("carved_pumpkin"));
	}

	@Test
	void lanternCanCraftItsTorchAndPreservesBatchRemainder() {
		Map<String, Integer> stock = new LinkedHashMap<>(Map.of("iron_ingot", 1, "stick", 1, "coal", 1));
		var result = SettlementConstructionMaterials.consumeMaterial(stock, new LinkedHashMap<>(), "lantern");
		assertTrue(result.supplied());
		assertEquals(2, result.craftingSteps());
		assertEquals(3, stock.get("torch"));
		assertEquals(1, stock.get("iron_nugget"));
		assertFalse(stock.containsKey("iron_ingot"));
	}

	@Test
	void seaLanternConsumesExactVanillaRecipeInputs() {
		Map<String, Integer> stock = new LinkedHashMap<>(Map.of("prismarine_shard", 4, "prismarine_crystals", 5));
		var result = SettlementConstructionMaterials.consumeMaterial(stock, new LinkedHashMap<>(), "sea_lantern");
		assertTrue(result.supplied());
		assertEquals(1, result.craftingSteps());
		assertTrue(stock.isEmpty());
	}

	@Test
	void failedSeaLanternCraftIsAtomic() {
		Map<String, Integer> stock = new LinkedHashMap<>(Map.of("prismarine_shard", 4, "prismarine_crystals", 4));
		Map<String, Integer> original = new LinkedHashMap<>(stock);
		var result = SettlementConstructionMaterials.consumeMaterial(stock, new LinkedHashMap<>(), "sea_lantern");
		assertFalse(result.supplied());
		assertEquals("prismarine_crystals", result.missingMaterialKey());
		assertEquals(original, stock);
	}

	@Test
	void copperBulbUsesThreeBlocksAndLeavesBatchRemainder() {
		Map<String, Integer> stock = new LinkedHashMap<>(Map.of("copper_block", 3, "redstone", 1, "blaze_rod", 1));
		var result = SettlementConstructionMaterials.consumeMaterial(stock, new LinkedHashMap<>(), "copper_bulb");
		assertTrue(result.supplied());
		assertEquals(1, result.craftingSteps());
		assertEquals(Map.of("copper_bulb", 3), stock);
	}

	@Test
	void redstoneLampCanCraftGlowstoneAndConsumesCircuitDust() {
		Map<String, Integer> stock = new LinkedHashMap<>(Map.of("glowstone_dust", 4, "redstone", 4));
		var result = SettlementConstructionMaterials.consumeMaterial(stock, new LinkedHashMap<>(), "redstone_lamp");
		assertTrue(result.supplied());
		assertEquals(2, result.craftingSteps());
		assertTrue(stock.isEmpty());
	}

	@Test
	void redstoneFixtureBaseConsumesNineDust() {
		Map<String, Integer> stock = stock("redstone", 9);
		var result = SettlementConstructionMaterials.consumeMaterial(stock, new LinkedHashMap<>(), "redstone_block");
		assertTrue(result.supplied());
		assertEquals(1, result.craftingSteps());
		assertTrue(stock.isEmpty());
	}

	@Test
	void endRodCraftLeavesVanillaBatchRemainder() {
		Map<String, Integer> stock = new LinkedHashMap<>(Map.of("blaze_rod", 1, "popped_chorus_fruit", 1));
		var result = SettlementConstructionMaterials.consumeMaterial(stock, new LinkedHashMap<>(), "end_rod");
		assertTrue(result.supplied());
		assertEquals(1, result.craftingSteps());
		assertEquals(Map.of("end_rod", 3), stock);
	}

	@Test
	void soulLanternCanCraftSoulTorchAndPreservesBatchRemainder() {
		Map<String, Integer> stock = new LinkedHashMap<>(Map.of(
			"coal", 1,
			"iron_ingot", 1,
			"soul_soil", 1,
			"stick", 1
		));
		var result = SettlementConstructionMaterials.consumeMaterial(stock, new LinkedHashMap<>(), "soul_lantern");
		assertTrue(result.supplied());
		assertEquals(2, result.craftingSteps());
		assertEquals(Map.of("iron_nugget", 1, "soul_torch", 3), stock);
	}

	@Test
	void campfireCanUseExtraLogAsCharcoal() {
		Map<String, Integer> stock = new LinkedHashMap<>(Map.of("logs", 4, "stick", 3));
		var result = SettlementConstructionMaterials.consumeMaterial(stock, new LinkedHashMap<>(), "campfire");
		assertTrue(result.supplied());
		assertTrue(stock.isEmpty());
	}

	@Test
	void campfireStillUsesCoalWhenPresent() {
		Map<String, Integer> stock = new LinkedHashMap<>(Map.of("logs", 3, "stick", 3, "coal", 1));
		var result = SettlementConstructionMaterials.consumeMaterial(stock, new LinkedHashMap<>(), "campfire");
		assertTrue(result.supplied());
		assertTrue(stock.isEmpty());
	}

	@Test
	void farmStarterCostCanCraftPlanksFromLogs() {
		Map<String, Integer> stock = new LinkedHashMap<>(Map.of("planks", 5, "logs", 1));
		assertTrue(SettlementConstructionMaterials.tryConsumeCost(stock, Map.of("planks", 7)));
		assertEquals(2, stock.getOrDefault("planks", 0));
		assertFalse(stock.containsKey("logs"));
	}

	@Test
	void farmStarterCostFailsWithoutEnoughWood() {
		Map<String, Integer> stock = new LinkedHashMap<>(Map.of("planks", 5));
		Map<String, Integer> original = new LinkedHashMap<>(stock);
		assertFalse(SettlementConstructionMaterials.tryConsumeCost(stock, Map.of("planks", 7)));
		assertEquals(original, stock);
	}

	private static Map<String, Integer> stock(String key, int amount) {
		Map<String, Integer> stock = new LinkedHashMap<>();
		stock.put(key, amount);
		return stock;
	}
}
