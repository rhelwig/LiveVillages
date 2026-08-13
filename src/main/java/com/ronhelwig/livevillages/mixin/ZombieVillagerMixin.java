package com.ronhelwig.livevillages.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import com.ronhelwig.livevillages.sim.CivicTierHolder;
import com.ronhelwig.livevillages.sim.SettlementProfessionUniforms;
import com.ronhelwig.livevillages.sim.SettlementTiers;

@Mixin(ZombieVillager.class)
public abstract class ZombieVillagerMixin implements CivicTierHolder {
	@Unique
	private static final EntityDataAccessor<Integer> LIVE_VILLAGES$CIVIC_TIER = SynchedEntityData.defineId(
		ZombieVillager.class,
		EntityDataSerializers.INT
	);

	@Inject(method = "defineSynchedData", at = @At("TAIL"))
	private void livevillages$defineCivicTier(SynchedEntityData.Builder builder, CallbackInfo ci) {
		builder.define(LIVE_VILLAGES$CIVIC_TIER, SettlementTiers.MIN_TIER);
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void livevillages$saveCivicTier(ValueOutput output, CallbackInfo ci) {
		output.putInt(SettlementProfessionUniforms.NBT_CIVIC_TIER, livevillages$getCivicTier());
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void livevillages$readCivicTier(ValueInput input, CallbackInfo ci) {
		livevillages$setCivicTier(input.getIntOr(SettlementProfessionUniforms.NBT_CIVIC_TIER, SettlementTiers.MIN_TIER));
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void livevillages$syncCivicTier(CallbackInfo ci) {
		ZombieVillager self = (ZombieVillager) (Object) this;
		if (!(self.level() instanceof ServerLevel level) || !SettlementProfessionUniforms.shouldRefreshCivicTier(self.tickCount)) {
			return;
		}

		int nextTier = SettlementProfessionUniforms.civicTierFor(level, self);
		if (nextTier != livevillages$getCivicTier()) {
			livevillages$setCivicTier(nextTier);
		}
	}

	@Override
	public int livevillages$getCivicTier() {
		return SettlementTiers.normalize(((ZombieVillager) (Object) this).getEntityData().get(LIVE_VILLAGES$CIVIC_TIER));
	}

	@Override
	public void livevillages$setCivicTier(int tier) {
		((ZombieVillager) (Object) this).getEntityData().set(LIVE_VILLAGES$CIVIC_TIER, SettlementTiers.normalize(tier));
	}
}
