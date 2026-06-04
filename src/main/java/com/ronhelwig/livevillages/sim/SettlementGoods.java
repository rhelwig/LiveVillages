package com.ronhelwig.livevillages.sim;

import java.util.List;
import java.util.Map;

import com.ronhelwig.livevillages.content.LiveVillagesBlocks;
import com.ronhelwig.livevillages.content.LiveVillagesItems;
import com.ronhelwig.livevillages.menu.TradeBoardTradeRules;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

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

		if (stack.is(Items.BREAD)) {
			return "bread";
		}

		if (stack.is(Items.BAKED_POTATO)) {
			return "baked_potato";
		}

		if (stack.is(Items.COOKIE)) {
			return "cookie";
		}

		if (stack.is(Items.PUMPKIN_PIE)) {
			return "pumpkin_pie";
		}

		if (stack.is(Items.CAKE)) {
			return "cake";
		}

		if (stack.is(Items.GOLDEN_APPLE)) {
			return "golden_apple";
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

		if (stack.is(Items.PAPER)) {
			return "paper";
		}

		if (stack.is(Items.BOOK)) {
			return "book";
		}

		if (stack.is(Items.BOOKSHELF)) {
			return "bookshelf";
		}

		if (stack.is(Items.GLASS_BOTTLE)) {
			return "glass_bottle";
		}

		if (stack.is(Items.NETHER_WART)) {
			return "nether_wart";
		}

		if (stack.is(Items.GLISTERING_MELON_SLICE)) {
			return "glistering_melon_slice";
		}

		if (stack.is(Items.HONEY_BOTTLE)) {
			return "honey_bottle";
		}

		if (stack.is(Items.HONEYCOMB)) {
			return "honeycomb";
		}

		if (stack.is(Items.CANDLE)) {
			return "candle";
		}

		if (stack.is(Items.SHEARS)) {
			return "shears";
		}

		if (isHealingPotion(stack)) {
			return "healing_potion";
		}

		if (stack.is(Items.LEATHER_HELMET)) {
			return "leather_helmet";
		}

		if (stack.is(Items.LEATHER_CHESTPLATE)) {
			return "leather_chestplate";
		}

		if (stack.is(Items.LEATHER_LEGGINGS)) {
			return "leather_leggings";
		}

		if (stack.is(Items.LEATHER_BOOTS)) {
			return "leather_boots";
		}

		if (stack.is(Items.IRON_SWORD)) {
			return "iron_sword";
		}

		if (stack.is(Items.IRON_PICKAXE)) {
			return "iron_pickaxe";
		}

		if (stack.is(Items.IRON_HELMET)) {
			return "iron_helmet";
		}

		if (stack.is(Items.IRON_CHESTPLATE)) {
			return "iron_chestplate";
		}

		if (stack.is(Items.IRON_LEGGINGS)) {
			return "iron_leggings";
		}

		if (stack.is(Items.IRON_BOOTS)) {
			return "iron_boots";
		}

		if (stack.is(Items.SHIELD)) {
			return "shield";
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

		if (TradeBoardTradeRules.isEggItem(stack)) {
			return "egg";
		}

		if (stack.is(Items.MILK_BUCKET)) {
			return "milk_bucket";
		}

		if (stack.is(Items.SUGAR)) {
			return "sugar";
		}

		if (stack.is(Items.COCOA_BEANS)) {
			return "cocoa_beans";
		}

		if (stack.is(Items.PUMPKIN)) {
			return "pumpkin";
		}

		if (stack.is(net.minecraft.tags.ItemTags.FLOWERS)) {
			return "flower";
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

		if (stack.is(LiveVillagesItems.COPPERHEAD_ARROW)) {
			return "copperhead_arrow";
		}

		if (stack.is(LiveVillagesItems.IRONHEAD_ARROW)) {
			return "ironhead_arrow";
		}

		if (stack.is(LiveVillagesItems.DIAMONDHEAD_ARROW)) {
			return "diamondhead_arrow";
		}

		if (stack.is(Items.COPPER_NUGGET)) {
			return "copper_nugget";
		}

		if (stack.is(Items.IRON_NUGGET)) {
			return "iron_nugget";
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

		if (isWoodenTrapdoor(stack)) {
			return "trapdoor";
		}

		if (stack.is(Items.COBBLESTONE)) {
			return "cobblestone";
		}

		if (stack.is(Items.COBBLED_DEEPSLATE)) {
			return "cobblestone";
		}

		if (stack.is(Items.IRON_BARS)) {
			return "iron_bars";
		}

		if (stack.is(LiveVillagesBlocks.MILEPOST_ITEM)) {
			return "milepost";
		}

		if (stack.is(LiveVillagesBlocks.TRADE_BOARD_ITEM)) {
			return "trade_board";
		}

		if (stack.is(LiveVillagesBlocks.CARPENTER_BENCH_ITEM)) {
			return "carpenter_bench";
		}

		if (stack.is(LiveVillagesBlocks.BAKERS_COUNTER_ITEM)) {
			return "bakers_counter";
		}

		if (stack.is(LiveVillagesBlocks.FORESTER_TABLE_ITEM)) {
			return "forester_table";
		}

		if (stack.is(LiveVillagesBlocks.MINER_WORKSTATION_ITEM)) {
			return "miner_workstation";
		}

		if (stack.is(LiveVillagesBlocks.SURVEYOR_TABLE_ITEM)) {
			return "surveyor_table";
		}

		if (stack.is(LiveVillagesBlocks.PORTMASTER_ANCHOR_ITEM)) {
			return "portmaster_anchor";
		}

		if (stack.is(LiveVillagesBlocks.LIGHTHOUSE_ITEM)) {
			return "lighthouse";
		}

		if (stack.is(LiveVillagesBlocks.SCRIBE_DESK_ITEM)) {
			return "scribe_desk";
		}

		if (stack.is(LiveVillagesBlocks.GUARD_POST_ITEM)) {
			return "guard_post";
		}

		if (stack.is(LiveVillagesBlocks.GARDENER_WORKSTATION_ITEM)) {
			return "gardener_workstation";
		}

		if (stack.is(LiveVillagesBlocks.HONEY_SEPARATOR_ITEM)) {
			return "honey_separator";
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

		if (stack.is(Items.AMETHYST_SHARD)) {
			return "amethyst_shard";
		}

		if (stack.is(Items.AMETHYST_BLOCK)) {
			return "amethyst";
		}

		if (stack.is(Items.CALCITE)) {
			return "calcite";
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

		if (stack.is(Items.CAMPFIRE)) {
			return "campfire";
		}

		if (stack.is(Items.BEEHIVE)) {
			return "bee_hive";
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

		if (stack.is(Items.GOLD_INGOT)) {
			return "gold_ingot";
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

	private static boolean isHealingPotion(ItemStack stack) {
		if (!stack.is(Items.POTION)) {
			return false;
		}

		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		return contents != null && (contents.is(Potions.HEALING) || contents.is(Potions.STRONG_HEALING));
	}

	private static boolean isWoodenTrapdoor(ItemStack stack) {
		return stack.is(Items.OAK_TRAPDOOR)
			|| stack.is(Items.SPRUCE_TRAPDOOR)
			|| stack.is(Items.BIRCH_TRAPDOOR)
			|| stack.is(Items.JUNGLE_TRAPDOOR)
			|| stack.is(Items.ACACIA_TRAPDOOR)
			|| stack.is(Items.CHERRY_TRAPDOOR)
			|| stack.is(Items.DARK_OAK_TRAPDOOR)
			|| stack.is(Items.PALE_OAK_TRAPDOOR)
			|| stack.is(Items.MANGROVE_TRAPDOOR)
			|| stack.is(Items.BAMBOO_TRAPDOOR);
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
