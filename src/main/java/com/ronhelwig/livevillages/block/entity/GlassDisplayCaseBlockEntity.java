package com.ronhelwig.livevillages.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import com.ronhelwig.livevillages.content.LiveVillagesBlockEntities;

public class GlassDisplayCaseBlockEntity extends SaleDisplayBlockEntity {
	public GlassDisplayCaseBlockEntity(BlockPos pos, BlockState blockState) {
		super(LiveVillagesBlockEntities.GLASS_DISPLAY_CASE, pos, blockState, "block.live-villages.glass_display_case");
	}
}
