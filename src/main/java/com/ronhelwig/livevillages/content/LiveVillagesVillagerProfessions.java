package com.ronhelwig.livevillages.content;

import java.util.function.Predicate;

import com.google.common.collect.ImmutableSet;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import net.fabricmc.fabric.api.object.builder.v1.world.poi.PoiHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.trading.TradeSet;

import com.ronhelwig.livevillages.LiveVillages;

public final class LiveVillagesVillagerProfessions {
	public static final ResourceKey<PoiType> TRADEMASTER_POI = poiKey("trademaster");
	public static final ResourceKey<VillagerProfession> TRADEMASTER = professionKey("trademaster");
	public static final ResourceKey<PoiType> CARPENTER_POI = poiKey("carpenter");
	public static final ResourceKey<VillagerProfession> CARPENTER = professionKey("carpenter");
	public static final ResourceKey<PoiType> SCRIBE_POI = poiKey("scribe");
	public static final ResourceKey<VillagerProfession> SCRIBE = professionKey("scribe");
	public static final ResourceKey<PoiType> GUARD_POI = poiKey("guard");
	public static final ResourceKey<VillagerProfession> GUARD = professionKey("guard");
	public static final ResourceKey<PoiType> GARDENER_POI = poiKey("gardener");
	public static final ResourceKey<VillagerProfession> GARDENER = professionKey("gardener");
	public static final ResourceKey<PoiType> BEEKEEPER_POI = poiKey("beekeeper");
	public static final ResourceKey<VillagerProfession> BEEKEEPER = professionKey("beekeeper");
	public static final ResourceKey<PoiType> BAKER_POI = poiKey("baker");
	public static final ResourceKey<VillagerProfession> BAKER = professionKey("baker");
	public static final ResourceKey<PoiType> ROADWRIGHT_POI = poiKey("roadwright");
	public static final ResourceKey<VillagerProfession> ROADWRIGHT = professionKey("roadwright");
	public static final ResourceKey<PoiType> MILEPOST_POI = poiKey("milepost");
	public static final ResourceKey<PoiType> FORESTER_POI = poiKey("forester");
	public static final ResourceKey<VillagerProfession> FORESTER = professionKey("forester");
	public static final ResourceKey<PoiType> MINER_POI = poiKey("miner");
	public static final ResourceKey<VillagerProfession> MINER = professionKey("miner");
	public static final ResourceKey<PoiType> PORTMASTER_POI = poiKey("portmaster");
	public static final ResourceKey<VillagerProfession> PORTMASTER = professionKey("portmaster");

	private LiveVillagesVillagerProfessions() {
	}

	public static void register() {
		PoiHelper.register(TRADEMASTER_POI.identifier(), 1, 1, LiveVillagesBlocks.TRADE_BOARD);
		PoiHelper.register(CARPENTER_POI.identifier(), 1, 1, LiveVillagesBlocks.CARPENTER_BENCH);
		PoiHelper.register(SCRIBE_POI.identifier(), 1, 1, LiveVillagesBlocks.SCRIBE_DESK);
		PoiHelper.register(GUARD_POI.identifier(), 5, 1, LiveVillagesBlocks.GUARD_POST);
		PoiHelper.register(GARDENER_POI.identifier(), 2, 1, LiveVillagesBlocks.GARDENER_WORKSTATION);
		PoiHelper.register(BEEKEEPER_POI.identifier(), 1, 1, LiveVillagesBlocks.HONEY_SEPARATOR);
		PoiHelper.register(BAKER_POI.identifier(), 1, 1, LiveVillagesBlocks.BAKERS_COUNTER);
		PoiHelper.register(ROADWRIGHT_POI.identifier(), 1, 1, LiveVillagesBlocks.SURVEYOR_TABLE);
		PoiHelper.register(MILEPOST_POI.identifier(), 1, 1, LiveVillagesBlocks.MILEPOST);
		PoiHelper.register(FORESTER_POI.identifier(), 1, 1, LiveVillagesBlocks.FORESTER_TABLE);
		PoiHelper.register(MINER_POI.identifier(), 1, 1, LiveVillagesBlocks.MINER_WORKSTATION);
		PoiHelper.register(PORTMASTER_POI.identifier(), 1, 1, LiveVillagesBlocks.PORTMASTER_ANCHOR);
		registerProfession(TRADEMASTER, TRADEMASTER_POI, SoundEvents.VILLAGER_WORK_CARTOGRAPHER);
		registerProfession(CARPENTER, CARPENTER_POI, SoundEvents.VILLAGER_WORK_MASON);
		registerProfession(SCRIBE, SCRIBE_POI, SoundEvents.VILLAGER_WORK_LIBRARIAN);
		registerProfession(GUARD, GUARD_POI, SoundEvents.VILLAGER_WORK_WEAPONSMITH);
		registerProfession(GARDENER, GARDENER_POI, SoundEvents.VILLAGER_WORK_FARMER);
		registerProfession(BEEKEEPER, BEEKEEPER_POI, SoundEvents.BEEHIVE_WORK);
		registerProfession(BAKER, BAKER_POI, SoundEvents.VILLAGER_WORK_BUTCHER);
		registerProfession(ROADWRIGHT, ROADWRIGHT_POI, SoundEvents.VILLAGER_WORK_TOOLSMITH);
		registerProfession(FORESTER, FORESTER_POI, SoundEvents.VILLAGER_WORK_FLETCHER);
		registerProfession(MINER, MINER_POI, SoundEvents.VILLAGER_WORK_TOOLSMITH);
		registerProfession(PORTMASTER, PORTMASTER_POI, SoundEvents.VILLAGER_WORK_FISHERMAN);
	}

	private static VillagerProfession registerProfession(
		ResourceKey<VillagerProfession> professionKey,
		ResourceKey<PoiType> jobSiteKey,
		SoundEvent workSound
	) {
		Predicate<Holder<PoiType>> jobSitePredicate = poiType -> poiType.is(jobSiteKey);
		Int2ObjectMap<ResourceKey<TradeSet>> tradeSets = new Int2ObjectOpenHashMap<>();
		return Registry.register(
			BuiltInRegistries.VILLAGER_PROFESSION,
			professionKey,
			new VillagerProfession(
				Component.translatable(professionTranslationKey(professionKey)),
				jobSitePredicate,
				jobSitePredicate,
				ImmutableSet.of(),
				ImmutableSet.of(),
				workSound,
				tradeSets
			)
		);
	}

	private static ResourceKey<PoiType> poiKey(String path) {
		return ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, LiveVillages.id(path));
	}

	private static ResourceKey<VillagerProfession> professionKey(String path) {
		return ResourceKey.create(Registries.VILLAGER_PROFESSION, LiveVillages.id(path));
	}

	private static String professionTranslationKey(ResourceKey<VillagerProfession> professionKey) {
		return "entity.minecraft.villager.%s.%s".formatted(
			professionKey.identifier().getNamespace(),
			professionKey.identifier().getPath()
		);
	}
}
