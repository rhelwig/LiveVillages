package com.ronhelwig.livevillages.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

import com.ronhelwig.livevillages.client.render.CivicTierRenderState;
import com.ronhelwig.livevillages.sim.SettlementTiers;

@Mixin(LivingEntityRenderState.class)
public abstract class LivingEntityRenderStateMixin implements CivicTierRenderState {
	@Unique
	private int liveVillagesCivicTier = SettlementTiers.MIN_TIER;

	@Override
	public int livevillages$getCivicTier() {
		return SettlementTiers.normalize(liveVillagesCivicTier);
	}

	@Override
	public void livevillages$setCivicTier(int tier) {
		liveVillagesCivicTier = SettlementTiers.normalize(tier);
	}
}
