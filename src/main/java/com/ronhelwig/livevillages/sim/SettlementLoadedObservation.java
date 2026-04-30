package com.ronhelwig.livevillages.sim;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
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
	private static final int SURVEYOR_MAX_ROADS = 512;
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

		if (!memory.roads.isEmpty() || !memory.points.isEmpty() || !memory.observedAreas.isEmpty()) {
			return;
		}

		for (ObservedRoad road : observation.roads()) {
			memory.roads.put(road.pos(), new TimedRoad(road, tick));
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
		LinkedHashMap<BlockPos, ObservedPoint> points = new LinkedHashMap<>();
		BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();

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

				if (points.size() < SURVEYOR_MAX_POINTS) {
					observeSurfacePointColumn(level, points, x, z);
				}
			}
		}

		return new SurveyorObservation(
			limitRoads(roads.values()),
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
			villager -> villager.distanceToSqr(center.getX() + 0.5D, center.getY() + 0.5D, center.getZ() + 0.5D) <= maxDistanceSquared
		));
		List<Villager> snapshot = List.copyOf(villagers);
		VILLAGER_CACHE.put(cacheKey, new CachedVillagers(snapshot, tick));
		return snapshot;
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

	private static void pruneSurveyorMemory(SurveyorMemory memory, long tick) {
		memory.roads.entrySet().removeIf(entry -> tick - entry.getValue().tick() > SURVEYOR_MEMORY_TICKS);
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

		if (state.is(Blocks.COBBLESTONE) || state.is(Blocks.COBBLESTONE_STAIRS) || state.is(Blocks.COBBLESTONE_SLAB) || state.is(Blocks.STONE) || state.is(Blocks.STONE_STAIRS) || state.is(Blocks.STONE_SLAB)) {
			return "cobble";
		}

		if (state.is(Blocks.STONE_BRICKS) || state.is(Blocks.STONE_BRICK_STAIRS) || state.is(Blocks.STONE_BRICK_SLAB) || state.is(Blocks.BRICKS) || state.is(Blocks.BRICK_STAIRS) || state.is(Blocks.BRICK_SLAB) || state.is(Blocks.SMOOTH_STONE) || state.is(Blocks.SMOOTH_STONE_SLAB)) {
			return "finished";
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
			case BUTCHER_SHOP -> "Butcher";
			case DOCK -> "Dock";
			case LIGHTHOUSE -> "Lighthouse";
			case TRADING_POST -> "Trading Post";
			case CARPENTER_WORKSHOP -> "Carpenter";
			case FLETCHER_HUT -> "Fletcher";
			case ROADWRIGHT_WORKSHOP -> "Roadwright";
			case CARTOGRAPHER_HOUSE -> "Cartographer";
			case FORESTER_WORKSHOP -> "Forester";
			case HOUSING_SHELTER -> "Housing";
			case SIMPLE_HOUSING_SHELTER -> "Shelter";
		};
	}

	private static ObservedRoad observedRoadAt(ServerLevel level, int x, int z) {
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		BlockPos pos = new BlockPos(x, y, z);
		BlockState state = level.getBlockState(pos);
		String quality = roadQuality(state);

		if (quality.isBlank() && state.is(Blocks.LEAF_LITTER)) {
			pos = pos.below();
			quality = roadQuality(level.getBlockState(pos));
		}

		if (quality.isBlank()) {
			return null;
		}

		return new ObservedRoad(pos, quality);
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

	private static List<ObservedRoad> limitRoads(Collection<ObservedRoad> roads) {
		return roads.stream()
			.limit(SURVEYOR_MAX_ROADS)
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
		private final LinkedHashMap<BlockPos, TimedPoint> points = new LinkedHashMap<>();
		private final LinkedHashMap<BlockPos, TimedObservedArea> observedAreas = new LinkedHashMap<>();

		private SurveyorObservation snapshot() {
			List<ObservedRoad> roadSnapshot = roads.values().stream()
				.sorted((first, second) -> Long.compare(second.tick(), first.tick()))
				.map(TimedRoad::road)
				.limit(SURVEYOR_MAX_ROADS)
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
			return new SurveyorObservation(roadSnapshot, pointSnapshot, observedAreaSnapshot);
		}
	}

	public record SurveyorObservation(List<ObservedRoad> roads, List<ObservedPoint> points, List<BlockPos> observedAreas) {
		public static final Codec<SurveyorObservation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			ObservedRoad.CODEC.listOf().optionalFieldOf("roads", List.of()).forGetter(SurveyorObservation::roads),
			ObservedPoint.CODEC.listOf().optionalFieldOf("points", List.of()).forGetter(SurveyorObservation::points),
			BlockPos.CODEC.listOf().optionalFieldOf("observed_areas", List.of()).forGetter(SurveyorObservation::observedAreas)
		).apply(instance, SurveyorObservation::new));

		public SurveyorObservation {
			roads = List.copyOf(roads);
			points = List.copyOf(points);
			observedAreas = List.copyOf(observedAreas);
		}

		public boolean empty() {
			return roads.isEmpty() && points.isEmpty() && observedAreas.isEmpty();
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
