package com.ronhelwig.livevillages.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record SettlementOverlayTaskView(
	String label,
	int count
) {
	public static final Codec<SettlementOverlayTaskView> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.fieldOf("label").forGetter(SettlementOverlayTaskView::label),
		Codec.INT.optionalFieldOf("count", 0).forGetter(SettlementOverlayTaskView::count)
	).apply(instance, SettlementOverlayTaskView::new));
}
