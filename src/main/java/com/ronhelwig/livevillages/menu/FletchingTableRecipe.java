package com.ronhelwig.livevillages.menu;

import java.util.Objects;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public record FletchingTableRecipe(
	String id,
	Item shaftItem,
	int shaftCount,
	Item fletchingItem,
	int fletchingCount,
	Item headItem,
	int headCount,
	ItemStack result
) {
	public FletchingTableRecipe {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(shaftItem, "shaftItem");
		Objects.requireNonNull(fletchingItem, "fletchingItem");
		Objects.requireNonNull(headItem, "headItem");
		Objects.requireNonNull(result, "result");
	}

	public boolean matches(ItemStack shaftStack, ItemStack fletchingStack, ItemStack headStack) {
		boolean matchingHead = "copperhead_arrow".equals(id)
			? FletchingTableRecipes.isCopperHeadMaterial(headStack)
			: headStack.is(headItem);

		return shaftStack.is(shaftItem)
			&& shaftStack.getCount() >= shaftCount
			&& fletchingStack.is(fletchingItem)
			&& fletchingStack.getCount() >= fletchingCount
			&& matchingHead
			&& headStack.getCount() >= headCount;
	}

	public ItemStack assemble() {
		return result.copy();
	}
}
