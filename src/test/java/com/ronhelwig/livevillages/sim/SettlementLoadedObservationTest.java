package com.ronhelwig.livevillages.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class SettlementLoadedObservationTest {
	@Test
	void villagerMembershipDistanceIgnoresElevation() {
		BlockPos center = new BlockPos(100, 80, -40);

		double surfaceDistance = SettlementLoadedObservation.horizontalDistanceToCenterSqr(110.5D, -39.5D, center);
		double bedrockDistance = SettlementLoadedObservation.horizontalDistanceToCenterSqr(110.5D, -39.5D, center);

		assertEquals(100.0D, surfaceDistance);
		assertEquals(surfaceDistance, bedrockDistance);
	}

	@Test
	void distanceUsesBlockCenterCoordinates() {
		BlockPos center = new BlockPos(10, 64, 20);

		assertEquals(0.0D, SettlementLoadedObservation.horizontalDistanceToCenterSqr(10.5D, 20.5D, center));
		assertEquals(25.0D, SettlementLoadedObservation.horizontalDistanceToCenterSqr(13.5D, 24.5D, center));
	}
}
