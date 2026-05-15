package com.ronhelwig.livevillages.sim;

import java.util.List;
import java.util.Map;

import com.ronhelwig.livevillages.content.LiveVillagesBlocks;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class SettlementGoods {
	public static final List<String> SEEDLING_GOODS = List.of(
		"oak_sapling",
		"spruce_sapling",
		"birch_sapling",
		"jungle_sapling",
		"acacia_sapling",
		"cherry_sapling",
		"dark_oak_sapling",
		"pale_oak_sapling",
		"mangrove_propagule"
	);

	private SettlementGoods() {
	}

	public static String goodsKeyForItem(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}

		if (stack.is(Items.WHEAT)) {
			return "wheat";
		}

		if (stack.is(Items.BEEF)) {
			return "beef";
		}

		if (stack.is(Items.COD) || stack.is(Items.SALMON) || stack.is(Items.COOKED_COD) || stack.is(Items.COOKED_SALMON)) {
			return "cod";
		}

		if (stack.is(Items.MUTTON)) {
			return "mutton";
		}

		if (stack.is(Items.PORKCHOP)) {
			return "pork";
		}

		if (stack.is(Items.LEATHER)) {
			return "leather";
		}

		if (stack.is(Items.WHEAT_SEEDS)) {
			return "wheat_seeds";
		}

		if (stack.is(Items.CARROT)) {
			return "carrot";
		}

		if (stack.is(Items.POTATO)) {
			return "potato";
		}

		if (stack.is(Items.BEETROOT)) {
			return "beetroot";
		}

		if (stack.is(net.minecraft.tags.ItemTags.WOOL)) {
			return "wool";
		}

		if (stack.is(Items.BEETROOT_SEEDS)) {
			return "beetroot_seeds";
		}

		if (stack.is(Items.BONE_MEAL)) {
			return "bone_meal";
		}

		if (stack.is(Items.LEAF_LITTER)) {
			return "leaf_litter";
		}

		if (stack.is(Items.APPLE)) {
			return "apple";
		}

		if (stack.is(Items.STICK)) {
			return "stick";
		}

		if (stack.is(Items.FLINT)) {
			return "flint";
		}

		if (stack.is(Items.FEATHER)) {
			return "feather";
		}

		if (stack.is(Items.ARROW)) {
			return "arrow";
		}

		if (stack.is(Items.OAK_SAPLING)) {
			return "oak_sapling";
		}

		if (stack.is(Items.SPRUCE_SAPLING)) {
			return "spruce_sapling";
		}

		if (stack.is(Items.BIRCH_SAPLING)) {
			return "birch_sapling";
		}

		if (stack.is(Items.JUNGLE_SAPLING)) {
			return "jungle_sapling";
		}

		if (stack.is(Items.ACACIA_SAPLING)) {
			return "acacia_sapling";
		}

		if (stack.is(Items.CHERRY_SAPLING)) {
			return "cherry_sapling";
		}

		if (stack.is(Items.DARK_OAK_SAPLING)) {
			return "dark_oak_sapling";
		}

		if (stack.is(Items.PALE_OAK_SAPLING)) {
			return "pale_oak_sapling";
		}

		if (stack.is(Items.MANGROVE_PROPAGULE)) {
			return "mangrove_propagule";
		}

		if (stack.is(net.minecraft.tags.ItemTags.LOGS)) {
			return "logs";
		}

		if (stack.is(net.minecraft.tags.ItemTags.PLANKS)) {
			return "planks";
		}

		if (stack.is(Items.COBBLESTONE)) {
			return "cobblestone";
		}

		if (stack.is(Items.COBBLED_DEEPSLATE)) {
			return "cobblestone";
		}

		if (stack.is(LiveVillagesBlocks.MILEPOST_ITEM)) {
			return "milepost";
		}

		if (stack.is(Items.DIRT) || stack.is(Items.COARSE_DIRT) || stack.is(Items.ROOTED_DIRT)) {
			return "dirt";
		}

		if (stack.is(Items.SAND) || stack.is(Items.RED_SAND)) {
			return "sand";
		}

		if (stack.is(Items.GLASS)) {
			return "glass";
		}

		if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL)) {
			return "coal";
		}

		if (stack.is(Items.REDSTONE)) {
			return "redstone";
		}

		if (stack.is(Items.LAPIS_LAZULI)) {
			return "lapis";
		}

		if (stack.is(Items.DIAMOND)) {
			return "diamond";
		}

		if (stack.is(Items.EMERALD)) {
			return "emerald";
		}

		if (stack.is(Items.TORCH)) {
			return "torch";
		}

		if (stack.is(Items.COPPER_TORCH)) {
			return "copper_torch";
		}

		if (stack.is(Items.LANTERN)) {
			return "lantern";
		}

		if (stack.is(Items.IRON_INGOT)) {
			return "iron_ingot";
		}

		if (stack.is(Items.RAW_IRON)) {
			return "raw_iron";
		}

		if (stack.is(Items.RAW_COPPER)) {
			return "raw_copper";
		}

		if (stack.is(Items.RAW_GOLD)) {
			return "raw_gold";
		}

		if (stack.is(Items.COPPER_INGOT)) {
			return "copper_ingot";
		}

		return null;
	}

	public static boolean isSeedlingGoods(String goodsKey) {
		return SEEDLING_GOODS.contains(goodsKey);
	}

	public static Item seedlingItem(String goodsKey) {
		return switch (goodsKey) {
			case "oak_sapling" -> Items.OAK_SAPLING;
			case "spruce_sapling" -> Items.SPRUCE_SAPLING;
			case "birch_sapling" -> Items.BIRCH_SAPLING;
			case "jungle_sapling" -> Items.JUNGLE_SAPLING;
			case "acacia_sapling" -> Items.ACACIA_SAPLING;
			case "cherry_sapling" -> Items.CHERRY_SAPLING;
			case "dark_oak_sapling" -> Items.DARK_OAK_SAPLING;
			case "pale_oak_sapling" -> Items.PALE_OAK_SAPLING;
			case "mangrove_propagule" -> Items.MANGROVE_PROPAGULE;
			default -> Items.AIR;
		};
	}

	public static void addGoods(Map<String, Integer> goods, String goodsKey, int amount) {
		if (goodsKey == null || goodsKey.isBlank() || amount <= 0) {
			return;
		}

		goods.merge(goodsKey, amount, Integer::sum);
	}

	public static boolean consumeGoods(Map<String, Integer> goods, String goodsKey, int amount) {
		if (amount <= 0) {
			return true;
		}

		int available = goods.getOrDefault(goodsKey, 0);

		if (available < amount) {
			return false;
		}

		int remaining = available - amount;

		if (remaining > 0) {
			goods.put(goodsKey, remaining);
		} else {
			goods.remove(goodsKey);
		}

		return true;
	}
}
