package com.ronhelwig.livevillages.sim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

import com.ronhelwig.livevillages.content.LiveVillagesItems;

public final class SettlementFarmerWork {
	private static final List<String> PLANTABLE_CROPS = List.of("wheat", "carrot", "potato", "beetroot");
	private static final double FARMER_WORK_REACH_DISTANCE_SQUARED = 6.25D;
	private static final double FARMER_WORK_SPEED = 0.8D;
	private static final int STANDARD_FARM_RADIUS_BLOCKS = 32;
	private static final int VILLAGE_FARM_RADIUS_BLOCKS = 48;
	private static final int SCAN_DEPTH_BLOCKS = 12;
	private static final int SCAN_HEIGHT_BLOCKS = 20;
	private static final int MAX_GARDEN_RANGE_BLOCKS = 16;
	private static final int MAX_GARDEN_PLOTS = 128;
	private static final int TERRITORY_Y_RANGE_BLOCKS = 2;
	private static final long FARMER_DECIDE_INTERVAL_TICKS = 320L;
	private static final int BLOCK_UPDATE_FLAGS = 3;

	private SettlementFarmerWork() {
	}

	public static boolean maintainLoadedGardens(ServerLevel level, SettlementState settlement, Map<String, Integer> stock) {
		int farmers = Math.max(0, settlement.population().getOrDefault("farmer", 0));

		if (farmers <= 0) {
			return false;
		}

		List<Garden> gardens = collectGardens(level, managedComposterPositions(level, settlement));

		if (gardens.isEmpty()) {
			return false;
		}

		boolean farmerTaskChanged = taskFarmers(level, settlement, stock, gardens);
		boolean worldChanged = repairGardenPlots(level, gardens);
		return farmerTaskChanged || worldChanged;
	}

	public static void applyLoadedFarmerWork(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, double elapsedDays) {
		int foodWorkers = Math.max(0, settlement.population().getOrDefault("farmer", 0));

		if (foodWorkers <= 0 || elapsedDays <= 0.0D) {
			return;
		}

		FarmSurvey survey = survey(level, settlement);
		List<Garden> gardens = collectGardens(level, survey.composterPositions());
		int activeFarmers = activeFarmers(foodWorkers, gardens.size());

		if (activeFarmers <= 0) {
			return;
		}

		tendGardens(level, settlement, stock, gardens, activeFarmers, elapsedDays);

		if (settlement.population().getOrDefault(SettlementRoleKeys.BAKER, 0) <= 0) {
			int breadBakeCapacity = scaledAmount(Math.min(
				activeFarmers * 3.0D,
				(gardens.size() * 1.5D) + (survey.hayBales() * 0.35D) + (survey.wheatCrops() * 0.05D) + 1.0D
			), elapsedDays);
			int availableWheat = stock.getOrDefault("wheat", 0);
			int breadBaked = Math.min(breadBakeCapacity, availableWheat / 3);

			if (breadBaked > 0) {
				stock.put("wheat", availableWheat - (breadBaked * 3));
				addGoods(stock, "bread", breadBaked);
			}
		}
	}

	private static FarmSurvey survey(ServerLevel level, SettlementState settlement) {
		BlockPos center = settlement.center();
		int radiusBlocks = farmRadius(settlement);
		int minY = Math.max(level.getMinY(), center.getY() - SCAN_DEPTH_BLOCKS);
		int maxY = Math.min(level.getMaxY() - 1, center.getY() + SCAN_HEIGHT_BLOCKS);
		BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();
		Set<BlockPos> composterPositions = new LinkedHashSet<>(SettlementVillagers.farmerJobSites(level, settlement));
		int hayBales = 0;
		int wheatCrops = 0;
		int carrotCrops = 0;
		int potatoCrops = 0;

		for (int x = center.getX() - radiusBlocks; x <= center.getX() + radiusBlocks; x++) {
			for (int z = center.getZ() - radiusBlocks; z <= center.getZ() + radiusBlocks; z++) {
				for (int y = minY; y <= maxY; y++) {
					scanPos.set(x, y, z);

					if (!level.isLoaded(scanPos)) {
						continue;
					}

					BlockState state = level.getBlockState(scanPos);

					if (composterPositions.isEmpty() && state.is(Blocks.COMPOSTER)) {
						composterPositions.add(scanPos.immutable());
					} else if (state.is(Blocks.HAY_BLOCK)) {
						hayBales++;
					} else if (state.is(Blocks.WHEAT)) {
						wheatCrops++;
					} else if (state.is(Blocks.CARROTS)) {
						carrotCrops++;
					} else if (state.is(Blocks.POTATOES)) {
						potatoCrops++;
					}
				}
			}
		}

		double maxDistanceSquared = (double) radiusBlocks * radiusBlocks;
		int cows = 0;
		int sheep = 0;

		for (Animal animal : level.getEntities(
			EntityTypeTest.forClass(Animal.class),
			entity -> entity.distanceToSqr(center.getX() + 0.5D, center.getY() + 0.5D, center.getZ() + 0.5D) <= maxDistanceSquared
		)) {
			if (animal.isBaby()) {
				continue;
			}

			if (animal instanceof Cow) {
				cows++;
			} else if (animal instanceof Sheep) {
				sheep++;
			}
		}

		return new FarmSurvey(List.copyOf(composterPositions), hayBales, wheatCrops, carrotCrops, potatoCrops, cows, sheep);
	}

	private static List<BlockPos> managedComposterPositions(ServerLevel level, SettlementState settlement) {
		List<BlockPos> farmerJobSites = SettlementVillagers.farmerJobSites(level, settlement);

		if (!farmerJobSites.isEmpty()) {
			return farmerJobSites;
		}

		return scanNearbyComposters(level, settlement);
	}

	private static List<BlockPos> scanNearbyComposters(ServerLevel level, SettlementState settlement) {
		BlockPos center = settlement.center();
		int radiusBlocks = farmRadius(settlement);
		int minY = Math.max(level.getMinY(), center.getY() - SCAN_DEPTH_BLOCKS);
		int maxY = Math.min(level.getMaxY() - 1, center.getY() + SCAN_HEIGHT_BLOCKS);
		BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();
		Set<BlockPos> composters = new LinkedHashSet<>();

		for (int x = center.getX() - radiusBlocks; x <= center.getX() + radiusBlocks; x++) {
			for (int z = center.getZ() - radiusBlocks; z <= center.getZ() + radiusBlocks; z++) {
				for (int y = minY; y <= maxY; y++) {
					scanPos.set(x, y, z);

					if (!level.isLoaded(scanPos) || !level.getBlockState(scanPos).is(Blocks.COMPOSTER)) {
						continue;
					}

					composters.add(scanPos.immutable());
				}
			}
		}

		return List.copyOf(composters);
	}

	private static boolean repairGardenPlots(ServerLevel level, List<Garden> gardens) {
		boolean changed = false;

		for (Garden garden : gardens) {
			Set<BlockPos> candidatePlots = new HashSet<>(garden.plots());

			for (BlockPos plotPos : garden.plots()) {
				for (Direction direction : Direction.Plane.HORIZONTAL) {
					BlockPos neighborPos = plotPos.relative(direction);

					if (isWithinGardenRange(garden.composterPos(), neighborPos)) {
						candidatePlots.add(neighborPos.immutable());
					}
				}
			}

			List<BlockPos> sortedCandidates = candidatePlots.stream()
				.sorted(Comparator.comparingInt((BlockPos pos) -> pos.getY())
					.thenComparingInt(BlockPos::getX)
					.thenComparingInt(BlockPos::getZ))
				.toList();

			for (BlockPos plotPos : sortedCandidates) {
				BlockState plotState = level.getBlockState(plotPos);
				BlockState cropState = level.getBlockState(plotPos.above());

				if (canRepairGardenPlot(plotState, cropState) && hasAdjacentGardenSupport(level, plotPos)) {
					level.setBlock(plotPos, Blocks.FARMLAND.defaultBlockState(), BLOCK_UPDATE_FLAGS);
					changed = true;
				}
			}
		}

		return changed;
	}

	private static void tendGardens(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		List<Garden> gardens,
		int activeFarmers,
		double elapsedDays
	) {
		if (gardens.isEmpty()) {
			return;
		}

		int activeGardenCount = Math.min(activeFarmers, gardens.size());
		int totalActions = Math.max(activeGardenCount, scaledAmount(activeFarmers * 8.0D, elapsedDays));
		List<Garden> activeGardens = gardens.subList(0, activeGardenCount);

		while (totalActions > 0) {
			boolean madeProgress = false;

			for (Garden garden : activeGardens) {
				if (totalActions <= 0) {
					break;
				}

				if (workGarden(level, settlement, stock, garden)) {
					totalActions--;
					madeProgress = true;
				}
			}

			if (!madeProgress) {
				return;
			}
		}
	}

	private static boolean taskFarmers(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, List<Garden> gardens) {
		List<Villager> farmers = SettlementVillagers.nearbyFarmers(level, settlement);

		if (farmers.isEmpty()) {
			return false;
		}

		boolean changed = false;
		Set<BlockPos> claimedGardens = new HashSet<>();

		for (Villager farmer : farmers) {
			if (!SettlementVillagerWorkSchedule.shouldStartNewWork(level, farmer, "farmer_garden", FARMER_DECIDE_INTERVAL_TICKS)) {
				if (SettlementVillagerWorkSchedule.isTakingBreak(level, farmer)) {
					farmer.getNavigation().stop();
				}

				continue;
			}

			Garden garden = assignGarden(level, farmer, gardens, claimedGardens);

			if (garden == null) {
				continue;
			}

			GardenTask task = chooseGardenTask(level, settlement, stock, garden, farmer.blockPosition());

			if (task == null) {
				continue;
			}

			showFarmerTool(farmer, task);
			steerFarmerTowardTask(level, settlement, farmer, task.targetPos());

			if (isWithinWorkReach(farmer, task.targetPos())) {
				Map<String, Integer> beforeStock = new HashMap<>(stock);
				if (performGardenTask(level, settlement, stock, garden, task)) {
					farmer.swing(InteractionHand.MAIN_HAND);
					SettlementProfessionReports.recordStockDeltas(
						level,
						settlement,
						SettlementRoleKeys.FARMER,
						farmer,
						beforeStock,
						stock,
						"produced"
					);
					SettlementProfessionReports.recordAccomplished(level, settlement, SettlementRoleKeys.FARMER, farmer, task.type().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' '));
					changed = true;
				}
			} else {
				SettlementProfessionDiagnostics.log(level, settlement, SettlementRoleKeys.FARMER, "moving_to_work", "villager=" + farmer.getUUID() + " task=" + task.type().name().toLowerCase(java.util.Locale.ROOT) + " target=" + task.targetPos().toShortString());
			}
		}

		return changed;
	}

	private static Garden assignGarden(ServerLevel level, Villager farmer, List<Garden> gardens, Set<BlockPos> claimedGardens) {
		BlockPos referencePos = SettlementVillagers.farmerJobSite(level, farmer).orElseGet(farmer::blockPosition);
		Garden matchedJobSiteGarden = gardenAtComposter(gardens, referencePos);

		if (matchedJobSiteGarden != null) {
			claimedGardens.add(matchedJobSiteGarden.composterPos());
			return matchedJobSiteGarden;
		}

		Garden unclaimedGarden = nearestGarden(referencePos, gardens, claimedGardens);

		if (unclaimedGarden != null) {
			claimedGardens.add(unclaimedGarden.composterPos());
			return unclaimedGarden;
		}

		return nearestGarden(referencePos, gardens, Set.of());
	}

	private static Garden gardenAtComposter(List<Garden> gardens, BlockPos composterPos) {
		for (Garden garden : gardens) {
			if (garden.composterPos().equals(composterPos)) {
				return garden;
			}
		}

		return null;
	}

	private static Garden nearestGarden(BlockPos pos, List<Garden> gardens, Set<BlockPos> excludedComposters) {
		Garden nearestGarden = null;
		double bestDistanceSquared = Double.POSITIVE_INFINITY;

		for (Garden garden : gardens) {
			if (excludedComposters.contains(garden.composterPos())) {
				continue;
			}

			double distanceSquared = garden.composterPos().distSqr(pos);

			if (nearestGarden == null || distanceSquared < bestDistanceSquared) {
				nearestGarden = garden;
				bestDistanceSquared = distanceSquared;
			}
		}

		return nearestGarden;
	}

	private static GardenTask chooseGardenTask(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Garden garden,
		BlockPos farmerPos
	) {
		GardenTask harvestTask = nearestHarvestTask(level, settlement, stock, garden, farmerPos);

		if (harvestTask != null) {
			return harvestTask;
		}

		GardenTask pickupTask = nearestCollectableItemTask(level, settlement, stock, garden, farmerPos);

		if (pickupTask != null) {
			return pickupTask;
		}

		GardenTask leafLitterBlockTask = nearestLeafLitterBlockTask(level, garden, farmerPos);

		if (leafLitterBlockTask != null) {
			return leafLitterBlockTask;
		}

		GardenTask plantTask = nearestPlantTask(level, settlement, stock, garden, farmerPos);

		if (plantTask != null) {
			return plantTask;
		}

		GardenTask composterOutputTask = readyComposterTask(level, garden);

		if (composterOutputTask != null) {
			return composterOutputTask;
		}

		GardenTask boneMealTask = nearestBoneMealTask(level, settlement, stock, garden, farmerPos);

		if (boneMealTask != null) {
			return boneMealTask;
		}

		GardenTask compostTask = compostTask(level, stock, garden);

		if (compostTask != null) {
			return compostTask;
		}

		return nearestGrassTask(level, garden, farmerPos);
	}

	private static GardenTask nearestHarvestTask(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Garden garden,
		BlockPos farmerPos
	) {
		return nearestTask(
			garden,
			farmerPos,
			plotPos -> {
				BlockPos cropPos = plotPos.above();
				BlockState cropState = level.getBlockState(cropPos);

				if (!isSupportedCrop(cropState) || !isMatureCrop(cropState)) {
					return null;
				}

				String cropKey = cropKeyForCropState(cropState);
				return new GardenTask(GardenTaskType.HARVEST, cropPos, cropKey, taskPriority(settlement, stock, cropKey) + 500, -1);
			}
		);
	}

	private static GardenTask nearestCollectableItemTask(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Garden garden,
		BlockPos farmerPos
	) {
		GardenTask bestTask = null;
		double bestDistanceSquared = Double.POSITIVE_INFINITY;

		for (ItemEntity itemEntity : level.getEntitiesOfClass(
			ItemEntity.class,
			gardenWorkBounds(garden),
			entity -> !entity.isRemoved() && isWithinGardenTerritory(garden, entity.blockPosition())
		)) {
			String goodsKey = goodsKeyForFarmerItem(itemEntity.getItem());

			if (goodsKey == null) {
				continue;
			}

			BlockPos targetPos = itemEntity.blockPosition();
			int priority = pickupPriority(settlement, stock, goodsKey);
			double distanceSquared = targetPos.distSqr(farmerPos);

			if (bestTask == null
				|| priority > bestTask.priority()
				|| (priority == bestTask.priority() && distanceSquared < bestDistanceSquared)) {
				bestTask = new GardenTask(GardenTaskType.PICKUP_ITEM, targetPos, null, priority, itemEntity.getId());
				bestDistanceSquared = distanceSquared;
			}
		}

		return bestTask;
	}

	private static GardenTask nearestLeafLitterBlockTask(ServerLevel level, Garden garden, BlockPos farmerPos) {
		GardenTask bestTask = null;
		double bestDistanceSquared = Double.POSITIVE_INFINITY;
		BlockPos composterPos = garden.composterPos();
		int minX = composterPos.getX() - MAX_GARDEN_RANGE_BLOCKS;
		int maxX = composterPos.getX() + MAX_GARDEN_RANGE_BLOCKS;
		int minY = composterPos.getY() - TERRITORY_Y_RANGE_BLOCKS;
		int maxY = composterPos.getY() + TERRITORY_Y_RANGE_BLOCKS;
		int minZ = composterPos.getZ() - MAX_GARDEN_RANGE_BLOCKS;
		int maxZ = composterPos.getZ() + MAX_GARDEN_RANGE_BLOCKS;
		BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();

		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				for (int y = minY; y <= maxY; y++) {
					scanPos.set(x, y, z);

					if (!level.hasChunkAt(scanPos) || !level.getBlockState(scanPos).is(Blocks.LEAF_LITTER)) {
						continue;
					}

					BlockPos targetPos = scanPos.immutable();
					double distanceSquared = targetPos.distSqr(farmerPos);

					if (bestTask == null || distanceSquared < bestDistanceSquared) {
						bestTask = new GardenTask(GardenTaskType.COLLECT_LEAF_LITTER_BLOCK, targetPos, null, 145, -1);
						bestDistanceSquared = distanceSquared;
					}
				}
			}
		}

		return bestTask;
	}

	private static GardenTask nearestPlantTask(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Garden garden,
		BlockPos farmerPos
	) {
		String cropKey = preferredCropKey(settlement, garden, stock);

		if (cropKey == null) {
			return null;
		}

		return nearestTask(
			garden,
			farmerPos,
			plotPos -> {
				BlockPos cropPos = plotPos.above();

				if (!level.getBlockState(cropPos).isAir() || !canPlantCrop(level.getBlockState(plotPos))) {
					return null;
				}

				return new GardenTask(GardenTaskType.PLANT, cropPos, cropKey, taskPriority(settlement, stock, cropKey) + 180, -1);
			}
		);
	}

	private static GardenTask readyComposterTask(ServerLevel level, Garden garden) {
		return isReadyComposter(level.getBlockState(garden.composterPos()))
			? new GardenTask(GardenTaskType.COLLECT_COMPOSTER_BONE_MEAL, garden.composterPos(), null, 170, -1)
			: null;
	}

	private static GardenTask nearestBoneMealTask(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Garden garden,
		BlockPos farmerPos
	) {
		if (stock.getOrDefault("bone_meal", 0) <= 0) {
			return null;
		}

		String preferredCrop = preferredCropKey(settlement, garden, stock);
		GardenTask bestTask = null;
		double bestDistanceSquared = Double.POSITIVE_INFINITY;
		int lowestAge = Integer.MAX_VALUE;
		int bestNeedPriority = Integer.MIN_VALUE;

		for (BlockPos plotPos : garden.plots()) {
			BlockPos cropPos = plotPos.above();
			BlockState cropState = level.getBlockState(cropPos);

			if (!isBonemealCandidate(cropState)) {
				continue;
			}

			String cropKey = cropKeyForCropState(cropState);
			int cropAge = cropAge(cropState);
			int needPriority = taskPriority(settlement, stock, cropKey);

			if (cropKey != null && cropKey.equals(preferredCrop)) {
				needPriority += 40;
			}

			double distanceSquared = cropPos.distSqr(farmerPos);

			if (bestTask == null
				|| cropAge < lowestAge
				|| (cropAge == lowestAge && needPriority > bestNeedPriority)
				|| (cropAge == lowestAge && needPriority == bestNeedPriority && distanceSquared < bestDistanceSquared)) {
				bestTask = new GardenTask(GardenTaskType.BONE_MEAL, cropPos, cropKey, needPriority, -1);
				lowestAge = cropAge;
				bestNeedPriority = needPriority;
				bestDistanceSquared = distanceSquared;
			}
		}

		return bestTask;
	}

	private static GardenTask compostTask(ServerLevel level, Map<String, Integer> stock, Garden garden) {
		return stock.getOrDefault("leaf_litter", 0) > 0 && canAcceptCompost(level.getBlockState(garden.composterPos()))
			? new GardenTask(GardenTaskType.COMPOST, garden.composterPos(), null, 90, -1)
			: null;
	}

	private static GardenTask nearestGrassTask(ServerLevel level, Garden garden, BlockPos farmerPos) {
		GardenTask bestTask = null;
		double bestDistanceSquared = Double.POSITIVE_INFINITY;
		BlockPos composterPos = garden.composterPos();
		int minX = composterPos.getX() - MAX_GARDEN_RANGE_BLOCKS;
		int maxX = composterPos.getX() + MAX_GARDEN_RANGE_BLOCKS;
		int minY = composterPos.getY() - TERRITORY_Y_RANGE_BLOCKS;
		int maxY = composterPos.getY() + TERRITORY_Y_RANGE_BLOCKS;
		int minZ = composterPos.getZ() - MAX_GARDEN_RANGE_BLOCKS;
		int maxZ = composterPos.getZ() + MAX_GARDEN_RANGE_BLOCKS;
		BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();

		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				for (int y = minY; y <= maxY; y++) {
					scanPos.set(x, y, z);

					if (!level.hasChunkAt(scanPos)) {
						continue;
					}

					BlockState state = level.getBlockState(scanPos);

					if (!isTrimmableGrass(state)) {
						continue;
					}

					BlockPos targetPos = scanPos.immutable();
					double distanceSquared = targetPos.distSqr(farmerPos);

					if (bestTask == null || distanceSquared < bestDistanceSquared) {
						bestTask = new GardenTask(GardenTaskType.TRIM_GRASS, targetPos, null, 60, -1);
						bestDistanceSquared = distanceSquared;
					}
				}
			}
		}

		return bestTask;
	}

	private static GardenTask nearestTask(Garden garden, BlockPos originPos, PlotTaskFactory factory) {
		GardenTask bestTask = null;
		double bestDistanceSquared = Double.POSITIVE_INFINITY;

		for (BlockPos plotPos : garden.plots()) {
			GardenTask candidate = factory.create(plotPos);

			if (candidate == null) {
				continue;
			}

			double distanceSquared = candidate.targetPos().distSqr(originPos);

			if (bestTask == null
				|| candidate.priority() > bestTask.priority()
				|| (candidate.priority() == bestTask.priority() && distanceSquared < bestDistanceSquared)) {
				bestTask = candidate;
				bestDistanceSquared = distanceSquared;
			}
		}

		return bestTask;
	}

	private static int taskPriority(SettlementState settlement, Map<String, Integer> stock, String cropKey) {
		if (cropKey == null) {
			return Integer.MIN_VALUE;
		}

		int current = stock.getOrDefault(cropKey, 0);
		int target = SettlementEconomyRules.targetForGoods(settlement, cropKey);
		return SettlementEconomyRules.shortagePriority(settlement, cropKey, current, target);
	}

	private static int pickupPriority(SettlementState settlement, Map<String, Integer> stock, String goodsKey) {
		if (goodsKey == null) {
			return Integer.MIN_VALUE;
		}

		if (goodsKey.equals("bone_meal")) {
			return 230;
		}

		if (goodsKey.equals("leaf_litter")) {
			return 140;
		}

		String cropKey = cropKeyForGoods(goodsKey);
		return taskPriority(settlement, stock, cropKey) + 220;
	}

	private static void steerFarmerTowardTask(ServerLevel level, SettlementState settlement, Villager farmer, BlockPos targetPos) {
		farmer.getLookControl().setLookAt(targetPos.getX() + 0.5D, targetPos.getY() + 0.5D, targetPos.getZ() + 0.5D);
		SettlementNavigation.moveToRoutineTarget(level, settlement, farmer, targetPos, FARMER_WORK_SPEED);
	}

	private static void showFarmerTool(Villager farmer, GardenTask task) {
		if (!farmer.getMainHandItem().isEmpty()) {
			return;
		}

		farmer.setItemSlot(
			EquipmentSlot.MAINHAND,
			new ItemStack(switch (task.type()) {
				case HARVEST, COLLECT_LEAF_LITTER_BLOCK, TRIM_GRASS -> LiveVillagesItems.SCYTHE;
				default -> Items.WOODEN_HOE;
			})
		);
	}

	private static boolean isWithinWorkReach(Villager farmer, BlockPos targetPos) {
		return farmer.distanceToSqr(targetPos.getX() + 0.5D, targetPos.getY() + 0.5D, targetPos.getZ() + 0.5D) <= FARMER_WORK_REACH_DISTANCE_SQUARED;
	}

	private static boolean performGardenTask(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Garden garden,
		GardenTask task
	) {
		BlockPos targetPos = task.targetPos();
		BlockState targetState = level.getBlockState(targetPos);

		return switch (task.type()) {
			case HARVEST -> {
				if (!isSupportedCrop(targetState) || !isMatureCrop(targetState)) {
					yield false;
				}

				harvestAndReplant(level, settlement, stock, garden, targetPos, targetState);
				yield true;
			}
			case PICKUP_ITEM -> collectItemEntity(level, stock, task.entityId(), garden);
			case COLLECT_LEAF_LITTER_BLOCK -> collectLeafLitterBlock(level, stock, targetPos, targetState, garden);
			case PLANT -> task.cropKey() != null
				&& targetState.isAir()
				&& canPlantCrop(level.getBlockState(targetPos.below()))
				&& plantCrop(level, stock, targetPos, task.cropKey());
			case COLLECT_COMPOSTER_BONE_MEAL -> collectComposterBoneMeal(level, stock, garden.composterPos());
			case BONE_MEAL -> useBonemeal(level, stock, targetPos, targetState);
			case COMPOST -> compostLeafLitter(level, stock, garden.composterPos());
			case TRIM_GRASS -> trimGrass(level, stock, targetPos, targetState);
		};
	}

	private static boolean workGarden(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, Garden garden) {
		for (BlockPos plotPos : garden.plots()) {
			BlockPos cropPos = plotPos.above();
			BlockState cropState = level.getBlockState(cropPos);

			if (!isSupportedCrop(cropState) || !isMatureCrop(cropState)) {
				continue;
			}

			harvestAndReplant(level, settlement, stock, garden, cropPos, cropState);
			return true;
		}

		String cropKey = preferredCropKey(settlement, garden, stock);

		if (cropKey == null) {
			return false;
		}

		for (BlockPos plotPos : garden.plots()) {
			BlockPos cropPos = plotPos.above();

			if (!level.getBlockState(cropPos).isAir() || !canPlantCrop(level.getBlockState(plotPos))) {
				continue;
			}

			if (plantCrop(level, stock, cropPos, cropKey)) {
				return true;
			}
		}

		return false;
	}

	private static boolean collectItemEntity(ServerLevel level, Map<String, Integer> stock, int entityId, Garden garden) {
		if (entityId < 0) {
			return false;
		}

		if (!(level.getEntity(entityId) instanceof ItemEntity itemEntity) || itemEntity.isRemoved()) {
			return false;
		}

		if (!isWithinGardenTerritory(garden, itemEntity.blockPosition())) {
			return false;
		}

		String goodsKey = goodsKeyForFarmerItem(itemEntity.getItem());

		if (goodsKey == null) {
			return false;
		}

		addGoods(stock, goodsKey, itemEntity.getItem().getCount());
		itemEntity.discard();
		return true;
	}

	private static boolean collectLeafLitterBlock(
		ServerLevel level,
		Map<String, Integer> stock,
		BlockPos blockPos,
		BlockState blockState,
		Garden garden
	) {
		if (!blockState.is(Blocks.LEAF_LITTER) || !isWithinGardenTerritory(garden, blockPos)) {
			return false;
		}

		int recovered = 0;

		for (ItemStack drop : Block.getDrops(blockState, level, blockPos, null)) {
			String goodsKey = goodsKeyForFarmerItem(drop);

			if (goodsKey != null) {
				addGoods(stock, goodsKey, drop.getCount());
				recovered += drop.getCount();
			}
		}

		if (recovered <= 0) {
			addGoods(stock, "leaf_litter", 1);
		}

		level.destroyBlock(blockPos, false);
		return true;
	}

	private static boolean collectComposterBoneMeal(ServerLevel level, Map<String, Integer> stock, BlockPos composterPos) {
		if (!isReadyComposter(level.getBlockState(composterPos))) {
			return false;
		}

		level.setBlock(composterPos, Blocks.COMPOSTER.defaultBlockState(), BLOCK_UPDATE_FLAGS);
		addGoods(stock, "bone_meal", 1);
		return true;
	}

	private static boolean useBonemeal(ServerLevel level, Map<String, Integer> stock, BlockPos cropPos, BlockState cropState) {
		if (!isBonemealCandidate(cropState)) {
			return false;
		}

		if (!(cropState.getBlock() instanceof BonemealableBlock bonemealable)) {
			return false;
		}

		if (!bonemealable.isValidBonemealTarget(level, cropPos, cropState) || !bonemealable.isBonemealSuccess(level, level.getRandom(), cropPos, cropState)) {
			return false;
		}

		if (!consumeGoods(stock, "bone_meal", 1)) {
			return false;
		}

		bonemealable.performBonemeal(level, level.getRandom(), cropPos, cropState);
		level.levelEvent(1505, cropPos, 0);
		return true;
	}

	private static boolean compostLeafLitter(ServerLevel level, Map<String, Integer> stock, BlockPos composterPos) {
		BlockState composterState = level.getBlockState(composterPos);

		if (!canAcceptCompost(composterState) || !consumeGoods(stock, "leaf_litter", 1)) {
			return false;
		}

		ComposterBlock.insertItem(null, composterState, level, new ItemStack(Items.LEAF_LITTER), composterPos);
		return true;
	}

	private static boolean trimGrass(ServerLevel level, Map<String, Integer> stock, BlockPos grassPos, BlockState grassState) {
		BlockPos rootPos = lowerTrimmableGrassPos(grassPos, grassState);
		BlockState rootState = level.getBlockState(rootPos);

		if (!isTrimmableGrass(rootState)) {
			return false;
		}

		for (ItemStack drop : Block.getDrops(rootState, level, rootPos, null)) {
			String goodsKey = goodsKeyForFarmerItem(drop);

			if (goodsKey != null) {
				addGoods(stock, goodsKey, drop.getCount());
			}
		}

		level.destroyBlock(rootPos, false);

		if (shouldGenerateLeafLitter(level, rootState)) {
			if (!placeLeafLitterBlock(level, rootPos)) {
				addGoods(stock, "leaf_litter", 1);
			}
		}

		return true;
	}

	private static void harvestAndReplant(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Garden garden,
		BlockPos cropPos,
		BlockState cropState
	) {
		for (ItemStack drop : Block.getDrops(cropState, level, cropPos, null)) {
			String goodsKey = goodsKeyForFarmerItem(drop);

			if (goodsKey != null) {
				addGoods(stock, goodsKey, drop.getCount());
			}
		}

		if (cropState.is(Blocks.WHEAT)) {
			addGoods(stock, "leaf_litter", 1);
		}

		level.setBlock(cropPos, Blocks.AIR.defaultBlockState(), BLOCK_UPDATE_FLAGS);
		level.levelEvent(2001, cropPos, Block.getId(cropState));

		String cropKey = preferredCropKey(settlement, garden, stock);

		if (cropKey != null) {
			plantCrop(level, stock, cropPos, cropKey);
		}
	}

	private static boolean plantCrop(ServerLevel level, Map<String, Integer> stock, BlockPos cropPos, String cropKey) {
		if (cropKey.equals("wheat")) {
			if (!consumeGoods(stock, "wheat_seeds", 1)) {
				return false;
			}

			level.setBlock(cropPos, Blocks.WHEAT.defaultBlockState(), BLOCK_UPDATE_FLAGS);
			return true;
		}

		if (cropKey.equals("carrot")) {
			if (!consumeGoods(stock, "carrot", 1)) {
				return false;
			}

			level.setBlock(cropPos, Blocks.CARROTS.defaultBlockState(), BLOCK_UPDATE_FLAGS);
			return true;
		}

		if (cropKey.equals("potato")) {
			if (!consumeGoods(stock, "potato", 1)) {
				return false;
			}

			level.setBlock(cropPos, Blocks.POTATOES.defaultBlockState(), BLOCK_UPDATE_FLAGS);
			return true;
		}

		if (cropKey.equals("beetroot")) {
			if (!consumeGoods(stock, "beetroot_seeds", 1)) {
				return false;
			}

			level.setBlock(cropPos, Blocks.BEETROOTS.defaultBlockState(), BLOCK_UPDATE_FLAGS);
			return true;
		}

		return false;
	}

	private static String preferredCropKey(SettlementState settlement, Garden garden, Map<String, Integer> stock) {
		String preferredCrop = null;
		int bestPriority = Integer.MIN_VALUE;
		int bestGardenPresence = Integer.MIN_VALUE;
		int bestAvailableSeed = Integer.MIN_VALUE;

		for (String cropKey : PLANTABLE_CROPS) {
			int availableSeed = availablePlantingStock(stock, cropKey);

			if (availableSeed <= 0) {
				continue;
			}

			int priority = taskPriority(settlement, stock, cropKey);
			int gardenPresence = garden.plantedCropCount(cropKey);

			if (preferredCrop == null
				|| priority > bestPriority
				|| (priority == bestPriority && gardenPresence > bestGardenPresence)
				|| (priority == bestPriority && gardenPresence == bestGardenPresence && availableSeed > bestAvailableSeed)) {
				preferredCrop = cropKey;
				bestPriority = priority;
				bestGardenPresence = gardenPresence;
				bestAvailableSeed = availableSeed;
			}
		}

		return preferredCrop;
	}

	private static int availablePlantingStock(Map<String, Integer> stock, String cropKey) {
		if (cropKey.equals("wheat")) {
			return stock.getOrDefault("wheat_seeds", 0);
		}

		if (cropKey.equals("beetroot")) {
			return stock.getOrDefault("beetroot_seeds", 0);
		}

		return stock.getOrDefault(cropKey, 0);
	}

	private static List<Garden> collectGardens(ServerLevel level, List<BlockPos> composterPositions) {
		if (composterPositions.isEmpty()) {
			return List.of();
		}

		List<BlockPos> sortedComposters = composterPositions.stream()
			.map(BlockPos::immutable)
			.sorted(Comparator.comparingInt((BlockPos pos) -> pos.getX())
				.thenComparingInt(pos -> pos.getY())
				.thenComparingInt(pos -> pos.getZ()))
			.toList();
		Set<BlockPos> claimedPlots = new HashSet<>();
		List<Garden> gardens = new ArrayList<>();

		for (BlockPos composterPos : sortedComposters) {
			gardens.add(collectGarden(level, composterPos, sortedComposters, claimedPlots));
		}

		gardens.sort(Comparator.comparingInt(Garden::plotCount).reversed());
		return gardens;
	}

	private static Garden collectGarden(
		ServerLevel level,
		BlockPos composterPos,
		List<BlockPos> allComposters,
		Set<BlockPos> claimedPlots
	) {
		Set<BlockPos> gardenPlots = new HashSet<>();

		for (int offsetX = -MAX_GARDEN_RANGE_BLOCKS; offsetX <= MAX_GARDEN_RANGE_BLOCKS; offsetX++) {
			for (int offsetZ = -MAX_GARDEN_RANGE_BLOCKS; offsetZ <= MAX_GARDEN_RANGE_BLOCKS; offsetZ++) {
				for (int offsetY = -TERRITORY_Y_RANGE_BLOCKS; offsetY <= TERRITORY_Y_RANGE_BLOCKS; offsetY++) {
					BlockPos plotPos = composterPos.offset(offsetX, offsetY, offsetZ);

					if (gardenPlots.size() >= MAX_GARDEN_PLOTS
						|| claimedPlots.contains(plotPos)
						|| !isGardenPlot(level, plotPos)
						|| !isWithinGardenRange(composterPos, plotPos)
						|| !isClosestManagedComposter(plotPos, composterPos, allComposters)) {
						continue;
					}

					gardenPlots.add(plotPos.immutable());
					claimedPlots.add(plotPos.immutable());
				}
			}
		}

		return createGarden(level, composterPos, gardenPlots);
	}

	private static boolean isClosestManagedComposter(BlockPos plotPos, BlockPos composterPos, List<BlockPos> allComposters) {
		double bestDistanceSquared = Double.POSITIVE_INFINITY;
		BlockPos bestComposter = null;

		for (BlockPos candidatePos : allComposters) {
			double distanceSquared = candidatePos.distSqr(plotPos);

			if (bestComposter == null
				|| distanceSquared < bestDistanceSquared
				|| (distanceSquared == bestDistanceSquared && compareBlockPos(candidatePos, bestComposter) < 0)) {
				bestComposter = candidatePos;
				bestDistanceSquared = distanceSquared;
			}
		}

		return composterPos.equals(bestComposter);
	}

	private static int compareBlockPos(BlockPos left, BlockPos right) {
		int comparedX = Integer.compare(left.getX(), right.getX());

		if (comparedX != 0) {
			return comparedX;
		}

		int comparedY = Integer.compare(left.getY(), right.getY());

		if (comparedY != 0) {
			return comparedY;
		}

		return Integer.compare(left.getZ(), right.getZ());
	}

	private static Garden createGarden(ServerLevel level, BlockPos composterPos, Set<BlockPos> gardenPlots) {
		List<BlockPos> sortedPlots = gardenPlots.stream()
			.sorted(Comparator.comparingInt((BlockPos pos) -> pos.getY())
				.thenComparingInt(BlockPos::getX)
				.thenComparingInt(BlockPos::getZ))
			.toList();
		int wheatPlots = 0;
		int carrotPlots = 0;
		int potatoPlots = 0;
		int beetrootPlots = 0;

		for (BlockPos plotPos : sortedPlots) {
			BlockState cropState = level.getBlockState(plotPos.above());

			if (cropState.is(Blocks.WHEAT)) {
				wheatPlots++;
			} else if (cropState.is(Blocks.CARROTS)) {
				carrotPlots++;
			} else if (cropState.is(Blocks.POTATOES)) {
				potatoPlots++;
			} else if (cropState.is(Blocks.BEETROOTS)) {
				beetrootPlots++;
			}
		}

		return new Garden(composterPos, sortedPlots, wheatPlots, carrotPlots, potatoPlots, beetrootPlots);
	}

	private static boolean isWithinGardenRange(BlockPos composterPos, BlockPos plotPos) {
		return Math.abs(composterPos.getX() - plotPos.getX()) <= MAX_GARDEN_RANGE_BLOCKS
			&& Math.abs(composterPos.getY() - plotPos.getY()) <= TERRITORY_Y_RANGE_BLOCKS
			&& Math.abs(composterPos.getZ() - plotPos.getZ()) <= MAX_GARDEN_RANGE_BLOCKS;
	}

	private static boolean isGardenPlot(ServerLevel level, BlockPos plotPos) {
		return level.hasChunkAt(plotPos) && canPlantCrop(level.getBlockState(plotPos));
	}

	private static AABB gardenWorkBounds(Garden garden) {
		BlockPos composterPos = garden.composterPos();
		return new AABB(
			composterPos.getX() - MAX_GARDEN_RANGE_BLOCKS - 0.5D,
			composterPos.getY() - TERRITORY_Y_RANGE_BLOCKS - 1.0D,
			composterPos.getZ() - MAX_GARDEN_RANGE_BLOCKS - 0.5D,
			composterPos.getX() + MAX_GARDEN_RANGE_BLOCKS + 1.5D,
			composterPos.getY() + TERRITORY_Y_RANGE_BLOCKS + 2.5D,
			composterPos.getZ() + MAX_GARDEN_RANGE_BLOCKS + 1.5D
		);
	}

	private static boolean isWithinGardenTerritory(Garden garden, BlockPos pos) {
		return isWithinGardenRange(garden.composterPos(), pos);
	}

	private static boolean canPlantCrop(BlockState plotState) {
		return plotState.is(Blocks.FARMLAND);
	}

	private static boolean canRepairGardenPlot(BlockState plotState, BlockState cropState) {
		return cropState.isAir() && (
			plotState.is(Blocks.DIRT)
				|| plotState.is(Blocks.GRASS_BLOCK)
				|| plotState.is(Blocks.COARSE_DIRT)
				|| plotState.is(Blocks.ROOTED_DIRT)
		);
	}

	private static boolean hasAdjacentGardenSupport(ServerLevel level, BlockPos pos) {
		int supportCount = 0;

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos neighborPos = pos.relative(direction);
			BlockState neighborPlotState = level.getBlockState(neighborPos);
			BlockState neighborCropState = level.getBlockState(neighborPos.above());

			if (canPlantCrop(neighborPlotState) || isSupportedCrop(neighborCropState)) {
				supportCount++;
			}
		}

		return supportCount >= 2;
	}

	private static boolean isSupportedCrop(BlockState cropState) {
		return cropState.is(Blocks.WHEAT)
			|| cropState.is(Blocks.CARROTS)
			|| cropState.is(Blocks.POTATOES)
			|| cropState.is(Blocks.BEETROOTS);
	}

	private static boolean isBonemealCandidate(BlockState cropState) {
		return isSupportedCrop(cropState) && !isMatureCrop(cropState);
	}

	private static boolean isMatureCrop(BlockState cropState) {
		return cropState.getBlock() instanceof CropBlock cropBlock && cropBlock.isMaxAge(cropState);
	}

	private static int cropAge(BlockState cropState) {
		return cropState.getBlock() instanceof CropBlock cropBlock ? cropBlock.getAge(cropState) : Integer.MAX_VALUE;
	}

	private static boolean isReadyComposter(BlockState composterState) {
		return composterState.is(Blocks.COMPOSTER)
			&& composterState.hasProperty(ComposterBlock.LEVEL)
			&& composterState.getValue(ComposterBlock.LEVEL) >= ComposterBlock.READY;
	}

	private static boolean canAcceptCompost(BlockState composterState) {
		return composterState.is(Blocks.COMPOSTER)
			&& composterState.hasProperty(ComposterBlock.LEVEL)
			&& composterState.getValue(ComposterBlock.LEVEL) < ComposterBlock.MAX_LEVEL;
	}

	private static boolean isTrimmableGrass(BlockState state) {
		return state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS);
	}

	private static BlockPos lowerTrimmableGrassPos(BlockPos grassPos, BlockState grassState) {
		if (grassState.is(Blocks.TALL_GRASS)
			&& grassState.hasProperty(DoublePlantBlock.HALF)
			&& grassState.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER) {
			return grassPos.below();
		}

		return grassPos;
	}

	private static boolean shouldGenerateLeafLitter(ServerLevel level, BlockState grassState) {
		return grassState.is(Blocks.TALL_GRASS) || level.getRandom().nextFloat() < 0.60F;
	}

	private static boolean placeLeafLitterBlock(ServerLevel level, BlockPos blockPos) {
		BlockState litterState = Blocks.LEAF_LITTER.defaultBlockState();
		BlockState currentState = level.getBlockState(blockPos);

		if ((!currentState.isAir() && !currentState.canBeReplaced()) || !litterState.canSurvive(level, blockPos)) {
			return false;
		}

		level.setBlock(blockPos, litterState, BLOCK_UPDATE_FLAGS);
		return true;
	}

	private static String cropKeyForCropState(BlockState cropState) {
		if (cropState.is(Blocks.WHEAT)) {
			return "wheat";
		}

		if (cropState.is(Blocks.CARROTS)) {
			return "carrot";
		}

		if (cropState.is(Blocks.POTATOES)) {
			return "potato";
		}

		if (cropState.is(Blocks.BEETROOTS)) {
			return "beetroot";
		}

		return null;
	}

	private static String cropKeyForGoods(String goodsKey) {
		return switch (goodsKey) {
			case "wheat", "wheat_seeds" -> "wheat";
			case "carrot" -> "carrot";
			case "potato" -> "potato";
			case "beetroot", "beetroot_seeds" -> "beetroot";
			default -> null;
		};
	}

	private static String goodsKeyForFarmerItem(ItemStack stack) {
		if (stack.is(Items.WHEAT)) {
			return "wheat";
		}

		if (stack.is(Items.WHEAT_SEEDS)) {
			return "wheat_seeds";
		}

		if (stack.is(Items.CARROT)) {
			return "carrot";
		}

		if (stack.is(Items.POTATO)) {
			return "potato";
		}

		if (stack.is(Items.BEETROOT)) {
			return "beetroot";
		}

		if (stack.is(Items.BEETROOT_SEEDS)) {
			return "beetroot_seeds";
		}

		if (stack.is(Items.BONE_MEAL)) {
			return "bone_meal";
		}

		if (stack.is(Items.LEAF_LITTER)) {
			return "leaf_litter";
		}

		return null;
	}

	private static boolean consumeGoods(Map<String, Integer> goods, String goodsKey, int amount) {
		int available = goods.getOrDefault(goodsKey, 0);

		if (available < amount) {
			return false;
		}

		goods.put(goodsKey, available - amount);
		return true;
	}

	private static int activeFarmers(int foodWorkers, int gardens) {
		if (foodWorkers <= 0) {
			return 0;
		}

		if (gardens <= 0) {
			return Math.min(foodWorkers, 1);
		}

		return Math.min(foodWorkers, gardens);
	}

	private static int farmRadius(SettlementState settlement) {
		return switch (settlement.kind()) {
			case VILLAGE, HARBOR -> VILLAGE_FARM_RADIUS_BLOCKS;
			case CUSTOM, OUTPOST -> STANDARD_FARM_RADIUS_BLOCKS;
		};
	}

	private static int scaledAmount(double dailyRate, double elapsedDays) {
		return Math.max(0, (int) Math.round(dailyRate * elapsedDays));
	}

	private static void addGoods(Map<String, Integer> goods, String goodsKey, int amount) {
		if (amount <= 0) {
			return;
		}

		goods.merge(goodsKey, amount, Integer::sum);
	}

	private record FarmSurvey(
		List<BlockPos> composterPositions,
		int hayBales,
		int wheatCrops,
		int carrotCrops,
		int potatoCrops,
		int cows,
		int sheep
	) {
	}

	private record Garden(
		BlockPos composterPos,
		List<BlockPos> plots,
		int wheatPlots,
		int carrotPlots,
		int potatoPlots,
		int beetrootPlots
	) {
		private int plotCount() {
			return plots.size();
		}

		private int plantedCropCount(String cropKey) {
			return switch (cropKey) {
				case "wheat" -> wheatPlots;
				case "carrot" -> carrotPlots;
				case "potato" -> potatoPlots;
				case "beetroot" -> beetrootPlots;
				default -> 0;
			};
		}
	}

	private record GardenTask(GardenTaskType type, BlockPos targetPos, String cropKey, int priority, int entityId) {
	}

	private enum GardenTaskType {
		HARVEST,
		PICKUP_ITEM,
		COLLECT_LEAF_LITTER_BLOCK,
		PLANT,
		COLLECT_COMPOSTER_BONE_MEAL,
		BONE_MEAL,
		COMPOST,
		TRIM_GRASS
	}

	@FunctionalInterface
	private interface PlotTaskFactory {
		GardenTask create(BlockPos plotPos);
	}
}
