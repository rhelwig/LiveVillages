package com.ronhelwig.livevillages.menu;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record TradeBoardProjectView(
	String label,
	int progressPercent
) {
	public static final Codec<TradeBoardProjectView> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.fieldOf("label").forGetter(TradeBoardProjectView::label),
		Codec.INT.optionalFieldOf("progress_percent", 0).forGetter(TradeBoardProjectView::progressPercent)
	).apply(instance, TradeBoardProjectView::new));
}
