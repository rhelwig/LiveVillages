package com.ronhelwig.livevillages.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.illager.AbstractIllager;

import com.ronhelwig.livevillages.sim.OutpostTrust;

@Mixin(AbstractIllager.class)
public abstract class AbstractIllagerMixin {
	@Inject(method = "canAttack", at = @At("HEAD"), cancellable = true)
	private void livevillages$respectOutpostTrust(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
		if (OutpostTrust.shouldSuppressIllagerAttack((AbstractIllager) (Object) this, target)) {
			cir.setReturnValue(false);
		}
	}
}
