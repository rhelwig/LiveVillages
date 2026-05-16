package com.ronhelwig.livevillages.sim;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import com.ronhelwig.livevillages.content.LiveVillagesVillagerProfessions;

public final class SettlementDefenseWork {
	private static final int VANILLA_BELL_RANGE_BLOCKS = 25;
	private static final int TARGET_REACQUIRE_PADDING_BLOCKS = 8;
	private static final double DEFENDER_RALLY_SPEED = 1.05D;
	private static final double DEFENDER_MELEE_RANGE_SQUARED = 4.0D;
	private static final long BELL_ALARM_DURATION_TICKS = 400L;
	private static final long TASK_MEMORY_TICKS = 40L;
	private static final long MELEE_ATTACK_COOLDOWN_TICKS = 20L;
	private static final double DEFENSE_CUE_PARTICLE_SPREAD = 0.18D;
	private static final Map<String, BellAlarm> BELL_ALARMS = new HashMap<>();
	private static final Map<String, Long> LAST_BELL_RING_TICKS = new HashMap<>();
	private static final Map<String, TimedTask> ACTIVE_TASKS = new HashMap<>();
	private static final Map<String, Long> LAST_ATTACK_TICKS = new HashMap<>();

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

	public static void maintainLoadedDefense(ServerLevel level, SettlementState settlement) {
		Optional<ActiveBellAlarm> activeAlarm = resolveActiveAlarm(level, settlement);
		SettlementFletcherWork.maintainLoadedDefense(level, settlement, activeAlarm);

		List<Villager> defenders = SettlementVillagers.nearbyAdultVillagers(level, settlement).stream()
			.filter(SettlementDefenseWork::canJoinBellDefense)
			.filter(villager -> !villager.getVillagerData().profession().is(VillagerProfession.FLETCHER))
			.toList();

		if (activeAlarm.isEmpty()) {
			for (Villager defender : defenders) {
				resetDefender(defender);
			}

			return;
		}

		ActiveBellAlarm alarm = activeAlarm.get();
		long tick = level.getServer().getTickCount();

		for (Villager defender : defenders) {
			if (!isWithinBellResponseRange(defender, alarm)) {
				resetDefender(defender);
				continue;
			}

			engageBellDefender(level, defender, alarm.target(), tick);
		}
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

	private static void engageBellDefender(ServerLevel level, Villager defender, Monster target, long tick) {
		showDefenseWeapon(defender);
		defender.setAggressive(true);
		defender.setTarget(target);
		defender.lookAt(target, 30.0F, 30.0F);
		ACTIVE_TASKS.put(defender.getUUID().toString(), new TimedTask("defending_village", tick));

		if (defender.distanceToSqr(target) > DEFENDER_MELEE_RANGE_SQUARED) {
			defender.getNavigation().moveTo(target.getX(), target.getY(), target.getZ(), DEFENDER_RALLY_SPEED);
			return;
		}

		defender.getNavigation().stop();

		if (!attackCooldownReady(defender, tick)) {
			return;
		}

		defender.swing(InteractionHand.MAIN_HAND);
		target.hurt(level.damageSources().mobAttack(defender), 4.0F);
		signalDefenderMeleeImpact(level, target);
		LAST_ATTACK_TICKS.put(defender.getUUID().toString(), tick);
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
			|| profession.is(LiveVillagesVillagerProfessions.FORESTER)
			|| profession.is(LiveVillagesVillagerProfessions.MINER)
			|| profession.is(LiveVillagesVillagerProfessions.ROADWRIGHT);
	}

	private static void showDefenseWeapon(Villager villager) {
		if (!villager.getMainHandItem().isEmpty()) {
			return;
		}

		villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
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
}
