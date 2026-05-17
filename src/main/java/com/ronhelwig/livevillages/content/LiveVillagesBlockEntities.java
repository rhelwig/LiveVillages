package com.ronhelwig.livevillages.content;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;

import com.ronhelwig.livevillages.LiveVillages;
import com.ronhelwig.livevillages.block.entity.BakersCounterBlockEntity;
import com.ronhelwig.livevillages.block.entity.GlassDisplayCaseBlockEntity;
import com.ronhelwig.livevillages.block.entity.MilepostBlockEntity;
import com.ronhelwig.livevillages.block.entity.TradeBoardBlockEntity;

public final class LiveVillagesBlockEntities {
	public static final BlockEntityType<TradeBoardBlockEntity> TRADE_BOARD = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		LiveVillages.id("trade_board"),
		FabricBlockEntityTypeBuilder.create(TradeBoardBlockEntity::new, LiveVillagesBlocks.TRADE_BOARD).build()
	);
	public static final BlockEntityType<MilepostBlockEntity> MILEPOST = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		LiveVillages.id("milepost"),
		FabricBlockEntityTypeBuilder.create(MilepostBlockEntity::new, LiveVillagesBlocks.MILEPOST).build()
	);
	public static final BlockEntityType<GlassDisplayCaseBlockEntity> GLASS_DISPLAY_CASE = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		LiveVillages.id("glass_display_case"),
		FabricBlockEntityTypeBuilder.create(
			GlassDisplayCaseBlockEntity::new,
			LiveVillagesBlocks.GLASS_DISPLAY_CASE,
			LiveVillagesBlocks.COPPER_GLASS_DISPLAY_CASE,
			LiveVillagesBlocks.IRON_GLASS_DISPLAY_CASE,
			LiveVillagesBlocks.GOLD_GLASS_DISPLAY_CASE,
			LiveVillagesBlocks.DIAMOND_GLASS_DISPLAY_CASE
		).build()
	);
	public static final BlockEntityType<BakersCounterBlockEntity> BAKERS_COUNTER = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		LiveVillages.id("bakers_counter"),
		FabricBlockEntityTypeBuilder.create(BakersCounterBlockEntity::new, LiveVillagesBlocks.BAKERS_COUNTER).build()
	);

	private LiveVillagesBlockEntities() {
	}

	public static void register() {
	}
}
