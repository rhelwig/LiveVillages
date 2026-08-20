package com.ronhelwig.livevillages.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import net.minecraft.client.renderer.blockentity.state.BellRenderState;

import com.ronhelwig.livevillages.client.render.CopperBellRenderState;

@Mixin(BellRenderState.class)
public abstract class BellRenderStateMixin implements CopperBellRenderState {
	@Unique
	private boolean livevillages$copperBell;

	@Override
	public boolean livevillages$isCopperBell() {
		return livevillages$copperBell;
	}

	@Override
	public void livevillages$setCopperBell(boolean copperBell) {
		livevillages$copperBell = copperBell;
	}
}
