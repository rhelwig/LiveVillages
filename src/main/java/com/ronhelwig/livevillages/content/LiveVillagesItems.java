package com.ronhelwig.livevillages.content;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

import com.ronhelwig.livevillages.LiveVillages;
import com.ronhelwig.livevillages.item.MaterialArrowItem;

public final class LiveVillagesItems {
	public static final MaterialArrowItem COPPERHEAD_ARROW = registerItem(
		"copperhead_arrow",
		new MaterialArrowItem("copper", new Item.Properties().setId(itemKey("copperhead_arrow")))
	);
	public static final MaterialArrowItem IRONHEAD_ARROW = registerItem(
		"ironhead_arrow",
		new MaterialArrowItem("iron", new Item.Properties().setId(itemKey("ironhead_arrow")))
	);
	public static final MaterialArrowItem DIAMONDHEAD_ARROW = registerItem(
		"diamondhead_arrow",
		new MaterialArrowItem("diamond", new Item.Properties().setId(itemKey("diamondhead_arrow")))
	);

	private LiveVillagesItems() {
	}

	public static void register() {
	}

	private static <T extends Item> T registerItem(String path, T item) {
		return Registry.register(BuiltInRegistries.ITEM, LiveVillages.id(path), item);
	}

	private static ResourceKey<Item> itemKey(String path) {
		return ResourceKey.create(Registries.ITEM, id(path));
	}

	private static Identifier id(String path) {
		return LiveVillages.id(path);
	}
}
