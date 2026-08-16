package com.ronhelwig.livevillages.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.entity.ZombieVillagerRenderer;
import net.minecraft.client.renderer.entity.state.ZombieVillagerRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;

import com.ronhelwig.livevillages.client.render.CivicTierRenderState;
import com.ronhelwig.livevillages.client.render.VillagerTextureScaleClient;
import com.ronhelwig.livevillages.sim.CivicTierHolder;
import com.ronhelwig.livevillages.sim.SettlementTiers;

@Mixin(ZombieVillagerRenderer.class)
public abstract class ZombieVillagerRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/monster/zombie/ZombieVillager;Lnet/minecraft/client/renderer/entity/state/ZombieVillagerRenderState;F)V",
		at = @At("TAIL")
	)
	private void livevillages$extractCivicTier(
		ZombieVillager villager,
		ZombieVillagerRenderState state,
		float partialTick,
		CallbackInfo ci
	) {
		int tier = villager instanceof CivicTierHolder holder
			? holder.livevillages$getCivicTier()
			: SettlementTiers.MIN_TIER;
		((CivicTierRenderState) state).livevillages$setCivicTier(tier);
	}

	@Inject(
		method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/ZombieVillagerRenderState;)Lnet/minecraft/resources/Identifier;",
		at = @At("RETURN"),
		cancellable = true
	)
	private void livevillages$scaleBaseTexture(ZombieVillagerRenderState state, CallbackInfoReturnable<Identifier> cir) {
		int tier = state instanceof CivicTierRenderState civicTier
			? civicTier.livevillages$getCivicTier()
			: SettlementTiers.MIN_TIER;
		Identifier remapped = VillagerTextureScaleClient.resolve(cir.getReturnValue(), tier);
		if (remapped != null) {
			cir.setReturnValue(remapped);
		}
	}
}
