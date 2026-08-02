package com.ronhelwig.livevillages.sim;

import static org.junit.jupiter.api.Assertions.*;

import com.ronhelwig.livevillages.MinecraftBootstrapTestSupport;
import java.util.Map;
import org.junit.jupiter.api.Test;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

class SettlementTradeRangeTest extends MinecraftBootstrapTestSupport {
	@Test
	void localLandRangeIsBoundedAndCartographerAndScribeBonusesArePresenceBased() {
		assertEquals(512, SettlementTradeRange.localLandRouteRangeBlocks());
		assertEquals(512, profile(Map.of()).landRouteRangeBlocks());
		assertEquals(1_024, profile(Map.of(SettlementRoleKeys.CARTOGRAPHER, 1)).landRouteRangeBlocks());
		assertEquals(1_280, profile(Map.of(SettlementRoleKeys.CARTOGRAPHER, 4, SettlementRoleKeys.SCRIBE, 3)).landRouteRangeBlocks());
	}

	@Test
	void waterRoutesRequireCompletedDockInfrastructure() {
		SettlementTradeRange.TradeRangeProfile unavailable = SettlementTradeRange.profile(
			settlement(Map.of(SettlementRoleKeys.CARTOGRAPHER, 1)), survey(false, 1, 1));
		assertFalse(unavailable.waterRoutesUnlocked());
		assertEquals(0, unavailable.waterRouteRangeBlocks());

		SettlementTradeRange.TradeRangeProfile noDock = SettlementTradeRange.profile(
			settlement(Map.of(SettlementRoleKeys.CARTOGRAPHER, 1)), survey(true, 0, 1));
		assertFalse(noDock.waterRoutesUnlocked());
	}

	@Test
	void dockAndLighthouseApplySpecifiedRangeAndCappedStackingBenefits() {
		SettlementTradeRange.TradeRangeProfile dock = SettlementTradeRange.profile(settlement(Map.of()), survey(true, 1, 0));
		assertTrue(dock.waterRoutesUnlocked());
		assertEquals(1_280, dock.waterRouteRangeBlocks());

		SettlementTradeRange.TradeRangeProfile supported = SettlementTradeRange.profile(
			settlement(Map.of(SettlementRoleKeys.CARTOGRAPHER, 1, SettlementRoleKeys.SCRIBE, 1)), survey(true, 1, 10));
		assertEquals(2_496, supported.waterRouteRangeBlocks());
		assertEquals(2_048, supported.portmasterMapDistanceBlocks());
		assertEquals(1.64D, supported.waterTradeCadenceBonusDays(), 0.000_001D);
		assertEquals(0.18D, supported.waterTradeQualityBonus(), 0.000_001D);
	}

	private static SettlementTradeRange.TradeRangeProfile profile(Map<String, Integer> population) {
		return SettlementTradeRange.profile(settlement(population), SettlementConstruction.InfrastructureSurvey.empty());
	}

	private static SettlementState settlement(Map<String, Integer> population) {
		return SettlementState.create("test", "Test", Level.OVERWORLD, BlockPos.ZERO, SettlementKind.VILLAGE)
			.withPopulation(population);
	}

	private static SettlementConstruction.InfrastructureSurvey survey(boolean available, int docks, int lighthouses) {
		return new SettlementConstruction.InfrastructureSurvey(
			available, 0, 0, 0, 0, 0, 0, docks, lighthouses, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0
		);
	}
}
