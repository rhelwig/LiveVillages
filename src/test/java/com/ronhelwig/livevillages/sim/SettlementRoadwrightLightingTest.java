package com.ronhelwig.livevillages.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.IntStream;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class SettlementRoadwrightLightingTest {
	@Test
	void deterministicSpacingProducesOneCandidatePerTwelveStraightRoadBlocks() {
		long northSouthCandidates = IntStream.range(0, 24)
			.filter(z -> SettlementRoadwrightWork.shouldPlacePathLightAt(new BlockPos(0, 64, z)))
			.count();
		long eastWestCandidates = IntStream.range(0, 24)
			.filter(x -> SettlementRoadwrightWork.shouldPlacePathLightAt(new BlockPos(x, 64, 0)))
			.count();

		assertEquals(2, northSouthCandidates);
		assertEquals(2, eastWestCandidates);
	}

	@Test
	void routeRadiusReachesOneAdditionalMilepostInterval() {
		assertEquals(292, SettlementRoadwrightWork.roadwrightRouteRadius(192));
		assertEquals(512, SettlementRoadwrightWork.roadwrightRouteRadius(480));
	}
}
