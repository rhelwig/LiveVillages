package com.ronhelwig.livevillages.menu;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;

public record ScribeDeskOpenData(
	BlockPos deskPos,
	String settlementId,
	String settlementName,
	int settlementTier,
	List<ScribeRecipeView> recipes,
	List<ScribeRecipeView> playerRecipes
) {
	public static final Codec<ScribeDeskOpenData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		BlockPos.CODEC.fieldOf("desk_pos").forGetter(ScribeDeskOpenData::deskPos),
		Codec.STRING.optionalFieldOf("settlement_id", "").forGetter(ScribeDeskOpenData::settlementId),
		Codec.STRING.optionalFieldOf("settlement_name", "").forGetter(ScribeDeskOpenData::settlementName),
		Codec.INT.optionalFieldOf("settlement_tier", 1).forGetter(ScribeDeskOpenData::settlementTier),
		ScribeRecipeView.CODEC.listOf().optionalFieldOf("recipes", List.of()).forGetter(ScribeDeskOpenData::recipes),
		ScribeRecipeView.CODEC.listOf().optionalFieldOf("player_recipes", List.of()).forGetter(ScribeDeskOpenData::playerRecipes)
	).apply(instance, ScribeDeskOpenData::new));
}
