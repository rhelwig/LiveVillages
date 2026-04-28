package com.ronhelwig.livevillages.menu;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record TradeBoardPlayerGoodsView(
	String goodsKey,
	String label,
	int amount
) {
	public static final Codec<TradeBoardPlayerGoodsView> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.fieldOf("goods_key").forGetter(TradeBoardPlayerGoodsView::goodsKey),
		Codec.STRING.fieldOf("label").forGetter(TradeBoardPlayerGoodsView::label),
		Codec.INT.optionalFieldOf("amount", 0).forGetter(TradeBoardPlayerGoodsView::amount)
	).apply(instance, TradeBoardPlayerGoodsView::new));
}
