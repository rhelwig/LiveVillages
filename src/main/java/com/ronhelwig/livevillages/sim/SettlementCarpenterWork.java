package com.ronhelwig.livevillages.sim;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class SettlementCarpenterWork {
	private static final double CARPENTRY_REACH_DISTANCE_SQUARED = 6.25D;
	private static final double CARPENTRY_WALK_SPEED = 0.75D;
	private static final long TASK_MEMORY_TICKS = 40L;
	private static final long CARPENTRY_DECIDE_INTERVAL_TICKS = 320L;
	private static final int IDLE_STAIRS_TARGET = 24;
	private static final int IDLE_SLAB_TARGET = 32;
	private static final int IDLE_PLANKS_EXTRA_TARGET = 32;
	private static final int IDLE_STICK_TARGET = 32;
	private static final Map<String, Long> ACTIVE_CARPENTRY_TASKS = new HashMap<>();

	private SettlementCarpenterWork() {
	}

	public static boolean maintainLoadedCarpentry(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Collection<SettlementBuildSite> buildSites
	) {
		List<Villager> carpenters = SettlementVillagers.nearbyCarpenters(level, settlement);

		if (carpenters.isEmpty()) {
			SettlementProfessionDiagnostics.log(level, settlement, "carpenter", "no_carpenters", "");
			return false;
		}

		String outputKey = desiredWoodOutput(settlement, stock, buildSites);

		if (outputKey == null) {
			SettlementProfessionDiagnostics.log(level, settlement, "carpenter", "no_output_needed", carpentryStockSummary(settlement, stock));
			return false;
		}

		boolean stockChanged = false;
		long tick = level.getServer().getTickCount();

		for (Villager carpenter : carpenters) {
			if (!SettlementVillagerWorkSchedule.shouldStartNewWork(level, carpenter, "carpentry", CARPENTRY_DECIDE_INTERVAL_TICKS)) {
				if (SettlementVillagerWorkSchedule.isTakingBreak(level, carpenter)) {
					carpenter.getNavigation().stop();
				}

				continue;
			}

			BlockPos workPos = SettlementVillagers.carpenterJobSite(level, carpenter)
				.orElseGet(() -> SettlementStockAccess.findStockAccessPos(level, settlement, buildSites.stream().toList()).orElse(settlement.center()));
			steerCarpenterTowardWork(level, settlement, carpenter, workPos);
			showCarpenterTool(carpenter);
			ACTIVE_CARPENTRY_TASKS.put(carpenter.getUUID().toString(), tick);

			if (!isWithinWorkReach(carpenter, workPos)) {
				SettlementProfessionDiagnostics.log(level, settlement, "carpenter", "moving_to_work", "villager=" + carpenter.getUUID() + " output=" + outputKey + " workPos=" + workPos.toShortString());
				continue;
			}

			if (craftWoodOutput(stock, outputKey)) {
				carpenter.swing(InteractionHand.MAIN_HAND);
				SettlementProfessionReports.recordConversion(
					level,
					settlement,
					SettlementRoleKeys.CARPENTER,
					carpenter,
					"logs",
					logCost(outputKey),
					outputKey,
					outputAmount(outputKey),
					"crafted " + outputAmount(outputKey) + " " + outputKey + " from logs"
				);
				stockChanged = true;
			}

			break;
		}

		return stockChanged;
	}

	private static String carpentryStockSummary(SettlementState settlement, Map<String, Integer> stock) {
		return "logs=" + stock.getOrDefault("logs", 0)
			+ " reserve=" + (SettlementEconomyRules.targetForGoods(settlement, "logs") + 8)
			+ " planks=" + stock.getOrDefault("planks", 0)
			+ " stairs=" + stock.getOrDefault("stairs", 0)
			+ " slabs=" + stock.getOrDefault("slab", 0)
			+ " sticks=" + stock.getOrDefault("stick", 0);
	}

	public static Optional<String> loadedCarpentryTaskKey(ServerLevel level, Villager villager) {
		Long tick = ACTIVE_CARPENTRY_TASKS.get(villager.getUUID().toString());

		if (tick == null || level.getServer().getTickCount() - tick > TASK_MEMORY_TICKS) {
			return Optional.empty();
		}

		return Optional.of("stocking_carpentry");
	}

	private static String desiredWoodOutput(SettlementState settlement, Map<String, Integer> stock, Collection<SettlementBuildSite> buildSites) {
		int logs = stock.getOrDefault("logs", 0);
		int logReserve = SettlementEconomyRules.targetForGoods(settlement, "logs") + 8;

		if (logs <= logReserve) {
			return null;
		}

		Map<String, Integer> demand = activeWoodDemand(buildSites);

		if (needsOutput(stock, demand, "stairs", 6)) {
			return "stairs";
		}

		if (needsOutput(stock, demand, "slab", 12)) {
			return "slab";
		}

		if (needsOutput(stock, demand, "planks", SettlementEconomyRules.targetForGoods(settlement, "planks"))) {
			return "planks";
		}

		if (logs <= logReserve + 16) {
			return null;
		}

		if (stock.getOrDefault("stairs", 0) < IDLE_STAIRS_TARGET) {
			return "stairs";
		}

		if (stock.getOrDefault("slab", 0) < IDLE_SLAB_TARGET) {
			return "slab";
		}

		if (stock.getOrDefault("planks", 0) < SettlementEconomyRules.targetForGoods(settlement, "planks") + IDLE_PLANKS_EXTRA_TARGET) {
			return "planks";
		}

		if (stock.getOrDefault("stick", 0) < IDLE_STICK_TARGET) {
			return "stick";
		}

		return null;
	}

	private static Map<String, Integer> activeWoodDemand(Collection<SettlementBuildSite> buildSites) {
		Map<String, Integer> demand = new HashMap<>();

		for (SettlementBuildSite buildSite : buildSites) {
			if (buildSite.complete()) {
				continue;
			}

			for (SettlementBuildBlockState block : buildSite.blocks()) {
				if (block.status() == SettlementBuildBlockStatus.PLACED || block.status() == SettlementBuildBlockStatus.PLAYER_PLACED) {
					continue;
				}

				if (isWoodOutput(block.expectedMaterialKey())) {
					demand.merge(block.expectedMaterialKey(), 1, Integer::sum);
				}
			}
		}

		return demand;
	}

	private static boolean needsOutput(Map<String, Integer> stock, Map<String, Integer> demand, String goodsKey, int reserveTarget) {
		int target = Math.max(reserveTarget, demand.getOrDefault(goodsKey, 0));
		return stock.getOrDefault(goodsKey, 0) < target;
	}

	private static boolean craftWoodOutput(Map<String, Integer> stock, String outputKey) {
		return switch (outputKey) {
			case "planks" -> {
				if (!SettlementGoods.consumeGoods(stock, "logs", 1)) {
					yield false;
				}

				SettlementGoods.addGoods(stock, "planks", 4);
				yield true;
			}
			case "stairs" -> {
				if (!SettlementGoods.consumeGoods(stock, "logs", 2)) {
					yield false;
				}

				SettlementGoods.addGoods(stock, "stairs", 4);
				yield true;
			}
			case "slab" -> {
				if (!SettlementGoods.consumeGoods(stock, "logs", 1)) {
					yield false;
				}

				SettlementGoods.addGoods(stock, "slab", 8);
				yield true;
			}
			case "stick" -> {
				if (!SettlementGoods.consumeGoods(stock, "logs", 1)) {
					yield false;
				}

				SettlementGoods.addGoods(stock, "stick", 8);
				yield true;
			}
			default -> false;
		};
	}

	private static int logCost(String outputKey) {
		return outputKey.equals("stairs") ? 2 : 1;
	}

	private static int outputAmount(String outputKey) {
		return switch (outputKey) {
			case "planks", "stairs" -> 4;
			case "slab", "stick" -> 8;
			default -> 0;
		};
	}

	private static boolean isWoodOutput(String goodsKey) {
		return goodsKey.equals("planks")
			|| goodsKey.equals("stairs")
			|| goodsKey.equals("slab")
			|| goodsKey.equals("stick")
			|| goodsKey.equals("fence")
			|| goodsKey.equals("fence_gate")
			|| goodsKey.equals("door");
	}

	private static void steerCarpenterTowardWork(ServerLevel level, SettlementState settlement, Villager carpenter, BlockPos workPos) {
		SettlementNavigation.moveToRoutineTarget(level, settlement, carpenter, workPos, CARPENTRY_WALK_SPEED);
	}

	private static boolean isWithinWorkReach(Villager carpenter, BlockPos workPos) {
		return carpenter.distanceToSqr(workPos.getX() + 0.5D, workPos.getY() + 0.5D, workPos.getZ() + 0.5D) <= CARPENTRY_REACH_DISTANCE_SQUARED;
	}

	private static void showCarpenterTool(Villager carpenter) {
		ItemStack held = carpenter.getMainHandItem();

		if (held.isEmpty()) {
			carpenter.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_AXE));
		}
	}
}
