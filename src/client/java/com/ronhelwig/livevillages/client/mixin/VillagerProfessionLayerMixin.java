package com.ronhelwig.livevillages.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.VillagerProfessionLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

import com.ronhelwig.livevillages.client.render.CivicTierRenderState;
import com.ronhelwig.livevillages.client.render.ProfessionOverlayTextures;
import com.ronhelwig.livevillages.sim.SettlementProfessionUniforms;
import com.ronhelwig.livevillages.sim.SettlementTiers;

@Mixin(VillagerProfessionLayer.class)
public abstract class VillagerProfessionLayerMixin {
	@Unique
	private static final ThreadLocal<Integer> LIVE_VILLAGES$CIVIC_TIER = ThreadLocal.withInitial(() -> SettlementTiers.MIN_TIER);

	@Inject(method = "submit", at = @At("HEAD"))
	private void livevillages$captureCivicTier(
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		int light,
		LivingEntityRenderState state,
		float limbSwing,
		float limbSwingAmount,
		CallbackInfo ci
	) {
		int tier = state instanceof CivicTierRenderState civicTier
			? civicTier.livevillages$getCivicTier()
			: SettlementTiers.MIN_TIER;
		LIVE_VILLAGES$CIVIC_TIER.set(tier);
	}

	@Inject(method = "submit", at = @At("RETURN"))
	private void livevillages$clearCivicTier(
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		int light,
		LivingEntityRenderState state,
		float limbSwing,
		float limbSwingAmount,
		CallbackInfo ci
	) {
		LIVE_VILLAGES$CIVIC_TIER.remove();
	}

	@Inject(
		method = "getIdentifier(Ljava/lang/String;Lnet/minecraft/resources/Identifier;)Lnet/minecraft/resources/Identifier;",
		at = @At("RETURN"),
		cancellable = true
	)
	private void livevillages$useCivicTierOverlay(String folder, Identifier id, CallbackInfoReturnable<Identifier> cir) {
		if (!"profession".equals(folder) || cir.getReturnValue() == null) {
			return;
		}

		cir.setReturnValue(SettlementProfessionUniforms.resolveOverlay(
			cir.getReturnValue(),
			LIVE_VILLAGES$CIVIC_TIER.get(),
			ProfessionOverlayTextures::exists
		));
	}
}
