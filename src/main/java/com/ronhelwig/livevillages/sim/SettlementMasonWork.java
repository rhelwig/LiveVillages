package com.ronhelwig.livevillages.sim;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class SettlementMasonWork {
	private static final double MASONRY_REACH_DISTANCE_SQUARED = 6.25D;
	private static final double MASONRY_WALK_SPEED = 0.75D;
	private static final long TASK_MEMORY_TICKS = 40L;
	private static final long MASONRY_DECIDE_INTERVAL_TICKS = 320L;
	private static final int COBBLESTONE_BATCH_SIZE = 1;
	private static final int MILEPOST_COBBLESTONE_COST = 8;
	private static final int MIN_COBBLESTONE_RESERVE_FOR_MILEPOSTS = 16;
	private static final Map<String, TimedTask> ACTIVE_TASKS = new HashMap<>();

	private SettlementMasonWork() {
	}

	public static boolean maintainLoadedMasonry(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Collection<SettlementBuildSite> buildSites
	) {
		List<Villager> masons = SettlementVillagers.nearbyMasons(level, settlement);

		if (masons.isEmpty()) {
			SettlementProfessionDiagnostics.log(level, settlement, "mason", "no_masons", "");
			return false;
		}

		long tick = level.getServer().getTickCount();
		List<SettlementBuildSite> buildSiteList = List.copyOf(buildSites);
		boolean stockChanged = false;

		for (Villager mason : masons) {
			if (!SettlementVillagerWorkSchedule.shouldStartNewWork(level, mason, "masonry", MASONRY_DECIDE_INTERVAL_TICKS)) {
				if (SettlementVillagerWorkSchedule.isTakingBreak(level, mason)) {
					mason.getNavigation().stop();
				}

				continue;
			}

			MasonryAssignment assignment = chooseAssignment(level, settlement, stock, buildSiteList, mason);
			showMasonTool(mason);
			SettlementNavigation.moveToRoutineTarget(level, settlement, mason, assignment.targetPos(), MASONRY_WALK_SPEED);
			ACTIVE_TASKS.put(mason.getUUID().toString(), new TimedTask(assignment.taskKey(), tick));

			if (isWithinWorkReach(mason, assignment.targetPos())) {
				mason.swing(InteractionHand.MAIN_HAND);

				if (assignment.stockGoodsKey().isBlank() || assignment.stockAmount() <= 0) {
					continue;
				}

				if (canProduceStockGoods(stock, assignment)) {
					if (!assignment.inputGoodsKey().isBlank() && assignment.inputAmount() > 0) {
						SettlementGoods.consumeGoods(stock, assignment.inputGoodsKey(), assignment.inputAmount());
					}

					stock.merge(assignment.stockGoodsKey(), assignment.stockAmount(), Integer::sum);
					SettlementProfessionReports.recordConversion(
						level,
						settlement,
						SettlementRoleKeys.MASON,
						mason,
						assignment.inputGoodsKey(),
						assignment.inputAmount(),
						assignment.stockGoodsKey(),
						assignment.stockAmount(),
						"completed " + assignment.taskKey().replace('_', ' ')
					);
					stockChanged = true;
				} else {
					SettlementProfessionDiagnostics.log(level, settlement, "mason", "missing_inputs", "task=" + assignment.taskKey() + " input=" + assignment.inputGoodsKey() + ":" + assignment.inputAmount());
				}
			} else {
				SettlementProfessionDiagnostics.log(level, settlement, "mason", "moving_to_work", "villager=" + mason.getUUID() + " task=" + assignment.taskKey() + " target=" + assignment.targetPos().toShortString());
			}
		}

		return stockChanged;
	}

	public static Optional<String> loadedMasonTaskKey(ServerLevel level, Villager villager) {
		TimedTask task = ACTIVE_TASKS.get(villager.getUUID().toString());

		if (task == null || level.getServer().getTickCount() - task.tick() > TASK_MEMORY_TICKS) {
			return Optional.empty();
		}

		return Optional.of(task.taskKey());
	}

	private static MasonryAssignment chooseAssignment(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		List<SettlementBuildSite> buildSites,
		Villager mason
	) {
		Optional<BlockPos> stoneConstructionTarget = buildSites.stream()
			.filter(buildSite -> !buildSite.complete())
			.filter(SettlementMasonWork::hasPendingStonework)
			.map(buildSite -> reachableMasonryTarget(level, buildSite.workstationPos()))
			.min(Comparator.comparingDouble(pos -> pos.distSqr(mason.blockPosition())));

		if (stoneConstructionTarget.isPresent()) {
			return new MasonryAssignment(stoneConstructionTarget.get(), "stone_construction", "", 0, "", 0);
		}

		Optional<BlockPos> generalConstructionTarget = buildSites.stream()
			.filter(buildSite -> !buildSite.complete())
			.map(buildSite -> reachableMasonryTarget(level, buildSite.workstationPos()))
			.min(Comparator.comparingDouble(pos -> pos.distSqr(mason.blockPosition())));

		if (generalConstructionTarget.isPresent()) {
			return new MasonryAssignment(generalConstructionTarget.get(), "stonework", "", 0, "", 0);
		}

		Optional<BlockPos> jobSite = SettlementVillagers.masonJobSite(level, mason)
			.map(rawPos -> reachableMasonryTarget(level, rawPos));
		int cobblestoneTarget = SettlementEconomyRules.targetForGoods(settlement, buildSites, "cobblestone");
		int cobblestoneShortage = Math.max(0, cobblestoneTarget - stock.getOrDefault("cobblestone", 0));
		int milepostShortage = preferredMilepostReserve(level, settlement) - stock.getOrDefault("milepost", 0);
		int cobblestoneReserveForMileposts = Math.max(MIN_COBBLESTONE_RESERVE_FOR_MILEPOSTS, cobblestoneTarget / 2);

		if (milepostShortage > 0
			&& jobSite.isPresent()
			&& stock.getOrDefault("cobblestone", 0) >= cobblestoneReserveForMileposts + MILEPOST_COBBLESTONE_COST) {
			return new MasonryAssignment(
				jobSite.get(),
				"carving_milepost",
				"milepost",
				1,
				"cobblestone",
				MILEPOST_COBBLESTONE_COST
			);
		}

		if (cobblestoneShortage > 0 && jobSite.isPresent()) {
			return new MasonryAssignment(
				jobSite.get(),
				"cutting_stone",
				"cobblestone",
				Math.min(COBBLESTONE_BATCH_SIZE, cobblestoneShortage),
				"",
				0
			);
		}

		BlockPos fallbackTarget = jobSite
			.or(() -> SettlementStockAccess.findStockAccessPos(level, settlement, buildSites))
			.orElse(settlement.center());
		return new MasonryAssignment(fallbackTarget, "stonework", "", 0, "", 0);
	}

	private static boolean canProduceStockGoods(Map<String, Integer> stock, MasonryAssignment assignment) {
		if (assignment.stockGoodsKey().isBlank() || assignment.stockAmount() <= 0) {
			return false;
		}

		if (assignment.inputGoodsKey().isBlank() || assignment.inputAmount() <= 0) {
			return true;
		}

		return stock.getOrDefault(assignment.inputGoodsKey(), 0) >= assignment.inputAmount();
	}

	private static int preferredMilepostReserve(ServerLevel level, SettlementState settlement) {
		if (SettlementVillagers.nearbyRoadwrights(level, settlement).isEmpty()) {
			return 0;
		}

		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(level.getServer());
		long landRoutes = savedData.getRoutesForSettlement(settlement.id()).stream()
			.filter(route -> route.type() == RouteType.LAND)
			.count();
		int nearbyMileposts = SettlementRoadwrightWork.nearbyMileposts(level, settlement.center(), 512).size();
		int routeDrivenReserve = landRoutes <= 0 ? 1 : (int) Math.min(4L, landRoutes + 1L);
		int gapAllowance = nearbyMileposts < landRoutes ? 1 : 0;
		return Math.max(1, Math.min(4, Math.max((settlement.totalPopulation() + 5) / 8, routeDrivenReserve + gapAllowance)));
	}

	private static BlockPos reachableMasonryTarget(ServerLevel level, BlockPos rawTargetPos) {
		return SettlementStockAccess.stockAccessStandPos(level, rawTargetPos)
			.orElse(rawTargetPos)
			.immutable();
	}

	private static boolean hasPendingStonework(SettlementBuildSite buildSite) {
		for (SettlementBuildBlockState block : buildSite.blocks()) {
			if ((block.status() == SettlementBuildBlockStatus.PENDING || block.status() == SettlementBuildBlockStatus.MISSING_MATERIAL)
				&& block.expectedMaterialKey().equals("cobblestone")) {
				return true;
			}
		}

		return false;
	}

	private static boolean isWithinWorkReach(Villager mason, BlockPos workPos) {
		return mason.distanceToSqr(workPos.getX() + 0.5D, workPos.getY() + 0.5D, workPos.getZ() + 0.5D) <= MASONRY_REACH_DISTANCE_SQUARED;
	}

	private static void showMasonTool(Villager mason) {
		ItemStack held = mason.getMainHandItem();

		if (held.isEmpty()) {
			mason.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
		}
	}

	private record TimedTask(String taskKey, long tick) {
	}

	private record MasonryAssignment(
		BlockPos targetPos,
		String taskKey,
		String stockGoodsKey,
		int stockAmount,
		String inputGoodsKey,
		int inputAmount
	) {
	}
}
