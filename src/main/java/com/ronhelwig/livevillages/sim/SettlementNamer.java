package com.ronhelwig.livevillages.sim;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

public final class SettlementNamer {
	private static final WordSet TEMPERATE_WORDS = new WordSet(
		new String[] {"Ash", "Bracken", "Cedar", "Dawn", "Elm", "Fern", "Glen", "Hazel", "Lark", "Moss", "River", "Stone", "Thorn", "Vale", "Willow"},
		new String[] {"brook", "cross", "dale", "field", "ford", "gate", "grove", "haven", "hollow", "market", "mere", "rest", "ridge", "stead", "wick", "worth"},
		new String[] {"Black", "Bleak", "Dread", "Gallow", "Grim", "Iron", "Ruin", "Thorn", "Woe", "Wraith"},
		new String[] {"camp", "den", "gate", "hold", "keep", "march", "scar", "spire", "watch", "yard"}
	);
	private static final WordSet DESERT_WORDS = new WordSet(
		new String[] {"Amber", "Dune", "Dust", "Mirage", "Ochre", "Saffron", "Sand", "Scorch", "Sirocco", "Sun"},
		new String[] {"barrow", "crest", "gate", "hollow", "market", "rest", "ridge", "spire", "stead", "wash"},
		new String[] {"Ashen", "Cinder", "Dread", "Ember", "Grim", "Ruin", "Scorch", "Skull", "Sunder", "Vile"},
		new String[] {"bastion", "camp", "gate", "pit", "scar", "spire", "watch", "wound", "yard", "zhar"}
	);
	private static final WordSet BADLANDS_WORDS = new WordSet(
		new String[] {"Copper", "Dust", "Mesa", "Ochre", "Red", "Rust", "Shard", "Sun", "Terrace", "Umber"},
		new String[] {"arch", "bend", "bluff", "crest", "gate", "hollow", "ridge", "scar", "shelf", "span"},
		new String[] {"Blood", "Broken", "Cinder", "Razor", "Ruin", "Rust", "Skull", "Sunder", "Vile", "Wrath"},
		new String[] {"bastion", "cut", "gate", "hold", "scar", "shelf", "spire", "teeth", "watch", "yard"}
	);
	private static final WordSet SAVANNA_WORDS = new WordSet(
		new String[] {"Acacia", "Amber", "Antler", "Gold", "Redgrass", "Sun", "Tallgrass", "Thorn", "Warmwind", "Wild"},
		new String[] {"cross", "field", "gate", "haven", "rest", "ridge", "stead", "step", "watch", "way"},
		new String[] {"Ash", "Bleak", "Brim", "Grim", "Maw", "Raze", "Skull", "Thorn", "Viper", "War"},
		new String[] {"camp", "gate", "hold", "march", "nest", "scar", "watch", "way", "yard", "zhal"}
	);
	private static final WordSet JUNGLE_WORDS = new WordSet(
		new String[] {"Bloom", "Canopy", "Emerald", "Fern", "Orchid", "Parrot", "Rain", "Root", "Vine", "Wild"},
		new String[] {"bloom", "grove", "hollow", "market", "mere", "reach", "rest", "shade", "tangle", "ward"},
		new String[] {"Fang", "Grim", "Night", "Razor", "Rot", "Skull", "Snare", "Thorn", "Venom", "Vile"},
		new String[] {"camp", "den", "fang", "maze", "nest", "pit", "snare", "spire", "watch", "wild"}
	);
	private static final WordSet SWAMP_WORDS = new WordSet(
		new String[] {"Bog", "Fen", "Lily", "Mangrove", "Mist", "Mire", "Moss", "Reed", "Still", "Willow"},
		new String[] {"bank", "cross", "fen", "hollow", "mere", "moor", "rest", "watch", "wick", "willow"},
		new String[] {"Black", "Blight", "Crook", "Dread", "Gloom", "Mire", "Rot", "Sable", "Witch", "Woe"},
		new String[] {"bog", "camp", "fen", "hold", "moor", "pit", "snare", "watch", "wick", "yard"}
	);
	private static final WordSet SNOW_WORDS = new WordSet(
		new String[] {"Frost", "Glacier", "Ice", "Pale", "Pine", "Silver", "Snow", "Winter", "Wolf", "Yarrow"},
		new String[] {"crest", "fell", "fjord", "gate", "haven", "hold", "reach", "rest", "ridge", "stead"},
		new String[] {"Bleak", "Cold", "Dire", "Fell", "Grim", "Ice", "Pale", "Rime", "Wolf", "Wraith"},
		new String[] {"camp", "fell", "hold", "keep", "ridge", "scar", "spire", "teeth", "watch", "waste"}
	);
	private static final WordSet TAIGA_WORDS = new WordSet(
		new String[] {"Cedar", "Fir", "Juniper", "Marten", "Needle", "Pine", "Spruce", "Stone", "Timber", "Winter"},
		new String[] {"brook", "cross", "gate", "grove", "hollow", "hold", "ridge", "stead", "watch", "wood"},
		new String[] {"Ash", "Dire", "Grim", "Hollow", "Iron", "Ruin", "Skull", "Thorn", "Warg", "Woe"},
		new String[] {"camp", "den", "gate", "hold", "lair", "scar", "spire", "watch", "wood", "yard"}
	);
	private static final WordSet MOUNTAIN_WORDS = new WordSet(
		new String[] {"Crag", "Eagle", "Granite", "High", "Iron", "Peak", "Rock", "Sky", "Stone", "Summit"},
		new String[] {"cairn", "crest", "gate", "haven", "hold", "pass", "reach", "ridge", "spire", "watch"},
		new String[] {"Black", "Dire", "Dread", "Fell", "Gore", "Grim", "Iron", "Razor", "Ruin", "Storm"},
		new String[] {"bastion", "cairn", "gate", "hold", "keep", "pass", "scar", "spire", "watch", "wound"}
	);
	private static final WordSet COASTAL_WORDS = new WordSet(
		new String[] {"Brine", "Coral", "Drift", "Foam", "Gull", "Reef", "Salt", "Shell", "Tide", "Wave"},
		new String[] {"bay", "cove", "dock", "haven", "point", "reach", "rest", "shore", "watch", "wharf"},
		new String[] {"Black", "Brine", "Dread", "Gale", "Grim", "Reaver", "Salt", "Skull", "Storm", "Wreck"},
		new String[] {"cove", "dock", "gate", "hold", "hook", "point", "scar", "watch", "wreck", "yard"}
	);
	private static final WordSet NETHER_WORDS = new WordSet(
		new String[] {"Ash", "Basalt", "Blaze", "Cinder", "Ember", "Glow", "Lava", "Nether", "Scoria", "Smolder"},
		new String[] {"bastion", "cross", "fall", "gate", "hold", "reach", "rift", "scar", "spire", "watch"},
		new String[] {"Blight", "Brim", "Cinder", "Dread", "Gore", "Hell", "Ruin", "Scorch", "Vile", "Wrath"},
		new String[] {"bastion", "gate", "hold", "pit", "rift", "scar", "spire", "teeth", "watch", "wound"}
	);
	private static final WordSet END_WORDS = new WordSet(
		new String[] {"Aster", "Drift", "Echo", "Ender", "Ivory", "Lone", "Pale", "Shimmer", "Silent", "Void"},
		new String[] {"cross", "gate", "hollow", "landing", "point", "reach", "rest", "spire", "watch", "way"},
		new String[] {"Abyss", "Bleak", "Dread", "Empty", "Night", "Null", "Pale", "Ruin", "Void", "Wraith"},
		new String[] {"gate", "hold", "pit", "scar", "spire", "teeth", "void", "watch", "way", "wound"}
	);

	private SettlementNamer() {
	}

	public static String generateUniqueName(SettlementKind kind, ServerLevel level, BlockPos center, Collection<SettlementState> existingSettlements) {
		Set<String> existingNames = new HashSet<>();
		WordSet words = wordSetFor(level, level.getBiome(center));

		for (SettlementState settlement : existingSettlements) {
			if (settlement.dimension().equals(level.dimension())) {
				existingNames.add(settlement.name());
			}
		}

		String baseName = switch (kind) {
			case OUTPOST -> generatedCoreName(level, center, words, true) + " Outpost";
			case HARBOR -> generatedCoreName(level, center, words, false) + " Harbor";
			case CUSTOM, VILLAGE -> generatedCoreName(level, center, words, false);
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

	private static String generatedCoreName(ServerLevel level, BlockPos center, WordSet words, boolean hostile) {
		int seed = 31 * level.dimension().identifier().hashCode() + 17 * center.getX() + 37 * center.getZ();
		String[] prefixes = hostile ? words.hostilePrefixes() : words.civilianPrefixes();
		String[] suffixes = hostile ? words.hostileSuffixes() : words.civilianSuffixes();
		int prefixIndex = Math.floorMod(seed, prefixes.length);
		int suffixIndex = Math.floorMod(seed / 7, suffixes.length);
		return prefixes[prefixIndex] + suffixes[suffixIndex];
	}

	private static WordSet wordSetFor(ServerLevel level, Holder<Biome> biome) {
		if (level.dimension().equals(Level.NETHER)) {
			return NETHER_WORDS;
		}

		if (level.dimension().equals(Level.END)) {
			return END_WORDS;
		}

		String biomePath = biomePath(biome);

		if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER) || biomePath.contains("beach")) {
			return COASTAL_WORDS;
		}

		if (biomePath.contains("badlands")) {
			return BADLANDS_WORDS;
		}

		if (biomePath.contains("desert")) {
			return DESERT_WORDS;
		}

		if (biomePath.contains("savanna")) {
			return SAVANNA_WORDS;
		}

		if (biomePath.contains("jungle") || biomePath.contains("bamboo")) {
			return JUNGLE_WORDS;
		}

		if (biomePath.contains("swamp") || biomePath.contains("mangrove")) {
			return SWAMP_WORDS;
		}

		if (biomePath.contains("snow") || biomePath.contains("ice") || biomePath.contains("frozen")) {
			return SNOW_WORDS;
		}

		if (biomePath.contains("taiga") || biomePath.contains("old_growth_spruce") || biomePath.contains("old_growth_pine")) {
			return TAIGA_WORDS;
		}

		if (biomePath.contains("peak") || biomePath.contains("mountain") || biomePath.contains("windswept") || biomePath.contains("stony")) {
			return MOUNTAIN_WORDS;
		}

		return TEMPERATE_WORDS;
	}

	private static String biomePath(Holder<Biome> biome) {
		return biome.unwrapKey()
			.map(key -> key.identifier().getPath())
			.orElse("")
			.toLowerCase(Locale.ROOT);
	}

	private record WordSet(
		String[] civilianPrefixes,
		String[] civilianSuffixes,
		String[] hostilePrefixes,
		String[] hostileSuffixes
	) {
	}
}
