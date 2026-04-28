package com.ronhelwig.livevillages.menu;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public record CarpenterBenchRecipe(Item input, ItemStack result) {
	public CarpenterBenchRecipe(Item input, Item result, int count) {
		this(input, new ItemStack(result, count));
	}

	public boolean matches(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() == input;
	}

	public ItemStack assemble() {
		return result.copy();
	}
}
