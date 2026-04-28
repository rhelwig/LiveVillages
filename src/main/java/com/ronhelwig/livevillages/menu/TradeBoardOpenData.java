package com.ronhelwig.livevillages.menu;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;

public record TradeBoardOpenData(
	BlockPos boardPos,
	TradeBoardSettlementView settlement
) {
	public static final Codec<TradeBoardOpenData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		BlockPos.CODEC.fieldOf("board_pos").forGetter(TradeBoardOpenData::boardPos),
		TradeBoardSettlementView.CODEC.fieldOf("settlement").forGetter(TradeBoardOpenData::settlement)
	).apply(instance, TradeBoardOpenData::new));
}
