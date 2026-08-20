package com.ronhelwig.livevillages.sim;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import com.ronhelwig.livevillages.mixin.StructureTemplateAccessor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/** Conservative, bounded maintenance for generated vanilla village buildings. */
public final class SettlementVanillaVillageRepairWork {
	private static final int SCAN_RADIUS_BLOCKS = 48;
	private static final int SCAN_BELOW_BLOCKS = 12;
	private static final int SCAN_ABOVE_BLOCKS = 20;
	private static final int COLUMNS_PER_PASS = 48;
	private static final int TEMPLATE_COLUMNS_PER_PASS = 16;
	private static final int MAX_RENOVATION_WORKERS_PER_PASS = 6;
	private static final double WORK_REACH_DISTANCE_SQUARED = 9.0D;
	private static final double WALK_SPEED = 0.7D;
	private static final int BLOCK_UPDATE_FLAGS = 3;
	private static final Map<String, Integer> CLEANUP_CURSORS = new HashMap<>();
	private static final Map<String, Integer> STRUCTURE_REPAIR_CURSORS = new HashMap<>();
	private static final Map<String, Integer> REPAIR_CURSORS = new HashMap<>();
	private static final Map<String, Map<String, MaintenanceTask>> ACTIVE_TASKS = new HashMap<>();

	private SettlementVanillaVillageRepairWork() {
	}

	public static MaintenanceResult maintain(ServerLevel level, SettlementState settlement, Map<String, Integer> stock) {
		if (!SettlementVillagerWorkSchedule.isProfessionWorkTime(level)) {
			return MaintenanceResult.unchanged();
		}

		List<Villager> workers = SettlementVillagers.nearbyConstructionWorkers(level, settlement).stream()
			.filter(villager -> !villager.isSleeping())
			.toList();
		if (workers.isEmpty()) {
			return MaintenanceResult.unchanged();
		}

		Map<String, Integer> stockBefore = new LinkedHashMap<>(stock);
		Map<String, MaintenanceTask> assignments = ACTIVE_TASKS.computeIfAbsent(settlement.id(), ignored -> new LinkedHashMap<>());
		Set<String> availableWorkerIds = workers.stream().map(worker -> worker.getUUID().toString()).collect(java.util.stream.Collectors.toSet());
		assignments.entrySet().removeIf(entry -> !availableWorkerIds.contains(entry.getKey()) || !taskStillAvailable(level, entry.getValue()));
		Set<BlockPos> claimedTargets = assignments.values().stream().map(MaintenanceTask::targetPos).collect(java.util.stream.Collectors.toSet());
		Set<String> busyWorkerIds = new HashSet<>();
		boolean worldChanged = false;
		int processedWorkers = 0;

		for (Villager worker : workers) {
			String workerId = worker.getUUID().toString();
			MaintenanceTask task = assignments.get(workerId);
			if (task == null || processedWorkers >= MAX_RENOVATION_WORKERS_PER_PASS) {
				continue;
			}
			processedWorkers++;
			busyWorkerIds.add(workerId);
			TaskProgress progress = progressTask(level, settlement, worker, task, stock);
			worldChanged |= progress.worldChanged();
			if (progress.finished()) {
				assignments.remove(workerId);
				claimedTargets.remove(task.targetPos());
			}
		}

		while (processedWorkers < Math.min(MAX_RENOVATION_WORKERS_PER_PASS, workers.size())) {
			MaintenanceTask task = chooseTask(level, settlement, stock, claimedTargets);
			if (task == null) {
				break;
			}
			Villager worker = workers.stream()
				.filter(candidate -> !busyWorkerIds.contains(candidate.getUUID().toString()))
				.min(Comparator.comparingDouble(candidate -> candidate.blockPosition().distSqr(task.targetPos())))
				.orElse(null);
			if (worker == null) {
				break;
			}
			String workerId = worker.getUUID().toString();
			assignments.put(workerId, task);
			claimedTargets.add(task.targetPos());
			busyWorkerIds.add(workerId);
			processedWorkers++;
			TaskProgress progress = progressTask(level, settlement, worker, task, stock);
			worldChanged |= progress.worldChanged();
			if (progress.finished()) {
				assignments.remove(workerId);
				claimedTargets.remove(task.targetPos());
			}
		}

		if (assignments.isEmpty()) {
			ACTIVE_TASKS.remove(settlement.id());
		}
		return new MaintenanceResult(worldChanged, !stock.equals(stockBefore), Set.copyOf(busyWorkerIds));
	}

	private static MaintenanceTask chooseTask(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, Set<BlockPos> claimedTargets) {
		MaintenanceTask cleanup = nextUnclaimed(() -> scanForCobweb(level, settlement), claimedTargets);
		if (cleanup != null) {
			return cleanup;
		}
		if (needsHousingRepair(settlement)) {
			MaintenanceTask housing = nextUnclaimed(() -> scanForFunctionalRepair(level, settlement, stock), claimedTargets);
			if (housing != null) {
				return housing;
			}
		}
		return nextUnclaimed(() -> scanForVanillaTemplateRepair(level, settlement, stock), claimedTargets);
	}

	private static MaintenanceTask nextUnclaimed(Supplier<MaintenanceTask> supplier, Set<BlockPos> claimedTargets) {
		for (int attempt = 0; attempt < MAX_RENOVATION_WORKERS_PER_PASS; attempt++) {
			MaintenanceTask task = supplier.get();
			if (task == null || !claimedTargets.contains(task.targetPos())) {
				return task;
			}
		}
		return null;
	}

	private static TaskProgress progressTask(
		ServerLevel level,
		SettlementState settlement,
		Villager worker,
		MaintenanceTask task,
		Map<String, Integer> stock
	) {
		if (!taskStillAvailable(level, task)) {
			return new TaskProgress(true, false);
		}
		if (worker.blockPosition().distSqr(task.targetPos()) > WORK_REACH_DISTANCE_SQUARED) {
			SettlementNavigation.moveToRoutineTarget(level, settlement, worker, task.standPos(), WALK_SPEED);
			return new TaskProgress(false, false);
		}

		Map<String, Integer> stockBefore = new LinkedHashMap<>(stock);
		worker.swing(InteractionHand.MAIN_HAND);
		boolean worldChanged = switch (task.kind()) {
			case COBWEB -> removeCobweb(level, task.targetPos());
			case STRUCTURE -> {
				if (!consume(stock, task.materialKey(), 1)) {
					yield false;
				}
				yield placeExpectedBlock(level, task);
			}
			case FARMLAND -> {
				BlockState current = level.getBlockState(task.targetPos());
				if (!isRepairableFarmSoil(current)) {
					yield false;
				}
				yield level.setBlock(task.targetPos(), task.state(), BLOCK_UPDATE_FLAGS);
			}
			case BED -> {
				if (stock.getOrDefault("bed", 0) > 0) {
					consume(stock, "bed", 1);
				} else if (stock.getOrDefault("wool", 0) >= 3 && stock.getOrDefault("planks", 0) >= 3) {
					consume(stock, "wool", 3);
					consume(stock, "planks", 3);
				} else {
					yield false;
				}
				yield placeBed(level, task);
			}
			case DOOR_HALF -> {
				if (!consume(stock, "planks", 1)) {
					yield false;
				}
				yield placeDoorHalf(level, task);
			}
		};

		if (!worldChanged) {
			stock.clear();
			stock.putAll(stockBefore);
		}
		return new TaskProgress(true, worldChanged);
	}

	private static boolean taskStillAvailable(ServerLevel level, MaintenanceTask task) {
		if (task == null || !level.hasChunkAt(task.targetPos())) {
			return false;
		}
		return switch (task.kind()) {
			case COBWEB -> level.getBlockState(task.targetPos()).is(Blocks.COBWEB);
			case STRUCTURE -> level.getBlockState(task.targetPos()).isAir();
			case FARMLAND -> isRepairableFarmSoil(level.getBlockState(task.targetPos()));
			case BED -> level.getBlockState(task.targetPos()).isAir() && level.getBlockState(task.secondaryPos()).isAir();
			case DOOR_HALF -> level.getBlockState(task.targetPos()).isAir();
		};
	}

	private static MaintenanceTask scanForVanillaTemplateRepair(ServerLevel level, SettlementState settlement, Map<String, Integer> stock) {
		return scan(level, settlement, STRUCTURE_REPAIR_CURSORS, TEMPLATE_COLUMNS_PER_PASS, pos -> expectedIntactBlock(level, pos)
			.flatMap(expected -> templateRepairTask(level, pos, expected, stock))
			.orElse(null));
	}

	private static Optional<MaintenanceTask> templateRepairTask(
		ServerLevel level,
		BlockPos pos,
		BlockState expected,
		Map<String, Integer> stock
	) {
		BlockState current = level.getBlockState(pos);
		if (expected.is(Blocks.FARMLAND) && isRepairableFarmSoil(current)) {
			return standPosition(level, pos.above())
				.map(stand -> new MaintenanceTask(TaskKind.FARMLAND, pos, pos, stand, expected, ""));
		}
		if (!current.isAir() || !isSafetyStructureBlock(expected)) {
			return Optional.empty();
		}
		String materialKey = materialKey(expected);
		if (materialKey.isBlank() || stock.getOrDefault(materialKey, 0) <= 0 || !expected.canSurvive(level, pos)) {
			return Optional.empty();
		}
		return standPosition(level, pos)
			.map(stand -> new MaintenanceTask(TaskKind.STRUCTURE, pos, pos, stand, expected, materialKey));
	}

	private static Optional<BlockState> expectedIntactBlock(ServerLevel level, BlockPos worldPos) {
		StructureStart start = level.structureManager().getStructureWithPieceAt(worldPos, StructureTags.VILLAGE);
		if (!start.isValid()) {
			return Optional.empty();
		}

		for (StructurePiece structurePiece : start.getPieces()) {
			if (!structurePiece.getBoundingBox().isInside(worldPos)
				|| !(structurePiece instanceof PoolElementStructurePiece piece)
				|| !(piece.getElement() instanceof SinglePoolElement element)) {
				continue;
			}
			Identifier damagedId = element.getTemplateLocation();
			String damagedPath = damagedId.getPath();
			if (!damagedPath.contains("/zombie/")) {
				continue;
			}
			Identifier intactId = Identifier.fromNamespaceAndPath(damagedId.getNamespace(), damagedPath.replace("/zombie/", "/"));
			Optional<StructureTemplate> intactTemplate = level.getStructureManager().get(intactId);
			if (intactTemplate.isEmpty()) {
				continue;
			}
			List<StructureTemplate.Palette> palettes = ((StructureTemplateAccessor) intactTemplate.get()).livevillages$getPalettes();
			if (palettes.isEmpty()) {
				continue;
			}
			StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(piece.getRotation());
			for (StructureBlockInfo block : palettes.getFirst().blocks()) {
				BlockPos transformed = StructureTemplate.transform(
					block.pos(),
					settings.getMirror(),
					settings.getRotation(),
					settings.getRotationPivot()
				).offset(piece.getPosition());
				if (transformed.equals(worldPos)) {
					return Optional.of(block.state().rotate(piece.getRotation()));
				}
			}
		}
		return Optional.empty();
	}

	private static boolean isSafetyStructureBlock(BlockState state) {
		return !state.isAir()
			&& !state.is(Blocks.STRUCTURE_VOID)
			&& !state.is(Blocks.JIGSAW)
			&& (state.isSolid()
				|| state.getBlock() instanceof IronBarsBlock
				|| state.getBlock() instanceof DoorBlock
				|| state.getBlock() instanceof FenceBlock
				|| state.getBlock() instanceof SlabBlock
				|| state.getBlock() instanceof StairBlock
				|| state.getBlock() instanceof WallBlock);
	}

	private static String materialKey(BlockState state) {
		if (state.getBlock() instanceof SlabBlock) {
			return state.is(BlockTags.WOODEN_SLABS) ? "slab" : stoneMaterialKey(state);
		}
		if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof FenceBlock) {
			return "planks";
		}
		if (state.getBlock() instanceof IronBarsBlock) {
			return state.is(Blocks.IRON_BARS) ? "iron_bars" : "glass";
		}
		String key = SettlementGoods.goodsKeyForItem(new ItemStack(state.getBlock().asItem()));
		return key == null ? stoneMaterialKey(state) : key;
	}

	private static String stoneMaterialKey(BlockState state) {
		String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		if (path.contains("sandstone")) {
			return "sand";
		}
		if (path.contains("stone_brick")) {
			return "stone_bricks";
		}
		if (path.contains("cobblestone")) {
			return "cobblestone";
		}
		if (path.contains("stone")) {
			return "stone";
		}
		return "";
	}

	private static boolean isRepairableFarmSoil(BlockState state) {
		return state.is(Blocks.DIRT)
			|| state.is(Blocks.GRASS_BLOCK)
			|| state.is(Blocks.COARSE_DIRT)
			|| state.is(Blocks.ROOTED_DIRT);
	}

	private static MaintenanceTask scanForCobweb(ServerLevel level, SettlementState settlement) {
		return scan(level, settlement, CLEANUP_CURSORS, pos -> {
			if (!level.getBlockState(pos).is(Blocks.COBWEB) || !isVanillaVillagePiece(level, pos)) {
				return null;
			}
			return standPosition(level, pos).map(stand -> new MaintenanceTask(TaskKind.COBWEB, pos, pos, stand, null, "")).orElse(null);
		});
	}

	private static MaintenanceTask scanForFunctionalRepair(ServerLevel level, SettlementState settlement, Map<String, Integer> stock) {
		return scan(level, settlement, REPAIR_CURSORS, pos -> {
			BlockState state = level.getBlockState(pos);
			if (isVanillaVillagePiece(level, pos) && state.getBlock() instanceof DoorBlock && state.hasProperty(DoorBlock.HALF)) {
				BlockPos missingPos = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
				if (level.getBlockState(missingPos).isAir() && stock.getOrDefault("planks", 0) > 0) {
					BlockState missingState = state.setValue(DoorBlock.HALF,
						state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
					if (!missingState.canSurvive(level, missingPos)) {
						return null;
					}
					return standPosition(level, missingPos)
						.map(stand -> new MaintenanceTask(TaskKind.DOOR_HALF, missingPos, missingPos, stand, missingState, "planks"))
						.orElse(null);
				}
			}

			boolean hasFinishedBed = stock.getOrDefault("bed", 0) > 0;
			boolean hasBedMaterials = stock.getOrDefault("wool", 0) >= 3 && stock.getOrDefault("planks", 0) >= 3;
			if (!state.isAir() || (!hasFinishedBed && !hasBedMaterials)) {
				return null;
			}
			return bedTaskAt(level, pos);
		});
	}

	private static MaintenanceTask bedTaskAt(ServerLevel level, BlockPos footPos) {
		Optional<BlockState> expectedFoot = expectedIntactBlock(level, footPos);
		if (expectedFoot.isEmpty()
			|| !(expectedFoot.get().getBlock() instanceof BedBlock)
			|| !expectedFoot.get().hasProperty(BedBlock.PART)
			|| expectedFoot.get().getValue(BedBlock.PART) != BedPart.FOOT
			|| !expectedFoot.get().hasProperty(BedBlock.FACING)) {
			return null;
		}

		Direction facing = expectedFoot.get().getValue(BedBlock.FACING);
		BlockPos headPos = footPos.relative(facing);
		Optional<BlockState> expectedHead = expectedIntactBlock(level, headPos);
		if (expectedHead.isEmpty()
			|| !(expectedHead.get().getBlock() instanceof BedBlock)
			|| !expectedHead.get().hasProperty(BedBlock.PART)
			|| expectedHead.get().getValue(BedBlock.PART) != BedPart.HEAD
			|| !expectedHead.get().hasProperty(BedBlock.FACING)
			|| expectedHead.get().getValue(BedBlock.FACING) != facing
			|| !level.getBlockState(footPos).isAir()
			|| !level.getBlockState(headPos).isAir()
			|| !level.getBlockState(footPos.above()).isAir()
			|| !level.getBlockState(headPos.above()).isAir()
			|| !level.getBlockState(footPos.below()).isSolid()
			|| !level.getBlockState(headPos.below()).isSolid()
			|| level.canSeeSky(footPos)
			|| level.canSeeSky(headPos)) {
			return null;
		}

		return bedStandPosition(level, footPos, headPos)
			.map(stand -> new MaintenanceTask(TaskKind.BED, footPos, headPos, stand, expectedFoot.get(), ""))
			.orElse(null);
	}

	private static Optional<BlockPos> bedStandPosition(ServerLevel level, BlockPos footPos, BlockPos headPos) {
		for (BlockPos bedPos : List.of(footPos, headPos)) {
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				BlockPos stand = bedPos.relative(direction);
				if ((stand.equals(footPos) || stand.equals(headPos))
					|| !level.getBlockState(stand).isAir()
					|| !level.getBlockState(stand.above()).isAir()
					|| !level.getBlockState(stand.below()).isSolid()) {
					continue;
				}
				return Optional.of(stand.immutable());
			}
		}
		return Optional.empty();
	}

	private static Optional<BlockPos> standPosition(ServerLevel level, BlockPos target) {
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos stand = target.relative(direction);
			if (level.getBlockState(stand).isAir() && level.getBlockState(stand.above()).isAir() && level.getBlockState(stand.below()).isSolid()) {
				return Optional.of(stand.immutable());
			}
		}
		return Optional.empty();
	}

	private static boolean isVanillaVillagePiece(ServerLevel level, BlockPos pos) {
		return level.structureManager().getStructureWithPieceAt(pos, StructureTags.VILLAGE).isValid();
	}

	private static MaintenanceTask scan(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> cursors,
		TaskProbe probe
	) {
		return scan(level, settlement, cursors, COLUMNS_PER_PASS, probe);
	}

	private static MaintenanceTask scan(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> cursors,
		int columnsPerPass,
		TaskProbe probe
	) {
		int width = SCAN_RADIUS_BLOCKS * 2 + 1;
		int columnCount = width * width;
		int cursor = Math.floorMod(cursors.getOrDefault(settlement.id(), 0), columnCount);
		BlockPos center = settlement.center();

		for (int checked = 0; checked < columnsPerPass; checked++) {
			int columnIndex = (cursor + checked) % columnCount;
			int zIndex = columnIndex / width;
			int xIndex = columnIndex % width;
			BlockPos columnPos = center.offset(xIndex - SCAN_RADIUS_BLOCKS, 0, zIndex - SCAN_RADIUS_BLOCKS);
			if (!level.hasChunkAt(columnPos)) {
				continue;
			}

			for (int dy = -SCAN_BELOW_BLOCKS; dy <= SCAN_ABOVE_BLOCKS; dy++) {
				MaintenanceTask task = probe.taskAt(columnPos.offset(0, dy, 0));
				if (task != null) {
					cursors.put(settlement.id(), (columnIndex + 1) % columnCount);
					return task;
				}
			}
		}
		cursors.put(settlement.id(), (cursor + columnsPerPass) % columnCount);
		return null;
	}

	private static boolean needsHousingRepair(SettlementState settlement) {
		return settlement.totalPopulation() > 0 && settlement.housingCapacity() < settlement.totalPopulation() + 1;
	}

	private static boolean removeCobweb(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).is(Blocks.COBWEB) && level.removeBlock(pos, false);
	}

	private static boolean placeBed(ServerLevel level, MaintenanceTask task) {
		BlockState foot = task.state();
		Direction facing = foot.getValue(BedBlock.FACING);
		BlockState head = foot.setValue(BedBlock.PART, BedPart.HEAD);
		if (!level.getBlockState(task.targetPos()).isAir()
			|| !level.getBlockState(task.secondaryPos()).isAir()
			|| !level.getBlockState(task.targetPos().above()).isAir()
			|| !level.getBlockState(task.secondaryPos().above()).isAir()
			|| bedStandPosition(level, task.targetPos(), task.secondaryPos()).isEmpty()) {
			return false;
		}
		level.setBlock(task.targetPos(), foot, BLOCK_UPDATE_FLAGS);
		if (!level.setBlock(task.secondaryPos(), head, BLOCK_UPDATE_FLAGS)) {
			level.removeBlock(task.targetPos(), false);
			return false;
		}
		return level.getBlockState(task.targetPos()).getBlock() == foot.getBlock()
			&& level.getBlockState(task.secondaryPos()).getBlock() == head.getBlock()
			&& task.secondaryPos().equals(task.targetPos().relative(facing));
	}

	private static boolean placeDoorHalf(ServerLevel level, MaintenanceTask task) {
		if (!level.getBlockState(task.targetPos()).isAir() || !task.state().canSurvive(level, task.targetPos())) {
			return false;
		}
		level.setBlock(task.targetPos(), task.state(), BLOCK_UPDATE_FLAGS);
		return level.getBlockState(task.targetPos()).getBlock() == task.state().getBlock();
	}

	private static boolean placeExpectedBlock(ServerLevel level, MaintenanceTask task) {
		if (!level.getBlockState(task.targetPos()).isAir() || !task.state().canSurvive(level, task.targetPos())) {
			return false;
		}
		level.setBlock(task.targetPos(), task.state(), BLOCK_UPDATE_FLAGS);
		return !level.getBlockState(task.targetPos()).isAir();
	}

	private static boolean consume(Map<String, Integer> stock, String key, int count) {
		int available = stock.getOrDefault(key, 0);
		if (available < count) {
			return false;
		}
		if (available == count) {
			stock.remove(key);
		} else {
			stock.put(key, available - count);
		}
		return true;
	}

	private enum TaskKind {
		COBWEB,
		STRUCTURE,
		FARMLAND,
		BED,
		DOOR_HALF
	}

	private record MaintenanceTask(TaskKind kind, BlockPos targetPos, BlockPos secondaryPos, BlockPos standPos, BlockState state, String materialKey) {
	}

	private record TaskProgress(boolean finished, boolean worldChanged) {
	}

	public record MaintenanceResult(boolean worldChanged, boolean stockChanged, Set<String> busyWorkerIds) {
		private static MaintenanceResult unchanged() {
			return new MaintenanceResult(false, false, Set.of());
		}
	}

	@FunctionalInterface
	private interface TaskProbe {
		MaintenanceTask taskAt(BlockPos pos);
	}
}
