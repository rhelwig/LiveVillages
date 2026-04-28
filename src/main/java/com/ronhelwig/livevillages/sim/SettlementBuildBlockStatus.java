package com.ronhelwig.livevillages.sim;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

public enum SettlementBuildBlockStatus implements StringRepresentable {
	PENDING("pending"),
	PLACED("placed"),
	MISSING_MATERIAL("missing_material"),
	BLOCKED("blocked"),
	PLAYER_PLACED("player_placed");

	public static final Codec<SettlementBuildBlockStatus> CODEC = StringRepresentable.fromEnum(SettlementBuildBlockStatus::values);

	private final String serializedName;

	SettlementBuildBlockStatus(String serializedName) {
		this.serializedName = serializedName;
	}

	@Override
	public String getSerializedName() {
		return serializedName;
	}
}
