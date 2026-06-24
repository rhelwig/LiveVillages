package com.ronhelwig.livevillages.sim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import com.ronhelwig.livevillages.content.LiveVillagesVillagerProfessions;

public final class SettlementVanillaProfessionWork {
	private static final double WORK_REACH_DISTANCE_SQUARED = 6.25D;
	private static final double CLERIC_HEAL_REACH_DISTANCE_SQUARED = 9.0D;
	private static final double WORK_WALK_SPEED = 0.75D;
	private static final double CLERIC_HEAL_WALK_SPEED = 0.9D;
	private static final float CLERIC_HEAL_AMOUNT = 6.0F;
	private static final int CLERIC_HEALING_RADIUS_BLOCKS = 28;
	private static final int CLERIC_PERSONAL_DANGER_RADIUS_BLOCKS = 6;
	private static final long TASK_MEMORY_TICKS = 40L;
	private static final long VANILLA_WORK_DECIDE_INTERVAL_TICKS = 320L;
	private static final long CLERIC_HEAL_COOLDOWN_TICKS = 180L;
	private static final long DAY_TICKS = 24_000L;
	private static final int SHEPHERD_BED_BATCH_SIZE = SettlementEconomyRules.scaledWorkerDailyUnits(4);
	private static final Map<String, Long> LAST_PRODUCTION_DAY = new HashMap<>();
	private static final Map<String, Long> LAST_EQUIPMENT_DAY = new HashMap<>();
	private static final Map<String, Long> LAST_CLERIC_HEAL_TICKS = new HashMap<>();
	private static final Map<String, TimedTask> ACTIVE_TASKS = new HashMap<>();
	private static final List<ProfessionWorkConfig> WORK_CONFIGS = List.of(
		new ProfessionWorkConfig(SettlementRoleKeys.CARTOGRAPHER, VillagerProfession.CARTOGRAPHER, poiType -> poiType.is(PoiTypes.CARTOGRAPHER)),
		new ProfessionWorkConfig(SettlementRoleKeys.CLERIC, VillagerProfession.CLERIC, poiType -> poiType.is(PoiTypes.CLERIC)),
		new ProfessionWorkConfig(SettlementRoleKeys.LIBRARIAN, VillagerProfession.LIBRARIAN, poiType -> poiType.is(PoiTypes.LIBRARIAN)),
		new ProfessionWorkConfig(SettlementRoleKeys.LEATHERWORKER, VillagerProfession.LEATHERWORKER, poiType -> poiType.is(PoiTypes.LEATHERWORKER)),
		new ProfessionWorkConfig(SettlementRoleKeys.SHEPHERD, VillagerProfession.SHEPHERD, poiType -> poiType.is(PoiTypes.SHEPHERD)),
		new ProfessionWorkConfig(SettlementRoleKeys.ARMORER, VillagerProfession.ARMORER, poiType -> poiType.is(PoiTypes.ARMORER)),
		new ProfessionWorkConfig(SettlementRoleKeys.TOOLSMITH, VillagerProfession.TOOLSMITH, poiType -> poiType.is(PoiTypes.TOOLSMITH)),
		new ProfessionWorkConfig(SettlementRoleKeys.WEAPONSMITH, VillagerProfession.WEAPONSMITH, poiType -> poiType.is(PoiTypes.WEAPONSMITH))
	);

	private SettlementVanillaProfessionWork() {
	}

	public static boolean maintainLoadedVanillaProfessionWork(ServerLevel level, SettlementState settlement, Map<String, Integer> stock) {
		boolean stockChanged = false;
		long currentDay = Math.floorDiv(level.getOverworldClockTime(), DAY_TICKS);
		long currentTick = level.getServer().getTickCount();
		List<Villager> villagers = SettlementVillagers.nearbyAdultVillagers(level, settlement);

		for (ProfessionWorkConfig config : WORK_CONFIGS) {
			for (Villager villager : villagers) {
				if (!villager.getVillagerData().profession().is(config.profession())) {
					continue;
				}

				if (config.roleKey().equals(SettlementRoleKeys.CLERIC)) {
					ClericHealingResult healingResult = maintainClericHealing(level, settlement, stock, villager, villagers, currentTick);

					if (healingResult.handled()) {
						stockChanged |= healingResult.stockChanged();
						continue;
					}
				}

				String productionKey = productionKey(level, settlement, villager, config.roleKey());
				String equipmentKey = equipmentKey(level, settlement, villager, config.roleKey());
				boolean productionDone = LAST_PRODUCTION_DAY.getOrDefault(productionKey, Long.MIN_VALUE) == currentDay;
				boolean equipmentDone = LAST_EQUIPMENT_DAY.getOrDefault(equipmentKey, Long.MIN_VALUE) == currentDay;

				if (productionDone && equipmentDone) {
					continue;
				}

				BlockPos workPos = workPosFor(level, settlement, villager, config)
					.orElse(settlement.center());
				boolean anvilSupportedSmithy = isSmithRole(config.roleKey()) && SettlementConstruction.hasSmithyAnvilSupport(level, settlement, workPos);
				Optional<ProductionPlan> productionPlan = productionDone
					? Optional.empty()
					: chooseProductionPlan(settlement, stock, config.roleKey(), currentDay, anvilSupportedSmithy);
				Optional<EquipmentPlan> equipmentPlan = productionPlan.isPresent() || equipmentDone
					? Optional.empty()
					: chooseEquipmentPlan(stock, config.roleKey(), villager, villagers);
				if (equipmentPlan.isEmpty() && productionPlan.isEmpty()) {
					if (!productionDone) {
						SettlementProfessionDiagnostics.log(level, settlement, config.roleKey(), "missing_inputs", stockSummary(config.roleKey(), stock));
					}
					continue;
				}

				if (!SettlementVillagerWorkSchedule.shouldStartNewWork(level, villager, config.roleKey(), VANILLA_WORK_DECIDE_INTERVAL_TICKS)) {
					if (SettlementVillagerWorkSchedule.isTakingBreak(level, villager)) {
						villager.getNavigation().stop();
					}

					continue;
				}

				String taskKey = equipmentPlan.map(EquipmentPlan::taskKey)
					.orElseGet(() -> productionPlan.get().taskKey());
				ACTIVE_TASKS.put(villager.getUUID().toString(), new TimedTask(taskKey, currentTick));
				SettlementNavigation.moveToRoutineTarget(level, settlement, villager, workPos, WORK_WALK_SPEED);

				if (!isWithinWorkReach(villager, workPos)) {
					SettlementProfessionDiagnostics.log(level, settlement, config.roleKey(), "moving_to_work", "villager=" + villager.getUUID() + " target=" + workPos.toShortString());
					continue;
				}

				boolean actionCompleted = equipmentPlan
					.map(plan -> performEquipmentSupport(level, settlement, stock, config.roleKey(), villager, plan))
					.orElseGet(() -> performProduction(level, settlement, stock, config.roleKey(), villager, productionPlan.get()));

				if (actionCompleted) {
					villager.swing(InteractionHand.MAIN_HAND);
					if (equipmentPlan.isPresent()) {
						LAST_EQUIPMENT_DAY.put(equipmentKey, currentDay);
					} else {
						LAST_PRODUCTION_DAY.put(productionKey, currentDay);
					}
					stockChanged = true;
				}
			}
		}

		return stockChanged;
	}

	private static ClericHealingResult maintainClericHealing(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Villager cleric,
		List<Villager> villagers,
		long currentTick
	) {
		if (stock.getOrDefault("healing_potion", 0) <= 0 || hostileTooClose(level, cleric.blockPosition(), CLERIC_PERSONAL_DANGER_RADIUS_BLOCKS)) {
			return ClericHealingResult.NOT_HANDLED;
		}

		String clericKey = productionKey(level, settlement, cleric, "healing");
		long lastHealTick = LAST_CLERIC_HEAL_TICKS.getOrDefault(clericKey, Long.MIN_VALUE);

		if (currentTick - lastHealTick < CLERIC_HEAL_COOLDOWN_TICKS) {
			return ClericHealingResult.NOT_HANDLED;
		}

		Optional<Villager> target = chooseClericHealingTarget(level, cleric, villagers);

		if (target.isEmpty()) {
			return ClericHealingResult.NOT_HANDLED;
		}

		Villager patient = target.get();
		cleric.setAggressive(false);
		cleric.setTarget(null);
		cleric.lookAt(patient, 30.0F, 30.0F);
		ACTIVE_TASKS.put(cleric.getUUID().toString(), new TimedTask("healing_defenders", currentTick));

		if (cleric.distanceToSqr(patient) > CLERIC_HEAL_REACH_DISTANCE_SQUARED) {
			cleric.getNavigation().moveTo(patient.getX(), patient.getY(), patient.getZ(), CLERIC_HEAL_WALK_SPEED);
			return ClericHealingResult.HANDLED_WITHOUT_STOCK_CHANGE;
		}

		cleric.getNavigation().stop();

		if (!SettlementGoods.consumeGoods(stock, "healing_potion", 1)) {
			return ClericHealingResult.NOT_HANDLED;
		}

		cleric.swing(InteractionHand.MAIN_HAND);
		patient.heal(CLERIC_HEAL_AMOUNT);
		SettlementProfessionReports.recordConsumed(level, settlement, SettlementRoleKeys.CLERIC, cleric, "healing_potion", 1);
		SettlementProfessionReports.recordAccomplished(level, settlement, SettlementRoleKeys.CLERIC, cleric, "healed defender");
		LAST_CLERIC_HEAL_TICKS.put(clericKey, currentTick);
		return ClericHealingResult.HANDLED_WITH_STOCK_CHANGE;
	}

	private static Optional<Villager> chooseClericHealingTarget(ServerLevel level, Villager cleric, List<Villager> villagers) {
		Villager best = null;
		double bestScore = Double.MAX_VALUE;

		for (Villager candidate : villagers) {
			if (candidate == cleric
				|| candidate.isRemoved()
				|| !candidate.isAlive()
				|| candidate.distanceToSqr(cleric) > (double) CLERIC_HEALING_RADIUS_BLOCKS * CLERIC_HEALING_RADIUS_BLOCKS
				|| candidate.getHealth() >= candidate.getMaxHealth() - 0.5F
				|| !isClericHealingPriorityTarget(candidate)) {
				continue;
			}

			double healthRatio = candidate.getHealth() / candidate.getMaxHealth();
			double score = healthRatio * 1000.0D + candidate.distanceToSqr(cleric);

			if (SettlementDefenseWork.loadedDefenseTaskKey(level, candidate).filter("defending_village"::equals).isPresent()) {
				score -= 500.0D;
			}

			if (score < bestScore) {
				bestScore = score;
				best = candidate;
			}
		}

		return Optional.ofNullable(best);
	}

	private static boolean isClericHealingPriorityTarget(Villager villager) {
		Holder<VillagerProfession> profession = villager.getVillagerData().profession();
		return profession.is(LiveVillagesVillagerProfessions.GUARD)
			|| profession.is(VillagerProfession.ARMORER)
			|| profession.is(VillagerProfession.BUTCHER)
			|| profession.is(VillagerProfession.FISHERMAN)
			|| profession.is(VillagerProfession.FLETCHER)
			|| profession.is(VillagerProfession.LEATHERWORKER)
			|| profession.is(VillagerProfession.SHEPHERD)
			|| profession.is(VillagerProfession.TOOLSMITH)
			|| profession.is(VillagerProfession.WEAPONSMITH)
			|| profession.unwrapKey()
				.map(key -> {
					String path = key.identifier().getPath();
					return path.equals(SettlementRoleKeys.FORESTER)
						|| path.equals(SettlementRoleKeys.GARDENER)
						|| path.equals(SettlementRoleKeys.MINER)
						|| path.equals(SettlementRoleKeys.PORTMASTER)
						|| path.equals(SettlementRoleKeys.ROADWRIGHT);
				})
				.orElse(false);
	}

	private static boolean hostileTooClose(ServerLevel level, BlockPos origin, int radius) {
		double radiusSqr = radius * radius;
		AABB bounds = new AABB(origin).inflate(radius);
		return !level.getEntitiesOfClass(Monster.class, bounds, monster ->
			!monster.isRemoved()
				&& monster.isAlive()
				&& monster.blockPosition().distSqr(origin) <= radiusSqr
		).isEmpty();
	}

	public static Optional<String> loadedVanillaProfessionTaskKey(ServerLevel level, Villager villager) {
		TimedTask task = ACTIVE_TASKS.get(villager.getUUID().toString());

		if (task == null || level.getServer().getTickCount() - task.tick() > TASK_MEMORY_TICKS) {
			return Optional.empty();
		}

		return Optional.of(task.taskKey());
	}

	private static Optional<ProductionPlan> chooseProductionPlan(SettlementState settlement, Map<String, Integer> stock, String roleKey, long currentDay, boolean anvilSupportedSmithy) {
		return switch (roleKey) {
			case SettlementRoleKeys.CARTOGRAPHER -> Optional.of(new ProductionPlan("refreshing_route_intelligence", "", 0, Map.of()));
			case SettlementRoleKeys.CLERIC -> planIfAvailable(
				"brewing_healing_potion",
				"healing_potion",
				1,
				Map.of("glass_bottle", 1, "nether_wart", 1, "glistering_melon_slice", 1),
				stock
			);
			case SettlementRoleKeys.LIBRARIAN -> librarianPlan(settlement, stock);
			case SettlementRoleKeys.LEATHERWORKER -> leatherworkerPlan(stock, currentDay);
			case SettlementRoleKeys.SHEPHERD -> shepherdPlan(settlement, stock);
			case SettlementRoleKeys.ARMORER -> armorerPlan(stock, currentDay, anvilSupportedSmithy);
			case SettlementRoleKeys.TOOLSMITH -> smithPlanIfAvailable("smithing_iron_pickaxe", "iron_pickaxe", Map.of("iron_ingot", 3, "stick", 2), stock, anvilSupportedSmithy);
			case SettlementRoleKeys.WEAPONSMITH -> smithPlanIfAvailable("sharpening_iron_sword", "iron_sword", Map.of("iron_ingot", 2, "stick", 1), stock, anvilSupportedSmithy);
			default -> Optional.empty();
		};
	}

	private static Optional<ProductionPlan> librarianPlan(SettlementState settlement, Map<String, Integer> stock) {
		if (stock.getOrDefault("book", 0) >= 3
			&& stock.getOrDefault("planks", 0) >= 6
			&& stock.getOrDefault("bookshelf", 0) < SettlementEconomyRules.targetForGoods(settlement, "bookshelf")) {
			return Optional.of(new ProductionPlan("assembling_bookshelf", "bookshelf", 1, Map.of("book", 3, "planks", 6)));
		}

		return planIfAvailable("copying_book", "book", 1, Map.of("paper", 3, "leather", 1), stock);
	}

	private static Optional<ProductionPlan> leatherworkerPlan(Map<String, Integer> stock, long currentDay) {
		String output = switch ((int) Math.floorMod(currentDay, 4L)) {
			case 0 -> "leather_chestplate";
			case 1 -> "leather_leggings";
			case 2 -> "leather_helmet";
			default -> "leather_boots";
		};
		int leatherCost = switch (output) {
			case "leather_chestplate" -> 8;
			case "leather_leggings" -> 7;
			case "leather_helmet" -> 5;
			default -> 4;
		};
		return planIfAvailable("crafting_" + output, output, 1, Map.of("leather", leatherCost), stock);
	}

	private static Optional<ProductionPlan> armorerPlan(Map<String, Integer> stock, long currentDay, boolean anvilSupportedSmithy) {
		return switch ((int) Math.floorMod(currentDay, 5L)) {
			case 0 -> smithPlanIfAvailable("forging_iron_chestplate", "iron_chestplate", Map.of("iron_ingot", 8), stock, anvilSupportedSmithy);
			case 1 -> smithPlanIfAvailable("forging_iron_leggings", "iron_leggings", Map.of("iron_ingot", 7), stock, anvilSupportedSmithy);
			case 2 -> smithPlanIfAvailable("forging_iron_helmet", "iron_helmet", Map.of("iron_ingot", 5), stock, anvilSupportedSmithy);
			case 3 -> smithPlanIfAvailable("forging_iron_boots", "iron_boots", Map.of("iron_ingot", 4), stock, anvilSupportedSmithy);
			default -> smithPlanIfAvailable("crafting_shield", "shield", Map.of("iron_ingot", 1, "planks", 6), stock, anvilSupportedSmithy);
		};
	}

	private static Optional<ProductionPlan> shepherdPlan(SettlementState settlement, Map<String, Integer> stock) {
		int bedNeed = SettlementEconomyRules.targetForGoods(settlement, "bed") - stock.getOrDefault("bed", 0);
		int craftableBeds = Math.min(stock.getOrDefault("wool", 0) / 3, stock.getOrDefault("planks", 0) / 3);
		int bedBatch = Math.min(Math.min(bedNeed, craftableBeds), SHEPHERD_BED_BATCH_SIZE);

		if (bedBatch > 0) {
			return Optional.of(new ProductionPlan("weaving_beds", "bed", bedBatch, Map.of("wool", bedBatch * 3, "planks", bedBatch * 3)));
		}

		return Optional.of(new ProductionPlan("collecting_wool", "wool", 1, Map.of()));
	}

	private static Optional<EquipmentPlan> chooseEquipmentPlan(Map<String, Integer> stock, String roleKey, Villager worker, List<Villager> villagers) {
		return switch (roleKey) {
			case SettlementRoleKeys.LEATHERWORKER -> leatherworkerEquipmentPlan(stock, worker, villagers);
			case SettlementRoleKeys.ARMORER -> armorerEquipmentPlan(stock, worker, villagers);
			case SettlementRoleKeys.TOOLSMITH -> toolsmithEquipmentPlan(stock, worker, villagers);
			case SettlementRoleKeys.WEAPONSMITH -> weaponsmithEquipmentPlan(stock, worker, villagers);
			default -> Optional.empty();
		};
	}

	private static Optional<EquipmentPlan> leatherworkerEquipmentPlan(Map<String, Integer> stock, Villager worker, List<Villager> villagers) {
		Optional<EquipmentPlan> selfArmor = leatherArmorPlan(stock, worker, "equipping own leather armor");

		if (selfArmor.isPresent()) {
			return selfArmor;
		}

		return villagers.stream()
			.filter(candidate -> candidate != worker)
			.map(target -> leatherArmorPlan(stock, target, "distributing leather armor"))
			.flatMap(Optional::stream)
			.findFirst();
	}

	private static Optional<EquipmentPlan> leatherArmorPlan(Map<String, Integer> stock, Villager target, String taskKey) {
		return equipmentPlanIfStocked(stock, target, EquipmentSlot.CHEST, "leather_chestplate", new ItemStack(Items.LEATHER_CHESTPLATE), taskKey)
			.or(() -> equipmentPlanIfStocked(stock, target, EquipmentSlot.LEGS, "leather_leggings", new ItemStack(Items.LEATHER_LEGGINGS), taskKey))
			.or(() -> equipmentPlanIfStocked(stock, target, EquipmentSlot.HEAD, "leather_helmet", new ItemStack(Items.LEATHER_HELMET), taskKey))
			.or(() -> equipmentPlanIfStocked(stock, target, EquipmentSlot.FEET, "leather_boots", new ItemStack(Items.LEATHER_BOOTS), taskKey));
	}

	private static Optional<EquipmentPlan> armorerEquipmentPlan(Map<String, Integer> stock, Villager worker, List<Villager> villagers) {
		Optional<EquipmentPlan> selfArmor = ironArmorPlan(stock, worker, "equipping own iron armor")
			.or(() -> shieldPlan(stock, worker, "equipping own shield"));

		if (selfArmor.isPresent()) {
			return selfArmor;
		}

		return villagers.stream()
			.filter(SettlementVanillaProfessionWork::isCombatGearPriorityTarget)
			.sorted(java.util.Comparator.comparingInt(SettlementVanillaProfessionWork::combatGearPriority))
			.map(target -> ironArmorPlan(stock, target, "distributing iron armor")
				.or(() -> shieldPlan(stock, target, "distributing shield")))
			.flatMap(Optional::stream)
			.findFirst();
	}

	private static Optional<EquipmentPlan> ironArmorPlan(Map<String, Integer> stock, Villager target, String taskKey) {
		return equipmentPlanIfStocked(stock, target, EquipmentSlot.CHEST, "iron_chestplate", new ItemStack(Items.IRON_CHESTPLATE), taskKey)
			.or(() -> equipmentPlanIfStocked(stock, target, EquipmentSlot.LEGS, "iron_leggings", new ItemStack(Items.IRON_LEGGINGS), taskKey))
			.or(() -> equipmentPlanIfStocked(stock, target, EquipmentSlot.HEAD, "iron_helmet", new ItemStack(Items.IRON_HELMET), taskKey))
			.or(() -> equipmentPlanIfStocked(stock, target, EquipmentSlot.FEET, "iron_boots", new ItemStack(Items.IRON_BOOTS), taskKey));
	}

	private static Optional<EquipmentPlan> shieldPlan(Map<String, Integer> stock, Villager target, String taskKey) {
		return equipmentPlanIfStocked(stock, target, EquipmentSlot.OFFHAND, "shield", new ItemStack(Items.SHIELD), taskKey);
	}

	private static Optional<EquipmentPlan> toolsmithEquipmentPlan(Map<String, Integer> stock, Villager worker, List<Villager> villagers) {
		Optional<EquipmentPlan> selfTool = selfEquipmentPlan(stock, worker, EquipmentSlot.MAINHAND, "iron_pickaxe", new ItemStack(Items.IRON_PICKAXE), "equipping own iron pickaxe");

		if (selfTool.isPresent()) {
			return selfTool;
		}

		return villagers.stream()
			.filter(SettlementVanillaProfessionWork::isToolPriorityTarget)
			.filter(SettlementVanillaProfessionWork::needsPickaxe)
			.sorted(java.util.Comparator.comparingInt(SettlementVanillaProfessionWork::toolPriority))
			.findFirst()
			.flatMap(target -> equipmentPlanIfStocked(
				stock,
				target,
				EquipmentSlot.MAINHAND,
				"iron_pickaxe",
				new ItemStack(Items.IRON_PICKAXE),
				"distributing iron pickaxe"
			));
	}

	private static Optional<EquipmentPlan> weaponsmithEquipmentPlan(Map<String, Integer> stock, Villager worker, List<Villager> villagers) {
		Optional<EquipmentPlan> selfWeapon = selfEquipmentPlan(stock, worker, EquipmentSlot.MAINHAND, "iron_sword", new ItemStack(Items.IRON_SWORD), "equipping own iron sword");

		if (selfWeapon.isPresent()) {
			return selfWeapon;
		}

		return villagers.stream()
			.filter(SettlementVanillaProfessionWork::isSwordPriorityTarget)
			.filter(SettlementVanillaProfessionWork::needsSword)
			.sorted(java.util.Comparator.comparingInt(SettlementVanillaProfessionWork::swordPriority))
			.findFirst()
			.flatMap(target -> equipmentPlanIfStocked(
				stock,
				target,
				EquipmentSlot.MAINHAND,
				"iron_sword",
				new ItemStack(Items.IRON_SWORD),
				"distributing iron sword"
			));
	}

	private static Optional<EquipmentPlan> selfEquipmentPlan(Map<String, Integer> stock, Villager target, EquipmentSlot slot, String goodsKey, ItemStack stack, String taskKey) {
		return target.getItemBySlot(slot).isEmpty()
			? equipmentPlanIfStocked(stock, target, slot, goodsKey, stack, taskKey)
			: Optional.empty();
	}

	private static Optional<EquipmentPlan> equipmentPlanIfStocked(Map<String, Integer> stock, Villager target, EquipmentSlot slot, String goodsKey, ItemStack stack, String taskKey) {
		return target.getItemBySlot(slot).isEmpty() && stock.getOrDefault(goodsKey, 0) > 0
			? Optional.of(new EquipmentPlan(taskKey, target, slot, goodsKey, stack.copy()))
			: Optional.empty();
	}

	private static boolean isCombatGearPriorityTarget(Villager villager) {
		Holder<VillagerProfession> profession = villager.getVillagerData().profession();
		return profession.is(LiveVillagesVillagerProfessions.GUARD)
			|| profession.is(VillagerProfession.ARMORER)
			|| profession.is(VillagerProfession.WEAPONSMITH)
			|| profession.is(VillagerProfession.TOOLSMITH)
			|| profession.is(VillagerProfession.FLETCHER)
			|| profession.is(VillagerProfession.FISHERMAN)
			|| profession.is(VillagerProfession.SHEPHERD)
			|| profession.is(VillagerProfession.LEATHERWORKER)
			|| profession.unwrapKey()
				.map(key -> {
					String path = key.identifier().getPath();
					return path.equals(SettlementRoleKeys.PORTMASTER)
						|| path.equals(SettlementRoleKeys.ROADWRIGHT)
						|| path.equals(SettlementRoleKeys.FORESTER)
						|| path.equals(SettlementRoleKeys.MINER);
				})
				.orElse(false);
	}

	private static boolean isSwordPriorityTarget(Villager villager) {
		Holder<VillagerProfession> profession = villager.getVillagerData().profession();
		return profession.is(LiveVillagesVillagerProfessions.GUARD)
			|| profession.is(VillagerProfession.ARMORER)
			|| profession.is(VillagerProfession.WEAPONSMITH)
			|| profession.is(VillagerProfession.SHEPHERD)
			|| profession.unwrapKey()
				.map(key -> {
					String path = key.identifier().getPath();
					return path.equals(SettlementRoleKeys.PORTMASTER)
						|| path.equals(SettlementRoleKeys.ROADWRIGHT);
				})
				.orElse(false);
	}

	private static boolean isToolPriorityTarget(Villager villager) {
		Holder<VillagerProfession> profession = villager.getVillagerData().profession();
		return profession.is(VillagerProfession.TOOLSMITH)
			|| profession.is(VillagerProfession.MASON)
			|| profession.unwrapKey()
				.map(key -> {
					String path = key.identifier().getPath();
					return path.equals(SettlementRoleKeys.MINER)
						|| path.equals(SettlementRoleKeys.ROADWRIGHT)
						|| path.equals(SettlementRoleKeys.FORESTER)
						|| path.equals(SettlementRoleKeys.CARPENTER);
				})
				.orElse(false);
	}

	private static boolean needsSword(Villager villager) {
		ItemStack mainHand = villager.getMainHandItem();
		return mainHand.isEmpty() || mainHand.is(Items.WOODEN_SWORD) || mainHand.is(Items.STONE_SWORD);
	}

	private static boolean needsPickaxe(Villager villager) {
		ItemStack mainHand = villager.getMainHandItem();
		return mainHand.isEmpty() || mainHand.is(Items.WOODEN_PICKAXE) || mainHand.is(Items.STONE_PICKAXE);
	}

	private static int combatGearPriority(Villager villager) {
		Holder<VillagerProfession> profession = villager.getVillagerData().profession();

		if (profession.is(LiveVillagesVillagerProfessions.GUARD)) {
			return 0;
		}

		return profession.unwrapKey()
			.map(key -> {
				String path = key.identifier().getPath();

				if (path.equals(SettlementRoleKeys.ROADWRIGHT)) {
					return 1;
				}

				if (path.equals(SettlementRoleKeys.PORTMASTER)) {
					return 2;
				}

				if (path.equals(SettlementRoleKeys.MINER) || path.equals(SettlementRoleKeys.FORESTER)) {
					return 3;
				}

				return 5;
			})
			.orElse(5);
	}

	private static int swordPriority(Villager villager) {
		Holder<VillagerProfession> profession = villager.getVillagerData().profession();

		if (profession.is(LiveVillagesVillagerProfessions.GUARD)) {
			return 0;
		}

		return profession.unwrapKey()
			.map(key -> key.identifier().getPath().equals(SettlementRoleKeys.ROADWRIGHT) ? 1 : 5)
			.orElse(5);
	}

	private static int toolPriority(Villager villager) {
		Holder<VillagerProfession> profession = villager.getVillagerData().profession();

		if (profession.unwrapKey().map(key -> key.identifier().getPath().equals(SettlementRoleKeys.MINER)).orElse(false)) {
			return 0;
		}

		if (profession.unwrapKey().map(key -> key.identifier().getPath().equals(SettlementRoleKeys.ROADWRIGHT)).orElse(false)) {
			return 1;
		}

		if (profession.unwrapKey().map(key -> key.identifier().getPath().equals(SettlementRoleKeys.FORESTER)).orElse(false)) {
			return 2;
		}

		if (profession.unwrapKey().map(key -> key.identifier().getPath().equals(SettlementRoleKeys.CARPENTER)).orElse(false)) {
			return 3;
		}

		return profession.is(VillagerProfession.MASON) ? 4 : 5;
	}

	private static Optional<ProductionPlan> planIfAvailable(
		String taskKey,
		String outputGoodsKey,
		int outputAmount,
		Map<String, Integer> inputs,
		Map<String, Integer> stock
	) {
		return hasInputs(stock, inputs)
			? Optional.of(new ProductionPlan(taskKey, outputGoodsKey, outputAmount, inputs))
			: Optional.empty();
	}

	private static Optional<ProductionPlan> smithPlanIfAvailable(
		String taskKey,
		String outputGoodsKey,
		Map<String, Integer> baseInputs,
		Map<String, Integer> stock,
		boolean anvilSupportedSmithy
	) {
		int outputAmount = anvilSupportedSmithy ? 2 : 1;
		Map<String, Integer> inputs = multiplyInputs(baseInputs, outputAmount);
		String boostedTaskKey = anvilSupportedSmithy ? taskKey + "_with_anvil" : taskKey;
		return planIfAvailable(boostedTaskKey, outputGoodsKey, outputAmount, inputs, stock);
	}

	private static Map<String, Integer> multiplyInputs(Map<String, Integer> inputs, int multiplier) {
		if (multiplier <= 1) {
			return inputs;
		}

		Map<String, Integer> multiplied = new LinkedHashMap<>();

		for (Map.Entry<String, Integer> input : inputs.entrySet()) {
			multiplied.put(input.getKey(), input.getValue() * multiplier);
		}

		return Map.copyOf(multiplied);
	}

	private static boolean isSmithRole(String roleKey) {
		return roleKey.equals(SettlementRoleKeys.ARMORER)
			|| roleKey.equals(SettlementRoleKeys.TOOLSMITH)
			|| roleKey.equals(SettlementRoleKeys.WEAPONSMITH);
	}

	private static boolean hasInputs(Map<String, Integer> stock, Map<String, Integer> inputs) {
		for (Map.Entry<String, Integer> entry : inputs.entrySet()) {
			if (stock.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
				return false;
			}
		}

		return true;
	}

	private static boolean performProduction(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		String roleKey,
		Villager villager,
		ProductionPlan plan
	) {
		if (!hasInputs(stock, plan.inputs())) {
			return false;
		}

		for (Map.Entry<String, Integer> input : plan.inputs().entrySet()) {
			if (!SettlementGoods.consumeGoods(stock, input.getKey(), input.getValue())) {
				return false;
			}
		}

		if (!plan.outputGoodsKey().isBlank() && plan.outputAmount() > 0) {
			SettlementGoods.addGoods(stock, plan.outputGoodsKey(), plan.outputAmount());
		}
		for (Map.Entry<String, Integer> input : plan.inputs().entrySet()) {
			SettlementProfessionReports.recordConsumed(level, settlement, roleKey, villager, input.getKey(), input.getValue());
		}
		if (!plan.outputGoodsKey().isBlank() && plan.outputAmount() > 0) {
			SettlementProfessionReports.recordProduced(level, settlement, roleKey, villager, plan.outputGoodsKey(), plan.outputAmount());
		}
		SettlementProfessionReports.recordAccomplished(level, settlement, roleKey, villager, plan.taskKey().replace('_', ' '));
		return true;
	}

	private static boolean performEquipmentSupport(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		String roleKey,
		Villager worker,
		EquipmentPlan plan
	) {
		if (!SettlementGoods.consumeGoods(stock, plan.goodsKey(), 1)) {
			return false;
		}

		plan.target().setItemSlot(plan.slot(), plan.stack().copy());
		SettlementProfessionReports.recordConsumed(level, settlement, roleKey, worker, plan.goodsKey(), 1);
		SettlementProfessionReports.recordAccomplished(level, settlement, roleKey, worker, plan.taskKey());
		return true;
	}

	private static Optional<BlockPos> workPosFor(ServerLevel level, SettlementState settlement, Villager villager, ProfessionWorkConfig config) {
		Optional<BlockPos> heldJobSite = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
			.filter(globalPos -> globalPos.dimension().equals(level.dimension()))
			.map(GlobalPos::pos)
			.map(BlockPos::immutable);

		if (heldJobSite.isPresent()) {
			return heldJobSite;
		}

		return level.getPoiManager().findAllClosestFirstWithType(
			config.poiPredicate(),
			pos -> true,
			settlement.center(),
			SettlementVillagers.settlementRadiusBlocks(settlement),
			PoiManager.Occupancy.ANY
		)
			.map(com.mojang.datafixers.util.Pair::getSecond)
			.map(BlockPos::immutable)
			.findFirst();
	}

	private static boolean isWithinWorkReach(Villager villager, BlockPos workPos) {
		return villager.blockPosition().distSqr(workPos) <= WORK_REACH_DISTANCE_SQUARED;
	}

	private static String stockSummary(String roleKey, Map<String, Integer> stock) {
		List<String> keys = switch (roleKey) {
			case SettlementRoleKeys.CARTOGRAPHER -> List.of();
			case SettlementRoleKeys.CLERIC -> List.of("glass_bottle", "nether_wart", "glistering_melon_slice");
			case SettlementRoleKeys.LIBRARIAN -> List.of("paper", "leather", "book", "planks");
			case SettlementRoleKeys.LEATHERWORKER -> List.of("leather");
			case SettlementRoleKeys.SHEPHERD -> List.of("wool", "planks");
			case SettlementRoleKeys.ARMORER -> List.of("iron_ingot", "planks");
			case SettlementRoleKeys.TOOLSMITH, SettlementRoleKeys.WEAPONSMITH -> List.of("iron_ingot", "stick");
			default -> List.of();
		};
		List<String> parts = new ArrayList<>();

		for (String key : keys) {
			parts.add(key + "=" + stock.getOrDefault(key, 0));
		}

		return String.join(" ", parts);
	}

	private static String productionKey(ServerLevel level, SettlementState settlement, Villager villager, String roleKey) {
		return level.dimension().identifier() + "|" + settlement.id() + "|" + villager.getUUID() + "|" + roleKey;
	}

	private static String equipmentKey(ServerLevel level, SettlementState settlement, Villager villager, String roleKey) {
		return productionKey(level, settlement, villager, roleKey) + "|equipment";
	}

	private record ProfessionWorkConfig(
		String roleKey,
		net.minecraft.resources.ResourceKey<VillagerProfession> profession,
		Predicate<Holder<PoiType>> poiPredicate
	) {
	}

	private record ProductionPlan(String taskKey, String outputGoodsKey, int outputAmount, Map<String, Integer> inputs) {
		private ProductionPlan {
			inputs = new LinkedHashMap<>(inputs);
		}
	}

	private record EquipmentPlan(String taskKey, Villager target, EquipmentSlot slot, String goodsKey, ItemStack stack) {
	}

	private record TimedTask(String taskKey, long tick) {
	}

	private record ClericHealingResult(boolean handled, boolean stockChanged) {
		private static final ClericHealingResult NOT_HANDLED = new ClericHealingResult(false, false);
		private static final ClericHealingResult HANDLED_WITHOUT_STOCK_CHANGE = new ClericHealingResult(true, false);
		private static final ClericHealingResult HANDLED_WITH_STOCK_CHANGE = new ClericHealingResult(true, true);
	}
}
