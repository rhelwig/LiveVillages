package com.ronhelwig.livevillages.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.world.entity.npc.villager.Villager;

import com.ronhelwig.livevillages.client.render.CivicTierRenderState;
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
}
