package com.ronhelwig.livevillages.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.ronhelwig.livevillages.LiveVillages;

public record SurveyorMapStatePayload(
	SurveyorMapSnapshot snapshot
) implements CustomPacketPayload {
	public static final Type<SurveyorMapStatePayload> TYPE = new Type<>(LiveVillages.id("surveyor_map_state"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SurveyorMapStatePayload> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(
		SurveyorMapSnapshot.CODEC.xmap(SurveyorMapStatePayload::new, SurveyorMapStatePayload::snapshot)
	);

	@Override
	public Type<SurveyorMapStatePayload> type() {
		return TYPE;
	}
}
