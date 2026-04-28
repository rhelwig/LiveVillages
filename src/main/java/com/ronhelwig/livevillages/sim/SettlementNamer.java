package com.ronhelwig.livevillages.sim;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class SettlementNamer {
	private static final String[] PREFIXES = {
		"Ash", "Birch", "Bracken", "Cedar", "Dawn", "Elm", "Fern", "Glen",
		"Hazel", "High", "Iron", "Juniper", "Lark", "Moss", "Oak", "Pine",
		"Quiet", "River", "Stone", "Thorn", "Umber", "Vale", "West", "Willow"
	};
	private static final String[] SUFFIXES = {
		"brook", "cross", "dale", "field", "ford", "gate", "grove", "haven",
		"hold", "hollow", "market", "mere", "point", "rest", "ridge", "stead",
		"watch", "wick", "wood", "worth"
	};

	private SettlementNamer() {
	}

	public static String generateUniqueName(SettlementKind kind, ResourceKey<Level> dimension, BlockPos center, Collection<SettlementState> existingSettlements) {
		Set<String> existingNames = new HashSet<>();

		for (SettlementState settlement : existingSettlements) {
			if (settlement.dimension().equals(dimension)) {
				existingNames.add(settlement.name());
			}
		}

		String baseName = switch (kind) {
			case OUTPOST -> "Outpost " + generatedCoreName(dimension, center);
			case HARBOR -> generatedCoreName(dimension, center) + " Harbor";
			case CUSTOM, VILLAGE -> generatedCoreName(dimension, center);
		};

		if (!existingNames.contains(baseName)) {
			return baseName;
		}

		for (int i = 2; i <= 99; i++) {
			String candidate = baseName + " " + i;

			if (!existingNames.contains(candidate)) {
				return candidate;
			}
		}

		return baseName + " " + Math.abs(center.getX() % 1000) + "-" + Math.abs(center.getZ() % 1000);
	}

	private static String generatedCoreName(ResourceKey<Level> dimension, BlockPos center) {
		int seed = 31 * dimension.identifier().hashCode() + 17 * center.getX() + 37 * center.getZ();
		int prefixIndex = Math.floorMod(seed, PREFIXES.length);
		int suffixIndex = Math.floorMod(seed / 7, SUFFIXES.length);
		return PREFIXES[prefixIndex] + SUFFIXES[suffixIndex];
	}
}
