package com.ronhelwig.livevillages.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;

import com.ronhelwig.livevillages.sim.SettlementDefenseWork;

@Mixin(BellBlock.class)
public abstract class BellBlockMixin {
	@Inject(
		method = "attemptToRing(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z",
		at = @At("RETURN")
	)
	private void livevillages$handleBellRing(Level level, BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && level instanceof ServerLevel serverLevel) {
			SettlementDefenseWork.onBellRung(serverLevel, pos);
		}
	}

	@Inject(
		method = "attemptToRing(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z",
		at = @At("RETURN")
	)
	private void livevillages$handleBellRing(Entity entity, Level level, BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && level instanceof ServerLevel serverLevel) {
			SettlementDefenseWork.onBellRung(serverLevel, pos);
		}
	}
}
