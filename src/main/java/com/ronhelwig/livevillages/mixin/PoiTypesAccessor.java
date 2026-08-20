package com.ronhelwig.livevillages.mixin;

import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(PoiTypes.class)
public interface PoiTypesAccessor {
	@Invoker("registerBlockStates")
	static void livevillages$registerBlockStates(Holder<PoiType> type, Set<BlockState> states) {
		throw new AssertionError();
	}
}
