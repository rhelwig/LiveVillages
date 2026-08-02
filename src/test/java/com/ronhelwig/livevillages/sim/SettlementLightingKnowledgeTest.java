package com.ronhelwig.livevillages.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SettlementLightingKnowledgeTest {
	@Test
	void discoveryIsBoundedToSupportedNonRecipeResources() {
		assertEquals(
			List.of("glow_berries", "shroomlight", "froglight"),
			SettlementLightingKnowledge.discoverableResourceKeys()
		);
		assertEquals(
			List.of("glow_berries", "froglight"),
			SettlementLightingKnowledge.observedResourceKeys(Map.of(
				"glow_berries", 2,
				"froglight", 1,
				"redstone_lamp", 4
			))
		);
	}

	@Test
	void absentOrEmptyStockDiscoversNothing() {
		assertEquals(List.of(), SettlementLightingKnowledge.observedResourceKeys(null));
		assertEquals(List.of(), SettlementLightingKnowledge.observedResourceKeys(Map.of()));
		assertEquals(List.of(), SettlementLightingKnowledge.observedResourceKeys(Map.of("shroomlight", 0)));
	}
}
