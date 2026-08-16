package com.ronhelwig.livevillages.sim;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.brigadier.StringReader;
import com.ronhelwig.livevillages.MinecraftBootstrapTestSupport;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class SettlementVillagerTextureScaleTest extends MinecraftBootstrapTestSupport {
	@Test
	void gameRuleOptionsExposeTheThreePixelSizes() {
		assertArrayEquals(
			new String[] { "64", "128", "256" },
			java.util.Arrays.stream(SettlementVillagerTextureScale.Option.values())
				.map(SettlementVillagerTextureScale.Option::toString)
				.toArray(String[]::new)
		);
		assertEquals(64, SettlementVillagerTextureScale.Option.PIXELS_64.pixels());
		assertEquals(128, SettlementVillagerTextureScale.Option.PIXELS_128.pixels());
		assertEquals(256, SettlementVillagerTextureScale.Option.PIXELS_256.pixels());
		assertEquals(SettlementVillagerTextureScale.Option.PIXELS_256, SettlementVillagerTextureScale.NEW_WORLD_DEFAULT);
	}

	@Test
	void inWorldGameRuleProtocolParsesOnlyTheDisplayedNumericChoices() throws Exception {
		assertEquals(
			SettlementVillagerTextureScale.Option.PIXELS_128,
			SettlementVillagerTextureScale.optionArgumentType().parse(new StringReader("128"))
		);
		assertThrows(
			com.mojang.brigadier.exceptions.CommandSyntaxException.class,
			() -> SettlementVillagerTextureScale.optionArgumentType().parse(new StringReader("129"))
		);
	}

	@Test
	void sanitizeSnapsToSupportedAtlasSizes() {
		assertEquals(64, SettlementVillagerTextureScale.sanitize(64));
		assertEquals(64, SettlementVillagerTextureScale.sanitize(80));
		assertEquals(128, SettlementVillagerTextureScale.sanitize(128));
		assertEquals(128, SettlementVillagerTextureScale.sanitize(160));
		assertEquals(256, SettlementVillagerTextureScale.sanitize(256));
		assertEquals(256, SettlementVillagerTextureScale.sanitize(300));
		assertEquals(SettlementVillagerTextureScale.Option.PIXELS_64, SettlementVillagerTextureScale.optionForScale(64));
		assertEquals(SettlementVillagerTextureScale.Option.PIXELS_128, SettlementVillagerTextureScale.optionForScale(128));
		assertEquals(SettlementVillagerTextureScale.Option.PIXELS_256, SettlementVillagerTextureScale.optionForScale(256));
	}

	@Test
	void remapLeavesVanillaScaleUnchangedAndRewritesHdSheetsIntoTheModNamespace() {
		Identifier farmer = Identifier.fromNamespaceAndPath("minecraft", "textures/entity/villager/profession/farmer.png");
		Identifier forester = Identifier.fromNamespaceAndPath("live-villages", "textures/entity/villager/profession/forester_tier3.png");
		Identifier pillager = Identifier.fromNamespaceAndPath("minecraft", "textures/entity/illager/pillager.png");
		Identifier torch = Identifier.fromNamespaceAndPath("minecraft", "textures/block/torch.png");

		assertEquals(farmer, SettlementVillagerTextureScale.remap(farmer, 64));
		assertEquals(
			Identifier.fromNamespaceAndPath("live-villages", "textures/entity/scale128/villager/profession/farmer.png"),
			SettlementVillagerTextureScale.remap(farmer, 128)
		);
		assertEquals(
			Identifier.fromNamespaceAndPath("live-villages", "textures/entity/scale256/villager/profession/forester_tier3.png"),
			SettlementVillagerTextureScale.remap(forester, 256)
		);
		assertEquals(
			Identifier.fromNamespaceAndPath("live-villages", "textures/entity/scale128/illager/pillager.png"),
			SettlementVillagerTextureScale.remap(pillager, 128)
		);
		assertEquals(torch, SettlementVillagerTextureScale.remap(torch, 256));
	}
}
