package com.ronhelwig.livevillages.menu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class CarpenterBenchRecipes {
	private static final Map<Item, List<CarpenterBenchRecipe>> RECIPES_BY_INPUT = createRecipes();

	private CarpenterBenchRecipes() {
	}

	public static List<CarpenterBenchRecipe> forInput(ItemStack input) {
		if (input.isEmpty()) {
			return List.of();
		}

		return RECIPES_BY_INPUT.getOrDefault(input.getItem(), List.of());
	}

	public static boolean acceptsInput(ItemStack input) {
		return !forInput(input).isEmpty();
	}

	private static Map<Item, List<CarpenterBenchRecipe>> createRecipes() {
		Map<Item, List<CarpenterBenchRecipe>> recipes = new LinkedHashMap<>();

		addFamily(recipes, new WoodFamily(
			Items.OAK_PLANKS,
			4,
			Items.OAK_STAIRS,
			Items.OAK_SLAB,
			Items.OAK_FENCE,
			Items.OAK_FENCE_GATE,
			Items.OAK_DOOR,
			Items.OAK_TRAPDOOR,
			Items.OAK_PRESSURE_PLATE,
			Items.OAK_BUTTON,
			Items.OAK_SIGN,
			Items.OAK_SHELF,
			Items.OAK_BOAT,
			Map.of(
				Items.OAK_LOG, Items.STRIPPED_OAK_LOG,
				Items.OAK_WOOD, Items.STRIPPED_OAK_WOOD
			),
			Items.OAK_LOG,
			Items.OAK_WOOD,
			Items.STRIPPED_OAK_LOG,
			Items.STRIPPED_OAK_WOOD
		));
		addFamily(recipes, new WoodFamily(
			Items.SPRUCE_PLANKS,
			4,
			Items.SPRUCE_STAIRS,
			Items.SPRUCE_SLAB,
			Items.SPRUCE_FENCE,
			Items.SPRUCE_FENCE_GATE,
			Items.SPRUCE_DOOR,
			Items.SPRUCE_TRAPDOOR,
			Items.SPRUCE_PRESSURE_PLATE,
			Items.SPRUCE_BUTTON,
			Items.SPRUCE_SIGN,
			Items.SPRUCE_SHELF,
			Items.SPRUCE_BOAT,
			Map.of(
				Items.SPRUCE_LOG, Items.STRIPPED_SPRUCE_LOG,
				Items.SPRUCE_WOOD, Items.STRIPPED_SPRUCE_WOOD
			),
			Items.SPRUCE_LOG,
			Items.SPRUCE_WOOD,
			Items.STRIPPED_SPRUCE_LOG,
			Items.STRIPPED_SPRUCE_WOOD
		));
		addFamily(recipes, new WoodFamily(
			Items.BIRCH_PLANKS,
			4,
			Items.BIRCH_STAIRS,
			Items.BIRCH_SLAB,
			Items.BIRCH_FENCE,
			Items.BIRCH_FENCE_GATE,
			Items.BIRCH_DOOR,
			Items.BIRCH_TRAPDOOR,
			Items.BIRCH_PRESSURE_PLATE,
			Items.BIRCH_BUTTON,
			Items.BIRCH_SIGN,
			Items.BIRCH_SHELF,
			Items.BIRCH_BOAT,
			Map.of(
				Items.BIRCH_LOG, Items.STRIPPED_BIRCH_LOG,
				Items.BIRCH_WOOD, Items.STRIPPED_BIRCH_WOOD
			),
			Items.BIRCH_LOG,
			Items.BIRCH_WOOD,
			Items.STRIPPED_BIRCH_LOG,
			Items.STRIPPED_BIRCH_WOOD
		));
		addFamily(recipes, new WoodFamily(
			Items.JUNGLE_PLANKS,
			4,
			Items.JUNGLE_STAIRS,
			Items.JUNGLE_SLAB,
			Items.JUNGLE_FENCE,
			Items.JUNGLE_FENCE_GATE,
			Items.JUNGLE_DOOR,
			Items.JUNGLE_TRAPDOOR,
			Items.JUNGLE_PRESSURE_PLATE,
			Items.JUNGLE_BUTTON,
			Items.JUNGLE_SIGN,
			Items.JUNGLE_SHELF,
			Items.JUNGLE_BOAT,
			Map.of(
				Items.JUNGLE_LOG, Items.STRIPPED_JUNGLE_LOG,
				Items.JUNGLE_WOOD, Items.STRIPPED_JUNGLE_WOOD
			),
			Items.JUNGLE_LOG,
			Items.JUNGLE_WOOD,
			Items.STRIPPED_JUNGLE_LOG,
			Items.STRIPPED_JUNGLE_WOOD
		));
		addFamily(recipes, new WoodFamily(
			Items.ACACIA_PLANKS,
			4,
			Items.ACACIA_STAIRS,
			Items.ACACIA_SLAB,
			Items.ACACIA_FENCE,
			Items.ACACIA_FENCE_GATE,
			Items.ACACIA_DOOR,
			Items.ACACIA_TRAPDOOR,
			Items.ACACIA_PRESSURE_PLATE,
			Items.ACACIA_BUTTON,
			Items.ACACIA_SIGN,
			Items.ACACIA_SHELF,
			Items.ACACIA_BOAT,
			Map.of(
				Items.ACACIA_LOG, Items.STRIPPED_ACACIA_LOG,
				Items.ACACIA_WOOD, Items.STRIPPED_ACACIA_WOOD
			),
			Items.ACACIA_LOG,
			Items.ACACIA_WOOD,
			Items.STRIPPED_ACACIA_LOG,
			Items.STRIPPED_ACACIA_WOOD
		));
		addFamily(recipes, new WoodFamily(
			Items.CHERRY_PLANKS,
			4,
			Items.CHERRY_STAIRS,
			Items.CHERRY_SLAB,
			Items.CHERRY_FENCE,
			Items.CHERRY_FENCE_GATE,
			Items.CHERRY_DOOR,
			Items.CHERRY_TRAPDOOR,
			Items.CHERRY_PRESSURE_PLATE,
			Items.CHERRY_BUTTON,
			Items.CHERRY_SIGN,
			Items.CHERRY_SHELF,
			Items.CHERRY_BOAT,
			Map.of(
				Items.CHERRY_LOG, Items.STRIPPED_CHERRY_LOG,
				Items.CHERRY_WOOD, Items.STRIPPED_CHERRY_WOOD
			),
			Items.CHERRY_LOG,
			Items.CHERRY_WOOD,
			Items.STRIPPED_CHERRY_LOG,
			Items.STRIPPED_CHERRY_WOOD
		));
		addFamily(recipes, new WoodFamily(
			Items.DARK_OAK_PLANKS,
			4,
			Items.DARK_OAK_STAIRS,
			Items.DARK_OAK_SLAB,
			Items.DARK_OAK_FENCE,
			Items.DARK_OAK_FENCE_GATE,
			Items.DARK_OAK_DOOR,
			Items.DARK_OAK_TRAPDOOR,
			Items.DARK_OAK_PRESSURE_PLATE,
			Items.DARK_OAK_BUTTON,
			Items.DARK_OAK_SIGN,
			Items.DARK_OAK_SHELF,
			Items.DARK_OAK_BOAT,
			Map.of(
				Items.DARK_OAK_LOG, Items.STRIPPED_DARK_OAK_LOG,
				Items.DARK_OAK_WOOD, Items.STRIPPED_DARK_OAK_WOOD
			),
			Items.DARK_OAK_LOG,
			Items.DARK_OAK_WOOD,
			Items.STRIPPED_DARK_OAK_LOG,
			Items.STRIPPED_DARK_OAK_WOOD
		));
		addFamily(recipes, new WoodFamily(
			Items.PALE_OAK_PLANKS,
			4,
			Items.PALE_OAK_STAIRS,
			Items.PALE_OAK_SLAB,
			Items.PALE_OAK_FENCE,
			Items.PALE_OAK_FENCE_GATE,
			Items.PALE_OAK_DOOR,
			Items.PALE_OAK_TRAPDOOR,
			Items.PALE_OAK_PRESSURE_PLATE,
			Items.PALE_OAK_BUTTON,
			Items.PALE_OAK_SIGN,
			Items.PALE_OAK_SHELF,
			Items.PALE_OAK_BOAT,
			Map.of(
				Items.PALE_OAK_LOG, Items.STRIPPED_PALE_OAK_LOG,
				Items.PALE_OAK_WOOD, Items.STRIPPED_PALE_OAK_WOOD
			),
			Items.PALE_OAK_LOG,
			Items.PALE_OAK_WOOD,
			Items.STRIPPED_PALE_OAK_LOG,
			Items.STRIPPED_PALE_OAK_WOOD
		));
		addFamily(recipes, new WoodFamily(
			Items.MANGROVE_PLANKS,
			4,
			Items.MANGROVE_STAIRS,
			Items.MANGROVE_SLAB,
			Items.MANGROVE_FENCE,
			Items.MANGROVE_FENCE_GATE,
			Items.MANGROVE_DOOR,
			Items.MANGROVE_TRAPDOOR,
			Items.MANGROVE_PRESSURE_PLATE,
			Items.MANGROVE_BUTTON,
			Items.MANGROVE_SIGN,
			Items.MANGROVE_SHELF,
			Items.MANGROVE_BOAT,
			Map.of(
				Items.MANGROVE_LOG, Items.STRIPPED_MANGROVE_LOG,
				Items.MANGROVE_WOOD, Items.STRIPPED_MANGROVE_WOOD
			),
			Items.MANGROVE_LOG,
			Items.MANGROVE_WOOD,
			Items.STRIPPED_MANGROVE_LOG,
			Items.STRIPPED_MANGROVE_WOOD
		));
		addFamily(recipes, new WoodFamily(
			Items.BAMBOO_PLANKS,
			2,
			Items.BAMBOO_STAIRS,
			Items.BAMBOO_SLAB,
			Items.BAMBOO_FENCE,
			Items.BAMBOO_FENCE_GATE,
			Items.BAMBOO_DOOR,
			Items.BAMBOO_TRAPDOOR,
			Items.BAMBOO_PRESSURE_PLATE,
			Items.BAMBOO_BUTTON,
			Items.BAMBOO_SIGN,
			Items.BAMBOO_SHELF,
			Items.BAMBOO_RAFT,
			Map.of(
				Items.BAMBOO_BLOCK, Items.STRIPPED_BAMBOO_BLOCK
			),
			Items.BAMBOO_BLOCK,
			Items.STRIPPED_BAMBOO_BLOCK
		));
		addFamily(recipes, new WoodFamily(
			Items.CRIMSON_PLANKS,
			4,
			Items.CRIMSON_STAIRS,
			Items.CRIMSON_SLAB,
			Items.CRIMSON_FENCE,
			Items.CRIMSON_FENCE_GATE,
			Items.CRIMSON_DOOR,
			Items.CRIMSON_TRAPDOOR,
			Items.CRIMSON_PRESSURE_PLATE,
			Items.CRIMSON_BUTTON,
			Items.CRIMSON_SIGN,
			Items.CRIMSON_SHELF,
			null,
			Map.of(
				Items.CRIMSON_STEM, Items.STRIPPED_CRIMSON_STEM,
				Items.CRIMSON_HYPHAE, Items.STRIPPED_CRIMSON_HYPHAE
			),
			Items.CRIMSON_STEM,
			Items.CRIMSON_HYPHAE,
			Items.STRIPPED_CRIMSON_STEM,
			Items.STRIPPED_CRIMSON_HYPHAE
		));
		addFamily(recipes, new WoodFamily(
			Items.WARPED_PLANKS,
			4,
			Items.WARPED_STAIRS,
			Items.WARPED_SLAB,
			Items.WARPED_FENCE,
			Items.WARPED_FENCE_GATE,
			Items.WARPED_DOOR,
			Items.WARPED_TRAPDOOR,
			Items.WARPED_PRESSURE_PLATE,
			Items.WARPED_BUTTON,
			Items.WARPED_SIGN,
			Items.WARPED_SHELF,
			null,
			Map.of(
				Items.WARPED_STEM, Items.STRIPPED_WARPED_STEM,
				Items.WARPED_HYPHAE, Items.STRIPPED_WARPED_HYPHAE
			),
			Items.WARPED_STEM,
			Items.WARPED_HYPHAE,
			Items.STRIPPED_WARPED_STEM,
			Items.STRIPPED_WARPED_HYPHAE
		));

		addBambooMosaicRecipes(recipes);

		recipes.replaceAll((input, inputRecipes) -> List.copyOf(inputRecipes));
		return Collections.unmodifiableMap(recipes);
	}

	private static void addFamily(Map<Item, List<CarpenterBenchRecipe>> recipes, WoodFamily family) {
		for (Item input : family.logInputs()) {
			add(recipes, input, family.planks(), family.plankOutputCount());
			addLogLikeOutputs(recipes, input, family);
		}

		addPlankOutputs(recipes, family.planks(), family);
	}

	private static void addLogLikeOutputs(Map<Item, List<CarpenterBenchRecipe>> recipes, Item input, WoodFamily family) {
		int plankCount = family.plankOutputCount();
		addOptional(recipes, input, family.strippedOutputs().get(input), 1);
		add(recipes, input, family.stairs(), plankCount);
		add(recipes, input, family.slab(), plankCount * 2);
		add(recipes, input, family.fence(), 3);
		add(recipes, input, family.fenceGate(), 1);
		add(recipes, input, family.door(), 3);
		add(recipes, input, family.trapdoor(), 2);
		add(recipes, input, family.pressurePlate(), Math.max(1, plankCount / 2));
		add(recipes, input, family.button(), plankCount);
		add(recipes, input, family.sign(), 3);
		add(recipes, input, family.shelf(), 1);
		addOptional(recipes, input, family.boat(), 1);
		add(recipes, input, Items.CHEST, 1);
		add(recipes, input, Items.BARREL, 1);
		add(recipes, input, Items.CRAFTING_TABLE, 1);
		add(recipes, input, Items.COMPOSTER, 1);
		add(recipes, input, Items.LADDER, plankCount);
		add(recipes, input, Items.STICK, plankCount * 2);
	}

	private static void addPlankOutputs(Map<Item, List<CarpenterBenchRecipe>> recipes, Item input, WoodFamily family) {
		add(recipes, input, family.stairs(), 1);
		add(recipes, input, family.slab(), 2);
		add(recipes, input, family.button(), 1);
		add(recipes, input, Items.STICK, 2);
	}

	private static void addBambooMosaicRecipes(Map<Item, List<CarpenterBenchRecipe>> recipes) {
		add(recipes, Items.BAMBOO_BLOCK, Items.BAMBOO_MOSAIC, 1);
		add(recipes, Items.STRIPPED_BAMBOO_BLOCK, Items.BAMBOO_MOSAIC, 1);
		add(recipes, Items.BAMBOO_PLANKS, Items.BAMBOO_MOSAIC, 1);
		add(recipes, Items.BAMBOO_MOSAIC, Items.BAMBOO_MOSAIC_STAIRS, 1);
		add(recipes, Items.BAMBOO_MOSAIC, Items.BAMBOO_MOSAIC_SLAB, 2);
	}

	private static void addOptional(Map<Item, List<CarpenterBenchRecipe>> recipes, Item input, Item output, int count) {
		if (output != null) {
			add(recipes, input, output, count);
		}
	}

	private static void add(Map<Item, List<CarpenterBenchRecipe>> recipes, Item input, Item output, int count) {
		recipes.computeIfAbsent(input, ignored -> new ArrayList<>()).add(new CarpenterBenchRecipe(input, output, count));
	}

	private record WoodFamily(
		Item planks,
		int plankOutputCount,
		Item stairs,
		Item slab,
		Item fence,
		Item fenceGate,
		Item door,
		Item trapdoor,
		Item pressurePlate,
		Item button,
		Item sign,
		Item shelf,
		Item boat,
		Map<Item, Item> strippedOutputs,
		Item... logInputs
	) {
	}
}
