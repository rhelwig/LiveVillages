package com.ronhelwig.livevillages.sim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.ronhelwig.livevillages.LiveVillages;
import com.ronhelwig.livevillages.block.MilepostBlock;
import com.ronhelwig.livevillages.content.LiveVillagesBlocks;
import com.ronhelwig.livevillages.content.LiveVillagesVillagerProfessions;

public final class SettlementRoadwrightWork {
	private static final double ROADWORK_REACH_DISTANCE_SQUARED = 9.0D;
	private static final double ROADWORK_SPEED = 0.75D;
	private static final int INTERNAL_TARGET_RADIUS_BLOCKS = 80;
	private static final int EXTERNAL_TARGET_RADIUS_BLOCKS = 512;
	private static final int MILEPOST_TARGET_RADIUS_BLOCKS = 192;
	private static final int INTERNAL_POI_SCAN_RADIUS_BLOCKS = 96;
	private static final int INTERNAL_POI_SCAN_Y_RANGE_BLOCKS = 24;
	private static final int INTERNAL_PATH_SEARCH_MARGIN_BLOCKS = 9; // Reduced from 18
	private static final int EXTERNAL_PATH_SEARCH_SPAN_BLOCKS = 80; // Reduced from 160
	private static final int EXISTING_PATH_REPAIR_RADIUS_BLOCKS = 56;
	private static final int MAX_RESHAPE_STEP_BLOCKS = 3;
	private static final int SURFACE_SEARCH_DEPTH_BLOCKS = 10;
	private static final int ROUTE_ENDPOINT_SEARCH_RADIUS_BLOCKS = 6;
	private static final int MAX_INTERNAL_PATH_SEARCH_NODES = 1_500; // Reduced from 6,000
	private static final int MAX_EXTERNAL_PATH_SEARCH_NODES = 2_250; // Reduced from 9,000
	private static final int MAX_SURVEYOR_MAP_PLAN_BLOCKS = 160;
	private static final int MAX_SURVEYOR_MAP_ROUTE_TRACE_BLOCKS = 640;
	private static final int MAX_SURVEYOR_FORECAST_TARGETS_PER_START = 12;
	private static final int MAX_SURVEYOR_FORECAST_TASKS_PER_ROUTE = 96;
	private static final int MAX_MILEPOST_TARGETS = 24;
	private static final int MAX_LOADED_ROADWORKERS_PER_MAINTENANCE = 3;
	private static final int MAX_CATCHUP_TASKS_PER_MAINTENANCE = 24;
	private static final double MAX_CATCHUP_DAYS = 3.0D;
	private static final long MAX_CATCHUP_WALL_TIME_NANOS = 150_000_000L;
	private static final int ROADWORK_FORECAST_DAYS_FOR_PLANNED_WORK = 1;
	private static final int ROADWORK_FORECAST_DAYS_FOR_ROUTE_TRACE = 21;
	private static final int ROADWORK_DAILY_WORK_UNITS_PER_ROADWRIGHT = SettlementEconomyRules.scaledWorkerDailyUnits(18);
	private static final int ROADWORK_DAILY_WORK_UNITS_PER_HELPER = SettlementEconomyRules.scaledWorkerDailyUnits(9);
	private static final long ROADWORK_TASK_CACHE_TICKS = 1_200L; // Keep expensive path searches from repeating while the same task remains useful.
	private static final long ROADWORK_PATH_CACHE_TICKS = 1_200L;
	private static final long ROADWORK_VISUAL_MEMORY_TICKS = 12_000L;
	private static final long ROADWORK_DECIDE_INTERVAL_TICKS = 320L;
	private static final long MILEPOST_TARGET_CACHE_TICKS = 100L;
	private static final int BLOCK_UPDATE_FLAGS = 3;
	private static final Map<String, CachedPoiTargets> INTERNAL_POI_TARGET_CACHE = new HashMap<>();
	private static final Map<String, CachedMilepostTargets> MILEPOST_TARGET_CACHE = new HashMap<>();
	private static final Map<String, CachedRoadworkPlan> ROADWORK_TASK_CACHE = new HashMap<>();
	private static final Map<String, CachedRoadworkPlan> ROADWORK_VISUAL_PLAN_CACHE = new HashMap<>();
	private static final Map<RoadworkPathCacheKey, CachedPlannedPath> ROADWORK_PATH_CACHE = new HashMap<>();

	private SettlementRoadwrightWork() {
	}

	public static RoadworkResult maintainLoadedRoadwork(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Collection<SettlementBuildSite> buildSites,
		Collection<SettlementState> allSettlements,
		List<RouteState> routes
	) {
		List<Villager> roadwrights = SettlementVillagers.nearbyRoadwrights(level, settlement);

		if (roadwrights.isEmpty()) {
			return RoadworkResult.unchanged();
		}

		List<Villager> roadWorkers = roadworkWorkersForMaintenance(level, settlement, roadwrights);
		List<PathTarget> externalTargets = externalTargets(settlement, allSettlements, routes);
		Set<String> busyRoadwrightIds = new HashSet<>();
		boolean worldChanged = false;
		boolean plannedThisPass = false;
		long tick = level.getServer().getTickCount();

		for (Villager roadwright : roadWorkers) {
			String roadwrightId = roadwright.getUUID().toString();
			boolean takingBreak = SettlementVillagerWorkSchedule.isTakingBreak(level, roadwright);

			if (takingBreak) {
				roadwright.getNavigation().stop();
				continue;
			}

			BlockPos workStart = roadworkStartPos(level, settlement, roadwright);
			List<PathTarget> internalTargets = internalTargets(level, settlement, buildSites, workStart);
			List<PathTarget> milepostTargets = milepostTargets(level, settlement, workStart);
			Optional<RoadworkDebugPlan> plan = cachedRoadworkPlan(level, settlement, roadwrightId, tick);

			if (plan.isEmpty() && SettlementVillagerWorkSchedule.shouldStartNewWork(level, roadwright, "roadwork", ROADWORK_DECIDE_INTERVAL_TICKS)) {
				if (!isPlanningRoadwright(roadwright) || plannedThisPass) {
					continue;
				}

				long chooseTaskStart = System.nanoTime();
				plan = chooseRoadworkPlan(level, settlement, roadwrightId, workStart, internalTargets, milepostTargets, externalTargets, tick);
				long chooseTaskTime = System.nanoTime() - chooseTaskStart;
				if (chooseTaskTime > 100_000_000) { // >100ms
					LiveVillages.LOGGER.warn("Roadwork: chooseRoadworkTask took {} ms for roadwright {}", Math.round(chooseTaskTime / 1_000_000.0D), roadwrightId);
				}

				plannedThisPass = plan.isPresent();
			}

			if (plan.isEmpty()) {
				continue;
			}

			rememberVisibleRoadworkPlan(settlement, roadwrightId, plan.get(), tick);
			PathTask task = plan.get().toPathTask();
			busyRoadwrightIds.add(roadwrightId);
			showRoadwrightTool(roadwright);
			steerRoadwrightTowardTask(roadwright, task.standPos());

			if (!isWithinWorkReach(roadwright, task.workPos())) {
				continue;
			}

			if (performRoadwork(level, settlement, stock, task)) {
				roadwright.swing(InteractionHand.MAIN_HAND);
				worldChanged = true;
				Optional<RoadworkDebugPlan> continuedPlan = advanceRoadworkPlanOnSameRoute(level, settlement, plan.get());

				if (continuedPlan.isPresent()) {
					ROADWORK_TASK_CACHE.put(roadworkTaskCacheKey(settlement, roadwrightId), new CachedRoadworkPlan(continuedPlan, tick));
					rememberVisibleRoadworkPlan(settlement, roadwrightId, continuedPlan.get(), tick);
				} else {
					ROADWORK_TASK_CACHE.remove(roadworkTaskCacheKey(settlement, roadwrightId));
				}
			}
		}

		return new RoadworkResult(Set.copyOf(busyRoadwrightIds), worldChanged);
	}

	public static RoadworkCatchupResult applyLoadedRoadworkCatchup(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Collection<SettlementBuildSite> buildSites,
		Collection<SettlementState> allSettlements,
		List<RouteState> routes,
		double elapsedDays
	) {
		if (elapsedDays <= 0.0D) {
			return RoadworkCatchupResult.none();
		}

		List<Villager> roadwrights = SettlementVillagers.nearbyRoadwrights(level, settlement);

		if (roadwrights.isEmpty()) {
			return RoadworkCatchupResult.none();
		}

		List<Villager> roadWorkers = roadworkWorkersForMaintenance(level, settlement, roadwrights);
		int helperCount = Math.max(0, roadWorkers.size() - roadwrights.size());
		int dailyWorkUnits = forecastDailyWorkUnits(roadwrights.size(), helperCount);
		int taskBudget = Math.min(
			MAX_CATCHUP_TASKS_PER_MAINTENANCE,
			Math.max(0, (int) Math.round(dailyWorkUnits * Math.min(MAX_CATCHUP_DAYS, elapsedDays)))
		);

		if (taskBudget <= 0) {
			return RoadworkCatchupResult.none();
		}

		Set<String> appliedTasks = new HashSet<>();
		boolean worldChanged = false;
		int tasksApplied = 0;
		long catchupStartNanos = System.nanoTime();

		for (ForecastRoutePlan candidate : surveyorMapForecastCandidates(level, settlement, buildSites, allSettlements, routes)) {
			if (tasksApplied >= taskBudget) {
				break;
			}

			for (PathTask task : candidate.tasks()) {
				if (tasksApplied >= taskBudget || System.nanoTime() - catchupStartNanos > MAX_CATCHUP_WALL_TIME_NANOS) {
					break;
				}

				String taskKey = task.action().name() + "@" + task.workPos().toShortString();

				if (!appliedTasks.add(taskKey)) {
					continue;
				}

				if (!performRoadwork(level, settlement, stock, task)) {
					continue;
				}

				worldChanged = true;
				tasksApplied++;
			}
		}

		return new RoadworkCatchupResult(worldChanged, tasksApplied);
	}

	public static Optional<String> loadedRoadworkTaskKey(
		ServerLevel level,
		SettlementState settlement,
		Collection<SettlementBuildSite> buildSites,
		Collection<SettlementState> allSettlements,
		List<RouteState> routes
	) {
		if (!usesLoadedRoadwork(level, settlement)) {
			return Optional.empty();
		}

		long tick = level.getServer().getTickCount();
		String cachePrefix = settlement.dimension().identifier() + "|" + settlement.id() + "|";
		boolean hasFreshRoadworkTask = ROADWORK_TASK_CACHE.entrySet().stream()
			.filter(entry -> entry.getKey().startsWith(cachePrefix))
			.map(Map.Entry::getKey)
			.anyMatch(roadwrightCacheKey -> cachedRoadworkPlan(level, settlement, roadwrightIdFromCacheKey(roadwrightCacheKey), tick).isPresent());

		if (hasFreshRoadworkTask) {
			return Optional.of("improving_paths");
		}

		return Optional.of("surveying_village");
	}

	public static Map<String, RoadworkDebugPlan> loadedRoadworkDebugPlans(
		ServerLevel level,
		SettlementState settlement,
		Collection<SettlementBuildSite> buildSites,
		Collection<SettlementState> allSettlements,
		List<RouteState> routes
	) {
		if (!usesLoadedRoadwork(level, settlement)) {
			return Map.of();
		}

		Map<String, RoadworkDebugPlan> debugPlans = new HashMap<>();
		long tick = level.getServer().getTickCount();

		for (Villager roadwright : SettlementVillagers.nearbyRoadwrights(level, settlement)) {
			String roadwrightId = roadwright.getUUID().toString();
			cachedRoadworkPlan(level, settlement, roadwrightId, tick)
				.ifPresent(plan -> debugPlans.put(roadwrightId, plan));
		}

		return Map.copyOf(debugPlans);
	}

	public static void restorePersistentPlans(
		SettlementState settlement,
		Map<String, RoadworkDebugPlan> persistedPlans,
		long tick
	) {
		if (persistedPlans == null || persistedPlans.isEmpty()) {
			return;
		}

		for (Map.Entry<String, RoadworkDebugPlan> entry : persistedPlans.entrySet()) {
			String cacheKey = roadworkTaskCacheKey(settlement, entry.getKey());

			ROADWORK_TASK_CACHE.putIfAbsent(cacheKey, new CachedRoadworkPlan(Optional.of(entry.getValue()), tick));
			ROADWORK_VISUAL_PLAN_CACHE.putIfAbsent(cacheKey, new CachedRoadworkPlan(Optional.of(entry.getValue()), tick));
		}
	}

	public static Map<String, RoadworkDebugPlan> persistentPlansForSettlement(ServerLevel level, SettlementState settlement, long tick) {
		Map<String, RoadworkDebugPlan> persisted = new HashMap<>();

		for (Map.Entry<String, RoadworkDebugPlan> entry : visibleRoadworkPlans(level, settlement, tick).entrySet()) {
			persisted.put(roadwrightIdFromCacheKey(entry.getKey()), entry.getValue());
		}

		return Map.copyOf(persisted);
	}

	public static SurveyorMapForecast surveyorMapForecast(
		ServerLevel level,
		SettlementState settlement,
		Collection<SettlementBuildSite> buildSites,
		Collection<SettlementState> allSettlements,
		List<RouteState> routes
	) {
		if (!usesLoadedRoadwork(level, settlement)) {
			return SurveyorMapForecast.empty();
		}

		List<Villager> roadwrights = SettlementVillagers.nearbyRoadwrights(level, settlement);
		List<Villager> roadWorkers = roadworkWorkersForMaintenance(level, settlement, roadwrights);
		int helperCount = Math.max(0, roadWorkers.size() - roadwrights.size());
		int dailyWorkUnits = forecastDailyWorkUnits(roadwrights.size(), helperCount);
		int plannedWorkBudget = Math.max(1, dailyWorkUnits * ROADWORK_FORECAST_DAYS_FOR_PLANNED_WORK);
		int routeTraceBudget = Math.max(plannedWorkBudget, dailyWorkUnits * ROADWORK_FORECAST_DAYS_FOR_ROUTE_TRACE);
		LinkedHashSet<BlockPos> routeTraceBlocks = new LinkedHashSet<>();
		LinkedHashSet<BlockPos> plannedWorkBlocks = new LinkedHashSet<>();

		for (ForecastRoutePlan candidate : surveyorMapForecastCandidates(level, settlement, buildSites, allSettlements, routes)) {
			if (routeTraceBudget <= 0 && plannedWorkBudget <= 0) {
				break;
			}

			if (routeTraceBudget > 0) {
				appendRouteTraceBlocks(routeTraceBlocks, candidate.routeTrace());
				routeTraceBudget -= Math.min(candidate.tasks().size(), routeTraceBudget);
			}

			if (plannedWorkBudget > 0) {
				for (PathTask task : candidate.tasks()) {
					plannedWorkBlocks.add(task.workPos().immutable());
					plannedWorkBudget--;

					if (plannedWorkBudget <= 0 || plannedWorkBlocks.size() >= MAX_SURVEYOR_MAP_PLAN_BLOCKS) {
						break;
					}
				}
			}

			if (routeTraceBlocks.size() >= MAX_SURVEYOR_MAP_ROUTE_TRACE_BLOCKS && plannedWorkBlocks.size() >= MAX_SURVEYOR_MAP_PLAN_BLOCKS) {
				break;
			}
		}

		return new SurveyorMapForecast(
			limitBlockPositions(routeTraceBlocks, MAX_SURVEYOR_MAP_ROUTE_TRACE_BLOCKS),
			limitBlockPositions(plannedWorkBlocks, MAX_SURVEYOR_MAP_PLAN_BLOCKS)
		);
	}

	public static List<BlockPos> loadedRoadworkRouteBlocks(
		ServerLevel level,
		SettlementState settlement,
		Collection<SettlementBuildSite> buildSites,
		Collection<SettlementState> allSettlements,
		List<RouteState> routes
	) {
		return surveyorMapForecast(level, settlement, buildSites, allSettlements, routes).routeTraceBlocks();
	}

	public static List<BlockPos> loadedRoadworkPlanBlocks(
		ServerLevel level,
		SettlementState settlement,
		Collection<SettlementBuildSite> buildSites,
		Collection<SettlementState> allSettlements,
		List<RouteState> routes
	) {
		return surveyorMapForecast(level, settlement, buildSites, allSettlements, routes).plannedWorkBlocks();
	}

	private static List<ForecastRoutePlan> surveyorMapForecastCandidates(
		ServerLevel level,
		SettlementState settlement,
		Collection<SettlementBuildSite> buildSites,
		Collection<SettlementState> allSettlements,
		List<RouteState> routes
	) {
		List<ForecastRoutePlan> candidates = new ArrayList<>();
		Set<ForecastRouteKey> seen = new HashSet<>();
		long tick = level.getServer().getTickCount();
		List<PathTarget> externalTargets = externalTargets(settlement, allSettlements, routes);

		for (Villager roadwright : SettlementVillagers.nearbyRoadwrights(level, settlement)) {
			cachedRoadworkPlan(level, settlement, roadwright.getUUID().toString(), tick)
				.flatMap(plan -> forecastRoutePlan(level, settlement, plan))
				.ifPresent(plan -> addForecastCandidate(candidates, seen, plan));
		}

		for (RoadworkDebugPlan visiblePlan : visibleRoadworkPlans(level, settlement, tick).values()) {
			forecastRoutePlan(level, settlement, visiblePlan)
				.ifPresent(plan -> addForecastCandidate(candidates, seen, plan));
		}

		for (BlockPos workStart : roadworkStartPositions(level, settlement)) {
			int addedForStart = 0;

			addedForStart += addForecastCandidate(candidates, seen, forecastRoutePlan(level, settlement, existingPathRepairPlan(level, settlement, workStart)));
			addedForStart += addForecastCandidatesForTargets(candidates, seen, level, settlement, workStart, centerTargets(level, settlement, workStart), true, "center", MAX_SURVEYOR_FORECAST_TARGETS_PER_START - addedForStart);
			addedForStart += addForecastCandidatesForTargets(candidates, seen, level, settlement, workStart, internalTargets(level, settlement, buildSites, workStart), true, "internal", MAX_SURVEYOR_FORECAST_TARGETS_PER_START - addedForStart);
			addedForStart += addForecastCandidatesForTargets(candidates, seen, level, settlement, workStart, milepostTargets(level, settlement, workStart), false, "milepost", MAX_SURVEYOR_FORECAST_TARGETS_PER_START - addedForStart);
			addedForStart += addForecastCandidatesForTargets(candidates, seen, level, settlement, workStart, externalTargets, false, "external", MAX_SURVEYOR_FORECAST_TARGETS_PER_START - addedForStart);
		}

		return List.copyOf(candidates);
	}

	private static int addForecastCandidatesForTargets(
		List<ForecastRoutePlan> candidates,
		Set<ForecastRouteKey> seen,
		ServerLevel level,
		SettlementState settlement,
		BlockPos workStart,
		List<PathTarget> targets,
		boolean internal,
		String targetKind,
		int remainingSlots
	) {
		if (remainingSlots <= 0) {
			return 0;
		}

		int added = 0;

		for (PathTarget target : targets) {
			if (added >= remainingSlots) {
				break;
			}

			added += addForecastCandidate(candidates, seen, forecastRoutePlan(level, settlement, roadworkPlanForTarget(level, settlement, workStart, target, internal, targetKind)));
		}

		return added;
	}

	private static Optional<ForecastRoutePlan> forecastRoutePlan(ServerLevel level, SettlementState settlement, Optional<RoadworkDebugPlan> plan) {
		return plan.flatMap(currentPlan -> forecastRoutePlan(level, settlement, currentPlan));
	}

	private static Optional<ForecastRoutePlan> forecastRoutePlan(ServerLevel level, SettlementState settlement, RoadworkDebugPlan plan) {
		Optional<RoadworkDebugPlan> currentPlan = recomputeRoadworkPlan(level, settlement, plan);

		if (currentPlan.isEmpty()) {
			return Optional.empty();
		}

		List<PathTask> tasks = pathTasksOnPath(
			level,
			settlement,
			currentPlan.get().routeTrace(),
			usesInternalRouteSearch(currentPlan.get().targetKind()),
			MAX_SURVEYOR_FORECAST_TASKS_PER_ROUTE
		);

		if (tasks.isEmpty()) {
			return Optional.empty();
		}

		return Optional.of(new ForecastRoutePlan(
			currentPlan.get().targetKind(),
			currentPlan.get().startPos(),
			currentPlan.get().targetPos(),
			currentPlan.get().routeTrace(),
			tasks
		));
	}

	private static int addForecastCandidate(List<ForecastRoutePlan> candidates, Set<ForecastRouteKey> seen, Optional<ForecastRoutePlan> candidate) {
		return candidate.map(plan -> addForecastCandidate(candidates, seen, plan)).orElse(0);
	}

	private static int addForecastCandidate(List<ForecastRoutePlan> candidates, Set<ForecastRouteKey> seen, ForecastRoutePlan candidate) {
		ForecastRouteKey key = new ForecastRouteKey(candidate.targetKind(), candidate.startPos(), candidate.targetPos());

		if (!seen.add(key)) {
			return 0;
		}

		candidates.add(candidate);
		return 1;
	}

	private static int forecastDailyWorkUnits(int roadwrightCount, int helperCount) {
		return (roadwrightCount * ROADWORK_DAILY_WORK_UNITS_PER_ROADWRIGHT) + (helperCount * ROADWORK_DAILY_WORK_UNITS_PER_HELPER);
	}

	private static void appendRouteTraceBlocks(Set<BlockPos> routeTraceBlocks, List<BlockPos> routeTrace) {
		for (BlockPos pos : routeTrace) {
			if (routeTraceBlocks.size() >= MAX_SURVEYOR_MAP_ROUTE_TRACE_BLOCKS) {
				return;
			}

			routeTraceBlocks.add(pos.immutable());
		}
	}

	private static List<BlockPos> limitBlockPositions(Collection<BlockPos> positions, int maxSize) {
		return positions.stream()
			.limit(maxSize)
			.map(BlockPos::immutable)
			.toList();
	}

	private static boolean usesLoadedRoadwork(ServerLevel level, SettlementState settlement) {
		return !SettlementVillagers.nearbyRoadwrights(level, settlement).isEmpty();
	}

	private static boolean isPlanningRoadwright(Villager villager) {
		return villager.getVillagerData().profession().is(LiveVillagesVillagerProfessions.ROADWRIGHT);
	}

	private static List<Villager> roadworkWorkersForMaintenance(ServerLevel level, SettlementState settlement, List<Villager> roadwrights) {
		List<Villager> helpers = SettlementVillagers.nearbyRoadworkHelpers(level, settlement);

		if (helpers.isEmpty()) {
			return List.of();
		}

		Set<String> roadwrightIds = roadwrights.stream()
			.map(villager -> villager.getUUID().toString())
			.collect(java.util.stream.Collectors.toSet());
		int workerLimit = Math.max(roadwrights.size(), MAX_LOADED_ROADWORKERS_PER_MAINTENANCE);
		return helpers.stream()
			.sorted(Comparator
				.comparing((Villager villager) -> !roadwrightIds.contains(villager.getUUID().toString()))
				.thenComparingDouble(villager -> villager.blockPosition().distSqr(settlement.center()))
				.thenComparing(villager -> villager.getUUID().toString()))
			.limit(workerLimit)
			.toList();
	}

	private static Optional<RoadworkDebugPlan> chooseRoadworkPlan(
		ServerLevel level,
		SettlementState settlement,
		String roadwrightId,
		BlockPos workStart,
		List<PathTarget> internalTargets,
		List<PathTarget> milepostTargets,
		List<PathTarget> externalTargets,
		long tick
	) {
		Optional<RoadworkDebugPlan> cachedPlan = cachedRoadworkPlan(level, settlement, roadwrightId, tick);

		if (cachedPlan.isPresent()) {
			return cachedPlan;
		}

		String cacheKey = roadworkTaskCacheKey(settlement, roadwrightId);
		Optional<RoadworkDebugPlan> plan = existingPathRepairPlan(level, settlement, workStart)
			.or(() -> choosePathPlan(level, settlement, workStart, centerTargets(level, settlement, workStart), true, "center"))
			.or(() -> choosePathPlan(level, settlement, workStart, milepostTargets, false, "milepost"))
			.or(() -> choosePathPlan(level, settlement, workStart, internalTargets, true, "internal"))
			.or(() -> choosePathPlan(level, settlement, workStart, externalTargets, false, "external"));

		ROADWORK_TASK_CACHE.put(cacheKey, new CachedRoadworkPlan(plan, tick));
		plan.ifPresent(chosenPlan -> rememberVisibleRoadworkPlan(settlement, roadwrightId, chosenPlan, tick));
		return plan;
	}

	private static Optional<RoadworkDebugPlan> cachedRoadworkPlan(ServerLevel level, SettlementState settlement, String roadwrightId, long tick) {
		String cacheKey = roadworkTaskCacheKey(settlement, roadwrightId);
		CachedRoadworkPlan cachedPlan = ROADWORK_TASK_CACHE.get(cacheKey);

		if (cachedPlan == null || tick - cachedPlan.tick() > ROADWORK_TASK_CACHE_TICKS || cachedPlan.plan().isEmpty()) {
			return Optional.empty();
		}

		RoadworkDebugPlan plan = cachedPlan.plan().get();

		if (isRoadworkTaskStillUseful(level, settlement, plan.toPathTask())) {
			return Optional.of(plan.asCached());
		}

		Optional<RoadworkDebugPlan> continuedPlan = advanceRoadworkPlanOnSameRoute(level, settlement, plan);

		if (continuedPlan.isPresent()) {
			ROADWORK_TASK_CACHE.put(cacheKey, new CachedRoadworkPlan(continuedPlan, tick));
			return Optional.of(continuedPlan.get().asCached());
		}

		ROADWORK_TASK_CACHE.remove(cacheKey);
		return Optional.empty();
	}

	private static Optional<RoadworkDebugPlan> advanceRoadworkPlanOnSameRoute(ServerLevel level, SettlementState settlement, RoadworkDebugPlan currentPlan) {
		boolean internal = usesInternalRouteSearch(currentPlan.targetKind());
		Optional<PathTask> nextTaskOnCachedTrace = firstMissingPathTaskOnPath(level, settlement, currentPlan.routeTrace(), internal);

		if (nextTaskOnCachedTrace.isPresent()) {
			return Optional.of(new RoadworkDebugPlan(
				nextTaskOnCachedTrace.get().workPos(),
				nextTaskOnCachedTrace.get().standPos(),
				nextTaskOnCachedTrace.get().action().name(),
				nextTaskOnCachedTrace.get().uphillDirection(),
				currentPlan.taskKey(),
				currentPlan.targetKind(),
				currentPlan.startPos(),
				currentPlan.targetPos(),
				currentPlan.routeTrace(),
				false
			));
		}

		return recomputeRoadworkPlan(level, settlement, currentPlan);
	}

	private static Optional<RoadworkDebugPlan> recomputeRoadworkPlan(ServerLevel level, SettlementState settlement, RoadworkDebugPlan currentPlan) {
		boolean internal = usesInternalRouteSearch(currentPlan.targetKind());
		List<BlockPos> recomputedRouteTrace = plannedSurfacePath(level, settlement, currentPlan.startPos(), currentPlan.targetPos(), internal);
		Optional<PathTask> nextTask = firstMissingPathTaskOnPath(level, settlement, recomputedRouteTrace, internal);

		if (nextTask.isPresent()) {
			return Optional.of(new RoadworkDebugPlan(
				nextTask.get().workPos(),
				nextTask.get().standPos(),
				nextTask.get().action().name(),
				nextTask.get().uphillDirection(),
				currentPlan.taskKey(),
				currentPlan.targetKind(),
				currentPlan.startPos(),
				currentPlan.targetPos(),
				recomputedRouteTrace,
				false
			));
		}

		Optional<PathTask> fallbackTask = firstMissingPathTaskOnPath(level, settlement, currentPlan.routeTrace(), internal);

		if (fallbackTask.isEmpty()) {
			return Optional.empty();
		}

		return Optional.of(new RoadworkDebugPlan(
			fallbackTask.get().workPos(),
			fallbackTask.get().standPos(),
			fallbackTask.get().action().name(),
			fallbackTask.get().uphillDirection(),
			currentPlan.taskKey(),
			currentPlan.targetKind(),
			currentPlan.startPos(),
			currentPlan.targetPos(),
			currentPlan.routeTrace(),
			false
		));
	}

	private static boolean usesInternalRouteSearch(String targetKind) {
		return !"external".equals(targetKind) && !"milepost".equals(targetKind);
	}

	private static void rememberVisibleRoadworkPlan(SettlementState settlement, String roadwrightId, RoadworkDebugPlan plan, long tick) {
		ROADWORK_VISUAL_PLAN_CACHE.put(roadworkTaskCacheKey(settlement, roadwrightId), new CachedRoadworkPlan(Optional.of(plan), tick));
	}

	private static Map<String, RoadworkDebugPlan> visibleRoadworkPlans(ServerLevel level, SettlementState settlement, long tick) {
		String cachePrefix = settlement.dimension().identifier() + "|" + settlement.id() + "|";
		Map<String, RoadworkDebugPlan> visiblePlans = new HashMap<>();

		for (Map.Entry<String, CachedRoadworkPlan> entry : ROADWORK_VISUAL_PLAN_CACHE.entrySet()) {
			if (!entry.getKey().startsWith(cachePrefix)) {
				continue;
			}

			CachedRoadworkPlan cachedPlan = entry.getValue();

			if (tick - cachedPlan.tick() > ROADWORK_VISUAL_MEMORY_TICKS || cachedPlan.plan().isEmpty()) {
				continue;
			}

			Optional<RoadworkDebugPlan> continuedPlan = advanceRoadworkPlanOnSameRoute(level, settlement, cachedPlan.plan().get());

			if (continuedPlan.isPresent()) {
				visiblePlans.put(entry.getKey(), continuedPlan.get().asCached());
			}
		}

		ROADWORK_VISUAL_PLAN_CACHE.entrySet().removeIf(entry -> entry.getKey().startsWith(cachePrefix)
			&& (tick - entry.getValue().tick() > ROADWORK_VISUAL_MEMORY_TICKS
				|| entry.getValue().plan().isEmpty()
				|| advanceRoadworkPlanOnSameRoute(level, settlement, entry.getValue().plan().get()).isEmpty()));
		return Map.copyOf(visiblePlans);
	}

	private static void addRoadworkPlanBlock(Set<BlockPos> plannedBlocks, Optional<PathTask> task) {
		task.map(PathTask::workPos).ifPresent(plannedBlocks::add);
	}

	private static String roadworkTaskCacheKey(SettlementState settlement, String roadwrightId) {
		return settlement.dimension().identifier() + "|" + settlement.id() + "|" + roadwrightId;
	}

	private static String roadwrightIdFromCacheKey(String cacheKey) {
		int separator = cacheKey.lastIndexOf('|');
		return separator >= 0 ? cacheKey.substring(separator + 1) : cacheKey;
	}

	private static boolean isRoadworkTaskStillUseful(ServerLevel level, SettlementState settlement, PathTask task) {
		BlockState workState = level.getBlockState(task.workPos());

		return switch (task.action()) {
			case PLACE_TRAIL -> canConvertToTrail(level, settlement, task.workPos(), workState);
			case COLLECT_LEAF_LITTER -> workState.is(Blocks.LEAF_LITTER);
			case RAISE_SURFACE -> canRaiseLowerRoadSurface(level, task.workPos());
			case PLACE_STAIR -> needsSlopeTreatment(level, task.workPos());
		};
	}

	private static void addFirstPlannedRoute(ServerLevel level, SettlementState settlement, Set<BlockPos> plannedBlocks, BlockPos start, List<PathTarget> targets, boolean internal) {
		for (PathTarget target : targets) {
			if (firstMissingPathBlock(level, settlement, start, target.pos(), internal).isEmpty()) {
				continue;
			}

			for (BlockPos pathPos : plannedSurfacePath(level, settlement, start, target.pos(), internal)) {
				plannedBlocks.add(pathPos.immutable());

				if (plannedBlocks.size() >= MAX_SURVEYOR_MAP_PLAN_BLOCKS) {
					return;
				}
			}

			return;
		}
	}

	private static List<BlockPos> roadworkStartPositions(ServerLevel level, SettlementState settlement) {
		LinkedHashSet<BlockPos> starts = new LinkedHashSet<>();

		for (Villager roadwright : SettlementVillagers.nearbyRoadwrights(level, settlement)) {
			starts.add(roadwrightOwnStart(level, settlement, roadwright).immutable());
		}

		if (starts.isEmpty()) {
			starts.add(settlement.center());
		}

		return List.copyOf(starts);
	}

	private static BlockPos roadwrightOwnStart(ServerLevel level, SettlementState settlement, Villager roadwright) {
		return SettlementVillagers.roadwrightJobSite(level, roadwright)
			.flatMap(jobSite -> roadwrightWorkstationStart(level, jobSite, settlement.center()))
			.orElse(roadwright.blockPosition())
			.immutable();
	}

	private static BlockPos roadworkStartPos(ServerLevel level, SettlementState settlement, Villager worker) {
		Optional<BlockPos> jobSite = SettlementVillagers.roadwrightJobSite(level, worker);

		if (jobSite.isPresent()) {
			return roadwrightWorkstationStart(level, jobSite.get(), settlement.center()).orElse(worker.blockPosition()).immutable();
		}

		return roadworkStartPositions(level, settlement).stream()
			.min(Comparator.comparingDouble(start -> start.distSqr(worker.blockPosition())))
			.orElse(worker.blockPosition())
			.immutable();
	}

	private static Optional<BlockPos> roadwrightWorkstationStart(ServerLevel level, BlockPos jobSite, BlockPos center) {
		return standableAccessTargets(level, jobSite).stream()
			.min(Comparator.comparingDouble(pos -> pos.distSqr(center)))
			.or(() -> nearestSurfacePathPos(level, jobSite, 2));
	}

	private static List<PathTarget> centerTargets(ServerLevel level, SettlementState settlement, BlockPos workStart) {
		List<BlockPos> accessTargets = standableAccessTargets(level, settlement.center());

		if (!accessTargets.isEmpty()) {
			return accessTargets.stream()
				.sorted(Comparator.comparingDouble(pos -> pos.distSqr(workStart)))
				.map(pos -> new PathTarget(pos, true))
				.toList();
		}

		return List.of(new PathTarget(settlement.center(), true));
	}

	private static List<PathTarget> internalTargets(ServerLevel level, SettlementState settlement, Collection<SettlementBuildSite> buildSites, BlockPos workStart) {
		List<PathTarget> targets = new ArrayList<>();
		targets.addAll(centerTargets(level, settlement, workStart));

		for (SettlementBuildSite buildSite : buildSites) {
			if (!buildSite.settlementId().equals(settlement.id())) {
				continue;
			}

			if (buildSite.workstationPos().distSqr(settlement.center()) > INTERNAL_TARGET_RADIUS_BLOCKS * INTERNAL_TARGET_RADIUS_BLOCKS) {
				continue;
			}

			targets.addAll(internalTargetsForBuildSite(buildSite));
		}

		targets.addAll(cachedScannedInternalPoiTargets(level, settlement));

		return targets.stream()
			.distinct()
			.sorted(Comparator.comparingDouble((PathTarget target) -> target.pos().equals(settlement.center()) ? -1.0D : target.pos().distSqr(workStart)))
			.toList();
	}

	public static List<BlockPos> nearbyMileposts(ServerLevel level, BlockPos center, int radiusBlocks) {
		return level.getPoiManager().findAllClosestFirstWithType(
			poiType -> poiType.is(LiveVillagesVillagerProfessions.MILEPOST_POI),
			pos -> true,
			center,
			radiusBlocks,
			PoiManager.Occupancy.ANY
		)
			.map(com.mojang.datafixers.util.Pair::getSecond)
			.filter(level::hasChunkAt)
			.filter(pos -> MilepostBlock.isLowerPart(level.getBlockState(pos)))
			.map(BlockPos::immutable)
			.limit(MAX_MILEPOST_TARGETS)
			.toList();
	}

	private static List<PathTarget> milepostTargets(ServerLevel level, SettlementState settlement, BlockPos workStart) {
		return cachedNearbyMileposts(level, settlement).stream()
			.filter(milepostPos -> milepostPos.distSqr(settlement.center()) >= 36.0D)
			.map(milepostPos -> standableAccessTargets(level, milepostPos).stream()
				.min(Comparator.comparingDouble(accessPos -> accessPos.distSqr(workStart)))
				.map(accessPos -> new PathTarget(accessPos, false)))
			.flatMap(Optional::stream)
			.sorted(Comparator
				.comparingDouble((PathTarget target) -> target.pos().distSqr(workStart))
				.thenComparingDouble(target -> target.pos().distSqr(settlement.center())))
			.toList();
	}

	private static List<BlockPos> cachedNearbyMileposts(ServerLevel level, SettlementState settlement) {
		String cacheKey = settlement.dimension().identifier() + "|" + settlement.id();
		long currentTick = level.getServer().getTickCount();
		CachedMilepostTargets cachedTargets = MILEPOST_TARGET_CACHE.get(cacheKey);

		if (cachedTargets != null && currentTick - cachedTargets.tick() <= MILEPOST_TARGET_CACHE_TICKS) {
			return cachedTargets.positions();
		}

		List<BlockPos> targets = nearbyMileposts(level, settlement.center(), MILEPOST_TARGET_RADIUS_BLOCKS);
		MILEPOST_TARGET_CACHE.put(cacheKey, new CachedMilepostTargets(currentTick, targets));
		return targets;
	}

	private static List<PathTarget> internalTargetsForBuildSite(SettlementBuildSite buildSite) {
		List<PathTarget> targets = new ArrayList<>();

		for (SettlementBuildBlockState block : buildSite.blocks()) {
			if (!block.blueprintSymbol().equals("D")) {
				continue;
			}

			BlockState plannedState = SettlementConstruction.plannedBuildSiteBlockState(buildSite, block);

			if (plannedState == null
				|| !plannedState.hasProperty(DoorBlock.FACING)
				|| !plannedState.hasProperty(DoorBlock.HALF)
				|| plannedState.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) {
				continue;
			}

			Optional<BlockPos> doorPos = SettlementConstruction.buildSiteBlockPos(buildSite, block);

			if (doorPos.isEmpty()) {
				continue;
			}

			Direction doorFacing = plannedState.getValue(DoorBlock.FACING);
			targets.add(new PathTarget(doorPos.get().relative(doorFacing), true));
		}

		targets.add(new PathTarget(buildSite.workstationPos(), true));
		return targets;
	}

	private static List<PathTarget> cachedScannedInternalPoiTargets(ServerLevel level, SettlementState settlement) {
		String cacheKey = settlement.dimension().identifier() + "|" + settlement.id();
		long currentTick = level.getServer().getTickCount();
		CachedPoiTargets cachedTargets = INTERNAL_POI_TARGET_CACHE.get(cacheKey);

		if (cachedTargets != null && currentTick - cachedTargets.tick() <= 20) {
			return cachedTargets.targets();
		}

		List<PathTarget> targets = scannedInternalPoiTargets(level, settlement);
		INTERNAL_POI_TARGET_CACHE.put(cacheKey, new CachedPoiTargets(currentTick, targets));
		return targets;
	}

	private static List<PathTarget> scannedInternalPoiTargets(ServerLevel level, SettlementState settlement) {
		List<PathTarget> targets = new ArrayList<>();
		BlockPos center = settlement.center();
		int radiusSquared = INTERNAL_POI_SCAN_RADIUS_BLOCKS * INTERNAL_POI_SCAN_RADIUS_BLOCKS;
		int minY = Math.max(level.getMinY(), center.getY() - INTERNAL_POI_SCAN_Y_RANGE_BLOCKS);
		int maxY = Math.min(level.getMaxY() - 1, center.getY() + INTERNAL_POI_SCAN_Y_RANGE_BLOCKS);
		BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();

		for (int x = center.getX() - INTERNAL_POI_SCAN_RADIUS_BLOCKS; x <= center.getX() + INTERNAL_POI_SCAN_RADIUS_BLOCKS; x++) {
			for (int z = center.getZ() - INTERNAL_POI_SCAN_RADIUS_BLOCKS; z <= center.getZ() + INTERNAL_POI_SCAN_RADIUS_BLOCKS; z++) {
				if (center.distToCenterSqr(x + 0.5D, center.getY() + 0.5D, z + 0.5D) > radiusSquared) {
					continue;
				}

				if (!level.hasChunkAt(new BlockPos(x, center.getY(), z))) {
					continue;
				}

				for (int y = minY; y <= maxY; y++) {
					scanPos.set(x, y, z);
					BlockState state = level.getBlockState(scanPos);

					if (!isInternalPoi(state)) {
						continue;
					}

					BlockPos poiPos = scanPos.immutable();

					if (state.hasProperty(DoorBlock.FACING) && state.hasProperty(DoorBlock.HALF)) {
						if (state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) {
							continue;
						}

						targets.add(new PathTarget(poiPos.relative(state.getValue(DoorBlock.FACING)), true));
					} else {
						standableAccessTargets(level, poiPos)
							.forEach(target -> targets.add(new PathTarget(target, true)));
					}
				}
			}
		}

		return targets;
	}

	private static boolean isInternalPoi(BlockState state) {
		if (state.is(Blocks.COMPOSTER)
			|| state.is(Blocks.CARTOGRAPHY_TABLE)
			|| state.is(Blocks.STONECUTTER)
			|| state.is(LiveVillagesBlocks.TRADE_BOARD)
			|| state.is(LiveVillagesBlocks.CARPENTER_BENCH)
			|| state.is(LiveVillagesBlocks.FORESTER_TABLE)
			|| state.is(LiveVillagesBlocks.SURVEYOR_TABLE)) {
			return true;
		}

		return state.getBlock() instanceof DoorBlock;
	}

	private static List<BlockPos> standableAccessTargets(ServerLevel level, BlockPos poiPos) {
		List<BlockPos> targets = new ArrayList<>();

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos candidate = poiPos.relative(direction);

			if (isStandable(level, candidate)) {
				targets.add(candidate);
			}
		}

		if (!targets.isEmpty()) {
			return targets;
		}

		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (Math.max(Math.abs(dx), Math.abs(dz)) != 2) {
					continue;
				}

				BlockPos candidate = poiPos.offset(dx, 0, dz);

				if (isStandable(level, candidate)) {
					targets.add(candidate);
				}
			}
		}

		return targets;
	}

	private static List<PathTarget> externalTargets(
		SettlementState settlement,
		Collection<SettlementState> allSettlements,
		List<RouteState> routes
	) {
		List<PathTarget> targets = new ArrayList<>();

		for (RouteState route : routes) {
			String targetId = route.fromSettlementId().equals(settlement.id()) ? route.toSettlementId() : route.fromSettlementId();
			findSettlementById(allSettlements, targetId)
				.filter(target -> target.dimension().equals(settlement.dimension()))
				.map(SettlementState::center)
				.map(pos -> new PathTarget(pos, false))
				.ifPresent(targets::add);
		}

		for (SettlementState candidate : allSettlements) {
			if (candidate.id().equals(settlement.id()) || !candidate.dimension().equals(settlement.dimension())) {
				continue;
			}

			if (candidate.center().distSqr(settlement.center()) <= EXTERNAL_TARGET_RADIUS_BLOCKS * EXTERNAL_TARGET_RADIUS_BLOCKS) {
				targets.add(new PathTarget(candidate.center(), false));
			}
		}

		return targets.stream()
			.distinct()
			.sorted(Comparator.comparingDouble(target -> target.pos().distSqr(settlement.center())))
			.toList();
	}

	private static Optional<SettlementState> findSettlementById(Collection<SettlementState> settlements, String settlementId) {
		return settlements.stream()
			.filter(settlement -> settlement.id().equals(settlementId))
			.findFirst();
	}

	private static Optional<RoadworkDebugPlan> choosePathPlan(
		ServerLevel level,
		SettlementState settlement,
		BlockPos start,
		List<PathTarget> targets,
		boolean internal,
		String targetKind
	) {
		for (PathTarget target : targets) {
			Optional<RoadworkDebugPlan> plan = roadworkPlanForTarget(level, settlement, start, target, internal, targetKind);

			if (plan.isPresent()) {
				return plan;
			}
		}

		return Optional.empty();
	}

	private static Optional<RoadworkDebugPlan> roadworkPlanForTarget(
		ServerLevel level,
		SettlementState settlement,
		BlockPos start,
		PathTarget target,
		boolean internal,
		String targetKind
	) {
		List<BlockPos> path = plannedSurfacePath(level, settlement, start, target.pos(), internal);
		Optional<PathTask> task = firstMissingPathTaskOnPath(level, settlement, path, internal);

		if (task.isEmpty()) {
			return Optional.empty();
		}

		return Optional.of(new RoadworkDebugPlan(
			task.get().workPos(),
			task.get().standPos(),
			task.get().action().name(),
			task.get().uphillDirection(),
			"improving_paths",
			targetKind,
			start.immutable(),
			target.pos().immutable(),
			path.stream().map(BlockPos::immutable).toList(),
			false
		));
	}

	private static Optional<PathTask> firstMissingPathBlock(ServerLevel level, SettlementState settlement, BlockPos start, BlockPos target, boolean internal) {
		List<BlockPos> path = plannedSurfacePath(level, settlement, start, target, internal);
		return firstMissingPathTaskOnPath(level, settlement, path, internal);
	}

	private static Optional<PathTask> firstMissingPathTaskOnPath(ServerLevel level, SettlementState settlement, List<BlockPos> path, boolean internal) {
		if (path.size() <= 1) {
			return Optional.empty();
		}

		int endIndex = internal ? Math.max(1, path.size() - 2) : path.size() - 1;

		for (int index = 1; index <= endIndex; index++) {
			BlockPos previousSurfacePos = path.get(index - 1);
			BlockPos surfacePos = path.get(index);
			BlockState surfaceState = level.getBlockState(surfacePos);

			if (!isRoadSurface(surfaceState)) {
				if (!canConvertToTrail(level, settlement, surfacePos, surfaceState)) {
					continue;
				}

				return Optional.of(trailTask(surfacePos));
			}

			Optional<PathTask> terrainTask = terrainTransitionTask(level, previousSurfacePos, surfacePos);

			if (terrainTask.isPresent()) {
				return terrainTask;
			}
		}

		return Optional.empty();
	}

	private static List<PathTask> pathTasksOnPath(ServerLevel level, SettlementState settlement, List<BlockPos> path, boolean internal, int maxTasks) {
		if (path.size() <= 1 || maxTasks <= 0) {
			return List.of();
		}

		List<PathTask> tasks = new ArrayList<>();
		int endIndex = internal ? Math.max(1, path.size() - 2) : path.size() - 1;

		for (int index = 1; index <= endIndex && tasks.size() < maxTasks; index++) {
			BlockPos previousSurfacePos = path.get(index - 1);
			BlockPos surfacePos = path.get(index);
			BlockState surfaceState = level.getBlockState(surfacePos);

			if (!isRoadSurface(surfaceState)) {
				if (canConvertToTrail(level, settlement, surfacePos, surfaceState)) {
					tasks.add(trailTask(surfacePos));
				}

				continue;
			}

			terrainTransitionTask(level, previousSurfacePos, surfacePos)
				.ifPresent(tasks::add);
		}

		return List.copyOf(tasks);
	}

	private static Optional<RoadworkDebugPlan> existingPathRepairPlan(ServerLevel level, SettlementState settlement, BlockPos center) {
		Optional<PathTask> repairTask = existingPathRepairTask(level, settlement, center);

		if (repairTask.isEmpty()) {
			return Optional.empty();
		}

		return Optional.of(new RoadworkDebugPlan(
			repairTask.get().workPos(),
			repairTask.get().standPos(),
			repairTask.get().action().name(),
			repairTask.get().uphillDirection(),
			"improving_paths",
			"repair",
			center.immutable(),
			repairTask.get().workPos().immutable(),
			List.of(repairTask.get().workPos().immutable()),
			false
		));
	}

	private static Optional<PathTask> existingPathRepairTask(ServerLevel level, SettlementState settlement, BlockPos center) {
		Optional<BlockPos> centerSurface = nearestSurfacePathPos(level, center, ROUTE_ENDPOINT_SEARCH_RADIUS_BLOCKS);

		if (centerSurface.isEmpty()) {
			return Optional.empty();
		}

		List<BlockPos> establishedPathSurfaces = new ArrayList<>();
		int radiusSquared = EXISTING_PATH_REPAIR_RADIUS_BLOCKS * EXISTING_PATH_REPAIR_RADIUS_BLOCKS;

		for (int x = center.getX() - EXISTING_PATH_REPAIR_RADIUS_BLOCKS; x <= center.getX() + EXISTING_PATH_REPAIR_RADIUS_BLOCKS; x++) {
			for (int z = center.getZ() - EXISTING_PATH_REPAIR_RADIUS_BLOCKS; z <= center.getZ() + EXISTING_PATH_REPAIR_RADIUS_BLOCKS; z++) {
				if (center.distToCenterSqr(x + 0.5D, center.getY() + 0.5D, z + 0.5D) > radiusSquared) {
					continue;
				}

				Optional<BlockPos> surfacePos = surfacePathPos(level, x, z);

				if (surfacePos.isEmpty() || !isEstablishedPathSurface(level.getBlockState(surfacePos.get()))) {
					continue;
				}

				establishedPathSurfaces.add(surfacePos.get());
			}
		}

		establishedPathSurfaces.sort(Comparator.comparingDouble(pos -> pos.distSqr(centerSurface.get())));

		for (BlockPos pathSurface : establishedPathSurfaces) {
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				Optional<BlockPos> neighborSurface = surfacePathPos(level, pathSurface.getX() + direction.getStepX(), pathSurface.getZ() + direction.getStepZ());

				if (neighborSurface.isEmpty()) {
					continue;
				}

				BlockState neighborState = level.getBlockState(neighborSurface.get());

				if (neighborState.is(Blocks.LEAF_LITTER)) {
					return Optional.of(collectLeafLitterTask(neighborSurface.get()));
				}

				BlockState aboveNeighborState = level.getBlockState(neighborSurface.get().above());

				if (aboveNeighborState.is(Blocks.LEAF_LITTER) && (isEstablishedPathSurface(neighborState) || canConvertToTrail(level, settlement, neighborSurface.get(), neighborState))) {
					return Optional.of(collectLeafLitterTask(neighborSurface.get().above()));
				}

				if (!isEstablishedPathSurface(neighborState)) {
					continue;
				}
			}
		}

		return Optional.empty();
	}

	private static Optional<PathTask> terrainTransitionTask(ServerLevel level, BlockPos firstSurfacePos, BlockPos secondSurfacePos) {
		int yDelta = secondSurfacePos.getY() - firstSurfacePos.getY();
		int heightChange = Math.abs(yDelta);

		if (heightChange == 0) {
			return Optional.empty();
		}

		BlockPos lowerSurfacePos = yDelta > 0 ? firstSurfacePos : secondSurfacePos;
		BlockPos higherSurfacePos = yDelta > 0 ? secondSurfacePos : firstSurfacePos;
		Optional<Direction> uphillDirection = horizontalDirectionBetween(lowerSurfacePos, higherSurfacePos);

		if (uphillDirection.isEmpty()) {
			return Optional.empty();
		}

		if (heightChange > 1) {
			if (heightChange > MAX_RESHAPE_STEP_BLOCKS || !canRaiseLowerRoadSurface(level, lowerSurfacePos)) {
				return Optional.empty();
			}

			return Optional.of(new PathTask(lowerSurfacePos, lowerSurfacePos.above(), RoadworkAction.RAISE_SURFACE, uphillDirection.get()));
		}

		if (!needsSlopeTreatment(level, lowerSurfacePos, higherSurfacePos)) {
			return Optional.empty();
		}

		return Optional.of(new PathTask(lowerSurfacePos, lowerSurfacePos.above(), RoadworkAction.PLACE_STAIR, uphillDirection.get()));
	}

	private static List<BlockPos> plannedSurfacePath(ServerLevel level, SettlementState settlement, BlockPos start, BlockPos target, boolean internal) {
		long tick = level.getServer().getTickCount();
		RoadworkPathCacheKey cacheKey = new RoadworkPathCacheKey(settlement.dimension().identifier().toString(), settlement.id(), start, target, internal);
		CachedPlannedPath cachedPath = ROADWORK_PATH_CACHE.get(cacheKey);

		if (cachedPath != null && tick - cachedPath.tick() <= ROADWORK_PATH_CACHE_TICKS) {
			return cachedPath.path();
		}

		Optional<BlockPos> startSurface = nearestSurfacePathPos(level, start, ROUTE_ENDPOINT_SEARCH_RADIUS_BLOCKS);
		Optional<BlockPos> targetSurface = nearestSurfacePathPos(level, target, internal ? ROUTE_ENDPOINT_SEARCH_RADIUS_BLOCKS : 2);

		if (startSurface.isEmpty() || targetSurface.isEmpty()) {
			cachePlannedSurfacePath(cacheKey, tick, List.of());
			return List.of();
		}

		BlockPos routeTarget = routeSearchTarget(level, startSurface.get(), targetSurface.get(), internal);
		ColumnKey targetKey = new ColumnKey(routeTarget.getX(), routeTarget.getZ());
		PriorityQueue<PathSearchNode> open = new PriorityQueue<>(Comparator.comparingDouble(PathSearchNode::priority));
		Map<ColumnKey, PathSearchVisit> visits = new HashMap<>();
		ColumnKey startKey = new ColumnKey(startSurface.get().getX(), startSurface.get().getZ());
		int margin = internal ? INTERNAL_PATH_SEARCH_MARGIN_BLOCKS : EXTERNAL_PATH_SEARCH_SPAN_BLOCKS / 3;
		int minX = Math.min(startSurface.get().getX(), routeTarget.getX()) - margin;
		int maxX = Math.max(startSurface.get().getX(), routeTarget.getX()) + margin;
		int minZ = Math.min(startSurface.get().getZ(), routeTarget.getZ()) - margin;
		int maxZ = Math.max(startSurface.get().getZ(), routeTarget.getZ()) + margin;
		int maxNodes = internal ? MAX_INTERNAL_PATH_SEARCH_NODES : MAX_EXTERNAL_PATH_SEARCH_NODES;

		open.add(new PathSearchNode(startKey, startSurface.get(), 0.0D, heuristic(startSurface.get(), routeTarget)));
		visits.put(startKey, new PathSearchVisit(startSurface.get(), 0.0D, null));

		int searchedNodes = 0;

		while (!open.isEmpty() && searchedNodes < maxNodes) {
			PathSearchNode current = open.poll();
			PathSearchVisit currentVisit = visits.get(current.key());

			if (currentVisit == null || current.cost() > currentVisit.cost()) {
				continue;
			}

			if (current.key().equals(targetKey)) {
				List<BlockPos> path = reconstructPath(visits, current.key());
				cachePlannedSurfacePath(cacheKey, tick, path);
				return path;
			}

			searchedNodes++;

			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dz == 0) {
						continue;
					}

					int nextX = current.surfacePos().getX() + dx;
					int nextZ = current.surfacePos().getZ() + dz;

					if (nextX < minX || nextX > maxX || nextZ < minZ || nextZ > maxZ) {
						continue;
					}

					Optional<BlockPos> nextSurface = surfacePathPos(level, nextX, nextZ);

					if (nextSurface.isEmpty()) {
						continue;
					}

					double stepCost = pathStepCost(level, settlement, current.surfacePos(), nextSurface.get(), dx != 0 && dz != 0, internal);

					if (!Double.isFinite(stepCost)) {
						continue;
					}

					ColumnKey nextKey = new ColumnKey(nextSurface.get().getX(), nextSurface.get().getZ());
					double nextCost = currentVisit.cost() + stepCost;
					PathSearchVisit previousVisit = visits.get(nextKey);

					if (previousVisit != null && previousVisit.cost() <= nextCost) {
						continue;
					}

					visits.put(nextKey, new PathSearchVisit(nextSurface.get(), nextCost, current.key()));
					open.add(new PathSearchNode(nextKey, nextSurface.get(), nextCost, nextCost + heuristic(nextSurface.get(), routeTarget)));
				}
			}
		}

		cachePlannedSurfacePath(cacheKey, tick, List.of());
		return List.of();
	}

	private static void cachePlannedSurfacePath(RoadworkPathCacheKey cacheKey, long tick, List<BlockPos> path) {
		ROADWORK_PATH_CACHE.put(cacheKey, new CachedPlannedPath(tick, path));

		if (ROADWORK_PATH_CACHE.size() <= 512) {
			return;
		}

		ROADWORK_PATH_CACHE.entrySet().removeIf(entry -> tick - entry.getValue().tick() > ROADWORK_PATH_CACHE_TICKS);
	}

	private static BlockPos routeSearchTarget(ServerLevel level, BlockPos startSurface, BlockPos targetSurface, boolean internal) {
		if (internal) {
			return targetSurface;
		}

		int dx = targetSurface.getX() - startSurface.getX();
		int dz = targetSurface.getZ() - startSurface.getZ();
		int steps = Math.max(Math.abs(dx), Math.abs(dz));

		if (steps <= EXTERNAL_PATH_SEARCH_SPAN_BLOCKS) {
			return targetSurface;
		}

		double t = EXTERNAL_PATH_SEARCH_SPAN_BLOCKS / (double) steps;
		int x = (int) Math.round(startSurface.getX() + dx * t);
		int z = (int) Math.round(startSurface.getZ() + dz * t);
		return surfacePathPos(level, x, z).orElse(targetSurface);
	}

	private static Optional<BlockPos> nearestSurfacePathPos(ServerLevel level, BlockPos center, int searchRadius) {
		Optional<BlockPos> exactSurface = surfacePathPos(level, center.getX(), center.getZ());

		if (exactSurface.isPresent()) {
			return exactSurface;
		}

		BlockPos bestSurface = null;
		double bestDistanceSquared = Double.MAX_VALUE;

		for (int radius = 1; radius <= searchRadius; radius++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
						continue;
					}

					Optional<BlockPos> surface = surfacePathPos(level, center.getX() + dx, center.getZ() + dz);

					if (surface.isEmpty()) {
						continue;
					}

					double distanceSquared = surface.get().distSqr(center);

					if (distanceSquared < bestDistanceSquared) {
						bestSurface = surface.get();
						bestDistanceSquared = distanceSquared;
					}
				}
			}

			if (bestSurface != null) {
				return Optional.of(bestSurface);
			}
		}

		return Optional.empty();
	}

	private static double pathStepCost(ServerLevel level, SettlementState settlement, BlockPos fromSurface, BlockPos toSurface, boolean diagonal, boolean internal) {
		int yDelta = Math.abs(toSurface.getY() - fromSurface.getY());

		if (diagonal && yDelta > 0) {
			return Double.POSITIVE_INFINITY;
		}

		if (yDelta > 1 && (!internal || yDelta > MAX_RESHAPE_STEP_BLOCKS || !canRaiseLowerRoadSurface(level, lowerSurface(fromSurface, toSurface)))) {
			return Double.POSITIVE_INFINITY;
		}

		BlockState surfaceState = level.getBlockState(toSurface);
		BlockState aboveState = level.getBlockState(toSurface.above());
		boolean roadSurface = isRoadSurface(surfaceState);

		if (!roadSurface && !canConvertToTrail(level, settlement, toSurface, surfaceState)) {
			return Double.POSITIVE_INFINITY;
		}

		if (roadSurface && !aboveState.isAir() && !aboveState.canBeReplaced()) {
			return Double.POSITIVE_INFINITY;
		}

		double cost = diagonal ? 1.4D : 1.0D;

		if (yDelta > 1) {
			cost += yDelta * 6.0D;
		} else {
			cost += yDelta * 0.75D;
		}

		if (roadSurface) {
			cost *= 0.3D;
		}

		return cost;
	}

	private static BlockPos lowerSurface(BlockPos firstSurfacePos, BlockPos secondSurfacePos) {
		return firstSurfacePos.getY() <= secondSurfacePos.getY() ? firstSurfacePos : secondSurfacePos;
	}

	private static double heuristic(BlockPos pos, BlockPos target) {
		int dx = Math.abs(target.getX() - pos.getX());
		int dz = Math.abs(target.getZ() - pos.getZ());
		return Math.max(dx, dz);
	}

	private static List<BlockPos> reconstructPath(Map<ColumnKey, PathSearchVisit> visits, ColumnKey endKey) {
		List<BlockPos> path = new ArrayList<>();
		ColumnKey cursor = endKey;

		while (cursor != null) {
			PathSearchVisit visit = visits.get(cursor);

			if (visit == null) {
				break;
			}

			path.add(visit.surfacePos());
			cursor = visit.previous();
		}

		Collections.reverse(path);
		return path;
	}

	private static Optional<BlockPos> surfacePathPos(ServerLevel level, int x, int z) {
		BlockPos columnPos = new BlockPos(x, level.getMinY(), z);

		if (!level.hasChunkAt(columnPos)) {
			return Optional.empty();
		}

		int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		int minY = Math.max(level.getMinY(), topY - SURFACE_SEARCH_DEPTH_BLOCKS);

		for (int y = topY; y >= minY; y--) {
			if (y < level.getMinY() || y > level.getMaxY() - 1) {
				continue;
			}

			BlockPos surfacePos = new BlockPos(x, y, z);
			BlockState surfaceState = level.getBlockState(surfacePos);

			if (isUsablePathSurface(level, surfacePos, surfaceState)) {
				return Optional.of(surfacePos);
			}
		}

		return Optional.empty();
	}

	private static boolean isUsablePathSurface(ServerLevel level, BlockPos surfacePos, BlockState surfaceState) {
		BlockState aboveState = level.getBlockState(surfacePos.above());

		if (!aboveState.isAir() && !aboveState.canBeReplaced()) {
			return false;
		}

		return isRoadSurface(surfaceState) || canConvertToTrail(level, null, surfacePos, surfaceState);
	}

	private static boolean canConvertToTrail(ServerLevel level, SettlementState settlement, BlockPos surfacePos, BlockState surfaceState) {
		BlockState aboveState = level.getBlockState(surfacePos.above());
		return (aboveState.isAir() || aboveState.canBeReplaced())
			&& SettlementGreenspace.canConvertToPath(level, settlement, surfacePos)
			&& (surfaceState.is(Blocks.GRASS_BLOCK)
				|| surfaceState.is(Blocks.DIRT)
				|| surfaceState.is(Blocks.COARSE_DIRT)
				|| surfaceState.is(Blocks.ROOTED_DIRT)
				|| surfaceState.is(Blocks.PODZOL)
				|| surfaceState.is(Blocks.MYCELIUM)
				|| surfaceState.is(BlockTags.DIRT));
	}

	private static PathTask trailTask(BlockPos surfacePos) {
		return new PathTask(surfacePos, surfacePos.above(), RoadworkAction.PLACE_TRAIL, Direction.NORTH);
	}

	private static PathTask collectLeafLitterTask(BlockPos leafLitterPos) {
		return new PathTask(leafLitterPos, leafLitterPos, RoadworkAction.COLLECT_LEAF_LITTER, Direction.NORTH);
	}

	private static boolean performRoadwork(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, PathTask task) {
		return switch (task.action()) {
			case PLACE_TRAIL -> placeTrail(level, settlement, stock, task.workPos());
			case COLLECT_LEAF_LITTER -> collectLeafLitter(level, stock, task.workPos());
			case RAISE_SURFACE -> raiseLowerRoadSurface(level, task.workPos());
			case PLACE_STAIR -> placeSlopeStair(level, task.workPos(), task.uphillDirection());
		};
	}

	private static boolean placeTrail(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, BlockPos surfacePos) {
		BlockState surfaceState = level.getBlockState(surfacePos);

		if (!canConvertToTrail(level, settlement, surfacePos, surfaceState)) {
			return false;
		}

		BlockPos abovePos = surfacePos.above();
		BlockState aboveState = level.getBlockState(abovePos);

		if (aboveState.is(Blocks.LEAF_LITTER)) {
			addGoods(stock, "leaf_litter", 1);
		}

		if (!aboveState.isAir()) {
			level.setBlock(abovePos, Blocks.AIR.defaultBlockState(), BLOCK_UPDATE_FLAGS);
		}

		level.setBlock(surfacePos, Blocks.DIRT_PATH.defaultBlockState(), BLOCK_UPDATE_FLAGS);
		return true;
	}

	private static boolean collectLeafLitter(ServerLevel level, Map<String, Integer> stock, BlockPos leafLitterPos) {
		if (!level.getBlockState(leafLitterPos).is(Blocks.LEAF_LITTER)) {
			return false;
		}

		level.setBlock(leafLitterPos, Blocks.AIR.defaultBlockState(), BLOCK_UPDATE_FLAGS);
		addGoods(stock, "leaf_litter", 1);
		return true;
	}

	private static void addGoods(Map<String, Integer> stock, String goodsKey, int amount) {
		stock.merge(goodsKey, amount, Integer::sum);
	}

	private static boolean raiseLowerRoadSurface(ServerLevel level, BlockPos lowerSurfacePos) {
		if (!canRaiseLowerRoadSurface(level, lowerSurfacePos)) {
			return false;
		}

		BlockPos fillPos = lowerSurfacePos.above();
		BlockPos headPos = fillPos.above();

		if (!level.getBlockState(headPos).isAir()) {
			level.setBlock(headPos, Blocks.AIR.defaultBlockState(), BLOCK_UPDATE_FLAGS);
		}

		level.setBlock(fillPos, roadFillStateNear(level, lowerSurfacePos), BLOCK_UPDATE_FLAGS);
		return true;
	}

	private static boolean canRaiseLowerRoadSurface(ServerLevel level, BlockPos lowerSurfacePos) {
		BlockState lowerSurfaceState = level.getBlockState(lowerSurfacePos);

		if (lowerSurfaceState.isAir() || !lowerSurfaceState.getFluidState().isEmpty()) {
			return false;
		}

		BlockState fillState = level.getBlockState(lowerSurfacePos.above());
		BlockState headState = level.getBlockState(lowerSurfacePos.above(2));

		return (fillState.isAir() || fillState.canBeReplaced())
			&& (headState.isAir() || headState.canBeReplaced());
	}

	private static BlockState roadFillStateNear(ServerLevel level, BlockPos lowerSurfacePos) {
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			Optional<BlockPos> neighborSurface = surfacePathPos(level, lowerSurfacePos.getX() + direction.getStepX(), lowerSurfacePos.getZ() + direction.getStepZ());

			if (neighborSurface.isEmpty()) {
				continue;
			}

			BlockState neighborState = level.getBlockState(neighborSurface.get());

			if (neighborState.is(Blocks.COBBLESTONE)
				|| neighborState.is(Blocks.COBBLESTONE_STAIRS)
				|| neighborState.is(Blocks.COBBLESTONE_SLAB)
				|| neighborState.is(Blocks.STONE)
				|| neighborState.is(Blocks.STONE_STAIRS)
				|| neighborState.is(Blocks.STONE_SLAB)) {
				return Blocks.COBBLESTONE.defaultBlockState();
			}
		}

		return Blocks.DIRT.defaultBlockState();
	}

	private static boolean placeSlopeStair(ServerLevel level, BlockPos lowerSurfacePos, Direction uphillDirection) {
		if (!needsSlopeTreatment(level, lowerSurfacePos)) {
			return false;
		}

		BlockPos stairPos = lowerSurfacePos.above();
		BlockPos headPos = stairPos.above();
		BlockState headState = level.getBlockState(headPos);

		if (!headState.isAir() && !headState.canBeReplaced()) {
			return false;
		}

		if (!headState.isAir()) {
			level.setBlock(headPos, Blocks.AIR.defaultBlockState(), BLOCK_UPDATE_FLAGS);
		}

		level.setBlock(stairPos, roadStairStateNear(level, lowerSurfacePos).setValue(StairBlock.FACING, uphillDirection), BLOCK_UPDATE_FLAGS);
		return true;
	}

	private static boolean needsSlopeTreatment(ServerLevel level, BlockPos lowerSurfacePos, BlockPos higherSurfacePos) {
		return needsSlopeTreatment(level, lowerSurfacePos)
			&& !isRoadSlope(level.getBlockState(higherSurfacePos))
			&& !isRoadSlope(level.getBlockState(higherSurfacePos.above()));
	}

	private static boolean needsSlopeTreatment(ServerLevel level, BlockPos lowerSurfacePos) {
		BlockState lowerSurfaceState = level.getBlockState(lowerSurfacePos);
		BlockState stairPosState = level.getBlockState(lowerSurfacePos.above());

		return !isRoadSlope(lowerSurfaceState)
			&& !isRoadSlope(stairPosState)
			&& (stairPosState.isAir() || stairPosState.canBeReplaced());
	}

	private static BlockState roadStairStateNear(ServerLevel level, BlockPos lowerSurfacePos) {
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			Optional<BlockPos> neighborSurface = surfacePathPos(level, lowerSurfacePos.getX() + direction.getStepX(), lowerSurfacePos.getZ() + direction.getStepZ());

			if (neighborSurface.isEmpty()) {
				continue;
			}

			BlockState neighborState = level.getBlockState(neighborSurface.get());

			if (neighborState.is(Blocks.STONE_BRICKS) || neighborState.is(Blocks.STONE_BRICK_STAIRS) || neighborState.is(Blocks.STONE_BRICK_SLAB)) {
				return Blocks.STONE_BRICK_STAIRS.defaultBlockState();
			}

			if (neighborState.is(Blocks.BRICKS) || neighborState.is(Blocks.BRICK_STAIRS) || neighborState.is(Blocks.BRICK_SLAB)) {
				return Blocks.BRICK_STAIRS.defaultBlockState();
			}

			if (neighborState.is(Blocks.COBBLESTONE) || neighborState.is(Blocks.COBBLESTONE_STAIRS) || neighborState.is(Blocks.COBBLESTONE_SLAB)) {
				return Blocks.COBBLESTONE_STAIRS.defaultBlockState();
			}

			if (neighborState.is(Blocks.STONE) || neighborState.is(Blocks.STONE_STAIRS) || neighborState.is(Blocks.STONE_SLAB) || neighborState.is(Blocks.SMOOTH_STONE) || neighborState.is(Blocks.SMOOTH_STONE_SLAB)) {
				return Blocks.STONE_STAIRS.defaultBlockState();
			}
		}

		return Blocks.OAK_STAIRS.defaultBlockState();
	}

	private static boolean isRoadSlope(BlockState state) {
		return isRoadStairOrSlab(state);
	}

	private static boolean isRoadSurface(BlockState state) {
		return state.is(Blocks.DIRT_PATH)
			|| state.is(Blocks.GRAVEL)
			|| state.is(Blocks.COBBLESTONE)
			|| state.is(Blocks.STONE)
			|| state.is(Blocks.SMOOTH_STONE)
			|| state.is(Blocks.GRANITE)
			|| state.is(Blocks.POLISHED_GRANITE)
			|| state.is(Blocks.DIORITE)
			|| state.is(Blocks.POLISHED_DIORITE)
			|| state.is(Blocks.ANDESITE)
			|| state.is(Blocks.POLISHED_ANDESITE)
			|| state.is(Blocks.STONE_BRICKS)
			|| state.is(Blocks.BRICKS)
			|| isRoadStairOrSlab(state);
	}

	private static boolean isEstablishedPathSurface(BlockState state) {
		return state.is(Blocks.DIRT_PATH)
			|| state.is(Blocks.GRAVEL)
			|| state.is(Blocks.COBBLESTONE)
			|| state.is(Blocks.COBBLESTONE_STAIRS)
			|| state.is(Blocks.COBBLESTONE_SLAB)
			|| state.is(Blocks.STONE_BRICKS)
			|| state.is(Blocks.STONE_BRICK_STAIRS)
			|| state.is(Blocks.STONE_BRICK_SLAB)
			|| state.is(Blocks.SMOOTH_STONE)
			|| state.is(Blocks.SMOOTH_STONE_SLAB);
	}

	private static boolean isRoadStairOrSlab(BlockState state) {
		return state.is(Blocks.COBBLESTONE_STAIRS)
			|| state.is(Blocks.COBBLESTONE_SLAB)
			|| state.is(Blocks.STONE_STAIRS)
			|| state.is(Blocks.STONE_SLAB)
			|| state.is(Blocks.STONE_BRICK_STAIRS)
			|| state.is(Blocks.STONE_BRICK_SLAB)
			|| state.is(Blocks.BRICK_STAIRS)
			|| state.is(Blocks.BRICK_SLAB)
			|| state.is(Blocks.SMOOTH_STONE_SLAB);
	}

	private static Optional<Direction> horizontalDirectionBetween(BlockPos from, BlockPos to) {
		int dx = Integer.compare(to.getX(), from.getX());
		int dz = Integer.compare(to.getZ(), from.getZ());

		if (dx != 0 && dz != 0) {
			return Optional.empty();
		}

		if (dx > 0) {
			return Optional.of(Direction.EAST);
		}

		if (dx < 0) {
			return Optional.of(Direction.WEST);
		}

		if (dz > 0) {
			return Optional.of(Direction.SOUTH);
		}

		if (dz < 0) {
			return Optional.of(Direction.NORTH);
		}

		return Optional.empty();
	}

	private static boolean isStandable(ServerLevel level, BlockPos pos) {
		BlockState footState = level.getBlockState(pos);
		BlockState headState = level.getBlockState(pos.above());
		BlockState belowState = level.getBlockState(pos.below());
		return footState.isAir() && headState.isAir() && !belowState.isAir();
	}

	private static void showRoadwrightTool(Villager roadwright) {
		ItemStack held = roadwright.getMainHandItem();

		if (held.isEmpty()) {
			roadwright.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SHOVEL));
		}
	}

	private static void steerRoadwrightTowardTask(Villager roadwright, BlockPos standPos) {
		roadwright.getNavigation().moveTo(standPos.getX() + 0.5D, standPos.getY(), standPos.getZ() + 0.5D, ROADWORK_SPEED);
	}

	private static boolean isWithinWorkReach(Villager roadwright, BlockPos surfacePos) {
		return roadwright.distanceToSqr(surfacePos.getX() + 0.5D, surfacePos.getY() + 1.0D, surfacePos.getZ() + 0.5D) <= ROADWORK_REACH_DISTANCE_SQUARED;
	}

	public record RoadworkResult(Set<String> busyRoadwrightIds, boolean worldChanged) {
		public static RoadworkResult unchanged() {
			return new RoadworkResult(Set.of(), false);
		}
	}

	public record RoadworkCatchupResult(boolean worldChanged, int tasksApplied) {
		public static RoadworkCatchupResult none() {
			return new RoadworkCatchupResult(false, 0);
		}
	}

	public record SurveyorMapForecast(List<BlockPos> routeTraceBlocks, List<BlockPos> plannedWorkBlocks) {
		public SurveyorMapForecast {
			routeTraceBlocks = List.copyOf(routeTraceBlocks);
			plannedWorkBlocks = List.copyOf(plannedWorkBlocks);
		}

		public static SurveyorMapForecast empty() {
			return new SurveyorMapForecast(List.of(), List.of());
		}
	}

	private record PathTarget(BlockPos pos, boolean internal) {
	}

	private record CachedPoiTargets(long tick, List<PathTarget> targets) {
	}

	private record CachedMilepostTargets(long tick, List<BlockPos> positions) {
	}

	private record CachedRoadworkPlan(Optional<RoadworkDebugPlan> plan, long tick) {
	}

	private record CachedPlannedPath(long tick, List<BlockPos> path) {
		private CachedPlannedPath {
			path = List.copyOf(path);
		}
	}

	private record ForecastRoutePlan(String targetKind, BlockPos startPos, BlockPos targetPos, List<BlockPos> routeTrace, List<PathTask> tasks) {
		private ForecastRoutePlan {
			startPos = startPos.immutable();
			targetPos = targetPos.immutable();
			routeTrace = List.copyOf(routeTrace);
			tasks = List.copyOf(tasks);
		}
	}

	private record ForecastRouteKey(String targetKind, BlockPos startPos, BlockPos targetPos) {
		private ForecastRouteKey {
			startPos = startPos.immutable();
			targetPos = targetPos.immutable();
		}
	}

	private record RoadworkPathCacheKey(String dimensionId, String settlementId, BlockPos startPos, BlockPos targetPos, boolean internal) {
		private RoadworkPathCacheKey {
			startPos = startPos.immutable();
			targetPos = targetPos.immutable();
		}
	}

	private enum RoadworkAction {
		PLACE_TRAIL,
		COLLECT_LEAF_LITTER,
		RAISE_SURFACE,
		PLACE_STAIR
	}

	private record PathTask(BlockPos workPos, BlockPos standPos, RoadworkAction action, Direction uphillDirection) {
	}

	public record RoadworkDebugPlan(
		BlockPos workPos,
		BlockPos standPos,
		String actionKey,
		Direction uphillDirection,
		String taskKey,
		String targetKind,
		BlockPos startPos,
		BlockPos targetPos,
		List<BlockPos> routeTrace,
		boolean cached
	) {
		public static final Codec<RoadworkDebugPlan> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BlockPos.CODEC.fieldOf("work_pos").forGetter(RoadworkDebugPlan::workPos),
			BlockPos.CODEC.fieldOf("stand_pos").forGetter(RoadworkDebugPlan::standPos),
			Codec.STRING.optionalFieldOf("action_key", "").forGetter(RoadworkDebugPlan::actionKey),
			Direction.CODEC.optionalFieldOf("uphill_direction").forGetter(plan -> Optional.ofNullable(plan.uphillDirection())),
			Codec.STRING.optionalFieldOf("task_key", "").forGetter(RoadworkDebugPlan::taskKey),
			Codec.STRING.optionalFieldOf("target_kind", "").forGetter(RoadworkDebugPlan::targetKind),
			BlockPos.CODEC.fieldOf("start_pos").forGetter(RoadworkDebugPlan::startPos),
			BlockPos.CODEC.fieldOf("target_pos").forGetter(RoadworkDebugPlan::targetPos),
			BlockPos.CODEC.listOf().optionalFieldOf("route_trace", List.of()).forGetter(RoadworkDebugPlan::routeTrace),
			Codec.BOOL.optionalFieldOf("cached", false).forGetter(RoadworkDebugPlan::cached)
		).apply(instance, (workPos, standPos, actionKey, uphillDirection, taskKey, targetKind, startPos, targetPos, routeTrace, cached) ->
			new RoadworkDebugPlan(workPos, standPos, actionKey, uphillDirection.orElse(null), taskKey, targetKind, startPos, targetPos, routeTrace, cached)
		));

		public RoadworkDebugPlan {
			workPos = workPos.immutable();
			standPos = standPos.immutable();
			startPos = startPos.immutable();
			targetPos = targetPos.immutable();
			routeTrace = List.copyOf(routeTrace);
		}

		private PathTask toPathTask() {
			return new PathTask(workPos, standPos, RoadworkAction.valueOf(actionKey), uphillDirection);
		}

		private RoadworkDebugPlan asCached() {
			return new RoadworkDebugPlan(workPos, standPos, actionKey, uphillDirection, taskKey, targetKind, startPos, targetPos, routeTrace, true);
		}
	}

	private record ColumnKey(int x, int z) {
	}

	private record PathSearchNode(ColumnKey key, BlockPos surfacePos, double cost, double priority) {
	}

	private record PathSearchVisit(BlockPos surfacePos, double cost, ColumnKey previous) {
	}
}
