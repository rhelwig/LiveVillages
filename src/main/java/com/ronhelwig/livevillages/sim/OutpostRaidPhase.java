package com.ronhelwig.livevillages.sim;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

public enum OutpostRaidPhase implements StringRepresentable {
	MUSTERING("mustering"),
	MARCHING("marching"),
	RAIDING("raiding"),
	COOLDOWN("cooldown");

	public static final Codec<OutpostRaidPhase> CODEC = StringRepresentable.fromEnum(OutpostRaidPhase::values);

	private final String serializedName;

	OutpostRaidPhase(String serializedName) {
		this.serializedName = serializedName;
	}

	@Override
	public String getSerializedName() {
		return serializedName;
	}
}
