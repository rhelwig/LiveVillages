package com.ronhelwig.livevillages.sim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

import com.ronhelwig.livevillages.content.LiveVillagesVillagerProfessions;

public final class SettlementVillagers {
	private static final int STANDARD_RADIUS_BLOCKS = 64;
	private static final int VILLAGE_RADIUS_BLOCKS = 96;
	private static final int JOB_SITE_SEARCH_RADIUS_BLOCKS = 48;
	private static final int CARPENTER_HOME_SEARCH_RADIUS_BLOCKS = 5;
	private static final int WORKSTATION_HOME_SEARCH_RADIUS_BLOCKS = 5;
	private static final int HOME_SEARCH_RADIUS_BLOCKS = 96;
	private static final int CHILDCARE_RADIUS_BLOCKS = 5;
	private static final int GATHERING_RADIUS_BLOCKS = 12;
	private static final long VILLAGE_GATHERING_START_TICK = 9_000L;
	private static final long VILLAGE_REST_START_TICK = 12_000L;
	private static final long VILLAGE_WAKEUP_END_TICK = 2_000L;
	private static final int CUSTOM_PROFESSION_LOCK_XP = 1;

	private SettlementVillagers() {
	}

	public static boolean usesActualVillagers(SettlementState settlement) {
		return settlement.kind() != SettlementKind.OUTPOST;
	}

	public static int countNearbyVillagers(ServerLevel level, SettlementState settlement) {
		return countNearbyVillagers(level, settlement.center(), villagerRadius(settlement));
	}

	public static int settlementRadiusBlocks(SettlementState settlement) {
		return villagerRadius(settlement);
	}

	public static int countNearbyVillagers(ServerLevel level, BlockPos center, int radiusBlocks) {
		return nearbyVillagers(level, center, radiusBlocks).size();
	}

	public static Map<String, Integer> censusPopulation(ServerLevel level, SettlementState settlement) {
		return createOperationalPopulation(nearbyVillagers(level, settlement.center(), villagerRadius(settlement)));
	}

	public static Map<String, Integer> nearbyProfessionPopulation(ServerLevel level, SettlementState settlement) {
		if (!usesActualVillagers(settlement)) {
			return Map.copyOf(settlement.population());
		}

		LinkedHashMap<String, Integer> population = new LinkedHashMap<>();

		for (Villager villager : nearbyVillagers(level, settlement.center(), villagerRadius(settlement))) {
			String professionKey = displayProfessionKey(villager);

			if (professionKey == null || professionKey.isBlank()) {
				continue;
			}

			population.merge(professionKey, 1, Integer::sum);
		}

		return population;
	}

	public static List<Villager> nearbyFarmers(ServerLevel level, SettlementState settlement) {
		return nearbyVillagers(level, settlement.center(), villagerRadius(settlement)).stream()
			.filter(villager -> !villager.isBaby() && villager.getVillagerData().profession().is(VillagerProfession.FARMER))
			.toList();
	}

	public static List<Villager> nearbyButchers(ServerLevel level, SettlementState settlement) {
		return nearbyVillagers(level, settlement.center(), villagerRadius(settlement)).stream()
			.filter(villager -> !villager.isBaby() && villager.getVillagerData().profession().is(VillagerProfession.BUTCHER))
			.toList();
	}

	public static List<Villager> nearbyFishermen(ServerLevel level, SettlementState settlement) {
		return nearbyVillagers(level, settlement.center(), villagerRadius(settlement)).stream()
			.filter(villager -> !villager.isBaby() && villager.getVillagerData().profession().is(VillagerProfession.FISHERMAN))
			.toList();
	}

	public static List<Villager> nearbyAdultVillagers(ServerLevel level, SettlementState settlement) {
		return nearbyAdultVillagers(level, settlement, villagerRadius(settlement));
	}

	public static List<Villager> nearbyAdultVillagers(ServerLevel level, SettlementState settlement, int radiusBlocks) {
		return nearbyVillagers(level, settlement.center(), radiusBlocks).stream()
			.filter(villager -> !villager.isBaby())
			.toList();
	}

	public static List<Villager> nearbyForesters(ServerLevel level, SettlementState settlement, int radiusBlocks) {
		return nearbyVillagers(level, settlement.center(), radiusBlocks).stream()
			.filter(villager -> !villager.isBaby() && isCustomForester(villager))
			.toList();
	}

	public static List<Villager> nearbyCarpenters(ServerLevel level, SettlementState settlement) {
		return nearbyVillagers(level, settlement.center(), villagerRadius(settlement)).stream()
			.filter(villager -> !villager.isBaby() && isCustomCarpenter(villager))
			.toList();
	}

	public static List<Villager> nearbyMiners(ServerLevel level, SettlementState settlement) {
		return nearbyVillagers(level, settlement.center(), villagerRadius(settlement)).stream()
			.filter(villager -> !villager.isBaby() && isCustomMiner(villager))
			.toList();
	}

	public static List<Villager> nearbyFletchers(ServerLevel level, SettlementState settlement) {
		return nearbyVillagers(level, settlement.center(), villagerRadius(settlement)).stream()
			.filter(villager -> !villager.isBaby() && villager.getVillagerData().profession().is(VillagerProfession.FLETCHER))
			.toList();
	}

	public static List<Villager> nearbyMasons(ServerLevel level, SettlementState settlement) {
		return nearbyVillagers(level, settlement.center(), villagerRadius(settlement)).stream()
			.filter(villager -> !villager.isBaby() && villager.getVillagerData().profession().is(VillagerProfession.MASON))
			.toList();
	}

	public static List<Villager> nearbyPortmasters(ServerLevel level, SettlementState settlement) {
		return nearbyVillagers(level, settlement.center(), villagerRadius(settlement)).stream()
			.filter(villager -> !villager.isBaby() && isCustomPortmaster(villager))
			.toList();
	}

	public static List<Villager> nearbyConstructionWorkers(ServerLevel level, SettlementState settlement) {
		return nearbyVillagers(level, settlement.center(), villagerRadius(settlement)).stream()
			.filter(SettlementVillagers::canHelpWithConstruction)
			.toList();
	}

	public static List<Villager> nearbyRoadwrights(ServerLevel level, SettlementState settlement) {
		return nearbyVillagers(level, settlement.center(), villagerRadius(settlement)).stream()
			.filter(villager -> !villager.isBaby() && isCustomRoadwright(villager))
			.toList();
	}

	public static List<Villager> nearbyRoadworkHelpers(ServerLevel level, SettlementState settlement) {
		List<Villager> villagers = nearbyVillagers(level, settlement.center(), villagerRadius(settlement));

		if (villagers.stream().noneMatch(SettlementVillagers::isCustomRoadwright)) {
			return List.of();
		}

		return villagers.stream()
			.filter(villager -> !villager.isBaby())
			.filter(villager -> isCustomRoadwright(villager) || villager.getVillagerData().profession().is(VillagerProfession.NONE))
			.toList();
	}

	public static List<VillagerTaskCount> nearbyVillagerTaskPopulation(
		ServerLevel level,
		SettlementState settlement,
		Set<String> constructionDeliveryVillagerIds,
		Map<String, String> roadworkTaskKeysByVillager
	) {
		LinkedHashMap<String, VillagerTaskCount> taskCounts = new LinkedHashMap<>();
		boolean roadworkActive = roadworkTaskKeysByVillager.values().stream().anyMatch("improving_paths"::equals);

		for (Villager villager : nearbyVillagers(level, settlement.center(), villagerRadius(settlement))) {
			String roleKey = villager.isBaby() ? "child" : displayProfessionKey(villager);

			if (roleKey == null || roleKey.isBlank()) {
				continue;
			}

			String taskKey = villagerTaskKey(level, settlement, villager, constructionDeliveryVillagerIds, roadworkTaskKeysByVillager, roadworkActive);
			String countKey = roleKey + "|" + taskKey;
			VillagerTaskCount existing = taskCounts.get(countKey);

			if (existing == null) {
				taskCounts.put(countKey, new VillagerTaskCount(roleKey, taskKey, 1));
			} else {
				taskCounts.put(countKey, new VillagerTaskCount(roleKey, taskKey, existing.count() + 1));
			}
		}

		return List.copyOf(taskCounts.values());
	}

	public static List<VillagerDebugView> nearbyVillagerDebugViews(
		ServerLevel level,
		SettlementState settlement,
		Set<String> constructionDeliveryVillagerIds,
		Map<String, String> roadworkTaskKeysByVillager,
		Map<String, ProfessionDebugInfo> professionDebugByVillager
	) {
		boolean roadworkActive = roadworkTaskKeysByVillager.values().stream().anyMatch("improving_paths"::equals);
		List<VillagerDebugView> debugViews = new ArrayList<>();

		for (Villager villager : nearbyVillagers(level, settlement.center(), villagerRadius(settlement))) {
			String roleKey = villager.isBaby() ? "child" : displayProfessionKey(villager);

			if (roleKey == null || roleKey.isBlank()) {
				continue;
			}

			String villagerId = villager.getUUID().toString();
			String taskKey = villagerTaskKey(level, settlement, villager, constructionDeliveryVillagerIds, roadworkTaskKeysByVillager, roadworkActive);
			ProfessionDebugInfo debugInfo = professionDebugByVillager.getOrDefault(villagerId, ProfessionDebugInfo.EMPTY);
			debugViews.add(new VillagerDebugView(
				humanizeKey(roleKey) + " @ " + villager.blockPosition().getX() + "," + villager.blockPosition().getZ(),
				humanizeKey(roleKey),
				taskKey,
				taskDescription(taskKey),
				debugInfo.targetLabel(),
				debugInfo.detailLabel()
			));
		}

		debugViews.sort(Comparator
			.comparing(VillagerDebugView::roleLabel)
			.thenComparing(VillagerDebugView::workerLabel));
		return List.copyOf(debugViews);
	}

	public static List<BlockPos> farmerJobSites(ServerLevel level, SettlementState settlement) {
		Set<BlockPos> jobSites = new LinkedHashSet<>();

		for (Villager villager : nearbyFarmers(level, settlement)) {
			farmerJobSite(level, villager)
				.map(BlockPos::immutable)
				.ifPresent(jobSites::add);
		}

		return List.copyOf(jobSites);
	}

	public static Optional<BlockPos> farmerJobSite(ServerLevel level, Villager villager) {
		return heldFarmerJobSite(level, villager).map(BlockPos::immutable);
	}

	public static Optional<BlockPos> roadwrightJobSite(ServerLevel level, Villager villager) {
		return heldRoadwrightJobSite(level, villager).map(BlockPos::immutable);
	}

	public static Optional<BlockPos> carpenterJobSite(ServerLevel level, Villager villager) {
		return heldCarpenterJobSite(level, villager).map(BlockPos::immutable);
	}

	public static Optional<BlockPos> foresterJobSite(ServerLevel level, Villager villager) {
		return heldForesterJobSite(level, villager).map(BlockPos::immutable);
	}

	public static Optional<BlockPos> minerJobSite(ServerLevel level, Villager villager) {
		return heldMinerJobSite(level, villager).map(BlockPos::immutable);
	}

	public static Optional<BlockPos> portmasterJobSite(ServerLevel level, Villager villager) {
		return heldPortmasterJobSite(level, villager).map(BlockPos::immutable);
	}

	public static Optional<BlockPos> fletcherJobSite(ServerLevel level, Villager villager) {
		return heldFletcherJobSite(level, villager).map(BlockPos::immutable);
	}

	public static Optional<BlockPos> masonJobSite(ServerLevel level, Villager villager) {
		return heldMasonJobSite(level, villager).map(BlockPos::immutable);
	}

	public static Optional<BlockPos> fishermanJobSite(ServerLevel level, Villager villager) {
		return heldFishermanJobSite(level, villager).map(BlockPos::immutable);
	}

	public static boolean ensureTrademaster(ServerLevel level, SettlementState settlement) {
		if (!usesActualVillagers(settlement)) {
			return false;
		}

		List<Villager> villagers = nearbyVillagers(level, settlement.center(), villagerRadius(settlement));
		boolean changed = false;

		if (villagers.stream().noneMatch(SettlementVillagers::isCustomTrademaster)) {
			changed = villagers.stream()
				.filter(villager -> !villager.isBaby())
				.filter(villager -> heldTrademasterJobSite(level, villager).isPresent() || canBecomeTrademaster(villager))
				.sorted(Comparator.comparingInt(SettlementVillagers::trademasterCandidateRank)
					.thenComparingDouble(villager -> distanceToCenterSqr(villager, settlement.center())))
				.anyMatch(villager -> promoteToTrademaster(level, villager));
		}

		for (Villager villager : villagers) {
			if (isCustomTrademaster(villager)) {
				lockCustomProfession(villager);
				changed |= ensureTrademasterJobSite(level, villager);
			}
		}

		return changed;
	}

	public static boolean ensureCarpenter(ServerLevel level, SettlementState settlement) {
		if (!usesActualVillagers(settlement)) {
			return false;
		}

		List<Villager> villagers = nearbyVillagers(level, settlement.center(), villagerRadius(settlement));
		boolean changed = false;

		if (villagers.stream().noneMatch(SettlementVillagers::isCustomCarpenter)) {
			changed = villagers.stream()
				.filter(villager -> !villager.isBaby())
				.filter(villager -> heldCarpenterJobSite(level, villager).isPresent() || canBecomeCarpenter(villager))
				.sorted(Comparator.comparingInt(SettlementVillagers::carpenterCandidateRank)
					.thenComparingDouble(villager -> distanceToCenterSqr(villager, settlement.center())))
				.anyMatch(villager -> promoteToCarpenter(level, villager));
		}

		for (Villager villager : villagers) {
			if (!isCustomCarpenter(villager)) {
				continue;
			}

			changed |= ensureCarpenterJobSite(level, villager);
			lockCustomProfession(villager);
			changed |= ensureCarpenterWorkshopHome(level, villager, villagers);
		}

		return changed;
	}

	public static boolean ensureRoadwright(ServerLevel level, SettlementState settlement) {
		if (!usesActualVillagers(settlement)) {
			return false;
		}

		List<Villager> villagers = nearbyVillagers(level, settlement.center(), villagerRadius(settlement));
		boolean changed = false;

		if (villagers.stream().noneMatch(SettlementVillagers::isCustomRoadwright)) {
			changed = villagers.stream()
				.filter(villager -> !villager.isBaby())
				.filter(villager -> heldRoadwrightJobSite(level, villager).isPresent() || canBecomeRoadwright(villager))
				.sorted(Comparator.comparingInt(SettlementVillagers::roadwrightCandidateRank)
					.thenComparingDouble(villager -> distanceToCenterSqr(villager, settlement.center())))
				.anyMatch(villager -> promoteToRoadwright(level, villager));
		}

		for (Villager villager : villagers) {
			if (isCustomRoadwright(villager)) {
				lockCustomProfession(villager);
				changed |= ensureRoadwrightJobSite(level, villager);
			}
		}

		return changed;
	}

	public static boolean ensureForester(ServerLevel level, SettlementState settlement) {
		if (!usesActualVillagers(settlement)) {
			return false;
		}

		List<Villager> villagers = nearbyVillagers(level, settlement.center(), villagerRadius(settlement));
		boolean changed = false;

		if (villagers.stream().noneMatch(SettlementVillagers::isCustomForester)) {
			changed = villagers.stream()
				.filter(villager -> !villager.isBaby())
				.filter(villager -> heldForesterJobSite(level, villager).isPresent() || canBecomeForester(villager))
				.sorted(Comparator.comparingInt(SettlementVillagers::foresterCandidateRank)
					.thenComparingDouble(villager -> distanceToCenterSqr(villager, settlement.center())))
				.anyMatch(villager -> promoteToForester(level, villager));
		}

		for (Villager villager : villagers) {
			if (isCustomForester(villager)) {
				lockCustomProfession(villager);
				changed |= ensureForesterJobSite(level, villager);
			}
		}

		return changed;
	}

	public static boolean ensureMiner(ServerLevel level, SettlementState settlement) {
		if (!usesActualVillagers(settlement)) {
			return false;
		}

		List<Villager> villagers = nearbyVillagers(level, settlement.center(), villagerRadius(settlement));
		boolean changed = false;
		int desiredMiners = desiredMinerCount(level, settlement);
		long currentMiners = villagers.stream()
			.filter(villager -> !villager.isBaby() && isCustomMiner(villager))
			.count();

		if (currentMiners < desiredMiners) {
			for (Villager villager : villagers.stream()
				.filter(villager -> !villager.isBaby())
				.filter(villager -> heldMinerJobSite(level, villager).isPresent() || canBecomeMiner(villager))
				.sorted(Comparator.comparingInt(SettlementVillagers::minerCandidateRank)
					.thenComparingDouble(villager -> distanceToCenterSqr(villager, settlement.center())))
				.toList()) {
				if (promoteToMiner(level, villager)) {
					changed = true;
					currentMiners++;
				}

				if (currentMiners >= desiredMiners) {
					break;
				}
			}
		}

		for (Villager villager : villagers) {
			if (isCustomMiner(villager)) {
				lockCustomProfession(villager);
				changed |= ensureMinerJobSite(level, villager);
			}
		}

		return changed;
	}

	public static boolean ensurePortmaster(ServerLevel level, SettlementState settlement) {
		if (!usesActualVillagers(settlement)) {
			return false;
		}

		List<Villager> villagers = nearbyVillagers(level, settlement.center(), villagerRadius(settlement));
		boolean changed = false;
		int desiredPortmasters = desiredPortmasterCount(level, settlement);
		long currentPortmasters = villagers.stream()
			.filter(villager -> !villager.isBaby() && isCustomPortmaster(villager))
			.count();

		if (currentPortmasters < desiredPortmasters) {
			for (Villager villager : villagers.stream()
				.filter(villager -> !villager.isBaby())
				.filter(villager -> heldPortmasterJobSite(level, villager).isPresent() || canBecomePortmaster(villager))
				.sorted(Comparator.comparingInt(SettlementVillagers::portmasterCandidateRank)
					.thenComparingDouble(villager -> distanceToCenterSqr(villager, settlement.center())))
				.toList()) {
				if (promoteToPortmaster(level, villager)) {
					changed = true;
					currentPortmasters++;
				}

				if (currentPortmasters >= desiredPortmasters) {
					break;
				}
			}
		}

		for (Villager villager : villagers) {
			if (isCustomPortmaster(villager)) {
				lockCustomProfession(villager);
				changed |= ensurePortmasterJobSite(level, villager);
			}
		}

		return changed;
	}

	public static boolean ensureFletcher(ServerLevel level, SettlementState settlement) {
		if (!usesActualVillagers(settlement)) {
			return false;
		}

		List<Villager> villagers = nearbyVillagers(level, settlement.center(), villagerRadius(settlement));
		boolean changed = false;
		int desiredFletchers = desiredFletcherCount(level, settlement);
		long currentFletchers = villagers.stream()
			.filter(villager -> !villager.isBaby() && villager.getVillagerData().profession().is(VillagerProfession.FLETCHER))
			.count();

		if (currentFletchers < desiredFletchers) {
			for (Villager villager : villagers.stream()
				.filter(villager -> !villager.isBaby())
				.filter(villager -> !villager.getVillagerData().profession().is(VillagerProfession.FLETCHER))
				.filter(SettlementVillagers::canBecomeFletcher)
				.sorted(Comparator.comparingInt(SettlementVillagers::fletcherCandidateRank)
					.thenComparingDouble(villager -> distanceToCenterSqr(villager, settlement.center())))
				.toList()) {
				if (promoteToFletcher(level, villager)) {
					changed = true;
					currentFletchers++;
				}

				if (currentFletchers >= desiredFletchers) {
					break;
				}
			}
		}

		for (Villager villager : villagers) {
			if (villager.getVillagerData().profession().is(VillagerProfession.FLETCHER)) {
				changed |= ensureFletcherJobSite(level, villager);
			}
		}

		return changed;
	}

	public static boolean ensureVillagerHomes(ServerLevel level, SettlementState settlement) {
		if (!usesActualVillagers(settlement)) {
			return false;
		}

		List<SettlementBuildSite> buildSites = LiveVillagesSavedData.get(level.getServer()).getBuildSitesForSettlement(settlement.id());
		List<Villager> villagers = nearbyVillagers(level, settlement.center(), villagerRadius(settlement)).stream()
			.sorted(Comparator.comparingInt((Villager villager) -> homeAssignmentPriority(level, villager, buildSites))
				.thenComparing(villager -> villager.getUUID().toString()))
			.toList();
		Set<BlockPos> claimedHomes = new LinkedHashSet<>();
		boolean changed = false;

		for (Villager villager : villagers) {
			Optional<BlockPos> originalHome = heldHome(level, villager);
			Optional<BlockPos> currentHome = originalHome
				.filter(home -> isValidHome(level, settlement, home))
				.filter(home -> !claimedHomes.contains(home));
			List<BlockPos> preferredHomes = preferredWorkstationHomes(level, villager, buildSites);

			if (currentHome.isPresent() && (preferredHomes.isEmpty() || preferredHomes.contains(currentHome.get()))) {
				claimedHomes.add(currentHome.get());
				continue;
			}

			Optional<BlockPos> preferredHome = preferredHomes.stream()
				.filter(home -> !claimedHomes.contains(home))
				.findFirst();

			if (preferredHome.isPresent()) {
				changed |= assignHome(level, villager, preferredHome.get(), originalHome);
				claimedHomes.add(preferredHome.get());
				continue;
			}

			if (currentHome.isPresent()) {
				claimedHomes.add(currentHome.get());
				continue;
			}

			if (originalHome.isPresent()) {
				villager.releasePoi(MemoryModuleType.HOME);
				villager.getBrain().eraseMemory(MemoryModuleType.HOME);
				changed = true;
			}

			Optional<BlockPos> home = findReachableHome(level, villager, claimedHomes);

			if (home.isEmpty()) {
				continue;
			}

			assignHome(level, villager, home.get(), Optional.empty());
			claimedHomes.add(home.get());
			changed = true;
		}

		return changed;
	}

	public static boolean ensureVillagerGatheringPoint(ServerLevel level, SettlementState settlement) {
		if (!usesActualVillagers(settlement)) {
			return false;
		}

		BlockPos gatheringPos = resolveGatheringPos(level, settlement);
		boolean changed = false;

		for (Villager villager : nearbyVillagers(level, settlement.center(), villagerRadius(settlement))) {
			Optional<BlockPos> currentMeetingPoint = villager.getBrain().getMemory(MemoryModuleType.MEETING_POINT)
				.filter(globalPos -> globalPos.dimension().equals(level.dimension()))
				.map(GlobalPos::pos);

			if (currentMeetingPoint.filter(gatheringPos::equals).isPresent()) {
				continue;
			}

			villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), gatheringPos));
			changed = true;
		}

		return changed;
	}

	public static Map<String, Integer> createOperationalPopulation(int villagers) {
		if (villagers <= 0) {
			return Map.of();
		}

		LinkedHashMap<String, Integer> population = new LinkedHashMap<>();
		int remaining = villagers;

		population.put(SettlementRoleKeys.TRADEMASTER, 1);
		remaining--;

		if (remaining > 0) {
			population.put(SettlementRoleKeys.FARMER, 1);
			remaining--;
		}

		if (remaining > 1) {
			population.put(SettlementRoleKeys.CARPENTER, 1);
			remaining--;
		}

		if (remaining > 2) {
			population.put(SettlementRoleKeys.GUARD, 1);
			remaining--;
		}

		if (remaining > 0) {
			population.put(SettlementRoleKeys.UNEMPLOYED, remaining);
		}

		return population;
	}

	private static Map<String, Integer> createOperationalPopulation(List<Villager> villagers) {
		if (villagers.isEmpty()) {
			return Map.of();
		}

		LinkedHashMap<String, Integer> population = new LinkedHashMap<>();
		int cartographers = 0;
		int farmers = 0;
		int fletchers = 0;
		int foresters = 0;
		int miners = 0;
		int portmasters = 0;
		int carpenters = 0;
		int masons = 0;
		int roadwrights = 0;
		int constructionSupport = 0;
		int trademasters = 0;
		int unemployed = 0;

		for (Villager villager : villagers) {
			if (villager.isBaby()) {
				unemployed++;
				continue;
			}

			var profession = villager.getVillagerData().profession();

			if (profession.is(VillagerProfession.CARTOGRAPHER)) {
				cartographers++;
			} else if (isFoodWorker(profession)) {
				farmers++;
			} else if (profession.is(VillagerProfession.FLETCHER)) {
				fletchers++;
			} else if (profession.is(LiveVillagesVillagerProfessions.FORESTER)) {
				foresters++;
			} else if (profession.is(LiveVillagesVillagerProfessions.MINER)) {
				miners++;
			} else if (profession.is(LiveVillagesVillagerProfessions.PORTMASTER)) {
				portmasters++;
			} else if (profession.is(LiveVillagesVillagerProfessions.CARPENTER)) {
				carpenters++;
			} else if (profession.is(LiveVillagesVillagerProfessions.ROADWRIGHT)) {
				roadwrights++;
			} else if (profession.is(VillagerProfession.MASON)) {
				masons++;
			} else if (isConstructionSupportWorker(profession)) {
				constructionSupport++;
			} else if (isTradeWorker(profession)) {
				trademasters++;
			} else {
				unemployed++;
			}
		}

		int remaining = villagers.size() - cartographers - farmers - fletchers - foresters - miners - portmasters - carpenters - masons - roadwrights - constructionSupport - trademasters - unemployed;
		unemployed += Math.max(0, remaining);

		if (trademasters <= 0 && !villagers.isEmpty()) {
			if (unemployed > 0) {
				trademasters = 1;
				unemployed--;
			} else if (constructionSupport > 0) {
				trademasters = 1;
				constructionSupport--;
			} else if (carpenters > 0) {
				trademasters = 1;
				carpenters--;
			} else if (masons > 0) {
				trademasters = 1;
				masons--;
			} else if (farmers > 1) {
				trademasters = 1;
				farmers--;
			}
		}

		if (cartographers > 0) {
			population.put(SettlementRoleKeys.CARTOGRAPHER, cartographers);
		}

		if (trademasters > 0) {
			population.put(SettlementRoleKeys.TRADEMASTER, trademasters);
		}

		if (farmers > 0) {
			population.put(SettlementRoleKeys.FARMER, farmers);
		}

		if (fletchers > 0) {
			population.put(SettlementRoleKeys.FLETCHER, fletchers);
		}

		if (foresters > 0) {
			population.put(SettlementRoleKeys.FORESTER, foresters);
		}

		if (miners > 0) {
			population.put(SettlementRoleKeys.MINER, miners);
		}

		if (portmasters > 0) {
			population.put(SettlementRoleKeys.PORTMASTER, portmasters);
		}

		if (carpenters > 0) {
			population.put(SettlementRoleKeys.CARPENTER, carpenters);
		}

		if (masons > 0) {
			population.put(SettlementRoleKeys.MASON, masons);
		}

		if (roadwrights > 0) {
			population.put(SettlementRoleKeys.ROADWRIGHT, roadwrights);
		}

		if (constructionSupport > 0) {
			population.put(SettlementRoleKeys.CONSTRUCTION_SUPPORT, constructionSupport);
		}

		if (villagers.size() >= 4) {
			int guards = Math.max(0, villagers.size() / 8);

			if (guards > 0) {
				int assignedGuards = Math.min(unemployed, guards);

				if (assignedGuards > 0) {
					population.put(SettlementRoleKeys.GUARD, assignedGuards);
					unemployed -= assignedGuards;
				}
			}
		}

		if (unemployed > 0) {
			population.put(SettlementRoleKeys.UNEMPLOYED, unemployed);
		}

		return population;
	}

	public static Optional<BlockPos> findSpawnPos(ServerLevel level, BlockPos center, int searchRadius) {
		for (int radius = 1; radius <= searchRadius; radius++) {
			for (int offsetX = -radius; offsetX <= radius; offsetX++) {
				for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
					if (Math.max(Math.abs(offsetX), Math.abs(offsetZ)) != radius) {
						continue;
					}

					Optional<BlockPos> spawnPos = findSpawnPosAtColumn(level, center.getX() + offsetX, center.getZ() + offsetZ);

					if (spawnPos.isPresent()) {
						return spawnPos;
					}
				}
			}
		}

		return Optional.empty();
	}

	public static boolean spawnVillager(ServerLevel level, BlockPos spawnPos) {
		Villager villager = EntityType.VILLAGER.create(level, EntitySpawnReason.EVENT);

		if (villager == null) {
			return false;
		}

		villager.setPos(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D);
		villager.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), EntitySpawnReason.EVENT, null);
		villager.setPersistenceRequired();
		return level.addFreshEntity(villager);
	}

	private static List<Villager> nearbyVillagers(ServerLevel level, BlockPos center, int radiusBlocks) {
		return SettlementLoadedObservation.nearbyVillagers(level, center, radiusBlocks);
	}

	private static boolean promoteToTrademaster(ServerLevel level, Villager villager) {
		Optional<BlockPos> heldJobSite = heldTrademasterJobSite(level, villager);

		if (heldJobSite.isPresent()) {
			villager.releasePoi(MemoryModuleType.JOB_SITE);
			assignTrademasterJobSite(level, villager, heldJobSite.get());
			return true;
		}

		Optional<BlockPos> reachableJobSite = findReachableTrademasterJobSite(level, villager);

		if (reachableJobSite.isEmpty()) {
			return false;
		}

		villager.releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);

		Optional<BlockPos> jobSite = level.getPoiManager().take(
			poiType -> poiType.is(LiveVillagesVillagerProfessions.TRADEMASTER_POI),
			(poiType, pos) -> pos.equals(reachableJobSite.get()),
			reachableJobSite.get(),
			1
		);

		if (jobSite.isEmpty()) {
			return false;
		}

		villager.releasePoi(MemoryModuleType.JOB_SITE);
		assignTrademasterJobSite(level, villager, jobSite.get());
		return true;
	}

	private static boolean promoteToCarpenter(ServerLevel level, Villager villager) {
		Optional<BlockPos> heldJobSite = heldCarpenterJobSite(level, villager);

		if (heldJobSite.isPresent()) {
			villager.releasePoi(MemoryModuleType.JOB_SITE);
			assignCarpenterJobSite(level, villager, heldJobSite.get());
			return true;
		}

		return ensureCarpenterJobSite(level, villager);
	}

	private static boolean promoteToRoadwright(ServerLevel level, Villager villager) {
		Optional<BlockPos> heldJobSite = heldRoadwrightJobSite(level, villager);

		if (heldJobSite.isPresent()) {
			villager.releasePoi(MemoryModuleType.JOB_SITE);
			assignRoadwrightJobSite(level, villager, heldJobSite.get());
			return true;
		}

		Optional<BlockPos> reachableJobSite = findReachableRoadwrightJobSite(level, villager);

		if (reachableJobSite.isEmpty()) {
			return false;
		}

		villager.releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);

		Optional<BlockPos> jobSite = level.getPoiManager().take(
			poiType -> poiType.is(LiveVillagesVillagerProfessions.ROADWRIGHT_POI),
			(poiType, pos) -> pos.equals(reachableJobSite.get()),
			reachableJobSite.get(),
			1
		);

		if (jobSite.isEmpty()) {
			return false;
		}

		villager.releasePoi(MemoryModuleType.JOB_SITE);
		assignRoadwrightJobSite(level, villager, jobSite.get());
		return true;
	}

	private static boolean promoteToForester(ServerLevel level, Villager villager) {
		Optional<BlockPos> heldJobSite = heldForesterJobSite(level, villager);

		if (heldJobSite.isPresent()) {
			villager.releasePoi(MemoryModuleType.JOB_SITE);
			assignForesterJobSite(level, villager, heldJobSite.get());
			return true;
		}

		Optional<BlockPos> reachableJobSite = findReachableForesterJobSite(level, villager);

		if (reachableJobSite.isEmpty()) {
			return false;
		}

		villager.releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);

		Optional<BlockPos> jobSite = level.getPoiManager().take(
			poiType -> poiType.is(LiveVillagesVillagerProfessions.FORESTER_POI),
			(poiType, pos) -> pos.equals(reachableJobSite.get()),
			reachableJobSite.get(),
			1
		);

		if (jobSite.isEmpty()) {
			return false;
		}

		villager.releasePoi(MemoryModuleType.JOB_SITE);
		assignForesterJobSite(level, villager, jobSite.get());
		return true;
	}

	private static boolean promoteToMiner(ServerLevel level, Villager villager) {
		Optional<BlockPos> heldJobSite = heldMinerJobSite(level, villager);

		if (heldJobSite.isPresent()) {
			villager.releasePoi(MemoryModuleType.JOB_SITE);
			assignMinerJobSite(level, villager, heldJobSite.get());
			return true;
		}

		Optional<BlockPos> reachableJobSite = findReachableMinerJobSite(level, villager);

		if (reachableJobSite.isEmpty()) {
			return false;
		}

		villager.releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);

		Optional<BlockPos> jobSite = level.getPoiManager().take(
			poiType -> poiType.is(LiveVillagesVillagerProfessions.MINER_POI),
			(poiType, pos) -> pos.equals(reachableJobSite.get()),
			reachableJobSite.get(),
			1
		);

		if (jobSite.isEmpty()) {
			return false;
		}

		villager.releasePoi(MemoryModuleType.JOB_SITE);
		assignMinerJobSite(level, villager, jobSite.get());
		return true;
	}

	private static boolean promoteToPortmaster(ServerLevel level, Villager villager) {
		Optional<BlockPos> heldJobSite = heldPortmasterJobSite(level, villager);

		if (heldJobSite.isPresent()) {
			villager.releasePoi(MemoryModuleType.JOB_SITE);
			assignPortmasterJobSite(level, villager, heldJobSite.get());
			return true;
		}

		Optional<BlockPos> reachableJobSite = findReachablePortmasterJobSite(level, villager);

		if (reachableJobSite.isEmpty()) {
			return false;
		}

		villager.releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);

		Optional<BlockPos> jobSite = level.getPoiManager().take(
			poiType -> poiType.is(LiveVillagesVillagerProfessions.PORTMASTER_POI),
			(poiType, pos) -> pos.equals(reachableJobSite.get()),
			reachableJobSite.get(),
			1
		);

		if (jobSite.isEmpty()) {
			return false;
		}

		villager.releasePoi(MemoryModuleType.JOB_SITE);
		assignPortmasterJobSite(level, villager, jobSite.get());
		return true;
	}

	private static boolean promoteToFletcher(ServerLevel level, Villager villager) {
		Optional<BlockPos> heldJobSite = heldFletcherJobSite(level, villager);

		if (heldJobSite.isPresent()) {
			assignFletcherJobSite(level, villager, heldJobSite.get());
			return true;
		}

		Optional<BlockPos> reachableJobSite = findReachableFletcherJobSite(level, villager, net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY);

		if (reachableJobSite.isEmpty()) {
			return false;
		}

		assignFletcherJobSite(level, villager, reachableJobSite.get());
		return true;
	}

	private static boolean ensureCarpenterJobSite(ServerLevel level, Villager villager) {
		if (heldCarpenterJobSite(level, villager).isPresent()) {
			return false;
		}

		Optional<BlockPos> reachableJobSite = findReachableCarpenterJobSite(level, villager);

		if (reachableJobSite.isEmpty()) {
			return false;
		}

		villager.releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);

		Optional<BlockPos> jobSite = level.getPoiManager().take(
			poiType -> poiType.is(LiveVillagesVillagerProfessions.CARPENTER_POI),
			(poiType, pos) -> pos.equals(reachableJobSite.get()),
			reachableJobSite.get(),
			1
		);

		if (jobSite.isEmpty()) {
			return false;
		}

		villager.releasePoi(MemoryModuleType.JOB_SITE);
		assignCarpenterJobSite(level, villager, jobSite.get());
		return true;
	}

	private static boolean ensureTrademasterJobSite(ServerLevel level, Villager villager) {
		if (heldTrademasterJobSite(level, villager).isPresent()) {
			return false;
		}

		Optional<BlockPos> reachableJobSite = findReachableTrademasterJobSite(level, villager);

		if (reachableJobSite.isEmpty()) {
			return false;
		}

		villager.releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);

		Optional<BlockPos> jobSite = level.getPoiManager().take(
			poiType -> poiType.is(LiveVillagesVillagerProfessions.TRADEMASTER_POI),
			(poiType, pos) -> pos.equals(reachableJobSite.get()),
			reachableJobSite.get(),
			1
		);

		if (jobSite.isEmpty()) {
			return false;
		}

		villager.releasePoi(MemoryModuleType.JOB_SITE);
		assignTrademasterJobSite(level, villager, jobSite.get());
		return true;
	}

	private static boolean ensureRoadwrightJobSite(ServerLevel level, Villager villager) {
		if (heldRoadwrightJobSite(level, villager).isPresent()) {
			return false;
		}

		Optional<BlockPos> reachableJobSite = findReachableRoadwrightJobSite(level, villager);

		if (reachableJobSite.isEmpty()) {
			return false;
		}

		villager.releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);

		Optional<BlockPos> jobSite = level.getPoiManager().take(
			poiType -> poiType.is(LiveVillagesVillagerProfessions.ROADWRIGHT_POI),
			(poiType, pos) -> pos.equals(reachableJobSite.get()),
			reachableJobSite.get(),
			1
		);

		if (jobSite.isEmpty()) {
			return false;
		}

		villager.releasePoi(MemoryModuleType.JOB_SITE);
		assignRoadwrightJobSite(level, villager, jobSite.get());
		return true;
	}

	private static boolean ensureForesterJobSite(ServerLevel level, Villager villager) {
		if (heldForesterJobSite(level, villager).isPresent()) {
			return false;
		}

		Optional<BlockPos> reachableJobSite = findReachableForesterJobSite(level, villager);

		if (reachableJobSite.isEmpty()) {
			return false;
		}

		villager.releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);

		Optional<BlockPos> jobSite = level.getPoiManager().take(
			poiType -> poiType.is(LiveVillagesVillagerProfessions.FORESTER_POI),
			(poiType, pos) -> pos.equals(reachableJobSite.get()),
			reachableJobSite.get(),
			1
		);

		if (jobSite.isEmpty()) {
			return false;
		}

		villager.releasePoi(MemoryModuleType.JOB_SITE);
		assignForesterJobSite(level, villager, jobSite.get());
		return true;
	}

	private static boolean ensureMinerJobSite(ServerLevel level, Villager villager) {
		if (heldMinerJobSite(level, villager).isPresent()) {
			return false;
		}

		Optional<BlockPos> reachableJobSite = findReachableMinerJobSite(level, villager);

		if (reachableJobSite.isEmpty()) {
			return false;
		}

		villager.releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);

		Optional<BlockPos> jobSite = level.getPoiManager().take(
			poiType -> poiType.is(LiveVillagesVillagerProfessions.MINER_POI),
			(poiType, pos) -> pos.equals(reachableJobSite.get()),
			reachableJobSite.get(),
			1
		);

		if (jobSite.isEmpty()) {
			return false;
		}

		villager.releasePoi(MemoryModuleType.JOB_SITE);
		assignMinerJobSite(level, villager, jobSite.get());
		return true;
	}

	private static boolean ensurePortmasterJobSite(ServerLevel level, Villager villager) {
		if (heldPortmasterJobSite(level, villager).isPresent()) {
			return false;
		}

		Optional<BlockPos> reachableJobSite = findReachablePortmasterJobSite(level, villager);

		if (reachableJobSite.isEmpty()) {
			return false;
		}

		villager.releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);

		Optional<BlockPos> jobSite = level.getPoiManager().take(
			poiType -> poiType.is(LiveVillagesVillagerProfessions.PORTMASTER_POI),
			(poiType, pos) -> pos.equals(reachableJobSite.get()),
			reachableJobSite.get(),
			1
		);

		if (jobSite.isEmpty()) {
			return false;
		}

		villager.releasePoi(MemoryModuleType.JOB_SITE);
		assignPortmasterJobSite(level, villager, jobSite.get());
		return true;
	}

	private static boolean ensureFletcherJobSite(ServerLevel level, Villager villager) {
		if (heldFletcherJobSite(level, villager).isPresent()) {
			return false;
		}

		Optional<BlockPos> reachableJobSite = findReachableFletcherJobSite(level, villager, net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY);

		if (reachableJobSite.isEmpty()) {
			return false;
		}

		villager.releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);

		Optional<BlockPos> jobSite = level.getPoiManager().take(
			poiType -> poiType.is(PoiTypes.FLETCHER),
			(poiType, pos) -> pos.equals(reachableJobSite.get()),
			reachableJobSite.get(),
			1
		);

		if (jobSite.isEmpty()) {
			return false;
		}

		villager.releasePoi(MemoryModuleType.JOB_SITE);
		assignFletcherJobSite(level, villager, jobSite.get());
		return true;
	}

	private static Optional<BlockPos> heldTrademasterJobSite(ServerLevel level, Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
			.or(() -> villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE))
			.filter(globalPos -> globalPos.dimension().equals(level.dimension()))
			.map(GlobalPos::pos)
			.filter(pos -> level.getPoiManager().exists(pos, poiType -> poiType.is(LiveVillagesVillagerProfessions.TRADEMASTER_POI)));
	}

	private static Optional<BlockPos> heldCarpenterJobSite(ServerLevel level, Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
			.or(() -> villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE))
			.filter(globalPos -> globalPos.dimension().equals(level.dimension()))
			.map(GlobalPos::pos)
			.filter(pos -> level.getPoiManager().exists(pos, poiType -> poiType.is(LiveVillagesVillagerProfessions.CARPENTER_POI)));
	}

	private static Optional<BlockPos> heldRoadwrightJobSite(ServerLevel level, Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
			.or(() -> villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE))
			.filter(globalPos -> globalPos.dimension().equals(level.dimension()))
			.map(GlobalPos::pos)
			.filter(pos -> level.getPoiManager().exists(pos, poiType -> poiType.is(LiveVillagesVillagerProfessions.ROADWRIGHT_POI)));
	}

	private static Optional<BlockPos> heldForesterJobSite(ServerLevel level, Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
			.or(() -> villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE))
			.filter(globalPos -> globalPos.dimension().equals(level.dimension()))
			.map(GlobalPos::pos)
			.filter(pos -> level.getPoiManager().exists(pos, poiType -> poiType.is(LiveVillagesVillagerProfessions.FORESTER_POI)));
	}

	private static Optional<BlockPos> heldMinerJobSite(ServerLevel level, Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
			.or(() -> villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE))
			.filter(globalPos -> globalPos.dimension().equals(level.dimension()))
			.map(GlobalPos::pos)
			.filter(pos -> level.getPoiManager().exists(pos, poiType -> poiType.is(LiveVillagesVillagerProfessions.MINER_POI)));
	}

	private static Optional<BlockPos> heldPortmasterJobSite(ServerLevel level, Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
			.or(() -> villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE))
			.filter(globalPos -> globalPos.dimension().equals(level.dimension()))
			.map(GlobalPos::pos)
			.filter(pos -> level.getPoiManager().exists(pos, poiType -> poiType.is(LiveVillagesVillagerProfessions.PORTMASTER_POI)));
	}

	private static Optional<BlockPos> heldButcherJobSite(ServerLevel level, Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
			.or(() -> villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE))
			.filter(globalPos -> globalPos.dimension().equals(level.dimension()))
			.map(GlobalPos::pos)
			.filter(pos -> level.getPoiManager().exists(pos, poiType -> poiType.is(PoiTypes.BUTCHER)));
	}

	private static Optional<BlockPos> heldCartographerJobSite(ServerLevel level, Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
			.or(() -> villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE))
			.filter(globalPos -> globalPos.dimension().equals(level.dimension()))
			.map(GlobalPos::pos)
			.filter(pos -> level.getPoiManager().exists(pos, poiType -> poiType.is(PoiTypes.CARTOGRAPHER)));
	}

	private static Optional<BlockPos> heldFarmerJobSite(ServerLevel level, Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
			.or(() -> villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE))
			.filter(globalPos -> globalPos.dimension().equals(level.dimension()))
			.map(GlobalPos::pos)
			.filter(pos -> level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.COMPOSTER));
	}

	private static Optional<BlockPos> heldFishermanJobSite(ServerLevel level, Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
			.or(() -> villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE))
			.filter(globalPos -> globalPos.dimension().equals(level.dimension()))
			.map(GlobalPos::pos)
			.filter(pos -> level.getPoiManager().exists(pos, poiType -> poiType.is(PoiTypes.FISHERMAN)));
	}

	private static Optional<BlockPos> heldFletcherJobSite(ServerLevel level, Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
			.or(() -> villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE))
			.filter(globalPos -> globalPos.dimension().equals(level.dimension()))
			.map(GlobalPos::pos)
			.filter(pos -> level.getPoiManager().exists(pos, poiType -> poiType.is(PoiTypes.FLETCHER)));
	}

	private static Optional<BlockPos> heldMasonJobSite(ServerLevel level, Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
			.or(() -> villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE))
			.filter(globalPos -> globalPos.dimension().equals(level.dimension()))
			.map(GlobalPos::pos)
			.filter(pos -> level.getPoiManager().exists(pos, poiType -> poiType.is(PoiTypes.MASON)));
	}

	private static Optional<BlockPos> findReachableTrademasterJobSite(ServerLevel level, Villager villager) {
		return level.getPoiManager().findAllClosestFirstWithType(
			poiType -> poiType.is(LiveVillagesVillagerProfessions.TRADEMASTER_POI),
			pos -> true,
			villager.blockPosition(),
			JOB_SITE_SEARCH_RADIUS_BLOCKS,
			net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.HAS_SPACE
		)
			.filter(pair -> canReach(villager, pair.getSecond(), pair.getFirst()))
			.map(com.mojang.datafixers.util.Pair::getSecond)
			.findFirst();
	}

	private static Optional<BlockPos> findReachableHome(ServerLevel level, Villager villager, Set<BlockPos> claimedHomes) {
		return level.getPoiManager().findAllClosestFirstWithType(
			poiType -> poiType.is(PoiTypes.HOME),
			pos -> !claimedHomes.contains(pos),
			villager.blockPosition(),
			HOME_SEARCH_RADIUS_BLOCKS,
			net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.HAS_SPACE
		)
			.filter(pair -> canReach(villager, pair.getSecond(), pair.getFirst()))
			.map(com.mojang.datafixers.util.Pair::getSecond)
			.findFirst();
	}

	private static int homeAssignmentPriority(ServerLevel level, Villager villager, List<SettlementBuildSite> buildSites) {
		return preferredWorkstationHomes(level, villager, buildSites).isEmpty() ? 1 : 0;
	}

	private static List<BlockPos> preferredWorkstationHomes(ServerLevel level, Villager villager, List<SettlementBuildSite> buildSites) {
		Optional<BlockPos> preferredJobSite = preferredWorkstationHomeJobSite(level, villager, buildSites);

		if (preferredJobSite.isEmpty()) {
			return List.of();
		}

		return level.getPoiManager().findAllClosestFirstWithType(
			poiType -> poiType.is(PoiTypes.HOME),
			pos -> true,
			preferredJobSite.get(),
			WORKSTATION_HOME_SEARCH_RADIUS_BLOCKS,
			net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY
		)
			.filter(pair -> canReach(villager, pair.getSecond(), pair.getFirst()))
			.map(com.mojang.datafixers.util.Pair::getSecond)
			.map(BlockPos::immutable)
			.toList();
	}

	private static Optional<BlockPos> preferredWorkstationHomeJobSite(ServerLevel level, Villager villager, List<SettlementBuildSite> buildSites) {
		if (isCustomCarpenter(villager)) {
			return preferredWorkstationHomeJobSite(level, villager, buildSites, SettlementBuildSiteType.CARPENTER_WORKSHOP, () -> heldCarpenterJobSite(level, villager));
		}

		if (isCustomRoadwright(villager)) {
			return preferredWorkstationHomeJobSite(level, villager, buildSites, SettlementBuildSiteType.ROADWRIGHT_WORKSHOP, () -> heldRoadwrightJobSite(level, villager));
		}

		if (isCustomForester(villager)) {
			return preferredWorkstationHomeJobSite(level, villager, buildSites, SettlementBuildSiteType.FORESTER_WORKSHOP, () -> heldForesterJobSite(level, villager));
		}

		if (villager.getVillagerData().profession().is(VillagerProfession.FLETCHER)) {
			return preferredWorkstationHomeJobSite(level, villager, buildSites, SettlementBuildSiteType.FLETCHER_HUT, () -> heldFletcherJobSite(level, villager));
		}

		if (villager.getVillagerData().profession().is(VillagerProfession.CARTOGRAPHER)) {
			return preferredWorkstationHomeJobSite(level, villager, buildSites, SettlementBuildSiteType.CARTOGRAPHER_HOUSE, () -> heldCartographerJobSite(level, villager));
		}

		if (villager.getVillagerData().profession().is(VillagerProfession.BUTCHER)) {
			return preferredWorkstationHomeJobSite(level, villager, buildSites, SettlementBuildSiteType.BUTCHER_SHOP, () -> heldButcherJobSite(level, villager));
		}

		if (villager.getVillagerData().profession().is(VillagerProfession.MASON)) {
			return preferredWorkstationHomeJobSite(level, villager, buildSites, SettlementBuildSiteType.MASON_WORKSHOP, () -> heldMasonJobSite(level, villager));
		}

		return Optional.empty();
	}

	private static Optional<BlockPos> preferredWorkstationHomeJobSite(
		ServerLevel level,
		Villager villager,
		List<SettlementBuildSite> buildSites,
		SettlementBuildSiteType expectedBuildSiteType,
		java.util.function.Supplier<Optional<BlockPos>> heldJobSite
	) {
		return heldJobSite.get()
			.filter(jobSite -> buildSites.stream()
				.anyMatch(buildSite -> buildSite.blueprintId() == expectedBuildSiteType && buildSite.workstationPos().equals(jobSite)));
	}

	private static Optional<BlockPos> findReachableCarpenterJobSite(ServerLevel level, Villager villager) {
		return level.getPoiManager().findAllClosestFirstWithType(
			poiType -> poiType.is(LiveVillagesVillagerProfessions.CARPENTER_POI),
			pos -> true,
			villager.blockPosition(),
			JOB_SITE_SEARCH_RADIUS_BLOCKS,
			net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.HAS_SPACE
		)
			.filter(pair -> canReach(villager, pair.getSecond(), pair.getFirst()))
			.map(com.mojang.datafixers.util.Pair::getSecond)
			.findFirst();
	}

	private static Optional<BlockPos> findReachableRoadwrightJobSite(ServerLevel level, Villager villager) {
		return level.getPoiManager().findAllClosestFirstWithType(
			poiType -> poiType.is(LiveVillagesVillagerProfessions.ROADWRIGHT_POI),
			pos -> true,
			villager.blockPosition(),
			JOB_SITE_SEARCH_RADIUS_BLOCKS,
			net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.HAS_SPACE
		)
			.filter(pair -> canReach(villager, pair.getSecond(), pair.getFirst()))
			.map(com.mojang.datafixers.util.Pair::getSecond)
			.findFirst();
	}

	private static Optional<BlockPos> findReachableForesterJobSite(ServerLevel level, Villager villager) {
		return level.getPoiManager().findAllClosestFirstWithType(
			poiType -> poiType.is(LiveVillagesVillagerProfessions.FORESTER_POI),
			pos -> true,
			villager.blockPosition(),
			JOB_SITE_SEARCH_RADIUS_BLOCKS,
			net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.HAS_SPACE
		)
			.filter(pair -> canReach(villager, pair.getSecond(), pair.getFirst()))
			.map(com.mojang.datafixers.util.Pair::getSecond)
			.findFirst();
	}

	private static Optional<BlockPos> findReachableMinerJobSite(ServerLevel level, Villager villager) {
		return level.getPoiManager().findAllClosestFirstWithType(
			poiType -> poiType.is(LiveVillagesVillagerProfessions.MINER_POI),
			pos -> true,
			villager.blockPosition(),
			JOB_SITE_SEARCH_RADIUS_BLOCKS,
			net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.HAS_SPACE
		)
			.filter(pair -> canReach(villager, pair.getSecond(), pair.getFirst()))
			.map(com.mojang.datafixers.util.Pair::getSecond)
			.findFirst();
	}

	private static Optional<BlockPos> findReachablePortmasterJobSite(ServerLevel level, Villager villager) {
		return level.getPoiManager().findAllClosestFirstWithType(
			poiType -> poiType.is(LiveVillagesVillagerProfessions.PORTMASTER_POI),
			pos -> true,
			villager.blockPosition(),
			JOB_SITE_SEARCH_RADIUS_BLOCKS,
			net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.HAS_SPACE
		)
			.filter(pair -> canReach(villager, pair.getSecond(), pair.getFirst()))
			.map(com.mojang.datafixers.util.Pair::getSecond)
			.findFirst();
	}

	private static Optional<BlockPos> findReachableFletcherJobSite(
		ServerLevel level,
		Villager villager,
		net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy occupancy
	) {
		return level.getPoiManager().findAllClosestFirstWithType(
			poiType -> poiType.is(PoiTypes.FLETCHER),
			pos -> true,
			villager.blockPosition(),
			JOB_SITE_SEARCH_RADIUS_BLOCKS,
			occupancy
		)
			.filter(pair -> canReach(villager, pair.getSecond(), pair.getFirst()))
			.map(com.mojang.datafixers.util.Pair::getSecond)
			.findFirst();
	}

	private static void assignTrademasterJobSite(ServerLevel level, Villager villager, BlockPos jobSite) {
		GlobalPos globalPos = GlobalPos.of(level.dimension(), jobSite);
		villager.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
		villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, globalPos);
		villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess(), LiveVillagesVillagerProfessions.TRADEMASTER));
		lockCustomProfession(villager);
		villager.setOffers(new MerchantOffers());
		villager.refreshBrain(level);
		level.broadcastEntityEvent(villager, (byte) 14);
	}

	private static void assignCarpenterJobSite(ServerLevel level, Villager villager, BlockPos jobSite) {
		GlobalPos globalPos = GlobalPos.of(level.dimension(), jobSite);
		villager.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
		villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, globalPos);
		villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess(), LiveVillagesVillagerProfessions.CARPENTER));
		lockCustomProfession(villager);
		villager.setOffers(new MerchantOffers());
		villager.refreshBrain(level);
		level.broadcastEntityEvent(villager, (byte) 14);
	}

	private static void assignRoadwrightJobSite(ServerLevel level, Villager villager, BlockPos jobSite) {
		GlobalPos globalPos = GlobalPos.of(level.dimension(), jobSite);
		villager.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
		villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, globalPos);
		villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess(), LiveVillagesVillagerProfessions.ROADWRIGHT));
		lockCustomProfession(villager);
		villager.setOffers(new MerchantOffers());
		villager.refreshBrain(level);
		level.broadcastEntityEvent(villager, (byte) 14);
	}

	private static void assignForesterJobSite(ServerLevel level, Villager villager, BlockPos jobSite) {
		GlobalPos globalPos = GlobalPos.of(level.dimension(), jobSite);
		villager.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
		villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, globalPos);
		villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess(), LiveVillagesVillagerProfessions.FORESTER));
		lockCustomProfession(villager);
		villager.setOffers(new MerchantOffers());
		villager.refreshBrain(level);
		level.broadcastEntityEvent(villager, (byte) 14);
	}

	private static void assignMinerJobSite(ServerLevel level, Villager villager, BlockPos jobSite) {
		GlobalPos globalPos = GlobalPos.of(level.dimension(), jobSite);
		villager.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
		villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, globalPos);
		villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess(), LiveVillagesVillagerProfessions.MINER));
		lockCustomProfession(villager);
		villager.setOffers(new MerchantOffers());
		villager.refreshBrain(level);
		level.broadcastEntityEvent(villager, (byte) 14);
	}

	private static void assignPortmasterJobSite(ServerLevel level, Villager villager, BlockPos jobSite) {
		GlobalPos globalPos = GlobalPos.of(level.dimension(), jobSite);
		villager.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
		villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, globalPos);
		villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess(), LiveVillagesVillagerProfessions.PORTMASTER));
		lockCustomProfession(villager);
		villager.setOffers(new MerchantOffers());
		villager.refreshBrain(level);
		level.broadcastEntityEvent(villager, (byte) 14);
	}

	private static void assignFletcherJobSite(ServerLevel level, Villager villager, BlockPos jobSite) {
		GlobalPos globalPos = GlobalPos.of(level.dimension(), jobSite);
		villager.releasePoi(MemoryModuleType.JOB_SITE);
		villager.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
		villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, globalPos);
		villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.FLETCHER));
		lockCustomProfession(villager);
		villager.setOffers(new MerchantOffers());
		villager.refreshBrain(level);
		level.broadcastEntityEvent(villager, (byte) 14);
	}

	private static void lockCustomProfession(Villager villager) {
		if (villager.getVillagerXp() < CUSTOM_PROFESSION_LOCK_XP) {
			villager.setVillagerXp(CUSTOM_PROFESSION_LOCK_XP);
		}
	}

	private static boolean ensureCarpenterWorkshopHome(ServerLevel level, Villager carpenter, List<Villager> villagers) {
		Optional<BlockPos> jobSite = heldCarpenterJobSite(level, carpenter);

		if (jobSite.isEmpty()) {
			return false;
		}

		Optional<BlockPos> workshopHome = findCarpenterWorkshopHome(level, jobSite.get());

		if (workshopHome.isEmpty()) {
			return false;
		}

		BlockPos homePos = workshopHome.get();
		Optional<BlockPos> currentHome = heldHome(level, carpenter);

		if (currentHome.filter(homePos::equals).isPresent()) {
			return false;
		}

		if (isClaimedByOtherCarpenter(level, homePos, carpenter, villagers)) {
			return false;
		}

		boolean changed = releaseNonCarpenterHomeClaims(level, homePos, carpenter, villagers);

		Optional<BlockPos> reservedHome = level.getPoiManager().take(
			poiType -> poiType.is(PoiTypes.HOME),
			(poiType, pos) -> pos.equals(homePos),
			homePos,
			1
		);

		if (reservedHome.isEmpty()) {
			return changed;
		}

		carpenter.releasePoi(MemoryModuleType.HOME);
		carpenter.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), reservedHome.get()));
		return true;
	}

	private static Optional<BlockPos> findCarpenterWorkshopHome(ServerLevel level, BlockPos jobSite) {
		return level.getPoiManager().findAllClosestFirstWithType(
			poiType -> poiType.is(PoiTypes.HOME),
			pos -> true,
			jobSite,
			CARPENTER_HOME_SEARCH_RADIUS_BLOCKS,
			net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY
		)
			.map(com.mojang.datafixers.util.Pair::getSecond)
			.findFirst();
	}

	private static boolean assignHome(ServerLevel level, Villager villager, BlockPos homePos, Optional<BlockPos> currentHome) {
		if (currentHome.filter(homePos::equals).isPresent()) {
			return false;
		}

		villager.releasePoi(MemoryModuleType.HOME);
		villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), homePos));
		return true;
	}

	private static Optional<BlockPos> heldHome(ServerLevel level, Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.HOME)
			.filter(globalPos -> globalPos.dimension().equals(level.dimension()))
			.map(GlobalPos::pos);
	}

	private static boolean isValidHome(ServerLevel level, SettlementState settlement, BlockPos homePos) {
		return horizontalDistanceSqr(homePos, settlement.center()) <= HOME_SEARCH_RADIUS_BLOCKS * HOME_SEARCH_RADIUS_BLOCKS
			&& level.hasChunkAt(homePos)
			&& level.getPoiManager().exists(homePos, poiType -> poiType.is(PoiTypes.HOME));
	}

	private static boolean releaseNonCarpenterHomeClaims(ServerLevel level, BlockPos homePos, Villager carpenter, List<Villager> villagers) {
		boolean changed = false;

		for (Villager villager : villagers) {
			if (villager.getUUID().equals(carpenter.getUUID()) || isCustomCarpenter(villager)) {
				continue;
			}

			if (heldHome(level, villager).filter(homePos::equals).isEmpty()) {
				continue;
			}

			villager.releasePoi(MemoryModuleType.HOME);
			villager.getBrain().eraseMemory(MemoryModuleType.HOME);
			changed = true;
		}

		return changed;
	}

	private static boolean isClaimedByOtherCarpenter(ServerLevel level, BlockPos homePos, Villager carpenter, List<Villager> villagers) {
		return villagers.stream()
			.filter(villager -> !villager.getUUID().equals(carpenter.getUUID()) && isCustomCarpenter(villager))
			.map(villager -> heldHome(level, villager))
			.anyMatch(home -> home.filter(homePos::equals).isPresent());
	}

	private static boolean canReach(Villager villager, BlockPos pos, Holder<PoiType> poiType) {
		var path = villager.getNavigation().createPath(pos, poiType.value().validRange());
		return path != null && path.canReach();
	}

	private static boolean canBecomeTrademaster(Villager villager) {
		return trademasterCandidateRank(villager) != Integer.MAX_VALUE;
	}

	private static boolean canBecomeCarpenter(Villager villager) {
		return carpenterCandidateRank(villager) != Integer.MAX_VALUE;
	}

	private static boolean canBecomeRoadwright(Villager villager) {
		return roadwrightCandidateRank(villager) != Integer.MAX_VALUE;
	}

	private static boolean canBecomeForester(Villager villager) {
		return foresterCandidateRank(villager) != Integer.MAX_VALUE;
	}

	private static boolean canBecomeMiner(Villager villager) {
		return minerCandidateRank(villager) != Integer.MAX_VALUE;
	}

	private static boolean canBecomePortmaster(Villager villager) {
		return portmasterCandidateRank(villager) != Integer.MAX_VALUE;
	}

	private static boolean canBecomeFletcher(Villager villager) {
		return fletcherCandidateRank(villager) != Integer.MAX_VALUE;
	}

	private static boolean canHelpWithConstruction(Villager villager) {
		if (villager.isBaby()) {
			return false;
		}

		Holder<VillagerProfession> profession = villager.getVillagerData().profession();
		return !profession.is(VillagerProfession.NITWIT);
	}

	private static String villagerTaskKey(
		ServerLevel level,
		SettlementState settlement,
		Villager villager,
		Set<String> constructionDeliveryVillagerIds,
		Map<String, String> roadworkTaskKeysByVillager,
		boolean roadworkActive
	) {
		if (villager.isBaby()) {
			if (villager.isSleeping()) {
				return "sleeping_in_bed";
			}

			return isVillageWakeupTime(level) ? "waking_breakfast" : "playing";
		}

		if (constructionDeliveryVillagerIds.contains(villager.getUUID().toString())) {
			return "carrying_construction_supplies";
		}

		if (villager.isSleeping()) {
			return "sleeping_in_bed";
		}

		if (isVillageRestTime(level)) {
			return heldHome(level, villager).isPresent() ? "returning_home" : "seeking_bed";
		}

		if (isVillageWakeupTime(level)) {
			return "waking_breakfast";
		}

		Optional<String> defenseTask = SettlementDefenseWork.loadedDefenseTaskKey(level, villager);

		if (defenseTask.isPresent()) {
			return defenseTask.get();
		}

		Optional<String> carriedGoodsTask = SettlementVillagerItemPickupWork.villagerTaskKey(level, villager);

		if (carriedGoodsTask.isPresent()) {
			return carriedGoodsTask.get();
		}

		Optional<String> breakTask = SettlementVillagerWorkSchedule.breakTaskKey(level, villager);

		if (breakTask.isPresent()) {
			return breakTask.get();
		}

		if (isVillageGatheringTime(level)) {
			if (isNearBabyVillager(level, villager)) {
				return "raising_child";
			}

			return isActuallyAtVillageGathering(level, settlement, villager) ? "village_gathering" : "heading_to_gathering";
		}

		if (isCustomRoadwright(villager)) {
			return roadworkTaskKeysByVillager.getOrDefault(villager.getUUID().toString(), "surveying_village");
		}

		if (isCustomForester(villager)) {
			Optional<String> forestryTask = SettlementForesterWork.loadedForestryTaskKey(level, villager);

			if (forestryTask.isPresent()) {
				return forestryTask.get();
			}
		}

		if (isCustomMiner(villager)) {
			Optional<String> minerTask = SettlementMinerWork.loadedMinerTaskKey(level, villager);

			if (minerTask.isPresent()) {
				return minerTask.get();
			}
		}

		if (villager.getVillagerData().profession().is(VillagerProfession.BUTCHER)) {
			Optional<String> butcherTask = SettlementButcherWork.loadedButcherTaskKey(level, villager);

			if (butcherTask.isPresent()) {
				return butcherTask.get();
			}
		}

		if (villager.getVillagerData().profession().is(VillagerProfession.FISHERMAN)) {
			Optional<String> fishermanTask = SettlementFishermanWork.loadedFishermanTaskKey(level, villager);

			if (fishermanTask.isPresent()) {
				return fishermanTask.get();
			}
		}

		if (isCustomPortmaster(villager)) {
			Optional<String> harborTask = SettlementPortmasterWork.loadedPortmasterTaskKey(level, villager);

			if (harborTask.isPresent()) {
				return harborTask.get();
			}
		}

		if (isCustomCarpenter(villager)) {
			Optional<String> carpentryTask = SettlementCarpenterWork.loadedCarpentryTaskKey(level, villager);

			if (carpentryTask.isPresent()) {
				return carpentryTask.get();
			}
		}

		if (villager.getVillagerData().profession().is(VillagerProfession.FLETCHER)) {
			Optional<String> fletchingTask = SettlementFletcherWork.loadedFletcherTaskKey(level, villager);

			if (fletchingTask.isPresent()) {
				return fletchingTask.get();
			}
		}

		if (villager.getVillagerData().profession().is(VillagerProfession.MASON)) {
			Optional<String> masonTask = SettlementMasonWork.loadedMasonTaskKey(level, villager);

			if (masonTask.isPresent()) {
				return masonTask.get();
			}
		}

		if (isNearBabyVillager(level, villager)) {
			return "raising_child";
		}

		if (villager.getVillagerData().profession().is(VillagerProfession.NONE) && roadworkActive) {
			return "helping_roadwright";
		}

		return professionTaskKey(villager, roadworkTaskKeysByVillager, roadworkActive);
	}

	private static String professionTaskKey(Villager villager, Map<String, String> roadworkTaskKeysByVillager, boolean roadworkActive) {
		Holder<VillagerProfession> profession = villager.getVillagerData().profession();

		if (profession.is(VillagerProfession.NONE)) {
			return "available_for_construction";
		}

		if (profession.is(VillagerProfession.NITWIT)) {
			return "mingling";
		}

		if (profession.is(LiveVillagesVillagerProfessions.TRADEMASTER)) {
			return "managing_trades";
		}

		if (profession.is(LiveVillagesVillagerProfessions.CARPENTER)) {
			return "maintaining_workshop";
		}

		if (profession.is(LiveVillagesVillagerProfessions.ROADWRIGHT)) {
			return roadworkTaskKeysByVillager.getOrDefault(villager.getUUID().toString(), "surveying_village");
		}

		if (profession.is(LiveVillagesVillagerProfessions.FORESTER)) {
			return "surveying_groves";
		}

		if (profession.is(LiveVillagesVillagerProfessions.MINER)) {
			return "surveying_mine";
		}

		if (profession.is(LiveVillagesVillagerProfessions.PORTMASTER)) {
			return "managing_harbor";
		}

		if (profession.is(VillagerProfession.FARMER)) {
			return "tending_crops";
		}

		if (profession.is(VillagerProfession.BUTCHER)) {
			return "tending_livestock";
		}

		if (profession.is(VillagerProfession.FISHERMAN)) {
			return "fishing";
		}

		if (profession.is(VillagerProfession.SHEPHERD)) {
			return "tending_flocks";
		}

		if (profession.is(VillagerProfession.MASON)) {
			return "stonework";
		}

		if (profession.is(VillagerProfession.CARTOGRAPHER)) {
			return "updating_maps";
		}

		if (profession.is(VillagerProfession.FLETCHER)) {
			return "stocking_arrows";
		}

		if (profession.is(VillagerProfession.CLERIC)) {
			return "community_care";
		}

		if (profession.is(VillagerProfession.LIBRARIAN)) {
			return "cataloging_books";
		}

		if (isConstructionSupportWorker(profession)) {
			return "maintaining_tools";
		}

		return canHelpWithConstruction(villager) ? "profession_work" : "idle";
	}

	private static boolean isNearBabyVillager(ServerLevel level, Villager villager) {
		return nearbyVillagers(level, villager.blockPosition(), CHILDCARE_RADIUS_BLOCKS).stream()
			.anyMatch(other -> !other.getUUID().equals(villager.getUUID()) && other.isBaby());
	}

	private static boolean isActuallyAtVillageGathering(ServerLevel level, SettlementState settlement, Villager villager) {
		BlockPos gatheringPos = resolveGatheringPos(level, settlement);
		return villager.blockPosition().distSqr(gatheringPos) <= GATHERING_RADIUS_BLOCKS * GATHERING_RADIUS_BLOCKS;
	}

	private static boolean isVillageGatheringTime(ServerLevel level) {
		long dayTime = level.getOverworldClockTime() % 24_000L;
		return dayTime >= VILLAGE_GATHERING_START_TICK && dayTime < VILLAGE_REST_START_TICK;
	}

	private static boolean isVillageRestTime(ServerLevel level) {
		long dayTime = level.getOverworldClockTime() % 24_000L;
		return dayTime >= VILLAGE_REST_START_TICK;
	}

	private static boolean isVillageWakeupTime(ServerLevel level) {
		long dayTime = level.getOverworldClockTime() % 24_000L;
		return dayTime < VILLAGE_WAKEUP_END_TICK;
	}

	private static BlockPos resolveGatheringPos(ServerLevel level, SettlementState settlement) {
		return level.getPoiManager()
			.findClosest(
				holder -> holder.is(PoiTypes.MEETING),
				settlement.center(),
				villagerRadius(settlement),
				net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY
			)
			.or(() -> level.getPoiManager()
				.findClosest(
					holder -> holder.is(LiveVillagesVillagerProfessions.TRADEMASTER_POI),
					settlement.center(),
					villagerRadius(settlement),
					net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY
				)
				.flatMap(pos -> SettlementStockAccess.stockAccessStandPos(level, pos).or(() -> Optional.of(pos))))
			.or(() -> level.getPoiManager()
				.findAllClosestFirstWithType(
					poiType -> !poiType.is(PoiTypes.HOME) && !poiType.is(PoiTypes.MEETING),
					pos -> true,
					settlement.center(),
					villagerRadius(settlement),
					net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY
				)
				.map(com.mojang.datafixers.util.Pair::getSecond)
				.flatMap(pos -> SettlementStockAccess.stockAccessStandPos(level, pos).or(() -> Optional.of(pos)).stream())
				.findFirst())
			.orElse(settlement.center());
	}

	private static int trademasterCandidateRank(Villager villager) {
		if (villager.isBaby()) {
			return Integer.MAX_VALUE;
		}

		Holder<VillagerProfession> profession = villager.getVillagerData().profession();

		if (profession.is(VillagerProfession.NONE)) {
			return 0;
		}

		return Integer.MAX_VALUE;
	}

	private static int carpenterCandidateRank(Villager villager) {
		if (villager.isBaby()) {
			return Integer.MAX_VALUE;
		}

		Holder<VillagerProfession> profession = villager.getVillagerData().profession();

		if (profession.is(VillagerProfession.NONE)) {
			return 0;
		}

		return Integer.MAX_VALUE;
	}

	private static int roadwrightCandidateRank(Villager villager) {
		if (villager.isBaby()) {
			return Integer.MAX_VALUE;
		}

		Holder<VillagerProfession> profession = villager.getVillagerData().profession();

		if (profession.is(VillagerProfession.NONE)) {
			return 0;
		}

		return Integer.MAX_VALUE;
	}

	private static int foresterCandidateRank(Villager villager) {
		if (villager.isBaby()) {
			return Integer.MAX_VALUE;
		}

		Holder<VillagerProfession> profession = villager.getVillagerData().profession();

		if (profession.is(VillagerProfession.NONE)) {
			return 0;
		}

		return Integer.MAX_VALUE;
	}

	private static int minerCandidateRank(Villager villager) {
		if (villager.isBaby()) {
			return Integer.MAX_VALUE;
		}

		Holder<VillagerProfession> profession = villager.getVillagerData().profession();

		if (profession.is(VillagerProfession.NONE)) {
			return 0;
		}

		return Integer.MAX_VALUE;
	}

	private static int portmasterCandidateRank(Villager villager) {
		if (villager.isBaby()) {
			return Integer.MAX_VALUE;
		}

		Holder<VillagerProfession> profession = villager.getVillagerData().profession();

		if (profession.is(VillagerProfession.NONE)) {
			return 0;
		}

		return Integer.MAX_VALUE;
	}

	private static int fletcherCandidateRank(Villager villager) {
		if (villager.isBaby()) {
			return Integer.MAX_VALUE;
		}

		Holder<VillagerProfession> profession = villager.getVillagerData().profession();

		if (profession.is(VillagerProfession.FLETCHER)) {
			return 0;
		}

		if (profession.is(VillagerProfession.NONE)) {
			return 1;
		}

		return Integer.MAX_VALUE;
	}

	private static int desiredFletcherCount(ServerLevel level, SettlementState settlement) {
		long workstationCount = level.getPoiManager().findAllClosestFirstWithType(
			poiType -> poiType.is(PoiTypes.FLETCHER),
			pos -> true,
			settlement.center(),
			villagerRadius(settlement),
			net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY
		)
			.map(com.mojang.datafixers.util.Pair::getSecond)
			.map(BlockPos::immutable)
			.distinct()
			.count();
		return (int) Math.min(Integer.MAX_VALUE, workstationCount * 2L);
	}

	private static int desiredPortmasterCount(ServerLevel level, SettlementState settlement) {
		long workstationCount = level.getPoiManager().findAllClosestFirstWithType(
			poiType -> poiType.is(LiveVillagesVillagerProfessions.PORTMASTER_POI),
			pos -> true,
			settlement.center(),
			villagerRadius(settlement),
			net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY
		)
			.map(com.mojang.datafixers.util.Pair::getSecond)
			.map(BlockPos::immutable)
			.distinct()
			.count();
		return (int) Math.min(Integer.MAX_VALUE, workstationCount);
	}

	private static int desiredMinerCount(ServerLevel level, SettlementState settlement) {
		long workstationCount = level.getPoiManager().findAllClosestFirstWithType(
			poiType -> poiType.is(LiveVillagesVillagerProfessions.MINER_POI),
			pos -> true,
			settlement.center(),
			villagerRadius(settlement),
			net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY
		)
			.map(com.mojang.datafixers.util.Pair::getSecond)
			.map(BlockPos::immutable)
			.distinct()
			.count();
		return (int) Math.min(Integer.MAX_VALUE, workstationCount);
	}

	private static boolean isCustomTrademaster(Villager villager) {
		return villager.getVillagerData().profession().is(LiveVillagesVillagerProfessions.TRADEMASTER);
	}

	private static boolean isCustomCarpenter(Villager villager) {
		return villager.getVillagerData().profession().is(LiveVillagesVillagerProfessions.CARPENTER);
	}

	private static boolean isCustomRoadwright(Villager villager) {
		return villager.getVillagerData().profession().is(LiveVillagesVillagerProfessions.ROADWRIGHT);
	}

	private static boolean isCustomForester(Villager villager) {
		return villager.getVillagerData().profession().is(LiveVillagesVillagerProfessions.FORESTER);
	}

	private static boolean isCustomMiner(Villager villager) {
		return villager.getVillagerData().profession().is(LiveVillagesVillagerProfessions.MINER);
	}

	private static boolean isCustomPortmaster(Villager villager) {
		return villager.getVillagerData().profession().is(LiveVillagesVillagerProfessions.PORTMASTER);
	}

	private static String displayProfessionKey(Villager villager) {
		if (villager.isBaby()) {
			return null;
		}

		Holder<VillagerProfession> profession = villager.getVillagerData().profession();

		if (profession.is(VillagerProfession.NONE)) {
			return SettlementRoleKeys.UNEMPLOYED;
		}

		if (profession.is(VillagerProfession.NITWIT)) {
			return "nitwit";
		}

		if (profession.is(LiveVillagesVillagerProfessions.CARPENTER)) {
			return SettlementRoleKeys.CARPENTER;
		}

		if (profession.is(VillagerProfession.CARTOGRAPHER)) {
			return SettlementRoleKeys.CARTOGRAPHER;
		}

		if (profession.is(VillagerProfession.FLETCHER)) {
			return SettlementRoleKeys.FLETCHER;
		}

		if (profession.is(LiveVillagesVillagerProfessions.PORTMASTER)) {
			return SettlementRoleKeys.PORTMASTER;
		}

		if (profession.is(LiveVillagesVillagerProfessions.ROADWRIGHT)) {
			return SettlementRoleKeys.ROADWRIGHT;
		}

		if (profession.is(LiveVillagesVillagerProfessions.FORESTER)) {
			return SettlementRoleKeys.FORESTER;
		}

		if (profession.is(LiveVillagesVillagerProfessions.MINER)) {
			return SettlementRoleKeys.MINER;
		}

		return profession.unwrapKey()
			.map(key -> key.identifier().getPath())
			.orElse("villager");
	}

	private static double distanceToCenterSqr(Villager villager, BlockPos center) {
		double dx = villager.getX() - (center.getX() + 0.5D);
		double dz = villager.getZ() - (center.getZ() + 0.5D);
		return (dx * dx) + (dz * dz);
	}

	private static double horizontalDistanceSqr(BlockPos first, BlockPos second) {
		double dx = (first.getX() + 0.5D) - (second.getX() + 0.5D);
		double dz = (first.getZ() + 0.5D) - (second.getZ() + 0.5D);
		return (dx * dx) + (dz * dz);
	}

	private static boolean isFoodWorker(Holder<VillagerProfession> profession) {
		return profession.is(VillagerProfession.FARMER)
			|| profession.is(VillagerProfession.BUTCHER)
			|| profession.is(VillagerProfession.FISHERMAN)
			|| profession.is(VillagerProfession.SHEPHERD);
	}

	private static boolean isConstructionSupportWorker(Holder<VillagerProfession> profession) {
		return profession.is(VillagerProfession.ARMORER)
			|| profession.is(VillagerProfession.TOOLSMITH)
			|| profession.is(VillagerProfession.WEAPONSMITH);
	}

	private static boolean isTradeWorker(Holder<VillagerProfession> profession) {
		return profession.is(LiveVillagesVillagerProfessions.TRADEMASTER)
			|| profession.is(VillagerProfession.CARTOGRAPHER)
			|| profession.is(VillagerProfession.CLERIC)
			|| profession.is(VillagerProfession.LIBRARIAN);
	}

	private static int villagerRadius(SettlementState settlement) {
		return switch (settlement.kind()) {
			case VILLAGE, HARBOR, CUSTOM -> VILLAGE_RADIUS_BLOCKS;
			case OUTPOST -> STANDARD_RADIUS_BLOCKS;
		};
	}

	private static Optional<BlockPos> findSpawnPosAtColumn(ServerLevel level, int x, int z) {
		int spawnY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos feetPos = new BlockPos(x, spawnY, z);

		if (!level.isLoaded(feetPos)) {
			return Optional.empty();
		}

		if (!level.getBlockState(feetPos.below()).isSolid()) {
			return Optional.empty();
		}

		if (!level.getBlockState(feetPos).isAir() || !level.getBlockState(feetPos.above()).isAir()) {
			return Optional.empty();
		}

		return Optional.of(feetPos);
	}

	public static String taskDescription(String taskKey) {
		return switch (taskKey) {
			case "available_for_construction" -> "available for construction";
			case "carrying_construction_supplies" -> "carrying construction supplies";
			case "carrying_collected_items" -> "carrying collected items";
			case "cataloging_books" -> "cataloging books";
			case "community_care" -> "community care";
			case "collecting_forest_drops" -> "collecting forest drops";
			case "cutting_stone" -> "cutting stone";
			case "cutting_trees" -> "cutting trees";
			case "depositing_into_trading_post" -> "depositing into Trading Post";
			case "digging_mine_shaft" -> "digging mine shaft";
			case "fishing" -> "fishing";
			case "fishing_from_boat" -> "fishing from boat";
			case "fishing_from_dock" -> "fishing from dock";
			case "fishing_from_shore" -> "fishing from shore";
			case "growing_up" -> "growing up";
			case "heading_to_boat" -> "heading to boat";
			case "heading_to_gathering" -> "heading to gathering";
			case "helping_roadwright" -> "helping Roadwright";
			case "idle" -> "idle";
			case "improving_paths" -> "improving paths";
			case "lighting_lighthouse" -> "lighting lighthouse";
			case "lighting_cave" -> "lighting cave";
			case "lighting_mine_shaft" -> "lighting mine shaft";
			case "inspecting_docks" -> "inspecting docks";
			case "checking_lighthouse" -> "checking lighthouse";
			case "extinguishing_lighthouse" -> "extinguishing lighthouse";
			case "mining_ore_vein" -> "mining ore vein";
			case "maintaining_tools" -> "maintaining tools";
			case "maintaining_workshop" -> "maintaining workshop";
			case "managing_harbor" -> "managing harbor";
			case "managing_trades" -> "managing trades";
			case "mingling" -> "mingling";
			case "mining_shaft_stone" -> "mining shaft stone";
			case "playing" -> "playing";
			case "planning_docks" -> "planning docks";
			case "profession_work" -> "profession work";
			case "raising_child" -> "raising child";
			case "reinforcing_shaft_support" -> "reinforcing shaft support";
			case "returning_fishing_boat" -> "returning fishing boat";
			case "returning_home" -> "returning home";
			case "rowing_out" -> "rowing out";
			case "seeking_bed" -> "seeking bed";
			case "sleeping" -> "sleeping";
			case "sleeping_in_bed" -> "sleeping in bed";
			case "placing_shaft_ladders" -> "placing shaft ladders";
			case "warning_harbor" -> "warning harbor";
			case "stonework" -> "stonework";
			case "defending_village" -> "defending village";
			case "breeding_herds" -> "breeding herds";
			case "building_pens" -> "building pens";
			case "culling_herds" -> "culling herds";
			case "herding_animals" -> "herding animals";
			case "planting_seedlings" -> "planting seedlings";
			case "stocking_arrows" -> "stocking arrows";
			case "stocking_carpentry" -> "stocking carpentry";
			case "stone_construction" -> "stone construction";
			case "surveying_groves" -> "surveying groves";
			case "surveying_routes" -> "surveying routes";
			case "surveying_village" -> "surveying village";
			case "taking_break" -> "taking a break";
			case "tending_crops" -> "tending crops";
			case "tending_flocks" -> "tending flocks";
			case "tending_livestock" -> "tending livestock";
			case "updating_maps" -> "updating maps";
			case "village_gathering" -> "village gathering";
			case "waking_breakfast" -> "waking up, breakfast";
			default -> humanizeKey(taskKey);
		};
	}

	private static String humanizeKey(String key) {
		String[] parts = key.split("_");
		StringBuilder result = new StringBuilder();

		for (String part : parts) {
			if (part.isEmpty()) {
				continue;
			}

			if (!result.isEmpty()) {
				result.append(' ');
			}

			result.append(Character.toUpperCase(part.charAt(0)));
			if (part.length() > 1) {
				result.append(part.substring(1));
			}
		}

		return result.toString();
	}

	public record VillagerTaskCount(
		String roleKey,
		String taskKey,
		int count
	) {
	}

	public record ProfessionDebugInfo(String targetLabel, String detailLabel) {
		public static final ProfessionDebugInfo EMPTY = new ProfessionDebugInfo("", "");
	}

	public record VillagerDebugView(
		String workerLabel,
		String roleLabel,
		String taskKey,
		String taskLabel,
		String targetLabel,
		String detailLabel
	) {
	}
}
