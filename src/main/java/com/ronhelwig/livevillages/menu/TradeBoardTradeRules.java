package com.ronhelwig.livevillages.menu;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ronhelwig.livevillages.content.LiveVillagesBlocks;
import com.ronhelwig.livevillages.sim.SettlementEconomyRules;
import com.ronhelwig.livevillages.sim.SettlementTiers;

import net.minecraft.core.NonNullList;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;

public final class TradeBoardTradeRules {
	private static final int VALUE_POINTS_PER_EMERALD = 240;
	private static final String ITEM_KEY_PREFIX = "item:";
	private static RecipeManager cachedRecipeManager;
	private static RecipeCostIndex cachedRecipeCostIndex = RecipeCostIndex.EMPTY;
	private static final List<String> TRADEABLE_GOODS_KEYS = List.of(
		"bread",
		"baked_potato",
		"cookie",
		"pumpkin_pie",
		"cake",
		"golden_apple",
		"beef",
		"mutton",
		"pork",
		"leather",
		"wheat",
		"carrot",
		"potato",
		"beetroot",
		"wool",
		"bed",
		"logs",
		"planks",
		"stairs",
		"slab",
		"chest",
		"stick",
		"flint",
		"feather",
		"arrow",
		"apple",
		"egg",
		"milk_bucket",
		"sugar",
		"cocoa_beans",
		"pumpkin",
		"oak_sapling",
		"spruce_sapling",
		"birch_sapling",
		"jungle_sapling",
		"acacia_sapling",
		"cherry_sapling",
		"dark_oak_sapling",
		"pale_oak_sapling",
		"mangrove_propagule",
		"cobblestone",
		"dirt",
		"ladder",
		"milepost",
		"sand",
		"glass",
		"coal",
		"torch",
		"lantern",
		"iron_ingot",
		"gold_ingot",
		"copper_ingot",
		"raw_iron",
		"raw_copper",
		"redstone",
		"diamond"
	);

	private TradeBoardTradeRules() {
	}

	public static List<String> tradeableGoodsKeys() {
		return TRADEABLE_GOODS_KEYS;
	}

	public static boolean isTradeableGoods(String goodsKey) {
		return bundleSize(goodsKey) > 0;
	}

	public static boolean isExactItemKey(String goodsKey) {
		return goodsKey != null && goodsKey.startsWith(ITEM_KEY_PREFIX);
	}

	public static boolean isUnlockedForSettlementTier(String goodsKey, int settlementTier) {
		return SettlementEconomyRules.isUnlockedForSettlementTier(goodsKey, settlementTier);
	}

	public static String exactItemKeyForStack(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}

		return ITEM_KEY_PREFIX + BuiltInRegistries.ITEM.getKey(stack.getItem());
	}

	public static String stockKeyForStack(ItemStack stack) {
		String goodsKey = goodsKeyForStack(stack);
		if (goodsKey != null) {
			return goodsKey;
		}

		return exactItemKeyForStack(stack);
	}

	public static boolean isEggItem(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}

		if (stack.is(Items.EGG)) {
			return true;
		}

		Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
		if (itemId == null) {
			return false;
		}

		String path = itemId.getPath();
		return (path.equals("egg") || path.endsWith("_egg")) && !path.endsWith("_spawn_egg");
	}

	public static int bundleSize(String goodsKey) {
		if (isExactItemKey(goodsKey)) {
			return exactItemBundleSize(itemForExactKey(goodsKey));
		}

		return switch (goodsKey) {
			case "bread" -> 6;
			case "baked_potato" -> 8;
			case "cookie" -> 12;
			case "pumpkin_pie" -> 4;
			case "cake" -> 1;
			case "golden_apple" -> 1;
			case "beef" -> 4;
			case "mutton" -> 4;
			case "pork" -> 4;
			case "leather" -> 4;
			case "wheat" -> 12;
			case "carrot", "potato", "beetroot" -> 10;
			case "wool" -> 6;
			case "bed" -> 1;
			case "logs" -> 6;
			case "planks" -> 12;
			case "stairs" -> 6;
			case "slab" -> 12;
			case "chest" -> 1;
			case "stick" -> 16;
			case "flint", "feather" -> 8;
			case "arrow" -> 16;
			case "apple" -> 8;
			case "egg" -> 8;
			case "milk_bucket" -> 1;
			case "sugar", "cocoa_beans" -> 8;
			case "pumpkin" -> 4;
			case "oak_sapling", "spruce_sapling", "birch_sapling", "jungle_sapling", "acacia_sapling", "cherry_sapling", "dark_oak_sapling", "pale_oak_sapling", "mangrove_propagule" -> 4;
			case "cobblestone" -> 16;
			case "dirt" -> 16;
			case "ladder" -> 8;
			case "milepost" -> 1;
			case "sand" -> 16;
			case "glass" -> 8;
			case "coal" -> 8;
			case "torch" -> 8;
			case "lantern" -> 4;
			case "iron_ingot" -> 4;
			case "gold_ingot" -> 4;
			case "copper_ingot" -> 4;
			case "raw_iron" -> 8;
			case "raw_copper" -> 8;
			case "redstone" -> 16;
			case "diamond" -> 4;
			default -> 0;
		};
	}

	public static int bundlePriceEmeralds(String goodsKey, int tradePricePercent) {
		int valuePoints = bundleValuePoints(goodsKey, tradePricePercent);
		if (valuePoints <= 0) {
			return 0;
		}

		return Math.max(1, divideRoundUp(valuePoints, VALUE_POINTS_PER_EMERALD));
	}

	public static int bundlePriceEmeralds(ServerLevel level, String goodsKey, int tradePricePercent) {
		int valuePoints = bundleValuePoints(level, goodsKey, tradePricePercent);
		if (valuePoints <= 0) {
			return 0;
		}

		return Math.max(1, divideRoundUp(valuePoints, VALUE_POINTS_PER_EMERALD));
	}

	public static int bundleValuePoints(String goodsKey, int tradePricePercent) {
		int bundleSize = bundleSize(goodsKey);

		if (bundleSize <= 0) {
			return 0;
		}

		return Math.max(1, itemValuePoints(goodsKey, tradePricePercent) * bundleSize);
	}

	public static int bundleValuePoints(ServerLevel level, String goodsKey, int tradePricePercent) {
		int bundleSize = bundleSize(goodsKey);

		if (bundleSize <= 0) {
			return 0;
		}

		return Math.max(1, itemValuePoints(level, goodsKey, tradePricePercent) * bundleSize);
	}

	public static int itemValuePoints(String goodsKey, int tradePricePercent) {
		int baseItemValuePoints = baseItemValuePoints(goodsKey, tradePricePercent);
		if (baseItemValuePoints <= 0) {
			return 0;
		}

		return Math.max(baseItemValuePoints, productionCostItemValuePoints(goodsKey));
	}

	public static int itemValuePoints(ServerLevel level, String goodsKey, int tradePricePercent) {
		int baseItemValuePoints = baseItemValuePoints(goodsKey, tradePricePercent);
		if (baseItemValuePoints <= 0) {
			return 0;
		}

		return Math.max(baseItemValuePoints, productionCostItemValuePoints(level, goodsKey));
	}

	public static int requiredItemsForValue(String goodsKey, int tradePricePercent, int requiredValuePoints) {
		int itemValuePoints = itemValuePoints(goodsKey, tradePricePercent);

		if (itemValuePoints <= 0 || requiredValuePoints <= 0) {
			return 0;
		}

		return (requiredValuePoints + itemValuePoints - 1) / itemValuePoints;
	}

	public static int requiredItemsForValue(ServerLevel level, String goodsKey, int tradePricePercent, int requiredValuePoints) {
		int itemValuePoints = itemValuePoints(level, goodsKey, tradePricePercent);

		if (itemValuePoints <= 0 || requiredValuePoints <= 0) {
			return 0;
		}

		return divideRoundUp(requiredValuePoints, itemValuePoints);
	}

	public static int productionCostPaymentAmount(String outputGoodsKey, int outputAmount, String paymentGoodsKey) {
		if (outputGoodsKey == null || paymentGoodsKey == null || outputAmount <= 0) {
			return 0;
		}

		int paymentValue = baseItemValuePoints(paymentGoodsKey, 100);
		if (paymentValue <= 0) {
			return 0;
		}

		ProductionRecipe recipe = fallbackProductionRecipeForGoods(outputGoodsKey);
		if (recipe == null || recipe.outputAmount() <= 0) {
			return 0;
		}

		int valueCost = divideRoundUp(productionRecipeCostValuePoints(recipe) * outputAmount, recipe.outputAmount() * paymentValue);
		int directIngredientCost = divideRoundUp(recipe.ingredients().getOrDefault(paymentGoodsKey, 0) * outputAmount, recipe.outputAmount());
		return Math.max(valueCost, directIngredientCost);
	}

	public static int productionCostPaymentAmount(ServerLevel level, String outputGoodsKey, int outputAmount, String paymentGoodsKey) {
		if (level == null || outputGoodsKey == null || paymentGoodsKey == null || outputAmount <= 0) {
			return productionCostPaymentAmount(outputGoodsKey, outputAmount, paymentGoodsKey);
		}

		int paymentValue = itemValuePoints(level, paymentGoodsKey, 100);
		if (paymentValue <= 0) {
			return 0;
		}

		int productionCost = productionCostItemValuePoints(level, outputGoodsKey);
		int fallbackAmount = productionCostPaymentAmount(outputGoodsKey, outputAmount, paymentGoodsKey);
		if (productionCost <= 0) {
			return fallbackAmount;
		}

		int recipeAmount = divideRoundUp(productionCost * outputAmount, paymentValue);
		return recipeAmount > 0 ? recipeAmount : fallbackAmount;
	}

	public static int productionCostItemValuePoints(String goodsKey) {
		ProductionRecipe recipe = fallbackProductionRecipeForGoods(goodsKey);
		if (recipe == null || recipe.outputAmount() <= 0) {
			return 0;
		}

		return divideRoundUp(productionRecipeCostValuePoints(recipe), recipe.outputAmount());
	}

	public static int productionCostItemValuePoints(ServerLevel level, String goodsKey) {
		if (level == null || goodsKey == null) {
			return productionCostItemValuePoints(goodsKey);
		}

		int recipeCost = recipeCostIndex(level).costValuePoints(goodsKey);
		return recipeCost > 0 ? recipeCost : productionCostItemValuePoints(goodsKey);
	}

	public static int countPlayerGoods(Inventory inventory, String goodsKey) {
		int count = 0;

		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);

			if (matchesGoods(goodsKey, stack)) {
				count += stack.getCount();
			}
		}

		return count;
	}

	public static boolean removePlayerGoods(Inventory inventory, String goodsKey, int amount) {
		if (amount <= 0 || countPlayerGoods(inventory, goodsKey) < amount) {
			return false;
		}

		int remaining = amount;

		for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
			ItemStack stack = inventory.getItem(slot);

			if (!matchesGoods(goodsKey, stack)) {
				continue;
			}

			int removed = Math.min(remaining, stack.getCount());
			stack.shrink(removed);
			remaining -= removed;

			if (stack.isEmpty()) {
				inventory.setItem(slot, ItemStack.EMPTY);
			}
		}

		return remaining == 0;
	}

	public static boolean removePlayerGoodsFromSlot(Inventory inventory, int slot, int amount) {
		if (amount <= 0 || slot < 0 || slot >= inventory.getContainerSize()) {
			return false;
		}

		ItemStack stack = inventory.getItem(slot);
		if (stack.isEmpty() || stack.getCount() < amount) {
			return false;
		}

		stack.shrink(amount);
		if (stack.isEmpty()) {
			inventory.setItem(slot, ItemStack.EMPTY);
		}

		return true;
	}

	public static boolean hasStoredContents(ItemStack stack) {
		return !storedContentsCopy(stack).isEmpty();
	}

	public static List<ItemStack> storedContentsCopy(ItemStack stack) {
		if (stack.isEmpty()) {
			return List.of();
		}

		BundleContents bundleContents = stack.get(DataComponents.BUNDLE_CONTENTS);
		if (bundleContents != null && !bundleContents.isEmpty()) {
			List<ItemStack> contents = new java.util.ArrayList<>();
			for (ItemStack content : bundleContents.itemCopyStream().toList()) {
				if (!content.isEmpty()) {
					contents.add(content.copy());
				}
			}
			return contents;
		}

		ItemContainerContents containerContents = stack.get(DataComponents.CONTAINER);
		if (containerContents != null) {
			NonNullList<ItemStack> copiedContents = NonNullList.create();
			containerContents.copyInto(copiedContents);
			List<ItemStack> contents = new java.util.ArrayList<>();
			for (ItemStack content : copiedContents) {
				if (!content.isEmpty()) {
					contents.add(content.copy());
				}
			}
			return contents;
		}

		return List.of();
	}

	public static void clearStoredContents(ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}

		if (stack.has(DataComponents.BUNDLE_CONTENTS)) {
			stack.remove(DataComponents.BUNDLE_CONTENTS);
		}
		if (stack.has(DataComponents.CONTAINER)) {
			stack.remove(DataComponents.CONTAINER);
		}
	}

	public static int countPlayerGoodsByExactItemKey(Inventory inventory, String exactItemKey) {
		if (!isExactItemKey(exactItemKey)) {
			return 0;
		}

		int count = 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (matchesExactItemKey(exactItemKey, stack)) {
				count += stack.getCount();
			}
		}

		return count;
	}

	public static boolean removePlayerGoodsByExactItemKey(Inventory inventory, String exactItemKey, int amount) {
		if (!isExactItemKey(exactItemKey) || amount <= 0) {
			return false;
		}

		int remaining = amount;
		for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!matchesExactItemKey(exactItemKey, stack)) {
				continue;
			}

			int remove = Math.min(stack.getCount(), remaining);
			stack.shrink(remove);
			remaining -= remove;

			if (stack.isEmpty()) {
				inventory.setItem(slot, ItemStack.EMPTY);
			}
		}

		return remaining == 0;
	}

	public static String goodsKeyForStack(ItemStack stack) {
		return goodsKeyForStack(stack, SettlementTiers.MAX_TIER);
	}

	public static String goodsKeyForStack(ItemStack stack, int settlementTier) {
		if (stack.isEmpty()) {
			return null;
		}

		for (String goodsKey : TRADEABLE_GOODS_KEYS) {
			if (isUnlockedForSettlementTier(goodsKey, settlementTier) && matchesGoods(goodsKey, stack)) {
				return goodsKey;
			}
		}

		return null;
	}

	public static ItemStack createGoodsStack(String goodsKey, int amount) {
		if (amount <= 0) {
			return ItemStack.EMPTY;
		}

		if (isExactItemKey(goodsKey)) {
			Item item = itemForExactKey(goodsKey);
			return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, amount);
		}

		return new ItemStack(switch (goodsKey) {
			case "bread" -> Items.BREAD;
			case "baked_potato" -> Items.BAKED_POTATO;
			case "cookie" -> Items.COOKIE;
			case "pumpkin_pie" -> Items.PUMPKIN_PIE;
			case "cake" -> Items.CAKE;
			case "golden_apple" -> Items.GOLDEN_APPLE;
			case "beef" -> Items.BEEF;
			case "cod" -> Items.COD;
			case "mutton" -> Items.MUTTON;
			case "pork" -> Items.PORKCHOP;
			case "leather" -> Items.LEATHER;
			case "wheat" -> Items.WHEAT;
			case "carrot" -> Items.CARROT;
			case "potato" -> Items.POTATO;
			case "beetroot" -> Items.BEETROOT;
			case "wool" -> Items.WHITE_WOOL;
			case "bed" -> Items.WHITE_BED;
			case "logs" -> Items.OAK_LOG;
			case "planks" -> Items.OAK_PLANKS;
			case "stairs" -> Items.OAK_STAIRS;
			case "slab" -> Items.OAK_SLAB;
			case "chest" -> Items.CHEST;
			case "stick" -> Items.STICK;
			case "flint" -> Items.FLINT;
			case "feather" -> Items.FEATHER;
			case "arrow" -> Items.ARROW;
			case "apple" -> Items.APPLE;
			case "egg" -> Items.EGG;
			case "milk_bucket" -> Items.MILK_BUCKET;
			case "sugar" -> Items.SUGAR;
			case "cocoa_beans" -> Items.COCOA_BEANS;
			case "pumpkin" -> Items.PUMPKIN;
			case "oak_sapling" -> Items.OAK_SAPLING;
			case "spruce_sapling" -> Items.SPRUCE_SAPLING;
			case "birch_sapling" -> Items.BIRCH_SAPLING;
			case "jungle_sapling" -> Items.JUNGLE_SAPLING;
			case "acacia_sapling" -> Items.ACACIA_SAPLING;
			case "cherry_sapling" -> Items.CHERRY_SAPLING;
			case "dark_oak_sapling" -> Items.DARK_OAK_SAPLING;
			case "pale_oak_sapling" -> Items.PALE_OAK_SAPLING;
			case "mangrove_propagule" -> Items.MANGROVE_PROPAGULE;
			case "cobblestone" -> Items.COBBLESTONE;
			case "dirt" -> Items.DIRT;
			case "ladder" -> Items.LADDER;
			case "milepost" -> LiveVillagesBlocks.MILEPOST_ITEM;
			case "sand" -> Items.SAND;
			case "glass" -> Items.GLASS;
			case "coal" -> Items.COAL;
			case "torch" -> Items.TORCH;
			case "lantern" -> Items.LANTERN;
			case "iron_ingot" -> Items.IRON_INGOT;
			case "gold_ingot" -> Items.GOLD_INGOT;
			case "copper_ingot" -> Items.COPPER_INGOT;
			case "raw_iron" -> Items.RAW_IRON;
			case "raw_copper" -> Items.RAW_COPPER;
			case "redstone" -> Items.REDSTONE;
			case "diamond" -> Items.DIAMOND;
			case "emerald" -> Items.EMERALD;
			default -> Items.AIR;
		}, amount);
	}

	public static String compactLabel(String goodsKey, String fallbackLabel) {
		if (isExactItemKey(goodsKey)) {
			return fallbackLabel;
		}

		return switch (goodsKey) {
			case "cobblestone" -> "Cobble";
			case "iron_ingot" -> "Iron";
			case "copper_ingot" -> "Copper";
			case "raw_iron" -> "Raw Iron";
			case "raw_copper" -> "Raw Copper";
			default -> fallbackLabel;
		};
	}

	public static String displayLabel(String goodsKey, String fallbackLabel) {
		if (!isExactItemKey(goodsKey)) {
			return fallbackLabel;
		}

		Item item = itemForExactKey(goodsKey);
		if (item == Items.AIR) {
			return fallbackLabel;
		}

		return item.getDefaultInstance().getHoverName().getString();
	}

	private static int baseItemValuePoints(String goodsKey, int tradePricePercent) {
		int bundleSize = bundleSize(goodsKey);
		int bundleValuePoints = baseBundleValuePoints(goodsKey, tradePricePercent);

		if (bundleSize <= 0 || bundleValuePoints <= 0) {
			return 0;
		}

		return Math.max(1, (int) Math.round(bundleValuePoints / (double) bundleSize));
	}

	private static int baseBundleValuePoints(String goodsKey, int tradePricePercent) {
		int basePrice = baseBundlePriceEmeralds(goodsKey);

		if (basePrice <= 0) {
			return 0;
		}

		return Math.max(1, (int) Math.round(basePrice * VALUE_POINTS_PER_EMERALD * (tradePricePercent / 100.0D)));
	}

	private static int productionRecipeCostValuePoints(ProductionRecipe recipe) {
		int cost = 0;

		for (Map.Entry<String, Integer> ingredient : recipe.ingredients().entrySet()) {
			int ingredientValue = baseItemValuePoints(ingredient.getKey(), 100);
			if (ingredientValue <= 0) {
				return 0;
			}

			cost += ingredientValue * ingredient.getValue();
		}

		return cost;
	}

	private static RecipeCostIndex recipeCostIndex(ServerLevel level) {
		RecipeManager recipeManager = level.getServer().getRecipeManager();
		if (recipeManager == cachedRecipeManager) {
			return cachedRecipeCostIndex;
		}

		RecipeCostIndex recipeCostIndex = buildRecipeCostIndex(recipeManager);
		cachedRecipeManager = recipeManager;
		cachedRecipeCostIndex = recipeCostIndex;
		return recipeCostIndex;
	}

	private static RecipeCostIndex buildRecipeCostIndex(RecipeManager recipeManager) {
		Map<String, Integer> itemCostByGoodsKey = new LinkedHashMap<>();
		for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
			ResolvedProductionRecipe recipe = resolveProductionRecipe(holder.value());
			if (recipe == null || recipe.outputGoodsKey() == null || recipe.outputAmount() <= 0 || recipe.costValuePoints() <= 0) {
				continue;
			}

			int itemCost = divideRoundUp(recipe.costValuePoints(), recipe.outputAmount());
			itemCostByGoodsKey.merge(recipe.outputGoodsKey(), itemCost, Math::min);
		}

		return new RecipeCostIndex(Map.copyOf(itemCostByGoodsKey));
	}

	private static ResolvedProductionRecipe resolveProductionRecipe(Recipe<?> recipe) {
		if (recipe == null || recipe.isSpecial()) {
			return null;
		}

		ItemStack result = recipeResultStack(recipe);
		String outputGoodsKey = stockKeyForStack(result);
		if (result.isEmpty() || outputGoodsKey == null) {
			return null;
		}

		List<Ingredient> ingredients = recipe.placementInfo().ingredients();
		if (ingredients.isEmpty()) {
			return null;
		}

		int cost = 0;
		for (Ingredient ingredient : ingredients) {
			int ingredientValue = ingredientBaseValuePoints(ingredient);
			if (ingredientValue <= 0) {
				return null;
			}

			cost += ingredientValue;
		}

		return new ResolvedProductionRecipe(outputGoodsKey, result.getCount(), cost);
	}

	private static ItemStack recipeResultStack(Recipe<?> recipe) {
		try {
			if (recipe instanceof CraftingRecipe craftingRecipe) {
				return craftingRecipe.assemble(CraftingInput.EMPTY);
			}

			if (recipe instanceof SingleItemRecipe singleItemRecipe) {
				ItemStack input = firstIngredientStack(singleItemRecipe.input());
				return input.isEmpty() ? ItemStack.EMPTY : singleItemRecipe.assemble(new SingleRecipeInput(input));
			}
		} catch (RuntimeException ignored) {
			return ItemStack.EMPTY;
		}

		return ItemStack.EMPTY;
	}

	private static int ingredientBaseValuePoints(Ingredient ingredient) {
		int bestValue = 0;

		for (Holder<Item> item : ingredient.items().toList()) {
			ItemStack stack = new ItemStack(item, 1);
			String stockKey = stockKeyForStack(stack);
			if (stockKey == null) {
				continue;
			}

			int value = baseItemValuePoints(stockKey, 100);
			if (value > 0 && (bestValue <= 0 || value < bestValue)) {
				bestValue = value;
			}
		}

		return bestValue;
	}

	private static ItemStack firstIngredientStack(Ingredient ingredient) {
		return ingredient.items()
			.findFirst()
			.map(item -> new ItemStack(item, 1))
			.orElse(ItemStack.EMPTY);
	}

	private static ProductionRecipe fallbackProductionRecipeForGoods(String goodsKey) {
		if (goodsKey == null || isExactItemKey(goodsKey)) {
			return null;
		}

		return switch (goodsKey) {
			case "bread" -> new ProductionRecipe("bread", 1, Map.of("wheat", 3));
			case "baked_potato" -> new ProductionRecipe("baked_potato", 1, Map.of("potato", 1));
			case "cookie" -> new ProductionRecipe("cookie", 8, Map.of("wheat", 2, "cocoa_beans", 1));
			case "pumpkin_pie" -> new ProductionRecipe("pumpkin_pie", 1, Map.of("pumpkin", 1, "egg", 1, "sugar", 1));
			case "cake" -> new ProductionRecipe("cake", 1, Map.of("milk_bucket", 3, "sugar", 2, "egg", 1, "wheat", 3));
			case "golden_apple" -> new ProductionRecipe("golden_apple", 1, Map.of("apple", 1, "gold_ingot", 8));
			case "planks" -> new ProductionRecipe("planks", 4, Map.of("logs", 1));
			case "stairs" -> new ProductionRecipe("stairs", 4, Map.of("logs", 2));
			case "slab" -> new ProductionRecipe("slab", 8, Map.of("logs", 1));
			case "stick" -> new ProductionRecipe("stick", 8, Map.of("logs", 1));
			case "chest" -> new ProductionRecipe("chest", 1, Map.of("planks", 8));
			case "ladder" -> new ProductionRecipe("ladder", 3, Map.of("stick", 7));
			case "torch" -> new ProductionRecipe("torch", 4, Map.of("coal", 1, "stick", 1));
			case "arrow" -> new ProductionRecipe("arrow", 4, Map.of("stick", 1, "flint", 1, "feather", 1));
			case "glass" -> new ProductionRecipe("glass", 1, Map.of("sand", 1));
			case "coal" -> new ProductionRecipe("coal", 1, Map.of("logs", 1));
			case "iron_ingot" -> new ProductionRecipe("iron_ingot", 1, Map.of("raw_iron", 1));
			case "copper_ingot" -> new ProductionRecipe("copper_ingot", 1, Map.of("raw_copper", 1));
			case "milepost" -> new ProductionRecipe("milepost", 1, Map.of("cobblestone", 8));
			default -> null;
		};
	}

	private static int baseBundlePriceEmeralds(String goodsKey) {
		if (isExactItemKey(goodsKey)) {
			return exactItemBundlePriceEmeralds(itemForExactKey(goodsKey));
		}

		return switch (goodsKey) {
			case "bread", "baked_potato", "cookie", "wheat", "carrot", "potato", "beetroot", "wool", "logs", "planks", "stairs", "slab", "stick", "flint", "feather", "arrow", "apple", "egg", "sugar", "cocoa_beans", "pumpkin", "cobblestone", "dirt", "sand", "torch" -> 1;
			case "oak_sapling", "spruce_sapling", "birch_sapling", "jungle_sapling", "acacia_sapling", "cherry_sapling", "dark_oak_sapling", "pale_oak_sapling", "mangrove_propagule" -> 1;
			case "chest", "ladder", "redstone" -> 1;
			case "glass", "bed", "lantern", "milepost", "pumpkin_pie", "milk_bucket" -> 2;
			case "beef" -> 2;
			case "mutton" -> 2;
			case "pork" -> 2;
			case "leather" -> 2;
			case "coal" -> 2;
			case "iron_ingot", "gold_ingot", "copper_ingot", "raw_iron", "raw_copper" -> 2;
			case "cake" -> 3;
			case "diamond" -> 4;
			case "golden_apple" -> 6;
			default -> 0;
		};
	}

	private static Item itemForExactKey(String goodsKey) {
		if (!isExactItemKey(goodsKey)) {
			return Items.AIR;
		}

		Identifier itemId = Identifier.tryParse(goodsKey.substring(ITEM_KEY_PREFIX.length()));
		if (itemId == null) {
			return Items.AIR;
		}

		return BuiltInRegistries.ITEM.getOptional(itemId).orElse(Items.AIR);
	}

	private static int divideRoundUp(int value, int divisor) {
		if (value <= 0 || divisor <= 0) {
			return 0;
		}

		return (value + divisor - 1) / divisor;
	}

	private record ProductionRecipe(String outputGoodsKey, int outputAmount, Map<String, Integer> ingredients) {
	}

	private record RecipeCostIndex(Map<String, Integer> costValuePointsByGoodsKey) {
		private static final RecipeCostIndex EMPTY = new RecipeCostIndex(Map.of());

		private int costValuePoints(String goodsKey) {
			return costValuePointsByGoodsKey.getOrDefault(goodsKey, 0);
		}
	}

	private record ResolvedProductionRecipe(String outputGoodsKey, int outputAmount, int costValuePoints) {
	}

	private static int exactItemBundleSize(Item item) {
		if (item == Items.AIR) {
			return 0;
		}

		ItemStack stack = item.getDefaultInstance();
		int maxStackSize = stack.getMaxStackSize();
		if (maxStackSize <= 1) {
			return 1;
		}
		if (maxStackSize <= 16) {
			return Math.min(4, maxStackSize);
		}
		return 8;
	}

	private static int exactItemBundlePriceEmeralds(Item item) {
		if (item == Items.AIR) {
			return 0;
		}

		ItemStack stack = item.getDefaultInstance();
		if (stack.isDamageableItem() || stack.getMaxStackSize() <= 1) {
			return 2;
		}

		return 1;
	}

	private static boolean matchesExactItemKey(String exactItemKey, ItemStack stack) {
		if (!isExactItemKey(exactItemKey) || stack.isEmpty()) {
			return false;
		}

		String stackKey = exactItemKeyForStack(stack);
		return exactItemKey.equals(stackKey);
	}

	private static boolean matchesGoods(String goodsKey, ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}

		return switch (goodsKey) {
			case "bread" -> stack.is(Items.BREAD);
			case "baked_potato" -> stack.is(Items.BAKED_POTATO);
			case "cookie" -> stack.is(Items.COOKIE);
			case "pumpkin_pie" -> stack.is(Items.PUMPKIN_PIE);
			case "cake" -> stack.is(Items.CAKE);
			case "golden_apple" -> stack.is(Items.GOLDEN_APPLE);
			case "beef" -> stack.is(Items.BEEF);
			case "mutton" -> stack.is(Items.MUTTON);
			case "pork" -> stack.is(Items.PORKCHOP);
			case "leather" -> stack.is(Items.LEATHER);
			case "wheat" -> stack.is(Items.WHEAT);
			case "carrot" -> stack.is(Items.CARROT);
			case "potato" -> stack.is(Items.POTATO);
			case "beetroot" -> stack.is(Items.BEETROOT);
			case "wool" -> stack.is(ItemTags.WOOL);
			case "bed" -> stack.is(ItemTags.BEDS);
			case "logs" -> stack.is(ItemTags.LOGS);
			case "planks" -> stack.is(ItemTags.PLANKS);
			case "stairs" -> stack.is(Items.OAK_STAIRS) || stack.is(Items.SPRUCE_STAIRS) || stack.is(Items.BIRCH_STAIRS) || stack.is(Items.JUNGLE_STAIRS) || stack.is(Items.ACACIA_STAIRS) || stack.is(Items.CHERRY_STAIRS) || stack.is(Items.DARK_OAK_STAIRS) || stack.is(Items.PALE_OAK_STAIRS) || stack.is(Items.MANGROVE_STAIRS);
			case "slab" -> stack.is(Items.OAK_SLAB) || stack.is(Items.SPRUCE_SLAB) || stack.is(Items.BIRCH_SLAB) || stack.is(Items.JUNGLE_SLAB) || stack.is(Items.ACACIA_SLAB) || stack.is(Items.CHERRY_SLAB) || stack.is(Items.DARK_OAK_SLAB) || stack.is(Items.PALE_OAK_SLAB) || stack.is(Items.MANGROVE_SLAB);
			case "chest" -> stack.is(Items.CHEST);
			case "stick" -> stack.is(Items.STICK);
			case "flint" -> stack.is(Items.FLINT);
			case "feather" -> stack.is(Items.FEATHER);
			case "arrow" -> stack.is(Items.ARROW);
			case "apple" -> stack.is(Items.APPLE);
			case "egg" -> isEggItem(stack);
			case "milk_bucket" -> stack.is(Items.MILK_BUCKET);
			case "sugar" -> stack.is(Items.SUGAR);
			case "cocoa_beans" -> stack.is(Items.COCOA_BEANS);
			case "pumpkin" -> stack.is(Items.PUMPKIN);
			case "oak_sapling" -> stack.is(Items.OAK_SAPLING);
			case "spruce_sapling" -> stack.is(Items.SPRUCE_SAPLING);
			case "birch_sapling" -> stack.is(Items.BIRCH_SAPLING);
			case "jungle_sapling" -> stack.is(Items.JUNGLE_SAPLING);
			case "acacia_sapling" -> stack.is(Items.ACACIA_SAPLING);
			case "cherry_sapling" -> stack.is(Items.CHERRY_SAPLING);
			case "dark_oak_sapling" -> stack.is(Items.DARK_OAK_SAPLING);
			case "pale_oak_sapling" -> stack.is(Items.PALE_OAK_SAPLING);
			case "mangrove_propagule" -> stack.is(Items.MANGROVE_PROPAGULE);
			case "cobblestone" -> stack.is(Items.COBBLESTONE);
			case "dirt" -> stack.is(Items.DIRT) || stack.is(Items.COARSE_DIRT) || stack.is(Items.ROOTED_DIRT);
			case "ladder" -> stack.is(Items.LADDER);
			case "milepost" -> stack.is(LiveVillagesBlocks.MILEPOST_ITEM);
			case "sand" -> stack.is(Items.SAND) || stack.is(Items.RED_SAND);
			case "glass" -> stack.is(Items.GLASS);
			case "coal" -> stack.is(Items.COAL) || stack.is(Items.CHARCOAL);
			case "torch" -> stack.is(Items.TORCH);
			case "lantern" -> stack.is(Items.LANTERN);
			case "iron_ingot" -> stack.is(Items.IRON_INGOT);
			case "gold_ingot" -> stack.is(Items.GOLD_INGOT);
			case "copper_ingot" -> stack.is(Items.COPPER_INGOT);
			case "raw_iron" -> stack.is(Items.RAW_IRON) || stack.is(Items.IRON_ORE) || stack.is(Items.DEEPSLATE_IRON_ORE);
			case "raw_copper" -> stack.is(Items.RAW_COPPER) || stack.is(Items.COPPER_ORE) || stack.is(Items.DEEPSLATE_COPPER_ORE);
			case "redstone" -> stack.is(Items.REDSTONE) || stack.is(Items.REDSTONE_ORE) || stack.is(Items.DEEPSLATE_REDSTONE_ORE);
			case "diamond" -> stack.is(Items.DIAMOND) || stack.is(Items.DIAMOND_ORE) || stack.is(Items.DEEPSLATE_DIAMOND_ORE);
			case "emerald" -> stack.is(Items.EMERALD);
			default -> false;
		};
	}
}
