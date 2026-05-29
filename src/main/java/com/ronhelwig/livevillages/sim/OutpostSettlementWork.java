package com.ronhelwig.livevillages.sim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.ronhelwig.livevillages.content.LiveVillagesVillagerProfessions;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.phys.AABB;

public final class OutpostSettlementWork {
	private static final int OUTPOST_MEMBER_RADIUS_MARGIN_BLOCKS = 0;
	private static final int OUTPOST_CONSTRUCTION_MEMBER_RADIUS_BLOCKS = 24;
	private static final int OUTPOST_CONSTRUCTION_MEMBER_Y_RANGE_BLOCKS = 24;
	private static final int OUTPOST_WORK_INTERVAL_TICKS = 800;
	private static final int MAX_OUTPOST_RECRUITMENT_CAPACITY = 24;
	private static final int ACTIVE_RAID_MEMBER_RADIUS_BLOCKS = 1_024;
	private static final int RECRUITMENT_FOOD_COST = 3;
	private static final String LIVE_VILLAGES_RECRUIT_TAG = "livevillages.outpost_recruit";
	private static final String ACTIVE_RAID_MEMBER_TAG = "livevillages.outpost_raid_member";
	private static final String OUTPOST_MEMBER_TAG_PREFIX = "livevillages.outpost_member.";
	private static final List<String> RECRUITMENT_FOOD_PRIORITY = List.of(
		"bread",
		"baked_potato",
		"beef",
		"mutton",
		"pork",
		"cod",
		"carrot",
		"potato",
		"beetroot",
		"wheat"
	);

	private OutpostSettlementWork() {
	}

	public static Map<String, Integer> censusPopulation(ServerLevel level, SettlementState settlement) {
		LinkedHashMap<String, Integer> population = new LinkedHashMap<>();
		BlockPos center = settlement.center();
		int radius = SettlementVillagers.settlementRadiusBlocks(settlement) + OUTPOST_MEMBER_RADIUS_MARGIN_BLOCKS;
		AABB bounds = new AABB(center).inflate(radius, 32.0D, radius);
		Set<UUID> countedRaiders = new HashSet<>();

		for (Raider raider : level.getEntitiesOfClass(Raider.class, bounds, raider -> raider.isAlive() && !raider.isRemoved())) {
			countedRaiders.add(raider.getUUID());
			population.merge(roleKey(raider), 1, Integer::sum);
		}

		AABB activeRaidBounds = new AABB(center).inflate(ACTIVE_RAID_MEMBER_RADIUS_BLOCKS, 96.0D, ACTIVE_RAID_MEMBER_RADIUS_BLOCKS);
		for (Raider raider : level.getEntitiesOfClass(Raider.class, activeRaidBounds, raider ->
			raider.isAlive()
				&& !raider.isRemoved()
				&& !countedRaiders.contains(raider.getUUID())
				&& isActiveRaidMember(raider, settlement))) {
			countedRaiders.add(raider.getUUID());
			population.merge(roleKey(raider), 1, Integer::sum);
		}

		for (Villager villager : level.getEntitiesOfClass(Villager.class, bounds, villager -> villager.isAlive() && !villager.isRemoved())) {
			String professionKey = SettlementVillagers.reportProfessionKey(villager);

			if (professionKey == null || professionKey.isBlank()) {
				continue;
			}

			population.merge(professionKey, 1, Integer::sum);
		}

		return population;
	}

	static List<BlockPos> constructionMemberPositions(ServerLevel level, SettlementState settlement) {
		List<BlockPos> positions = new ArrayList<>();
		BlockPos center = settlement.center();
		int radius = SettlementVillagers.settlementRadiusBlocks(settlement) + OUTPOST_MEMBER_RADIUS_MARGIN_BLOCKS;
		AABB bounds = new AABB(center).inflate(radius, 32.0D, radius);

		for (Raider raider : level.getEntitiesOfClass(Raider.class, bounds, raider -> raider.isAlive() && !raider.isRemoved())) {
			positions.add(raider.blockPosition());
		}

		for (Villager villager : level.getEntitiesOfClass(Villager.class, bounds, villager -> villager.isAlive() && !villager.isRemoved())) {
			positions.add(villager.blockPosition());
		}

		return positions;
	}

	public static int trimExcessUntrackedRaiders(ServerLevel level, SettlementState settlement) {
		if (level == null || settlement == null || settlement.kind() != SettlementKind.OUTPOST) {
			return 0;
		}

		BlockPos center = settlement.center();
		int radius = SettlementVillagers.settlementRadiusBlocks(settlement) + OUTPOST_MEMBER_RADIUS_MARGIN_BLOCKS;
		AABB bounds = new AABB(center).inflate(radius, 32.0D, radius);
		List<Raider> raiders = new ArrayList<>(level.getEntitiesOfClass(Raider.class, bounds, raider -> raider.isAlive() && !raider.isRemoved()));
		int villagers = level.getEntitiesOfClass(Villager.class, bounds, villager -> villager.isAlive() && !villager.isRemoved()).size();
		int allowedRaiders = Math.max(0, recruitmentCapacity(settlement) - villagers);
		int excessRaiders = raiders.size() - allowedRaiders;

		if (excessRaiders <= 0) {
			return 0;
		}

		List<Raider> removableRaiders = raiders.stream()
			.filter(raider -> !isLiveVillagesRecruit(raider))
			.filter(raider -> !isActiveRaidMember(raider, settlement))
			.filter(raider -> !raider.hasCustomName())
			.sorted(Comparator.comparingDouble((Raider raider) -> raider.blockPosition().distSqr(center)).reversed())
			.toList();

		int removed = 0;
		for (Raider raider : removableRaiders) {
			if (removed >= excessRaiders) {
				break;
			}

			raider.discard();
			removed++;
		}

		return removed;
	}

	public static void markOutpostRaidMember(Raider raider, SettlementState settlement) {
		if (raider == null || settlement == null) {
			return;
		}

		raider.addTag(ACTIVE_RAID_MEMBER_TAG);
		raider.addTag(outpostMemberTag(settlement.id()));
		raider.setPersistenceRequired();
	}

	public static void clearActiveRaidMember(Raider raider) {
		if (raider == null) {
			return;
		}

		raider.removeTag(ACTIVE_RAID_MEMBER_TAG);
		raider.setCanJoinRaid(false);
		raider.setPatrolTarget(null);
	}

	public static boolean isActiveRaidMember(Raider raider, SettlementState settlement) {
		return raider != null
			&& settlement != null
			&& raider.entityTags().contains(ACTIVE_RAID_MEMBER_TAG)
			&& raider.entityTags().contains(outpostMemberTag(settlement.id()));
	}

	static boolean canReachConstructionFromAnyMember(List<BlockPos> memberPositions, BlockPos targetPos) {
		if (memberPositions == null || memberPositions.isEmpty() || targetPos == null) {
			return false;
		}

		double radiusSquared = OUTPOST_CONSTRUCTION_MEMBER_RADIUS_BLOCKS * OUTPOST_CONSTRUCTION_MEMBER_RADIUS_BLOCKS;

		for (BlockPos memberPos : memberPositions) {
			if (Math.abs(memberPos.getY() - targetPos.getY()) > OUTPOST_CONSTRUCTION_MEMBER_Y_RANGE_BLOCKS) {
				continue;
			}

			double dx = (memberPos.getX() + 0.5D) - (targetPos.getX() + 0.5D);
			double dz = (memberPos.getZ() + 0.5D) - (targetPos.getZ() + 0.5D);
			if ((dx * dx) + (dz * dz) <= radiusSquared) {
				return true;
			}
		}

		return false;
	}

	public static boolean canWorkThisPass(SettlementState settlement, long currentTick, int intervalTicks, int workerCount) {
		if (workerCount <= 0) {
			return false;
		}

		int effectiveInterval = Math.max(intervalTicks, OUTPOST_WORK_INTERVAL_TICKS / Math.min(workerCount, 4));
		long phase = stableModulo(settlement.id() + "|outpost_work", effectiveInterval);
		return Math.floorMod(currentTick - phase, effectiveInterval) < LiveVillagesScheduler.LOADED_MAINTENANCE_CHECK_INTERVAL;
	}

	public static int recruitmentCapacity(SettlementState settlement) {
		int baseCapacity = 5 + settlement.tier() * 3 + settlement.defenseLevel() * 2;
		return Math.max(4, Math.min(MAX_OUTPOST_RECRUITMENT_CAPACITY, baseCapacity));
	}

	public static int bedUsingPopulation(SettlementState settlement) {
		if (settlement == null || settlement.kind() != SettlementKind.OUTPOST) {
			return settlement == null ? 0 : settlement.totalPopulation();
		}

		return bedUsingPopulation(settlement, settlement.population());
	}

	public static int bedUsingPopulation(SettlementState settlement, Map<String, Integer> population) {
		if (settlement == null || settlement.kind() != SettlementKind.OUTPOST) {
			return population == null
				? 0
				: population.values().stream().mapToInt(Integer::intValue).sum();
		}

		if (population == null || population.isEmpty()) {
			return 0;
		}

		return population.entrySet().stream()
			.filter(entry -> !entry.getKey().equals(SettlementRoleKeys.PILLAGER))
			.filter(entry -> !entry.getKey().equals(SettlementRoleKeys.GUARD))
			.mapToInt(Map.Entry::getValue)
			.sum();
	}

	public static boolean hasRecruitmentSupplies(Map<String, Integer> stock) {
		return availableFood(stock) >= RECRUITMENT_FOOD_COST;
	}

	public static void consumeRecruitmentSupplies(Map<String, Integer> stock) {
		consumeFood(stock, RECRUITMENT_FOOD_COST);

		if (SettlementGoods.consumeGoods(stock, "emerald", 1)
			|| SettlementGoods.consumeGoods(stock, "iron_ingot", 1)
			|| SettlementGoods.consumeGoods(stock, "leather", 1)) {
			return;
		}

		SettlementGoods.consumeGoods(stock, "arrow", Math.min(4, stock.getOrDefault("arrow", 0)));
	}

	public static boolean spawnRecruit(ServerLevel level, SettlementState settlement, BlockPos spawnPos, Map<String, Integer> stock) {
		return switch (chooseRecruitKind(level, settlement)) {
			case FLETCHER -> spawnVillagerRecruit(level, spawnPos, VillagerProfession.FLETCHER, stock);
			case FORESTER -> spawnVillagerRecruit(level, spawnPos, LiveVillagesVillagerProfessions.FORESTER, stock);
			case ROADWRIGHT -> spawnVillagerRecruit(level, spawnPos, LiveVillagesVillagerProfessions.ROADWRIGHT, stock);
			case PILLAGER -> spawnPillagerRecruit(level, spawnPos, stock);
		};
	}

	private static RecruitKind chooseRecruitKind(ServerLevel level, SettlementState settlement) {
		Map<String, Integer> population = censusPopulation(level, settlement);
		long fletcherOpenings = Math.max(0L, workstationCount(level, settlement, RecruitKind.FLETCHER) - population.getOrDefault(SettlementRoleKeys.FLETCHER, 0));
		long foresterOpenings = Math.max(0L, workstationCount(level, settlement, RecruitKind.FORESTER) - population.getOrDefault(SettlementRoleKeys.FORESTER, 0));
		long roadwrightOpenings = Math.max(0L, workstationCount(level, settlement, RecruitKind.ROADWRIGHT) - population.getOrDefault(SettlementRoleKeys.ROADWRIGHT, 0));

		if (fletcherOpenings > 0 && foresterOpenings > 0) {
			int roll = (int) stableModulo(settlement.id() + "|recruit|" + level.getGameTime(), 100);
			return roll < 65 ? RecruitKind.FLETCHER : RecruitKind.FORESTER;
		}

		if (fletcherOpenings > 0) {
			return RecruitKind.FLETCHER;
		}

		if (foresterOpenings > 0) {
			return RecruitKind.FORESTER;
		}

		if (roadwrightOpenings > 0) {
			return RecruitKind.ROADWRIGHT;
		}

		return RecruitKind.PILLAGER;
	}

	private static long workstationCount(ServerLevel level, SettlementState settlement, RecruitKind recruitKind) {
		int radius = SettlementVillagers.settlementRadiusBlocks(settlement) + OUTPOST_MEMBER_RADIUS_MARGIN_BLOCKS;
		return level.getPoiManager().findAllClosestFirstWithType(
			poiType -> switch (recruitKind) {
				case FLETCHER -> poiType.is(PoiTypes.FLETCHER);
				case FORESTER -> poiType.is(LiveVillagesVillagerProfessions.FORESTER_POI);
				case ROADWRIGHT -> poiType.is(LiveVillagesVillagerProfessions.ROADWRIGHT_POI);
				case PILLAGER -> false;
			},
			pos -> true,
			settlement.center(),
			radius,
			PoiManager.Occupancy.ANY
		).count();
	}

	private static boolean spawnPillagerRecruit(ServerLevel level, BlockPos spawnPos, Map<String, Integer> stock) {
		Pillager pillager = EntityType.PILLAGER.create(level, EntitySpawnReason.EVENT);
		if (pillager == null) {
			return false;
		}

		pillager.setPos(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D);
		pillager.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), EntitySpawnReason.EVENT, null);
		OutpostGear.clearSpawnedEquipment(pillager);
		OutpostGear.equipFromStock(pillager, stock);
		pillager.addTag(LIVE_VILLAGES_RECRUIT_TAG);
		pillager.setPersistenceRequired();
		return level.addFreshEntity(pillager);
	}

	private static boolean spawnVillagerRecruit(
		ServerLevel level,
		BlockPos spawnPos,
		net.minecraft.resources.ResourceKey<VillagerProfession> profession,
		Map<String, Integer> stock
	) {
		Villager villager = EntityType.VILLAGER.create(level, EntitySpawnReason.EVENT);
		if (villager == null) {
			return false;
		}

		villager.setPos(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D);
		villager.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), EntitySpawnReason.EVENT, null);
		villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess(), profession));
		villager.setVillagerXp(1);
		villager.setOffers(new MerchantOffers());
		villager.refreshBrain(level);
		OutpostGear.clearSpawnedEquipment(villager);
		OutpostGear.equipFromStock(villager, stock);
		villager.addTag(LIVE_VILLAGES_RECRUIT_TAG);
		villager.setPersistenceRequired();
		return level.addFreshEntity(villager);
	}

	private static boolean isLiveVillagesRecruit(Raider raider) {
		return raider.entityTags().contains(LIVE_VILLAGES_RECRUIT_TAG);
	}

	private static String outpostMemberTag(String settlementId) {
		StringBuilder sanitized = new StringBuilder();

		for (int i = 0; i < settlementId.length(); i++) {
			char ch = settlementId.charAt(i);
			sanitized.append(Character.isLetterOrDigit(ch) || ch == '_' || ch == '-' ? ch : '_');
		}

		return OUTPOST_MEMBER_TAG_PREFIX + sanitized;
	}

	private static int availableFood(Map<String, Integer> stock) {
		int total = 0;

		for (String goodsKey : RECRUITMENT_FOOD_PRIORITY) {
			total += Math.max(0, stock.getOrDefault(goodsKey, 0));
		}

		return total;
	}

	private static void consumeFood(Map<String, Integer> stock, int amount) {
		int remaining = amount;

		for (String goodsKey : RECRUITMENT_FOOD_PRIORITY) {
			if (remaining <= 0) {
				break;
			}

			int used = Math.min(remaining, Math.max(0, stock.getOrDefault(goodsKey, 0)));
			if (used > 0 && SettlementGoods.consumeGoods(stock, goodsKey, used)) {
				remaining -= used;
			}
		}
	}

	private static String roleKey(Raider raider) {
		return raider instanceof Pillager ? SettlementRoleKeys.PILLAGER : SettlementRoleKeys.GUARD;
	}

	private static long stableModulo(String key, long modulo) {
		long hash = 1125899906842597L;

		for (int i = 0; i < key.length(); i++) {
			hash = (hash * 31L) + key.charAt(i);
		}

		return Math.floorMod(hash, modulo);
	}

	private enum RecruitKind {
		PILLAGER,
		FLETCHER,
		FORESTER,
		ROADWRIGHT
	}
}
