package com.ronhelwig.livevillages.client.render;

import java.util.ArrayList;
import java.util.EnumMap;
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
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;

import com.ronhelwig.livevillages.block.entity.MilepostBlockEntity;

public class MilepostBlockEntityRenderer implements BlockEntityRenderer<MilepostBlockEntity, MilepostBlockEntityRenderer.RenderState> {
	private static final int MAX_VERTICAL_ROWS = 13;
	private static final int TEXT_COLOR = 0xFFE7E1D2;
	private static final float TEXT_SCALE = 0.020F;
	private static final float TEXT_CENTER_Y = 1.56F;
	private static final float TEXT_FACE_OFFSET = 0.314F;

	private final Font font;

	public MilepostBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
		font = context.font();
	}

	@Override
	public RenderState createRenderState() {
		return new RenderState();
	}

	@Override
	public void extractRenderState(
		MilepostBlockEntity blockEntity,
		RenderState renderState,
		float partialTick,
		Vec3 cameraPosition,
		ModelFeatureRenderer.CrumblingOverlay breakingOverlay
	) {
		BlockEntityRenderState.extractBase(blockEntity, renderState, breakingOverlay);

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			renderState.labels.put(direction, layoutVerticalLabel(blockEntity.labelFor(direction)));
		}
	}

	@Override
	public void submit(RenderState renderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			FormattedCharSequence[] label = renderState.labels.get(direction);

			if (label == null || label.length == 0) {
				continue;
			}

			submitFace(poseStack, submitNodeCollector, renderState, direction, label);
		}
	}

	private void submitFace(
		PoseStack poseStack,
		OrderedSubmitNodeCollector submitNodeCollector,
		RenderState renderState,
		Direction direction,
		FormattedCharSequence[] label
	) {
		Direction renderDirection = direction.getOpposite();
		poseStack.pushPose();
		poseStack.translate(
			0.5F + renderDirection.getStepX() * TEXT_FACE_OFFSET,
			TEXT_CENTER_Y,
			0.5F + renderDirection.getStepZ() * TEXT_FACE_OFFSET
		);
		poseStack.mulPose(Axis.YP.rotationDegrees(faceRotation(renderDirection)));

		poseStack.scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);
		float totalHeight = label.length * font.lineHeight;
		float startY = -totalHeight / 2.0F;

		for (int index = 0; index < label.length; index++) {
			FormattedCharSequence line = label[index];
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
				TEXT_COLOR,
				0,
				0
			);
		}

		poseStack.popPose();
	}

	private static float faceRotation(Direction direction) {
		return switch (direction) {
			case SOUTH -> 0.0F;
			case WEST -> 90.0F;
			case NORTH -> 180.0F;
			case EAST -> 270.0F;
			default -> 0.0F;
		};
	}

	private FormattedCharSequence[] layoutVerticalLabel(String destinationName) {
		if (destinationName == null || destinationName.isBlank()) {
			return new FormattedCharSequence[0];
		}

		List<String> rows = new ArrayList<>();
		rows.addAll(verticalWord("To"));
		rows.add("");

		for (String part : normalizeDisplayName(destinationName).split(" ")) {
			if (part.isBlank()) {
				continue;
			}

			if (!rows.isEmpty() && !rows.get(rows.size() - 1).isEmpty()) {
				rows.add("");
			}

			rows.addAll(verticalWord(part));
		}

		if (rows.size() > MAX_VERTICAL_ROWS) {
			rows = new ArrayList<>(rows.subList(0, MAX_VERTICAL_ROWS));
			for (int index = Math.max(0, rows.size() - 3); index < rows.size(); index++) {
				rows.set(index, ".");
			}
		}

		while (!rows.isEmpty() && rows.get(rows.size() - 1).isEmpty()) {
			rows.remove(rows.size() - 1);
		}

		return rows.stream()
			.map(row -> Component.literal(row).getVisualOrderText())
			.toArray(FormattedCharSequence[]::new);
	}

	private String normalizeDisplayName(String destinationName) {
		return destinationName.trim().replaceAll("\\s+", " ");
	}

	private static List<String> verticalWord(String word) {
		List<String> rows = new ArrayList<>(word.length());

		for (int i = 0; i < word.length(); i++) {
			rows.add(String.valueOf(word.charAt(i)));
		}

		return rows;
	}

	public static final class RenderState extends BlockEntityRenderState {
		private final EnumMap<Direction, FormattedCharSequence[]> labels = new EnumMap<>(Direction.class);
	}
}
