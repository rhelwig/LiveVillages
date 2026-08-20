package com.ronhelwig.livevillages.sim;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ronhelwig.livevillages.MinecraftBootstrapTestSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;

class SettlementConstructionReconciliationTest extends MinecraftBootstrapTestSupport {
	@Test
	void mineEntranceKeepsEquivalentLaddersAndLogs() {
		SettlementBuildSite mine = buildSite(SettlementBuildSiteType.MINE_ENTRANCE);
		var northLadder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH);
		var southLadder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
		var verticalLog = Blocks.BIRCH_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
		var horizontalLog = Blocks.BIRCH_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);

		assertTrue(SettlementConstructionWork.isEquivalentMineEntranceUtilityBlock(mine, northLadder, southLadder));
		assertTrue(SettlementConstructionWork.isEquivalentMineEntranceUtilityBlock(mine, verticalLog, horizontalLog));
	}

	@Test
	void otherStructuresStillRespectUtilityOrientation() {
		SettlementBuildSite housing = buildSite(SettlementBuildSiteType.HOUSING_SHELTER);
		var northLadder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH);
		var southLadder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);

		assertFalse(SettlementConstructionWork.isEquivalentMineEntranceUtilityBlock(housing, northLadder, southLadder));
	}

	@Test
	void emergencyHousingOnlyAddressesHomelessnessAndDefersToIncompleteStructures() {
		assertEquals(
			SettlementConstruction.EmergencyHousingAction.NONE,
			SettlementConstruction.emergencyHousingAction(40, 40, false, true)
		);
		assertEquals(
			SettlementConstruction.EmergencyHousingAction.NONE,
			SettlementConstruction.emergencyHousingAction(40, 20, false, false)
		);
		assertEquals(
			SettlementConstruction.EmergencyHousingAction.PLANNED_ONLY,
			SettlementConstruction.emergencyHousingAction(40, 20, true, true)
		);
		assertEquals(
			SettlementConstruction.EmergencyHousingAction.OUTDOOR_ALLOWED,
			SettlementConstruction.emergencyHousingAction(40, 20, false, true)
		);
	}

	@Test
	void mineEntranceFoundationDoesNotOccupyAuthoredShaftCells() {
		SettlementBuildSite mine = buildSite(SettlementBuildSiteType.MINE_ENTRANCE);
		SettlementBuildBlockState legacyLadderFill = SettlementBuildBlockState.pending("0,-1,-4", '0', "dirt");
		SettlementBuildBlockState legacyAirFill = SettlementBuildBlockState.pending("0,0,-4", '0', "dirt");
		SettlementBuildBlockState ordinaryOuterFill = SettlementBuildBlockState.pending("-2,-3,-4", '0', "dirt");

		assertTrue(SettlementConstruction.isObsoleteFoundationOverAuthoredBlueprint(mine, legacyLadderFill));
		assertTrue(SettlementConstruction.isObsoleteFoundationOverAuthoredBlueprint(mine, legacyAirFill));
		assertFalse(SettlementConstruction.isObsoleteFoundationOverAuthoredBlueprint(mine, ordinaryOuterFill));
	}

	@Test
	void chapelBlueprintFollowsAuthoredFootprint() {
		SettlementBuildSite tier2 = chapelSite(List.of(
			SettlementBuildBlockState.pending("-2,-3,1", 'M', "cobblestone")
		));
		SettlementBuildSite tier1 = chapelSite(List.of(
			SettlementBuildBlockState.pending("0,3,1", 'W', "altar")
		));

		assertEquals('B', SettlementConstruction.currentBlueprintSymbol(
			tier2,
			SettlementBuildBlockState.pending("1,-2,1", 'B', "bed")
		));
		assertEquals('W', SettlementConstruction.currentBlueprintSymbol(
			tier1,
			SettlementBuildBlockState.pending("0,3,1", 'W', "altar")
		));
		assertEquals('A', SettlementConstruction.currentBlueprintSymbol(
			tier1,
			SettlementBuildBlockState.pending("1,-2,1", 'B', "bed")
		));

		List<SettlementBuildBlockState> tier1Blocks = SettlementConstruction.currentBlueprintBlocks(tier1);
		List<SettlementBuildBlockState> tier2Blocks = SettlementConstruction.currentBlueprintBlocks(tier2);
		assertTrue(tier1Blocks.stream().anyMatch(block -> "0,3,1".equals(block.position())));
		assertTrue(tier1Blocks.stream().anyMatch(block -> "0,9,1".equals(block.position())));
		assertTrue(tier1Blocks.stream().noneMatch(block -> "1,-2,1".equals(block.position())));
		assertTrue(tier1Blocks.stream().noneMatch(block -> "0,11,1".equals(block.position())));
		assertTrue(tier2Blocks.stream().anyMatch(block -> "1,-2,1".equals(block.position())));
		assertTrue(tier2Blocks.stream().anyMatch(block -> "0,11,1".equals(block.position())));
		assertEquals("candle", SettlementConstruction.currentBlueprintMaterialKey(
			tier2,
			SettlementBuildBlockState.pending("-1,-2,2", 'U', "torch"),
			'U'
		));

		SettlementBuildSite tier3 = chapelSite(List.of(
			SettlementBuildBlockState.pending("0,11,8", 'M', "cobblestone")
		));
		SettlementBuildSite tier4 = chapelSite(List.of(
			SettlementBuildBlockState.pending("0,10,14", 'F', "fence")
		));
		assertEquals('M', SettlementConstruction.currentBlueprintSymbol(
			tier3,
			SettlementBuildBlockState.pending("0,11,8", 'M', "cobblestone")
		));
		assertEquals('F', SettlementConstruction.currentBlueprintSymbol(
			tier3,
			SettlementBuildBlockState.pending("0,10,13", 'F', "fence")
		));
		assertEquals('g', SettlementConstruction.currentBlueprintSymbol(
			tier3,
			SettlementBuildBlockState.pending("0,10,9", 'g', "copper_bell")
		));
		assertTrue(SettlementConstruction.currentBlueprintBlocks(tier3).stream().noneMatch(block -> "0,10,14".equals(block.position())));
		assertEquals('F', SettlementConstruction.currentBlueprintSymbol(
			tier4,
			SettlementBuildBlockState.pending("0,10,14", 'F', "fence")
		));
		assertEquals('F', SettlementConstruction.currentBlueprintSymbol(
			tier4,
			SettlementBuildBlockState.pending("0,-2,14", 'F', "fence")
		));
		assertEquals("waxed_copper_stairs", SettlementConstruction.currentBlueprintMaterialKey(
			tier3,
			SettlementBuildBlockState.pending("0,10,11", 'S', "stairs"),
			'S'
		));
	}

	@Test
	void leftoverFoundationDoesNotBlockCompletion() {
		SettlementBuildSite chapel = chapelSite(List.of(
			SettlementBuildBlockState.placed("0,3,1", 'W', "altar"),
			SettlementBuildBlockState.pending("0,3,-1", '0', "dirt")
		));

		assertFalse(SettlementConstruction.isRequiredBuildSiteBlock(
			chapel,
			SettlementBuildBlockState.pending("0,3,-1", '0', "dirt")
		));
		assertTrue(SettlementConstruction.isRequiredBuildSiteBlock(
			chapel,
			SettlementBuildBlockState.placed("0,3,1", 'W', "altar")
		));
		assertTrue(SettlementConstruction.isBuildSiteComplete(chapel));
	}

	@Test
	void rememberedCompleteSurvivesOptionalAndAirCells() {
		SettlementBuildSite church = chapelSite(List.of(
			SettlementBuildBlockState.placed("0,3,1", 'W', "altar"),
			SettlementBuildBlockState.pending("1,3,1", 'b', "brewing_stand"),
			SettlementBuildBlockState.pending("0,3,2", 'A', "")
		));
		church = church.withBlocks(church.blocks(), true, 1L);

		assertTrue(SettlementConstruction.isBuildSiteComplete(church));
		assertFalse(SettlementConstruction.isRequiredBuildSiteBlock(
			church,
			SettlementBuildBlockState.pending("1,3,1", 'b', "brewing_stand")
		));
	}

	private static SettlementBuildSite chapelSite(List<SettlementBuildBlockState> blocks) {
		return new SettlementBuildSite(
			"test",
			"settlement",
			SettlementBuildSiteType.CLERIC_SHRINE,
			BlockPos.ZERO,
			BlockPos.ZERO,
			BlockPos.ZERO,
			Direction.NORTH,
			"birch",
			"cobblestone",
			Map.of(),
			blocks,
			false,
			0L,
			0L
		);
	}

	private static SettlementBuildSite buildSite(SettlementBuildSiteType type) {
		return new SettlementBuildSite(
			"test", "settlement", type, BlockPos.ZERO, BlockPos.ZERO, BlockPos.ZERO,
			Direction.NORTH, "birch", "cobblestone", Map.of(), List.of(), false, 0L, 0L
		);
	}
}
