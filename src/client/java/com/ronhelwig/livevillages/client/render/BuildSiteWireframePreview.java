package com.ronhelwig.livevillages.client.render;

import java.util.Optional;
import java.util.Set;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import com.ronhelwig.livevillages.client.LiveVillagesClientKeys;
import com.ronhelwig.livevillages.content.LiveVillagesBlocks;
import com.ronhelwig.livevillages.network.BuildSitePreviewBlockView;
import com.ronhelwig.livevillages.network.BuildSitePreviewRequestPayload;
import com.ronhelwig.livevillages.network.BuildSitePreviewSnapshot;
import com.ronhelwig.livevillages.network.BuildSitePreviewStatePayload;
import com.ronhelwig.livevillages.sim.SettlementConstruction;

public final class BuildSiteWireframePreview {
	private static final KeyMapping TOGGLE_KEY = KeyMappingHelper.registerKeyMapping(
		new KeyMapping(
			"key.live-villages.toggle_build_site_preview",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_O,
			LiveVillagesClientKeys.CONTROLS
		)
	);
	private static final int REFRESH_INTERVAL_TICKS = 10;
	private static final int RED_WIREFRAME_COLOR = 0xFFFF2222;
	private static final int AMBER_WIREFRAME_COLOR = 0xFFFFB347;
	private static final int PLACEMENT_WIREFRAME_COLOR = 0xFF66FF66;
	private static final int EXCAVATION_WIREFRAME_COLOR = 0xFF66CCFF;
	private static final int BLOCKER_WIREFRAME_COLOR = 0xFFFF55FF;
	private static final double WIREFRAME_THICKNESS_OFFSET = 0.012D;
	private static final VoxelShape BLOCK_SHAPE = Shapes.block();

	private static boolean visible;
	private static boolean waitingForSnapshot;
	private static long nextRefreshTick;
	private static BuildSitePreviewSnapshot latestSnapshot;
	private static String lastStatusMessage = "";

	private BuildSiteWireframePreview() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(BuildSiteWireframePreview::tick);
		ClientPlayNetworking.registerGlobalReceiver(BuildSitePreviewStatePayload.TYPE, (payload, context) -> {
			latestSnapshot = payload.snapshot();
			if (!latestSnapshot.statusMessage().equals(lastStatusMessage) && !latestSnapshot.statusMessage().isBlank()) {
				showActionbar(Minecraft.getInstance(), latestSnapshot.statusMessage());
			}
			lastStatusMessage = latestSnapshot.statusMessage();
			waitingForSnapshot = false;
		});
		LevelRenderEvents.END_MAIN.register(BuildSiteWireframePreview::render);
	}

	private static void tick(Minecraft client) {
		while (TOGGLE_KEY.consumeClick()) {
			visible = !visible;
			latestSnapshot = null;
			waitingForSnapshot = false;
			nextRefreshTick = 0L;
			lastStatusMessage = "";

			if (visible) {
				showActionbar(client, "Build preview on");
				requestSnapshot(client);
			} else {
				sendInactivePreview(client);
				showActionbar(client, "Build preview off");
			}
		}

		if (client.player == null || client.level == null) {
			latestSnapshot = null;
			waitingForSnapshot = false;
			nextRefreshTick = 0L;
			lastStatusMessage = "";
			return;
		}

		if (!visible) {
			return;
		}

		if (waitingForSnapshot) {
			if (client.level.getGameTime() < nextRefreshTick + REFRESH_INTERVAL_TICKS) {
				return;
			}

			waitingForSnapshot = false;
		}

		if (client.level.getGameTime() >= nextRefreshTick) {
			requestSnapshot(client);
		}
	}

	private static void requestSnapshot(Minecraft client) {
		if (client.player == null || client.level == null) {
			return;
		}

		if (!ClientPlayNetworking.canSend(BuildSitePreviewRequestPayload.TYPE)) {
			latestSnapshot = BuildSitePreviewSnapshot.unavailable("Server does not support the build preview.");
			waitingForSnapshot = false;
			nextRefreshTick = client.level.getGameTime() + 100L;
			return;
		}

		ClientPlayNetworking.send(new BuildSitePreviewRequestPayload(true, targetedBlockPos(client)));
		waitingForSnapshot = true;
		nextRefreshTick = client.level.getGameTime() + REFRESH_INTERVAL_TICKS;
	}

	private static void sendInactivePreview(Minecraft client) {
		if (client.player != null && ClientPlayNetworking.canSend(BuildSitePreviewRequestPayload.TYPE)) {
			ClientPlayNetworking.send(new BuildSitePreviewRequestPayload(false, Optional.empty()));
		}
	}

	private static Optional<BlockPos> targetedBlockPos(Minecraft client) {
		if (client.hitResult instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK) {
			BlockPos clickedPos = blockHit.getBlockPos();
			if (isPreviewAnchorBlock(client.level.getBlockState(clickedPos))) {
				return Optional.of(clickedPos);
			}

			return Optional.of(
				SettlementConstruction.isBuildSiteReplaceable(client.level.getBlockState(clickedPos))
					? clickedPos
					: clickedPos.relative(blockHit.getDirection())
			);
		}

		return Optional.empty();
	}

	private static boolean isPreviewAnchorBlock(BlockState state) {
		return state.is(LiveVillagesBlocks.PORTMASTER_ANCHOR)
			|| state.is(LiveVillagesBlocks.BAKERS_COUNTER)
			|| state.is(LiveVillagesBlocks.CARPENTER_BENCH)
			|| state.is(LiveVillagesBlocks.MINER_WORKSTATION)
			|| state.is(LiveVillagesBlocks.SURVEYOR_TABLE)
			|| state.is(LiveVillagesBlocks.SCRIBE_DESK)
			|| state.is(LiveVillagesBlocks.GARDENER_WORKSTATION)
			|| state.is(LiveVillagesBlocks.HONEY_SEPARATOR)
			|| state.is(LiveVillagesBlocks.GUARD_POST)
			|| state.is(LiveVillagesBlocks.FORESTER_TABLE)
			|| state.is(LiveVillagesBlocks.TRADE_BOARD)
			|| state.is(LiveVillagesBlocks.LIGHTHOUSE)
			|| state.is(LiveVillagesBlocks.PALISADE_GATEHOUSE)
			|| state.is(LiveVillagesBlocks.COPPER_PALISADE_GATEHOUSE)
			|| state.is(Blocks.CARTOGRAPHY_TABLE)
			|| state.is(Blocks.SMOKER)
			|| state.is(Blocks.STONECUTTER)
			|| state.is(Blocks.FLETCHING_TABLE);
	}

	private static void render(LevelRenderContext context) {
		if (!visible || latestSnapshot == null || !latestSnapshot.available()) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) {
			return;
		}

		Camera camera = client.gameRenderer.getMainCamera();
		Vec3 cameraPos = camera.position();
		VertexConsumer buffer = context.bufferSource().getBuffer(RenderTypes.lines());
		Set<BlockPos> blockerPositions = Set.copyOf(latestSnapshot.blockerPositions());

		for (var block : latestSnapshot.blocks()) {
			BlockPos blockPos = block.pos();
			boolean highlightedBlocker = blockerPositions.contains(blockPos);
			int color = block.isExcavation()
				? EXCAVATION_WIREFRAME_COLOR
				: highlightedBlocker
				? BLOCKER_WIREFRAME_COLOR
				: latestSnapshot.prospective()
				? (latestSnapshot.placementValid() ? PLACEMENT_WIREFRAME_COLOR : RED_WIREFRAME_COLOR)
				: previewColorForHeldItems(block, client);
			renderThickShape(
				context.poseStack(),
				buffer,
				blockPos.getX() - cameraPos.x,
				blockPos.getY() - cameraPos.y,
				blockPos.getZ() - cameraPos.z,
				color
			);
		}

		for (BlockPos blockerPos : blockerPositions) {
			if (latestSnapshot.blocks().stream().anyMatch(block -> block.pos().equals(blockerPos))) {
				continue;
			}

			renderThickShape(
				context.poseStack(),
				buffer,
				blockerPos.getX() - cameraPos.x,
				blockerPos.getY() - cameraPos.y,
				blockerPos.getZ() - cameraPos.z,
				BLOCKER_WIREFRAME_COLOR
			);
		}

		context.bufferSource().endBatch(RenderTypes.lines());
	}

	private static void renderThickShape(
		com.mojang.blaze3d.vertex.PoseStack poseStack,
		VertexConsumer buffer,
		double x,
		double y,
		double z,
		int color
	) {
		renderShape(poseStack, buffer, x, y, z, color);
		renderShape(poseStack, buffer, x + WIREFRAME_THICKNESS_OFFSET, y, z, color);
		renderShape(poseStack, buffer, x - WIREFRAME_THICKNESS_OFFSET, y, z, color);
		renderShape(poseStack, buffer, x, y + WIREFRAME_THICKNESS_OFFSET, z, color);
		renderShape(poseStack, buffer, x, y - WIREFRAME_THICKNESS_OFFSET, z, color);
	}

	private static void renderShape(
		com.mojang.blaze3d.vertex.PoseStack poseStack,
		VertexConsumer buffer,
		double x,
		double y,
		double z,
		int color
	) {
		ShapeRenderer.renderShape(poseStack, buffer, BLOCK_SHAPE, x, y, z, color, 1.0F);
	}

	private static int previewColorForHeldItems(BuildSitePreviewBlockView block, Minecraft client) {
		BuildSitePreviewBlockView.ItemMatch mainHandMatch = block.itemMatch(client.player.getMainHandItem());
		BuildSitePreviewBlockView.ItemMatch offHandMatch = block.itemMatch(client.player.getOffhandItem());
		BuildSitePreviewBlockView.ItemMatch bestMatch = mainHandMatch.ordinal() >= offHandMatch.ordinal() ? mainHandMatch : offHandMatch;

		return switch (bestMatch) {
			case EXACT -> PLACEMENT_WIREFRAME_COLOR;
			case COMPATIBLE_MATERIAL -> AMBER_WIREFRAME_COLOR;
			case NONE -> RED_WIREFRAME_COLOR;
		};
	}

	private static void showActionbar(Minecraft client, String message) {
		if (client.player != null) {
			client.player.sendOverlayMessage(Component.literal(message));
		}
	}
}
