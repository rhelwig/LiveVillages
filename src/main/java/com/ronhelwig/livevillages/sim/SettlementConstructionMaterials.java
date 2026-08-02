package com.ronhelwig.livevillages.sim;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SettlementConstructionMaterials {
	private SettlementConstructionMaterials() {
	}

	public static ConstructionMaterialResult canSupplyBlock(Map<String, Integer> settlementStock, SettlementBuildBlockState block) {
		Map<String, Integer> stockCopy = new LinkedHashMap<>(settlementStock);
		return consumeForBlock(stockCopy, new LinkedHashMap<>(), block);
	}

	public static ConstructionMaterialResult consumeForBlock(
		Map<String, Integer> settlementStock,
		Map<String, Integer> villagerGoods,
		SettlementBuildBlockState block
	) {
		if (block.status() == SettlementBuildBlockStatus.PLACED || block.status() == SettlementBuildBlockStatus.PLAYER_PLACED) {
			return ConstructionMaterialResult.supplied(0);
		}

		return consumeMaterial(settlementStock, villagerGoods, block.expectedMaterialKey());
	}

	public static ConstructionMaterialResult consumeMaterial(
		Map<String, Integer> settlementStock,
		Map<String, Integer> villagerGoods,
		String materialKey
	) {
		if (materialKey == null || materialKey.isBlank()) {
			return ConstructionMaterialResult.supplied(0);
		}

		if (consumeDirect(villagerGoods, materialKey, 1) || consumeDirect(settlementStock, materialKey, 1)) {
			return ConstructionMaterialResult.supplied(0);
		}

		ConstructionMaterialResult craftedFromVillagerGoods = craftMaterial(villagerGoods, materialKey);

		if (craftedFromVillagerGoods.supplied()) {
			return craftedFromVillagerGoods;
		}

		ConstructionMaterialResult craftedFromSettlementStock = craftMaterial(settlementStock, materialKey);

		if (craftedFromSettlementStock.supplied()) {
			return craftedFromSettlementStock;
		}

		String missingKey = craftedFromSettlementStock.missingMaterialKey().isBlank()
			? craftedFromVillagerGoods.missingMaterialKey()
			: craftedFromSettlementStock.missingMaterialKey();
		return ConstructionMaterialResult.missing(missingKey.isBlank() ? materialKey : missingKey);
	}

	private static ConstructionMaterialResult craftMaterial(Map<String, Integer> goods, String materialKey) {
		return switch (materialKey) {
			case "campfire" -> craftCampfire(goods);
			case "planks" -> craftPlanks(goods);
			case "stairs" -> craftWoodPart(goods, "stairs", 6, 4);
			case "slab" -> craftWoodPart(goods, "slab", 3, 6);
			case "door" -> craftWoodPart(goods, "door", 6, 3);
			case "trapdoor" -> craftWoodPart(goods, "trapdoor", 3, 1);
			case "fence" -> craftWoodPart(goods, "fence", 2, 1);
			case "fence_gate" -> craftWoodPart(goods, "fence_gate", 4, 1);
			case "bed" -> craftBed(goods);
			case "candle" -> craftCandle(goods);
			case "glowstone" -> craftGlowstone(goods);
			case "jack_o_lantern" -> craftJackOLantern(goods);
			case "glass" -> craftGlass(goods);
			case "stone" -> craftStone(goods);
			case "smooth_stone" -> craftSmoothStone(goods);
			case "stone_bricks" -> craftStoneBricks(goods);
			case "iron_ingot" -> craftRefinedMaterial(goods, "iron_ingot");
			case "copper_ingot" -> craftRefinedMaterial(goods, "copper_ingot");
			case "copper_block" -> craftCopperBlock(goods);
			case "copper_bulb" -> craftCopperBulb(goods);
			case "end_rod" -> craftEndRod(goods);
			case "redstone_block" -> craftRedstoneBlock(goods);
			case "redstone_lamp" -> craftRedstoneLamp(goods);
			case "ladder" -> craftLadder(goods);
			case "lantern" -> craftLantern(goods);
			case "soul_lantern" -> craftSoulLantern(goods);
			case "soul_torch" -> craftSoulTorch(goods);
			case "sea_lantern" -> craftSeaLantern(goods);
			case "trade_board" -> craftWoodPart(goods, "trade_board", 6, 1);
			case "carpenter_bench" -> craftWoodPart(goods, "carpenter_bench", 4, 1);
			case "surveyor_table" -> craftWoodPart(goods, "surveyor_table", 4, 1);
			case "cartography_table" -> craftWoodPart(goods, "cartography_table", 4, 1);
			case "chest" -> craftWoodPart(goods, "chest", 8, 1);
			case "torch" -> craftTorch(goods);
			default -> ConstructionMaterialResult.missing(materialKey);
		};
	}

	private static ConstructionMaterialResult craftPlanks(Map<String, Integer> goods) {
		if (!consumeDirect(goods, "logs", 1)) {
			return ConstructionMaterialResult.missing("logs");
		}

		addGoods(goods, "planks", 3);
		return ConstructionMaterialResult.supplied(1);
	}

	private static ConstructionMaterialResult craftWoodPart(Map<String, Integer> goods, String outputKey, int plankCost, int outputCount) {
		int craftingSteps = ensurePlanks(goods, plankCost);

		if (craftingSteps < 0) {
			return ConstructionMaterialResult.missing("planks");
		}

		if (!consumeDirect(goods, "planks", plankCost)) {
			return ConstructionMaterialResult.missing("planks");
		}

		addGoods(goods, outputKey, Math.max(0, outputCount - 1));
		return ConstructionMaterialResult.supplied(craftingSteps + 1);
	}

	private static ConstructionMaterialResult craftBed(Map<String, Integer> goods) {
		Map<String, Integer> workingGoods = new LinkedHashMap<>(goods);
		int craftingSteps = ensurePlanks(workingGoods, 3);

		if (craftingSteps < 0) {
			return ConstructionMaterialResult.missing("planks");
		}

		if (!consumeDirect(workingGoods, "wool", 3)) {
			return ConstructionMaterialResult.missing("wool");
		}

		if (!consumeDirect(workingGoods, "planks", 3)) {
			return ConstructionMaterialResult.missing("planks");
		}

		goods.clear();
		goods.putAll(workingGoods);
		return ConstructionMaterialResult.supplied(craftingSteps + 1);
	}

	private static ConstructionMaterialResult craftGlass(Map<String, Integer> goods) {
		if (!SettlementRefining.consumeRefinedMaterial(goods, "glass")) {
			return ConstructionMaterialResult.missing("glass");
		}

		return ConstructionMaterialResult.supplied(1);
	}

	private static ConstructionMaterialResult craftStone(Map<String, Integer> goods) {
		if (!consumeDirect(goods, "cobblestone", 1)) {
			return ConstructionMaterialResult.missing("cobblestone");
		}

		return ConstructionMaterialResult.supplied(1);
	}

	private static ConstructionMaterialResult craftSmoothStone(Map<String, Integer> goods) {
		if (!consumeDirect(goods, "stone", 1)) {
			ConstructionMaterialResult stoneResult = craftStone(goods);

			if (!stoneResult.supplied()) {
				return stoneResult;
			}
		}

		return ConstructionMaterialResult.supplied(1);
	}

	private static ConstructionMaterialResult craftStoneBricks(Map<String, Integer> goods) {
		if (!consumeDirect(goods, "stone", 1)) {
			ConstructionMaterialResult stoneResult = craftStone(goods);

			if (!stoneResult.supplied()) {
				return stoneResult;
			}
		}

		return ConstructionMaterialResult.supplied(1);
	}

	private static ConstructionMaterialResult craftRefinedMaterial(Map<String, Integer> goods, String goodsKey) {
		if (!SettlementRefining.consumeRefinedMaterial(goods, goodsKey)) {
			return ConstructionMaterialResult.missing(goodsKey);
		}

		return ConstructionMaterialResult.supplied(1);
	}

	private static ConstructionMaterialResult craftCopperBlock(Map<String, Integer> goods) {
		Map<String, Integer> workingGoods = new LinkedHashMap<>(goods);
		int craftingSteps = 0;

		for (int ingot = 0; ingot < 9; ingot++) {
			if (!SettlementRefining.consumeRefinedMaterial(workingGoods, "copper_ingot")) {
				return ConstructionMaterialResult.missing("copper_ingot");
			}

			craftingSteps++;
		}

		goods.clear();
		goods.putAll(workingGoods);
		return ConstructionMaterialResult.supplied(craftingSteps + 1);
	}

	private static ConstructionMaterialResult craftCopperBulb(Map<String, Integer> goods) {
		Map<String, Integer> workingGoods = new LinkedHashMap<>(goods);
		int craftingSteps = 0;

		for (int block = 0; block < 3; block++) {
			if (!consumeDirect(workingGoods, "copper_block", 1)) {
				ConstructionMaterialResult copperBlockResult = craftCopperBlock(workingGoods);

				if (!copperBlockResult.supplied()) {
					return ConstructionMaterialResult.missing(copperBlockResult.missingMaterialKey());
				}

				craftingSteps += copperBlockResult.craftingSteps();

				if (!consumeDirect(workingGoods, "copper_block", 1)) {
					return ConstructionMaterialResult.missing("copper_block");
				}
			}
		}

		if (!consumeDirect(workingGoods, "redstone", 1)) {
			return ConstructionMaterialResult.missing("redstone");
		}

		if (!consumeDirect(workingGoods, "blaze_rod", 1)) {
			return ConstructionMaterialResult.missing("blaze_rod");
		}

		addGoods(workingGoods, "copper_bulb", 3);
		goods.clear();
		goods.putAll(workingGoods);
		return ConstructionMaterialResult.supplied(craftingSteps + 1);
	}

	private static ConstructionMaterialResult craftTorch(Map<String, Integer> goods) {
		Map<String, Integer> workingGoods = new LinkedHashMap<>(goods);
		int craftingSteps = ensureSticks(workingGoods, 1);

		if (craftingSteps < 0) {
			return ConstructionMaterialResult.missing("stick");
		}

		if (!consumeDirect(workingGoods, "coal", 1)) {
			return ConstructionMaterialResult.missing("coal");
		}

		if (!consumeDirect(workingGoods, "stick", 1)) {
			return ConstructionMaterialResult.missing("stick");
		}

		addGoods(workingGoods, "torch", 3);
		goods.clear();
		goods.putAll(workingGoods);
		return ConstructionMaterialResult.supplied(craftingSteps + 1);
	}

	private static ConstructionMaterialResult craftCandle(Map<String, Integer> goods) {
		if (!consumeDirect(goods, "honeycomb", 1)) {
			return ConstructionMaterialResult.missing("honeycomb");
		}

		if (!consumeDirect(goods, "string", 1)) {
			addGoods(goods, "honeycomb", 1);
			return ConstructionMaterialResult.missing("string");
		}

		return ConstructionMaterialResult.supplied(1);
	}

	private static ConstructionMaterialResult craftGlowstone(Map<String, Integer> goods) {
		if (!consumeDirect(goods, "glowstone_dust", 4)) {
			return ConstructionMaterialResult.missing("glowstone_dust");
		}

		return ConstructionMaterialResult.supplied(1);
	}

	private static ConstructionMaterialResult craftEndRod(Map<String, Integer> goods) {
		Map<String, Integer> workingGoods = new LinkedHashMap<>(goods);

		if (!consumeDirect(workingGoods, "blaze_rod", 1)) {
			return ConstructionMaterialResult.missing("blaze_rod");
		}

		if (!consumeDirect(workingGoods, "popped_chorus_fruit", 1)) {
			return ConstructionMaterialResult.missing("popped_chorus_fruit");
		}

		addGoods(workingGoods, "end_rod", 3);
		goods.clear();
		goods.putAll(workingGoods);
		return ConstructionMaterialResult.supplied(1);
	}

	private static ConstructionMaterialResult craftRedstoneBlock(Map<String, Integer> goods) {
		if (!consumeDirect(goods, "redstone", 9)) {
			return ConstructionMaterialResult.missing("redstone");
		}

		return ConstructionMaterialResult.supplied(1);
	}

	private static ConstructionMaterialResult craftRedstoneLamp(Map<String, Integer> goods) {
		Map<String, Integer> workingGoods = new LinkedHashMap<>(goods);
		int craftingSteps = 0;

		if (!consumeDirect(workingGoods, "glowstone", 1)) {
			ConstructionMaterialResult glowstoneResult = craftGlowstone(workingGoods);

			if (!glowstoneResult.supplied()) {
				return ConstructionMaterialResult.missing(glowstoneResult.missingMaterialKey());
			}

			craftingSteps += glowstoneResult.craftingSteps();
		}

		if (!consumeDirect(workingGoods, "redstone", 4)) {
			return ConstructionMaterialResult.missing("redstone");
		}

		goods.clear();
		goods.putAll(workingGoods);
		return ConstructionMaterialResult.supplied(craftingSteps + 1);
	}

	private static ConstructionMaterialResult craftJackOLantern(Map<String, Integer> goods) {
		Map<String, Integer> workingGoods = new LinkedHashMap<>(goods);
		int craftingSteps = 0;

		if (!consumeDirect(workingGoods, "carved_pumpkin", 1)) {
			return ConstructionMaterialResult.missing("carved_pumpkin");
		}

		if (!consumeDirect(workingGoods, "torch", 1)) {
			ConstructionMaterialResult torchResult = craftTorch(workingGoods);

			if (!torchResult.supplied()) {
				return ConstructionMaterialResult.missing(torchResult.missingMaterialKey());
			}

			craftingSteps += torchResult.craftingSteps();
		}

		goods.clear();
		goods.putAll(workingGoods);
		return ConstructionMaterialResult.supplied(craftingSteps + 1);
	}

	private static ConstructionMaterialResult craftLadder(Map<String, Integer> goods) {
		Map<String, Integer> workingGoods = new LinkedHashMap<>(goods);
		int craftingSteps = ensureSticks(workingGoods, 7);

		if (craftingSteps < 0) {
			return ConstructionMaterialResult.missing("stick");
		}

		if (!consumeDirect(workingGoods, "stick", 7)) {
			return ConstructionMaterialResult.missing("stick");
		}

		addGoods(workingGoods, "ladder", 2);
		goods.clear();
		goods.putAll(workingGoods);
		return ConstructionMaterialResult.supplied(craftingSteps + 1);
	}

	private static ConstructionMaterialResult craftCampfire(Map<String, Integer> goods) {
		Map<String, Integer> workingGoods = new LinkedHashMap<>(goods);
		int craftingSteps = ensureSticks(workingGoods, 3);

		if (craftingSteps < 0) {
			return ConstructionMaterialResult.missing("stick");
		}

		if (!consumeDirect(workingGoods, "logs", 3)) {
			return ConstructionMaterialResult.missing("logs");
		}

		if (!consumeDirect(workingGoods, "stick", 3)) {
			return ConstructionMaterialResult.missing("stick");
		}

		if (!consumeDirect(workingGoods, "coal", 1)) {
			return ConstructionMaterialResult.missing("coal");
		}

		goods.clear();
		goods.putAll(workingGoods);
		return ConstructionMaterialResult.supplied(craftingSteps + 1);
	}

	private static ConstructionMaterialResult craftLantern(Map<String, Integer> goods) {
		Map<String, Integer> workingGoods = new LinkedHashMap<>(goods);
		int craftingSteps = 0;

		if (!consumeDirect(workingGoods, "torch", 1)) {
			ConstructionMaterialResult torchResult = craftTorch(workingGoods);

			if (!torchResult.supplied()) {
				return ConstructionMaterialResult.missing(torchResult.missingMaterialKey());
			}

			craftingSteps += torchResult.craftingSteps();
		}

		if (!consumeLanternIron(workingGoods)) {
			return ConstructionMaterialResult.missing("iron_nugget");
		}

		goods.clear();
		goods.putAll(workingGoods);
		return ConstructionMaterialResult.supplied(craftingSteps + 1);
	}

	private static ConstructionMaterialResult craftSoulLantern(Map<String, Integer> goods) {
		Map<String, Integer> workingGoods = new LinkedHashMap<>(goods);
		int craftingSteps = 0;

		if (!consumeDirect(workingGoods, "soul_torch", 1)) {
			ConstructionMaterialResult soulTorchResult = craftSoulTorch(workingGoods);

			if (!soulTorchResult.supplied()) {
				return ConstructionMaterialResult.missing(soulTorchResult.missingMaterialKey());
			}

			craftingSteps += soulTorchResult.craftingSteps();
		}

		if (!consumeLanternIron(workingGoods)) {
			return ConstructionMaterialResult.missing("iron_nugget");
		}

		goods.clear();
		goods.putAll(workingGoods);
		return ConstructionMaterialResult.supplied(craftingSteps + 1);
	}

	private static boolean consumeLanternIron(Map<String, Integer> goods) {
		if (consumeDirect(goods, "iron_nugget", 8)) {
			return true;
		}

		if (!SettlementRefining.consumeRefinedMaterial(goods, "iron_ingot")) {
			return false;
		}

		addGoods(goods, "iron_nugget", 1);
		return true;
	}

	private static ConstructionMaterialResult craftSoulTorch(Map<String, Integer> goods) {
		Map<String, Integer> workingGoods = new LinkedHashMap<>(goods);
		int craftingSteps = ensureSticks(workingGoods, 1);

		if (craftingSteps < 0) {
			return ConstructionMaterialResult.missing("stick");
		}

		if (!consumeDirect(workingGoods, "coal", 1)) {
			return ConstructionMaterialResult.missing("coal");
		}

		if (!consumeDirect(workingGoods, "stick", 1)) {
			return ConstructionMaterialResult.missing("stick");
		}

		if (!consumeDirect(workingGoods, "soul_soil", 1) && !consumeDirect(workingGoods, "soul_sand", 1)) {
			return ConstructionMaterialResult.missing("soul_soil");
		}

		addGoods(workingGoods, "soul_torch", 3);
		goods.clear();
		goods.putAll(workingGoods);
		return ConstructionMaterialResult.supplied(craftingSteps + 1);
	}

	private static ConstructionMaterialResult craftSeaLantern(Map<String, Integer> goods) {
		Map<String, Integer> workingGoods = new LinkedHashMap<>(goods);

		if (!consumeDirect(workingGoods, "prismarine_shard", 4)) {
			return ConstructionMaterialResult.missing("prismarine_shard");
		}

		if (!consumeDirect(workingGoods, "prismarine_crystals", 5)) {
			return ConstructionMaterialResult.missing("prismarine_crystals");
		}

		goods.clear();
		goods.putAll(workingGoods);
		return ConstructionMaterialResult.supplied(1);
	}

	private static int ensurePlanks(Map<String, Integer> goods, int requiredPlanks) {
		int availablePlanks = goods.getOrDefault("planks", 0);

		if (availablePlanks >= requiredPlanks) {
			return 0;
		}

		int missingPlanks = requiredPlanks - availablePlanks;
		int logsNeeded = (missingPlanks + 3) / 4;

		if (!consumeDirect(goods, "logs", logsNeeded)) {
			return -1;
		}

		addGoods(goods, "planks", logsNeeded * 4);
		return logsNeeded;
	}

	private static int ensureSticks(Map<String, Integer> goods, int requiredSticks) {
		int availableSticks = goods.getOrDefault("stick", 0);

		if (availableSticks >= requiredSticks) {
			return 0;
		}

		int missingSticks = requiredSticks - availableSticks;
		int planksNeeded = (missingSticks + 3) / 4 * 2;
		int craftingSteps = ensurePlanks(goods, planksNeeded);

		if (craftingSteps < 0) {
			return -1;
		}

		if (!consumeDirect(goods, "planks", planksNeeded)) {
			return -1;
		}

		addGoods(goods, "stick", (planksNeeded / 2) * 4);
		return craftingSteps + 1;
	}

	private static boolean consumeDirect(Map<String, Integer> goods, String goodsKey, int amount) {
		if (amount <= 0) {
			return true;
		}

		int available = goods.getOrDefault(goodsKey, 0);

		if (available < amount) {
			return false;
		}

		int remaining = available - amount;

		if (remaining > 0) {
			goods.put(goodsKey, remaining);
		} else {
			goods.remove(goodsKey);
		}

		return true;
	}

	private static void addGoods(Map<String, Integer> goods, String goodsKey, int amount) {
		if (amount <= 0) {
			return;
		}

		goods.merge(goodsKey, amount, Integer::sum);
	}

	public record ConstructionMaterialResult(boolean supplied, String missingMaterialKey, int craftingSteps) {
		public static ConstructionMaterialResult supplied(int craftingSteps) {
			return new ConstructionMaterialResult(true, "", Math.max(0, craftingSteps));
		}

		public static ConstructionMaterialResult missing(String missingMaterialKey) {
			return new ConstructionMaterialResult(false, missingMaterialKey == null ? "" : missingMaterialKey, 0);
		}
	}
}
