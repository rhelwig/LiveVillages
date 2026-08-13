package com.ronhelwig.livevillages.sim;

import java.util.Optional;
import java.util.function.Predicate;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * Civic-tier profession overlays. Texture files stay at
 * {@code textures/entity/villager/profession/<role>.png} for tier 1, with
 * optional {@code <role>_tier2.png} through {@code <role>_tier4.png}.
 */
public final class SettlementProfessionUniforms {
	public static final String NBT_CIVIC_TIER = "LiveVillagesCivicTier";
	private static final int SYNC_INTERVAL_TICKS = 40;

	private SettlementProfessionUniforms() {
	}

	public static int civicTierFor(ServerLevel level, Entity entity) {
		if (level == null || entity == null) {
			return SettlementTiers.MIN_TIER;
		}

		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(level.getServer());
		Optional<SettlementState> assigned = savedData.villagerSettlement(entity.getUUID())
			.flatMap(savedData::getSettlement)
			.filter(settlement -> settlement.dimension().equals(level.dimension()));

		if (assigned.isPresent()) {
			return SettlementTiers.normalize(assigned.get().tier());
		}

		return savedData.findSettlementForPosition(
				level.dimension(),
				entity.blockPosition(),
				SettlementVillagers::usesActualVillagers
			)
			.map(settlement -> SettlementTiers.normalize(settlement.tier()))
			.orElse(SettlementTiers.MIN_TIER);
	}

	public static boolean shouldRefreshCivicTier(int tickCount) {
		return tickCount > 0 && tickCount % SYNC_INTERVAL_TICKS == 0;
	}

	public static Identifier overlayTexture(Identifier baseTexture, int tier) {
		int normalized = SettlementTiers.normalize(tier);
		if (baseTexture == null || normalized <= SettlementTiers.MIN_TIER) {
			return baseTexture;
		}

		String path = baseTexture.getPath();
		if (!path.endsWith(".png") || path.contains("_tier")) {
			return baseTexture;
		}

		return Identifier.fromNamespaceAndPath(
			baseTexture.getNamespace(),
			path.substring(0, path.length() - 4) + "_tier" + normalized + ".png"
		);
	}

	public static Identifier resolveOverlay(Identifier baseTexture, int tier, Predicate<Identifier> exists) {
		if (baseTexture == null) {
			return null;
		}

		for (int candidateTier = SettlementTiers.normalize(tier); candidateTier >= 2; candidateTier--) {
			Identifier candidate = overlayTexture(baseTexture, candidateTier);
			if (candidate != null && exists.test(candidate)) {
				return candidate;
			}
		}

		return baseTexture;
	}
}
