package com.ronhelwig.livevillages.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.ronhelwig.livevillages.LiveVillages;

public record SettlementOverlayStatePayload(
	SettlementOverlaySnapshot snapshot
) implements CustomPacketPayload {
	public static final Type<SettlementOverlayStatePayload> TYPE = new Type<>(LiveVillages.id("settlement_overlay_state"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SettlementOverlayStatePayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.fromCodecWithRegistries(SettlementOverlaySnapshot.CODEC),
		SettlementOverlayStatePayload::snapshot,
		SettlementOverlayStatePayload::new
	);

	@Override
	public Type<SettlementOverlayStatePayload> type() {
		return TYPE;
	}
}
