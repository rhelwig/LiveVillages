package com.ronhelwig.livevillages.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import com.ronhelwig.livevillages.MinecraftBootstrapTestSupport;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class SettlementStateModelTest extends MinecraftBootstrapTestSupport {
	@Test
	void settlementDefensivelyCopiesCollectionsAndNormalizesTier() {
		Map<String, Integer> population = new LinkedHashMap<>(Map.of("farmer", 2));
		List<SettlementProject> projects = new ArrayList<>(List.of(project()));
		SettlementState state = new SettlementState(
			"id", "Name", Level.OVERWORLD, BlockPos.ZERO, SettlementKind.VILLAGE, 99,
			population, Map.of(), Map.of(), 2, 1.0D, 0.0D, 0, 0.0D, projects, 4L, 3L
		);
		population.put("guard", 1);
		projects.clear();

		assertEquals(4, state.tier());
		assertEquals(2, state.totalPopulation());
		assertEquals(1, state.projects().size());
		assertThrows(UnsupportedOperationException.class, () -> state.population().put("guard", 1));
	}

	@Test
	void settlementCodecRoundTripsTimelineAndProjects() {
		SettlementState state = new SettlementState(
			"id", "Name", Level.OVERWORLD, new BlockPos(12, 70, -13), SettlementKind.HARBOR, 2,
			Map.of("farmer", 3), Map.of("emerald", 500), Map.of("bread", 4), 5,
			1.2D, 0.4D, 2, 0.5D, List.of(project()), 90L, 10L
		);

		assertEquals(state, roundTrip(SettlementState.CODEC, state));
	}

	@Test
	void projectProgressPercentIsBoundedAndHandlesNoRequiredWork() {
		assertEquals(0, new SettlementProject("p", SettlementProjectType.ROAD, "", -1, 10).progressPercent());
		assertEquals(50, new SettlementProject("p", SettlementProjectType.ROAD, "", 5, 10).progressPercent());
		assertEquals(100, new SettlementProject("p", SettlementProjectType.ROAD, "", 11, 10).progressPercent());
		assertEquals(100, new SettlementProject("p", SettlementProjectType.ROAD, "", 0, 0).progressPercent());
	}

	@Test
	void buildBlockFactoriesAndUpdatesPreserveConstructionIntent() {
		SettlementBuildBlockState block = SettlementBuildBlockState.pending("1,2,3", 'W', "oak_planks")
			.withStatus(SettlementBuildBlockStatus.BLOCKED, null)
			.withBlueprintSymbol('L', null);

		assertEquals("1,2,3", block.position());
		assertEquals("L", block.blueprintSymbol());
		assertEquals("", block.expectedMaterialKey());
		assertEquals(SettlementBuildBlockStatus.BLOCKED, block.status());
		assertEquals("", block.blocker());
	}

	@Test
	void buildSiteFiltersInvalidMaterialsAndUsesLegacyAnchorFallback() {
		Map<String, Integer> materials = new LinkedHashMap<>();
		materials.put("planks", 4);
		materials.put("", 2);
		materials.put("logs", 0);
		SettlementBuildSite site = site(materials);

		assertEquals(Map.of("planks", 4), site.siteMaterials());
		assertTrue(site.referencesWorkstation(site.anchorPos()));
		assertTrue(site.referencesWorkstation(site.workstationPos()));
		assertFalse(site.referencesWorkstation(BlockPos.ZERO));

		var encoded = SettlementBuildSite.CODEC.encodeStart(JsonOps.INSTANCE, site).result().orElseThrow().getAsJsonObject();
		encoded.remove("anchor_pos");
		SettlementBuildSite legacy = SettlementBuildSite.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow();
		assertEquals(site.workstationPos(), legacy.anchorPos());
	}

	@Test
	void buildSiteCodecRoundTripsAndCopiesBlocks() {
		SettlementBuildSite site = site(Map.of("planks", 4));
		assertEquals(site, roundTrip(SettlementBuildSite.CODEC, site));
	}

	private static SettlementProject project() {
		return new SettlementProject("project", SettlementProjectType.HOUSING, "", 2.0D, 10.0D);
	}

	private static SettlementBuildSite site(Map<String, Integer> materials) {
		return new SettlementBuildSite(
			"site", "settlement", SettlementBuildSiteType.CARPENTER_WORKSHOP,
			new BlockPos(10, 64, 10), new BlockPos(11, 64, 11), new BlockPos(12, 64, 12),
			Direction.NORTH, "oak", "cobblestone", materials,
			List.of(SettlementBuildBlockState.pending("0,0,0", 'P', "oak_planks")),
			false, 1L, 2L
		);
	}

	private static <T> T roundTrip(com.mojang.serialization.Codec<T> codec, T value) {
		return codec.parse(JsonOps.INSTANCE, codec.encodeStart(JsonOps.INSTANCE, value).result().orElseThrow())
			.result().orElseThrow();
	}
}
