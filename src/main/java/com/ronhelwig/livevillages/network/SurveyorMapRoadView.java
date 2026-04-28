package com.ronhelwig.livevillages.network;

import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;

public record SurveyorMapRoadView(
	BlockPos pos,
	String quality
) {
	public static final Codec<SurveyorMapRoadView> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		BlockPos.CODEC.fieldOf("pos").forGetter(SurveyorMapRoadView::pos),
		Codec.STRING.optionalFieldOf("quality", "trail").forGetter(SurveyorMapRoadView::quality)
	).apply(instance, SurveyorMapRoadView::new));

	public SurveyorMapRoadView {
		Objects.requireNonNull(pos, "pos");
		Objects.requireNonNull(quality, "quality");
	}
}
