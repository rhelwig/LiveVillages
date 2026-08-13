package com.ronhelwig.livevillages.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import com.ronhelwig.livevillages.MinecraftBootstrapTestSupport;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class SettlementProfessionUniformsTest extends MinecraftBootstrapTestSupport {
	@Test
	void overlayTextureKeepsTierOneAsTheBaseFile() {
		Identifier base = Identifier.fromNamespaceAndPath("live-villages", "textures/entity/villager/profession/forester.png");
		assertEquals(base, SettlementProfessionUniforms.overlayTexture(base, 1));
	}

	@Test
	void overlayTextureAppendsTheRequestedCivicTier() {
		Identifier base = Identifier.fromNamespaceAndPath("live-villages", "textures/entity/villager/profession/forester.png");
		assertEquals(
			"textures/entity/villager/profession/forester_tier3.png",
			SettlementProfessionUniforms.overlayTexture(base, 3).getPath()
		);
	}

	@Test
	void resolveOverlayFallsBackToTheNextLowerAuthoredLook() {
		Identifier base = Identifier.fromNamespaceAndPath("live-villages", "textures/entity/villager/profession/baker.png");
		Set<String> present = Set.of("textures/entity/villager/profession/baker.png", "textures/entity/villager/profession/baker_tier2.png");

		Identifier resolved = SettlementProfessionUniforms.resolveOverlay(base, 4, id -> present.contains(id.getPath()));
		assertEquals("textures/entity/villager/profession/baker_tier2.png", resolved.getPath());
	}

	@Test
	void resolveOverlayUsesTheBaseLookWhenNoHigherTierExists() {
		Identifier base = Identifier.fromNamespaceAndPath("minecraft", "textures/entity/villager/profession/farmer.png");
		Identifier resolved = SettlementProfessionUniforms.resolveOverlay(base, 3, id -> false);
		assertEquals(base, resolved);
	}

	@Test
	void civicTierRefreshUsesABoundedInterval() {
		assertFalse(SettlementProfessionUniforms.shouldRefreshCivicTier(0));
		assertTrue(SettlementProfessionUniforms.shouldRefreshCivicTier(40));
		assertFalse(SettlementProfessionUniforms.shouldRefreshCivicTier(41));
	}
}
