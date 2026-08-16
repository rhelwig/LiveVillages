package com.ronhelwig.livevillages.sim;

import static org.junit.jupiter.api.Assertions.*;

import com.ronhelwig.livevillages.MinecraftBootstrapTestSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SettlementEconomyRulesTest extends MinecraftBootstrapTestSupport {
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
	void workerScalingHandlesInvalidInputsAndAppliesDefaultMultiplier() {
		assertEquals(1, SettlementEconomyRules.scaledWorkerTickInterval(0));
		assertEquals(10, SettlementEconomyRules.scaledWorkerTickInterval(20));
		assertEquals(50, SettlementEconomyRules.scaledWorkerTickInterval(100));
		assertEquals(0, SettlementEconomyRules.scaledWorkerDailyUnits(0));
		assertEquals(6, SettlementEconomyRules.scaledWorkerDailyUnits(3));
		assertEquals(5.0D, SettlementEconomyRules.scaledWorkerDailyRate(2.5D));
	}

	@Test
	void workerScalingUsesTheConfiguredMultiplierAndRejectsInvalidValues() {
		assertEquals(2.0D, SettlementEconomyRules.sanitizeWorkerProductivityMultiplier(2.0D));
		assertEquals(0.25D, SettlementEconomyRules.sanitizeWorkerProductivityMultiplier(0.25D));
		assertEquals(50.0D, SettlementEconomyRules.sanitizeWorkerProductivityMultiplier(50.0D));
		assertEquals(50.0D, SettlementEconomyRules.sanitizeWorkerProductivityMultiplier(80.0D));
		assertEquals(2.0D, SettlementEconomyRules.sanitizeWorkerProductivityMultiplier(0.0D));
		assertEquals(2.0D, SettlementEconomyRules.sanitizeWorkerProductivityMultiplier(-1.0D));
		assertEquals(2.0D, SettlementEconomyRules.sanitizeWorkerProductivityMultiplier(Double.NaN));
		assertEquals(100, SettlementEconomyRules.scaledWorkerTickInterval(100, 1.0D));
		assertEquals(3, SettlementEconomyRules.scaledWorkerDailyUnits(3, 1.0D));
		assertEquals(2.5D, SettlementEconomyRules.scaledWorkerDailyRate(2.5D, 1.0D));
		assertEquals(10, SettlementEconomyRules.scaledWorkerTickInterval(100, 10.0D));
		assertEquals(4, SettlementEconomyRules.scaledWorkerTickInterval(100, 50.0D));
		assertEquals(30, SettlementEconomyRules.scaledWorkerDailyUnits(3, 10.0D));
		assertEquals(150, SettlementEconomyRules.scaledWorkerDailyUnits(3, 50.0D));
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
	void housingPressureCountsMissingBedsAndIgnoresOutposts() {
		SettlementState village = new SettlementState(
			"moss",
			"Mossfield",
			net.minecraft.world.level.Level.OVERWORLD,
			net.minecraft.core.BlockPos.ZERO,
			SettlementKind.CUSTOM,
			1,
			Map.of("trademaster", 1),
			Map.of(),
			Map.of("wool", 9, "logs", 88, "planks", 149, "cobblestone", 106),
			0,
			1.0D,
			0.1D,
			0,
			0.0D,
			List.of(),
			0L,
			0L
		);
		assertEquals(2, SettlementEconomyRules.housingPressure(village));
		assertEquals(2, SettlementEconomyRules.targetForGoods(village, "bed"));

		SettlementState housed = village.withHousingCapacity(2);
		assertEquals(0, SettlementEconomyRules.housingPressure(housed));
		assertEquals(0, SettlementEconomyRules.targetForGoods(housed, "bed"));

		SettlementState outpost = new SettlementState(
			"camp",
			"Camp",
			net.minecraft.world.level.Level.OVERWORLD,
			net.minecraft.core.BlockPos.ZERO,
			SettlementKind.OUTPOST,
			1,
			Map.of("guard", 2),
			Map.of(),
			Map.of(),
			0,
			1.0D,
			0.1D,
			0,
			0.0D,
			List.of(),
			0L,
			0L
		);
		assertEquals(0, SettlementEconomyRules.housingPressure(outpost));
	}

	@Test
	void settlementAmbitionsKeepHousingAheadOfPopulationAndAimForTheNextTier() {
		SettlementState village = settlementWithPopulationAndHousing(7, 8);
		assertEquals(11, SettlementEconomySimulator.desiredGrowthHousingCapacity(village));
		assertEquals(16, SettlementTiers.nextPopulationGoal(village));

		SettlementState nearTierTwo = settlementWithPopulationAndHousing(15, 15);
		assertEquals(16, SettlementEconomySimulator.desiredGrowthHousingCapacity(nearTierTwo));
	}

	@Test
	void peacefulSettlementsStillWantDefenseButLaterAndAtLowerDensity() {
		assertEquals(8, SettlementEconomySimulator.minimumDefensePopulation(net.minecraft.world.Difficulty.PEACEFUL));
		assertEquals(3, SettlementEconomySimulator.minimumDefensePopulation(net.minecraft.world.Difficulty.NORMAL));
		assertEquals(1, SettlementEconomySimulator.desiredDefenseLevel(8, net.minecraft.world.Difficulty.PEACEFUL));
		assertEquals(2, SettlementEconomySimulator.desiredDefenseLevel(8, net.minecraft.world.Difficulty.HARD));
	}

	@Test
	void steepTerrainRaisesRoadwrightPriority() {
		SettlementTerrainAssessment.TerrainAssessment flat = SettlementTerrainAssessment.fromSamples(64, 66, 8, 24);
		SettlementTerrainAssessment.TerrainAssessment mountain = SettlementTerrainAssessment.fromSamples(58, 102, 180, 24);

		assertFalse(flat.isSteep());
		assertTrue(mountain.isSteep());
		assertTrue(mountain.roadwrightPriorityBonus() > flat.roadwrightPriorityBonus());
	}

	private static SettlementState settlementWithPopulationAndHousing(int population, int housing) {
		return new SettlementState(
			"ambition", "Ambition", net.minecraft.world.level.Level.OVERWORLD, net.minecraft.core.BlockPos.ZERO,
			SettlementKind.CUSTOM, 1, Map.of("farmer", population), Map.of(), Map.of("bread", 128),
			housing, 1.0D, 0.5D, 0, 0.0D, List.of(), 0L, 0L
		);
	}

	@Test
	void populatedVillageWantsAStarterFarmBeforeFarmersExist() {
		SettlementState village = new SettlementState(
			"moss",
			"Mossfield",
			net.minecraft.world.level.Level.OVERWORLD,
			net.minecraft.core.BlockPos.ZERO,
			SettlementKind.CUSTOM,
			1,
			Map.of("trademaster", 2),
			Map.of(),
			Map.of("planks", 40),
			0,
			1.0D,
			0.1D,
			0,
			0.0D,
			List.of(),
			0L,
			0L
		);
		assertEquals(1, SettlementConstruction.desiredFarmSites(village, 0));
		assertTrue(SettlementEconomyRules.plannedDemandForGoods(village, "planks") >= 7);

		SettlementState empty = village.withPopulation(Map.of());
		assertEquals(0, SettlementConstruction.desiredFarmSites(empty, 0));

		SettlementState farmers = village.withPopulation(Map.of("farmer", 4));
		assertEquals(2, SettlementConstruction.desiredFarmSites(farmers, 0));
		assertEquals(2, SettlementConstruction.desiredFarmSites(farmers, 2));
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
