package com.ronhelwig.livevillages.sim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import com.ronhelwig.livevillages.LiveVillages;

public final class OutpostRaids {
	private static final long RAID_COOLDOWN_TICKS = (long) SettlementEconomyRules.TICKS_PER_DAY * 7L;
	private static final long MUSTER_TICKS = 1_200L;
	private static final long MIN_MARCH_TICKS = 2_400L;
	private static final long MAX_MARCH_TICKS = 12_000L;
	private static final long MIN_RETURN_TICKS = 1_200L;
	private static final long MAX_RETURN_TICKS = 8_000L;
	private static final long RAID_MAX_TICKS = 6_000L;
	private static final int CONTROL_REQUIRED_TICKS = 1_800;
	private static final int CONTROL_RADIUS_BLOCKS = 32;
	private static final int RAID_ARRIVAL_RADIUS_BLOCKS = 18;
	private static final int RAID_RETURN_HOME_RADIUS_BLOCKS = 32;
	private static final int TARGET_SEARCH_RADIUS_BLOCKS = 1_024;
	private static final int BANNER_NOTICE_RADIUS_BLOCKS = 256;
	private static final int PARTICIPATION_RADIUS_BLOCKS = 160;
	private static final int RAID_PARTY_ENTITY_SEARCH_MARGIN_BLOCKS = 96;
	private static final int RAID_LAND_WAYPOINT_MAX_BLOCKS = 128;
	private static final int RAID_LAND_WAYPOINT_STEP_BLOCKS = 16;
	private static final int RAID_LAND_WAYPOINT_LATERAL_BLOCKS = 96;
	private static final int RAID_WAYPOINT_REACHED_RADIUS_BLOCKS = 40;
	private static final int RAID_WATER_SCAN_BLOCKS = 128;
	private static final int TARGET_CONTROL_SEARCH_RADIUS_BLOCKS = 36;
	private static final int TARGET_CONTROL_SEARCH_STEP_BLOCKS = 4;
	private static final int RAID_CARTOGRAPHER_RADIUS_BONUS_BLOCKS = 384;
	private static final int RAID_ROADWRIGHT_RADIUS_BONUS_BLOCKS = 256;
	private static final int RAID_PORTMASTER_RADIUS_BONUS_BLOCKS = 256;
	private static final int RAID_TRADEMASTER_RADIUS_BONUS_BLOCKS = 192;
	private static final int RAID_LIGHTHOUSE_RADIUS_BONUS_BLOCKS = 512;
	private static final int MAX_LOOT_TYPES = 12;
	private static final int MUSTER_GROUND_MIN_BLOCKS = 10;
	private static final int MUSTER_GROUND_MAX_BLOCKS = 22;
	private static final int MUSTER_GROUND_STEP_BLOCKS = 4;
	private static final int MUSTER_GROUND_LATERAL_BLOCKS = 14;
	private static final int MIN_RAIDERS_TO_LAUNCH = 8;
	private static final int RAIDERS_LEFT_BEHIND = 4;
	private static final int MIN_PARTY_SIZE = 3;
	private static final int MAX_PARTY_SIZE = 8;
	private static final int TRIBUTE_SUPPORT = 18;
	private static final int RAID_SUCCESS_SUPPORT = 32;
	private static final double TRIBUTE_SECURITY_PENALTY = 0.05D;
	private static final double RAID_SUCCESS_SECURITY_PENALTY = 0.12D;
	private static final double RAID_REPELLED_SECURITY_PENALTY = 0.03D;
	private static final String MUSTER_BANNER_TAG = "livevillages.outpost_raid_banner";
	private static final List<String> LOOT_PRIORITY = List.of(
		"emerald",
		"iron_ingot",
		"copper_ingot",
		"gold_ingot",
		"arrow",
		"flint",
		"feather",
		"leather",
		"beef",
		"mutton",
		"pork",
		"cod",
		"bread",
		"wheat",
		"carrot",
		"potato",
		"beetroot",
		"logs",
		"planks",
		"cobblestone"
	);

	private OutpostRaids() {
	}

	public record RaidLaunchResult(boolean launched, String message) {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register(OutpostRaids::onAfterDeath);
	}

	public static long currentRaidTick(MinecraftServer server) {
		if (server == null) {
			return 0L;
		}

		ServerLevel overworld = server.getLevel(Level.OVERWORLD);
		return overworld == null ? server.getTickCount() : overworld.getGameTime();
	}

	public static boolean maintain(MinecraftServer server, LiveVillagesSavedData savedData, long currentTick) {
		boolean changed = false;
		long sessionTick = server.getTickCount();

		for (OutpostRaidState raidState : List.copyOf(savedData.outpostRaidStates())) {
			OutpostRaidState normalizedRaidState = normalizeRaidClock(raidState, currentTick, sessionTick);
			if (!normalizedRaidState.equals(raidState)) {
				savedData.putOutpostRaidState(normalizedRaidState);
				raidState = normalizedRaidState;
				changed = true;
			}

			changed |= advanceRaid(server, savedData, raidState, currentTick);
		}

		for (SettlementState outpost : savedData.getSettlements()) {
			if (outpost.kind() != SettlementKind.OUTPOST) {
				continue;
			}

			Optional<OutpostRaidState> existingState = savedData.outpostRaidState(outpost.id());
			if (existingState.isPresent()
				&& existingState.get().phase() != OutpostRaidPhase.COOLDOWN) {
				continue;
			}

			if (existingState.isPresent() && currentTick < existingState.get().nextEligibleTick()) {
				continue;
			}

			changed |= tryLaunchRaid(server, savedData, outpost, currentTick);
		}

		return changed;
	}

	private static OutpostRaidState normalizeRaidClock(OutpostRaidState raidState, long currentTick, long sessionTick) {
		if (raidState == null || raidState.nextEligibleTick() <= 0L || currentTick <= RAID_COOLDOWN_TICKS * 2L) {
			return raidState;
		}

		long latestPlausibleSessionClock = sessionTick + RAID_COOLDOWN_TICKS + (long) SettlementEconomyRules.TICKS_PER_DAY;
		if (raidState.nextEligibleTick() > latestPlausibleSessionClock) {
			return raidState;
		}

		long offset = currentTick - sessionTick;
		return raidState.withClockOffset(offset);
	}

	public static String describeRaidState(OutpostRaidState raidState, SettlementState target, long currentTick) {
		if (raidState == null) {
			return "No raid planned";
		}

		String targetName = target == null || raidState.targetSettlementId().isBlank()
			? "unknown target"
			: target.name();
		return switch (raidState.phase()) {
			case MUSTERING -> "Mustering " + raidState.partySize() + " raiders for " + targetName;
			case MARCHING -> "Marching " + raidState.partySize() + " raiders toward " + targetName;
			case RAIDING -> "Raiding " + targetName + " (" + raidState.controlProgressTicks() + "/" + CONTROL_REQUIRED_TICKS + " control)";
			case RETURNING -> "Returning from " + targetName;
			case COOLDOWN -> raidState.outcome().isBlank()
				? "Raid cooldown: next eligible in " + formatTicks(Math.max(0L, raidState.nextEligibleTick() - currentTick))
				: "Last raid: " + raidState.outcome() + "; next eligible in " + formatTicks(Math.max(0L, raidState.nextEligibleTick() - currentTick));
		};
	}

	public static boolean forceRetreat(MinecraftServer server, LiveVillagesSavedData savedData, SettlementState outpost, long currentTick) {
		OutpostRaidState raidState = savedData.outpostRaidState(outpost.id()).orElse(null);
		if (raidState == null || raidState.phase() == OutpostRaidPhase.COOLDOWN) {
			return false;
		}

		Optional<SettlementState> target = savedData.getSettlement(raidState.targetSettlementId());
		long nextEligibleTick = Math.max(currentTick, raidState.nextEligibleTick());
		if (target.isEmpty() || target.get().kind() == SettlementKind.OUTPOST) {
			savedData.putOutpostRaidState(raidState.completed(
				"retreated: target lost",
				currentTick,
				nextEligibleTick,
				raidState.lastLoot(),
				raidState.lastPlayerRewards()
			));
			return true;
		}

		OutpostRaidState returning = raidState.phase() == OutpostRaidPhase.RETURNING
			? raidState.withAnnouncementTick(currentTick)
			: raidState.returning(
				retreatOutcome(raidState, target.get()),
				currentTick,
				nextEligibleTick,
				raidState.lastLoot(),
				raidState.lastPlayerRewards()
			);
		announce(server, savedData, outpost, target.get(), returning, "returning from");
		steerLoadedRaidersHome(server, outpost, target.get(), returning.partySize());
		savedData.putOutpostRaidState(returning.withAnnouncementTick(currentTick));
		return true;
	}

	public static RaidLaunchResult requestRaidFromPlayer(ServerPlayer player, LiveVillagesSavedData savedData, long currentTick) {
		if (player == null || !(player.level() instanceof ServerLevel level)) {
			return new RaidLaunchResult(false, "Only a loaded player can call an outpost raid.");
		}

		SettlementState outpost = OutpostTrust.findOrCreateOutpostAt(level, savedData, player.blockPosition()).orElse(null);
		if (outpost == null || outpost.kind() != SettlementKind.OUTPOST) {
			return new RaidLaunchResult(false, "No outpost answered the horn.");
		}

		if (!savedData.settlementPlayerStanding(outpost, player).rank().atLeast(OutpostPlayerRank.BANNER_BEARER)) {
			return new RaidLaunchResult(false, outpost.name() + " does not trust you enough to muster raiders.");
		}

		OutpostRaidState existingState = savedData.outpostRaidState(outpost.id()).orElse(null);
		if (existingState != null && existingState.phase() != OutpostRaidPhase.COOLDOWN) {
			SettlementState target = savedData.getSettlement(existingState.targetSettlementId()).orElse(null);
			return new RaidLaunchResult(false, OutpostRaids.describeRaidState(existingState, target, currentTick));
		}

		if (existingState != null && currentTick < existingState.nextEligibleTick()) {
			return new RaidLaunchResult(
				false,
				outpost.name() + " is not ready for another raid for " + formatTicks(existingState.nextEligibleTick() - currentTick) + "."
			);
		}

		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return new RaidLaunchResult(false, "No outpost answered the horn.");
		}

		ServerLevel outpostLevel = server.getLevel(outpost.dimension());
		if (outpostLevel == null || !outpostLevel.isLoaded(outpost.center()) || !outpostLevel.isPositionEntityTicking(outpost.center())) {
			return new RaidLaunchResult(false, outpost.name() + " must be loaded before it can muster a raid.");
		}

		int raiders = loadedRaiderCount(outpostLevel, outpost);
		if (raiders < MIN_RAIDERS_TO_LAUNCH) {
			return new RaidLaunchResult(false, outpost.name() + " needs at least " + MIN_RAIDERS_TO_LAUNCH + " loaded raiders to launch.");
		}

		if (chooseTarget(savedData, outpost).isEmpty()) {
			return new RaidLaunchResult(false, outpost.name() + " has no worthwhile nearby raid target.");
		}

		boolean launched = tryLaunchRaid(server, savedData, outpost, currentTick);
		return launched
			? new RaidLaunchResult(true, outpost.name() + " answers your horn and musters a raid.")
			: new RaidLaunchResult(false, outpost.name() + " could not muster a raid yet.");
	}

	private static void onAfterDeath(LivingEntity entity, DamageSource damageSource) {
		if (!(entity instanceof Raider raider) || !(entity.level() instanceof ServerLevel level)) {
			return;
		}

		MinecraftServer server = level.getServer();
		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(server);
		String outpostId = OutpostSettlementWork.activeRaidOutpostId(raider, savedData.getSettlements()).orElse("");
		if (outpostId.isBlank()) {
			return;
		}

		OutpostRaidState raidState = savedData.outpostRaidState(outpostId).orElse(null);
		if (raidState == null || raidState.phase() == OutpostRaidPhase.COOLDOWN) {
			return;
		}

		SettlementState outpost = savedData.getSettlement(outpostId).orElse(null);
		SettlementState target = savedData.getSettlement(raidState.targetSettlementId()).orElse(null);
		if (outpost == null || target == null) {
			return;
		}

		announceRaidMemberDeath(server, savedData, outpost, target, raider);
	}

	private static String retreatOutcome(OutpostRaidState raidState, SettlementState target) {
		return switch (raidState.phase()) {
			case MUSTERING -> "raid on " + target.name() + " called off during muster";
			case MARCHING -> "retreated before reaching " + target.name();
			case RAIDING -> "retreated from " + target.name();
			case RETURNING, COOLDOWN -> raidState.outcome();
		};
	}

	private static boolean tryLaunchRaid(MinecraftServer server, LiveVillagesSavedData savedData, SettlementState outpost, long currentTick) {
		ServerLevel level = server.getLevel(outpost.dimension());
		if (level == null || !level.isLoaded(outpost.center()) || !level.isPositionEntityTicking(outpost.center())) {
			return false;
		}

		if (!hasNearbyBannerBearer(server, savedData, outpost, outpost.center(), BANNER_NOTICE_RADIUS_BLOCKS)) {
			return false;
		}

		int raiders = loadedRaiderCount(level, outpost);
		if (raiders < MIN_RAIDERS_TO_LAUNCH) {
			return false;
		}

		Optional<SettlementState> target = chooseTarget(savedData, outpost);
		if (target.isEmpty()) {
			return false;
		}

		int partySize = Math.max(MIN_PARTY_SIZE, Math.min(MAX_PARTY_SIZE, raiders - RAIDERS_LEFT_BEHIND));
		OutpostRaidState raidState = OutpostRaidState.mustering(
			outpost.id(),
			target.get().id(),
			partySize,
			currentTick,
			currentTick + RAID_COOLDOWN_TICKS
		);
		announce(server, savedData, outpost, target.get(), raidState, "mustering a raid against");
		musterLoadedRaiders(level, outpost, target.get(), partySize);
		savedData.putOutpostRaidState(raidState.withAnnouncementTick(currentTick));
		return true;
	}

	private static boolean advanceRaid(MinecraftServer server, LiveVillagesSavedData savedData, OutpostRaidState raidState, long currentTick) {
		Optional<SettlementState> outpost = savedData.getSettlement(raidState.outpostSettlementId());
		Optional<SettlementState> target = savedData.getSettlement(raidState.targetSettlementId());

		if (outpost.isEmpty()) {
			savedData.removeOutpostRaidState(raidState.outpostSettlementId());
			return true;
		}

		if (raidState.phase() == OutpostRaidPhase.COOLDOWN) {
			return false;
		}

		if (target.isEmpty() || target.get().kind() == SettlementKind.OUTPOST) {
			savedData.putOutpostRaidState(raidState.completed("abandoned: target lost", currentTick, currentTick + (long) SettlementEconomyRules.TICKS_PER_DAY));
			return true;
		}

		return switch (raidState.phase()) {
			case MUSTERING -> advanceMustering(server, savedData, outpost.get(), target.get(), raidState, currentTick);
			case MARCHING -> advanceMarching(server, savedData, outpost.get(), target.get(), raidState, currentTick);
			case RAIDING -> advanceRaiding(server, savedData, outpost.get(), target.get(), raidState, currentTick);
			case RETURNING -> advanceReturning(server, savedData, outpost.get(), target.get(), raidState, currentTick);
			case COOLDOWN -> false;
		};
	}

	private static boolean advanceMustering(
		MinecraftServer server,
		LiveVillagesSavedData savedData,
		SettlementState outpost,
		SettlementState target,
		OutpostRaidState raidState,
		long currentTick
	) {
		OutpostRaidState updated = announceIfDue(server, savedData, outpost, target, raidState, currentTick, "mustering a raid against");
		ServerLevel outpostLevel = server.getLevel(outpost.dimension());
		if (outpostLevel != null) {
			musterLoadedRaiders(outpostLevel, outpost, target, raidState.partySize());
		}

		if (currentTick - updated.phaseStartedTick() < MUSTER_TICKS) {
			if (!updated.equals(raidState)) {
				savedData.putOutpostRaidState(updated);
				return true;
			}
			return false;
		}

		updated = updated.withPhase(OutpostRaidPhase.MARCHING, currentTick);
		announce(server, savedData, outpost, target, updated, "marching toward");
		if (outpostLevel != null) {
			steerLoadedRaiders(outpostLevel, outpost, target, raidState.partySize());
		}
		savedData.putOutpostRaidState(updated.withAnnouncementTick(currentTick));
		return true;
	}

	private static boolean advanceMarching(
		MinecraftServer server,
		LiveVillagesSavedData savedData,
		SettlementState outpost,
		SettlementState target,
		OutpostRaidState raidState,
		long currentTick
	) {
		OutpostRaidState updated = announceIfDue(server, savedData, outpost, target, raidState, currentTick, "marching toward");
		long marchTicks = marchTicks(outpost, target);
		ServerLevel outpostLevel = server.getLevel(outpost.dimension());
		if (outpostLevel != null) {
			steerLoadedRaiders(outpostLevel, outpost, target, raidState.partySize());
		}

		ServerLevel targetLevel = server.getLevel(target.dimension());
		boolean targetLoaded = targetLevel != null && targetLevel.isLoaded(target.center());
		boolean targetTicking = targetLevel != null && targetLevel.isPositionEntityTicking(target.center());
		long elapsedTicks = currentTick - updated.phaseStartedTick();
		RaidArrivalStatus arrivalStatus = targetLevel == null
			? RaidArrivalStatus.unobserved(target.center(), raidState.partySize())
			: raidArrivalStatus(targetLevel, outpost, target, raidState.partySize());
		logMarchProgress(
			server.getTickCount(),
			outpostLevel != null ? outpostLevel : targetLevel,
			outpost,
			target,
			raidState.partySize(),
			elapsedTicks,
			marchTicks,
			targetLoaded,
			targetTicking
		);

		if (targetLoaded
			&& targetTicking
			&& maybeRetreatAfterHeavyLosses(server, savedData, outpost, target, updated, arrivalStatus.activeRaiders(), currentTick, false)) {
			return true;
		}

		if (elapsedTicks < marchTicks) {
			if (!updated.equals(raidState)) {
				savedData.putOutpostRaidState(updated);
				return true;
			}
			return false;
		}

		if (targetLevel != null
			&& targetLoaded
			&& targetTicking
			&& !arrivalStatus.arrived()) {
			if (elapsedTicks >= MAX_MARCH_TICKS) {
				return retreatAfterFailedMarch(server, savedData, outpost, target, updated, arrivalStatus.activeRaiders(), currentTick);
			}

			if (!updated.equals(raidState)) {
				savedData.putOutpostRaidState(updated);
				return true;
			}
			return false;
		}

		updated = updated.withPhase(OutpostRaidPhase.RAIDING, currentTick);
		announce(server, savedData, outpost, target, updated, "has reached");
		savedData.putOutpostRaidState(updated.withAnnouncementTick(currentTick));
		return true;
	}

	private static boolean advanceRaiding(
		MinecraftServer server,
		LiveVillagesSavedData savedData,
		SettlementState outpost,
		SettlementState target,
		OutpostRaidState raidState,
		long currentTick
	) {
		ServerLevel targetLevel = server.getLevel(target.dimension());
		int nearbyGolems = targetLevel == null ? 0 : countNearbyGolems(targetLevel, target);
		RaidArrivalStatus arrivalStatus = targetLevel == null
			? RaidArrivalStatus.unobserved(target.center(), raidState.partySize())
			: raidArrivalStatus(targetLevel, outpost, target, raidState.partySize());
		if (targetLevel != null
			&& maybeRetreatAfterHeavyLosses(server, savedData, outpost, target, raidState, arrivalStatus.activeRaiders(), currentTick, true)) {
			return true;
		}

		int effectivePartySize = arrivalStatus.observed()
			? Math.min(raidState.partySize(), arrivalStatus.activeRaiders())
			: raidState.partySize();

		if (raidState.controlProgressTicks() == 0 && shouldPayTribute(target, effectivePartySize, nearbyGolems)) {
			LootTransfer transfer = transferLoot(savedData, outpost, target, effectivePartySize, true);
			damageTargetSecurity(savedData, target, TRIBUTE_SECURITY_PENALTY);
			Map<String, Integer> rewards = awardParticipants(server, savedData, outpost, target, TRIBUTE_SUPPORT, target.name() + " paid tribute to " + outpost.name() + ".");
			OutpostRaidState returning = raidState.returning(
				"paid off by " + target.name() + " (" + transfer.totalTransferred() + " goods)",
				currentTick,
				currentTick + RAID_COOLDOWN_TICKS,
				transfer.transferred(),
				rewards
			);
			announce(server, savedData, outpost, target, returning, "returning from");
			steerLoadedRaidersHome(server, outpost, target, returning.partySize());
			savedData.putOutpostRaidState(returning.withAnnouncementTick(currentTick));
			return true;
		}

		int progress = raidState.controlProgressTicks() + controlProgressThisTick(target, effectivePartySize, nearbyGolems);
		if (progress >= CONTROL_REQUIRED_TICKS) {
			LootTransfer transfer = transferLoot(savedData, outpost, target, effectivePartySize, false);
			damageTargetSecurity(savedData, target, RAID_SUCCESS_SECURITY_PENALTY);
			Map<String, Integer> rewards = awardParticipants(server, savedData, outpost, target, RAID_SUCCESS_SUPPORT, outpost.name() + " successfully raided " + target.name() + ".");
			OutpostRaidState returning = raidState.returning(
				"successful raid on " + target.name() + " (" + transfer.totalTransferred() + " goods)",
				currentTick,
				currentTick + RAID_COOLDOWN_TICKS,
				transfer.transferred(),
				rewards
			);
			announce(server, savedData, outpost, target, returning, "returning from");
			steerLoadedRaidersHome(server, outpost, target, returning.partySize());
			savedData.putOutpostRaidState(returning.withAnnouncementTick(currentTick));
			return true;
		}

		if (currentTick - raidState.phaseStartedTick() >= RAID_MAX_TICKS) {
			damageTargetSecurity(savedData, target, RAID_REPELLED_SECURITY_PENALTY);
			OutpostRaidState returning = raidState.returning(
				"repelled at " + target.name(),
				currentTick,
				currentTick + RAID_COOLDOWN_TICKS,
				Map.of(),
				Map.of()
			);
			announce(server, savedData, outpost, target, returning, "returning from");
			steerLoadedRaidersHome(server, outpost, target, returning.partySize());
			savedData.putOutpostRaidState(returning.withAnnouncementTick(currentTick));
			return true;
		}

		OutpostRaidState updated = raidState.withControlProgress(progress);
		savedData.putOutpostRaidState(announceIfDue(server, savedData, outpost, target, updated, currentTick, "is raiding"));
		return true;
	}

	private static boolean advanceReturning(
		MinecraftServer server,
		LiveVillagesSavedData savedData,
		SettlementState outpost,
		SettlementState target,
		OutpostRaidState raidState,
		long currentTick
	) {
		OutpostRaidState updated = announceIfDue(server, savedData, outpost, target, raidState, currentTick, "returning from");
		steerLoadedRaidersHome(server, outpost, target, updated.partySize());

		ServerLevel returnLevel = server.getLevel(outpost.dimension());
		RaidReturnStatus returnStatus = returnLevel == null
			? RaidReturnStatus.unobserved(raidMusterPosFallback(outpost, target))
			: raidReturnStatus(returnLevel, outpost, target);
		long elapsedTicks = currentTick - updated.phaseStartedTick();
		long returnTicks = returnTicks(outpost, target);
		logReturnProgress(server.getTickCount(), returnStatus, outpost, target, elapsedTicks, returnTicks);

		if (elapsedTicks < returnTicks
			|| (returnStatus.observed()
				&& returnStatus.activeRaiders() > returnStatus.homeRaiders()
				&& elapsedTicks < MAX_RETURN_TICKS)) {
			if (!updated.equals(raidState)) {
				savedData.putOutpostRaidState(updated);
				return true;
			}
			return false;
		}

		if (returnLevel != null && returnStatus.activeRaiders() > returnStatus.homeRaiders()) {
			recoverReturningRaiders(returnLevel, outpost, target);
		}
		clearReturnedRaiders(server, outpost, target);
		OutpostRaidState completed = updated.completed(
			updated.outcome(),
			currentTick,
			updated.nextEligibleTick(),
			updated.lastLoot(),
			updated.lastPlayerRewards()
		);
		savedData.putOutpostRaidState(completed);
		return true;
	}

	private static Optional<SettlementState> chooseTarget(LiveVillagesSavedData savedData, SettlementState outpost) {
		double searchRadius = raidSearchRadius(savedData, outpost);
		List<SettlementState> candidates = savedData.getSettlements().stream()
			.filter(settlement -> !settlement.id().equals(outpost.id()))
			.filter(settlement -> settlement.kind() != SettlementKind.OUTPOST)
			.filter(settlement -> settlement.dimension().equals(outpost.dimension()))
			.filter(settlement -> horizontalDistanceSqr(settlement.center(), outpost.center()) <= searchRadius * searchRadius)
			.filter(settlement -> transferableValue(settlement) >= 8)
			.toList();

		if (candidates.isEmpty()) {
			return Optional.empty();
		}

		String lastTargetId = savedData.outpostRaidState(outpost.id())
			.map(OutpostRaidState::targetSettlementId)
			.orElse("");
		List<SettlementState> preferredCandidates = candidates.size() <= 1 || lastTargetId.isBlank()
			? candidates
			: candidates.stream()
				.filter(settlement -> !settlement.id().equals(lastTargetId))
				.toList();

		if (preferredCandidates.isEmpty()) {
			preferredCandidates = candidates;
		}

		return preferredCandidates.stream()
			.max(Comparator
				.comparingInt(OutpostRaids::transferableValue)
				.thenComparingDouble(settlement -> -horizontalDistanceSqr(settlement.center(), outpost.center()))
				.thenComparing(SettlementState::name));
	}

	private static double raidSearchRadius(LiveVillagesSavedData savedData, SettlementState outpost) {
		int radius = TARGET_SEARCH_RADIUS_BLOCKS;
		radius += roleCount(outpost, SettlementRoleKeys.CARTOGRAPHER) > 0 ? RAID_CARTOGRAPHER_RADIUS_BONUS_BLOCKS : 0;
		radius += roleCount(outpost, SettlementRoleKeys.ROADWRIGHT) > 0 ? RAID_ROADWRIGHT_RADIUS_BONUS_BLOCKS : 0;
		radius += roleCount(outpost, SettlementRoleKeys.PORTMASTER) > 0 ? RAID_PORTMASTER_RADIUS_BONUS_BLOCKS : 0;
		radius += roleCount(outpost, SettlementRoleKeys.TRADEMASTER) > 0 ? RAID_TRADEMASTER_RADIUS_BONUS_BLOCKS : 0;
		radius += completedBuildSites(savedData, outpost, SettlementBuildSiteType.LIGHTHOUSE) > 0 ? RAID_LIGHTHOUSE_RADIUS_BONUS_BLOCKS : 0;
		return radius;
	}

	private static int roleCount(SettlementState settlement, String roleKey) {
		return Math.max(0, settlement.population().getOrDefault(roleKey, 0));
	}

	private static long completedBuildSites(LiveVillagesSavedData savedData, SettlementState settlement, SettlementBuildSiteType buildSiteType) {
		return savedData.getBuildSitesForSettlement(settlement.id()).stream()
			.filter(SettlementBuildSite::complete)
			.filter(buildSite -> buildSite.blueprintId() == buildSiteType)
			.count();
	}

	private static boolean shouldPayTribute(SettlementState target, int partySize, int nearbyGolems) {
		int transferableValue = transferableValue(target);
		if (transferableValue < 12) {
			return false;
		}

		double defensePressure = target.security() + (nearbyGolems * 0.20D);
		double raidPressure = Math.min(1.0D, partySize * 0.12D);
		return defensePressure < raidPressure;
	}

	private static int controlProgressThisTick(SettlementState target, int partySize, int nearbyGolems) {
		double defensePressure = target.security() + (nearbyGolems * 0.18D);
		double raidPressure = Math.min(1.0D, partySize * 0.11D);

		if (defensePressure >= raidPressure + 0.25D) {
			return 0;
		}

		if (nearbyGolems > 0 || defensePressure >= raidPressure) {
			return 5;
		}

		return 20;
	}

	private static boolean maybeRetreatAfterHeavyLosses(
		MinecraftServer server,
		LiveVillagesSavedData savedData,
		SettlementState outpost,
		SettlementState target,
		OutpostRaidState raidState,
		int activeRaiders,
		long currentTick,
		boolean damageTargetSecurity
	) {
		if (activeRaiders >= MIN_PARTY_SIZE) {
			return false;
		}

		if (damageTargetSecurity) {
			damageTargetSecurity(savedData, target, RAID_REPELLED_SECURITY_PENALTY);
		}

		String outcome = activeRaiders <= 0
			? "lost raiding party near " + target.name()
			: "retreated from " + target.name() + " after heavy losses";
		OutpostRaidState returning = raidState.returning(
			outcome,
			currentTick,
			Math.max(currentTick, raidState.nextEligibleTick()),
			raidState.lastLoot(),
			raidState.lastPlayerRewards()
		);
		LiveVillages.LOGGER.info(
			"Outpost raid retreating after losses: outpost={} target={} active={} minimum={}",
			outpost.name(),
			target.name(),
			activeRaiders,
			MIN_PARTY_SIZE
		);
		announce(server, savedData, outpost, target, returning, "returning from");
		steerLoadedRaidersHome(server, outpost, target, returning.partySize());
		savedData.putOutpostRaidState(returning.withAnnouncementTick(currentTick));
		return true;
	}

	private static boolean retreatAfterFailedMarch(
		MinecraftServer server,
		LiveVillagesSavedData savedData,
		SettlementState outpost,
		SettlementState target,
		OutpostRaidState raidState,
		int activeRaiders,
		long currentTick
	) {
		OutpostRaidState returning = raidState.returning(
			"retreated before reaching " + target.name() + " (path blocked)",
			currentTick,
			Math.max(currentTick, raidState.nextEligibleTick()),
			raidState.lastLoot(),
			raidState.lastPlayerRewards()
		);
		LiveVillages.LOGGER.info(
			"Outpost raid retreating after failed march: outpost={} target={} active={} elapsed={}",
			outpost.name(),
			target.name(),
			activeRaiders,
			currentTick - raidState.phaseStartedTick()
		);
		announce(server, savedData, outpost, target, returning, "returning from");
		steerLoadedRaidersHome(server, outpost, target, returning.partySize());
		savedData.putOutpostRaidState(returning.withAnnouncementTick(currentTick));
		return true;
	}

	private static LootTransfer transferLoot(
		LiveVillagesSavedData savedData,
		SettlementState outpost,
		SettlementState target,
		int partySize,
		boolean tribute
	) {
		Map<String, Integer> targetStock = new LinkedHashMap<>(target.stock());
		Map<String, Integer> outpostStock = new LinkedHashMap<>(outpost.stock());
		Map<String, Integer> targetWealth = new LinkedHashMap<>(target.wealth());
		Map<String, Integer> outpostWealth = new LinkedHashMap<>(outpost.wealth());
		Map<String, Integer> targetPopulation = new LinkedHashMap<>(target.population());
		Map<String, Integer> outpostPopulation = new LinkedHashMap<>(outpost.population());
		int transferableValue = transferableValue(target);
		int fractionLimit = Math.max(1, (int) Math.ceil(transferableValue * (tribute ? 0.125D : 0.25D)));
		int carryLimit = Math.max(1, partySize * (tribute ? 4 : 8));
		int remaining = Math.min(fractionLimit, carryLimit);
		Map<String, Integer> transferred = new LinkedHashMap<>();
		if (!tribute) {
			int thralls = transferThralls(targetPopulation, outpostPopulation, partySize);
			if (thralls > 0) {
				SettlementGoods.addGoods(transferred, SettlementRoleKeys.THRALL, thralls);
			}
		}

		int emeralds = transferEmeraldWealth(targetWealth, outpostWealth, partySize, tribute, remaining);
		if (emeralds > 0) {
			SettlementGoods.addGoods(transferred, "emerald", emeralds);
			remaining -= emeralds;
		}

		for (String goodsKey : sortedLootKeys(targetStock)) {
			if (remaining <= 0 || transferred.size() >= MAX_LOOT_TYPES) {
				break;
			}

			int available = Math.max(0, targetStock.getOrDefault(goodsKey, 0));
			if (available <= 0) {
				continue;
			}

			int lootSlotsRemaining = Math.max(1, MAX_LOOT_TYPES - transferred.size());
			int amount = Math.min(available, Math.max(1, (int) Math.ceil(remaining / (double) lootSlotsRemaining)));
			if (!SettlementGoods.consumeGoods(targetStock, goodsKey, amount)) {
				continue;
			}

			SettlementGoods.addGoods(outpostStock, goodsKey, amount);
			SettlementGoods.addGoods(transferred, goodsKey, amount);
			remaining -= amount;
		}

		savedData.putSettlement(withRaidEconomy(target, targetPopulation, targetWealth, targetStock));
		savedData.putSettlement(withRaidEconomy(outpost, outpostPopulation, outpostWealth, outpostStock));
		return new LootTransfer(transferred);
	}

	private static int transferThralls(Map<String, Integer> targetPopulation, Map<String, Integer> outpostPopulation, int partySize) {
		int targetPopulationTotal = targetPopulation.values().stream()
			.mapToInt(amount -> Math.max(0, amount))
			.sum();
		int maxByRaiders = Math.max(0, partySize / 2);
		int maxByPopulation = Math.max(0, (int) Math.floor(targetPopulationTotal * 0.20D));
		int remaining = Math.min(maxByRaiders, maxByPopulation);
		int transferred = 0;

		for (String roleKey : sortedThrallSourceRoles(targetPopulation)) {
			if (remaining <= 0) {
				break;
			}

			int available = Math.max(0, targetPopulation.getOrDefault(roleKey, 0));
			if (available <= 0) {
				continue;
			}

			int amount = Math.min(available, remaining);
			int updatedAmount = available - amount;
			if (updatedAmount > 0) {
				targetPopulation.put(roleKey, updatedAmount);
			} else {
				targetPopulation.remove(roleKey);
			}
			SettlementGoods.addGoods(outpostPopulation, SettlementRoleKeys.THRALL, amount);
			remaining -= amount;
			transferred += amount;
		}

		return transferred;
	}

	private static List<String> sortedThrallSourceRoles(Map<String, Integer> population) {
		List<String> roles = new ArrayList<>(population.keySet());
		roles.remove(SettlementRoleKeys.THRALL);
		roles.remove("child");
		roles.sort(Comparator.comparingInt(OutpostRaids::thrallSourcePriority).thenComparing(role -> role));
		return roles;
	}

	private static int thrallSourcePriority(String roleKey) {
		return switch (roleKey) {
			case SettlementRoleKeys.UNEMPLOYED -> 0;
			case "nitwit" -> 1;
			default -> 10;
		};
	}

	private static int transferEmeraldWealth(
		Map<String, Integer> targetWealth,
		Map<String, Integer> outpostWealth,
		int partySize,
		boolean tribute,
		int remainingCarry
	) {
		int available = Math.max(0, targetWealth.getOrDefault("emerald", 0));
		if (available <= 0 || remainingCarry <= 0) {
			return 0;
		}

		int fractionLimit = Math.max(1, (int) Math.ceil(available * (tribute ? 0.10D : 0.20D)));
		int partyLimit = Math.max(1, partySize * (tribute ? 2 : 4));
		int amount = Math.min(Math.min(available, remainingCarry), Math.min(fractionLimit, partyLimit));
		if (!SettlementGoods.consumeGoods(targetWealth, "emerald", amount)) {
			return 0;
		}

		SettlementGoods.addGoods(outpostWealth, "emerald", amount);
		return amount;
	}

	private static void damageTargetSecurity(LiveVillagesSavedData savedData, SettlementState target, double penalty) {
		SettlementState currentTarget = savedData.getSettlement(target.id()).orElse(target);
		double updatedSecurity = Math.max(0.0D, currentTarget.security() - penalty);
		if (updatedSecurity == currentTarget.security()) {
			return;
		}

		savedData.putSettlement(currentTarget.withSimulationState(
			currentTarget.population(),
			currentTarget.wealth(),
			currentTarget.stock(),
			currentTarget.housingCapacity(),
			currentTarget.comfort(),
			updatedSecurity,
			currentTarget.defenseLevel(),
			currentTarget.growthProgress(),
			currentTarget.projects(),
			currentTarget.lastUpdateTick()
		));
	}

	private static SettlementState withRaidEconomy(SettlementState settlement, Map<String, Integer> population, Map<String, Integer> wealth, Map<String, Integer> stock) {
		return settlement.withSimulationState(
			population,
			wealth,
			stock,
			settlement.housingCapacity(),
			settlement.comfort(),
			settlement.security(),
			settlement.defenseLevel(),
			settlement.growthProgress(),
			settlement.projects(),
			settlement.lastUpdateTick()
		);
	}

	private static List<String> sortedLootKeys(Map<String, Integer> stock) {
		List<String> keys = new ArrayList<>(stock.keySet());
		keys.sort(Comparator.comparingInt(OutpostRaids::lootPriority).thenComparing(key -> key));
		return keys;
	}

	private static int lootPriority(String goodsKey) {
		if (OutpostGear.isValuedExactItem(goodsKey)) {
			return 1;
		}

		int priorityIndex = LOOT_PRIORITY.indexOf(goodsKey);
		return priorityIndex >= 0 ? priorityIndex * 2 : 100;
	}

	private static int transferableStock(Map<String, Integer> stock) {
		return stock.values().stream()
			.mapToInt(amount -> Math.max(0, amount))
			.sum();
	}

	private static int transferableValue(SettlementState settlement) {
		return transferableStock(settlement.stock())
			+ Math.max(0, settlement.wealth().getOrDefault("emerald", 0));
	}

	private static void musterLoadedRaiders(ServerLevel level, SettlementState outpost, SettlementState target, int partySize) {
		BlockPos musterPos = raidMusterPos(level, outpost, target);
		AABB bounds = new AABB(outpost.center()).inflate(CONTROL_RADIUS_BLOCKS, 64.0D, CONTROL_RADIUS_BLOCKS);
		List<Raider> candidates = level.getEntitiesOfClass(Raider.class, bounds, raider -> raider.isAlive() && !raider.isRemoved()).stream()
			.sorted(Comparator.comparingDouble(raider -> raider.blockPosition().distSqr(musterPos)))
			.toList();
		List<Raider> groundRaiders = candidates.stream()
			.filter(raider -> Math.abs(raider.blockPosition().getY() - musterPos.getY()) <= 8)
			.limit(Math.max(MIN_PARTY_SIZE, partySize))
			.toList();
		List<Raider> raiders = groundRaiders.size() >= Math.min(MIN_PARTY_SIZE, partySize)
			? groundRaiders
			: candidates.stream()
				.limit(Math.max(MIN_PARTY_SIZE, partySize))
				.toList();
		if (raiders.isEmpty()) {
			return;
		}

		Raider bannerCarrier = raiders.stream()
			.filter(raider -> raider.entityTags().contains(MUSTER_BANNER_TAG))
			.findFirst()
			.orElse(raiders.get(0));
		bannerCarrier.addTag(MUSTER_BANNER_TAG);
		bannerCarrier.setPatrolLeader(true);
		bannerCarrier.setItemSlot(
			EquipmentSlot.HEAD,
			Raid.getOminousBannerInstance(level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN))
		);

		BlockPos marchWaypoint = raidMarchWaypoint(level, outpost, target);
		int raiderIndex = 0;
		for (Raider raider : raiders) {
			OutpostSettlementWork.markOutpostRaidMember(raider, outpost);
			raider.setCanJoinRaid(true);
			raider.setPatrolTarget(marchWaypoint);
			moveStuckTowerRaiderToMuster(level, outpost, musterPos, raider, raiderIndex);
			if (raider.distanceToSqr(musterPos.getX() + 0.5D, musterPos.getY(), musterPos.getZ() + 0.5D) > 36.0D) {
				raider.getNavigation().moveTo(musterPos.getX() + 0.5D, musterPos.getY() + 1.0D, musterPos.getZ() + 0.5D, 0.85D);
			}

			raider.lookAt(bannerCarrier, 30.0F, 30.0F);
			raiderIndex++;
		}
	}

	private static void moveStuckTowerRaiderToMuster(ServerLevel level, SettlementState outpost, BlockPos musterPos, Raider raider, int raiderIndex) {
		if (raider.blockPosition().getY() <= musterPos.getY() + 10
			|| horizontalDistanceSqr(raider.blockPosition(), outpost.center()) > CONTROL_RADIUS_BLOCKS * CONTROL_RADIUS_BLOCKS
			|| raider.getNavigation().createPath(musterPos, 0) != null) {
			return;
		}

		BlockPos slot = musterSlot(level, musterPos, raiderIndex);
		raider.teleportTo(slot.getX() + 0.5D, slot.getY() + 1.0D, slot.getZ() + 0.5D);
		raider.getNavigation().stop();
	}

	private static BlockPos musterSlot(ServerLevel level, BlockPos musterPos, int raiderIndex) {
		int ring = 1 + raiderIndex / 8;
		int step = raiderIndex % 8;
		int dx = switch (step) {
			case 1, 2, 3 -> ring;
			case 5, 6, 7 -> -ring;
			default -> 0;
		};
		int dz = switch (step) {
			case 0, 1, 7 -> -ring;
			case 3, 4, 5 -> ring;
			default -> 0;
		};
		return surfacePos(level, musterPos.getX() + dx, musterPos.getZ() + dz).orElse(musterPos);
	}

	private static void steerLoadedRaiders(ServerLevel level, SettlementState outpost, SettlementState target, int partySize) {
		steerLoadedRaidersToward(level, outpost, target, partySize, false);
	}

	private static void steerLoadedRaidersHome(MinecraftServer server, SettlementState outpost, SettlementState target, int partySize) {
		ServerLevel level = server.getLevel(outpost.dimension());
		if (level != null) {
			steerLoadedRaidersToward(level, outpost, target, partySize, true);
		}
	}

	private static void steerLoadedRaidersToward(ServerLevel level, SettlementState outpost, SettlementState target, int partySize, boolean returning) {
		AABB bounds = between(outpost.center(), target.center()).inflate(RAID_PARTY_ENTITY_SEARCH_MARGIN_BLOCKS, 64.0D, RAID_PARTY_ENTITY_SEARCH_MARGIN_BLOCKS);
		List<Raider> raiders = level.getEntitiesOfClass(Raider.class, bounds, raider ->
			raider.isAlive()
				&& !raider.isRemoved()
				&& OutpostSettlementWork.isActiveRaidMember(raider, outpost)).stream()
			.sorted(Comparator.comparingDouble(raider -> Math.min(
				raider.blockPosition().distSqr(outpost.center()),
				raider.blockPosition().distSqr(target.center())
			)))
			.limit(Math.max(MIN_PARTY_SIZE, partySize))
			.toList();

		if (raiders.isEmpty()) {
			return;
		}

		BlockPos waypoint = returning ? null : raidMarchWaypoint(level, outpost, target);
		BlockPos destination = returning ? null : raidTargetControlPos(level, target);

		for (Raider raider : raiders) {
			BlockPos currentTarget = returning
				? raidReturnTargetFor(level, outpost, target, raider.blockPosition())
				: hasReachedRaidWaypoint(raider.blockPosition(), waypoint, destination)
					? destination
					: waypoint;
			raider.setCanJoinRaid(true);
			raider.setPatrolTarget(currentTarget);
			if (raider.getNavigation().isDone() || horizontalDistanceSqr(raider.blockPosition(), currentTarget) > 8.0D * 8.0D) {
				raider.getNavigation().moveTo(currentTarget.getX() + 0.5D, currentTarget.getY() + 1.0D, currentTarget.getZ() + 0.5D, 0.9D);
			}
		}
	}

	private static boolean hasReachedRaidWaypoint(BlockPos currentPos, BlockPos waypoint, BlockPos destination) {
		if (horizontalDistanceSqr(currentPos, waypoint) <= RAID_WAYPOINT_REACHED_RADIUS_BLOCKS * RAID_WAYPOINT_REACHED_RADIUS_BLOCKS) {
			return true;
		}

		double destinationX = destination.getX() - waypoint.getX();
		double destinationZ = destination.getZ() - waypoint.getZ();
		double currentX = currentPos.getX() - waypoint.getX();
		double currentZ = currentPos.getZ() - waypoint.getZ();
		return destinationX * currentX + destinationZ * currentZ > 0.0D;
	}

	private static RaidArrivalStatus raidArrivalStatus(ServerLevel level, SettlementState outpost, SettlementState target, int partySize) {
		BlockPos targetPos = raidTargetControlPos(level, target);
		AABB bounds = between(outpost.center(), target.center()).inflate(RAID_PARTY_ENTITY_SEARCH_MARGIN_BLOCKS, 64.0D, RAID_PARTY_ENTITY_SEARCH_MARGIN_BLOCKS);
		List<Raider> activeRaiders = level.getEntitiesOfClass(Raider.class, bounds, raider ->
			raider.isAlive()
				&& !raider.isRemoved()
				&& OutpostSettlementWork.isActiveRaidMember(raider, outpost));

		if (activeRaiders.isEmpty()) {
			return new RaidArrivalStatus(true, 0, requiredArrivalCount(partySize, 0), 0, targetPos, null, 0.0D);
		}

		int required = requiredArrivalCount(partySize, activeRaiders.size());
		long arrived = activeRaiders.stream()
			.filter(raider -> horizontalDistanceSqr(raider.blockPosition(), targetPos) <= RAID_ARRIVAL_RADIUS_BLOCKS * RAID_ARRIVAL_RADIUS_BLOCKS)
			.count();
		Raider closestRaider = activeRaiders.stream()
			.min(Comparator.comparingDouble(raider -> horizontalDistanceSqr(raider.blockPosition(), targetPos)))
			.orElse(null);
		BlockPos closestPos = closestRaider == null ? null : closestRaider.blockPosition();
		double closestDistanceSqr = closestRaider == null ? 0.0D : horizontalDistanceSqr(closestPos, targetPos);
		return new RaidArrivalStatus(true, activeRaiders.size(), required, arrived, targetPos, closestPos, closestDistanceSqr);
	}

	private static int requiredArrivalCount(int partySize, int activeRaiders) {
		int originalRequired = Math.min(partySize, Math.max(MIN_PARTY_SIZE, (partySize + 1) / 2));
		return activeRaiders <= 0 ? originalRequired : Math.min(activeRaiders, originalRequired);
	}

	private static void logMarchProgress(
		int serverTick,
		ServerLevel level,
		SettlementState outpost,
		SettlementState target,
		int partySize,
		long elapsedTicks,
		long marchTicks,
		boolean targetLoaded,
		boolean targetTicking
	) {
		if (serverTick % 200 != 0) {
			return;
		}

		RaidArrivalStatus status = level == null
			? RaidArrivalStatus.unobserved(target.center(), partySize)
			: raidArrivalStatus(level, outpost, target, partySize);
		BlockPos waypoint = level == null ? target.center() : raidMarchWaypoint(level, outpost, target);
		double closestWaypointDistance = status.closestRaiderPos() == null ? 0.0D : Math.sqrt(horizontalDistanceSqr(status.closestRaiderPos(), waypoint));
		LiveVillages.LOGGER.info(
			"Outpost raid march: outpost={} target={} elapsed={}/{} targetLoaded={} targetTicking={} active={} arrived={}/{} control={} closest={} closestDistance={} waypoint={} waypointDistance={}",
			outpost.name(),
			target.name(),
			elapsedTicks,
			marchTicks,
			targetLoaded,
			targetTicking,
			status.activeRaiders(),
			status.arrivedRaiders(),
			status.requiredRaiders(),
			shortPos(status.targetPos()),
			shortPos(status.closestRaiderPos()),
			Math.round(Math.sqrt(status.closestDistanceSqr())),
			shortPos(waypoint),
			Math.round(closestWaypointDistance)
		);
	}

	private static String shortPos(BlockPos pos) {
		return pos == null ? "none" : pos.toShortString();
	}

	private static RaidReturnStatus raidReturnStatus(ServerLevel level, SettlementState outpost, SettlementState target) {
		BlockPos homePos = raidMusterPos(level, outpost, target);
		List<Raider> activeRaiders = activeRaidersBetween(level, outpost, target);
		if (activeRaiders.isEmpty()) {
			return new RaidReturnStatus(true, 0, 0, homePos, null, 0.0D);
		}

		long homeRaiders = activeRaiders.stream()
			.filter(raider -> horizontalDistanceSqr(raider.blockPosition(), homePos) <= RAID_RETURN_HOME_RADIUS_BLOCKS * RAID_RETURN_HOME_RADIUS_BLOCKS)
			.count();
		Raider closestRaider = activeRaiders.stream()
			.min(Comparator.comparingDouble(raider -> horizontalDistanceSqr(raider.blockPosition(), homePos)))
			.orElse(null);
		BlockPos closestPos = closestRaider == null ? null : closestRaider.blockPosition();
		double closestDistanceSqr = closestRaider == null ? 0.0D : horizontalDistanceSqr(closestPos, homePos);
		return new RaidReturnStatus(true, activeRaiders.size(), homeRaiders, homePos, closestPos, closestDistanceSqr);
	}

	private static List<Raider> activeRaidersBetween(ServerLevel level, SettlementState outpost, SettlementState target) {
		AABB bounds = between(outpost.center(), target.center()).inflate(RAID_PARTY_ENTITY_SEARCH_MARGIN_BLOCKS, 64.0D, RAID_PARTY_ENTITY_SEARCH_MARGIN_BLOCKS);
		return level.getEntitiesOfClass(Raider.class, bounds, raider ->
			raider.isAlive()
				&& !raider.isRemoved()
				&& OutpostSettlementWork.isActiveRaidMember(raider, outpost));
	}

	private static void logReturnProgress(
		int serverTick,
		RaidReturnStatus status,
		SettlementState outpost,
		SettlementState target,
		long elapsedTicks,
		long returnTicks
	) {
		if (serverTick % 200 != 0) {
			return;
		}

		LiveVillages.LOGGER.info(
			"Outpost raid return: outpost={} target={} elapsed={}/{} active={} home={} homePos={} closest={} closestHomeDistance={}",
			outpost.name(),
			target.name(),
			elapsedTicks,
			returnTicks,
			status.activeRaiders(),
			status.homeRaiders(),
			shortPos(status.homePos()),
			shortPos(status.closestRaiderPos()),
			Math.round(Math.sqrt(status.closestHomeDistanceSqr()))
		);
	}

	private record RaidArrivalStatus(
		boolean observed,
		int activeRaiders,
		int requiredRaiders,
		long arrivedRaiders,
		BlockPos targetPos,
		BlockPos closestRaiderPos,
		double closestDistanceSqr
	) {
		static RaidArrivalStatus unobserved(BlockPos targetPos, int partySize) {
			return new RaidArrivalStatus(false, 0, requiredArrivalCount(partySize, 0), 0, targetPos, null, 0.0D);
		}

		boolean arrived() {
			return activeRaiders >= MIN_PARTY_SIZE && arrivedRaiders >= requiredRaiders;
		}
	}

	private record RaidReturnStatus(
		boolean observed,
		int activeRaiders,
		long homeRaiders,
		BlockPos homePos,
		BlockPos closestRaiderPos,
		double closestHomeDistanceSqr
	) {
		static RaidReturnStatus unobserved(BlockPos homePos) {
			return new RaidReturnStatus(false, 0, 0, homePos, null, 0.0D);
		}
	}

	private static void clearReturnedRaiders(MinecraftServer server, SettlementState outpost, SettlementState target) {
		ServerLevel level = server.getLevel(outpost.dimension());
		if (level == null) {
			return;
		}

		BlockPos homePos = raidMusterPos(level, outpost, target);
		for (Raider raider : activeRaidersBetween(level, outpost, target)) {
			if (horizontalDistanceSqr(raider.blockPosition(), homePos) > RAID_RETURN_HOME_RADIUS_BLOCKS * RAID_RETURN_HOME_RADIUS_BLOCKS) {
				continue;
			}

			OutpostSettlementWork.clearActiveRaidMember(raider);
		}
	}

	private static void recoverReturningRaiders(ServerLevel level, SettlementState outpost, SettlementState target) {
		BlockPos homePos = raidMusterPos(level, outpost, target);
		int recovered = 0;
		int index = 0;
		for (Raider raider : activeRaidersBetween(level, outpost, target)) {
			if (horizontalDistanceSqr(raider.blockPosition(), homePos) <= RAID_RETURN_HOME_RADIUS_BLOCKS * RAID_RETURN_HOME_RADIUS_BLOCKS) {
				index++;
				continue;
			}

			BlockPos slot = musterSlot(level, homePos, index);
			raider.teleportTo(slot.getX() + 0.5D, slot.getY() + 1.0D, slot.getZ() + 0.5D);
			raider.getNavigation().stop();
			recovered++;
			index++;
		}

		if (recovered > 0) {
			LiveVillages.LOGGER.info(
				"Recovered {} returning outpost raid stragglers to {} near {}.",
				recovered,
				outpost.name(),
				shortPos(homePos)
			);
		}
	}

	private static BlockPos raidMarchWaypoint(ServerLevel level, SettlementState outpost, SettlementState target) {
		BlockPos destination = raidTargetControlPos(level, target);
		if (!lineHasWaterOrUnknown(level, outpost.center(), destination, RAID_WATER_SCAN_BLOCKS)) {
			return destination;
		}

		double dx = destination.getX() - outpost.center().getX();
		double dz = destination.getZ() - outpost.center().getZ();
		double distance = Math.hypot(dx, dz);
		if (distance < 1.0D) {
			return destination;
		}

		double forwardX = dx / distance;
		double forwardZ = dz / distance;
		double lateralX = -forwardZ;
		double lateralZ = forwardX;
		BlockPos best = null;
		double bestScore = Double.POSITIVE_INFINITY;

		for (int forward = RAID_LAND_WAYPOINT_STEP_BLOCKS * 2; forward <= RAID_LAND_WAYPOINT_MAX_BLOCKS; forward += RAID_LAND_WAYPOINT_STEP_BLOCKS) {
			for (int lateral = -RAID_LAND_WAYPOINT_LATERAL_BLOCKS; lateral <= RAID_LAND_WAYPOINT_LATERAL_BLOCKS; lateral += RAID_LAND_WAYPOINT_STEP_BLOCKS) {
				int x = (int) Math.round(outpost.center().getX() + forwardX * forward + lateralX * lateral);
				int z = (int) Math.round(outpost.center().getZ() + forwardZ * forward + lateralZ * lateral);
				Optional<BlockPos> surface = surfacePos(level, x, z);

				if (surface.isEmpty() || lineHasWaterOrUnknown(level, outpost.center(), surface.get(), forward + Math.abs(lateral))) {
					continue;
				}

				double score = horizontalDistanceSqr(surface.get(), destination)
					+ Math.abs(lateral) * 8.0D
					+ (RAID_LAND_WAYPOINT_MAX_BLOCKS - forward) * 2.0D
					- pathSurfaceBonus(level.getBlockState(surface.get()));

				if (score < bestScore) {
					best = surface.get();
					bestScore = score;
				}
			}
		}

		return best == null ? destination : best;
	}

	private static BlockPos raidReturnWaypoint(ServerLevel level, SettlementState outpost, SettlementState target) {
		BlockPos homePos = raidMusterPos(level, outpost, target);
		return raidLandWaypoint(level, raidTargetControlPos(level, target), homePos).orElse(homePos);
	}

	private static BlockPos raidReturnTargetFor(ServerLevel level, SettlementState outpost, SettlementState target, BlockPos currentPos) {
		BlockPos homePos = raidMusterPos(level, outpost, target);
		if (horizontalDistanceSqr(currentPos, homePos) <= 12.0D * 12.0D
			|| !lineHasWaterOrUnknown(level, currentPos, homePos, RAID_WATER_SCAN_BLOCKS)) {
			return homePos;
		}

		return raidLandWaypoint(level, currentPos, homePos).orElse(homePos);
	}

	private static Optional<BlockPos> raidLandWaypoint(ServerLevel level, BlockPos from, BlockPos to) {
		double dx = to.getX() - from.getX();
		double dz = to.getZ() - from.getZ();
		double distance = Math.hypot(dx, dz);
		if (distance < 1.0D) {
			return Optional.of(to);
		}

		double forwardX = dx / distance;
		double forwardZ = dz / distance;
		double lateralX = -forwardZ;
		double lateralZ = forwardX;
		BlockPos best = null;
		double bestScore = Double.POSITIVE_INFINITY;

		for (int forward = RAID_LAND_WAYPOINT_STEP_BLOCKS * 2; forward <= RAID_LAND_WAYPOINT_MAX_BLOCKS; forward += RAID_LAND_WAYPOINT_STEP_BLOCKS) {
			for (int lateral = -RAID_LAND_WAYPOINT_LATERAL_BLOCKS; lateral <= RAID_LAND_WAYPOINT_LATERAL_BLOCKS; lateral += RAID_LAND_WAYPOINT_STEP_BLOCKS) {
				int x = (int) Math.round(from.getX() + forwardX * forward + lateralX * lateral);
				int z = (int) Math.round(from.getZ() + forwardZ * forward + lateralZ * lateral);
				Optional<BlockPos> surface = surfacePos(level, x, z);

				if (surface.isEmpty() || lineHasWaterOrUnknown(level, from, surface.get(), forward + Math.abs(lateral))) {
					continue;
				}

				double score = horizontalDistanceSqr(surface.get(), to)
					+ Math.abs(lateral) * 8.0D
					+ (RAID_LAND_WAYPOINT_MAX_BLOCKS - forward) * 2.0D
					- pathSurfaceBonus(level.getBlockState(surface.get()));

				if (score < bestScore) {
					best = surface.get();
					bestScore = score;
				}
			}
		}

		return Optional.ofNullable(best);
	}

	private static BlockPos raidMusterPos(ServerLevel level, SettlementState outpost, SettlementState target) {
		double dx = target.center().getX() - outpost.center().getX();
		double dz = target.center().getZ() - outpost.center().getZ();
		double distance = Math.hypot(dx, dz);

		if (distance < 1.0D) {
			return surfacePos(level, outpost.center().getX() + MUSTER_GROUND_MIN_BLOCKS, outpost.center().getZ()).orElse(outpost.center());
		}

		double forwardX = dx / distance;
		double forwardZ = dz / distance;
		double lateralX = -forwardZ;
		double lateralZ = forwardX;
		BlockPos best = null;
		double bestScore = Double.POSITIVE_INFINITY;

		for (int forward = MUSTER_GROUND_MIN_BLOCKS; forward <= MUSTER_GROUND_MAX_BLOCKS; forward += MUSTER_GROUND_STEP_BLOCKS) {
			for (int lateral = -MUSTER_GROUND_LATERAL_BLOCKS; lateral <= MUSTER_GROUND_LATERAL_BLOCKS; lateral += MUSTER_GROUND_STEP_BLOCKS) {
				int x = (int) Math.round(outpost.center().getX() + forwardX * forward + lateralX * lateral);
				int z = (int) Math.round(outpost.center().getZ() + forwardZ * forward + lateralZ * lateral);
				Optional<BlockPos> surface = surfacePos(level, x, z);

				if (surface.isEmpty()) {
					continue;
				}

				double score = Math.abs(lateral) * 6.0D
					+ Math.abs(forward - MUSTER_GROUND_MIN_BLOCKS)
					+ Math.max(0, surface.get().getY() - outpost.center().getY()) * 12.0D
					- pathSurfaceBonus(level.getBlockState(surface.get()));

				if (score < bestScore) {
					best = surface.get();
					bestScore = score;
				}
			}
		}

		return best == null ? outpost.center() : best;
	}

	private static BlockPos raidMusterPosFallback(SettlementState outpost, SettlementState target) {
		double dx = target.center().getX() - outpost.center().getX();
		double dz = target.center().getZ() - outpost.center().getZ();
		double distance = Math.hypot(dx, dz);
		if (distance < 1.0D) {
			return outpost.center();
		}

		int x = (int) Math.round(outpost.center().getX() + (dx / distance) * MUSTER_GROUND_MIN_BLOCKS);
		int z = (int) Math.round(outpost.center().getZ() + (dz / distance) * MUSTER_GROUND_MIN_BLOCKS);
		return new BlockPos(x, outpost.center().getY(), z);
	}

	private static BlockPos raidTargetControlPos(ServerLevel level, SettlementState target) {
		Optional<BlockPos> centerSurface = surfacePos(level, target.center().getX(), target.center().getZ());
		BlockPos best = centerSurface.orElse(null);
		double bestScore = best == null ? Double.POSITIVE_INFINITY : raidTargetControlScore(level, target, best);

		for (int dx = -TARGET_CONTROL_SEARCH_RADIUS_BLOCKS; dx <= TARGET_CONTROL_SEARCH_RADIUS_BLOCKS; dx += TARGET_CONTROL_SEARCH_STEP_BLOCKS) {
			for (int dz = -TARGET_CONTROL_SEARCH_RADIUS_BLOCKS; dz <= TARGET_CONTROL_SEARCH_RADIUS_BLOCKS; dz += TARGET_CONTROL_SEARCH_STEP_BLOCKS) {
				if (dx * dx + dz * dz > TARGET_CONTROL_SEARCH_RADIUS_BLOCKS * TARGET_CONTROL_SEARCH_RADIUS_BLOCKS) {
					continue;
				}

				Optional<BlockPos> surface = surfacePos(level, target.center().getX() + dx, target.center().getZ() + dz);
				if (surface.isEmpty()) {
					continue;
				}

				double score = raidTargetControlScore(level, target, surface.get());
				if (score < bestScore) {
					best = surface.get();
					bestScore = score;
				}
			}
		}

		return best == null ? target.center() : best;
	}

	private static double raidTargetControlScore(ServerLevel level, SettlementState target, BlockPos surface) {
		return horizontalDistanceSqr(surface, target.center())
			+ Math.abs(surface.getY() - target.center().getY()) * 64.0D
			- pathSurfaceBonus(level.getBlockState(surface));
	}

	private static boolean lineHasWaterOrUnknown(ServerLevel level, BlockPos from, BlockPos to, int maxDistanceBlocks) {
		double dx = to.getX() - from.getX();
		double dz = to.getZ() - from.getZ();
		int steps = Math.max(1, Math.min(maxDistanceBlocks, (int) Math.ceil(Math.hypot(dx, dz))));

		for (int step = 1; step <= steps; step++) {
			double t = step / (double) steps;
			int x = (int) Math.round(from.getX() + dx * t);
			int z = (int) Math.round(from.getZ() + dz * t);
			Optional<BlockPos> surface = surfacePos(level, x, z);

			if (surface.isEmpty()) {
				return true;
			}
		}

		return false;
	}

	private static Optional<BlockPos> surfacePos(ServerLevel level, int x, int z) {
		BlockPos columnPos = new BlockPos(x, level.getMinY(), z);
		if (!level.hasChunkAt(columnPos)) {
			return Optional.empty();
		}

		int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		int minY = Math.max(level.getMinY(), topY - 4);

		for (int y = topY; y >= minY; y--) {
			BlockPos surface = new BlockPos(x, y, z);
			if (isDryStandable(level, surface)) {
				return Optional.of(surface);
			}
		}

		return Optional.empty();
	}

	private static boolean isDryStandable(ServerLevel level, BlockPos surface) {
		BlockState surfaceState = level.getBlockState(surface);
		BlockState aboveState = level.getBlockState(surface.above());
		BlockState headState = level.getBlockState(surface.above(2));
		return surfaceState.getFluidState().isEmpty()
			&& aboveState.getFluidState().isEmpty()
			&& headState.getFluidState().isEmpty()
			&& !surfaceState.getCollisionShape(level, surface).isEmpty()
			&& (aboveState.isAir() || aboveState.canBeReplaced());
	}

	private static double pathSurfaceBonus(BlockState state) {
		if (state.is(Blocks.DIRT_PATH)) {
			return 32_000.0D;
		}

		if (state.is(Blocks.COBBLESTONE)
			|| state.is(Blocks.COBBLESTONE_STAIRS)
			|| state.is(Blocks.COBBLESTONE_SLAB)
			|| state.is(Blocks.SMOOTH_STONE)
			|| state.is(Blocks.SMOOTH_STONE_SLAB)
			|| state.is(Blocks.POLISHED_GRANITE)
			|| state.is(Blocks.POLISHED_DIORITE)
			|| state.is(Blocks.POLISHED_ANDESITE)
			|| state.is(Blocks.STONE_BRICKS)
			|| state.is(Blocks.STONE_BRICK_STAIRS)
			|| state.is(Blocks.STONE_BRICK_SLAB)) {
			return 16_000.0D;
		}

		return 0.0D;
	}

	private static Map<String, Integer> awardParticipants(
		MinecraftServer server,
		LiveVillagesSavedData savedData,
		SettlementState outpost,
		SettlementState target,
		int support,
		String message
	) {
		Map<String, Integer> rewards = new LinkedHashMap<>();

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.level().dimension().equals(outpost.dimension())) {
				continue;
			}

			boolean nearTarget = horizontalDistanceSqr(player.blockPosition(), target.center()) <= PARTICIPATION_RADIUS_BLOCKS * PARTICIPATION_RADIUS_BLOCKS;
			boolean nearOutpost = horizontalDistanceSqr(player.blockPosition(), outpost.center()) <= PARTICIPATION_RADIUS_BLOCKS * PARTICIPATION_RADIUS_BLOCKS;
			if (!nearTarget && !nearOutpost) {
				continue;
			}

			if (!savedData.settlementPlayerStanding(outpost, player).rank().atLeast(OutpostPlayerRank.BANNER_BEARER)) {
				continue;
			}

			SettlementPlayerStandings.recordSupport((ServerLevel) player.level(), player, outpost, support);
			player.sendSystemMessage(Component.literal(message + " +" + support + " support."), true);
			rewards.put(player.getGameProfile().name(), support);
		}

		return rewards;
	}

	private static OutpostRaidState announceIfDue(
		MinecraftServer server,
		LiveVillagesSavedData savedData,
		SettlementState outpost,
		SettlementState target,
		OutpostRaidState raidState,
		long currentTick,
		String verb
	) {
		if (raidState.lastAnnouncementTick() > 0L && currentTick - raidState.lastAnnouncementTick() < 6_000L) {
			return raidState;
		}

		announce(server, savedData, outpost, target, raidState, verb);
		return raidState.withAnnouncementTick(currentTick);
	}

	private static void announce(
		MinecraftServer server,
		LiveVillagesSavedData savedData,
		SettlementState outpost,
		SettlementState target,
		OutpostRaidState raidState,
		String verb
	) {
		String message = outpost.name() + " is " + verb + " " + target.name() + " with " + raidState.partySize() + " raiders.";

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.level().dimension().equals(outpost.dimension())) {
				continue;
			}

			boolean nearOutpost = horizontalDistanceSqr(player.blockPosition(), outpost.center()) <= BANNER_NOTICE_RADIUS_BLOCKS * BANNER_NOTICE_RADIUS_BLOCKS;
			boolean nearTarget = horizontalDistanceSqr(player.blockPosition(), target.center()) <= BANNER_NOTICE_RADIUS_BLOCKS * BANNER_NOTICE_RADIUS_BLOCKS;
			if (!nearOutpost && !nearTarget) {
				continue;
			}

			if (!savedData.settlementPlayerStanding(outpost, player).rank().atLeast(OutpostPlayerRank.BANNER_BEARER)) {
				continue;
			}

			player.sendSystemMessage(Component.literal(message), true);
		}
	}

	private static void announceRaidMemberDeath(
		MinecraftServer server,
		LiveVillagesSavedData savedData,
		SettlementState outpost,
		SettlementState target,
		Raider raider
	) {
		String message = raider.getType().getDescription().getString()
			+ " from clan "
			+ outpost.name()
			+ " was killed in a raid on "
			+ target.name()
			+ ".";

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.level().dimension().equals(outpost.dimension())) {
				continue;
			}

			boolean nearOutpost = horizontalDistanceSqr(player.blockPosition(), outpost.center()) <= BANNER_NOTICE_RADIUS_BLOCKS * BANNER_NOTICE_RADIUS_BLOCKS;
			boolean nearTarget = horizontalDistanceSqr(player.blockPosition(), target.center()) <= BANNER_NOTICE_RADIUS_BLOCKS * BANNER_NOTICE_RADIUS_BLOCKS;
			boolean nearDeath = horizontalDistanceSqr(player.blockPosition(), raider.blockPosition()) <= BANNER_NOTICE_RADIUS_BLOCKS * BANNER_NOTICE_RADIUS_BLOCKS;
			if (!nearOutpost && !nearTarget && !nearDeath) {
				continue;
			}

			if (!savedData.settlementPlayerStanding(outpost, player).rank().atLeast(OutpostPlayerRank.BANNER_BEARER)) {
				continue;
			}

			player.sendSystemMessage(Component.literal(message));
		}
	}

	private static boolean hasNearbyBannerBearer(
		MinecraftServer server,
		LiveVillagesSavedData savedData,
		SettlementState outpost,
		BlockPos center,
		int radiusBlocks
	) {
		double radiusSquared = radiusBlocks * radiusBlocks;

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.level().dimension().equals(outpost.dimension())) {
				continue;
			}

			if (horizontalDistanceSqr(player.blockPosition(), center) > radiusSquared) {
				continue;
			}

			if (savedData.settlementPlayerStanding(outpost, player).rank().atLeast(OutpostPlayerRank.BANNER_BEARER)) {
				return true;
			}
		}

		return false;
	}

	private static int loadedRaiderCount(ServerLevel level, SettlementState outpost) {
		return OutpostSettlementWork.censusPopulation(level, outpost).entrySet().stream()
			.filter(entry -> entry.getKey().equals(SettlementRoleKeys.PILLAGER) || entry.getKey().equals(SettlementRoleKeys.GUARD))
			.mapToInt(Map.Entry::getValue)
			.sum();
	}

	private static int countNearbyGolems(ServerLevel level, SettlementState target) {
		AABB bounds = new AABB(target.center()).inflate(CONTROL_RADIUS_BLOCKS, 24.0D, CONTROL_RADIUS_BLOCKS);
		return level.getEntitiesOfClass(
			LivingEntity.class,
			bounds,
			entity -> entity.getType() == EntityType.IRON_GOLEM && entity.isAlive() && !entity.isRemoved()
		).size();
	}

	private static long marchTicks(SettlementState outpost, SettlementState target) {
		double distance = Math.sqrt(horizontalDistanceSqr(outpost.center(), target.center()));
		long ticks = Math.round(distance * 4.0D);
		return Math.max(MIN_MARCH_TICKS, Math.min(MAX_MARCH_TICKS, ticks));
	}

	private static long returnTicks(SettlementState outpost, SettlementState target) {
		double distance = Math.sqrt(horizontalDistanceSqr(outpost.center(), target.center()));
		long ticks = Math.round(distance * 3.0D);
		return Math.max(MIN_RETURN_TICKS, Math.min(MAX_RETURN_TICKS, ticks));
	}

	private static double horizontalDistanceSqr(BlockPos first, BlockPos second) {
		double dx = (first.getX() + 0.5D) - (second.getX() + 0.5D);
		double dz = (first.getZ() + 0.5D) - (second.getZ() + 0.5D);
		return (dx * dx) + (dz * dz);
	}

	private static AABB between(BlockPos first, BlockPos second) {
		return new AABB(
			Math.min(first.getX(), second.getX()),
			Math.min(first.getY(), second.getY()),
			Math.min(first.getZ(), second.getZ()),
			Math.max(first.getX(), second.getX()) + 1.0D,
			Math.max(first.getY(), second.getY()) + 1.0D,
			Math.max(first.getZ(), second.getZ()) + 1.0D
		);
	}

	private static String formatTicks(long ticks) {
		long days = ticks / (long) SettlementEconomyRules.TICKS_PER_DAY;
		long hours = (ticks % (long) SettlementEconomyRules.TICKS_PER_DAY) / 1_000L;

		if (days > 0) {
			return days + "d " + hours + "h";
		}

		return hours + "h";
	}

	private record LootTransfer(Map<String, Integer> transferred) {
		private int totalTransferred() {
			return transferred.values().stream().mapToInt(Integer::intValue).sum();
		}
	}
}
