package com.ronhelwig.livevillages.sim;

import com.mojang.serialization.Codec;

import java.util.Map;

import net.minecraft.util.StringRepresentable;

public enum SettlementProjectType implements StringRepresentable {
	TRADING_POST("trading_post", Map.of("planks", 6, "stick", 2)),
	HOUSING("housing", Map.of("planks", 22, "logs", 4, "cobblestone", 12)),
	CARPENTER_WORKSHOP("carpenter_workshop", Map.of("planks", 32, "logs", 10, "cobblestone", 6)),
	DOCK("dock", Map.of("logs", 4, "planks", 24)),
	LIGHTHOUSE("lighthouse", Map.of("cobblestone", 40)),
	DEFENSE("defense", Map.of("logs", 6, "cobblestone", 16)),
	ROAD("road", Map.of()),
	COMPOSTER("composter", Map.of("planks", 7)),
	STORAGE("storage", Map.of("planks", 8));

	public static final Codec<SettlementProjectType> CODEC = StringRepresentable.fromEnum(SettlementProjectType::values);

	private final String serializedName;
	private final Map<String, Integer> stockCost;

	SettlementProjectType(String serializedName, Map<String, Integer> stockCost) {
		this.serializedName = serializedName;
		this.stockCost = Map.copyOf(stockCost);
	}

	@Override
	public String getSerializedName() {
		return serializedName;
	}

	public Map<String, Integer> stockCost() {
		return stockCost;
	}
}
