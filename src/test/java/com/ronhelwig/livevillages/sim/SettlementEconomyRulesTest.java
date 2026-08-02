package com.ronhelwig.livevillages.sim;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SettlementEconomyRulesTest {
	@Test
	void bakeryGoodsFollowTheSpecifiedTierProgression() {
		assertTier("bread", 1);
		assertTier("baked_potato", 1);
		assertTier("cookie", 2);
		assertTier("pumpkin_pie", 2);
		assertTier("cake", 3);
		assertTier("golden_apple", 4);
	}

	@Test
	void workerScalingHandlesInvalidInputsAndAppliesPlaytestMultiplier() {
		assertEquals(1, SettlementEconomyRules.scaledWorkerTickInterval(0));
		assertEquals(20, SettlementEconomyRules.scaledWorkerTickInterval(20));
		assertEquals(50, SettlementEconomyRules.scaledWorkerTickInterval(100));
		assertEquals(0, SettlementEconomyRules.scaledWorkerDailyUnits(0));
		assertEquals(6, SettlementEconomyRules.scaledWorkerDailyUnits(3));
		assertEquals(5.0D, SettlementEconomyRules.scaledWorkerDailyRate(2.5D));
	}

	@Test
	void periodicScalingIsDeterministicAndConservesDailyOutput() {
		assertEquals(0, SettlementEconomyRules.scaledPeriodicAmount("", 2.0D, 0, 24_000));
		assertEquals(0, SettlementEconomyRules.scaledPeriodicAmount("farmer", -1.0D, 0, 24_000));
		assertEquals(4, SettlementEconomyRules.scaledPeriodicAmount("farmer", 4.0D, 0, 24_000));
		int split = SettlementEconomyRules.scaledPeriodicAmount("farmer", 4.0D, 0, 12_000)
			+ SettlementEconomyRules.scaledPeriodicAmount("farmer", 4.0D, 12_000, 24_000);
		assertEquals(4, split);
	}

	@Test
	void targetRulesHaveUniqueKeysAndRejectUnknownGoods() {
		long distinct = SettlementEconomyRules.targetRules().stream().map(SettlementEconomyRules.TargetRule::goodsKey).distinct().count();
		assertEquals(SettlementEconomyRules.targetRules().size(), distinct);
		assertEquals(0, SettlementEconomyRules.targetForGoods("not_a_good", 20));
		assertFalse(SettlementEconomyRules.isFoodGoods("leather"));
		assertTrue(SettlementEconomyRules.isFoodGoods("bread"));
	}

	private static void assertTier(String goodsKey, int expectedTier) {
		assertEquals(expectedTier, SettlementEconomyRules.requiredTierForGoods(goodsKey));
		assertTrue(SettlementEconomyRules.isUnlockedForSettlementTier(goodsKey, expectedTier));
		if (expectedTier > 1) {
			assertFalse(SettlementEconomyRules.isUnlockedForSettlementTier(goodsKey, expectedTier - 1));
		}
	}
}
