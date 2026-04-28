package com.ronhelwig.livevillages.network;

import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;

public record SurveyorMapPointView(
	BlockPos pos,
	String kind,
	String label
) {
	public static final Codec<SurveyorMapPointView> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		BlockPos.CODEC.fieldOf("pos").forGetter(SurveyorMapPointView::pos),
		Codec.STRING.optionalFieldOf("kind", "").forGetter(SurveyorMapPointView::kind),
		Codec.STRING.optionalFieldOf("label", "").forGetter(SurveyorMapPointView::label)
	).apply(instance, SurveyorMapPointView::new));

	public SurveyorMapPointView {
		Objects.requireNonNull(pos, "pos");
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(label, "label");
	}
}
