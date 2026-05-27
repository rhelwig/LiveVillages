package com.ronhelwig.livevillages.menu;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;

public record TradeBoardOpenData(
	BlockPos boardPos,
	TradeBoardSettlementView settlement,
	String playerStandingLabel,
	TradeBoardRaidView raid
) {
	public static final Codec<TradeBoardOpenData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		BlockPos.CODEC.fieldOf("board_pos").forGetter(TradeBoardOpenData::boardPos),
		TradeBoardSettlementView.CODEC.fieldOf("settlement").forGetter(TradeBoardOpenData::settlement),
		Codec.STRING.optionalFieldOf("player_standing_label", "").forGetter(TradeBoardOpenData::playerStandingLabel),
		TradeBoardRaidView.CODEC.optionalFieldOf("raid", TradeBoardRaidView.EMPTY).forGetter(TradeBoardOpenData::raid)
	).apply(instance, TradeBoardOpenData::new));
}
