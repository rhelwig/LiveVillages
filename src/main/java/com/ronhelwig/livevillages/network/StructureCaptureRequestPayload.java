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

public record StructureCaptureRequestPayload(
	Optional<BlockPos> targetPos
) implements CustomPacketPayload {
	public static final Type<StructureCaptureRequestPayload> TYPE = new Type<>(LiveVillages.id("structure_capture_request"));
	public static final Codec<StructureCaptureRequestPayload> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		BlockPos.CODEC.optionalFieldOf("target_pos").forGetter(StructureCaptureRequestPayload::targetPos)
	).apply(instance, StructureCaptureRequestPayload::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, StructureCaptureRequestPayload> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

	@Override
	public Type<StructureCaptureRequestPayload> type() {
		return TYPE;
	}
}
