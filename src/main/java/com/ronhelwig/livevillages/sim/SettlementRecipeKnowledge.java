package com.ronhelwig.livevillages.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;

public final class SettlementRecipeKnowledge {
	private static final List<String> STARTER_CUSTOM_RECIPE_IDS = List.of(
		"live-villages:string_from_wool",
		"live-villages:altar",
		"live-villages:pulpit"
	);
	private static final List<String> STARTER_RECIPE_PATHS = List.of(
		"oak_boat",
		"oak_chest_boat",
		"chest",
		"crafting_table",
		"furnace",
		"bread",
		"book",
		"bookshelf",
		"paper",
		"map",
		"compass",
		"ladder",
		"torch",
		"campfire",
		"lectern",
		"cartography_table",
		"fletching_table",
		"loom",
		"white_dye",
		"white_dye_from_bone_meal",
		"white_dye_from_lily_of_the_valley",
		"orange_dye",
		"orange_dye_from_orange_tulip",
		"orange_dye_from_torchflower",
		"magenta_dye",
		"magenta_dye_from_allium",
		"magenta_dye_from_lilac",
		"light_blue_dye",
		"light_blue_dye_from_blue_orchid",
		"yellow_dye",
		"yellow_dye_from_dandelion",
		"yellow_dye_from_sunflower",
		"lime_dye",
		"pink_dye",
		"pink_dye_from_peony",
		"pink_dye_from_pink_tulip",
		"pink_dye_from_pink_petals",
		"gray_dye",
		"light_gray_dye",
		"light_gray_dye_from_azure_bluet",
		"light_gray_dye_from_oxeye_daisy",
		"light_gray_dye_from_white_tulip",
		"cyan_dye",
		"purple_dye",
		"blue_dye",
		"blue_dye_from_cornflower",
		"brown_dye",
		"red_dye",
		"red_dye_from_beetroot",
		"red_dye_from_poppy",
		"red_dye_from_rose_bush",
		"red_dye_from_tulip"
	);
	private static final List<String> TIER_TWO_RECIPE_PATHS = List.of(
		"cobblestone_stairs",
		"candle",
		"jack_o_lantern"
	);
	private static final List<String> TIER_THREE_RECIPE_PATHS = List.of(
		"glowstone",
		"sea_lantern"
	);
	private static final List<String> TIER_THREE_CUSTOM_RECIPE_IDS = List.of(
		"live-villages:copper_bell",
		"live-villages:copper_stairs",
		"live-villages:waxed_copper_stairs",
		"live-villages:waxed_copper_stairs_from_honeycomb"
	);

	private SettlementRecipeKnowledge() {
	}

	public static List<String> starterRecipeIds() {
		List<String> recipes = new ArrayList<>(STARTER_RECIPE_PATHS.stream()
			.map(path -> Identifier.withDefaultNamespace(path).toString())
			.toList());
		recipes.addAll(STARTER_CUSTOM_RECIPE_IDS);
		return List.copyOf(recipes);
	}

	public static List<String> recipeIdsForTier(int tier) {
		List<String> recipes = new ArrayList<>(starterRecipeIds());

		if (SettlementTiers.normalize(tier) >= 2) {
			TIER_TWO_RECIPE_PATHS.stream()
				.map(path -> Identifier.withDefaultNamespace(path).toString())
				.forEach(recipes::add);
		}

		if (SettlementTiers.normalize(tier) >= 3) {
			TIER_THREE_RECIPE_PATHS.stream()
				.map(path -> Identifier.withDefaultNamespace(path).toString())
				.forEach(recipes::add);
			recipes.addAll(TIER_THREE_CUSTOM_RECIPE_IDS);
		}

		return List.copyOf(recipes);
	}

	public static List<String> observedLanternRecipeIds() {
		return List.of(Identifier.withDefaultNamespace("lantern").toString());
	}

	public static Optional<ResourceKey<Recipe<?>>> recipeKey(String recipeId) {
		if (recipeId == null || recipeId.isBlank()) {
			return Optional.empty();
		}

		Identifier identifier = Identifier.tryParse(recipeId);
		return identifier == null
			? Optional.empty()
			: Optional.of(ResourceKey.create(Registries.RECIPE, identifier));
	}

	public static ScribeRecipePrice scribeRecipePrice(String recipeId, String outputItemId) {
		String path = pathForRecipeId(recipeId);

		if (path.equals("lectern") || path.equals("cartography_table") || path.equals("fletching_table") || path.equals("loom")) {
			return new ScribeRecipePrice(BuiltInRegistries.ITEM.getKey(Items.BOOK).toString(), 2);
		}

		if (path.equals("copper_bulb") || path.equals("redstone_lamp") || path.equals("sea_lantern") || path.equals("end_rod")) {
			return new ScribeRecipePrice(BuiltInRegistries.ITEM.getKey(Items.BOOK).toString(), 3);
		}

		if (path.equals("map") || path.equals("compass") || path.equals("bookshelf") || path.equals("lantern") || path.equals("soul_lantern") || outputItemId.endsWith(":bookshelf")) {
			return new ScribeRecipePrice(BuiltInRegistries.ITEM.getKey(Items.BOOK).toString(), 1);
		}

		if (path.equals("oak_chest_boat") || path.equals("furnace") || path.equals("book") || path.equals("campfire") || path.equals("candle") || path.equals("jack_o_lantern") || path.equals("glowstone")) {
			return new ScribeRecipePrice(BuiltInRegistries.ITEM.getKey(Items.PAPER).toString(), 2);
		}

		return new ScribeRecipePrice(BuiltInRegistries.ITEM.getKey(Items.PAPER).toString(), 1);
	}

	private static String pathForRecipeId(String recipeId) {
		Identifier identifier = Identifier.tryParse(recipeId);
		return identifier == null ? recipeId : identifier.getPath();
	}

	public record ScribeRecipePrice(String paymentItemId, int paymentCount) {
	}
}
