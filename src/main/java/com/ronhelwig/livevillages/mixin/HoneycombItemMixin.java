package com.ronhelwig.livevillages.mixin;

import java.util.function.Supplier;
import java.util.Optional;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.google.common.base.Suppliers;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.ronhelwig.livevillages.content.LiveVillagesBlocks;

@Mixin(HoneycombItem.class)
public abstract class HoneycombItemMixin {
	@Shadow
	@Final
	@Mutable
	private static Supplier<BiMap<Block, Block>> WAXABLES;

	@Shadow
	@Final
	@Mutable
	private static Supplier<BiMap<Block, Block>> WAX_OFF_BY_BLOCK;

	@Inject(method = "getWaxed", at = @At("HEAD"))
	private static void livevillages$installCopperStairWaxing(
		BlockState state,
		CallbackInfoReturnable<Optional<BlockState>> cir
	) {
		if (WAXABLES.get().containsKey(LiveVillagesBlocks.COPPER_STAIRS)) {
			return;
		}
		BiMap<Block, Block> waxables = ImmutableBiMap.<Block, Block>builder()
			.putAll(WAXABLES.get())
			.put(LiveVillagesBlocks.COPPER_STAIRS, LiveVillagesBlocks.WAXED_COPPER_STAIRS)
			.build();
		WAXABLES = Suppliers.ofInstance(waxables);
		WAX_OFF_BY_BLOCK = Suppliers.ofInstance(waxables.inverse());
	}
}
