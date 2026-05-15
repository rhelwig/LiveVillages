package com.ronhelwig.livevillages.content;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;

import com.ronhelwig.livevillages.LiveVillages;
import com.ronhelwig.livevillages.menu.CarpenterBenchMenu;
import com.ronhelwig.livevillages.menu.FletchingTableMenu;
import com.ronhelwig.livevillages.menu.GlassDisplayCaseMenu;
import com.ronhelwig.livevillages.menu.TradeBoardMenu;
import com.ronhelwig.livevillages.menu.TradeBoardOpenData;

public final class LiveVillagesMenus {
	public static final MenuType<CarpenterBenchMenu> CARPENTER_BENCH = Registry.register(
		BuiltInRegistries.MENU,
		LiveVillages.id("carpenter_bench"),
		new MenuType<>(CarpenterBenchMenu::new, FeatureFlags.VANILLA_SET)
	);
	public static final MenuType<FletchingTableMenu> FLETCHING_TABLE = Registry.register(
		BuiltInRegistries.MENU,
		LiveVillages.id("fletching_table"),
		new MenuType<>(FletchingTableMenu::new, FeatureFlags.VANILLA_SET)
	);
	public static final MenuType<GlassDisplayCaseMenu> GLASS_DISPLAY_CASE = Registry.register(
		BuiltInRegistries.MENU,
		LiveVillages.id("glass_display_case"),
		new MenuType<>(GlassDisplayCaseMenu::new, FeatureFlags.VANILLA_SET)
	);

	public static final MenuType<TradeBoardMenu> TRADE_BOARD = Registry.register(
		BuiltInRegistries.MENU,
		LiveVillages.id("trade_board"),
		new ExtendedMenuType<>(TradeBoardMenu::new, ByteBufCodecs.fromCodecWithRegistries(TradeBoardOpenData.CODEC))
	);

	private LiveVillagesMenus() {
	}

	public static void register() {
	}
}
