package com.ronhelwig.livevillages.menu;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record TradeBoardRoleView(
	String roleKey,
	String label,
	int count
) {
	public static final Codec<TradeBoardRoleView> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.fieldOf("role_key").forGetter(TradeBoardRoleView::roleKey),
		Codec.STRING.fieldOf("label").forGetter(TradeBoardRoleView::label),
		Codec.INT.optionalFieldOf("count", 0).forGetter(TradeBoardRoleView::count)
	).apply(instance, TradeBoardRoleView::new));
}
