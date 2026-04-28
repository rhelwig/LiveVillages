package com.ronhelwig.livevillages.sim;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.server.level.ServerLevel;

public final class SettlementEconomySimulator {
	private static final List<String> FOOD_PRIORITY = List.of("bread", "carrot", "potato", "beetroot", "wheat", "beef");
	private static final double FOOD_ITEMS_PER_PERSON_PER_DAY = 2.0D;
	private static final double MAX_ROUTE_DISTANCE_BLOCKS = 1_024.0D;

	private SettlementEconomySimulator() {
	}

	public static SimulationResult advanceSettlement(
		SettlementState settlement,
		List<RouteState> routes,
		Collection<SettlementState> allSettlements,
		long currentTick,
		ServerLevel level
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

		if (infrastructure.available()) {
			int reconciledHousing = settlement.kind() == SettlementKind.CUSTOM
				? infrastructure.housingCapacity()
				: Math.max(simulationSettlement.housingCapacity(), infrastructure.housingCapacity());

			if (reconciledHousing != simulationSettlement.housingCapacity()) {
				simulationSettlement = simulationSettlement.withHousingCapacity(reconciledHousing);
			}
		}

		int population = simulationSettlement.totalPopulation();

		if (population <= 0) {
			return new SimulationResult(simulationSettlement.withLastUpdateTick(currentTick), List.of(), 0);
		}

		Map<String, Integer> stock = new LinkedHashMap<>(simulationSettlement.stock());
		Map<String, Integer> wealth = new LinkedHashMap<>(simulationSettlement.wealth());
		Map<String, Integer> populationMap = new LinkedHashMap<>(simulationSettlement.population());
		Map<String, Integer> nearbyProfessions = level != null && SettlementVillagers.usesActualVillagers(settlement)
			? SettlementVillagers.nearbyProfessionPopulation(level, settlement)
			: Map.of();

		applyProduction(simulationSettlement, routes, stock, wealth, elapsedDays, level, infrastructure, nearbyProfessions);
		SupplyState foodSupply = consumeFood(stock, population, elapsedDays);
		SupplyState upkeepSupply = consumeUpkeep(stock, simulationSettlement.housingCapacity(), population, elapsedDays);

		List<SettlementProject> plannedProjects = planProjects(simulationSettlement, routes, allSettlements, infrastructure);
		ProjectAdvanceResult projectResult = advanceProjects(
			simulationSettlement,
			plannedProjects,
			routes,
			allSettlements,
			stock,
			elapsedDays,
			currentTick,
			level
		);

		List<RouteState> activeRoutes = new ArrayList<>(routes);
		activeRoutes.addAll(projectResult.createdRoutes());

		double security = computeSecurity(simulationSettlement, activeRoutes, projectResult.defenseLevel(), population, elapsedDays);
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
		ServerLevel level,
		SettlementConstruction.InfrastructureSurvey infrastructure,
		Map<String, Integer> nearbyProfessions
	) {
		int population = settlement.totalPopulation();
		int farmers = roleCount(settlement, SettlementRoleKeys.FARMER);
		int carpenters = roleCount(settlement, SettlementRoleKeys.CARPENTER);
		int masons = roleCount(settlement, SettlementRoleKeys.MASON);
		int constructionSupport = roleCount(settlement, SettlementRoleKeys.CONSTRUCTION_SUPPORT);
		int fishermen = roleCount(nearbyProfessions, "fisherman");
		int gardeners = roleCount(settlement, "gardener");
		int foresters = roleCount(settlement, "forester");
		int miners = roleCount(settlement, "miner");
		int trademasters = roleCount(settlement, SettlementRoleKeys.TRADEMASTER);
		int portmasters = roleCount(settlement, SettlementRoleKeys.PORTMASTER);
		double civilianScale = settlement.kind() == SettlementKind.OUTPOST ? 0.65D : 1.0D;
		double tradeScale = settlement.kind() == SettlementKind.HARBOR ? 1.2D : 1.0D;
		boolean useLoadedFarmerWork = level != null && SettlementVillagers.usesActualVillagers(settlement);

		addGoods(stock, "wheat", scaledAmount((population * 2.0D * civilianScale) + (useLoadedFarmerWork ? 0.0D : farmers * 5.0D) + gardeners * 1.5D, elapsedDays));
		addGoods(stock, "bread", scaledAmount((population * 0.75D * civilianScale) + (useLoadedFarmerWork ? 0.0D : farmers * 1.5D) + trademasters * 0.5D, elapsedDays));
		addGoods(stock, "carrot", scaledAmount((population * 0.35D * civilianScale) + (useLoadedFarmerWork ? 0.0D : farmers * 1.25D) + gardeners * 3.0D, elapsedDays));
		addGoods(stock, "potato", scaledAmount((population * 0.35D * civilianScale) + (useLoadedFarmerWork ? 0.0D : farmers * 1.25D) + gardeners * 3.0D, elapsedDays));
		addGoods(stock, "beetroot", scaledAmount((population * 0.18D * civilianScale) + (useLoadedFarmerWork ? 0.0D : farmers * 0.9D) + gardeners * 1.5D, elapsedDays));
		addGoods(stock, "cod", scaledAmount(fishermanCatchRate(fishermen, infrastructure), elapsedDays));
		addGoods(stock, "logs", scaledAmount((population * 0.5D) + foresters * 5.0D + carpenters * 1.0D + constructionSupport * 1.0D, elapsedDays));
		addGoods(stock, "planks", scaledAmount((population * 0.35D) + foresters * 2.0D + carpenters * 4.0D + constructionSupport * 4.0D, elapsedDays));
		addGoods(stock, "stick", scaledAmount(foresters * 2.0D, elapsedDays));
		addGoods(stock, "apple", scaledAmount(foresters * 0.5D, elapsedDays));
		addGoods(stock, "oak_sapling", scaledAmount(foresters * 1.0D, elapsedDays));
		addGoods(stock, "cobblestone", scaledAmount((population * 0.45D) + miners * 6.0D + masons * 1.5D + constructionSupport * 1.5D, elapsedDays));
		addGoods(stock, "iron_ingot", scaledAmount(miners * 1.5D, elapsedDays));
		addGoods(
			wealth,
			"emerald",
			scaledAmount(
				(population * 0.3D)
					+ trademasters * 2.0D
					+ routes.size() * (tradeScale + harborRouteTradeBonus(infrastructure, portmasters) + tradingPostRouteTradeBonus(infrastructure))
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

		if (useLoadedFarmerWork) {
			SettlementFarmerWork.applyLoadedFarmerWork(level, settlement, stock, elapsedDays);
		}
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
		SettlementConstruction.InfrastructureSurvey infrastructure
	) {
		List<SettlementProject> projects = new ArrayList<>();

		for (SettlementProject project : settlement.projects()) {
			if (project.type() == SettlementProjectType.DOCK && infrastructure.incompleteDocks() > 0) {
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

		int population = settlement.totalPopulation();
		int effectiveHousing = Math.max(settlement.housingCapacity(), infrastructure.housingCapacity());
		boolean needsHousing = population > 0 && effectiveHousing < population + 1;

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
			int farmers = roleCount(settlement, SettlementRoleKeys.FARMER);
			int desiredComposters = Math.max(0, Math.min(2, farmers));

			if (desiredComposters > 0 && infrastructure.composters() + countProjectType(projects, SettlementProjectType.COMPOSTER) < desiredComposters) {
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
				&& infrastructure.lighthouses() + countProjectType(projects, SettlementProjectType.LIGHTHOUSE) < 1) {
				projects.add(new SettlementProject(nextProjectId(projects, "lighthouse"), SettlementProjectType.LIGHTHOUSE, "", 0.0D, 1.1D));
			}
		}

		if (population >= 3 && settlement.defenseLevel() < desiredDefenseLevel(population) && !hasProjectType(projects, SettlementProjectType.DEFENSE)) {
			projects.add(new SettlementProject(nextProjectId(projects, "defense"), SettlementProjectType.DEFENSE, "", 0.0D, 0.8D));
		}

		maybeQueueRoadProject(settlement, projects, routes, allSettlements);
		return projects;
	}

	private static void maybeQueueRoadProject(
		SettlementState settlement,
		List<SettlementProject> projects,
		List<RouteState> routes,
		Collection<SettlementState> allSettlements
	) {
		if (!canPlanRoutes(settlement)) {
			return;
		}

		int desiredRoutes = settlement.kind() == SettlementKind.CUSTOM ? 1 : 2;
		long activeRoadProjects = projects.stream()
			.filter(project -> project.type() == SettlementProjectType.ROAD)
			.count();

		if (routes.size() + activeRoadProjects >= desiredRoutes) {
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

			if (distance > MAX_ROUTE_DISTANCE_BLOCKS || distance >= nearestDistance) {
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
		ServerLevel level
	) {
		List<SettlementProject> remainingProjects = new ArrayList<>();
		List<RouteState> createdRoutes = new ArrayList<>();
		int housingCapacity = settlement.housingCapacity();
		int defenseLevel = settlement.defenseLevel();
		boolean useWorldConstruction = level != null && SettlementVillagers.usesActualVillagers(settlement);

		for (SettlementProject project : projects) {
			double newProgress = Math.min(project.requiredProgress(), project.progress() + (projectWorkRate(settlement, project.type()) * elapsedDays));
			SettlementProject progressed = project.withProgress(newProgress);

			if (newProgress + 1.0E-6D < project.requiredProgress()) {
				remainingProjects.add(progressed);
				continue;
			}

			switch (project.type()) {
				case HOUSING -> {
					if (useWorldConstruction) {
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
						createdRoutes.add(createLandRoute(settlement, target.get(), distanceBlocks, currentTick));
						continue;
					}
				}
			}

			remainingProjects.add(progressed);
		}

		return new ProjectAdvanceResult(remainingProjects, createdRoutes, housingCapacity, defenseLevel);
	}

	private static double computeSecurity(SettlementState settlement, List<RouteState> routes, int defenseLevel, int population, double elapsedDays) {
		int guards = roleCount(settlement, "guard");
		int fletchers = roleCount(settlement, SettlementRoleKeys.FLETCHER);
		double baseSecurity = settlement.kind() == SettlementKind.OUTPOST ? 0.35D : 0.12D;
		double defenderPressure = population <= 0 ? 0.0D : ((guards * 0.7D) + (fletchers * 0.45D)) / population;
		double routeSupport = Math.min(0.18D, routes.size() * 0.03D);
		double fortificationSupport = defenseLevel * 0.08D;
		double targetSecurity = clamp(baseSecurity + defenderPressure + routeSupport + fortificationSupport, 0.05D, 0.95D);
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
		double targetComfort = 0.55D
			+ clamp(foodRatio, 0.0D, 1.1D) * 0.25D
			+ clamp(upkeepRatio, 0.0D, 1.0D) * 0.10D
			+ clamp(housingRatio - 0.75D, 0.0D, 0.35D)
			+ security * 0.10D
			+ varietyBonus;
		double blend = clamp(elapsedDays * 0.35D, 0.15D, 1.0D);
		return clamp(settlement.comfort() + (targetComfort - settlement.comfort()) * blend, 0.35D, 1.35D);
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

	private static RouteState createLandRoute(SettlementState from, SettlementState to, int distanceBlocks, long currentTick) {
		double initialQuality = clamp(0.46D - Math.min(0.16D, distanceBlocks / 8_192.0D), 0.30D, 0.46D);
		double initialSecurity = clamp((from.security() + to.security()) * 0.5D, 0.20D, 0.95D);
		int throughputBase = 64 + Math.max(0, 128 - (distanceBlocks / 8));
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

	private static boolean canPlanRoutes(SettlementState settlement) {
		return settlement.kind() != SettlementKind.OUTPOST
			&& roleCount(settlement, SettlementRoleKeys.TRADEMASTER) > 0
			&& roleCount(settlement, SettlementRoleKeys.ROADWRIGHT) > 0;
	}

	private static boolean hasProjectType(List<SettlementProject> projects, SettlementProjectType type) {
		return projects.stream().anyMatch(project -> project.type() == type);
	}

	private static int countProjectType(List<SettlementProject> projects, SettlementProjectType type) {
		return (int) projects.stream()
			.filter(project -> project.type() == type)
			.count();
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

		return switch (type) {
			case HOUSING -> Math.max(0.6D, baseLabor + woodConstruction + stoneConstruction * 0.8D + unemployed * 0.7D + trademasters * 0.8D);
			case CARPENTER_WORKSHOP, COMPOSTER, STORAGE, DOCK -> Math.max(0.5D, baseLabor + woodConstruction + stoneConstruction * 0.45D + unemployed * 0.5D + trademasters * 0.6D);
			case LIGHTHOUSE -> Math.max(0.55D, baseLabor + stoneConstruction * 1.3D + woodConstruction * 0.35D + unemployed * 0.45D + trademasters * 0.75D);
			case DEFENSE -> Math.max(0.4D, baseLabor + guards * 1.1D + stoneConstruction * 0.9D + woodConstruction * 0.45D + unemployed * 0.25D + trademasters * 0.4D);
			case ROAD -> Math.max(0.4D, baseLabor + roadwrights * 1.8D + stoneConstruction * 0.55D + woodConstruction * 0.55D + unemployed * 0.35D + trademasters * 1.0D);
		};
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

	private static int roleCount(SettlementState settlement, String role) {
		return Math.max(0, settlement.population().getOrDefault(role, 0));
	}

	private static int roleCount(Map<String, Integer> population, String role) {
		return Math.max(0, population.getOrDefault(role, 0));
	}

	private static double fishermanCatchRate(int fishermen, SettlementConstruction.InfrastructureSurvey infrastructure) {
		if (fishermen <= 0) {
			return 0.0D;
		}

		double catchRate = fishermen * 1.5D;

		if (infrastructure.available() && infrastructure.hasLargeWaterBody()) {
			catchRate += fishermen * 0.75D;

			if (infrastructure.docks() > 0) {
				catchRate *= 1.0D + Math.min(0.8D, infrastructure.docks() * 0.6D);
			}
		}

		return catchRate;
	}

	private static double harborLocalTradeBonus(SettlementConstruction.InfrastructureSurvey infrastructure, int portmasters) {
		if (!infrastructure.available() || !infrastructure.hasLargeWaterBody()) {
			return 0.0D;
		}

		return infrastructure.docks() * (0.6D + Math.min(0.35D, portmasters * 0.2D))
			+ infrastructure.lighthouses() * (1.4D + Math.min(0.45D, portmasters * 0.25D));
	}

	private static double harborRouteTradeBonus(SettlementConstruction.InfrastructureSurvey infrastructure, int portmasters) {
		if (!infrastructure.available() || !infrastructure.hasLargeWaterBody()) {
			return 0.0D;
		}

		return infrastructure.docks() * (0.18D + Math.min(0.08D, portmasters * 0.04D))
			+ infrastructure.lighthouses() * (0.45D + Math.min(0.12D, portmasters * 0.06D));
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
