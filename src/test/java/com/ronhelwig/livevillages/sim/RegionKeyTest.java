package com.ronhelwig.livevillages.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mojang.serialization.JsonOps;
import com.ronhelwig.livevillages.MinecraftBootstrapTestSupport;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class RegionKeyTest extends MinecraftBootstrapTestSupport {
	@Test
	void blockCoordinatesUseFloorDivisionAcrossZero() {
		assertEquals(new RegionKey(Level.OVERWORLD, 0, 0), RegionKey.fromBlockPos(Level.OVERWORLD, new BlockPos(511, 0, 511)));
		assertEquals(new RegionKey(Level.OVERWORLD, 1, 1), RegionKey.fromBlockPos(Level.OVERWORLD, new BlockPos(512, 0, 512)));
		assertEquals(new RegionKey(Level.OVERWORLD, -1, -1), RegionKey.fromBlockPos(Level.OVERWORLD, new BlockPos(-1, 0, -1)));
		assertEquals(new RegionKey(Level.OVERWORLD, -2, -2), RegionKey.fromBlockPos(Level.OVERWORLD, new BlockPos(-513, 0, -513)));
	}

	@Test
	void midpointFloorsNegativeHalfCoordinatesBeforePartitioning() {
		RegionKey midpoint = RegionKey.midpoint(
			Level.OVERWORLD,
			new BlockPos(-513, 0, -513),
			new BlockPos(-512, 0, -512)
		);

		assertEquals(new RegionKey(Level.OVERWORLD, -2, -2), midpoint);
	}

	@Test
	void midpointDoesNotOverflowAtCoordinateExtremes() {
		RegionKey midpoint = RegionKey.midpoint(
			Level.OVERWORLD,
			new BlockPos(Integer.MAX_VALUE, 0, Integer.MAX_VALUE),
			new BlockPos(Integer.MAX_VALUE, 0, Integer.MAX_VALUE)
		);

		assertEquals(4_194_303, midpoint.x());
		assertEquals(4_194_303, midpoint.z());
	}

	@Test
	void codecRoundTripsAndComparatorIsDeterministic() {
		RegionKey key = new RegionKey(Level.NETHER, -3, 7);
		RegionKey decoded = RegionKey.CODEC.parse(
			JsonOps.INSTANCE,
			RegionKey.CODEC.encodeStart(JsonOps.INSTANCE, key).result().orElseThrow()
		).result().orElseThrow();
		assertEquals(key, decoded);

		List<RegionKey> keys = new ArrayList<>(List.of(
			new RegionKey(Level.OVERWORLD, 2, 0),
			new RegionKey(Level.NETHER, 0, 0),
			new RegionKey(Level.OVERWORLD, 1, 3),
			new RegionKey(Level.OVERWORLD, 1, 2)
		));
		keys.sort(RegionKey.COMPARATOR);

		assertEquals(List.of(
			new RegionKey(Level.OVERWORLD, 1, 2),
			new RegionKey(Level.OVERWORLD, 1, 3),
			new RegionKey(Level.OVERWORLD, 2, 0),
			new RegionKey(Level.NETHER, 0, 0)
		), keys);
	}
}
