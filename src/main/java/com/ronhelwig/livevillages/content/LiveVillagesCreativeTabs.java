package com.ronhelwig.livevillages.content;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import com.ronhelwig.livevillages.LiveVillages;

public final class LiveVillagesCreativeTabs {
	private static final ResourceKey<CreativeModeTab> MAIN_TAB_KEY = ResourceKey.create(
		Registries.CREATIVE_MODE_TAB,
		LiveVillages.id("main")
	);

	private LiveVillagesCreativeTabs() {
	}

	public static void register() {
		Registry.register(
			BuiltInRegistries.CREATIVE_MODE_TAB,
			MAIN_TAB_KEY,
			FabricCreativeModeTab.builder()
				.title(Component.translatable("itemGroup.live-villages.main"))
				.icon(() -> new ItemStack(LiveVillagesBlocks.TRADE_BOARD_ITEM))
				.displayItems((context, output) -> {
					output.accept(LiveVillagesBlocks.TRADE_BOARD_ITEM);
					output.accept(LiveVillagesBlocks.CARPENTER_BENCH_ITEM);
					output.accept(LiveVillagesBlocks.SCRIBE_DESK_ITEM);
					output.accept(LiveVillagesBlocks.GUARD_POST_ITEM);
					output.accept(LiveVillagesBlocks.GARDENER_WORKSTATION_ITEM);
					output.accept(LiveVillagesBlocks.HONEY_SEPARATOR_ITEM);
					output.accept(LiveVillagesBlocks.SURVEYOR_TABLE_ITEM);
					output.accept(LiveVillagesBlocks.FORESTER_TABLE_ITEM);
					output.accept(LiveVillagesBlocks.MINER_WORKSTATION_ITEM);
					output.accept(LiveVillagesBlocks.BAKERS_COUNTER_ITEM);
					output.accept(LiveVillagesBlocks.GLASS_DISPLAY_CASE_ITEM);
					output.accept(LiveVillagesBlocks.COPPER_GLASS_DISPLAY_CASE_ITEM);
					output.accept(LiveVillagesBlocks.IRON_GLASS_DISPLAY_CASE_ITEM);
					output.accept(LiveVillagesBlocks.GOLD_GLASS_DISPLAY_CASE_ITEM);
					output.accept(LiveVillagesBlocks.DIAMOND_GLASS_DISPLAY_CASE_ITEM);
					output.accept(LiveVillagesBlocks.MILEPOST_ITEM);
					output.accept(LiveVillagesBlocks.PORTMASTER_ANCHOR_ITEM);
					output.accept(LiveVillagesBlocks.LIGHTHOUSE_ITEM);
					output.accept(LiveVillagesBlocks.SIMPLE_HOUSING_SHELTER_ITEM);
					output.accept(LiveVillagesBlocks.HOUSING_SHELTER_ITEM);
					output.accept(LiveVillagesBlocks.PALISADE_GATEHOUSE_ITEM);
					output.accept(LiveVillagesBlocks.COPPER_PALISADE_GATEHOUSE_ITEM);
					output.accept(LiveVillagesBlocks.PALISADE_POINT_ITEM);
					output.accept(LiveVillagesBlocks.ALTAR_ITEM);
					output.accept(LiveVillagesBlocks.PULPIT_ITEM);
					output.accept(LiveVillagesBlocks.COPPER_BELL_ITEM);
					output.accept(LiveVillagesBlocks.COPPER_STAIRS_ITEM);
					output.accept(LiveVillagesBlocks.WAXED_COPPER_STAIRS_ITEM);
					output.accept(LiveVillagesItems.SLING);
					output.accept(LiveVillagesItems.CROOKED_STAFF);
					output.accept(LiveVillagesItems.SCYTHE);
					output.accept(LiveVillagesItems.COPPERHEAD_ARROW);
					output.accept(LiveVillagesItems.IRONHEAD_ARROW);
					output.accept(LiveVillagesItems.DIAMONDHEAD_ARROW);
				})
				.build()
		);
	}
}
