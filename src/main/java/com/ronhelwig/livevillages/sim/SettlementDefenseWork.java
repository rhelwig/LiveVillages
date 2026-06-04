package com.ronhelwig.livevillages.sim;

import java.util.Comparator;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import com.ronhelwig.livevillages.block.GuardPostBlock;
import com.ronhelwig.livevillages.content.LiveVillagesBlocks;
import com.ronhelwig.livevillages.content.LiveVillagesItems;
import com.ronhelwig.livevillages.content.LiveVillagesVillagerProfessions;

public final class SettlementDefenseWork {
	private static final int BLOCK_UPDATE_FLAGS = 3;
	private static final int VANILLA_BELL_RANGE_BLOCKS = 25;
	private static final int TARGET_REACQUIRE_PADDING_BLOCKS = 8;
	private static final double DEFENDER_RALLY_SPEED = 1.05D;
	private static final double DEFENDER_MELEE_RANGE_SQUARED = 4.0D;
	private static final double GUARD_RANGED_RANGE_SQUARED = 225.0D;
	private static final double SLING_RANGE_SQUARED = 144.0D;
	private static final float SLING_DAMAGE = 2.0F;
	private static final long BELL_ALARM_DURATION_TICKS = 400L;
	private static final long TASK_MEMORY_TICKS = 40L;
	private static final long MELEE_ATTACK_COOLDOWN_TICKS = 20L;
	private static final long RANGED_ATTACK_COOLDOWN_TICKS = 30L;
	private static final long GUARD_PATROL_DECIDE_INTERVAL_TICKS = 260L;
	private static final long GUARD_ESCORT_DECIDE_INTERVAL_TICKS = 80L;
	private static final int GUARD_ESCORT_SEARCH_RADIUS_BLOCKS = 36;
	private static final double GUARD_ESCORT_CLOSE_RANGE_SQUARED = 9.0D;
	private static final double GUARD_ESCORT_MAX_TARGET_DISTANCE_SQUARED = GUARD_ESCORT_SEARCH_RADIUS_BLOCKS * GUARD_ESCORT_SEARCH_RADIUS_BLOCKS;
	private static final int GUARD_ESCORT_ROUTE_CORRIDOR_RADIUS_BLOCKS = 8;
	private static final double GUARD_ESCORT_ROUTE_CORRIDOR_RADIUS_SQUARED = GUARD_ESCORT_ROUTE_CORRIDOR_RADIUS_BLOCKS * GUARD_ESCORT_ROUTE_CORRIDOR_RADIUS_BLOCKS;
	private static final double GUARD_ESCORT_ROUTE_CORRIDOR_SCORE_BIAS = 192.0D;
	private static final double DEFENSE_CUE_PARTICLE_SPREAD = 0.18D;
	private static final int MAX_GUARD_TROPHY_DISPLAYS = 4;
	private static final Map<String, BellAlarm> BELL_ALARMS = new HashMap<>();
	private static final Map<String, Long> LAST_BELL_RING_TICKS = new HashMap<>();
	private static final Map<String, TimedTask> ACTIVE_TASKS = new HashMap<>();
	private static final Map<String, Long> LAST_ATTACK_TICKS = new HashMap<>();
	private static final Map<String, Long> LAST_PATROL_TICKS = new HashMap<>();
	private static final Map<String, Long> LAST_ESCORT_TICKS = new HashMap<>();
	private static final Map<String, RangedLoadout> GUARD_RANGED_LOADOUTS = new HashMap<>();
	private static final Map<String, ItemStack> SAVED_GUARD_MELEE_WEAPONS = new HashMap<>();
	private static final List<GearOption> GUARD_WEAPONS = List.of(
		new GearOption("netherite_sword", Items.NETHERITE_SWORD, 80, "Netherite Sword"),
		new GearOption("diamond_sword", Items.DIAMOND_SWORD, 70, "Diamond Sword"),
		new GearOption("trident", Items.TRIDENT, 65, "Trident"),
		new GearOption("mace", Items.MACE, 65, "Mace"),
		new GearOption("iron_sword", Items.IRON_SWORD, 60, "Iron Sword"),
		new GearOption("stone_sword", Items.STONE_SWORD, 50, "Stone Sword"),
		new GearOption("golden_sword", Items.GOLDEN_SWORD, 45, "Golden Sword"),
		new GearOption("wooden_sword", Items.WOODEN_SWORD, 40, "Wooden Sword")
	);
	private static final List<GearOption> GUARD_HELMETS = List.of(
		new GearOption("netherite_helmet", Items.NETHERITE_HELMET, 80, "Netherite Helmet"),
		new GearOption("diamond_helmet", Items.DIAMOND_HELMET, 70, "Diamond Helmet"),
		new GearOption("iron_helmet", Items.IRON_HELMET, 60, "Iron Helmet"),
		new GearOption("chainmail_helmet", Items.CHAINMAIL_HELMET, 55, "Chainmail Helmet"),
		new GearOption("golden_helmet", Items.GOLDEN_HELMET, 45, "Golden Helmet"),
		new GearOption("leather_helmet", Items.LEATHER_HELMET, 30, "Leather Helmet")
	);
	private static final List<GearOption> GUARD_CHEST_ARMOR = List.of(
		new GearOption("netherite_chestplate", Items.NETHERITE_CHESTPLATE, 80, "Netherite Chestplate"),
		new GearOption("diamond_chestplate", Items.DIAMOND_CHESTPLATE, 70, "Diamond Chestplate"),
		new GearOption("iron_chestplate", Items.IRON_CHESTPLATE, 60, "Iron Chestplate"),
		new GearOption("chainmail_chestplate", Items.CHAINMAIL_CHESTPLATE, 55, "Chainmail Chestplate"),
		new GearOption("golden_chestplate", Items.GOLDEN_CHESTPLATE, 45, "Golden Chestplate"),
		new GearOption("leather_chestplate", Items.LEATHER_CHESTPLATE, 30, "Leather Tunic")
	);
	private static final List<GearOption> GUARD_LEGGINGS = List.of(
		new GearOption("netherite_leggings", Items.NETHERITE_LEGGINGS, 80, "Netherite Leggings"),
		new GearOption("diamond_leggings", Items.DIAMOND_LEGGINGS, 70, "Diamond Leggings"),
		new GearOption("iron_leggings", Items.IRON_LEGGINGS, 60, "Iron Leggings"),
		new GearOption("chainmail_leggings", Items.CHAINMAIL_LEGGINGS, 55, "Chainmail Leggings"),
		new GearOption("golden_leggings", Items.GOLDEN_LEGGINGS, 45, "Golden Leggings"),
		new GearOption("leather_leggings", Items.LEATHER_LEGGINGS, 30, "Leather Pants")
	);
	private static final List<GearOption> GUARD_BOOTS = List.of(
		new GearOption("netherite_boots", Items.NETHERITE_BOOTS, 80, "Netherite Boots"),
		new GearOption("diamond_boots", Items.DIAMOND_BOOTS, 70, "Diamond Boots"),
		new GearOption("iron_boots", Items.IRON_BOOTS, 60, "Iron Boots"),
		new GearOption("chainmail_boots", Items.CHAINMAIL_BOOTS, 55, "Chainmail Boots"),
		new GearOption("golden_boots", Items.GOLDEN_BOOTS, 45, "Golden Boots"),
		new GearOption("leather_boots", Items.LEATHER_BOOTS, 30, "Leather Boots")
	);
	private static final List<RangedGearOption> GUARD_RANGED_WEAPONS = List.of(
		new RangedGearOption("crossbow", Items.CROSSBOW, 5.0F, "Crossbow"),
		new RangedGearOption("bow", Items.BOW, 4.0F, "Bow")
	);

	private SettlementDefenseWork() {
	}

	public static void register() {
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register(SettlementDefenseWork::onKilledOtherEntity);
	}

	public static void onBellRung(ServerLevel level, BlockPos bellPos) {
		long tick = level.getServer().getTickCount();
		String bellKey = bellKey(level, bellPos);
		long lastTick = LAST_BELL_RING_TICKS.getOrDefault(bellKey, Long.MIN_VALUE);

		if (lastTick == tick) {
			return;
		}

		LAST_BELL_RING_TICKS.put(bellKey, tick);
		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(level.getServer());
		Optional<SettlementState> settlement = savedData.findSettlementForPosition(level.dimension(), bellPos, SettlementVillagers::usesActualVillagers);

		if (settlement.isEmpty()) {
			return;
		}

		int responseRadius = bellResponseRadius(settlement.get());
		Optional<Monster> threat = findNearbyThreat(level, bellPos, responseRadius);

		if (threat.isEmpty()) {
			return;
		}

		BELL_ALARMS.put(
			alarmKey(settlement.get()),
			new BellAlarm(
				bellPos.immutable(),
				threat.get().getUUID(),
				threat.get().blockPosition().immutable(),
				responseRadius,
				tick + BELL_ALARM_DURATION_TICKS
			)
		);
	}

	public static boolean maintainLoadedDefense(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Collection<SettlementBuildSite> buildSites,
		Collection<SettlementConstructionDelivery> constructionDeliveries,
		Collection<SettlementState> allSettlements,
		List<RouteState> routes
	) {
		Optional<ActiveBellAlarm> activeAlarm = resolveActiveAlarm(level, settlement);
		SettlementFletcherWork.maintainLoadedDefense(level, settlement, activeAlarm, buildSites);
		maintainGuardTrophyDisplays(level, settlement, stock, buildSites);

		List<Villager> defenders = SettlementVillagers.nearbyAdultVillagers(level, settlement).stream()
			.filter(SettlementDefenseWork::canJoinBellDefense)
			.filter(villager -> !villager.getVillagerData().profession().is(VillagerProfession.FLETCHER))
			.toList();

		if (activeAlarm.isEmpty()) {
			List<BlockPos> guardPosts = SettlementConstruction.findPlacedGuardPosts(level, settlement);
			boolean hasGuards = defenders.stream()
				.anyMatch(defender -> defender.getVillagerData().profession().is(LiveVillagesVillagerProfessions.GUARD));
			List<BlockPos> routeCorridorBlocks = hasGuards && !routes.isEmpty()
				? SettlementRoadwrightWork.loadedRoadworkRouteBlocks(level, settlement, buildSites, allSettlements, routes)
				: List.of();
			List<EscortCandidate> escortCandidates = escortCandidates(level, settlement, constructionDeliveries, routeCorridorBlocks);
			Set<String> claimedEscortTargetIds = new java.util.HashSet<>();
			long tick = level.getServer().getTickCount();

			for (Villager defender : defenders) {
				if (defender.getVillagerData().profession().is(LiveVillagesVillagerProfessions.GUARD)) {
					if (!escortSettlementWorker(level, settlement, defender, escortCandidates, claimedEscortTargetIds, tick)) {
						patrolGuardPost(level, settlement, defender, guardPosts, tick);
					}
				} else {
					resetDefender(defender);
				}
			}

			return false;
		}

		ActiveBellAlarm alarm = activeAlarm.get();
		long tick = level.getServer().getTickCount();
		boolean stockChanged = false;

		for (Villager defender : defenders) {
			if (!isWithinBellResponseRange(defender, alarm)) {
				resetDefender(defender);
				continue;
			}

			stockChanged |= engageBellDefender(level, settlement, stock, defender, alarm.target(), tick);
		}

		return stockChanged;
	}

	public static Optional<String> loadedDefenseTaskKey(ServerLevel level, Villager villager) {
		TimedTask task = ACTIVE_TASKS.get(villager.getUUID().toString());

		if (task == null || level.getServer().getTickCount() - task.tick() > TASK_MEMORY_TICKS) {
			return Optional.empty();
		}

		return Optional.of(task.taskKey());
	}

	public static int bellResponseRadius(SettlementState settlement) {
		return Math.max(VANILLA_BELL_RANGE_BLOCKS, (SettlementVillagers.settlementRadiusBlocks(settlement) + 1) / 2);
	}

	private static void onKilledOtherEntity(ServerLevel level, Entity entity, LivingEntity killedEntity, net.minecraft.world.damagesource.DamageSource damageSource) {
		if (!(entity instanceof ServerPlayer player) || !(killedEntity instanceof Monster monster)) {
			return;
		}

		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(level.getServer());
		Optional<SettlementState> settlement = savedData.findSettlementForPosition(
			level.dimension(),
			monster.blockPosition(),
			existingSettlement -> existingSettlement.kind() != SettlementKind.OUTPOST && SettlementVillagers.usesActualVillagers(existingSettlement)
		);

		if (settlement.isEmpty() || !isThreateningSettlement(level, settlement.get(), monster)) {
			return;
		}

		savedData.addBakeryFreebiesOwed(settlement.get().id(), player.getUUID(), 1);
	}

	private static Optional<ActiveBellAlarm> resolveActiveAlarm(ServerLevel level, SettlementState settlement) {
		String key = alarmKey(settlement);
		BellAlarm alarm = BELL_ALARMS.get(key);

		if (alarm == null) {
			return Optional.empty();
		}

		long tick = level.getServer().getTickCount();

		if (tick > alarm.expiresAtTick()) {
			BELL_ALARMS.remove(key);
			return Optional.empty();
		}

		Monster target = level.getEntity(alarm.targetUuid()) instanceof Monster monster && monster.isAlive() && !monster.isRemoved()
			? monster
			: null;

		if (target == null || target.blockPosition().distSqr(alarm.bellPos()) > Math.pow(alarm.responseRadius() + TARGET_REACQUIRE_PADDING_BLOCKS, 2)) {
			Optional<Monster> reacquired = findNearbyThreat(level, alarm.bellPos(), alarm.responseRadius() + TARGET_REACQUIRE_PADDING_BLOCKS);

			if (reacquired.isEmpty()) {
				BELL_ALARMS.remove(key);
				return Optional.empty();
			}

			target = reacquired.get();
		}

		BELL_ALARMS.put(
			key,
			new BellAlarm(alarm.bellPos(), target.getUUID(), target.blockPosition().immutable(), alarm.responseRadius(), tick + BELL_ALARM_DURATION_TICKS)
		);
		return Optional.of(new ActiveBellAlarm(alarm.bellPos(), alarm.responseRadius(), target));
	}

	private static Optional<Monster> findNearbyThreat(ServerLevel level, BlockPos origin, int radius) {
		double radiusSqr = radius * radius;
		AABB bounds = new AABB(origin).inflate(radius);
		return level.getEntitiesOfClass(Monster.class, bounds, monster ->
			!monster.isRemoved()
				&& monster.isAlive()
				&& monster.blockPosition().distSqr(origin) <= radiusSqr
		)
			.stream()
			.min(Comparator.comparingDouble(monster -> monster.distanceToSqr(origin.getX() + 0.5D, origin.getY() + 0.5D, origin.getZ() + 0.5D)));
	}

	private static boolean isThreateningSettlement(ServerLevel level, SettlementState settlement, Monster monster) {
		int settlementRadius = SettlementVillagers.settlementRadiusBlocks(settlement);
		if (monster.blockPosition().distSqr(settlement.center()) <= (double) settlementRadius * settlementRadius) {
			return true;
		}

		return resolveActiveAlarm(level, settlement)
			.filter(alarm -> monster.getUUID().equals(alarm.target().getUUID())
				|| monster.blockPosition().distSqr(alarm.bellPos()) <= (double) alarm.responseRadius() * alarm.responseRadius())
			.isPresent();
	}

	private static boolean engageBellDefender(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, Villager defender, Monster target, long tick) {
		boolean stockChanged = equipGuardCombatGear(level, settlement, stock, defender);
		showDefenseWeapon(defender);
		defender.setAggressive(true);
		defender.setTarget(target);
		defender.lookAt(target, 30.0F, 30.0F);
		ACTIVE_TASKS.put(defender.getUUID().toString(), new TimedTask("defending_village", tick));

		RangedAttackResult rangedAttack = tryGuardRangedAttack(level, settlement, stock, defender, target, tick);
		stockChanged |= rangedAttack.stockChanged();

		if (rangedAttack.handled()) {
			return stockChanged;
		}

		if (trySlingRangedAttack(level, settlement, defender, target, tick)) {
			return stockChanged;
		}

		if (defender.distanceToSqr(target) > DEFENDER_MELEE_RANGE_SQUARED) {
			defender.getNavigation().moveTo(target.getX(), target.getY(), target.getZ(), DEFENDER_RALLY_SPEED);
			return stockChanged;
		}

		showCloseDefenseWeapon(defender);
		restoreGuardMeleeWeapon(defender);
		defender.getNavigation().stop();

		if (!attackCooldownReady(defender, tick)) {
			return stockChanged;
		}

		defender.swing(InteractionHand.MAIN_HAND);
		target.hurt(level.damageSources().mobAttack(defender), defenderAttackDamage(defender));
		signalDefenderMeleeImpact(level, target);
		LAST_ATTACK_TICKS.put(defender.getUUID().toString(), tick);
		return stockChanged;
	}

	public static void signalDefenderRangedAttack(ServerLevel level, Villager defender) {
		level.playSound(
			null,
			defender.getX(),
			defender.getY(),
			defender.getZ(),
			SoundEvents.SKELETON_SHOOT,
			SoundSource.NEUTRAL,
			1.0F,
			1.0F / (defender.getRandom().nextFloat() * 0.4F + 0.8F)
		);
		level.sendParticles(
			ParticleTypes.CRIT,
			defender.getX(),
			defender.getEyeY() - 0.1D,
			defender.getZ(),
			4,
			DEFENSE_CUE_PARTICLE_SPREAD,
			DEFENSE_CUE_PARTICLE_SPREAD,
			DEFENSE_CUE_PARTICLE_SPREAD,
			0.01D
		);
	}

	private static boolean attackCooldownReady(Villager defender, long tick) {
		Long lastAttackTick = LAST_ATTACK_TICKS.get(defender.getUUID().toString());
		return lastAttackTick == null || tick - lastAttackTick >= MELEE_ATTACK_COOLDOWN_TICKS;
	}

	private static boolean rangedAttackCooldownReady(Villager defender, long tick) {
		Long lastAttackTick = LAST_ATTACK_TICKS.get(defender.getUUID().toString());
		return lastAttackTick == null || tick - lastAttackTick >= RANGED_ATTACK_COOLDOWN_TICKS;
	}

	private static void signalDefenderMeleeImpact(ServerLevel level, Monster target) {
		level.playSound(
			null,
			target.getX(),
			target.getY(),
			target.getZ(),
			SoundEvents.PLAYER_ATTACK_SWEEP,
			SoundSource.NEUTRAL,
			0.7F,
			1.0F + (target.getRandom().nextFloat() * 0.2F)
		);
		level.sendParticles(
			ParticleTypes.SWEEP_ATTACK,
			target.getX(),
			target.getY(0.5D),
			target.getZ(),
			1,
			0.0D,
			0.0D,
			0.0D,
			0.0D
		);
	}

	private static void resetDefender(Villager defender) {
		defender.getNavigation().stop();
		defender.setAggressive(false);
		defender.setTarget(null);
	}

	private static List<EscortCandidate> escortCandidates(
		ServerLevel level,
		SettlementState settlement,
		Collection<SettlementConstructionDelivery> constructionDeliveries,
		List<BlockPos> routeCorridorBlocks
	) {
		Set<String> constructionDeliveryVillagerIds = constructionDeliveries.stream()
			.filter(delivery -> delivery.settlementId().equals(settlement.id()))
			.map(SettlementConstructionDelivery::villagerId)
			.collect(java.util.stream.Collectors.toSet());

		return SettlementVillagers.nearbyAdultVillagers(level, settlement).stream()
			.filter(candidate -> !candidate.isRemoved())
			.filter(candidate -> candidate.isAlive())
			.filter(candidate -> !candidate.getVillagerData().profession().is(LiveVillagesVillagerProfessions.GUARD))
			.map(candidate -> escortCandidate(level, candidate, constructionDeliveryVillagerIds, routeCorridorBlocks))
			.flatMap(Optional::stream)
			.toList();
	}

	private static Optional<EscortCandidate> escortCandidate(
		ServerLevel level,
		Villager candidate,
		Set<String> constructionDeliveryVillagerIds,
		List<BlockPos> routeCorridorBlocks
	) {
		if (constructionDeliveryVillagerIds.contains(candidate.getUUID().toString())) {
			return Optional.of(new EscortCandidate(
				candidate,
				"carrying_construction_supplies",
				routeCorridorDistance(candidate.blockPosition(), "carrying_construction_supplies", routeCorridorBlocks)
			));
		}

		return SettlementVillagers.loadedNonDefenseTaskKey(level, candidate)
			.filter(SettlementDefenseWork::isEscortWorthyTaskKey)
			.map(taskKey -> new EscortCandidate(
				candidate,
				taskKey,
				routeCorridorDistance(candidate.blockPosition(), taskKey, routeCorridorBlocks)
			));
	}

	private static boolean escortSettlementWorker(
		ServerLevel level,
		SettlementState settlement,
		Villager guard,
		List<EscortCandidate> candidates,
		Set<String> claimedEscortTargetIds,
		long tick
	) {
		Optional<EscortCandidate> escortTarget = bestEscortTarget(guard, candidates, claimedEscortTargetIds);

		if (escortTarget.isEmpty()) {
			return false;
		}

		Villager worker = escortTarget.get().villager();
		claimedEscortTargetIds.add(worker.getUUID().toString());
		guard.setAggressive(false);
		guard.setTarget(null);
		showDefenseWeapon(guard);
		ACTIVE_TASKS.put(guard.getUUID().toString(), new TimedTask("escorting_worker", tick));

		String guardId = guard.getUUID().toString();
		long lastEscortTick = LAST_ESCORT_TICKS.getOrDefault(guardId, Long.MIN_VALUE);

		if (tick - lastEscortTick < GUARD_ESCORT_DECIDE_INTERVAL_TICKS || SettlementVillagerWorkSchedule.isTakingBreak(level, guard)) {
			return true;
		}

		BlockPos escortTargetPos = escortTargetPosition(guard, worker, settlement, escortTarget.get());

		if (guard.blockPosition().distSqr(escortTargetPos) <= GUARD_ESCORT_CLOSE_RANGE_SQUARED) {
			guard.getNavigation().stop();
		} else {
			SettlementNavigation.moveToRoutineTarget(level, settlement, guard, escortTargetPos, DEFENDER_RALLY_SPEED * 0.8D);
		}

		LAST_ESCORT_TICKS.put(guardId, tick);
		return true;
	}

	private static Optional<EscortCandidate> bestEscortTarget(Villager guard, List<EscortCandidate> candidates, Set<String> claimedEscortTargetIds) {
		EscortCandidate bestTarget = null;
		double bestScore = Double.MAX_VALUE;

		for (EscortCandidate candidate : candidates) {
			if (claimedEscortTargetIds.contains(candidate.villager().getUUID().toString())) {
				continue;
			}

			double distance = guard.distanceToSqr(candidate.villager());

			if (distance > GUARD_ESCORT_MAX_TARGET_DISTANCE_SQUARED) {
				continue;
			}

			double score = distance + escortTaskPriority(candidate.taskKey()) * 128.0D + routeCorridorEscortScore(candidate);

			if (score < bestScore) {
				bestScore = score;
				bestTarget = candidate;
			}
		}

		return Optional.ofNullable(bestTarget);
	}

	private static BlockPos escortTargetPosition(Villager guard, Villager worker, SettlementState settlement, EscortCandidate candidate) {
		BlockPos workerPos = worker.blockPosition();

		if (candidate.routeDistance().nearestBlock().isPresent()
			&& candidate.routeDistance().distanceSquared() <= GUARD_ESCORT_ROUTE_CORRIDOR_RADIUS_SQUARED) {
			return candidate.routeDistance().nearestBlock().get();
		}

		BlockPos guardPos = guard.blockPosition();
		Direction direction = Direction.getApproximateNearest(
			workerPos.getX() - guardPos.getX(),
			0.0D,
			workerPos.getZ() - guardPos.getZ()
		).getOpposite();

		if (direction.getAxis().isVertical()) {
			direction = Direction.getApproximateNearest(
				workerPos.getX() - settlement.center().getX(),
				0.0D,
				workerPos.getZ() - settlement.center().getZ()
			).getOpposite();
		}

		if (direction.getAxis().isVertical()) {
			direction = Direction.NORTH;
		}

		return workerPos.relative(direction).immutable();
	}

	private static EscortRouteDistance routeCorridorDistance(BlockPos workerPos, String taskKey, List<BlockPos> routeCorridorBlocks) {
		if (routeCorridorBlocks.isEmpty() || !isRouteCorridorEscortTask(taskKey)) {
			return EscortRouteDistance.NONE;
		}

		BlockPos nearestBlock = null;
		double nearestDistanceSquared = Double.MAX_VALUE;

		for (BlockPos routeBlock : routeCorridorBlocks) {
			double distanceSquared = horizontalDistanceSquared(workerPos, routeBlock);
			if (distanceSquared < nearestDistanceSquared) {
				nearestDistanceSquared = distanceSquared;
				nearestBlock = routeBlock;
			}
		}

		if (nearestBlock == null) {
			return EscortRouteDistance.NONE;
		}

		return new EscortRouteDistance(nearestDistanceSquared, Optional.of(nearestBlock.immutable()));
	}

	private static double routeCorridorEscortScore(EscortCandidate candidate) {
		if (candidate.routeDistance().distanceSquared() > GUARD_ESCORT_ROUTE_CORRIDOR_RADIUS_SQUARED) {
			return 0.0D;
		}

		double proximity = GUARD_ESCORT_ROUTE_CORRIDOR_RADIUS_SQUARED - candidate.routeDistance().distanceSquared();
		return -GUARD_ESCORT_ROUTE_CORRIDOR_SCORE_BIAS - proximity;
	}

	private static boolean isRouteCorridorEscortTask(String taskKey) {
		return switch (taskKey) {
			case "carrying_collected_items",
				"carrying_construction_supplies",
				"depositing_into_trading_post",
				"collecting_forest_drops",
				"cutting_trees",
				"fishing",
				"fishing_from_boat",
				"fishing_from_dock",
				"fishing_from_shore",
				"heading_to_boat",
				"returning_fishing_boat",
				"rowing_out",
				"checking_lighthouse",
				"extinguishing_lighthouse",
				"inspecting_docks",
				"lighting_lighthouse",
				"managing_harbor",
				"planning_docks" -> true;
			default -> false;
		};
	}

	private static double horizontalDistanceSquared(BlockPos first, BlockPos second) {
		double dx = (first.getX() + 0.5D) - (second.getX() + 0.5D);
		double dz = (first.getZ() + 0.5D) - (second.getZ() + 0.5D);
		return (dx * dx) + (dz * dz);
	}

	private static boolean isEscortWorthyTaskKey(String taskKey) {
		return switch (taskKey) {
			case "carrying_collected_items",
				"carrying_construction_supplies",
				"depositing_into_trading_post",
				"collecting_forest_drops",
				"cutting_trees",
				"digging_mine_shaft",
				"lighting_cave",
				"lighting_mine_shaft",
				"lighting_primary_tunnel",
				"lighting_secondary_tunnel",
				"marking_underground_discovery",
				"mining_geode_resources",
				"mining_ore_vein",
				"mining_primary_tunnel_ore",
				"mining_reachable_ore",
				"mining_reachable_shaft_ore",
				"mining_reachable_shaft_stone",
				"mining_shaft_stone",
				"mining_secondary_tunnel_ore",
				"placing_bedrock_safety_ladders",
				"placing_primary_tunnel_support",
				"placing_secondary_tunnel_support",
				"placing_shaft_ladders",
				"returning_to_mine_shaft",
				"reinforcing_shaft_support",
				"fishing",
				"fishing_from_boat",
				"fishing_from_dock",
				"fishing_from_shore",
				"heading_to_boat",
				"returning_fishing_boat",
				"rowing_out",
				"checking_lighthouse",
				"extinguishing_lighthouse",
				"inspecting_docks",
				"lighting_lighthouse",
				"managing_harbor",
				"planning_docks" -> true;
			default -> false;
		};
	}

	private static int escortTaskPriority(String taskKey) {
		return switch (taskKey) {
			case "digging_mine_shaft",
				"lighting_cave",
				"lighting_mine_shaft",
				"lighting_primary_tunnel",
				"lighting_secondary_tunnel",
				"marking_underground_discovery",
				"mining_geode_resources",
				"mining_ore_vein",
				"mining_primary_tunnel_ore",
				"mining_reachable_ore",
				"mining_reachable_shaft_ore",
				"mining_reachable_shaft_stone",
				"mining_shaft_stone",
				"mining_secondary_tunnel_ore",
				"placing_bedrock_safety_ladders",
				"placing_primary_tunnel_support",
				"placing_secondary_tunnel_support",
				"placing_shaft_ladders",
				"returning_to_mine_shaft",
				"reinforcing_shaft_support" -> 0;
			case "checking_lighthouse",
				"extinguishing_lighthouse",
				"fishing_from_boat",
				"fishing_from_dock",
				"fishing_from_shore",
				"heading_to_boat",
				"inspecting_docks",
				"lighting_lighthouse",
				"managing_harbor",
				"planning_docks",
				"returning_fishing_boat",
				"rowing_out" -> 1;
			case "carrying_construction_supplies",
				"depositing_into_trading_post" -> 2;
			case "carrying_collected_items",
				"collecting_forest_drops",
				"cutting_trees",
				"fishing" -> 3;
			default -> 4;
		};
	}

	private static void patrolGuardPost(ServerLevel level, SettlementState settlement, Villager guard, List<BlockPos> guardPosts, long tick) {
		guard.setAggressive(false);
		guard.setTarget(null);
		showDefenseWeapon(guard);
		ACTIVE_TASKS.put(guard.getUUID().toString(), new TimedTask("patrolling_guard_post", tick));

		String guardId = guard.getUUID().toString();
		long lastPatrolTick = LAST_PATROL_TICKS.getOrDefault(guardId, Long.MIN_VALUE);

		if (tick - lastPatrolTick < GUARD_PATROL_DECIDE_INTERVAL_TICKS || SettlementVillagerWorkSchedule.isTakingBreak(level, guard)) {
			return;
		}

		BlockPos patrolTarget = patrolTargetFor(guard, settlement, guardPosts, tick);

		if (guard.blockPosition().distSqr(patrolTarget) <= 4.0D) {
			LAST_PATROL_TICKS.put(guardId, tick - GUARD_PATROL_DECIDE_INTERVAL_TICKS + 40L);
			return;
		}

		SettlementNavigation.moveToRoutineTarget(level, settlement, guard, patrolTarget, DEFENDER_RALLY_SPEED * 0.75D);
		LAST_PATROL_TICKS.put(guardId, tick);
	}

	private static BlockPos patrolTargetFor(Villager guard, SettlementState settlement, List<BlockPos> guardPosts, long tick) {
		Direction direction = patrolDirection(guard, tick);
		BlockPos anchor = nearestGuardPost(guard.blockPosition(), guardPosts).orElse(settlement.center());
		int distance = guardPosts.isEmpty() ? Math.max(8, SettlementVillagers.settlementRadiusBlocks(settlement) / 2) : 4;
		return anchor.relative(direction, distance).immutable();
	}

	private static Direction patrolDirection(Villager guard, long tick) {
		Direction[] directions = { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };
		int index = Math.floorMod((int) (tick / 1_200L) + guard.getUUID().hashCode(), directions.length);
		return directions[index];
	}

	private static Optional<BlockPos> nearestGuardPost(BlockPos origin, List<BlockPos> guardPosts) {
		BlockPos bestPos = null;
		double bestDistance = Double.MAX_VALUE;

		for (BlockPos guardPost : guardPosts) {
			double distance = guardPost.distSqr(origin);

			if (distance < bestDistance) {
				bestDistance = distance;
				bestPos = guardPost;
			}
		}

		return Optional.ofNullable(bestPos);
	}

	private static void maintainGuardTrophyDisplays(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		java.util.Collection<SettlementBuildSite> buildSites
	) {
		int desiredDisplays = Math.min(MAX_GUARD_TROPHY_DISPLAYS, stock.getOrDefault("desecrated_enemy_banner", 0));

		if (desiredDisplays <= 0) {
			return;
		}

		List<BlockPos> guardPosts = SettlementConstruction.findPlacedGuardPosts(level, settlement);

		if (guardPosts.isEmpty()) {
			return;
		}

		int existingDisplays = 0;

		for (BlockPos guardPost : guardPosts) {
			if (hasIncompleteGuardPostBuildSite(settlement, buildSites, guardPost)) {
				continue;
			}

			for (GuardTrophyCandidate candidate : guardTrophyCandidates(level, guardPost)) {
				if (isGuardTrophyDisplay(level.getBlockState(candidate.pos()))) {
					existingDisplays++;
				}
			}
		}

		if (existingDisplays >= desiredDisplays) {
			return;
		}

		for (BlockPos guardPost : guardPosts) {
			if (hasIncompleteGuardPostBuildSite(settlement, buildSites, guardPost)) {
				continue;
			}

			for (GuardTrophyCandidate candidate : guardTrophyCandidates(level, guardPost)) {
				if (isGuardTrophyDisplay(level.getBlockState(candidate.pos()))) {
					continue;
				}

				if (placeGuardTrophyDisplay(level, candidate)) {
					return;
				}
			}
		}
	}

	private static boolean hasIncompleteGuardPostBuildSite(
		SettlementState settlement,
		java.util.Collection<SettlementBuildSite> buildSites,
		BlockPos guardPost
	) {
		return buildSites.stream()
			.anyMatch(buildSite -> buildSite.blueprintId() == SettlementBuildSiteType.GUARD_POST
				&& !buildSite.complete()
				&& buildSite.settlementId().equals(settlement.id())
				&& (buildSite.workstationPos().equals(guardPost) || buildSite.anchorPos().equals(guardPost)));
	}

	private static List<GuardTrophyCandidate> guardTrophyCandidates(ServerLevel level, BlockPos guardPost) {
		BlockState guardPostState = level.getBlockState(guardPost);

		if (!guardPostState.hasProperty(GuardPostBlock.FACING)) {
			return List.of();
		}

		Direction facing = guardPostState.getValue(GuardPostBlock.FACING);
		Direction right = facing.getClockWise();
		Direction left = facing.getCounterClockWise();
		Direction back = facing.getOpposite();

		return List.of(
			guardTrophyCandidate(guardPost, right, right, 2, 2),
			guardTrophyCandidate(guardPost, left, left, 2, 2),
			guardTrophyCandidate(guardPost, right, right, 1, 3),
			guardTrophyCandidate(guardPost, left, left, 1, 3),
			new GuardTrophyCandidate(guardPost.relative(back, 4).above(2).relative(back), back),
			new GuardTrophyCandidate(guardPost.relative(back, 4).above(3).relative(back), back)
		);
	}

	private static GuardTrophyCandidate guardTrophyCandidate(BlockPos guardPost, Direction side, Direction bannerFacing, int sideDistance, int up) {
		return new GuardTrophyCandidate(guardPost.relative(side, sideDistance + 1).above(up), bannerFacing);
	}

	private static boolean placeGuardTrophyDisplay(ServerLevel level, GuardTrophyCandidate candidate) {
		BlockState bannerState = Blocks.WHITE_WALL_BANNER.defaultBlockState().setValue(WallBannerBlock.FACING, candidate.facing());

		if (!canPlaceGuardTrophyAt(level, candidate.pos(), bannerState)) {
			return false;
		}

		BlockState oldState = level.getBlockState(candidate.pos());
		level.setBlock(candidate.pos(), bannerState, BLOCK_UPDATE_FLAGS);

		if (level.getBlockEntity(candidate.pos()) instanceof BannerBlockEntity bannerBlockEntity) {
			bannerBlockEntity.applyComponentsFromItemStack(desecratedEnemyBannerStack(level));
			bannerBlockEntity.setChanged();
			level.sendBlockUpdated(candidate.pos(), oldState, bannerState, BLOCK_UPDATE_FLAGS);
			return true;
		}

		return true;
	}

	private static boolean canPlaceGuardTrophyAt(ServerLevel level, BlockPos pos, BlockState bannerState) {
		if (!level.hasChunkAt(pos) || !level.hasChunkAt(pos.relative(bannerState.getValue(WallBannerBlock.FACING).getOpposite()))) {
			return false;
		}

		BlockState currentState = level.getBlockState(pos);
		BlockPos supportPos = pos.relative(bannerState.getValue(WallBannerBlock.FACING).getOpposite());
		BlockState supportState = level.getBlockState(supportPos);

		return canReplaceForGuardTrophy(currentState)
			&& level.getBlockEntity(pos) == null
			&& supportState.isSolid()
			&& !isFunctionalOrRoadSurface(currentState)
			&& !isFunctionalOrRoadSurface(supportState)
			&& !touchesFunctionalOrRoadSurface(level, pos)
			&& bannerState.canSurvive(level, pos);
	}

	private static boolean canReplaceForGuardTrophy(BlockState state) {
		return state.isAir()
			|| state.is(Blocks.SHORT_GRASS)
			|| state.is(Blocks.TALL_GRASS)
			|| state.is(Blocks.LEAF_LITTER);
	}

	private static boolean touchesFunctionalOrRoadSurface(ServerLevel level, BlockPos pos) {
		if (isFunctionalOrRoadSurface(level.getBlockState(pos.below()))) {
			return true;
		}

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			if (isFunctionalOrRoadSurface(level.getBlockState(pos.relative(direction)))) {
				return true;
			}
		}

		return false;
	}

	private static boolean isFunctionalOrRoadSurface(BlockState state) {
		return state.getBlock() instanceof DoorBlock
			|| state.getBlock() instanceof BedBlock
			|| state.getBlock() instanceof LadderBlock
			|| state.getBlock() instanceof TrapDoorBlock
			|| state.getBlock() instanceof FenceGateBlock
			|| state.is(Blocks.BELL)
			|| state.is(Blocks.BARREL)
			|| state.is(Blocks.CHEST)
			|| state.is(Blocks.DIRT_PATH)
			|| state.is(Blocks.FARMLAND)
			|| state.is(Blocks.WHEAT)
			|| state.is(Blocks.CARROTS)
			|| state.is(Blocks.POTATOES)
			|| state.is(Blocks.BEETROOTS)
			|| state.is(Blocks.COBBLESTONE)
			|| state.is(Blocks.STONE_BRICKS)
			|| state.is(Blocks.BRICKS)
			|| state.is(Blocks.SMOOTH_STONE)
			|| state.is(LiveVillagesBlocks.GUARD_POST)
			|| state.is(LiveVillagesBlocks.TRADE_BOARD);
	}

	private static boolean isGuardTrophyDisplay(BlockState state) {
		return state.is(Blocks.WHITE_WALL_BANNER);
	}

	private static ItemStack desecratedEnemyBannerStack(ServerLevel level) {
		HolderGetter<BannerPattern> patterns = level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
		ItemStack stack = Raid.getOminousBannerInstance(patterns);
		BannerPatternLayers baseLayers = stack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
		BannerPatternLayers trophyLayers = new BannerPatternLayers.Builder()
			.addAll(baseLayers)
			.addIfRegistered(patterns, BannerPatterns.CIRCLE_MIDDLE, DyeColor.RED)
			.addIfRegistered(patterns, BannerPatterns.STRIPE_DOWNRIGHT, DyeColor.RED)
			.build();
		stack.set(DataComponents.BANNER_PATTERNS, trophyLayers);
		stack.set(DataComponents.ITEM_NAME, Component.literal("Desecrated Enemy Banner"));
		return stack;
	}

	private static boolean isWithinBellResponseRange(Villager villager, ActiveBellAlarm alarm) {
		return villager.blockPosition().distSqr(alarm.bellPos()) <= (double) alarm.responseRadius() * alarm.responseRadius();
	}

	private static boolean canJoinBellDefense(Villager villager) {
		if (villager.isBaby()) {
			return false;
		}

		Holder<VillagerProfession> profession = villager.getVillagerData().profession();

		if (profession.is(VillagerProfession.NONE) || profession.is(VillagerProfession.NITWIT)) {
			return false;
		}

		if (isNonCombatBellCivilian(profession)) {
			return false;
		}

		return isCombatCapableFieldWorker(profession);
	}

	private static boolean isNonCombatBellCivilian(Holder<VillagerProfession> profession) {
		return profession.is(LiveVillagesVillagerProfessions.TRADEMASTER)
			|| profession.is(LiveVillagesVillagerProfessions.SCRIBE)
			|| profession.is(VillagerProfession.CARTOGRAPHER)
			|| profession.is(VillagerProfession.CLERIC)
			|| profession.is(VillagerProfession.LIBRARIAN);
	}

	private static boolean isCombatCapableFieldWorker(Holder<VillagerProfession> profession) {
		return profession.is(VillagerProfession.BUTCHER)
			|| profession.is(VillagerProfession.FISHERMAN)
			|| profession.is(VillagerProfession.FLETCHER)
			|| profession.is(VillagerProfession.SHEPHERD)
			|| profession.is(VillagerProfession.ARMORER)
			|| profession.is(VillagerProfession.LEATHERWORKER)
			|| profession.is(VillagerProfession.TOOLSMITH)
			|| profession.is(VillagerProfession.WEAPONSMITH)
			|| profession.is(LiveVillagesVillagerProfessions.GUARD)
			|| profession.is(LiveVillagesVillagerProfessions.FORESTER)
			|| profession.is(LiveVillagesVillagerProfessions.GARDENER)
			|| profession.is(LiveVillagesVillagerProfessions.MINER)
			|| profession.is(LiveVillagesVillagerProfessions.PORTMASTER)
			|| profession.is(LiveVillagesVillagerProfessions.ROADWRIGHT);
	}

	private static void showDefenseWeapon(Villager villager) {
		Holder<VillagerProfession> profession = villager.getVillagerData().profession();

		if (profession.is(VillagerProfession.FISHERMAN)) {
			if (!villager.getMainHandItem().is(Items.IRON_AXE)) {
				villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
			}
			return;
		}

		if (profession.is(LiveVillagesVillagerProfessions.PORTMASTER)) {
			if (!villager.getMainHandItem().is(Items.IRON_SWORD)) {
				villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			}
			return;
		}

		if (profession.is(LiveVillagesVillagerProfessions.GUARD)) {
			if (villager.getMainHandItem().isEmpty()) {
				villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
			}
			return;
		}

		if (profession.is(LiveVillagesVillagerProfessions.GARDENER)) {
			showSling(villager);
			return;
		}

		if (profession.is(VillagerProfession.SHEPHERD)) {
			if (!villager.getMainHandItem().is(LiveVillagesItems.SLING)
				&& !villager.getMainHandItem().is(LiveVillagesItems.CROOKED_STAFF)) {
				villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(LiveVillagesItems.SLING));
			}
			return;
		}

		if (!villager.getMainHandItem().isEmpty()) {
			return;
		}

		villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
	}

	private static void showSling(Villager villager) {
		if (!villager.getMainHandItem().is(LiveVillagesItems.SLING)) {
			villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(LiveVillagesItems.SLING));
		}
	}

	private static void showCloseDefenseWeapon(Villager villager) {
		if (villager.getVillagerData().profession().is(VillagerProfession.SHEPHERD)
			&& !villager.getMainHandItem().is(LiveVillagesItems.CROOKED_STAFF)) {
			villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(LiveVillagesItems.CROOKED_STAFF));
		}
	}

	private static boolean equipGuardCombatGear(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, Villager villager) {
		if (!villager.getVillagerData().profession().is(LiveVillagesVillagerProfessions.GUARD)) {
			return false;
		}

		boolean changed = false;
		changed |= equipBestGuardGear(level, settlement, stock, villager, EquipmentSlot.MAINHAND, GUARD_WEAPONS);
		changed |= equipBestGuardGear(level, settlement, stock, villager, EquipmentSlot.HEAD, GUARD_HELMETS);
		changed |= equipBestGuardGear(level, settlement, stock, villager, EquipmentSlot.CHEST, GUARD_CHEST_ARMOR);
		changed |= equipBestGuardGear(level, settlement, stock, villager, EquipmentSlot.LEGS, GUARD_LEGGINGS);
		changed |= equipBestGuardGear(level, settlement, stock, villager, EquipmentSlot.FEET, GUARD_BOOTS);

		if (villager.getOffhandItem().isEmpty() && SettlementGoods.consumeGoods(stock, "shield", 1)) {
			villager.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
			SettlementProfessionReports.recordConsumed(level, settlement, SettlementRoleKeys.GUARD, villager, "shield", 1);
			SettlementProfessionReports.recordAccomplished(level, settlement, SettlementRoleKeys.GUARD, villager, "equipped Shield");
			changed = true;
		}

		return changed;
	}

	private static boolean equipBestGuardGear(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, Villager villager, EquipmentSlot slot, List<GearOption> options) {
		int currentQuality = gearQuality(villager.getItemBySlot(slot), options);
		Optional<GearOption> upgrade = options.stream()
			.filter(option -> option.quality() > currentQuality)
			.filter(option -> stock.getOrDefault(option.goodsKey(), 0) > 0)
			.findFirst();

		if (upgrade.isEmpty() || !SettlementGoods.consumeGoods(stock, upgrade.get().goodsKey(), 1)) {
			return false;
		}

		villager.setItemSlot(slot, new ItemStack(upgrade.get().item()));
		SettlementProfessionReports.recordConsumed(level, settlement, SettlementRoleKeys.GUARD, villager, upgrade.get().goodsKey(), 1);
		SettlementProfessionReports.recordAccomplished(level, settlement, SettlementRoleKeys.GUARD, villager, "equipped " + upgrade.get().displayName());
		return true;
	}

	private static RangedAttackResult tryGuardRangedAttack(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Villager defender,
		Monster target,
		long tick
	) {
		if (!defender.getVillagerData().profession().is(LiveVillagesVillagerProfessions.GUARD)) {
			return RangedAttackResult.NOT_HANDLED;
		}

		double distance = defender.distanceToSqr(target);

		if (distance <= DEFENDER_MELEE_RANGE_SQUARED || distance > GUARD_RANGED_RANGE_SQUARED || !defender.hasLineOfSight(target)) {
			return RangedAttackResult.NOT_HANDLED;
		}

		String arrowKey = stock.getOrDefault("arrow", 0) > 0 ? "arrow" : stock.getOrDefault("spectral_arrow", 0) > 0 ? "spectral_arrow" : "";

		if (arrowKey.isEmpty()) {
			return RangedAttackResult.NOT_HANDLED;
		}

		RangedLoadoutResult loadoutResult = guardRangedLoadout(level, settlement, stock, defender);

		if (loadoutResult == null) {
			return RangedAttackResult.NOT_HANDLED;
		}

		RangedLoadout loadout = loadoutResult.loadout();
		defender.getNavigation().stop();
		showGuardRangedWeapon(defender, loadout);

		if (!rangedAttackCooldownReady(defender, tick)) {
			return new RangedAttackResult(loadoutResult.stockChanged(), true);
		}

		if (!SettlementGoods.consumeGoods(stock, arrowKey, 1)) {
			return new RangedAttackResult(loadoutResult.stockChanged(), false);
		}

		defender.swing(InteractionHand.MAIN_HAND);
		target.hurt(level.damageSources().mobAttack(defender), loadout.damage());
		signalDefenderRangedAttack(level, defender);
		SettlementProfessionReports.recordConsumed(level, settlement, SettlementRoleKeys.GUARD, defender, arrowKey, 1);
		SettlementProfessionReports.recordAccomplished(level, settlement, SettlementRoleKeys.GUARD, defender, "shot " + loadout.displayName());
		LAST_ATTACK_TICKS.put(defender.getUUID().toString(), tick);
		return new RangedAttackResult(true, true);
	}

	private static boolean trySlingRangedAttack(
		ServerLevel level,
		SettlementState settlement,
		Villager defender,
		Monster target,
		long tick
	) {
		if (!usesSling(defender)) {
			return false;
		}

		double distance = defender.distanceToSqr(target);

		if (distance <= DEFENDER_MELEE_RANGE_SQUARED || distance > SLING_RANGE_SQUARED || !defender.hasLineOfSight(target)) {
			return false;
		}

		defender.getNavigation().stop();
		showSling(defender);

		if (!rangedAttackCooldownReady(defender, tick)) {
			return true;
		}

		defender.swing(InteractionHand.MAIN_HAND);
		target.hurt(level.damageSources().mobAttack(defender), SLING_DAMAGE);
		signalDefenderRangedAttack(level, defender);
		SettlementProfessionReports.recordAccomplished(level, settlement, roleKeyFor(defender), defender, "shot sling");
		LAST_ATTACK_TICKS.put(defender.getUUID().toString(), tick);
		return true;
	}

	private static RangedLoadoutResult guardRangedLoadout(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, Villager defender) {
		String defenderKey = defender.getUUID().toString();
		RangedLoadout existingLoadout = GUARD_RANGED_LOADOUTS.get(defenderKey);

		if (existingLoadout != null) {
			return new RangedLoadoutResult(existingLoadout, false);
		}

		for (RangedGearOption option : GUARD_RANGED_WEAPONS) {
			if (!SettlementGoods.consumeGoods(stock, option.goodsKey(), 1)) {
				continue;
			}

			RangedLoadout loadout = new RangedLoadout(option.item(), option.damage(), option.displayName());
			GUARD_RANGED_LOADOUTS.put(defenderKey, loadout);
			SettlementProfessionReports.recordConsumed(level, settlement, SettlementRoleKeys.GUARD, defender, option.goodsKey(), 1);
			SettlementProfessionReports.recordAccomplished(level, settlement, SettlementRoleKeys.GUARD, defender, "equipped " + option.displayName());
			return new RangedLoadoutResult(loadout, true);
		}

		return null;
	}

	private static void showGuardRangedWeapon(Villager defender, RangedLoadout loadout) {
		String defenderKey = defender.getUUID().toString();
		ItemStack held = defender.getMainHandItem();

		if (!held.is(loadout.item()) && !isGuardRangedWeapon(held) && !held.isEmpty()) {
			SAVED_GUARD_MELEE_WEAPONS.put(defenderKey, held.copy());
		}

		if (!held.is(loadout.item())) {
			defender.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(loadout.item()));
		}
	}

	private static void restoreGuardMeleeWeapon(Villager defender) {
		String defenderKey = defender.getUUID().toString();
		ItemStack held = defender.getMainHandItem();

		if (!isGuardRangedWeapon(held)) {
			return;
		}

		ItemStack savedWeapon = SAVED_GUARD_MELEE_WEAPONS.remove(defenderKey);
		defender.setItemSlot(EquipmentSlot.MAINHAND, savedWeapon == null ? new ItemStack(Items.WOODEN_SWORD) : savedWeapon);
	}

	private static boolean isGuardRangedWeapon(ItemStack stack) {
		return stack.is(Items.BOW) || stack.is(Items.CROSSBOW);
	}

	private static boolean usesSling(Villager defender) {
		Holder<VillagerProfession> profession = defender.getVillagerData().profession();
		return profession.is(LiveVillagesVillagerProfessions.GARDENER)
			|| profession.is(VillagerProfession.SHEPHERD);
	}

	private static String roleKeyFor(Villager defender) {
		Holder<VillagerProfession> profession = defender.getVillagerData().profession();

		if (profession.is(LiveVillagesVillagerProfessions.GARDENER)) {
			return SettlementRoleKeys.GARDENER;
		}

		if (profession.is(VillagerProfession.SHEPHERD)) {
			return SettlementRoleKeys.SHEPHERD;
		}

		if (profession.is(LiveVillagesVillagerProfessions.GUARD)) {
			return SettlementRoleKeys.GUARD;
		}

		if (profession.is(VillagerProfession.FLETCHER)) {
			return SettlementRoleKeys.FLETCHER;
		}

		return profession.unwrapKey()
			.map(key -> key.identifier().getPath())
			.orElse("villager");
	}

	private static int gearQuality(ItemStack stack, List<GearOption> options) {
		if (stack.isEmpty()) {
			return 0;
		}

		for (GearOption option : options) {
			if (stack.is(option.item())) {
				return option.quality();
			}
		}

		return 1;
	}

	private static float defenderAttackDamage(Villager defender) {
		ItemStack weapon = defender.getMainHandItem();

		if (weapon.is(Items.NETHERITE_SWORD) || weapon.is(Items.TRIDENT) || weapon.is(Items.MACE)) {
			return 8.0F;
		}

		if (weapon.is(Items.DIAMOND_SWORD)) {
			return 7.0F;
		}

		if (weapon.is(Items.IRON_SWORD)) {
			return 6.0F;
		}

		if (weapon.is(Items.STONE_SWORD) || weapon.is(Items.IRON_AXE)) {
			return 5.0F;
		}

		if (weapon.is(Items.WOODEN_SWORD) || weapon.is(Items.GOLDEN_SWORD)) {
			return 4.0F;
		}

		if (weapon.is(LiveVillagesItems.CROOKED_STAFF)) {
			return 3.0F;
		}

		return 2.0F;
	}

	private static String alarmKey(SettlementState settlement) {
		return settlement.dimension().identifier() + "|" + settlement.id();
	}

	private static String bellKey(ServerLevel level, BlockPos bellPos) {
		return level.dimension().identifier() + "|" + bellPos.asLong();
	}

	private record BellAlarm(BlockPos bellPos, UUID targetUuid, BlockPos lastKnownTargetPos, int responseRadius, long expiresAtTick) {
	}

	public record ActiveBellAlarm(BlockPos bellPos, int responseRadius, Monster target) {
	}

	private record TimedTask(String taskKey, long tick) {
	}

	private record GuardTrophyCandidate(BlockPos pos, Direction facing) {
	}

	private record EscortCandidate(Villager villager, String taskKey, EscortRouteDistance routeDistance) {
	}

	private record EscortRouteDistance(double distanceSquared, Optional<BlockPos> nearestBlock) {
		private static final EscortRouteDistance NONE = new EscortRouteDistance(Double.MAX_VALUE, Optional.empty());
	}

	private record GearOption(String goodsKey, Item item, int quality, String displayName) {
	}

	private record RangedGearOption(String goodsKey, Item item, float damage, String displayName) {
	}

	private record RangedLoadout(Item item, float damage, String displayName) {
	}

	private record RangedLoadoutResult(RangedLoadout loadout, boolean stockChanged) {
	}

	private record RangedAttackResult(boolean stockChanged, boolean handled) {
		private static final RangedAttackResult NOT_HANDLED = new RangedAttackResult(false, false);
	}
}
