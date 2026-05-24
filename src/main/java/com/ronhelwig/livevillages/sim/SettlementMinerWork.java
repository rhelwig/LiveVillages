package com.ronhelwig.livevillages.sim;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import com.ronhelwig.livevillages.LiveVillages;

public final class SettlementMinerWork {
	private static final long DAY_TICKS = 24_000L;
	private static final long VILLAGE_GATHERING_START_TICK = 9_000L;
	private static final long VILLAGE_REST_START_TICK = 12_000L;
	private static final double MINING_WORK_REACH_DISTANCE_SQUARED = 6.25D;
	private static final double SHAFT_ENTRY_SNAP_DISTANCE_SQUARED = 25.0D;
	private static final double SURFACE_RETURN_RESCUE_SNAP_DISTANCE_SQUARED = 3_600.0D;
	private static final double MINING_WALK_SPEED = 0.75D;
	private static final long TASK_MEMORY_TICKS = 320L;
	private static final long FAILED_MINER_TASK_COOLDOWN_TICKS = 600L;
	private static final long MINING_DECIDE_INTERVAL_TICKS = 40L;
	private static final int MAX_MINER_TASKS_PER_PASS = 8;
	private static final int ADJACENT_VEIN_SEARCH_RADIUS_BLOCKS = 3;
	private static final int MAX_MINER_COMPLETED_TASKS_PER_DAY = SettlementEconomyRules.scaledWorkerDailyUnits(48);
	private static final int MAX_SHAFT_DEPTH_BLOCKS = 96;
	private static final int SHAFT_LIGHT_SPACING_LEVELS = 8;
	private static final int MAX_CAVE_SCAN_RADIUS_BLOCKS = 10;
	private static final int MAX_CAVE_SCAN_AIR_CELLS = 96;
	private static final int MAX_CAVE_STAND_POSITIONS = 24;
	private static final int CAVE_LIGHT_SPACING_BLOCKS = 7;
	private static final int PRIMARY_TUNNEL_HEIGHT_BLOCKS = 3;
	private static final int PRIMARY_TUNNEL_SOLID_GAP_LEVELS = 2;
	private static final int PRIMARY_TUNNEL_INTERVAL_LEVELS = PRIMARY_TUNNEL_HEIGHT_BLOCKS + PRIMARY_TUNNEL_SOLID_GAP_LEVELS;
	private static final int PRIMARY_TUNNEL_WIDTH_BLOCKS = 2;
	private static final int PRIMARY_TUNNEL_LIGHT_SPACING_BLOCKS = 9;
	private static final int SECONDARY_TUNNEL_HEIGHT_BLOCKS = 2;
	private static final int SECONDARY_TUNNEL_SPACING_BLOCKS = 5;
	private static final int SECONDARY_TUNNEL_LIGHT_SPACING_BLOCKS = 9;
	private static final int TUNNEL_HOSTILE_DETECTION_RADIUS_BLOCKS = 10;
	private static final int SURFACE_RETURN_STEPS_PER_PASS = 8;
	private static final int EVENING_SHAFT_ASCENT_STEPS_PER_PASS = 24;
	private static final int SURFACE_RETURN_PATH_SEARCH_BLOCKS = 28;
	private static final int SURFACE_RETURN_PATH_SEARCH_NODES = 512;
	private static final int EVENING_SURFACE_RETURN_STEPS_PER_PASS = 16;
	private static final int EVENING_SURFACE_PATH_SEARCH_BLOCKS = 48;
	private static final int EVENING_SURFACE_PATH_SEARCH_NODES = 768;
	private static final int STRANDED_BELOW_SHAFT_TOLERANCE_BLOCKS = 4;
	private static final int MINE_ESCAPE_HORIZONTAL_RADIUS_BLOCKS = 48;
	private static final int REACHABLE_RESOURCE_SEARCH_BLOCKS = 72;
	private static final int REACHABLE_RESOURCE_SEARCH_NODES = 768;
	private static final int SAFE_CAVE_BELOW_SHAFT_TOLERANCE_BLOCKS = 2;
	private static final long SURFACE_RETURN_DIAGNOSTIC_INTERVAL_TICKS = 200L;
	private static final int MINE_MOUTH_CLEARANCE_HORIZONTAL_BLOCKS = 4;
	private static final Map<String, TimedTask> ACTIVE_TASKS = new HashMap<>();
	private static final Map<String, ActiveMinerWork> ACTIVE_ASSIGNMENTS = new HashMap<>();
	private static final Map<String, Long> BLOCKED_MINER_TASKS = new HashMap<>();
	private static final Map<String, Long> SURFACE_RETURN_DIAGNOSTICS = new HashMap<>();
	private static final Map<String, Integer> DAILY_COMPLETED_TASKS = new HashMap<>();
	private static final Set<String> CLEARED_MINE_MOUTH_VILLAGERS = new HashSet<>();

	private SettlementMinerWork() {
	}

	public static boolean maintainLoadedMining(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Collection<SettlementBuildSite> buildSites
	) {
		List<Villager> miners = SettlementVillagers.nearbyMiners(level, settlement);

		if (miners.isEmpty()) {
			SettlementProfessionDiagnostics.log(level, settlement, SettlementRoleKeys.MINER, "no_miners", "");
			return false;
		}

		List<SettlementBuildSite> mineEntrances = buildSites.stream()
			.filter(buildSite -> buildSite.blueprintId() == SettlementBuildSiteType.MINE_ENTRANCE)
			.filter(buildSite -> isOperationalMineEntrance(level, buildSite))
			.toList();

		if (mineEntrances.isEmpty()) {
			SettlementProfessionDiagnostics.log(
				level,
				settlement,
				SettlementRoleKeys.MINER,
				"no_operational_mine_entrance",
				"mineBuildSites=" + buildSites.stream().filter(buildSite -> buildSite.blueprintId() == SettlementBuildSiteType.MINE_ENTRANCE).count()
			);
			if (isVillageGatheringTime(level) || isVillageRestTime(level)) {
				for (Villager miner : miners) {
					logSurfaceReturnDiagnostic(level, settlement, miner, null, "no_operational_mine_entrance");
				}
			}

			return false;
		}

		boolean changed = false;
		long tick = level.getServer().getTickCount();
		pruneOldMinerDailyActions(level);
		pruneExpiredMinerTaskBlocks(tick);
		changed |= rescueTrappedVillagersFromMines(level, settlement, stock, mineEntrances, miners);

		for (Villager miner : miners) {
			String minerId = miner.getUUID().toString();
			Optional<MineSite> mineSite = mineSiteFor(level, miner, mineEntrances);

			if (mineSite.isEmpty()) {
				clearMinerAssignment(minerId);
				SettlementProfessionDiagnostics.log(level, settlement, SettlementRoleKeys.MINER, "no_matching_mine_site", "villager=" + miner.getUUID() + " mineEntrances=" + mineEntrances.size());
				if (isVillageGatheringTime(level) || isVillageRestTime(level)) {
					logSurfaceReturnDiagnostic(level, settlement, miner, null, "no_matching_mine_site");
				}
				continue;
			}

			MineSite activeMineSite = mineSite.get();

			if (isVillageGatheringTime(level) || isVillageRestTime(level)) {
				clearMinerAssignment(minerId);
				refreshMineMouthClearance(level, miner, activeMineSite);

				if (needsMineMouthExit(level, miner, activeMineSite)) {
					changed |= moveMinerTowardSurface(
						level,
						settlement,
						stock,
						miner,
						activeMineSite,
						EVENING_SHAFT_ASCENT_STEPS_PER_PASS
					);
				} else if (miner.blockPosition().getY() < surfaceLadderY(activeMineSite) - 2) {
					changed |= moveMinerTowardSurface(
						level,
						settlement,
						stock,
						miner,
						activeMineSite,
						EVENING_SHAFT_ASCENT_STEPS_PER_PASS
					);
				}

				if (!SettlementVillagers.isNearSettlementEveningTarget(level, settlement, miner)
					&& isMinerEligibleForEveningWalk(level, miner, activeMineSite)) {
					changed |= moveMinerTowardEveningTarget(level, settlement, miner, activeMineSite);
				}
				continue;
			}

			CLEARED_MINE_MOUTH_VILLAGERS.remove(minerId);

			if (isStrandedBelowPlannedShaft(level, miner, activeMineSite)) {
				ACTIVE_TASKS.put(minerId, new TimedTask("returning_to_mine_shaft", tick));
				changed |= moveMinerTowardSurface(level, settlement, stock, miner, activeMineSite);
				SettlementProfessionDiagnostics.log(level, settlement, SettlementRoleKeys.MINER, "rescuing_below_planned_shaft", minerWorkSummary(level, settlement, stock, miner, activeMineSite));
				continue;
			}

			ActiveMinerWork activeWork = activeMinerWork(minerId, tick);
			boolean decideTurn = SettlementVillagerWorkSchedule.shouldStartNewWork(level, miner, "mining", MINING_DECIDE_INTERVAL_TICKS);

			if (!decideTurn && activeWork == null) {
				if (SettlementVillagerWorkSchedule.isTakingBreak(level, miner)) {
					changed |= moveMinerTowardSurface(level, settlement, stock, miner, activeMineSite);
				}

				continue;
			}

			int tasksThisPass = 0;
			while (tasksThisPass < MAX_MINER_TASKS_PER_PASS && completedMinerActionsToday(level, miner) < MAX_MINER_COMPLETED_TASKS_PER_DAY) {
				if (activeWork == null) {
					if (!decideTurn) {
						break;
					}

					Optional<MinerTask> task = chooseTask(level, settlement, stock, miner, activeMineSite);
					if (task.isEmpty()) {
						SettlementProfessionDiagnostics.log(level, settlement, SettlementRoleKeys.MINER, "no_task", minerWorkSummary(level, settlement, stock, miner, activeMineSite));
						break;
					}

					activeWork = rememberMinerWork(minerId, task.get(), tick);
				}

				MinerTask minerTask = activeWork.task();
				showMinerTool(miner, minerTask.action());
				moveMinerTowardTask(level, miner, activeMineSite, minerTask);
				ACTIVE_TASKS.put(minerId, new TimedTask(minerTask.taskKey(), tick));

				if (!canMinerReachTask(miner, minerTask)) {
					SettlementProfessionDiagnostics.log(level, settlement, SettlementRoleKeys.MINER, "moving_to_work", minerTaskSummary(level, miner, activeMineSite, minerTask));
					break;
				}

				if (!performTask(level, settlement, stock, miner, activeMineSite, minerTask)) {
					rememberBlockedMinerTask(minerId, minerTask, tick);
					clearMinerAssignment(minerId);
					SettlementProfessionDiagnostics.log(level, settlement, SettlementRoleKeys.MINER, "task_failed", minerTaskSummary(level, miner, activeMineSite, minerTask));
					break;
				}

				recordCompletedMinerAction(level, miner);
				miner.swing(InteractionHand.MAIN_HAND);
				tasksThisPass++;
				changed = true;

				if (isOreMiningAction(minerTask.action())) {
					int bottomUp = deepestCompleteShaftUp(level, activeMineSite);
					Optional<MinerTask> veinFollowUp = chooseAdjacentOreTask(level, settlement, stock, miner, activeMineSite, bottomUp);
					if (veinFollowUp.isPresent()) {
						activeWork = rememberMinerWork(minerId, veinFollowUp.get(), tick);
						continue;
					}
				}

				clearMinerAssignment(minerId);
				activeWork = null;
			}

			if (completedMinerActionsToday(level, miner) >= MAX_MINER_COMPLETED_TASKS_PER_DAY) {
				SettlementProfessionDiagnostics.log(level, settlement, SettlementRoleKeys.MINER, "daily_cap_reached", "villager=" + miner.getUUID() + " completed=" + completedMinerActionsToday(level, miner));
			}
		}

		return changed;
	}

	public static Optional<String> loadedMinerTaskKey(ServerLevel level, Villager villager) {
		TimedTask task = ACTIVE_TASKS.get(villager.getUUID().toString());

		if (task == null || level.getServer().getTickCount() - task.tick() > TASK_MEMORY_TICKS) {
			return Optional.empty();
		}

		return Optional.of(task.taskKey());
	}

	public static boolean moveSurfaceWorkerAwayFromLoadedMine(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Villager villager,
		String roleKey
	) {
		List<SettlementBuildSite> mineEntrances = LiveVillagesSavedData.get(level.getServer()).getBuildSitesForSettlement(settlement.id()).stream()
			.filter(buildSite -> buildSite.blueprintId() == SettlementBuildSiteType.MINE_ENTRANCE)
			.filter(buildSite -> isOperationalMineEntrance(level, buildSite))
			.toList();

		if (mineEntrances.isEmpty()) {
			return false;
		}

		return moveNonMinerOutOfLoadedMine(level, settlement, stock, villager, roleKey, mineEntrances);
	}

	private static boolean rescueTrappedVillagersFromMines(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		List<SettlementBuildSite> mineEntrances,
		List<Villager> miners
	) {
		Set<String> minerIds = miners.stream()
			.map(villager -> villager.getUUID().toString())
			.collect(java.util.stream.Collectors.toSet());
		boolean changed = false;

		for (Villager villager : SettlementVillagers.nearbyAdultVillagers(level, settlement, SettlementVillagers.settlementRadiusBlocks(settlement))) {
			if (minerIds.contains(villager.getUUID().toString()) || villager.isSleeping()) {
				continue;
			}

			String roleKey = SettlementVillagers.reportProfessionKey(villager);
			changed |= moveNonMinerOutOfLoadedMine(level, settlement, stock, villager, roleKey, mineEntrances);
		}

		return changed;
	}

	private static boolean moveNonMinerOutOfLoadedMine(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Villager villager,
		String roleKey,
		List<SettlementBuildSite> mineEntrances
	) {
		Optional<MineSite> mineSite = nearestMineSiteFor(level, villager.blockPosition(), mineEntrances);
		if (mineSite.isEmpty()) {
			return false;
		}

		boolean mouthHazard = isMineMouthShaftColumn(mineSite.get(), villager.blockPosition());
		boolean trapped = isTrappedNearMine(level, villager, mineSite.get());
		if (!mouthHazard && !trapped) {
			return false;
		}

		boolean changed = moveMinerTowardSurface(level, settlement, stock, villager, mineSite.get());
		SettlementProfessionDiagnostics.log(
			level,
			settlement,
			roleKey,
			mouthHazard ? "mine_mouth_avoidance" : "mine_escape",
			"villager=" + villager.getUUID()
				+ " role=" + roleKey
				+ " pos=" + villager.blockPosition().toShortString()
				+ " mine=" + mineSite.get().buildSite().workstationPos().toShortString()
				+ " mouthHazard=" + mouthHazard
				+ " trapped=" + trapped
		);
		return changed;
	}

	private static Optional<MineSite> nearestMineSiteFor(ServerLevel level, BlockPos pos, List<SettlementBuildSite> mineEntrances) {
		MineSite bestSite = null;
		double bestDistanceSquared = Double.POSITIVE_INFINITY;

		for (SettlementBuildSite buildSite : mineEntrances) {
			Optional<MineSite> mineSite = createMineSite(level, buildSite);
			if (mineSite.isEmpty()) {
				continue;
			}

			double distanceSquared = horizontalDistanceSqr(buildSite.workstationPos(), pos);
			if (bestSite == null || distanceSquared < bestDistanceSquared) {
				bestSite = mineSite.get();
				bestDistanceSquared = distanceSquared;
			}
		}

		return Optional.ofNullable(bestSite);
	}

	private static int completedMinerActionsToday(ServerLevel level, Villager miner) {
		return DAILY_COMPLETED_TASKS.getOrDefault(minerDailyActionKey(level, miner), 0);
	}

	private static void recordCompletedMinerAction(ServerLevel level, Villager miner) {
		DAILY_COMPLETED_TASKS.merge(minerDailyActionKey(level, miner), 1, Integer::sum);
	}

	private static String minerDailyActionKey(ServerLevel level, Villager miner) {
		long dayIndex = Math.floorDiv(level.getOverworldClockTime(), DAY_TICKS);
		return dayIndex + "|" + miner.getUUID();
	}

	private static void pruneOldMinerDailyActions(ServerLevel level) {
		long currentDayIndex = Math.floorDiv(level.getOverworldClockTime(), DAY_TICKS);
		String currentPrefix = currentDayIndex + "|";
		String previousPrefix = (currentDayIndex - 1L) + "|";
		DAILY_COMPLETED_TASKS.keySet().removeIf(key -> !key.startsWith(currentPrefix) && !key.startsWith(previousPrefix));
	}

	private static boolean isStrandedBelowPlannedShaft(ServerLevel level, Villager villager, MineSite mineSite) {
		if (currentLadderPos(level, mineSite, villager.blockPosition()).isPresent()) {
			return false;
		}

		int bottomUp = deepestCompleteShaftUp(level, mineSite);
		int bottomY = worldPos(mineSite.buildSite(), mineSite.layout().ladderColumns().getFirst(), bottomUp).getY();
		return villager.blockPosition().getY() < bottomY - STRANDED_BELOW_SHAFT_TOLERANCE_BLOCKS
			&& nearestExistingLadderPos(level, mineSite, villager.blockPosition()).isPresent();
	}

	private static boolean isTrappedNearMine(ServerLevel level, Villager villager, MineSite mineSite) {
		BlockPos pos = villager.blockPosition();

		if (currentLadderPos(level, mineSite, pos).isPresent() || isSurfaceLadderColumn(mineSite, pos)) {
			return true;
		}

		if (pos.getY() >= surfaceLadderY(mineSite) - 4) {
			return false;
		}

		if (horizontalDistanceSqr(pos, mineSite.buildSite().workstationPos()) > (double) MINE_ESCAPE_HORIZONTAL_RADIUS_BLOCKS * MINE_ESCAPE_HORIZONTAL_RADIUS_BLOCKS) {
			return false;
		}

		return nearestExistingLadderPos(level, mineSite, pos).isPresent()
			|| returnPathStepTowardLadder(level, mineSite, pos).isPresent();
	}

	private static Optional<MineSite> mineSiteFor(ServerLevel level, Villager miner, List<SettlementBuildSite> mineEntrances) {
		Optional<BlockPos> jobSite = SettlementVillagers.minerJobSite(level, miner);
		SettlementBuildSite bestSite = null;
		double bestDistanceSquared = Double.POSITIVE_INFINITY;

		for (SettlementBuildSite buildSite : mineEntrances) {
			if (jobSite.isPresent() && buildSite.workstationPos().equals(jobSite.get())) {
				return createMineSite(level, buildSite);
			}

			double distanceSquared = buildSite.workstationPos().distSqr(miner.blockPosition());

			if (bestSite == null || distanceSquared < bestDistanceSquared) {
				bestSite = buildSite;
				bestDistanceSquared = distanceSquared;
			}
		}

		return bestSite == null ? Optional.empty() : createMineSite(level, bestSite);
	}

	private static Optional<MineSite> createMineSite(ServerLevel level, SettlementBuildSite buildSite) {
		ShaftLayout layout = shaftLayout(buildSite);

		if (layout.ladderColumns().isEmpty() || layout.openColumns().isEmpty()) {
			return Optional.empty();
		}

		BlockPos sampleLadderPos = worldPos(buildSite, layout.ladderColumns().getFirst(), -1);
		BlockState ladderState = findTemplateLadderState(level, buildSite, layout);
		Optional<Direction> intendedSupportDirection = intendedShaftSupportDirection(buildSite, layout);
		Direction supportDirection = intendedSupportDirection.isPresent()
			? intendedSupportDirection.get()
			: supportDirectionFor(level, sampleLadderPos, ladderState);
		if (supportDirection != null && ladderState.hasProperty(LadderBlock.FACING)) {
			ladderState = ladderState.setValue(LadderBlock.FACING, supportDirection.getOpposite());
		}
		return supportDirection == null
			? Optional.empty()
			: Optional.of(new MineSite(buildSite, layout, ladderState, supportDirection));
	}

	private static boolean isOperationalMineEntrance(ServerLevel level, SettlementBuildSite buildSite) {
		ShaftLayout layout = shaftLayout(buildSite);

		if (layout.ladderColumns().isEmpty() || layout.openColumns().isEmpty()) {
			return false;
		}

		boolean hasStarterLadder = false;

		for (ShaftColumn column : layout.ladderColumns()) {
			if (level.getBlockState(worldPos(buildSite, column, -1)).is(Blocks.LADDER)) {
				hasStarterLadder = true;
				break;
			}
		}

		if (!hasStarterLadder) {
			return false;
		}

		for (ShaftColumn column : layout.openColumns()) {
			if (level.getBlockState(worldPos(buildSite, column, -1)).isAir()
				|| canMine(level.getBlockState(worldPos(buildSite, column, -1)), level, worldPos(buildSite, column, -1))) {
				return true;
			}
		}

		return false;
	}

	private static Optional<MinerTask> chooseTask(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, Villager miner, MineSite mineSite) {
		int bottomUp = deepestCompleteShaftUp(level, mineSite);
		List<BlockPos> frontierCells = shaftFrontierCells(mineSite, bottomUp);
		Optional<CavePocket> shaftBreachCave = scanNearbyCave(level, mineSite, frontierCells);
		boolean shaftCavernQuarantined = isShaftCavernQuarantined(level, mineSite, bottomUp);
		boolean hostileShaftBreach = shaftBreachCave
			.map(pocket -> hostilesPresentNearAirCells(level, pocket.airCells()))
				.orElse(false);

		Optional<MinerTask> starterShaftRepairTask = availableMinerTask(
			level,
			miner,
			chooseStarterShaftRepairTask(level, stock, mineSite)
		);
		if (starterShaftRepairTask.isPresent()) {
			return starterShaftRepairTask;
		}

		Optional<MinerTask> adjacentOreTask = availableMinerTask(
			level,
			miner,
			chooseAdjacentOreTask(level, settlement, stock, miner, mineSite, bottomUp)
		);
		if (adjacentOreTask.isPresent()) {
			return adjacentOreTask;
		}

		Optional<MinerTask> reachableResourceTask = availableMinerTask(
			level,
			miner,
			chooseReachableUndergroundResourceTask(level, stock, mineSite, bottomUp, miner.blockPosition())
		);
		if (reachableResourceTask.isPresent()) {
			return reachableResourceTask;
		}

		Optional<BlockPos> lightPos = desiredLightPos(level, mineSite, bottomUp);
		Optional<BlockPos> standPos = standPosFor(miner, mineSite, bottomUp);

		if (standPos.isEmpty()) {
			return Optional.empty();
		}

		if (!shaftCavernQuarantined && hostileShaftBreach && shaftBreachCave.isPresent()) {
			Optional<MinerTask> shaftClosureTask = availableMinerTask(
				level,
				miner,
				chooseShaftCavernClosureTask(level, mineSite, bottomUp, standPos.get(), shaftBreachCave.get().airCells())
			);
			if (shaftClosureTask.isPresent()) {
				return shaftClosureTask;
			}
		}

		if (shaftCavernQuarantined || hostileShaftBreach) {
			Optional<MinerTask> tunnelTask = choosePrimaryTunnelTask(level, settlement, stock, miner, mineSite, bottomUp);
			if (tunnelTask.isPresent()) {
				return tunnelTask;
			}

			return chooseReachableShaftWallMiningTask(level, mineSite, bottomUp);
		}

		if (lightPos.isPresent()) {
			BlockState lightState = preferredShaftLightState(level, stock, mineSite, lightPos.get());

			if (lightState != null) {
				Optional<MinerTask> lightTask = availableMinerTask(
					level,
					miner,
					new MinerTask(MinerAction.PLACE_LIGHT, lightPos.get(), lightState, standPos.get(), "lighting_mine_shaft")
				);
				if (lightTask.isPresent()) {
					return lightTask;
				}
			}
		}

		Optional<BlockPos> stoneTarget = findExposedStoneTarget(level, mineSite, frontierCells, standPos.get());
		Optional<BlockPos> oreTarget = findExposedOreTarget(level, mineSite, frontierCells, standPos.get());

		if (oreTarget.isPresent()) {
			BlockPos targetPos = oreTarget.get();
			BlockState replacementState = null;
			boolean canMineOreTarget = true;

				if (requiresShaftStructureReplacement(targetPos, mineSite, bottomUp)) {
					replacementState = supportReplacementMaterial(stock);

					if (replacementState == null) {
						Optional<MinerTask> stoneTask = availableMinerTask(
						level,
						miner,
						stoneTarget.map(pos -> new MinerTask(MinerAction.MINE_STONE, pos, null, standPos.get(), "mining_shaft_stone"))
					);
					if (stoneTask.isPresent()) {
						return stoneTask;
					}
					canMineOreTarget = false;
				}
			}

			if (canMineOreTarget) {
				Optional<MinerTask> oreTask = availableMinerTask(
					level,
					miner,
					new MinerTask(MinerAction.MINE_ORE, targetPos, replacementState, standPos.get(), "mining_ore_vein")
				);
				if (oreTask.isPresent()) {
					return oreTask;
				}
			}
		}

		Optional<MinerTask> tunnelTask = choosePrimaryTunnelTask(level, settlement, stock, miner, mineSite, bottomUp);
		if (tunnelTask.isPresent()) {
			return tunnelTask;
		}

		Optional<MinerTask> caveTask = chooseCaveTask(level, stock, mineSite, frontierCells, bottomUp, miner);

		Optional<MinerTask> availableCaveTask = availableMinerTask(level, miner, caveTask);
		if (availableCaveTask.isPresent()) {
			return availableCaveTask;
		}

		int nextUp = bottomUp - 1;

		if (shouldStopShaftBeforeLevel(level, mineSite, nextUp)) {
			Optional<MinerTask> safetyLadderTask = chooseBedrockSafetyLadderTask(level, stock, mineSite, bottomUp, standPos.get());
			Optional<MinerTask> availableSafetyLadderTask = availableMinerTask(level, miner, safetyLadderTask);
			if (availableSafetyLadderTask.isPresent()) {
				return availableSafetyLadderTask;
			}

			return chooseReachableShaftWallMiningTask(level, mineSite, bottomUp);
		}

		if (!canSupplyNextLadderLevel(stock)) {
			if (stoneTarget.isPresent()) {
				Optional<MinerTask> stoneTask = availableMinerTask(
					level,
					miner,
					stoneTarget.map(pos -> new MinerTask(MinerAction.MINE_STONE, pos, null, standPos.get(), "mining_shaft_stone"))
				);
				if (stoneTask.isPresent()) {
					return stoneTask;
				}
			}

			return chooseReachableShaftWallMiningTask(level, mineSite, bottomUp);
		}

		for (ShaftColumn column : mineSite.layout().ladderColumns()) {
			BlockPos supportPos = worldPos(mineSite.buildSite(), column, nextUp).relative(mineSite.supportDirection());
			BlockState supportState = level.getBlockState(supportPos);

			if (!isStableLadderSupport(supportState)) {
				BlockState replacementMaterial = supportReplacementMaterial(stock);

				if (replacementMaterial == null) {
					Optional<MinerTask> stoneTask = availableMinerTask(
						level,
						miner,
						stoneTarget.map(pos -> new MinerTask(MinerAction.MINE_STONE, pos, null, standPos.get(), "mining_shaft_stone"))
					);
					if (stoneTask.isPresent()) {
						return stoneTask;
					}
					continue;
				}

				Optional<MinerTask> supportTask = availableMinerTask(
					level,
					miner,
					new MinerTask(MinerAction.PLACE_SUPPORT, supportPos, replacementMaterial, standPos.get(), "reinforcing_shaft_support")
				);
				if (supportTask.isPresent()) {
					return supportTask;
				}
			}
		}

		for (ShaftColumn column : mineSite.layout().ladderColumns()) {
			BlockPos targetPos = worldPos(mineSite.buildSite(), column, nextUp);
			BlockState targetState = level.getBlockState(targetPos);

			if (!targetState.isAir() && !targetState.is(Blocks.LADDER)) {
				Optional<MinerTask> digTask = availableMinerTask(
					level,
					miner,
					new MinerTask(MinerAction.DIG_SHAFT, targetPos, null, standPos.get(), "digging_mine_shaft")
				);
				if (digTask.isPresent()) {
					return digTask;
				}
			}
		}

		for (ShaftColumn column : mineSite.layout().ladderColumns()) {
			BlockPos targetPos = worldPos(mineSite.buildSite(), column, nextUp);
			BlockState targetState = level.getBlockState(targetPos);

			if (!targetState.is(Blocks.LADDER)) {
				Optional<MinerTask> ladderTask = availableMinerTask(
					level,
					miner,
					new MinerTask(MinerAction.PLACE_LADDER, targetPos, mineSite.ladderStateTemplate(), standPos.get(), "placing_shaft_ladders")
				);
				if (ladderTask.isPresent()) {
					return ladderTask;
				}
			}
		}

		for (ShaftColumn column : mineSite.layout().openColumns()) {
			BlockPos targetPos = worldPos(mineSite.buildSite(), column, nextUp);
			BlockState targetState = level.getBlockState(targetPos);

			if (!isClearOpenShaftCell(targetState)) {
				Optional<MinerTask> digTask = availableMinerTask(
					level,
					miner,
					new MinerTask(MinerAction.DIG_SHAFT, targetPos, null, standPos.get(), "digging_mine_shaft")
				);
				if (digTask.isPresent()) {
					return digTask;
				}
			}
		}

		if (stoneTarget.isPresent()) {
			Optional<MinerTask> stoneTask = availableMinerTask(
				level,
				miner,
				stoneTarget.map(pos -> new MinerTask(MinerAction.MINE_STONE, pos, null, standPos.get(), "mining_shaft_stone"))
			);
			if (stoneTask.isPresent()) {
				return stoneTask;
			}
		}

		return chooseReachableShaftWallMiningTask(level, mineSite, bottomUp);
	}

	private static Optional<MinerTask> chooseStarterShaftRepairTask(ServerLevel level, Map<String, Integer> stock, MineSite mineSite) {
		for (int up = -1; up >= mineSite.layout().starterMinUp(); up--) {
			for (ShaftColumn ladderColumn : mineSite.layout().ladderColumns()) {
				BlockPos supportPos = worldPos(mineSite.buildSite(), ladderColumn, up).relative(mineSite.supportDirection());
				BlockState supportState = level.getBlockState(supportPos);

				if (!isStableLadderSupport(supportState)) {
					BlockState replacementMaterial = supportReplacementMaterial(stock);
					if (replacementMaterial != null) {
						return Optional.of(new MinerTask(
							MinerAction.PLACE_SUPPORT,
							supportPos,
							replacementMaterial,
							shaftLevelStandPos(mineSite, up, supportPos),
							"repairing_shaft_support"
						));
					}
				}
			}
		}

		for (int up = -1; up >= mineSite.layout().starterMinUp(); up--) {
			for (BlockPos wallPos : starterShaftWallPositions(mineSite, up)) {
				if (!isStableLadderSupport(level.getBlockState(wallPos))) {
					BlockState replacementMaterial = supportReplacementMaterial(stock);
					if (replacementMaterial != null) {
						return Optional.of(new MinerTask(
							MinerAction.PLACE_SUPPORT,
							wallPos,
							replacementMaterial,
							shaftLevelStandPos(mineSite, up, wallPos),
							"repairing_shaft_wall"
						));
					}
				}
			}
		}

		for (int up = -1; up >= mineSite.layout().starterMinUp(); up--) {
			for (ShaftColumn column : mineSite.layout().ladderColumns()) {
				BlockPos ladderPos = worldPos(mineSite.buildSite(), column, up);
				BlockState ladderState = level.getBlockState(ladderPos);

				if (!ladderState.isAir() && !ladderState.is(Blocks.LADDER)) {
					return Optional.of(new MinerTask(
						MinerAction.DIG_SHAFT,
						ladderPos,
						null,
						shaftLevelStandPos(mineSite, up, ladderPos),
						"clearing_starter_shaft_ladder_cell"
					));
				}

				if (!ladderState.is(Blocks.LADDER) && canSupplySingleLadder(stock)) {
					return Optional.of(new MinerTask(
						MinerAction.PLACE_LADDER,
						ladderPos,
						mineSite.ladderStateTemplate(),
						shaftLevelStandPos(mineSite, up, ladderPos),
						"repairing_starter_shaft_ladder"
					));
				}
			}
		}

		for (int up = -1; up >= mineSite.layout().starterMinUp(); up--) {
			for (ShaftColumn column : mineSite.layout().openColumns()) {
				BlockPos openPos = worldPos(mineSite.buildSite(), column, up);
				BlockState openState = level.getBlockState(openPos);

				if (!isClearOpenShaftCell(openState) || !isValidShaftOpenCell(level, mineSite, openPos, openState)) {
					return Optional.of(new MinerTask(
						MinerAction.DIG_SHAFT,
						openPos,
						null,
						shaftLevelStandPos(mineSite, up, openPos),
						"clearing_starter_shaft_open_cell"
					));
				}
			}
		}

		return Optional.empty();
	}

	private static Optional<MinerTask> chooseReachableShaftWallMiningTask(ServerLevel level, MineSite mineSite, int bottomUp) {
		for (int up = bottomUp; up <= -1; up++) {
			for (ShaftColumn ladderColumn : mineSite.layout().ladderColumns()) {
				BlockPos standPos = worldPos(mineSite.buildSite(), ladderColumn, up);
				if (!level.getBlockState(standPos).is(Blocks.LADDER)) {
					continue;
				}

					for (Direction direction : Direction.Plane.HORIZONTAL) {
						BlockPos targetPos = standPos.relative(direction);
						if (isRequiredLadderSupport(targetPos, mineSite) || isProtectedStarterShaftWall(targetPos, mineSite)) {
							continue;
						}

						if (isUsefulOre(level.getBlockState(targetPos))) {
							return Optional.of(new MinerTask(MinerAction.MINE_ORE, targetPos, null, standPos, "mining_reachable_shaft_ore"));
					}

					if (isUsefulStone(level.getBlockState(targetPos))) {
						return Optional.of(new MinerTask(MinerAction.MINE_STONE, targetPos, null, standPos, "mining_reachable_shaft_stone"));
					}
				}
			}
		}

		return Optional.empty();
	}

	private static Optional<MinerTask> chooseReachableUndergroundResourceTask(
		ServerLevel level,
		Map<String, Integer> stock,
		MineSite mineSite,
		int bottomUp,
		BlockPos minerPos
	) {
		Set<BlockPos> visited = new HashSet<>();
		ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
		BlockPos origin = mineSite.buildSite().workstationPos();

		if (isWithinReachableResourceSearch(origin, minerPos) && isStandableTunnelCell(level, minerPos) && visited.add(minerPos.immutable())) {
			frontier.addFirst(minerPos.immutable());
		}

		for (BlockPos ladderPos : existingMineLadderPositions(level, mineSite)) {
			if (Math.abs(ladderPos.getY() - surfaceLadderY(mineSite)) < 4) {
				continue;
			}

			if (visited.add(ladderPos)) {
				frontier.addLast(ladderPos);
			}

			for (Direction direction : Direction.Plane.HORIZONTAL) {
				BlockPos standPos = ladderPos.relative(direction);
				if (isWithinReachableResourceSearch(origin, standPos) && isStandableTunnelCell(level, standPos) && visited.add(standPos.immutable())) {
					frontier.addLast(standPos.immutable());
				}
			}
		}

		BlockPos bestTarget = null;
		BlockPos bestStand = null;
		double bestDistanceSquared = Double.POSITIVE_INFINITY;

		while (!frontier.isEmpty() && visited.size() <= REACHABLE_RESOURCE_SEARCH_NODES) {
			BlockPos standPos = frontier.removeFirst();
			Optional<OreWorkTarget> resourceTarget = reachableOreTargetFromStandPos(level, stock, mineSite, bottomUp, standPos);
			if (resourceTarget.isPresent()) {
				double distanceSquared = minerPos.distSqr(resourceTarget.get().targetPos());
				if (bestTarget == null || distanceSquared < bestDistanceSquared) {
					bestTarget = resourceTarget.get().targetPos();
					bestStand = resourceTarget.get().standPos();
					bestDistanceSquared = distanceSquared;
				}
			}

			for (BlockPos next : reachableResourceNeighbors(level, mineSite, origin, standPos)) {
				if (visited.add(next)) {
					frontier.addLast(next);
				}
			}
		}

		if (bestTarget == null || bestStand == null) {
			return Optional.empty();
		}

		BlockState targetState = level.getBlockState(bestTarget);
		BlockState replacementState = null;
			if (requiresShaftStructureReplacement(bestTarget, mineSite, bottomUp)) {
				replacementState = supportReplacementMaterial(stock);
				if (replacementState == null) {
					return Optional.empty();
				}
			}

		return Optional.of(new MinerTask(MinerAction.MINE_ORE, bestTarget, replacementState, bestStand, miningResourceTaskKey(targetState)));
	}

	private static Optional<OreWorkTarget> reachableOreTargetFromStandPos(
		ServerLevel level,
		Map<String, Integer> stock,
		MineSite mineSite,
		int bottomUp,
		BlockPos standPos
	) {
		BlockPos bestTarget = null;
		double bestDistanceSquared = Double.POSITIVE_INFINITY;

		for (BlockPos inspectOrigin : List.of(standPos, standPos.above())) {
			for (Direction direction : Direction.values()) {
				BlockPos targetPos = inspectOrigin.relative(direction);
				BlockState targetState = level.getBlockState(targetPos);

				if (!isUsefulOre(targetState) || isRequiredLadderSupport(targetPos, mineSite)) {
					continue;
				}

				if (requiresShaftStructureReplacement(targetPos, mineSite, bottomUp) && supportReplacementMaterial(stock) == null) {
					continue;
				}

				double distanceSquared = standPos.distSqr(targetPos);
				if (bestTarget == null || distanceSquared < bestDistanceSquared) {
					bestTarget = targetPos.immutable();
					bestDistanceSquared = distanceSquared;
				}
			}
		}

		return bestTarget == null ? Optional.empty() : Optional.of(new OreWorkTarget(bestTarget, standPos));
	}

	private static Optional<MinerTask> chooseAdjacentOreTask(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Villager miner,
		MineSite mineSite,
		int bottomUp
	) {
		BlockPos minerPos = miner.blockPosition();
		BlockPos bestTarget = null;
		BlockPos bestStand = null;
		double bestDistanceSquared = Double.POSITIVE_INFINITY;

		for (int dx = -ADJACENT_VEIN_SEARCH_RADIUS_BLOCKS; dx <= ADJACENT_VEIN_SEARCH_RADIUS_BLOCKS; dx++) {
			for (int dy = -ADJACENT_VEIN_SEARCH_RADIUS_BLOCKS; dy <= ADJACENT_VEIN_SEARCH_RADIUS_BLOCKS; dy++) {
				for (int dz = -ADJACENT_VEIN_SEARCH_RADIUS_BLOCKS; dz <= ADJACENT_VEIN_SEARCH_RADIUS_BLOCKS; dz++) {
					if (dx == 0 && dy == 0 && dz == 0) {
						continue;
					}

					BlockPos targetPos = minerPos.offset(dx, dy, dz);
					if (!isUsefulOre(level.getBlockState(targetPos)) || isRequiredLadderSupport(targetPos, mineSite)) {
						continue;
					}

					Optional<BlockPos> standPos = resolveOreStandPos(level, mineSite, miner, targetPos);
					if (standPos.isEmpty()) {
						continue;
					}

					BlockState replacementState = null;
				if (requiresShaftStructureReplacement(targetPos, mineSite, bottomUp)) {
					replacementState = supportReplacementMaterial(stock);
					if (replacementState == null) {
						continue;
					}
					}

					double distanceSquared = minerPos.distSqr(targetPos);
					if (bestTarget == null || distanceSquared < bestDistanceSquared) {
						bestTarget = targetPos.immutable();
						bestStand = standPos.get();
						bestDistanceSquared = distanceSquared;
					}
				}
			}
		}

		if (bestTarget == null || bestStand == null) {
			return Optional.empty();
		}

		BlockState replacementState = requiresShaftStructureReplacement(bestTarget, mineSite, bottomUp)
			? supportReplacementMaterial(stock)
			: null;
		return Optional.of(new MinerTask(
			MinerAction.MINE_ORE,
			bestTarget,
			replacementState,
			bestStand,
			miningResourceTaskKey(level.getBlockState(bestTarget))
		));
	}

	private static Optional<BlockPos> resolveOreStandPos(ServerLevel level, MineSite mineSite, Villager miner, BlockPos targetPos) {
		BlockPos minerPos = miner.blockPosition();
		List<BlockPos> candidates = new ArrayList<>();
		candidates.add(minerPos);
		candidates.add(minerPos.above());
		candidates.add(minerPos.below());

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos candidate = minerPos.relative(direction);
			if (isStandableTunnelCell(level, candidate) || isExistingMineLadder(level, mineSite, candidate)) {
				candidates.add(candidate.immutable());
			}
		}

		return candidates.stream()
			.filter(standPos -> standPos.distSqr(targetPos) <= MINING_WORK_REACH_DISTANCE_SQUARED)
			.min(Comparator.comparingDouble(standPos -> standPos.distSqr(targetPos)))
			.map(BlockPos::immutable);
	}

	private static boolean isOreMiningAction(MinerAction action) {
		return action == MinerAction.MINE_ORE || action == MinerAction.MINE_STONE;
	}

	private static String miningResourceTaskKey(BlockState targetState) {
		if (targetState.is(Blocks.AMETHYST_CLUSTER) || targetState.is(Blocks.AMETHYST_BLOCK) || targetState.is(Blocks.CALCITE)) {
			return "mining_geode_resources";
		}

		return "mining_reachable_ore";
	}

	private static List<BlockPos> reachableResourceNeighbors(ServerLevel level, MineSite mineSite, BlockPos origin, BlockPos standPos) {
		List<BlockPos> neighbors = new ArrayList<>(8);

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			for (int dy = -1; dy <= 1; dy++) {
				BlockPos candidate = standPos.relative(direction).offset(0, dy, 0);
				if (isWithinReachableResourceSearch(origin, candidate) && (isStandableTunnelCell(level, candidate) || isExistingMineLadder(level, mineSite, candidate))) {
					neighbors.add(candidate.immutable());
				}
			}
		}

		if (isExistingMineLadder(level, mineSite, standPos)) {
			for (Direction direction : List.of(Direction.UP, Direction.DOWN)) {
				BlockPos candidate = standPos.relative(direction);
				if (isWithinReachableResourceSearch(origin, candidate) && isExistingMineLadder(level, mineSite, candidate)) {
					neighbors.add(candidate.immutable());
				}
			}
		}

		return neighbors;
	}

	private static boolean isWithinReachableResourceSearch(BlockPos origin, BlockPos candidate) {
		return Math.abs(candidate.getX() - origin.getX()) <= REACHABLE_RESOURCE_SEARCH_BLOCKS
			&& Math.abs(candidate.getY() - origin.getY()) <= MAX_SHAFT_DEPTH_BLOCKS + 8
			&& Math.abs(candidate.getZ() - origin.getZ()) <= REACHABLE_RESOURCE_SEARCH_BLOCKS;
	}

	private static Optional<MinerTask> chooseCaveTask(
		ServerLevel level,
		Map<String, Integer> stock,
		MineSite mineSite,
		List<BlockPos> frontierCells,
		int bottomUp,
		Villager miner
	) {
		Optional<CavePocket> cavePocket = scanNearbyCave(level, mineSite, frontierCells);

		if (cavePocket.isEmpty() || isShaftCavernQuarantined(level, mineSite, bottomUp)) {
			return Optional.empty();
		}

		if (hostilesPresentNearAirCells(level, cavePocket.get().airCells())) {
			Optional<BlockPos> standPos = standPosFor(miner, mineSite, bottomUp);
			if (standPos.isPresent()) {
				Optional<MinerTask> shaftClosureTask = chooseShaftCavernClosureTask(
					level,
					mineSite,
					bottomUp,
					standPos.get(),
					cavePocket.get().airCells()
				);
				if (shaftClosureTask.isPresent()) {
					return shaftClosureTask;
				}
			}

			return Optional.empty();
		}

		List<BlockPos> reachableStandPositions = cavePocket.get().standPositions().stream()
			.filter(standPos -> isMineReachableStandPos(level, mineSite, standPos))
			.filter(standPos -> isSafeCaveStandPos(mineSite, bottomUp, standPos))
			.toList();

		if (reachableStandPositions.isEmpty()) {
			return Optional.empty();
		}

		BlockState caveLightState = preferredCaveLightState(stock);

		if (caveLightState != null) {
			for (BlockPos targetPos : cavePocket.get().lightTargets()) {
				if (hasNearbyCaveLight(level, targetPos, CAVE_LIGHT_SPACING_BLOCKS)) {
					continue;
				}

				Optional<BlockPos> standPos = adjacentCaveStandPos(targetPos, reachableStandPositions);

				if (standPos.isPresent()) {
					return Optional.of(new MinerTask(MinerAction.PLACE_LIGHT, targetPos, caveLightState, standPos.get(), "lighting_cave"));
				}
			}
		}

		for (BlockPos standPos : reachableStandPositions) {
			Optional<BlockPos> oreTarget = exposedOreFromStandPos(level, standPos, cavePocket.get().airCells());

			if (oreTarget.isEmpty()) {
				continue;
			}

			BlockPos targetPos = oreTarget.get();
			BlockState replacementState = null;

			if (requiresLadderSupportReplacement(targetPos, mineSite, bottomUp)) {
				replacementState = supportReplacementMaterial(stock);

				if (replacementState == null) {
					continue;
				}
			}

			return Optional.of(new MinerTask(MinerAction.MINE_ORE, targetPos, replacementState, standPos, "mining_ore_vein"));
		}

		return Optional.empty();
	}

	private static boolean isSafeCaveStandPos(MineSite mineSite, int bottomUp, BlockPos standPos) {
		int bottomY = worldPos(mineSite.buildSite(), mineSite.layout().ladderColumns().getFirst(), bottomUp).getY();
		return standPos.getY() >= bottomY - SAFE_CAVE_BELOW_SHAFT_TOLERANCE_BLOCKS;
	}

	private static Optional<MinerTask> chooseBedrockSafetyLadderTask(
		ServerLevel level,
		Map<String, Integer> stock,
		MineSite mineSite,
		int bottomUp,
		BlockPos standPos
	) {
		if (!canSupplySingleLadder(stock)) {
			return Optional.empty();
		}

		int highestUp = Math.min(-1, bottomUp + 2);

		for (int up = bottomUp; up <= highestUp; up++) {
			for (ShaftColumn column : allShaftColumns(mineSite)) {
				BlockPos targetPos = worldPos(mineSite.buildSite(), column, up);

				if (level.getBlockState(targetPos).is(Blocks.LADDER)) {
					continue;
				}

				if (canPlaceLadder(level, targetPos, mineSite.supportDirection())) {
					return Optional.of(new MinerTask(
						MinerAction.PLACE_LADDER,
						targetPos,
						mineSite.ladderStateTemplate(),
						standPos,
						"placing_bedrock_safety_ladders"
					));
				}
			}
		}

		return Optional.empty();
	}

	private static Optional<MinerTask> choosePrimaryTunnelTask(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Villager miner,
		MineSite mineSite,
		int bottomUp
	) {
		List<PrimaryTunnelBranch> branches = plannedPrimaryTunnelBranches(mineSite, bottomUp);

		for (PrimaryTunnelBranch branch : branches) {
			if (isPrimaryTunnelClosed(level, mineSite, branch)) {
				continue;
			}

			Optional<MinerTask> discoveryTask = availableMinerTask(level, miner, choosePrimaryTunnelDiscoveryTask(level, settlement, mineSite, branch));
			if (discoveryTask.isPresent()) {
				return discoveryTask;
			}

			Optional<MinerTask> closureTask = availableMinerTask(
				level,
				miner,
				chooseTunnelClosureTask(level, miner, mineSite, branch, primaryBranchOpenAirCells(level, settlement, mineSite, branch))
			);
			if (closureTask.isPresent()) {
				return closureTask;
			}

			Optional<MinerTask> oreTask = availableMinerTask(level, miner, choosePrimaryTunnelOreTask(level, settlement, stock, mineSite, branch, bottomUp));
			if (oreTask.isPresent()) {
				return oreTask;
			}
		}

		for (PrimaryTunnelBranch branch : branches) {
			if (isPrimaryTunnelClosed(level, mineSite, branch)) {
				continue;
			}

			Optional<MinerTask> lightTask = availableMinerTask(level, miner, choosePrimaryTunnelLightTask(level, settlement, stock, mineSite, branch));
			if (lightTask.isPresent()) {
				return lightTask;
			}
		}

		for (PrimaryTunnelBranch branch : branches) {
			if (isPrimaryTunnelClosed(level, mineSite, branch)) {
				continue;
			}

			Optional<MinerTask> excavationTask = availableMinerTask(level, miner, choosePrimaryTunnelExcavationTask(level, settlement, stock, mineSite, branch));
			if (excavationTask.isPresent()) {
				return excavationTask;
			}
		}

		Optional<MinerTask> secondaryTask = availableMinerTask(level, miner, chooseSecondaryTunnelTask(level, settlement, stock, miner, mineSite, branches, bottomUp));
		if (secondaryTask.isPresent()) {
			return secondaryTask;
		}

		return Optional.empty();
	}

	private static Optional<MinerTask> choosePrimaryTunnelOreTask(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		MineSite mineSite,
		PrimaryTunnelBranch branch,
		int bottomUp
	) {
		int tunnelLength = primaryTunnelLengthBlocks(settlement, mineSite, branch);
		for (int step = 0; step < tunnelLength; step++) {
			if (!isPrimaryTunnelRowOpen(level, mineSite, branch, step)) {
				break;
			}

			List<BlockPos> standPositions = primaryTunnelStandPositions(level, mineSite, branch, step);
			if (standPositions.isEmpty()) {
				break;
			}

			Set<BlockPos> airCells = primaryTunnelAirCells(mineSite, branch, step);
			Optional<MinerTask> discoveryTask = chooseInterestingDiscoverySignTask(level, mineSite, branch, step, airCells, standPositions.getFirst());
			if (discoveryTask.isPresent()) {
				return discoveryTask;
			}

			Optional<OreWorkTarget> oreTarget = exposedOreFromAirCells(level, airCells, standPositions);
			if (oreTarget.isEmpty()) {
				continue;
			}

			BlockPos targetPos = oreTarget.get().targetPos();
			BlockState replacementState = null;

			if (requiresLadderSupportReplacement(targetPos, mineSite, bottomUp)) {
				replacementState = supportReplacementMaterial(stock);

				if (replacementState == null) {
					continue;
				}
			}

			return Optional.of(new MinerTask(MinerAction.MINE_ORE, targetPos, replacementState, oreTarget.get().standPos(), "mining_primary_tunnel_ore"));
		}

		return Optional.empty();
	}

	private static Optional<MinerTask> choosePrimaryTunnelExcavationTask(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		MineSite mineSite,
		PrimaryTunnelBranch branch
	) {
		BlockPos workingStandPos = worldPos(mineSite.buildSite(), branch.anchorColumn(), branch.levelUp());

		if (!level.getBlockState(workingStandPos).is(Blocks.LADDER)) {
			return Optional.empty();
		}

		int tunnelLength = primaryTunnelLengthBlocks(settlement, mineSite, branch);
		for (int step = 0; step < tunnelLength; step++) {
			if (step > 0) {
				List<BlockPos> previousStandPositions = primaryTunnelStandPositions(level, mineSite, branch, step - 1);

				if (previousStandPositions.isEmpty()) {
					break;
				}

				workingStandPos = previousStandPositions.getFirst();
			}

			for (int width = 0; width < PRIMARY_TUNNEL_WIDTH_BLOCKS; width++) {
				BlockPos targetFloorPos = branchTunnelFloorPos(mineSite, branch, step, width);

				if (!level.getBlockState(targetFloorPos.below()).isSolid()) {
					BlockState supportState = supportReplacementMaterial(stock);
					if (supportState == null) {
						return Optional.empty();
					}

					return Optional.of(new MinerTask(MinerAction.PLACE_SUPPORT, targetFloorPos.below(), supportState, workingStandPos, "placing_primary_tunnel_support"));
				}

				for (int height = 0; height < PRIMARY_TUNNEL_HEIGHT_BLOCKS; height++) {
					BlockPos targetPos = targetFloorPos.above(height);
					BlockState targetState = level.getBlockState(targetPos);
					if (targetState.isAir()) {
						continue;
					}

					if (!canMine(targetState, level, targetPos)) {
						return Optional.empty();
					}

					return Optional.of(new MinerTask(MinerAction.DIG_TUNNEL, targetPos, null, workingStandPos, "digging_primary_tunnel"));
				}
			}
		}

		return Optional.empty();
	}

	private static Optional<MinerTask> choosePrimaryTunnelLightTask(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		MineSite mineSite,
		PrimaryTunnelBranch branch
	) {
		BlockState lightState = preferredPrimaryTunnelLightState(stock, mineSite);
		if (lightState == null) {
			return Optional.empty();
		}

		int tunnelLength = primaryTunnelLengthBlocks(settlement, mineSite, branch);

		for (int step = PRIMARY_TUNNEL_LIGHT_SPACING_BLOCKS - 1; step < tunnelLength; step += PRIMARY_TUNNEL_LIGHT_SPACING_BLOCKS) {
			if (!isPrimaryTunnelRowOpen(level, mineSite, branch, step)) {
				break;
			}

			BlockPos floorPos = branchTunnelFloorPos(mineSite, branch, step, 0);
			BlockPos lightPos = floorPos.above(1);
			if (isCaveLight(level.getBlockState(lightPos))) {
				continue;
			}
			if (!level.getBlockState(lightPos).isAir()) {
				continue;
			}

			return Optional.of(new MinerTask(MinerAction.PLACE_LIGHT, lightPos, lightState, floorPos, "lighting_primary_tunnel"));
		}

		return Optional.empty();
	}

	private static Optional<MinerTask> chooseSecondaryTunnelTask(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Villager miner,
		MineSite mineSite,
		List<PrimaryTunnelBranch> primaryBranches,
		int bottomUp
	) {
		for (PrimaryTunnelBranch primaryBranch : primaryBranches) {
			if (isPrimaryTunnelClosed(level, mineSite, primaryBranch)) {
				continue;
			}

			int primaryLength = primaryTunnelLengthBlocks(settlement, mineSite, primaryBranch);

			for (int primaryStep = SECONDARY_TUNNEL_SPACING_BLOCKS - 1; primaryStep < primaryLength; primaryStep += SECONDARY_TUNNEL_SPACING_BLOCKS) {
				if (!isPrimaryTunnelRowOpen(level, mineSite, primaryBranch, primaryStep)) {
					break;
				}

				for (SecondaryTunnelBranch secondaryBranch : secondaryTunnelBranches(mineSite, primaryBranch, primaryStep)) {
					Set<BlockPos> secondaryAirCells = secondaryBranchOpenAirCells(level, settlement, mineSite, secondaryBranch);
					Optional<MinerTask> discoveryTask = chooseInterestingDiscoverySignTask(
						level,
						mineSite,
						primaryBranch,
						primaryStep,
						secondaryAirCells,
						branchTunnelFloorPos(mineSite, primaryBranch, primaryStep, 0)
					);
					if (discoveryTask.isPresent()) {
						return discoveryTask;
					}

					Optional<MinerTask> closureTask = chooseTunnelClosureTask(level, miner, mineSite, primaryBranch, secondaryAirCells);
					if (closureTask.isPresent()) {
						return closureTask;
					}

					Optional<MinerTask> oreTask = chooseSecondaryTunnelOreTask(level, settlement, stock, mineSite, secondaryBranch, bottomUp);
					if (oreTask.isPresent()) {
						return oreTask;
					}

					Optional<MinerTask> lightTask = chooseSecondaryTunnelLightTask(level, settlement, stock, mineSite, secondaryBranch);
					if (lightTask.isPresent()) {
						return lightTask;
					}

					Optional<MinerTask> excavationTask = chooseSecondaryTunnelExcavationTask(level, settlement, stock, mineSite, secondaryBranch);
					if (excavationTask.isPresent()) {
						return excavationTask;
					}
				}
			}
		}

		return Optional.empty();
	}

	private static Optional<MinerTask> chooseSecondaryTunnelOreTask(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		MineSite mineSite,
		SecondaryTunnelBranch branch,
		int bottomUp
	) {
		int tunnelLength = secondaryTunnelLengthBlocks(settlement, mineSite, branch);

		for (int step = 0; step < tunnelLength; step++) {
			if (!isSecondaryTunnelCellOpen(level, mineSite, branch, step)) {
				break;
			}

			BlockPos standPos = secondaryTunnelFloorPos(mineSite, branch, step);
			Optional<OreWorkTarget> oreTarget = exposedOreFromAirCells(level, secondaryTunnelAirCells(mineSite, branch, step), List.of(standPos));
			if (oreTarget.isEmpty()) {
				continue;
			}

			BlockPos targetPos = oreTarget.get().targetPos();
			BlockState replacementState = null;

			if (requiresLadderSupportReplacement(targetPos, mineSite, bottomUp)) {
				replacementState = supportReplacementMaterial(stock);

				if (replacementState == null) {
					continue;
				}
			}

			return Optional.of(new MinerTask(MinerAction.MINE_ORE, targetPos, replacementState, oreTarget.get().standPos(), "mining_secondary_tunnel_ore"));
		}

		return Optional.empty();
	}

	private static Optional<MinerTask> chooseSecondaryTunnelLightTask(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		MineSite mineSite,
		SecondaryTunnelBranch branch
	) {
		BlockState lightState = preferredSecondaryTunnelLightState(stock, branch.direction());
		if (lightState == null) {
			return Optional.empty();
		}

		int tunnelLength = secondaryTunnelLengthBlocks(settlement, mineSite, branch);

		for (int step = SECONDARY_TUNNEL_LIGHT_SPACING_BLOCKS - 1; step < tunnelLength; step += SECONDARY_TUNNEL_LIGHT_SPACING_BLOCKS) {
			if (!isSecondaryTunnelCellOpen(level, mineSite, branch, step)) {
				break;
			}

			BlockPos floorPos = secondaryTunnelFloorPos(mineSite, branch, step);
			BlockPos lightPos = floorPos.above(1);
			if (isCaveLight(level.getBlockState(lightPos))) {
				continue;
			}
			if (!level.getBlockState(lightPos).isAir()) {
				continue;
			}

			return Optional.of(new MinerTask(MinerAction.PLACE_LIGHT, lightPos, lightState, floorPos, "lighting_secondary_tunnel"));
		}

		return Optional.empty();
	}

	private static Optional<MinerTask> chooseSecondaryTunnelExcavationTask(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		MineSite mineSite,
		SecondaryTunnelBranch branch
	) {
		int tunnelLength = secondaryTunnelLengthBlocks(settlement, mineSite, branch);

		for (int step = 0; step < tunnelLength; step++) {
			BlockPos workingStandPos = secondaryTunnelWorkStandPos(level, mineSite, branch, step).orElse(null);
			if (workingStandPos == null) {
				break;
			}

			BlockPos floorPos = secondaryTunnelFloorPos(mineSite, branch, step);
			if (!level.getBlockState(floorPos.below()).isSolid()) {
				BlockState supportState = supportReplacementMaterial(stock);
				if (supportState == null) {
					return Optional.empty();
				}

				return Optional.of(new MinerTask(MinerAction.PLACE_SUPPORT, floorPos.below(), supportState, workingStandPos, "placing_secondary_tunnel_support"));
			}

			for (int height = 0; height < SECONDARY_TUNNEL_HEIGHT_BLOCKS; height++) {
				BlockPos targetPos = floorPos.above(height);
				BlockState targetState = level.getBlockState(targetPos);
				if (isClearTunnelCell(targetState)) {
					continue;
				}

				if (!canMine(targetState, level, targetPos)) {
					return Optional.empty();
				}

				return Optional.of(new MinerTask(MinerAction.DIG_TUNNEL, targetPos, null, workingStandPos, "digging_secondary_tunnel"));
			}
		}

		return Optional.empty();
	}

	private static List<PrimaryTunnelBranch> plannedPrimaryTunnelBranches(MineSite mineSite, int bottomUp) {
		if (mineSite.layout().ladderColumns().isEmpty()) {
			return List.of();
		}

		ShaftColumn leftAnchor = mineSite.layout().ladderColumns().stream()
			.min(Comparator.comparingInt(ShaftColumn::right))
			.orElse(mineSite.layout().ladderColumns().getFirst());
		ShaftColumn rightAnchor = mineSite.layout().ladderColumns().stream()
			.max(Comparator.comparingInt(ShaftColumn::right))
			.orElse(mineSite.layout().ladderColumns().getLast());
		List<PrimaryTunnelBranch> branches = new ArrayList<>();
		Direction leftDirection = mineSite.buildSite().facing().getCounterClockWise();
		Direction rightDirection = mineSite.buildSite().facing().getClockWise();

		for (int up = -1; up >= bottomUp; up--) {
			if (!shouldCreatePrimaryTunnelLevel(up)) {
				continue;
			}

			branches.add(new PrimaryTunnelBranch(up, leftAnchor, leftDirection));
			branches.add(new PrimaryTunnelBranch(up, rightAnchor, rightDirection));
		}

		return branches;
	}

	private static boolean shouldCreatePrimaryTunnelLevel(int up) {
		return up < 0 && Math.floorMod(Math.abs(up), PRIMARY_TUNNEL_INTERVAL_LEVELS) == 0;
	}

	private static BlockPos branchTunnelFloorPos(MineSite mineSite, PrimaryTunnelBranch branch, int step, int widthOffset) {
		return worldPos(mineSite.buildSite(), branch.anchorColumn(), branch.levelUp())
			.relative(branch.direction(), step + 1)
			.relative(primaryTunnelWidthDirection(mineSite), widthOffset);
	}

	private static int primaryTunnelLengthBlocks(SettlementState settlement, MineSite mineSite, PrimaryTunnelBranch branch) {
		int radius = SettlementVillagers.settlementRadiusBlocks(settlement);
		int length = 0;

		while (length < radius * 2) {
			boolean insideSettlement = true;

			for (int width = 0; width < PRIMARY_TUNNEL_WIDTH_BLOCKS; width++) {
				if (horizontalDistanceSqr(branchTunnelFloorPos(mineSite, branch, length, width), settlement.center()) > (double) radius * radius) {
					insideSettlement = false;
					break;
				}
			}

			if (!insideSettlement) {
				break;
			}

			length++;
		}

		return length;
	}

	private static double horizontalDistanceSqr(BlockPos first, BlockPos second) {
		double dx = (first.getX() + 0.5D) - (second.getX() + 0.5D);
		double dz = (first.getZ() + 0.5D) - (second.getZ() + 0.5D);
		return (dx * dx) + (dz * dz);
	}

	private static Direction primaryTunnelWidthDirection(MineSite mineSite) {
		return mineSite.buildSite().facing();
	}

	private static BlockPos primaryTunnelClosureSignPos(MineSite mineSite, PrimaryTunnelBranch branch) {
		return branchTunnelFloorPos(mineSite, branch, 0, PRIMARY_TUNNEL_WIDTH_BLOCKS - 1);
	}

	private static boolean isPrimaryTunnelClosed(ServerLevel level, MineSite mineSite, PrimaryTunnelBranch branch) {
		return isMonsterClosureSign(level, primaryTunnelClosureSignPos(mineSite, branch));
	}

	private static boolean isMonsterClosureSign(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);

		if (!(state.getBlock() instanceof StandingSignBlock) && !(state.getBlock() instanceof WallSignBlock)) {
			return false;
		}

		if (!(level.getBlockEntity(pos) instanceof SignBlockEntity signBlockEntity)) {
			return true;
		}

		for (boolean front : List.of(true, false)) {
			SignText text = signBlockEntity.getText(front);

			for (int line = 0; line < 4; line++) {
				String message = text.getMessage(line, false).getString().toLowerCase();

				if (message.contains("monster")) {
					return true;
				}
			}
		}

		return false;
	}

	private static List<BlockPos> shaftCavernClosureSignCandidates(MineSite mineSite, int bottomUp) {
		return mineSite.layout().openColumns().stream()
			.map(column -> worldPos(mineSite.buildSite(), column, bottomUp))
			.toList();
	}

	private static boolean isShaftCavernQuarantined(ServerLevel level, MineSite mineSite, int bottomUp) {
		return shaftCavernClosureSignCandidates(mineSite, bottomUp).stream()
			.anyMatch(pos -> isMonsterClosureSign(level, pos));
	}

	private static Optional<MinerTask> chooseShaftCavernClosureTask(
		ServerLevel level,
		MineSite mineSite,
		int bottomUp,
		BlockPos standPos,
		Set<BlockPos> caveAirCells
	) {
		if (caveAirCells.isEmpty() || isShaftCavernQuarantined(level, mineSite, bottomUp)) {
			return Optional.empty();
		}

		if (!hostilesPresentNearAirCells(level, caveAirCells)) {
			return Optional.empty();
		}

		Direction signFacing = mineSite.supportDirection().getOpposite();

		for (BlockPos signPos : shaftCavernClosureSignCandidates(mineSite, bottomUp)) {
			if (!canPlaceStandingSign(level, signPos)) {
				continue;
			}

			BlockState signState = Blocks.OAK_SIGN.defaultBlockState()
				.setValue(StandingSignBlock.ROTATION, signRotationForFacing(signFacing));
			return Optional.of(new MinerTask(
				MinerAction.PLACE_WARNING_SIGN,
				signPos,
				signState,
				standPos,
				"closing_monster_shaft_cavern",
				List.of("Cavern closed", "due to", "monsters")
			));
		}

		return Optional.empty();
	}

	private static boolean hostilesPresentNearAirCells(ServerLevel level, Set<BlockPos> airCells) {
		if (airCells.isEmpty()) {
			return false;
		}

		AABB bounds = airCells.stream()
			.map(AABB::new)
			.reduce(AABB::minmax)
			.orElseGet(() -> new AABB(BlockPos.ZERO))
			.inflate(TUNNEL_HOSTILE_DETECTION_RADIUS_BLOCKS);
		double radiusSquared = (double) TUNNEL_HOSTILE_DETECTION_RADIUS_BLOCKS * TUNNEL_HOSTILE_DETECTION_RADIUS_BLOCKS;

		return level.getEntitiesOfClass(Monster.class, bounds, monster -> monster.isAlive() && !monster.isRemoved()).stream()
			.anyMatch(monster -> airCells.stream().anyMatch(airCell -> monster.blockPosition().distSqr(airCell) <= radiusSquared));
	}

	private static Optional<MinerTask> chooseTunnelClosureTask(
		ServerLevel level,
		Villager miner,
		MineSite mineSite,
		PrimaryTunnelBranch branch,
		Set<BlockPos> tunnelAirCells
	) {
		if (tunnelAirCells.isEmpty() || isPrimaryTunnelClosed(level, mineSite, branch)) {
			return Optional.empty();
		}

		if (!hostilesPresentNearAirCells(level, tunnelAirCells)) {
			return Optional.empty();
		}

		BlockPos signPos = primaryTunnelClosureSignPos(mineSite, branch);
		if (!canPlaceStandingSign(level, signPos)) {
			return Optional.empty();
		}

		BlockState signState = Blocks.OAK_SIGN.defaultBlockState()
			.setValue(StandingSignBlock.ROTATION, signRotationForFacing(branch.direction().getOpposite()));
		BlockPos ladderStandPos = worldPos(mineSite.buildSite(), branch.anchorColumn(), branch.levelUp());
		return Optional.of(new MinerTask(
			MinerAction.PLACE_WARNING_SIGN,
			signPos,
			signState,
			ladderStandPos,
			"closing_monster_tunnel",
			List.of("Tunnel closed", "due to", "monsters")
		));
	}

	private static Optional<MinerTask> chooseInterestingDiscoverySignTask(
		ServerLevel level,
		MineSite mineSite,
		PrimaryTunnelBranch branch,
		int primaryStep,
		Set<BlockPos> tunnelAirCells,
		BlockPos standPos
	) {
		if (tunnelAirCells.isEmpty() || hasPrimaryTunnelMarkerSign(level, mineSite, branch, primaryStep)) {
			return Optional.empty();
		}

		Optional<InterestingDiscovery> discovery = interestingDiscoveryFromAirCells(level, tunnelAirCells);
		if (discovery.isEmpty()) {
			return Optional.empty();
		}

		return primaryTunnelMarkerSignTarget(level, mineSite, branch, primaryStep)
			.map(signTarget -> new MinerTask(
				MinerAction.PLACE_POI_SIGN,
				signTarget.pos(),
				signTarget.state(),
				standPos,
				"marking_underground_discovery",
				discovery.get().signLines()
			));
	}

	private static Optional<MinerTask> choosePrimaryTunnelDiscoveryTask(
		ServerLevel level,
		SettlementState settlement,
		MineSite mineSite,
		PrimaryTunnelBranch branch
	) {
		int tunnelLength = primaryTunnelLengthBlocks(settlement, mineSite, branch);

		for (int step = 0; step < tunnelLength; step++) {
			if (!isPrimaryTunnelRowOpen(level, mineSite, branch, step)) {
				break;
			}

			List<BlockPos> standPositions = primaryTunnelStandPositions(level, mineSite, branch, step);
			if (standPositions.isEmpty()) {
				break;
			}

			Optional<MinerTask> discoveryTask = chooseInterestingDiscoverySignTask(
				level,
				mineSite,
				branch,
				step,
				primaryTunnelAirCells(mineSite, branch, step),
				standPositions.getFirst()
			);
			if (discoveryTask.isPresent()) {
				return discoveryTask;
			}
		}

		return Optional.empty();
	}

	private static Optional<InterestingDiscovery> interestingDiscoveryFromAirCells(ServerLevel level, Set<BlockPos> airCells) {
		for (BlockPos airCell : airCells.stream().sorted(Comparator.comparingInt(BlockPos::getY)).toList()) {
			for (Direction direction : Direction.values()) {
				BlockState state = level.getBlockState(airCell.relative(direction));

				if (isAncientCityIndicator(state)) {
					return Optional.of(new InterestingDiscovery(List.of("Ancient city", "nearby")));
				}

				if (isSculkSiteIndicator(state)) {
					return Optional.of(new InterestingDiscovery(List.of("Sculk site", "nearby")));
				}

				if (isGeodeIndicator(state)) {
					return Optional.of(new InterestingDiscovery(List.of("Geode", "nearby")));
				}
			}
		}

		return Optional.empty();
	}

	private static boolean isGeodeIndicator(BlockState state) {
		return state.is(Blocks.AMETHYST_BLOCK)
			|| state.is(Blocks.BUDDING_AMETHYST)
			|| state.is(Blocks.CALCITE)
			|| state.is(Blocks.SMOOTH_BASALT);
	}

	private static boolean isAncientCityIndicator(BlockState state) {
		return state.is(Blocks.REINFORCED_DEEPSLATE);
	}

	private static boolean isSculkSiteIndicator(BlockState state) {
		return state.is(Blocks.SCULK)
			|| state.is(Blocks.SCULK_CATALYST)
			|| state.is(Blocks.SCULK_SHRIEKER)
			|| state.is(Blocks.SCULK_SENSOR);
	}

	private static boolean hasPrimaryTunnelMarkerSign(ServerLevel level, MineSite mineSite, PrimaryTunnelBranch branch, int step) {
		return primaryTunnelMarkerSignTargets(mineSite, branch, step).stream()
			.anyMatch(target -> level.getBlockEntity(target.pos()) instanceof SignBlockEntity);
	}

	private static Optional<SignTarget> primaryTunnelMarkerSignTarget(ServerLevel level, MineSite mineSite, PrimaryTunnelBranch branch, int step) {
		return primaryTunnelMarkerSignTargets(mineSite, branch, step).stream()
			.filter(target -> canPlaceWallSign(level, target.pos(), target.facing()))
			.findFirst();
	}

	private static List<SignTarget> primaryTunnelMarkerSignTargets(MineSite mineSite, PrimaryTunnelBranch branch, int step) {
		Direction widthDirection = primaryTunnelWidthDirection(mineSite);
		BlockPos nearWallPos = branchTunnelFloorPos(mineSite, branch, step, 0).above(1);
		BlockPos farWallPos = branchTunnelFloorPos(mineSite, branch, step, PRIMARY_TUNNEL_WIDTH_BLOCKS - 1).above(1);
		return List.of(
			new SignTarget(nearWallPos, Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, widthDirection), widthDirection),
			new SignTarget(farWallPos, Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, widthDirection.getOpposite()), widthDirection.getOpposite())
		);
	}

	private static boolean isPrimaryTunnelRowOpen(ServerLevel level, MineSite mineSite, PrimaryTunnelBranch branch, int step) {
		for (int width = 0; width < PRIMARY_TUNNEL_WIDTH_BLOCKS; width++) {
			BlockPos floorPos = branchTunnelFloorPos(mineSite, branch, step, width);

			if (!level.getBlockState(floorPos.below()).isSolid()) {
				return false;
			}

			for (int height = 0; height < PRIMARY_TUNNEL_HEIGHT_BLOCKS; height++) {
				if (!isClearTunnelCell(level.getBlockState(floorPos.above(height)))) {
					return false;
				}
			}
		}

		return true;
	}

	private static Set<BlockPos> primaryBranchOpenAirCells(ServerLevel level, SettlementState settlement, MineSite mineSite, PrimaryTunnelBranch branch) {
		Set<BlockPos> airCells = new LinkedHashSet<>();
		int tunnelLength = primaryTunnelLengthBlocks(settlement, mineSite, branch);

		for (int step = 0; step < tunnelLength; step++) {
			if (!isPrimaryTunnelRowOpen(level, mineSite, branch, step)) {
				break;
			}

			airCells.addAll(primaryTunnelAirCells(mineSite, branch, step));
		}

		return airCells;
	}

	private static List<BlockPos> primaryTunnelStandPositions(ServerLevel level, MineSite mineSite, PrimaryTunnelBranch branch, int step) {
		List<BlockPos> positions = new ArrayList<>();

		for (int width = 0; width < PRIMARY_TUNNEL_WIDTH_BLOCKS; width++) {
			BlockPos floorPos = branchTunnelFloorPos(mineSite, branch, step, width);
			if (isStandableTunnelCell(level, floorPos) && isClearTunnelCell(level.getBlockState(floorPos.above(2)))) {
				positions.add(floorPos);
			}
		}

		return positions;
	}

	private static Set<BlockPos> primaryTunnelAirCells(MineSite mineSite, PrimaryTunnelBranch branch, int step) {
		Set<BlockPos> airCells = new LinkedHashSet<>();

		for (int width = 0; width < PRIMARY_TUNNEL_WIDTH_BLOCKS; width++) {
			BlockPos floorPos = branchTunnelFloorPos(mineSite, branch, step, width);

			for (int height = 0; height < PRIMARY_TUNNEL_HEIGHT_BLOCKS; height++) {
				airCells.add(floorPos.above(height).immutable());
			}
		}

		return airCells;
	}

	private static List<SecondaryTunnelBranch> secondaryTunnelBranches(MineSite mineSite, PrimaryTunnelBranch primaryBranch, int primaryStep) {
		Direction widthDirection = primaryTunnelWidthDirection(mineSite);
		return List.of(
			new SecondaryTunnelBranch(branchTunnelFloorPos(mineSite, primaryBranch, primaryStep, 0), widthDirection.getOpposite()),
			new SecondaryTunnelBranch(branchTunnelFloorPos(mineSite, primaryBranch, primaryStep, PRIMARY_TUNNEL_WIDTH_BLOCKS - 1), widthDirection)
		);
	}

	private static int secondaryTunnelLengthBlocks(SettlementState settlement, MineSite mineSite, SecondaryTunnelBranch branch) {
		int radius = SettlementVillagers.settlementRadiusBlocks(settlement);
		int length = 0;

		while (length < radius * 2
			&& horizontalDistanceSqr(secondaryTunnelFloorPos(mineSite, branch, length), settlement.center()) <= (double) radius * radius) {
			length++;
		}

		return length;
	}

	private static BlockPos secondaryTunnelFloorPos(MineSite mineSite, SecondaryTunnelBranch branch, int step) {
		return branch.originPos().relative(branch.direction(), step + 1);
	}

	private static boolean isSecondaryTunnelCellOpen(ServerLevel level, MineSite mineSite, SecondaryTunnelBranch branch, int step) {
		BlockPos floorPos = secondaryTunnelFloorPos(mineSite, branch, step);

		if (!level.getBlockState(floorPos.below()).isSolid()) {
			return false;
		}

		for (int height = 0; height < SECONDARY_TUNNEL_HEIGHT_BLOCKS; height++) {
			if (!isClearTunnelCell(level.getBlockState(floorPos.above(height)))) {
				return false;
			}
		}

		return true;
	}

	private static Set<BlockPos> secondaryTunnelAirCells(MineSite mineSite, SecondaryTunnelBranch branch, int step) {
		Set<BlockPos> airCells = new LinkedHashSet<>();
		BlockPos floorPos = secondaryTunnelFloorPos(mineSite, branch, step);

		for (int height = 0; height < SECONDARY_TUNNEL_HEIGHT_BLOCKS; height++) {
			airCells.add(floorPos.above(height).immutable());
		}

		return airCells;
	}

	private static Set<BlockPos> secondaryBranchOpenAirCells(ServerLevel level, SettlementState settlement, MineSite mineSite, SecondaryTunnelBranch branch) {
		Set<BlockPos> airCells = new LinkedHashSet<>();
		int tunnelLength = secondaryTunnelLengthBlocks(settlement, mineSite, branch);

		for (int step = 0; step < tunnelLength; step++) {
			if (!isSecondaryTunnelCellOpen(level, mineSite, branch, step)) {
				break;
			}

			airCells.addAll(secondaryTunnelAirCells(mineSite, branch, step));
		}

		return airCells;
	}

	private static Optional<BlockPos> secondaryTunnelWorkStandPos(ServerLevel level, MineSite mineSite, SecondaryTunnelBranch branch, int step) {
		if (step <= 0) {
			return isStandableTunnelCell(level, branch.originPos()) ? Optional.of(branch.originPos()) : Optional.empty();
		}

		BlockPos previousFloorPos = secondaryTunnelFloorPos(mineSite, branch, step - 1);
		return isStandableTunnelCell(level, previousFloorPos) ? Optional.of(previousFloorPos) : Optional.empty();
	}

	private static boolean performTask(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Villager miner,
		MineSite mineSite,
		MinerTask task
	) {
		Map<String, Integer> beforeStock = new HashMap<>(stock);
		boolean completed = switch (task.action()) {
			case DIG_SHAFT -> mineBlockIntoStock(level, settlement, stock, miner, task.targetPos(), null);
			case DIG_TUNNEL -> mineBlockIntoStock(level, settlement, stock, miner, task.targetPos(), null);
			case MINE_ORE -> mineBlockIntoStock(level, settlement, stock, miner, task.targetPos(), task.replacementState());
			case MINE_STONE -> mineBlockIntoStock(level, settlement, stock, miner, task.targetPos(), null);
			case PLACE_SUPPORT -> placeSupport(level, stock, task.targetPos(), task.replacementState());
			case PLACE_LADDER -> placeLadder(level, stock, task.targetPos(), task.replacementState(), mineSite.supportDirection());
			case PLACE_LIGHT -> placeLight(level, stock, task.targetPos(), task.replacementState());
			case PLACE_WARNING_SIGN -> placeSign(level, task.targetPos(), task.replacementState(), task.signLines());
			case PLACE_POI_SIGN -> placeSign(level, task.targetPos(), task.replacementState(), task.signLines());
		};

		if (completed) {
			SettlementProfessionReports.recordStockDeltas(
				level,
				settlement,
				SettlementRoleKeys.MINER,
				miner,
				beforeStock,
				stock,
				null
			);
			SettlementProfessionReports.recordAccomplished(level, settlement, SettlementRoleKeys.MINER, miner, task.taskKey().replace('_', ' '));
		}

		return completed;
	}

	private static boolean placeSign(ServerLevel level, BlockPos targetPos, BlockState signState, List<String> lines) {
		if (signState == null || !level.getBlockState(targetPos).isAir()) {
			return false;
		}

		level.setBlock(targetPos, signState, 3);

		if (level.getBlockEntity(targetPos) instanceof SignBlockEntity signBlockEntity) {
			SignText text = new SignText();

			for (int i = 0; i < Math.min(4, lines.size()); i++) {
				text = text.setMessage(i, Component.literal(lines.get(i)));
			}

			signBlockEntity.setText(text, true);
			signBlockEntity.setChanged();
			level.sendBlockUpdated(targetPos, signState, signState, 3);
		}

		return true;
	}

	private static boolean placeLight(ServerLevel level, Map<String, Integer> stock, BlockPos targetPos, BlockState lightState) {
		if (lightState == null
			|| !level.getBlockState(targetPos).isAir()
			|| !lightState.canSurvive(level, targetPos)
			|| !consumeLightSource(stock, lightState)) {
			return false;
		}

		level.setBlock(targetPos, lightState, 3);
		return true;
	}

	private static boolean placeSupport(ServerLevel level, Map<String, Integer> stock, BlockPos targetPos, BlockState supportState) {
		if (supportState == null || !consumeSupportMaterial(stock, supportState)) {
			return false;
		}

		level.setBlock(targetPos, supportState, 3);
		return true;
	}

	private static boolean placeLadder(
		ServerLevel level,
		Map<String, Integer> stock,
		BlockPos targetPos,
		BlockState ladderState,
		Direction supportDirection
	) {
		if (ladderState == null || !canPlaceLadder(level, targetPos, supportDirection) || !consumeLadder(stock)) {
			return false;
		}

		level.setBlock(targetPos, ladderState, 3);
		return true;
	}

	private static boolean mineBlockIntoStock(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Villager miner,
		BlockPos targetPos,
		BlockState replacementState
	) {
		BlockState targetState = level.getBlockState(targetPos);

		if (!canMine(targetState, level, targetPos)) {
			return false;
		}

		if (replacementState != null && !consumeSupportMaterial(stock, replacementState)) {
			return false;
		}

		Map<String, Integer> drops = new LinkedHashMap<>();
		for (ItemStack drop : Block.getDrops(targetState, level, targetPos, null, null, new ItemStack(Items.IRON_PICKAXE))) {
			String goodsKey = SettlementGoods.goodsKeyForItem(drop);

			if (goodsKey != null) {
				SettlementGoods.addGoods(stock, goodsKey, drop.getCount());
				drops.merge(goodsKey, drop.getCount(), Integer::sum);
			}
		}

		SettlementProfessionReports.recordMinedBlock(level, settlement, SettlementRoleKeys.MINER, miner, targetState, drops);

		level.levelEvent(2001, targetPos, Block.getId(targetState));

		if (replacementState == null) {
			level.setBlock(targetPos, Blocks.AIR.defaultBlockState(), 3);
		} else {
			level.setBlock(targetPos, replacementState, 3);
		}

		return true;
	}

	private static boolean canMine(BlockState state, ServerLevel level, BlockPos pos) {
		return !state.isAir()
			&& !state.is(Blocks.BEDROCK)
			&& state.getDestroySpeed(level, pos) >= 0.0F
			&& state.getFluidState().isEmpty();
	}

	private static boolean canPlaceLadder(ServerLevel level, BlockPos targetPos, Direction supportDirection) {
		BlockState currentState = level.getBlockState(targetPos);

		if (!currentState.isAir()) {
			return false;
		}

		return isStableLadderSupport(level.getBlockState(targetPos.relative(supportDirection)));
	}

	private static Optional<BlockPos> findExposedOreTarget(ServerLevel level, MineSite mineSite, List<BlockPos> frontierCells, BlockPos standPos) {
		Set<BlockPos> candidates = new LinkedHashSet<>();

		for (BlockPos shaftPos : frontierCells) {
			for (Direction direction : Direction.values()) {
				BlockPos candidatePos = shaftPos.relative(direction);

				if (frontierCells.contains(candidatePos)
					|| standPos.distSqr(candidatePos) > MINING_WORK_REACH_DISTANCE_SQUARED
					|| !isUsefulOre(level.getBlockState(candidatePos))) {
					continue;
				}

				candidates.add(candidatePos.immutable());
			}
		}

		return candidates.stream()
			.sorted(Comparator.comparingDouble(candidate -> candidate.distSqr(mineSite.buildSite().workstationPos())))
			.findFirst();
	}

	private static Optional<BlockPos> findExposedStoneTarget(ServerLevel level, MineSite mineSite, List<BlockPos> frontierCells, BlockPos standPos) {
		Set<BlockPos> candidates = new LinkedHashSet<>();

		for (BlockPos shaftPos : frontierCells) {
			for (Direction direction : Direction.values()) {
				BlockPos candidatePos = shaftPos.relative(direction);

				if (frontierCells.contains(candidatePos)
					|| standPos.distSqr(candidatePos) > MINING_WORK_REACH_DISTANCE_SQUARED
					|| candidatePos.equals(mineSite.buildSite().workstationPos())
					|| !isUsefulStone(level.getBlockState(candidatePos))
					|| isRequiredLadderSupport(candidatePos, mineSite)
					|| isProtectedStarterShaftWall(candidatePos, mineSite)) {
					continue;
				}

				candidates.add(candidatePos.immutable());
			}
		}

		return candidates.stream()
			.sorted(Comparator.comparingDouble(candidate -> candidate.distSqr(mineSite.buildSite().workstationPos())))
			.findFirst();
	}

	private static Optional<BlockPos> desiredLightPos(ServerLevel level, MineSite mineSite, int bottomUp) {
		if (!shouldLightLevel(bottomUp)) {
			return Optional.empty();
		}

		List<ShaftColumn> openColumns = mineSite.layout().openColumns();

		if (openColumns.isEmpty()) {
			return Optional.empty();
		}

		int index = Math.floorMod(Math.abs(bottomUp) / SHAFT_LIGHT_SPACING_LEVELS, openColumns.size());
		BlockPos torchPos = worldPos(mineSite.buildSite(), openColumns.get(index), bottomUp);
		BlockState currentState = level.getBlockState(torchPos);
		return isShaftLight(currentState) ? Optional.empty() : Optional.of(torchPos);
	}

	private static Optional<CavePocket> scanNearbyCave(ServerLevel level, MineSite mineSite, List<BlockPos> frontierCells) {
		if (frontierCells.isEmpty()) {
			return Optional.empty();
		}

		Set<BlockPos> frontierSet = new HashSet<>(frontierCells);
		Set<BlockPos> airCells = new LinkedHashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		BlockPos explorationCenter = frontierCells.getLast();

		for (BlockPos frontierCell : frontierCells) {
			for (Direction direction : Direction.values()) {
				BlockPos candidatePos = frontierCell.relative(direction);

				if (frontierSet.contains(candidatePos)
					|| !level.getBlockState(candidatePos).isAir()
					|| candidatePos.distSqr(explorationCenter) > MAX_CAVE_SCAN_RADIUS_BLOCKS * MAX_CAVE_SCAN_RADIUS_BLOCKS) {
					continue;
				}

				if (airCells.add(candidatePos.immutable())) {
					queue.add(candidatePos.immutable());
				}
			}
		}

		while (!queue.isEmpty() && airCells.size() < MAX_CAVE_SCAN_AIR_CELLS) {
			BlockPos currentPos = queue.removeFirst();

			for (Direction direction : Direction.values()) {
				BlockPos nextPos = currentPos.relative(direction);

				if (frontierSet.contains(nextPos)
					|| airCells.contains(nextPos)
					|| !level.getBlockState(nextPos).isAir()
					|| nextPos.distSqr(explorationCenter) > MAX_CAVE_SCAN_RADIUS_BLOCKS * MAX_CAVE_SCAN_RADIUS_BLOCKS) {
					continue;
				}

				airCells.add(nextPos.immutable());
				queue.add(nextPos.immutable());
			}
		}

		if (airCells.isEmpty()) {
			return Optional.empty();
		}

		List<BlockPos> standPositions = airCells.stream()
			.filter(pos -> isStandableCaveCell(level, pos))
			.sorted(Comparator.comparingDouble(pos -> pos.distSqr(explorationCenter)))
			.limit(MAX_CAVE_STAND_POSITIONS)
			.toList();

		if (standPositions.isEmpty()) {
			return Optional.empty();
		}

		List<BlockPos> lightTargets = standPositions.stream()
			.filter(pos -> !frontierSet.contains(pos))
			.toList();

		return Optional.of(new CavePocket(Set.copyOf(airCells), standPositions, lightTargets));
	}

	private static List<BlockPos> shaftFrontierCells(MineSite mineSite, int bottomUp) {
		List<BlockPos> cells = new ArrayList<>();

		for (int up = mineSite.layout().starterMinUp(); up >= bottomUp; up--) {
			for (ShaftColumn column : mineSite.layout().ladderColumns()) {
				cells.add(worldPos(mineSite.buildSite(), column, up));
			}

			for (ShaftColumn column : mineSite.layout().openColumns()) {
				cells.add(worldPos(mineSite.buildSite(), column, up));
			}
		}

		return cells;
	}

	private static List<ShaftColumn> allShaftColumns(MineSite mineSite) {
		Set<ShaftColumn> columns = new LinkedHashSet<>();
		columns.addAll(mineSite.layout().ladderColumns());
		columns.addAll(mineSite.layout().openColumns());
		return new ArrayList<>(columns);
	}

	private static int deepestCompleteShaftUp(ServerLevel level, MineSite mineSite) {
		int bottomUp = mineSite.layout().starterMinUp();

		for (int up = -1; up >= mineSite.layout().starterMinUp() - MAX_SHAFT_DEPTH_BLOCKS; up--) {
			if (!isCompleteShaftLevel(level, mineSite, up)) {
				break;
			}

			bottomUp = up;
		}

		return bottomUp;
	}

	private static boolean isCompleteShaftLevel(ServerLevel level, MineSite mineSite, int up) {
		for (ShaftColumn column : mineSite.layout().ladderColumns()) {
			if (!level.getBlockState(worldPos(mineSite.buildSite(), column, up)).is(Blocks.LADDER)) {
				return false;
			}
		}

		for (ShaftColumn column : mineSite.layout().openColumns()) {
			if (!isClearOpenShaftCell(level.getBlockState(worldPos(mineSite.buildSite(), column, up)))) {
				return false;
			}
		}

		return true;
	}

	private static boolean requiresLadderSupportReplacement(BlockPos targetPos, MineSite mineSite, int bottomUp) {
		for (int up = mineSite.layout().starterMinUp(); up >= bottomUp; up--) {
			for (ShaftColumn ladderColumn : mineSite.layout().ladderColumns()) {
				BlockPos supportPos = worldPos(mineSite.buildSite(), ladderColumn, up).relative(mineSite.supportDirection());

				if (supportPos.equals(targetPos)) {
					return true;
				}
			}
		}

		return false;
	}

	private static boolean requiresShaftStructureReplacement(BlockPos targetPos, MineSite mineSite, int bottomUp) {
		return requiresLadderSupportReplacement(targetPos, mineSite, bottomUp)
			|| isProtectedStarterShaftWall(targetPos, mineSite);
	}

	private static boolean isRequiredLadderSupport(BlockPos targetPos, MineSite mineSite) {
		for (int up = mineSite.layout().starterMinUp() - MAX_SHAFT_DEPTH_BLOCKS; up <= -1; up++) {
			for (ShaftColumn ladderColumn : mineSite.layout().ladderColumns()) {
				if (worldPos(mineSite.buildSite(), ladderColumn, up).relative(mineSite.supportDirection()).equals(targetPos)) {
					return true;
				}
			}
		}

		return false;
	}

	private static boolean shouldStopShaftBeforeLevel(ServerLevel level, MineSite mineSite, int nextUp) {
		for (ShaftColumn column : allShaftColumns(mineSite)) {
			BlockPos targetPos = worldPos(mineSite.buildSite(), column, nextUp);

			if (targetPos.getY() <= level.getMinY()) {
				return true;
			}

			if (level.getBlockState(targetPos).is(Blocks.BEDROCK) || level.getBlockState(targetPos.below()).is(Blocks.BEDROCK)) {
				return true;
			}
		}

		for (ShaftColumn ladderColumn : mineSite.layout().ladderColumns()) {
			BlockPos supportPos = worldPos(mineSite.buildSite(), ladderColumn, nextUp).relative(mineSite.supportDirection());

			if (level.getBlockState(supportPos).is(Blocks.BEDROCK) || level.getBlockState(supportPos.below()).is(Blocks.BEDROCK)) {
				return true;
			}
		}

		return false;
	}

	private static boolean canSupplyNextLadderLevel(Map<String, Integer> stock) {
		Map<String, Integer> stockCopy = new HashMap<>(stock);
		return consumeLadder(stockCopy) && consumeLadder(stockCopy);
	}

	private static boolean canSupplySingleLadder(Map<String, Integer> stock) {
		return consumeLadder(new HashMap<>(stock));
	}

	private static boolean shouldLightLevel(int up) {
		return up < 0 && Math.floorMod(Math.abs(up), SHAFT_LIGHT_SPACING_LEVELS) == 0;
	}

	private static boolean consumeLadder(Map<String, Integer> stock) {
		return SettlementConstructionMaterials.consumeMaterial(stock, new HashMap<>(), "ladder").supplied();
	}

	private static boolean consumeLightSource(Map<String, Integer> stock, BlockState lightState) {
		if (lightState.is(Blocks.COPPER_WALL_TORCH) || lightState.is(Blocks.COPPER_TORCH)) {
			if (SettlementGoods.consumeGoods(stock, "copper_torch", 1)) {
				return true;
			}

			return consumeCopperTorchParts(stock);
		}

		return SettlementConstructionMaterials.consumeMaterial(stock, new HashMap<>(), "torch").supplied();
	}

	private static boolean consumeCopperTorchParts(Map<String, Integer> stock) {
		Map<String, Integer> workingStock = new HashMap<>(stock);

		if (!SettlementRefining.consumeRefinedMaterial(workingStock, "copper_ingot")) {
			return false;
		}

		if (!SettlementConstructionMaterials.consumeMaterial(workingStock, new HashMap<>(), "torch").supplied()) {
			return false;
		}

		stock.clear();
		stock.putAll(workingStock);
		return true;
	}

	private static boolean canSupplyTorch(Map<String, Integer> stock) {
		return SettlementConstructionMaterials.consumeMaterial(new HashMap<>(stock), new HashMap<>(), "torch").supplied();
	}

	private static boolean canSupplyCopperTorch(Map<String, Integer> stock) {
		if (stock.getOrDefault("copper_torch", 0) > 0) {
			return true;
		}

		return consumeCopperTorchParts(new HashMap<>(stock));
	}

	private static boolean consumeSupportMaterial(Map<String, Integer> stock, BlockState state) {
		if (state.is(Blocks.COBBLESTONE)) {
			return SettlementGoods.consumeGoods(stock, "cobblestone", 1);
		}

		if (state.is(Blocks.DIRT)) {
			return SettlementGoods.consumeGoods(stock, "dirt", 1);
		}

		return false;
	}

	private static BlockState supportReplacementMaterial(Map<String, Integer> stock) {
		if (stock.getOrDefault("dirt", 0) > 0) {
			return Blocks.DIRT.defaultBlockState();
		}

		if (stock.getOrDefault("cobblestone", 0) > 0) {
			return Blocks.COBBLESTONE.defaultBlockState();
		}

		return null;
	}

	private static boolean isUsefulOre(BlockState state) {
		return state.is(Blocks.COAL_ORE)
			|| state.is(Blocks.DEEPSLATE_COAL_ORE)
			|| state.is(Blocks.COPPER_ORE)
			|| state.is(Blocks.DEEPSLATE_COPPER_ORE)
			|| state.is(Blocks.IRON_ORE)
			|| state.is(Blocks.DEEPSLATE_IRON_ORE)
			|| state.is(Blocks.GOLD_ORE)
			|| state.is(Blocks.DEEPSLATE_GOLD_ORE)
			|| state.is(Blocks.REDSTONE_ORE)
			|| state.is(Blocks.DEEPSLATE_REDSTONE_ORE)
			|| state.is(Blocks.LAPIS_ORE)
			|| state.is(Blocks.DEEPSLATE_LAPIS_ORE)
			|| state.is(Blocks.DIAMOND_ORE)
			|| state.is(Blocks.DEEPSLATE_DIAMOND_ORE)
			|| state.is(Blocks.EMERALD_ORE)
			|| state.is(Blocks.DEEPSLATE_EMERALD_ORE)
			|| state.is(Blocks.AMETHYST_CLUSTER)
			|| state.is(Blocks.AMETHYST_BLOCK)
			|| state.is(Blocks.CALCITE);
	}

	private static boolean isUsefulStone(BlockState state) {
		return state.is(Blocks.STONE)
			|| state.is(Blocks.DEEPSLATE)
			|| state.is(Blocks.COBBLESTONE)
			|| state.is(Blocks.COBBLED_DEEPSLATE)
			|| state.is(Blocks.TUFF)
			|| state.is(Blocks.ANDESITE)
			|| state.is(Blocks.DIORITE)
			|| state.is(Blocks.GRANITE);
	}

	private static boolean isShaftLight(BlockState state) {
		return state.is(Blocks.WALL_TORCH) || state.is(Blocks.COPPER_WALL_TORCH);
	}

	private static boolean isCaveLight(BlockState state) {
		return state.is(Blocks.TORCH)
			|| state.is(Blocks.COPPER_TORCH)
			|| state.is(Blocks.WALL_TORCH)
			|| state.is(Blocks.COPPER_WALL_TORCH);
	}

	private static boolean isClearOpenShaftCell(BlockState state) {
		return state.isAir() || state.is(Blocks.LADDER) || isShaftLight(state);
	}

	private static boolean isValidShaftOpenCell(ServerLevel level, MineSite mineSite, BlockPos pos, BlockState state) {
		return !isShaftLight(state) || shaftLightSupportDirection(level, mineSite, pos)
			.map(supportDirection -> state.hasProperty(WallTorchBlock.FACING)
				&& state.getValue(WallTorchBlock.FACING) == supportDirection.getOpposite()
				&& state.canSurvive(level, pos))
			.orElse(false);
	}

	private static boolean isClearTunnelCell(BlockState state) {
		return state.isAir() || isCaveLight(state) || state.getBlock() instanceof WallSignBlock;
	}

	private static boolean isStableLadderSupport(BlockState state) {
		return !state.isAir() && state.isSolid();
	}

	private static Optional<Direction> shaftLightSupportDirection(ServerLevel level, MineSite mineSite, BlockPos targetPos) {
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos supportPos = targetPos.relative(direction);
			if (!isShaftColumnAt(mineSite, supportPos, targetPos.getY())
				&& isStableLadderSupport(level.getBlockState(supportPos))) {
				return Optional.of(direction);
			}
		}

		return Optional.empty();
	}

	private static boolean isStandableCaveCell(ServerLevel level, BlockPos pos) {
		return isStandableTunnelCell(level, pos);
	}

	private static boolean isStandableTunnelCell(ServerLevel level, BlockPos pos) {
		return isClearTunnelCell(level.getBlockState(pos))
			&& isClearTunnelCell(level.getBlockState(pos.above()))
			&& level.getBlockState(pos.below()).isSolid();
	}

	private static boolean hasNearbyCaveLight(ServerLevel level, BlockPos center, int radiusBlocks) {
		for (int x = center.getX() - radiusBlocks; x <= center.getX() + radiusBlocks; x++) {
			for (int y = center.getY() - 2; y <= center.getY() + 2; y++) {
				for (int z = center.getZ() - radiusBlocks; z <= center.getZ() + radiusBlocks; z++) {
					BlockPos scanPos = new BlockPos(x, y, z);

					if (scanPos.distSqr(center) <= radiusBlocks * radiusBlocks && isCaveLight(level.getBlockState(scanPos))) {
						return true;
					}
				}
			}
		}

		return false;
	}

	private static BlockState preferredShaftLightState(ServerLevel level, Map<String, Integer> stock, MineSite mineSite, BlockPos targetPos) {
		Optional<Direction> torchSupportDirection = shaftLightSupportDirection(level, mineSite, targetPos);

		if (torchSupportDirection.isEmpty()) {
			return null;
		}

		Direction torchFacing = torchSupportDirection.get().getOpposite();

		if (canSupplyCopperTorch(stock)) {
			return Blocks.COPPER_WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, torchFacing);
		}

		if (canSupplyTorch(stock)) {
			return Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, torchFacing);
		}

		return null;
	}

	private static BlockState preferredCaveLightState(Map<String, Integer> stock) {
		if (canSupplyCopperTorch(stock)) {
			return Blocks.COPPER_TORCH.defaultBlockState();
		}

		if (canSupplyTorch(stock)) {
			return Blocks.TORCH.defaultBlockState();
		}

		return null;
	}

	private static BlockState preferredPrimaryTunnelLightState(Map<String, Integer> stock, MineSite mineSite) {
		Direction torchFacing = primaryTunnelWidthDirection(mineSite);

		if (canSupplyCopperTorch(stock)) {
			return Blocks.COPPER_WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, torchFacing);
		}

		if (canSupplyTorch(stock)) {
			return Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, torchFacing);
		}

		return null;
	}

	private static BlockState preferredSecondaryTunnelLightState(Map<String, Integer> stock, Direction tunnelDirection) {
		Direction torchFacing = tunnelDirection.getClockWise();

		if (canSupplyCopperTorch(stock)) {
			return Blocks.COPPER_WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, torchFacing);
		}

		if (canSupplyTorch(stock)) {
			return Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, torchFacing);
		}

		return null;
	}

	private static boolean canPlaceStandingSign(ServerLevel level, BlockPos pos) {
		return level.hasChunkAt(pos)
			&& level.getBlockState(pos).isAir()
			&& level.getBlockState(pos.below()).isSolid();
	}

	private static boolean canPlaceWallSign(ServerLevel level, BlockPos pos, Direction facing) {
		return level.hasChunkAt(pos)
			&& level.getBlockState(pos).isAir()
			&& level.getBlockState(pos.relative(facing.getOpposite())).isSolid();
	}

	private static int signRotationForFacing(Direction facing) {
		return switch (facing) {
			case EAST -> 12;
			case NORTH -> 8;
			case WEST -> 4;
			default -> 0;
		};
	}

	private static Optional<BlockPos> standPosFor(Villager miner, MineSite mineSite, int targetUp) {
		for (int up = targetUp; up <= -1; up++) {
			for (ShaftColumn ladderColumn : mineSite.layout().ladderColumns()) {
				BlockPos candidate = worldPos(mineSite.buildSite(), ladderColumn, up);
				if (levelHasLadder(miner, candidate)) {
					return Optional.of(candidate);
				}
			}
		}

		return mineSite.layout().ladderColumns().isEmpty()
			? Optional.empty()
			: Optional.of(worldPos(mineSite.buildSite(), mineSite.layout().ladderColumns().getFirst(), -1));
	}

	private static boolean levelHasLadder(Villager miner, BlockPos pos) {
		return miner.level().getBlockState(pos).is(Blocks.LADDER);
	}

	private static void moveMinerTowardTask(ServerLevel level, Villager miner, MineSite mineSite, MinerTask task) {
		BlockPos shaftTravelPos = level.getBlockState(task.standPos()).is(Blocks.LADDER)
			? task.standPos()
			: shaftTravelStandPos(level, mineSite, task.standPos()).orElse(task.standPos());

		boolean advancedInShaft = tryAdvanceMinerDownShaft(level, miner, mineSite, shaftTravelPos);
		if (advancedInShaft && level.getBlockState(task.standPos()).is(Blocks.LADDER)) {
			return;
		}

		if (advancedInShaft && !miner.blockPosition().equals(shaftTravelPos)) {
			return;
		}

		if (tryStepMinerTowardMineStand(level, miner, mineSite, task.standPos())) {
			return;
		}

		miner.getNavigation().moveTo(
			task.standPos().getX() + 0.5D,
			task.standPos().getY(),
			task.standPos().getZ() + 0.5D,
			MINING_WALK_SPEED
		);
	}

	private static boolean tryStepMinerTowardMineStand(ServerLevel level, Villager miner, MineSite mineSite, BlockPos targetStandPos) {
		BlockPos currentPos = miner.blockPosition();

		if (currentPos.equals(targetStandPos)) {
			return true;
		}

		Optional<BlockPos> pathStep = minePathStepToward(level, mineSite, currentPos, targetStandPos);
		if (pathStep.isEmpty()) {
			return false;
		}

		miner.getNavigation().stop();
		placeVillagerSafely(miner, pathStep.get());
		return true;
	}

	private static Optional<BlockPos> minePathStepToward(ServerLevel level, MineSite mineSite, BlockPos startPos, BlockPos targetPos) {
		ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
		Map<BlockPos, BlockPos> previousByPos = new HashMap<>();
		Set<BlockPos> visited = new HashSet<>();

		BlockPos start = startPos.immutable();
		BlockPos target = targetPos.immutable();
		frontier.add(start);
		visited.add(start);

		while (!frontier.isEmpty() && visited.size() <= SURFACE_RETURN_PATH_SEARCH_NODES) {
			BlockPos current = frontier.removeFirst();

			if (!current.equals(start) && current.equals(target)) {
				BlockPos step = current;
				BlockPos previous = previousByPos.get(step);

				while (previous != null && !previous.equals(start)) {
					step = previous;
					previous = previousByPos.get(step);
				}

				return Optional.of(step.immutable());
			}

			for (BlockPos next : mineTravelNeighbors(level, mineSite, start, current)) {
				if (!visited.add(next)) {
					continue;
				}

				previousByPos.put(next, current);
				frontier.addLast(next);
			}
		}

		return Optional.empty();
	}

	private static List<BlockPos> mineTravelNeighbors(ServerLevel level, MineSite mineSite, BlockPos startPos, BlockPos currentPos) {
		List<BlockPos> neighbors = new ArrayList<>(8);

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			for (int dy = -1; dy <= 1; dy++) {
				BlockPos candidate = currentPos.relative(direction).offset(0, dy, 0);
				if (isWithinSurfaceReturnSearch(startPos, candidate) && isSurfaceReturnCell(level, mineSite, candidate)) {
					neighbors.add(candidate.immutable());
				}
			}
		}

		if (isExistingMineLadder(level, mineSite, currentPos)) {
			for (Direction direction : List.of(Direction.UP, Direction.DOWN)) {
				BlockPos candidate = currentPos.relative(direction);
				if (isWithinSurfaceReturnSearch(startPos, candidate) && isExistingMineLadder(level, mineSite, candidate)) {
					neighbors.add(candidate.immutable());
				}
			}
		}

		return neighbors;
	}

	private static Optional<BlockPos> shaftTravelStandPos(ServerLevel level, MineSite mineSite, BlockPos targetStandPos) {
		BlockPos bestPos = null;
		double bestScore = Double.POSITIVE_INFINITY;

		for (int up = mineSite.layout().starterMinUp() - MAX_SHAFT_DEPTH_BLOCKS; up <= -1; up++) {
			for (ShaftColumn ladderColumn : mineSite.layout().ladderColumns()) {
				BlockPos candidatePos = worldPos(mineSite.buildSite(), ladderColumn, up);
				if (!level.getBlockState(candidatePos).is(Blocks.LADDER)) {
					continue;
				}

				int verticalDistance = Math.abs(candidatePos.getY() - targetStandPos.getY());
				double horizontalDistance = Math.abs(candidatePos.getX() - targetStandPos.getX())
					+ Math.abs(candidatePos.getZ() - targetStandPos.getZ());
				double score = verticalDistance * 100.0D + horizontalDistance;

				if (bestPos == null || score < bestScore) {
					bestPos = candidatePos.immutable();
					bestScore = score;
				}
			}
		}

		return Optional.ofNullable(bestPos);
	}

	private static boolean moveMinerTowardSurface(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Villager miner,
		MineSite mineSite
	) {
		return moveMinerTowardSurface(level, settlement, stock, miner, mineSite, SURFACE_RETURN_STEPS_PER_PASS);
	}

	private static boolean moveMinerTowardSurface(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Villager miner,
		MineSite mineSite,
		int maxSteps
	) {
		BlockPos startPos = miner.blockPosition();
		refreshMineMouthClearance(level, miner, mineSite);

		if (needsMineMouthExit(level, miner, mineSite) && tryExitTopOfMineLadder(level, miner, mineSite)) {
			refreshMineMouthClearance(level, miner, mineSite);
			return true;
		}

		boolean changed = placeSurfaceReturnRescueLadder(level, stock, miner, mineSite);
		boolean moved = false;

		for (int step = 0; step < maxSteps; step++) {
			if (!moveMinerOneStepTowardSurface(level, miner, mineSite)) {
				break;
			}

			if (!miner.blockPosition().equals(startPos)) {
				moved = true;
			}
		}

		if (!moved && startPos.equals(miner.blockPosition())) {
			if (needsMineMouthExit(level, miner, mineSite) && tryExitTopOfMineLadder(level, miner, mineSite)) {
				refreshMineMouthClearance(level, miner, mineSite);
				return true;
			}

			if (startPos.getY() < surfaceLadderY(mineSite) - 2) {
				logSurfaceReturnDiagnostic(level, settlement, miner, mineSite, "surface_return_blocked");
			} else {
				logSurfaceReturnDiagnostic(level, settlement, miner, mineSite, "top_ladder_no_exit");
			}
		}

		return changed || moved;
	}

	private static boolean isNearMineSurface(BlockPos pos, MineSite mineSite) {
		int surfaceY = surfaceLadderY(mineSite);
		if (pos.getY() < surfaceY - 4 || pos.getY() > surfaceY + 4) {
			return false;
		}

		return allShaftColumns(mineSite).stream()
			.anyMatch(column -> {
				BlockPos shaftPos = worldPos(mineSite.buildSite(), column, -1);
				return shaftPos.getX() == pos.getX() && shaftPos.getZ() == pos.getZ();
			});
	}

	private static boolean placeSurfaceReturnRescueLadder(ServerLevel level, Map<String, Integer> stock, Villager miner, MineSite mineSite) {
		BlockPos currentPos = miner.blockPosition();

		if (currentPos.getY() >= surfaceLadderY(mineSite) || !canSupplySingleLadder(stock)) {
			return false;
		}

		for (BlockPos candidatePos : shaftRescueLadderCandidates(mineSite, currentPos)) {
			if (level.getBlockState(candidatePos).is(Blocks.LADDER)) {
				continue;
			}

			if (placeLadder(level, stock, candidatePos, mineSite.ladderStateTemplate(), mineSite.supportDirection())) {
				return true;
			}
		}

		return false;
	}

	private static List<BlockPos> shaftRescueLadderCandidates(MineSite mineSite, BlockPos currentPos) {
		int currentUp = currentPos.getY() - mineSite.buildSite().origin().getY();
		List<BlockPos> candidates = new ArrayList<>();

		for (ShaftColumn column : allShaftColumns(mineSite)) {
			BlockPos candidatePos = worldPos(mineSite.buildSite(), column, currentUp);

			if (candidatePos.getX() == currentPos.getX() && candidatePos.getZ() == currentPos.getZ()) {
				candidates.add(candidatePos);
			}
		}

		for (ShaftColumn column : allShaftColumns(mineSite)) {
			BlockPos candidatePos = worldPos(mineSite.buildSite(), column, currentUp);

			if (!candidates.contains(candidatePos)) {
				candidates.add(candidatePos);
			}
		}

		return candidates;
	}

	private static boolean moveMinerOneStepTowardSurface(ServerLevel level, Villager miner, MineSite mineSite) {
		BlockPos currentPos = miner.blockPosition();
		Optional<BlockPos> currentLadderPos = currentLadderPos(level, mineSite, currentPos);

		if (currentLadderPos.isPresent()) {
			Optional<BlockPos> topLadderPos = topExistingLadderPos(level, mineSite, currentLadderPos.get());

			if (topLadderPos.isPresent() && currentLadderPos.get().getY() >= topLadderPos.get().getY() - 1) {
				return tryExitTopOfMineLadder(level, miner, mineSite);
			}

			if (topLadderPos.isPresent() && currentLadderPos.get().getY() < topLadderPos.get().getY()) {
				BlockPos topLadder = topLadderPos.get();
				Optional<BlockPos> ladderPathExitPos = surfaceExitPos(level, mineSite);
				if (ladderPathExitPos.isPresent() && hasContinuousLadderPath(level, currentLadderPos.get(), topLadder)) {
					miner.getNavigation().stop();
					placeVillagerSafely(miner, ladderPathExitPos.get());
					return true;
				}

				BlockPos nextPos = currentLadderPos.get().above();

				if (level.getBlockState(nextPos).is(Blocks.LADDER)) {
					miner.getNavigation().stop();
					placeVillagerSafely(miner, nextPos);
					return true;
				}

				Optional<BlockPos> higherLadderPos = nearestHigherExistingLadderPos(level, mineSite, currentLadderPos.get());
				if (higherLadderPos.isPresent()
					&& higherLadderPos.get().distSqr(currentLadderPos.get()) <= SURFACE_RETURN_RESCUE_SNAP_DISTANCE_SQUARED) {
					miner.getNavigation().stop();
					placeVillagerSafely(miner, higherLadderPos.get());
					return true;
				}

				if (currentLadderPos.get().distSqr(topLadder) <= SURFACE_RETURN_RESCUE_SNAP_DISTANCE_SQUARED) {
					return tryExitTopOfMineLadder(level, miner, mineSite);
				}
			}

			return false;
		}

		Optional<BlockPos> nearestLadderPos = nearestExistingLadderPos(level, mineSite, currentPos);

		if (nearestLadderPos.isPresent()) {
			Optional<BlockPos> pathStep = returnPathStepTowardLadder(level, mineSite, currentPos);
			if (pathStep.isPresent()) {
				miner.getNavigation().stop();
				placeVillagerSafely(miner, pathStep.get());
				return true;
			}

			if (tryStepMinerTowardLadder(level, miner, currentPos, nearestLadderPos.get())) {
				return true;
			}

			double nearestLadderDistanceSquared = miner.distanceToSqr(
				nearestLadderPos.get().getX() + 0.5D,
				nearestLadderPos.get().getY(),
				nearestLadderPos.get().getZ() + 0.5D
			);
			if (nearestLadderDistanceSquared <= SURFACE_RETURN_RESCUE_SNAP_DISTANCE_SQUARED) {
				miner.getNavigation().stop();
				placeVillagerSafely(miner, nearestLadderPos.get());
				return true;
			}

			miner.getNavigation().moveTo(
				nearestLadderPos.get().getX() + 0.5D,
				nearestLadderPos.get().getY(),
				nearestLadderPos.get().getZ() + 0.5D,
				MINING_WALK_SPEED
			);
		}

		return false;
	}

	private static int surfaceLadderY(MineSite mineSite) {
		return worldPos(mineSite.buildSite(), mineSite.layout().ladderColumns().getFirst(), -1).getY();
	}

	private static List<BlockPos> surfaceLadderPositions(MineSite mineSite) {
		return mineSite.layout().ladderColumns().stream()
			.map(column -> worldPos(mineSite.buildSite(), column, -1))
			.toList();
	}

	private static Optional<BlockPos> surfaceLadderPosForColumn(MineSite mineSite, BlockPos pos) {
		return surfaceLadderPositions(mineSite).stream()
			.filter(ladderPos -> ladderPos.getX() == pos.getX() && ladderPos.getZ() == pos.getZ())
			.findFirst();
	}

	private static boolean isSurfaceLadderColumn(MineSite mineSite, BlockPos pos) {
		return surfaceLadderPosForColumn(mineSite, pos)
			.map(ladderPos -> pos.getY() >= ladderPos.getY() - 4 && pos.getY() <= ladderPos.getY() + 4)
			.orElse(false);
	}

	private static boolean isMineMouthShaftColumn(MineSite mineSite, BlockPos pos) {
		int surfaceY = surfaceLadderY(mineSite);
		if (pos.getY() < surfaceY - 4 || pos.getY() > surfaceY + 4) {
			return false;
		}

		return allShaftColumns(mineSite).stream()
			.anyMatch(column -> {
				BlockPos shaftPos = worldPos(mineSite.buildSite(), column, -1);
				return shaftPos.getX() == pos.getX() && shaftPos.getZ() == pos.getZ();
			});
	}

	private static Optional<BlockPos> returnPathStepTowardLadder(ServerLevel level, MineSite mineSite, BlockPos startPos) {
		ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
		Map<BlockPos, BlockPos> previousByPos = new HashMap<>();
		Set<BlockPos> visited = new HashSet<>();

		BlockPos start = startPos.immutable();
		frontier.add(start);
		visited.add(start);

		while (!frontier.isEmpty() && visited.size() <= SURFACE_RETURN_PATH_SEARCH_NODES) {
			BlockPos current = frontier.removeFirst();

			if (!current.equals(start) && isExistingMineLadder(level, mineSite, current)) {
				BlockPos step = current;
				BlockPos previous = previousByPos.get(step);

				while (previous != null && !previous.equals(start)) {
					step = previous;
					previous = previousByPos.get(step);
				}

				return Optional.of(step.immutable());
			}

			for (BlockPos next : surfaceReturnNeighbors(level, mineSite, start, current)) {
				if (!visited.add(next)) {
					continue;
				}

				previousByPos.put(next, current);
				frontier.addLast(next);
			}
		}

		return Optional.empty();
	}

	private static List<BlockPos> surfaceReturnNeighbors(ServerLevel level, MineSite mineSite, BlockPos startPos, BlockPos currentPos) {
		List<BlockPos> neighbors = new ArrayList<>(6);

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos candidate = currentPos.relative(direction);
			if (isWithinSurfaceReturnSearch(startPos, candidate) && isSurfaceReturnCell(level, mineSite, candidate)) {
				neighbors.add(candidate.immutable());
			}
		}

		if (isExistingMineLadder(level, mineSite, currentPos)) {
			for (Direction direction : List.of(Direction.UP, Direction.DOWN)) {
				BlockPos candidate = currentPos.relative(direction);
				if (isWithinSurfaceReturnSearch(startPos, candidate) && isExistingMineLadder(level, mineSite, candidate)) {
					neighbors.add(candidate.immutable());
				}
			}
		}

		return neighbors;
	}

	private static boolean isWithinSurfaceReturnSearch(BlockPos startPos, BlockPos candidate) {
		return Math.abs(candidate.getX() - startPos.getX()) <= SURFACE_RETURN_PATH_SEARCH_BLOCKS
			&& Math.abs(candidate.getY() - startPos.getY()) <= 16
			&& Math.abs(candidate.getZ() - startPos.getZ()) <= SURFACE_RETURN_PATH_SEARCH_BLOCKS;
	}

	private static boolean isSurfaceReturnCell(ServerLevel level, MineSite mineSite, BlockPos pos) {
		return isExistingMineLadder(level, mineSite, pos) || isStandableTunnelCell(level, pos);
	}

	private static boolean isExistingMineLadder(ServerLevel level, MineSite mineSite, BlockPos pos) {
		for (ShaftColumn ladderColumn : mineSite.layout().ladderColumns()) {
			if (pos.getX() == worldPos(mineSite.buildSite(), ladderColumn, -1).getX()
				&& pos.getZ() == worldPos(mineSite.buildSite(), ladderColumn, -1).getZ()
				&& level.getBlockState(pos).is(Blocks.LADDER)) {
				return true;
			}
		}

		return false;
	}

	private static boolean tryStepMinerTowardLadder(ServerLevel level, Villager miner, BlockPos currentPos, BlockPos ladderPos) {
		if (Math.abs(currentPos.getY() - ladderPos.getY()) > 1) {
			return false;
		}

		int dx = Integer.compare(ladderPos.getX() - currentPos.getX(), 0);
		int dz = Integer.compare(ladderPos.getZ() - currentPos.getZ(), 0);
		List<BlockPos> candidates = new ArrayList<>(2);

		if (Math.abs(ladderPos.getX() - currentPos.getX()) >= Math.abs(ladderPos.getZ() - currentPos.getZ())) {
			if (dx != 0) {
				candidates.add(currentPos.offset(dx, 0, 0));
			}
			if (dz != 0) {
				candidates.add(currentPos.offset(0, 0, dz));
			}
		} else {
			if (dz != 0) {
				candidates.add(currentPos.offset(0, 0, dz));
			}
			if (dx != 0) {
				candidates.add(currentPos.offset(dx, 0, 0));
			}
		}

		for (BlockPos candidate : candidates) {
			if (!candidate.equals(ladderPos) && !isStandableTunnelCell(level, candidate)) {
				continue;
			}

			if (candidate.equals(ladderPos) && !level.getBlockState(candidate).is(Blocks.LADDER)) {
				continue;
			}

			miner.getNavigation().stop();
			placeVillagerSafely(miner, candidate);
			return true;
		}

		return false;
	}

	private static boolean tryAdvanceMinerDownShaft(ServerLevel level, Villager miner, MineSite mineSite, BlockPos targetStandPos) {
		if (!level.getBlockState(targetStandPos).is(Blocks.LADDER)) {
			return false;
		}

		BlockPos currentPos = miner.blockPosition();
		BlockPos currentLadderPos = currentLadderPos(level, mineSite, currentPos).orElse(null);
		BlockPos topLadderPos = topLadderPosForTarget(mineSite, targetStandPos);

		if (currentLadderPos == null) {
			if (topLadderPos == null || miner.distanceToSqr(topLadderPos.getX() + 0.5D, topLadderPos.getY(), topLadderPos.getZ() + 0.5D) > SHAFT_ENTRY_SNAP_DISTANCE_SQUARED) {
				return false;
			}

			miner.getNavigation().stop();
			if (hasContinuousLadderPath(level, topLadderPos, targetStandPos)) {
				placeVillagerSafely(miner, targetStandPos);
			} else {
				placeVillagerSafely(miner, topLadderPos);
			}
			return true;
		}

		if (currentLadderPos.getX() != targetStandPos.getX() || currentLadderPos.getZ() != targetStandPos.getZ()) {
			return false;
		}

		if (currentLadderPos.getY() == targetStandPos.getY()) {
			return true;
		}

		if (currentLadderPos.getY() < targetStandPos.getY()) {
			BlockPos nextPos = currentLadderPos.above();

			if (!level.getBlockState(nextPos).is(Blocks.LADDER)) {
				return false;
			}

			miner.getNavigation().stop();
			placeVillagerSafely(miner, nextPos);
			return true;
		}

		if (hasContinuousLadderPath(level, currentLadderPos, targetStandPos)) {
			miner.getNavigation().stop();
			placeVillagerSafely(miner, targetStandPos);
			return true;
		}

		BlockPos nextPos = currentLadderPos.below();

		if (!level.getBlockState(nextPos).is(Blocks.LADDER)) {
			return false;
		}

		miner.getNavigation().stop();
		placeVillagerSafely(miner, nextPos);
		return true;
	}

	private static boolean hasContinuousLadderPath(ServerLevel level, BlockPos fromPos, BlockPos toPos) {
		if (fromPos.getX() != toPos.getX() || fromPos.getZ() != toPos.getZ()) {
			return false;
		}

		int minY = Math.min(fromPos.getY(), toPos.getY());
		int maxY = Math.max(fromPos.getY(), toPos.getY());
		for (int y = minY; y <= maxY; y++) {
			if (!level.getBlockState(new BlockPos(fromPos.getX(), y, fromPos.getZ())).is(Blocks.LADDER)) {
				return false;
			}
		}

		return true;
	}

	private static Optional<BlockPos> currentLadderPos(ServerLevel level, MineSite mineSite, BlockPos currentPos) {
		BlockPos bestPos = null;
		int bestVerticalDistance = Integer.MAX_VALUE;

		for (int up = mineSite.layout().starterMinUp() - MAX_SHAFT_DEPTH_BLOCKS; up <= -1; up++) {
			for (ShaftColumn ladderColumn : mineSite.layout().ladderColumns()) {
				BlockPos ladderPos = worldPos(mineSite.buildSite(), ladderColumn, up);

				if (ladderPos.getX() == currentPos.getX()
					&& ladderPos.getZ() == currentPos.getZ()
					&& Math.abs(ladderPos.getY() - currentPos.getY()) <= 1
					&& level.getBlockState(ladderPos).is(Blocks.LADDER)) {
					int verticalDistance = Math.abs(ladderPos.getY() - currentPos.getY());

					if (bestPos == null
						|| verticalDistance < bestVerticalDistance
						|| (verticalDistance == bestVerticalDistance && ladderPos.getY() > bestPos.getY())) {
						bestPos = ladderPos.immutable();
						bestVerticalDistance = verticalDistance;
					}
				}
			}
		}

		return Optional.ofNullable(bestPos);
	}

	private static BlockPos topLadderPosForTarget(MineSite mineSite, BlockPos targetStandPos) {
		for (ShaftColumn ladderColumn : mineSite.layout().ladderColumns()) {
			BlockPos candidate = worldPos(mineSite.buildSite(), ladderColumn, -1);

			if (candidate.getX() == targetStandPos.getX() && candidate.getZ() == targetStandPos.getZ()) {
				return candidate;
			}
		}

		return null;
	}

	private static Optional<BlockPos> topExistingLadderPos(ServerLevel level, MineSite mineSite, BlockPos columnReference) {
		BlockPos bestPos = null;

		for (BlockPos ladderPos : existingMineLadderPositions(level, mineSite)) {
			if (ladderPos.getX() != columnReference.getX() || ladderPos.getZ() != columnReference.getZ()) {
				continue;
			}

			if (bestPos == null || ladderPos.getY() > bestPos.getY()) {
				bestPos = ladderPos.immutable();
			}
		}

		return Optional.ofNullable(bestPos);
	}

	private static boolean tryExitTopOfMineLadder(ServerLevel level, Villager miner, MineSite mineSite) {
		if (!needsMineMouthExit(level, miner, mineSite)) {
			return false;
		}

		BlockPos currentPos = miner.blockPosition();
		Optional<BlockPos> currentLadderPos = currentLadderPos(level, mineSite, currentPos);
		BlockPos ladderPos = currentLadderPos.orElse(currentPos);
		Optional<BlockPos> topLadderPos = topExistingLadderPos(level, mineSite, ladderPos);

		for (BlockPos stepPos : topLadderExitStepCandidates(level, mineSite, topLadderPos.orElse(ladderPos), null)) {
			if (stepPos.equals(currentPos)) {
				continue;
			}

			miner.getNavigation().stop();
			placeVillagerSafely(miner, stepPos);
			refreshMineMouthClearance(level, miner, mineSite);
			return true;
		}

		Optional<BlockPos> exitPos = surfaceExitPos(level, mineSite);
		if (exitPos.isPresent() && !exitPos.get().equals(currentPos)) {
			miner.getNavigation().stop();
			placeVillagerSafely(miner, exitPos.get());
			refreshMineMouthClearance(level, miner, mineSite);
			return true;
		}

		return false;
	}

	private static boolean needsMineMouthExit(ServerLevel level, Villager miner, MineSite mineSite) {
		if (hasClearedMineMouth(miner)) {
			return false;
		}

		BlockPos pos = miner.blockPosition();
		if (pos.getY() < surfaceLadderY(mineSite) - 3) {
			return true;
		}

		return isInsideMineShaftColumn(level, mineSite, pos) || currentLadderPos(level, mineSite, pos).isPresent();
	}

	private static boolean hasClearedMineMouth(Villager miner) {
		return CLEARED_MINE_MOUTH_VILLAGERS.contains(miner.getUUID().toString());
	}

	private static void refreshMineMouthClearance(ServerLevel level, Villager miner, MineSite mineSite) {
		BlockPos pos = miner.blockPosition();
		String minerId = miner.getUUID().toString();

		if (isInsideMineShaftColumn(level, mineSite, pos) || currentLadderPos(level, mineSite, pos).isPresent()) {
			CLEARED_MINE_MOUTH_VILLAGERS.remove(minerId);
			return;
		}

		if (!isNearMineSurface(pos, mineSite) || horizontalDistanceToNearestShaftColumn(pos, mineSite) >= MINE_MOUTH_CLEARANCE_HORIZONTAL_BLOCKS) {
			CLEARED_MINE_MOUTH_VILLAGERS.add(minerId);
		}
	}

	private static int horizontalDistanceToNearestShaftColumn(BlockPos pos, MineSite mineSite) {
		int bestDistance = Integer.MAX_VALUE;

		for (ShaftColumn column : allShaftColumns(mineSite)) {
			BlockPos shaftPos = worldPos(mineSite.buildSite(), column, -1);
			int distance = Math.max(Math.abs(pos.getX() - shaftPos.getX()), Math.abs(pos.getZ() - shaftPos.getZ()));
			bestDistance = Math.min(bestDistance, distance);
		}

		return bestDistance;
	}

	private static List<BlockPos> topLadderExitStepCandidates(
		ServerLevel level,
		MineSite mineSite,
		BlockPos ladderPos,
		BlockPos sortTarget
	) {
		Set<BlockPos> candidates = new LinkedHashSet<>();

		for (int dy = -1; dy <= 2; dy++) {
			BlockPos origin = ladderPos.offset(0, dy, 0);
			candidates.add(origin.above());

			for (Direction direction : Direction.Plane.HORIZONTAL) {
				candidates.add(origin.relative(direction));
				candidates.add(origin.relative(direction).above());
			}
		}

		Comparator<BlockPos> ordering = sortTarget == null
			? Comparator.<BlockPos>comparingDouble(candidate -> candidate.distSqr(mineSite.buildSite().workstationPos())).reversed()
			: Comparator.comparingDouble(candidate -> candidate.distSqr(sortTarget));

		return candidates.stream()
			.filter(candidate -> !isNearMineSurface(candidate, mineSite))
			.filter(candidate -> isWalkableSurfaceCell(level, candidate) || isStandableSurfaceCell(level, candidate))
			.sorted(ordering)
			.toList();
	}

	private static Optional<BlockPos> nearestExistingLadderPos(ServerLevel level, MineSite mineSite, BlockPos currentPos) {
		BlockPos bestPos = null;
		double bestDistanceSquared = Double.POSITIVE_INFINITY;

		for (BlockPos ladderPos : existingMineLadderPositions(level, mineSite)) {
			double distanceSquared = ladderPos.distSqr(currentPos);

			if (bestPos == null || distanceSquared < bestDistanceSquared) {
				bestPos = ladderPos.immutable();
				bestDistanceSquared = distanceSquared;
			}
		}

		return Optional.ofNullable(bestPos);
	}

	private static List<BlockPos> existingMineLadderPositions(ServerLevel level, MineSite mineSite) {
		List<BlockPos> ladderPositions = new ArrayList<>();

		for (int up = mineSite.layout().starterMinUp() - MAX_SHAFT_DEPTH_BLOCKS; up <= -1; up++) {
			for (ShaftColumn ladderColumn : mineSite.layout().ladderColumns()) {
				BlockPos ladderPos = worldPos(mineSite.buildSite(), ladderColumn, up);

				if (level.getBlockState(ladderPos).is(Blocks.LADDER)) {
					ladderPositions.add(ladderPos.immutable());
				}
			}
		}

		return ladderPositions;
	}

	private static Optional<BlockPos> nearestHigherExistingLadderPos(ServerLevel level, MineSite mineSite, BlockPos currentLadderPos) {
		BlockPos bestPos = null;
		int bestVerticalDistance = Integer.MAX_VALUE;

		for (int up = mineSite.layout().starterMinUp() - MAX_SHAFT_DEPTH_BLOCKS; up <= -1; up++) {
			for (ShaftColumn ladderColumn : mineSite.layout().ladderColumns()) {
				BlockPos ladderPos = worldPos(mineSite.buildSite(), ladderColumn, up);

				if (ladderPos.getX() != currentLadderPos.getX()
					|| ladderPos.getZ() != currentLadderPos.getZ()
					|| ladderPos.getY() <= currentLadderPos.getY()
					|| !level.getBlockState(ladderPos).is(Blocks.LADDER)) {
					continue;
				}

				int verticalDistance = ladderPos.getY() - currentLadderPos.getY();
				if (bestPos == null || verticalDistance < bestVerticalDistance) {
					bestPos = ladderPos.immutable();
					bestVerticalDistance = verticalDistance;
				}
			}
		}

		return Optional.ofNullable(bestPos);
	}

	private static void logSurfaceReturnDiagnostic(
		ServerLevel level,
		SettlementState settlement,
		Villager miner,
		MineSite mineSite,
		String reason
	) {
		long tick = level.getServer().getTickCount();
		String key = settlement.id() + "|" + miner.getUUID() + "|" + reason;
		long previousTick = SURFACE_RETURN_DIAGNOSTICS.getOrDefault(key, Long.MIN_VALUE);
		if (previousTick != Long.MIN_VALUE && tick - previousTick < SURFACE_RETURN_DIAGNOSTIC_INTERVAL_TICKS) {
			return;
		}

		SURFACE_RETURN_DIAGNOSTICS.put(key, tick);
		Optional<BlockPos> nearestLadderPos = mineSite == null
			? Optional.empty()
			: nearestExistingLadderPos(level, mineSite, miner.blockPosition());
		Optional<BlockPos> currentLadderPos = mineSite == null
			? Optional.empty()
			: currentLadderPos(level, mineSite, miner.blockPosition());
		Optional<BlockPos> returnPathStep = mineSite == null
			? Optional.empty()
			: returnPathStepTowardLadder(level, mineSite, miner.blockPosition());

		LiveVillages.LOGGER.warn(
			"Miner surface return diagnostic: settlement={} villager={} pos={} reason={} mineSite={} currentLadder={} nearestLadder={} nearestLadderDistance={} returnPathStep={}",
			settlement.id(),
			miner.getUUID(),
			miner.blockPosition(),
			reason,
			mineSite == null ? "none" : mineSite.buildSite().workstationPos(),
			currentLadderPos.map(BlockPos::toShortString).orElse("none"),
			nearestLadderPos.map(BlockPos::toShortString).orElse("none"),
			nearestLadderPos
				.map(pos -> Math.round(Math.sqrt(pos.distSqr(miner.blockPosition())) * 10.0D) / 10.0D)
				.map(Object::toString)
				.orElse("n/a"),
			returnPathStep.map(BlockPos::toShortString).orElse("none")
		);
	}

	private static String minerWorkSummary(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, Villager miner, MineSite mineSite) {
		int bottomUp = deepestCompleteShaftUp(level, mineSite);
		List<BlockPos> frontierCells = shaftFrontierCells(mineSite, bottomUp);
		Optional<BlockPos> standPos = standPosFor(miner, mineSite, bottomUp);
		return "villager=" + miner.getUUID()
			+ " pos=" + miner.blockPosition().toShortString()
			+ " mine=" + mineSite.buildSite().workstationPos().toShortString()
			+ " bottomY=" + worldPos(mineSite.buildSite(), mineSite.layout().ladderColumns().getFirst(), bottomUp).getY()
			+ " stand=" + standPos.map(BlockPos::toShortString).orElse("none")
			+ " currentLadder=" + currentLadderPos(level, mineSite, miner.blockPosition()).map(BlockPos::toShortString).orElse("none")
			+ " nearestLadder=" + nearestExistingLadderPos(level, mineSite, miner.blockPosition()).map(BlockPos::toShortString).orElse("none")
			+ " frontier=" + frontierCells.size()
			+ " primary=" + primaryTunnelProgressSummary(level, settlement, mineSite, bottomUp)
			+ " ladders=" + stock.getOrDefault("ladder", 0)
			+ " torches=" + stock.getOrDefault("torch", 0)
			+ " copperTorches=" + stock.getOrDefault("copper_torch", 0)
			+ " dirt=" + stock.getOrDefault("dirt", 0)
			+ " cobble=" + stock.getOrDefault("cobblestone", 0);
	}

	private static String primaryTunnelProgressSummary(ServerLevel level, SettlementState settlement, MineSite mineSite, int bottomUp) {
		List<PrimaryTunnelBranch> branches = plannedPrimaryTunnelBranches(mineSite, bottomUp);
		int openRows = 0;
		int plannedRows = 0;
		int completeBranches = 0;

		for (PrimaryTunnelBranch branch : branches) {
			int length = primaryTunnelLengthBlocks(settlement, mineSite, branch);
			plannedRows += length;
			int branchOpenRows = 0;

			for (int step = 0; step < length; step++) {
				if (!isPrimaryTunnelRowOpen(level, mineSite, branch, step)) {
					break;
				}

				branchOpenRows++;
			}

			openRows += branchOpenRows;
			if (length > 0 && branchOpenRows >= length) {
				completeBranches++;
			}
		}

		return branches.size() + "branches/" + completeBranches + "complete/" + openRows + "of" + plannedRows + "rows";
	}

	private static String minerTaskSummary(ServerLevel level, Villager miner, MineSite mineSite, MinerTask task) {
		return "villager=" + miner.getUUID()
			+ " pos=" + miner.blockPosition().toShortString()
			+ " action=" + task.action()
			+ " task=" + task.taskKey()
			+ " target=" + task.targetPos().toShortString()
			+ " stand=" + task.standPos().toShortString()
			+ " targetDistance=" + Math.round(Math.sqrt(miner.blockPosition().distSqr(task.targetPos())) * 10.0D) / 10.0D
			+ " canReach=" + canMinerReachTask(miner, task)
			+ " currentLadder=" + currentLadderPos(level, mineSite, miner.blockPosition()).map(BlockPos::toShortString).orElse("none")
			+ " standReachable=" + isMineReachableStandPos(level, mineSite, task.standPos());
	}

	private static boolean isMineReachableStandPos(ServerLevel level, MineSite mineSite, BlockPos standPos) {
		return isExistingMineLadder(level, mineSite, standPos)
			|| returnPathStepTowardLadder(level, mineSite, standPos).isPresent();
	}

	private static Optional<BlockPos> adjacentCaveStandPos(BlockPos targetPos, List<BlockPos> standPositions) {
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos candidatePos = targetPos.relative(direction);

			for (BlockPos standPos : standPositions) {
				if (standPos.equals(candidatePos)) {
					return Optional.of(standPos);
				}
			}
		}

		return Optional.empty();
	}

	private static Optional<BlockPos> exposedOreFromStandPos(ServerLevel level, BlockPos standPos, Set<BlockPos> caveAirCells) {
		for (BlockPos inspectOrigin : List.of(standPos, standPos.above())) {
			for (Direction direction : Direction.values()) {
				BlockPos candidatePos = inspectOrigin.relative(direction);

				if (caveAirCells.contains(candidatePos)) {
					continue;
				}

				if (isUsefulOre(level.getBlockState(candidatePos))) {
					return Optional.of(candidatePos.immutable());
				}
			}
		}

		return Optional.empty();
	}

	private static Optional<OreWorkTarget> exposedOreFromAirCells(ServerLevel level, Set<BlockPos> airCells, List<BlockPos> standPositions) {
		for (BlockPos airCell : airCells.stream().sorted(Comparator.comparingInt(BlockPos::getY)).toList()) {
			for (Direction direction : Direction.values()) {
				BlockPos candidatePos = airCell.relative(direction);

				if (airCells.contains(candidatePos) || !isUsefulOre(level.getBlockState(candidatePos))) {
					continue;
				}

				return nearestStandPosForTarget(candidatePos, standPositions)
					.map(standPos -> new OreWorkTarget(candidatePos.immutable(), standPos));
			}
		}

		return Optional.empty();
	}

	private static Optional<BlockPos> nearestStandPosForTarget(BlockPos targetPos, List<BlockPos> standPositions) {
		return standPositions.stream()
			.filter(standPos -> standPos.distSqr(targetPos) <= MINING_WORK_REACH_DISTANCE_SQUARED)
			.min(Comparator.comparingDouble(standPos -> standPos.distSqr(targetPos)))
			.map(BlockPos::immutable);
	}

	private static boolean isVillageRestTime(ServerLevel level) {
		long dayTime = Math.floorMod(level.getOverworldClockTime(), DAY_TICKS);
		return dayTime >= VILLAGE_REST_START_TICK;
	}

	private static boolean isVillageGatheringTime(ServerLevel level) {
		long dayTime = Math.floorMod(level.getOverworldClockTime(), DAY_TICKS);
		return dayTime >= VILLAGE_GATHERING_START_TICK && dayTime < VILLAGE_REST_START_TICK;
	}

	private static Optional<BlockPos> surfaceExitPos(ServerLevel level, MineSite mineSite) {
		List<BlockPos> surfaceLadderPositions = surfaceLadderPositions(mineSite);
		BlockPos firstSurfaceLadderPos = surfaceLadderPositions.getFirst();
		List<BlockPos> candidates = new ArrayList<>();
		BlockPos workstationPos = mineSite.buildSite().workstationPos();

		for (BlockPos surfaceLadderPos : surfaceLadderPositions) {
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				candidates.add(surfaceLadderPos.relative(direction));
			}
		}

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			candidates.add(workstationPos.relative(direction));
		}

		candidates.add(workstationPos.above());

		for (int radius = 1; radius <= 8; radius++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
						continue;
					}

					for (int dy = -1; dy <= 5; dy++) {
						for (BlockPos surfaceLadderPos : surfaceLadderPositions) {
							candidates.add(surfaceLadderPos.offset(dx, dy, dz));
						}
						candidates.add(workstationPos.offset(dx, dy, dz));
					}
				}
			}
		}

		return candidates.stream()
			.filter(candidate -> candidate.getY() >= firstSurfaceLadderPos.getY() - 1)
			.filter(candidate -> !isNearMineSurface(candidate, mineSite))
			.filter(candidate -> isWalkableSurfaceCell(level, candidate) || isStandableSurfaceCell(level, candidate))
			.sorted(Comparator
				.comparingDouble((BlockPos candidate) -> Math.abs(candidate.getY() - firstSurfaceLadderPos.getY()))
				.thenComparingDouble(candidate -> candidate.distSqr(workstationPos)))
			.findFirst()
			.map(BlockPos::immutable);
	}

	private static boolean isStandableSurfaceCell(ServerLevel level, BlockPos pos) {
		return level.hasChunkAt(pos)
			&& level.getBlockState(pos).isAir()
			&& level.getBlockState(pos.above()).isAir()
			&& level.getBlockState(pos.below()).isSolid();
	}

	private static void placeVillagerSafely(Villager villager, BlockPos pos) {
		villager.setDeltaMovement(0.0D, 0.0D, 0.0D);
		villager.fallDistance = 0.0F;
		villager.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
	}

	private static BlockState findTemplateLadderState(ServerLevel level, SettlementBuildSite buildSite, ShaftLayout layout) {
		for (SettlementBuildBlockState block : buildSite.blocks()) {
			if (!"R".equals(block.blueprintSymbol())) {
				continue;
			}

			BuildRelativePos relativePos = parseRelativePos(block.position());

			if (relativePos == null) {
				continue;
			}

			BlockPos ladderPos = worldPos(buildSite, new ShaftColumn(relativePos.right(), relativePos.forward()), relativePos.up());
			BlockState state = level.getBlockState(ladderPos);

			if (state.is(Blocks.LADDER)) {
				return state;
			}
		}

		return Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, buildSite.facing());
	}

	private static Direction supportDirectionFor(ServerLevel level, BlockPos ladderPos, BlockState ladderState) {
		Direction facing = ladderState.hasProperty(LadderBlock.FACING) ? ladderState.getValue(LadderBlock.FACING) : Direction.NORTH;

		for (Direction candidate : List.of(facing, facing.getOpposite())) {
			if (isStableLadderSupport(level.getBlockState(ladderPos.relative(candidate)))) {
				return candidate;
			}
		}

		for (Direction candidate : Direction.Plane.HORIZONTAL) {
			if (isStableLadderSupport(level.getBlockState(ladderPos.relative(candidate)))) {
				return candidate;
			}
		}

		return null;
	}

	private static Optional<Direction> intendedShaftSupportDirection(SettlementBuildSite buildSite, ShaftLayout layout) {
		Set<ShaftColumn> openColumns = new HashSet<>(layout.openColumns());

		for (RelativeDirection direction : RelativeDirection.values()) {
			boolean openSide = layout.ladderColumns().stream()
				.allMatch(ladderColumn -> openColumns.contains(new ShaftColumn(
					ladderColumn.right() + direction.rightStep(),
					ladderColumn.forward() + direction.forwardStep()
				)));

			if (openSide) {
				return Optional.of(direction.worldDirection(buildSite.facing()).getOpposite());
			}
		}

		return Optional.empty();
	}

	private static ShaftLayout shaftLayout(SettlementBuildSite buildSite) {
		List<SettlementBuildBlockState> shaftBlocks = buildSite.blocks().stream()
			.filter(block -> !block.blueprintSymbol().isBlank())
			.filter(block -> {
				char symbol = block.blueprintSymbol().charAt(0);
				return symbol == 'R' || symbol == 'E';
			})
			.toList();
		Set<ShaftColumn> ladderColumns = new LinkedHashSet<>();
		Set<ShaftColumn> openColumns = new LinkedHashSet<>();
		int starterMinUp = shaftBlocks.stream()
			.map(SettlementBuildBlockState::position)
			.map(SettlementMinerWork::parseRelativePos)
			.filter(pos -> pos != null && pos.up() < 0)
			.mapToInt(BuildRelativePos::up)
			.min()
			.orElse(-1);

		for (SettlementBuildBlockState block : shaftBlocks) {
			BuildRelativePos relativePos = parseRelativePos(block.position());

			if (relativePos == null || relativePos.up() != starterMinUp) {
				continue;
			}

			char symbol = block.blueprintSymbol().charAt(0);

			if (symbol == 'R') {
				ladderColumns.add(new ShaftColumn(relativePos.right(), relativePos.forward()));
			} else if (symbol == 'E') {
				openColumns.add(new ShaftColumn(relativePos.right(), relativePos.forward()));
			}
		}

		List<ShaftColumn> sortedLadders = ladderColumns.stream()
			.sorted(Comparator.comparingInt(ShaftColumn::forward).thenComparingInt(ShaftColumn::right))
			.toList();
		List<ShaftColumn> sortedOpens = openColumns.stream()
			.sorted(Comparator.comparingInt(ShaftColumn::forward).thenComparingInt(ShaftColumn::right))
			.toList();
		return new ShaftLayout(sortedLadders, sortedOpens, starterMinUp);
	}

	private static BlockPos worldPos(SettlementBuildSite buildSite, ShaftColumn column, int up) {
		return offset(buildSite.origin(), buildSite.facing(), column.right(), column.forward(), up);
	}

	private static BlockPos shaftLevelStandPos(MineSite mineSite, int up, BlockPos targetPos) {
		return mineSite.layout().ladderColumns().stream()
			.map(column -> worldPos(mineSite.buildSite(), column, up))
			.min(Comparator.comparingDouble(candidate -> candidate.distSqr(targetPos)))
			.orElseGet(() -> worldPos(mineSite.buildSite(), mineSite.layout().ladderColumns().getFirst(), up));
	}

	private static boolean isProtectedStarterShaftWall(BlockPos targetPos, MineSite mineSite) {
		for (int up = -1; up >= mineSite.layout().starterMinUp(); up--) {
			if (starterShaftWallPositions(mineSite, up).contains(targetPos)) {
				return true;
			}
		}

		return false;
	}

	private static Set<BlockPos> starterShaftWallPositions(MineSite mineSite, int up) {
		Set<BlockPos> wallPositions = new LinkedHashSet<>();
		List<ShaftColumn> shaftColumns = allShaftColumns(mineSite);

		for (ShaftColumn column : shaftColumns) {
			BlockPos shaftPos = worldPos(mineSite.buildSite(), column, up);

			for (Direction direction : Direction.Plane.HORIZONTAL) {
				BlockPos wallPos = shaftPos.relative(direction);
				if (!isShaftColumnAt(mineSite, wallPos, shaftPos.getY())) {
					wallPositions.add(wallPos.immutable());
				}
			}
		}

		return wallPositions;
	}

	private static boolean isShaftColumnAt(MineSite mineSite, BlockPos pos, int y) {
		return allShaftColumns(mineSite).stream().anyMatch(column -> {
			BlockPos shaftPos = worldPos(mineSite.buildSite(), column, -1);
			return shaftPos.getX() == pos.getX() && shaftPos.getZ() == pos.getZ() && pos.getY() == y;
		});
	}

	private static BlockPos offset(BlockPos origin, Direction facing, int right, int forward, int up) {
		return origin.relative(facing.getClockWise(), right).relative(facing, forward).above(up);
	}

	private static void showMinerTool(Villager miner, MinerAction action) {
		ItemStack held = miner.getMainHandItem();

		if (!held.isEmpty()) {
			return;
		}

		miner.setItemSlot(
			EquipmentSlot.MAINHAND,
			new ItemStack(action == MinerAction.PLACE_LADDER ? Items.LADDER : Items.IRON_PICKAXE)
		);
	}

	private static ActiveMinerWork activeMinerWork(String minerId, long tick) {
		ActiveMinerWork activeWork = ACTIVE_ASSIGNMENTS.get(minerId);

		if (activeWork == null || tick - activeWork.assignedTick() > TASK_MEMORY_TICKS) {
			if (activeWork != null) {
				rememberBlockedMinerTask(minerId, activeWork.task(), tick);
			}
			clearMinerAssignment(minerId);
			return null;
		}

		return activeWork;
	}

	private static Optional<MinerTask> availableMinerTask(ServerLevel level, Villager miner, Optional<MinerTask> task) {
		if (task.isEmpty() || isMinerTaskBlocked(level, miner, task.get())) {
			return Optional.empty();
		}

		return task;
	}

	private static Optional<MinerTask> availableMinerTask(ServerLevel level, Villager miner, MinerTask task) {
		return availableMinerTask(level, miner, Optional.of(task));
	}

	private static boolean isMinerTaskBlocked(ServerLevel level, Villager miner, MinerTask task) {
		String key = minerTaskBlockKey(miner.getUUID().toString(), task);
		long blockedUntilTick = BLOCKED_MINER_TASKS.getOrDefault(key, Long.MIN_VALUE);

		if (blockedUntilTick == Long.MIN_VALUE) {
			return false;
		}

		long tick = level.getServer().getTickCount();
		if (tick >= blockedUntilTick) {
			BLOCKED_MINER_TASKS.remove(key);
			return false;
		}

		return true;
	}

	private static void rememberBlockedMinerTask(String minerId, MinerTask task, long tick) {
		BLOCKED_MINER_TASKS.put(minerTaskBlockKey(minerId, task), tick + FAILED_MINER_TASK_COOLDOWN_TICKS);
	}

	private static void pruneExpiredMinerTaskBlocks(long tick) {
		BLOCKED_MINER_TASKS.entrySet().removeIf(entry -> entry.getValue() <= tick);
	}

	private static String minerTaskBlockKey(String minerId, MinerTask task) {
		return minerId
			+ "|" + task.action()
			+ "|" + task.taskKey()
			+ "|" + task.targetPos().toShortString()
			+ "|" + task.standPos().toShortString();
	}

	private static ActiveMinerWork rememberMinerWork(String minerId, MinerTask task, long tick) {
		ActiveMinerWork activeWork = new ActiveMinerWork(task, tick);
		ACTIVE_ASSIGNMENTS.put(minerId, activeWork);
		return activeWork;
	}

	private static void clearMinerAssignment(String minerId) {
		ACTIVE_ASSIGNMENTS.remove(minerId);
		ACTIVE_TASKS.remove(minerId);
	}

	private static boolean canMinerReachTask(Villager miner, MinerTask task) {
		if (isWithinWorkReach(miner, task.targetPos())) {
			return true;
		}

		BlockPos minerPos = miner.blockPosition();
		BlockPos standPos = task.standPos();
		return minerPos.distSqr(standPos) <= 2.25D && standPos.distSqr(task.targetPos()) <= MINING_WORK_REACH_DISTANCE_SQUARED;
	}

	private static boolean isWithinWorkReach(Villager miner, BlockPos workPos) {
		return miner.blockPosition().distSqr(workPos) <= MINING_WORK_REACH_DISTANCE_SQUARED;
	}

	private static boolean isMinerEligibleForEveningWalk(ServerLevel level, Villager miner, MineSite mineSite) {
		BlockPos minerPos = miner.blockPosition();
		int surfaceY = surfaceLadderY(mineSite);

		if (minerPos.getY() < surfaceY - 8) {
			return false;
		}

		return hasClearedMineMouth(miner)
			|| (!isInsideMineShaftColumn(level, mineSite, minerPos) && horizontalDistanceToNearestShaftColumn(minerPos, mineSite) >= 2);
	}

	private static boolean isInsideMineShaftColumn(ServerLevel level, MineSite mineSite, BlockPos pos) {
		int surfaceY = surfaceLadderY(mineSite);

		if (pos.getY() > surfaceY + 2 || pos.getY() < surfaceY - 8) {
			return false;
		}

		boolean inShaftColumn = allShaftColumns(mineSite).stream().anyMatch(column -> {
			BlockPos shaftPos = worldPos(mineSite.buildSite(), column, -1);
			return shaftPos.getX() == pos.getX() && shaftPos.getZ() == pos.getZ();
		});

		if (!inShaftColumn) {
			return false;
		}

		return isExistingMineLadder(level, mineSite, pos)
			|| isStandableTunnelCell(level, pos)
			|| level.getBlockState(pos).is(Blocks.LADDER);
	}

	private static boolean moveMinerTowardEveningTarget(
		ServerLevel level,
		SettlementState settlement,
		Villager miner,
		MineSite mineSite
	) {
		Optional<BlockPos> targetPos = SettlementVillagers.settlementEveningTargetPos(level, settlement, miner);
		if (targetPos.isEmpty() || SettlementVillagers.isNearSettlementEveningTarget(level, miner, targetPos.get())) {
			return false;
		}

		refreshMineMouthClearance(level, miner, mineSite);

		if (needsMineMouthExit(level, miner, mineSite)) {
			return tryExitTopOfMineLadder(level, miner, mineSite);
		}

		BlockPos target = targetPos.get();
		miner.getNavigation().stop();
		if (miner.getNavigation().moveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, MINING_WALK_SPEED)) {
			return true;
		}

		boolean moved = false;
		BlockPos startPos = miner.blockPosition();

		for (int step = 0; step < EVENING_SURFACE_RETURN_STEPS_PER_PASS; step++) {
			if (!moveMinerOneStepTowardOverworldTarget(level, miner, mineSite, target)) {
				break;
			}

			if (!miner.blockPosition().equals(startPos)) {
				moved = true;
			}
		}

		refreshMineMouthClearance(level, miner, mineSite);
		return moved;
	}

	private static boolean moveMinerOneStepTowardOverworldTarget(
		ServerLevel level,
		Villager miner,
		MineSite mineSite,
		BlockPos targetPos
	) {
		BlockPos currentPos = miner.blockPosition();
		if (SettlementVillagers.isNearSettlementEveningTarget(level, miner, targetPos)) {
			return false;
		}

		Optional<BlockPos> pathStep = overworldPathStepToward(level, mineSite, currentPos, targetPos);
		if (pathStep.isPresent()) {
			miner.getNavigation().stop();
			placeVillagerSafely(miner, pathStep.get());
			return true;
		}

		miner.getNavigation().moveTo(
			targetPos.getX() + 0.5D,
			targetPos.getY(),
			targetPos.getZ() + 0.5D,
			MINING_WALK_SPEED
		);
		return false;
	}

	private static Optional<BlockPos> overworldPathStepToward(
		ServerLevel level,
		MineSite mineSite,
		BlockPos startPos,
		BlockPos targetPos
	) {
		ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
		Map<BlockPos, BlockPos> previousByPos = new HashMap<>();
		Set<BlockPos> visited = new HashSet<>();

		BlockPos start = startPos.immutable();
		BlockPos target = targetPos.immutable();
		frontier.add(start);
		visited.add(start);

		while (!frontier.isEmpty() && visited.size() <= EVENING_SURFACE_PATH_SEARCH_NODES) {
			BlockPos current = frontier.removeFirst();

			if (!current.equals(start) && current.equals(target)) {
				BlockPos step = current;
				BlockPos previous = previousByPos.get(step);

				while (previous != null && !previous.equals(start)) {
					step = previous;
					previous = previousByPos.get(step);
				}

				return Optional.of(step.immutable());
			}

			for (BlockPos next : overworldTravelNeighbors(level, mineSite, start, current)) {
				if (!visited.add(next)) {
					continue;
				}

				previousByPos.put(next, current);
				frontier.addLast(next);
			}
		}

		return Optional.empty();
	}

	private static List<BlockPos> overworldTravelNeighbors(
		ServerLevel level,
		MineSite mineSite,
		BlockPos startPos,
		BlockPos currentPos
	) {
		List<BlockPos> neighbors = new ArrayList<>(8);

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos candidate = currentPos.relative(direction);
			if (isEveningTravelCell(level, mineSite, startPos, candidate)) {
				neighbors.add(candidate.immutable());
			}
		}

		for (Direction direction : List.of(Direction.UP, Direction.DOWN)) {
			BlockPos candidate = currentPos.relative(direction);
			if (isEveningTravelCell(level, mineSite, startPos, candidate)) {
				neighbors.add(candidate.immutable());
			}
		}

		return neighbors;
	}

	private static boolean isEveningTravelCell(ServerLevel level, MineSite mineSite, BlockPos startPos, BlockPos candidate) {
		return isWithinOverworldPathSearch(startPos, candidate)
			&& isWalkableSurfaceCell(level, candidate)
			&& !isBlockedMineMouthTravelCell(mineSite, candidate);
	}

	private static boolean isBlockedMineMouthTravelCell(MineSite mineSite, BlockPos candidate) {
		return isNearMineSurface(candidate, mineSite) && candidate.getY() <= surfaceLadderY(mineSite) + 1;
	}

	private static boolean isWithinOverworldPathSearch(BlockPos startPos, BlockPos candidate) {
		return Math.abs(candidate.getX() - startPos.getX()) <= EVENING_SURFACE_PATH_SEARCH_BLOCKS
			&& Math.abs(candidate.getY() - startPos.getY()) <= 12
			&& Math.abs(candidate.getZ() - startPos.getZ()) <= EVENING_SURFACE_PATH_SEARCH_BLOCKS;
	}

	private static boolean isWalkableSurfaceCell(ServerLevel level, BlockPos pos) {
		if (!level.hasChunkAt(pos)) {
			return false;
		}

		BlockState feetState = level.getBlockState(pos);
		BlockState belowState = level.getBlockState(pos.below());

		if (feetState.isAir() && belowState.isSolid() && level.getBlockState(pos.above()).isAir()) {
			return true;
		}

		return feetState.isSolid() && level.getBlockState(pos.above()).isAir() && level.getBlockState(pos.above(2)).isAir();
	}

	private static BuildRelativePos parseRelativePos(String position) {
		String[] parts = position.split(",");

		if (parts.length != 3) {
			return null;
		}

		try {
			return new BuildRelativePos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	private enum MinerAction {
		DIG_SHAFT,
		DIG_TUNNEL,
		MINE_ORE,
		MINE_STONE,
		PLACE_SUPPORT,
		PLACE_LADDER,
		PLACE_LIGHT,
		PLACE_WARNING_SIGN,
		PLACE_POI_SIGN
	}

	private record TimedTask(String taskKey, long tick) {
	}

	private record ActiveMinerWork(MinerTask task, long assignedTick) {
	}

	private record BuildRelativePos(int right, int forward, int up) {
	}

	private record ShaftColumn(int right, int forward) {
	}

	private record PrimaryTunnelBranch(int levelUp, ShaftColumn anchorColumn, Direction direction) {
	}

	private record SecondaryTunnelBranch(BlockPos originPos, Direction direction) {
		private SecondaryTunnelBranch {
			originPos = originPos.immutable();
		}
	}

	private enum RelativeDirection {
		FORWARD(0, 1),
		BACKWARD(0, -1),
		RIGHT(1, 0),
		LEFT(-1, 0);

		private final int rightStep;
		private final int forwardStep;

		RelativeDirection(int rightStep, int forwardStep) {
			this.rightStep = rightStep;
			this.forwardStep = forwardStep;
		}

		private int rightStep() {
			return rightStep;
		}

		private int forwardStep() {
			return forwardStep;
		}

		private Direction worldDirection(Direction facing) {
			return switch (this) {
				case FORWARD -> facing;
				case BACKWARD -> facing.getOpposite();
				case RIGHT -> facing.getClockWise();
				case LEFT -> facing.getCounterClockWise();
			};
		}
	}

	private record ShaftLayout(List<ShaftColumn> ladderColumns, List<ShaftColumn> openColumns, int starterMinUp) {
	}

	private record MineSite(
		SettlementBuildSite buildSite,
		ShaftLayout layout,
		BlockState ladderStateTemplate,
		Direction supportDirection
	) {
	}

	private record CavePocket(Set<BlockPos> airCells, List<BlockPos> standPositions, List<BlockPos> lightTargets) {
	}

	private record OreWorkTarget(BlockPos targetPos, BlockPos standPos) {
	}

	private record InterestingDiscovery(List<String> signLines) {
	}

	private record SignTarget(BlockPos pos, BlockState state, Direction facing) {
		private SignTarget {
			pos = pos.immutable();
		}
	}

	private record MinerTask(MinerAction action, BlockPos targetPos, BlockState replacementState, BlockPos standPos, String taskKey, List<String> signLines) {
		private MinerTask(MinerAction action, BlockPos targetPos, BlockState replacementState, BlockPos standPos, String taskKey) {
			this(action, targetPos, replacementState, standPos, taskKey, List.of());
		}
	}
}
