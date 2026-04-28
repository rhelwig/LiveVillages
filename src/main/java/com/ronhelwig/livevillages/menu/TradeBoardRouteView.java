package com.ronhelwig.livevillages.menu;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record TradeBoardRouteView(
	String targetSettlementName,
	String summary
) {
	public static final Codec<TradeBoardRouteView> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.fieldOf("target_settlement_name").forGetter(TradeBoardRouteView::targetSettlementName),
		Codec.STRING.fieldOf("summary").forGetter(TradeBoardRouteView::summary)
	).apply(instance, TradeBoardRouteView::new));
}
