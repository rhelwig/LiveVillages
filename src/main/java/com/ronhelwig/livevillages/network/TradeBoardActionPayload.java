package com.ronhelwig.livevillages.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.ronhelwig.livevillages.LiveVillages;

public record TradeBoardActionPayload(
	BlockPos boardPos,
	String actionType,
	int rowIndex,
	int optionIndex,
	String rowKey,
	String primaryGoodsKey,
	String secondaryGoodsKey,
	int amount
) implements CustomPacketPayload {
	public static final String ACTION_PLAYER_TRADE = "player_trade";
	public static final String ACTION_VILLAGE_TRADE = "village_trade";
	public static final String ACTION_DONATION = "donation";
	public static final String ACTION_DONATE_CONTENTS = "donate_contents";

	public static final Type<TradeBoardActionPayload> TYPE = new Type<>(LiveVillages.id("trade_board_action"));
	public static final Codec<TradeBoardActionPayload> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		BlockPos.CODEC.fieldOf("board_pos").forGetter(TradeBoardActionPayload::boardPos),
		Codec.STRING.fieldOf("action_type").forGetter(TradeBoardActionPayload::actionType),
		Codec.INT.optionalFieldOf("row_index", -1).forGetter(TradeBoardActionPayload::rowIndex),
		Codec.INT.optionalFieldOf("option_index", -1).forGetter(TradeBoardActionPayload::optionIndex),
		Codec.STRING.optionalFieldOf("row_key", "").forGetter(TradeBoardActionPayload::rowKey),
		Codec.STRING.optionalFieldOf("primary_goods_key", "").forGetter(TradeBoardActionPayload::primaryGoodsKey),
		Codec.STRING.optionalFieldOf("secondary_goods_key", "").forGetter(TradeBoardActionPayload::secondaryGoodsKey),
		Codec.INT.optionalFieldOf("amount", 0).forGetter(TradeBoardActionPayload::amount)
	).apply(instance, TradeBoardActionPayload::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, TradeBoardActionPayload> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

	@Override
	public Type<TradeBoardActionPayload> type() {
		return TYPE;
	}
}
