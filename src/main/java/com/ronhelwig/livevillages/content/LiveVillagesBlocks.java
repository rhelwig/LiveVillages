package com.ronhelwig.livevillages.content;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import com.ronhelwig.livevillages.LiveVillages;
import com.ronhelwig.livevillages.block.CarpenterBenchBlock;
import com.ronhelwig.livevillages.block.ForesterTableBlock;
import com.ronhelwig.livevillages.block.MilepostBlock;
import com.ronhelwig.livevillages.block.PortmasterAnchorBlock;
import com.ronhelwig.livevillages.block.SurveyorTableBlock;
import com.ronhelwig.livevillages.block.TradeBoardBlock;

public final class LiveVillagesBlocks {
	public static final TradeBoardBlock TRADE_BOARD = registerBlock(
		"trade_board",
		new TradeBoardBlock(
			BlockBehaviour.Properties.of()
				.setId(blockKey("trade_board"))
				.noCollision()
				.noOcclusion()
				.strength(2.5F)
				.sound(SoundType.WOOD)
		)
	);
	public static final Item TRADE_BOARD_ITEM = registerItem(
		"trade_board",
		new BlockItem(TRADE_BOARD, new Item.Properties().setId(itemKey("trade_board")).useBlockDescriptionPrefix())
	);
	public static final CarpenterBenchBlock CARPENTER_BENCH = registerBlock(
		"carpenter_bench",
		new CarpenterBenchBlock(
			BlockBehaviour.Properties.ofFullCopy(Blocks.CRAFTING_TABLE)
				.setId(blockKey("carpenter_bench"))
				.strength(2.5F)
				.sound(SoundType.WOOD)
		)
	);
	public static final Item CARPENTER_BENCH_ITEM = registerItem(
		"carpenter_bench",
		new BlockItem(CARPENTER_BENCH, new Item.Properties().setId(itemKey("carpenter_bench")).useBlockDescriptionPrefix())
	);
	public static final SurveyorTableBlock SURVEYOR_TABLE = registerBlock(
		"surveyor_table",
		new SurveyorTableBlock(
			BlockBehaviour.Properties.ofFullCopy(Blocks.CRAFTING_TABLE)
				.setId(blockKey("surveyor_table"))
				.strength(2.5F)
				.sound(SoundType.WOOD)
		)
	);
	public static final Item SURVEYOR_TABLE_ITEM = registerItem(
		"surveyor_table",
		new BlockItem(SURVEYOR_TABLE, new Item.Properties().setId(itemKey("surveyor_table")).useBlockDescriptionPrefix())
	);
	public static final MilepostBlock MILEPOST = registerBlock(
		"milepost",
		new MilepostBlock(
			BlockBehaviour.Properties.of()
				.setId(blockKey("milepost"))
				.noOcclusion()
				.strength(2.5F)
				.sound(SoundType.STONE)
		)
	);
	public static final Item MILEPOST_ITEM = registerItem(
		"milepost",
		new BlockItem(MILEPOST, new Item.Properties().setId(itemKey("milepost")).useBlockDescriptionPrefix())
	);
	public static final ForesterTableBlock FORESTER_TABLE = registerBlock(
		"forester_table",
		new ForesterTableBlock(
			BlockBehaviour.Properties.ofFullCopy(Blocks.CRAFTING_TABLE)
				.setId(blockKey("forester_table"))
				.strength(2.5F)
				.sound(SoundType.WOOD)
		)
	);
	public static final Item FORESTER_TABLE_ITEM = registerItem(
		"forester_table",
		new BlockItem(FORESTER_TABLE, new Item.Properties().setId(itemKey("forester_table")).useBlockDescriptionPrefix())
	);
	public static final PortmasterAnchorBlock PORTMASTER_ANCHOR = registerBlock(
		"portmaster_anchor",
		new PortmasterAnchorBlock(
			BlockBehaviour.Properties.ofFullCopy(Blocks.STRIPPED_OAK_WOOD)
				.setId(blockKey("portmaster_anchor"))
				.noOcclusion()
				.strength(2.5F)
				.sound(SoundType.WOOD)
		)
	);
	public static final Item PORTMASTER_ANCHOR_ITEM = registerItem(
		"portmaster_anchor",
		new BlockItem(PORTMASTER_ANCHOR, new Item.Properties().setId(itemKey("portmaster_anchor")).useBlockDescriptionPrefix())
	);

	private LiveVillagesBlocks() {
	}

	public static void register() {
	}

	private static <T extends Block> T registerBlock(String path, T block) {
		return Registry.register(BuiltInRegistries.BLOCK, LiveVillages.id(path), block);
	}

	private static <T extends Item> T registerItem(String path, T item) {
		return Registry.register(BuiltInRegistries.ITEM, LiveVillages.id(path), item);
	}

	private static ResourceKey<Block> blockKey(String path) {
		return ResourceKey.create(Registries.BLOCK, id(path));
	}

	private static ResourceKey<Item> itemKey(String path) {
		return ResourceKey.create(Registries.ITEM, id(path));
	}

	private static Identifier id(String path) {
		return LiveVillages.id(path);
	}
}
