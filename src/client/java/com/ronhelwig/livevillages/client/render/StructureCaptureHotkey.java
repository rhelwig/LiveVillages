package com.ronhelwig.livevillages.client.render;

import java.util.Optional;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import com.ronhelwig.livevillages.client.LiveVillagesClientKeys;
import com.ronhelwig.livevillages.network.StructureCaptureRequestPayload;

public final class StructureCaptureHotkey {
	private static final KeyMapping CAPTURE_KEY = KeyMappingHelper.registerKeyMapping(
		new KeyMapping(
			"key.live-villages.capture_structure_blueprint",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_P,
			LiveVillagesClientKeys.CONTROLS
		)
	);

	private StructureCaptureHotkey() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(StructureCaptureHotkey::tick);
	}

	private static void tick(Minecraft client) {
		while (CAPTURE_KEY.consumeClick()) {
			requestCapture(client);
		}
	}

	private static void requestCapture(Minecraft client) {
		if (client.player == null || client.level == null) {
			return;
		}

		if (!ClientPlayNetworking.canSend(StructureCaptureRequestPayload.TYPE)) {
			client.player.sendOverlayMessage(Component.literal("Server does not support structure capture."));
			return;
		}

		ClientPlayNetworking.send(new StructureCaptureRequestPayload(targetedBlockPos(client)));
		client.player.sendOverlayMessage(Component.literal("Requested structure capture."));
	}

	private static Optional<net.minecraft.core.BlockPos> targetedBlockPos(Minecraft client) {
		if (client.hitResult instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK) {
			return Optional.of(blockHit.getBlockPos());
		}

		return Optional.empty();
	}
}
