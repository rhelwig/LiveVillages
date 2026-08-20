package com.ronhelwig.livevillages.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class SettlementHousingUpgradeTest {
	@Test
	void housingUpgradesAdvanceOnlyOneStructureStep() {
		assertEquals(
			SettlementBuildSiteType.HOUSING_SHELTER,
			SettlementConstruction.nextHousingUpgradeType(SettlementBuildSiteType.SIMPLE_HOUSING_SHELTER)
		);
		assertEquals(
			SettlementBuildSiteType.DUPLEX,
			SettlementConstruction.nextHousingUpgradeType(SettlementBuildSiteType.HOUSING_SHELTER)
		);
		assertNull(SettlementConstruction.nextHousingUpgradeType(SettlementBuildSiteType.DUPLEX));
	}

	@Test
	void duplexAddsASecondPairOfBeds() {
		assertEquals(1, SettlementConstruction.plannedBedCount(SettlementBuildSiteType.SIMPLE_HOUSING_SHELTER));
		assertEquals(2, SettlementConstruction.plannedBedCount(SettlementBuildSiteType.HOUSING_SHELTER));
		assertEquals(4, SettlementConstruction.plannedBedCount(SettlementBuildSiteType.DUPLEX));
	}
}
