package com.ronhelwig.livevillages.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.raid.Raider;

import com.ronhelwig.livevillages.sim.OutpostTrust;
import com.ronhelwig.livevillages.sim.SettlementChurchSanctuary;

@Mixin(Mob.class)
public abstract class MobTargetMixin {
	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void livevillages$respectOutpostTrust(LivingEntity target, CallbackInfo ci) {
		Mob self = (Mob) (Object) this;
		if (self instanceof Raider raider
			&& OutpostTrust.shouldSuppressTarget(raider, target)) {
			ci.cancel();
			return;
		}

		if (target != null && SettlementChurchSanctuary.shouldSuppressMobTarget(self, target)) {
			ci.cancel();
		}
	}

	@Inject(method = "aiStep", at = @At("TAIL"))
	private void livevillages$clearSanctuaryTargets(CallbackInfo ci) {
		Mob self = (Mob) (Object) this;
		LivingEntity target = self.getTarget();
		if (target != null && SettlementChurchSanctuary.shouldSuppressMobTarget(self, target)) {
			self.setTarget(null);
		}
	}
}
