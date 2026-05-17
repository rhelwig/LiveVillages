package com.ronhelwig.livevillages.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import com.ronhelwig.livevillages.content.LiveVillagesBlockEntities;

public class BakersCounterBlockEntity extends SaleDisplayBlockEntity {
	public BakersCounterBlockEntity(BlockPos pos, BlockState blockState) {
		super(LiveVillagesBlockEntities.BAKERS_COUNTER, pos, blockState);
	}
}
