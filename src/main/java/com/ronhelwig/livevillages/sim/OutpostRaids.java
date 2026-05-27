package com.ronhelwig.livevillages.sim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

public final class OutpostRaids {
	private static final long RAID_COOLDOWN_TICKS = (long) SettlementEconomyRules.TICKS_PER_DAY * 7L;
	private static final long MUSTER_TICKS = 1_200L;
	private static final long MIN_MARCH_TICKS = 2_400L;
	private static final long MAX_MARCH_TICKS = 12_000L;
	private static final long RAID_MAX_TICKS = 6_000L;
	private static final int CONTROL_REQUIRED_TICKS = 1_800;
	private static final int CONTROL_RADIUS_BLOCKS = 32;
	private static final int TARGET_SEARCH_RADIUS_BLOCKS = 1_024;
	private static final int BANNER_NOTICE_RADIUS_BLOCKS = 256;
	private static final int PARTICIPATION_RADIUS_BLOCKS = 160;
	private static final int RAID_PARTY_ENTITY_SEARCH_MARGIN_BLOCKS = 96;
	private static final int RAID_LAND_WAYPOINT_MAX_BLOCKS = 128;
	private static final int RAID_LAND_WAYPOINT_STEP_BLOCKS = 16;
	private static final int RAID_LAND_WAYPOINT_LATERAL_BLOCKS = 96;
	private static final int RAID_WATER_SCAN_BLOCKS = 128;
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
		"bread",
		"beef",
		"mutton",
		"pork",
		"cod",
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
			case COOLDOWN -> raidState.outcome().isBlank()
				? "Raid cooldown: next eligible in " + formatTicks(Math.max(0L, raidState.nextEligibleTick() - currentTick))
				: "Last raid: " + raidState.outcome() + "; next eligible in " + formatTicks(Math.max(0L, raidState.nextEligibleTick() - currentTick));
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

		if (currentTick - updated.phaseStartedTick() < marchTicks) {
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

		if (raidState.controlProgressTicks() == 0 && shouldPayTribute(target, raidState, nearbyGolems)) {
			LootTransfer transfer = transferLoot(savedData, outpost, target, raidState.partySize(), true);
			damageTargetSecurity(savedData, target, TRIBUTE_SECURITY_PENALTY);
			Map<String, Integer> rewards = awardParticipants(server, savedData, outpost, target, TRIBUTE_SUPPORT, target.name() + " paid tribute to " + outpost.name() + ".");
			OutpostRaidState completed = raidState.completed(
				"paid off by " + target.name() + " (" + transfer.totalTransferred() + " goods)",
				currentTick,
				currentTick + RAID_COOLDOWN_TICKS,
				transfer.transferred(),
				rewards
			);
			savedData.putOutpostRaidState(completed);
			return true;
		}

		int progress = raidState.controlProgressTicks() + controlProgressThisTick(target, raidState, nearbyGolems);
		if (progress >= CONTROL_REQUIRED_TICKS) {
			LootTransfer transfer = transferLoot(savedData, outpost, target, raidState.partySize(), false);
			damageTargetSecurity(savedData, target, RAID_SUCCESS_SECURITY_PENALTY);
			Map<String, Integer> rewards = awardParticipants(server, savedData, outpost, target, RAID_SUCCESS_SUPPORT, outpost.name() + " successfully raided " + target.name() + ".");
			OutpostRaidState completed = raidState.completed(
				"successful raid on " + target.name() + " (" + transfer.totalTransferred() + " goods)",
				currentTick,
				currentTick + RAID_COOLDOWN_TICKS,
				transfer.transferred(),
				rewards
			);
			savedData.putOutpostRaidState(completed);
			return true;
		}

		if (currentTick - raidState.phaseStartedTick() >= RAID_MAX_TICKS) {
			damageTargetSecurity(savedData, target, RAID_REPELLED_SECURITY_PENALTY);
			OutpostRaidState completed = raidState.completed(
				"repelled at " + target.name(),
				currentTick,
				currentTick + RAID_COOLDOWN_TICKS
			);
			announce(server, savedData, outpost, target, completed, "was repelled at");
			savedData.putOutpostRaidState(completed.withAnnouncementTick(currentTick));
			return true;
		}

		OutpostRaidState updated = raidState.withControlProgress(progress);
		savedData.putOutpostRaidState(announceIfDue(server, savedData, outpost, target, updated, currentTick, "is raiding"));
		return true;
	}

	private static Optional<SettlementState> chooseTarget(LiveVillagesSavedData savedData, SettlementState outpost) {
		return savedData.getSettlements().stream()
			.filter(settlement -> !settlement.id().equals(outpost.id()))
			.filter(settlement -> settlement.kind() != SettlementKind.OUTPOST)
			.filter(settlement -> settlement.dimension().equals(outpost.dimension()))
			.filter(settlement -> horizontalDistanceSqr(settlement.center(), outpost.center()) <= TARGET_SEARCH_RADIUS_BLOCKS * TARGET_SEARCH_RADIUS_BLOCKS)
			.filter(settlement -> transferableValue(settlement) >= 8)
			.min(Comparator
				.comparingDouble((SettlementState settlement) -> horizontalDistanceSqr(settlement.center(), outpost.center()))
				.thenComparing(SettlementState::name));
	}

	private static boolean shouldPayTribute(SettlementState target, OutpostRaidState raidState, int nearbyGolems) {
		int transferableValue = transferableValue(target);
		if (transferableValue < 12) {
			return false;
		}

		double defensePressure = target.security() + (nearbyGolems * 0.20D);
		double raidPressure = Math.min(1.0D, raidState.partySize() * 0.12D);
		return defensePressure < raidPressure;
	}

	private static int controlProgressThisTick(SettlementState target, OutpostRaidState raidState, int nearbyGolems) {
		double defensePressure = target.security() + (nearbyGolems * 0.18D);
		double raidPressure = Math.min(1.0D, raidState.partySize() * 0.11D);

		if (defensePressure >= raidPressure + 0.25D) {
			return 0;
		}

		if (nearbyGolems > 0 || defensePressure >= raidPressure) {
			return 5;
		}

		return 20;
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
		int transferableValue = transferableValue(target);
		int fractionLimit = Math.max(1, (int) Math.ceil(transferableValue * (tribute ? 0.125D : 0.25D)));
		int carryLimit = Math.max(1, partySize * (tribute ? 4 : 8));
		int remaining = Math.min(fractionLimit, carryLimit);
		Map<String, Integer> transferred = new LinkedHashMap<>();
		int emeralds = transferEmeraldWealth(targetWealth, outpostWealth, partySize, tribute, remaining);
		if (emeralds > 0) {
			SettlementGoods.addGoods(transferred, "emerald", emeralds);
			remaining -= emeralds;
		}

		for (String goodsKey : sortedLootKeys(targetStock)) {
			if (remaining <= 0) {
				break;
			}

			int available = Math.max(0, targetStock.getOrDefault(goodsKey, 0));
			if (available <= 0) {
				continue;
			}

			int amount = Math.min(available, remaining);
			if (!SettlementGoods.consumeGoods(targetStock, goodsKey, amount)) {
				continue;
			}

			SettlementGoods.addGoods(outpostStock, goodsKey, amount);
			SettlementGoods.addGoods(transferred, goodsKey, amount);
			remaining -= amount;
		}

		savedData.putSettlement(withRaidEconomy(target, targetWealth, targetStock));
		savedData.putSettlement(withRaidEconomy(outpost, outpostWealth, outpostStock));
		return new LootTransfer(transferred);
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

	private static SettlementState withRaidEconomy(SettlementState settlement, Map<String, Integer> wealth, Map<String, Integer> stock) {
		return settlement.withSimulationState(
			settlement.population(),
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
		AABB bounds = new AABB(outpost.center()).inflate(CONTROL_RADIUS_BLOCKS, 24.0D, CONTROL_RADIUS_BLOCKS);
		List<Raider> raiders = level.getEntitiesOfClass(Raider.class, bounds, raider -> raider.isAlive() && !raider.isRemoved()).stream()
			.sorted(Comparator.comparingDouble(raider -> raider.blockPosition().distSqr(outpost.center())))
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

		BlockPos musterPos = outpost.center();
		BlockPos marchWaypoint = raidMarchWaypoint(level, outpost, target);
		for (Raider raider : raiders) {
			OutpostSettlementWork.markOutpostRaidMember(raider, outpost);
			raider.setCanJoinRaid(true);
			raider.setPatrolTarget(marchWaypoint);
			if (raider.distanceToSqr(musterPos.getX() + 0.5D, musterPos.getY(), musterPos.getZ() + 0.5D) > 36.0D) {
				raider.getNavigation().moveTo(musterPos.getX() + 0.5D, musterPos.getY(), musterPos.getZ() + 0.5D, 0.85D);
			}

			raider.lookAt(bannerCarrier, 30.0F, 30.0F);
		}
	}

	private static void steerLoadedRaiders(ServerLevel level, SettlementState outpost, SettlementState target, int partySize) {
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

		BlockPos waypoint = raidMarchWaypoint(level, outpost, target);

		for (Raider raider : raiders) {
			BlockPos currentTarget = horizontalDistanceSqr(raider.blockPosition(), waypoint) <= 12.0D * 12.0D
				? target.center()
				: waypoint;
			raider.setCanJoinRaid(true);
			raider.setPatrolTarget(currentTarget);
			if (raider.getNavigation().isDone() || horizontalDistanceSqr(raider.blockPosition(), currentTarget) > 8.0D * 8.0D) {
				raider.getNavigation().moveTo(currentTarget.getX() + 0.5D, currentTarget.getY() + 1.0D, currentTarget.getZ() + 0.5D, 0.9D);
			}
		}
	}

	private static BlockPos raidMarchWaypoint(ServerLevel level, SettlementState outpost, SettlementState target) {
		if (!lineHasWaterOrUnknown(level, outpost.center(), target.center(), RAID_WATER_SCAN_BLOCKS)) {
			return target.center();
		}

		double dx = target.center().getX() - outpost.center().getX();
		double dz = target.center().getZ() - outpost.center().getZ();
		double distance = Math.hypot(dx, dz);
		if (distance < 1.0D) {
			return target.center();
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

				double score = horizontalDistanceSqr(surface.get(), target.center())
					+ Math.abs(lateral) * 8.0D
					+ (RAID_LAND_WAYPOINT_MAX_BLOCKS - forward) * 2.0D
					- pathSurfaceBonus(level.getBlockState(surface.get()));

				if (score < bestScore) {
					best = surface.get();
					bestScore = score;
				}
			}
		}

		return best == null ? target.center() : best;
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
