package com.ronhelwig.livevillages.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

class SettlementChurchWorkTest {
	@Test
	void churchComfortRequiresACompletedChurch() {
		assertEquals(0.0D, SettlementChurchWork.comfortBonus(0, 1));
		assertEquals(0.04D, SettlementChurchWork.comfortBonus(1, 0), 0.0001D);
		assertEquals(0.07D, SettlementChurchWork.comfortBonus(1, 1), 0.0001D);
		assertEquals(0.10D, SettlementChurchWork.comfortBonus(2, 1), 0.0001D);
		assertEquals(0.13D, SettlementChurchWork.comfortBonus(3, 2), 0.0001D);
		assertEquals(0.17D, SettlementChurchWork.comfortBonus(4, 1), 0.0001D);
	}

	@Test
	void meetingWindowIsOneHourAfterBreakfastOnFullMoonDay() {
		assertEquals(3_600L, SettlementChurchWork.MEETING_START_TICK);
		assertEquals(1_000L, SettlementChurchWork.MEETING_DURATION_TICKS);
		assertEquals(8, SettlementChurchWork.MEETING_INTERVAL_DAYS);
		assertTrue(SettlementChurchWork.MEETING_START_TICK > 2_600L);
		assertTrue(SettlementChurchWork.MEETING_END_TICK < SettlementVillagerWorkSchedule.VILLAGE_GATHERING_START_TICK);
	}

	@Test
	void masonCopperBellCostsMatchThePlayerRecipe() {
		assertEquals(6, SettlementMasonWork.COPPER_BELL_INGOT_COST);
	}

	@Test
	void sanctuaryCoversTheNaveAndVestryButNotTheYard() {
		BlockPos origin = new BlockPos(0, 64, 0);
		Direction north = Direction.NORTH;

		assertTrue(contains(SettlementChurchSanctuary.volumesFor(origin, north, 1), relative(origin, north, 0, 5, 1)));
		assertTrue(contains(SettlementChurchSanctuary.volumesFor(origin, north, 1), relative(origin, north, 0, 9, 1)));
		assertFalse(contains(SettlementChurchSanctuary.volumesFor(origin, north, 1), relative(origin, north, 0, 10, 1)));
		assertFalse(contains(SettlementChurchSanctuary.volumesFor(origin, north, 1), relative(origin, north, 0, -2, 1)));

		assertTrue(contains(SettlementChurchSanctuary.volumesFor(origin, north, 2), relative(origin, north, 0, -2, 1)));
		assertTrue(contains(SettlementChurchSanctuary.volumesFor(origin, north, 2), relative(origin, north, 0, 8, 1)));
		assertFalse(contains(SettlementChurchSanctuary.volumesFor(origin, north, 2), relative(origin, north, -3, -2, 1)));
		assertFalse(contains(SettlementChurchSanctuary.volumesFor(origin, north, 2), relative(origin, north, 0, 12, 1)));

		assertTrue(contains(SettlementChurchSanctuary.volumesFor(origin, north, 3), relative(origin, north, 0, 10, 8)));
		assertFalse(contains(SettlementChurchSanctuary.volumesFor(origin, north, 2), relative(origin, north, 0, 10, 8)));
	}

	private static BlockPos relative(BlockPos origin, Direction facing, int right, int forward, int up) {
		return origin.relative(facing.getClockWise(), right).relative(facing, forward).above(up);
	}

	private static boolean contains(java.util.List<net.minecraft.world.phys.AABB> boxes, BlockPos pos) {
		double x = pos.getX() + 0.5D;
		double y = pos.getY() + 0.5D;
		double z = pos.getZ() + 0.5D;
		return boxes.stream().anyMatch(box -> box.contains(x, y, z));
	}
}
