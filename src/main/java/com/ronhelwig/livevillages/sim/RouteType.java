package com.ronhelwig.livevillages.sim;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

public enum RouteType implements StringRepresentable {
	LAND("land"),
	WATER("water");

	public static final Codec<RouteType> CODEC = StringRepresentable.fromEnum(RouteType::values);

	private final String serializedName;

	RouteType(String serializedName) {
		this.serializedName = serializedName;
	}

	@Override
	public String getSerializedName() {
		return serializedName;
	}
}
