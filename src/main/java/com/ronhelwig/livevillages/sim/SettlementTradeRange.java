package com.ronhelwig.livevillages.sim;

public final class SettlementTradeRange {
	private static final int LOCAL_LAND_ROUTE_RANGE_BLOCKS = 512;
	private static final int BASE_LAND_ROUTE_RANGE_BLOCKS = LOCAL_LAND_ROUTE_RANGE_BLOCKS;
	private static final int CARTOGRAPHER_ROUTE_RANGE_BONUS_BLOCKS = 512;
	private static final int SCRIBE_ROUTE_RANGE_BONUS_BLOCKS = 256;
	private static final int DOCK_WATER_ROUTE_RANGE_BLOCKS = 1_280;
	private static final int LIGHTHOUSE_WATER_ROUTE_RANGE_BONUS_BLOCKS = 512;
	private static final int CARTOGRAPHER_WATER_ROUTE_BONUS_BLOCKS = 256;
	private static final int SCRIBE_WATER_ROUTE_BONUS_BLOCKS = 256;
	private static final int PORTMASTER_MAP_MIN_DISTANCE_BLOCKS = 1_024;
	private static final int PORTMASTER_MAP_MAX_DISTANCE_BLOCKS = 2_048;
	private static final int LIGHTHOUSE_ADDITIONAL_WATER_RANGE_BONUS_BLOCKS = 64;
	private static final int LIGHTHOUSE_ADDITIONAL_WATER_RANGE_BONUS_CAP_BLOCKS = 192;
	private static final double CARTOGRAPHER_TRADE_CADENCE_BONUS_DAYS = 0.40D;
	private static final double SCRIBE_TRADE_CADENCE_BONUS_DAYS = 0.40D;
	private static final double CARTOGRAPHER_TRADE_QUALITY_BONUS = 0.04D;
	private static final double SCRIBE_TRADE_QUALITY_BONUS = 0.04D;
	private static final double CARTOGRAPHER_LOCAL_TRADE_BONUS = 0.30D;
	private static final double SCRIBE_LOCAL_TRADE_BONUS = 0.30D;
	private static final double CARTOGRAPHER_ROUTE_TRADE_BONUS = 0.10D;
	private static final double SCRIBE_ROUTE_TRADE_BONUS = 0.10D;
	private static final double LIGHTHOUSE_TRADE_CADENCE_BONUS_DAYS = 0.60D;
	private static final double LIGHTHOUSE_ADDITIONAL_TRADE_CADENCE_BONUS_DAYS = 0.08D;
	private static final double LIGHTHOUSE_ADDITIONAL_TRADE_CADENCE_BONUS_CAP_DAYS = 0.24D;
	private static final double LIGHTHOUSE_TRADE_QUALITY_BONUS = 0.07D;
	private static final double LIGHTHOUSE_ADDITIONAL_TRADE_QUALITY_BONUS = 0.01D;
	private static final double LIGHTHOUSE_ADDITIONAL_TRADE_QUALITY_BONUS_CAP = 0.03D;
	private static final double LIGHTHOUSE_LOCAL_TRADE_BONUS = 0.70D;
	private static final double LIGHTHOUSE_ADDITIONAL_LOCAL_TRADE_BONUS = 0.08D;
	private static final double LIGHTHOUSE_ADDITIONAL_LOCAL_TRADE_BONUS_CAP = 0.24D;
	private static final double LIGHTHOUSE_ROUTE_TRADE_BONUS = 0.22D;
	private static final double LIGHTHOUSE_ADDITIONAL_ROUTE_TRADE_BONUS = 0.02D;
	private static final double LIGHTHOUSE_ADDITIONAL_ROUTE_TRADE_BONUS_CAP = 0.06D;

	private SettlementTradeRange() {
	}

	public static int localLandRouteRangeBlocks() {
		return LOCAL_LAND_ROUTE_RANGE_BLOCKS;
	}

	public static TradeRangeProfile profile(SettlementState settlement, SettlementConstruction.InfrastructureSurvey infrastructure) {
		int cartographers = roleCount(settlement, SettlementRoleKeys.CARTOGRAPHER);
		int scribes = roleCount(settlement, SettlementRoleKeys.SCRIBE);
		int landRouteRangeBlocks = BASE_LAND_ROUTE_RANGE_BLOCKS
			+ rangeBonus(cartographers, CARTOGRAPHER_ROUTE_RANGE_BONUS_BLOCKS)
			+ rangeBonus(scribes, SCRIBE_ROUTE_RANGE_BONUS_BLOCKS);
		double landTradeCadenceBonusDays = presenceBonus(cartographers, CARTOGRAPHER_TRADE_CADENCE_BONUS_DAYS)
			+ presenceBonus(scribes, SCRIBE_TRADE_CADENCE_BONUS_DAYS);
		double landTradeQualityBonus = presenceBonus(cartographers, CARTOGRAPHER_TRADE_QUALITY_BONUS)
			+ presenceBonus(scribes, SCRIBE_TRADE_QUALITY_BONUS);
		double localTradeBonus = presenceBonus(cartographers, CARTOGRAPHER_LOCAL_TRADE_BONUS)
			+ presenceBonus(scribes, SCRIBE_LOCAL_TRADE_BONUS);
		double routeTradeBonus = presenceBonus(cartographers, CARTOGRAPHER_ROUTE_TRADE_BONUS)
			+ presenceBonus(scribes, SCRIBE_ROUTE_TRADE_BONUS);
		boolean waterRoutesUnlocked = infrastructure.available() && infrastructure.docks() > 0;
		int waterRouteRangeBlocks = 0;
		double waterTradeCadenceBonusDays = landTradeCadenceBonusDays;
		double waterTradeQualityBonus = landTradeQualityBonus;

		if (waterRoutesUnlocked) {
			waterRouteRangeBlocks = DOCK_WATER_ROUTE_RANGE_BLOCKS
				+ rangeBonus(cartographers, CARTOGRAPHER_WATER_ROUTE_BONUS_BLOCKS)
				+ rangeBonus(scribes, SCRIBE_WATER_ROUTE_BONUS_BLOCKS)
				+ lighthouseWaterRangeBonus(infrastructure.lighthouses());
			waterTradeCadenceBonusDays += lighthouseTradeCadenceBonus(infrastructure.lighthouses());
			waterTradeQualityBonus += lighthouseTradeQualityBonus(infrastructure.lighthouses());
			localTradeBonus += lighthouseLocalTradeBonus(infrastructure.lighthouses());
			routeTradeBonus += lighthouseRouteTradeBonus(infrastructure.lighthouses());
		}

		int portmasterMapDistanceBlocks = clamp(
			Math.max(PORTMASTER_MAP_MIN_DISTANCE_BLOCKS, waterRouteRangeBlocks),
			PORTMASTER_MAP_MIN_DISTANCE_BLOCKS,
			PORTMASTER_MAP_MAX_DISTANCE_BLOCKS
		);

		return new TradeRangeProfile(
			landRouteRangeBlocks,
			waterRouteRangeBlocks,
			portmasterMapDistanceBlocks,
			waterRoutesUnlocked,
			cartographers > 0,
			scribes > 0,
			landTradeCadenceBonusDays,
			waterTradeCadenceBonusDays,
			landTradeQualityBonus,
			waterTradeQualityBonus,
			localTradeBonus,
			routeTradeBonus
		);
	}

	private static int roleCount(SettlementState settlement, String role) {
		return Math.max(0, settlement.population().getOrDefault(role, 0));
	}

	private static int rangeBonus(int count, int perSourceBonusBlocks) {
		return count > 0 ? perSourceBonusBlocks : 0;
	}

	private static double presenceBonus(int count, double bonus) {
		return count > 0 ? bonus : 0.0D;
	}

	private static int lighthouseWaterRangeBonus(int lighthouseCount) {
		if (lighthouseCount <= 0) {
			return 0;
		}

		int extraLighthouses = Math.max(0, lighthouseCount - 1);
		return LIGHTHOUSE_WATER_ROUTE_RANGE_BONUS_BLOCKS
			+ Math.min(LIGHTHOUSE_ADDITIONAL_WATER_RANGE_BONUS_CAP_BLOCKS, extraLighthouses * LIGHTHOUSE_ADDITIONAL_WATER_RANGE_BONUS_BLOCKS);
	}

	private static double lighthouseTradeCadenceBonus(int lighthouseCount) {
		if (lighthouseCount <= 0) {
			return 0.0D;
		}

		int extraLighthouses = Math.max(0, lighthouseCount - 1);
		return LIGHTHOUSE_TRADE_CADENCE_BONUS_DAYS
			+ Math.min(
				LIGHTHOUSE_ADDITIONAL_TRADE_CADENCE_BONUS_CAP_DAYS,
				extraLighthouses * LIGHTHOUSE_ADDITIONAL_TRADE_CADENCE_BONUS_DAYS
			);
	}

	private static double lighthouseTradeQualityBonus(int lighthouseCount) {
		if (lighthouseCount <= 0) {
			return 0.0D;
		}

		int extraLighthouses = Math.max(0, lighthouseCount - 1);
		return LIGHTHOUSE_TRADE_QUALITY_BONUS
			+ Math.min(
				LIGHTHOUSE_ADDITIONAL_TRADE_QUALITY_BONUS_CAP,
				extraLighthouses * LIGHTHOUSE_ADDITIONAL_TRADE_QUALITY_BONUS
			);
	}

	private static double lighthouseLocalTradeBonus(int lighthouseCount) {
		if (lighthouseCount <= 0) {
			return 0.0D;
		}

		int extraLighthouses = Math.max(0, lighthouseCount - 1);
		return LIGHTHOUSE_LOCAL_TRADE_BONUS
			+ Math.min(
				LIGHTHOUSE_ADDITIONAL_LOCAL_TRADE_BONUS_CAP,
				extraLighthouses * LIGHTHOUSE_ADDITIONAL_LOCAL_TRADE_BONUS
			);
	}

	private static double lighthouseRouteTradeBonus(int lighthouseCount) {
		if (lighthouseCount <= 0) {
			return 0.0D;
		}

		int extraLighthouses = Math.max(0, lighthouseCount - 1);
		return LIGHTHOUSE_ROUTE_TRADE_BONUS
			+ Math.min(
				LIGHTHOUSE_ADDITIONAL_ROUTE_TRADE_BONUS_CAP,
				extraLighthouses * LIGHTHOUSE_ADDITIONAL_ROUTE_TRADE_BONUS
			);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	public record TradeRangeProfile(
		int landRouteRangeBlocks,
		int waterRouteRangeBlocks,
		int portmasterMapDistanceBlocks,
		boolean waterRoutesUnlocked,
		boolean hasCartographerSupport,
		boolean hasScribeSupport,
		double landTradeCadenceBonusDays,
		double waterTradeCadenceBonusDays,
		double landTradeQualityBonus,
		double waterTradeQualityBonus,
		double localTradeBonus,
		double routeTradeBonus
	) {
	}
}
