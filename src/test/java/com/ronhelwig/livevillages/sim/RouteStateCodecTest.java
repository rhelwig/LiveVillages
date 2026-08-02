package com.ronhelwig.livevillages.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.ronhelwig.livevillages.MinecraftBootstrapTestSupport;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class RouteStateCodecTest extends MinecraftBootstrapTestSupport {
	@Test
	void codecRoundTripsAllPersistentFields() {
		RouteState route = new RouteState(
			"route-1", Level.OVERWORLD, "from", "to", RouteType.LAND, RouteTier.BRICK,
			1536, 0.8D, 0.65D, 128, "moved bread", 91L, 92L, 93L
		);

		assertEquals(route, roundTrip(route));
	}

	@Test
	void codecDefaultsPreserveLegacySaveCompatibility() {
		JsonObject json = JsonParser.parseString("""
			{
			  "id": "legacy",
			  "dimension": "minecraft:overworld",
			  "from_settlement_id": "a",
			  "to_settlement_id": "b",
			  "type": "land",
			  "tier": "gravel"
			}
			""").getAsJsonObject();

		RouteState route = RouteState.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();

		assertEquals(0, route.distanceBlocks());
		assertEquals(0.25D, route.quality());
		assertEquals(1.0D, route.security());
		assertEquals(64, route.throughputBase());
		assertEquals(RouteTier.GRAVEL, route.tier());
	}

	@Test
	void updateMethodsChangeOnlyTheirNamedTimelineFields() {
		RouteState original = RouteState.create("route", Level.OVERWORLD, "a", "b", RouteType.WATER);
		RouteState surveyed = original.withSurvey(RouteTier.RIVER, 400, 0.7D, 0.9D, 80, 10L);
		RouteState attempted = surveyed.withTradeAttemptTick(11L);
		RouteState traded = attempted.withTradeSummary("fish: 4", 12L);

		assertEquals(10L, traded.lastSurveyTick());
		assertEquals(12L, traded.lastTradeTick());
		assertEquals(12L, traded.lastTradeAttemptTick());
		assertEquals("fish: 4", traded.lastTransferSummary());
		assertEquals(original, original.withLastSurveyTick(0L));
	}

	private static RouteState roundTrip(RouteState value) {
		return RouteState.CODEC.parse(
			JsonOps.INSTANCE,
			RouteState.CODEC.encodeStart(JsonOps.INSTANCE, value).result().orElseThrow()
		).result().orElseThrow();
	}
}
