package com.ronhelwig.livevillages.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.ronhelwig.livevillages.LiveVillages;

public record PortmasterMapStatePayload(
	PortmasterMapSnapshot snapshot
) implements CustomPacketPayload {
	public static final Type<PortmasterMapStatePayload> TYPE = new Type<>(LiveVillages.id("portmaster_map_state"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PortmasterMapStatePayload> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(
		PortmasterMapSnapshot.CODEC.xmap(PortmasterMapStatePayload::new, PortmasterMapStatePayload::snapshot)
	);

	@Override
	public Type<PortmasterMapStatePayload> type() {
		return TYPE;
	}
}
