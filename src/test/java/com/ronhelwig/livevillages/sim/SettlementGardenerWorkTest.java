package com.ronhelwig.livevillages.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ronhelwig.livevillages.MinecraftBootstrapTestSupport;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.properties.Half;
import org.junit.jupiter.api.Test;

class SettlementGardenerWorkTest extends MinecraftBootstrapTestSupport {
	@Test
	void flowerBedTrapdoorOpensOutwardFromItsBed() {
		var eastEdging = SettlementGardenerWork.flowerBedTrapdoorState(Direction.EAST);

		assertEquals(Direction.EAST, eastEdging.getValue(TrapDoorBlock.FACING));
		assertEquals(Half.BOTTOM, eastEdging.getValue(TrapDoorBlock.HALF));
		assertTrue(eastEdging.getValue(TrapDoorBlock.OPEN));
	}
}
