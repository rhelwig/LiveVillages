package com.ronhelwig.livevillages.menu;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record TradeBoardGoodsView(
	String goodsKey,
	String label,
	int current,
	int target,
	int tradePricePercent,
	int tradeBundleSize,
	int tradePriceEmeralds
) {
	public static final Codec<TradeBoardGoodsView> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.fieldOf("goods_key").forGetter(TradeBoardGoodsView::goodsKey),
		Codec.STRING.fieldOf("label").forGetter(TradeBoardGoodsView::label),
		Codec.INT.optionalFieldOf("current", 0).forGetter(TradeBoardGoodsView::current),
		Codec.INT.optionalFieldOf("target", 0).forGetter(TradeBoardGoodsView::target),
		Codec.INT.optionalFieldOf("trade_price_percent", 100).forGetter(TradeBoardGoodsView::tradePricePercent),
		Codec.INT.optionalFieldOf("trade_bundle_size", 0).forGetter(TradeBoardGoodsView::tradeBundleSize),
		Codec.INT.optionalFieldOf("trade_price_emeralds", 0).forGetter(TradeBoardGoodsView::tradePriceEmeralds)
	).apply(instance, TradeBoardGoodsView::new));
}
