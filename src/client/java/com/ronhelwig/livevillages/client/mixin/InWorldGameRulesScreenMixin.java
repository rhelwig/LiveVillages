package com.ronhelwig.livevillages.client.mixin;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.mixin.gamerule.client.AbstractGameRulesScreenAccessor;
import net.minecraft.client.gui.screens.options.InWorldGameRulesScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.ronhelwig.livevillages.LiveVillagesGameRules;
import com.ronhelwig.livevillages.client.render.VillagerTextureScaleClient;
import com.ronhelwig.livevillages.network.VillagerTextureScaleUpdatePayload;
import com.ronhelwig.livevillages.sim.SettlementVillagerTextureScale;

@Mixin(InWorldGameRulesScreen.class)
public abstract class InWorldGameRulesScreenMixin {
	@Inject(method = "onGameRuleValuesUpdated", at = @At("HEAD"))
	private void liveVillages$initializeTextureScale(CallbackInfo callbackInfo) {
		var gameRules = ((AbstractGameRulesScreenAccessor) this).getGameRules();
		gameRules.set(
			LiveVillagesGameRules.VILLAGER_TEXTURE_SCALE,
			SettlementVillagerTextureScale.optionForScale(VillagerTextureScaleClient.currentScale()),
			null
		);
	}

	@Inject(method = "onDone", at = @At("HEAD"))
	private void liveVillages$applyTextureScale(CallbackInfo callbackInfo) {
		var gameRules = ((AbstractGameRulesScreenAccessor) this).getGameRules();
		ClientPlayNetworking.send(new VillagerTextureScaleUpdatePayload(
			gameRules.get(LiveVillagesGameRules.VILLAGER_TEXTURE_SCALE).pixels()
		));
	}
}
