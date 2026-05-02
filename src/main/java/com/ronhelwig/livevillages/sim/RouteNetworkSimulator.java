package com.ronhelwig.livevillages.sim;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

public final class RouteNetworkSimulator {
	private static final long MIN_ROUTE_UPDATE_TICKS = 2_000L;
	private static final double MAX_TRADE_INTERVAL_DAYS = 7.0D;
	private static final double MIN_TRADE_INTERVAL_DAYS = 0.5D;
	private static final int MIN_SURVEY_SAMPLES = 12;
	private static final int MAX_SURVEY_SAMPLES = 48;
	private static final int SURVEY_SAMPLE_RADIUS_BLOCKS = 2;
	private static final double SOURCE_RESERVE_RATIO = 0.55D;
	private static final double MIN_SHORTAGE_IMBALANCE_RATIO = 0.10D;

	private RouteNetworkSimulator() {
	}

	public static RouteAdvanceResult advanceRoute(
		ServerLevel level,
		RouteState route,
		SettlementState from,
		SettlementState to,
		Collection<SettlementBuildSite> fromBuildSites,
		Collection<SettlementBuildSite> toBuildSites,
		long currentTick
	) {
		long elapsedTicks = Math.max(0L, currentTick - route.lastSurveyTick());

		if (elapsedTicks < MIN_ROUTE_UPDATE_TICKS) {
			return new RouteAdvanceResult(route, from, to);
		}

		SettlementTradeRange.TradeRangeProfile fromRange = tradeProfileForRoute(level, route, from);
		SettlementTradeRange.TradeRangeProfile toRange = tradeProfileForRoute(level, route, to);
		RouteState surveyedRoute = surveyRoute(level, route, from, to, currentTick, fromRange, toRange);
		TradeAdvanceResult tradeResult = advanceTrade(surveyedRoute, from, to, fromBuildSites, toBuildSites, currentTick, fromRange, toRange);
		return new RouteAdvanceResult(tradeResult.route(), tradeResult.fromSettlement(), tradeResult.toSettlement());
	}

	private static RouteState surveyRoute(
		ServerLevel level,
		RouteState route,
		SettlementState from,
		SettlementState to,
		long currentTick,
		SettlementTradeRange.TradeRangeProfile fromRange,
		SettlementTradeRange.TradeRangeProfile toRange
	) {
		int distanceBlocks = Math.max(1, (int) Math.round(Math.sqrt(from.center().distSqr(to.center()))));

		if (route.type() != RouteType.LAND) {
			double quality = clamp(
				baseQuality(route.tier()) + averageTradeQualityBonus(fromRange, toRange, true) - Math.min(0.08D, distanceBlocks / 12_288.0D),
				0.70D,
				0.97D
			);
			double security = clamp((from.security() + to.security()) * 0.5D, 0.15D, 0.95D);
			int throughputBase = determineThroughputBase(route.tier(), quality, distanceBlocks)
				+ (int) Math.round(averageRouteTradeBonus(fromRange, toRange) * 32.0D);
			return route.withSurvey(route.tier(), distanceBlocks, quality, security, throughputBase, currentTick);
		}

		int sampleCount = Math.max(MIN_SURVEY_SAMPLES, Math.min(MAX_SURVEY_SAMPLES, distanceBlocks / 16));
		SurveyStats surveyStats = collectSurveyStats(level, from.center(), to.center(), sampleCount);
		RouteTier tier = determineLandTier(route.tier(), surveyStats);
		double quality = clamp(determineQuality(tier, surveyStats, distanceBlocks) + averageTradeQualityBonus(fromRange, toRange, false), 0.25D, 0.95D);
		double security = determineRouteSecurity(from, to, quality);
		int throughputBase = determineThroughputBase(tier, quality, distanceBlocks)
			+ (int) Math.round(averageRouteTradeBonus(fromRange, toRange) * 20.0D);
		return route.withSurvey(tier, distanceBlocks, quality, security, throughputBase, currentTick);
	}

	private static SurveyStats collectSurveyStats(ServerLevel level, BlockPos from, BlockPos to, int sampleCount) {
		int pathSamples = 0;
		int gravelSamples = 0;
		int cobbleSamples = 0;
		int brickSamples = 0;
		int loadedSamples = 0;

		for (int i = 0; i < sampleCount; i++) {
			double t = sampleCount == 1 ? 0.0D : i / (double) (sampleCount - 1);
			int x = (int) Math.round(lerp(from.getX(), to.getX(), t));
			int z = (int) Math.round(lerp(from.getZ(), to.getZ(), t));
			RoadSample sample = bestRoadSampleNear(level, x, z, from.getY());

			if (!sample.loaded()) {
				continue;
			}

			loadedSamples++;

			if (sample.kind() == RoadSampleKind.BRICK) {
				brickSamples++;
			} else if (sample.kind() == RoadSampleKind.COBBLE) {
				cobbleSamples++;
			} else if (sample.kind() == RoadSampleKind.GRAVEL) {
				gravelSamples++;
			} else if (sample.kind() == RoadSampleKind.TRAIL) {
				pathSamples++;
			}
		}

		return new SurveyStats(loadedSamples, pathSamples, gravelSamples, cobbleSamples, brickSamples);
	}

	private static RoadSample bestRoadSampleNear(ServerLevel level, int x, int z, int referenceY) {
		boolean loaded = false;
		RoadSampleKind bestKind = RoadSampleKind.NONE;

		for (int radius = 0; radius <= SURVEY_SAMPLE_RADIUS_BLOCKS; radius++) {
			for (int offsetX = -radius; offsetX <= radius; offsetX++) {
				for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
					if (Math.max(Math.abs(offsetX), Math.abs(offsetZ)) != radius) {
						continue;
					}

					BlockPos columnPos = new BlockPos(x + offsetX, referenceY, z + offsetZ);

					if (!level.hasChunkAt(columnPos)) {
						continue;
					}

					loaded = true;
					RoadSampleKind kind = roadSampleKindAt(level, x + offsetX, z + offsetZ);

					if (kind.weight() > bestKind.weight()) {
						bestKind = kind;

						if (bestKind == RoadSampleKind.BRICK) {
							return new RoadSample(true, bestKind);
						}
					}
				}
			}
		}

		return new RoadSample(loaded, bestKind);
	}

	private static RoadSampleKind roadSampleKindAt(ServerLevel level, int x, int z) {
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;

		if (y < level.getMinY() || y > level.getMaxY() - 1) {
			return RoadSampleKind.NONE;
		}

		BlockPos surfacePos = new BlockPos(x, y, z);
		BlockState surfaceState = level.getBlockState(surfacePos);
		BlockState belowState = level.getBlockState(surfacePos.below());

		if (isBrickRoad(surfaceState, belowState)) {
			return RoadSampleKind.BRICK;
		}

		if (isCobbleRoad(surfaceState, belowState)) {
			return RoadSampleKind.COBBLE;
		}

		if (isGravelRoad(surfaceState, belowState)) {
			return RoadSampleKind.GRAVEL;
		}

		if (isTrail(surfaceState, belowState)) {
			return RoadSampleKind.TRAIL;
		}

		return RoadSampleKind.NONE;
	}

	private static TradeAdvanceResult advanceTrade(
		RouteState route,
		SettlementState from,
		SettlementState to,
		Collection<SettlementBuildSite> fromBuildSites,
		Collection<SettlementBuildSite> toBuildSites,
		long currentTick,
		SettlementTradeRange.TradeRangeProfile fromRange,
		SettlementTradeRange.TradeRangeProfile toRange
	) {
		long elapsedTradeTicks = Math.max(0L, currentTick - route.lastTradeAttemptTick());
		long minTradeIntervalTicks = computeTradeIntervalTicks(route, from, to, fromRange, toRange);

		if (elapsedTradeTicks < minTradeIntervalTicks) {
			return new TradeAdvanceResult(route, from, to);
		}

		double elapsedDays = elapsedTradeTicks / SettlementEconomyRules.TICKS_PER_DAY;
		RouteState attemptedRoute = route.withTradeAttemptTick(currentTick);
		int throughputBudget = computeThroughputBudget(route, elapsedDays);

		if (throughputBudget <= 0) {
			return new TradeAdvanceResult(attemptedRoute, from, to);
		}

		Map<String, Integer> fromStock = new LinkedHashMap<>(from.stock());
		Map<String, Integer> toStock = new LinkedHashMap<>(to.stock());
		List<Shipment> shipments = new ArrayList<>();
		int remainingBudget = throughputBudget;

		List<GoodsDemand> transfers = computeTransferCandidates(from, to, fromBuildSites, toBuildSites, fromStock, toStock);

		for (GoodsDemand demand : transfers) {
			if (remainingBudget <= 0) {
				break;
			}

			int transferAmount = Math.min(remainingBudget, Math.min(demand.availableToSend(), demand.neededAtDestination()));

			if (transferAmount <= 0) {
				continue;
			}

			Map<String, Integer> sourceStock = demand.fromSettlementId().equals(from.id()) ? fromStock : toStock;
			Map<String, Integer> destinationStock = demand.fromSettlementId().equals(from.id()) ? toStock : fromStock;
			sourceStock.put(demand.goodsKey(), sourceStock.getOrDefault(demand.goodsKey(), 0) - transferAmount);
			destinationStock.merge(demand.goodsKey(), transferAmount, Integer::sum);
			shipments.add(new Shipment(demand.goodsKey(), transferAmount, demand.fromSettlementId(), demand.toSettlementId()));
			remainingBudget -= transferAmount;
		}

		cleanMap(fromStock);
		cleanMap(toStock);

		if (shipments.isEmpty()) {
			return new TradeAdvanceResult(attemptedRoute, from, to);
		}

		String summary = summarizeShipments(shipments, from, to);

		SettlementState updatedFrom = from.withSimulationState(
			from.population(),
			from.wealth(),
			fromStock,
			from.housingCapacity(),
			from.comfort(),
			from.security(),
			from.defenseLevel(),
			from.growthProgress(),
			from.projects(),
			from.lastUpdateTick()
		);
		SettlementState updatedTo = to.withSimulationState(
			to.population(),
			to.wealth(),
			toStock,
			to.housingCapacity(),
			to.comfort(),
			to.security(),
			to.defenseLevel(),
			to.growthProgress(),
			to.projects(),
			to.lastUpdateTick()
		);
		return new TradeAdvanceResult(attemptedRoute.withTradeSummary(summary, currentTick), updatedFrom, updatedTo);
	}

	private static List<GoodsDemand> computeTransferCandidates(
		SettlementState from,
		SettlementState to,
		Collection<SettlementBuildSite> fromBuildSites,
		Collection<SettlementBuildSite> toBuildSites,
		Map<String, Integer> fromStock,
		Map<String, Integer> toStock
	) {
		List<GoodsDemand> demands = new ArrayList<>();
		addTransferCandidates(demands, from, to, fromBuildSites, toBuildSites, fromStock, toStock);
		addTransferCandidates(demands, to, from, toBuildSites, fromBuildSites, toStock, fromStock);
		demands.sort(Comparator.comparingInt(GoodsDemand::priority).reversed());
		return demands;
	}

	private static void addTransferCandidates(
		List<GoodsDemand> demands,
		SettlementState source,
		SettlementState destination,
		Collection<SettlementBuildSite> sourceBuildSites,
		Collection<SettlementBuildSite> destinationBuildSites,
		Map<String, Integer> sourceStock,
		Map<String, Integer> destinationStock
	) {
		for (SettlementEconomyRules.TargetRule targetRule : SettlementEconomyRules.targetRules()) {
			int sourceTarget = SettlementEconomyRules.targetForGoods(source, sourceBuildSites, targetRule.goodsKey());
			int destinationTarget = SettlementEconomyRules.targetForGoods(destination, destinationBuildSites, targetRule.goodsKey());
			int sourceCurrent = sourceStock.getOrDefault(targetRule.goodsKey(), 0);
			int destinationCurrent = destinationStock.getOrDefault(targetRule.goodsKey(), 0);
			int sourceSurplus = Math.max(0, sourceCurrent - sourceTarget);
			int sourceReserve = sourceTarget <= 0 ? 0 : Math.max(1, (int) Math.ceil(sourceTarget * SOURCE_RESERVE_RATIO));
			int sourceAvailableToSend = Math.max(0, sourceCurrent - sourceReserve);
			int destinationShortage = Math.max(0, destinationTarget - destinationCurrent);

			if (sourceAvailableToSend <= 0 || destinationShortage <= 0) {
				continue;
			}

			if (sourceSurplus <= 0 && sourceTarget > 0 && destinationTarget > 0) {
				double sourceRatio = sourceCurrent / (double) Math.max(1, sourceTarget);
				double destinationRatio = destinationCurrent / (double) Math.max(1, destinationTarget);

				if (sourceRatio <= destinationRatio + MIN_SHORTAGE_IMBALANCE_RATIO) {
					continue;
				}
			}

			demands.add(new GoodsDemand(
				targetRule.goodsKey(),
				source.id(),
				destination.id(),
				sourceAvailableToSend,
				destinationShortage,
				SettlementEconomyRules.shortagePriority(destination, destinationBuildSites, targetRule.goodsKey(), destinationCurrent, destinationTarget)
					+ sourceAvailableToSend
					+ (sourceSurplus > 0 ? 100 : 0)
			));
		}
	}

	private static int computeThroughputBudget(RouteState route, double elapsedDays) {
		if (elapsedDays <= 0.0D) {
			return 0;
		}

		double distanceKm = Math.max(0.1D, route.distanceBlocks() / 1_000.0D);
		double throughput = route.throughputBase() * route.quality() * route.security() * elapsedDays / (1.0D + distanceKm);

		if (throughput <= 0.0D) {
			return 0;
		}

		return Math.max(1, (int) Math.round(throughput));
	}

	private static long computeTradeIntervalTicks(
		RouteState route,
		SettlementState from,
		SettlementState to,
		SettlementTradeRange.TradeRangeProfile fromRange,
		SettlementTradeRange.TradeRangeProfile toRange
	) {
		double combinedPopulation = from.totalPopulation() + to.totalPopulation();
		double averageComfort = (from.comfort() + to.comfort()) * 0.5D;
		double sizeReduction = Math.min(2.5D, combinedPopulation / 8.0D);
		double routeReduction = tierTradeCadenceBonus(route.tier())
			+ route.quality() * 1.5D
			+ route.security() * 0.75D
			+ averageTradeCadenceBonus(fromRange, toRange, route.type() != RouteType.LAND)
			+ Math.max(0.0D, averageComfort - 0.80D);
		double intervalDays = clamp(MAX_TRADE_INTERVAL_DAYS - sizeReduction - routeReduction, MIN_TRADE_INTERVAL_DAYS, MAX_TRADE_INTERVAL_DAYS);
		return Math.max(MIN_ROUTE_UPDATE_TICKS, Math.round(intervalDays * SettlementEconomyRules.TICKS_PER_DAY));
	}

	private static SettlementTradeRange.TradeRangeProfile tradeProfileForRoute(ServerLevel level, RouteState route, SettlementState settlement) {
		SettlementConstruction.InfrastructureSurvey infrastructure = route.type() == RouteType.WATER
			? SettlementConstruction.survey(level, settlement)
			: SettlementConstruction.InfrastructureSurvey.empty();
		return SettlementTradeRange.profile(settlement, infrastructure);
	}

	private static double averageTradeCadenceBonus(
		SettlementTradeRange.TradeRangeProfile fromRange,
		SettlementTradeRange.TradeRangeProfile toRange,
		boolean waterRoute
	) {
		return waterRoute
			? (fromRange.waterTradeCadenceBonusDays() + toRange.waterTradeCadenceBonusDays()) * 0.5D
			: (fromRange.landTradeCadenceBonusDays() + toRange.landTradeCadenceBonusDays()) * 0.5D;
	}

	private static double averageTradeQualityBonus(
		SettlementTradeRange.TradeRangeProfile fromRange,
		SettlementTradeRange.TradeRangeProfile toRange,
		boolean waterRoute
	) {
		return waterRoute
			? (fromRange.waterTradeQualityBonus() + toRange.waterTradeQualityBonus()) * 0.5D
			: (fromRange.landTradeQualityBonus() + toRange.landTradeQualityBonus()) * 0.5D;
	}

	private static double averageRouteTradeBonus(
		SettlementTradeRange.TradeRangeProfile fromRange,
		SettlementTradeRange.TradeRangeProfile toRange
	) {
		return (fromRange.routeTradeBonus() + toRange.routeTradeBonus()) * 0.5D;
	}

	private static double tierTradeCadenceBonus(RouteTier tier) {
		return switch (tier) {
			case NONE -> 0.0D;
			case TRAIL -> 0.25D;
			case GRAVEL -> 0.75D;
			case COBBLE, RIVER -> 1.25D;
			case BRICK, CANAL -> 1.75D;
			case SEA_LANE -> 2.25D;
		};
	}

	private static RouteTier determineLandTier(RouteTier currentTier, SurveyStats surveyStats) {
		if (surveyStats.loadedSamples() <= 0) {
			return currentTier;
		}

		if (surveyStats.brickSamples() >= Math.max(2, surveyStats.loadedSamples() / 4)) {
			return RouteTier.BRICK;
		}

		if (surveyStats.brickSamples() + surveyStats.cobbleSamples() >= Math.max(2, surveyStats.loadedSamples() / 4)) {
			return RouteTier.COBBLE;
		}

		if (surveyStats.brickSamples() + surveyStats.cobbleSamples() + surveyStats.gravelSamples() >= Math.max(2, surveyStats.loadedSamples() / 4)) {
			return RouteTier.GRAVEL;
		}

		if (surveyStats.pathSamples() > 0) {
			return RouteTier.TRAIL;
		}

		return currentTier == RouteTier.NONE ? RouteTier.TRAIL : currentTier;
	}

	private static double determineQuality(RouteTier tier, SurveyStats surveyStats, int distanceBlocks) {
		if (surveyStats.loadedSamples() <= 0) {
			return baseQuality(tier);
		}

		double pavedSamples = surveyStats.pathSamples()
			+ surveyStats.gravelSamples() * 1.2D
			+ surveyStats.cobbleSamples() * 1.5D
			+ surveyStats.brickSamples() * 1.8D;
		double coverage = pavedSamples / (surveyStats.loadedSamples() * 1.8D);
		double distancePenalty = Math.min(0.18D, distanceBlocks / 8_192.0D);
		return clamp(baseQuality(tier) + coverage * 0.24D - distancePenalty, 0.25D, 0.95D);
	}

	private static double determineRouteSecurity(SettlementState from, SettlementState to, double quality) {
		double settlementAverage = (from.security() + to.security()) * 0.5D;
		return clamp(settlementAverage * 0.70D + quality * 0.30D, 0.18D, 0.95D);
	}

	private static int determineThroughputBase(RouteTier tier, double quality, int distanceBlocks) {
		int tierBase = switch (tier) {
			case NONE -> 40;
			case TRAIL -> 64;
			case GRAVEL -> 84;
			case COBBLE -> 112;
			case BRICK -> 144;
			case RIVER -> 128;
			case CANAL -> 164;
			case SEA_LANE -> 192;
		};
		int distancePenalty = Math.max(0, distanceBlocks / 16);
		return Math.max(24, (int) Math.round(tierBase * (0.75D + quality * 0.5D)) - distancePenalty);
	}

	private static double baseQuality(RouteTier tier) {
		return switch (tier) {
			case NONE -> 0.25D;
			case TRAIL -> 0.40D;
			case GRAVEL -> 0.55D;
			case COBBLE -> 0.72D;
			case BRICK -> 0.84D;
			case RIVER -> 0.72D;
			case CANAL -> 0.84D;
			case SEA_LANE -> 0.90D;
		};
	}

	private static boolean isTrail(BlockState surfaceState, BlockState belowState) {
		return surfaceState.is(Blocks.DIRT_PATH)
			|| belowState.is(Blocks.DIRT_PATH)
			|| surfaceState.is(Blocks.COARSE_DIRT)
			|| surfaceState.is(Blocks.DIRT);
	}

	private static boolean isGravelRoad(BlockState surfaceState, BlockState belowState) {
		return surfaceState.is(Blocks.GRAVEL) || belowState.is(Blocks.GRAVEL);
	}

	private static boolean isCobbleRoad(BlockState surfaceState, BlockState belowState) {
		return surfaceState.is(Blocks.COBBLESTONE)
			|| surfaceState.is(Blocks.MOSSY_COBBLESTONE)
			|| surfaceState.is(Blocks.COBBLESTONE_SLAB)
			|| surfaceState.is(Blocks.COBBLESTONE_STAIRS)
			|| surfaceState.is(Blocks.STONE)
			|| surfaceState.is(Blocks.STONE_SLAB)
			|| surfaceState.is(Blocks.STONE_STAIRS)
			|| isWoodenBridgeDeck(surfaceState)
			|| belowState.is(Blocks.COBBLESTONE)
			|| belowState.is(Blocks.MOSSY_COBBLESTONE)
			|| belowState.is(Blocks.STONE)
			|| isWoodenBridgeDeck(belowState);
	}

	private static boolean isBrickRoad(BlockState surfaceState, BlockState belowState) {
		return surfaceState.is(Blocks.BRICKS)
			|| surfaceState.is(Blocks.BRICK_SLAB)
			|| surfaceState.is(Blocks.STONE_BRICKS)
			|| surfaceState.is(Blocks.MOSSY_STONE_BRICKS)
			|| surfaceState.is(Blocks.STONE_BRICK_SLAB)
			|| surfaceState.is(Blocks.SMOOTH_STONE)
			|| surfaceState.is(Blocks.SMOOTH_STONE_SLAB)
			|| belowState.is(Blocks.BRICKS)
			|| belowState.is(Blocks.STONE_BRICKS)
			|| belowState.is(Blocks.MOSSY_STONE_BRICKS)
			|| belowState.is(Blocks.SMOOTH_STONE);
	}

	private static boolean isWoodenBridgeDeck(BlockState state) {
		return isInTag(state, BlockTags.PLANKS)
			|| isInTag(state, BlockTags.WOODEN_SLABS)
			|| isInTag(state, BlockTags.WOODEN_STAIRS);
	}

	private static String summarizeShipments(List<Shipment> shipments, SettlementState from, SettlementState to) {
		List<String> parts = new ArrayList<>();

		for (Shipment shipment : shipments) {
			String sourceName = shipment.fromSettlementId().equals(from.id()) ? from.name() : to.name();
			String destinationName = shipment.toSettlementId().equals(from.id()) ? from.name() : to.name();
			parts.add("%s %dx %s->%s".formatted(humanizeGoodsKey(shipment.goodsKey()), shipment.amount(), sourceName, destinationName));
		}

		return String.join(", ", parts);
	}

	private static String humanizeGoodsKey(String goodsKey) {
		String[] parts = goodsKey.split("_");
		StringBuilder result = new StringBuilder();

		for (String part : parts) {
			if (part.isEmpty()) {
				continue;
			}

			if (!result.isEmpty()) {
				result.append(' ');
			}

			result.append(Character.toUpperCase(part.charAt(0)));
			result.append(part.substring(1));
		}

		return result.toString();
	}

	private static void cleanMap(Map<String, Integer> values) {
		values.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() <= 0);
	}

	private static double lerp(int start, int end, double t) {
		return start + (end - start) * t;
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	public record RouteAdvanceResult(RouteState route, SettlementState fromSettlement, SettlementState toSettlement) {
	}

	private record SurveyStats(int loadedSamples, int pathSamples, int gravelSamples, int cobbleSamples, int brickSamples) {
	}

	private enum RoadSampleKind {
		NONE(0),
		TRAIL(1),
		GRAVEL(2),
		COBBLE(3),
		BRICK(4);

		private final int weight;

		RoadSampleKind(int weight) {
			this.weight = weight;
		}

		private int weight() {
			return weight;
		}
	}

	private record RoadSample(boolean loaded, RoadSampleKind kind) {
	}

	private record GoodsDemand(
		String goodsKey,
		String fromSettlementId,
		String toSettlementId,
		int availableToSend,
		int neededAtDestination,
		int priority
	) {
	}

	private record Shipment(String goodsKey, int amount, String fromSettlementId, String toSettlementId) {
	}

	private record TradeAdvanceResult(RouteState route, SettlementState fromSettlement, SettlementState toSettlement) {
	}

	private static boolean isInTag(BlockState state, TagKey<Block> tag) {
		return state.is(tag, blockState -> true);
	}
}
