package com.ronhelwig.livevillages.sim;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

public enum RouteTier implements StringRepresentable {
	NONE("none"),
	TRAIL("trail"),
	// Legacy tier kept for save compatibility; new road surveying should not produce it.
	GRAVEL("gravel"),
	COBBLE("cobble"),
	SMOOTH_STONE("smooth_stone"),
	BRICK("brick"),
	RIVER("river"),
	CANAL("canal"),
	SEA_LANE("sea_lane");

	public static final Codec<RouteTier> CODEC = StringRepresentable.fromEnum(RouteTier::values);

	private final String serializedName;

	RouteTier(String serializedName) {
		this.serializedName = serializedName;
	}

	@Override
	public String getSerializedName() {
		return serializedName;
	}
}
