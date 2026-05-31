package com.ronhelwig.livevillages.sim;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;

import com.ronhelwig.livevillages.block.MilepostBlock;
import com.ronhelwig.livevillages.content.LiveVillagesBlocks;

public final class SettlementLoadedObservation {
	private static final long VILLAGER_CACHE_TICKS = 20L;
	private static final long SURVEYOR_MEMORY_TICKS = 144_000L;
	private static final int VILLAGER_OBSERVATION_RADIUS_BLOCKS = 24;
	private static final int SURVEYOR_POI_SCAN_Y_RANGE_BLOCKS = 16;
	private static final int SURVEYOR_MAX_OBSERVERS_PER_UPDATE = 8;
	private static final int SURVEYOR_MAX_COLUMNS_PER_UPDATE = 1_800;
	private static final int SURVEYOR_MAX_COLUMNS_PER_OBSERVER = 240;
	private static final int SURVEYOR_MAX_CENTER_COLUMNS_PER_UPDATE = 360;
	private static final int SURVEYOR_MILEPOST_POINTER_RADIUS_BLOCKS = 192;
	private static final int SURVEYOR_FULL_SCAN_BELOW_SURFACE_BLOCKS = 8;
	private static final int SURVEYOR_FULL_SCAN_ABOVE_SURFACE_BLOCKS = 12;
	private static final int SURVEYOR_ROAD_SCAN_BELOW_SURFACE_BLOCKS = 8;
	private static final int SURVEYOR_ROAD_SCAN_ABOVE_SURFACE_BLOCKS = 2;
	private static final int SURVEYOR_MAX_ROADS = 16_384;
	private static final int SURVEYOR_MAX_STRUCTURES = 12_288;
	private static final int SURVEYOR_MAX_WATER_COLUMNS = 8_192;
	private static final int SURVEYOR_MAX_POINTS = 160;
	private static final int SURVEYOR_MAX_OBSERVED_AREAS = 768;
	private static final Map<String, CachedVillagers> VILLAGER_CACHE = new HashMap<>();
	private static final Map<String, SurveyorMemory> SURVEYOR_MEMORY = new HashMap<>();

	private SettlementLoadedObservation() {
	}

	public static List<Villager> nearbyVillagers(ServerLevel level, SettlementState settlement, int radiusBlocks) {
		return nearbyVillagers(level, settlement.center(), radiusBlocks, settlement.dimension().identifier() + "|" + settlement.id());
	}

	public static List<Villager> nearbyVillagers(ServerLevel level, BlockPos center, int radiusBlocks) {
		return nearbyVillagers(level, center, radiusBlocks, "pos:" + center.asLong());
	}

	public static List<BlockPos> captureFullyVisibleSurveyorWater(
		ServerLevel level,
		BlockPos center,
		int mapRadius
	) {
		int mapRadiusSquared = mapRadius * mapRadius;
		LinkedHashSet<BlockPos> waters = new LinkedHashSet<>();
		BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();

		for (int x = center.getX() - mapRadius; x <= center.getX() + mapRadius && waters.size() < SURVEYOR_MAX_WATER_COLUMNS; x++) {
			for (int z = center.getZ() - mapRadius; z <= center.getZ() + mapRadius && waters.size() < SURVEYOR_MAX_WATER_COLUMNS; z++) {
				if (center.distToCenterSqr(x + 0.5D, center.getY() + 0.5D, z + 0.5D) > mapRadiusSquared) {
					continue;
				}

				scanPos.set(x, center.getY(), z);
				if (!level.hasChunkAt(scanPos)) {
					continue;
				}

				observedWaterAt(level, x, z).ifPresent(waters::add);
			}
		}

		return List.copyOf(waters);
	}

	public static SurveyorObservation captureSurveyorMapObservation(
		ServerLevel level,
		SettlementState settlement,
		Collection<SettlementBuildSite> buildSites,
		int mapRadius,
		boolean fogOfWarEnabled
	) {
		if (fogOfWarEnabled) {
			return updateSurveyorMapObservation(level, settlement, buildSites, mapRadius);
		}

		return captureFullyVisibleSurveyorMapObservation(level, settlement, buildSites, mapRadius);
	}

	public static SurveyorObservation updateSurveyorMapObservation(
		ServerLevel level,
		SettlementState settlement,
		Collection<SettlementBuildSite> buildSites,
		int mapRadius
	) {
		long tick = level.getServer().getTickCount();
		String key = surveyorMemoryKey(settlement);
		SurveyorMemory memory = SURVEYOR_MEMORY.computeIfAbsent(key, ignored -> new SurveyorMemory());
		BlockPos center = settlement.center();
		Set<Long> scannedColumns = new HashSet<>();
		List<ObservedPoint> anchorPoints = surveyorAnchorPoints(level, settlement, buildSites, mapRadius);
		memory.observedAreas.put(center, new TimedObservedArea(center, tick));
		ColumnScanBudget scanBudget = new ColumnScanBudget(SURVEYOR_MAX_COLUMNS_PER_UPDATE);

		for (ObservedPoint point : anchorPoints) {
			memory.points.put(point.pos(), new TimedPoint(point, tick));
		}

		List<Villager> nearbyVillagers = nearbyVillagers(level, settlement, mapRadius);

		for (Villager villager : nearbyVillagers) {
			BlockPos observerPos = villager.blockPosition().immutable();
			memory.observedAreas.put(observerPos, new TimedObservedArea(observerPos, tick));
		}

		for (Villager villager : selectSurveyorObservers(nearbyVillagers, tick)) {
			if (scanBudget.isDepleted()) {
				break;
			}

			scanSurveyorObservationAround(
				level,
				settlement,
				memory,
				villager.blockPosition().immutable(),
				mapRadius,
				tick,
				scannedColumns,
				scanBudget,
				SURVEYOR_MAX_COLUMNS_PER_OBSERVER
			);
		}

		if (!scanBudget.isDepleted()) {
			scanSurveyorObservationAround(
				level,
				settlement,
				memory,
				center,
				mapRadius,
				tick,
				scannedColumns,
				scanBudget,
				SURVEYOR_MAX_CENTER_COLUMNS_PER_UPDATE
			);
		}

		pruneSurveyorMemory(memory, tick);
		return memory.snapshot();
	}

	public static void restoreSurveyorObservationIfMissing(SettlementState settlement, SurveyorObservation observation, long tick) {
		if (observation == null || observation.empty()) {
			return;
		}

		SurveyorMemory memory = SURVEYOR_MEMORY.computeIfAbsent(surveyorMemoryKey(settlement), ignored -> new SurveyorMemory());

		if (!memory.roads.isEmpty() || !memory.structures.isEmpty() || !memory.points.isEmpty() || !memory.observedAreas.isEmpty()) {
			return;
		}

		for (ObservedRoad road : observation.roads()) {
			memory.roads.put(road.pos(), new TimedRoad(road, tick));
		}

		for (ObservedStructure structure : observation.structures()) {
			memory.structures.put(structure.pos(), new TimedStructure(structure, tick));
		}

		for (ObservedPoint point : observation.points()) {
			memory.points.put(point.pos(), new TimedPoint(point, tick));
		}

		for (BlockPos observedArea : observation.observedAreas()) {
			memory.observedAreas.put(observedArea, new TimedObservedArea(observedArea, tick));
		}
	}

	private static SurveyorObservation captureFullyVisibleSurveyorMapObservation(
		ServerLevel level,
		SettlementState settlement,
		Collection<SettlementBuildSite> buildSites,
		int mapRadius
	) {
		BlockPos center = settlement.center();
		int mapRadiusSquared = mapRadius * mapRadius;
		LinkedHashMap<BlockPos, ObservedRoad> roads = new LinkedHashMap<>();
		LinkedHashMap<BlockPos, ObservedStructure> structures = new LinkedHashMap<>();
		LinkedHashMap<BlockPos, ObservedPoint> points = new LinkedHashMap<>();
		BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();

		collectBuildSiteStructures(buildSites, structures);

		for (ObservedPoint point : surveyorAnchorPoints(level, settlement, buildSites, mapRadius)) {
			points.put(point.pos(), point);
		}

		for (int x = center.getX() - mapRadius; x <= center.getX() + mapRadius; x++) {
			for (int z = center.getZ() - mapRadius; z <= center.getZ() + mapRadius; z++) {
				if (center.distToCenterSqr(x + 0.5D, center.getY() + 0.5D, z + 0.5D) > mapRadiusSquared) {
					continue;
				}

				scanPos.set(x, center.getY(), z);

				if (!level.hasChunkAt(scanPos)) {
					continue;
				}

				observeRoadColumn(level, roads, x, z);
				observeStructureColumn(level, structures, x, z);

				if (points.size() < SURVEYOR_MAX_POINTS) {
					observeSurfacePointColumn(level, points, x, z);
				}
			}
		}

		return new SurveyorObservation(
			limitRoads(roads.values()),
			limitStructures(structures.values()),
			limitPoints(points.values()),
			List.of()
		);
	}

	private static List<Villager> selectSurveyorObservers(List<Villager> villagers, long tick) {
		if (villagers.size() <= SURVEYOR_MAX_OBSERVERS_PER_UPDATE) {
			return villagers;
		}

		List<Villager> selectedVillagers = new ArrayList<>(SURVEYOR_MAX_OBSERVERS_PER_UPDATE);
		int startIndex = Math.floorMod((int) (tick / VILLAGER_CACHE_TICKS), villagers.size());

		for (int i = 0; i < SURVEYOR_MAX_OBSERVERS_PER_UPDATE; i++) {
			selectedVillagers.add(villagers.get((startIndex + i) % villagers.size()));
		}

		return selectedVillagers;
	}

	private static List<Villager> nearbyVillagers(ServerLevel level, BlockPos center, int radiusBlocks, String keyPrefix) {
		long tick = level.getServer().getTickCount();
		String cacheKey = level.dimension().identifier() + "|" + keyPrefix + "|" + radiusBlocks;
		CachedVillagers cached = VILLAGER_CACHE.get(cacheKey);

		if (cached != null && tick - cached.tick() <= VILLAGER_CACHE_TICKS) {
			return cached.villagers();
		}

		double maxDistanceSquared = (double) radiusBlocks * radiusBlocks;
		List<Villager> villagers = new ArrayList<>(level.getEntities(
			EntityTypeTest.forClass(Villager.class),
			villager -> horizontalDistanceToCenterSqr(villager.getX(), villager.getZ(), center) <= maxDistanceSquared
		));
		List<Villager> snapshot = List.copyOf(villagers);
		VILLAGER_CACHE.put(cacheKey, new CachedVillagers(snapshot, tick));
		return snapshot;
	}

	private static double horizontalDistanceToCenterSqr(double x, double z, BlockPos center) {
		double dx = x - (center.getX() + 0.5D);
		double dz = z - (center.getZ() + 0.5D);
		return (dx * dx) + (dz * dz);
	}

	private static void scanSurveyorObservationAround(
		ServerLevel level,
		SettlementState settlement,
		SurveyorMemory memory,
		BlockPos observerPos,
		int mapRadius,
		long tick,
		Set<Long> scannedColumns,
		ColumnScanBudget scanBudget,
		int maxColumnsForObserver
	) {
		if (maxColumnsForObserver <= 0 || scanBudget.isDepleted()) {
			return;
		}

		BlockPos center = settlement.center();
		int mapRadiusSquared = mapRadius * mapRadius;
		int observationRadiusSquared = VILLAGER_OBSERVATION_RADIUS_BLOCKS * VILLAGER_OBSERVATION_RADIUS_BLOCKS;
		int minY = Math.max(level.getMinY(), observerPos.getY() - SURVEYOR_POI_SCAN_Y_RANGE_BLOCKS);
		int maxY = Math.min(level.getMaxY() - 1, observerPos.getY() + SURVEYOR_POI_SCAN_Y_RANGE_BLOCKS);
		BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();
		int width = (VILLAGER_OBSERVATION_RADIUS_BLOCKS * 2) + 1;
		int totalCells = width * width;
		int startIndex = Math.floorMod(observerPos.asLong() + ((tick / VILLAGER_CACHE_TICKS) * 431L), totalCells);
		int scannedForObserver = 0;

		for (int step = 0; step < totalCells && scannedForObserver < maxColumnsForObserver && !scanBudget.isDepleted(); step++) {
			int cellIndex = (startIndex + step) % totalCells;
			int dx = (cellIndex % width) - VILLAGER_OBSERVATION_RADIUS_BLOCKS;
			int dz = (cellIndex / width) - VILLAGER_OBSERVATION_RADIUS_BLOCKS;
			int x = observerPos.getX() + dx;
			int z = observerPos.getZ() + dz;

			if ((dx * dx) + (dz * dz) > observationRadiusSquared
				|| center.distToCenterSqr(x + 0.5D, center.getY() + 0.5D, z + 0.5D) > mapRadiusSquared) {
				continue;
			}

			scanPos.set(x, observerPos.getY(), z);

			if (!level.hasChunkAt(scanPos) || !scannedColumns.add(columnKey(x, z))) {
				continue;
			}

			if (!scanBudget.tryConsume()) {
				return;
			}

			scannedForObserver++;
			observeRoadColumn(level, memory, x, z, tick);
			observeStructureColumn(level, memory, x, z, tick);

			for (int y = minY; y <= maxY && memory.points.size() < SURVEYOR_MAX_POINTS; y++) {
				scanPos.set(x, y, z);
				MapPointMarker marker = mapPointMarker(level.getBlockState(scanPos));

				if (marker == null) {
					continue;
				}

				BlockPos pointPos = scanPos.immutable();
				memory.points.put(pointPos, new TimedPoint(new ObservedPoint(pointPos, marker.kind(), marker.label()), tick));
			}
		}
	}

	private static long columnKey(int x, int z) {
		return ((long) x << 32) ^ (z & 0xffffffffL);
	}

	private static void observeRoadColumn(ServerLevel level, SurveyorMemory memory, int x, int z, long tick) {
		ObservedRoad road = observedRoadAt(level, x, z);

		if (road != null) {
			memory.roads.put(road.pos(), new TimedRoad(road, tick));
		}
	}

	private static void observeRoadColumn(ServerLevel level, Map<BlockPos, ObservedRoad> roads, int x, int z) {
		ObservedRoad road = observedRoadAt(level, x, z);

		if (road != null) {
			roads.put(road.pos(), road);
		}
	}

	private static void observeStructureColumn(ServerLevel level, SurveyorMemory memory, int x, int z, long tick) {
		ObservedStructure structure = observedStructureAt(level, x, z);

		if (structure != null) {
			memory.structures.put(structure.pos(), new TimedStructure(structure, tick));
		}
	}

	private static void observeStructureColumn(ServerLevel level, Map<BlockPos, ObservedStructure> structures, int x, int z) {
		ObservedStructure structure = observedStructureAt(level, x, z);

		if (structure != null) {
			structures.putIfAbsent(structure.pos(), structure);
		}
	}

	private static void pruneSurveyorMemory(SurveyorMemory memory, long tick) {
		memory.roads.entrySet().removeIf(entry -> tick - entry.getValue().tick() > SURVEYOR_MEMORY_TICKS);
		memory.structures.entrySet().removeIf(entry -> tick - entry.getValue().tick() > SURVEYOR_MEMORY_TICKS);
		memory.points.entrySet().removeIf(entry -> tick - entry.getValue().tick() > SURVEYOR_MEMORY_TICKS);
		memory.observedAreas.entrySet().removeIf(entry -> tick - entry.getValue().tick() > SURVEYOR_MEMORY_TICKS);
	}

	private static MapPointMarker mapPointMarker(BlockState state) {
		if (state.is(LiveVillagesBlocks.TRADE_BOARD)) {
			return new MapPointMarker("poi", "Trade");
		}

		if (state.is(LiveVillagesBlocks.CARPENTER_BENCH)) {
			return new MapPointMarker("poi", "Carpenter");
		}

		if (state.is(LiveVillagesBlocks.FORESTER_TABLE)) {
			return new MapPointMarker("poi", "Forester");
		}

		if (state.is(LiveVillagesBlocks.MINER_WORKSTATION)) {
			return new MapPointMarker("poi", "Miner");
		}

		if (state.is(LiveVillagesBlocks.SURVEYOR_TABLE)) {
			return new MapPointMarker("poi", "Surveyor");
		}

		if (state.is(LiveVillagesBlocks.MILEPOST)) {
			if (!MilepostBlock.isLowerPart(state)) {
				return null;
			}

			return new MapPointMarker("milepost", "Milepost");
		}

		if (state.is(Blocks.COMPOSTER)) {
			return new MapPointMarker("poi", "Garden");
		}

		if (state.is(Blocks.CARTOGRAPHY_TABLE)) {
			return new MapPointMarker("poi", "Map");
		}

		if (state.is(Blocks.FLETCHING_TABLE)) {
			return new MapPointMarker("poi", "Fletcher");
		}

		if (state.is(Blocks.STONECUTTER)) {
			return new MapPointMarker("poi", "Mason");
		}

		if (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST) || state.is(Blocks.BARREL)) {
			return new MapPointMarker("poi", "Storage");
		}

		if (state.getBlock() instanceof DoorBlock) {
			return new MapPointMarker("poi", "Door");
		}

		return null;
	}

	private static String roadQuality(BlockState state) {
		if (state.is(Blocks.DIRT_PATH)) {
			return "trail";
		}

		if (state.is(Blocks.GRAVEL)) {
			return "gravel";
		}

		if (state.is(Blocks.COBBLESTONE)
			|| state.is(Blocks.MOSSY_COBBLESTONE)
			|| state.is(Blocks.COBBLESTONE_STAIRS)
			|| state.is(Blocks.COBBLESTONE_SLAB)) {
			return "cobble";
		}

		if (state.is(Blocks.SMOOTH_STONE)
			|| state.is(Blocks.SMOOTH_STONE_SLAB)
			|| state.is(Blocks.STONE_STAIRS)
			|| state.is(Blocks.STONE_SLAB)
			|| state.is(Blocks.POLISHED_GRANITE)
			|| state.is(Blocks.POLISHED_DIORITE)
			|| state.is(Blocks.POLISHED_ANDESITE)) {
			return "smooth";
		}

		if (state.is(Blocks.STONE_BRICKS)
			|| state.is(Blocks.MOSSY_STONE_BRICKS)
			|| state.is(Blocks.STONE_BRICK_STAIRS)
			|| state.is(Blocks.STONE_BRICK_SLAB)
			|| state.is(Blocks.BRICKS)
			|| state.is(Blocks.BRICK_STAIRS)
			|| state.is(Blocks.BRICK_SLAB)) {
			return "brick";
		}

		return "";
	}

	private static List<ObservedPoint> surveyorAnchorPoints(
		ServerLevel level,
		SettlementState settlement,
		Collection<SettlementBuildSite> buildSites,
		int mapRadius
	) {
		BlockPos center = settlement.center();
		int mapRadiusSquared = mapRadius * mapRadius;
		LinkedHashMap<BlockPos, ObservedPoint> points = new LinkedHashMap<>();
		points.put(center, new ObservedPoint(center, "center", "Center"));

		for (SettlementBuildSite buildSite : buildSites) {
			if (buildSite.workstationPos().distSqr(center) > mapRadiusSquared) {
				continue;
			}

			BlockPos pos = buildSite.workstationPos().immutable();
			String kind = buildSite.complete() ? "building" : "project";
			points.put(pos, new ObservedPoint(pos, kind, buildSiteSurveyLabel(buildSite)));
		}

		for (BlockPos milepostPos : SettlementRoadwrightWork.nearbyMileposts(level, center, Math.max(mapRadius, SURVEYOR_MILEPOST_POINTER_RADIUS_BLOCKS))) {
			points.put(milepostPos, new ObservedPoint(milepostPos, "milepost", "Milepost"));
		}

		return limitPoints(points.values());
	}

	private static String buildSiteSurveyLabel(SettlementBuildSite buildSite) {
		return switch (buildSite.blueprintId()) {
			case BAKERY -> "Bakery";
			case BUTCHER_SHOP -> "Butcher";
			case DOCK -> "Dock";
			case LIGHTHOUSE -> "Lighthouse";
			case MASON_WORKSHOP -> "Mason";
			case MINE_ENTRANCE -> "Mine Entrance";
			case TRADING_POST -> "Trading Post";
			case CARPENTER_WORKSHOP -> "Carpenter";
			case FLETCHER_HUT -> "Fletcher";
			case ROADWRIGHT_WORKSHOP -> "Roadwright";
			case CARTOGRAPHER_HOUSE -> "Cartographer";
			case FORESTER_WORKSHOP -> "Forester";
			case HOUSING_SHELTER -> "Housing";
			case PALISADE_GATEHOUSE, COPPER_PALISADE_GATEHOUSE -> "Gatehouse";
			case SIMPLE_HOUSING_SHELTER -> "Shelter";
		};
	}

	private static void collectBuildSiteStructures(Collection<SettlementBuildSite> buildSites, Map<BlockPos, ObservedStructure> structures) {
		for (SettlementBuildSite buildSite : buildSites) {
			String kind = buildSite.complete() ? "building" : "project";

			for (SettlementBuildBlockState buildBlock : buildSite.blocks()) {
				BlockState plannedState = SettlementConstruction.plannedBuildSiteBlockState(buildSite, buildBlock);

				if (plannedState == null || plannedState.isAir()) {
					continue;
				}

				Optional<BlockPos> worldPos = SettlementConstruction.buildSiteBlockPos(buildSite, buildBlock);

				if (worldPos.isEmpty()) {
					continue;
				}

				structures.putIfAbsent(columnPos(worldPos.get()), new ObservedStructure(columnPos(worldPos.get()), kind));
			}
		}
	}

	private static ObservedRoad observedRoadAt(ServerLevel level, int x, int z) {
		int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		int minY = Math.max(level.getMinY(), surfaceY - SURVEYOR_ROAD_SCAN_BELOW_SURFACE_BLOCKS);
		int maxY = Math.min(level.getMaxY() - 1, surfaceY + SURVEYOR_ROAD_SCAN_ABOVE_SURFACE_BLOCKS);
		BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();

		for (int y = maxY; y >= minY; y--) {
			scanPos.set(x, y, z);
			BlockState state = level.getBlockState(scanPos);
			String quality = roadQuality(state);

			if (quality.isBlank() && state.is(Blocks.LEAF_LITTER)) {
				quality = roadQuality(level.getBlockState(scanPos.below()));
				if (!quality.isBlank() && qualifiesAsSurveyRoad(level, scanPos.below(), quality)) {
					return new ObservedRoad(scanPos.below().immutable(), quality);
				}

				continue;
			}

			if (!quality.isBlank() && qualifiesAsSurveyRoad(level, scanPos, quality)) {
				return new ObservedRoad(scanPos.immutable(), quality);
			}
		}

		return null;
	}

	private static ObservedStructure observedStructureAt(ServerLevel level, int x, int z) {
		int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		int minY = Math.max(level.getMinY(), surfaceY - SURVEYOR_FULL_SCAN_BELOW_SURFACE_BLOCKS);
		int maxY = Math.min(level.getMaxY() - 1, surfaceY + SURVEYOR_FULL_SCAN_ABOVE_SURFACE_BLOCKS);
		BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();

		for (int y = maxY; y >= minY; y--) {
			scanPos.set(x, y, z);

			if (looksLikeSurveyStructureBlock(level, scanPos)) {
				BlockPos pos = columnPos(x, z);
				return new ObservedStructure(pos, "building");
			}
		}

		return null;
	}

	private static void observeSurfacePointColumn(ServerLevel level, Map<BlockPos, ObservedPoint> points, int x, int z) {
		int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		int minY = Math.max(level.getMinY(), surfaceY - SURVEYOR_FULL_SCAN_BELOW_SURFACE_BLOCKS);
		int maxY = Math.min(level.getMaxY() - 1, surfaceY + SURVEYOR_FULL_SCAN_ABOVE_SURFACE_BLOCKS);
		BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();

		for (int y = minY; y <= maxY && points.size() < SURVEYOR_MAX_POINTS; y++) {
			scanPos.set(x, y, z);
			MapPointMarker marker = mapPointMarker(level.getBlockState(scanPos));

			if (marker == null) {
				continue;
			}

			BlockPos pointPos = scanPos.immutable();
			points.put(pointPos, new ObservedPoint(pointPos, marker.kind(), marker.label()));
		}
	}

	private static boolean looksLikeSurveyStructureBlock(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);

		if (state.isAir() || state.canBeReplaced()) {
			return false;
		}

		if (state.getBlock() instanceof DoorBlock
			|| state.is(Blocks.GLASS)
			|| state.is(Blocks.GLASS_PANE)
			|| state.is(BlockTags.PLANKS)
			|| state.is(BlockTags.WOODEN_STAIRS)
			|| state.is(BlockTags.WOODEN_SLABS)
			|| state.is(BlockTags.FENCES)
			|| state.is(BlockTags.FENCE_GATES)
			|| state.is(BlockTags.TRAPDOORS)
			|| state.is(Blocks.LADDER)) {
			return true;
		}

		MapPointMarker marker = mapPointMarker(state);
		if (marker != null && !"milepost".equals(marker.kind())) {
			return true;
		}

		if (state.is(BlockTags.LOGS)) {
			return looksLikeBuiltLogColumn(level, pos);
		}

		return false;
	}

	private static boolean qualifiesAsSurveyRoad(ServerLevel level, BlockPos pos, String quality) {
		if (!"gravel".equals(quality)) {
			return true;
		}

		for (BlockPos neighborPos : List.of(pos.north(), pos.south(), pos.east(), pos.west())) {
			String neighborQuality = roadQuality(level.getBlockState(neighborPos));
			if (!neighborQuality.isBlank() && !"gravel".equals(neighborQuality)) {
				return true;
			}
		}

		return false;
	}

	private static boolean looksLikeBuiltLogColumn(ServerLevel level, BlockPos pos) {
		int horizontalLogNeighbors = 0;
		boolean craftedAttachment = false;

		for (BlockPos neighborPos : List.of(pos.north(), pos.south(), pos.east(), pos.west())) {
			BlockState neighborState = level.getBlockState(neighborPos);

			if (neighborState.is(BlockTags.LOGS)) {
				horizontalLogNeighbors++;
			}

			if (isBuiltWoodAttachment(neighborState)) {
				craftedAttachment = true;
			}
		}

		for (BlockPos neighborPos : List.of(
			pos.above(),
			pos.below(),
			pos.above().north(),
			pos.above().south(),
			pos.above().east(),
			pos.above().west(),
			pos.below().north(),
			pos.below().south(),
			pos.below().east(),
			pos.below().west()
		)) {
			if (isBuiltWoodAttachment(level.getBlockState(neighborPos))) {
				craftedAttachment = true;
				break;
			}
		}

		if (horizontalLogNeighbors == 0 && !craftedAttachment) {
			return false;
		}

		if (horizontalLogNeighbors >= 2 || craftedAttachment) {
			return hasNearbyLeaves(level, pos) ? horizontalLogNeighbors >= 2 && craftedAttachment : true;
		}

		return false;
	}

	private static boolean isBuiltWoodAttachment(BlockState state) {
		return state.is(BlockTags.PLANKS)
			|| state.is(BlockTags.WOODEN_STAIRS)
			|| state.is(BlockTags.WOODEN_SLABS)
			|| state.is(BlockTags.FENCES)
			|| state.is(BlockTags.FENCE_GATES)
			|| state.is(BlockTags.TRAPDOORS)
			|| state.getBlock() instanceof DoorBlock;
	}

	private static boolean hasNearbyLeaves(ServerLevel level, BlockPos pos) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = 0; dy <= 2; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dy == 0 && dz == 0) {
						continue;
					}

					BlockState neighborState = level.getBlockState(pos.offset(dx, dy, dz));

					if (neighborState.getBlock() instanceof LeavesBlock || neighborState.is(BlockTags.LEAVES)) {
						return true;
					}
				}
			}
		}

		return false;
	}

	private static Optional<BlockPos> observedWaterAt(ServerLevel level, int x, int z) {
		int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;

		if (surfaceY < level.getMinY() || surfaceY > level.getMaxY() - 1) {
			return Optional.empty();
		}

		BlockPos surfacePos = new BlockPos(x, surfaceY, z);
		return level.getBlockState(surfacePos).is(Blocks.WATER) ? Optional.of(columnPos(x, z)) : Optional.empty();
	}

	private static BlockPos columnPos(BlockPos pos) {
		return columnPos(pos.getX(), pos.getZ());
	}

	private static BlockPos columnPos(int x, int z) {
		return new BlockPos(x, 0, z);
	}

	private static List<ObservedRoad> limitRoads(Collection<ObservedRoad> roads) {
		return roads.stream()
			.limit(SURVEYOR_MAX_ROADS)
			.toList();
	}

	private static List<ObservedStructure> limitStructures(Collection<ObservedStructure> structures) {
		return structures.stream()
			.limit(SURVEYOR_MAX_STRUCTURES)
			.toList();
	}

	private static List<ObservedPoint> limitPoints(Collection<ObservedPoint> points) {
		return points.stream()
			.limit(SURVEYOR_MAX_POINTS)
			.toList();
	}

	private static String surveyorMemoryKey(SettlementState settlement) {
		return settlement.dimension().identifier() + "|" + settlement.id();
	}

	private record CachedVillagers(List<Villager> villagers, long tick) {
	}

	private record TimedRoad(ObservedRoad road, long tick) {
	}

	private record TimedStructure(ObservedStructure structure, long tick) {
	}

	private record TimedPoint(ObservedPoint point, long tick) {
	}

	private record TimedObservedArea(BlockPos pos, long tick) {
		private TimedObservedArea {
			pos = pos.immutable();
		}
	}

	private static final class ColumnScanBudget {
		private int remainingColumns;

		private ColumnScanBudget(int remainingColumns) {
			this.remainingColumns = remainingColumns;
		}

		private boolean isDepleted() {
			return remainingColumns <= 0;
		}

		private boolean tryConsume() {
			if (isDepleted()) {
				return false;
			}

			remainingColumns--;
			return true;
		}
	}

	private static final class SurveyorMemory {
		private final LinkedHashMap<BlockPos, TimedRoad> roads = new LinkedHashMap<>();
		private final LinkedHashMap<BlockPos, TimedStructure> structures = new LinkedHashMap<>();
		private final LinkedHashMap<BlockPos, TimedPoint> points = new LinkedHashMap<>();
		private final LinkedHashMap<BlockPos, TimedObservedArea> observedAreas = new LinkedHashMap<>();

		private SurveyorObservation snapshot() {
			List<ObservedRoad> roadSnapshot = roads.values().stream()
				.sorted((first, second) -> Long.compare(second.tick(), first.tick()))
				.map(TimedRoad::road)
				.limit(SURVEYOR_MAX_ROADS)
				.toList();
			List<ObservedStructure> structureSnapshot = structures.values().stream()
				.sorted((first, second) -> Long.compare(second.tick(), first.tick()))
				.map(TimedStructure::structure)
				.limit(SURVEYOR_MAX_STRUCTURES)
				.toList();
			List<ObservedPoint> pointSnapshot = points.values().stream()
				.sorted((first, second) -> Long.compare(second.tick(), first.tick()))
				.map(TimedPoint::point)
				.limit(SURVEYOR_MAX_POINTS)
				.toList();
			List<BlockPos> observedAreaSnapshot = observedAreas.values().stream()
				.sorted((first, second) -> Long.compare(second.tick(), first.tick()))
				.map(TimedObservedArea::pos)
				.limit(SURVEYOR_MAX_OBSERVED_AREAS)
				.toList();
			return new SurveyorObservation(roadSnapshot, structureSnapshot, pointSnapshot, observedAreaSnapshot);
		}
	}

	public record SurveyorObservation(List<ObservedRoad> roads, List<ObservedStructure> structures, List<ObservedPoint> points, List<BlockPos> observedAreas) {
		public static final Codec<SurveyorObservation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			ObservedRoad.CODEC.listOf().optionalFieldOf("roads", List.of()).forGetter(SurveyorObservation::roads),
			ObservedStructure.CODEC.listOf().optionalFieldOf("structures", List.of()).forGetter(SurveyorObservation::structures),
			ObservedPoint.CODEC.listOf().optionalFieldOf("points", List.of()).forGetter(SurveyorObservation::points),
			BlockPos.CODEC.listOf().optionalFieldOf("observed_areas", List.of()).forGetter(SurveyorObservation::observedAreas)
		).apply(instance, SurveyorObservation::new));

		public SurveyorObservation {
			roads = List.copyOf(roads);
			structures = List.copyOf(structures);
			points = List.copyOf(points);
			observedAreas = List.copyOf(observedAreas);
		}

		public boolean empty() {
			return roads.isEmpty() && structures.isEmpty() && points.isEmpty() && observedAreas.isEmpty();
		}
	}

	public record ObservedRoad(BlockPos pos, String quality) {
		public static final Codec<ObservedRoad> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BlockPos.CODEC.fieldOf("pos").forGetter(ObservedRoad::pos),
			Codec.STRING.optionalFieldOf("quality", "").forGetter(ObservedRoad::quality)
		).apply(instance, ObservedRoad::new));

		public ObservedRoad {
			pos = pos.immutable();
		}
	}

	public record ObservedStructure(BlockPos pos, String kind) {
		public static final Codec<ObservedStructure> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BlockPos.CODEC.fieldOf("pos").forGetter(ObservedStructure::pos),
			Codec.STRING.optionalFieldOf("kind", "building").forGetter(ObservedStructure::kind)
		).apply(instance, ObservedStructure::new));

		public ObservedStructure {
			pos = pos.immutable();
		}
	}

	public record ObservedPoint(BlockPos pos, String kind, String label) {
		public static final Codec<ObservedPoint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BlockPos.CODEC.fieldOf("pos").forGetter(ObservedPoint::pos),
			Codec.STRING.optionalFieldOf("kind", "").forGetter(ObservedPoint::kind),
			Codec.STRING.optionalFieldOf("label", "").forGetter(ObservedPoint::label)
		).apply(instance, ObservedPoint::new));

		public ObservedPoint {
			pos = pos.immutable();
		}
	}

	private record MapPointMarker(String kind, String label) {
	}
}
