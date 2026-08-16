package com.ronhelwig.livevillages.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.ronhelwig.livevillages.LiveVillages;

/**
 * Applies the value selected by Fabric's enum cycling control in the
 * in-world gamerule screen. Minecraft's vanilla screen-to-server path does
 * not reliably retain custom enum values.
 */
public record VillagerTextureScaleUpdatePayload(int scale) implements CustomPacketPayload {
	public static final Type<VillagerTextureScaleUpdatePayload> TYPE = new Type<>(LiveVillages.id("villager_texture_scale_update"));
	public static final StreamCodec<RegistryFriendlyByteBuf, VillagerTextureScaleUpdatePayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT,
		VillagerTextureScaleUpdatePayload::scale,
		VillagerTextureScaleUpdatePayload::new
	);

	@Override
	public Type<VillagerTextureScaleUpdatePayload> type() {
		return TYPE;
	}
}
