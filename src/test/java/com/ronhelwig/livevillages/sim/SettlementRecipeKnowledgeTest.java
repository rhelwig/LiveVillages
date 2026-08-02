package com.ronhelwig.livevillages.sim;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import com.ronhelwig.livevillages.MinecraftBootstrapTestSupport;
import org.junit.jupiter.api.Test;

class SettlementRecipeKnowledgeTest extends MinecraftBootstrapTestSupport {
	@Test
	void lightingRecipesUnlockAtTheirConfiguredTiers() {
		List<String> tierOne = SettlementRecipeKnowledge.recipeIdsForTier(1);
		List<String> tierTwo = SettlementRecipeKnowledge.recipeIdsForTier(2);
		List<String> tierThree = SettlementRecipeKnowledge.recipeIdsForTier(3);
		assertTrue(tierOne.contains("minecraft:torch"));
		assertTrue(tierOne.contains("minecraft:campfire"));
		assertFalse(tierOne.contains("minecraft:candle"));
		assertFalse(tierOne.contains("minecraft:jack_o_lantern"));
		assertTrue(tierTwo.contains("minecraft:candle"));
		assertTrue(tierTwo.contains("minecraft:jack_o_lantern"));
		assertFalse(tierTwo.contains("minecraft:glowstone"));
		assertFalse(tierTwo.contains("minecraft:sea_lantern"));
		assertTrue(tierThree.contains("minecraft:glowstone"));
		assertTrue(tierThree.contains("minecraft:sea_lantern"));
	}

	@Test
	void recipeTierIsNormalizedAndDoesNotDuplicateEntries() {
		assertEquals(SettlementRecipeKnowledge.recipeIdsForTier(1), SettlementRecipeKnowledge.recipeIdsForTier(-10));
		List<String> tierFour = SettlementRecipeKnowledge.recipeIdsForTier(4);
		assertEquals(tierFour.size(), tierFour.stream().distinct().count());
	}

	@Test
	void observingALanternTeachesOnlyTheLanternRecipe() {
		assertEquals(List.of("minecraft:lantern"), SettlementRecipeKnowledge.observedLanternRecipeIds());
	}

	@Test
	void lightingRecipePricesReflectComplexity() {
		assertEquals(new SettlementRecipeKnowledge.ScribeRecipePrice("minecraft:paper", 2),
			SettlementRecipeKnowledge.scribeRecipePrice("minecraft:candle", "minecraft:candle"));
		assertEquals(new SettlementRecipeKnowledge.ScribeRecipePrice("minecraft:book", 1),
			SettlementRecipeKnowledge.scribeRecipePrice("minecraft:lantern", "minecraft:lantern"));
		assertEquals(new SettlementRecipeKnowledge.ScribeRecipePrice("minecraft:book", 3),
			SettlementRecipeKnowledge.scribeRecipePrice("minecraft:sea_lantern", "minecraft:sea_lantern"));
	}
}
