package com.ronhelwig.livevillages.content;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ToolMaterial;

import com.ronhelwig.livevillages.LiveVillages;
import com.ronhelwig.livevillages.item.CrookedStaffItem;
import com.ronhelwig.livevillages.item.MaterialArrowItem;
import com.ronhelwig.livevillages.item.SlingItem;

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
	public static final Item SLING = registerItem(
		"sling",
		new SlingItem(new Item.Properties().setId(itemKey("sling")).durability(128).repairable(Items.LEATHER))
	);
	public static final Item CROOKED_STAFF = registerItem(
		"crooked_staff",
		new CrookedStaffItem(new Item.Properties().setId(itemKey("crooked_staff")).sword(ToolMaterial.WOOD, 3.0F, -2.0F))
	);
	public static final Item SCYTHE = registerItem(
		"scythe",
		new Item(new Item.Properties().setId(itemKey("scythe")))
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
