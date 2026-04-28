package com.ronhelwig.livevillages.sim;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

public enum SettlementBuildSiteType implements StringRepresentable {
	CARTOGRAPHER_HOUSE("cartographer_house"),
	CARPENTER_WORKSHOP("carpenter_workshop"),
	DOCK("dock"),
	FLETCHER_HUT("fletcher_hut"),
	FORESTER_WORKSHOP("forester_workshop"),
	ROADWRIGHT_WORKSHOP("roadwright_workshop"),
	TRADING_POST("trading_post");

	public static final Codec<SettlementBuildSiteType> CODEC = StringRepresentable.fromEnum(SettlementBuildSiteType::values);

	private final String serializedName;

	SettlementBuildSiteType(String serializedName) {
		this.serializedName = serializedName;
	}

	@Override
	public String getSerializedName() {
		return serializedName;
	}
}
