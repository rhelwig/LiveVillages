package com.ronhelwig.livevillages.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.ronhelwig.livevillages.LiveVillages;

public record BuildSitePreviewStatePayload(
	BuildSitePreviewSnapshot snapshot
) implements CustomPacketPayload {
	public static final Type<BuildSitePreviewStatePayload> TYPE = new Type<>(LiveVillages.id("build_site_preview_state"));
	public static final StreamCodec<RegistryFriendlyByteBuf, BuildSitePreviewStatePayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.fromCodecWithRegistries(BuildSitePreviewSnapshot.CODEC),
		BuildSitePreviewStatePayload::snapshot,
		BuildSitePreviewStatePayload::new
	);

	@Override
	public Type<BuildSitePreviewStatePayload> type() {
		return TYPE;
	}
}
