package com.ronhelwig.livevillages.sim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import com.ronhelwig.livevillages.content.LiveVillagesBlocks;

public final class SettlementChurchWork {
	static final long DAY_TICKS = 24_000L;
	static final int MEETING_INTERVAL_DAYS = 8;
	static final long MEETING_START_TICK = 3_600L;
	static final long MEETING_DURATION_TICKS = 1_000L;
	static final long MEETING_END_TICK = MEETING_START_TICK + MEETING_DURATION_TICKS;
	private static final int RING_COUNT = 3;
	private static final int RING_SPACING_TICKS = 40;
	private static final double SERVICE_WALK_SPEED = 0.85D;
	private static final int LEISURE_SCAN_RADIUS_BLOCKS = 36;
	private static final int LEISURE_SCAN_STEP_BLOCKS = 4;
	private static final int MAX_LEISURE_SPOTS = 24;
	private static final long LEISURE_CACHE_TICKS = 200L;
	private static final Map<String, LeisureCache> LEISURE_CACHES = new HashMap<>();
	private static final Map<String, Long> LAST_SERVICE_RING_TICKS = new HashMap<>();
	private static final ThreadLocal<Boolean> SERVICE_RING = ThreadLocal.withInitial(() -> false);

	private SettlementChurchWork() {
	}

	public static boolean isServiceRing() {
		return Boolean.TRUE.equals(SERVICE_RING.get());
	}

	public static boolean isChurchMeetingDay(ServerLevel level) {
		return Math.floorMod(Math.floorDiv(level.getOverworldClockTime(), DAY_TICKS), MEETING_INTERVAL_DAYS) == 0;
	}

	public static boolean isChurchServiceTime(ServerLevel level) {
		if (!isChurchMeetingDay(level)) {
			return false;
		}

		long dayTime = Math.floorMod(level.getOverworldClockTime(), DAY_TICKS);
		return dayTime >= MEETING_START_TICK && dayTime < MEETING_END_TICK;
	}

	public static boolean isChurchSabbathRest(ServerLevel level) {
		if (!isChurchMeetingDay(level)) {
			return false;
		}

		long dayTime = Math.floorMod(level.getOverworldClockTime(), DAY_TICKS);
		return dayTime >= MEETING_START_TICK && dayTime < SettlementVillagerWorkSchedule.VILLAGE_GATHERING_START_TICK;
	}

	public static double comfortBonus(int completedChurchTier, int clericCount) {
		double churchBonus = switch (Math.max(0, completedChurchTier)) {
			case 0 -> 0.0D;
			case 1 -> 0.04D;
			case 2 -> 0.07D;
			case 3 -> 0.10D;
			default -> 0.14D;
		};
		double clericBonus = completedChurchTier > 0 && clericCount > 0 ? 0.03D : 0.0D;
		return churchBonus + clericBonus;
	}

	public static int highestCompletedChurchTier(List<SettlementBuildSite> buildSites) {
		int highest = 0;
		for (SettlementBuildSite buildSite : buildSites) {
			if (buildSite.blueprintId() != SettlementBuildSiteType.CLERIC_SHRINE || !buildSite.complete()) {
				continue;
			}

			highest = Math.max(highest, SettlementConstruction.churchCivicTier(buildSite));
		}

		return highest;
	}

	public static boolean maintainLoadedChurchMeetings(
		ServerLevel level,
		SettlementState settlement,
		List<SettlementBuildSite> buildSites
	) {
		if (!SettlementVillagers.usesActualVillagers(settlement) || !hasChurchAndCleric(level, settlement, buildSites)) {
			return false;
		}

		if (isChurchServiceTime(level)) {
			ringChurchBells(level, settlement, buildSites);
			directVillagersToService(level, settlement, buildSites);
			return false;
		}

		if (isChurchSabbathRest(level)) {
			directVillagersToLeisure(level, settlement);
		}

		return false;
	}

	public static Optional<String> loadedChurchTaskKey(ServerLevel level, Villager villager) {
		if (villager.isBaby() || villager.isSleeping()) {
			return Optional.empty();
		}

		if (isChurchServiceTime(level)) {
			return Optional.of(villager.getVillagerData().profession().is(VillagerProfession.CLERIC) ? "leading_service" : "attending_church");
		}

		if (isChurchSabbathRest(level)) {
			return Optional.of("enjoying_the_village");
		}

		return Optional.empty();
	}

	private static boolean hasChurchAndCleric(ServerLevel level, SettlementState settlement, List<SettlementBuildSite> buildSites) {
		return highestCompletedChurchTier(buildSites) > 0
			&& SettlementVillagers.nearbyAdultVillagers(level, settlement).stream()
				.anyMatch(villager -> villager.getVillagerData().profession().is(VillagerProfession.CLERIC));
	}

	private static void ringChurchBells(ServerLevel level, SettlementState settlement, List<SettlementBuildSite> buildSites) {
		long dayTime = Math.floorMod(level.getOverworldClockTime(), DAY_TICKS);
		int ringIndex = (int) ((dayTime - MEETING_START_TICK) / RING_SPACING_TICKS);
		if (ringIndex < 0 || ringIndex >= RING_COUNT) {
			return;
		}

		String ringKey = settlement.id() + "|" + Math.floorDiv(level.getOverworldClockTime(), DAY_TICKS) + "|" + ringIndex;
		if (LAST_SERVICE_RING_TICKS.containsKey(ringKey)) {
			return;
		}

		Optional<BlockPos> bellPos = findChurchBell(level, settlement, buildSites);
		if (bellPos.isEmpty()) {
			return;
		}

		LAST_SERVICE_RING_TICKS.put(ringKey, (long) level.getServer().getTickCount());
		SERVICE_RING.set(true);
		try {
			BlockState state = level.getBlockState(bellPos.get());
			if (state.getBlock() instanceof BellBlock bell) {
				bell.attemptToRing(level, bellPos.get(), state.getValue(BellBlock.FACING));
			}
		} finally {
			SERVICE_RING.set(false);
		}
	}

	private static void directVillagersToService(ServerLevel level, SettlementState settlement, List<SettlementBuildSite> buildSites) {
		Optional<SettlementBuildSite> church = completedChurch(buildSites);
		if (church.isEmpty()) {
			return;
		}

		List<Villager> adults = SettlementVillagers.nearbyAdultVillagers(level, settlement);
		List<Villager> clerics = adults.stream()
			.filter(villager -> villager.getVillagerData().profession().is(VillagerProfession.CLERIC))
			.sorted(Comparator.comparing(villager -> villager.getUUID().toString()))
			.toList();
		Optional<BlockPos> altarStand = standingPosBehind(level, church.get(), 'W');
		Optional<BlockPos> pulpitStand = standingPosBehind(level, church.get(), 'p');
		List<BlockPos> pews = congregationStands(level, church.get());

		int pewIndex = 0;
		for (Villager villager : adults) {
			BlockPos target;
			if (!clerics.isEmpty() && villager.getUUID().equals(clerics.get(0).getUUID()) && altarStand.isPresent()) {
				target = altarStand.get();
			} else if (clerics.size() > 1 && villager.getUUID().equals(clerics.get(1).getUUID()) && pulpitStand.isPresent()) {
				target = pulpitStand.get();
			} else if (!pews.isEmpty()) {
				target = pews.get(Math.floorMod(pewIndex++, pews.size()));
			} else {
				target = church.get().workstationPos();
			}

			SettlementNavigation.moveToRoutineTarget(level, settlement, villager, target, SERVICE_WALK_SPEED);
		}
	}

	private static void directVillagersToLeisure(ServerLevel level, SettlementState settlement) {
		List<BlockPos> spots = leisureSpots(level, settlement);
		List<Villager> adults = SettlementVillagers.nearbyAdultVillagers(level, settlement);
		if (spots.isEmpty()) {
			return;
		}

		for (Villager villager : adults) {
			BlockPos spot = spots.get(Math.floorMod(villager.getUUID().hashCode(), spots.size()));
			SettlementNavigation.moveToRoutineTarget(level, settlement, villager, spot, SERVICE_WALK_SPEED);
		}
	}

	private static Optional<SettlementBuildSite> completedChurch(List<SettlementBuildSite> buildSites) {
		return buildSites.stream()
			.filter(site -> site.blueprintId() == SettlementBuildSiteType.CLERIC_SHRINE && site.complete())
			.max(Comparator.comparingInt(SettlementConstruction::churchCivicTier));
	}

	private static Optional<BlockPos> findChurchBell(ServerLevel level, SettlementState settlement, List<SettlementBuildSite> buildSites) {
		for (SettlementBuildSite buildSite : buildSites) {
			if (buildSite.blueprintId() != SettlementBuildSiteType.CLERIC_SHRINE) {
				continue;
			}

			for (SettlementBuildBlockState block : buildSite.blocks()) {
				if (!"g".equals(block.blueprintSymbol()) && !"copper_bell".equals(block.expectedMaterialKey())) {
					continue;
				}

				Optional<BlockPos> pos = SettlementConstruction.buildSiteBlockPos(buildSite, block);
				if (pos.isPresent() && level.getBlockState(pos.get()).getBlock() instanceof BellBlock) {
					return pos;
				}
			}
		}

		int radius = SettlementVillagers.settlementRadiusBlocks(settlement);
		BlockPos center = settlement.center();
		BlockPos.MutableBlockPos scan = new BlockPos.MutableBlockPos();
		for (int x = center.getX() - radius; x <= center.getX() + radius; x += 2) {
			for (int z = center.getZ() - radius; z <= center.getZ() + radius; z += 2) {
				int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				for (int y = surfaceY; y <= surfaceY + 16; y++) {
					scan.set(x, y, z);
					if (level.getBlockState(scan).is(LiveVillagesBlocks.COPPER_BELL)) {
						return Optional.of(scan.immutable());
					}
				}
			}
		}

		return Optional.empty();
	}

	private static Optional<BlockPos> standingPosBehind(ServerLevel level, SettlementBuildSite church, char symbol) {
		for (SettlementBuildBlockState block : church.blocks()) {
			if (block.blueprintSymbol().isEmpty() || block.blueprintSymbol().charAt(0) != symbol) {
				continue;
			}

			Optional<BlockPos> workstation = SettlementConstruction.buildSiteBlockPos(church, block);
			if (workstation.isEmpty()) {
				continue;
			}

			return Optional.of(workstation.get().relative(church.facing().getOpposite()));
		}

		return Optional.empty();
	}

	private static List<BlockPos> congregationStands(ServerLevel level, SettlementBuildSite church) {
		List<BlockPos> stands = new ArrayList<>();
		BlockPos altar = church.workstationPos();
		Direction facing = church.facing();
		for (int forward = 1; forward <= 6; forward++) {
			for (int right = -2; right <= 2; right++) {
				stands.add(altar.relative(facing, forward).relative(facing.getClockWise(), right));
			}
		}

		return stands;
	}

	private static List<BlockPos> leisureSpots(ServerLevel level, SettlementState settlement) {
		long tick = level.getServer().getTickCount();
		LeisureCache cached = LEISURE_CACHES.get(settlement.id());
		if (cached != null && tick - cached.tick() < LEISURE_CACHE_TICKS) {
			return cached.spots();
		}

		List<BlockPos> spots = new ArrayList<>();
		BlockPos center = settlement.center();
		for (int x = center.getX() - LEISURE_SCAN_RADIUS_BLOCKS; x <= center.getX() + LEISURE_SCAN_RADIUS_BLOCKS; x += LEISURE_SCAN_STEP_BLOCKS) {
			for (int z = center.getZ() - LEISURE_SCAN_RADIUS_BLOCKS; z <= center.getZ() + LEISURE_SCAN_RADIUS_BLOCKS; z += LEISURE_SCAN_STEP_BLOCKS) {
				BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, center.getY(), z));
				if (isLeisureSpot(level, surface) && spots.size() < MAX_LEISURE_SPOTS) {
					spots.add(surface);
				}
			}
		}

		LEISURE_CACHES.put(settlement.id(), new LeisureCache(List.copyOf(spots), tick));
		return spots;
	}

	private static boolean isLeisureSpot(ServerLevel level, BlockPos surface) {
		BlockState ground = level.getBlockState(surface.below());
		BlockState above = level.getBlockState(surface);
		return above.is(BlockTags.FLOWERS)
			|| above.getBlock() instanceof BushBlock
			|| above.is(BlockTags.LEAVES)
			|| above.is(BlockTags.LOGS)
			|| ground.is(BlockTags.LEAVES)
			|| ground.is(BlockTags.LOGS);
	}

	private record LeisureCache(List<BlockPos> spots, long tick) {
	}
}
