package com.ronhelwig.livevillages.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.raid.Raider;

import com.ronhelwig.livevillages.sim.OutpostTrust;

@Mixin(Pillager.class)
public abstract class PillagerMixin {
	@Inject(method = "getArmPose", at = @At("HEAD"), cancellable = true)
	private void livevillages$relaxAroundAcceptedPlayers(CallbackInfoReturnable<AbstractIllager.IllagerArmPose> cir) {
		if ((Object) this instanceof Raider raider && OutpostTrust.hasNearbyAcceptedPlayer(raider)) {
			cir.setReturnValue(AbstractIllager.IllagerArmPose.NEUTRAL);
		}
	}
}
