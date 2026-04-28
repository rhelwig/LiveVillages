package com.ronhelwig.livevillages.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(FarmlandBlock.class)
public abstract class FarmlandBlockMixin {
	@Redirect(
		method = "fallOn",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/FarmlandBlock;turnToDirt(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"
		)
	)
	private void livevillages$preventVillagerTrample(Entity entity, BlockState state, Level level, BlockPos pos) {
		if (entity instanceof Villager) {
			return;
		}

		FarmlandBlock.turnToDirt(entity, state, level, pos);
	}
}
