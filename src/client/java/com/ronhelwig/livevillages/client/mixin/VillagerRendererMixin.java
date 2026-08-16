package com.ronhelwig.livevillages.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.npc.villager.Villager;

import com.ronhelwig.livevillages.client.render.CivicTierRenderState;
import com.ronhelwig.livevillages.client.render.VillagerTextureScaleClient;
import com.ronhelwig.livevillages.sim.CivicTierHolder;
import com.ronhelwig.livevillages.sim.SettlementTiers;

@Mixin(VillagerRenderer.class)
public abstract class VillagerRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/npc/villager/Villager;Lnet/minecraft/client/renderer/entity/state/VillagerRenderState;F)V",
		at = @At("TAIL")
	)
	private void livevillages$extractCivicTier(Villager villager, VillagerRenderState state, float partialTick, CallbackInfo ci) {
		int tier = villager instanceof CivicTierHolder holder
			? holder.livevillages$getCivicTier()
			: SettlementTiers.MIN_TIER;
		((CivicTierRenderState) state).livevillages$setCivicTier(tier);
	}

	@Inject(
		method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/VillagerRenderState;)Lnet/minecraft/resources/Identifier;",
		at = @At("RETURN"),
		cancellable = true
	)
	private void livevillages$scaleBaseTexture(VillagerRenderState state, CallbackInfoReturnable<Identifier> cir) {
		int tier = state instanceof CivicTierRenderState civicTier
			? civicTier.livevillages$getCivicTier()
			: SettlementTiers.MIN_TIER;
		Identifier remapped = VillagerTextureScaleClient.resolve(cir.getReturnValue(), tier);
		if (remapped != null) {
			cir.setReturnValue(remapped);
		}
	}
}
