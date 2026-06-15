package com.ronhelwig.livevillages.sim;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import com.ronhelwig.livevillages.LiveVillages;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

public final class VillageAutodetector {
	private static final int BACKGROUND_SCAN_CHUNKS_PER_CYCLE = 2;
	private static final int MAX_PENDING_BACKGROUND_SCANS = 6;
	private static final int VILLAGE_SCAN_RADIUS_BLOCKS = 96;
	private static final double MERGE_RADIUS_BLOCKS = 128.0D;
	private static final TicketType BACKGROUND_SCAN_TICKET_TYPE = TicketType.PLAYER_LOADING;
	private static final Set<String> PENDING_BACKGROUND_SCANS = ConcurrentHashMap.newKeySet();
	private static final Predicate<Holder<net.minecraft.world.entity.ai.village.poi.PoiType>> HOME_POI = holder -> holder.is(PoiTypes.HOME);
	private static final Predicate<Holder<net.minecraft.world.entity.ai.village.poi.PoiType>> MEETING_POI = holder -> holder.is(PoiTypes.MEETING);

	private VillageAutodetector() {
	}

	public static void register() {
		ServerLevelEvents.LOAD.register(VillageAutodetector::onLevelLoad);
		ServerChunkEvents.CHUNK_LOAD.register(VillageAutodetector::onChunkLoad);
	}

	public static void tick(MinecraftServer server) {
		long methodStart = System.nanoTime();
		
		ServerLevel overworld = server.getLevel(Level.OVERWORLD);

		if (overworld == null) {
			return;
		}

		int availableBudget = Math.min(BACKGROUND_SCAN_CHUNKS_PER_CYCLE, MAX_PENDING_BACKGROUND_SCANS - PENDING_BACKGROUND_SCANS.size());

		if (availableBudget <= 0) {
			return;
		}

		BlockPos scanCenter = getBackgroundScanCenter(server);
		List<ChunkPos> chunksToScan = LiveVillagesSavedData.get(server).reserveVillageScanChunks(Level.OVERWORLD, scanCenter, availableBudget);

		for (ChunkPos chunkPos : chunksToScan) {
			queueBackgroundChunkScan(overworld, chunkPos);
		}
		
		long totalTime = System.nanoTime() - methodStart;
		if (totalTime > 5_000_000) { // >5ms
			LiveVillages.LOGGER.warn("VillageAutodetector: tick took {} ms, scanned {} chunks", Math.round(totalTime / 1_000_000.0D), chunksToScan.size());
		}
	}

	private static void onLevelLoad(MinecraftServer server, ServerLevel level) {
		LiveVillagesSavedData.get(server);
	}

	private static void onChunkLoad(ServerLevel level, LevelChunk chunk, boolean generated) {
		registerVillageFromChunk(level, chunk.getPos(), SettlementClock.persistentTick(level));
	}

	public static int rescanAround(ServerLevel level, BlockPos origin, int radiusChunks, long currentTick) {
		ChunkPos centerChunk = ChunkPos.containing(origin);
		int scannedChunks = 0;

		for (int chunkX = centerChunk.x() - radiusChunks; chunkX <= centerChunk.x() + radiusChunks; chunkX++) {
			for (int chunkZ = centerChunk.z() - radiusChunks; chunkZ <= centerChunk.z() + radiusChunks; chunkZ++) {
				registerVillageFromChunk(level, new ChunkPos(chunkX, chunkZ), currentTick);
				scannedChunks++;
			}
		}

		return scannedChunks;
	}

	private static void registerVillageFromChunk(ServerLevel level, ChunkPos chunkPos, long currentTick) {
		PoiManager poiManager = level.getPoiManager();
		poiManager.ensureLoadedAndValid(level, chunkPos.getMiddleBlockPosition(64), VILLAGE_SCAN_RADIUS_BLOCKS);
		List<BlockPos> meetingPoints = poiManager.getInChunk(MEETING_POI, chunkPos, PoiManager.Occupancy.ANY)
			.map(PoiRecord::getPos)
			.toList();
		List<BlockPos> homePoints = poiManager.getInChunk(HOME_POI, chunkPos, PoiManager.Occupancy.ANY)
			.map(PoiRecord::getPos)
			.toList();

		if (meetingPoints.isEmpty() && homePoints.isEmpty()) {
			return;
		}

		BlockPos candidateCenter = determineCandidateCenter(chunkPos, meetingPoints, homePoints);
		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(level.getServer());
			Optional<SettlementState> existing = savedData.findNearestSettlement(level.dimension(), candidateCenter, MERGE_RADIUS_BLOCKS, settlement -> settlement.kind() != SettlementKind.OUTPOST);
			long nearbyHomes = countNearbyHomes(level, candidateCenter);
			long nearbyMeetingPoints = countNearbyMeetingPoints(level, candidateCenter);
			long nearbyVillagers = SettlementVillagers.countNearbyVillagers(level, candidateCenter, VILLAGE_SCAN_RADIUS_BLOCKS);
			int estimatedPopulation = (int) nearbyVillagers;

			if (existing.isPresent()) {
				SettlementState settlement = existing.get();
				int updatedHousingCapacity = Math.max(settlement.housingCapacity(), (int) nearbyHomes);
				SettlementState updatedSettlement = settlement;

				if (updatedHousingCapacity != settlement.housingCapacity()) {
					updatedSettlement = updatedSettlement.withHousingCapacity(updatedHousingCapacity);
				}

				if (settlement.kind() == SettlementKind.VILLAGE && estimatedPopulation > settlement.totalPopulation()) {
					updatedSettlement = updatedSettlement.withPopulation(SettlementVillagers.createOperationalPopulation(estimatedPopulation));
				}

				if (settlement.kind() == SettlementKind.VILLAGE && settlement.stock().isEmpty()) {
					updatedSettlement = updatedSettlement.withStock(starterStockForPopulation(Math.max(estimatedPopulation, updatedSettlement.totalPopulation()), nearbyHomes));
				}

				if (!updatedSettlement.equals(settlement)) {
					savedData.putSettlement(updatedSettlement);
				}

				return;
			}

		if (!isLikelyVillageSignature(nearbyHomes, nearbyMeetingPoints, nearbyVillagers)) {
			return;
		}

		String id = createVillageId(level.dimension(), candidateCenter);
		String name = SettlementNamer.generateUniqueName(SettlementKind.VILLAGE, level, candidateCenter, savedData.getSettlements());
			SettlementState settlement = new SettlementState(
				id,
				name,
				level.dimension(),
				candidateCenter,
				SettlementKind.VILLAGE,
				1,
				SettlementVillagers.createOperationalPopulation(estimatedPopulation),
				java.util.Map.of(),
				starterStockForPopulation(estimatedPopulation, nearbyHomes),
				(int) nearbyHomes,
				1.0D,
				0.0D,
				0,
				0.0D,
				java.util.List.of(),
				currentTick,
			currentTick
		);
		savedData.putSettlement(settlement);
	}

	private static Map<String, Integer> starterStockForPopulation(int estimatedPopulation, long nearbyHomes) {
		int population = Math.max(1, estimatedPopulation);
		int homes = Math.max(1, (int) nearbyHomes);
		Map<String, Integer> stock = new LinkedHashMap<>();

		stock.put("bread", population * 7);
		stock.put("baked_potato", population * 3);
		stock.put("carrot", population * 2);
		stock.put("beetroot", population * 2);
		stock.put("wheat", 16 + population * 6);
		stock.put("logs", 24 + population * 8 + homes * 2);
		stock.put("planks", 48 + population * 12 + homes * 4);
		stock.put("cobblestone", 48 + population * 12 + homes * 3);
		stock.put("stick", 8 + population * 2);
		stock.put("dirt", 16 + population * 4);
		stock.put("ladder", Math.max(4, population));

		return stock;
	}

	public static boolean isLikelyVillage(ServerLevel level, BlockPos center) {
		return isLikelyVillageSignature(
			countNearbyHomes(level, center),
			countNearbyMeetingPoints(level, center),
			SettlementVillagers.countNearbyVillagers(level, center, VILLAGE_SCAN_RADIUS_BLOCKS)
		);
	}

	private static BlockPos determineCandidateCenter(ChunkPos chunkPos, List<BlockPos> meetingPoints, List<BlockPos> homePoints) {
		if (!meetingPoints.isEmpty()) {
			return meetingPoints.stream()
				.min(Comparator.comparingDouble(point -> point.distSqr(chunkPos.getMiddleBlockPosition(point.getY()))))
				.orElse(meetingPoints.getFirst())
				.immutable();
		}

		if (!homePoints.isEmpty()) {
			long sumX = 0;
			long sumY = 0;
			long sumZ = 0;

			for (BlockPos homePoint : homePoints) {
				sumX += homePoint.getX();
				sumY += homePoint.getY();
				sumZ += homePoint.getZ();
			}

			int count = homePoints.size();
			return new BlockPos((int) (sumX / count), (int) (sumY / count), (int) (sumZ / count));
		}

		return chunkPos.getMiddleBlockPosition(0);
	}

	private static String createVillageId(net.minecraft.resources.ResourceKey<Level> dimension, BlockPos center) {
		String dimensionKey = dimension.identifier().toString().replace(':', '_').replace('/', '_');
		return "village:" + dimensionKey + "_" + center.getX() + "_" + center.getZ();
	}

	private static long countNearbyHomes(ServerLevel level, BlockPos center) {
		return level.getPoiManager().getCountInRange(HOME_POI, center, VILLAGE_SCAN_RADIUS_BLOCKS, PoiManager.Occupancy.ANY);
	}

	private static long countNearbyMeetingPoints(ServerLevel level, BlockPos center) {
		return level.getPoiManager().getCountInRange(MEETING_POI, center, VILLAGE_SCAN_RADIUS_BLOCKS, PoiManager.Occupancy.ANY);
	}

	private static boolean isLikelyVillageSignature(long nearbyHomes, long nearbyMeetingPoints, long nearbyVillagers) {
		boolean hasVillagePois = nearbyHomes > 0 || nearbyMeetingPoints > 0;
		return hasVillagePois && nearbyVillagers > 0;
	}

	private static void queueBackgroundChunkScan(ServerLevel level, ChunkPos chunkPos) {
		String scanKey = level.dimension().identifier() + ":" + chunkPos.x() + "," + chunkPos.z();

		if (!PENDING_BACKGROUND_SCANS.add(scanKey)) {
			return;
		}

		if (level.getChunkSource().getChunkNow(chunkPos.x(), chunkPos.z()) != null) {
			try {
				registerVillageFromChunk(level, chunkPos, SettlementClock.persistentTick(level));
			} finally {
				PENDING_BACKGROUND_SCANS.remove(scanKey);
			}

			return;
		}

		try {
			level.getChunkSource().addTicketAndLoadWithRadius(BACKGROUND_SCAN_TICKET_TYPE, chunkPos, 0)
				.whenComplete((result, throwable) -> level.getServer().execute(() -> {
					try {
						if (throwable == null) {
							registerVillageFromChunk(level, chunkPos, SettlementClock.persistentTick(level));
						}
					} finally {
						level.getChunkSource().removeTicketWithRadius(BACKGROUND_SCAN_TICKET_TYPE, chunkPos, 0);
						PENDING_BACKGROUND_SCANS.remove(scanKey);
					}
				}));
		} catch (RuntimeException exception) {
			PENDING_BACKGROUND_SCANS.remove(scanKey);
			throw exception;
		}
	}

	private static BlockPos getBackgroundScanCenter(MinecraftServer server) {
		var respawnData = server.getRespawnData();

		if (respawnData != null && Level.OVERWORLD.equals(respawnData.dimension())) {
			return respawnData.pos();
		}

		return BlockPos.ZERO;
	}
}
