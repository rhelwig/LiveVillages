package com.ronhelwig.livevillages.client.render;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.resources.Identifier;

import com.ronhelwig.livevillages.network.VillagerTextureScalePayload;
import com.ronhelwig.livevillages.sim.SettlementProfessionUniforms;
import com.ronhelwig.livevillages.sim.SettlementTiers;
import com.ronhelwig.livevillages.sim.SettlementVillagerTextureScale;

public final class VillagerTextureScaleClient {
	private static int currentScale = SettlementVillagerTextureScale.DEFAULT_SCALE;

	private VillagerTextureScaleClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(VillagerTextureScalePayload.TYPE, (payload, context) ->
			currentScale = SettlementVillagerTextureScale.sanitize(payload.scale())
		);
	}

	public static int currentScale() {
		return currentScale;
	}

	public static Identifier resolve(Identifier original) {
		return resolve(original, SettlementTiers.MIN_TIER);
	}

	public static Identifier resolve(Identifier original, int civicTier) {
		if (original == null) {
			return null;
		}

		Identifier tiered = SettlementProfessionUniforms.resolveOverlay(
			original,
			civicTier,
			ProfessionOverlayTextures::exists
		);
		Identifier scaled = SettlementVillagerTextureScale.remap(tiered, currentScale());
		if (scaled != null && !scaled.equals(tiered) && ProfessionOverlayTextures.exists(scaled)) {
			return scaled;
		}

		if (tiered != null && !tiered.equals(original) && ProfessionOverlayTextures.exists(tiered)) {
			return tiered;
		}

		Identifier scaledBase = SettlementVillagerTextureScale.remap(original, currentScale());
		if (scaledBase != null && !scaledBase.equals(original) && ProfessionOverlayTextures.exists(scaledBase)) {
			return scaledBase;
		}

		return original;
	}
}
