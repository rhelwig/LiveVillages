package com.ronhelwig.livevillages.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.ronhelwig.livevillages.LiveVillages;

public record SettlementOverlayRequestPayload() implements CustomPacketPayload {
	public static final SettlementOverlayRequestPayload INSTANCE = new SettlementOverlayRequestPayload();
	public static final Type<SettlementOverlayRequestPayload> TYPE = new Type<>(LiveVillages.id("settlement_overlay_request"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SettlementOverlayRequestPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<SettlementOverlayRequestPayload> type() {
		return TYPE;
	}
}
