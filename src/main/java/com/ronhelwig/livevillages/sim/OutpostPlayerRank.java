package com.ronhelwig.livevillages.sim;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

public enum OutpostPlayerRank implements StringRepresentable {
	UNKNOWN("unknown"),
	TOLERATED("tolerated"),
	ASSOCIATE("associate"),
	RAIDER("raider"),
	BANNER_BEARER("banner_bearer"),
	CAPTAIN("captain");

	public static final Codec<OutpostPlayerRank> CODEC = StringRepresentable.fromEnum(OutpostPlayerRank::values);

	private final String serializedName;

	OutpostPlayerRank(String serializedName) {
		this.serializedName = serializedName;
	}

	@Override
	public String getSerializedName() {
		return serializedName;
	}

	public boolean atLeast(OutpostPlayerRank minimum) {
		return ordinal() >= minimum.ordinal();
	}
}
