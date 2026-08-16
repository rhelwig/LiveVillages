package com.ronhelwig.livevillages.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.raid.Raider;

import com.ronhelwig.livevillages.sim.CivicTierHolder;
import com.ronhelwig.livevillages.sim.SettlementProfessionUniforms;

@Mixin(Raider.class)
public abstract class RaiderCivicTierMixin {
	@Inject(method = "aiStep", at = @At("TAIL"))
	private void livevillages$syncCivicTier(CallbackInfo ci) {
		Raider self = (Raider) (Object) this;
		if (!(self instanceof CivicTierHolder holder)
			|| !(self.level() instanceof ServerLevel level)
			|| !SettlementProfessionUniforms.shouldRefreshCivicTier(self.tickCount)) {
			return;
		}

		int nextTier = SettlementProfessionUniforms.civicTierFor(level, self);
		if (nextTier != holder.livevillages$getCivicTier()) {
			holder.livevillages$setCivicTier(nextTier);
		}
	}
}
