package com.ronhelwig.livevillages.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import com.ronhelwig.livevillages.sim.CivicTierHolder;
import com.ronhelwig.livevillages.sim.OutpostTrust;
import com.ronhelwig.livevillages.sim.SettlementProfessionUniforms;
import com.ronhelwig.livevillages.sim.SettlementTiers;

@Mixin(Pillager.class)
public abstract class PillagerMixin implements CivicTierHolder {
	@Unique
	private static final EntityDataAccessor<Integer> LIVE_VILLAGES$CIVIC_TIER = SynchedEntityData.defineId(
		Pillager.class,
		EntityDataSerializers.INT
	);

	@Inject(method = "getArmPose", at = @At("HEAD"), cancellable = true)
	private void livevillages$relaxAroundAcceptedPlayers(CallbackInfoReturnable<AbstractIllager.IllagerArmPose> cir) {
		if ((Object) this instanceof Raider raider && OutpostTrust.hasNearbyAcceptedPlayer(raider)) {
			cir.setReturnValue(AbstractIllager.IllagerArmPose.NEUTRAL);
		}
	}

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

	@Override
	public int livevillages$getCivicTier() {
		return SettlementTiers.normalize(((Pillager) (Object) this).getEntityData().get(LIVE_VILLAGES$CIVIC_TIER));
	}

	@Override
	public void livevillages$setCivicTier(int tier) {
		((Pillager) (Object) this).getEntityData().set(LIVE_VILLAGES$CIVIC_TIER, SettlementTiers.normalize(tier));
	}
}
