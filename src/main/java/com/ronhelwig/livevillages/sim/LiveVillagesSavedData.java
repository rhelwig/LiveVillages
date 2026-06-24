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
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.core.Holder;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;

import com.ronhelwig.livevillages.LiveVillages;
import com.ronhelwig.livevillages.LiveVillagesGameRules;
import com.ronhelwig.livevillages.block.ShelterAnchorBlock;
import com.ronhelwig.livevillages.block.TradeBoardBlock;
import com.ronhelwig.livevillages.block.entity.TradeBoardBlockEntity;
import com.ronhelwig.livevillages.content.LiveVillagesBlocks;

public class LiveVillagesSavedData extends SavedData {
	private static final long CUSTOM_SETTLEMENT_BOOTSTRAP_DELAY_TICKS = (long) SettlementEconomyRules.TICKS_PER_DAY;
	private static final long CUSTOM_SETTLEMENT_BOOTSTRAP_RETRY_TICKS = 6_000L;
	private static final int CUSTOM_SETTLEMENT_VILLAGER_RADIUS_BLOCKS = 32;
	private static final int CUSTOM_SETTLEMENT_SPAWN_SEARCH_RADIUS = 6;
	private static final long POPULATION_DIAGNOSTIC_INTERVAL_TICKS = 1_200L;
	private static final int SCRIBE_ROUTE_EXCHANGE_ONE_SIDED_LIMIT = 2;
	private static final int SCRIBE_ROUTE_EXCHANGE_TWO_SIDED_LIMIT = 3;
	public static final int SHARED_MAP_SAMPLE_STRIDE_BLOCKS = 4;
	public static final long SURVEY_CACHE_DURATION_TICKS = 2_000L; // Cache survey for 100 seconds
	private static final long DEFAULT_LOADED_ROADWORK_CATCHUP_TICKS = (long) SettlementEconomyRules.TICKS_PER_DAY;
	private static final int SHARED_MAP_PLAYER_OBSERVATION_RADIUS_BLOCKS = 32;
	private static final int SHARED_MAP_MAX_SAMPLES_PER_UPDATE = 1_200;
	private static final int SHARED_MAP_REFRESH_TICKS = 600;
	private static final int SHARED_MAP_MAX_CELLS_PER_DIMENSION = 40_000;
	private static final int LOADED_CONSTRUCTION_DISCOVERY_INTERVAL_TICKS = 600;
	private static final int LOADED_CONSTRUCTION_HOME_INTERVAL_TICKS = 200;
	private static final int LOADED_ROADWORK_MAINTENANCE_INTERVAL_TICKS = 200;
	private static final int VIRTUAL_TRADING_POST_BELL_SEARCH_RADIUS_BLOCKS = 48;
	private static final int VIRTUAL_TRADING_POST_BELL_SEARCH_HALF_HEIGHT_BLOCKS = 24;
	private static final int[] VIRTUAL_TRADING_POST_BELL_SEARCH_RADII = { 3, 4, 5, 6, 8, 10, 12, 16, 20, 24 };
	private static final int[] VIRTUAL_TRADING_POST_SEARCH_RADII = { 6, 8, 10, 12, 14, 16, 18, 22, 26, 30 };
	private static final int VIRTUAL_TRADING_POST_SIDE_SEARCH_BLOCKS = 5;
	private static final String SHELTER_MARKER_MATERIAL_CREDIT_KEY = "__shelter_marker_material_credit__";
	private static final int[] AUTONOMOUS_WORKSTATION_BELL_SEARCH_RADII = { 5, 6, 8, 10, 12, 16, 20, 24, 28 };
	private static final int[] AUTONOMOUS_WORKSTATION_SEARCH_RADII = { 8, 10, 12, 14, 16, 20, 24, 28, 32 };
	private static final int AUTONOMOUS_WORKSTATION_SIDE_SEARCH_BLOCKS = 7;
	private static final int AUTONOMOUS_WORKSTATION_BUILD_SITE_SPACING_BLOCKS = 8;
	private static final int AUTONOMOUS_WORKSTATION_SUPPORT_SPACING_BLOCKS = 12;
	private static final int MAX_AUTONOMOUS_WORKSTATION_START_ATTEMPTS_PER_PASS = 4;
	private static final long AUTONOMOUS_WORKSTATION_FAILED_RETRY_TICKS = 2_400L;
	private static final Set<String> WOOD_CONSTRUCTION_MATERIAL_KEYS = Set.of(
		"logs",
		"planks",
		"slab",
		"stairs",
		"stick",
		"fence",
		"fence_gate",
		"door",
		"trapdoor",
		"chest",
		"carpenter_bench",
		"forester_table"
	);
	private static final Set<String> STONE_AND_ORE_CONSTRUCTION_MATERIAL_KEYS = Set.of(
		"cobblestone",
		"stone",
		"smooth_stone",
		"stone_bricks",
		"iron_ingot",
		"copper_ingot",
		"coal",
		"miner_workstation"
	);
	private static final Map<String, Integer> VIRTUAL_TRADING_POST_STARTER_STOCK = Map.of(
		"bread", 56,
		"baked_potato", 24,
		"logs", 88,
		"planks", 149,
		"cobblestone", 106,
		"stick", 24,
		"dirt", 48
	);
	private static final com.mojang.serialization.MapCodec<SupplementalPersistence> SUPPLEMENTAL_PERSISTENCE_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING, OutpostPlayerStanding.CODEC)).optionalFieldOf("outpost_player_standings", Map.of()).forGetter(SupplementalPersistence::playerStandings),
		Codec.unboundedMap(Codec.STRING, OutpostRaidState.CODEC).optionalFieldOf("outpost_raids", Map.of()).forGetter(SupplementalPersistence::raids),
		Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf()).optionalFieldOf("scribe_recipe_ledgers", Map.of()).forGetter(SupplementalPersistence::scribeRecipeLedgers)
	).apply(instance, SupplementalPersistence::new));
	private static final Codec<LiveVillagesSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.unboundedMap(Codec.STRING, SettlementState.CODEC).optionalFieldOf("settlements", Map.of()).forGetter(data -> data.settlements),
		Codec.unboundedMap(Codec.STRING, RouteState.CODEC).optionalFieldOf("routes", Map.of()).forGetter(data -> data.routes),
		Codec.unboundedMap(Codec.STRING, SettlementBuildSite.CODEC).optionalFieldOf("build_sites", Map.of()).forGetter(data -> data.buildSites),
		Codec.unboundedMap(Codec.STRING, SettlementConstructionDelivery.CODEC).optionalFieldOf("construction_deliveries", Map.of()).forGetter(data -> data.constructionDeliveries),
		Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("bootstrap_villager_spawn_ticks", Map.of()).forGetter(data -> data.bootstrapVillagerSpawnTicks),
		Codec.unboundedMap(Codec.STRING, SharedTerrainCell.CODEC).optionalFieldOf("shared_terrain_knowledge", Map.of()).forGetter(data -> data.sharedTerrainKnowledge),
		Codec.unboundedMap(Codec.STRING, SettlementLoadedObservation.SurveyorObservation.CODEC).optionalFieldOf("saved_surveyor_observations", Map.of()).forGetter(data -> data.savedSurveyorObservations),
		Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING, SettlementRoadwrightWork.RoadworkDebugPlan.CODEC)).optionalFieldOf("saved_roadwork_plans", Map.of()).forGetter(data -> data.savedRoadworkPlans),
		Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING, Codec.INT)).optionalFieldOf("bakery_freebies_owed", Map.of()).forGetter(data -> data.bakeryFreebiesOwed),
		SUPPLEMENTAL_PERSISTENCE_CODEC.forGetter(data -> new SupplementalPersistence(data.outpostPlayerStandings, data.outpostRaidStates, data.scribeRecipeLedgers)),
		Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("villager_settlements", Map.of()).forGetter(data -> data.villagerSettlements),
		Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("preferred_villager_homes", Map.of()).forGetter(data -> data.preferredVillagerHomes),
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
	private final LinkedHashMap<String, Map<String, Integer>> bakeryFreebiesOwed;
	private final LinkedHashMap<String, List<String>> scribeRecipeLedgers;
	private final LinkedHashMap<String, Map<String, OutpostPlayerStanding>> outpostPlayerStandings;
	private final LinkedHashMap<String, OutpostRaidState> outpostRaidStates;
	private final LinkedHashMap<String, String> villagerSettlements;
	private final LinkedHashMap<String, Long> preferredVillagerHomes;
	private final LinkedHashMap<String, Long> loadedRoadworkCatchupTicks;
	private int nextRegionCursor;
	private long lastGlobalUpdateTick;
	private VillageScanCursor villageScanCursor;
	public final Map<String, CachedSurvey> surveyCache = new HashMap<>();
	private final Map<String, Integer> autonomousSupportStartOffsets = new HashMap<>();
	private final Map<String, Long> autonomousSupportRetryAfterTicks = new HashMap<>();
	private final Map<String, Long> populationDiagnosticTicks = new HashMap<>();

	public record CachedSurvey(SettlementConstruction.InfrastructureSurvey survey, long lastSurveyTick) {
	}

	private record OutpostPersistence(
		Map<String, Map<String, OutpostPlayerStanding>> playerStandings,
		Map<String, OutpostRaidState> raids
	) {
		private OutpostPersistence {
			playerStandings = playerStandings == null ? Map.of() : playerStandings;
			raids = raids == null ? Map.of() : raids;
		}
	}

	private record SupplementalPersistence(
		Map<String, Map<String, OutpostPlayerStanding>> playerStandings,
		Map<String, OutpostRaidState> raids,
		Map<String, List<String>> scribeRecipeLedgers
	) {
		private SupplementalPersistence {
			playerStandings = playerStandings == null ? Map.of() : playerStandings;
			raids = raids == null ? Map.of() : raids;
			scribeRecipeLedgers = scribeRecipeLedgers == null ? Map.of() : scribeRecipeLedgers;
		}
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
		this(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), new SupplementalPersistence(Map.of(), Map.of(), Map.of()), Map.of(), Map.of(), Map.of(), 0, 0L, Optional.empty());
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
		Map<String, Map<String, Integer>> bakeryFreebiesOwed,
		SupplementalPersistence supplementalPersistence,
		Map<String, String> villagerSettlements,
		Map<String, Long> preferredVillagerHomes,
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
		this.bakeryFreebiesOwed = new LinkedHashMap<>();
		bakeryFreebiesOwed.forEach((settlementId, playerFreebies) -> this.bakeryFreebiesOwed.put(settlementId, new LinkedHashMap<>(playerFreebies)));
		this.scribeRecipeLedgers = new LinkedHashMap<>();
		supplementalPersistence.scribeRecipeLedgers().forEach((settlementId, recipeIds) -> this.scribeRecipeLedgers.put(settlementId, sortedRecipeIds(recipeIds)));
		this.outpostPlayerStandings = new LinkedHashMap<>();
		supplementalPersistence.playerStandings().forEach((settlementId, playerStandings) -> this.outpostPlayerStandings.put(settlementId, new LinkedHashMap<>(playerStandings)));
		this.outpostRaidStates = new LinkedHashMap<>(supplementalPersistence.raids());
		this.villagerSettlements = new LinkedHashMap<>(villagerSettlements);
		this.preferredVillagerHomes = new LinkedHashMap<>(preferredVillagerHomes);
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

	public Collection<OutpostRaidState> outpostRaidStates() {
		return Collections.unmodifiableCollection(outpostRaidStates.values());
	}

	public List<String> knownScribeRecipeIds(String settlementId) {
		if (settlementId == null || settlementId.isBlank()) {
			return List.of();
		}

		return List.copyOf(scribeRecipeLedgers.getOrDefault(settlementId, List.of()));
	}

	public List<String> ensureScribeStarterRecipes(String settlementId, Collection<String> recipeIds) {
		if (settlementId == null || settlementId.isBlank() || recipeIds == null || recipeIds.isEmpty()) {
			return knownScribeRecipeIds(settlementId);
		}

		List<String> previous = scribeRecipeLedgers.getOrDefault(settlementId, List.of());
		List<String> updated = sortedRecipeIds(previous, recipeIds);

		if (!updated.equals(previous)) {
			scribeRecipeLedgers.put(settlementId, updated);
			setDirty();
		}

		return List.copyOf(updated);
	}

	public boolean addKnownScribeRecipe(String settlementId, String recipeId) {
		if (settlementId == null || settlementId.isBlank() || recipeId == null || recipeId.isBlank()) {
			return false;
		}

		List<String> previous = scribeRecipeLedgers.getOrDefault(settlementId, List.of());
		List<String> updated = sortedRecipeIds(previous, List.of(recipeId));

		if (updated.equals(previous)) {
			return false;
		}

		scribeRecipeLedgers.put(settlementId, updated);
		setDirty();
		return true;
	}

	public Optional<OutpostRaidState> outpostRaidState(String outpostSettlementId) {
		if (outpostSettlementId == null || outpostSettlementId.isBlank()) {
			return Optional.empty();
		}

		return Optional.ofNullable(outpostRaidStates.get(outpostSettlementId));
	}

	public void putOutpostRaidState(OutpostRaidState raidState) {
		if (raidState == null || raidState.outpostSettlementId().isBlank()) {
			return;
		}

		OutpostRaidState previous = outpostRaidStates.put(raidState.outpostSettlementId(), raidState);
		if (!raidState.equals(previous)) {
			setDirty();
		}
	}

	public void removeOutpostRaidState(String outpostSettlementId) {
		if (outpostSettlementId == null || outpostSettlementId.isBlank()) {
			return;
		}

		if (outpostRaidStates.remove(outpostSettlementId) != null) {
			setDirty();
		}
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

	public Optional<SettlementBuildSite> findBuildSite(String settlementId, SettlementBuildSiteType blueprintId) {
		return buildSites.values().stream()
			.filter(buildSite -> buildSite.settlementId().equals(settlementId))
			.filter(buildSite -> buildSite.blueprintId() == blueprintId)
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

			double distanceSquared = horizontalDistanceSqr(settlement.center(), position);

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

			double distanceSquared = horizontalDistanceSqr(settlement.center(), position);
			int settlementRadius = SettlementVillagers.settlementRadiusBlocks(settlement);

			if (distanceSquared > (double) settlementRadius * settlementRadius || distanceSquared >= nearestDistanceSquared) {
				continue;
			}

			nearestSettlement = settlement;
			nearestDistanceSquared = distanceSquared;
		}

		return Optional.ofNullable(nearestSettlement);
	}

	private static double horizontalDistanceSqr(BlockPos first, BlockPos second) {
		double dx = (first.getX() + 0.5D) - (second.getX() + 0.5D);
		double dz = (first.getZ() + 0.5D) - (second.getZ() + 0.5D);
		return (dx * dx) + (dz * dz);
	}

	private static List<String> sortedRecipeIds(Collection<String> recipeIds) {
		return sortedRecipeIds(List.of(), recipeIds);
	}

	private static List<String> sortedRecipeIds(Collection<String> first, Collection<String> second) {
		TreeSet<String> sorted = new TreeSet<>();
		addRecipeIds(sorted, first);
		addRecipeIds(sorted, second);
		return List.copyOf(sorted);
	}

	private static void addRecipeIds(Set<String> target, Collection<String> recipeIds) {
		if (recipeIds == null) {
			return;
		}

		for (String recipeId : recipeIds) {
			if (recipeId != null && !recipeId.isBlank()) {
				target.add(recipeId);
			}
		}
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

	public int bakeryFreebiesOwed(String settlementId, UUID playerId) {
		if (settlementId == null || playerId == null) {
			return 0;
		}

		Map<String, Integer> playerFreebies = bakeryFreebiesOwed.get(settlementId);
		if (playerFreebies == null) {
			return 0;
		}

		return Math.max(0, playerFreebies.getOrDefault(playerId.toString(), 0));
	}

	public void addBakeryFreebiesOwed(String settlementId, UUID playerId, int amount) {
		if (settlementId == null || settlementId.isBlank() || playerId == null || amount <= 0) {
			return;
		}

		Map<String, Integer> updated = new LinkedHashMap<>(bakeryFreebiesOwed.getOrDefault(settlementId, Map.of()));
		String playerKey = playerId.toString();
		updated.put(playerKey, updated.getOrDefault(playerKey, 0) + amount);
		bakeryFreebiesOwed.put(settlementId, updated);
		setDirty();
	}

	public boolean consumeBakeryFreebie(String settlementId, UUID playerId) {
		int current = bakeryFreebiesOwed(settlementId, playerId);
		if (current <= 0) {
			return false;
		}

		Map<String, Integer> updated = new LinkedHashMap<>(bakeryFreebiesOwed.getOrDefault(settlementId, Map.of()));
		String playerKey = playerId.toString();
		if (current == 1) {
			updated.remove(playerKey);
		} else {
			updated.put(playerKey, current - 1);
		}

		if (updated.isEmpty()) {
			bakeryFreebiesOwed.remove(settlementId);
		} else {
			bakeryFreebiesOwed.put(settlementId, updated);
		}

		setDirty();
		return true;
	}

	public OutpostPlayerStanding outpostPlayerStanding(String settlementId, UUID playerId) {
		if (settlementId == null || playerId == null) {
			return OutpostPlayerStanding.unknown();
		}

		Map<String, OutpostPlayerStanding> playerStandings = outpostPlayerStandings.get(settlementId);
		if (playerStandings == null) {
			return OutpostPlayerStanding.unknown();
		}

		return playerStandings.getOrDefault(playerId.toString(), OutpostPlayerStanding.unknown());
	}

	public OutpostPlayerStanding settlementPlayerStanding(SettlementState settlement, UUID playerId) {
		if (settlement == null || playerId == null) {
			return OutpostPlayerStanding.unknown();
		}

		Map<String, OutpostPlayerStanding> playerStandings = outpostPlayerStandings.get(settlement.id());
		if (playerStandings == null) {
			return SettlementPlayerStandings.defaultStanding(settlement.kind());
		}

		return playerStandings.getOrDefault(playerId.toString(), SettlementPlayerStandings.defaultStanding(settlement.kind()));
	}

	public OutpostPlayerStanding settlementPlayerStanding(SettlementState settlement, ServerPlayer player) {
		if (settlement == null || player == null) {
			return OutpostPlayerStanding.unknown();
		}

		Map<String, OutpostPlayerStanding> playerStandings = outpostPlayerStandings.get(settlement.id());
		OutpostPlayerStanding defaultStanding = SettlementPlayerStandings.defaultStanding(settlement.kind());
		if (playerStandings == null || playerStandings.isEmpty()) {
			return defaultStanding;
		}

		OutpostPlayerStanding uuidStanding = playerStandings.get(player.getUUID().toString());
		OutpostPlayerStanding nameStanding = playerStandings.get(playerStandingNameKey(player));
		OutpostPlayerStanding singleplayerStanding = playerStandings.get(singleplayerStandingKey());

		if (player.level().getServer().isSingleplayer()) {
			List<OutpostPlayerStanding> directStandings = new ArrayList<>();
			if (singleplayerStanding != null) {
				directStandings.add(singleplayerStanding);
			}
			if (uuidStanding != null) {
				directStandings.add(uuidStanding);
			}
			if (nameStanding != null) {
				directStandings.add(nameStanding);
			}

			OutpostPlayerStanding standing = singleplayerStanding != null
				? SettlementPlayerStandings.bestStanding(settlement.kind(), directStandings)
				: SettlementPlayerStandings.mergeHistoricalStandings(settlement.kind(), playerStandings.values());

			if (!standing.equals(defaultStanding)
				&& (singleplayerStanding == null || !singleplayerStanding.equals(standing) || uuidStanding == null || nameStanding == null)) {
				setSettlementPlayerStanding(settlement, player, standing);
			}

			return standing;
		}

		if (uuidStanding != null) {
			return uuidStanding;
		}

		if (nameStanding != null) {
			return nameStanding;
		}

		return defaultStanding;
	}

	public void setSettlementPlayerStanding(String settlementId, UUID playerId, OutpostPlayerStanding standing) {
		setOutpostPlayerStanding(settlementId, playerId, standing);
	}

	public void setSettlementPlayerStanding(SettlementState settlement, ServerPlayer player, OutpostPlayerStanding standing) {
		if (settlement == null || player == null || standing == null) {
			return;
		}

		setPlayerStandingEntries(
			settlement.id(),
			player.getUUID().toString(),
			playerStandingNameKey(player),
			player.level().getServer().isSingleplayer() ? singleplayerStandingKey() : null,
			standing
		);
	}

	public void setOutpostPlayerStanding(String settlementId, UUID playerId, OutpostPlayerStanding standing) {
		if (settlementId == null || settlementId.isBlank() || playerId == null || standing == null) {
			return;
		}

		setPlayerStandingEntries(settlementId, playerId.toString(), null, null, standing);
	}

	private void setPlayerStandingEntries(
		String settlementId,
		String primaryKey,
		String secondaryKey,
		String tertiaryKey,
		OutpostPlayerStanding standing
	) {
		if (settlementId == null || settlementId.isBlank() || primaryKey == null || primaryKey.isBlank() || standing == null) {
			return;
		}

		Map<String, OutpostPlayerStanding> updated = new LinkedHashMap<>(outpostPlayerStandings.getOrDefault(settlementId, Map.of()));
		boolean changed = !standing.equals(updated.put(primaryKey, standing));

		if (secondaryKey != null && !secondaryKey.isBlank()) {
			changed |= !standing.equals(updated.put(secondaryKey, standing));
		}

		if (tertiaryKey != null && !tertiaryKey.isBlank()) {
			changed |= !standing.equals(updated.put(tertiaryKey, standing));
		}

		if (!changed) {
			return;
		}

		outpostPlayerStandings.put(settlementId, updated);
		setDirty();
	}

	private static String playerStandingNameKey(ServerPlayer player) {
		return "name:" + player.getGameProfile().name().toLowerCase(java.util.Locale.ROOT);
	}

	private static String singleplayerStandingKey() {
		return "singleplayer";
	}

	public Optional<BlockPos> preferredVillagerHome(UUID villagerId) {
		if (villagerId == null) {
			return Optional.empty();
		}

		Long home = preferredVillagerHomes.get(villagerId.toString());
		return home == null ? Optional.empty() : Optional.of(BlockPos.of(home));
	}

	public Optional<String> villagerSettlement(UUID villagerId) {
		if (villagerId == null) {
			return Optional.empty();
		}

		String settlementId = villagerSettlements.get(villagerId.toString());
		return settlementId == null || settlementId.isBlank() ? Optional.empty() : Optional.of(settlementId);
	}

	public boolean setVillagerSettlement(UUID villagerId, String settlementId) {
		if (villagerId == null || settlementId == null || settlementId.isBlank()) {
			return false;
		}

		String previous = villagerSettlements.put(villagerId.toString(), settlementId);
		if (settlementId.equals(previous)) {
			return false;
		}

		setDirty();
		return true;
	}

	public boolean clearVillagerSettlement(UUID villagerId) {
		if (villagerId == null) {
			return false;
		}

		if (villagerSettlements.remove(villagerId.toString()) == null) {
			return false;
		}

		setDirty();
		return true;
	}

	public boolean setPreferredVillagerHome(UUID villagerId, BlockPos homePos) {
		if (villagerId == null || homePos == null) {
			return false;
		}

		Long previous = preferredVillagerHomes.put(villagerId.toString(), homePos.asLong());
		if (previous != null && previous == homePos.asLong()) {
			return false;
		}

		setDirty();
		return true;
	}

	public boolean clearPreferredVillagerHome(UUID villagerId) {
		if (villagerId == null) {
			return false;
		}

		if (preferredVillagerHomes.remove(villagerId.toString()) == null) {
			return false;
		}

		setDirty();
		return true;
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
		settlement = settlement.withTier(SettlementTiers.unlockedTier(settlement));
		SettlementState previous = settlements.put(settlement.id(), settlement);

		if (!settlement.equals(previous)) {
			setDirty();
		}
	}

	public SettlementState putSettlementAndRefreshBuildSiteMaterialStatus(SettlementState settlement, long currentTick) {
		boolean changed = false;
		settlement = settlement.withTier(SettlementTiers.unlockedTier(settlement));
		SettlementState previous = settlements.put(settlement.id(), settlement);

		if (!settlement.equals(previous)) {
			changed = true;
		}

		Map<String, Integer> reservedStock = new LinkedHashMap<>(settlement.stock());
		for (SettlementBuildSite buildSite : getBuildSitesForSettlement(settlement.id())) {
			SettlementBuildSite updatedBuildSite = SettlementConstruction.updateBuildSiteMaterialStatus(buildSite, reservedStock, currentTick);
			if (!updatedBuildSite.equals(buildSite)) {
				buildSites.put(updatedBuildSite.id(), updatedBuildSite);
				changed = true;
			}
		}

		if (changed) {
			setDirty();
		}

		return settlements.get(settlement.id());
	}

	public void removeSettlement(String settlementId) {
		boolean removed = settlements.remove(settlementId) != null;
		removed |= bootstrapVillagerSpawnTicks.remove(settlementId) != null;
		removed |= bakeryFreebiesOwed.remove(settlementId) != null;
		removed |= outpostPlayerStandings.remove(settlementId) != null;
		removed |= outpostRaidStates.remove(settlementId) != null;
		removed |= outpostRaidStates.entrySet().removeIf(entry -> entry.getValue().targetSettlementId().equals(settlementId));
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
				settlements.put(settlementId, settlement.withStock(stock).withTier(SettlementTiers.unlockedTier(settlement.withStock(stock))));
				deliveriesChanged = true;
			}
		}

		if (deliveriesChanged) {
			setDirty();
		}
	}

	public void removeBuildSite(String buildSiteId) {
		SettlementBuildSite removedBuildSite = buildSites.remove(buildSiteId);
		if (removedBuildSite == null) {
			return;
		}

		Map<String, Integer> returnedGoods = new LinkedHashMap<>();
		for (var iterator = constructionDeliveries.entrySet().iterator(); iterator.hasNext();) {
			SettlementConstructionDelivery delivery = iterator.next().getValue();

			if (!delivery.buildSiteId().equals(buildSiteId)) {
				continue;
			}

			if (!delivery.materialKey().isBlank()) {
				SettlementGoods.addGoods(returnedGoods, delivery.materialKey(), delivery.amount());
			}

			iterator.remove();
		}

		if (!returnedGoods.isEmpty()) {
			SettlementState settlement = settlements.get(removedBuildSite.settlementId());

			if (settlement != null) {
				Map<String, Integer> stock = new LinkedHashMap<>(settlement.stock());
				returnedGoods.forEach((goodsKey, amount) -> SettlementGoods.addGoods(stock, goodsKey, amount));
				putSettlement(settlement.withStock(stock));
			}
		}

		setDirty();
	}

	private boolean retireObsoleteLoadedPalisadeWallBuildSites(ServerLevel level, SettlementState settlement) {
		Set<String> obsoleteBuildSiteIds = SettlementConstruction.loadedWorldObsoletePalisadeWallBuildSiteIds(
			level,
			getBuildSitesForSettlement(settlement.id())
		);
		if (obsoleteBuildSiteIds.isEmpty()) {
			return false;
		}

		for (String buildSiteId : obsoleteBuildSiteIds) {
			removeBuildSite(buildSiteId);
		}

		return true;
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

			Map<String, Integer> actualPopulation = SettlementVillagers.censusPopulation(level, settlement);
			int actualTotalPopulation = actualPopulation.values().stream().mapToInt(Integer::intValue).sum();
			if (actualTotalPopulation > 0) {
				SettlementState updatedSettlement = settlement.withPopulation(actualPopulation);
				logPopulationDiagnostic(
					level,
					settlement,
					updatedSettlement,
					0,
					"bootstrap_blocked_actual_population",
					"actualTotal=" + actualTotalPopulation
				);

				if (!updatedSettlement.equals(settlement)) {
					entry.setValue(updatedSettlement);
					changed = true;
				}
				continue;
			}

			int nearbyVillagers = SettlementVillagers.countNearbyVillagers(level, settlement.center(), CUSTOM_SETTLEMENT_VILLAGER_RADIUS_BLOCKS);
			if (nearbyVillagers > 0) {
				logPopulationDiagnostic(level, settlement, settlement, 0, "bootstrap_blocked_nearby_villagers", "nearbyWithinBootstrapRadius=" + nearbyVillagers);
				continue;
			}

			Optional<BlockPos> spawnPos = SettlementVillagers.findSpawnPos(level, settlement.center(), CUSTOM_SETTLEMENT_SPAWN_SEARCH_RADIUS);

			if (spawnPos.isEmpty()) {
				logPopulationDiagnostic(level, settlement, settlement, 0, "bootstrap_no_spawn_pos", "searchRadius=" + CUSTOM_SETTLEMENT_SPAWN_SEARCH_RADIUS);
				continue;
			}

			if (!SettlementVillagers.spawnVillager(level, spawnPos.get())) {
				logPopulationDiagnostic(level, settlement, settlement, 0, "bootstrap_spawn_failed", "spawnPos=" + spawnPos.get().toShortString());
				continue;
			}

			Long previousSpawnTick = bootstrapVillagerSpawnTicks.put(settlement.id(), currentTick);
			SettlementVillagers.ensureWorkforce(level, settlement);
			SettlementVillagers.ensureVillagerHomes(level, settlement);
			SettlementVillagers.ensureVillagerGatheringPoint(level, settlement);
			SettlementState updatedSettlement = settlement.withPopulation(SettlementVillagers.censusPopulation(level, settlement));
			logPopulationDiagnostic(level, settlement, updatedSettlement, 1, "bootstrap_spawned", "spawnPos=" + spawnPos.get().toShortString());

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
			changed |= ensureWorkforceIfNeeded(level, settlement);
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
			SettlementTrademasterWork.maintainLoadedTradeManagement(level, workingSettlement, activeBuildSites);
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
			stockChanged |= SettlementShepherdWork.maintainLoadedShepherding(
				level,
				workingSettlement,
				stock,
				getRoutesForSettlement(workingSettlement.id()).size()
			);
			stockChanged |= SettlementFishermanWork.maintainLoadedFishing(level, workingSettlement, stock);
			stockChanged |= SettlementForesterWork.maintainLoadedForestry(level, workingSettlement, stock);
			stockChanged |= SettlementGardenerWork.maintainLoadedGardening(level, workingSettlement, stock);
			stockChanged |= SettlementBeekeeperWork.maintainLoadedBeekeeping(level, workingSettlement, stock);
			stockChanged |= SettlementMinerWork.maintainLoadedMining(level, workingSettlement, stock, activeBuildSites);
			stockChanged |= SettlementCarpenterWork.maintainLoadedCarpentry(level, workingSettlement, stock, activeBuildSites);
			stockChanged |= SettlementBakerWork.maintainLoadedBaking(level, workingSettlement, stock, activeBuildSites);
			stockChanged |= SettlementFletcherWork.maintainLoadedFletching(level, workingSettlement, stock, activeBuildSites);
			stockChanged |= SettlementMasonWork.maintainLoadedMasonry(level, workingSettlement, stock, activeBuildSites);
			stockChanged |= SettlementVanillaProfessionWork.maintainLoadedVanillaProfessionWork(level, workingSettlement, stock);
			changed |= SettlementScribeWork.maintainLoadedScribing(level, workingSettlement, this);
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
			Map<String, Integer> stock = new LinkedHashMap<>(workingSettlement.stock());
			boolean stockChanged = SettlementDefenseWork.maintainLoadedDefense(
				level,
				workingSettlement,
				stock,
				getBuildSitesForSettlement(workingSettlement.id()),
				getConstructionDeliveries(),
				getSettlementsInDimension(server, settlement.dimension()),
				getRoutesForSettlement(workingSettlement.id())
			);
			SettlementAccessWork.maintainLoadedDefensiveAccess(
				level,
				workingSettlement,
				getBuildSitesForSettlement(workingSettlement.id())
			);
			SettlementState updatedSettlement = stockChanged ? workingSettlement.withStock(stock) : workingSettlement;

			if (!updatedSettlement.equals(settlement)) {
				entry.setValue(updatedSettlement);
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
				|| level == null || !level.isLoaded(settlement.center()) || !level.isPositionEntityTicking(settlement.center())) {
				continue;
			}

			long taskStart = SettlementPerformanceLog.start();

			if (settlement.kind() == SettlementKind.OUTPOST) {
				Map<String, Integer> actualPopulation = OutpostSettlementWork.censusPopulation(level, settlement);
				SettlementState workingSettlement = actualPopulation.equals(settlement.population())
					? settlement
					: settlement.withPopulation(actualPopulation);
				Map<String, Integer> stock = new LinkedHashMap<>(workingSettlement.stock());
				changed |= OutpostGear.maintainOutpostEquipment(level, workingSettlement, stock) > 0;
				List<SettlementBuildSite> activeBuildSites = getBuildSitesForSettlement(settlement.id());
				boolean discoveryDue = activeBuildSites.isEmpty()
					|| isThrottledConstructionStepDue(workingSettlement, currentTick, "construction_discovery", LOADED_CONSTRUCTION_DISCOVERY_INTERVAL_TICKS);

				if (discoveryDue) {
					long discoveryStepStart = System.nanoTime();
					SettlementConstruction.PlacedWorkstations placedWorkstations = SettlementConstruction.scanPlacedWorkstations(level, workingSettlement);
					changed |= tryStartPlacedWorkstationBuildSites(level, workingSettlement, stock, placedWorkstations);
					discoveryStepStart = warnIfConstructionDiscoverySlow("outpost_placed_workstations", workingSettlement, discoveryStepStart);
					changed |= retireObsoleteLoadedPalisadeWallBuildSites(level, workingSettlement);
					warnIfConstructionDiscoverySlow("outpost_palisade_retirement", workingSettlement, discoveryStepStart);
					activeBuildSites = getBuildSitesForSettlement(settlement.id());
				}

				SettlementConstructionWork.ConstructionWorkResult constructionResult = SettlementConstructionWork.maintainLoadedOutpostConstruction(
					level,
					workingSettlement,
					stock,
					activeBuildSites,
					OutpostSettlementWork.canWorkThisPass(workingSettlement, currentTick, intervalTicks, actualPopulation.values().stream().mapToInt(Integer::intValue).sum())
				);

				for (SettlementBuildSite buildSite : constructionResult.buildSites()) {
					SettlementBuildSite previousBuildSite = buildSites.put(buildSite.id(), buildSite);

					if (!buildSite.equals(previousBuildSite)) {
						changed = true;
					}
				}

				boolean stockChanged = !stock.equals(workingSettlement.stock());
				SettlementState updatedSettlement = stockChanged ? workingSettlement.withStock(stock) : workingSettlement;

				if (!updatedSettlement.equals(settlement)) {
					entry.setValue(updatedSettlement);
					changed = true;
				}

				changed |= constructionResult.worldChanged();
				SettlementPerformanceLog.warnIfSlow("loaded_outpost_construction", workingSettlement, taskStart, server.getTickCount());
				continue;
			}

			if (!SettlementVillagers.usesActualVillagers(settlement)) {
				continue;
			}

			Map<String, Integer> actualPopulation = SettlementVillagers.censusPopulation(level, settlement);
			SettlementState workingSettlement = actualPopulation.equals(settlement.population())
				? settlement
				: settlement.withPopulation(actualPopulation);
			Map<String, Integer> stock = new LinkedHashMap<>(workingSettlement.stock());
			List<SettlementBuildSite> activeBuildSites = getBuildSitesForSettlement(settlement.id());
			boolean discoveryDue = activeBuildSites.isEmpty()
				|| isThrottledConstructionStepDue(workingSettlement, currentTick, "construction_discovery", LOADED_CONSTRUCTION_DISCOVERY_INTERVAL_TICKS);
			boolean catchupMaterialized = false;

			if (discoveryDue) {
				long discoveryStepStart = System.nanoTime();
				changed |= retireObsoleteLoadedPalisadeWallBuildSites(level, workingSettlement);
				discoveryStepStart = warnIfConstructionDiscoverySlow("palisade_retirement", workingSettlement, discoveryStepStart);
				MaterializationResult tradingPostMaterialization = tryMaterializeVirtualTradingPost(level, workingSettlement, stock);
				changed |= tradingPostMaterialization.changed();
				catchupMaterialized |= tradingPostMaterialization.materialized();
				discoveryStepStart = warnIfConstructionDiscoverySlow("virtual_trading_post", workingSettlement, discoveryStepStart);

				if (!catchupMaterialized) {
					MaterializationResult supportMaterialization = tryMaterializeAutonomousSupportWorkstation(level, workingSettlement, stock);
					changed |= supportMaterialization.changed();
					catchupMaterialized |= supportMaterialization.materialized();
					discoveryStepStart = warnIfConstructionDiscoverySlow("autonomous_support", workingSettlement, discoveryStepStart);
				}

				if (!catchupMaterialized) {
					SettlementConstruction.PlacedWorkstations placedWorkstations = SettlementConstruction.scanPlacedWorkstations(level, workingSettlement);
					changed |= tryStartPlacedWorkstationBuildSites(level, workingSettlement, stock, placedWorkstations);
					warnIfConstructionDiscoverySlow("placed_workstations", workingSettlement, discoveryStepStart);
				}
				activeBuildSites = getBuildSitesForSettlement(settlement.id());
			}

			if (catchupMaterialized) {
				boolean stockChanged = !stock.equals(workingSettlement.stock());
				SettlementState updatedSettlement = stockChanged ? workingSettlement.withStock(stock) : workingSettlement;

				if (!updatedSettlement.equals(settlement)) {
					entry.setValue(updatedSettlement);
					changed = true;
				}

				SettlementPerformanceLog.warnIfSlow("loaded_construction_materialization", workingSettlement, taskStart, server.getTickCount());
				continue;
			}

			boolean homeMaintenanceDue = activeBuildSites.isEmpty()
				|| isThrottledConstructionStepDue(workingSettlement, currentTick, "construction_homes", LOADED_CONSTRUCTION_HOME_INTERVAL_TICKS);

			if (homeMaintenanceDue) {
				long homesStart = System.nanoTime();
				changed |= SettlementVillagers.ensureVillagerHomes(level, workingSettlement);
				changed |= SettlementVillagers.ensureVillagerGatheringPoint(level, workingSettlement);
				SettlementVillagers.logEveningReturnDiagnostics(level, workingSettlement);
				long homesTime = System.nanoTime() - homesStart;
				if (homesTime > 100_000_000) { // >100ms
					LiveVillages.LOGGER.warn("Ensure villager homes took {} ms for settlement {}", Math.round(homesTime / 1_000_000.0D), settlement.id());
				}
			}

			long lastRoadworkCatchupTick = loadedRoadworkCatchupTicks.getOrDefault(
				workingSettlement.id(),
				Math.max(0L, currentTick - DEFAULT_LOADED_ROADWORK_CATCHUP_TICKS)
			);
			long elapsedRoadworkCatchupTicks = Math.max(0L, currentTick - lastRoadworkCatchupTick);
			boolean roadworkCatchupChanged = false;
			boolean roadworkDue = isThrottledConstructionStepDue(workingSettlement, currentTick, "roadwork", LOADED_ROADWORK_MAINTENANCE_INTERVAL_TICKS);
			SettlementRoadwrightWork.RoadworkResult roadworkResult = SettlementRoadwrightWork.RoadworkResult.unchanged();

			if (roadworkDue && elapsedRoadworkCatchupTicks >= SettlementEconomyRules.MIN_SIMULATION_TICKS) {
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

			if (roadworkDue) {
				Long previousRoadworkCatchupTick = loadedRoadworkCatchupTicks.put(workingSettlement.id(), currentTick);
				changed |= previousRoadworkCatchupTick == null || previousRoadworkCatchupTick.longValue() != currentTick;
				long roadworkStart = System.nanoTime();
				roadworkResult = SettlementRoadwrightWork.maintainLoadedRoadwork(
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
			}

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

	private static long warnIfConstructionDiscoverySlow(String stepKey, SettlementState settlement, long stepStartNanos) {
		long elapsed = System.nanoTime() - stepStartNanos;
		if (elapsed > 50_000_000) {
			LiveVillages.LOGGER.warn(
				"Construction discovery step took {} ms: settlement={} step={}",
				Math.round(elapsed / 1_000_000.0D),
				settlement.id(),
				stepKey
			);
		}

		return System.nanoTime();
	}

	private static boolean isThrottledConstructionStepDue(SettlementState settlement, long currentTick, String operationKey, int intervalTicks) {
		int stepTicks = Math.max(1, LiveVillagesScheduler.TICKS_BETWEEN_CONSTRUCTION_MAINTENANCE);
		int stepSlots = Math.max(1, (intervalTicks + stepTicks - 1) / stepTicks);
		long step = Math.floorDiv(currentTick, stepTicks);
		long phase = stableModulo(settlement.id() + "|" + operationKey, stepSlots);
		return Math.floorMod(step - phase, stepSlots) == 0;
	}

	private static long stableModulo(String key, long modulo) {
		long hash = 1125899906842597L;

		for (int i = 0; i < key.length(); i++) {
			hash = (hash * 31L) + key.charAt(i);
		}

		return Math.floorMod(hash, modulo);
	}

	private static boolean ensureWorkforceIfNeeded(ServerLevel level, SettlementState settlement) {
		if (!SettlementVillagers.usesActualVillagers(settlement)) {
			return false;
		}

		long workforceStart = System.nanoTime();
		boolean changed = SettlementVillagers.ensureWorkforce(level, settlement);
		long workforceTime = System.nanoTime() - workforceStart;
		if (workforceTime > 50_000_000) {
			LiveVillages.LOGGER.warn("Ensure workforce took {} ms for settlement {}", Math.round(workforceTime / 1_000_000.0D), settlement.id());
		}

		return changed;
	}

	private boolean tryStartPlacedWorkstationBuildSites(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		SettlementConstruction.PlacedWorkstations workstations
	) {
		boolean changed = false;
		changed |= tryStartPlacedCarpenterWorkshopBuildSites(level, settlement, stock, workstations.carpenterBenches());
		changed |= tryStartPlacedBakeryBuildSites(level, settlement, stock, workstations.bakersCounters());
		changed |= tryStartPlacedBeekeeperApiaryBuildSites(level, settlement, stock, workstations.honeySeparators());
		changed |= tryStartPlacedMineEntranceBuildSites(level, settlement, stock, workstations.minerWorkstations());
		changed |= tryStartPlacedRoadwrightWorkshopBuildSites(level, settlement, stock, workstations.surveyorTables());
		changed |= tryStartPlacedForesterWorkshopBuildSites(level, settlement, stock, workstations.foresterTables());
		changed |= tryStartPlacedScribeOfficeBuildSites(level, settlement, stock, workstations.scribeDesks());
		changed |= tryStartPlacedGardenerShedBuildSites(level, settlement, stock, workstations.gardenerWorkstations());
		changed |= tryStartPlacedGuardPostBuildSites(level, settlement, stock, workstations.guardPosts());
		changed |= tryStartVanillaCartographerHouseBuildSites(level, settlement, stock, workstations.cartographyTables());
		changed |= tryStartVanillaButcherShopBuildSites(level, settlement, stock, workstations.smokers());
		changed |= tryStartVanillaMasonWorkshopBuildSites(level, settlement, stock, workstations.stonecutters());
		changed |= tryStartVanillaFletcherHutBuildSites(level, settlement, stock, workstations.fletchingTables());
		changed |= tryStartVanillaClericShrineBuildSites(level, settlement, stock, workstations.brewingStands());
		changed |= tryStartVanillaLeatherworkerWorkshopBuildSites(level, settlement, stock, workstations.cauldrons());
		changed |= tryStartVanillaLibraryBuildSites(level, settlement, stock, workstations.lecterns());
		changed |= tryStartVanillaShepherdHutBuildSites(level, settlement, stock, workstations.looms());
		changed |= tryStartVanillaSmithyBuildSites(level, settlement, stock, workstations.smithingWorkstations());
		changed |= tryStartPlacedTradeBoardBuildSites(level, settlement, stock, workstations.tradeBoards());
		changed |= tryStartPlacedPortmasterDockBuildSites(level, settlement, stock, workstations.portmasterAnchors());
		changed |= tryStartPlacedLighthouseBuildSites(level, settlement, stock, workstations.lighthouses());
		changed |= tryStartPlacedSimpleHousingShelterBuildSites(level, settlement, stock, workstations.simpleHousingShelters());
		changed |= tryStartPlacedHousingShelterBuildSites(level, settlement, stock, workstations.housingShelters());
		return changed;
	}

	private boolean tryStartVanillaCartographerHouseBuildSites(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, List<BlockPos> cartographyTables) {
		boolean changed = false;

		for (BlockPos tablePos : cartographyTables) {
			Optional<SettlementBuildSite> existingBuildSite = findBuildSite(settlement.id(), SettlementBuildSiteType.CARTOGRAPHER_HOUSE, tablePos);

			SettlementConstruction.WorkstationBuildResult buildResult = tryStartVanillaWorkstationBuild(
				level,
				tablePos,
				() -> SettlementConstruction.tryStartCartographerHouseAtWorkstation(
					level,
					tablePos,
					SettlementConstruction.cartographerHouseFacingFor(settlement, tablePos),
					settlement.id(),
					stock,
					existingBuildSite
				)
			);

			if (buildResult.isStarted() || buildResult.isResumed()) {
				SettlementBuildSite previousBuildSite = buildSites.put(buildResult.buildSite().id(), buildResult.buildSite());
				changed |= !buildResult.buildSite().equals(previousBuildSite);
				changed |= ensureWorkforceIfNeeded(level, settlement);
			}
		}

		return changed;
	}

	private SettlementConstruction.WorkstationBuildResult tryStartVanillaWorkstationBuild(
		ServerLevel level,
		BlockPos workstationPos,
		Supplier<SettlementConstruction.WorkstationBuildResult> starter
	) {
		SettlementConstruction.WorkstationBuildResult buildResult = SettlementConstruction.withBlockedStructureSignsSuppressed(starter);
		SettlementConstruction.removeCantBuildHereSignsAroundWorkstation(level, workstationPos);

		return buildResult;
	}

	private boolean tryStartPlacedTradeBoardBuildSites(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, List<BlockPos> tradeBoards) {
		boolean changed = false;

		for (BlockPos boardPos : tradeBoards) {
			if (!(level.getBlockEntity(boardPos) instanceof TradeBoardBlockEntity tradeBoard)
				|| !tradeBoard.resolveSettlement(level).id().equals(settlement.id())) {
				continue;
			}

			Optional<SettlementBuildSite> existingBuildSite = findBuildSite(settlement.id(), SettlementBuildSiteType.TRADING_POST, boardPos);
			if (existingBuildSite.isEmpty() && findBuildSite(settlement.id(), SettlementBuildSiteType.TRADING_POST).isPresent()) {
				continue;
			}

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
				changed |= ensureWorkforceIfNeeded(level, settlement);
			}
		}

		return changed;
	}

	private MaterializationResult tryMaterializeVirtualTradingPost(ServerLevel level, SettlementState settlement, Map<String, Integer> stock) {
		if (settlement.kind() == SettlementKind.OUTPOST
			|| findBuildSite(settlement.id(), SettlementBuildSiteType.TRADING_POST).isPresent()
			|| hasLinkedTradeBoard(level, settlement)
			|| !shouldMaterializeVirtualTradingPost(level, settlement)) {
			return MaterializationResult.unchanged();
		}

		boolean stockChanged = ensureVirtualTradingPostStarterStock(stock);
		List<VirtualTradeBoardSite> sites = virtualTradeBoardSites(level, settlement);
		int blockedAttempts = 0;
		int completedAttempts = 0;

		for (VirtualTradeBoardSite site : sites) {
			BlockState boardState = LiveVillagesBlocks.TRADE_BOARD.defaultBlockState().setValue(TradeBoardBlock.FACING, site.facing());
			level.setBlock(site.pos(), boardState, 3);
			if (level.getBlockEntity(site.pos()) instanceof TradeBoardBlockEntity tradeBoard) {
				tradeBoard.linkSettlement(settlement);
			}

			SettlementConstruction.WorkstationBuildResult buildResult = SettlementConstruction.tryStartTradingPostAtWorkstation(
				level,
				site.pos(),
				site.facing(),
				settlement.id(),
				stock,
				Optional.empty(),
				false
			);

			if (buildResult.isStarted() || buildResult.isResumed()) {
				SettlementBuildSite previousBuildSite = buildSites.put(buildResult.buildSite().id(), buildResult.buildSite());
				boolean changed = stockChanged || !buildResult.buildSite().equals(previousBuildSite);
				LiveVillages.LOGGER.info("Materialized virtual Trading Post for settlement {} with Trade Board at {}", settlement.id(), site.pos());
				return new MaterializationResult(changed, true);
			}

			if (buildResult.isBlocked()) {
				blockedAttempts++;
			} else if (buildResult.isCompleted()) {
				completedAttempts++;
			}

			level.setBlock(site.pos(), Blocks.AIR.defaultBlockState(), 3);
		}

		LiveVillages.LOGGER.warn(
			"Could not materialize virtual Trading Post for settlement {}; candidates={} blocked={} already_sheltered={}",
			settlement.id(),
			sites.size(),
			blockedAttempts,
			completedAttempts
		);
		return new MaterializationResult(stockChanged, false);
	}

	private MaterializationResult tryMaterializeAutonomousSupportWorkstation(ServerLevel level, SettlementState settlement, Map<String, Integer> stock) {
		if (settlement.kind() == SettlementKind.OUTPOST || settlement.totalPopulation() <= 0) {
			return MaterializationResult.unchanged();
		}

		for (AutonomousSupportNeed need : autonomousSupportNeeds(level, settlement, stock)) {
			if (tryMaterializeAutonomousSupportWorkstation(level, settlement, stock, need.type())) {
				return MaterializationResult.changedAndMaterialized();
			}
		}

		return MaterializationResult.unchanged();
	}

	private List<AutonomousSupportNeed> autonomousSupportNeeds(ServerLevel level, SettlementState settlement, Map<String, Integer> stock) {
		List<SettlementBuildSite> activeBuildSites = getBuildSitesForSettlement(settlement.id());
		List<AutonomousSupportNeed> needs = new ArrayList<>();
		int woodDemand = materialDemand(activeBuildSites, WOOD_CONSTRUCTION_MATERIAL_KEYS);
		int stoneDemand = materialDemand(activeBuildSites, STONE_AND_ORE_CONSTRUCTION_MATERIAL_KEYS);
		int logShortage = shortage(settlement, activeBuildSites, stock, "logs");
		int plankShortage = shortage(settlement, activeBuildSites, stock, "planks");
		int woodworkShortage = shortage(settlement, activeBuildSites, stock, "stairs")
			+ shortage(settlement, activeBuildSites, stock, "slab")
			+ shortage(settlement, activeBuildSites, stock, "stick");
		int cobbleShortage = shortage(settlement, activeBuildSites, stock, "cobblestone");
		int ironShortage = shortage(settlement, activeBuildSites, stock, "iron_ingot");
		int fuelShortage = Math.max(0, 8 - stock.getOrDefault("coal", 0) - stock.getOrDefault("charcoal", 0));
		SettlementConstruction.PlacedWorkstations placedWorkstations = SettlementConstruction.scanPlacedWorkstations(level, settlement);

		addAutonomousSupportNeed(needs, settlement, placedWorkstations, AutonomousSupportType.FORESTER, (logShortage * 3) + plankShortage + woodDemand + 80);
		addAutonomousSupportNeed(needs, settlement, placedWorkstations, AutonomousSupportType.CARPENTER, (plankShortage * 2) + woodworkShortage + woodDemand + 60);
		addAutonomousSupportNeed(needs, settlement, placedWorkstations, AutonomousSupportType.MINER, (cobbleShortage * 3) + (ironShortage * 2) + (fuelShortage * 3) + stoneDemand + 55);
		needs.sort((left, right) -> Integer.compare(right.score(), left.score()));
		return needs;
	}

	private void addAutonomousSupportNeed(
		List<AutonomousSupportNeed> needs,
		SettlementState settlement,
		SettlementConstruction.PlacedWorkstations placedWorkstations,
		AutonomousSupportType type,
		int score
	) {
		if (score <= type.minimumScore() || hasAutonomousSupportWorkstation(settlement, type, placedWorkstations)) {
			return;
		}

		needs.add(new AutonomousSupportNeed(type, score));
	}

	private int shortage(SettlementState settlement, List<SettlementBuildSite> activeBuildSites, Map<String, Integer> stock, String goodsKey) {
		int target = SettlementEconomyRules.targetForGoods(settlement, activeBuildSites, goodsKey);
		return Math.max(0, target - stock.getOrDefault(goodsKey, 0));
	}

	private int materialDemand(List<SettlementBuildSite> activeBuildSites, Set<String> materialKeys) {
		return (int) activeBuildSites.stream()
			.filter(buildSite -> !buildSite.complete())
			.flatMap(buildSite -> buildSite.blocks().stream())
			.filter(block -> materialKeys.contains(block.expectedMaterialKey()))
			.count();
	}

	private boolean hasAutonomousSupportWorkstation(ServerLevel level, SettlementState settlement, AutonomousSupportType type) {
		return hasAutonomousSupportWorkstation(settlement, type, SettlementConstruction.scanPlacedWorkstations(level, settlement));
	}

	private boolean hasAutonomousSupportWorkstation(
		SettlementState settlement,
		AutonomousSupportType type,
		SettlementConstruction.PlacedWorkstations placedWorkstations
	) {
		return switch (type) {
			case CARPENTER -> findBuildSite(settlement.id(), SettlementBuildSiteType.CARPENTER_WORKSHOP).isPresent()
				|| !placedWorkstations.carpenterBenches().isEmpty();
			case FORESTER -> findBuildSite(settlement.id(), SettlementBuildSiteType.FORESTER_WORKSHOP).isPresent()
				|| !placedWorkstations.foresterTables().isEmpty();
			case MINER -> findBuildSite(settlement.id(), SettlementBuildSiteType.MINE_ENTRANCE).isPresent()
				|| !placedWorkstations.minerWorkstations().isEmpty();
		};
	}

	private boolean tryMaterializeAutonomousSupportWorkstation(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		AutonomousSupportType type
	) {
		String retryKey = autonomousSupportRetryKey(settlement, type);
		long currentTick = level.getServer().getTickCount();

		if (hasAutonomousSupportWorkstation(level, settlement, type)) {
			autonomousSupportStartOffsets.remove(retryKey);
			autonomousSupportRetryAfterTicks.remove(retryKey);
			return false;
		}

		long retryAfterTick = autonomousSupportRetryAfterTicks.getOrDefault(retryKey, 0L);
		if (retryAfterTick > currentTick) {
			return false;
		}

		int blockedAttempts = 0;
		long sitesStart = System.nanoTime();
		List<VirtualWorkstationSite> sites = virtualWorkstationSites(level, settlement, false);
		long sitesTime = System.nanoTime() - sitesStart;
		if (sitesTime > 50_000_000) {
			LiveVillages.LOGGER.warn(
				"Autonomous {} candidate generation took {} ms for settlement {}; candidates={}",
				type.structureLabel(),
				Math.round(sitesTime / 1_000_000.0D),
				settlement.id(),
				sites.size()
			);
		}

		long clearanceStart = System.nanoTime();
		AutonomousWorkstationClearance clearance = autonomousWorkstationClearance(level, settlement);
		long clearanceTime = System.nanoTime() - clearanceStart;
		if (clearanceTime > 50_000_000) {
			LiveVillages.LOGGER.warn(
				"Autonomous {} clearance cache took {} ms for settlement {}; supportPositions={} buildSiteAnchors={} buildSiteBlocks={}",
				type.structureLabel(),
				Math.round(clearanceTime / 1_000_000.0D),
				settlement.id(),
				clearance.supportPositions().size(),
				clearance.buildSiteAnchors().size(),
				clearance.buildSiteBlocks().size()
			);
		}

		int attemptedSites = 0;
		int scannedSites = 0;

		if (sites.isEmpty()) {
			autonomousSupportStartOffsets.remove(retryKey);
			autonomousSupportRetryAfterTicks.put(retryKey, currentTick + AUTONOMOUS_WORKSTATION_FAILED_RETRY_TICKS);
			return false;
		}

		int startOffset = Math.floorMod(autonomousSupportStartOffsets.getOrDefault(retryKey, 0), sites.size());
		int maxAttempts = Math.min(sites.size(), MAX_AUTONOMOUS_WORKSTATION_START_ATTEMPTS_PER_PASS);

		for (int offset = 0; offset < sites.size() && attemptedSites < maxAttempts; offset++) {
			VirtualWorkstationSite site = sites.get((startOffset + offset) % sites.size());
			scannedSites++;

			if (!isAutonomousWorkstationCandidateClear(clearance, site.pos())) {
				continue;
			}

			attemptedSites++;
			level.setBlock(site.pos(), autonomousSupportWorkstationState(type), 3);
			long buildStart = System.nanoTime();
			SettlementConstruction.WorkstationBuildResult buildResult = tryStartAutonomousSupportBuildSite(level, settlement, stock, type, site);
			long buildTime = System.nanoTime() - buildStart;
			if (buildTime > 50_000_000) {
				LiveVillages.LOGGER.warn(
					"Autonomous {} build-site startup took {} ms for settlement {} at {}",
					type.structureLabel(),
					Math.round(buildTime / 1_000_000.0D),
					settlement.id(),
					site.pos()
				);
			}
			SettlementConstruction.removeCantBuildHereSignsAroundWorkstation(level, site.pos());

			if (buildResult.isStarted() || buildResult.isResumed()) {
				buildSites.put(buildResult.buildSite().id(), buildResult.buildSite());
				autonomousSupportStartOffsets.remove(retryKey);
				autonomousSupportRetryAfterTicks.remove(retryKey);
				LiveVillages.LOGGER.info(
					"Materialized autonomous {} for settlement {} with workstation at {}",
					type.structureLabel(),
					settlement.id(),
					site.pos()
				);
				return true;
			}

			if (buildResult.isCompleted()) {
				autonomousSupportStartOffsets.remove(retryKey);
				autonomousSupportRetryAfterTicks.remove(retryKey);
				LiveVillages.LOGGER.info(
					"Placed autonomous {} workstation for settlement {} inside existing shelter at {}",
					type.structureLabel(),
					settlement.id(),
					site.pos()
				);
				return true;
			}

			if (buildResult.isBlocked()) {
				blockedAttempts++;
			}

			level.setBlock(site.pos(), Blocks.AIR.defaultBlockState(), 3);
		}

		int nextOffset = (startOffset + Math.max(1, scannedSites)) % sites.size();
		autonomousSupportStartOffsets.put(retryKey, nextOffset);
		autonomousSupportRetryAfterTicks.put(retryKey, currentTick + AUTONOMOUS_WORKSTATION_FAILED_RETRY_TICKS);

		LiveVillages.LOGGER.warn(
			"Could not materialize autonomous {} for settlement {}; candidates={} scanned={} attempted={} blocked={}",
			type.structureLabel(),
			settlement.id(),
			sites.size(),
			scannedSites,
			attemptedSites,
			blockedAttempts
		);
		return false;
	}

	private String autonomousSupportRetryKey(SettlementState settlement, AutonomousSupportType type) {
		return settlement.id() + "|" + type.name();
	}

	private BlockState autonomousSupportWorkstationState(AutonomousSupportType type) {
		return switch (type) {
			case CARPENTER -> LiveVillagesBlocks.CARPENTER_BENCH.defaultBlockState();
			case FORESTER -> LiveVillagesBlocks.FORESTER_TABLE.defaultBlockState();
			case MINER -> LiveVillagesBlocks.MINER_WORKSTATION.defaultBlockState();
		};
	}

	private SettlementConstruction.WorkstationBuildResult tryStartAutonomousSupportBuildSite(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		AutonomousSupportType type,
		VirtualWorkstationSite site
	) {
		return SettlementConstruction.withBlockedStructureSignsSuppressed(() -> switch (type) {
			case CARPENTER -> SettlementConstruction.tryStartCarpenterWorkshopAtWorkstation(
				level,
				site.pos(),
				site.facing(),
				settlement.id(),
				stock,
				Optional.empty()
			);
			case FORESTER -> SettlementConstruction.tryStartForesterWorkshopAtWorkstation(
				level,
				site.pos(),
				site.facing(),
				settlement.id(),
				stock,
				Optional.empty()
			);
			case MINER -> SettlementConstruction.tryStartMineEntranceAtWorkstation(
				level,
				site.pos(),
				site.facing(),
				settlement.id(),
				stock,
				Optional.empty()
			);
		});
	}

	private List<VirtualWorkstationSite> virtualWorkstationSites(ServerLevel level, SettlementState settlement) {
		return virtualWorkstationSites(level, settlement, true);
	}

	private List<VirtualWorkstationSite> virtualWorkstationSites(ServerLevel level, SettlementState settlement, boolean requireClearCandidate) {
		List<VirtualWorkstationSite> sites = new ArrayList<>();
		Set<Long> seen = new HashSet<>();
		AutonomousWorkstationClearance clearance = requireClearCandidate ? autonomousWorkstationClearance(level, settlement) : null;

		nearestLoadedBell(level, settlement).ifPresent(bellPos ->
			addVirtualWorkstationSitesNearAnchor(level, sites, seen, bellPos, AUTONOMOUS_WORKSTATION_BELL_SEARCH_RADII, clearance)
		);

		addVirtualWorkstationSitesNearAnchor(level, sites, seen, settlement.center(), AUTONOMOUS_WORKSTATION_SEARCH_RADII, clearance);

		return sites;
	}

	private void addVirtualWorkstationSitesNearAnchor(
		ServerLevel level,
		List<VirtualWorkstationSite> sites,
		Set<Long> seen,
		BlockPos anchor,
		int[] searchRadii,
		AutonomousWorkstationClearance clearance
	) {
		for (int radius : searchRadii) {
			for (Direction facing : Direction.Plane.HORIZONTAL) {
				Direction lateral = facing.getClockWise();

				for (int sideOffset = 0; sideOffset <= AUTONOMOUS_WORKSTATION_SIDE_SEARCH_BLOCKS; sideOffset++) {
					for (int sign : sideOffset == 0 ? List.of(0) : List.of(-1, 1)) {
						BlockPos columnPos = anchor
							.relative(facing, radius)
							.relative(lateral, sideOffset * sign);
						virtualSupportWorkstationPlacementPos(level, columnPos).ifPresent(pos -> {
							if (seen.add(pos.asLong()) && (clearance == null || isAutonomousWorkstationCandidateClear(clearance, pos))) {
								sites.add(new VirtualWorkstationSite(pos, facing.getOpposite()));
							}
						});
					}
				}
			}
		}
	}

	private Optional<BlockPos> virtualSupportWorkstationPlacementPos(ServerLevel level, BlockPos columnPos) {
		Optional<BlockPos> placementPos = virtualTradeBoardPlacementPos(level, columnPos);
		if (placementPos.isEmpty()) {
			return Optional.empty();
		}

		BlockPos pos = placementPos.get();
		if (SettlementConstruction.isPositionInExistingShelteredStructure(level, pos)) {
			return Optional.empty();
		}

		BlockPos groundPos = pos.below();
		BlockState groundState = level.getBlockState(groundPos);
		if (groundState.is(BlockTags.DIRT)
			|| groundState.is(Blocks.GRASS_BLOCK)
			|| groundState.is(Blocks.DIRT_PATH)
			|| groundState.is(Blocks.SAND)
			|| groundState.is(Blocks.RED_SAND)
			|| groundState.is(Blocks.GRAVEL)
			|| groundState.is(Blocks.STONE)) {
			return placementPos;
		}

		return Optional.empty();
	}

	private AutonomousWorkstationClearance autonomousWorkstationClearance(ServerLevel level, SettlementState settlement) {
		List<BlockPos> supportPositions = SettlementConstruction.scanPlacedWorkstations(level, settlement).supportWorkstations();

		List<BlockPos> buildSiteAnchors = new ArrayList<>();
		List<BlockPos> buildSiteBlocks = new ArrayList<>();
		for (SettlementBuildSite buildSite : getBuildSitesForSettlement(settlement.id())) {
			buildSiteAnchors.add(buildSite.workstationPos());
			buildSiteAnchors.add(buildSite.anchorPos());

			for (SettlementBuildBlockState block : buildSite.blocks()) {
				SettlementConstruction.buildSiteBlockPos(buildSite, block).ifPresent(buildSiteBlocks::add);
			}
		}

		return new AutonomousWorkstationClearance(
			List.copyOf(supportPositions),
			List.copyOf(buildSiteAnchors),
			List.copyOf(buildSiteBlocks)
		);
	}

	private boolean isAutonomousWorkstationCandidateClear(AutonomousWorkstationClearance clearance, BlockPos pos) {
		int supportSpacingSquared = AUTONOMOUS_WORKSTATION_SUPPORT_SPACING_BLOCKS * AUTONOMOUS_WORKSTATION_SUPPORT_SPACING_BLOCKS;
		for (BlockPos supportPos : clearance.supportPositions()) {
			if (horizontalDistanceSquared(supportPos, pos) < supportSpacingSquared) {
				return false;
			}
		}

		for (BlockPos buildSiteAnchor : clearance.buildSiteAnchors()) {
			if (horizontalDistanceSquared(buildSiteAnchor, pos) < supportSpacingSquared) {
				return false;
			}
		}

		int buildSiteSpacingSquared = AUTONOMOUS_WORKSTATION_BUILD_SITE_SPACING_BLOCKS * AUTONOMOUS_WORKSTATION_BUILD_SITE_SPACING_BLOCKS;
		for (BlockPos buildSiteBlock : clearance.buildSiteBlocks()) {
			if (horizontalDistanceSquared(buildSiteBlock, pos) < buildSiteSpacingSquared) {
				return false;
			}
		}

		return true;
	}

	private int horizontalDistanceSquared(BlockPos left, BlockPos right) {
		int dx = left.getX() - right.getX();
		int dz = left.getZ() - right.getZ();
		return dx * dx + dz * dz;
	}

	private void cleanupAutonomousPlacementWarningSigns(ServerLevel level, SettlementState settlement) {
		for (VirtualWorkstationSite site : virtualWorkstationSites(level, settlement, false)) {
			SettlementConstruction.removeCantBuildHereSignsAroundWorkstation(level, site.pos());
		}
	}

	private boolean ensureVirtualTradingPostStarterStock(Map<String, Integer> stock) {
		boolean changed = false;

		for (Map.Entry<String, Integer> entry : VIRTUAL_TRADING_POST_STARTER_STOCK.entrySet()) {
			int current = stock.getOrDefault(entry.getKey(), 0);
			if (current < entry.getValue()) {
				stock.put(entry.getKey(), entry.getValue());
				changed = true;
			}
		}

		return changed;
	}

	private boolean hasLinkedTradeBoard(ServerLevel level, SettlementState settlement) {
		for (BlockPos boardPos : SettlementConstruction.findPlacedTradeBoards(level, settlement)) {
			if (level.getBlockEntity(boardPos) instanceof TradeBoardBlockEntity tradeBoard
				&& tradeBoard.resolveSettlement(level).id().equals(settlement.id())) {
				return true;
			}
		}

		return false;
	}

	private boolean shouldMaterializeVirtualTradingPost(ServerLevel level, SettlementState settlement) {
		return settlement.totalPopulation() > 0;
	}

	private List<VirtualTradeBoardSite> virtualTradeBoardSites(ServerLevel level, SettlementState settlement) {
		List<VirtualTradeBoardSite> sites = new ArrayList<>();
		Set<Long> seen = new HashSet<>();

		nearestLoadedBell(level, settlement).ifPresent(bellPos ->
			addVirtualTradeBoardSitesNearAnchor(level, sites, seen, bellPos, VIRTUAL_TRADING_POST_BELL_SEARCH_RADII)
		);

		addVirtualTradeBoardSitesNearAnchor(level, sites, seen, settlement.center(), VIRTUAL_TRADING_POST_SEARCH_RADII);

		return sites;
	}

	private void addVirtualTradeBoardSitesNearAnchor(
		ServerLevel level,
		List<VirtualTradeBoardSite> sites,
		Set<Long> seen,
		BlockPos anchor,
		int[] searchRadii
	) {
		for (int radius : searchRadii) {
			for (Direction facing : Direction.Plane.HORIZONTAL) {
				Direction lateral = facing.getClockWise();

				for (int sideOffset = 0; sideOffset <= VIRTUAL_TRADING_POST_SIDE_SEARCH_BLOCKS; sideOffset++) {
					for (int sign : sideOffset == 0 ? List.of(0) : List.of(-1, 1)) {
						BlockPos columnPos = anchor
							.relative(facing, radius)
							.relative(lateral, sideOffset * sign);
						virtualTradeBoardPlacementPos(level, columnPos).ifPresent(pos -> {
							if (seen.add(pos.asLong())) {
								sites.add(new VirtualTradeBoardSite(pos, facing.getOpposite()));
							}
						});
					}
				}
			}
		}
	}

	private Optional<BlockPos> nearestLoadedBell(ServerLevel level, SettlementState settlement) {
		int radius = Math.min(SettlementVillagers.settlementRadiusBlocks(settlement), VIRTUAL_TRADING_POST_BELL_SEARCH_RADIUS_BLOCKS);
		int minY = Math.max(level.getMinY(), settlement.center().getY() - VIRTUAL_TRADING_POST_BELL_SEARCH_HALF_HEIGHT_BLOCKS);
		int maxY = Math.min(level.getMaxY(), settlement.center().getY() + VIRTUAL_TRADING_POST_BELL_SEARCH_HALF_HEIGHT_BLOCKS);
		long maxDistanceSqr = (long) radius * radius;
		BlockPos nearestBell = null;
		double nearestDistanceSqr = Double.POSITIVE_INFINITY;
		BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();

		for (int x = settlement.center().getX() - radius; x <= settlement.center().getX() + radius; x++) {
			for (int z = settlement.center().getZ() - radius; z <= settlement.center().getZ() + radius; z++) {
				double horizontalDistanceSqr = settlement.center().distToCenterSqr(x + 0.5D, settlement.center().getY() + 0.5D, z + 0.5D);

				if (horizontalDistanceSqr > maxDistanceSqr) {
					continue;
				}

				scanPos.set(x, settlement.center().getY(), z);
				if (!level.hasChunkAt(scanPos)) {
					continue;
				}

				for (int y = minY; y <= maxY; y++) {
					scanPos.set(x, y, z);

					if (!level.getBlockState(scanPos).is(Blocks.BELL)) {
						continue;
					}

					double distanceSqr = scanPos.distSqr(settlement.center());
					if (distanceSqr < nearestDistanceSqr) {
						nearestDistanceSqr = distanceSqr;
						nearestBell = scanPos.immutable();
					}
				}
			}
		}

		return Optional.ofNullable(nearestBell);
	}

	private Optional<BlockPos> virtualTradeBoardPlacementPos(ServerLevel level, BlockPos columnPos) {
		if (!level.hasChunkAt(columnPos)) {
			return Optional.empty();
		}

		int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, columnPos.getX(), columnPos.getZ()) - 1;
		if (groundY < level.getMinY() || groundY > level.getMaxY() - 2) {
			return Optional.empty();
		}

		BlockPos groundPos = new BlockPos(columnPos.getX(), groundY, columnPos.getZ());
		BlockState groundState = level.getBlockState(groundPos);
		if (!groundState.isFaceSturdy(level, groundPos, Direction.UP)
			|| level.getFluidState(groundPos).is(FluidTags.WATER)) {
			return Optional.empty();
		}

		BlockPos boardPos = groundPos.above();
		if (!level.getBlockState(boardPos).isAir()
			|| !level.getBlockState(boardPos.above()).isAir()
			|| level.getFluidState(boardPos).is(FluidTags.WATER)) {
			return Optional.empty();
		}

		return Optional.of(boardPos.immutable());
	}

	private boolean tryStartVanillaButcherShopBuildSites(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, List<BlockPos> smokers) {
		boolean changed = false;

		for (BlockPos smokerPos : smokers) {
			Optional<SettlementBuildSite> existingBuildSite = findBuildSite(settlement.id(), SettlementBuildSiteType.BUTCHER_SHOP, smokerPos);

			SettlementConstruction.WorkstationBuildResult buildResult = tryStartVanillaWorkstationBuild(
				level,
				smokerPos,
				() -> SettlementConstruction.tryStartButcherShopAtWorkstation(
					level,
					smokerPos,
					SettlementConstruction.fletcherHutFacingFor(settlement, smokerPos),
					settlement.id(),
					stock,
					existingBuildSite
				)
			);

			if (buildResult.isStarted() || buildResult.isResumed()) {
				SettlementBuildSite previousBuildSite = buildSites.put(buildResult.buildSite().id(), buildResult.buildSite());
				changed |= !buildResult.buildSite().equals(previousBuildSite);
				changed |= ensureWorkforceIfNeeded(level, settlement);
			}
		}

		return changed;
	}

	private boolean tryStartVanillaMasonWorkshopBuildSites(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, List<BlockPos> stonecutters) {
		boolean changed = false;

		for (BlockPos stonecutterPos : stonecutters) {
			Optional<SettlementBuildSite> existingBuildSite = findBuildSite(settlement.id(), SettlementBuildSiteType.MASON_WORKSHOP, stonecutterPos);

			SettlementConstruction.WorkstationBuildResult buildResult = tryStartVanillaWorkstationBuild(
				level,
				stonecutterPos,
				() -> SettlementConstruction.tryStartMasonWorkshopAtWorkstation(
					level,
					stonecutterPos,
					SettlementConstruction.fletcherHutFacingFor(settlement, stonecutterPos),
					settlement.id(),
					stock,
					existingBuildSite
				)
			);

			if (buildResult.isStarted() || buildResult.isResumed()) {
				SettlementBuildSite previousBuildSite = buildSites.put(buildResult.buildSite().id(), buildResult.buildSite());
				changed |= !buildResult.buildSite().equals(previousBuildSite);
				changed |= ensureWorkforceIfNeeded(level, settlement);
			}
		}

		return changed;
	}

	private boolean tryStartPlacedPortmasterDockBuildSites(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, List<BlockPos> portmasterAnchors) {
		boolean changed = false;

		for (BlockPos anchorPos : portmasterAnchors) {
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
				changed |= ensureWorkforceIfNeeded(level, settlement);
			}
		}

		return changed;
	}

	private boolean tryStartPlacedLighthouseBuildSites(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, List<BlockPos> lighthouses) {
		boolean changed = false;

		for (BlockPos markerPos : lighthouses) {
			Optional<SettlementBuildSite> existingBuildSite = findBuildSite(settlement.id(), SettlementBuildSiteType.LIGHTHOUSE, markerPos);
			Map<String, Integer> stockBeforeLighthouse = new LinkedHashMap<>(stock);
			boolean contributedRecipeGoods = existingBuildSite.isEmpty();

			if (contributedRecipeGoods) {
				SettlementGoods.addGoods(stock, "cobblestone", 8);
				SettlementGoods.addGoods(stock, "campfire", 1);
			}

			SettlementConstruction.WorkstationBuildResult buildResult = SettlementConstruction.tryStartLighthouseAtMarker(
				level,
				markerPos,
				settlement.id(),
				stock,
				existingBuildSite
			);

			if (buildResult.isStarted() || buildResult.isResumed()) {
				if (buildResult.isStarted() || !stock.equals(stockBeforeLighthouse)) {
					SettlementState updatedSettlement = settlement.withStock(stock);
					settlements.put(settlement.id(), updatedSettlement.withTier(SettlementTiers.unlockedTier(updatedSettlement)));
					changed = true;
				}

				SettlementBuildSite previousBuildSite = buildSites.put(buildResult.buildSite().id(), buildResult.buildSite());
				changed |= !buildResult.buildSite().equals(previousBuildSite);
			} else if (contributedRecipeGoods) {
				SettlementGoods.consumeGoods(stock, "cobblestone", 8);
				SettlementGoods.consumeGoods(stock, "campfire", 1);
			}
		}

		return changed;
	}

	private boolean tryStartVanillaFletcherHutBuildSites(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, List<BlockPos> fletchingTables) {
		boolean changed = false;

		for (BlockPos tablePos : fletchingTables) {
			Optional<SettlementBuildSite> existingBuildSite = findBuildSite(settlement.id(), SettlementBuildSiteType.FLETCHER_HUT, tablePos);

			SettlementConstruction.WorkstationBuildResult buildResult = tryStartVanillaWorkstationBuild(
				level,
				tablePos,
				() -> SettlementConstruction.tryStartFletcherHutAtWorkstation(
					level,
					tablePos,
					SettlementConstruction.fletcherHutFacingFor(settlement, tablePos),
					settlement.id(),
					stock,
					existingBuildSite
				)
			);

			if (buildResult.isStarted() || buildResult.isResumed()) {
				SettlementBuildSite previousBuildSite = buildSites.put(buildResult.buildSite().id(), buildResult.buildSite());
				changed |= !buildResult.buildSite().equals(previousBuildSite);
				changed |= ensureWorkforceIfNeeded(level, settlement);
			}
		}

		return changed;
	}

	private boolean tryStartVanillaClericShrineBuildSites(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, List<BlockPos> brewingStands) {
		boolean changed = false;

		for (BlockPos brewingStandPos : brewingStands) {
			Optional<SettlementBuildSite> existingBuildSite = findBuildSite(settlement.id(), SettlementBuildSiteType.CLERIC_SHRINE, brewingStandPos);

			SettlementConstruction.WorkstationBuildResult buildResult = tryStartVanillaWorkstationBuild(
				level,
				brewingStandPos,
				() -> SettlementConstruction.tryStartClericShrineAtWorkstation(
					level,
					brewingStandPos,
					SettlementConstruction.fletcherHutFacingFor(settlement, brewingStandPos),
					settlement.id(),
					stock,
					existingBuildSite
				)
			);

			if (buildResult.isStarted() || buildResult.isResumed()) {
				SettlementBuildSite previousBuildSite = buildSites.put(buildResult.buildSite().id(), buildResult.buildSite());
				changed |= !buildResult.buildSite().equals(previousBuildSite);
				changed |= ensureWorkforceIfNeeded(level, settlement);
			}
		}

		return changed;
	}

	private boolean tryStartVanillaLeatherworkerWorkshopBuildSites(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, List<BlockPos> cauldrons) {
		boolean changed = false;

		for (BlockPos cauldronPos : cauldrons) {
			Optional<SettlementBuildSite> existingBuildSite = findBuildSite(settlement.id(), SettlementBuildSiteType.LEATHERWORKER_WORKSHOP, cauldronPos);

			SettlementConstruction.WorkstationBuildResult buildResult = tryStartVanillaWorkstationBuild(
				level,
				cauldronPos,
				() -> SettlementConstruction.tryStartLeatherworkerWorkshopAtWorkstation(
					level,
					cauldronPos,
					SettlementConstruction.fletcherHutFacingFor(settlement, cauldronPos),
					settlement.id(),
					stock,
					existingBuildSite
				)
			);

			if (buildResult.isStarted() || buildResult.isResumed()) {
				SettlementBuildSite previousBuildSite = buildSites.put(buildResult.buildSite().id(), buildResult.buildSite());
				changed |= !buildResult.buildSite().equals(previousBuildSite);
				changed |= ensureWorkforceIfNeeded(level, settlement);
			}
		}

		return changed;
	}

	private boolean tryStartVanillaLibraryBuildSites(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, List<BlockPos> lecterns) {
		boolean changed = false;

		for (BlockPos lecternPos : lecterns) {
			Optional<SettlementBuildSite> existingBuildSite = findBuildSite(settlement.id(), SettlementBuildSiteType.LIBRARY, lecternPos);

			SettlementConstruction.WorkstationBuildResult buildResult = tryStartVanillaWorkstationBuild(
				level,
				lecternPos,
				() -> SettlementConstruction.tryStartLibraryAtWorkstation(
					level,
					lecternPos,
					SettlementConstruction.fletcherHutFacingFor(settlement, lecternPos),
					settlement.id(),
					stock,
					existingBuildSite
				)
			);

			if (buildResult.isStarted() || buildResult.isResumed()) {
				SettlementBuildSite previousBuildSite = buildSites.put(buildResult.buildSite().id(), buildResult.buildSite());
				changed |= !buildResult.buildSite().equals(previousBuildSite);
				changed |= ensureWorkforceIfNeeded(level, settlement);
			}
		}

		return changed;
	}

	private boolean tryStartVanillaShepherdHutBuildSites(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, List<BlockPos> looms) {
		boolean changed = false;

		for (BlockPos loomPos : looms) {
			Optional<SettlementBuildSite> existingBuildSite = findBuildSite(settlement.id(), SettlementBuildSiteType.SHEPHERD_HUT, loomPos);

			SettlementConstruction.WorkstationBuildResult buildResult = tryStartVanillaWorkstationBuild(
				level,
				loomPos,
				() -> SettlementConstruction.tryStartShepherdHutAtWorkstation(
					level,
					loomPos,
					SettlementConstruction.fletcherHutFacingFor(settlement, loomPos),
					settlement.id(),
					stock,
					existingBuildSite
				)
			);

			if (buildResult.isStarted() || buildResult.isResumed()) {
				SettlementBuildSite previousBuildSite = buildSites.put(buildResult.buildSite().id(), buildResult.buildSite());
				changed |= !buildResult.buildSite().equals(previousBuildSite);
				changed |= ensureWorkforceIfNeeded(level, settlement);
			}
		}

		return changed;
	}

	private boolean tryStartVanillaSmithyBuildSites(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, List<BlockPos> smithingWorkstations) {
		boolean changed = false;

		for (BlockPos workstationPos : smithingWorkstations) {
			Optional<SettlementBuildSite> existingBuildSite = findBuildSite(settlement.id(), SettlementBuildSiteType.SMITHY, workstationPos);

			SettlementConstruction.WorkstationBuildResult buildResult = tryStartVanillaWorkstationBuild(
				level,
				workstationPos,
				() -> SettlementConstruction.tryStartSmithyAtWorkstation(
					level,
					workstationPos,
					SettlementConstruction.fletcherHutFacingFor(settlement, workstationPos),
					settlement.id(),
					stock,
					existingBuildSite
				)
			);

			if (buildResult.isStarted() || buildResult.isResumed()) {
				SettlementBuildSite previousBuildSite = buildSites.put(buildResult.buildSite().id(), buildResult.buildSite());
				changed |= !buildResult.buildSite().equals(previousBuildSite);
				changed |= ensureWorkforceIfNeeded(level, settlement);
			}
		}

		return changed;
	}

	private boolean tryStartPlacedCarpenterWorkshopBuildSites(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, List<BlockPos> carpenterBenches) {
		boolean changed = false;

		for (BlockPos benchPos : carpenterBenches) {
			Optional<SettlementBuildSite> existingBuildSite = findBuildSite(settlement.id(), SettlementBuildSiteType.CARPENTER_WORKSHOP, benchPos);
			SettlementConstruction.WorkstationBuildResult buildResult = SettlementConstruction.tryStartCarpenterWorkshopAtWorkstation(
				level,
				benchPos,
				existingBuildSite.map(SettlementBuildSite::facing).orElseGet(() -> SettlementConstruction.fletcherHutFacingFor(settlement, benchPos)),
				settlement.id(),
				stock,
				existingBuildSite
			);

			if (buildResult.isStarted() || buildResult.isResumed()) {
				SettlementBuildSite previousBuildSite = buildSites.put(buildResult.buildSite().id(), buildResult.buildSite());
				changed |= !buildResult.buildSite().equals(previousBuildSite);
				changed |= ensureWorkforceIfNeeded(level, settlement);
			}
		}

		return changed;
	}

	private boolean tryStartPlacedScribeOfficeBuildSites(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, List<BlockPos> scribeDesks) {
		boolean changed = false;

		for (BlockPos deskPos : scribeDesks) {
			Optional<SettlementBuildSite> existingBuildSite = findBuildSite(settlement.id(), SettlementBuildSiteType.SCRIBE_OFFICE, deskPos);

			SettlementConstruction.WorkstationBuildResult buildResult = SettlementConstruction.tryStartScribeOfficeAtWorkstation(
				level,
				deskPos,
				SettlementConstruction.fletcherHutFacingFor(settlement, deskPos),
				settlement.id(),
				stock,
				existingBuildSite
			);

			if (buildResult.isStarted() || buildResult.isResumed()) {
				SettlementBuildSite previousBuildSite = buildSites.put(buildResult.buildSite().id(), buildResult.buildSite());
				changed |= !buildResult.buildSite().equals(previousBuildSite);
				changed |= ensureWorkforceIfNeeded(level, settlement);
			}
		}

		return changed;
	}

	private boolean tryStartPlacedGuardPostBuildSites(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, List<BlockPos> guardPosts) {
		boolean changed = false;

		for (BlockPos guardPostPos : guardPosts) {
			Optional<SettlementBuildSite> existingBuildSite = findBuildSite(settlement.id(), SettlementBuildSiteType.GUARD_POST, guardPostPos);

			SettlementConstruction.WorkstationBuildResult buildResult = SettlementConstruction.tryStartGuardPostAtWorkstation(
				level,
				guardPostPos,
				SettlementConstruction.fletcherHutFacingFor(settlement, guardPostPos),
				settlement.id(),
				stock,
				existingBuildSite
			);

			if (buildResult.isStarted() || buildResult.isResumed()) {
				SettlementBuildSite previousBuildSite = buildSites.put(buildResult.buildSite().id(), buildResult.buildSite());
				changed |= !buildResult.buildSite().equals(previousBuildSite);
				changed |= ensureWorkforceIfNeeded(level, settlement);
			}
		}

		return changed;
	}

	private boolean tryStartPlacedGardenerShedBuildSites(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, List<BlockPos> gardenerWorkstations) {
		boolean changed = false;

		for (BlockPos workstationPos : gardenerWorkstations) {
			Optional<SettlementBuildSite> existingBuildSite = findBuildSite(settlement.id(), SettlementBuildSiteType.GARDENER_SHED, workstationPos);

			SettlementConstruction.WorkstationBuildResult buildResult = SettlementConstruction.tryStartGardenerShedAtWorkstation(
				level,
				workstationPos,
				SettlementConstruction.fletcherHutFacingFor(settlement, workstationPos),
				settlement.id(),
				stock,
				existingBuildSite
			);

			if (buildResult.isStarted() || buildResult.isResumed()) {
				SettlementBuildSite previousBuildSite = buildSites.put(buildResult.buildSite().id(), buildResult.buildSite());
				changed |= !buildResult.buildSite().equals(previousBuildSite);
				changed |= ensureWorkforceIfNeeded(level, settlement);
			}
		}

		return changed;
	}

	private boolean tryStartPlacedBeekeeperApiaryBuildSites(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, List<BlockPos> honeySeparators) {
		boolean changed = false;

		for (BlockPos separatorPos : honeySeparators) {
			Optional<SettlementBuildSite> existingBuildSite = findBuildSite(settlement.id(), SettlementBuildSiteType.BEEKEEPER_APIARY, separatorPos);

			SettlementConstruction.WorkstationBuildResult buildResult = SettlementConstruction.tryStartBeekeeperApiaryAtWorkstation(
				level,
				separatorPos,
				SettlementConstruction.fletcherHutFacingFor(settlement, separatorPos),
				settlement.id(),
				stock,
				existingBuildSite
			);

			if (buildResult.isStarted() || buildResult.isResumed()) {
				SettlementBuildSite previousBuildSite = buildSites.put(buildResult.buildSite().id(), buildResult.buildSite());
				changed |= !buildResult.buildSite().equals(previousBuildSite);
				changed |= ensureWorkforceIfNeeded(level, settlement);
			}
		}

		return changed;
	}

	private boolean tryStartPlacedSimpleHousingShelterBuildSites(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, List<BlockPos> shelterMarkers) {
		boolean changed = false;

		for (BlockPos markerPos : shelterMarkers) {
			Optional<SettlementBuildSite> existingBuildSite = findBuildSite(settlement.id(), SettlementBuildSiteType.SIMPLE_HOUSING_SHELTER, markerPos);

			SettlementConstruction.WorkstationBuildResult buildResult = SettlementConstruction.tryStartSimpleHousingShelterAtDoor(
				level,
				markerPos,
				shelterMarkerFacing(level, settlement, markerPos, existingBuildSite),
				settlement.id(),
				stock,
				existingBuildSite
			);

			buildResult = creditShelterMarkerRecipeMaterials(level, stock, existingBuildSite, buildResult, Map.of("bed", 1, "planks", 8));

			if (buildResult.isStarted() || buildResult.isResumed()) {
				SettlementBuildSite previousBuildSite = buildSites.put(buildResult.buildSite().id(), buildResult.buildSite());
				changed |= !buildResult.buildSite().equals(previousBuildSite);
				changed |= ensureWorkforceIfNeeded(level, settlement);
			}
		}

		return changed;
	}

	private boolean tryStartPlacedHousingShelterBuildSites(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, List<BlockPos> shelterMarkers) {
		boolean changed = false;

		for (BlockPos markerPos : shelterMarkers) {
			Optional<SettlementBuildSite> existingBuildSite = findBuildSite(settlement.id(), SettlementBuildSiteType.HOUSING_SHELTER, markerPos);

			SettlementConstruction.WorkstationBuildResult buildResult = SettlementConstruction.tryStartHousingShelterAtDoor(
				level,
				markerPos,
				shelterMarkerFacing(level, settlement, markerPos, existingBuildSite),
				settlement.id(),
				stock,
				existingBuildSite
			);

			buildResult = creditShelterMarkerRecipeMaterials(
				level,
				stock,
				existingBuildSite,
				buildResult,
				Map.of("bed", 2, "logs", 4, "planks", 1, "glass", 1, "lantern", 1)
			);

			if (buildResult.isStarted() || buildResult.isResumed()) {
				SettlementBuildSite previousBuildSite = buildSites.put(buildResult.buildSite().id(), buildResult.buildSite());
				changed |= !buildResult.buildSite().equals(previousBuildSite);
				changed |= ensureWorkforceIfNeeded(level, settlement);
			}
		}

		return changed;
	}

	private static Direction shelterMarkerFacing(ServerLevel level, SettlementState settlement, BlockPos markerPos, Optional<SettlementBuildSite> existingBuildSite) {
		if (existingBuildSite.isPresent()) {
			return existingBuildSite.get().facing();
		}

		BlockState state = level.getBlockState(markerPos);
		return state.hasProperty(ShelterAnchorBlock.FACING)
			? state.getValue(ShelterAnchorBlock.FACING)
			: SettlementConstruction.fletcherHutFacingFor(settlement, markerPos);
	}

	private static SettlementConstruction.WorkstationBuildResult creditShelterMarkerRecipeMaterials(
		ServerLevel level,
		Map<String, Integer> stock,
		Optional<SettlementBuildSite> existingBuildSite,
		SettlementConstruction.WorkstationBuildResult buildResult,
		Map<String, Integer> recipeGoods
	) {
		if ((!buildResult.isStarted() && !buildResult.isResumed()) || buildResult.buildSite() == null) {
			return buildResult;
		}

		if (buildResult.buildSite().siteMaterials().containsKey(SHELTER_MARKER_MATERIAL_CREDIT_KEY)) {
			return buildResult;
		}

		long tick = level.getServer().getTickCount();
		Map<String, Integer> creditedGoods = new LinkedHashMap<>(recipeGoods);
		creditedGoods.put(SHELTER_MARKER_MATERIAL_CREDIT_KEY, 1);
		SettlementBuildSite creditedBuildSite = SettlementConstruction.updateBuildSiteMaterialStatus(
			buildResult.buildSite().withAddedSiteMaterials(creditedGoods, tick),
			stock,
			tick
		);

		return buildResult.isStarted()
			? SettlementConstruction.WorkstationBuildResult.started(creditedBuildSite)
			: SettlementConstruction.WorkstationBuildResult.resumed(creditedBuildSite);
	}

	private boolean tryStartPlacedBakeryBuildSites(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, List<BlockPos> bakersCounters) {
		boolean changed = false;

		for (BlockPos workstationPos : bakersCounters) {
			Optional<SettlementBuildSite> existingBuildSite = findBuildSite(settlement.id(), SettlementBuildSiteType.BAKERY, workstationPos);

			SettlementConstruction.WorkstationBuildResult buildResult = SettlementConstruction.tryStartBakeryAtWorkstation(
				level,
				workstationPos,
				existingBuildSite.map(SettlementBuildSite::facing).orElseGet(() -> SettlementConstruction.fletcherHutFacingFor(settlement, workstationPos)),
				settlement.id(),
				stock,
				existingBuildSite
			);

			if (buildResult.isStarted() || buildResult.isResumed()) {
				SettlementBuildSite previousBuildSite = buildSites.put(buildResult.buildSite().id(), buildResult.buildSite());
				changed |= !buildResult.buildSite().equals(previousBuildSite);
				changed |= ensureWorkforceIfNeeded(level, settlement);
			}
		}

		return changed;
	}

	private boolean tryStartPlacedMineEntranceBuildSites(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, List<BlockPos> minerWorkstations) {
		boolean changed = false;

		for (BlockPos workstationPos : minerWorkstations) {
			Optional<SettlementBuildSite> existingBuildSite = findBuildSite(settlement.id(), SettlementBuildSiteType.MINE_ENTRANCE, workstationPos);

			SettlementConstruction.WorkstationBuildResult buildResult = SettlementConstruction.tryStartMineEntranceAtWorkstation(
				level,
				workstationPos,
				existingBuildSite.map(SettlementBuildSite::facing).orElseGet(() -> SettlementConstruction.fletcherHutFacingFor(settlement, workstationPos)),
				settlement.id(),
				stock,
				existingBuildSite
			);

			if (buildResult.isStarted() || buildResult.isResumed()) {
				SettlementBuildSite previousBuildSite = buildSites.put(buildResult.buildSite().id(), buildResult.buildSite());
				changed |= !buildResult.buildSite().equals(previousBuildSite);
				changed |= ensureWorkforceIfNeeded(level, settlement);
			}
		}

		return changed;
	}

	private boolean tryStartPlacedRoadwrightWorkshopBuildSites(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, List<BlockPos> surveyorTables) {
		boolean changed = false;

		for (BlockPos tablePos : surveyorTables) {
			Optional<SettlementBuildSite> existingBuildSite = findBuildSite(settlement.id(), SettlementBuildSiteType.ROADWRIGHT_WORKSHOP, tablePos);
			SettlementConstruction.WorkstationBuildResult buildResult = SettlementConstruction.tryStartRoadwrightWorkshopAtWorkstation(
				level,
				tablePos,
				existingBuildSite.map(SettlementBuildSite::facing).orElseGet(() -> SettlementConstruction.fletcherHutFacingFor(settlement, tablePos)),
				settlement.id(),
				stock,
				existingBuildSite
			);

			if (buildResult.isStarted() || buildResult.isResumed()) {
				SettlementBuildSite previousBuildSite = buildSites.put(buildResult.buildSite().id(), buildResult.buildSite());
				changed |= !buildResult.buildSite().equals(previousBuildSite);
				changed |= ensureWorkforceIfNeeded(level, settlement);
			}
		}

		return changed;
	}

	private boolean tryStartPlacedForesterWorkshopBuildSites(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, List<BlockPos> foresterTables) {
		boolean changed = false;

		for (BlockPos tablePos : foresterTables) {
			Optional<SettlementBuildSite> existingBuildSite = findBuildSite(settlement.id(), SettlementBuildSiteType.FORESTER_WORKSHOP, tablePos);
			SettlementConstruction.WorkstationBuildResult buildResult = SettlementConstruction.tryStartForesterWorkshopAtWorkstation(
				level,
				tablePos,
				existingBuildSite.map(SettlementBuildSite::facing).orElseGet(() -> SettlementConstruction.fletcherHutFacingFor(settlement, tablePos)),
				settlement.id(),
				stock,
				existingBuildSite
			);

			if (buildResult.isStarted() || buildResult.isResumed()) {
				SettlementBuildSite previousBuildSite = buildSites.put(buildResult.buildSite().id(), buildResult.buildSite());
				changed |= !buildResult.buildSite().equals(previousBuildSite);
				changed |= ensureWorkforceIfNeeded(level, settlement);
			}
		}

		return changed;
	}

	private boolean advanceRegion(MinecraftServer server, RegionKey region, long currentTick) {
		boolean changed = false;

		for (Map.Entry<String, SettlementState> entry : settlements.entrySet()) {
			SettlementState settlement = entry.getValue();

			if (!settlement.region().equals(region) || settlement.lastUpdateTick() == currentTick) {
				continue;
			}

			if (settlement.lastUpdateTick() > currentTick) {
				settlement = settlement.withLastUpdateTick(Math.max(0L, currentTick - SettlementEconomyRules.MIN_SIMULATION_TICKS));
			}

			ServerLevel level = server.getLevel(settlement.dimension());
			boolean loadedSettlement = level != null
				&& level.isLoaded(settlement.center())
				&& level.isPositionEntityTicking(settlement.center());
			SettlementState simulationInput = settlement;

			if (loadedSettlement && SettlementVillagers.usesActualVillagers(settlement)) {
				SettlementVillagers.ensureWorkforce(level, settlement);
				SettlementVillagers.ensureVillagerHomes(level, settlement);
				SettlementVillagers.ensureVillagerGatheringPoint(level, settlement);
				simulationInput = settlement.withPopulation(SettlementVillagers.censusPopulation(level, settlement));
			} else if (loadedSettlement && settlement.kind() == SettlementKind.OUTPOST) {
				int trimmedRaiders = OutpostSettlementWork.trimExcessUntrackedRaiders(level, settlement);
				if (trimmedRaiders > 0) {
					LiveVillages.LOGGER.info("Trimmed {} excess unmanaged raiders from outpost {}", trimmedRaiders, settlement.id());
				}
				simulationInput = settlement.withPopulation(OutpostSettlementWork.censusPopulation(level, settlement));
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
			if (loadedSettlement && level != null && SettlementVillagers.usesActualVillagers(settlement)) {
				logPopulationDiagnostic(
					level,
					settlement,
					updatedSettlement,
					simulationResult.requestedVillagerSpawns(),
					"economy",
					""
				);
			}

			if (simulationResult.requestedVillagerSpawns() > 0 && level != null && level.isLoaded(updatedSettlement.center()) && level.isPositionEntityTicking(updatedSettlement.center())) {
				if (updatedSettlement.kind() == SettlementKind.OUTPOST) {
					LiveVillages.LOGGER.info("Recruiting {} outpost members for settlement {}", simulationResult.requestedVillagerSpawns(), settlement.id());
					OutpostRecruitmentResult recruitmentResult = spawnOutpostRecruits(level, updatedSettlement, simulationResult.requestedVillagerSpawns());

					if (recruitmentResult.spawned() > 0) {
						updatedSettlement = updatedSettlement
							.withStock(recruitmentResult.stock())
							.withGrowthProgress(Math.max(0.0D, updatedSettlement.growthProgress() - recruitmentResult.spawned()))
							.withPopulation(OutpostSettlementWork.censusPopulation(level, updatedSettlement));
					}
				} else if (SettlementVillagers.usesActualVillagers(updatedSettlement)) {
					LiveVillages.LOGGER.info("Spawning {} villagers for settlement {}", simulationResult.requestedVillagerSpawns(), settlement.id());
					int spawnedVillagers = spawnSettlementVillagers(level, updatedSettlement, simulationResult.requestedVillagerSpawns());

					if (spawnedVillagers > 0) {
						SettlementVillagers.ensureWorkforce(level, updatedSettlement);
						SettlementVillagers.ensureVillagerHomes(level, updatedSettlement);
						SettlementVillagers.ensureVillagerGatheringPoint(level, updatedSettlement);
						updatedSettlement = updatedSettlement
							.withGrowthProgress(Math.max(0.0D, updatedSettlement.growthProgress() - spawnedVillagers))
							.withPopulation(SettlementVillagers.censusPopulation(level, updatedSettlement));
					}

					logPopulationDiagnostic(
						level,
						simulationResult.settlement(),
						updatedSettlement,
						spawnedVillagers,
						spawnedVillagers >= simulationResult.requestedVillagerSpawns() ? "growth_spawned" : "growth_spawn_shortfall",
						"requested=" + simulationResult.requestedVillagerSpawns()
					);
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
				settlements.put(fromSettlement.id(), routeAdvanceResult.fromSettlement().withTier(SettlementTiers.unlockedTier(routeAdvanceResult.fromSettlement())));
				changed = true;
			}

			if (!routeAdvanceResult.toSettlement().equals(toSettlement)) {
				settlements.put(toSettlement.id(), routeAdvanceResult.toSettlement().withTier(SettlementTiers.unlockedTier(routeAdvanceResult.toSettlement())));
				changed = true;
			}

			if (routeAdvanceResult.route().lastTradeAttemptTick() == currentTick
				&& route.lastTradeAttemptTick() != currentTick
				&& exchangeScribeRecipes(level, routeAdvanceResult.fromSettlement(), routeAdvanceResult.toSettlement(), route.id())) {
				changed = true;
			}
		}

		return changed;
	}

	private boolean exchangeScribeRecipes(ServerLevel level, SettlementState fromSettlement, SettlementState toSettlement, String routeId) {
		boolean fromHasScribe = hasScribeSupport(fromSettlement);
		boolean toHasScribe = hasScribeSupport(toSettlement);

		if (!fromHasScribe && !toHasScribe) {
			return false;
		}

		if (fromHasScribe) {
			ensureScribeStarterRecipes(fromSettlement.id(), SettlementRecipeKnowledge.recipeIdsForTier(SettlementTiers.unlockedTier(fromSettlement)));
		}

		if (toHasScribe) {
			ensureScribeStarterRecipes(toSettlement.id(), SettlementRecipeKnowledge.recipeIdsForTier(SettlementTiers.unlockedTier(toSettlement)));
		}

		List<String> fromRecipes = knownScribeRecipeIds(fromSettlement.id());
		List<String> toRecipes = knownScribeRecipeIds(toSettlement.id());
		boolean changed = false;
		int exchangeLimit = scribeRouteExchangeLimit(fromHasScribe, toHasScribe);
		List<String> recipesForTo = unknownRecipes(fromRecipes, toRecipes, exchangeLimit);
		List<String> recipesForFrom = unknownRecipes(toRecipes, fromRecipes, exchangeLimit);

		for (String recipeForTo : recipesForTo) {
			if (addKnownScribeRecipe(toSettlement.id(), recipeForTo)) {
				SettlementProfessionDiagnostics.log(
					level,
					toSettlement,
					SettlementRoleKeys.SCRIBE,
					"recipe_trade_received",
					"route=" + routeId + " from=" + fromSettlement.name() + " recipe=" + recipeForTo
				);
				changed = true;
			}
		}

		for (String recipeForFrom : recipesForFrom) {
			if (addKnownScribeRecipe(fromSettlement.id(), recipeForFrom)) {
				SettlementProfessionDiagnostics.log(
					level,
					fromSettlement,
					SettlementRoleKeys.SCRIBE,
					"recipe_trade_received",
					"route=" + routeId + " from=" + toSettlement.name() + " recipe=" + recipeForFrom
				);
				changed = true;
			}
		}

		return changed;
	}

	private static boolean hasScribeSupport(SettlementState settlement) {
		return settlement.population().getOrDefault(SettlementRoleKeys.SCRIBE, 0) > 0;
	}

	private static int scribeRouteExchangeLimit(boolean fromHasScribe, boolean toHasScribe) {
		return fromHasScribe && toHasScribe ? SCRIBE_ROUTE_EXCHANGE_TWO_SIDED_LIMIT : SCRIBE_ROUTE_EXCHANGE_ONE_SIDED_LIMIT;
	}

	private static List<String> unknownRecipes(List<String> sourceRecipes, List<String> knownRecipes, int limit) {
		Set<String> known = new HashSet<>(knownRecipes);
		return sourceRecipes.stream()
			.filter(recipeId -> !known.contains(recipeId))
			.limit(limit)
			.toList();
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

		if (!hasElapsed(currentTick, settlement.createdTick(), CUSTOM_SETTLEMENT_BOOTSTRAP_DELAY_TICKS)) {
			return false;
		}

		long lastSpawnTick = bootstrapVillagerSpawnTicks.getOrDefault(settlement.id(), 0L);
		return hasElapsed(currentTick, lastSpawnTick, CUSTOM_SETTLEMENT_BOOTSTRAP_RETRY_TICKS);
	}

	private static boolean hasElapsed(long currentTick, long previousTick, long requiredElapsedTicks) {
		return previousTick <= 0L || previousTick > currentTick || (currentTick - previousTick) >= requiredElapsedTicks;
	}

	private void logPopulationDiagnostic(
		ServerLevel level,
		SettlementState before,
		SettlementState after,
		int requestedOrSpawnedVillagers,
		String phase,
		String details
	) {
		if (level == null || before == null || after == null || phase == null) {
			return;
		}

		boolean routineEconomyLog = "economy".equals(phase);
		if (routineEconomyLog && before.kind() != SettlementKind.CUSTOM && requestedOrSpawnedVillagers <= 0) {
			return;
		}

		long currentTick = level.getServer().getTickCount();
		String key = before.dimension().identifier() + "|" + before.id() + "|" + phase;
		long previousTick = populationDiagnosticTicks.getOrDefault(key, Long.MIN_VALUE);
		if (previousTick != Long.MIN_VALUE && currentTick - previousTick < POPULATION_DIAGNOSTIC_INTERVAL_TICKS) {
			return;
		}

		populationDiagnosticTicks.put(key, currentTick);
		Map<String, Integer> actualPopulation = SettlementVillagers.censusPopulation(level, before);
		int ledgerBeforeTotal = populationTotal(before.population());
		int ledgerAfterTotal = populationTotal(after.population());
		int actualTotal = populationTotal(actualPopulation);
		int nearbyBootstrapRadius = SettlementVillagers.countNearbyVillagers(level, before.center(), CUSTOM_SETTLEMENT_VILLAGER_RADIUS_BLOCKS);
		List<Villager> reportableVillagers = SettlementVillagers.reportableVillagers(level, before);
		int adults = 0;
		int children = 0;
		for (Villager villager : reportableVillagers) {
			if (villager.isBaby()) {
				children++;
			} else {
				adults++;
			}
		}

		LiveVillages.LOGGER.info(
			"Population diagnostic: settlement={} name={} phase={} ledgerBefore={} ledgerAfter={} actual={} totals={}/{}/{} adults={} children={} nearbyBootstrapRadius={} housing={} food={} comfort={} security={} growth={}->{} requestedOrSpawned={} villagers={} {}",
			before.id(),
			before.name(),
			phase,
			before.population(),
			after.population(),
			actualPopulation,
			ledgerBeforeTotal,
			ledgerAfterTotal,
			actualTotal,
			adults,
			children,
			nearbyBootstrapRadius,
			after.housingCapacity(),
			SettlementEconomySimulator.totalFoodStock(after.stock()),
			roundedPercent(after.comfort()),
			roundedPercent(after.security()),
			roundedDouble(before.growthProgress()),
			roundedDouble(after.growthProgress()),
			requestedOrSpawnedVillagers,
			villagerDiagnosticSummary(level, before, reportableVillagers),
			details == null || details.isBlank() ? "" : details
		);
	}

	private static int populationTotal(Map<String, Integer> population) {
		return population.values().stream().mapToInt(Integer::intValue).sum();
	}

	private static double roundedDouble(double value) {
		return Math.round(value * 100.0D) / 100.0D;
	}

	private static String roundedPercent(double value) {
		return Math.round(value * 100.0D) + "%";
	}

	private static String villagerDiagnosticSummary(ServerLevel level, SettlementState settlement, List<Villager> villagers) {
		if (villagers.isEmpty()) {
			return "none";
		}

		StringBuilder summary = new StringBuilder();
		int limit = Math.min(12, villagers.size());
		for (int i = 0; i < limit; i++) {
			Villager villager = villagers.get(i);
			BlockPos pos = villager.blockPosition();
			String role = SettlementVillagers.reportProfessionKey(villager);
			String task = SettlementVillagers.reportTaskKey(level, settlement, villager);
			int distance = (int) Math.round(Math.sqrt(pos.distSqr(settlement.center())));

			if (i > 0) {
				summary.append("; ");
			}

			summary
				.append(shortUuid(villager))
				.append(":role=")
				.append(role == null || role.isBlank() ? "unknown" : role)
				.append(",baby=")
				.append(villager.isBaby())
				.append(",pos=")
				.append(pos.toShortString())
				.append(",dist=")
				.append(distance)
				.append(",task=")
				.append(task == null || task.isBlank() ? "unknown" : task);
		}

		if (villagers.size() > limit) {
			summary.append("; +").append(villagers.size() - limit).append(" more");
		}

		return summary.toString();
	}

	private static String shortUuid(Villager villager) {
		String uuid = villager.getUUID().toString();
		return uuid.length() <= 8 ? uuid : uuid.substring(0, 8);
	}

	private static int spawnSettlementVillagers(ServerLevel level, SettlementState settlement, int requestedVillagers) {
		int spawnedVillagers = 0;

		for (int i = 0; i < requestedVillagers; i++) {
			Optional<BlockPos> spawnPos = SettlementVillagers.findSpawnPos(level, settlement.center(), CUSTOM_SETTLEMENT_SPAWN_SEARCH_RADIUS);

			if (spawnPos.isEmpty()) {
				LiveVillages.LOGGER.info(
					"Settlement villager spawn failed: settlement={} reason=no_spawn_pos searchRadius={} requested={} spawned={}",
					settlement.id(),
					CUSTOM_SETTLEMENT_SPAWN_SEARCH_RADIUS,
					requestedVillagers,
					spawnedVillagers
				);
				break;
			}

			if (!SettlementVillagers.spawnVillager(level, spawnPos.get())) {
				LiveVillages.LOGGER.info(
					"Settlement villager spawn failed: settlement={} reason=entity_rejected spawnPos={} requested={} spawned={}",
					settlement.id(),
					spawnPos.get().toShortString(),
					requestedVillagers,
					spawnedVillagers
				);
				break;
			}

			LiveVillages.LOGGER.info("Settlement villager spawned: settlement={} spawnPos={}", settlement.id(), spawnPos.get().toShortString());
			spawnedVillagers++;
		}

		return spawnedVillagers;
	}

	private static OutpostRecruitmentResult spawnOutpostRecruits(ServerLevel level, SettlementState settlement, int requestedRecruits) {
		int spawnedRecruits = 0;
		Map<String, Integer> stock = new LinkedHashMap<>(settlement.stock());
		int livePopulation = OutpostSettlementWork.censusPopulation(level, settlement).values().stream().mapToInt(Integer::intValue).sum();
		int remainingCapacity = Math.max(0, OutpostSettlementWork.recruitmentCapacity(settlement) - livePopulation);
		int spawnLimit = Math.min(requestedRecruits, remainingCapacity);

		for (int i = 0; i < spawnLimit; i++) {
			Optional<BlockPos> spawnPos = SettlementVillagers.findSpawnPos(level, settlement.center(), CUSTOM_SETTLEMENT_SPAWN_SEARCH_RADIUS);

			if (spawnPos.isEmpty() || !OutpostSettlementWork.hasRecruitmentSupplies(stock)) {
				break;
			}

			if (!OutpostSettlementWork.spawnRecruit(level, settlement, spawnPos.get(), stock)) {
				break;
			}

			OutpostSettlementWork.consumeRecruitmentSupplies(stock);
			spawnedRecruits++;
		}

		return new OutpostRecruitmentResult(spawnedRecruits, stock);
	}

	private record OutpostRecruitmentResult(int spawned, Map<String, Integer> stock) {
	}

	private record VirtualTradeBoardSite(BlockPos pos, Direction facing) {
	}

	private record MaterializationResult(boolean changed, boolean materialized) {
		private static MaterializationResult unchanged() {
			return new MaterializationResult(false, false);
		}

		private static MaterializationResult changedAndMaterialized() {
			return new MaterializationResult(true, true);
		}
	}

	private record AutonomousSupportNeed(AutonomousSupportType type, int score) {
	}

	private record AutonomousWorkstationClearance(
		List<BlockPos> supportPositions,
		List<BlockPos> buildSiteAnchors,
		List<BlockPos> buildSiteBlocks
	) {
	}

	private enum AutonomousSupportType {
		FORESTER("Forester's Workshop", 80),
		CARPENTER("Carpenter's Workshop", 70),
		MINER("Mine Entrance", 75);

		private final String structureLabel;
		private final int minimumScore;

		AutonomousSupportType(String structureLabel, int minimumScore) {
			this.structureLabel = structureLabel;
			this.minimumScore = minimumScore;
		}

		private String structureLabel() {
			return structureLabel;
		}

		private int minimumScore() {
			return minimumScore;
		}
	}

	private record VirtualWorkstationSite(BlockPos pos, Direction facing) {
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
