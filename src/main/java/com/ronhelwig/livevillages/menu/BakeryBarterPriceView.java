package com.ronhelwig.livevillages.menu;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record BakeryBarterPriceView(
	String goodsKey,
	String paymentGoodsKey,
	int pricePressurePercent
) {
	public static final Codec<BakeryBarterPriceView> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.fieldOf("goods_key").forGetter(BakeryBarterPriceView::goodsKey),
		Codec.STRING.fieldOf("payment_goods_key").forGetter(BakeryBarterPriceView::paymentGoodsKey),
		Codec.INT.optionalFieldOf("price_pressure_percent", 100).forGetter(BakeryBarterPriceView::pricePressurePercent)
	).apply(instance, BakeryBarterPriceView::new));

	public BakeryBarterPriceView {
		pricePressurePercent = Math.max(55, Math.min(130, pricePressurePercent));
	}
}
