package com.ronhelwig.livevillages.sim;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
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
	private static final int WALL_POST_SEARCH_RADIUS_BLOCKS = 64;
	private static final long TASK_MEMORY_TICKS = 40L;
	private static final long FLETCHING_DECIDE_INTERVAL_TICKS = 320L;
	private static final long WALL_PATROL_DECIDE_INTERVAL_TICKS = 640L;
	private static final long DEFENSE_ATTACK_COOLDOWN_TICKS = 20L;
	private static final long DEFENSE_RETREAT_TICKS = 120L;
	private static final int RECENT_INJURY_TICKS = 60;
	private static final Map<String, TimedTask> ACTIVE_TASKS = new HashMap<>();
	private static final Map<String, Long> LAST_ATTACK_TICKS = new HashMap<>();
	private static final Map<String, Long> RETREAT_UNTIL_TICKS = new HashMap<>();

	private SettlementFletcherWork() {
	}

	public static boolean maintainLoadedFletching(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Collection<SettlementBuildSite> buildSites
	) {
		List<Villager> fletchers = SettlementVillagers.nearbyFletchers(level, settlement);

		if (fletchers.isEmpty()) {
			SettlementProfessionDiagnostics.log(level, settlement, "fletcher", "no_fletchers", "");
			return false;
		}

		if (hasNearbyThreat(level, settlement, fletchers)) {
			return false;
		}

		if (!needsArrows(settlement, stock)) {
			SettlementProfessionDiagnostics.log(level, settlement, "fletcher", "no_arrow_work", fletchingStockSummary(settlement, stock));
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
			SettlementNavigation.moveToRoutineTarget(level, settlement, fletcher, workPos, FLETCHING_WALK_SPEED);
			ACTIVE_TASKS.put(fletcher.getUUID().toString(), new TimedTask("stocking_arrows", tick));

			if (!isWithinWorkReach(fletcher, workPos)) {
				SettlementProfessionDiagnostics.log(level, settlement, "fletcher", "moving_to_work", "villager=" + fletcher.getUUID() + " workPos=" + workPos.toShortString());
				continue;
			}

			Optional<CraftedArrowBatch> craftedArrows = craftArrows(stock);
			if (craftedArrows.isPresent()) {
				CraftedArrowBatch batch = craftedArrows.get();
				fletcher.swing(InteractionHand.MAIN_HAND);
				for (Map.Entry<String, Integer> consumed : batch.consumedGoods().entrySet()) {
					SettlementProfessionReports.recordConsumed(level, settlement, SettlementRoleKeys.FLETCHER, fletcher, consumed.getKey(), consumed.getValue());
				}

				SettlementProfessionReports.recordProduced(level, settlement, SettlementRoleKeys.FLETCHER, fletcher, batch.outputGoodsKey(), batch.outputAmount());
				SettlementProfessionReports.recordAccomplished(level, settlement, SettlementRoleKeys.FLETCHER, fletcher, "stocked " + batch.outputLabel());
				stockChanged = true;
			}
		}

		return stockChanged;
	}

	private static String fletchingStockSummary(SettlementState settlement, Map<String, Integer> stock) {
		return "arrows=" + arrowReserveStock(stock)
			+ " target=" + SettlementEconomyRules.targetForGoods(settlement, "arrow")
			+ " sticks=" + stock.getOrDefault("stick", 0)
			+ " flint=" + stock.getOrDefault("flint", 0)
			+ " copper_nuggets=" + stock.getOrDefault("copper_nugget", 0)
			+ " copper_ingots=" + stock.getOrDefault("copper_ingot", 0)
			+ " feathers=" + stock.getOrDefault("feather", 0);
	}

	public static void maintainLoadedDefense(
		ServerLevel level,
		SettlementState settlement,
		Optional<SettlementDefenseWork.ActiveBellAlarm> activeAlarm,
		Collection<SettlementBuildSite> buildSites
	) {
		List<Villager> fletchers = SettlementVillagers.nearbyFletchers(level, settlement);

		if (fletchers.isEmpty()) {
			return;
		}

		long tick = level.getServer().getTickCount();

		for (Villager fletcher : fletchers) {
			String fletcherId = fletcher.getUUID().toString();
			if (wasRecentlyInjured(fletcher)) {
				RETREAT_UNTIL_TICKS.put(fletcherId, tick + DEFENSE_RETREAT_TICKS);
			}

			if (RETREAT_UNTIL_TICKS.getOrDefault(fletcherId, 0L) > tick) {
				retreatFromWall(level, settlement, fletcher, buildSites, tick);
				continue;
			}

			List<Monster> hostiles = nearbyHostiles(level, settlement, fletcher);
			Optional<Monster> target = bellAlarmTarget(fletcher, activeAlarm)
				.or(() -> nearestHostile(fletcher, hostiles));
			List<BlockPos> wallPosts = palisadeWallPostsNear(level, buildSites, fletcher.blockPosition());
			showBow(fletcher);
			fletcher.setAggressive(target.isPresent() || !hostiles.isEmpty());

			if (target.isEmpty()) {
				patrolWallIfAvailable(level, fletcher, wallPosts, tick);
				continue;
			}

			Monster hostile = target.get();
			ACTIVE_TASKS.put(fletcherId, new TimedTask("defending_village", tick));
			fletcher.lookAt(hostile, 30.0F, 30.0F);

			Optional<BlockPos> firingPost = bestWallPostForTarget(fletcher, hostile, wallPosts);
			if (firingPost.isPresent() && !isWithinWorkReach(fletcher, firingPost.get())) {
				ACTIVE_TASKS.put(fletcherId, new TimedTask("taking_wall_position", tick));
				moveTo(level, fletcher, firingPost.get(), DEFENSE_WALK_SPEED);
				continue;
			}

			if (!fletcher.hasLineOfSight(hostile) || fletcher.distanceToSqr(hostile) > DEFENSE_RANGE_SQUARED) {
				fletcher.getNavigation().moveTo(hostile.getX(), hostile.getY(), hostile.getZ(), DEFENSE_WALK_SPEED);
				continue;
			}

			fletcher.getNavigation().stop();

			if (!attackCooldownReady(fletcher, tick)) {
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
		return arrowReserveStock(stock) < SettlementEconomyRules.targetForGoods(settlement, "arrow")
			&& stock.getOrDefault("stick", 0) > 0
			&& stock.getOrDefault("feather", 0) > 0
			&& (stock.getOrDefault("copper_nugget", 0) > 0
				|| stock.getOrDefault("copper_ingot", 0) > 0
				|| stock.getOrDefault("flint", 0) > 0);
	}

	private static Optional<CraftedArrowBatch> craftArrows(Map<String, Integer> stock) {
		if (stock.getOrDefault("stick", 0) <= 0 || stock.getOrDefault("feather", 0) <= 0) {
			return Optional.empty();
		}

		if (stock.getOrDefault("copper_nugget", 0) > 0 || stock.getOrDefault("copper_ingot", 0) > 0) {
			Map<String, Integer> consumed = new LinkedHashMap<>();
			consumeForFletching(stock, consumed, "stick", 1);
			consumeForFletching(stock, consumed, "feather", 1);
			consumeCopperArrowhead(stock, consumed);
			SettlementGoods.addGoods(stock, "copperhead_arrow", 8);
			return Optional.of(new CraftedArrowBatch("copperhead_arrow", 8, consumed, "Copperhead Arrows"));
		}

		if (stock.getOrDefault("flint", 0) > 0) {
			Map<String, Integer> consumed = new LinkedHashMap<>();
			consumeForFletching(stock, consumed, "stick", 1);
			consumeForFletching(stock, consumed, "feather", 1);
			consumeForFletching(stock, consumed, "flint", 1);
			SettlementGoods.addGoods(stock, "arrow", 8);
			return Optional.of(new CraftedArrowBatch("arrow", 8, consumed, "arrows"));
		}

		return Optional.empty();
	}

	private static int arrowReserveStock(Map<String, Integer> stock) {
		return stock.getOrDefault("arrow", 0) + stock.getOrDefault("copperhead_arrow", 0);
	}

	private static void consumeCopperArrowhead(Map<String, Integer> stock, Map<String, Integer> consumed) {
		if (stock.getOrDefault("copper_nugget", 0) > 0) {
			consumeForFletching(stock, consumed, "copper_nugget", 1);
			return;
		}

		consumeForFletching(stock, consumed, "copper_ingot", 1);
		SettlementGoods.addGoods(stock, "copper_nugget", 8);
	}

	private static void consumeForFletching(Map<String, Integer> stock, Map<String, Integer> consumed, String goodsKey, int amount) {
		if (SettlementGoods.consumeGoods(stock, goodsKey, amount)) {
			consumed.merge(goodsKey, amount, Integer::sum);
		}
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

	private static List<BlockPos> palisadeWallPostsNear(ServerLevel level, Collection<SettlementBuildSite> buildSites, BlockPos nearPos) {
		if (buildSites.isEmpty()) {
			return List.of();
		}

		double maxDistanceSquared = WALL_POST_SEARCH_RADIUS_BLOCKS * WALL_POST_SEARCH_RADIUS_BLOCKS;
		List<BlockPos> posts = new ArrayList<>();

		for (SettlementBuildSite buildSite : buildSites) {
			if (buildSite.blueprintId() != SettlementBuildSiteType.PALISADE_WALL) {
				continue;
			}

			for (SettlementBuildBlockState block : buildSite.blocks()) {
				if (block.blueprintSymbol().isBlank()
					|| (block.blueprintSymbol().charAt(0) != 'B' && block.blueprintSymbol().charAt(0) != 'C')) {
					continue;
				}

				Optional<BlockPos> slabPos = SettlementConstruction.buildSiteBlockPos(buildSite, block);
				if (slabPos.isEmpty() || slabPos.get().distSqr(nearPos) > maxDistanceSquared || !level.hasChunkAt(slabPos.get())) {
					continue;
				}

				BlockPos standPos = slabPos.get().above();
				if (!level.getBlockState(slabPos.get()).is(BlockTags.WOODEN_SLABS)
					|| !level.getBlockState(standPos).isAir()) {
					continue;
				}

				posts.add(standPos.immutable());
			}
		}

		return posts;
	}

	private static Optional<BlockPos> bestWallPostForTarget(Villager fletcher, Monster hostile, List<BlockPos> wallPosts) {
		return wallPosts.stream()
			.min(Comparator.comparingDouble(post -> post.distSqr(hostile.blockPosition()) + post.distSqr(fletcher.blockPosition()) * 0.35D));
	}

	private static void patrolWallIfAvailable(ServerLevel level, Villager fletcher, List<BlockPos> wallPosts, long tick) {
		if (wallPosts.isEmpty()
			|| !SettlementVillagerWorkSchedule.shouldStartNewWork(level, fletcher, "wall_patrol", WALL_PATROL_DECIDE_INTERVAL_TICKS)) {
			return;
		}

		BlockPos post = wallPosts.get(Math.floorMod(fletcher.getUUID().hashCode() + (int) (tick / WALL_PATROL_DECIDE_INTERVAL_TICKS), wallPosts.size()));
		ACTIVE_TASKS.put(fletcher.getUUID().toString(), new TimedTask("patrolling_wall", tick));
		moveTo(level, fletcher, post, DEFENSE_WALK_SPEED);
	}

	private static void retreatFromWall(
		ServerLevel level,
		SettlementState settlement,
		Villager fletcher,
		Collection<SettlementBuildSite> buildSites,
		long tick
	) {
		BlockPos retreatPos = SettlementVillagers.fletcherJobSite(level, fletcher)
			.or(() -> SettlementStockAccess.findStockAccessPos(level, settlement, List.copyOf(buildSites)))
			.orElse(settlement.center());
		ACTIVE_TASKS.put(fletcher.getUUID().toString(), new TimedTask("retreating_from_wall", tick));
		fletcher.setAggressive(false);
		moveTo(level, fletcher, retreatPos, DEFENSE_WALK_SPEED);
	}

	private static boolean wasRecentlyInjured(Villager fletcher) {
		LivingEntity attacker = fletcher.getLastHurtByMob();
		return attacker != null
			&& attacker.isAlive()
			&& fletcher.tickCount - fletcher.getLastHurtByMobTimestamp() <= RECENT_INJURY_TICKS;
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

	private static void moveTo(ServerLevel level, Villager fletcher, BlockPos pos, double speed) {
		if (!level.hasChunkAt(pos)) {
			return;
		}

		fletcher.getNavigation().moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, speed);
	}

	private static void showBow(Villager fletcher) {
		if (!fletcher.getMainHandItem().is(Items.BOW)) {
			fletcher.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		}
	}

	private static boolean attackCooldownReady(Villager fletcher, long tick) {
		Long lastAttackTick = LAST_ATTACK_TICKS.get(fletcher.getUUID().toString());
		return lastAttackTick == null || tick - lastAttackTick >= DEFENSE_ATTACK_COOLDOWN_TICKS;
	}

	private record TimedTask(String taskKey, long tick) {
	}

	private record CraftedArrowBatch(String outputGoodsKey, int outputAmount, Map<String, Integer> consumedGoods, String outputLabel) {
	}
}
