package com.ronhelwig.livevillages.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.entity.EvokerRenderer;
import net.minecraft.client.renderer.entity.state.EvokerRenderState;
import net.minecraft.resources.Identifier;

import com.ronhelwig.livevillages.client.render.CivicTierRenderState;
import com.ronhelwig.livevillages.client.render.VillagerTextureScaleClient;
import com.ronhelwig.livevillages.sim.SettlementTiers;

@Mixin(EvokerRenderer.class)
public abstract class EvokerRendererMixin {
	@Inject(
		method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/EvokerRenderState;)Lnet/minecraft/resources/Identifier;",
		at = @At("RETURN"),
		cancellable = true
	)
	private void livevillages$scaleTexture(EvokerRenderState state, CallbackInfoReturnable<Identifier> cir) {
		int tier = state instanceof CivicTierRenderState civicTier
			? civicTier.livevillages$getCivicTier()
			: SettlementTiers.MIN_TIER;
		Identifier remapped = VillagerTextureScaleClient.resolve(cir.getReturnValue(), tier);
		if (remapped != null) {
			cir.setReturnValue(remapped);
		}
	}
}
