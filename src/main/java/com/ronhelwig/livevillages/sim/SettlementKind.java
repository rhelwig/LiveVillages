package com.ronhelwig.livevillages.sim;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

public enum SettlementKind implements StringRepresentable {
	VILLAGE("village"),
	HARBOR("harbor"),
	OUTPOST("outpost"),
	CUSTOM("custom");

	public static final Codec<SettlementKind> CODEC = StringRepresentable.fromEnum(SettlementKind::values);

	private final String serializedName;

	SettlementKind(String serializedName) {
		this.serializedName = serializedName;
	}

	@Override
	public String getSerializedName() {
		return serializedName;
	}
}
