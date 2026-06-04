package com.ronhelwig.livevillages.client.screen;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.ronhelwig.livevillages.LiveVillages;

record SettlementScreenTheme(
	int overlay,
	int header,
	int body,
	int border,
	int divider,
	int titleText,
	int accentText,
	int bodyText,
	int secondaryText,
	int mutedText,
	int disabledText,
	int panelFill,
	int panelStrongFill,
	int panelOutline,
	int selectionFill,
	int selectionOutline,
	int successText,
	int failureText
) {
	private static final String RESOURCE_PATH = "assets/live-villages/screen_themes.json";
	private static final SettlementScreenTheme[] DEFAULT_BY_TIER = {
		new SettlementScreenTheme(
			0xEE1F1A17, 0xFF6B5A36, 0xFF2C241A, 0xFFB69155, 0xFF4A3A26,
			0xFFF8E6BE, 0xFFF2CF84, 0xFFE8DDC8, 0xFFDCC8A4, 0xFF9F8E72, 0xFF806F5A,
			0x11FFF5E6, 0x22FFF5E6, 0x447A633F, 0x553E6A3B, 0xFF9BC87D,
			0xFFD5E6B7, 0xFFFF9A8A
		),
		new SettlementScreenTheme(
			0xEE151F1B, 0xFF3D6E55, 0xFF172820, 0xFF9AD0A9, 0xFF315042,
			0xFFF0FFE8, 0xFFCFEF9D, 0xFFE2F2DC, 0xFFBCD9BE, 0xFF8FA98F, 0xFF657B68,
			0x142FEA9B, 0x283BCF97, 0x555C9A72, 0x55316E4E, 0xFFBCE47B,
			0xFFD8F7C5, 0xFFFF9C8E
		),
		new SettlementScreenTheme(
			0xEE111D25, 0xFF2D6681, 0xFF132733, 0xFF8FD0E8, 0xFF274D61,
			0xFFEAFBFF, 0xFFFFD37A, 0xFFE2F3F7, 0xFFB8D5DE, 0xFF86A7B2, 0xFF607B84,
			0x1436C8FF, 0x2836B5E7, 0x5566BFD9, 0x552E718F, 0xFFFFD36F,
			0xFFD3F7D4, 0xFFFFA08C
		),
		new SettlementScreenTheme(
			0xEE101219, 0xFF3C4B86, 0xFF171A26, 0xFFD7C675, 0xFF34385D,
			0xFFFFF4CC, 0xFFFFD866, 0xFFECEAF6, 0xFFCFC9E2, 0xFFA49AB7, 0xFF777088,
			0x18FFD866, 0x253C72D0, 0x66D7C675, 0x553C72D0, 0xFFFFE082,
			0xFFD7F7D2, 0xFFFF9C9C
		)
	};
	private static volatile SettlementScreenTheme[] cachedThemes;

	static SettlementScreenTheme forTier(int tier) {
		SettlementScreenTheme[] themes = loadThemes();
		int index = Math.max(0, Math.min(themes.length - 1, tier - 1));
		return themes[index];
	}

	private static SettlementScreenTheme[] loadThemes() {
		SettlementScreenTheme[] themes = cachedThemes;
		if (themes != null) {
			return themes;
		}

		cachedThemes = loadBundledThemes();
		return cachedThemes;
	}

	private static SettlementScreenTheme[] loadBundledThemes() {
		SettlementScreenTheme[] defaults = DEFAULT_BY_TIER.clone();
		try (InputStream stream = SettlementScreenTheme.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
			if (stream == null) {
				LiveVillages.LOGGER.warn("Missing settlement screen theme resource: {}", RESOURCE_PATH);
				return defaults;
			}

			try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
				JsonElement json = JsonParser.parseReader(reader);
				if (!json.isJsonObject()) {
					return defaults;
				}

				JsonArray tiers = json.getAsJsonObject().getAsJsonArray("tiers");
				if (tiers == null || tiers.isEmpty()) {
					return defaults;
				}

				SettlementScreenTheme[] loaded = defaults.clone();
				int count = Math.min(loaded.length, tiers.size());
				for (int index = 0; index < count; index++) {
					JsonElement tier = tiers.get(index);
					if (tier != null && tier.isJsonObject()) {
						loaded[index] = fromJson(tier.getAsJsonObject(), loaded[index]);
					}
				}

				return loaded;
			}
		} catch (Exception exception) {
			LiveVillages.LOGGER.error("Failed to load settlement screen themes from {}", RESOURCE_PATH, exception);
			return defaults;
		}
	}

	private static SettlementScreenTheme fromJson(JsonObject json, SettlementScreenTheme fallback) {
		return new SettlementScreenTheme(
			color(json, "overlay", fallback.overlay()),
			color(json, "header", fallback.header()),
			color(json, "body", fallback.body()),
			color(json, "border", fallback.border()),
			color(json, "divider", fallback.divider()),
			color(json, "title_text", fallback.titleText()),
			color(json, "accent_text", fallback.accentText()),
			color(json, "body_text", fallback.bodyText()),
			color(json, "secondary_text", fallback.secondaryText()),
			color(json, "muted_text", fallback.mutedText()),
			color(json, "disabled_text", fallback.disabledText()),
			color(json, "panel_fill", fallback.panelFill()),
			color(json, "panel_strong_fill", fallback.panelStrongFill()),
			color(json, "panel_outline", fallback.panelOutline()),
			color(json, "selection_fill", fallback.selectionFill()),
			color(json, "selection_outline", fallback.selectionOutline()),
			color(json, "success_text", fallback.successText()),
			color(json, "failure_text", fallback.failureText())
		);
	}

	private static int color(JsonObject json, String field, int fallback) {
		JsonElement value = json.get(field);
		if (value == null || !value.isJsonPrimitive()) {
			return fallback;
		}

		String raw = value.getAsString().trim();
		if (raw.isBlank()) {
			return fallback;
		}

		try {
			String normalized = raw.startsWith("#") ? "0x" + raw.substring(1) : raw;
			return (int) Long.decode(normalized).longValue();
		} catch (NumberFormatException exception) {
			LiveVillages.LOGGER.warn("Ignoring invalid settlement screen color '{}' for {}", raw, field);
			return fallback;
		}
	}
}
