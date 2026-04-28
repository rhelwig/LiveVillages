package com.ronhelwig.livevillages.network;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.ronhelwig.livevillages.LiveVillages;

public record BuildSitePreviewRequestPayload(
	boolean active,
	Optional<BlockPos> targetPos
) implements CustomPacketPayload {
	public static final Type<BuildSitePreviewRequestPayload> TYPE = new Type<>(LiveVillages.id("build_site_preview_request"));
	public static final Codec<BuildSitePreviewRequestPayload> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.BOOL.optionalFieldOf("active", true).forGetter(BuildSitePreviewRequestPayload::active),
		BlockPos.CODEC.optionalFieldOf("target_pos").forGetter(BuildSitePreviewRequestPayload::targetPos)
	).apply(instance, BuildSitePreviewRequestPayload::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, BuildSitePreviewRequestPayload> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

	@Override
	public Type<BuildSitePreviewRequestPayload> type() {
		return TYPE;
	}
}
