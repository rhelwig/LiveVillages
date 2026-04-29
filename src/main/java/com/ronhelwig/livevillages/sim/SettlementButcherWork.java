package com.ronhelwig.livevillages.sim;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

public final class SettlementButcherWork {
	private static final double BUTCHER_WORK_REACH_DISTANCE_SQUARED = 9.0D;
	private static final double BUTCHER_WALK_SPEED = 0.75D;
	private static final long BUTCHER_DECIDE_INTERVAL_TICKS = 320L;
	private static final Map<String, TimedTask> ACTIVE_TASKS = new HashMap<>();

	private SettlementButcherWork() {
	}

	public static void maintainLoadedButchery(ServerLevel level, SettlementState settlement) {
		for (Villager butcher : SettlementVillagers.nearbyButchers(level, settlement)) {
			if (!SettlementVillagerWorkSchedule.shouldStartNewWork(level, butcher, "butchery", BUTCHER_DECIDE_INTERVAL_TICKS)) {
				if (SettlementVillagerWorkSchedule.isTakingBreak(level, butcher)) {
					butcher.getNavigation().stop();
				}

				continue;
			}

			Optional<ButcherTask> task = chooseButcherTask(level, settlement, butcher);

			if (task.isEmpty()) {
				ACTIVE_TASKS.remove(butcher.getUUID().toString());
				continue;
			}

			ButcherTask butcherTask = task.get();
			ACTIVE_TASKS.put(butcher.getUUID().toString(), new TimedTask(butcherTask.taskKey(), level.getServer().getTickCount()));
			showButcherTool(butcher, butcherTask);
			steerButcherTowardTask(butcher, butcherTask.workPos());

			if (!isWithinWorkReach(butcher, butcherTask.workPos())) {
				continue;
			}

			if (performButcherTask(level, butcherTask)) {
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

		if (survey.cows() <= 0 && survey.sheep() <= 0) {
			return;
		}

		int activeButchers = Math.min(butchers, Math.max(1, (survey.cows() + survey.sheep() + 3) / 4));
		int requiredWheatFeed = scaledAmount(Math.min(activeButchers * 1.5D, (survey.cows() * 0.60D) + (survey.sheep() * 0.35D)), elapsedDays);
		int wheatFeedCost = Math.min(stock.getOrDefault("wheat", 0), requiredWheatFeed);
		double feedCoverage = requiredWheatFeed <= 0 ? 1.0D : wheatFeedCost / (double) requiredWheatFeed;

		if (wheatFeedCost > 0) {
			stock.put("wheat", stock.getOrDefault("wheat", 0) - wheatFeedCost);
		}

		SettlementGoods.addGoods(stock, "beef", scaledAmount(
			Math.min(activeButchers * 2.0D, survey.cows() * 0.35D) * feedCoverage,
			elapsedDays
		));
		SettlementGoods.addGoods(stock, "mutton", scaledAmount(
			Math.min(activeButchers * 1.5D, survey.sheep() * 0.30D) * feedCoverage,
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

		return new HerdSurvey(cows, sheep);
	}

	private static Optional<ButcherTask> chooseButcherTask(ServerLevel level, SettlementState settlement, Villager butcher) {
		int radius = SettlementVillagers.settlementRadiusBlocks(settlement);
		AABB herdBounds = new AABB(settlement.center()).inflate(radius);

		return level.getEntitiesOfClass(
			Sheep.class,
			herdBounds,
			sheep -> sheep.readyForShearing()
				&& sheep.blockPosition().distSqr(settlement.center()) <= radius * radius
		).stream()
			.min(java.util.Comparator.comparingDouble(sheep -> sheep.blockPosition().distSqr(butcher.blockPosition())))
			.map(sheep -> new ButcherTask(ButcherAction.SHEAR_SHEEP, sheep.getId(), sheep.blockPosition(), "tending_flocks"));
	}

	private static boolean performButcherTask(ServerLevel level, ButcherTask task) {
		return switch (task.action()) {
			case SHEAR_SHEEP -> level.getEntity(task.entityId()) instanceof Sheep sheep
				&& sheep.readyForShearing()
				&& shearSheep(level, sheep);
		};
	}

	private static boolean shearSheep(ServerLevel level, Sheep sheep) {
		sheep.shear(level, SoundSource.NEUTRAL, new ItemStack(Items.SHEARS));
		return true;
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
			new ItemStack(task.action() == ButcherAction.SHEAR_SHEEP ? Items.SHEARS : Items.WHEAT)
		);
	}

	private static void steerButcherTowardTask(Villager butcher, BlockPos workPos) {
		butcher.getNavigation().moveTo(workPos.getX() + 0.5D, workPos.getY(), workPos.getZ() + 0.5D, BUTCHER_WALK_SPEED);
	}

	private static boolean isWithinWorkReach(Villager butcher, BlockPos workPos) {
		return butcher.distanceToSqr(workPos.getX() + 0.5D, workPos.getY() + 0.5D, workPos.getZ() + 0.5D) <= BUTCHER_WORK_REACH_DISTANCE_SQUARED;
	}

	private enum ButcherAction {
		SHEAR_SHEEP
	}

	private record ButcherTask(ButcherAction action, int entityId, BlockPos workPos, String taskKey) {
	}

	private record TimedTask(String taskKey, long tick) {
	}

	private record HerdSurvey(int cows, int sheep) {
	}
}
