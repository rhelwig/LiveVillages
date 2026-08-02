package com.ronhelwig.livevillages.sim;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Stable semantic keys for light sources that have no crafting recipe to put in
 * the Scribe recipe ledger.
 */
public final class SettlementLightingKnowledge {
	private static final List<String> DISCOVERABLE_RESOURCE_KEYS = List.of(
		"glow_berries",
		"shroomlight",
		"froglight"
	);

	private SettlementLightingKnowledge() {
	}

	public static List<String> discoverableResourceKeys() {
		return DISCOVERABLE_RESOURCE_KEYS;
	}

	public static List<String> validResourceKeys(Collection<String> resourceKeys) {
		if (resourceKeys == null || resourceKeys.isEmpty()) {
			return List.of();
		}

		return DISCOVERABLE_RESOURCE_KEYS.stream()
			.filter(resourceKeys::contains)
			.toList();
	}

	/**
	 * Treat possession as the bounded observation step. Settlement stock is
	 * already normalized by {@link SettlementGoods}, so this avoids world scans
	 * and also recognizes resources received from players or ordinary trade.
	 */
	public static List<String> observedResourceKeys(Map<String, Integer> stock) {
		if (stock == null || stock.isEmpty()) {
			return List.of();
		}

		return DISCOVERABLE_RESOURCE_KEYS.stream()
			.filter(resourceKey -> stock.getOrDefault(resourceKey, 0) > 0)
			.toList();
	}
}
