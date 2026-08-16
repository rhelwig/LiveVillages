package com.ronhelwig.livevillages.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.ronhelwig.livevillages.LiveVillages;

public record VillagerTextureScalePayload(int scale) implements CustomPacketPayload {
	public static final Type<VillagerTextureScalePayload> TYPE = new Type<>(LiveVillages.id("villager_texture_scale"));
	public static final StreamCodec<RegistryFriendlyByteBuf, VillagerTextureScalePayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT,
		VillagerTextureScalePayload::scale,
		VillagerTextureScalePayload::new
	);

	@Override
	public Type<VillagerTextureScalePayload> type() {
		return TYPE;
	}
}
