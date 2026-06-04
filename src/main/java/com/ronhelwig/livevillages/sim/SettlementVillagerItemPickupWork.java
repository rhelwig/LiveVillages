package com.ronhelwig.livevillages.sim;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.ronhelwig.livevillages.LiveVillages;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

public final class SettlementVillagerItemPickupWork {
	private static final double PICKUP_SCAN_RADIUS_BLOCKS = 2.25D;
	private static final double DEPOSIT_REACH_DISTANCE_SQUARED = 6.25D;
	private static final double DEPOSIT_WALK_SPEED = 0.75D;
	private static final int MAX_CARRIED_ITEMS = 64;
	private static final long DEPOSIT_START_TICK = 8_000L;
	private static final long GATHERING_START_TICK = 9_000L;
	private static final long ITEM_PICKUP_DECIDE_INTERVAL_TICKS = 320L;
	private static final Map<String, CarriedGoods> CARRIED_GOODS = new HashMap<>();

	private SettlementVillagerItemPickupWork() {
	}

	public static boolean maintainLoadedItemCollection(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Collection<SettlementBuildSite> buildSites,
		Set<String> excludedVillagerIds
	) {
		long methodStart = System.nanoTime();
		
		List<Villager> villagers = SettlementVillagers.nearbyAdultVillagers(
			level,
			settlement,
			SettlementVillagers.settlementRadiusBlocks(settlement) + 64
		);

		if (villagers.isEmpty()) {
			return false;
		}

		long entityScanTime = System.nanoTime() - methodStart;
		if (entityScanTime > 5_000_000) { // >5ms
			LiveVillages.LOGGER.warn("ItemPickup: entity scan took {} ms for {} villagers", Math.round(entityScanTime / 1_000_000.0D), villagers.size());
		}

		boolean stockChanged = false;
		Optional<BlockPos> stockAccessPos = SettlementStockAccess.findStockAccessPos(level, settlement, List.copyOf(buildSites));
		boolean depositTime = isDepositTime(level);

		long workStart = System.nanoTime();
		for (Villager villager : villagers) {
			String villagerId = villager.getUUID().toString();

			if (excludedVillagerIds.contains(villagerId)) {
				continue;
			}

			CarriedGoods carriedGoods = CARRIED_GOODS.get(villagerId);

			if (!depositTime && !SettlementVillagerWorkSchedule.shouldStartNewWork(level, villager, "item_pickup", ITEM_PICKUP_DECIDE_INTERVAL_TICKS)) {
				if (SettlementVillagerWorkSchedule.isTakingBreak(level, villager)) {
					villager.getNavigation().stop();
				}

				continue;
			}

			if (depositTime && carriedGoods != null && !carriedGoods.goods().isEmpty()) {
				if (stockAccessPos.isEmpty()) {
					depositIntoStock(level, settlement, villager, stock, carriedGoods);
					CARRIED_GOODS.remove(villagerId);
					stockChanged = true;
					continue;
				}

				BlockPos depositPos = stockAccessPos.get();
				steerVillagerTowardDeposit(level, settlement, villager, depositPos);

				if (isWithinDepositReach(villager, depositPos)) {
					depositIntoStock(level, settlement, villager, stock, carriedGoods);
					CARRIED_GOODS.remove(villagerId);
					stockChanged = true;
				}

				continue;
			}

			if (!depositTime) {
				collectNearbyDroppedGoods(level, settlement, villager, carriedGoods);
			}
		}

		long totalTime = System.nanoTime() - methodStart;
		if (totalTime > 10_000_000) { // >10ms
			LiveVillages.LOGGER.warn("ItemPickup: total work took {} ms for settlement {}", Math.round(totalTime / 1_000_000.0D), settlement.id());
		}

		return stockChanged;
	}

	public static Optional<String> villagerTaskKey(ServerLevel level, Villager villager) {
		CarriedGoods carriedGoods = CARRIED_GOODS.get(villager.getUUID().toString());

		if (carriedGoods == null || carriedGoods.goods().isEmpty()) {
			return Optional.empty();
		}

		return Optional.of(isDepositTime(level) ? "depositing_into_trading_post" : "carrying_collected_items");
	}

	static boolean carryItemEntity(ServerLevel level, Villager villager, ItemEntity itemEntity) {
		if (itemEntity == null || itemEntity.isRemoved()) {
			return false;
		}

		String villagerId = villager.getUUID().toString();
		CarriedGoods carriedGoods = CARRIED_GOODS.get(villagerId);
		int carriedCount = carriedGoods == null ? 0 : carriedGoods.totalCount();

		if (carriedCount >= MAX_CARRIED_ITEMS) {
			return false;
		}

		ItemStack stack = itemEntity.getItem();
		String goodsKey = SettlementGoods.goodsKeyForItem(stack);

		if (goodsKey == null) {
			return false;
		}

		int amount = Math.min(stack.getCount(), MAX_CARRIED_ITEMS - carriedCount);

		if (amount <= 0) {
			return false;
		}

		CarriedGoods updatedGoods = carriedGoods == null ? new CarriedGoods(new LinkedHashMap<>(), level.getServer().getTickCount()) : carriedGoods;
		updatedGoods.goods().merge(goodsKey, amount, Integer::sum);
		CARRIED_GOODS.put(villagerId, updatedGoods);

		if (amount >= stack.getCount()) {
			itemEntity.discard();
		} else {
			stack.shrink(amount);
		}

		return true;
	}

	private static void collectNearbyDroppedGoods(
		ServerLevel level,
		SettlementState settlement,
		Villager villager,
		CarriedGoods carriedGoods
	) {
		long methodStart = System.nanoTime();
		
		int carriedCount = carriedGoods == null ? 0 : carriedGoods.totalCount();

		if (carriedCount >= MAX_CARRIED_ITEMS) {
			return;
		}

		AABB bounds = villager.getBoundingBox().inflate(PICKUP_SCAN_RADIUS_BLOCKS);
		List<ItemEntity> nearbyItems = level.getEntitiesOfClass(ItemEntity.class, bounds, entity -> !entity.isRemoved());
		
		long entityScanTime = System.nanoTime() - methodStart;
		if (entityScanTime > 1_000_000) { // >1ms
			LiveVillages.LOGGER.warn("ItemPickup: entity scan took {} ms, found {} items", Math.round(entityScanTime / 1_000_000.0D), nearbyItems.size());
		}
		
		// Limit items scanned per villager per tick to prevent O(villagers × items) performance issues
		int maxItemsToCheck = Math.min(nearbyItems.size(), 5); // Check at most 5 items per villager per maintenance cycle
		
		for (int i = 0; i < maxItemsToCheck; i++) {
			ItemEntity itemEntity = nearbyItems.get(i);
			if (carriedCount >= MAX_CARRIED_ITEMS) {
				return;
			}

			if (itemEntity.blockPosition().distSqr(settlement.center()) > SettlementVillagers.settlementRadiusBlocks(settlement) * SettlementVillagers.settlementRadiusBlocks(settlement)) {
				continue;
			}

			ItemStack stack = itemEntity.getItem();
			String goodsKey = SettlementGoods.goodsKeyForItem(stack);

			if (goodsKey == null) {
				continue;
			}

			int amount = Math.min(stack.getCount(), MAX_CARRIED_ITEMS - carriedCount);

			if (amount <= 0) {
				continue;
			}

			if (carryItemEntity(level, villager, itemEntity)) {
				carriedGoods = CARRIED_GOODS.get(villager.getUUID().toString());
				carriedCount = carriedGoods == null ? carriedCount : carriedGoods.totalCount();
			}
		}
	}

	private static void depositIntoStock(ServerLevel level, SettlementState settlement, Villager villager, Map<String, Integer> stock, CarriedGoods carriedGoods) {
		for (Map.Entry<String, Integer> entry : carriedGoods.goods().entrySet()) {
			SettlementGoods.addGoods(stock, entry.getKey(), entry.getValue());
			String roleKey = SettlementVillagers.reportProfessionKey(villager);
			if (roleKey != null && !roleKey.isBlank()) {
				SettlementProfessionReports.recordRecovered(level, settlement, roleKey, villager, entry.getKey(), entry.getValue());
			}
		}

		String roleKey = SettlementVillagers.reportProfessionKey(villager);
		if (roleKey != null && !roleKey.isBlank()) {
			SettlementProfessionReports.recordAccomplished(level, settlement, roleKey, villager, "deposited recovered goods at settlement stock");
		}
	}

	private static void steerVillagerTowardDeposit(ServerLevel level, SettlementState settlement, Villager villager, BlockPos depositPos) {
		SettlementNavigation.moveToRoutineTarget(level, settlement, villager, depositPos, DEPOSIT_WALK_SPEED);
	}

	private static boolean isWithinDepositReach(Villager villager, BlockPos depositPos) {
		return villager.distanceToSqr(depositPos.getX() + 0.5D, depositPos.getY() + 0.5D, depositPos.getZ() + 0.5D) <= DEPOSIT_REACH_DISTANCE_SQUARED;
	}

	private static boolean isDepositTime(ServerLevel level) {
		long dayTime = level.getOverworldClockTime() % 24_000L;
		return dayTime >= DEPOSIT_START_TICK && dayTime < GATHERING_START_TICK;
	}

	private record CarriedGoods(Map<String, Integer> goods, long firstPickupTick) {
		private int totalCount() {
			return goods.values().stream()
				.mapToInt(Integer::intValue)
				.sum();
		}
	}
}
