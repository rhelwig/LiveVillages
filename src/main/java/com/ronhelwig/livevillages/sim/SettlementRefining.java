package com.ronhelwig.livevillages.sim;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SettlementRefining {
	private static final int SMELT_FUEL_UNITS_PER_ITEM = 2;
	private static final int COAL_FUEL_UNITS = 16;
	private static final int PLANK_FUEL_UNITS = 3;

	private SettlementRefining() {
	}

	public static void refineTowardSettlementTargets(SettlementState settlement, Map<String, Integer> stock) {
		refineTowardTarget(settlement, stock, "coal", SettlementEconomyRules.targetForGoods(settlement, "coal"));
		refineTowardTarget(settlement, stock, "iron_ingot", SettlementEconomyRules.targetForGoods(settlement, "iron_ingot"));
		refineTowardTarget(settlement, stock, "copper_ingot", SettlementEconomyRules.targetForGoods(settlement, "copper_ingot"));
		refineTowardTarget(settlement, stock, "gold_ingot", SettlementEconomyRules.targetForGoods(settlement, "gold_ingot"));
	}

	public static int effectiveGoodsCount(SettlementState settlement, String goodsKey) {
		if (!supportsRefining(goodsKey)) {
			return settlement.stock().getOrDefault(goodsKey, 0);
		}

		Map<String, Integer> workingStock = new LinkedHashMap<>(settlement.stock());
		int target = Math.max(workingStock.getOrDefault(goodsKey, 0), SettlementEconomyRules.targetForGoods(settlement, goodsKey));
		refineTowardTarget(settlement, workingStock, goodsKey, target);
		return workingStock.getOrDefault(goodsKey, 0);
	}

	public static boolean consumeRefinedMaterial(Map<String, Integer> stock, String goodsKey) {
		Map<String, Integer> workingStock = new LinkedHashMap<>(stock);

		if (SettlementGoods.consumeGoods(workingStock, goodsKey, 1)) {
			stock.clear();
			stock.putAll(workingStock);
			return true;
		}

		FuelLedger fuelLedger = new FuelLedger();
		boolean supplied = switch (goodsKey) {
			case "coal" -> consumeCharcoal(workingStock, Map.of());
			case "glass" -> smeltForImmediateUse(workingStock, "sand", Map.of(), fuelLedger);
			case "iron_ingot" -> smeltForImmediateUse(workingStock, "raw_iron", Map.of(), fuelLedger);
			case "copper_ingot" -> smeltForImmediateUse(workingStock, "raw_copper", Map.of(), fuelLedger);
			case "gold_ingot" -> smeltForImmediateUse(workingStock, "raw_gold", Map.of(), fuelLedger);
			default -> false;
		};

		if (!supplied) {
			return false;
		}

		stock.clear();
		stock.putAll(workingStock);
		return true;
	}

	public static boolean canSupplyRefinedMaterial(Map<String, Integer> stock, String goodsKey) {
		Map<String, Integer> workingStock = new LinkedHashMap<>(stock);
		return consumeRefinedMaterial(workingStock, goodsKey);
	}

	public static boolean supportsRefining(String goodsKey) {
		return goodsKey.equals("coal")
			|| goodsKey.equals("glass")
			|| goodsKey.equals("iron_ingot")
			|| goodsKey.equals("copper_ingot")
			|| goodsKey.equals("gold_ingot");
	}

	private static void refineTowardTarget(SettlementState settlement, Map<String, Integer> stock, String goodsKey, int target) {
		if (target <= 0 || stock.getOrDefault(goodsKey, 0) >= target || !supportsRefining(goodsKey)) {
			return;
		}

		Map<String, Integer> reservedGoods = reservedGoods(settlement, goodsKey);
		FuelLedger fuelLedger = new FuelLedger();

		while (stock.getOrDefault(goodsKey, 0) < target) {
			boolean refined = switch (goodsKey) {
				case "coal" -> refineCharcoalToStock(stock, reservedGoods);
				case "glass" -> smeltToStock(stock, "sand", "glass", reservedGoods, fuelLedger);
				case "iron_ingot" -> smeltToStock(stock, "raw_iron", "iron_ingot", reservedGoods, fuelLedger);
				case "copper_ingot" -> smeltToStock(stock, "raw_copper", "copper_ingot", reservedGoods, fuelLedger);
				case "gold_ingot" -> smeltToStock(stock, "raw_gold", "gold_ingot", reservedGoods, fuelLedger);
				default -> false;
			};

			if (!refined) {
				return;
			}
		}
	}

	private static Map<String, Integer> reservedGoods(SettlementState settlement, String outputKey) {
		Map<String, Integer> reservedGoods = new LinkedHashMap<>();
		int logReserve = SettlementEconomyRules.targetForGoods(settlement, "logs");
		int plankReserve = SettlementEconomyRules.targetForGoods(settlement, "planks");
		int coalReserve = SettlementEconomyRules.targetForGoods(settlement, "coal");

		if (logReserve > 0) {
			reservedGoods.put("logs", logReserve);
		}

		if (plankReserve > 0) {
			reservedGoods.put("planks", plankReserve);
		}

		if (!outputKey.equals("coal") && coalReserve > 0) {
			reservedGoods.put("coal", coalReserve);
		}

		return reservedGoods;
	}

	private static boolean refineCharcoalToStock(Map<String, Integer> stock, Map<String, Integer> reservedGoods) {
		if (!consumeCharcoal(stock, reservedGoods)) {
			return false;
		}

		SettlementGoods.addGoods(stock, "coal", 1);
		return true;
	}

	private static boolean smeltToStock(
		Map<String, Integer> stock,
		String inputKey,
		String outputKey,
		Map<String, Integer> reservedGoods,
		FuelLedger fuelLedger
	) {
		if (!smeltForImmediateUse(stock, inputKey, reservedGoods, fuelLedger)) {
			return false;
		}

		SettlementGoods.addGoods(stock, outputKey, 1);
		return true;
	}

	private static boolean smeltForImmediateUse(
		Map<String, Integer> stock,
		String inputKey,
		Map<String, Integer> reservedGoods,
		FuelLedger fuelLedger
	) {
		if (!SettlementGoods.consumeGoods(stock, inputKey, 1)) {
			return false;
		}

		if (fuelLedger.consumeSmeltFuel(stock, reservedGoods)) {
			return true;
		}

		SettlementGoods.addGoods(stock, inputKey, 1);
		return false;
	}

	private static boolean consumeCharcoal(Map<String, Integer> stock, Map<String, Integer> reservedGoods) {
		int availableLogs = stock.getOrDefault("logs", 0);
		int reservedLogs = reservedGoods.getOrDefault("logs", 0);

		if (availableLogs <= reservedLogs) {
			return false;
		}

		return SettlementGoods.consumeGoods(stock, "logs", 1);
	}

	private static boolean consumeReservedAwareFuel(
		Map<String, Integer> stock,
		Map<String, Integer> reservedGoods,
		String goodsKey,
		int fuelUnits
	) {
		int available = stock.getOrDefault(goodsKey, 0);
		int reserved = reservedGoods.getOrDefault(goodsKey, 0);

		if (available <= reserved || !SettlementGoods.consumeGoods(stock, goodsKey, 1)) {
			return false;
		}

		return true;
	}

	private static final class FuelLedger {
		private int carriedFuelUnits;

		private boolean consumeSmeltFuel(Map<String, Integer> stock, Map<String, Integer> reservedGoods) {
			while (carriedFuelUnits < SMELT_FUEL_UNITS_PER_ITEM) {
				if (!addFuel(stock, reservedGoods)) {
					return false;
				}
			}

			carriedFuelUnits -= SMELT_FUEL_UNITS_PER_ITEM;
			return true;
		}

		private boolean addFuel(Map<String, Integer> stock, Map<String, Integer> reservedGoods) {
			if (consumeReservedAwareFuel(stock, reservedGoods, "coal", COAL_FUEL_UNITS)) {
				carriedFuelUnits += COAL_FUEL_UNITS;
				return true;
			}

			if (consumeCharcoal(stock, reservedGoods)) {
				carriedFuelUnits += COAL_FUEL_UNITS;
				return true;
			}

			if (consumeReservedAwareFuel(stock, reservedGoods, "planks", PLANK_FUEL_UNITS)) {
				carriedFuelUnits += PLANK_FUEL_UNITS;
				return true;
			}

			return false;
		}
	}
}
