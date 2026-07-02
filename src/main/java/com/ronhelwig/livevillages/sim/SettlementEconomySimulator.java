package com.ronhelwig.livevillages.sim;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.server.level.ServerLevel;

public final class SettlementEconomySimulator {
	private static final List<String> FOOD_PRIORITY = List.of(
		"bread",
		"baked_potato",
		"pumpkin_pie",
		"cookie",
		"cake",
		"golden_apple",
		"carrot",
		"potato",
		"beetroot",
		"wheat",
		"cod",
		"beef",
		"mutton",
		"pork"
	);
	private static final double FOOD_ITEMS_PER_PERSON_PER_DAY = 2.0D;
	private static final int ABANDONED_RECOVERY_FOOD_TARGET = 32;
	private static final int ABANDONED_RECOVERY_MIN_FOOD = 12;
	private static final double ABANDONED_RECOVERY_PROGRESS_PER_DAY = 0.35D;

	private SettlementEconomySimulator() {
	}

	public static SimulationResult advanceSettlement(
		SettlementState settlement,
		List<RouteState> routes,
		Collection<SettlementState> allSettlements,
		long currentTick,
		ServerLevel level,
		boolean loadedSettlement
	) {
		long elapsedTicks = Math.max(0L, currentTick - settlement.lastUpdateTick());

		if (elapsedTicks < SettlementEconomyRules.MIN_SIMULATION_TICKS) {
			return new SimulationResult(settlement, List.of(), 0);
		}

		double elapsedDays = Math.min(SettlementEconomyRules.MAX_CATCH_UP_DAYS, elapsedTicks / SettlementEconomyRules.TICKS_PER_DAY);

		if (elapsedDays <= 0.0D) {
			return new SimulationResult(settlement, List.of(), 0);
		}

		SettlementState simulationSettlement = settlement;
		SettlementConstruction.InfrastructureSurvey infrastructure = level != null && SettlementVillagers.usesActualVillagers(settlement)
			? SettlementConstruction.survey(level, settlement)
			: SettlementConstruction.InfrastructureSurvey.empty();

		if (loadedSettlement && infrastructure.available()) {
			int reconciledHousing = settlement.kind() == SettlementKind.CUSTOM
				? infrastructure.housingCapacity()
				: Math.max(simulationSettlement.housingCapacity(), infrastructure.housingCapacity());

			if (reconciledHousing != simulationSettlement.housingCapacity()) {
				simulationSettlement = simulationSettlement.withHousingCapacity(reconciledHousing);
			}
		}

		int population = simulationSettlement.totalPopulation();

		if (population <= 0) {
			GrowthResult recoveryResult = applyAbandonedRecovery(simulationSettlement, elapsedDays);
			SettlementState updatedSettlement = simulationSettlement.withSimulationState(
				Map.of(),
				simulationSettlement.wealth(),
				simulationSettlement.stock(),
				simulationSettlement.housingCapacity(),
				simulationSettlement.comfort(),
				simulationSettlement.security(),
				simulationSettlement.defenseLevel(),
				recoveryResult.progress(),
				simulationSettlement.projects(),
				currentTick
			);
			return new SimulationResult(updatedSettlement, List.of(), recoveryResult.requestedVillagerSpawns());
		}

		Map<String, Integer> stock = new LinkedHashMap<>(simulationSettlement.stock());
		Map<String, Integer> wealth = new LinkedHashMap<>(simulationSettlement.wealth());
		Map<String, Integer> populationMap = new LinkedHashMap<>(simulationSettlement.population());
		Map<String, Integer> nearbyProfessions = loadedSettlement && level != null && SettlementVillagers.usesActualVillagers(settlement)
			? SettlementVillagers.censusPopulation(level, settlement)
			: Map.of();

		applyProduction(simulationSettlement, routes, stock, wealth, elapsedDays, settlement.lastUpdateTick(), currentTick, level, loadedSettlement, infrastructure, nearbyProfessions);
		SupplyState foodSupply = consumeFood(stock, population, elapsedDays);
		SupplyState upkeepSupply = consumeUpkeep(stock, simulationSettlement.housingCapacity(), population, elapsedDays);

		List<SettlementProject> plannedProjects = planProjects(simulationSettlement, routes, allSettlements, infrastructure, level);
		ProjectAdvanceResult projectResult = advanceProjects(
			simulationSettlement,
			plannedProjects,
			routes,
			allSettlements,
			stock,
			elapsedDays,
			currentTick,
			level,
			loadedSettlement,
			infrastructure
		);

		List<RouteState> activeRoutes = new ArrayList<>(routes);
		activeRoutes.addAll(projectResult.createdRoutes());

		double security = computeSecurity(simulationSettlement, infrastructure, activeRoutes, projectResult.defenseLevel(), population, elapsedDays);
		double comfort = computeComfort(
			simulationSettlement,
			projectResult.housingCapacity(),
			population,
			foodSupply,
			upkeepSupply,
			security,
			stock,
			elapsedDays
		);
		GrowthResult growthResult = applyGrowth(
			simulationSettlement,
			population,
			projectResult.housingCapacity(),
			foodSupply,
			comfort,
			security,
			elapsedDays
		);
		double growthProgress = growthResult.progress();

		cleanMap(stock);
		cleanMap(wealth);
		cleanMap(populationMap);

		SettlementState updatedSettlement = simulationSettlement.withSimulationState(
			populationMap,
			wealth,
			stock,
			projectResult.housingCapacity(),
			comfort,
			security,
			projectResult.defenseLevel(),
			growthProgress,
			projectResult.projects(),
			currentTick
		);
		return new SimulationResult(updatedSettlement, projectResult.createdRoutes(), growthResult.requestedVillagerSpawns());
	}

	private static void applyProduction(
		SettlementState settlement,
		List<RouteState> routes,
		Map<String, Integer> stock,
		Map<String, Integer> wealth,
		double elapsedDays,
		long previousTick,
		long currentTick,
		ServerLevel level,
		boolean loadedSettlement,
		SettlementConstruction.InfrastructureSurvey infrastructure,
		Map<String, Integer> nearbyProfessions
	) {
		int population = settlement.totalPopulation();
		double civilianScale = settlement.kind() == SettlementKind.OUTPOST ? 0.65D : 1.0D;
		double tradeScale = settlement.kind() == SettlementKind.HARBOR ? 1.2D : 1.0D;
		boolean useLoadedVillagerFoodWork = loadedSettlement && level != null && SettlementVillagers.usesActualVillagers(settlement);
		int farmers = effectiveRoleCount(settlement, nearbyProfessions, SettlementRoleKeys.FARMER, useLoadedVillagerFoodWork);
		int bakers = effectiveRoleCount(settlement, nearbyProfessions, SettlementRoleKeys.BAKER, useLoadedVillagerFoodWork);
		int butchers = effectiveRoleCount(settlement, nearbyProfessions, SettlementRoleKeys.BUTCHER, useLoadedVillagerFoodWork);
		int carpenters = effectiveRoleCount(settlement, nearbyProfessions, SettlementRoleKeys.CARPENTER, useLoadedVillagerFoodWork);
		int masons = effectiveRoleCount(settlement, nearbyProfessions, SettlementRoleKeys.MASON, useLoadedVillagerFoodWork);
		int constructionSupport = effectiveRoleCount(settlement, nearbyProfessions, SettlementRoleKeys.CONSTRUCTION_SUPPORT, useLoadedVillagerFoodWork);
		int fishermen = effectiveRoleCount(settlement, nearbyProfessions, SettlementRoleKeys.FISHERMAN, useLoadedVillagerFoodWork);
		int gardeners = effectiveRoleCount(settlement, nearbyProfessions, SettlementRoleKeys.GARDENER, useLoadedVillagerFoodWork);
		int beekeepers = effectiveRoleCount(settlement, nearbyProfessions, SettlementRoleKeys.BEEKEEPER, useLoadedVillagerFoodWork);
		int foresters = effectiveRoleCount(settlement, nearbyProfessions, SettlementRoleKeys.FORESTER, useLoadedVillagerFoodWork);
		int miners = effectiveRoleCount(settlement, nearbyProfessions, SettlementRoleKeys.MINER, useLoadedVillagerFoodWork);
		int trademasters = effectiveRoleCount(settlement, nearbyProfessions, SettlementRoleKeys.TRADEMASTER, useLoadedVillagerFoodWork);
		int portmasters = effectiveRoleCount(settlement, nearbyProfessions, SettlementRoleKeys.PORTMASTER, useLoadedVillagerFoodWork);
		int clerics = effectiveRoleCount(settlement, nearbyProfessions, SettlementRoleKeys.CLERIC, useLoadedVillagerFoodWork);
		int librarians = effectiveRoleCount(settlement, nearbyProfessions, SettlementRoleKeys.LIBRARIAN, useLoadedVillagerFoodWork);
		int leatherworkers = effectiveRoleCount(settlement, nearbyProfessions, SettlementRoleKeys.LEATHERWORKER, useLoadedVillagerFoodWork);
		int shepherds = effectiveRoleCount(settlement, nearbyProfessions, SettlementRoleKeys.SHEPHERD, useLoadedVillagerFoodWork);
		int armorers = effectiveRoleCount(settlement, nearbyProfessions, SettlementRoleKeys.ARMORER, useLoadedVillagerFoodWork);
		int toolsmiths = effectiveRoleCount(settlement, nearbyProfessions, SettlementRoleKeys.TOOLSMITH, useLoadedVillagerFoodWork);
		int weaponsmiths = effectiveRoleCount(settlement, nearbyProfessions, SettlementRoleKeys.WEAPONSMITH, useLoadedVillagerFoodWork);
		SettlementTradeRange.TradeRangeProfile tradeRange = SettlementTradeRange.profile(settlement, infrastructure);

		addGoods(stock, "wheat", scaledAmount((population * 2.0D * civilianScale) + (useLoadedVillagerFoodWork ? 0.0D : farmers * 5.0D), elapsedDays));
		addGoods(stock, "bread", scaledAmount((population * 0.75D * civilianScale) + (useLoadedVillagerFoodWork || bakers > 0 ? 0.0D : farmers * 1.5D) + trademasters * 0.5D, elapsedDays));
		addGoods(stock, "carrot", scaledAmount((population * 0.35D * civilianScale) + (useLoadedVillagerFoodWork ? 0.0D : farmers * 1.25D), elapsedDays));
		addGoods(stock, "potato", scaledAmount((population * 0.35D * civilianScale) + (useLoadedVillagerFoodWork ? 0.0D : farmers * 1.25D), elapsedDays));
		addGoods(stock, "beetroot", scaledAmount((population * 0.18D * civilianScale) + (useLoadedVillagerFoodWork ? 0.0D : farmers * 0.9D), elapsedDays));
		addRoleGoods(level, settlement, stock, SettlementRoleKeys.GARDENER, "egg", scaledAmount(useLoadedVillagerFoodWork ? 0.0D : gardeners * 1.0D, elapsedDays));
		addRoleGoods(level, settlement, stock, SettlementRoleKeys.GARDENER, "flower", scaledAmount(useLoadedVillagerFoodWork ? 0.0D : gardeners * 0.75D, elapsedDays));
		addGoods(stock, "beef", scaledAmount(useLoadedVillagerFoodWork ? 0.0D : butchers * 1.5D * civilianScale, elapsedDays));
		addGoods(stock, "mutton", scaledAmount(useLoadedVillagerFoodWork ? 0.0D : shepherds * 0.6D * civilianScale, elapsedDays));
		addGoods(stock, "pork", scaledAmount(useLoadedVillagerFoodWork ? 0.0D : butchers * 1.0D * civilianScale, elapsedDays));
		addGoods(stock, "cod", scaledAmount(useLoadedVillagerFoodWork ? 0.0D : SettlementFishermanWork.dailyCatchRate(fishermen, infrastructure, 0), elapsedDays));
		addGoods(stock, "logs", scaledAmount(population * 0.5D, elapsedDays));
		addRoleGoods(level, settlement, stock, SettlementRoleKeys.FORESTER, "logs", scaledAmount(foresters * 5.0D, elapsedDays));
		addRoleGoods(level, settlement, stock, SettlementRoleKeys.CARPENTER, "logs", scaledAmount(carpenters * 1.0D, elapsedDays));
		addGoods(stock, "logs", scaledAmount(constructionSupport * 1.0D, elapsedDays));
		addGoods(stock, "planks", scaledAmount((population * 0.35D) + foresters * 2.0D + constructionSupport * 4.0D, elapsedDays));
		addRoleGoods(level, settlement, stock, SettlementRoleKeys.CARPENTER, "planks", scaledAmount(carpenters * 4.0D, elapsedDays));
		addRoleGoods(level, settlement, stock, SettlementRoleKeys.FORESTER, "stick", scaledAmount(foresters * 2.0D, elapsedDays));
		addRoleGoods(level, settlement, stock, SettlementRoleKeys.FORESTER, "apple", scaledAmount(foresters * 0.5D, elapsedDays));
		addRoleGoods(level, settlement, stock, SettlementRoleKeys.FORESTER, "oak_sapling", scaledAmount(foresters * 1.0D, elapsedDays));
		addRoleGoods(level, settlement, stock, SettlementRoleKeys.BEEKEEPER, "honey_bottle", scaledAmount(useLoadedVillagerFoodWork ? 0.0D : beekeepers * 1.5D, elapsedDays));
		addRoleGoods(level, settlement, stock, SettlementRoleKeys.BEEKEEPER, "honeycomb", scaledAmount(useLoadedVillagerFoodWork ? 0.0D : beekeepers * 1.0D, elapsedDays));
		addRoleGoods(level, settlement, stock, SettlementRoleKeys.BEEKEEPER, "candle", scaledAmount(useLoadedVillagerFoodWork ? 0.0D : beekeepers * 0.5D, elapsedDays));
		addGoods(stock, "cobblestone", scaledAmount((population * 0.45D) + constructionSupport * 1.5D, elapsedDays));
		addRoleMinedGoods(level, settlement, stock, SettlementRoleKeys.MINER, "cobblestone", scaledAmount(miners * 6.0D, elapsedDays));
		addRoleGoods(level, settlement, stock, SettlementRoleKeys.MASON, "cobblestone", scaledAmount(masons * 1.5D, elapsedDays));
		addRoleMinedGoods(level, settlement, stock, SettlementRoleKeys.MINER, "coal", scaledAmount(miners * 0.45D, elapsedDays));
		addRoleMinedGoods(level, settlement, stock, SettlementRoleKeys.MINER, "raw_iron", scaledAmount(miners * 1.5D, elapsedDays));
		addRoleMinedGoods(level, settlement, stock, SettlementRoleKeys.MINER, "raw_copper", scaledAmount(miners * 0.75D, elapsedDays));
		addGoods(
			wealth,
			"emerald",
			scaledAmount(
				(population * 0.3D)
					+ trademasters * 2.0D
					+ routes.size() * (tradeScale + tradeRange.routeTradeBonus() + harborRouteTradeBonus(infrastructure, portmasters) + tradingPostRouteTradeBonus(infrastructure))
					+ tradeRange.localTradeBonus()
					+ harborLocalTradeBonus(infrastructure, portmasters),
				elapsedDays
			)
		);
		addGoods(
			wealth,
			"emerald",
			scaledAmount(
				tradingPostLocalTradeBonus(infrastructure),
				elapsedDays
			)
		);

		int effectiveBakers = useLoadedVillagerFoodWork
			? nearbyProfessions.getOrDefault(SettlementRoleKeys.BAKER, 0)
			: bakers;
		SettlementBakerWork.applyBakingProduction(level, settlement, stock, elapsedDays, effectiveBakers);

		if (!useLoadedVillagerFoodWork) {
			applyAbstractVanillaProfessionProduction(
				settlement,
				stock,
				elapsedDays,
				clerics,
				librarians,
				leatherworkers,
				shepherds,
				armorers,
				toolsmiths,
				weaponsmiths,
				infrastructure.anvilSupportedSmithies() > 0
			);
		}

		if (useLoadedVillagerFoodWork) {
			SettlementFarmerWork.applyLoadedFarmerWork(level, settlement, stock, elapsedDays);
			SettlementButcherWork.applyLoadedButcherWork(level, settlement, stock, butchers, previousTick, currentTick);
			SettlementShepherdWork.applyLoadedShepherdWork(level, settlement, stock, shepherds, previousTick, currentTick);
		}

		SettlementRefining.refineTowardSettlementTargets(settlement, stock);
	}

	private static void applyAbstractVanillaProfessionProduction(
		SettlementState settlement,
		Map<String, Integer> stock,
		double elapsedDays,
		int clerics,
		int librarians,
		int leatherworkers,
		int shepherds,
		int armorers,
		int toolsmiths,
		int weaponsmiths,
		boolean anvilSupportedSmithy
	) {
		produceFromRecipe(stock, "healing_potion", scaledAmount(clerics, elapsedDays), Map.of("glass_bottle", 1, "nether_wart", 1, "glistering_melon_slice", 1));

		int librarianActions = scaledAmount(librarians, elapsedDays);
		int bookshelfNeed = Math.max(0, SettlementEconomyRules.targetForGoods(settlement, "bookshelf") - stock.getOrDefault("bookshelf", 0));
		int bookshelves = produceFromRecipe(stock, "bookshelf", Math.min(librarianActions, bookshelfNeed), Map.of("book", 3, "planks", 6));
		produceFromRecipe(stock, "book", librarianActions - bookshelves, Map.of("paper", 3, "leather", 1));

		int leatherworkerActions = scaledAmount(leatherworkers, elapsedDays);
		leatherworkerActions -= produceFromRecipe(stock, "leather_chestplate", leatherworkerActions, Map.of("leather", 8));
		leatherworkerActions -= produceFromRecipe(stock, "leather_leggings", leatherworkerActions, Map.of("leather", 7));
		leatherworkerActions -= produceFromRecipe(stock, "leather_helmet", leatherworkerActions, Map.of("leather", 5));
		produceFromRecipe(stock, "leather_boots", leatherworkerActions, Map.of("leather", 4));

		int shepherdActions = scaledAmount(shepherds, elapsedDays);
		int bedNeed = Math.max(0, SettlementEconomyRules.targetForGoods(settlement, "bed") - stock.getOrDefault("bed", 0));
		int beds = produceFromRecipe(stock, "bed", Math.min(shepherdActions, bedNeed), Map.of("wool", 3, "planks", 3));
		addGoods(stock, "wool", Math.max(0, shepherdActions - beds));

		int smithyMultiplier = anvilSupportedSmithy ? 2 : 1;
		produceFromRecipe(stock, "iron_chestplate", scaledAmount(armorers * smithyMultiplier, elapsedDays), Map.of("iron_ingot", 8));
		produceFromRecipe(stock, "iron_pickaxe", scaledAmount(toolsmiths * smithyMultiplier, elapsedDays), Map.of("iron_ingot", 3, "stick", 2));
		produceFromRecipe(stock, "iron_sword", scaledAmount(weaponsmiths * smithyMultiplier, elapsedDays), Map.of("iron_ingot", 2, "stick", 1));
	}

	private static int produceFromRecipe(Map<String, Integer> stock, String outputGoodsKey, int requested, Map<String, Integer> inputs) {
		int produced = 0;

		while (produced < requested && hasRecipeInputs(stock, inputs)) {
			for (Map.Entry<String, Integer> input : inputs.entrySet()) {
				consumeGoods(stock, input.getKey(), input.getValue());
			}

			addGoods(stock, outputGoodsKey, 1);
			produced++;
		}

		return produced;
	}

	private static boolean hasRecipeInputs(Map<String, Integer> stock, Map<String, Integer> inputs) {
		for (Map.Entry<String, Integer> input : inputs.entrySet()) {
			if (stock.getOrDefault(input.getKey(), 0) < input.getValue()) {
				return false;
			}
		}

		return true;
	}

	private static int effectiveRoleCount(SettlementState settlement, Map<String, Integer> nearbyProfessions, String roleKey, boolean useNearbyProfessions) {
		if (useNearbyProfessions) {
			return Math.max(0, nearbyProfessions.getOrDefault(roleKey, 0));
		}

		return roleCount(settlement, roleKey);
	}

	private static void addRoleGoods(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, String roleKey, String goodsKey, int amount) {
		addGoods(stock, goodsKey, amount);
	}

	private static void addRoleMinedGoods(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, String roleKey, String goodsKey, int amount) {
		addGoods(stock, goodsKey, amount);
	}

	private static SupplyState consumeFood(Map<String, Integer> stock, int population, double elapsedDays) {
		int requiredFood = Math.max(0, scaledAmount(population * FOOD_ITEMS_PER_PERSON_PER_DAY, elapsedDays));

		if (requiredFood <= 0) {
			return new SupplyState(0, 0);
		}

		int consumed = 0;

		for (String goodsKey : FOOD_PRIORITY) {
			if (consumed >= requiredFood) {
				break;
			}

			int available = stock.getOrDefault(goodsKey, 0);
			int used = Math.min(requiredFood - consumed, available);

			if (used > 0) {
				stock.put(goodsKey, available - used);
				consumed += used;
			}
		}

		return new SupplyState(consumed, requiredFood);
	}

	private static SupplyState consumeUpkeep(Map<String, Integer> stock, int housingCapacity, int population, double elapsedDays) {
		double structureLoad = Math.max(population, housingCapacity * 0.5D);
		int requiredLogs = scaledAmount(structureLoad * 0.15D, elapsedDays);
		int requiredPlanks = scaledAmount(structureLoad * 0.35D, elapsedDays);
		int requiredCobblestone = scaledAmount(structureLoad * 0.25D, elapsedDays);
		int requiredTotal = requiredLogs + requiredPlanks + requiredCobblestone;

		if (requiredTotal <= 0) {
			return new SupplyState(0, 0);
		}

		int fulfilled = 0;
		fulfilled += consumeGoods(stock, "logs", requiredLogs);
		fulfilled += consumeGoods(stock, "planks", requiredPlanks);
		fulfilled += consumeGoods(stock, "cobblestone", requiredCobblestone);
		return new SupplyState(fulfilled, requiredTotal);
	}

	private static List<SettlementProject> planProjects(
		SettlementState settlement,
		List<RouteState> routes,
		Collection<SettlementState> allSettlements,
		SettlementConstruction.InfrastructureSurvey infrastructure,
		ServerLevel level
	) {
		List<SettlementProject> projects = new ArrayList<>();

		for (SettlementProject project : settlement.projects()) {
			if (project.type() == SettlementProjectType.TRADING_POST && infrastructure.tradingPosts() + infrastructure.incompleteTradingPosts() > 0) {
				continue;
			}

			if (project.type() == SettlementProjectType.DOCK && infrastructure.incompleteDocks() > 0) {
				continue;
			}

			if (project.type() == SettlementProjectType.CARPENTER_WORKSHOP
				&& infrastructure.carpenterBenches() + infrastructure.incompleteCarpenterWorkshops() > 0) {
				continue;
			}

			if (project.type() == SettlementProjectType.ROAD) {
				if (project.targetSettlementId().isBlank() || findSettlementById(allSettlements, project.targetSettlementId()).isEmpty()) {
					continue;
				}

				if (routeExistsTo(routes, project.targetSettlementId())) {
					continue;
				}
			}

			projects.add(project);
		}

		if (settlement.kind() == SettlementKind.OUTPOST) {
			return planOutpostProjects(settlement, projects);
		}

		int population = settlement.totalPopulation();
		int effectiveHousing = Math.max(settlement.housingCapacity(), infrastructure.housingCapacity()) + infrastructure.incompleteHousingCapacity();
		boolean needsHousing = population > 0 && effectiveHousing < population + 1;

		if (infrastructure.available()) {
			int desiredFarmSites = SettlementConstruction.desiredFarmSites(settlement, infrastructure.farmAreas());
			int pendingFarmStarterAnchors = level == null ? 0 : SettlementConstruction.pendingFarmStarterAnchors(level, settlement);
			int neededComposterProjects = Math.max(0, desiredFarmSites - infrastructure.coveredFarmAreas() - pendingFarmStarterAnchors);
			projects = limitProjectType(projects, SettlementProjectType.COMPOSTER, neededComposterProjects);
		}

		if (population > 0
			&& infrastructure.tradingPosts() + infrastructure.incompleteTradingPosts() + countProjectType(projects, SettlementProjectType.TRADING_POST) < 1) {
			projects.add(new SettlementProject(nextProjectId(projects, "trading-post"), SettlementProjectType.TRADING_POST, "", 0.0D, 0.35D));
		}

		if (needsHousing && !hasProjectType(projects, SettlementProjectType.HOUSING)) {
			projects.add(new SettlementProject(nextProjectId(projects, "housing"), SettlementProjectType.HOUSING, "", 0.0D, 1.2D));
		}

		if (infrastructure.available()
			&& population > 0
			&& infrastructure.carpenterBenches()
				+ infrastructure.incompleteCarpenterWorkshops()
				+ countProjectType(projects, SettlementProjectType.CARPENTER_WORKSHOP) < 1) {
			projects.add(new SettlementProject(nextProjectId(projects, "carpenter-workshop"), SettlementProjectType.CARPENTER_WORKSHOP, "", 0.0D, 0.65D));
		}

		if (infrastructure.available() && !needsHousing) {
			int desiredFarmSites = SettlementConstruction.desiredFarmSites(settlement, infrastructure.farmAreas());
			int pendingFarmStarterAnchors = level == null ? 0 : SettlementConstruction.pendingFarmStarterAnchors(level, settlement);

			if (desiredFarmSites > 0
				&& infrastructure.coveredFarmAreas() + pendingFarmStarterAnchors + countProjectType(projects, SettlementProjectType.COMPOSTER) < desiredFarmSites) {
				projects.add(new SettlementProject(nextProjectId(projects, "composter"), SettlementProjectType.COMPOSTER, "", 0.0D, 0.55D));
			}

			int totalStock = settlement.stock().values().stream()
				.mapToInt(Integer::intValue)
				.sum();

			if (totalStock >= 48 && infrastructure.storageBlocks() + countProjectType(projects, SettlementProjectType.STORAGE) < 1) {
				projects.add(new SettlementProject(nextProjectId(projects, "storage"), SettlementProjectType.STORAGE, "", 0.0D, 0.7D));
			}

				if (infrastructure.hasLargeWaterBody() && infrastructure.docks() + infrastructure.incompleteDocks() + countProjectType(projects, SettlementProjectType.DOCK) < 1) {
					projects.add(new SettlementProject(nextProjectId(projects, "dock"), SettlementProjectType.DOCK, "", 0.0D, 0.85D));
				}

			if (infrastructure.hasLargeWaterBody()
				&& infrastructure.docks() > 0
				&& population >= 6
				&& infrastructure.lighthouses() + infrastructure.incompleteLighthouses() + countProjectType(projects, SettlementProjectType.LIGHTHOUSE) < 1) {
				projects.add(new SettlementProject(nextProjectId(projects, "lighthouse"), SettlementProjectType.LIGHTHOUSE, "", 0.0D, 1.1D));
			}
		}

		boolean needsDefenseRecovery = settlement.security() < 0.45D;
		if (population >= 3 && (settlement.defenseLevel() < desiredDefenseLevel(population) || needsDefenseRecovery) && !hasProjectType(projects, SettlementProjectType.DEFENSE)) {
			projects.add(new SettlementProject(nextProjectId(projects, "defense"), SettlementProjectType.DEFENSE, "", 0.0D, 0.8D));
		}

		maybeQueueRoadProject(settlement, projects, routes, allSettlements, infrastructure, level);
		return projects;
	}

	private static List<SettlementProject> planOutpostProjects(SettlementState settlement, List<SettlementProject> existingProjects) {
		List<SettlementProject> projects = new ArrayList<>();

		for (SettlementProject project : existingProjects) {
			if (project.type() == SettlementProjectType.STORAGE || project.type() == SettlementProjectType.DEFENSE) {
				projects.add(project);
			}
		}

		int totalStock = settlement.stock().values().stream()
			.mapToInt(Integer::intValue)
			.sum();
		if (totalStock >= 24 && !hasProjectType(projects, SettlementProjectType.STORAGE)) {
			projects.add(new SettlementProject(nextProjectId(projects, "storage"), SettlementProjectType.STORAGE, "", 0.0D, 0.45D));
		}

		int population = Math.max(1, settlement.totalPopulation());
		int desiredDefense = Math.max(1, desiredDefenseLevel(population) + 1);
		if (settlement.defenseLevel() < desiredDefense && !hasProjectType(projects, SettlementProjectType.DEFENSE)) {
			projects.add(new SettlementProject(nextProjectId(projects, "defense"), SettlementProjectType.DEFENSE, "", 0.0D, 0.6D));
		}

		return projects;
	}

	private static void maybeQueueRoadProject(
		SettlementState settlement,
		List<SettlementProject> projects,
		List<RouteState> routes,
		Collection<SettlementState> allSettlements,
		SettlementConstruction.InfrastructureSurvey infrastructure,
		ServerLevel level
	) {
		SettlementTradeRange.TradeRangeProfile sourceRange = SettlementTradeRange.profile(settlement, infrastructure);

		if (!canPlanLandRoutes(settlement) && !canPlanWaterRoutes(settlement, sourceRange)) {
			return;
		}

		long activeRoadProjects = projects.stream()
			.filter(project -> project.type() == SettlementProjectType.ROAD)
			.count();

		if (activeRoadProjects > 0) {
			return;
		}

		SettlementState nearestTarget = null;
		double nearestDistance = Double.POSITIVE_INFINITY;

		for (SettlementState candidate : allSettlements) {
			if (candidate.id().equals(settlement.id()) || candidate.kind() == SettlementKind.OUTPOST) {
				continue;
			}

			if (!candidate.dimension().equals(settlement.dimension())) {
				continue;
			}

			if (routeExistsTo(routes, candidate.id()) || hasRoadProjectForTarget(projects, candidate.id())) {
				continue;
			}

			double distance = Math.sqrt(settlement.center().distSqr(candidate.center()));
			SettlementConstruction.InfrastructureSurvey candidateInfrastructure = level != null
				? SettlementConstruction.survey(level, candidate)
				: SettlementConstruction.InfrastructureSurvey.empty();
			SettlementTradeRange.TradeRangeProfile candidateRange = SettlementTradeRange.profile(candidate, candidateInfrastructure);

			if (!canReachCandidate(settlement, candidate, distance, sourceRange, candidateRange) || distance >= nearestDistance) {
				continue;
			}

			nearestTarget = candidate;
			nearestDistance = distance;
		}

		if (nearestTarget == null) {
			return;
		}

		String routeId = routeIdForSettlements(settlement.id(), nearestTarget.id());
		projects.add(new SettlementProject(routeId, SettlementProjectType.ROAD, nearestTarget.id(), 0.0D, 0.75D + (nearestDistance / 768.0D)));
	}

	private static ProjectAdvanceResult advanceProjects(
		SettlementState settlement,
		List<SettlementProject> projects,
		List<RouteState> routes,
		Collection<SettlementState> allSettlements,
		Map<String, Integer> stock,
		double elapsedDays,
		long currentTick,
		ServerLevel level,
		boolean loadedSettlement,
		SettlementConstruction.InfrastructureSurvey infrastructure
	) {
		List<SettlementProject> remainingProjects = new ArrayList<>();
		List<RouteState> createdRoutes = new ArrayList<>();
		int housingCapacity = settlement.housingCapacity();
		int defenseLevel = settlement.defenseLevel();
		boolean useWorldConstruction = loadedSettlement && level != null && SettlementVillagers.usesActualVillagers(settlement);

		for (SettlementProject project : projects) {
			boolean wasComplete = project.progress() + 1.0E-6D >= project.requiredProgress();
			double newProgress = Math.min(project.requiredProgress(), project.progress() + (projectWorkRate(settlement, project.type()) * elapsedDays));
			SettlementProject progressed = project.withProgress(newProgress);

			if (newProgress + 1.0E-6D < project.requiredProgress()) {
				remainingProjects.add(progressed);
				continue;
			}

			switch (project.type()) {
				case TRADING_POST -> {
					if (useWorldConstruction) {
						remainingProjects.add(progressed);
						continue;
					}

					if (wasComplete || tryConsumeCost(stock, project.type().stockCost())) {
						remainingProjects.add(progressed);
						continue;
					}

					double blockedProgress = Math.max(0.0D, project.requiredProgress() - 0.01D);
					remainingProjects.add(progressed.withProgress(Math.min(progressed.progress(), blockedProgress)));
					continue;
				}
				case HOUSING -> {
					if (useWorldConstruction) {
						if (infrastructure.incompleteHousingCapacity() > 0) {
							int effectiveHousing = Math.max(settlement.housingCapacity(), infrastructure.housingCapacity())
								+ infrastructure.incompleteHousingCapacity();
							if (effectiveHousing >= settlement.totalPopulation() + 1) {
								continue;
							}

							double blockedProgress = Math.max(0.0D, project.requiredProgress() - 0.01D);
							remainingProjects.add(progressed.withProgress(Math.min(progressed.progress(), blockedProgress)));
							continue;
						}

						SettlementConstruction.CompletionResult completionResult = SettlementConstruction.tryCompleteProject(level, settlement, project, stock);

						if (completionResult.completed()) {
							housingCapacity += completionResult.housingCapacityDelta();
							continue;
						}
					} else if (tryConsumeCost(stock, project.type().stockCost())) {
						housingCapacity += 1;
						continue;
					}
				}
				case CARPENTER_WORKSHOP, COMPOSTER, STORAGE, DOCK, LIGHTHOUSE -> {
					if (useWorldConstruction) {
						if (SettlementConstruction.tryCompleteProject(level, settlement, project, stock).completed()) {
							continue;
						}
					} else if (tryConsumeCost(stock, project.type().stockCost())) {
						continue;
					}
				}
				case DEFENSE -> {
					if (tryConsumeCost(stock, project.type().stockCost())) {
						defenseLevel += 1;
						continue;
					}
				}
				case ROAD -> {
					Optional<SettlementState> target = findSettlementById(allSettlements, project.targetSettlementId());

					if (target.isEmpty() || routeExistsTo(routes, project.targetSettlementId()) || routeExistsTo(createdRoutes, project.targetSettlementId())) {
						continue;
					}

					int distanceBlocks = (int) Math.round(Math.sqrt(settlement.center().distSqr(target.get().center())));

					if (tryConsumeCost(stock, roadProjectCost(distanceBlocks))) {
						createdRoutes.add(createRoute(settlement, target.get(), distanceBlocks, currentTick, level));
						continue;
					}
				}
			}

			remainingProjects.add(progressed);
		}

		return new ProjectAdvanceResult(remainingProjects, createdRoutes, housingCapacity, defenseLevel);
	}

	private static double computeSecurity(
		SettlementState settlement,
		SettlementConstruction.InfrastructureSurvey infrastructure,
		List<RouteState> routes,
		int defenseLevel,
		int population,
		double elapsedDays
	) {
		int guards = roleCount(settlement, "guard");
		int fletchers = roleCount(settlement, SettlementRoleKeys.FLETCHER);
		double baseSecurity = settlement.kind() == SettlementKind.OUTPOST ? 0.35D : 0.12D;
		double defenderPressure = population <= 0 ? 0.0D : ((guards * 0.7D) + (fletchers * 0.45D)) / population;
		double routeSupport = Math.min(0.18D, routes.size() * 0.03D);
		double fortificationSupport = defenseLevel * 0.08D;
		double palisadeSupport = palisadeSecurityBonus(infrastructure);
		double lighthouseSupport = lighthouseSecurityBonus(infrastructure);
		double trophySupport = guardTrophySecurityBonus(settlement.stock());
		double targetSecurity = clamp(baseSecurity + defenderPressure + routeSupport + fortificationSupport + palisadeSupport + lighthouseSupport + trophySupport, 0.05D, 0.95D);
		double blend = clamp(elapsedDays * 0.4D, 0.15D, 1.0D);
		return clamp(settlement.security() + (targetSecurity - settlement.security()) * blend, 0.0D, 1.0D);
	}

	private static double computeComfort(
		SettlementState settlement,
		int housingCapacity,
		int population,
		SupplyState foodSupply,
		SupplyState upkeepSupply,
		double security,
		Map<String, Integer> stock,
		double elapsedDays
	) {
		double foodRatio = foodSupply.ratio();
		double upkeepRatio = upkeepSupply.ratio();
		double housingRatio = housingCapacity / (double) Math.max(1, population);
		double varietyBonus = foodVariety(stock) * 0.03D;
		double gardenerBonus = Math.min(0.08D, roleCount(settlement, SettlementRoleKeys.GARDENER) * 0.02D);
		double beekeeperBonus = Math.min(0.06D, roleCount(settlement, SettlementRoleKeys.BEEKEEPER) * 0.02D);
		double trophyBonus = guardTrophyComfortBonus(stock);
		double targetComfort = 0.55D
			+ clamp(foodRatio, 0.0D, 1.1D) * 0.25D
			+ clamp(upkeepRatio, 0.0D, 1.0D) * 0.10D
			+ clamp(housingRatio - 0.75D, 0.0D, 0.35D)
			+ security * 0.10D
			+ gardenerBonus
			+ beekeeperBonus
			+ trophyBonus
			+ varietyBonus;
		double blend = clamp(elapsedDays * 0.35D, 0.15D, 1.0D);
		return clamp(settlement.comfort() + (targetComfort - settlement.comfort()) * blend, 0.35D, 1.35D);
	}

	private static double guardTrophySecurityBonus(Map<String, Integer> stock) {
		return Math.min(0.06D, stock.getOrDefault("desecrated_enemy_banner", 0) * 0.015D);
	}

	private static double guardTrophyComfortBonus(Map<String, Integer> stock) {
		return Math.min(0.03D, stock.getOrDefault("desecrated_enemy_banner", 0) * 0.005D);
	}

	private static GrowthResult applyGrowth(
		SettlementState settlement,
		int population,
		int housingCapacity,
		SupplyState foodSupply,
		double comfort,
		double security,
		double elapsedDays
	) {
		if (settlement.kind() == SettlementKind.OUTPOST) {
			return applyOutpostGrowth(settlement, population, foodSupply, security, elapsedDays);
		}

		int freeHousing = Math.max(0, housingCapacity - population);
		double housingFactor = clamp(freeHousing / (double) Math.max(1, population), 0.0D, 1.0D);
		double foodFactor = clamp(foodSupply.ratio(), 0.0D, 1.1D);
		double comfortFactor = clamp((comfort - 0.75D) / 0.35D, 0.0D, 1.0D);
		double growthRate = 0.14D * foodFactor * (0.45D + comfortFactor * 0.35D + security * 0.20D) * housingFactor;
		double progress = settlement.growthProgress() + (growthRate * elapsedDays);

		if (freeHousing <= 0 || foodFactor < 0.75D) {
			progress = Math.max(0.0D, progress - elapsedDays * 0.08D);
		}

		progress = clamp(progress, 0.0D, Math.max(0.99D, freeHousing + 0.99D));
		int requestedVillagerSpawns = freeHousing > 0 ? Math.min(1, (int) Math.floor(progress)) : 0;
		return new GrowthResult(progress, requestedVillagerSpawns);
	}

	private static GrowthResult applyAbandonedRecovery(SettlementState settlement, double elapsedDays) {
		if (settlement.kind() == SettlementKind.OUTPOST) {
			return new GrowthResult(settlement.growthProgress(), 0);
		}

		int foodStock = totalFoodStock(settlement.stock());
		int recoveryCapacity = Math.max(0, settlement.housingCapacity());
		if (settlement.kind() == SettlementKind.CUSTOM && recoveryCapacity <= 0) {
			recoveryCapacity = 1;
		}

		double progress = settlement.growthProgress();

		if (recoveryCapacity <= 0 || foodStock < ABANDONED_RECOVERY_MIN_FOOD) {
			progress = Math.max(0.0D, progress - elapsedDays * 0.06D);
			return new GrowthResult(progress, 0);
		}

		double foodFactor = clamp(foodStock / (double) ABANDONED_RECOVERY_FOOD_TARGET, 0.0D, 1.25D);
		double securityFactor = clamp(settlement.security(), 0.15D, 1.0D);
		double comfortFactor = clamp(settlement.comfort(), 0.35D, 1.0D);
		double capacityFactor = clamp(recoveryCapacity / 2.0D, 0.5D, 1.0D);
		double growthRate = ABANDONED_RECOVERY_PROGRESS_PER_DAY
			* foodFactor
			* capacityFactor
			* (0.55D + securityFactor * 0.25D + comfortFactor * 0.20D);
		progress += growthRate * elapsedDays;
		progress = clamp(progress, 0.0D, Math.max(0.99D, recoveryCapacity + 0.99D));
		return new GrowthResult(progress, Math.min(1, (int) Math.floor(progress)));
	}

	private static GrowthResult applyOutpostGrowth(
		SettlementState settlement,
		int population,
		SupplyState foodSupply,
		double security,
		double elapsedDays
	) {
		int freeCapacity = Math.max(0, OutpostSettlementWork.recruitmentCapacity(settlement) - population);
		double foodFactor = clamp(foodSupply.ratio(), 0.0D, 1.1D);
		double capacityFactor = freeCapacity <= 0 ? 0.0D : clamp(freeCapacity / (double) Math.max(4, population), 0.25D, 1.0D);
		double growthRate = 0.18D * foodFactor * (0.55D + security * 0.45D) * capacityFactor;
		double progress = settlement.growthProgress() + (growthRate * elapsedDays);

		if (freeCapacity <= 0 || foodFactor < 0.65D) {
			progress = Math.max(0.0D, progress - elapsedDays * 0.06D);
		}

		progress = clamp(progress, 0.0D, Math.max(0.99D, freeCapacity + 0.99D));
		int requestedRecruits = freeCapacity > 0 ? Math.min(1, (int) Math.floor(progress)) : 0;
		return new GrowthResult(progress, requestedRecruits);
	}

	private static RouteState createLandRoute(
		SettlementState from,
		SettlementState to,
		int distanceBlocks,
		long currentTick,
		SettlementTradeRange.TradeRangeProfile fromRange,
		SettlementTradeRange.TradeRangeProfile toRange
	) {
		double tradeQualityBonus = average(fromRange.landTradeQualityBonus(), toRange.landTradeQualityBonus());
		double routeTradeBonus = average(fromRange.routeTradeBonus(), toRange.routeTradeBonus());
		double initialQuality = clamp(0.46D - Math.min(0.16D, distanceBlocks / 8_192.0D) + tradeQualityBonus, 0.30D, 0.65D);
		double initialSecurity = clamp((from.security() + to.security()) * 0.5D, 0.20D, 0.95D);
		int throughputBase = 64 + Math.max(0, 128 - (distanceBlocks / 8)) + (int) Math.round(routeTradeBonus * 24.0D);
		return new RouteState(
			routeIdForSettlements(from.id(), to.id()),
			from.dimension(),
			from.id(),
			to.id(),
			RouteType.LAND,
			RouteTier.TRAIL,
			distanceBlocks,
			initialQuality,
			initialSecurity,
			throughputBase,
			"",
			0L,
			currentTick,
			currentTick
		);
	}

	private static RouteState createWaterRoute(
		SettlementState from,
		SettlementState to,
		int distanceBlocks,
		long currentTick,
		SettlementTradeRange.TradeRangeProfile fromRange,
		SettlementTradeRange.TradeRangeProfile toRange
	) {
		double tradeQualityBonus = average(fromRange.waterTradeQualityBonus(), toRange.waterTradeQualityBonus());
		double routeTradeBonus = average(fromRange.routeTradeBonus(), toRange.routeTradeBonus());
		double initialQuality = clamp(0.82D - Math.min(0.10D, distanceBlocks / 12_288.0D) + tradeQualityBonus, 0.70D, 0.97D);
		double initialSecurity = clamp((from.security() + to.security()) * 0.5D + 0.08D, 0.25D, 0.95D);
		int throughputBase = 112 + Math.max(0, 160 - (distanceBlocks / 12)) + (int) Math.round(routeTradeBonus * 36.0D);
		return new RouteState(
			routeIdForSettlements(from.id(), to.id()),
			from.dimension(),
			from.id(),
			to.id(),
			RouteType.WATER,
			RouteTier.SEA_LANE,
			distanceBlocks,
			initialQuality,
			initialSecurity,
			throughputBase,
			"",
			0L,
			currentTick,
			currentTick
		);
	}

	private static RouteState createRoute(SettlementState from, SettlementState to, int distanceBlocks, long currentTick, ServerLevel level) {
		SettlementConstruction.InfrastructureSurvey fromInfrastructure = level != null
			? SettlementConstruction.survey(level, from)
			: SettlementConstruction.InfrastructureSurvey.empty();
		SettlementConstruction.InfrastructureSurvey toInfrastructure = level != null
			? SettlementConstruction.survey(level, to)
			: SettlementConstruction.InfrastructureSurvey.empty();
		SettlementTradeRange.TradeRangeProfile fromRange = SettlementTradeRange.profile(from, fromInfrastructure);
		SettlementTradeRange.TradeRangeProfile toRange = SettlementTradeRange.profile(to, toInfrastructure);
		boolean waterRoute = canReachByWater(from, to, distanceBlocks, fromRange, toRange)
			&& (!canReachByLand(distanceBlocks, fromRange, toRange) || preferredWaterRoute(fromInfrastructure, toInfrastructure, fromRange, toRange));
			return waterRoute
				? createWaterRoute(from, to, distanceBlocks, currentTick, fromRange, toRange)
				: createLandRoute(from, to, distanceBlocks, currentTick, fromRange, toRange);
	}

	private static boolean canPlanLandRoutes(SettlementState settlement) {
		return settlement.kind() != SettlementKind.OUTPOST
			&& roleCount(settlement, SettlementRoleKeys.TRADEMASTER) > 0
			&& roleCount(settlement, SettlementRoleKeys.ROADWRIGHT) > 0;
	}

	private static boolean canPlanWaterRoutes(SettlementState settlement, SettlementTradeRange.TradeRangeProfile tradeRange) {
		return settlement.kind() != SettlementKind.OUTPOST
			&& roleCount(settlement, SettlementRoleKeys.TRADEMASTER) > 0
			&& tradeRange.waterRoutesUnlocked();
	}

	private static boolean canReachCandidate(
		SettlementState source,
		SettlementState candidate,
		double distanceBlocks,
		SettlementTradeRange.TradeRangeProfile sourceRange,
		SettlementTradeRange.TradeRangeProfile candidateRange
	) {
		if (canPlanLandRoutes(source) && canReachByLand(distanceBlocks, sourceRange, candidateRange)) {
			return true;
		}

		return canPlanWaterRoutes(source, sourceRange) && canReachByWater(source, candidate, distanceBlocks, sourceRange, candidateRange);
	}

	private static boolean canReachByLand(
		double distanceBlocks,
		SettlementTradeRange.TradeRangeProfile sourceRange,
		SettlementTradeRange.TradeRangeProfile candidateRange
	) {
		if (distanceBlocks <= SettlementTradeRange.localLandRouteRangeBlocks()) {
			return true;
		}

		if (!sourceRange.hasCartographerSupport() && !candidateRange.hasCartographerSupport()) {
			return false;
		}

		return distanceBlocks <= Math.max(sourceRange.landRouteRangeBlocks(), candidateRange.landRouteRangeBlocks());
	}

	private static boolean canReachByWater(
		SettlementState source,
		SettlementState candidate,
		double distanceBlocks,
		SettlementTradeRange.TradeRangeProfile sourceRange,
		SettlementTradeRange.TradeRangeProfile candidateRange
	) {
		return sourceRange.waterRoutesUnlocked()
			&& candidateRange.waterRoutesUnlocked()
			&& distanceBlocks <= Math.max(sourceRange.waterRouteRangeBlocks(), candidateRange.waterRouteRangeBlocks());
	}

	private static boolean preferredWaterRoute(
		SettlementConstruction.InfrastructureSurvey fromInfrastructure,
		SettlementConstruction.InfrastructureSurvey toInfrastructure,
		SettlementTradeRange.TradeRangeProfile fromRange,
		SettlementTradeRange.TradeRangeProfile toRange
	) {
		return fromInfrastructure.lighthouses() + toInfrastructure.lighthouses() > 0
			|| Math.max(fromRange.waterRouteRangeBlocks(), toRange.waterRouteRangeBlocks())
				> Math.max(fromRange.landRouteRangeBlocks(), toRange.landRouteRangeBlocks());
	}

	private static boolean hasProjectType(List<SettlementProject> projects, SettlementProjectType type) {
		return projects.stream().anyMatch(project -> project.type() == type);
	}

	private static int countProjectType(List<SettlementProject> projects, SettlementProjectType type) {
		return (int) projects.stream()
			.filter(project -> project.type() == type)
			.count();
	}

	private static List<SettlementProject> limitProjectType(List<SettlementProject> projects, SettlementProjectType type, int maxCount) {
		List<SettlementProject> limited = new ArrayList<>(projects.size());
		int kept = 0;

		for (SettlementProject project : projects) {
			if (project.type() != type) {
				limited.add(project);
				continue;
			}

			if (kept < maxCount) {
				limited.add(project);
				kept++;
			}
		}

		return limited;
	}

	private static String nextProjectId(List<SettlementProject> projects, String prefix) {
		int nextIndex = 1;

		for (SettlementProject project : projects) {
			if (project.id().startsWith(prefix)) {
				nextIndex++;
			}
		}

		return prefix + ":" + nextIndex;
	}

	private static boolean hasRoadProjectForTarget(List<SettlementProject> projects, String targetSettlementId) {
		return projects.stream()
			.anyMatch(project -> project.type() == SettlementProjectType.ROAD && project.targetSettlementId().equals(targetSettlementId));
	}

	private static Optional<SettlementState> findSettlementById(Collection<SettlementState> settlements, String id) {
		return settlements.stream()
			.filter(settlement -> settlement.id().equals(id))
			.findFirst();
	}

	private static int desiredDefenseLevel(int population) {
		return Math.max(1, (int) Math.ceil(population / 6.0D));
	}

	private static double projectWorkRate(SettlementState settlement, SettlementProjectType type) {
		int carpenters = roleCount(settlement, SettlementRoleKeys.CARPENTER);
		int masons = roleCount(settlement, SettlementRoleKeys.MASON);
		int constructionSupport = roleCount(settlement, SettlementRoleKeys.CONSTRUCTION_SUPPORT);
		int guards = roleCount(settlement, SettlementRoleKeys.GUARD);
		int roadwrights = roleCount(settlement, SettlementRoleKeys.ROADWRIGHT);
		int trademasters = roleCount(settlement, SettlementRoleKeys.TRADEMASTER);
		int unemployed = roleCount(settlement, SettlementRoleKeys.UNEMPLOYED);
		double baseLabor = Math.max(0.25D, settlement.totalPopulation() * 0.15D);
		double woodConstruction = carpenters * 1.5D + constructionSupport * 1.3D;
		double stoneConstruction = masons * 1.4D + constructionSupport * 1.0D;

		return SettlementEconomyRules.scaledWorkerDailyRate(switch (type) {
			case TRADING_POST -> Math.max(0.8D, baseLabor + woodConstruction + stoneConstruction * 0.35D + unemployed * 0.8D + trademasters * 1.2D);
			case HOUSING -> Math.max(0.6D, baseLabor + woodConstruction + stoneConstruction * 0.8D + unemployed * 0.7D + trademasters * 0.8D);
			case CARPENTER_WORKSHOP, COMPOSTER, STORAGE, DOCK -> Math.max(0.5D, baseLabor + woodConstruction + stoneConstruction * 0.45D + unemployed * 0.5D + trademasters * 0.6D);
			case LIGHTHOUSE -> Math.max(0.55D, baseLabor + stoneConstruction * 1.3D + woodConstruction * 0.35D + unemployed * 0.45D + trademasters * 0.75D);
			case DEFENSE -> Math.max(0.4D, baseLabor + guards * 1.1D + stoneConstruction * 0.9D + woodConstruction * 0.45D + unemployed * 0.25D + trademasters * 0.4D);
			case ROAD -> Math.max(0.4D, baseLabor + roadwrights * 1.8D + stoneConstruction * 0.55D + woodConstruction * 0.55D + unemployed * 0.35D + trademasters * 1.0D);
		});
	}

	private static Map<String, Integer> roadProjectCost(int distanceBlocks) {
		return Map.of(
			"logs",
			4,
			"planks",
			4 + Math.max(0, distanceBlocks / 256),
			"cobblestone",
			6 + Math.max(0, distanceBlocks / 128)
		);
	}

	private static boolean tryConsumeCost(Map<String, Integer> stock, Map<String, Integer> cost) {
		for (Map.Entry<String, Integer> entry : cost.entrySet()) {
			if (stock.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
				return false;
			}
		}

		for (Map.Entry<String, Integer> entry : cost.entrySet()) {
			stock.put(entry.getKey(), stock.getOrDefault(entry.getKey(), 0) - entry.getValue());
		}

		return true;
	}

	private static boolean routeExistsTo(List<RouteState> routes, String targetSettlementId) {
		return routes.stream().anyMatch(route ->
			route.fromSettlementId().equals(targetSettlementId) || route.toSettlementId().equals(targetSettlementId)
		);
	}

	private static String routeIdForSettlements(String firstSettlementId, String secondSettlementId) {
		if (firstSettlementId.compareTo(secondSettlementId) <= 0) {
			return "route:" + firstSettlementId + "|" + secondSettlementId;
		}

		return "route:" + secondSettlementId + "|" + firstSettlementId;
	}

	private static int foodVariety(Map<String, Integer> stock) {
		int varieties = 0;

		for (String goodsKey : FOOD_PRIORITY) {
			if (stock.getOrDefault(goodsKey, 0) > 0) {
				varieties++;
			}
		}

		return varieties;
	}

	static int totalFoodStock(Map<String, Integer> stock) {
		int total = 0;

		for (String goodsKey : FOOD_PRIORITY) {
			total += Math.max(0, stock.getOrDefault(goodsKey, 0));
		}

		return total;
	}

	private static int roleCount(SettlementState settlement, String role) {
		return Math.max(0, settlement.population().getOrDefault(role, 0));
	}

	private static int roleCount(Map<String, Integer> population, String role) {
		return Math.max(0, population.getOrDefault(role, 0));
	}

	private static double average(double first, double second) {
		return (first + second) * 0.5D;
	}

	private static double harborLocalTradeBonus(SettlementConstruction.InfrastructureSurvey infrastructure, int portmasters) {
		if (!infrastructure.available() || !infrastructure.hasLargeWaterBody()) {
			return 0.0D;
		}

		return infrastructure.docks() * (0.6D + Math.min(0.35D, portmasters * 0.2D))
			+ scaledLighthouseCount(infrastructure.lighthouses()) * (1.4D + Math.min(0.45D, portmasters * 0.25D));
	}

	private static double harborRouteTradeBonus(SettlementConstruction.InfrastructureSurvey infrastructure, int portmasters) {
		if (!infrastructure.available() || !infrastructure.hasLargeWaterBody()) {
			return 0.0D;
		}

		return infrastructure.docks() * (0.18D + Math.min(0.08D, portmasters * 0.04D))
			+ scaledLighthouseCount(infrastructure.lighthouses()) * (0.45D + Math.min(0.12D, portmasters * 0.06D));
	}

	private static double lighthouseSecurityBonus(SettlementConstruction.InfrastructureSurvey infrastructure) {
		if (!infrastructure.available() || infrastructure.lighthouses() <= 0) {
			return 0.0D;
		}

		return 0.03D + Math.min(0.03D, Math.max(0, infrastructure.lighthouses() - 1) * 0.01D);
	}

	private static double palisadeSecurityBonus(SettlementConstruction.InfrastructureSurvey infrastructure) {
		if (!infrastructure.available()) {
			return 0.0D;
		}

		double coverageBonus = infrastructure.palisadeCoverage() * 0.18D;
		double gatehouseBonus = Math.min(0.04D, Math.max(0, infrastructure.palisadeGatehouses()) * 0.01D);
		return Math.min(0.22D, coverageBonus + gatehouseBonus);
	}

	private static double scaledLighthouseCount(int lighthouseCount) {
		if (lighthouseCount <= 0) {
			return 0.0D;
		}

		return 1.0D + Math.min(0.30D, Math.max(0, lighthouseCount - 1) * 0.10D);
	}

	private static double tradingPostLocalTradeBonus(SettlementConstruction.InfrastructureSurvey infrastructure) {
		if (!infrastructure.available()) {
			return 0.0D;
		}

		return infrastructure.tradingPosts() * 1.5D;
	}

	private static double tradingPostRouteTradeBonus(SettlementConstruction.InfrastructureSurvey infrastructure) {
		if (!infrastructure.available()) {
			return 0.0D;
		}

		return infrastructure.tradingPosts() * 0.20D;
	}

	private static int scaledAmount(double dailyRate, double elapsedDays) {
		return Math.max(0, (int) Math.round(dailyRate * elapsedDays));
	}

	private static int consumeGoods(Map<String, Integer> stock, String goodsKey, int required) {
		if (required <= 0) {
			return 0;
		}

		int available = stock.getOrDefault(goodsKey, 0);
		int consumed = Math.min(required, available);

		if (consumed > 0) {
			stock.put(goodsKey, available - consumed);
		}

		return consumed;
	}

	private static void addGoods(Map<String, Integer> goods, String goodsKey, int amount) {
		if (amount <= 0) {
			return;
		}

		goods.merge(goodsKey, amount, Integer::sum);
	}

	private static void cleanMap(Map<String, Integer> values) {
		values.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() <= 0);
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	public record SimulationResult(SettlementState settlement, List<RouteState> createdRoutes, int requestedVillagerSpawns) {
	}

	private record GrowthResult(double progress, int requestedVillagerSpawns) {
	}

	private record SupplyState(int fulfilled, int required) {
		private double ratio() {
			if (required <= 0) {
				return 1.0D;
			}

			return fulfilled / (double) required;
		}
	}

	private record ProjectAdvanceResult(
		List<SettlementProject> projects,
		List<RouteState> createdRoutes,
		int housingCapacity,
		int defenseLevel
	) {
	}
}
