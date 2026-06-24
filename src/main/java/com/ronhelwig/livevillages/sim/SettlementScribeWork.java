package com.ronhelwig.livevillages.sim;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class SettlementScribeWork {
	private static final double SCRIBE_REACH_DISTANCE_SQUARED = 6.25D;
	private static final double SCRIBE_WALK_SPEED = 0.75D;
	private static final long TASK_MEMORY_TICKS = 80L;
	private static final long SCRIBE_DECIDE_INTERVAL_TICKS = 500L;
	private static final long SCRIBE_WORK_INTERVAL_TICKS = 1_200L;
	private static final Map<String, TimedTask> ACTIVE_TASKS = new HashMap<>();
	private static final Map<String, Long> LAST_WORK_TICKS = new HashMap<>();

	private SettlementScribeWork() {
	}

	public static boolean maintainLoadedScribing(ServerLevel level, SettlementState settlement, LiveVillagesSavedData savedData) {
		List<Villager> scribes = SettlementVillagers.nearbyScribes(level, settlement);

		if (scribes.isEmpty()) {
			SettlementProfessionDiagnostics.log(level, settlement, SettlementRoleKeys.SCRIBE, "no_scribes", "");
			return false;
		}

		List<BlockPos> desks = SettlementConstruction.findPlacedScribeDesks(level, settlement);

		if (desks.isEmpty()) {
			SettlementProfessionDiagnostics.log(level, settlement, SettlementRoleKeys.SCRIBE, "no_scribe_desks", "");
			return false;
		}

		List<String> previousRecipes = savedData.knownScribeRecipeIds(settlement.id());
		List<String> knownRecipes = savedData.ensureScribeStarterRecipes(settlement.id(), SettlementRecipeKnowledge.recipeIdsForTier(SettlementTiers.unlockedTier(settlement)));
		boolean dataChanged = !knownRecipes.equals(previousRecipes);
		long tick = level.getServer().getTickCount();

		for (Villager scribe : scribes) {
			showScribeTool(scribe);

			if (!SettlementVillagerWorkSchedule.shouldStartNewWork(level, scribe, "scribing", SCRIBE_DECIDE_INTERVAL_TICKS)) {
				if (SettlementVillagerWorkSchedule.isTakingBreak(level, scribe)) {
					scribe.getNavigation().stop();
				}

				continue;
			}

			BlockPos desk = nearestDesk(scribe.blockPosition(), desks);

			if (desk == null) {
				continue;
			}

			String taskKey = dataChanged ? "cataloging_starter_recipes" : "copying_settlement_recipes";
			BlockPos workPos = standableDeskAccess(level, desk).orElse(desk);
			ACTIVE_TASKS.put(scribe.getUUID().toString(), new TimedTask(taskKey, tick));

			if (scribe.blockPosition().distSqr(workPos) > SCRIBE_REACH_DISTANCE_SQUARED) {
				SettlementNavigation.moveToRoutineTarget(level, settlement, scribe, workPos, SCRIBE_WALK_SPEED);
				SettlementProfessionDiagnostics.log(level, settlement, SettlementRoleKeys.SCRIBE, "moving_to_work", "villager=" + scribe.getUUID() + " target=" + workPos.toShortString());
				continue;
			}

			if (!workCooldownReady(scribe, tick)) {
				continue;
			}

			scribe.swing(InteractionHand.MAIN_HAND);
			LAST_WORK_TICKS.put(scribe.getUUID().toString(), tick);
			SettlementProfessionReports.recordAccomplished(
				level,
				settlement,
				SettlementRoleKeys.SCRIBE,
				scribe,
				dataChanged ? "cataloged starter recipes" : "copied settlement recipes"
			);
		}

		return dataChanged;
	}

	public static Optional<String> loadedScribeTaskKey(ServerLevel level, Villager villager) {
		TimedTask task = ACTIVE_TASKS.get(villager.getUUID().toString());

		if (task == null || level.getServer().getTickCount() - task.tick() > TASK_MEMORY_TICKS) {
			return Optional.empty();
		}

		return Optional.of(task.taskKey());
	}

	private static BlockPos nearestDesk(BlockPos origin, List<BlockPos> desks) {
		BlockPos nearest = null;
		double nearestDistance = Double.MAX_VALUE;

		for (BlockPos desk : desks) {
			double distance = desk.distSqr(origin);

			if (distance < nearestDistance) {
				nearest = desk;
				nearestDistance = distance;
			}
		}

		return nearest;
	}

	private static Optional<BlockPos> standableDeskAccess(ServerLevel level, BlockPos desk) {
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos candidate = desk.relative(direction);

			if (isStandable(level, candidate)) {
				return Optional.of(candidate.immutable());
			}
		}

		return Optional.empty();
	}

	private static boolean isStandable(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).isAir()
			&& level.getBlockState(pos.above()).isAir()
			&& !level.getBlockState(pos.below()).isAir();
	}

	private static boolean workCooldownReady(Villager scribe, long tick) {
		Long lastWorkTick = LAST_WORK_TICKS.get(scribe.getUUID().toString());
		return lastWorkTick == null || tick - lastWorkTick >= SCRIBE_WORK_INTERVAL_TICKS;
	}

	private static void showScribeTool(Villager scribe) {
		ItemStack held = scribe.getMainHandItem();

		if (held.isEmpty() || !held.is(Items.WRITABLE_BOOK)) {
			scribe.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WRITABLE_BOOK));
		}
	}

	private record TimedTask(String taskKey, long tick) {
	}
}
