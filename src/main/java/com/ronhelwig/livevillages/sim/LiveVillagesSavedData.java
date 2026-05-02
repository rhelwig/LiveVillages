package com.ronhelwig.livevillages.sim;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.core.Holder;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.tags.BiomeTags;

import com.ronhelwig.livevillages.LiveVillages;
import com.ronhelwig.livevillages.LiveVillagesGameRules;
import com.ronhelwig.livevillages.block.entity.TradeBoardBlockEntity;

public class LiveVillagesSavedData extends SavedData {
	private static final long CUSTOM_SETTLEMENT_BOOTSTRAP_DELAY_TICKS = (long) SettlementEconomyRules.TICKS_PER_DAY;
	private static final long CUSTOM_SETTLEMENT_BOOTSTRAP_RETRY_TICKS = 6_000L;
	private static final int CUSTOM_SETTLEMENT_VILLAGER_RADIUS_BLOCKS = 32;
	private static final int CUSTOM_SETTLEMENT_SPAWN_SEARCH_RADIUS = 6;
	public static final int SHARED_MAP_SAMPLE_STRIDE_BLOCKS = 4;
	public static final long SURVEY_CACHE_DURATION_TICKS = 2_000L; // Cache survey for 100 seconds
	private static final long DEFAULT_LOADED_ROADWORK_CATCHUP_TICKS = (long) SettlementEconomyRules.TICKS_PER_DAY;
	private static final int SHARED_MAP_PLAYER_OBSERVATION_RADIUS_BLOCKS = 32;
	private static final int SHARED_MAP_MAX_SAMPLES_PER_UPDATE = 1_200;
	private static final int SHARED_MAP_REFRESH_TICKS = 600;
	private static final int SHARED_MAP_MAX_CELLS_PER_DIMENSION = 40_000;
	private static final Codec<LiveVillagesSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.unboundedMap(Codec.STRING, SettlementState.CODEC).optionalFieldOf("settlements", Map.of()).forGetter(data -> data.settlements),
		Codec.unboundedMap(Codec.STRING, RouteState.CODEC).optionalFieldOf("routes", Map.of()).forGetter(data -> data.routes),
		Codec.unboundedMap(Codec.STRING, SettlementBuildSite.CODEC).optionalFieldOf("build_sites", Map.of()).forGetter(data -> data.buildSites),
		Codec.unboundedMap(Codec.STRING, SettlementConstructionDelivery.CODEC).optionalFieldOf("construction_deliveries", Map.of()).forGetter(data -> data.constructionDeliveries),
		Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("bootstrap_villager_spawn_ticks", Map.of()).forGetter(data -> data.bootstrapVillagerSpawnTicks),
		Codec.unboundedMap(Codec.STRING, SharedTerrainCell.CODEC).optionalFieldOf("shared_terrain_knowledge", Map.of()).forGetter(data -> data.sharedTerrainKnowledge),
		Codec.unboundedMap(Codec.STRING, SettlementLoadedObservation.SurveyorObservation.CODEC).optionalFieldOf("saved_surveyor_observations", Map.of()).forGetter(data -> data.savedSurveyorObservations),
		Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING, SettlementRoadwrightWork.RoadworkDebugPlan.CODEC)).optionalFieldOf("saved_roadwork_plans", Map.of()).forGetter(data -> data.savedRoadworkPlans),
		Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("loaded_roadwork_catchup_ticks", Map.of()).forGetter(data -> data.loadedRoadworkCatchupTicks),
		Codec.INT.optionalFieldOf("next_region_cursor", 0).forGetter(data -> data.nextRegionCursor),
		Codec.LONG.optionalFieldOf("last_global_update_tick", 0L).forGetter(data -> data.lastGlobalUpdateTick),
		VillageScanCursor.CODEC.optionalFieldOf("village_scan_cursor").forGetter(data -> Optional.ofNullable(data.villageScanCursor))
	).apply(instance, LiveVillagesSavedData::new));
	public static final SavedDataType<LiveVillagesSavedData> TYPE = new SavedDataType<>(
		LiveVillages.id("economy_state"),
		LiveVillagesSavedData::new,
		CODEC,
		null
	);

	private final LinkedHashMap<String, SettlementState> settlements;
	private final LinkedHashMap<String, RouteState> routes;
	private final LinkedHashMap<String, SettlementBuildSite> buildSites;
	private final LinkedHashMap<String, SettlementConstructionDelivery> constructionDeliveries;
	private final LinkedHashMap<String, Long> bootstrapVillagerSpawnTicks;
	private final LinkedHashMap<String, SharedTerrainCell> sharedTerrainKnowledge;
	private final LinkedHashMap<String, SettlementLoadedObservation.SurveyorObservation> savedSurveyorObservations;
	private final LinkedHashMap<String, Map<String, SettlementRoadwrightWork.RoadworkDebugPlan>> savedRoadworkPlans;
	private final LinkedHashMap<String, Long> loadedRoadworkCatchupTicks;
	private int nextRegionCursor;
	private long lastGlobalUpdateTick;
	private VillageScanCursor villageScanCursor;
	public final Map<String, CachedSurvey> surveyCache = new HashMap<>();

	public record CachedSurvey(SettlementConstruction.InfrastructureSurvey survey, long lastSurveyTick) {
	}

	public record SharedTerrainCell(String terrainCode, long lastObservedTick) {
		public static final Codec<SharedTerrainCell> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.optionalFieldOf("terrain_code", "U").forGetter(SharedTerrainCell::terrainCode),
			Codec.LONG.optionalFieldOf("last_observed_tick", 0L).forGetter(SharedTerrainCell::lastObservedTick)
		).apply(instance, SharedTerrainCell::new));

		public SharedTerrainCell {
			terrainCode = terrainCode == null || terrainCode.isBlank() ? "U" : terrainCode.substring(0, 1);
		}

		public char terrain() {
			return terrainCode.charAt(0);
		}
	}

	public LiveVillagesSavedData() {
		this(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), 0, 0L, Optional.empty());
	}

	private LiveVillagesSavedData(
		Map<String, SettlementState> settlements,
		Map<String, RouteState> routes,
		Map<String, SettlementBuildSite> buildSites,
		Map<String, SettlementConstructionDelivery> constructionDeliveries,
		Map<String, Long> bootstrapVillagerSpawnTicks,
		Map<String, SharedTerrainCell> sharedTerrainKnowledge,
		Map<String, SettlementLoadedObservation.SurveyorObservation> savedSurveyorObservations,
		Map<String, Map<String, SettlementRoadwrightWork.RoadworkDebugPlan>> savedRoadworkPlans,
		Map<String, Long> loadedRoadworkCatchupTicks,
		int nextRegionCursor,
		long lastGlobalUpdateTick,
		Optional<VillageScanCursor> villageScanCursor
	) {
		this.settlements = new LinkedHashMap<>(settlements);
		this.routes = new LinkedHashMap<>(routes);
		this.buildSites = new LinkedHashMap<>(buildSites);
		this.constructionDeliveries = new LinkedHashMap<>(constructionDeliveries);
		this.bootstrapVillagerSpawnTicks = new LinkedHashMap<>(bootstrapVillagerSpawnTicks);
		this.sharedTerrainKnowledge = new LinkedHashMap<>(sharedTerrainKnowledge);
		this.savedSurveyorObservations = new LinkedHashMap<>(savedSurveyorObservations);
		this.savedRoadworkPlans = new LinkedHashMap<>();
		savedRoadworkPlans.forEach((settlementId, plans) -> this.savedRoadworkPlans.put(settlementId, new LinkedHashMap<>(plans)));
		this.loadedRoadworkCatchupTicks = new LinkedHashMap<>(loadedRoadworkCatchupTicks);
		this.nextRegionCursor = nextRegionCursor;
		this.lastGlobalUpdateTick = lastGlobalUpdateTick;
		this.villageScanCursor = villageScanCursor.orElse(null);
	}

	public static LiveVillagesSavedData get(MinecraftServer server) {
		ServerLevel overworld = Objects.requireNonNull(server.getLevel(Level.OVERWORLD), "Overworld was not available");
		return overworld.getDataStorage().computeIfAbsent(TYPE);
	}

	public Optional<SettlementState> getSettlement(String id) {
		return Optional.ofNullable(settlements.get(id));
	}

	public Collection<SettlementState> getSettlements() {
		return Collections.unmodifiableCollection(settlements.values());
	}

	public Collection<RouteState> getRoutes() {
		return Collections.unmodifiableCollection(routes.values());
	}

	public Collection<SettlementBuildSite> getBuildSites() {
		return Collections.unmodifiableCollection(buildSites.values());
	}

	public Collection<SettlementConstructionDelivery> getConstructionDeliveries() {
		return Collections.unmodifiableCollection(constructionDeliveries.values());
	}

	public char sharedTerrainAt(net.minecraft.resources.ResourceKey<Level> dimension, int worldX, int worldZ) {
		SharedTerrainCell cell = sharedTerrainKnowledge.get(sharedTerrainKey(dimension, quantizedMapCoordinate(worldX), quantizedMapCoordinate(worldZ)));
		return cell == null ? 'U' : cell.terrain();
	}

	public void restoreSettlementMapMemory(ServerLevel level, SettlementState settlement) {
		SettlementLoadedObservation.restoreSurveyorObservationIfMissing(
			settlement,
			savedSurveyorObservations.get(settlement.id()),
			level.getServer().getTickCount()
		);
		SettlementRoadwrightWork.restorePersistentPlans(
			settlement,
			savedRoadworkPlans.getOrDefault(settlement.id(), Map.of()),
			level.getServer().getTickCount()
		);
	}

	public void storeSurveyorObservation(String settlementId, SettlementLoadedObservation.SurveyorObservation observation) {
		if (observation == null || observation.empty()) {
			return;
		}

		SettlementLoadedObservation.SurveyorObservation previous = savedSurveyorObservations.put(settlementId, observation);

		if (!observation.equals(previous)) {
			setDirty();
		}
	}

	public void storeRoadworkPlans(String settlementId, Map<String, SettlementRoadwrightWork.RoadworkDebugPlan> plans) {
		if (plans == null || plans.isEmpty()) {
			if (savedRoadworkPlans.remove(settlementId) != null) {
				setDirty();
			}
			return;
		}

		Map<String, SettlementRoadwrightWork.RoadworkDebugPlan> copy = new LinkedHashMap<>(plans);
		Map<String, SettlementRoadwrightWork.RoadworkDebugPlan> previous = savedRoadworkPlans.put(settlementId, copy);

		if (!copy.equals(previous)) {
			setDirty();
		}
	}

	public List<RouteState> getRoutesForSettlement(String settlementId) {
		return routes.values().stream()
			.filter(route -> route.fromSettlementId().equals(settlementId) || route.toSettlementId().equals(settlementId))
			.toList();
	}

	public List<SettlementBuildSite> getBuildSitesForSettlement(String settlementId) {
		return buildSites.values().stream()
			.filter(buildSite -> buildSite.settlementId().equals(settlementId))
			.toList();
	}

	public Optional<SettlementBuildSite> findBuildSite(String settlementId, SettlementBuildSiteType blueprintId, BlockPos workstationPos) {
		return buildSites.values().stream()
			.filter(buildSite -> buildSite.settlementId().equals(settlementId))
			.filter(buildSite -> buildSite.blueprintId() == blueprintId)
			.filter(buildSite -> buildSite.referencesWorkstation(workstationPos))
			.findFirst();
	}

	public Optional<SettlementState> findNearestSettlement(net.minecraft.resources.ResourceKey<Level> dimension, net.minecraft.core.BlockPos position, double maxDistanceBlocks, Predicate<SettlementState> filter) {
		double maxDistanceSquared = maxDistanceBlocks * maxDistanceBlocks;
		SettlementState nearestSettlement = null;
		double nearestDistanceSquared = maxDistanceSquared;

		for (SettlementState settlement : settlements.values()) {
			if (!settlement.dimension().equals(dimension) || !filter.test(settlement)) {
				continue;
			}

			double distanceSquared = settlement.center().distSqr(position);

			if (distanceSquared > nearestDistanceSquared) {
				continue;
			}

			nearestSettlement = settlement;
			nearestDistanceSquared = distanceSquared;
		}

		return Optional.ofNullable(nearestSettlement);
	}

	public Optional<SettlementState> findSettlementForPosition(net.minecraft.resources.ResourceKey<Level> dimension, net.minecraft.core.BlockPos position, Predicate<SettlementState> filter) {
		SettlementState nearestSettlement = null;
		double nearestDistanceSquared = Double.MAX_VALUE;

		for (SettlementState settlement : settlements.values()) {
			if (!settlement.dimension().equals(dimension) || !filter.test(settlement)) {
				continue;
			}

			double distanceSquared = settlement.center().distSqr(position);
			int settlementRadius = SettlementVillagers.settlementRadiusBlocks(settlement);

			if (distanceSquared > (double) settlementRadius * settlementRadius || distanceSquared >= nearestDistanceSquared) {
				continue;
			}

			nearestSettlement = settlement;
			nearestDistanceSquared = distanceSquared;
		}

		return Optional.ofNullable(nearestSettlement);
	}

	public int settlementCount() {
		return settlements.size();
	}

	public int regionCount() {
		return getOrderedRegions().size();
	}

	public int routeCount() {
		return routes.size();
	}

	public int buildSiteCount() {
		return buildSites.size();
	}

	public List<ChunkPos> reserveVillageScanChunks(net.minecraft.resources.ResourceKey<Level> dimension, net.minecraft.core.BlockPos center, int chunkBudget) {
		if (chunkBudget <= 0) {
			return List.of();
		}

		ChunkPos centerChunk = ChunkPos.containing(center);
		VillageScanCursor cursor = villageScanCursor;
		boolean changed = false;

		if (cursor == null || !cursor.matchesOrigin(dimension, centerChunk)) {
			cursor = VillageScanCursor.create(dimension, centerChunk);
			changed = true;
		}

		List<ChunkPos> chunks = new ArrayList<>(chunkBudget);

		for (int i = 0; i < chunkBudget; i++) {
			chunks.add(cursor.currentChunk());
			cursor = cursor.advance();
			changed = true;
		}

		if (changed) {
			villageScanCursor = cursor;
			setDirty();
		}

		return chunks;
	}

	public void putSettlement(SettlementState settlement) {
		SettlementState previous = settlements.put(settlement.id(), settlement);

		if (!settlement.equals(previous)) {
			setDirty();
		}
	}

	public void removeSettlement(String settlementId) {
		boolean removed = settlements.remove(settlementId) != null;
		removed |= bootstrapVillagerSpawnTicks.remove(settlementId) != null;
		removed |= buildSites.entrySet().removeIf(entry -> entry.getValue().settlementId().equals(settlementId));
		removed |= constructionDeliveries.entrySet().removeIf(entry -> entry.getValue().settlementId().equals(settlementId));

		if (removed) {
			setDirty();
		}
	}

	public void putRoute(RouteState route) {
		RouteState previous = routes.put(route.id(), route);

		if (!route.equals(previous)) {
			setDirty();
		}
	}

	public void removeRoute(String routeId) {
		if (routes.remove(routeId) != null) {
			setDirty();
		}
	}

	public void maintainSharedMapKnowledge(MinecraftServer server) {
		long tick = server.getTickCount();
		int remainingBudget = SHARED_MAP_MAX_SAMPLES_PER_UPDATE;
		boolean changed = false;
		Set<String> touchedDimensions = new HashSet<>();

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (remainingBudget <= 0 || !(player.level() instanceof ServerLevel level)) {
				break;
			}

			ObservedSharedTerrain observedSamples = observeSharedTerrainAround(level, player.blockPosition().immutable(), tick, remainingBudget);

			if (observedSamples.samplesConsumed() <= 0) {
				continue;
			}

			remainingBudget -= observedSamples.samplesConsumed();
			changed |= observedSamples.changed();
			touchedDimensions.add(level.dimension().identifier().toString());
		}

		if (!changed) {
			return;
		}

		for (String dimensionKey : touchedDimensions) {
			pruneSharedTerrainKnowledge(dimensionKey);
		}

		setDirty();
	}

	public void putBuildSite(SettlementBuildSite buildSite) {
		SettlementBuildSite previous = buildSites.put(buildSite.id(), buildSite);

		if (!buildSite.equals(previous)) {
			setDirty();
		}
	}

	public void releaseConstructionDeliveriesForBlock(String settlementId, String buildSiteId, String blockPosition) {
		boolean deliveriesChanged = false;
		Map<String, Integer> returnedGoods = new LinkedHashMap<>();

		for (var iterator = constructionDeliveries.entrySet().iterator(); iterator.hasNext();) {
			SettlementConstructionDelivery delivery = iterator.next().getValue();

			if (!delivery.settlementId().equals(settlementId)
				|| !delivery.buildSiteId().equals(buildSiteId)
				|| !delivery.blockPosition().equals(blockPosition)) {
				continue;
			}

			if (!delivery.materialKey().isBlank()) {
				SettlementGoods.addGoods(returnedGoods, delivery.materialKey(), delivery.amount());
			}

			iterator.remove();
			deliveriesChanged = true;
		}

		if (!returnedGoods.isEmpty()) {
			SettlementState settlement = settlements.get(settlementId);

			if (settlement != null) {
				Map<String, Integer> stock = new LinkedHashMap<>(settlement.stock());
				returnedGoods.forEach((goodsKey, amount) -> SettlementGoods.addGoods(stock, goodsKey, amount));
				settlements.put(settlementId, settlement.withStock(stock));
				deliveriesChanged = true;
			}
		}

		if (deliveriesChanged) {
			setDirty();
		}
	}

	public void removeBuildSite(String buildSiteId) {
		if (buildSites.remove(buildSiteId) != null) {
			setDirty();
		}
	}

	public int advanceRoundRobin(MinecraftServer server, long currentTick, int regionsPerCycle) {
		if (regionsPerCycle <= 0) {
			return 0;
		}

		List<RegionKey> orderedRegions = getOrderedRegions();

		if (orderedRegions.isEmpty()) {
			boolean changed = false;

			if (nextRegionCursor != 0) {
				nextRegionCursor = 0;
				changed = true;
			}

			if (lastGlobalUpdateTick != currentTick) {
				lastGlobalUpdateTick = currentTick;
				changed = true;
			}

			if (changed) {
				setDirty();
			}

			return 0;
		}

		int cursor = Math.floorMod(nextRegionCursor, orderedRegions.size());
		int updates = Math.min(regionsPerCycle, orderedRegions.size());
		boolean changed = false;

		for (int i = 0; i < updates; i++) {
			RegionKey region = orderedRegions.get(cursor);
			changed |= advanceRegion(server, region, currentTick);
			cursor = (cursor + 1) % orderedRegions.size();
		}

		if (nextRegionCursor != cursor) {
			nextRegionCursor = cursor;
			changed = true;
		}

		if (lastGlobalUpdateTick != currentTick) {
			lastGlobalUpdateTick = currentTick;
			changed = true;
		}

		if (changed) {
			setDirty();
		}

		return updates;
	}

	public void ensureCustomSettlementVillagers(MinecraftServer server, long currentTick) {
		boolean changed = false;

		for (Map.Entry<String, SettlementState> entry : settlements.entrySet()) {
			SettlementState settlement = entry.getValue();

			if (!shouldEnsureBootstrapVillager(settlement, currentTick)) {
				continue;
			}

			ServerLevel level = server.getLevel(settlement.dimension());

			if (level == null || !level.isLoaded(settlement.center()) || !level.isPositionEntityTicking(settlement.center())) {
				continue;
			}

			if (SettlementVillagers.countNearbyVillagers(level, settlement.center(), CUSTOM_SETTLEMENT_VILLAGER_RADIUS_BLOCKS) > 0) {
				continue;
			}

			Optional<BlockPos> spawnPos = SettlementVillagers.findSpawnPos(level, settlement.center(), CUSTOM_SETTLEMENT_SPAWN_SEARCH_RADIUS);

			if (spawnPos.isEmpty() || !SettlementVillagers.spawnVillager(level, spawnPos.get())) {
				continue;
			}

			Long previousSpawnTick = bootstrapVillagerSpawnTicks.put(settlement.id(), currentTick);
			SettlementVillagers.ensureTrademaster(level, settlement);
			SettlementVillagers.ensureCarpenter(level, settlement);
			SettlementVillagers.ensureFletcher(level, settlement);
			SettlementVillagers.ensureVillagerHomes(level, settlement);
			SettlementState updatedSettlement = settlement.withPopulation(SettlementVillagers.censusPopulation(level, settlement));

			if (!updatedSettlement.equals(settlement)) {
				entry.setValue(updatedSettlement);
				changed = true;
			}

			if (!Objects.equals(previousSpawnTick, currentTick)) {
				changed = true;
			}
		}

		if (changed) {
			setDirty();
		}
	}

	public void maintainLoadedFarmerState(MinecraftServer server, int intervalTicks) {
		boolean changed = false;
		long currentTick = server.getTickCount();

		for (Map.Entry<String, SettlementState> entry : settlements.entrySet()) {
			SettlementState settlement = entry.getValue();
			ServerLevel level = server.getLevel(settlement.dimension());

			if (!isLoadedMaintenanceDue(settlement, currentTick, "farmer", intervalTicks)
				|| level == null || !level.isLoaded(settlement.center()) || !level.isPositionEntityTicking(settlement.center()) || !SettlementVillagers.usesActualVillagers(settlement)) {
				continue;
			}

			long taskStart = SettlementPerformanceLog.start();
			Map<String, Integer> actualPopulation = SettlementVillagers.censusPopulation(level, settlement);
			SettlementState workingSettlement = actualPopulation.equals(settlement.population())
				? settlement
				: settlement.withPopulation(actualPopulation);
			Map<String, Integer> stock = new LinkedHashMap<>(workingSettlement.stock());
			boolean stockChanged = SettlementFarmerWork.maintainLoadedGardens(level, workingSettlement, stock);

			if (!stockChanged && workingSettlement.equals(settlement)) {
				SettlementPerformanceLog.warnIfSlow("loaded_farmer", workingSettlement, taskStart, server.getTickCount());
				continue;
			}

			SettlementState updatedSettlement = stockChanged ? workingSettlement.withStock(stock) : workingSettlement;

			if (!updatedSettlement.equals(settlement)) {
				entry.setValue(updatedSettlement);
				changed = true;
			}

			SettlementPerformanceLog.warnIfSlow("loaded_farmer", workingSettlement, taskStart, server.getTickCount());
		}

		if (changed) {
			setDirty();
		}
	}

	private ObservedSharedTerrain observeSharedTerrainAround(ServerLevel level, BlockPos center, long tick, int remainingBudget) {
		if (remainingBudget <= 0) {
			return new ObservedSharedTerrain(0, false);
		}

		int radiusSteps = SHARED_MAP_PLAYER_OBSERVATION_RADIUS_BLOCKS / SHARED_MAP_SAMPLE_STRIDE_BLOCKS;
		int totalWidth = (radiusSteps * 2) + 1;
		int totalCells = totalWidth * totalWidth;
		int startIndex = Math.floorMod(center.asLong() + (tick / 20L * 131L), totalCells);
		int observed = 0;
		boolean changed = false;

		for (int step = 0; step < totalCells && observed < remainingBudget; step++) {
			int cellIndex = (startIndex + step) % totalCells;
			int dx = ((cellIndex % totalWidth) - radiusSteps) * SHARED_MAP_SAMPLE_STRIDE_BLOCKS;
			int dz = ((cellIndex / totalWidth) - radiusSteps) * SHARED_MAP_SAMPLE_STRIDE_BLOCKS;

			if ((dx * dx) + (dz * dz) > SHARED_MAP_PLAYER_OBSERVATION_RADIUS_BLOCKS * SHARED_MAP_PLAYER_OBSERVATION_RADIUS_BLOCKS) {
				continue;
			}

			int worldX = center.getX() + dx;
			int worldZ = center.getZ() + dz;
			char terrain = observeLoadedTerrain(level, worldX, worldZ);

			if (terrain == 'U') {
				continue;
			}

			changed |= recordSharedTerrainObservation(level.dimension(), worldX, worldZ, terrain, tick);
			observed++;
		}

		return new ObservedSharedTerrain(observed, changed);
	}

	private boolean recordSharedTerrainObservation(net.minecraft.resources.ResourceKey<Level> dimension, int worldX, int worldZ, char terrain, long tick) {
		int sampleX = quantizedMapCoordinate(worldX);
		int sampleZ = quantizedMapCoordinate(worldZ);
		String key = sharedTerrainKey(dimension, sampleX, sampleZ);
		SharedTerrainCell current = sharedTerrainKnowledge.get(key);

		if (current != null && current.terrain() == terrain && tick - current.lastObservedTick() < SHARED_MAP_REFRESH_TICKS) {
			return false;
		}

		sharedTerrainKnowledge.put(key, new SharedTerrainCell(String.valueOf(terrain), tick));
		return true;
	}

	private void pruneSharedTerrainKnowledge(String dimensionKey) {
		List<Map.Entry<String, SharedTerrainCell>> entries = sharedTerrainKnowledge.entrySet().stream()
			.filter(entry -> entry.getKey().startsWith(dimensionKey + "|"))
			.sorted(Map.Entry.comparingByValue((first, second) -> Long.compare(first.lastObservedTick(), second.lastObservedTick())))
			.toList();
		int excess = entries.size() - SHARED_MAP_MAX_CELLS_PER_DIMENSION;

		for (int i = 0; i < excess; i++) {
			sharedTerrainKnowledge.remove(entries.get(i).getKey());
		}
	}

	private static char observeLoadedTerrain(ServerLevel level, int worldX, int worldZ) {
		BlockPos columnPos = new BlockPos(worldX, level.getSeaLevel(), worldZ);

		if (!level.hasChunkAt(columnPos)) {
			return 'U';
		}

		int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ) - 1;

		if (surfaceY < level.getMinY() || surfaceY > level.getMaxY() - 1) {
			return fallbackBiomeTerrain(level, worldX, worldZ);
		}

		BlockPos.MutableBlockPos samplePos = new BlockPos.MutableBlockPos(worldX, surfaceY, worldZ);

		for (int depth = 0; depth < 5 && samplePos.getY() >= level.getMinY(); depth++) {
			if (level.getBlockState(samplePos).is(Blocks.WATER)) {
				return 'W';
			}

			samplePos.move(0, -1, 0);
		}

		return 'L';
	}

	private static char fallbackBiomeTerrain(ServerLevel level, int worldX, int worldZ) {
		Holder<Biome> biome = level.getUncachedNoiseBiome(worldX >> 2, level.getSeaLevel() >> 2, worldZ >> 2);
		return biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER) ? 'W' : 'L';
	}

	private static int quantizedMapCoordinate(int worldCoordinate) {
		return Math.floorDiv(worldCoordinate, SHARED_MAP_SAMPLE_STRIDE_BLOCKS);
	}

	private static String sharedTerrainKey(net.minecraft.resources.ResourceKey<Level> dimension, int sampleX, int sampleZ) {
		return dimension.identifier() + "|" + sampleX + "|" + sampleZ;
	}

	private record ObservedSharedTerrain(int samplesConsumed, boolean changed) {
	}

	public void maintainLoadedResourceState(MinecraftServer server, int intervalTicks) {
		boolean changed = false;
		long currentTick = server.getTickCount();

		for (Map.Entry<String, SettlementState> entry : settlements.entrySet()) {
			SettlementState settlement = entry.getValue();
			ServerLevel level = server.getLevel(settlement.dimension());

			if (!isLoadedMaintenanceDue(settlement, currentTick, "resource", intervalTicks)
				|| level == null || !level.isLoaded(settlement.center()) || !level.isPositionEntityTicking(settlement.center()) || !SettlementVillagers.usesActualVillagers(settlement)) {
				continue;
			}

			long taskStart = SettlementPerformanceLog.start();
			Map<String, Integer> actualPopulation = SettlementVillagers.censusPopulation(level, settlement);
			SettlementState workingSettlement = actualPopulation.equals(settlement.population())
				? settlement
				: settlement.withPopulation(actualPopulation);
			Map<String, Integer> stock = new LinkedHashMap<>(workingSettlement.stock());
			List<SettlementBuildSite> activeBuildSites = getBuildSitesForSettlement(settlement.id());
			Set<String> constructionDeliveryVillagerIds = constructionDeliveries.values().stream()
				.filter(delivery -> delivery.settlementId().equals(settlement.id()))
				.map(SettlementConstructionDelivery::villagerId)
				.collect(java.util.stream.Collectors.toSet());
			boolean stockChanged = false;
			stockChanged |= SettlementVillagerItemPickupWork.maintainLoadedItemCollection(
				level,
				workingSettlement,
				stock,
				activeBuildSites,
				constructionDeliveryVillagerIds
			);
			SettlementButcherWork.maintainLoadedButchery(
				level,
				workingSettlement,
				stock,
				getRoutesForSettlement(workingSettlement.id()).size()
			);
			SettlementForesterWork.maintainLoadedForestry(level, workingSettlement, stock);
			stockChanged |= SettlementCarpenterWork.maintainLoadedCarpentry(level, workingSettlement, stock, activeBuildSites);
			stockChanged |= SettlementFletcherWork.maintainLoadedFletching(level, workingSettlement, stock, activeBuildSites);
			SettlementPortmasterWork.maintainLoadedHarbor(level, workingSettlement);
			stockChanged |= !stock.equals(workingSettlement.stock());
			SettlementState updatedSettlement = stockChanged ? workingSettlement.withStock(stock) : workingSettlement;

			if (!updatedSettlement.equals(settlement)) {
				entry.setValue(updatedSettlement);
				changed = true;
			}

			SettlementPerformanceLog.warnIfSlow("loaded_resource", workingSettlement, taskStart, server.getTickCount());
		}

		if (changed) {
			setDirty();
		}
	}

	public void maintainLoadedDefenseState(MinecraftServer server) {
		boolean changed = false;
		long currentTick = server.getTickCount();

		for (Map.Entry<String, SettlementState> entry : settlements.entrySet()) {
			SettlementState settlement = entry.getValue();
			ServerLevel level = server.getLevel(settlement.dimension());

			if (level == null || !level.isLoaded(settlement.center()) || !level.isPositionEntityTicking(settlement.center()) || !SettlementVillagers.usesActualVillagers(settlement)) {
				continue;
			}

			long taskStart = SettlementPerformanceLog.start();
			Map<String, Integer> actualPopulation = SettlementVillagers.censusPopulation(level, settlement);
			SettlementState workingSettlement = actualPopulation.equals(settlement.population())
				? settlement
				: settlement.withPopulation(actualPopulation);
			SettlementDefenseWork.maintainLoadedDefense(level, workingSettlement);

			if (!workingSettlement.equals(settlement)) {
				entry.setValue(workingSettlement);
				changed = true;
			}

			SettlementPerformanceLog.warnIfSlow("loaded_defense", workingSettlement, taskStart, currentTick);
		}

		if (changed) {
			setDirty();
		}
	}

	public void maintainLoadedConstructionState(MinecraftServer server, int intervalTicks) {
		boolean changed = false;
		long currentTick = server.getTickCount();

		for (Map.Entry<String, SettlementState> entry : settlements.entrySet()) {
			SettlementState settlement = entry.getValue();
			ServerLevel level = server.getLevel(settlement.dimension());

			if (!isLoadedMaintenanceDue(settlement, currentTick, "construction", intervalTicks)
				|| level == null || !level.isLoaded(settlement.center()) || !level.isPositionEntityTicking(settlement.center()) || !SettlementVillagers.usesActualVillagers(settlement)) {
				continue;
			}

			long taskStart = SettlementPerformanceLog.start();
			Map<String, Integer> actualPopulation = SettlementVillagers.censusPopulation(level, settlement);
			SettlementState workingSettlement = actualPopulation.equals(settlement.population())
				? settlement
				: settlement.withPopulation(actualPopulation);
			long homesStart = System.nanoTime();
			changed |= SettlementVillagers.ensureVillagerHomes(level, workingSettlement);
			long homesTime = System.nanoTime() - homesStart;
			if (homesTime > 100_000_000) { // >100ms
				LiveVillages.LOGGER.warn("Ensure villager homes took {} ms for settlement {}", Math.round(homesTime / 1_000_000.0D), settlement.id());
			}
			Map<String, Integer> stock = new LinkedHashMap<>(workingSettlement.stock());
			changed |= tryStartPlacedCarpenterWorkshopBuildSites(level, workingSettlement, stock);
			changed |= tryStartPlacedRoadwrightWorkshopBuildSites(level, workingSettlement, stock);
				changed |= tryStartPlacedForesterWorkshopBuildSites(level, workingSettlement, stock);
				changed |= tryStartVanillaCartographerHouseBuildSites(level, workingSettlement, stock);
				changed |= tryStartVanillaButcherShopBuildSites(level, workingSettlement, stock);
				changed |= tryStartVanillaFletcherHutBuildSites(level, workingSettlement, stock);
				changed |= tryStartPlacedTradeBoardBuildSites(level, workingSettlement, stock);
				changed |= tryStartPlacedPortmasterDockBuildSites(level, workingSettlement, stock);
				changed |= tryStartPlacedLighthouseBuildSites(level, workingSettlement, stock);
				List<SettlementBuildSite> activeBuildSites = getBuildSitesForSettlement(settlement.id());
			restoreSettlementMapMemory(level, workingSettlement);
			if (LiveVillagesGameRules.surveyorMapFogEnabled(level)
				&& !SettlementVillagers.nearbyRoadwrights(level, workingSettlement).isEmpty()) {
				long surveyorObservationStart = SettlementPerformanceLog.start();
				SettlementLoadedObservation.SurveyorObservation observation = SettlementLoadedObservation.updateSurveyorMapObservation(
					level,
					workingSettlement,
					activeBuildSites,
					Math.max(96, SettlementVillagers.settlementRadiusBlocks(workingSettlement))
				);
				storeSurveyorObservation(workingSettlement.id(), observation);
				SettlementPerformanceLog.warnIfSlow("loaded_surveyor_observation", workingSettlement, surveyorObservationStart, server.getTickCount());
			}
			long lastRoadworkCatchupTick = loadedRoadworkCatchupTicks.getOrDefault(
				workingSettlement.id(),
				Math.max(0L, currentTick - DEFAULT_LOADED_ROADWORK_CATCHUP_TICKS)
			);
			long elapsedRoadworkCatchupTicks = Math.max(0L, currentTick - lastRoadworkCatchupTick);
			boolean roadworkCatchupChanged = false;

			if (elapsedRoadworkCatchupTicks >= SettlementEconomyRules.MIN_SIMULATION_TICKS) {
				long roadworkCatchupStart = System.nanoTime();
				SettlementRoadwrightWork.RoadworkCatchupResult roadworkCatchupResult = SettlementRoadwrightWork.applyLoadedRoadworkCatchup(
					level,
					workingSettlement,
					stock,
					activeBuildSites,
					getSettlementsInDimension(server, settlement.dimension()),
					getRoutesForSettlement(settlement.id()),
					elapsedRoadworkCatchupTicks / SettlementEconomyRules.TICKS_PER_DAY
				);
				long roadworkCatchupTime = System.nanoTime() - roadworkCatchupStart;

				if (roadworkCatchupTime > 100_000_000) {
					LiveVillages.LOGGER.warn("Roadwork catch-up took {} ms for settlement {}", Math.round(roadworkCatchupTime / 1_000_000.0D), settlement.id());
				}

				roadworkCatchupChanged = roadworkCatchupResult.worldChanged();
			}

			Long previousRoadworkCatchupTick = loadedRoadworkCatchupTicks.put(workingSettlement.id(), currentTick);
			changed |= previousRoadworkCatchupTick == null || previousRoadworkCatchupTick.longValue() != currentTick;
			long roadworkStart = System.nanoTime();
			SettlementRoadwrightWork.RoadworkResult roadworkResult = SettlementRoadwrightWork.maintainLoadedRoadwork(
				level,
				workingSettlement,
				stock,
				activeBuildSites,
				getSettlementsInDimension(server, settlement.dimension()),
				getRoutesForSettlement(settlement.id())
			);
			long roadworkTime = System.nanoTime() - roadworkStart;
			if (roadworkTime > 100_000_000) { // >100ms
				LiveVillages.LOGGER.warn("Roadwork maintenance took {} ms for settlement {}", Math.round(roadworkTime / 1_000_000.0D), settlement.id());
			}
			storeRoadworkPlans(workingSettlement.id(), SettlementRoadwrightWork.persistentPlansForSettlement(level, workingSettlement, currentTick));

			if (activeBuildSites.isEmpty()) {
				boolean stockChanged = !stock.equals(workingSettlement.stock());
				SettlementState updatedSettlement = stockChanged ? workingSettlement.withStock(stock) : workingSettlement;

				if (!updatedSettlement.equals(settlement)) {
					entry.setValue(updatedSettlement);
					changed = true;
				}

				changed |= roadworkCatchupChanged;
				changed |= roadworkResult.worldChanged();
				SettlementPerformanceLog.warnIfSlow("loaded_construction", workingSettlement, taskStart, server.getTickCount());
				continue;
			}

			SettlementConstructionWork.ConstructionWorkResult constructionResult = SettlementConstructionWork.maintainLoadedConstruction(
				level,
				workingSettlement,
				stock,
				activeBuildSites,
				constructionDeliveries,
				roadworkResult.busyRoadwrightIds()
			);

			for (SettlementBuildSite buildSite : constructionResult.buildSites()) {
				SettlementBuildSite previousBuildSite = buildSites.put(buildSite.id(), buildSite);

				if (!buildSite.equals(previousBuildSite)) {
					changed = true;
				}
			}

			boolean stockChanged = !stock.equals(workingSettlement.stock());
			changed |= constructionResult.deliveriesChanged();
			changed |= roadworkCatchupChanged;
			changed |= roadworkResult.worldChanged();
			SettlementState updatedSettlement = stockChanged ? workingSettlement.withStock(stock) : workingSettlement;

			if (!updatedSettlement.equals(settlement)) {
				entry.setValue(updatedSettlement);
				changed = true;
			}

			SettlementPerformanceLog.warnIfSlow("loaded_construction", workingSettlement, taskStart, server.getTickCount());
		}

		if (changed) {
			setDirty();
		}
	}

	private static boolean isLoadedMaintenanceDue(SettlementState settlement, long currentTick, String operationKey, int intervalTicks) {
		if (intervalTicks <= LiveVillagesScheduler.LOADED_MAINTENANCE_CHECK_INTERVAL) {
			return true;
		}

		long phase = stableModulo(settlement.id() + "|" + operationKey, intervalTicks);
		return Math.floorMod(currentTick - phase, intervalTicks) < LiveVillagesScheduler.LOADED_MAINTENANCE_CHECK_INTERVAL;
	}

	private static long stableModulo(String key, long modulo) {
		long hash = 1125899906842597L;

		for (int i = 0; i < key.length(); i++) {
			hash = (hash * 31L) + key.charAt(i);
		}

		return Math.floorMod(hash, modulo);
	}

	private boolean tryStartVanillaCartographerHouseBuildSites(ServerLevel level, SettlementState settlement, Map<String, Integer> stock) {
		boolean changed = false;

		for (BlockPos tablePos : SettlementConstruction.findPlacedCartographyTables(level, settlement)) {
			Optional<SettlementBuildSite> existingBuildSite = findBuildSite(settlement.id(), SettlementBuildSiteType.CARTOGRAPHER_HOUSE, tablePos);

			SettlementConstruction.WorkstationBuildResult buildResult = SettlementConstruction.tryStartCartographerHouseAtWorkstation(
				level,
				tablePos,
				SettlementConstruction.cartographerHouseFacingFor(settlement, tablePos),
				settlement.id(),
				stock,
				existingBuildSite
			);

			if (buildResult.isStarted() || buildResult.isResumed()) {
				SettlementBuildSite previousBuildSite = buildSites.put(buildResult.buildSite().id(), buildResult.buildSite());
				changed |= !buildResult.buildSite().equals(previousBuildSite);
			}
		}

		return changed;
	}

	private boolean tryStartPlacedTradeBoardBuildSites(ServerLevel level, SettlementState settlement, Map<String, Integer> stock) {
		boolean changed = false;

		for (BlockPos boardPos : SettlementConstruction.findPlacedTradeBoards(level, settlement)) {
			if (!(level.getBlockEntity(boardPos) instanceof TradeBoardBlockEntity tradeBoard)
				|| !tradeBoard.resolveSettlement(level).id().equals(settlement.id())) {
				continue;
			}

			Optional<SettlementBuildSite> existingBuildSite = findBuildSite(settlement.id(), SettlementBuildSiteType.TRADING_POST, boardPos);

			SettlementConstruction.WorkstationBuildResult buildResult = SettlementConstruction.tryStartTradingPostAtWorkstation(
				level,
				boardPos,
				SettlementConstruction.tradeBoardFacingFor(level, boardPos),
				settlement.id(),
				stock,
				existingBuildSite,
				false
			);

			if (buildResult.isStarted() || buildResult.isResumed()) {
				SettlementBuildSite previousBuildSite = buildSites.put(buildResult.buildSite().id(), buildResult.buildSite());
				changed |= !buildResult.buildSite().equals(previousBuildSite);
				changed |= SettlementVillagers.ensureTrademaster(level, settlement);
			}
		}

		return changed;
	}

	private boolean tryStartVanillaButcherShopBuildSites(ServerLevel level, SettlementState settlement, Map<String, Integer> stock) {
		boolean changed = false;

		for (BlockPos smokerPos : SettlementConstruction.findPlacedSmokers(level, settlement)) {
			Optional<SettlementBuildSite> existingBuildSite = findBuildSite(settlement.id(), SettlementBuildSiteType.BUTCHER_SHOP, smokerPos);

			SettlementConstruction.WorkstationBuildResult buildResult = SettlementConstruction.tryStartButcherShopAtWorkstation(
				level,
				smokerPos,
				SettlementConstruction.fletcherHutFacingFor(settlement, smokerPos),
				settlement.id(),
				stock,
				existingBuildSite
			);

			if (buildResult.isStarted() || buildResult.isResumed()) {
				SettlementBuildSite previousBuildSite = buildSites.put(buildResult.buildSite().id(), buildResult.buildSite());
				changed |= !buildResult.buildSite().equals(previousBuildSite);
			}
		}

		return changed;
	}

	private boolean tryStartPlacedPortmasterDockBuildSites(ServerLevel level, SettlementState settlement, Map<String, Integer> stock) {
		boolean changed = false;

		for (BlockPos anchorPos : SettlementConstruction.findPlacedPortmasterAnchors(level, settlement)) {
			Optional<SettlementBuildSite> existingBuildSite = findBuildSite(settlement.id(), SettlementBuildSiteType.DOCK, anchorPos);
			SettlementConstruction.WorkstationBuildResult buildResult = SettlementConstruction.tryStartDockAtPortmasterAnchor(
				level,
				anchorPos,
				SettlementConstruction.portmasterAnchorFacingFor(level, anchorPos),
				settlement.id(),
				stock,
				existingBuildSite
			);

			if (buildResult.isStarted() || buildResult.isResumed()) {
				SettlementBuildSite previousBuildSite = buildSites.put(buildResult.buildSite().id(), buildResult.buildSite());
				changed |= !buildResult.buildSite().equals(previousBuildSite);
				changed |= SettlementVillagers.ensurePortmaster(level, settlement);
			}
		}

		return changed;
	}

	private boolean tryStartPlacedLighthouseBuildSites(ServerLevel level, SettlementState settlement, Map<String, Integer> stock) {
		boolean changed = false;

		for (BlockPos markerPos : SettlementConstruction.findPlacedLighthouses(level, settlement)) {
			Optional<SettlementBuildSite> existingBuildSite = findBuildSite(settlement.id(), SettlementBuildSiteType.LIGHTHOUSE, markerPos);
			SettlementConstruction.WorkstationBuildResult buildResult = SettlementConstruction.tryStartLighthouseAtMarker(
				level,
				markerPos,
				settlement.id(),
				stock,
				existingBuildSite
			);

			if (buildResult.isStarted() || buildResult.isResumed()) {
				SettlementBuildSite previousBuildSite = buildSites.put(buildResult.buildSite().id(), buildResult.buildSite());
				changed |= !buildResult.buildSite().equals(previousBuildSite);
			}
		}

		return changed;
	}

	private boolean tryStartVanillaFletcherHutBuildSites(ServerLevel level, SettlementState settlement, Map<String, Integer> stock) {
		boolean changed = false;

		for (BlockPos tablePos : SettlementConstruction.findPlacedFletchingTables(level, settlement)) {
			Optional<SettlementBuildSite> existingBuildSite = findBuildSite(settlement.id(), SettlementBuildSiteType.FLETCHER_HUT, tablePos);

			SettlementConstruction.WorkstationBuildResult buildResult = SettlementConstruction.tryStartFletcherHutAtWorkstation(
				level,
				tablePos,
				SettlementConstruction.fletcherHutFacingFor(settlement, tablePos),
				settlement.id(),
				stock,
				existingBuildSite
			);

			if (buildResult.isStarted() || buildResult.isResumed()) {
				SettlementBuildSite previousBuildSite = buildSites.put(buildResult.buildSite().id(), buildResult.buildSite());
				changed |= !buildResult.buildSite().equals(previousBuildSite);
				changed |= SettlementVillagers.ensureFletcher(level, settlement);
			}
		}

		return changed;
	}

	private boolean tryStartPlacedCarpenterWorkshopBuildSites(ServerLevel level, SettlementState settlement, Map<String, Integer> stock) {
		boolean changed = false;

		for (BlockPos benchPos : SettlementConstruction.findPlacedCarpenterBenches(level, settlement)) {
			Optional<SettlementBuildSite> existingBuildSite = findBuildSite(settlement.id(), SettlementBuildSiteType.CARPENTER_WORKSHOP, benchPos);
			if (existingBuildSite.isEmpty()) {
				continue;
			}
			SettlementConstruction.WorkstationBuildResult buildResult = SettlementConstruction.tryStartCarpenterWorkshopAtWorkstation(
				level,
				benchPos,
				existingBuildSite.get().facing(),
				settlement.id(),
				stock,
				existingBuildSite
			);

			if (buildResult.isStarted() || buildResult.isResumed()) {
				SettlementBuildSite previousBuildSite = buildSites.put(buildResult.buildSite().id(), buildResult.buildSite());
				changed |= !buildResult.buildSite().equals(previousBuildSite);
				changed |= SettlementVillagers.ensureCarpenter(level, settlement);
			}
		}

		return changed;
	}

	private boolean tryStartPlacedRoadwrightWorkshopBuildSites(ServerLevel level, SettlementState settlement, Map<String, Integer> stock) {
		boolean changed = false;

		for (BlockPos tablePos : SettlementConstruction.findPlacedSurveyorTables(level, settlement)) {
			Optional<SettlementBuildSite> existingBuildSite = findBuildSite(settlement.id(), SettlementBuildSiteType.ROADWRIGHT_WORKSHOP, tablePos);
			if (existingBuildSite.isEmpty()) {
				continue;
			}
			SettlementConstruction.WorkstationBuildResult buildResult = SettlementConstruction.tryStartRoadwrightWorkshopAtWorkstation(
				level,
				tablePos,
				existingBuildSite.get().facing(),
				settlement.id(),
				stock,
				existingBuildSite
			);

			if (buildResult.isStarted() || buildResult.isResumed()) {
				SettlementBuildSite previousBuildSite = buildSites.put(buildResult.buildSite().id(), buildResult.buildSite());
				changed |= !buildResult.buildSite().equals(previousBuildSite);
				changed |= SettlementVillagers.ensureRoadwright(level, settlement);
			}
		}

		return changed;
	}

	private boolean tryStartPlacedForesterWorkshopBuildSites(ServerLevel level, SettlementState settlement, Map<String, Integer> stock) {
		boolean changed = false;

		for (BlockPos tablePos : SettlementConstruction.findPlacedForesterTables(level, settlement)) {
			Optional<SettlementBuildSite> existingBuildSite = findBuildSite(settlement.id(), SettlementBuildSiteType.FORESTER_WORKSHOP, tablePos);
			if (existingBuildSite.isEmpty()) {
				continue;
			}
			SettlementConstruction.WorkstationBuildResult buildResult = SettlementConstruction.tryStartForesterWorkshopAtWorkstation(
				level,
				tablePos,
				existingBuildSite.get().facing(),
				settlement.id(),
				stock,
				existingBuildSite
			);

			if (buildResult.isStarted() || buildResult.isResumed()) {
				SettlementBuildSite previousBuildSite = buildSites.put(buildResult.buildSite().id(), buildResult.buildSite());
				changed |= !buildResult.buildSite().equals(previousBuildSite);
				changed |= SettlementVillagers.ensureForester(level, settlement);
			}
		}

		return changed;
	}

	private boolean advanceRegion(MinecraftServer server, RegionKey region, long currentTick) {
		boolean changed = false;

		for (Map.Entry<String, SettlementState> entry : settlements.entrySet()) {
			SettlementState settlement = entry.getValue();

			if (!settlement.region().equals(region) || settlement.lastUpdateTick() >= currentTick) {
				continue;
			}

			ServerLevel level = server.getLevel(settlement.dimension());
			boolean loadedSettlement = level != null
				&& level.isLoaded(settlement.center())
				&& level.isPositionEntityTicking(settlement.center());
			SettlementState simulationInput = settlement;

			if (loadedSettlement && SettlementVillagers.usesActualVillagers(settlement)) {
				SettlementVillagers.ensureTrademaster(level, settlement);
				SettlementVillagers.ensureCarpenter(level, settlement);
				SettlementVillagers.ensureRoadwright(level, settlement);
				SettlementVillagers.ensureForester(level, settlement);
				SettlementVillagers.ensurePortmaster(level, settlement);
				SettlementVillagers.ensureFletcher(level, settlement);
				SettlementVillagers.ensureVillagerHomes(level, settlement);
				simulationInput = settlement.withPopulation(SettlementVillagers.censusPopulation(level, settlement));
			}

			long simulationStart = SettlementPerformanceLog.start();
			SettlementEconomySimulator.SimulationResult simulationResult = SettlementEconomySimulator.advanceSettlement(
				simulationInput,
				getRoutesForSettlement(settlement.id()),
				getSettlementsInDimension(server, settlement.dimension()),
				currentTick,
				level,
				loadedSettlement
			);
			SettlementPerformanceLog.warnIfSlow("economy_simulation", simulationInput, simulationStart, currentTick);

			SettlementState updatedSettlement = simulationResult.settlement();

			if (simulationResult.requestedVillagerSpawns() > 0 && level != null && level.isLoaded(updatedSettlement.center()) && level.isPositionEntityTicking(updatedSettlement.center()) && SettlementVillagers.usesActualVillagers(updatedSettlement)) {
				LiveVillages.LOGGER.info("Spawning {} villagers for settlement {}", simulationResult.requestedVillagerSpawns(), settlement.id());
				int spawnedVillagers = spawnSettlementVillagers(level, updatedSettlement, simulationResult.requestedVillagerSpawns());

				if (spawnedVillagers > 0) {
					SettlementVillagers.ensureTrademaster(level, updatedSettlement);
					SettlementVillagers.ensureCarpenter(level, updatedSettlement);
					SettlementVillagers.ensureRoadwright(level, updatedSettlement);
					SettlementVillagers.ensureForester(level, updatedSettlement);
					SettlementVillagers.ensureFletcher(level, updatedSettlement);
					SettlementVillagers.ensureVillagerHomes(level, updatedSettlement);
					updatedSettlement = updatedSettlement
						.withGrowthProgress(Math.max(0.0D, updatedSettlement.growthProgress() - spawnedVillagers))
						.withPopulation(SettlementVillagers.censusPopulation(level, updatedSettlement));
				}
			}

			if (!updatedSettlement.equals(settlement)) {
				entry.setValue(updatedSettlement);
				changed = true;
			}

			for (RouteState route : simulationResult.createdRoutes()) {
				RouteState previousRoute = routes.put(route.id(), route);

				if (!route.equals(previousRoute)) {
					changed = true;
				}
			}
		}

		Map<String, List<SettlementBuildSite>> buildSitesBySettlement = indexBuildSitesBySettlement();

		for (Map.Entry<String, RouteState> entry : routes.entrySet()) {
			RouteState route = entry.getValue();
			Optional<RegionKey> routeRegion = getRouteRegion(route);

			if (routeRegion.isEmpty() || !routeRegion.get().equals(region) || route.lastSurveyTick() >= currentTick) {
				continue;
			}

			ServerLevel level = server.getLevel(route.dimension());
			SettlementState fromSettlement = settlements.get(route.fromSettlementId());
			SettlementState toSettlement = settlements.get(route.toSettlementId());

			if (level == null || fromSettlement == null || toSettlement == null) {
				RouteState updatedRoute = route.withLastSurveyTick(currentTick);

				if (!updatedRoute.equals(route)) {
					entry.setValue(updatedRoute);
					changed = true;
				}

				continue;
			}

			RouteNetworkSimulator.RouteAdvanceResult routeAdvanceResult = RouteNetworkSimulator.advanceRoute(
				level,
				route,
				fromSettlement,
				toSettlement,
				buildSitesBySettlement.getOrDefault(fromSettlement.id(), List.of()),
				buildSitesBySettlement.getOrDefault(toSettlement.id(), List.of()),
				currentTick
			);

			if (!routeAdvanceResult.route().equals(route)) {
				entry.setValue(routeAdvanceResult.route());
				changed = true;
			}

			if (!routeAdvanceResult.fromSettlement().equals(fromSettlement)) {
				settlements.put(fromSettlement.id(), routeAdvanceResult.fromSettlement());
				changed = true;
			}

			if (!routeAdvanceResult.toSettlement().equals(toSettlement)) {
				settlements.put(toSettlement.id(), routeAdvanceResult.toSettlement());
				changed = true;
			}
		}

		return changed;
	}

	private Map<String, List<SettlementBuildSite>> indexBuildSitesBySettlement() {
		Map<String, List<SettlementBuildSite>> indexedBuildSites = new HashMap<>();

		for (SettlementBuildSite buildSite : buildSites.values()) {
			indexedBuildSites.computeIfAbsent(buildSite.settlementId(), key -> new ArrayList<>()).add(buildSite);
		}

		return indexedBuildSites;
	}

	private boolean shouldEnsureBootstrapVillager(SettlementState settlement, long currentTick) {
		if (settlement.kind() != SettlementKind.CUSTOM) {
			return false;
		}

		if ((currentTick - settlement.createdTick()) < CUSTOM_SETTLEMENT_BOOTSTRAP_DELAY_TICKS) {
			return false;
		}

		long lastSpawnTick = bootstrapVillagerSpawnTicks.getOrDefault(settlement.id(), 0L);
		return lastSpawnTick <= 0L || (currentTick - lastSpawnTick) >= CUSTOM_SETTLEMENT_BOOTSTRAP_RETRY_TICKS;
	}

	private static int spawnSettlementVillagers(ServerLevel level, SettlementState settlement, int requestedVillagers) {
		int spawnedVillagers = 0;

		for (int i = 0; i < requestedVillagers; i++) {
			Optional<BlockPos> spawnPos = SettlementVillagers.findSpawnPos(level, settlement.center(), CUSTOM_SETTLEMENT_SPAWN_SEARCH_RADIUS);

			if (spawnPos.isEmpty() || !SettlementVillagers.spawnVillager(level, spawnPos.get())) {
				break;
			}

			spawnedVillagers++;
		}

		return spawnedVillagers;
	}

	private List<SettlementState> getSettlementsInDimension(MinecraftServer server, net.minecraft.resources.ResourceKey<Level> dimension) {
		if (server.getLevel(dimension) == null) {
			return List.of();
		}

		return settlements.values().stream()
			.filter(settlement -> settlement.dimension().equals(dimension))
			.toList();
	}

	private List<RegionKey> getOrderedRegions() {
		TreeSet<RegionKey> orderedRegions = new TreeSet<>(RegionKey.COMPARATOR);

		for (SettlementState settlement : settlements.values()) {
			orderedRegions.add(settlement.region());
		}

		for (RouteState route : routes.values()) {
			getRouteRegion(route).ifPresent(orderedRegions::add);
		}

		return new ArrayList<>(orderedRegions);
	}

	private Optional<RegionKey> getRouteRegion(RouteState route) {
		SettlementState from = settlements.get(route.fromSettlementId());
		SettlementState to = settlements.get(route.toSettlementId());

		if (from != null && to != null && from.dimension().equals(route.dimension()) && to.dimension().equals(route.dimension())) {
			return Optional.of(RegionKey.midpoint(route.dimension(), from.center(), to.center()));
		}

		if (from != null && from.dimension().equals(route.dimension())) {
			return Optional.of(from.region());
		}

		if (to != null && to.dimension().equals(route.dimension())) {
			return Optional.of(to.region());
		}

		return Optional.empty();
	}
}
