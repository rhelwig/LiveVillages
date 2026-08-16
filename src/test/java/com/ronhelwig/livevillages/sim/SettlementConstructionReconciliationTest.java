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

	private static SettlementBuildSite buildSite(SettlementBuildSiteType type) {
		return new SettlementBuildSite(
			"test", "settlement", type, BlockPos.ZERO, BlockPos.ZERO, BlockPos.ZERO,
			Direction.NORTH, "birch", "cobblestone", Map.of(), List.of(), false, 0L, 0L
		);
	}
}
