package com.ronhelwig.livevillages.network;

import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record SettlementOverlayWorkerView(
	String workerLabel,
	String taskLabel,
	String targetLabel,
	String detailLabel
) {
	public static final Codec<SettlementOverlayWorkerView> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.optionalFieldOf("worker_label", "").forGetter(SettlementOverlayWorkerView::workerLabel),
		Codec.STRING.optionalFieldOf("task_label", "").forGetter(SettlementOverlayWorkerView::taskLabel),
		Codec.STRING.optionalFieldOf("target_label", "").forGetter(SettlementOverlayWorkerView::targetLabel),
		Codec.STRING.optionalFieldOf("detail_label", "").forGetter(SettlementOverlayWorkerView::detailLabel)
	).apply(instance, SettlementOverlayWorkerView::new));

	public SettlementOverlayWorkerView {
		Objects.requireNonNull(workerLabel, "workerLabel");
		Objects.requireNonNull(taskLabel, "taskLabel");
		Objects.requireNonNull(targetLabel, "targetLabel");
		Objects.requireNonNull(detailLabel, "detailLabel");
	}
}
