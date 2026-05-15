package com.ronhelwig.livevillages.sim;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

public enum SettlementBuildSiteType implements StringRepresentable {
	BAKERY("bakery"),
	BUTCHER_SHOP("butcher_shop"),
	CARTOGRAPHER_HOUSE("cartographer_house"),
	CARPENTER_WORKSHOP("carpenter_workshop"),
	DOCK("dock"),
	FLETCHER_HUT("fletcher_hut"),
	FORESTER_WORKSHOP("forester_workshop"),
	HOUSING_SHELTER("housing_shelter"),
	LIGHTHOUSE("lighthouse"),
	MASON_WORKSHOP("mason_workshop"),
	MINE_ENTRANCE("mine_entrance"),
	ROADWRIGHT_WORKSHOP("roadwright_workshop"),
	SIMPLE_HOUSING_SHELTER("simple_housing_shelter"),
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
