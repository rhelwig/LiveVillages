package com.ronhelwig.livevillages.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.raid.Raider;

import com.ronhelwig.livevillages.sim.OutpostTrust;

@Mixin(Mob.class)
public abstract class MobTargetMixin {
	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void livevillages$respectOutpostTrust(LivingEntity target, CallbackInfo ci) {
		if ((Object) this instanceof Raider raider
			&& OutpostTrust.shouldSuppressTarget(raider, target)) {
			ci.cancel();
		}
	}
}
