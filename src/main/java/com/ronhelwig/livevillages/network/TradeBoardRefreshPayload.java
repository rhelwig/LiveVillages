package com.ronhelwig.livevillages.network;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.ronhelwig.livevillages.LiveVillages;
import com.ronhelwig.livevillages.menu.TradeBoardInventoryEntryView;
import com.ronhelwig.livevillages.menu.TradeBoardPlayerGoodsView;
import com.ronhelwig.livevillages.menu.TradeBoardSettlementView;

public record TradeBoardRefreshPayload(
	BlockPos boardPos,
	TradeBoardSettlementView settlement,
	String message,
	boolean success,
	List<TradeBoardPlayerGoodsView> playerGoods,
	List<TradeBoardInventoryEntryView> inventoryRows
) implements CustomPacketPayload {
	public static final Type<TradeBoardRefreshPayload> TYPE = new Type<>(LiveVillages.id("trade_board_refresh"));
	public static final Codec<TradeBoardRefreshPayload> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		BlockPos.CODEC.fieldOf("board_pos").forGetter(TradeBoardRefreshPayload::boardPos),
		TradeBoardSettlementView.CODEC.fieldOf("settlement").forGetter(TradeBoardRefreshPayload::settlement),
		Codec.STRING.optionalFieldOf("message", "").forGetter(TradeBoardRefreshPayload::message),
		Codec.BOOL.optionalFieldOf("success", false).forGetter(TradeBoardRefreshPayload::success),
		TradeBoardPlayerGoodsView.CODEC.listOf().optionalFieldOf("player_goods", List.of()).forGetter(TradeBoardRefreshPayload::playerGoods),
		TradeBoardInventoryEntryView.CODEC.listOf().optionalFieldOf("inventory_rows", List.of()).forGetter(TradeBoardRefreshPayload::inventoryRows)
	).apply(instance, TradeBoardRefreshPayload::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, TradeBoardRefreshPayload> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

	@Override
	public Type<TradeBoardRefreshPayload> type() {
		return TYPE;
	}
}
