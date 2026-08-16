package com.ronhelwig.livevillages.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.entity.VindicatorRenderer;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.resources.Identifier;

import com.ronhelwig.livevillages.client.render.CivicTierRenderState;
import com.ronhelwig.livevillages.client.render.VillagerTextureScaleClient;
import com.ronhelwig.livevillages.sim.SettlementTiers;

@Mixin(VindicatorRenderer.class)
public abstract class VindicatorRendererMixin {
	@Inject(
		method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/IllagerRenderState;)Lnet/minecraft/resources/Identifier;",
		at = @At("RETURN"),
		cancellable = true
	)
	private void livevillages$scaleTexture(IllagerRenderState state, CallbackInfoReturnable<Identifier> cir) {
		int tier = state instanceof CivicTierRenderState civicTier
			? civicTier.livevillages$getCivicTier()
			: SettlementTiers.MIN_TIER;
		Identifier remapped = VillagerTextureScaleClient.resolve(cir.getReturnValue(), tier);
		if (remapped != null) {
			cir.setReturnValue(remapped);
		}
	}
}
