package com.ronhelwig.livevillages.sim;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

public final class SettlementFletcherWork {
	private static final double FLETCHING_REACH_DISTANCE_SQUARED = 6.25D;
	private static final double FLETCHING_WALK_SPEED = 0.75D;
	private static final double DEFENSE_WALK_SPEED = 0.95D;
	private static final double DEFENSE_RANGE_BLOCKS = 40.0D;
	private static final double DEFENSE_RANGE_SQUARED = DEFENSE_RANGE_BLOCKS * DEFENSE_RANGE_BLOCKS;
	private static final int DEFENSE_SCAN_RADIUS_BLOCKS = 48;
	private static final long TASK_MEMORY_TICKS = 40L;
	private static final long FLETCHING_DECIDE_INTERVAL_TICKS = 320L;
	private static final long DEFENSE_ATTACK_COOLDOWN_TICKS = 20L;
	private static final Map<String, TimedTask> ACTIVE_TASKS = new HashMap<>();
	private static final Map<String, Long> LAST_ATTACK_TICKS = new HashMap<>();

	private SettlementFletcherWork() {
	}

	public static boolean maintainLoadedFletching(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Collection<SettlementBuildSite> buildSites
	) {
		List<Villager> fletchers = SettlementVillagers.nearbyFletchers(level, settlement);

		if (fletchers.isEmpty() || hasNearbyThreat(level, settlement, fletchers) || !needsArrows(settlement, stock)) {
			return false;
		}

		boolean stockChanged = false;
		long tick = level.getServer().getTickCount();

		for (Villager fletcher : fletchers) {
			if (!SettlementVillagerWorkSchedule.shouldStartNewWork(level, fletcher, "fletching", FLETCHING_DECIDE_INTERVAL_TICKS)) {
				if (SettlementVillagerWorkSchedule.isTakingBreak(level, fletcher)) {
					fletcher.getNavigation().stop();
				}

				continue;
			}

			BlockPos workPos = SettlementVillagers.fletcherJobSite(level, fletcher)
				.or(() -> SettlementStockAccess.findStockAccessPos(level, settlement, List.copyOf(buildSites)))
				.orElse(settlement.center());
			showBow(fletcher);
			fletcher.setAggressive(false);
			fletcher.getNavigation().moveTo(workPos.getX() + 0.5D, workPos.getY(), workPos.getZ() + 0.5D, FLETCHING_WALK_SPEED);
			ACTIVE_TASKS.put(fletcher.getUUID().toString(), new TimedTask("stocking_arrows", tick));

			if (!isWithinWorkReach(fletcher, workPos)) {
				continue;
			}

			if (craftArrows(stock)) {
				fletcher.swing(InteractionHand.MAIN_HAND);
				stockChanged = true;
			}
		}

		return stockChanged;
	}

	public static void maintainLoadedDefense(
		ServerLevel level,
		SettlementState settlement,
		Optional<SettlementDefenseWork.ActiveBellAlarm> activeAlarm
	) {
		List<Villager> fletchers = SettlementVillagers.nearbyFletchers(level, settlement);

		if (fletchers.isEmpty()) {
			return;
		}

		long tick = level.getServer().getTickCount();

		for (Villager fletcher : fletchers) {
			List<Monster> hostiles = nearbyHostiles(level, settlement, fletcher);
			Optional<Monster> target = bellAlarmTarget(fletcher, activeAlarm)
				.or(() -> nearestHostile(fletcher, hostiles));
			showBow(fletcher);
			fletcher.setAggressive(target.isPresent() || !hostiles.isEmpty());

			if (target.isEmpty()) {
				continue;
			}

			Monster hostile = target.get();
			ACTIVE_TASKS.put(fletcher.getUUID().toString(), new TimedTask("defending_village", tick));
			fletcher.lookAt(hostile, 30.0F, 30.0F);

			if (!fletcher.hasLineOfSight(hostile) || fletcher.distanceToSqr(hostile) > DEFENSE_RANGE_SQUARED) {
				fletcher.getNavigation().moveTo(hostile.getX(), hostile.getY(), hostile.getZ(), DEFENSE_WALK_SPEED);
				continue;
			}

			fletcher.getNavigation().stop();

			if (tick - LAST_ATTACK_TICKS.getOrDefault(fletcher.getUUID().toString(), Long.MIN_VALUE) < DEFENSE_ATTACK_COOLDOWN_TICKS) {
				continue;
			}

			fireSkeletonStrengthArrow(level, fletcher, hostile);
			LAST_ATTACK_TICKS.put(fletcher.getUUID().toString(), tick);
			fletcher.swing(InteractionHand.MAIN_HAND);
		}
	}

	public static Optional<String> loadedFletcherTaskKey(ServerLevel level, Villager villager) {
		TimedTask task = ACTIVE_TASKS.get(villager.getUUID().toString());

		if (task == null || level.getServer().getTickCount() - task.tick() > TASK_MEMORY_TICKS) {
			return Optional.empty();
		}

		return Optional.of(task.taskKey());
	}

	private static boolean needsArrows(SettlementState settlement, Map<String, Integer> stock) {
		return stock.getOrDefault("arrow", 0) < SettlementEconomyRules.targetForGoods(settlement, "arrow")
			&& stock.getOrDefault("stick", 0) > 0
			&& stock.getOrDefault("flint", 0) > 0
			&& stock.getOrDefault("feather", 0) > 0;
	}

	private static boolean craftArrows(Map<String, Integer> stock) {
		if (!SettlementGoods.consumeGoods(stock, "stick", 1)
			|| !SettlementGoods.consumeGoods(stock, "flint", 1)
			|| !SettlementGoods.consumeGoods(stock, "feather", 1)) {
			return false;
		}

		SettlementGoods.addGoods(stock, "arrow", 4);
		return true;
	}

	private static boolean hasNearbyThreat(ServerLevel level, SettlementState settlement, List<Villager> fletchers) {
		for (Villager fletcher : fletchers) {
			if (!nearbyHostiles(level, settlement, fletcher).isEmpty()) {
				return true;
			}
		}

		return false;
	}

	private static List<Monster> nearbyHostiles(ServerLevel level, SettlementState settlement, Villager fletcher) {
		int settlementRadius = SettlementVillagers.settlementRadiusBlocks(settlement);
		int radius = Math.max(24, Math.min(DEFENSE_SCAN_RADIUS_BLOCKS, settlementRadius + 8));
		double radiusSqr = radius * radius;
		AABB bounds = new AABB(fletcher.blockPosition()).inflate(radius);
		return level.getEntitiesOfClass(Monster.class, bounds, monster ->
			!monster.isRemoved()
				&& monster.isAlive()
				&& monster.blockPosition().distSqr(fletcher.blockPosition()) <= radiusSqr
		);
	}

	private static Optional<Monster> nearestHostile(Villager fletcher, List<Monster> hostiles) {
		Optional<Monster> visible = hostiles.stream()
			.filter(fletcher::hasLineOfSight)
			.min(Comparator.comparingDouble(fletcher::distanceToSqr));

		if (visible.isPresent()) {
			return visible;
		}

		return hostiles.stream()
			.min(Comparator.comparingDouble(fletcher::distanceToSqr));
	}

	private static Optional<Monster> bellAlarmTarget(
		Villager fletcher,
		Optional<SettlementDefenseWork.ActiveBellAlarm> activeAlarm
	) {
		return activeAlarm
			.filter(alarm -> fletcher.blockPosition().distSqr(alarm.bellPos()) <= (double) alarm.responseRadius() * alarm.responseRadius())
			.map(SettlementDefenseWork.ActiveBellAlarm::target)
			.filter(hostile -> hostile.isAlive() && !hostile.isRemoved());
	}

	private static void fireSkeletonStrengthArrow(ServerLevel level, Villager fletcher, Monster hostile) {
		ItemStack bow = fletcher.getMainHandItem().is(Items.BOW) ? fletcher.getMainHandItem() : new ItemStack(Items.BOW);
		ItemStack projectileStack = new ItemStack(Items.ARROW);
		AbstractArrow arrow = ProjectileUtil.getMobArrow(fletcher, projectileStack, 1.0F, bow);
		double x = hostile.getX() - fletcher.getX();
		double z = hostile.getZ() - fletcher.getZ();
		double horizontalDistance = Math.sqrt(x * x + z * z);
		double y = hostile.getY(1.0D / 3.0D) - arrow.getY() + horizontalDistance * 0.2D;
		arrow.setOwner(fletcher);
		arrow.setPos(fletcher.getX(), fletcher.getEyeY() - 0.1D, fletcher.getZ());
		arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
		arrow.shoot(x, y, z, 1.6F, 14 - level.getDifficulty().getId() * 4);
		level.addFreshEntity(arrow);
		SettlementDefenseWork.signalDefenderRangedAttack(level, fletcher);
	}

	private static boolean isWithinWorkReach(Villager fletcher, BlockPos workPos) {
		return fletcher.distanceToSqr(workPos.getX() + 0.5D, workPos.getY() + 0.5D, workPos.getZ() + 0.5D) <= FLETCHING_REACH_DISTANCE_SQUARED;
	}

	private static void showBow(Villager fletcher) {
		if (!fletcher.getMainHandItem().is(Items.BOW)) {
			fletcher.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		}
	}

	private record TimedTask(String taskKey, long tick) {
	}
}
