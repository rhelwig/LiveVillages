package com.ronhelwig.livevillages.menu;

import java.util.List;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.ronhelwig.livevillages.content.LiveVillagesItems;

public final class FletchingTableRecipes {
	static final TagKey<Item> COPPER_NUGGETS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "copper_nuggets"));
	private static final List<FletchingTableRecipe> RECIPES = List.of(
		new FletchingTableRecipe("arrow", Items.STICK, 1, Items.FEATHER, 1, Items.FLINT, 1, new ItemStack(Items.ARROW, 8)),
		new FletchingTableRecipe("copperhead_arrow", Items.STICK, 1, Items.FEATHER, 1, Items.COPPER_NUGGET, 1, new ItemStack(LiveVillagesItems.COPPERHEAD_ARROW, 8)),
		new FletchingTableRecipe("ironhead_arrow", Items.STICK, 1, Items.FEATHER, 1, Items.IRON_NUGGET, 1, new ItemStack(LiveVillagesItems.IRONHEAD_ARROW, 8)),
		new FletchingTableRecipe("diamondhead_arrow", Items.STICK, 1, Items.FEATHER, 1, Items.DIAMOND, 1, new ItemStack(LiveVillagesItems.DIAMONDHEAD_ARROW, 4))
	);

	private FletchingTableRecipes() {
	}

	public static List<FletchingTableRecipe> all() {
		return RECIPES;
	}

	public static boolean acceptsShaft(ItemStack stack) {
		return stack.is(Items.STICK);
	}

	public static boolean acceptsFletching(ItemStack stack) {
		return stack.is(Items.FEATHER);
	}

	public static boolean acceptsHead(ItemStack stack) {
		return stack.is(Items.FLINT)
			|| isCopperHeadMaterial(stack)
			|| stack.is(Items.IRON_NUGGET)
			|| stack.is(Items.DIAMOND);
	}

	static boolean isCopperHeadMaterial(ItemStack stack) {
		return stack.is(Items.COPPER_NUGGET) || stack.is(COPPER_NUGGETS);
	}

	public static int recipeIndexForHead(ItemStack headStack) {
		if (headStack.is(Items.FLINT)) {
			return 0;
		}

		if (isCopperHeadMaterial(headStack)) {
			return 1;
		}

		if (headStack.is(Items.IRON_NUGGET)) {
			return 2;
		}

		if (headStack.is(Items.DIAMOND)) {
			return 3;
		}

		return -1;
	}

	public static int firstCraftableRecipeIndex(ItemStack shaftStack, ItemStack fletchingStack, ItemStack headStack) {
		for (int index = 0; index < RECIPES.size(); index++) {
			if (RECIPES.get(index).matches(shaftStack, fletchingStack, headStack)) {
				return index;
			}
		}

		return -1;
	}
}
