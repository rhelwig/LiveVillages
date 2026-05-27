package com.ronhelwig.livevillages.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.raid.Raider;

import com.ronhelwig.livevillages.sim.OutpostTrust;

@Mixin(LookAtPlayerGoal.class)
public abstract class LookAtPlayerGoalMixin {
	@Shadow
	protected Mob mob;

	@Shadow
	protected Entity lookAt;

	@Inject(method = "canUse", at = @At("RETURN"), cancellable = true)
	private void livevillages$ignoreAcceptedPlayers(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValue() && shouldRelax()) {
			lookAt = null;
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "canContinueToUse", at = @At("RETURN"), cancellable = true)
	private void livevillages$stopWatchingAcceptedPlayers(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValue() && shouldRelax()) {
			lookAt = null;
			cir.setReturnValue(false);
		}
	}

	private boolean shouldRelax() {
		return mob instanceof Raider raider
			&& lookAt instanceof ServerPlayer player
			&& OutpostTrust.shouldShowAcceptedPlayerBodyLanguage(raider, player);
	}
}
