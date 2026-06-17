package com.ronhelwig.livevillages.network;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Comparator;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

import com.ronhelwig.livevillages.content.LiveVillagesBlocks;
import com.ronhelwig.livevillages.menu.TradeBoardLogic;
import com.ronhelwig.livevillages.menu.TradeBoardSettlementView;
import com.ronhelwig.livevillages.menu.TradeBoardTrading;
import com.ronhelwig.livevillages.LiveVillagesGameRules;
import com.ronhelwig.livevillages.sim.LiveVillagesSavedData;
import com.ronhelwig.livevillages.sim.RouteState;
import com.ronhelwig.livevillages.sim.SettlementBuildBlockState;
import com.ronhelwig.livevillages.sim.SettlementBuildBlockStatus;
import com.ronhelwig.livevillages.sim.SettlementBuildSite;
import com.ronhelwig.livevillages.sim.SettlementBuildSiteType;
import com.ronhelwig.livevillages.sim.SettlementConstruction;
import com.ronhelwig.livevillages.sim.SettlementConstructionDelivery;
import com.ronhelwig.livevillages.sim.SettlementKind;
import com.ronhelwig.livevillages.sim.SettlementLoadedObservation;
import com.ronhelwig.livevillages.sim.SettlementPlayerStandings;
import com.ronhelwig.livevillages.sim.SettlementRoadwrightWork;
import com.ronhelwig.livevillages.sim.SettlementState;
import com.ronhelwig.livevillages.sim.SettlementTradeRange;
import com.ronhelwig.livevillages.sim.SettlementVillagers;
import com.ronhelwig.livevillages.sim.StructureBlueprintCapture;

public final class LiveVillagesNetworking {
	private static final double OVERLAY_MAX_DISTANCE_BLOCKS = 192.0D;
	private static final double BUILD_SITE_PREVIEW_MAX_DISTANCE_BLOCKS = 192.0D;
	private static final double ACTIVE_BUILD_SITE_PREVIEW_MAX_DISTANCE_BLOCKS = 64.0D;
	private static final int SURVEYOR_MAP_RADIUS_BLOCKS = 96;
	private static final int OVERLAY_STOCK_ROWS = 12;
	private static final int OVERLAY_ROUTE_ROWS = 4;
	private static final int OVERLAY_PROJECT_ROWS = 4;
	private static final long BUILD_SITE_PREVIEW_ACTIVE_TICKS = 80L;
	private static final long TICKS_PER_DAY = 24_000L;
	private static final int PORTMASTER_MAP_MIN_RADIUS_BLOCKS = 128;
	private static final int PORTMASTER_MAP_MAX_RADIUS_BLOCKS = 2_048;
	private static final int PORTMASTER_MAP_TERRAIN_RADIUS_WITHOUT_CARTOGRAPHER = 320;
	private static final int PORTMASTER_MAP_TERRAIN_GRID_SIZE = 184;
	private static final String BLOCKED_REASON_UNSUPPORTED = "unsupported";
	private static final String MAP_MARKER_LOCAL_LIGHTHOUSE = "local_lighthouse";
	private static final String MAP_MARKER_LOCAL_PORT = "local_port";
	private static final String MAP_MARKER_KNOWN_LIGHTHOUSE = "known_lighthouse";
	private static final String MAP_MARKER_KNOWN_PORT = "known_port";
	private static final String MAP_MARKER_KNOWN_SETTLEMENT = "known_settlement";
	private static final Map<UUID, Long> ACTIVE_BUILD_SITE_PREVIEWS = new ConcurrentHashMap<>();

	private LiveVillagesNetworking() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(SettlementOverlayRequestPayload.TYPE, SettlementOverlayRequestPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SettlementOverlayStatePayload.TYPE, SettlementOverlayStatePayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(BuildSitePreviewRequestPayload.TYPE, BuildSitePreviewRequestPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(StructureCaptureRequestPayload.TYPE, StructureCaptureRequestPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(TradeBoardActionPayload.TYPE, TradeBoardActionPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(BuildSitePreviewStatePayload.TYPE, BuildSitePreviewStatePayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(TradeBoardRefreshPayload.TYPE, TradeBoardRefreshPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SurveyorMapStatePayload.TYPE, SurveyorMapStatePayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PortmasterMapStatePayload.TYPE, PortmasterMapStatePayload.STREAM_CODEC);

		ServerPlayNetworking.registerGlobalReceiver(SettlementOverlayRequestPayload.TYPE, (payload, context) -> {
			SettlementOverlaySnapshot snapshot = buildNearestSettlementSnapshot(context.player());
			ServerPlayNetworking.send(context.player(), new SettlementOverlayStatePayload(snapshot));
		});
		ServerPlayNetworking.registerGlobalReceiver(BuildSitePreviewRequestPayload.TYPE, (payload, context) -> {
			if (!payload.active()) {
				ACTIVE_BUILD_SITE_PREVIEWS.remove(context.player().getUUID());
				return;
			}

			ACTIVE_BUILD_SITE_PREVIEWS.put(context.player().getUUID(), (long) context.player().level().getServer().getTickCount());
			BuildSitePreviewSnapshot snapshot = buildBuildSitePreviewSnapshot(context.player(), payload.targetPos());
			ServerPlayNetworking.send(context.player(), new BuildSitePreviewStatePayload(snapshot));
		});
		ServerPlayNetworking.registerGlobalReceiver(StructureCaptureRequestPayload.TYPE, (payload, context) ->
			StructureBlueprintCapture.exportLookedAtStructure(context.player(), payload.targetPos())
		);
		ServerPlayNetworking.registerGlobalReceiver(TradeBoardActionPayload.TYPE, (payload, context) ->
			TradeBoardTrading.handleTradeAction(context.player(), payload)
		);
	}

	public static boolean isBuildSitePreviewActive(ServerPlayer player) {
		Long lastPreviewTick = ACTIVE_BUILD_SITE_PREVIEWS.get(player.getUUID());

		if (lastPreviewTick == null) {
			return false;
		}

		return player.level().getServer().getTickCount() - lastPreviewTick <= BUILD_SITE_PREVIEW_ACTIVE_TICKS;
	}

	public static void sendSurveyorMap(ServerPlayer player, BlockPos surveyorTablePos) {
		ServerPlayNetworking.send(player, new SurveyorMapStatePayload(buildSurveyorMapSnapshot(player, surveyorTablePos)));
	}

	public static void sendPortmasterMap(ServerPlayer player, BlockPos portmasterAnchorPos) {
		ServerPlayNetworking.send(player, new PortmasterMapStatePayload(buildPortmasterMapSnapshot(player, portmasterAnchorPos)));
	}

	private static SurveyorMapSnapshot buildSurveyorMapSnapshot(ServerPlayer player, BlockPos surveyorTablePos) {
		ServerLevel level = (ServerLevel) player.level();
		Optional<SettlementState> settlement = SettlementConstruction.findWorkstationSettlement(level, surveyorTablePos);

		if (settlement.isEmpty()) {
			return SurveyorMapSnapshot.unavailable("No settlement found near this Surveyor's Table.", surveyorTablePos);
		}

		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(level.getServer());
		List<SettlementBuildSite> buildSites = savedData.getBuildSitesForSettlement(settlement.get().id());
		int boundaryRadius = SettlementVillagers.settlementRadiusBlocks(settlement.get());
		int mapRadius = Math.max(SURVEYOR_MAP_RADIUS_BLOCKS, boundaryRadius);
		boolean fogOfWarEnabled = false;
		List<SettlementState> settlementsInDimension = savedData.getSettlements().stream()
			.filter(candidate -> candidate.dimension().equals(settlement.get().dimension()))
			.toList();
		List<RouteState> routes = savedData.getRoutesForSettlement(settlement.get().id());
		SettlementRoadwrightWork.SurveyorMapForecast roadworkForecast = SettlementRoadwrightWork.surveyorMapForecast(
			level,
			settlement.get(),
			buildSites,
			settlementsInDimension,
			routes
		);
		SettlementLoadedObservation.SurveyorObservation observation = SettlementLoadedObservation.captureSurveyorMapObservation(
			level,
			settlement.get(),
			buildSites,
			mapRadius,
			false
		);
		List<BlockPos> waterColumns = SettlementLoadedObservation.captureFullyVisibleSurveyorWater(
			level,
			settlement.get().center(),
			mapRadius
		);
		savedData.storeRoadworkPlans(
			settlement.get().id(),
			SettlementRoadwrightWork.persistentPlansForSettlement(level, settlement.get(), level.getServer().getTickCount())
		);
		return new SurveyorMapSnapshot(
			"",
			settlement.get().name(),
			settlement.get().tier(),
			buildSurveyTimeLabel(level),
			settlement.get().center(),
			mapRadius,
			boundaryRadius,
			observation.roads().stream()
				.map(road -> new SurveyorMapRoadView(road.pos(), road.quality()))
				.toList(),
			waterColumns,
			observation.structures().stream()
				.map(structure -> new SurveyorMapStructureView(structure.pos(), structure.kind()))
				.toList(),
			observation.points().stream()
				.map(point -> new SurveyorMapPointView(point.pos(), point.kind(), point.label()))
				.toList(),
			SettlementVillagers.nearbyRoadwrights(level, settlement.get()).stream()
				.map(villager -> villager.blockPosition().immutable())
				.toList(),
			roadworkForecast.routeTraceBlocks(),
			roadworkForecast.plannedWorkBlocks(),
			fogOfWarEnabled,
			observation.observedAreas()
		);
	}

	private static PortmasterMapSnapshot buildPortmasterMapSnapshot(ServerPlayer player, BlockPos portmasterAnchorPos) {
		ServerLevel level = (ServerLevel) player.level();
		Optional<SettlementState> settlement = SettlementConstruction.findWorkstationSettlement(level, portmasterAnchorPos);

		if (settlement.isEmpty()) {
			return PortmasterMapSnapshot.unavailable("No settlement found near this Portmaster's Anchor.", portmasterAnchorPos);
		}

		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(level.getServer());
		SettlementState homeSettlement = settlement.get();
		SettlementTradeRange.TradeRangeProfile tradeRange = SettlementTradeRange.profile(homeSettlement, SettlementConstruction.survey(level, homeSettlement));
		boolean hasCartographer = tradeRange.hasCartographerSupport();
		List<PortmasterMapPortView> ports = collectPortmasterMapPorts(
			level,
			savedData,
			homeSettlement,
			portmasterAnchorPos,
			hasCartographer,
			tradeRange.portmasterMapDistanceBlocks()
		);
		int farthestPortDistance = ports.stream()
			.mapToInt(PortmasterMapPortView::distanceBlocks)
			.max()
			.orElse(0);
		int radius = roundUpToStep(clamp(
			Math.max(PORTMASTER_MAP_MIN_RADIUS_BLOCKS, Math.max(tradeRange.portmasterMapDistanceBlocks(), farthestPortDistance + 32)),
			PORTMASTER_MAP_MIN_RADIUS_BLOCKS,
			PORTMASTER_MAP_MAX_RADIUS_BLOCKS
		), 32);
		int terrainRadius = hasCartographer ? radius : Math.min(radius, PORTMASTER_MAP_TERRAIN_RADIUS_WITHOUT_CARTOGRAPHER);
		List<String> terrainRows = buildPortmasterTerrainRows(level, savedData, portmasterAnchorPos, terrainRadius, hasCartographer);
		return new PortmasterMapSnapshot(
			"",
			homeSettlement.name(),
			buildSurveyTimeLabel(level),
			hasCartographer,
			portmasterAnchorPos.immutable(),
			radius,
			terrainRadius,
			terrainRows,
			ports
		);
	}

	private static List<PortmasterMapPortView> collectPortmasterMapPorts(
		ServerLevel level,
		LiveVillagesSavedData savedData,
		SettlementState homeSettlement,
		BlockPos anchorPos,
		boolean hasCartographer,
		int maxKnownPortDistanceBlocks
	) {
		return savedData.getSettlements().stream()
			.filter(candidate -> candidate.dimension().equals(homeSettlement.dimension()))
			.filter(candidate -> candidate.kind() != SettlementKind.OUTPOST)
			.map(candidate -> buildKnownPortView(level, homeSettlement, candidate, anchorPos, hasCartographer, maxKnownPortDistanceBlocks))
			.flatMap(Optional::stream)
			.sorted(Comparator.<PortmasterMapPortView>comparingInt(
					point -> portmasterMarkerPriority(point.kind())
				)
				.thenComparingInt(PortmasterMapPortView::distanceBlocks)
				.thenComparing(PortmasterMapPortView::name))
			.toList();
	}

	private static Optional<PortmasterMapPortView> buildKnownPortView(
		ServerLevel level,
		SettlementState homeSettlement,
		SettlementState candidate,
		BlockPos homeAnchorPos,
		boolean hasCartographer,
		int maxKnownPortDistanceBlocks
	) {
		boolean local = candidate.id().equals(homeSettlement.id());
		int distanceBlocks = local
			? 0
			: (int) Math.round(Math.sqrt(homeSettlement.center().distSqr(candidate.center())));

		if (!local && distanceBlocks > maxKnownPortDistanceBlocks) {
			return Optional.empty();
		}

		SettlementConstruction.InfrastructureSurvey survey = SettlementConstruction.survey(level, candidate);
		List<BlockPos> dockOrigins = survey.docks() > 0 ? SettlementConstruction.findDockOrigins(level, candidate) : List.of();
		List<BlockPos> lighthousePositions = survey.lighthouses() > 0 ? SettlementConstruction.findLighthouseTops(level, candidate) : List.of();
		List<BlockPos> anchorPositions = dockOrigins.isEmpty() ? SettlementConstruction.findPlacedPortmasterAnchors(level, candidate) : List.of();
		boolean hasPort = local || !dockOrigins.isEmpty() || !anchorPositions.isEmpty();
		boolean hasLighthouse = local || !lighthousePositions.isEmpty();

		if (!hasPort && !hasLighthouse && !hasCartographer) {
			return Optional.empty();
		}

		BlockPos representativePos = local
			? homeAnchorPos.immutable()
			: hasLighthouse
				? lighthousePositions.stream().findFirst()
				.orElse(candidate.center())
				.immutable()
				: hasPort
				? dockOrigins.stream().findFirst()
				.or(() -> anchorPositions.stream().findFirst())
				.orElse(candidate.center())
				.immutable()
				: candidate.center().immutable();
		int labelDistanceBlocks = local ? 0 : (int) Math.round(Math.sqrt(homeAnchorPos.distSqr(representativePos)));
		String kind = local
			? hasLighthouse ? MAP_MARKER_LOCAL_LIGHTHOUSE : MAP_MARKER_LOCAL_PORT
			: hasLighthouse
				? MAP_MARKER_KNOWN_LIGHTHOUSE
			: hasPort
				? MAP_MARKER_KNOWN_PORT
				: MAP_MARKER_KNOWN_SETTLEMENT;
		return Optional.of(new PortmasterMapPortView(representativePos, candidate.name(), labelDistanceBlocks, kind));
	}

	private static int portmasterMarkerPriority(String kind) {
		return switch (kind) {
			case MAP_MARKER_LOCAL_LIGHTHOUSE -> 0;
			case MAP_MARKER_LOCAL_PORT -> 1;
			case MAP_MARKER_KNOWN_LIGHTHOUSE -> 2;
			case MAP_MARKER_KNOWN_PORT -> 3;
			default -> 4;
		};
	}

	private static List<String> buildPortmasterTerrainRows(
		ServerLevel level,
		LiveVillagesSavedData savedData,
		BlockPos center,
		int radius,
		boolean hasCartographer
	) {
		List<String> rows = new java.util.ArrayList<>(PORTMASTER_MAP_TERRAIN_GRID_SIZE);

		for (int gridZ = 0; gridZ < PORTMASTER_MAP_TERRAIN_GRID_SIZE; gridZ++) {
			StringBuilder row = new StringBuilder(PORTMASTER_MAP_TERRAIN_GRID_SIZE);

			for (int gridX = 0; gridX < PORTMASTER_MAP_TERRAIN_GRID_SIZE; gridX++) {
				int worldX = sampleWorldCoordinate(center.getX(), radius, gridX, PORTMASTER_MAP_TERRAIN_GRID_SIZE);
				int worldZ = sampleWorldCoordinate(center.getZ(), radius, gridZ, PORTMASTER_MAP_TERRAIN_GRID_SIZE);
				row.append(samplePortmasterTerrain(level, savedData, worldX, worldZ, hasCartographer));
			}

			rows.add(row.toString());
		}

		return rows;
	}

	private static char samplePortmasterTerrain(
		ServerLevel level,
		LiveVillagesSavedData savedData,
		int worldX,
		int worldZ,
		boolean hasCartographer
	) {
		BlockPos columnPos = new BlockPos(worldX, level.getSeaLevel(), worldZ);

		if (!level.hasChunkAt(columnPos)) {
			char sharedTerrain = savedData.sharedTerrainAt(level.dimension(), worldX, worldZ);
			if (sharedTerrain != 'U') {
				return sharedTerrain;
			}

			return hasCartographer ? fallbackBiomeTerrain(level, worldX, worldZ) : 'U';
		}

		int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ) - 1;

		if (surfaceY < level.getMinY() || surfaceY > level.getMaxY() - 1) {
			return hasCartographer ? fallbackBiomeTerrain(level, worldX, worldZ) : 'U';
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

	private static int sampleWorldCoordinate(int center, int radius, int gridIndex, int gridSize) {
		double normalized = ((gridIndex + 0.5D) / gridSize) - 0.5D;
		return center + (int) Math.round(normalized * radius * 2.0D);
	}

	private static String buildSurveyTimeLabel(ServerLevel level) {
		long worldTime = level.getOverworldClockTime();
		long day = Math.floorDiv(worldTime, TICKS_PER_DAY) + 1L;
		long dayTime = Math.floorMod(worldTime, TICKS_PER_DAY);
		return "Day " + day + ", " + approximateTimeLabel(dayTime);
	}

	private static String approximateTimeLabel(long dayTime) {
		if (dayTime < 1_500L) {
			return "Sunrise";
		}

		if (dayTime < 5_000L) {
			return "Morning";
		}

		if (dayTime < 8_000L) {
			return "Noon";
		}

		if (dayTime < 11_500L) {
			return "Afternoon";
		}

		if (dayTime < 13_000L) {
			return "Sunset";
		}

		if (dayTime < 16_000L) {
			return "Dusk";
		}

		if (dayTime < 22_000L) {
			return "Night";
		}

		return "Pre-dawn";
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static int roundUpToStep(int value, int step) {
		if (step <= 0) {
			return value;
		}

		return ((value + step - 1) / step) * step;
	}

	private static SettlementOverlaySnapshot buildNearestSettlementSnapshot(ServerPlayer player) {
		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(player.level().getServer());
		var nearestSettlement = savedData.findNearestSettlement(
			player.level().dimension(),
			player.blockPosition(),
			OVERLAY_MAX_DISTANCE_BLOCKS,
			settlement -> true
		);

		if (nearestSettlement.isEmpty()) {
			return SettlementOverlaySnapshot.unavailable("No nearby settlement.");
		}

		SettlementState settlement = nearestSettlement.get();
		savedData.restoreSettlementMapMemory((ServerLevel) player.level(), settlement);
		List<RouteState> routes = savedData.getRoutesForSettlement(settlement.id());
		List<SettlementBuildSite> buildSites = savedData.getBuildSitesForSettlement(settlement.id());
		TradeBoardSettlementView view = TradeBoardLogic.createSettlementView(
			(ServerLevel) player.level(),
			settlement,
			routes,
			settlementId -> savedData.getSettlement(settlementId).map(SettlementState::name).orElse("Unknown"),
			OVERLAY_STOCK_ROWS,
			OVERLAY_ROUTE_ROWS,
			OVERLAY_PROJECT_ROWS,
			SettlementVillagers.nearbyProfessionPopulation((ServerLevel) player.level(), settlement),
			TradeBoardLogic.constructionTradeDemand(buildSites),
			buildSites
		);
		int distanceBlocks = (int) Math.round(Math.sqrt(settlement.center().distSqr(player.blockPosition())));
		List<SettlementConstructionDelivery> deliveries = savedData.getConstructionDeliveries().stream()
			.filter(delivery -> delivery.settlementId().equals(settlement.id()))
			.toList();
		Set<String> deliveryVillagers = deliveries.stream()
			.map(SettlementConstructionDelivery::villagerId)
			.collect(java.util.stream.Collectors.toSet());
		SettlementOverlayConstructionView construction = createConstructionView((ServerLevel) player.level(), settlement, buildSites, deliveries);
		Map<String, SettlementRoadwrightWork.RoadworkDebugPlan> roadworkDebugPlans = SettlementRoadwrightWork.loadedRoadworkDebugPlans(
			(ServerLevel) player.level(),
			settlement,
			buildSites,
			savedData.getSettlements().stream()
				.filter(candidate -> candidate.dimension().equals(settlement.dimension()))
				.toList(),
			savedData.getRoutesForSettlement(settlement.id())
		);
		Map<String, String> roadworkTaskKeysByVillager = roadworkDebugPlans.entrySet().stream()
			.collect(java.util.stream.Collectors.toMap(
				Map.Entry::getKey,
				entry -> entry.getValue().taskKey()
			));
		Map<String, SettlementVillagers.ProfessionDebugInfo> professionDebugByVillager = roadworkDebugPlans.entrySet().stream()
			.collect(java.util.stream.Collectors.toMap(
				Map.Entry::getKey,
				entry -> new SettlementVillagers.ProfessionDebugInfo(
					roadworkTargetLabel(entry.getValue()),
					roadworkDetailLabel(entry.getValue())
				)
			));
		List<SettlementOverlayTaskView> tasks = SettlementVillagers.nearbyVillagerTaskPopulation((ServerLevel) player.level(), settlement, deliveryVillagers, roadworkTaskKeysByVillager).stream()
			.map(taskCount -> new SettlementOverlayTaskView(taskLabel(taskCount.roleKey(), taskCount.taskKey()), taskCount.count()))
			.toList();
		List<SettlementOverlayWorkerView> workers = SettlementVillagers.nearbyVillagerDebugViews(
			(ServerLevel) player.level(),
			settlement,
			deliveryVillagers,
			roadworkTaskKeysByVillager,
			professionDebugByVillager
		).stream()
			.map(worker -> new SettlementOverlayWorkerView(worker.workerLabel(), worker.taskLabel(), worker.targetLabel(), worker.detailLabel()))
			.toList();
		String playerStandingLabel = SettlementPlayerStandings.debugStanding(savedData, settlement, player);
		return SettlementOverlaySnapshot.available(view, distanceBlocks, playerStandingLabel, construction, tasks, workers);
	}

	private static SettlementOverlayConstructionView createConstructionView(
		ServerLevel level,
		SettlementState settlement,
		List<SettlementBuildSite> buildSites,
		List<SettlementConstructionDelivery> deliveries
	) {
		int activeBuildSites = 0;
		int pendingBlocks = 0;
		int missingMaterialBlocks = 0;
		int blockedBlocks = 0;

		for (SettlementBuildSite buildSite : buildSites) {
			if (isBuildSiteCompleteForDisplay(buildSite)) {
				continue;
			}

			activeBuildSites++;

			for (SettlementBuildBlockState block : buildSite.blocks()) {
				if (!SettlementConstruction.isRequiredBuildSiteBlock(buildSite, block)) {
					continue;
				}

				if (block.status() == SettlementBuildBlockStatus.PENDING) {
					pendingBlocks++;
				} else if (block.status() == SettlementBuildBlockStatus.MISSING_MATERIAL) {
					missingMaterialBlocks++;
				} else if (block.status() == SettlementBuildBlockStatus.BLOCKED) {
					blockedBlocks++;
				}
			}
		}

		return new SettlementOverlayConstructionView(
			activeBuildSites,
			settlement.kind() == SettlementKind.OUTPOST
				? SettlementVillagers.nearbyProfessionPopulation(level, settlement).values().stream().mapToInt(Integer::intValue).sum()
				: SettlementVillagers.nearbyConstructionWorkers(level, settlement).size(),
			pendingBlocks,
			missingMaterialBlocks,
			blockedBlocks,
			deliveries.size()
		);
	}

	private static String taskLabel(String roleKey, String taskKey) {
		return humanizeKey(roleKey) + ": " + SettlementVillagers.taskDescription(taskKey);
	}

	private static String roadworkTargetLabel(SettlementRoadwrightWork.RoadworkDebugPlan plan) {
		return "Target: " + humanizeKey(plan.targetKind()) + " @ " + plan.targetPos().getX() + "," + plan.targetPos().getZ();
	}

	private static String roadworkDetailLabel(SettlementRoadwrightWork.RoadworkDebugPlan plan) {
		String cacheText = plan.cached() ? "cached" : "fresh";
		return "Route: " + plan.routeTrace().size() + "b, " + humanizeKey(plan.actionKey().toLowerCase(Locale.ROOT)) + ", " + cacheText;
	}

	private static BuildSitePreviewSnapshot buildBuildSitePreviewSnapshot(ServerPlayer player, Optional<BlockPos> targetPos) {
		Optional<BuildSitePreviewSnapshot> workstationPreview = buildProspectiveWorkstationPreviewSnapshot(player, targetPos);

		if (workstationPreview.isPresent()) {
			return workstationPreview.get();
		}

		Optional<BuildSitePreviewSnapshot> buildSitePreview = buildNearestBuildSitePreviewSnapshot(player, targetPos);

		if (buildSitePreview.isPresent()) {
			return buildSitePreview.get();
		}

		Optional<BuildSitePreviewSnapshot> infrastructurePreview = buildPlacedInfrastructurePreviewSnapshot(player, targetPos);

		return infrastructurePreview.orElseGet(() -> BuildSitePreviewSnapshot.unavailable("No active build site nearby."));
	}

	private static Optional<BuildSitePreviewSnapshot> buildProspectiveWorkstationPreviewSnapshot(ServerPlayer player, Optional<BlockPos> targetPos) {
		ServerLevel level = (ServerLevel) player.level();
		BlockPos placementPos = targetPos.orElse(null);
		ItemStack previewStack = heldPreviewWorkstationStack(player);

		if (placementPos == null || previewStack.isEmpty()) {
			return Optional.empty();
		}

		Item item = previewStack.getItem();
		Optional<SettlementState> settlement = SettlementConstruction.findWorkstationSettlement(level, placementPos);

		if (item != LiveVillagesBlocks.TRADE_BOARD_ITEM && settlement.isEmpty()) {
			return Optional.empty();
		}

		if (isLoneWorkstationPreviewItem(item) && SettlementConstruction.isPositionInExistingShelteredStructure(level, placementPos)) {
			return Optional.empty();
		}

		SettlementConstruction.StructurePreview preview;
		String settlementName;

		if (item == LiveVillagesBlocks.TRADE_BOARD_ITEM) {
			SettlementConstruction.TradeBoardPlacementDecision decision = SettlementConstruction.evaluateTradeBoardPlacement(level, placementPos);
			Direction placementFacing = player.getDirection().getAxis() == Direction.Axis.Y ? Direction.NORTH : player.getDirection().getOpposite();
			preview = SettlementConstruction.previewTradingPostAtWorkstation(
				level,
				decision.settlement().map(SettlementState::id).orElse("preview:new_settlement"),
				placementPos,
				placementFacing
			);
			settlementName = decision.settlement().map(SettlementState::name).orElse("New Settlement");

			if (decision.foundsNewSettlement() && preview.placementValid() && preview.statusMessage().isBlank()) {
				preview = new SettlementConstruction.StructurePreview(
					preview.previewId(),
					preview.previewType(),
					true,
					decision.statusMessage(),
					preview.blockerPositions(),
					preview.blocks()
				);
			}

			if (decision.blocked()) {
				preview = new SettlementConstruction.StructurePreview(
					preview.previewId(),
					preview.previewType(),
					false,
					decision.statusMessage(),
					preview.blockerPositions(),
					preview.blocks()
				);
			}
		} else if (item == LiveVillagesBlocks.PALISADE_POINT_ITEM) {
			return Optional.of(palisadePointPreviewSnapshot(player, settlement.get(), placementPos));
		} else {
			preview = structurePreviewForHeldWorkstation(level, settlement.get(), placementPos, player.getDirection(), previewStack);
			settlementName = settlement.get().name();
		}

		if (preview == null || preview.blocks().isEmpty()) {
			return Optional.empty();
		}

		int distanceBlocks = (int) Math.round(Math.sqrt(placementPos.distSqr(player.blockPosition())));
		String statusMessage = preview.statusMessage();
		if (item == LiveVillagesBlocks.PALISADE_GATEHOUSE_ITEM || item == LiveVillagesBlocks.COPPER_PALISADE_GATEHOUSE_ITEM) {
			statusMessage = palisadeRadiusStatus(statusMessage, settlement.orElse(null), placementPos);
		}
		return Optional.of(BuildSitePreviewSnapshot.prospective(
			statusMessage,
			settlementName,
			preview.previewId(),
			preview.previewType(),
			distanceBlocks,
			preview.placementValid(),
			preview.blockerPositions(),
			preview.blocks().stream()
				.map(block -> new BuildSitePreviewBlockView(block.pos(), block.materialKey(), block.blockId()))
				.toList()
			));
	}

	private static BuildSitePreviewSnapshot palisadePointPreviewSnapshot(ServerPlayer player, SettlementState settlement, BlockPos placementPos) {
		return BuildSitePreviewSnapshot.prospective(
			palisadeRadiusStatus("", settlement, placementPos),
			settlement.name(),
			settlement.id() + ":palisade_point_preview:" + placementPos.getX() + "_" + placementPos.getY() + "_" + placementPos.getZ(),
			"Palisade Point",
			(int) Math.round(Math.sqrt(placementPos.distSqr(player.blockPosition()))),
			true,
			List.of(),
			List.of()
		);
	}

	private static String palisadeRadiusStatus(String statusMessage, SettlementState settlement, BlockPos placementPos) {
		if (settlement == null) {
			return statusMessage;
		}

		double distance = Math.sqrt(horizontalDistanceSqr(settlement.center(), placementPos));
		int radius = Math.max(1, SettlementVillagers.settlementRadiusBlocks(settlement));
		int percent = (int) Math.round(distance * 100.0D / radius);
		int targetBlocks = (int) Math.round(radius * 0.8D);
		String radiusMessage = "Palisade radius: " + percent + "% (" + Math.round(distance) + " of " + radius + "b; target about " + targetBlocks + "b).";
		return statusMessage == null || statusMessage.isBlank() ? radiusMessage : statusMessage + " " + radiusMessage;
	}

	private static double horizontalDistanceSqr(BlockPos left, BlockPos right) {
		double dx = left.getX() - right.getX();
		double dz = left.getZ() - right.getZ();
		return dx * dx + dz * dz;
	}

	private static Optional<BuildSitePreviewSnapshot> buildPlacedInfrastructurePreviewSnapshot(ServerPlayer player, Optional<BlockPos> targetPos) {
		ServerLevel level = (ServerLevel) player.level();
		Optional<BuildSitePreviewSnapshot> targetedAnchorPreview = targetPos.flatMap(pos -> targetedPortmasterAnchorPreview(player, level, pos));

		if (targetedAnchorPreview.isPresent()) {
			return targetedAnchorPreview;
		}

		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(level.getServer());
		double maxDistanceSquared = BUILD_SITE_PREVIEW_MAX_DISTANCE_BLOCKS * BUILD_SITE_PREVIEW_MAX_DISTANCE_BLOCKS;
		PlacedInfrastructurePreviewCandidate bestCandidate = null;

		for (SettlementState settlement : savedData.getSettlements()) {
			if (!settlement.dimension().equals(level.dimension())) {
				continue;
			}

			for (BlockPos anchorPos : SettlementConstruction.findPlacedPortmasterAnchors(level, settlement)) {
				if (savedData.findBuildSite(settlement.id(), SettlementBuildSiteType.DOCK, anchorPos).isPresent()) {
					continue;
				}
				if (SettlementConstruction.hasCompletedDockNearPortmasterAnchor(
					level,
					anchorPos,
					SettlementConstruction.portmasterAnchorFacingFor(level, anchorPos)
				)) {
					continue;
				}

				double distanceSquared = anchorPos.distSqr(player.blockPosition());

				if (distanceSquared > maxDistanceSquared) {
					continue;
				}

				SettlementConstruction.StructurePreview preview = SettlementConstruction.previewDockAtPortmasterAnchor(
					level,
					settlement.id(),
					anchorPos,
					SettlementConstruction.portmasterAnchorFacingFor(level, anchorPos)
				);
				if (preview.blocks().isEmpty()) {
					continue;
				}

				boolean targeted = targetPos.filter(pos -> placedInfrastructureContains(anchorPos, preview, pos)).isPresent();
				if (!targeted && !preview.placementValid()) {
					continue;
				}

				PlacedInfrastructurePreviewCandidate candidate = new PlacedInfrastructurePreviewCandidate(
					settlement,
					anchorPos.immutable(),
					preview,
					distanceSquared,
					targeted
				);

				if (isBetterInfrastructurePreviewCandidate(candidate, bestCandidate)) {
					bestCandidate = candidate;
				}
			}
		}

		if (bestCandidate == null || (!bestCandidate.targeted() && targetPos.isPresent())) {
			return Optional.empty();
		}

		int distanceBlocks = (int) Math.round(Math.sqrt(bestCandidate.distanceSquared()));
		return Optional.of(BuildSitePreviewSnapshot.prospective(
			bestCandidate.preview().statusMessage(),
			bestCandidate.settlement().name(),
			bestCandidate.preview().previewId(),
			bestCandidate.preview().previewType(),
			distanceBlocks,
			bestCandidate.preview().placementValid(),
			bestCandidate.preview().blockerPositions(),
			bestCandidate.preview().blocks().stream()
				.map(block -> new BuildSitePreviewBlockView(block.pos(), block.materialKey(), block.blockId()))
					.toList()
			));
	}

	private static Optional<BuildSitePreviewSnapshot> targetedPortmasterAnchorPreview(ServerPlayer player, ServerLevel level, BlockPos targetPos) {
		if (level.getBlockState(targetPos).is(LiveVillagesBlocks.PORTMASTER_ANCHOR)) {
			return portmasterAnchorPreviewAt(player, level, targetPos);
		}

		for (Direction direction : Direction.values()) {
			BlockPos neighborPos = targetPos.relative(direction);
			if (level.getBlockState(neighborPos).is(LiveVillagesBlocks.PORTMASTER_ANCHOR)) {
				Optional<BuildSitePreviewSnapshot> preview = portmasterAnchorPreviewAt(player, level, neighborPos);
				if (preview.isPresent()) {
					return preview;
				}
			}
		}

		return Optional.empty();
	}

	private static Optional<BuildSitePreviewSnapshot> portmasterAnchorPreviewAt(ServerPlayer player, ServerLevel level, BlockPos anchorPos) {
		Optional<SettlementState> settlement = SettlementConstruction.findWorkstationSettlement(level, anchorPos);
		if (settlement.isEmpty()) {
			return Optional.empty();
		}

		if (LiveVillagesSavedData.get(level.getServer()).findBuildSite(settlement.get().id(), SettlementBuildSiteType.DOCK, anchorPos).isPresent()) {
			return Optional.empty();
		}

		SettlementConstruction.StructurePreview preview = SettlementConstruction.previewDockAtPortmasterAnchor(
			level,
			settlement.get().id(),
			anchorPos,
			SettlementConstruction.portmasterAnchorFacingFor(level, anchorPos)
		);
		if (preview.blocks().isEmpty()) {
			return Optional.empty();
		}

		int distanceBlocks = (int) Math.round(Math.sqrt(anchorPos.distSqr(player.blockPosition())));
		return Optional.of(BuildSitePreviewSnapshot.prospective(
			preview.statusMessage(),
			settlement.get().name(),
			preview.previewId(),
			preview.previewType(),
			distanceBlocks,
			preview.placementValid(),
			preview.blockerPositions(),
			preview.blocks().stream()
				.map(block -> new BuildSitePreviewBlockView(block.pos(), block.materialKey(), block.blockId()))
					.toList()
		));
	}

	private static ItemStack heldPreviewWorkstationStack(ServerPlayer player) {
		if (isPreviewWorkstationItem(player.getMainHandItem().getItem())) {
			return player.getMainHandItem();
		}

		if (isPreviewWorkstationItem(player.getOffhandItem().getItem())) {
			return player.getOffhandItem();
		}

		return ItemStack.EMPTY;
	}

	private static boolean isPreviewWorkstationItem(Item item) {
		return item == LiveVillagesBlocks.BAKERS_COUNTER_ITEM
			|| item == LiveVillagesBlocks.CARPENTER_BENCH_ITEM
			|| item == LiveVillagesBlocks.LIGHTHOUSE_ITEM
			|| item == LiveVillagesBlocks.MINER_WORKSTATION_ITEM
			|| item == LiveVillagesBlocks.SURVEYOR_TABLE_ITEM
			|| item == LiveVillagesBlocks.SCRIBE_DESK_ITEM
			|| item == LiveVillagesBlocks.GARDENER_WORKSTATION_ITEM
			|| item == LiveVillagesBlocks.HONEY_SEPARATOR_ITEM
			|| item == LiveVillagesBlocks.GUARD_POST_ITEM
			|| item == LiveVillagesBlocks.FORESTER_TABLE_ITEM
			|| item == LiveVillagesBlocks.TRADE_BOARD_ITEM
			|| item == LiveVillagesBlocks.PORTMASTER_ANCHOR_ITEM
			|| item == LiveVillagesBlocks.PALISADE_GATEHOUSE_ITEM
			|| item == LiveVillagesBlocks.COPPER_PALISADE_GATEHOUSE_ITEM
			|| item == LiveVillagesBlocks.PALISADE_POINT_ITEM
			|| item == LiveVillagesBlocks.SIMPLE_HOUSING_SHELTER_ITEM
			|| item == LiveVillagesBlocks.HOUSING_SHELTER_ITEM
			|| item == Items.CARTOGRAPHY_TABLE
			|| item == Items.SMOKER
			|| item == Items.STONECUTTER
			|| item == Items.FLETCHING_TABLE
			|| item == Items.BREWING_STAND
			|| item == Items.CAULDRON
			|| item == Items.LECTERN
			|| item == Items.LOOM
			|| item == Items.BLAST_FURNACE
			|| item == Items.SMITHING_TABLE
			|| item == Items.GRINDSTONE;
	}

	private static boolean isLoneWorkstationPreviewItem(Item item) {
		return item == LiveVillagesBlocks.BAKERS_COUNTER_ITEM
			|| item == LiveVillagesBlocks.CARPENTER_BENCH_ITEM
			|| item == LiveVillagesBlocks.MINER_WORKSTATION_ITEM
			|| item == LiveVillagesBlocks.SURVEYOR_TABLE_ITEM
			|| item == LiveVillagesBlocks.SCRIBE_DESK_ITEM
			|| item == LiveVillagesBlocks.GARDENER_WORKSTATION_ITEM
			|| item == LiveVillagesBlocks.HONEY_SEPARATOR_ITEM
			|| item == LiveVillagesBlocks.GUARD_POST_ITEM
			|| item == LiveVillagesBlocks.FORESTER_TABLE_ITEM
			|| item == LiveVillagesBlocks.TRADE_BOARD_ITEM
			|| item == Items.CARTOGRAPHY_TABLE
			|| item == Items.SMOKER
			|| item == Items.STONECUTTER
			|| item == Items.FLETCHING_TABLE
			|| item == Items.BREWING_STAND
			|| item == Items.CAULDRON
			|| item == Items.LECTERN
			|| item == Items.LOOM
			|| item == Items.BLAST_FURNACE
			|| item == Items.SMITHING_TABLE
			|| item == Items.GRINDSTONE;
	}

	private static SettlementConstruction.StructurePreview structurePreviewForHeldWorkstation(
		ServerLevel level,
		SettlementState settlement,
		BlockPos placementPos,
		Direction playerFacing,
		ItemStack stack
	) {
		Direction placementFacing = playerFacing.getAxis() == Direction.Axis.Y ? Direction.NORTH : playerFacing.getOpposite();
		Item item = stack.getItem();

		if (item == LiveVillagesBlocks.BAKERS_COUNTER_ITEM) {
			return SettlementConstruction.previewBakeryAtWorkstation(level, settlement.id(), placementPos, placementFacing);
		}

		if (item == LiveVillagesBlocks.CARPENTER_BENCH_ITEM) {
			return SettlementConstruction.previewCarpenterWorkshopAtWorkstation(level, settlement.id(), placementPos, placementFacing);
		}

		if (item == LiveVillagesBlocks.LIGHTHOUSE_ITEM) {
			return SettlementConstruction.previewLighthouseAtMarker(level, settlement.id(), placementPos);
		}

		if (item == LiveVillagesBlocks.MINER_WORKSTATION_ITEM) {
			return SettlementConstruction.previewMineEntranceAtWorkstation(level, settlement.id(), placementPos, placementFacing);
		}

		if (item == LiveVillagesBlocks.SURVEYOR_TABLE_ITEM) {
			return SettlementConstruction.previewRoadwrightWorkshopAtWorkstation(level, settlement.id(), placementPos, placementFacing);
		}

		if (item == LiveVillagesBlocks.SCRIBE_DESK_ITEM) {
			return SettlementConstruction.previewScribeOfficeAtWorkstation(level, settlement.id(), placementPos, placementFacing);
		}

		if (item == LiveVillagesBlocks.GARDENER_WORKSTATION_ITEM) {
			return SettlementConstruction.previewGardenerShedAtWorkstation(level, settlement.id(), placementPos, placementFacing);
		}

		if (item == LiveVillagesBlocks.HONEY_SEPARATOR_ITEM) {
			return SettlementConstruction.previewBeekeeperApiaryAtWorkstation(level, settlement.id(), placementPos, placementFacing);
		}

		if (item == LiveVillagesBlocks.GUARD_POST_ITEM) {
			return SettlementConstruction.previewGuardPostAtWorkstation(level, settlement.id(), placementPos, placementFacing);
		}

		if (item == LiveVillagesBlocks.FORESTER_TABLE_ITEM) {
			return SettlementConstruction.previewForesterWorkshopAtWorkstation(level, settlement.id(), placementPos, placementFacing);
		}

		if (item == LiveVillagesBlocks.TRADE_BOARD_ITEM) {
			return SettlementConstruction.previewTradingPostAtWorkstation(level, settlement.id(), placementPos, placementFacing);
		}

		if (item == LiveVillagesBlocks.PORTMASTER_ANCHOR_ITEM) {
			return SettlementConstruction.previewDockAtPortmasterAnchor(level, settlement.id(), placementPos, placementFacing);
		}

		if (item == LiveVillagesBlocks.PALISADE_GATEHOUSE_ITEM) {
			return SettlementConstruction.previewPalisadeGatehouseAtDoor(level, settlement.id(), placementPos, SettlementConstruction.facingAwayFrom(settlement.center(), placementPos), false);
		}

		if (item == LiveVillagesBlocks.COPPER_PALISADE_GATEHOUSE_ITEM) {
			return SettlementConstruction.previewPalisadeGatehouseAtDoor(level, settlement.id(), placementPos, SettlementConstruction.facingAwayFrom(settlement.center(), placementPos), true);
		}

		if (item == LiveVillagesBlocks.SIMPLE_HOUSING_SHELTER_ITEM) {
			return SettlementConstruction.previewSimpleHousingShelterAtDoor(level, settlement.id(), placementPos, placementFacing);
		}

		if (item == LiveVillagesBlocks.HOUSING_SHELTER_ITEM) {
			return SettlementConstruction.previewHousingShelterAtDoor(level, settlement.id(), placementPos, placementFacing);
		}

		if (item == Items.CARTOGRAPHY_TABLE) {
			return SettlementConstruction.previewCartographerHouseAtWorkstation(level, settlement, placementPos);
		}

		if (item == Items.SMOKER) {
			return SettlementConstruction.previewButcherShopAtWorkstation(level, settlement.id(), placementPos, placementFacing);
		}

		if (item == Items.STONECUTTER) {
			return SettlementConstruction.previewMasonWorkshopAtWorkstation(level, settlement.id(), placementPos, placementFacing);
		}

		if (item == Items.FLETCHING_TABLE) {
			return SettlementConstruction.previewFletcherHutAtWorkstation(level, settlement.id(), placementPos, SettlementConstruction.fletcherHutFacingFor(settlement, placementPos));
		}

		if (item == Items.BREWING_STAND) {
			return SettlementConstruction.previewClericShrineAtWorkstation(level, settlement.id(), placementPos, placementFacing);
		}

		if (item == Items.CAULDRON) {
			return SettlementConstruction.previewLeatherworkerWorkshopAtWorkstation(level, settlement.id(), placementPos, placementFacing);
		}

		if (item == Items.LECTERN) {
			return SettlementConstruction.previewLibraryAtWorkstation(level, settlement.id(), placementPos, placementFacing);
		}

		if (item == Items.LOOM) {
			return SettlementConstruction.previewShepherdHutAtWorkstation(level, settlement.id(), placementPos, placementFacing);
		}

		if (item == Items.BLAST_FURNACE || item == Items.SMITHING_TABLE || item == Items.GRINDSTONE) {
			return SettlementConstruction.previewSmithyAtWorkstation(level, settlement.id(), placementPos, placementFacing);
		}

		return null;
	}

	private static Optional<BuildSitePreviewSnapshot> buildNearestBuildSitePreviewSnapshot(ServerPlayer player, Optional<BlockPos> targetPos) {
		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(player.level().getServer());
		double maxDistanceSquared = ACTIVE_BUILD_SITE_PREVIEW_MAX_DISTANCE_BLOCKS * ACTIVE_BUILD_SITE_PREVIEW_MAX_DISTANCE_BLOCKS;
		BuildSitePreviewCandidate bestCandidate = null;

		for (SettlementBuildSite buildSite : savedData.getBuildSites()) {
			if (isBuildSiteCompleteForDisplay(buildSite)) {
				continue;
			}

			Optional<SettlementState> settlement = savedData.getSettlement(buildSite.settlementId());
			if (settlement.isEmpty() || !settlement.get().dimension().equals(player.level().dimension())) {
				continue;
			}

			SettlementBuildSite previewBuildSite = SettlementConstruction.applyBiomeMaterialPalette(
				player.level(),
				settlement.get(),
				buildSite,
				player.level().getServer().getTickCount()
			);
			List<BuildSitePreviewBlockView> previewBlocks = previewBlocks(previewBuildSite);
			if (previewBlocks.isEmpty()) {
				continue;
			}

			double distanceSquared = activeBuildSitePreviewDistanceSquared(previewBuildSite, previewBlocks, player.blockPosition());
			if (distanceSquared > maxDistanceSquared) {
				continue;
			}

			boolean targeted = targetPos.filter(pos -> buildSiteContains(previewBuildSite, pos)).isPresent();
			BuildSitePreviewCandidate candidate = new BuildSitePreviewCandidate(
				previewBuildSite,
				settlement.get(),
				previewBlocks,
				distanceSquared,
				targeted
			);

			if (isBetterPreviewCandidate(candidate, bestCandidate)) {
				bestCandidate = candidate;
			}
		}

		if (bestCandidate == null) {
			return Optional.empty();
		}

		int distanceBlocks = (int) Math.round(Math.sqrt(bestCandidate.distanceSquared()));
		List<BlockPos> blockerPositions = activeBuildSiteBlockers(player.level(), bestCandidate.buildSite());
		return Optional.of(BuildSitePreviewSnapshot.active(
			activeBuildSitePreviewStatus(bestCandidate.buildSite(), blockerPositions.size()),
			bestCandidate.settlement().name(),
			bestCandidate.buildSite().id(),
			buildSiteTypeLabel(bestCandidate.buildSite().blueprintId()),
			distanceBlocks,
			blockerPositions,
			bestCandidate.previewBlocks()
		));
	}

	private static double activeBuildSitePreviewDistanceSquared(
		SettlementBuildSite buildSite,
		List<BuildSitePreviewBlockView> previewBlocks,
		BlockPos playerPos
	) {
		double distanceSquared = Math.min(
			Math.min(
				buildSite.origin().distSqr(playerPos),
				buildSite.workstationPos().distSqr(playerPos)
			),
			buildSite.anchorPos().distSqr(playerPos)
		);

		for (BuildSitePreviewBlockView block : previewBlocks) {
			distanceSquared = Math.min(distanceSquared, block.pos().distSqr(playerPos));
		}

		return distanceSquared;
	}

	private static String activeBuildSitePreviewStatus(SettlementBuildSite buildSite, int activeBlockedBlocks) {
		int missingMaterialBlocks = 0;

		for (SettlementBuildBlockState block : buildSite.blocks()) {
			if (!SettlementConstruction.isRequiredBuildSiteBlock(buildSite, block)) {
				continue;
			}

			if (block.status() == SettlementBuildBlockStatus.MISSING_MATERIAL) {
				missingMaterialBlocks++;
			}
		}

		if (activeBlockedBlocks > 0 && missingMaterialBlocks > 0) {
			return "Magenta blocks are blocked: " + activeBlockedBlocks + ". Awaiting materials: " + missingMaterialBlocks + ".";
		}

		if (activeBlockedBlocks > 0) {
			return "Magenta blocks are blocked: " + activeBlockedBlocks + ".";
		}

		if (missingMaterialBlocks > 0) {
			return "Awaiting construction materials: " + missingMaterialBlocks + ".";
		}

		return "";
	}

	private static boolean isBuildSiteCompleteForDisplay(SettlementBuildSite buildSite) {
		if (buildSite.complete()) {
			return true;
		}

		boolean hasRequiredBlocks = false;

		for (SettlementBuildBlockState block : buildSite.blocks()) {
			if (!SettlementConstruction.isRequiredBuildSiteBlock(buildSite, block)) {
				continue;
			}

			hasRequiredBlocks = true;

			if (block.status() != SettlementBuildBlockStatus.PLACED && block.status() != SettlementBuildBlockStatus.PLAYER_PLACED) {
				return false;
			}
		}

		return hasRequiredBlocks;
	}

	private static List<BlockPos> activeBuildSiteBlockers(ServerLevel level, SettlementBuildSite buildSite) {
		return buildSite.blocks().stream()
			.filter(block -> activeBuildSiteBlocker(level, buildSite, block))
			.map(block -> SettlementConstruction.buildSiteBlockPos(buildSite, block))
			.flatMap(Optional::stream)
			.map(BlockPos::immutable)
			.toList();
	}

	private static boolean activeBuildSiteBlocker(ServerLevel level, SettlementBuildSite buildSite, SettlementBuildBlockState block) {
		if (!SettlementConstruction.isRequiredBuildSiteBlock(buildSite, block)) {
			return false;
		}

		if (block.status() != SettlementBuildBlockStatus.BLOCKED) {
			return false;
		}

		Optional<BlockPos> blockPos = SettlementConstruction.buildSiteBlockPos(buildSite, block);
		BlockState plannedState = SettlementConstruction.plannedBuildSiteBlockState(buildSite, block);

		if (blockPos.isEmpty() || plannedState == null || !level.hasChunkAt(blockPos.get())) {
			return false;
		}

		BlockState currentState = level.getBlockState(blockPos.get());

		if (currentState.equals(plannedState)
			|| currentState.is(plannedState.getBlock())
			|| SettlementConstruction.isFlexibleMaterialMatch(currentState, plannedState, block.expectedMaterialKey())
			|| (!BLOCKED_REASON_UNSUPPORTED.equals(block.blocker()) && SettlementConstruction.isBuildSiteReplaceable(currentState))) {
			return false;
		}

		if (BLOCKED_REASON_UNSUPPORTED.equals(block.blocker())) {
			return true;
		}

		return !plannedState.isAir() || !currentState.isAir();
	}

	private static boolean isBetterPreviewCandidate(BuildSitePreviewCandidate candidate, BuildSitePreviewCandidate bestCandidate) {
		if (bestCandidate == null) {
			return true;
		}

		if (candidate.targeted() != bestCandidate.targeted()) {
			return candidate.targeted();
		}

		return candidate.distanceSquared() < bestCandidate.distanceSquared();
	}

	private static boolean isBetterInfrastructurePreviewCandidate(
		PlacedInfrastructurePreviewCandidate candidate,
		PlacedInfrastructurePreviewCandidate bestCandidate
	) {
		if (bestCandidate == null) {
			return true;
		}

		if (candidate.targeted() != bestCandidate.targeted()) {
			return candidate.targeted();
		}

		return candidate.distanceSquared() < bestCandidate.distanceSquared();
	}

	private static List<BuildSitePreviewBlockView> previewBlocks(SettlementBuildSite buildSite) {
		return buildSite.blocks().stream()
			.filter(LiveVillagesNetworking::shouldPreviewBlock)
			.map(block -> previewBlock(buildSite, block))
			.flatMap(Optional::stream)
			.toList();
	}

	private static Optional<BuildSitePreviewBlockView> previewBlock(SettlementBuildSite buildSite, SettlementBuildBlockState block) {
		Optional<BlockPos> pos = SettlementConstruction.buildSiteBlockPos(buildSite, block);
		BlockState plannedState = SettlementConstruction.plannedBuildSiteBlockState(buildSite, block);

		if (pos.isEmpty() || plannedState == null) {
			return Optional.empty();
		}

		if (plannedState.isAir() && !"E".equals(block.blueprintSymbol())) {
			return Optional.empty();
		}

		return Optional.of(new BuildSitePreviewBlockView(
			pos.get(),
			block.expectedMaterialKey(),
			BuiltInRegistries.BLOCK.getKey(plannedState.getBlock()).toString()
		));
	}

	private static boolean shouldPreviewBlock(SettlementBuildBlockState block) {
		return block.status() != SettlementBuildBlockStatus.PLACED && block.status() != SettlementBuildBlockStatus.PLAYER_PLACED;
	}

	private static boolean buildSiteContains(SettlementBuildSite buildSite, BlockPos pos) {
		if (buildSite.anchorPos().equals(pos) || buildSite.workstationPos().equals(pos) || buildSite.origin().equals(pos)) {
			return true;
		}

		for (SettlementBuildBlockState block : buildSite.blocks()) {
			Optional<BlockPos> blockPos = SettlementConstruction.buildSiteBlockPos(buildSite, block);
			if (blockPos.isPresent() && blockPos.get().equals(pos)) {
				return true;
			}
		}

		return false;
	}

	private static boolean placedInfrastructureContains(BlockPos anchorPos, SettlementConstruction.StructurePreview preview, BlockPos pos) {
		if (anchorPos.equals(pos)) {
			return true;
		}

		for (SettlementConstruction.StructurePreviewBlock block : preview.blocks()) {
			if (block.pos().equals(pos)) {
				return true;
			}
		}

		return false;
	}

	private static String buildSiteTypeLabel(SettlementBuildSiteType type) {
		return switch (type) {
			case BAKERY -> "Bakery";
			case BEEKEEPER_APIARY -> "Beekeeper's Apiary";
			case BUTCHER_SHOP -> "Butcher Shop";
			case CARTOGRAPHER_HOUSE -> "Cartographer's House";
			case CARPENTER_WORKSHOP -> "Carpenter's Workshop";
			case CLERIC_SHRINE -> "Cleric Shrine";
			case DOCK -> "Dock";
			case LIGHTHOUSE -> "Lighthouse";
			case LEATHERWORKER_WORKSHOP -> "Leatherworker's Workshop";
			case LIBRARY -> "Library";
			case MASON_WORKSHOP -> "Mason's Workshop";
			case MINE_ENTRANCE -> "Mine Entrance";
			case PALISADE_GATEHOUSE, COPPER_PALISADE_GATEHOUSE -> "Palisade Gatehouse";
			case PALISADE_WALL -> "Palisade Wall";
			case FLETCHER_HUT -> "Fletcher's Hut";
			case FORESTER_WORKSHOP -> "Forester's Workshop";
			case GARDENER_SHED -> "Gardener's Shed";
			case GUARD_POST -> "Guard Post";
			case HOUSING_SHELTER -> "Housing Shelter";
			case ROADWRIGHT_WORKSHOP -> "Roadwright's Workshop";
			case SCRIBE_OFFICE -> "Scribe Office";
			case SHEPHERD_HUT -> "Shepherd's Hut";
			case SIMPLE_HOUSING_SHELTER -> "Simple Housing Shelter";
			case SMITHY -> "Smithy";
			case TRADING_POST -> "Trade Post";
		};
	}

	private static String humanizeKey(String key) {
		String[] parts = key.split("_");
		StringBuilder result = new StringBuilder();

		for (String part : parts) {
			if (part.isBlank()) {
				continue;
			}

			if (!result.isEmpty()) {
				result.append(' ');
			}

			result.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
			if (part.length() > 1) {
				result.append(part.substring(1));
			}
		}

		return result.toString();
	}

	private record BuildSitePreviewCandidate(
		SettlementBuildSite buildSite,
		SettlementState settlement,
		List<BuildSitePreviewBlockView> previewBlocks,
		double distanceSquared,
		boolean targeted
	) {
	}

	private record PlacedInfrastructurePreviewCandidate(
		SettlementState settlement,
		BlockPos anchorPos,
		SettlementConstruction.StructurePreview preview,
		double distanceSquared,
		boolean targeted
	) {
	}
}
