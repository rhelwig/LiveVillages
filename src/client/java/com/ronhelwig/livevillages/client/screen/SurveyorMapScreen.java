package com.ronhelwig.livevillages.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.ronhelwig.livevillages.network.SurveyorMapSnapshot;
import com.ronhelwig.livevillages.network.SurveyorMapStatePayload;

public class SurveyorMapScreen extends Screen {
	private static final int MAP_SIZE = 184;
	private static final int FOG_CELL_SIZE = 4;
	private static final int FOG_GRID_SIZE = MAP_SIZE / FOG_CELL_SIZE;
	private static final int LEGEND_WIDTH = 92;
	private static final int COMPASS_SIZE = 26;
	private static final int PADDING = 10;

	private final SurveyorMapSnapshot snapshot;
	private final boolean[][] revealedCells;
	private float playerYawDegrees = 180.0F;

	public SurveyorMapScreen(SurveyorMapSnapshot snapshot) {
		super(Component.literal("Village Survey Map"));
		this.snapshot = snapshot;
		this.revealedCells = buildRevealedCells(snapshot);
	}

	public static void registerNetworking() {
		ClientPlayNetworking.registerGlobalReceiver(SurveyorMapStatePayload.TYPE, (payload, context) -> {
			context.client().setScreen(new SurveyorMapScreen(payload.snapshot()));
		});
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		extractTransparentBackground(graphics);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		Minecraft minecraft = Minecraft.getInstance();
		Font font = minecraft.font;
		playerYawDegrees = minecraft.player == null ? 180.0F : minecraft.player.getYRot();
		int panelWidth = MAP_SIZE + LEGEND_WIDTH + PADDING * 3;
		int panelHeight = MAP_SIZE + PADDING * 2 + 18;
		int left = (width - panelWidth) / 2;
		int top = (height - panelHeight) / 2;
		int mapLeft = left + PADDING;
		int mapTop = top + PADDING + 14;
		int legendLeft = mapLeft + MAP_SIZE + PADDING;
		int compassLeft = legendLeft + LEGEND_WIDTH - COMPASS_SIZE;
		int compassTop = mapTop + 2;
		int legendTop = compassTop + COMPASS_SIZE + 10;

		graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xEE1F1A17);
		graphics.fill(left + 1, top + 1, left + panelWidth - 1, top + 18, 0xFF5F4E31);
		graphics.outline(left, top, panelWidth, panelHeight, 0xFFB69155);
		graphics.fill(mapLeft, mapTop, mapLeft + MAP_SIZE, mapTop + MAP_SIZE, 0xFF243022);
		graphics.outline(mapLeft, mapTop, MAP_SIZE, MAP_SIZE, 0xFF827456);

		graphics.text(font, snapshot.available() ? snapshot.settlementName() + " Survey" : "Survey unavailable", left + PADDING, top + 5, 0xFFF8E6BE, false);
		if (snapshot.available() && !snapshot.timeLabel().isBlank()) {
			String timeLabel = snapshot.timeLabel();
			graphics.text(font, timeLabel, left + panelWidth - PADDING - font.width(timeLabel), top + 5, 0xFFE8DDC8, false);
		}

		if (!snapshot.available()) {
			graphics.text(font, snapshot.statusMessage(), mapLeft, mapTop + 8, 0xFFE8DDC8, false);
			return;
		}

		for (var road : snapshot.roads()) {
			ScreenPoint point = mapPoint(mapLeft, mapTop, road.pos().getX(), road.pos().getZ());
			int x = point.x();
			int y = point.y();
			if (insideMap(mapLeft, mapTop, x, y)) {
				graphics.fill(x, y, x + 2, y + 2, roadColor(road.quality()));
			}
		}

		drawSurveyFog(graphics, mapLeft, mapTop);

		for (var pos : snapshot.roadwrightRoutes()) {
			ScreenPoint point = mapPoint(mapLeft, mapTop, pos.getX(), pos.getZ());
			int x = point.x();
			int y = point.y();
			if (insideMap(mapLeft, mapTop, x, y)) {
				graphics.fill(x, y, x + 1, y + 1, 0xFF5BD7FF);
			}
		}

		for (var pos : snapshot.plannedRoadwork()) {
			ScreenPoint point = mapPoint(mapLeft, mapTop, pos.getX(), pos.getZ());
			int x = point.x();
			int y = point.y();
			if (insideMap(mapLeft, mapTop, x, y)) {
				graphics.fill(x, y, x + 2, y + 2, 0xFFFF66DD);
			}
		}

		for (var point : snapshot.points()) {
			ScreenPoint screenPoint = mapPoint(mapLeft, mapTop, point.pos().getX(), point.pos().getZ());
			if (insideMap(mapLeft, mapTop, screenPoint.x(), screenPoint.y())) {
				graphics.fill(screenPoint.x() - 1, screenPoint.y() - 1, screenPoint.x() + 2, screenPoint.y() + 2, pointColor(point.kind()));
			}
		}

		drawOffMapPointIndicators(graphics, mapLeft, mapTop);

		for (var pos : snapshot.roadwrights()) {
			ScreenPoint point = mapPoint(mapLeft, mapTop, pos.getX(), pos.getZ());
			int x = point.x();
			int y = point.y();
			if (insideMap(mapLeft, mapTop, x, y)) {
				graphics.fill(x - 2, y - 2, x + 3, y + 3, 0xFF4EEBFF);
			}
		}

		drawBoundary(graphics, mapLeft, mapTop);
		drawCompassRose(graphics, font, compassLeft, compassTop);
		drawLegend(graphics, font, legendLeft, legendTop);
		drawHoveredLabel(graphics, font, mapLeft, mapTop, mouseX, mouseY);
	}

	private void drawCompassRose(GuiGraphicsExtractor graphics, Font font, int left, int top) {
		int centerX = left + COMPASS_SIZE / 2;
		int centerY = top + COMPASS_SIZE / 2;

		graphics.fill(left, top, left + COMPASS_SIZE, top + COMPASS_SIZE, 0xCC17120F);
		graphics.outline(left, top, COMPASS_SIZE, COMPASS_SIZE, 0xFF9B8151);
		graphics.fill(centerX, centerY, centerX + 1, centerY + 1, 0xFFF2CF84);

		drawCompassDirection(graphics, font, centerX, centerY, 0, -1, "N", 0xFFF8E6BE, true);
		drawCompassDirection(graphics, font, centerX, centerY, 1, 0, "E", 0xFFAE9B7B, false);
		drawCompassDirection(graphics, font, centerX, centerY, 0, 1, "S", 0xFFAE9B7B, false);
		drawCompassDirection(graphics, font, centerX, centerY, -1, 0, "W", 0xFFAE9B7B, false);
	}

	private void drawCompassDirection(
		GuiGraphicsExtractor graphics,
		Font font,
		int centerX,
		int centerY,
		int worldDx,
		int worldDz,
		String label,
		int color,
		boolean highlight
	) {
		double[] transformed = rotatedDelta(worldDx, worldDz);
		int armX = centerX + (int) Math.round(transformed[0] * 7.0D);
		int armY = centerY + (int) Math.round(transformed[1] * 7.0D);

		for (int step = 1; step <= 5; step++) {
			int x = centerX + (int) Math.round(transformed[0] * step);
			int y = centerY + (int) Math.round(transformed[1] * step);
			graphics.fill(x, y, x + 1, y + 1, highlight ? 0xFFF2CF84 : 0xFF7C6743);
		}

		graphics.text(font, label, armX - font.width(label) / 2, armY - 4, color, false);
	}

	private void drawSurveyFog(GuiGraphicsExtractor graphics, int mapLeft, int mapTop) {
		for (int gridX = 0; gridX < FOG_GRID_SIZE; gridX++) {
			for (int gridY = 0; gridY < FOG_GRID_SIZE; gridY++) {
				int x = mapLeft + gridX * FOG_CELL_SIZE;
				int y = mapTop + gridY * FOG_CELL_SIZE;
				int[] worldPos = worldPosAtPixel(mapLeft, mapTop, x + 2, y + 2);
				int worldX = worldPos[0];
				int worldZ = worldPos[1];
				double distanceFromCenter = horizontalDistanceSquared(worldX, worldZ, snapshot.center().getX(), snapshot.center().getZ());

				if (distanceFromCenter > snapshot.boundaryRadius() * snapshot.boundaryRadius()) {
					graphics.fill(x, y, Math.min(x + FOG_CELL_SIZE, mapLeft + MAP_SIZE), Math.min(y + FOG_CELL_SIZE, mapTop + MAP_SIZE), 0xAA090907);
				} else if (!revealedCells[gridX][gridY]) {
					graphics.fill(x, y, Math.min(x + FOG_CELL_SIZE, mapLeft + MAP_SIZE), Math.min(y + FOG_CELL_SIZE, mapTop + MAP_SIZE), 0x88141813);
				}
			}
		}
	}

	private void drawBoundary(GuiGraphicsExtractor graphics, int mapLeft, int mapTop) {
		for (int degrees = 0; degrees < 360; degrees++) {
			double radians = Math.toRadians(degrees);
			int worldX = snapshot.center().getX() + (int) Math.round(Math.cos(radians) * snapshot.boundaryRadius());
			int worldZ = snapshot.center().getZ() + (int) Math.round(Math.sin(radians) * snapshot.boundaryRadius());
			ScreenPoint point = mapPoint(mapLeft, mapTop, worldX, worldZ);
			int x = point.x();
			int y = point.y();

			if (insideMap(mapLeft, mapTop, x, y)) {
				graphics.fill(x, y, x + 1, y + 1, 0xFFD6B15F);
			}
		}
	}

	private void drawLegend(GuiGraphicsExtractor graphics, Font font, int x, int y) {
		graphics.text(font, "Legend", x, y, 0xFFF2CF84, false);
		y += 12;
		y = legendRow(graphics, font, x, y, 0xFFB99758, "Trail");
		y = legendRow(graphics, font, x, y, 0xFF9C9C9C, "Gravel");
		y = legendRow(graphics, font, x, y, 0xFF7C7C7C, "Cobble");
		y = legendRow(graphics, font, x, y, 0xFFD2D2D2, "Finished");
		y += 4;
		y = legendRow(graphics, font, x, y, 0xFFFFDD66, "Center");
		y = legendRow(graphics, font, x, y, 0xFF66FF88, "Building");
		y = legendRow(graphics, font, x, y, 0xFFFFAA44, "Project");
		y = legendRow(graphics, font, x, y, 0xFFB6D0FF, "Milepost");
		y = legendRow(graphics, font, x, y, 0xFFFFFF66, "POI");
		y = legendRow(graphics, font, x, y, 0xFF4EEBFF, "Roadwright");
		y = legendRow(graphics, font, x, y, 0xFF5BD7FF, "Route trace");
		y = legendRow(graphics, font, x, y, 0xFFFF66DD, "Planned work");
		y = legendRow(graphics, font, x, y, 0xFFD6B15F, "Boundary");
		y += 8;
		graphics.text(font, "Esc closes", x, y, 0xFF9F8E72, false);
	}

	private int legendRow(GuiGraphicsExtractor graphics, Font font, int x, int y, int color, String label) {
		graphics.fill(x, y + 2, x + 8, y + 8, color);
		graphics.text(font, label, x + 12, y, 0xFFE8DDC8, false);
		return y + 10;
	}

	private void drawHoveredLabel(GuiGraphicsExtractor graphics, Font font, int mapLeft, int mapTop, int mouseX, int mouseY) {
		String label = "";

		for (var point : snapshot.points()) {
			ScreenPoint screenPoint = mapPoint(mapLeft, mapTop, point.pos().getX(), point.pos().getZ());
			if (Math.abs(mouseX - screenPoint.x()) <= 4 && Math.abs(mouseY - screenPoint.y()) <= 4) {
				label = point.label();
				break;
			}
		}

		if (label.isBlank()) {
			for (var pos : snapshot.roadwrights()) {
				ScreenPoint screenPoint = mapPoint(mapLeft, mapTop, pos.getX(), pos.getZ());
				if (Math.abs(mouseX - screenPoint.x()) <= 4 && Math.abs(mouseY - screenPoint.y()) <= 4) {
					label = "Roadwright";
					break;
				}
			}
		}

		if (label.isBlank()) {
			for (var point : snapshot.points()) {
				if (!"milepost".equals(point.kind())) {
					continue;
				}

				ClampedIndicator indicator = clampedIndicator(mapLeft, mapTop, point.pos().getX(), point.pos().getZ());

				if (indicator == null || !indicator.offMap()) {
					continue;
				}

				if (Math.abs(mouseX - indicator.x()) <= 5 && Math.abs(mouseY - indicator.y()) <= 5) {
					label = point.label();
					break;
				}
			}
		}

		if (label.isBlank()) {
			return;
		}

		int left = Math.min(mouseX + 8, mapLeft + MAP_SIZE - font.width(label) - 8);
		int top = Math.max(mapTop + 2, mouseY - 12);
		graphics.fill(left - 3, top - 2, left + font.width(label) + 3, top + 10, 0xEE1F1A17);
		graphics.outline(left - 3, top - 2, font.width(label) + 6, 12, 0xFFB69155);
		graphics.text(font, label, left, top, 0xFFF8E6BE, false);
	}

	private void drawOffMapPointIndicators(GuiGraphicsExtractor graphics, int mapLeft, int mapTop) {
		for (var point : snapshot.points()) {
			if (!"milepost".equals(point.kind())) {
				continue;
			}

			ClampedIndicator indicator = clampedIndicator(mapLeft, mapTop, point.pos().getX(), point.pos().getZ());

			if (indicator == null || !indicator.offMap()) {
				continue;
			}

			int color = pointColor(point.kind());
			graphics.fill(indicator.x() - 1, indicator.y() - 1, indicator.x() + 2, indicator.y() + 2, color);
			graphics.outline(indicator.x() - 3, indicator.y() - 3, 6, 6, 0xFF243022);
			graphics.fill(indicator.innerX(), indicator.innerY(), indicator.x() + 1, indicator.y() + 1, color);
		}
	}

	private ScreenPoint mapPoint(int mapLeft, int mapTop, int worldX, int worldZ) {
		double[] transformed = rotatedDelta(worldX - snapshot.center().getX(), worldZ - snapshot.center().getZ());
		int x = mapLeft + MAP_SIZE / 2 + (int) Math.round(transformed[0] * mapScale());
		int y = mapTop + MAP_SIZE / 2 + (int) Math.round(transformed[1] * mapScale());
		return new ScreenPoint(x, y);
	}

	private int[] worldPosAtPixel(int mapLeft, int mapTop, int pixelX, int pixelY) {
		double offsetX = (pixelX - mapLeft - MAP_SIZE / 2) / mapScale();
		double offsetY = (pixelY - mapTop - MAP_SIZE / 2) / mapScale();
		double[] worldDelta = inverseRotatedDelta(offsetX, offsetY);
		return new int[] {
			snapshot.center().getX() + (int) Math.round(worldDelta[0]),
			snapshot.center().getZ() + (int) Math.round(worldDelta[1])
		};
	}

	private static boolean insideMap(int mapLeft, int mapTop, int x, int y) {
		return x >= mapLeft && x < mapLeft + MAP_SIZE && y >= mapTop && y < mapTop + MAP_SIZE;
	}

	private static double horizontalDistanceSquared(int firstX, int firstZ, int secondX, int secondZ) {
		int dx = firstX - secondX;
		int dz = firstZ - secondZ;
		return dx * dx + dz * dz;
	}

	private static boolean[][] buildRevealedCells(SurveyorMapSnapshot snapshot) {
		boolean[][] revealed = new boolean[FOG_GRID_SIZE][FOG_GRID_SIZE];

		if (!snapshot.available()) {
			return revealed;
		}

		if (!snapshot.fogOfWarEnabled()) {
			reveal(revealed, snapshot, snapshot.center().getX(), snapshot.center().getZ(), snapshot.boundaryRadius());
			return revealed;
		}

		reveal(revealed, snapshot, snapshot.center().getX(), snapshot.center().getZ(), 14);

		for (var pos : snapshot.observedAreas()) {
			reveal(revealed, snapshot, pos.getX(), pos.getZ(), 24);
		}

		for (var point : snapshot.points()) {
			reveal(revealed, snapshot, point.pos().getX(), point.pos().getZ(), 14);
		}

		for (var pos : snapshot.roadwrights()) {
			reveal(revealed, snapshot, pos.getX(), pos.getZ(), 18);
		}

		for (var pos : snapshot.plannedRoadwork()) {
			reveal(revealed, snapshot, pos.getX(), pos.getZ(), 10);
		}

		for (var road : snapshot.roads()) {
			reveal(revealed, snapshot, road.pos().getX(), road.pos().getZ(), 5);
		}

		return revealed;
	}

	private static void reveal(boolean[][] revealed, SurveyorMapSnapshot snapshot, int worldX, int worldZ, int radiusBlocks) {
		double scale = MAP_SIZE / (double) (snapshot.radius() * 2);
		int centerX = MAP_SIZE / 2 + (int) Math.round((worldX - snapshot.center().getX()) * scale);
		int centerY = MAP_SIZE / 2 + (int) Math.round((worldZ - snapshot.center().getZ()) * scale);
		int pixelRadius = Math.max(1, (int) Math.ceil(radiusBlocks * scale));
		int cellRadius = Math.max(1, (int) Math.ceil(pixelRadius / (double) FOG_CELL_SIZE));
		int centerGridX = centerX / FOG_CELL_SIZE;
		int centerGridY = centerY / FOG_CELL_SIZE;

		for (int gridX = Math.max(0, centerGridX - cellRadius); gridX <= Math.min(FOG_GRID_SIZE - 1, centerGridX + cellRadius); gridX++) {
			for (int gridY = Math.max(0, centerGridY - cellRadius); gridY <= Math.min(FOG_GRID_SIZE - 1, centerGridY + cellRadius); gridY++) {
				int cellCenterX = gridX * FOG_CELL_SIZE + FOG_CELL_SIZE / 2;
				int cellCenterY = gridY * FOG_CELL_SIZE + FOG_CELL_SIZE / 2;
				int dx = cellCenterX - centerX;
				int dy = cellCenterY - centerY;

				if (dx * dx + dy * dy <= pixelRadius * pixelRadius) {
					revealed[gridX][gridY] = true;
				}
			}
		}
	}

	private static int roadColor(String quality) {
		return switch (quality) {
			case "gravel" -> 0xFF9C9C9C;
			case "cobble" -> 0xFF7C7C7C;
			case "finished" -> 0xFFD2D2D2;
			default -> 0xFFB99758;
		};
	}

	private static int pointColor(String kind) {
		return switch (kind) {
			case "center" -> 0xFFFFDD66;
			case "project" -> 0xFFFFAA44;
			case "building" -> 0xFF66FF88;
			case "milepost" -> 0xFFB6D0FF;
			default -> 0xFFFFFF66;
		};
	}

	private ClampedIndicator clampedIndicator(int mapLeft, int mapTop, int worldX, int worldZ) {
		double[] transformed = rotatedDelta(worldX - snapshot.center().getX(), worldZ - snapshot.center().getZ());
		double scaledX = transformed[0] * mapScale();
		double scaledY = transformed[1] * mapScale();
		double half = MAP_SIZE / 2.0D - 4.0D;

		if (Math.abs(scaledX) <= half && Math.abs(scaledY) <= half) {
			return new ClampedIndicator(0, 0, 0, 0, false);
		}

		double clampedScale = Math.min(half / Math.max(1.0D, Math.abs(scaledX)), half / Math.max(1.0D, Math.abs(scaledY)));
		int x = mapLeft + MAP_SIZE / 2 + (int) Math.round(scaledX * clampedScale);
		int y = mapTop + MAP_SIZE / 2 + (int) Math.round(scaledY * clampedScale);
		int innerX = mapLeft + MAP_SIZE / 2 + (int) Math.round(scaledX * Math.max(0.0D, clampedScale - 0.12D));
		int innerY = mapTop + MAP_SIZE / 2 + (int) Math.round(scaledY * Math.max(0.0D, clampedScale - 0.12D));
		return new ClampedIndicator(x, y, innerX, innerY, true);
	}

	private double mapScale() {
		return MAP_SIZE / (double) (snapshot.radius() * 2);
	}

	private double[] rotatedDelta(double worldDx, double worldDz) {
		double yawRadians = Math.toRadians(playerYawDegrees);
		double forwardX = -Math.sin(yawRadians);
		double forwardZ = Math.cos(yawRadians);
		double rightX = -forwardZ;
		double rightZ = forwardX;
		double screenX = (worldDx * rightX) + (worldDz * rightZ);
		double screenY = -((worldDx * forwardX) + (worldDz * forwardZ));
		return new double[] {screenX, screenY};
	}

	private double[] inverseRotatedDelta(double screenDx, double screenDy) {
		double yawRadians = Math.toRadians(playerYawDegrees);
		double forwardX = -Math.sin(yawRadians);
		double forwardZ = Math.cos(yawRadians);
		double rightX = -forwardZ;
		double rightZ = forwardX;
		double worldDx = (screenDx * rightX) + (-screenDy * forwardX);
		double worldDz = (screenDx * rightZ) + (-screenDy * forwardZ);
		return new double[] {worldDx, worldDz};
	}

	private record ClampedIndicator(int x, int y, int innerX, int innerY, boolean offMap) {
	}

	private record ScreenPoint(int x, int y) {
	}
}
