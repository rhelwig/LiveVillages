package com.ronhelwig.livevillages.sim;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.ronhelwig.livevillages.LiveVillages;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

final class StructurePaletteTable {
	private static final String RESOURCE_PATH = "data/live-villages/structure_palette_rules.json";
	private static final PaletteConfig DEFAULT_CONFIG = new PaletteConfig("oak", "cobblestone", List.of());

	private static volatile PaletteConfig cachedConfig;

	private StructurePaletteTable() {
	}

	static StructureMaterialPalette paletteFor(Holder<Biome> biome) {
		PaletteConfig config = loadConfig();
		String woodFamily = config.defaultWoodFamily();
		String stoneMaterial = config.defaultStoneMaterial();

		for (PaletteRule rule : config.rules()) {
			if (!rule.matches(biome)) {
				continue;
			}

			if (!rule.woodFamily().isBlank()) {
				woodFamily = rule.woodFamily();
			}

			if (!rule.stoneMaterial().isBlank()) {
				stoneMaterial = rule.stoneMaterial();
			}
		}

		return new StructureMaterialPalette(woodFamily, stoneMaterial);
	}

	private static PaletteConfig loadConfig() {
		PaletteConfig config = cachedConfig;
		if (config != null) {
			return config;
		}

		cachedConfig = loadBundledConfig();
		return cachedConfig;
	}

	private static PaletteConfig loadBundledConfig() {
		try (InputStream stream = StructurePaletteTable.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
			if (stream == null) {
				LiveVillages.LOGGER.warn("Missing structure palette rules resource: {}", RESOURCE_PATH);
				return DEFAULT_CONFIG;
			}

			try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
				JsonElement json = JsonParser.parseReader(reader);
				return PaletteConfig.CODEC.parse(JsonOps.INSTANCE, json)
					.resultOrPartial(error -> LiveVillages.LOGGER.error("Failed to parse structure palette rules: {}", error))
					.orElse(DEFAULT_CONFIG);
			}
		} catch (Exception exception) {
			LiveVillages.LOGGER.error("Failed to load structure palette rules from {}", RESOURCE_PATH, exception);
			return DEFAULT_CONFIG;
		}
	}

	private static Identifier parseIdentifier(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}

		int separator = raw.indexOf(':');

		try {
			if (separator < 0) {
				return Identifier.fromNamespaceAndPath("minecraft", raw);
			}

			return Identifier.fromNamespaceAndPath(raw.substring(0, separator), raw.substring(separator + 1));
		} catch (IllegalArgumentException exception) {
			LiveVillages.LOGGER.warn("Ignoring invalid structure palette identifier '{}'", raw);
			return null;
		}
	}

	private record PaletteRule(List<String> biomes, List<String> biomeTags, String woodFamily, String stoneMaterial) {
		private static final Codec<PaletteRule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.listOf().optionalFieldOf("biomes", List.of()).forGetter(PaletteRule::biomes),
			Codec.STRING.listOf().optionalFieldOf("biome_tags", List.of()).forGetter(PaletteRule::biomeTags),
			Codec.STRING.optionalFieldOf("wood_family", "").forGetter(PaletteRule::woodFamily),
			Codec.STRING.optionalFieldOf("stone_material", "").forGetter(PaletteRule::stoneMaterial)
		).apply(instance, PaletteRule::new));

		private boolean matches(Holder<Biome> biome) {
			for (String biomeId : biomes) {
				Identifier identifier = parseIdentifier(biomeId);
				if (identifier != null && biome.is(ResourceKey.create(Registries.BIOME, identifier))) {
					return true;
				}
			}

			for (String biomeTagId : biomeTags) {
				Identifier identifier = parseIdentifier(biomeTagId);
				if (identifier != null && biome.is(TagKey.create(Registries.BIOME, identifier))) {
					return true;
				}
			}

			return false;
		}
	}

	private record PaletteConfig(String defaultWoodFamily, String defaultStoneMaterial, List<PaletteRule> rules) {
		private static final Codec<PaletteConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.optionalFieldOf("default_wood_family", "oak").forGetter(PaletteConfig::defaultWoodFamily),
			Codec.STRING.optionalFieldOf("default_stone_material", "cobblestone").forGetter(PaletteConfig::defaultStoneMaterial),
			PaletteRule.CODEC.listOf().optionalFieldOf("rules", List.of()).forGetter(PaletteConfig::rules)
		).apply(instance, PaletteConfig::new));
	}
}
