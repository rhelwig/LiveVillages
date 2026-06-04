package com.ronhelwig.livevillages.menu;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record ScribeRecipeView(
	String recipeId,
	String label,
	String outputItemId,
	String paymentItemId,
	int paymentCount
) {
	public static final Codec<ScribeRecipeView> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.fieldOf("recipe_id").forGetter(ScribeRecipeView::recipeId),
		Codec.STRING.fieldOf("label").forGetter(ScribeRecipeView::label),
		Codec.STRING.fieldOf("output_item_id").forGetter(ScribeRecipeView::outputItemId),
		Codec.STRING.fieldOf("payment_item_id").forGetter(ScribeRecipeView::paymentItemId),
		Codec.INT.optionalFieldOf("payment_count", 1).forGetter(ScribeRecipeView::paymentCount)
	).apply(instance, ScribeRecipeView::new));
}
