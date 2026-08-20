package com.ronhelwig.livevillages.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BellRenderer;
import net.minecraft.client.renderer.blockentity.state.BellRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import com.mojang.blaze3d.vertex.PoseStack;

import com.ronhelwig.livevillages.LiveVillages;
import com.ronhelwig.livevillages.client.render.CopperBellRenderState;
import com.ronhelwig.livevillages.content.LiveVillagesBlocks;

@Mixin(BellRenderer.class)
public abstract class BellRendererMixin {
	@Unique
	private static final SpriteId COPPER_BELL_TEXTURE = Sheets.BLOCK_ENTITIES_MAPPER.apply(LiveVillages.id("bell/copper_bell_body"));

	@Unique
	private static final ThreadLocal<Boolean> COPPER_SUBMIT = ThreadLocal.withInitial(() -> false);

	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/level/block/entity/BellBlockEntity;Lnet/minecraft/client/renderer/blockentity/state/BellRenderState;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
		at = @At("TAIL")
	)
	private void livevillages$markCopperBell(
		BellBlockEntity blockEntity,
		BellRenderState state,
		float partialTick,
		Vec3 cameraPos,
		ModelFeatureRenderer.CrumblingOverlay crumblingOverlay,
		CallbackInfo ci
	) {
		if (state instanceof CopperBellRenderState copperState) {
			copperState.livevillages$setCopperBell(blockEntity.getBlockState().is(LiveVillagesBlocks.COPPER_BELL));
		}
	}

	@Inject(
		method = "submit(Lnet/minecraft/client/renderer/blockentity/state/BellRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At("HEAD")
	)
	private void livevillages$beginCopperSubmit(
		BellRenderState state,
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		CameraRenderState cameraRenderState,
		CallbackInfo ci
	) {
		COPPER_SUBMIT.set(state instanceof CopperBellRenderState copperState && copperState.livevillages$isCopperBell());
	}

	@Redirect(
		method = "submit(Lnet/minecraft/client/renderer/blockentity/state/BellRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(
			value = "FIELD",
			target = "Lnet/minecraft/client/renderer/blockentity/BellRenderer;BELL_TEXTURE:Lnet/minecraft/client/resources/model/sprite/SpriteId;"
		)
	)
	private SpriteId livevillages$copperBellTexture() {
		return Boolean.TRUE.equals(COPPER_SUBMIT.get()) ? COPPER_BELL_TEXTURE : BellRenderer.BELL_TEXTURE;
	}
}
