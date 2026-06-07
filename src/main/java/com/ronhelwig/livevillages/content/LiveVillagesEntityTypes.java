package com.ronhelwig.livevillages.content;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import com.ronhelwig.livevillages.LiveVillages;
import com.ronhelwig.livevillages.item.SlingStoneEntity;

public final class LiveVillagesEntityTypes {
	public static final EntityType<SlingStoneEntity> SLING_STONE = register(
		"sling_stone",
		EntityType.Builder.<SlingStoneEntity>of(SlingStoneEntity::new, MobCategory.MISC)
			.sized(0.25F, 0.25F)
			.clientTrackingRange(4)
			.updateInterval(10)
	);

	private LiveVillagesEntityTypes() {
	}

	public static void register() {
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String path, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, LiveVillages.id(path), builder.build(key));
	}

	private static Identifier id(String path) {
		return LiveVillages.id(path);
	}
}
