package com.ronhelwig.livevillages.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import com.ronhelwig.livevillages.MinecraftBootstrapTestSupport;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

class LiveVillagesSavedDataRemovalTest extends MinecraftBootstrapTestSupport {
	@Test
	void removingSettlementCascadesRoutesLedgersAndVillagerState() throws Exception {
		LiveVillagesSavedData data = new LiveVillagesSavedData();
		String removedId = "removed";
		String retainedId = "retained";
		UUID removedVillager = UUID.randomUUID();
		UUID retainedVillager = UUID.randomUUID();

		data.putSettlement(SettlementState.create(removedId, "Removed", Level.OVERWORLD, BlockPos.ZERO, SettlementKind.VILLAGE));
		data.putSettlement(SettlementState.create(retainedId, "Retained", Level.OVERWORLD, new BlockPos(128, 64, 0), SettlementKind.VILLAGE));
		data.putRoute(RouteState.create("connected", Level.OVERWORLD, removedId, retainedId, RouteType.LAND));
		data.putRoute(RouteState.create("retained-route", Level.OVERWORLD, retainedId, "third", RouteType.LAND));
		data.ensureScribeStarterRecipes(removedId, java.util.List.of("minecraft:torch"));
		data.addKnownScribeResources(removedId, java.util.List.of("coal"));
		data.setVillagerSettlement(removedVillager, removedId);
		data.setVillagerSettlement(retainedVillager, retainedId);
		data.setPreferredVillagerHome(removedVillager, new BlockPos(1, 64, 1));
		data.setPreferredVillagerHome(retainedVillager, new BlockPos(129, 64, 1));
		map(data, "loadedRoadworkCatchupTicks").put(removedId, 10L);
		map(data, "autonomousSupportStartOffsets").put(removedId + "|CARPENTER", 2);
		map(data, "autonomousSupportRetryAfterTicks").put(removedId + "|CARPENTER", 20L);
		map(data, "populationDiagnosticTicks").put("minecraft:overworld|" + removedId + "|economy", 30L);

		data.removeSettlement(removedId);

		assertTrue(data.getSettlement(removedId).isEmpty());
		assertEquals(java.util.List.of(), data.getRoutesForSettlement(removedId));
		assertEquals(java.util.List.of("retained-route"), data.getRoutes().stream().map(RouteState::id).toList());
		assertEquals(java.util.List.of(), data.knownScribeRecipeIds(removedId));
		assertEquals(java.util.List.of(), data.knownScribeResourceKeys(removedId));
		assertTrue(data.villagerSettlement(removedVillager).isEmpty());
		assertTrue(data.preferredVillagerHome(removedVillager).isEmpty());
		assertEquals(retainedId, data.villagerSettlement(retainedVillager).orElseThrow());
		assertTrue(data.preferredVillagerHome(retainedVillager).isPresent());
		assertFalse(map(data, "loadedRoadworkCatchupTicks").containsKey(removedId));
		assertFalse(map(data, "autonomousSupportStartOffsets").containsKey(removedId + "|CARPENTER"));
		assertFalse(map(data, "autonomousSupportRetryAfterTicks").containsKey(removedId + "|CARPENTER"));
		assertFalse(map(data, "populationDiagnosticTicks").containsKey("minecraft:overworld|" + removedId + "|economy"));
	}

	@SuppressWarnings("unchecked")
	private static <V> Map<String, V> map(LiveVillagesSavedData data, String fieldName) throws Exception {
		Field field = LiveVillagesSavedData.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		return (Map<String, V>) field.get(data);
	}
}
