package com.ronhelwig.livevillages.sim;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

public final class SettlementButcherWork {
	private static final double BUTCHER_WORK_REACH_DISTANCE_SQUARED = 9.0D;
	private static final double BUTCHER_WALK_SPEED = 0.75D;
	private static final long BUTCHER_DECIDE_INTERVAL_TICKS = 320L;
	private static final int MAX_PEN_DETECTION_RADIUS_BLOCKS = 8;
	private static final int MAX_PEN_AREA_BLOCKS = 64;
	private static final int MIN_PEN_AREA_BLOCKS = 4;
	private static final int PEN_SITE_SEARCH_RADIUS_BLOCKS = 12;
	private static final int BLOCK_UPDATE_FLAGS = 3;
	private static final List<String> PIG_FEED_GOODS = List.of("carrot", "potato", "beetroot");
	private static final Map<String, TimedTask> ACTIVE_TASKS = new HashMap<>();

	private SettlementButcherWork() {
	}

	public static void maintainLoadedButchery(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, int routeCount) {
		HerdSurvey survey = survey(level, settlement);
		List<AnimalPen> pens = detectPens(level, survey.animals());

		for (Villager butcher : SettlementVillagers.nearbyButchers(level, settlement)) {
			if (!SettlementVillagerWorkSchedule.shouldStartNewWork(level, butcher, "butchery", BUTCHER_DECIDE_INTERVAL_TICKS)) {
				if (SettlementVillagerWorkSchedule.isTakingBreak(level, butcher)) {
					butcher.getNavigation().stop();
				}

				continue;
			}

			Optional<ButcherTask> task = chooseButcherTask(level, settlement, butcher, stock, routeCount, survey, pens);

			if (task.isEmpty()) {
				ACTIVE_TASKS.remove(butcher.getUUID().toString());
				continue;
			}

			ButcherTask butcherTask = task.get();
			BlockPos workPos = resolveTaskPos(level, butcherTask);

			if (workPos == null) {
				ACTIVE_TASKS.remove(butcher.getUUID().toString());
				continue;
			}

			ACTIVE_TASKS.put(butcher.getUUID().toString(), new TimedTask(butcherTask.taskKey(), level.getServer().getTickCount()));
			showButcherTool(butcher, butcherTask);
			steerButcherTowardTask(butcher, workPos);

			if (!isWithinWorkReach(butcher, workPos)) {
				continue;
			}

			if (performButcherTask(level, butcher, butcherTask, stock)) {
				butcher.swing(InteractionHand.MAIN_HAND);
			}
		}
	}

	public static void applyLoadedButcherWork(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, double elapsedDays) {
		int butchers = Math.max(0, settlement.population().getOrDefault(SettlementRoleKeys.BUTCHER, 0));

		if (butchers <= 0 || elapsedDays <= 0.0D) {
			return;
		}

		HerdSurvey survey = survey(level, settlement);

		if (survey.adultCows() <= 0 && survey.adultSheep() <= 0 && survey.adultPigs() <= 0) {
			return;
		}

		int activeButchers = Math.min(butchers, Math.max(1, (survey.adultCows() + survey.adultSheep() + survey.adultPigs() + 3) / 4));
		int requiredWheatFeed = scaledAmount(
			Math.min(activeButchers * 1.5D, (survey.adultCows() * 0.60D) + (survey.adultSheep() * 0.35D)),
			elapsedDays
		);
		int wheatFeedCost = Math.min(stock.getOrDefault("wheat", 0), requiredWheatFeed);
		double wheatCoverage = requiredWheatFeed <= 0 ? 1.0D : wheatFeedCost / (double) requiredWheatFeed;

		if (wheatFeedCost > 0) {
			stock.put("wheat", stock.getOrDefault("wheat", 0) - wheatFeedCost);
		}

		int requiredPigFeed = scaledAmount(Math.min(activeButchers, survey.adultPigs() * 0.40D), elapsedDays);
		int pigFeedCost = consumePigFeed(stock, requiredPigFeed);
		double pigCoverage = requiredPigFeed <= 0 ? 1.0D : pigFeedCost / (double) requiredPigFeed;

		SettlementGoods.addGoods(stock, "beef", scaledAmount(
			Math.min(activeButchers * 2.0D, survey.adultCows() * 0.35D) * wheatCoverage,
			elapsedDays
		));
		SettlementGoods.addGoods(stock, "mutton", scaledAmount(
			Math.min(activeButchers * 1.5D, survey.adultSheep() * 0.30D) * wheatCoverage,
			elapsedDays
		));
		SettlementGoods.addGoods(stock, "pork", scaledAmount(
			Math.min(activeButchers * 1.5D, survey.adultPigs() * 0.30D) * pigCoverage,
			elapsedDays
		));
	}

	public static Optional<String> loadedButcherTaskKey(ServerLevel level, Villager villager) {
		TimedTask task = ACTIVE_TASKS.get(villager.getUUID().toString());

		if (task == null || level.getServer().getTickCount() - task.tick() > 40L) {
			return Optional.empty();
		}

		return Optional.of(task.taskKey());
	}

	private static HerdSurvey survey(ServerLevel level, SettlementState settlement) {
		BlockPos center = settlement.center();
		double maxDistanceSquared = (double) SettlementVillagers.settlementRadiusBlocks(settlement) * SettlementVillagers.settlementRadiusBlocks(settlement);
		List<Animal> animals = new ArrayList<>();
		int adultCows = 0;
		int totalCows = 0;
		int adultSheep = 0;
		int totalSheep = 0;
		int adultPigs = 0;
		int totalPigs = 0;

		for (Animal animal : level.getEntities(
			EntityTypeTest.forClass(Animal.class),
			entity -> entity.distanceToSqr(center.getX() + 0.5D, center.getY() + 0.5D, center.getZ() + 0.5D) <= maxDistanceSquared
		)) {
			if (animal instanceof Cow) {
				animals.add(animal);
				totalCows++;
				if (!animal.isBaby()) {
					adultCows++;
				}
			} else if (animal instanceof Sheep) {
				animals.add(animal);
				totalSheep++;
				if (!animal.isBaby()) {
					adultSheep++;
				}
			} else if (animal instanceof Pig) {
				animals.add(animal);
				totalPigs++;
				if (!animal.isBaby()) {
					adultPigs++;
				}
			}
		}

		return new HerdSurvey(List.copyOf(animals), adultCows, totalCows, adultSheep, totalSheep, adultPigs, totalPigs);
	}

	private static List<AnimalPen> detectPens(ServerLevel level, List<Animal> animals) {
		List<AnimalPen> pens = new ArrayList<>();
		Set<String> seenBounds = new HashSet<>();

		for (Animal animal : animals) {
			if (!isPenWalkableCell(level, animal.blockPosition())) {
				continue;
			}

			Optional<AnimalPen> pen = detectPenAt(level, animal.blockPosition());

			if (pen.isEmpty()) {
				continue;
			}

			String key = penBoundsKey(pen.get());

			if (seenBounds.add(key)) {
				pens.add(pen.get());
			}
		}

		if (pens.isEmpty()) {
			return List.of();
		}

		List<AnimalPen> enrichedPens = new ArrayList<>(pens.size());

		for (AnimalPen pen : pens) {
			int cows = 0;
			int sheep = 0;
			int pigs = 0;

			for (Animal animal : animals) {
				if (!pen.contains(animal.blockPosition())) {
					continue;
				}

				if (animal instanceof Cow) {
					cows++;
				} else if (animal instanceof Sheep) {
					sheep++;
				} else if (animal instanceof Pig) {
					pigs++;
				}
			}

			enrichedPens.add(new AnimalPen(pen.min(), pen.max(), pen.center(), pen.interiorArea(), cows, sheep, pigs));
		}

		return List.copyOf(enrichedPens);
	}

	private static Optional<AnimalPen> detectPenAt(ServerLevel level, BlockPos start) {
		ArrayDeque<BlockPos> open = new ArrayDeque<>();
		Set<BlockPos> visited = new HashSet<>();
		open.add(start.immutable());
		int minX = start.getX();
		int maxX = start.getX();
		int minZ = start.getZ();
		int maxZ = start.getZ();

		while (!open.isEmpty()) {
			BlockPos current = open.removeFirst();

			if (!visited.add(current)) {
				continue;
			}

			if (visited.size() > MAX_PEN_AREA_BLOCKS
				|| Math.abs(current.getX() - start.getX()) > MAX_PEN_DETECTION_RADIUS_BLOCKS
				|| Math.abs(current.getZ() - start.getZ()) > MAX_PEN_DETECTION_RADIUS_BLOCKS) {
				return Optional.empty();
			}

			minX = Math.min(minX, current.getX());
			maxX = Math.max(maxX, current.getX());
			minZ = Math.min(minZ, current.getZ());
			maxZ = Math.max(maxZ, current.getZ());

			for (Direction direction : Direction.Plane.HORIZONTAL) {
				BlockPos next = current.relative(direction);

				if (isFenceBoundary(level.getBlockState(next))) {
					continue;
				}

				if (!isPenWalkableCell(level, next)) {
					return Optional.empty();
				}

				open.add(next.immutable());
			}
		}

		if (visited.size() < MIN_PEN_AREA_BLOCKS) {
			return Optional.empty();
		}

		BlockPos min = new BlockPos(minX, start.getY(), minZ);
		BlockPos max = new BlockPos(maxX, start.getY(), maxZ);
		BlockPos center = new BlockPos((minX + maxX) / 2, start.getY(), (minZ + maxZ) / 2);
		return Optional.of(new AnimalPen(min, max, center, visited.size(), 0, 0, 0));
	}

	private static Optional<ButcherTask> chooseButcherTask(
		ServerLevel level,
		SettlementState settlement,
		Villager butcher,
		Map<String, Integer> stock,
		int routeCount,
		HerdSurvey survey,
		List<AnimalPen> pens
	) {
		Optional<ButcherTask> buildPenTask = choosePenBuildTask(level, settlement, butcher, stock, survey, pens);

		if (buildPenTask.isPresent()) {
			return buildPenTask;
		}

		Optional<ButcherTask> herdTask = chooseHerdTask(level, settlement, butcher, stock, survey, pens);

		if (herdTask.isPresent()) {
			return herdTask;
		}

		Optional<ButcherTask> breedingTask = chooseBreedingTask(level, settlement, butcher, survey, stock, routeCount);

		if (breedingTask.isPresent()) {
			return breedingTask;
		}

		Optional<ButcherTask> cullingTask = chooseCullingTask(level, settlement, butcher, survey, routeCount);

		if (cullingTask.isPresent()) {
			return cullingTask;
		}

		return chooseShearingTask(level, settlement, butcher);
	}

	private static Optional<ButcherTask> choosePenBuildTask(
		ServerLevel level,
		SettlementState settlement,
		Villager butcher,
		Map<String, Integer> stock,
		HerdSurvey survey,
		List<AnimalPen> pens
	) {
		for (LivestockType livestockType : List.of(LivestockType.PIG, LivestockType.COW, LivestockType.SHEEP)) {
			Optional<PenPlan> plan = neededPenPlan(level, settlement, survey, pens, livestockType);

			if (plan.isEmpty()) {
				continue;
			}

			PenPlan penPlan = plan.get();

			if (isPenPlanBuilt(level, penPlan)) {
				continue;
			}

			PenBlock missingBlock = nearestMissingPenBlock(level, penPlan, butcher.blockPosition());

			if (missingBlock == null) {
				continue;
			}

			if (!canSupplyPenMaterial(stock, missingBlock.materialKey())) {
				continue;
			}

			return Optional.of(new ButcherTask(
				ButcherAction.BUILD_PEN,
				livestockType,
				-1,
				-1,
				missingBlock.pos(),
				missingBlock.pos(),
				missingBlock.materialKey(),
				"building_pens"
			));
		}

		return Optional.empty();
	}

	private static Optional<ButcherTask> chooseHerdTask(
		ServerLevel level,
		SettlementState settlement,
		Villager butcher,
		Map<String, Integer> stock,
		HerdSurvey survey,
		List<AnimalPen> pens
	) {
		ButcherTask bestTask = null;
		double bestDistanceSquared = Double.POSITIVE_INFINITY;

		for (LivestockType livestockType : List.of(LivestockType.PIG, LivestockType.COW, LivestockType.SHEEP)) {
			AnimalPen targetPen = usablePenForType(level, settlement, survey, pens, livestockType).orElse(null);

			if (targetPen == null || !hasHerdingFeed(stock, livestockType)) {
				continue;
			}

			for (Animal animal : survey.animals()) {
				if (!livestockType.matches(animal) || targetPen.contains(animal.blockPosition()) || isInsideAnyPen(animal.blockPosition(), pens)) {
					continue;
				}

				double distanceSquared = animal.blockPosition().distSqr(butcher.blockPosition());

				if (distanceSquared < bestDistanceSquared) {
					bestDistanceSquared = distanceSquared;
					bestTask = new ButcherTask(
						ButcherAction.HERD_ANIMAL,
						livestockType,
						animal.getId(),
						-1,
						animal.blockPosition(),
						targetPen.center(),
						"",
						"herding_animals"
					);
				}
			}
		}

		return Optional.ofNullable(bestTask);
	}

	private static Optional<ButcherTask> chooseBreedingTask(
		ServerLevel level,
		SettlementState settlement,
		Villager butcher,
		HerdSurvey survey,
		Map<String, Integer> stock,
		int routeCount
	) {
		int desiredSheep = desiredSheepCount(settlement, routeCount);
		int desiredCows = desiredCowCount(settlement, routeCount);
		int desiredPigs = desiredPigCount(settlement, routeCount);
		int sheepDeficit = Math.max(0, desiredSheep - survey.totalSheep());
		int cowDeficit = Math.max(0, desiredCows - survey.totalCows());
		int pigDeficit = Math.max(0, desiredPigs - survey.totalPigs());
		List<ScoredTask> candidates = new ArrayList<>();

		if (sheepDeficit > 0 && stock.getOrDefault("wheat", 0) >= 2) {
			findBreedingTask(level, settlement, butcher, Sheep.class, LivestockType.SHEEP, ButcherAction.BREED_SHEEP)
				.ifPresent(task -> candidates.add(new ScoredTask(task, sheepDeficit / (double) Math.max(1, desiredSheep))));
		}

		if (cowDeficit > 0 && stock.getOrDefault("wheat", 0) >= 2) {
			findBreedingTask(level, settlement, butcher, Cow.class, LivestockType.COW, ButcherAction.BREED_COW)
				.ifPresent(task -> candidates.add(new ScoredTask(task, cowDeficit / (double) Math.max(1, desiredCows))));
		}

		if (pigDeficit > 0 && availablePigFeedUnits(stock) >= 2) {
			findBreedingTask(level, settlement, butcher, Pig.class, LivestockType.PIG, ButcherAction.BREED_PIG)
				.ifPresent(task -> candidates.add(new ScoredTask(task, pigDeficit / (double) Math.max(1, desiredPigs))));
		}

		return candidates.stream()
			.sorted(Comparator
				.comparingDouble(ScoredTask::score).reversed()
				.thenComparingDouble(candidate -> candidate.task().workPos().distSqr(butcher.blockPosition())))
			.map(ScoredTask::task)
			.findFirst();
	}

	private static Optional<ButcherTask> chooseCullingTask(
		ServerLevel level,
		SettlementState settlement,
		Villager butcher,
		HerdSurvey survey,
		int routeCount
	) {
		int desiredSheep = desiredSheepCount(settlement, routeCount);
		int desiredCows = desiredCowCount(settlement, routeCount);
		int desiredPigs = desiredPigCount(settlement, routeCount);
		int sheepExcess = Math.max(0, survey.totalSheep() - desiredSheep);
		int cowExcess = Math.max(0, survey.totalCows() - desiredCows);
		int pigExcess = Math.max(0, survey.totalPigs() - desiredPigs);
		List<ScoredTask> candidates = new ArrayList<>();

		if (sheepExcess > 1) {
			findCullTask(level, settlement, butcher, Sheep.class, LivestockType.SHEEP, ButcherAction.CULL_SHEEP)
				.ifPresent(task -> candidates.add(new ScoredTask(task, sheepExcess / (double) Math.max(1, desiredSheep))));
		}

		if (cowExcess > 1) {
			findCullTask(level, settlement, butcher, Cow.class, LivestockType.COW, ButcherAction.CULL_COW)
				.ifPresent(task -> candidates.add(new ScoredTask(task, cowExcess / (double) Math.max(1, desiredCows))));
		}

		if (pigExcess > 1) {
			findCullTask(level, settlement, butcher, Pig.class, LivestockType.PIG, ButcherAction.CULL_PIG)
				.ifPresent(task -> candidates.add(new ScoredTask(task, pigExcess / (double) Math.max(1, desiredPigs))));
		}

		return candidates.stream()
			.sorted(Comparator
				.comparingDouble(ScoredTask::score).reversed()
				.thenComparingDouble(candidate -> candidate.task().workPos().distSqr(butcher.blockPosition())))
			.map(ScoredTask::task)
			.findFirst();
	}

	private static Optional<ButcherTask> chooseShearingTask(ServerLevel level, SettlementState settlement, Villager butcher) {
		int radius = SettlementVillagers.settlementRadiusBlocks(settlement);
		AABB herdBounds = new AABB(settlement.center()).inflate(radius);

		return level.getEntitiesOfClass(
			Sheep.class,
			herdBounds,
			sheep -> sheep.readyForShearing()
				&& sheep.blockPosition().distSqr(settlement.center()) <= radius * radius
		).stream()
			.min(Comparator.comparingDouble(sheep -> sheep.blockPosition().distSqr(butcher.blockPosition())))
			.map(sheep -> new ButcherTask(
				ButcherAction.SHEAR_SHEEP,
				LivestockType.SHEEP,
				sheep.getId(),
				-1,
				sheep.blockPosition(),
				sheep.blockPosition(),
				"",
				"tending_flocks"
			));
	}

	private static <T extends Animal> Optional<ButcherTask> findBreedingTask(
		ServerLevel level,
		SettlementState settlement,
		Villager butcher,
		Class<T> animalType,
		LivestockType livestockType,
		ButcherAction action
	) {
		int radius = SettlementVillagers.settlementRadiusBlocks(settlement);
		AABB herdBounds = new AABB(settlement.center()).inflate(radius);
		List<T> candidates = level.getEntitiesOfClass(
			animalType,
			herdBounds,
			animal -> animal.blockPosition().distSqr(settlement.center()) <= radius * radius && canBreed(animal)
		);
		ButcherTask bestTask = null;
		double bestScore = Double.MAX_VALUE;

		for (int i = 0; i < candidates.size(); i++) {
			T first = candidates.get(i);

			for (int j = i + 1; j < candidates.size(); j++) {
				T second = candidates.get(j);

				if (first.distanceToSqr(second) > 16.0D) {
					continue;
				}

				double score = first.blockPosition().distSqr(butcher.blockPosition()) + second.blockPosition().distSqr(butcher.blockPosition());

				if (score < bestScore) {
					bestScore = score;
					bestTask = new ButcherTask(action, livestockType, first.getId(), second.getId(), first.blockPosition(), first.blockPosition(), "", "breeding_herds");
				}
			}
		}

		return Optional.ofNullable(bestTask);
	}

	private static <T extends Animal> Optional<ButcherTask> findCullTask(
		ServerLevel level,
		SettlementState settlement,
		Villager butcher,
		Class<T> animalType,
		LivestockType livestockType,
		ButcherAction action
	) {
		int radius = SettlementVillagers.settlementRadiusBlocks(settlement);
		AABB herdBounds = new AABB(settlement.center()).inflate(radius);

		return level.getEntitiesOfClass(
			animalType,
			herdBounds,
			animal -> animal.blockPosition().distSqr(settlement.center()) <= radius * radius && canCull(animal)
		).stream()
			.min(Comparator.comparingDouble(animal -> animal.blockPosition().distSqr(butcher.blockPosition())))
			.map(animal -> new ButcherTask(action, livestockType, animal.getId(), -1, animal.blockPosition(), animal.blockPosition(), "", "culling_herds"));
	}

	private static Optional<PenPlan> neededPenPlan(
		ServerLevel level,
		SettlementState settlement,
		HerdSurvey survey,
		List<AnimalPen> pens,
		LivestockType livestockType
	) {
		List<Animal> animals = survey.animals().stream()
			.filter(livestockType::matches)
			.toList();

		if (animals.isEmpty()) {
			return Optional.empty();
		}

		List<Animal> strayAnimals = animals.stream()
			.filter(animal -> !isInsideAnyPen(animal.blockPosition(), pens))
			.toList();
		int totalCapacity = pens.stream()
			.filter(pen -> pen.matchesType(livestockType))
			.mapToInt(AnimalPen::capacity)
			.sum();

		if (strayAnimals.isEmpty() && animals.size() <= totalCapacity) {
			return Optional.empty();
		}

		int neededCapacity = Math.max(strayAnimals.size(), animals.size() - totalCapacity);
		return findPenPlan(level, settlement, strayAnimals.isEmpty() ? animals : strayAnimals, neededCapacity);
	}

	private static Optional<AnimalPen> usablePenForType(
		ServerLevel level,
		SettlementState settlement,
		HerdSurvey survey,
		List<AnimalPen> pens,
		LivestockType livestockType
	) {
		Optional<AnimalPen> existingPen = pens.stream()
			.filter(pen -> pen.matchesType(livestockType))
			.max(Comparator.comparingInt(AnimalPen::interiorArea));

		if (existingPen.isPresent()) {
			return existingPen;
		}

		Optional<PenPlan> plan = findPenPlan(
			level,
			settlement,
			survey.animals().stream().filter(livestockType::matches).toList(),
			1
		);

		if (plan.isPresent() && isPenPlanBuilt(level, plan.get())) {
			return Optional.of(plan.get().asAnimalPen());
		}

		return Optional.empty();
	}

	private static Optional<PenPlan> findPenPlan(
		ServerLevel level,
		SettlementState settlement,
		List<Animal> anchorAnimals,
		int neededCapacity
	) {
		if (anchorAnimals.isEmpty()) {
			return Optional.empty();
		}

		BlockPos anchor = averageAnimalPos(anchorAnimals);
		int interiorSize = interiorSizeForCapacity(neededCapacity + 2);

		for (int radius = 0; radius <= PEN_SITE_SEARCH_RADIUS_BLOCKS; radius += 2) {
			for (int dx = -radius; dx <= radius; dx += 2) {
				for (int dz = -radius; dz <= radius; dz += 2) {
					if (Math.abs(dx) != radius && Math.abs(dz) != radius && radius > 0) {
						continue;
					}

					int x = anchor.getX() + dx;
					int z = anchor.getZ() + dz;
					int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
					PenPlan plan = PenPlan.create(new BlockPos(x, y, z), interiorSize);

					if (isValidPenPlan(level, settlement, plan)) {
						return Optional.of(plan);
					}
				}
			}
		}

		return Optional.empty();
	}

	private static boolean isValidPenPlan(ServerLevel level, SettlementState settlement, PenPlan plan) {
		int radius = SettlementVillagers.settlementRadiusBlocks(settlement);

		for (PenBlock block : plan.boundaryBlocks()) {
			BlockPos pos = block.pos();

			if (pos.distSqr(settlement.center()) > radius * radius || !level.hasChunkAt(pos)) {
				return false;
			}

			BlockState currentState = level.getBlockState(pos);

			if (!isMatchingPenMaterial(currentState, block.materialKey()) && !SettlementConstruction.isBuildSiteReplaceable(currentState)) {
				return false;
			}

			if (!level.getBlockState(pos.below()).isSolid()) {
				return false;
			}
		}

		for (BlockPos pos : plan.interiorBlocks()) {
			if (!level.hasChunkAt(pos)
				|| !level.getBlockState(pos.below()).isSolid()
				|| !SettlementConstruction.isBuildSiteReplaceable(level.getBlockState(pos))
				|| !SettlementConstruction.isBuildSiteReplaceable(level.getBlockState(pos.above()))) {
				return false;
			}
		}

		return true;
	}

	private static boolean isPenPlanBuilt(ServerLevel level, PenPlan plan) {
		for (PenBlock block : plan.boundaryBlocks()) {
			if (!isMatchingPenMaterial(level.getBlockState(block.pos()), block.materialKey())) {
				return false;
			}
		}

		return true;
	}

	private static PenBlock nearestMissingPenBlock(ServerLevel level, PenPlan plan, BlockPos butcherPos) {
		return plan.boundaryBlocks().stream()
			.filter(block -> !isMatchingPenMaterial(level.getBlockState(block.pos()), block.materialKey()))
			.min(Comparator.comparingDouble(block -> block.pos().distSqr(butcherPos)))
			.orElse(null);
	}

	private static boolean performButcherTask(ServerLevel level, Villager butcher, ButcherTask task, Map<String, Integer> stock) {
		return switch (task.action()) {
			case BUILD_PEN -> buildPenBlock(level, task, stock);
			case HERD_ANIMAL -> herdAnimal(level, task, stock);
			case SHEAR_SHEEP -> level.getEntity(task.entityId()) instanceof Sheep sheep
				&& sheep.readyForShearing()
				&& shearSheep(level, sheep);
			case BREED_SHEEP, BREED_COW -> breedWithWheat(level, task, stock);
			case BREED_PIG -> breedWithPigFeed(level, task, stock);
			case CULL_SHEEP, CULL_COW, CULL_PIG -> cullAnimal(level, butcher, task);
		};
	}

	private static boolean buildPenBlock(ServerLevel level, ButcherTask task, Map<String, Integer> stock) {
		if (task.targetPos() == null) {
			return false;
		}

		BlockState currentState = level.getBlockState(task.targetPos());

		if (isMatchingPenMaterial(currentState, task.materialKey())) {
			return false;
		}

		if (!SettlementConstruction.isBuildSiteReplaceable(currentState)) {
			return false;
		}

		SettlementConstructionMaterials.ConstructionMaterialResult materialResult = SettlementConstructionMaterials.consumeMaterial(
			stock,
			new LinkedHashMap<>(),
			task.materialKey()
		);

		if (!materialResult.supplied()) {
			return false;
		}

		level.setBlock(task.targetPos(), plannedPenState(task), BLOCK_UPDATE_FLAGS);
		return true;
	}

	private static boolean herdAnimal(ServerLevel level, ButcherTask task, Map<String, Integer> stock) {
		if (!(level.getEntity(task.entityId()) instanceof Animal animal) || task.targetPos() == null || !hasHerdingFeed(stock, task.livestockType())) {
			return false;
		}

		animal.getNavigation().moveTo(task.targetPos().getX() + 0.5D, task.targetPos().getY(), task.targetPos().getZ() + 0.5D, 1.05D);
		return true;
	}

	private static boolean shearSheep(ServerLevel level, Sheep sheep) {
		sheep.shear(level, SoundSource.NEUTRAL, new ItemStack(Items.SHEARS));
		return true;
	}

	private static boolean breedWithWheat(ServerLevel level, ButcherTask task, Map<String, Integer> stock) {
		if (stock.getOrDefault("wheat", 0) < 2) {
			return false;
		}

		if (!canBreedPair(level, task)) {
			return false;
		}

		stock.put("wheat", stock.getOrDefault("wheat", 0) - 2);
		setPairInLove(level, task);
		return true;
	}

	private static boolean breedWithPigFeed(ServerLevel level, ButcherTask task, Map<String, Integer> stock) {
		if (availablePigFeedUnits(stock) < 2 || !canBreedPair(level, task)) {
			return false;
		}

		if (consumePigFeed(stock, 2) < 2) {
			return false;
		}

		setPairInLove(level, task);
		return true;
	}

	private static boolean canBreedPair(ServerLevel level, ButcherTask task) {
		return level.getEntity(task.entityId()) instanceof Animal first
			&& level.getEntity(task.secondaryEntityId()) instanceof Animal second
			&& first.getClass().equals(second.getClass())
			&& canBreed(first)
			&& canBreed(second)
			&& first.distanceToSqr(second) <= 25.0D;
	}

	private static void setPairInLove(ServerLevel level, ButcherTask task) {
		if (level.getEntity(task.entityId()) instanceof Animal first && level.getEntity(task.secondaryEntityId()) instanceof Animal second) {
			first.setInLove(null);
			second.setInLove(null);
		}
	}

	private static boolean canBreed(Animal animal) {
		return !animal.isBaby() && animal.getAge() == 0 && !animal.isInLove();
	}

	private static boolean cullAnimal(ServerLevel level, Villager butcher, ButcherTask task) {
		if (!(level.getEntity(task.entityId()) instanceof Animal animal) || !canCull(animal)) {
			return false;
		}

		animal.hurt(level.damageSources().mobAttack(butcher), Float.MAX_VALUE);
		return animal.isRemoved() || !animal.isAlive();
	}

	private static boolean canCull(Animal animal) {
		return !animal.isBaby() && animal.getAge() == 0 && !animal.isInLove();
	}

	private static BlockPos resolveTaskPos(ServerLevel level, ButcherTask task) {
		if (task.action() == ButcherAction.BUILD_PEN) {
			return task.targetPos();
		}

		if (level.getEntity(task.entityId()) instanceof Animal animal) {
			return animal.blockPosition();
		}

		return task.workPos();
	}

	private static boolean isPenWalkableCell(ServerLevel level, BlockPos pos) {
		return level.hasChunkAt(pos)
			&& level.getBlockState(pos.below()).isSolid()
			&& SettlementConstruction.isBuildSiteReplaceable(level.getBlockState(pos))
			&& SettlementConstruction.isBuildSiteReplaceable(level.getBlockState(pos.above()));
	}

	private static boolean isFenceBoundary(BlockState state) {
		return state.is(BlockTags.WOODEN_FENCES) || state.is(BlockTags.FENCE_GATES);
	}

	private static boolean isMatchingPenMaterial(BlockState state, String materialKey) {
		return switch (materialKey) {
			case "fence" -> state.is(BlockTags.WOODEN_FENCES);
			case "fence_gate" -> state.is(BlockTags.FENCE_GATES);
			default -> false;
		};
	}

	private static BlockState plannedPenState(ButcherTask task) {
		return switch (task.materialKey()) {
			case "fence" -> Blocks.OAK_FENCE.defaultBlockState();
			case "fence_gate" -> Blocks.OAK_FENCE_GATE.defaultBlockState()
				.setValue(FenceGateBlock.FACING, Direction.SOUTH)
				.setValue(FenceGateBlock.OPEN, true);
			default -> Blocks.AIR.defaultBlockState();
		};
	}

	private static boolean canSupplyPenMaterial(Map<String, Integer> stock, String materialKey) {
		return SettlementConstructionMaterials.canSupplyBlock(stock, SettlementBuildBlockState.pending("", '?', materialKey)).supplied();
	}

	private static boolean hasHerdingFeed(Map<String, Integer> stock, LivestockType livestockType) {
		return switch (livestockType) {
			case COW, SHEEP -> stock.getOrDefault("wheat", 0) > 0;
			case PIG -> availablePigFeedUnits(stock) > 0;
		};
	}

	private static int availablePigFeedUnits(Map<String, Integer> stock) {
		int total = 0;

		for (String goodsKey : PIG_FEED_GOODS) {
			total += stock.getOrDefault(goodsKey, 0);
		}

		return total;
	}

	private static int consumePigFeed(Map<String, Integer> stock, int amount) {
		int remaining = Math.max(0, amount);
		int consumed = 0;

		for (String goodsKey : PIG_FEED_GOODS) {
			if (remaining <= 0) {
				break;
			}

			int available = stock.getOrDefault(goodsKey, 0);
			int used = Math.min(remaining, available);

			if (used <= 0) {
				continue;
			}

			stock.put(goodsKey, available - used);
			remaining -= used;
			consumed += used;
		}

		return consumed;
	}

	private static boolean isInsideAnyPen(BlockPos pos, List<AnimalPen> pens) {
		return pens.stream().anyMatch(pen -> pen.contains(pos));
	}

	private static BlockPos averageAnimalPos(List<Animal> animals) {
		int sumX = 0;
		int sumY = 0;
		int sumZ = 0;

		for (Animal animal : animals) {
			sumX += animal.blockPosition().getX();
			sumY += animal.blockPosition().getY();
			sumZ += animal.blockPosition().getZ();
		}

		return new BlockPos(sumX / animals.size(), sumY / animals.size(), sumZ / animals.size());
	}

	private static int interiorSizeForCapacity(int neededCapacity) {
		if (neededCapacity > 12) {
			return 9;
		}

		if (neededCapacity > 6) {
			return 7;
		}

		return 5;
	}

	private static String penBoundsKey(AnimalPen pen) {
		return pen.min().getX() + ":" + pen.min().getY() + ":" + pen.min().getZ() + ":" + pen.max().getX() + ":" + pen.max().getZ();
	}

	private static int desiredSheepCount(SettlementState settlement, int routeCount) {
		int population = Math.max(1, settlement.totalPopulation());
		double exportMultiplier = exportHerdMultiplier(routeCount);
		return Math.max(4, (int) Math.ceil(population * 0.5D * exportMultiplier));
	}

	private static int desiredCowCount(SettlementState settlement, int routeCount) {
		int population = Math.max(1, settlement.totalPopulation());
		double exportMultiplier = exportHerdMultiplier(routeCount);
		return Math.max(8, (int) Math.ceil(population * exportMultiplier));
	}

	private static int desiredPigCount(SettlementState settlement, int routeCount) {
		int population = Math.max(1, settlement.totalPopulation());
		double exportMultiplier = exportHerdMultiplier(routeCount);
		return Math.max(4, (int) Math.ceil(population * 0.4D * exportMultiplier));
	}

	private static double exportHerdMultiplier(int routeCount) {
		return 1.0D + Math.min(0.5D, Math.max(0, routeCount) * 0.25D);
	}

	private static int scaledAmount(double dailyRate, double elapsedDays) {
		if (dailyRate <= 0.0D || elapsedDays <= 0.0D) {
			return 0;
		}

		return Math.max(0, (int) Math.round(dailyRate * elapsedDays));
	}

	private static void showButcherTool(Villager butcher, ButcherTask task) {
		if (!butcher.getMainHandItem().isEmpty()) {
			return;
		}

		butcher.setItemSlot(
			EquipmentSlot.MAINHAND,
			new ItemStack(switch (task.action()) {
				case BUILD_PEN -> task.materialKey().equals("fence_gate") ? Items.OAK_FENCE_GATE : Items.OAK_FENCE;
				case HERD_ANIMAL, BREED_PIG -> Items.CARROT;
				case BREED_SHEEP, BREED_COW -> Items.WHEAT;
				case SHEAR_SHEEP -> Items.SHEARS;
				case CULL_SHEEP, CULL_COW, CULL_PIG -> Items.IRON_AXE;
			})
		);
	}

	private static void steerButcherTowardTask(Villager butcher, BlockPos workPos) {
		butcher.getNavigation().moveTo(workPos.getX() + 0.5D, workPos.getY(), workPos.getZ() + 0.5D, BUTCHER_WALK_SPEED);
	}

	private static boolean isWithinWorkReach(Villager butcher, BlockPos workPos) {
		return butcher.distanceToSqr(workPos.getX() + 0.5D, workPos.getY() + 0.5D, workPos.getZ() + 0.5D) <= BUTCHER_WORK_REACH_DISTANCE_SQUARED;
	}

	private enum LivestockType {
		SHEEP(Sheep.class),
		COW(Cow.class),
		PIG(Pig.class);

		private final Class<? extends Animal> entityClass;

		LivestockType(Class<? extends Animal> entityClass) {
			this.entityClass = entityClass;
		}

		private boolean matches(Animal animal) {
			return entityClass.isInstance(animal);
		}
	}

	private enum ButcherAction {
		BUILD_PEN,
		HERD_ANIMAL,
		SHEAR_SHEEP,
		BREED_SHEEP,
		BREED_COW,
		BREED_PIG,
		CULL_SHEEP,
		CULL_COW,
		CULL_PIG
	}

	private record ButcherTask(
		ButcherAction action,
		LivestockType livestockType,
		int entityId,
		int secondaryEntityId,
		BlockPos workPos,
		BlockPos targetPos,
		String materialKey,
		String taskKey
	) {
	}

	private record TimedTask(String taskKey, long tick) {
	}

	private record HerdSurvey(
		List<Animal> animals,
		int adultCows,
		int totalCows,
		int adultSheep,
		int totalSheep,
		int adultPigs,
		int totalPigs
	) {
	}

	private record AnimalPen(
		BlockPos min,
		BlockPos max,
		BlockPos center,
		int interiorArea,
		int cows,
		int sheep,
		int pigs
	) {
		private boolean contains(BlockPos pos) {
			return pos.getY() == min.getY()
				&& pos.getX() >= min.getX()
				&& pos.getX() <= max.getX()
				&& pos.getZ() >= min.getZ()
				&& pos.getZ() <= max.getZ();
		}

		private boolean matchesType(LivestockType livestockType) {
			return switch (livestockType) {
				case COW -> cows > 0;
				case SHEEP -> sheep > 0;
				case PIG -> pigs > 0;
			};
		}

		private int capacity() {
			return Math.max(2, interiorArea / 4);
		}
	}

	private record PenPlan(BlockPos center, int interiorSize) {
		private static PenPlan create(BlockPos center, int interiorSize) {
			return new PenPlan(center.immutable(), interiorSize);
		}

		private int halfSpan() {
			return (interiorSize + 1) / 2;
		}

		private int minX() {
			return center.getX() - halfSpan();
		}

		private int maxX() {
			return center.getX() + halfSpan();
		}

		private int minZ() {
			return center.getZ() - halfSpan();
		}

		private int maxZ() {
			return center.getZ() + halfSpan();
		}

		private List<BlockPos> interiorBlocks() {
			List<BlockPos> blocks = new ArrayList<>();

			for (int x = minX() + 1; x <= maxX() - 1; x++) {
				for (int z = minZ() + 1; z <= maxZ() - 1; z++) {
					blocks.add(new BlockPos(x, center.getY(), z));
				}
			}

			return List.copyOf(blocks);
		}

		private List<PenBlock> boundaryBlocks() {
			List<PenBlock> blocks = new ArrayList<>();
			BlockPos gatePos = new BlockPos(center.getX(), center.getY(), maxZ());

			for (int x = minX(); x <= maxX(); x++) {
				blocks.add(new PenBlock(new BlockPos(x, center.getY(), minZ()), "fence"));
				blocks.add(new PenBlock(new BlockPos(x, center.getY(), maxZ()), x == center.getX() ? "fence_gate" : "fence"));
			}

			for (int z = minZ() + 1; z <= maxZ() - 1; z++) {
				blocks.add(new PenBlock(new BlockPos(minX(), center.getY(), z), "fence"));
				blocks.add(new PenBlock(new BlockPos(maxX(), center.getY(), z), "fence"));
			}

			return blocks.stream()
				.filter(block -> !block.pos().equals(gatePos) || block.materialKey().equals("fence_gate"))
				.toList();
		}

		private AnimalPen asAnimalPen() {
			BlockPos min = new BlockPos(minX() + 1, center.getY(), minZ() + 1);
			BlockPos max = new BlockPos(maxX() - 1, center.getY(), maxZ() - 1);
			return new AnimalPen(min, max, center, interiorBlocks().size(), 0, 0, 0);
		}
	}

	private record PenBlock(BlockPos pos, String materialKey) {
	}

	private record ScoredTask(ButcherTask task, double score) {
	}
}
