package com.ronhelwig.livevillages.client.render;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import com.ronhelwig.livevillages.block.TradeBoardBlock;
import com.ronhelwig.livevillages.block.entity.TradeBoardBlockEntity;

public class TradeBoardBlockEntityRenderer implements BlockEntityRenderer<TradeBoardBlockEntity, TradeBoardBlockEntityRenderer.RenderState> {
	private static final String FALLBACK_LABEL = "Trade Board";
	private static final int MAX_TEXT_WIDTH = 48;
	private static final int MAX_LINES = 2;
	private static final float TEXT_SCALE = 0.014F;
	private static final float TEXT_CENTER_Y = 0.5625F;
	private static final float TEXT_FACE_OFFSET = 0.065F;

	private final Font font;

	public TradeBoardBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
		font = context.font();
	}

	@Override
	public RenderState createRenderState() {
		return new RenderState();
	}

	@Override
	public void extractRenderState(
		TradeBoardBlockEntity blockEntity,
		RenderState renderState,
		float partialTick,
		Vec3 cameraPosition,
		ModelFeatureRenderer.CrumblingOverlay breakingOverlay
	) {
		BlockEntityRenderState.extractBase(blockEntity, renderState, breakingOverlay);
		renderState.facing = blockEntity.getBlockState().getValue(TradeBoardBlock.FACING);
		renderState.lines = layoutLines(blockEntity.displaySettlementName());
		renderState.textColor = textColorForTier(blockEntity.displaySettlementTier());
	}

	@Override
	public void submit(RenderState renderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
		if (renderState.lines.length == 0) {
			return;
		}

		submitFace(renderState, poseStack, submitNodeCollector, false);
		submitFace(renderState, poseStack, submitNodeCollector, true);
	}

	private void submitFace(RenderState renderState, PoseStack poseStack, OrderedSubmitNodeCollector submitNodeCollector, boolean backFace) {
		poseStack.pushPose();
		poseStack.translate(0.5F, 0.5F, 0.5F);
		poseStack.mulPose(Axis.YP.rotationDegrees(modelRotation(renderState.facing)));

		if (backFace) {
			poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
		}

		poseStack.translate(0.0F, TEXT_CENTER_Y - 0.5F, TEXT_FACE_OFFSET);
		poseStack.scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);

		float totalHeight = renderState.lines.length * font.lineHeight;
		float startY = -totalHeight / 2.0F;

		for (int index = 0; index < renderState.lines.length; index++) {
			FormattedCharSequence line = renderState.lines[index];
			float x = -font.width(line) / 2.0F;
			float y = startY + index * font.lineHeight;
			submitNodeCollector.submitText(
				poseStack,
				x,
				y,
				line,
				false,
				Font.DisplayMode.POLYGON_OFFSET,
				renderState.lightCoords,
				renderState.textColor,
				0,
				0
			);
		}

		poseStack.popPose();
	}

	private float modelRotation(Direction facing) {
		return switch (facing) {
			case NORTH -> 0.0F;
			case EAST -> 90.0F;
			case SOUTH -> 180.0F;
			case WEST -> 270.0F;
			default -> 0.0F;
		};
	}

	private int textColorForTier(int tier) {
		return switch (tier) {
			case 2 -> 0xFF1F5F3B;
			case 3 -> 0xFF1D4F92;
			case 4 -> 0xFF5D3D8C;
			default -> 0xFF2A1C12;
		};
	}

	private FormattedCharSequence[] layoutLines(String settlementName) {
		String normalized = normalizeDisplayName(settlementName);

		if (font.width(normalized) <= MAX_TEXT_WIDTH) {
			return new FormattedCharSequence[] {Component.literal(normalized).getVisualOrderText()};
		}

		List<String> lines = new ArrayList<>(MAX_LINES);
		List<String> words = new ArrayList<>();

		for (String part : normalized.split(" ")) {
			if (!part.isBlank()) {
				words.add(part);
			}
		}

		if (words.isEmpty()) {
			return new FormattedCharSequence[] {Component.literal(FALLBACK_LABEL).getVisualOrderText()};
		}

		int wordIndex = 0;

		for (int lineIndex = 0; lineIndex < MAX_LINES && wordIndex < words.size(); lineIndex++) {
			StringBuilder builder = new StringBuilder();

			while (wordIndex < words.size()) {
				String candidate = builder.isEmpty()
					? words.get(wordIndex)
					: builder + " " + words.get(wordIndex);

				if (!builder.isEmpty() && font.width(candidate) > MAX_TEXT_WIDTH) {
					break;
				}

				builder.setLength(0);
				builder.append(candidate);
				wordIndex++;

				if (font.width(candidate) > MAX_TEXT_WIDTH) {
					break;
				}
			}

			String line = builder.isEmpty() ? truncateToWidth(words.get(wordIndex++), MAX_TEXT_WIDTH) : builder.toString();
			line = truncateToWidth(line, MAX_TEXT_WIDTH);
			lines.add(line);
		}

		if (wordIndex < words.size()) {
			int lastIndex = lines.size() - 1;
			lines.set(lastIndex, truncateToWidth(lines.get(lastIndex) + " ...", MAX_TEXT_WIDTH));
		}

		return lines.stream()
			.map(line -> Component.literal(line).getVisualOrderText())
			.toArray(FormattedCharSequence[]::new);
	}

	private String normalizeDisplayName(String settlementName) {
		if (settlementName == null || settlementName.isBlank()) {
			return FALLBACK_LABEL;
		}

		return settlementName.trim().replaceAll("\\s+", " ");
	}

	private String truncateToWidth(String text, int maxWidth) {
		if (font.width(text) <= maxWidth) {
			return text;
		}

		int ellipsisWidth = font.width("...");
		String truncated = font.plainSubstrByWidth(text, Math.max(0, maxWidth - ellipsisWidth));
		return truncated.stripTrailing() + "...";
	}

	public static final class RenderState extends BlockEntityRenderState {
		private Direction facing = Direction.NORTH;
		private FormattedCharSequence[] lines = new FormattedCharSequence[0];
		private int textColor = 0xFF2A1C12;
	}
}
