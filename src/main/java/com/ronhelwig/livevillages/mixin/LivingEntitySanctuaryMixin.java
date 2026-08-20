package com.ronhelwig.livevillages.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import com.ronhelwig.livevillages.sim.SettlementChurchSanctuary;

@Mixin(LivingEntity.class)
public abstract class LivingEntitySanctuaryMixin {
	@Inject(
		method = "isInvulnerableTo(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;)Z",
		at = @At("RETURN"),
		cancellable = true
	)
	private void livevillages$protectChurchSanctuary(ServerLevel level, DamageSource source, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && SettlementChurchSanctuary.protectsFrom((LivingEntity) (Object) this, source)) {
			cir.setReturnValue(true);
		}
	}
}
