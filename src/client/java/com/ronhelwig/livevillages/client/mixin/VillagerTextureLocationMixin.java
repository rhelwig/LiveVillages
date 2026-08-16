package com.ronhelwig.livevillages.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.entity.IllagerRenderer;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.world.entity.monster.illager.AbstractIllager;

import com.ronhelwig.livevillages.client.render.CivicTierRenderState;
import com.ronhelwig.livevillages.sim.CivicTierHolder;
import com.ronhelwig.livevillages.sim.SettlementTiers;

@Mixin(IllagerRenderer.class)
public abstract class VillagerTextureLocationMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/monster/illager/AbstractIllager;Lnet/minecraft/client/renderer/entity/state/IllagerRenderState;F)V",
		at = @At("TAIL")
	)
	private void livevillages$extractIllagerCivicTier(
		AbstractIllager illager,
		IllagerRenderState state,
		float partialTick,
		CallbackInfo ci
	) {
		int tier = illager instanceof CivicTierHolder holder
			? holder.livevillages$getCivicTier()
			: SettlementTiers.MIN_TIER;
		((CivicTierRenderState) state).livevillages$setCivicTier(tier);
	}
}
