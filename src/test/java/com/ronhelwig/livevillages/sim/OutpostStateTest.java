package com.ronhelwig.livevillages.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.mojang.serialization.JsonOps;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OutpostStateTest {
	@Test
	void standingClampsNegativePointsAndPromotesAcrossReachedThresholds() {
		assertEquals(0, new OutpostPlayerStanding(OutpostPlayerRank.UNKNOWN, -5, 0L).supportPoints());

		OutpostPlayerStanding promoted = OutpostPlayerStanding.unknown()
			.withParley(OutpostPlayerRank.TOLERATED, 20L)
			.withSupportAdded(100, 10, 25, 50, 100);

		assertEquals(OutpostPlayerRank.CAPTAIN, promoted.rank());
		assertEquals(100, promoted.supportPoints());
		assertEquals(20L, promoted.lastParleyTick());
	}

	@Test
	void nonPositiveSupportIsNoOpAndAdditionSaturatesInsteadOfOverflowing() {
		OutpostPlayerStanding standing = new OutpostPlayerStanding(OutpostPlayerRank.ASSOCIATE, Integer.MAX_VALUE - 2, 3L);

		assertSame(standing, standing.withSupportAdded(0, 10));
		assertSame(standing, standing.withSupportAdded(-1, 10));
		assertEquals(Integer.MAX_VALUE, standing.withSupportAdded(10, 10).supportPoints());
	}

	@Test
	void standingCodecRoundTrips() {
		OutpostPlayerStanding standing = new OutpostPlayerStanding(OutpostPlayerRank.BANNER_BEARER, 42, 99L);
		assertEquals(standing, roundTrip(OutpostPlayerStanding.CODEC, standing));
	}

	@Test
	void raidTransitionsPreserveHistoryAndResetPhaseProgress() {
		OutpostRaidState mustering = OutpostRaidState.mustering("outpost", "target", 4, 100L, 500L)
			.withControlProgress(25)
			.withAnnouncementTick(110L);
		OutpostRaidState marching = mustering.withPhase(OutpostRaidPhase.MARCHING, 120L);

		assertEquals(0, marching.controlProgressTicks());
		assertEquals(100L, marching.createdTick());
		assertEquals(120L, marching.phaseStartedTick());
		assertEquals(110L, marching.lastAnnouncementTick());
	}

	@Test
	void raidStateDefensivelyCopiesLootAndOffsetsOnlyEstablishedClocks() {
		Map<String, Integer> loot = new LinkedHashMap<>(Map.of("emerald", 3));
		OutpostRaidState state = OutpostRaidState.mustering("outpost", "target", 3, 100L, 0L)
			.returning("victory", 200L, 600L, loot, Map.of("support", 4));
		loot.put("bread", 2);

		assertEquals(Map.of("emerald", 3), state.lastLoot());
		OutpostRaidState offset = state.withClockOffset(50L);
		assertEquals(150L, offset.createdTick());
		assertEquals(250L, offset.phaseStartedTick());
		assertEquals(650L, offset.nextEligibleTick());
		assertEquals(0L, offset.lastAnnouncementTick());
		assertSame(state, state.withClockOffset(0L));
	}

	@Test
	void raidCodecRoundTripsItemizedCompletionFeedback() {
		OutpostRaidState state = OutpostRaidState.mustering("outpost", "target", 5, 1L, 2L)
			.completed("sacked", 20L, 100L, Map.of("bread", 8), Map.of("support", 3));

		OutpostRaidState decoded = roundTrip(OutpostRaidState.CODEC, state);
		assertEquals(state, decoded);
		assertNotSame(state.lastLoot(), decoded.lastLoot());
	}

	private static <T> T roundTrip(com.mojang.serialization.Codec<T> codec, T value) {
		return codec.parse(JsonOps.INSTANCE, codec.encodeStart(JsonOps.INSTANCE, value).result().orElseThrow())
			.result().orElseThrow();
	}
}
