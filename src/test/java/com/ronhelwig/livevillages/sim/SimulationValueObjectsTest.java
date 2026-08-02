package com.ronhelwig.livevillages.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mojang.serialization.JsonOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.Test;

class SimulationValueObjectsTest {
	@Test
	void enumCodecsUseStablePersistentNames() {
		assertEquals("\"land\"", encoded(RouteType.CODEC, RouteType.LAND));
		assertEquals("\"smooth_stone\"", encoded(RouteTier.CODEC, RouteTier.SMOOTH_STONE));
		assertEquals("\"outpost\"", encoded(SettlementKind.CODEC, SettlementKind.OUTPOST));
		assertEquals("\"player_placed\"", encoded(SettlementBuildBlockStatus.CODEC, SettlementBuildBlockStatus.PLAYER_PLACED));
		assertEquals("\"banner_bearer\"", encoded(OutpostPlayerRank.CODEC, OutpostPlayerRank.BANNER_BEARER));
		assertEquals("\"returning\"", encoded(OutpostRaidPhase.CODEC, OutpostRaidPhase.RETURNING));
	}

	@Test
	void rankOrderingMatchesTheSpecifiedProgression() {
		assertEquals(false, OutpostPlayerRank.UNKNOWN.atLeast(OutpostPlayerRank.TOLERATED));
		assertEquals(true, OutpostPlayerRank.CAPTAIN.atLeast(OutpostPlayerRank.BANNER_BEARER));
		assertEquals(true, OutpostPlayerRank.RAIDER.atLeast(OutpostPlayerRank.RAIDER));
	}

	@Test
	void projectStockCostsAreImmutable() {
		assertEquals(22, SettlementProjectType.HOUSING.stockCost().get("planks"));
		assertThrows(UnsupportedOperationException.class, () -> SettlementProjectType.HOUSING.stockCost().put("logs", 99));
	}

	@Test
	void settlementClockIsSafeBeforeServerOrLevelExists() {
		assertEquals(0L, SettlementClock.persistentTick((MinecraftServer) null));
		assertEquals(0L, SettlementClock.persistentTick((ServerLevel) null));
	}

	private static <T> String encoded(com.mojang.serialization.Codec<T> codec, T value) {
		return codec.encodeStart(JsonOps.INSTANCE, value).result().orElseThrow().toString();
	}
}
