package com.ronhelwig.livevillages.content;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import com.ronhelwig.livevillages.LiveVillages;
import com.ronhelwig.livevillages.block.BakersCounterBlock;
import com.ronhelwig.livevillages.block.CarpenterBenchBlock;
import com.ronhelwig.livevillages.block.ForesterTableBlock;
import com.ronhelwig.livevillages.block.GlassDisplayCaseBlock;
import com.ronhelwig.livevillages.block.HousingShelterBlock;
import com.ronhelwig.livevillages.block.LighthouseBlock;
import com.ronhelwig.livevillages.block.MilepostBlock;
import com.ronhelwig.livevillages.block.MinerWorkstationBlock;
import com.ronhelwig.livevillages.block.PortmasterAnchorBlock;
import com.ronhelwig.livevillages.block.SimpleHousingShelterBlock;
import com.ronhelwig.livevillages.block.SurveyorTableBlock;
import com.ronhelwig.livevillages.block.TradeBoardBlock;

public final class LiveVillagesBlocks {
	public static final TagKey<Block> GLASS_DISPLAY_CASES = TagKey.create(Registries.BLOCK, id("glass_display_cases"));
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
	public static final Block GLASS_DISPLAY_CASE = registerBlock(
		"glass_display_case",
		new GlassDisplayCaseBlock(
			BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS)
				.setId(blockKey("glass_display_case"))
				.sound(SoundType.GLASS)
		)
	);
	public static final Item GLASS_DISPLAY_CASE_ITEM = registerItem(
		"glass_display_case",
		new BlockItem(GLASS_DISPLAY_CASE, new Item.Properties().setId(itemKey("glass_display_case")).useBlockDescriptionPrefix())
	);
	public static final Block COPPER_GLASS_DISPLAY_CASE = registerBlock(
		"copper_glass_display_case",
		new GlassDisplayCaseBlock(
			BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS)
				.setId(blockKey("copper_glass_display_case"))
				.sound(SoundType.METAL)
		)
	);
	public static final Item COPPER_GLASS_DISPLAY_CASE_ITEM = registerItem(
		"copper_glass_display_case",
		new BlockItem(COPPER_GLASS_DISPLAY_CASE, new Item.Properties().setId(itemKey("copper_glass_display_case")).useBlockDescriptionPrefix())
	);
	public static final Block IRON_GLASS_DISPLAY_CASE = registerBlock(
		"iron_glass_display_case",
		new GlassDisplayCaseBlock(
			BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS)
				.setId(blockKey("iron_glass_display_case"))
				.sound(SoundType.METAL)
		)
	);
	public static final Item IRON_GLASS_DISPLAY_CASE_ITEM = registerItem(
		"iron_glass_display_case",
		new BlockItem(IRON_GLASS_DISPLAY_CASE, new Item.Properties().setId(itemKey("iron_glass_display_case")).useBlockDescriptionPrefix())
	);
	public static final Block GOLD_GLASS_DISPLAY_CASE = registerBlock(
		"gold_glass_display_case",
		new GlassDisplayCaseBlock(
			BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS)
				.setId(blockKey("gold_glass_display_case"))
				.sound(SoundType.METAL)
		)
	);
	public static final Item GOLD_GLASS_DISPLAY_CASE_ITEM = registerItem(
		"gold_glass_display_case",
		new BlockItem(GOLD_GLASS_DISPLAY_CASE, new Item.Properties().setId(itemKey("gold_glass_display_case")).useBlockDescriptionPrefix())
	);
	public static final Block DIAMOND_GLASS_DISPLAY_CASE = registerBlock(
		"diamond_glass_display_case",
		new GlassDisplayCaseBlock(
			BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS)
				.setId(blockKey("diamond_glass_display_case"))
				.sound(SoundType.METAL)
		)
	);
	public static final Item DIAMOND_GLASS_DISPLAY_CASE_ITEM = registerItem(
		"diamond_glass_display_case",
		new BlockItem(DIAMOND_GLASS_DISPLAY_CASE, new Item.Properties().setId(itemKey("diamond_glass_display_case")).useBlockDescriptionPrefix())
	);
	public static final BakersCounterBlock BAKERS_COUNTER = registerBlock(
		"bakers_counter",
		new BakersCounterBlock(
			BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS)
				.setId(blockKey("bakers_counter"))
				.sound(SoundType.GLASS)
		)
	);
	public static final Item BAKERS_COUNTER_ITEM = registerItem(
		"bakers_counter",
		new BlockItem(BAKERS_COUNTER, new Item.Properties().setId(itemKey("bakers_counter")).useBlockDescriptionPrefix())
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
	public static final MinerWorkstationBlock MINER_WORKSTATION = registerBlock(
		"miner_workstation",
		new MinerWorkstationBlock(
			BlockBehaviour.Properties.ofFullCopy(Blocks.STONECUTTER)
				.setId(blockKey("miner_workstation"))
				.strength(3.5F)
				.sound(SoundType.STONE)
		)
	);
	public static final Item MINER_WORKSTATION_ITEM = registerItem(
		"miner_workstation",
		new BlockItem(MINER_WORKSTATION, new Item.Properties().setId(itemKey("miner_workstation")).useBlockDescriptionPrefix())
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
	public static final LighthouseBlock LIGHTHOUSE = registerBlock(
		"lighthouse",
		new LighthouseBlock(
			BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_BRICKS)
				.setId(blockKey("lighthouse"))
				.strength(2.5F)
				.sound(SoundType.STONE)
		)
	);
	public static final Item LIGHTHOUSE_ITEM = registerItem(
		"lighthouse",
		new BlockItem(LIGHTHOUSE, new Item.Properties().setId(itemKey("lighthouse")).useBlockDescriptionPrefix())
	);
	public static final SimpleHousingShelterBlock SIMPLE_HOUSING_SHELTER = registerBlock(
		"simple_housing_shelter",
		new SimpleHousingShelterBlock(
			BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS)
				.setId(blockKey("simple_housing_shelter"))
				.strength(2.5F)
				.sound(SoundType.WOOD)
		)
	);
	public static final Item SIMPLE_HOUSING_SHELTER_ITEM = registerItem(
		"simple_housing_shelter",
		new BlockItem(SIMPLE_HOUSING_SHELTER, new Item.Properties().setId(itemKey("simple_housing_shelter")).useBlockDescriptionPrefix())
	);
	public static final HousingShelterBlock HOUSING_SHELTER = registerBlock(
		"housing_shelter",
		new HousingShelterBlock(
			BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS)
				.setId(blockKey("housing_shelter"))
				.strength(2.5F)
				.sound(SoundType.WOOD)
		)
	);
	public static final Item HOUSING_SHELTER_ITEM = registerItem(
		"housing_shelter",
		new BlockItem(HOUSING_SHELTER, new Item.Properties().setId(itemKey("housing_shelter")).useBlockDescriptionPrefix())
	);

	private LiveVillagesBlocks() {
	}

	public static void register() {
	}

	public static boolean isGlassDisplayCase(BlockState state) {
		return state.is(GLASS_DISPLAY_CASES);
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
