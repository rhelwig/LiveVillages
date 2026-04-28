package com.ronhelwig.livevillages.sim;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.ronhelwig.livevillages.LiveVillages;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;

import com.ronhelwig.livevillages.content.LiveVillagesBlocks;

public final class SettlementConstructionWork {
	private static final double CONSTRUCTION_WORK_REACH_DISTANCE_SQUARED = 25.0D;
	private static final double CONSTRUCTION_WORK_SPEED = 0.8D;
	private static final long MAX_ACTIVE_DELIVERY_AGE_TICKS = 1_200L;
	private static final long MAX_DELIVERY_AGE_TICKS = 24_000L;
	private static final int STOCK_ACCESS_SCAN_RADIUS_BLOCKS = 48;
	private static final int STOCK_ACCESS_SCAN_Y_RANGE_BLOCKS = 24;
	private static final int MAX_CONSTRUCTION_TASK_CANDIDATES_PER_SITE = 20;
	private static final int CONSTRUCTION_DELIVERY_BATCH_SIZE = 32;
	private static final long CONSTRUCTION_DECIDE_INTERVAL_TICKS = 320L;
	private static final int BLOCK_UPDATE_FLAGS = 3;

	private SettlementConstructionWork() {
	}

	public static ConstructionWorkResult maintainLoadedConstruction(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		List<SettlementBuildSite> buildSites,
		Map<String, SettlementConstructionDelivery> deliveries,
		Set<String> excludedWorkerIds
	) {
		long methodStart = System.nanoTime();
		
		if (buildSites.isEmpty()) {
			return ConstructionWorkResult.unchanged(buildSites);
		}

		long tick = level.getServer().getTickCount();
		List<SettlementBuildSite> workingBuildSites = new ArrayList<>(buildSites.size());
		boolean buildSitesChanged = false;
		Map<String, Integer> stockBeforeReconcile = new LinkedHashMap<>(stock);

		for (SettlementBuildSite buildSite : buildSites) {
			SettlementBuildSite reconciledBuildSite = reconcileBuildSiteWithWorld(level, buildSite, stock, tick);
			SettlementBuildSite refreshedBuildSite = SettlementConstruction.updateBuildSiteMaterialStatus(reconciledBuildSite, stock, tick);

			if (!refreshedBuildSite.equals(buildSite)) {
				buildSitesChanged = true;
			}

			workingBuildSites.add(refreshedBuildSite);
		}

		Set<String> excludedIds = excludedWorkerIds == null ? Set.of() : excludedWorkerIds;
		List<Villager> workers = SettlementVillagers.nearbyConstructionWorkers(level, settlement).stream()
			.filter(worker -> !excludedIds.contains(worker.getUUID().toString()))
			.toList();
			
		long entityScanTime = System.nanoTime() - methodStart;
		if (entityScanTime > 3_000_000) { // >3ms
			LiveVillages.LOGGER.warn("ConstructionWork: entity scan took {} ms for {} workers", Math.round(entityScanTime / 1_000_000.0D), workers.size());
		}
		boolean stockChanged = !stock.equals(stockBeforeReconcile);
		boolean worldChanged = false;
		boolean cleanupChanged = cleanupDeliveries(settlement, stock, workingBuildSites, deliveries, workers, excludedIds, tick);
		boolean deliveriesChanged = cleanupChanged;
		stockChanged |= cleanupChanged;
		Optional<BlockPos> stockAccessPos = SettlementStockAccess.findStockAccessPos(level, settlement, workingBuildSites);

		if (!workers.isEmpty()) {
			Set<String> claimedBlocks = claimedDeliveryBlocks(settlement, deliveries);

			for (Villager worker : workers) {
				String workerId = worker.getUUID().toString();
				SettlementConstructionDelivery delivery = deliveries.get(workerId);

				if (delivery != null && delivery.settlementId().equals(settlement.id())) {
					ConstructionTask deliveredTask = taskForDelivery(level, worker, workingBuildSites, delivery);

					if (deliveredTask != null) {
						if (isStaleActiveDelivery(delivery, deliveredTask, worker, tick)) {
							returnDeliveryToStock(stock, delivery);
							deliveries.remove(workerId);
							deliveriesChanged = true;
							stockChanged = true;
							worker.getNavigation().stop();
							continue;
						}

						claimedBlocks.add(deliveredTask.claimKey());
						steerWorkerTowardTask(worker, deliveredTask.standPos());

						if (isWithinWorkReach(worker, deliveredTask.targetPos())) {
							ConstructionActionResult actionResult = performConstructionTask(level, stock, deliveredTask, tick, true);

							if (actionResult.buildSiteChanged()) {
								workingBuildSites.set(deliveredTask.siteIndex(), actionResult.buildSite());
								buildSitesChanged = true;
							}

							if (finishDeliveryIfSatisfied(stock, deliveries, workerId, delivery, actionResult.buildSite(), tick)) {
								deliveriesChanged = true;
								stockChanged = true;
							}

							stockChanged |= actionResult.stockChanged();
							worldChanged |= actionResult.worldChanged();
						}

						continue;
					}

					returnDeliveryToStock(stock, delivery);
					deliveries.remove(workerId);
					deliveriesChanged = true;
					stockChanged = true;
				}

				if (!SettlementVillagerWorkSchedule.shouldStartNewWork(level, worker, "construction", CONSTRUCTION_DECIDE_INTERVAL_TICKS)) {
					if (SettlementVillagerWorkSchedule.isTakingBreak(level, worker)) {
						worker.getNavigation().stop();
					}

					continue;
				}

				long chooseTaskStart = System.nanoTime();
				ConstructionTask task = chooseConstructionTask(level, workingBuildSites, worker, claimedBlocks);
				long chooseTaskTime = System.nanoTime() - chooseTaskStart;
				if (chooseTaskTime > 10_000_000) { // >10ms
					LiveVillages.LOGGER.warn("ConstructionWork: chooseConstructionTask took {} ms for worker {}", Math.round(chooseTaskTime / 1_000_000.0D), workerId);
				}

				if (task == null) {
					continue;
				}

				claimedBlocks.add(task.claimKey());

				if (shouldPickUpSuppliesFirst(level, task) && stockAccessPos.isPresent()) {
					BlockPos supplyPos = stockAccessPos.get();
					steerWorkerTowardTask(worker, supplyPos);

					if (isWithinWorkReach(worker, supplyPos)) {
						DeliveryPickupResult pickupResult = pickUpConstructionDelivery(stock, deliveries, workerId, settlement, task, tick);

						if (pickupResult.buildSiteChanged()) {
							workingBuildSites.set(task.siteIndex(), pickupResult.buildSite());
							buildSitesChanged = true;
						}

						stockChanged |= pickupResult.stockChanged();
						deliveriesChanged |= pickupResult.deliveryChanged();
					}

					continue;
				}

				steerWorkerTowardTask(worker, task.standPos());

				if (!isWithinWorkReach(worker, task.targetPos())) {
					continue;
				}

				ConstructionActionResult actionResult = performConstructionTask(level, stock, task, tick, false);

				if (actionResult.buildSiteChanged()) {
					workingBuildSites.set(task.siteIndex(), actionResult.buildSite());
					buildSitesChanged = true;
				}

				stockChanged |= actionResult.stockChanged();
				worldChanged |= actionResult.worldChanged();
			}
		}

		long totalTime = System.nanoTime() - methodStart;
		if (totalTime > 15_000_000) { // >15ms
			LiveVillages.LOGGER.warn("ConstructionWork: total work took {} ms for settlement {} with {} build sites", Math.round(totalTime / 1_000_000.0D), settlement.id(), buildSites.size());
		}

		return new ConstructionWorkResult(List.copyOf(workingBuildSites), stockChanged, buildSitesChanged, worldChanged, deliveriesChanged);
	}

	private static SettlementBuildSite reconcileBuildSiteWithWorld(ServerLevel level, SettlementBuildSite buildSite, Map<String, Integer> stock, long tick) {
		List<SettlementBuildBlockState> updatedBlocks = new ArrayList<>(buildSite.blocks().size());
		Set<String> retainedPositions = new HashSet<>();
		boolean changed = false;

		for (SettlementBuildBlockState block : buildSite.blocks()) {
			Optional<BlockPos> blockPos = SettlementConstruction.buildSiteBlockPos(buildSite, block);
			BlockState plannedState = SettlementConstruction.plannedBuildSiteBlockState(buildSite, block);

			if (blockPos.isEmpty() || plannedState == null || !level.hasChunkAt(blockPos.get())) {
				updatedBlocks.add(block);
				continue;
			}

			BlockState currentState = level.getBlockState(blockPos.get());
			char currentSymbol = SettlementConstruction.currentBlueprintSymbol(buildSite, block);

			if (currentSymbol == 'A') {
				if (isCompatiblePlacedBlock(currentState, plannedState) && SettlementConstruction.tryClearBuildSiteBlock(level, blockPos.get(), stock)) {
					changed = true;
				}

				changed = true;
				continue;
			}

			if (!block.blueprintSymbol().equals(Character.toString(currentSymbol))) {
				String materialKey = SettlementConstruction.currentBlueprintMaterialKey(buildSite, block, currentSymbol);
				block = block.withBlueprintSymbol(currentSymbol, materialKey).withStatus(SettlementBuildBlockStatus.PENDING, "");
				plannedState = SettlementConstruction.plannedBuildSiteBlockState(buildSite, block);

				if (plannedState == null) {
					changed = true;
					continue;
				}

				changed = true;
			}

			boolean exactMatch = currentState.equals(plannedState);
			SettlementBuildBlockState updatedBlock = block;

			if (block.status() == SettlementBuildBlockStatus.PLACED || block.status() == SettlementBuildBlockStatus.PLAYER_PLACED) {
				if (!exactMatch) {
					updatedBlock = block.withStatus(SettlementBuildBlockStatus.PENDING, "");
				}
			} else if (exactMatch) {
				updatedBlock = block.withStatus(SettlementBuildBlockStatus.PLAYER_PLACED, "");
			} else if (block.status() == SettlementBuildBlockStatus.BLOCKED && SettlementConstruction.isBuildSiteReplaceable(currentState)) {
				updatedBlock = block.withStatus(SettlementBuildBlockStatus.PENDING, "");
			}

			if (!updatedBlock.equals(block)) {
				changed = true;
			}

			updatedBlocks.add(updatedBlock);
			retainedPositions.add(updatedBlock.position());
		}

		for (SettlementBuildBlockState currentBlueprintBlock : SettlementConstruction.currentBlueprintBlocks(buildSite)) {
			if (retainedPositions.contains(currentBlueprintBlock.position())) {
				continue;
			}

			Optional<BlockPos> blockPos = SettlementConstruction.buildSiteBlockPos(buildSite, currentBlueprintBlock);
			BlockState plannedState = SettlementConstruction.plannedBuildSiteBlockState(buildSite, currentBlueprintBlock);

			if (blockPos.isEmpty() || plannedState == null || !level.hasChunkAt(blockPos.get())) {
				continue;
			}

			BlockState currentState = level.getBlockState(blockPos.get());
			SettlementBuildBlockState addedBlock = currentState.equals(plannedState)
				? currentBlueprintBlock.withStatus(SettlementBuildBlockStatus.PLAYER_PLACED, "")
				: currentBlueprintBlock;
			updatedBlocks.add(addedBlock);
			changed = true;
		}

		return changed ? buildSite.withBlocks(updatedBlocks, isComplete(updatedBlocks), tick) : buildSite;
	}

	private static boolean cleanupDeliveries(
		SettlementState settlement,
		Map<String, Integer> stock,
		List<SettlementBuildSite> buildSites,
		Map<String, SettlementConstructionDelivery> deliveries,
		List<Villager> workers,
		Set<String> excludedWorkerIds,
		long tick
	) {
		boolean changed = false;
		Set<String> nearbyWorkerIds = new HashSet<>();

		for (Villager worker : workers) {
			nearbyWorkerIds.add(worker.getUUID().toString());
		}

		for (var iterator = deliveries.entrySet().iterator(); iterator.hasNext();) {
			Map.Entry<String, SettlementConstructionDelivery> entry = iterator.next();
			SettlementConstructionDelivery delivery = entry.getValue();

			if (!delivery.settlementId().equals(settlement.id())) {
				continue;
			}

			SettlementBuildSite buildSite = buildSiteById(buildSites, delivery.buildSiteId());
			SettlementBuildBlockState block = buildBlockByPosition(buildSite, delivery.blockPosition());
			boolean workerTemporarilyReserved = excludedWorkerIds.contains(delivery.villagerId());
			long deliveryAgeTicks = tick - delivery.pickedUpTick();
			boolean workerUnavailable = !nearbyWorkerIds.contains(delivery.villagerId());
			boolean deliveryExpired = deliveryAgeTicks > MAX_DELIVERY_AGE_TICKS
				|| (workerUnavailable && deliveryAgeTicks > MAX_ACTIVE_DELIVERY_AGE_TICKS);

			if (buildSite == null || block == null || block.status() == SettlementBuildBlockStatus.PLAYER_PLACED || deliveryExpired || workerTemporarilyReserved) {
				returnDeliveryToStock(stock, delivery);
				iterator.remove();
				changed = true;
			} else if (block.status() == SettlementBuildBlockStatus.PLACED) {
				returnDeliveryRemainderToStock(stock, delivery, delivery.amount() - 1);
				iterator.remove();
				changed = true;
			} else if (buildSite.complete()) {
				returnDeliveryToStock(stock, delivery);
				iterator.remove();
				changed = true;
			}
		}

		return changed;
	}

	private static Set<String> claimedDeliveryBlocks(SettlementState settlement, Map<String, SettlementConstructionDelivery> deliveries) {
		Set<String> claimedBlocks = new HashSet<>();

		for (SettlementConstructionDelivery delivery : deliveries.values()) {
			if (delivery.settlementId().equals(settlement.id())) {
				claimedBlocks.add(claimKey(delivery.buildSiteId(), delivery.blockPosition()));
			}
		}

		return claimedBlocks;
	}

	private static Optional<BlockPos> findStockAccessPos(ServerLevel level, SettlementState settlement, List<SettlementBuildSite> buildSites) {
		for (SettlementBuildSite buildSite : buildSites) {
			if (buildSite.blueprintId() == SettlementBuildSiteType.TRADING_POST
				&& level.hasChunkAt(buildSite.workstationPos())) {
				return stockAccessStandPos(level, buildSite.workstationPos()).or(() -> Optional.of(settlement.center()));
			}
		}

		BlockPos nearestTradeBoardAccess = null;
		double nearestDistanceSquared = Double.POSITIVE_INFINITY;
		BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();
		int minY = Math.max(level.getMinY(), settlement.center().getY() - STOCK_ACCESS_SCAN_Y_RANGE_BLOCKS);
		int maxY = Math.min(level.getMaxY() - 1, settlement.center().getY() + STOCK_ACCESS_SCAN_Y_RANGE_BLOCKS);

		for (int x = settlement.center().getX() - STOCK_ACCESS_SCAN_RADIUS_BLOCKS; x <= settlement.center().getX() + STOCK_ACCESS_SCAN_RADIUS_BLOCKS; x++) {
			for (int z = settlement.center().getZ() - STOCK_ACCESS_SCAN_RADIUS_BLOCKS; z <= settlement.center().getZ() + STOCK_ACCESS_SCAN_RADIUS_BLOCKS; z++) {
				if (!level.hasChunkAt(new BlockPos(x, settlement.center().getY(), z))) {
					continue;
				}

				for (int y = minY; y <= maxY; y++) {
					scanPos.set(x, y, z);

					if (!level.getBlockState(scanPos).is(LiveVillagesBlocks.TRADE_BOARD)) {
						continue;
					}

					Optional<BlockPos> accessPos = stockAccessStandPos(level, scanPos.immutable());

					if (accessPos.isEmpty()) {
						continue;
					}

					double distanceSquared = scanPos.distSqr(settlement.center());

					if (nearestTradeBoardAccess == null || distanceSquared < nearestDistanceSquared) {
						nearestTradeBoardAccess = accessPos.get();
						nearestDistanceSquared = distanceSquared;
					}
				}
			}
		}

		return nearestTradeBoardAccess == null ? Optional.of(settlement.center()) : Optional.of(nearestTradeBoardAccess);
	}

	private static Optional<BlockPos> stockAccessStandPos(ServerLevel level, BlockPos workstationPos) {
		BlockPos fallback = null;
		double fallbackDistanceSquared = Double.POSITIVE_INFINITY;

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos candidate = workstationPos.relative(direction);

			if (!isStandable(level, candidate)) {
				continue;
			}

			if (isRoadSurfaceForAccess(level.getBlockState(candidate.below()))) {
				return Optional.of(candidate);
			}

			double distanceSquared = candidate.distSqr(workstationPos);

			if (distanceSquared < fallbackDistanceSquared) {
				fallback = candidate;
				fallbackDistanceSquared = distanceSquared;
			}
		}

		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (Math.max(Math.abs(dx), Math.abs(dz)) != 2) {
					continue;
				}

				BlockPos candidate = workstationPos.offset(dx, 0, dz);

				if (!isStandable(level, candidate)) {
					continue;
				}

				double distanceSquared = candidate.distSqr(workstationPos);

				if (distanceSquared < fallbackDistanceSquared) {
					fallback = candidate;
					fallbackDistanceSquared = distanceSquared;
				}
			}
		}

		return Optional.ofNullable(fallback);
	}

	private static ConstructionTask taskForDelivery(
		ServerLevel level,
		Villager worker,
		List<SettlementBuildSite> buildSites,
		SettlementConstructionDelivery delivery
	) {
		for (int siteIndex = 0; siteIndex < buildSites.size(); siteIndex++) {
			SettlementBuildSite buildSite = buildSites.get(siteIndex);

			if (!buildSite.id().equals(delivery.buildSiteId())) {
				continue;
			}

			for (int blockIndex = 0; blockIndex < buildSite.blocks().size(); blockIndex++) {
				SettlementBuildBlockState block = buildSite.blocks().get(blockIndex);

				if (!block.position().equals(delivery.blockPosition())
					|| block.status() == SettlementBuildBlockStatus.PLACED
					|| block.status() == SettlementBuildBlockStatus.PLAYER_PLACED) {
					continue;
				}

				BuildRelativePos relativePos = parseRelativePos(block.position());
				Optional<BlockPos> targetPos = SettlementConstruction.buildSiteBlockPos(buildSite, block);
				BlockState plannedState = SettlementConstruction.plannedBuildSiteBlockState(buildSite, block);

				if (relativePos == null || targetPos.isEmpty() || plannedState == null || !level.hasChunkAt(targetPos.get())) {
					return null;
				}

				Optional<BlockPos> standPos = standPosFor(level, worker, buildSite, targetPos.get());

				if (standPos.isEmpty()) {
					return null;
				}

				return new ConstructionTask(
					siteIndex,
					blockIndex,
					buildSite,
					block,
					targetPos.get(),
					standPos.get(),
					relativePos.up(),
					claimKey(buildSite, block)
				);
			}
		}

		return null;
	}

	private static boolean isStaleActiveDelivery(
		SettlementConstructionDelivery delivery,
		ConstructionTask task,
		Villager worker,
		long tick
	) {
		if (tick - delivery.pickedUpTick() <= MAX_ACTIVE_DELIVERY_AGE_TICKS) {
			return false;
		}

		return !isWithinWorkReach(worker, task.targetPos());
	}

	private static DeliveryPickupResult pickUpConstructionDelivery(
		Map<String, Integer> stock,
		Map<String, SettlementConstructionDelivery> deliveries,
		String workerId,
		SettlementState settlement,
		ConstructionTask task,
		long tick
	) {
		SettlementConstructionMaterials.ConstructionMaterialResult materialResult =
			SettlementConstructionMaterials.consumeForBlock(stock, new LinkedHashMap<>(), task.block());

		if (!materialResult.supplied()) {
			return DeliveryPickupResult.blockStatusChanged(
				updateBlockStatus(task.buildSite(), task.blockIndex(), SettlementBuildBlockStatus.MISSING_MATERIAL, materialResult.missingMaterialKey(), tick)
			);
		}

		int amount = 1;

		for (; amount < CONSTRUCTION_DELIVERY_BATCH_SIZE; amount++) {
			SettlementConstructionMaterials.ConstructionMaterialResult extraMaterialResult =
				SettlementConstructionMaterials.consumeMaterial(stock, new LinkedHashMap<>(), task.block().expectedMaterialKey());

			if (!extraMaterialResult.supplied()) {
				break;
			}
		}

		deliveries.put(workerId, new SettlementConstructionDelivery(
			workerId,
			settlement.id(),
			task.buildSite().id(),
			task.block().position(),
			task.block().expectedMaterialKey(),
			amount,
			tick
		));
		return DeliveryPickupResult.pickedUp();
	}

	private static boolean finishDeliveryIfSatisfied(
		Map<String, Integer> stock,
		Map<String, SettlementConstructionDelivery> deliveries,
		String workerId,
		SettlementConstructionDelivery delivery,
		SettlementBuildSite buildSite,
		long tick
	) {
		SettlementBuildBlockState block = buildBlockByPosition(buildSite, delivery.blockPosition());

		if (block == null) {
			returnDeliveryToStock(stock, delivery);
			deliveries.remove(workerId);
			return true;
		}

		if (block.status() == SettlementBuildBlockStatus.PLACED) {
			if (delivery.amount() <= 1) {
				deliveries.remove(workerId);
				return true;
			}

			Optional<SettlementBuildBlockState> nextBlock = nextDeliveryBlock(buildSite, deliveries, workerId, delivery.materialKey());

			if (nextBlock.isEmpty()) {
				returnDeliveryRemainderToStock(stock, delivery, delivery.amount() - 1);
				deliveries.remove(workerId);
				return true;
			}

			deliveries.put(workerId, delivery.withAssignment(nextBlock.get().position(), delivery.amount() - 1, tick));
			return true;
		}

		if (block.status() == SettlementBuildBlockStatus.PLAYER_PLACED || buildSite.complete()) {
			returnDeliveryToStock(stock, delivery);
			deliveries.remove(workerId);
			return true;
		}

		return false;
	}

	private static Optional<SettlementBuildBlockState> nextDeliveryBlock(
		SettlementBuildSite buildSite,
		Map<String, SettlementConstructionDelivery> deliveries,
		String workerId,
		String materialKey
	) {
		Set<String> claimedBlocks = new HashSet<>();

		for (Map.Entry<String, SettlementConstructionDelivery> entry : deliveries.entrySet()) {
			if (entry.getKey().equals(workerId)) {
				continue;
			}

			SettlementConstructionDelivery delivery = entry.getValue();

			if (delivery.buildSiteId().equals(buildSite.id())) {
				claimedBlocks.add(delivery.blockPosition());
			}
		}

		for (SettlementBuildBlockState candidateBlock : buildSite.blocks()) {
			if (!candidateBlock.expectedMaterialKey().equals(materialKey)
				|| claimedBlocks.contains(candidateBlock.position())
				|| (candidateBlock.status() != SettlementBuildBlockStatus.PENDING && candidateBlock.status() != SettlementBuildBlockStatus.MISSING_MATERIAL)
				|| isPairedContinuation(buildSite, candidateBlock)) {
				continue;
			}

			return Optional.of(candidateBlock);
		}

		return Optional.empty();
	}

	private static boolean shouldPickUpSuppliesFirst(ServerLevel level, ConstructionTask task) {
		if (task.block().expectedMaterialKey().isBlank()) {
			return false;
		}

		BlockState plannedState = SettlementConstruction.plannedBuildSiteBlockState(task.buildSite(), task.block());

		if (plannedState == null) {
			return false;
		}

		BlockState currentState = level.getBlockState(task.targetPos());

		if (isCompatiblePlacedBlock(currentState, plannedState)) {
			return false;
		}

		if (!SettlementConstruction.isBuildSiteReplaceable(currentState)) {
			return false;
		}

		Optional<PairedBlock> pairedBlock = pairedRootBlock(task.buildSite(), task.block());

		if (pairedBlock.isEmpty()) {
			return true;
		}

		Optional<BlockPos> pairedPos = SettlementConstruction.buildSiteBlockPos(task.buildSite(), pairedBlock.get().block());
		BlockState pairedPlannedState = SettlementConstruction.plannedBuildSiteBlockState(task.buildSite(), pairedBlock.get().block());

		if (pairedPos.isEmpty() || pairedPlannedState == null || !level.hasChunkAt(pairedPos.get())) {
			return false;
		}

		BlockState pairedCurrentState = level.getBlockState(pairedPos.get());
		return isCompatiblePlacedBlock(pairedCurrentState, pairedPlannedState)
			|| SettlementConstruction.isBuildSiteReplaceable(pairedCurrentState);
	}

	private static SettlementBuildSite buildSiteById(List<SettlementBuildSite> buildSites, String buildSiteId) {
		for (SettlementBuildSite buildSite : buildSites) {
			if (buildSite.id().equals(buildSiteId)) {
				return buildSite;
			}
		}

		return null;
	}

	private static SettlementBuildBlockState buildBlockByPosition(SettlementBuildSite buildSite, String blockPosition) {
		if (buildSite == null) {
			return null;
		}

		for (SettlementBuildBlockState block : buildSite.blocks()) {
			if (block.position().equals(blockPosition)) {
				return block;
			}
		}

		return null;
	}

	private static void returnDeliveryToStock(Map<String, Integer> stock, SettlementConstructionDelivery delivery) {
		if (!delivery.materialKey().isBlank()) {
			stock.merge(delivery.materialKey(), delivery.amount(), Integer::sum);
		}
	}

	private static void returnDeliveryRemainderToStock(Map<String, Integer> stock, SettlementConstructionDelivery delivery, int amount) {
		if (!delivery.materialKey().isBlank() && amount > 0) {
			stock.merge(delivery.materialKey(), amount, Integer::sum);
		}
	}

	private static ConstructionTask chooseConstructionTask(
		ServerLevel level,
		List<SettlementBuildSite> buildSites,
		Villager worker,
		Set<String> claimedBlocks
	) {
		ConstructionTask bestTask = null;
		double bestDistanceSquared = Double.POSITIVE_INFINITY;

		for (int siteIndex = 0; siteIndex < buildSites.size(); siteIndex++) {
			SettlementBuildSite buildSite = buildSites.get(siteIndex);

			if (buildSite.complete()) {
				continue;
			}

			int candidateBlocksChecked = 0;
			for (int blockIndex = 0; blockIndex < buildSite.blocks().size(); blockIndex++) {
				SettlementBuildBlockState block = buildSite.blocks().get(blockIndex);

				if ((block.status() != SettlementBuildBlockStatus.PENDING && block.status() != SettlementBuildBlockStatus.MISSING_MATERIAL)
					|| isPairedContinuation(buildSite, block)) {
					continue;
				}

				String claimKey = claimKey(buildSite, block);

				if (claimedBlocks.contains(claimKey)) {
					continue;
				}

				BuildRelativePos relativePos = parseRelativePos(block.position());
				Optional<BlockPos> targetPos = SettlementConstruction.buildSiteBlockPos(buildSite, block);
				BlockState plannedState = SettlementConstruction.plannedBuildSiteBlockState(buildSite, block);

				if (relativePos == null || targetPos.isEmpty() || plannedState == null || !level.hasChunkAt(targetPos.get())) {
					continue;
				}

				BlockState currentState = level.getBlockState(targetPos.get());
				if (block.status() == SettlementBuildBlockStatus.MISSING_MATERIAL
					&& (SettlementConstruction.isBuildSiteReplaceable(currentState) || isCompatiblePlacedBlock(currentState, plannedState))) {
					continue;
				}

				if (isUnsupportedWallTorch(level, targetPos.get(), plannedState)) {
					continue;
				}

				candidateBlocksChecked++;

				if (candidateBlocksChecked > MAX_CONSTRUCTION_TASK_CANDIDATES_PER_SITE) {
					break;
				}

				Optional<BlockPos> standPos = nearestStandPosFor(level, worker, buildSite, targetPos.get());
				if (standPos.isEmpty()) {
					continue;
				}

				double distanceSquared = standPos.get().distSqr(worker.blockPosition());
				ConstructionTask candidateTask = new ConstructionTask(
					siteIndex,
					blockIndex,
					buildSite,
					block,
					targetPos.get(),
					standPos.get(),
					relativePos.up(),
					claimKey
				);

				if (bestTask == null
					|| candidateTask.layer() < bestTask.layer()
					|| (candidateTask.layer() == bestTask.layer() && distanceSquared < bestDistanceSquared)) {
					bestTask = candidateTask;
					bestDistanceSquared = distanceSquared;
				}
			}
		}

		if (bestTask == null) {
			return null;
		}

		long standPosStart = System.nanoTime();
		Optional<BlockPos> reachableStandPos = standPosFor(level, worker, bestTask.buildSite(), bestTask.targetPos());
		long standPosTime = System.nanoTime() - standPosStart;
		if (standPosTime > 10_000_000) { // >10ms
			LiveVillages.LOGGER.warn("ConstructionWork: final standPosFor took {} ms for block at {}", Math.round(standPosTime / 1_000_000.0D), bestTask.targetPos());
		}

		if (reachableStandPos.isEmpty()) {
			return null;
		}

		return bestTask.withStandPos(reachableStandPos.get());
	}

	private static boolean isUnsupportedWallTorch(ServerLevel level, BlockPos targetPos, BlockState plannedState) {
		return plannedState.is(Blocks.WALL_TORCH) && !plannedState.canSurvive(level, targetPos);
	}

	private static ConstructionActionResult performConstructionTask(
		ServerLevel level,
		Map<String, Integer> stock,
		ConstructionTask task,
		long tick,
		boolean hasDelivery
	) {
		if (task.blockIndex() >= task.buildSite().blocks().size()) {
			return ConstructionActionResult.noChange(task.buildSite());
		}

		SettlementBuildBlockState block = task.buildSite().blocks().get(task.blockIndex());

		if (!canWorkBlockStatus(block.status(), hasDelivery)) {
			return ConstructionActionResult.noChange(task.buildSite());
		}

		Optional<PairedBlock> pairedBlock = pairedRootBlock(task.buildSite(), block);

		if (pairedBlock.isPresent()) {
			return performPairedPlacement(level, stock, task, block, pairedBlock.get(), tick, hasDelivery);
		}

		return performSinglePlacement(level, stock, task, block, tick, hasDelivery);
	}

	private static ConstructionActionResult performSinglePlacement(
		ServerLevel level,
		Map<String, Integer> stock,
		ConstructionTask task,
		SettlementBuildBlockState block,
		long tick,
		boolean hasDelivery
	) {
		BlockState plannedState = SettlementConstruction.plannedBuildSiteBlockState(task.buildSite(), block);

		if (plannedState == null) {
			return ConstructionActionResult.noChange(task.buildSite());
		}

		BlockState currentState = level.getBlockState(task.targetPos());

		if (isProtectedWorkstationOccupyingDifferentPlannedBlock(task, block, currentState)) {
			return ConstructionActionResult.blockStatusChanged(
				updateBlockStatus(task.buildSite(), task.blockIndex(), SettlementBuildBlockStatus.PLAYER_PLACED, "", tick),
				false
			);
		}

		if (isCompatiblePlacedBlock(currentState, plannedState)) {
			boolean normalizedWorld = !currentState.equals(plannedState);

			if (normalizedWorld) {
				level.setBlock(task.targetPos(), plannedState, BLOCK_UPDATE_FLAGS);
			}

			return ConstructionActionResult.blockStatusChanged(
				updateBlockStatus(task.buildSite(), task.blockIndex(), SettlementBuildBlockStatus.PLAYER_PLACED, "", tick),
				normalizedWorld
			);
		}

		if (!SettlementConstruction.isBuildSiteReplaceable(currentState)) {
			if (shouldReplaceDirectlyToPreserveWorkstationSupport(task)
				&& SettlementConstruction.tryReplaceBuildSiteBlock(level, task.targetPos(), plannedState, stock)) {
				return ConstructionActionResult.placed(
					updateBlockStatus(task.buildSite(), task.blockIndex(), SettlementBuildBlockStatus.PLACED, "", tick),
					!hasDelivery
				);
			}

			if (SettlementConstruction.tryClearBuildSiteBlock(level, task.targetPos(), stock)) {
				return ConstructionActionResult.worldChanged(task.buildSite(), true);
			}

			return ConstructionActionResult.blockStatusChanged(
				updateBlockStatus(task.buildSite(), task.blockIndex(), SettlementBuildBlockStatus.BLOCKED, "blocked", tick),
				false
			);
		}

		SettlementConstructionMaterials.ConstructionMaterialResult materialResult = hasDelivery
			? SettlementConstructionMaterials.ConstructionMaterialResult.supplied(0)
			: SettlementConstructionMaterials.consumeForBlock(stock, new LinkedHashMap<>(), block);

		if (!materialResult.supplied()) {
			return ConstructionActionResult.blockStatusChanged(
				updateBlockStatus(task.buildSite(), task.blockIndex(), SettlementBuildBlockStatus.MISSING_MATERIAL, materialResult.missingMaterialKey(), tick),
				false
			);
		}

		level.setBlock(task.targetPos(), plannedState, BLOCK_UPDATE_FLAGS);
		return ConstructionActionResult.placed(
			updateBlockStatus(task.buildSite(), task.blockIndex(), SettlementBuildBlockStatus.PLACED, "", tick),
			!hasDelivery
		);
	}

	private static ConstructionActionResult performPairedPlacement(
		ServerLevel level,
		Map<String, Integer> stock,
		ConstructionTask task,
		SettlementBuildBlockState block,
		PairedBlock pairedBlock,
		long tick,
		boolean hasDelivery
	) {
		BlockState plannedState = SettlementConstruction.plannedBuildSiteBlockState(task.buildSite(), block);
		Optional<BlockPos> pairedPos = SettlementConstruction.buildSiteBlockPos(task.buildSite(), pairedBlock.block());
		BlockState pairedPlannedState = SettlementConstruction.plannedBuildSiteBlockState(task.buildSite(), pairedBlock.block());

		if (plannedState == null || pairedPos.isEmpty() || pairedPlannedState == null || !level.hasChunkAt(pairedPos.get())) {
			return ConstructionActionResult.noChange(task.buildSite());
		}

		BlockState currentState = level.getBlockState(task.targetPos());
		BlockState pairedCurrentState = level.getBlockState(pairedPos.get());
		boolean currentMatches = isCompatiblePlacedBlock(currentState, plannedState);
		boolean pairedMatches = isCompatiblePlacedBlock(pairedCurrentState, pairedPlannedState);

		if (currentMatches && pairedMatches) {
			if (!currentState.equals(plannedState)) {
				level.setBlock(task.targetPos(), plannedState, BLOCK_UPDATE_FLAGS);
			}

			if (!pairedCurrentState.equals(pairedPlannedState)) {
				level.setBlock(pairedPos.get(), pairedPlannedState, BLOCK_UPDATE_FLAGS);
			}

			return ConstructionActionResult.blockStatusChanged(
				updateTwoBlockStatuses(
					task.buildSite(),
					task.blockIndex(),
					SettlementBuildBlockStatus.PLAYER_PLACED,
					"",
					pairedBlock.index(),
					SettlementBuildBlockStatus.PLAYER_PLACED,
					"",
					tick
				),
				!currentState.equals(plannedState) || !pairedCurrentState.equals(pairedPlannedState)
			);
		}

		if (!currentMatches && !SettlementConstruction.isBuildSiteReplaceable(currentState)) {
			if (shouldReplaceDirectlyToPreserveWorkstationSupport(task)
				&& SettlementConstruction.tryReplaceBuildSiteBlock(level, task.targetPos(), plannedState, stock)) {
				currentMatches = true;
				currentState = plannedState;
			}
		}

		if (!currentMatches && !SettlementConstruction.isBuildSiteReplaceable(currentState)) {
			if (SettlementConstruction.tryClearBuildSiteBlock(level, task.targetPos(), stock)) {
				return ConstructionActionResult.worldChanged(task.buildSite(), true);
			}

			return ConstructionActionResult.blockStatusChanged(
				updateBlockStatus(task.buildSite(), task.blockIndex(), SettlementBuildBlockStatus.BLOCKED, "blocked", tick),
				false
			);
		}

		if (!pairedMatches && !SettlementConstruction.isBuildSiteReplaceable(pairedCurrentState)) {
			if (SettlementConstruction.tryClearBuildSiteBlock(level, pairedPos.get(), stock)) {
				return ConstructionActionResult.worldChanged(task.buildSite(), true);
			}

			return ConstructionActionResult.blockStatusChanged(
				updateBlockStatus(task.buildSite(), pairedBlock.index(), SettlementBuildBlockStatus.BLOCKED, "blocked", tick),
				false
			);
		}

		SettlementConstructionMaterials.ConstructionMaterialResult materialResult = hasDelivery
			? SettlementConstructionMaterials.ConstructionMaterialResult.supplied(0)
			: SettlementConstructionMaterials.consumeForBlock(stock, new LinkedHashMap<>(), block);

		if (!materialResult.supplied()) {
			return ConstructionActionResult.blockStatusChanged(
				updateBlockStatus(task.buildSite(), task.blockIndex(), SettlementBuildBlockStatus.MISSING_MATERIAL, materialResult.missingMaterialKey(), tick),
				false
			);
		}

		level.setBlock(task.targetPos(), plannedState, BLOCK_UPDATE_FLAGS);
		level.setBlock(pairedPos.get(), pairedPlannedState, BLOCK_UPDATE_FLAGS);
		return ConstructionActionResult.placed(
			updateTwoBlockStatuses(
				task.buildSite(),
				task.blockIndex(),
				SettlementBuildBlockStatus.PLACED,
				"",
				pairedBlock.index(),
				pairedBlock.block().status() == SettlementBuildBlockStatus.PLAYER_PLACED ? SettlementBuildBlockStatus.PLAYER_PLACED : SettlementBuildBlockStatus.PLACED,
				"",
				tick
			),
			!hasDelivery
		);
	}

	private static boolean canWorkBlockStatus(SettlementBuildBlockStatus status, boolean hasDelivery) {
		return status == SettlementBuildBlockStatus.PENDING
			|| status == SettlementBuildBlockStatus.MISSING_MATERIAL
			|| (hasDelivery && status == SettlementBuildBlockStatus.BLOCKED);
	}

	private static Optional<PairedBlock> pairedRootBlock(SettlementBuildSite buildSite, SettlementBuildBlockState block) {
		BuildRelativePos relativePos = parseRelativePos(block.position());

		if (relativePos == null || block.blueprintSymbol().isBlank()) {
			return Optional.empty();
		}

		String pairedPosition = null;
		char symbol = block.blueprintSymbol().charAt(0);

		if (symbol == 'D' && relativePos.up() == 1) {
			pairedPosition = relativePosition(relativePos.right(), relativePos.forward(), 2);
		} else if (isBedFoot(buildSite, relativePos)) {
				pairedPosition = switch (buildSite.blueprintId()) {
					case CARTOGRAPHER_HOUSE -> null;
					case CARPENTER_WORKSHOP -> relativePosition(-1, -3, 1);
					case DOCK -> null;
					case FLETCHER_HUT -> relativePosition(relativePos.right(), -3, 1);
				case FORESTER_WORKSHOP -> relativePosition(-1, -3, 1);
				case ROADWRIGHT_WORKSHOP -> relativePosition(-1, -3, 1);
				case TRADING_POST -> relativePosition(2, -1, 1);
			};
		}

		if (pairedPosition == null) {
			return Optional.empty();
		}

		for (int i = 0; i < buildSite.blocks().size(); i++) {
			SettlementBuildBlockState candidateBlock = buildSite.blocks().get(i);

			if (candidateBlock.position().equals(pairedPosition)) {
				return Optional.of(new PairedBlock(i, candidateBlock));
			}
		}

		return Optional.empty();
	}

	private static boolean isPairedContinuation(SettlementBuildSite buildSite, SettlementBuildBlockState block) {
		BuildRelativePos relativePos = parseRelativePos(block.position());

		if (relativePos == null || block.blueprintSymbol().isBlank()) {
			return false;
		}

		char symbol = block.blueprintSymbol().charAt(0);
		return (symbol == 'D' && relativePos.up() == 2) || isBedHead(buildSite, relativePos);
	}

	private static boolean isBedFoot(SettlementBuildSite buildSite, BuildRelativePos relativePos) {
		return (buildSite.blueprintId() == SettlementBuildSiteType.CARPENTER_WORKSHOP
				&& relativePos.right() == -1
				&& relativePos.forward() == -2
				&& relativePos.up() == 1)
			|| (buildSite.blueprintId() == SettlementBuildSiteType.ROADWRIGHT_WORKSHOP
				&& relativePos.right() == -1
				&& relativePos.forward() == -2
				&& relativePos.up() == 1)
			|| (buildSite.blueprintId() == SettlementBuildSiteType.FLETCHER_HUT
				&& (relativePos.right() == -1 || relativePos.right() == 1)
				&& relativePos.forward() == -2
				&& relativePos.up() == 1)
			|| (buildSite.blueprintId() == SettlementBuildSiteType.FORESTER_WORKSHOP
				&& relativePos.right() == -1
				&& relativePos.forward() == -2
				&& relativePos.up() == 1)
			|| (buildSite.blueprintId() == SettlementBuildSiteType.TRADING_POST
				&& relativePos.right() == 1
				&& relativePos.forward() == -1
				&& relativePos.up() == 1);
	}

	private static boolean isBedHead(SettlementBuildSite buildSite, BuildRelativePos relativePos) {
		return (buildSite.blueprintId() == SettlementBuildSiteType.CARPENTER_WORKSHOP
				&& relativePos.right() == -1
				&& relativePos.forward() == -3
				&& relativePos.up() == 1)
			|| (buildSite.blueprintId() == SettlementBuildSiteType.ROADWRIGHT_WORKSHOP
				&& relativePos.right() == -1
				&& relativePos.forward() == -3
				&& relativePos.up() == 1)
			|| (buildSite.blueprintId() == SettlementBuildSiteType.FLETCHER_HUT
				&& (relativePos.right() == -1 || relativePos.right() == 1)
				&& relativePos.forward() == -3
				&& relativePos.up() == 1)
			|| (buildSite.blueprintId() == SettlementBuildSiteType.FORESTER_WORKSHOP
				&& relativePos.right() == -1
				&& relativePos.forward() == -3
				&& relativePos.up() == 1)
			|| (buildSite.blueprintId() == SettlementBuildSiteType.TRADING_POST
				&& relativePos.right() == 2
				&& relativePos.forward() == -1
				&& relativePos.up() == 1);
	}

	private static SettlementBuildSite updateBlockStatus(
		SettlementBuildSite buildSite,
		int blockIndex,
		SettlementBuildBlockStatus status,
		String blocker,
		long tick
	) {
		List<SettlementBuildBlockState> updatedBlocks = new ArrayList<>(buildSite.blocks());
		updatedBlocks.set(blockIndex, updatedBlocks.get(blockIndex).withStatus(status, blocker));
		return buildSite.withBlocks(updatedBlocks, isComplete(updatedBlocks), tick);
	}

	private static SettlementBuildSite updateTwoBlockStatuses(
		SettlementBuildSite buildSite,
		int firstBlockIndex,
		SettlementBuildBlockStatus firstStatus,
		String firstBlocker,
		int secondBlockIndex,
		SettlementBuildBlockStatus secondStatus,
		String secondBlocker,
		long tick
	) {
		List<SettlementBuildBlockState> updatedBlocks = new ArrayList<>(buildSite.blocks());
		updatedBlocks.set(firstBlockIndex, updatedBlocks.get(firstBlockIndex).withStatus(firstStatus, firstBlocker));
		updatedBlocks.set(secondBlockIndex, updatedBlocks.get(secondBlockIndex).withStatus(secondStatus, secondBlocker));
		return buildSite.withBlocks(updatedBlocks, isComplete(updatedBlocks), tick);
	}

	private static boolean isComplete(List<SettlementBuildBlockState> blocks) {
		for (SettlementBuildBlockState block : blocks) {
			if (block.status() != SettlementBuildBlockStatus.PLACED && block.status() != SettlementBuildBlockStatus.PLAYER_PLACED) {
				return false;
			}
		}

		return true;
	}

	private static boolean isCompatiblePlacedBlock(BlockState currentState, BlockState plannedState) {
		if (!currentState.is(plannedState.getBlock())) {
			return false;
		}

		if (requiresExactState(currentState) || requiresExactState(plannedState)) {
			return currentState.equals(plannedState);
		}

		return true;
	}

	private static boolean requiresExactState(BlockState state) {
		return state.getBlock() instanceof BedBlock
			|| state.getBlock() instanceof DoorBlock
			|| state.getBlock() instanceof FenceBlock
			|| state.getBlock() instanceof FenceGateBlock
			|| state.getBlock() instanceof RotatedPillarBlock
			|| state.getBlock() instanceof SlabBlock
			|| state.getBlock() instanceof StairBlock
			|| state.getBlock() instanceof WallTorchBlock;
	}

	private static boolean isProtectedWorkstationOccupyingDifferentPlannedBlock(
		ConstructionTask task,
		SettlementBuildBlockState block,
		BlockState currentState
	) {
		return task.targetPos().equals(task.buildSite().workstationPos())
			&& !SettlementConstruction.isAnchoredWorkstationBlock(task.buildSite(), block)
			&& (currentState.is(LiveVillagesBlocks.TRADE_BOARD)
				|| currentState.is(LiveVillagesBlocks.CARPENTER_BENCH)
				|| currentState.is(LiveVillagesBlocks.SURVEYOR_TABLE)
				|| currentState.is(Blocks.CARTOGRAPHY_TABLE));
	}

	private static boolean shouldReplaceDirectlyToPreserveWorkstationSupport(ConstructionTask task) {
		return task.targetPos().above().equals(task.buildSite().workstationPos());
	}

	private static void steerWorkerTowardTask(Villager worker, BlockPos standPos) {
		worker.getNavigation().moveTo(standPos.getX() + 0.5D, standPos.getY(), standPos.getZ() + 0.5D, CONSTRUCTION_WORK_SPEED);
	}

	private static boolean isWithinWorkReach(Villager worker, BlockPos targetPos) {
		return worker.distanceToSqr(targetPos.getX() + 0.5D, targetPos.getY() + 0.5D, targetPos.getZ() + 0.5D) <= CONSTRUCTION_WORK_REACH_DISTANCE_SQUARED;
	}

	private static Optional<BlockPos> standPosFor(ServerLevel level, Villager worker, SettlementBuildSite buildSite, BlockPos targetPos) {
		List<BlockPos> candidates = standCandidatesFor(level, buildSite, targetPos);
		candidates.sort((a, b) -> Double.compare(a.distSqr(worker.blockPosition()), b.distSqr(worker.blockPosition())));

		// Try pathfinding for closest candidates, max 5
		for (int i = 0; i < Math.min(candidates.size(), 5); i++) {
			BlockPos candidate = candidates.get(i);
			Path path = worker.getNavigation().createPath(candidate, 0);

			if (path != null && path.canReach()) {
				return Optional.of(candidate);
			}
		}

		// Return closest candidate as fallback
		return candidates.isEmpty() ? Optional.empty() : Optional.of(candidates.get(0));
	}

	private static Optional<BlockPos> nearestStandPosFor(ServerLevel level, Villager worker, SettlementBuildSite buildSite, BlockPos targetPos) {
		List<BlockPos> candidates = standCandidatesFor(level, buildSite, targetPos);
		BlockPos workerPos = worker.blockPosition();
		BlockPos closestCandidate = null;
		double closestDistance = Double.POSITIVE_INFINITY;

		for (BlockPos candidate : candidates) {
			double distance = candidate.distSqr(workerPos);

			if (distance < closestDistance) {
				closestCandidate = candidate;
				closestDistance = distance;
			}
		}

		return Optional.ofNullable(closestCandidate);
	}

	private static List<BlockPos> standCandidatesFor(ServerLevel level, SettlementBuildSite buildSite, BlockPos targetPos) {
		List<BlockPos> candidates = new ArrayList<>();
		int minY = Math.max(level.getMinY(), Math.min(buildSite.origin().getY() + 1, targetPos.getY() - 4));
		int maxY = Math.min(level.getMaxY() - 2, Math.min(targetPos.getY(), buildSite.origin().getY() + 2));

		for (int radius = 0; radius <= 2; radius++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
						continue;
					}

					for (int y = minY; y <= maxY; y++) {
						BlockPos candidate = new BlockPos(targetPos.getX() + dx, y, targetPos.getZ() + dz);

						if (!isStandable(level, candidate) || candidate.distSqr(targetPos) > CONSTRUCTION_WORK_REACH_DISTANCE_SQUARED) {
							continue;
						}

						candidates.add(candidate);
					}
				}
			}
		}

		return candidates;
	}

	private static boolean isStandable(ServerLevel level, BlockPos pos) {
		BlockState footState = level.getBlockState(pos);
		BlockState headState = level.getBlockState(pos.above());
		BlockState belowState = level.getBlockState(pos.below());
		return footState.isAir() && headState.isAir() && !belowState.isAir();
	}

	private static boolean isRoadSurfaceForAccess(BlockState state) {
		return state.is(Blocks.DIRT_PATH)
			|| state.is(Blocks.GRAVEL)
			|| state.is(Blocks.COBBLESTONE)
			|| state.is(Blocks.STONE)
			|| state.is(Blocks.SMOOTH_STONE)
			|| state.is(Blocks.STONE_BRICKS)
			|| state.is(Blocks.BRICKS)
			|| state.getBlock() instanceof SlabBlock
			|| state.getBlock() instanceof StairBlock;
	}

	private static String claimKey(SettlementBuildSite buildSite, SettlementBuildBlockState block) {
		return claimKey(buildSite.id(), block.position());
	}

	private static String claimKey(String buildSiteId, String blockPosition) {
		return buildSiteId + ":" + blockPosition;
	}

	private static String relativePosition(int right, int forward, int up) {
		return right + "," + forward + "," + up;
	}

	private static BuildRelativePos parseRelativePos(String position) {
		String[] parts = position.split(",");

		if (parts.length != 3) {
			return null;
		}

		try {
			return new BuildRelativePos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	public record ConstructionWorkResult(
		List<SettlementBuildSite> buildSites,
		boolean stockChanged,
		boolean buildSitesChanged,
		boolean worldChanged,
		boolean deliveriesChanged
	) {
		public static ConstructionWorkResult unchanged(List<SettlementBuildSite> buildSites) {
			return new ConstructionWorkResult(List.copyOf(buildSites), false, false, false, false);
		}
	}

	private record DeliveryPickupResult(
		SettlementBuildSite buildSite,
		boolean buildSiteChanged,
		boolean stockChanged,
		boolean deliveryChanged
	) {
		private static DeliveryPickupResult pickedUp() {
			return new DeliveryPickupResult(null, false, true, true);
		}

		private static DeliveryPickupResult blockStatusChanged(SettlementBuildSite buildSite) {
			return new DeliveryPickupResult(buildSite, true, false, false);
		}
	}

	private record ConstructionTask(
		int siteIndex,
		int blockIndex,
		SettlementBuildSite buildSite,
		SettlementBuildBlockState block,
		BlockPos targetPos,
		BlockPos standPos,
		int layer,
		String claimKey
	) {
		private ConstructionTask withStandPos(BlockPos newStandPos) {
			return new ConstructionTask(siteIndex, blockIndex, buildSite, block, targetPos, newStandPos, layer, claimKey);
		}
	}

	private record ConstructionActionResult(
		SettlementBuildSite buildSite,
		boolean buildSiteChanged,
		boolean stockChanged,
		boolean worldChanged
	) {
		private static ConstructionActionResult noChange(SettlementBuildSite buildSite) {
			return new ConstructionActionResult(buildSite, false, false, false);
		}

		private static ConstructionActionResult worldChanged(SettlementBuildSite buildSite, boolean stockChanged) {
			return new ConstructionActionResult(buildSite, false, stockChanged, true);
		}

		private static ConstructionActionResult blockStatusChanged(SettlementBuildSite buildSite, boolean worldChanged) {
			return new ConstructionActionResult(buildSite, true, false, worldChanged);
		}

		private static ConstructionActionResult placed(SettlementBuildSite buildSite, boolean stockChanged) {
			return new ConstructionActionResult(buildSite, true, stockChanged, true);
		}
	}

	private record PairedBlock(int index, SettlementBuildBlockState block) {
	}

	private record BuildRelativePos(int right, int forward, int up) {
	}
}
