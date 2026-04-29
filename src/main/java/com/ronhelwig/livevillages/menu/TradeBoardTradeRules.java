package com.ronhelwig.livevillages.menu;

import java.util.List;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class TradeBoardTradeRules {
	private static final int VALUE_POINTS_PER_EMERALD = 240;
	private static final List<String> TRADEABLE_GOODS_KEYS = List.of(
		"bread",
		"beef",
		"mutton",
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
		"sand",
		"glass",
		"coal",
		"torch",
		"lantern",
		"iron_ingot"
	);

	private TradeBoardTradeRules() {
	}

	public static List<String> tradeableGoodsKeys() {
		return TRADEABLE_GOODS_KEYS;
	}

	public static boolean isTradeableGoods(String goodsKey) {
		return bundleSize(goodsKey) > 0;
	}

	public static int bundleSize(String goodsKey) {
		return switch (goodsKey) {
			case "bread" -> 6;
			case "beef" -> 4;
			case "mutton" -> 4;
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
			case "oak_sapling", "spruce_sapling", "birch_sapling", "jungle_sapling", "acacia_sapling", "cherry_sapling", "dark_oak_sapling", "pale_oak_sapling", "mangrove_propagule" -> 4;
			case "cobblestone" -> 16;
			case "sand" -> 16;
			case "glass" -> 8;
			case "coal" -> 8;
			case "torch" -> 8;
			case "lantern" -> 4;
			case "iron_ingot" -> 4;
			default -> 0;
		};
	}

	public static int bundlePriceEmeralds(String goodsKey, int tradePricePercent) {
		int basePrice = baseBundlePriceEmeralds(goodsKey);

		if (basePrice <= 0) {
			return 0;
		}

		return Math.max(1, (int) Math.round(basePrice * (tradePricePercent / 100.0D)));
	}

	public static int bundleValuePoints(String goodsKey, int tradePricePercent) {
		int basePrice = baseBundlePriceEmeralds(goodsKey);

		if (basePrice <= 0) {
			return 0;
		}

		return Math.max(1, (int) Math.round(basePrice * VALUE_POINTS_PER_EMERALD * (tradePricePercent / 100.0D)));
	}

	public static int itemValuePoints(String goodsKey, int tradePricePercent) {
		int bundleSize = bundleSize(goodsKey);

		if (bundleSize <= 0) {
			return 0;
		}

		return Math.max(1, (int) Math.round(bundleValuePoints(goodsKey, tradePricePercent) / (double) bundleSize));
	}

	public static int requiredItemsForValue(String goodsKey, int tradePricePercent, int requiredValuePoints) {
		int itemValuePoints = itemValuePoints(goodsKey, tradePricePercent);

		if (itemValuePoints <= 0 || requiredValuePoints <= 0) {
			return 0;
		}

		return (requiredValuePoints + itemValuePoints - 1) / itemValuePoints;
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

	public static String goodsKeyForStack(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}

		for (String goodsKey : TRADEABLE_GOODS_KEYS) {
			if (matchesGoods(goodsKey, stack)) {
				return goodsKey;
			}
		}

		return null;
	}

	public static ItemStack createGoodsStack(String goodsKey, int amount) {
		if (amount <= 0) {
			return ItemStack.EMPTY;
		}

		return new ItemStack(switch (goodsKey) {
			case "bread" -> Items.BREAD;
			case "beef" -> Items.BEEF;
			case "mutton" -> Items.MUTTON;
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
			case "sand" -> Items.SAND;
			case "glass" -> Items.GLASS;
			case "coal" -> Items.COAL;
			case "torch" -> Items.TORCH;
			case "lantern" -> Items.LANTERN;
			case "iron_ingot" -> Items.IRON_INGOT;
			case "emerald" -> Items.EMERALD;
			default -> Items.AIR;
		}, amount);
	}

	public static String compactLabel(String goodsKey, String fallbackLabel) {
		return switch (goodsKey) {
			case "cobblestone" -> "Cobble";
			case "iron_ingot" -> "Iron";
			default -> fallbackLabel;
		};
	}

	private static int baseBundlePriceEmeralds(String goodsKey) {
		return switch (goodsKey) {
			case "bread", "wheat", "carrot", "potato", "beetroot", "wool", "logs", "planks", "stairs", "slab", "stick", "flint", "feather", "arrow", "apple", "cobblestone", "sand", "torch" -> 1;
			case "oak_sapling", "spruce_sapling", "birch_sapling", "jungle_sapling", "acacia_sapling", "cherry_sapling", "dark_oak_sapling", "pale_oak_sapling", "mangrove_propagule" -> 1;
			case "chest" -> 1;
			case "glass", "bed", "lantern" -> 2;
			case "beef" -> 2;
			case "mutton" -> 2;
			case "coal" -> 2;
			case "iron_ingot" -> 2;
			default -> 0;
		};
	}

	private static boolean matchesGoods(String goodsKey, ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}

		return switch (goodsKey) {
			case "bread" -> stack.is(Items.BREAD);
			case "beef" -> stack.is(Items.BEEF);
			case "mutton" -> stack.is(Items.MUTTON);
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
			case "sand" -> stack.is(Items.SAND) || stack.is(Items.RED_SAND);
			case "glass" -> stack.is(Items.GLASS);
			case "coal" -> stack.is(Items.COAL) || stack.is(Items.CHARCOAL);
			case "torch" -> stack.is(Items.TORCH);
			case "lantern" -> stack.is(Items.LANTERN);
			case "iron_ingot" -> stack.is(Items.IRON_INGOT);
			case "emerald" -> stack.is(Items.EMERALD);
			default -> false;
		};
	}
}
