package com.ronhelwig.livevillages.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import com.ronhelwig.livevillages.content.LiveVillagesBlocks;

@Mixin(BlockEntityType.class)
public abstract class BlockEntityTypeMixin {
	@Inject(method = "isValid", at = @At("HEAD"), cancellable = true)
	private void livevillages$allowCopperBell(BlockState state, CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this == BlockEntityType.BELL && state.is(LiveVillagesBlocks.COPPER_BELL)) {
			cir.setReturnValue(true);
		}
	}
}
