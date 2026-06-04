package com.ronhelwig.livevillages.sim;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

public enum SettlementBuildSiteType implements StringRepresentable {
	BAKERY("bakery"),
	BEEKEEPER_APIARY("beekeeper_apiary"),
	BUTCHER_SHOP("butcher_shop"),
	CARTOGRAPHER_HOUSE("cartographer_house"),
	CARPENTER_WORKSHOP("carpenter_workshop"),
	CLERIC_SHRINE("cleric_shrine"),
	DOCK("dock"),
	FLETCHER_HUT("fletcher_hut"),
	FORESTER_WORKSHOP("forester_workshop"),
	GARDENER_SHED("gardener_shed"),
	GUARD_POST("guard_post"),
	HOUSING_SHELTER("housing_shelter"),
	LEATHERWORKER_WORKSHOP("leatherworker_workshop"),
	LIGHTHOUSE("lighthouse"),
	LIBRARY("library"),
	MASON_WORKSHOP("mason_workshop"),
	MINE_ENTRANCE("mine_entrance"),
	PALISADE_GATEHOUSE("palisade_gatehouse"),
	COPPER_PALISADE_GATEHOUSE("copper_palisade_gatehouse"),
	PALISADE_WALL("palisade_wall"),
	ROADWRIGHT_WORKSHOP("roadwright_workshop"),
	SCRIBE_OFFICE("scribe_office"),
	SHEPHERD_HUT("shepherd_hut"),
	SIMPLE_HOUSING_SHELTER("simple_housing_shelter"),
	SMITHY("smithy"),
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
