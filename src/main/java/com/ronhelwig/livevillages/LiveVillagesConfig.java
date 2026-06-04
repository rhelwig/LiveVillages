package com.ronhelwig.livevillages;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

public final class LiveVillagesConfig {
	private static final String CONFIG_FILE = LiveVillages.MOD_ID + ".json";
	private static final String DAILY_SETTLEMENT_REPORTS = "daily_settlement_reports";
	private static final Config DEFAULT_CONFIG = new Config(false);

	private static volatile Config config = DEFAULT_CONFIG;

	private LiveVillagesConfig() {
	}

	public static void load() {
		config = loadFromDisk();
	}

	public static boolean dailySettlementReportsEnabled() {
		return config.dailySettlementReports();
	}

	private static Config loadFromDisk() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE);

		if (!Files.exists(path)) {
			writeDefaultConfig(path);
			return DEFAULT_CONFIG;
		}

		try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonElement root = JsonParser.parseReader(reader);
			if (root == null || !root.isJsonObject()) {
				LiveVillages.LOGGER.warn("Ignoring malformed Live Villages config at {}", path);
				return DEFAULT_CONFIG;
			}

			JsonObject object = root.getAsJsonObject();
			JsonElement dailyReports = object.get(DAILY_SETTLEMENT_REPORTS);
			if (dailyReports == null || !dailyReports.isJsonPrimitive() || !dailyReports.getAsJsonPrimitive().isBoolean()) {
				return DEFAULT_CONFIG;
			}

			return new Config(dailyReports.getAsBoolean());
		} catch (Exception exception) {
			LiveVillages.LOGGER.warn("Failed to load Live Villages config at {}; using defaults", path, exception);
			return DEFAULT_CONFIG;
		}
	}

	private static void writeDefaultConfig(Path path) {
		try {
			Files.createDirectories(path.getParent());
			try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				writer.write("{\n");
				writer.write("  \"" + DAILY_SETTLEMENT_REPORTS + "\": false\n");
				writer.write("}\n");
			}
		} catch (Exception exception) {
			LiveVillages.LOGGER.warn("Failed to write default Live Villages config at {}", path, exception);
		}
	}

	private record Config(boolean dailySettlementReports) {
	}
}
