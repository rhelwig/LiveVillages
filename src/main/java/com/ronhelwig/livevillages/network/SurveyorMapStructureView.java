package com.ronhelwig.livevillages.network;

import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;

public record SurveyorMapStructureView(
	BlockPos pos,
	String kind
) {
	public static final Codec<SurveyorMapStructureView> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		BlockPos.CODEC.fieldOf("pos").forGetter(SurveyorMapStructureView::pos),
		Codec.STRING.optionalFieldOf("kind", "building").forGetter(SurveyorMapStructureView::kind)
	).apply(instance, SurveyorMapStructureView::new));

	public SurveyorMapStructureView {
		Objects.requireNonNull(pos, "pos");
		Objects.requireNonNull(kind, "kind");
	}
}
