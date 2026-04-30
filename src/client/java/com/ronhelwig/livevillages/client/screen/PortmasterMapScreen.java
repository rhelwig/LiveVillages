package com.ronhelwig.livevillages.client.screen;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.ronhelwig.livevillages.network.PortmasterMapPortView;
import com.ronhelwig.livevillages.network.PortmasterMapSnapshot;
import com.ronhelwig.livevillages.network.PortmasterMapStatePayload;

public class PortmasterMapScreen extends Screen {
	private static final int MAP_SIZE = 184;
	private static final int TERRAIN_CELL_SIZE = 2;
	private static final int LEGEND_WIDTH = 104;
	private static final int COMPASS_SIZE = 26;
	private static final int PADDING = 10;
	private static final Component NO_CARTOGRAPHER_HINT = Component.literal("Hire a cartographer to expand the map.");
	private static final String MARKER_LOCAL_LIGHTHOUSE = "local_lighthouse";
	private static final String MARKER_LOCAL_PORT = "local_port";
	private static final String MARKER_KNOWN_LIGHTHOUSE = "known_lighthouse";
	private static final String MARKER_KNOWN_PORT = "known_port";
	private static final String MARKER_KNOWN_SETTLEMENT = "known_settlement";

	private final PortmasterMapSnapshot snapshot;
	private final List<Integer> zoomRadii;
	private float playerYawDegrees = 180.0F;
	private int currentRadius;

	public PortmasterMapScreen(PortmasterMapSnapshot snapshot) {
		super(Component.literal("Harbor Trade Map"));
		this.snapshot = snapshot;
		this.zoomRadii = buildZoomRadii(snapshot.radius());
		this.currentRadius = snapshot.radius();
	}

	public static void registerNetworking() {
		ClientPlayNetworking.registerGlobalReceiver(PortmasterMapStatePayload.TYPE, (payload, context) -> {
			context.client().setScreen(new PortmasterMapScreen(payload.snapshot()));
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

		graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xEE16110D);
		graphics.fill(left + 1, top + 1, left + panelWidth - 1, top + 18, 0xFF3B2D20);
		graphics.outline(left, top, panelWidth, panelHeight, 0xFFC8A96A);
		graphics.fill(mapLeft, mapTop, mapLeft + MAP_SIZE, mapTop + MAP_SIZE, 0xFF1F2930);
		graphics.outline(mapLeft, mapTop, MAP_SIZE, MAP_SIZE, 0xFF8D7650);

		graphics.text(font, snapshot.available() ? snapshot.settlementName() + " Trade Map" : "Trade map unavailable", left + PADDING, top + 5, 0xFFF6E1B8, false);
		if (snapshot.available() && !snapshot.timeLabel().isBlank()) {
			String timeLabel = snapshot.timeLabel();
			graphics.text(font, timeLabel, left + panelWidth - PADDING - font.width(timeLabel), top + 5, 0xFFE6D9C0, false);
		}

		if (!snapshot.available()) {
			graphics.text(font, snapshot.statusMessage(), mapLeft, mapTop + 8, 0xFFE6D9C0, false);
			return;
		}

		drawTerrain(graphics, mapLeft, mapTop);

		for (PortmasterMapPortView port : snapshot.ports()) {
			ScreenPoint point = mapPoint(mapLeft, mapTop, port.pos().getX(), port.pos().getZ());
			if (insideMap(mapLeft, mapTop, point.x(), point.y())) {
				drawPortSymbol(graphics, point.x(), point.y(), port.kind());
			}
		}

		drawOffMapPortIndicators(graphics, mapLeft, mapTop);
		drawCompassRose(graphics, font, compassLeft, compassTop);
		drawLegend(graphics, font, legendLeft, legendTop);
		drawHoveredLabel(graphics, font, mapLeft, mapTop, mouseX, mouseY);
		drawFooterHint(graphics, font, left, top, panelWidth, panelHeight);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (!snapshot.available()) {
			return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
		}

		if (verticalAmount > 0.0D) {
			if (applyZoom(true)) {
				afterMouseAction();
				return true;
			}

			return false;
		}

		if (verticalAmount < 0.0D) {
			if (applyZoom(false)) {
				afterMouseAction();
				return true;
			}

			return false;
		}

		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (!snapshot.available()) {
			return super.keyPressed(event);
		}

		if (event.key() == GLFW.GLFW_KEY_EQUAL || event.key() == GLFW.GLFW_KEY_KP_ADD) {
			if (applyZoom(true)) {
				afterKeyboardAction();
				return true;
			}

			return false;
		}

		if (event.key() == GLFW.GLFW_KEY_MINUS || event.key() == GLFW.GLFW_KEY_KP_SUBTRACT) {
			if (applyZoom(false)) {
				afterKeyboardAction();
				return true;
			}

			return false;
		}

		return super.keyPressed(event);
	}

	private void drawTerrain(GuiGraphicsExtractor graphics, int mapLeft, int mapTop) {
		for (int screenY = 0; screenY < MAP_SIZE; screenY += TERRAIN_CELL_SIZE) {
			for (int screenX = 0; screenX < MAP_SIZE; screenX += TERRAIN_CELL_SIZE) {
				int pixelCenterX = mapLeft + screenX + TERRAIN_CELL_SIZE / 2;
				int pixelCenterY = mapTop + screenY + TERRAIN_CELL_SIZE / 2;
				int[] worldPos = worldPosAtPixel(mapLeft, mapTop, pixelCenterX, pixelCenterY);
				int color = terrainColor(terrainAtWorldPos(worldPos[0], worldPos[1]));
				graphics.fill(
					mapLeft + screenX,
					mapTop + screenY,
					Math.min(mapLeft + screenX + TERRAIN_CELL_SIZE, mapLeft + MAP_SIZE),
					Math.min(mapTop + screenY + TERRAIN_CELL_SIZE, mapTop + MAP_SIZE),
					color
				);
			}
		}
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

	private void drawLegend(GuiGraphicsExtractor graphics, Font font, int x, int y) {
		graphics.text(font, "Legend", x, y, 0xFFF2CF84, false);
		y += 12;
		y = legendRow(graphics, font, x, y, 0xFF35546F, "Sea");
		y = legendRow(graphics, font, x, y, 0xFF6F7B5B, "Land");
		y = legendRow(graphics, font, x, y, 0xFF24211C, "Uncharted");
		y += 4;
		drawPortSymbol(graphics, x + 4, y + 5, MARKER_LOCAL_LIGHTHOUSE);
		graphics.text(font, "Local lighthouse", x + 12, y, 0xFFE6D9C0, false);
		y += 10;
		drawPortSymbol(graphics, x + 4, y + 5, MARKER_LOCAL_PORT);
		graphics.text(font, "Local port", x + 12, y, 0xFFE6D9C0, false);
		y += 10;
		drawPortSymbol(graphics, x + 4, y + 5, MARKER_KNOWN_LIGHTHOUSE);
		graphics.text(font, "Lighthouse", x + 12, y, 0xFFE6D9C0, false);
		y += 10;
		drawPortSymbol(graphics, x + 4, y + 5, MARKER_KNOWN_PORT);
		graphics.text(font, "Known port", x + 12, y, 0xFFE6D9C0, false);

		if (snapshot.ports().stream().anyMatch(port -> MARKER_KNOWN_SETTLEMENT.equals(port.kind()))) {
			y += 10;
			drawPortSymbol(graphics, x + 4, y + 5, MARKER_KNOWN_SETTLEMENT);
			graphics.text(font, "Settlement", x + 12, y, 0xFFE6D9C0, false);
		}

		y += 18;
		graphics.text(font, zoomLabel(), x, y, 0xFF9F8E72, false);
		y += 10;
		graphics.text(font, "Scroll or +/- zoom", x, y, 0xFF9F8E72, false);
		y += 14;
		graphics.text(font, "Esc closes", x, y, 0xFF9F8E72, false);
	}

	private int legendRow(GuiGraphicsExtractor graphics, Font font, int x, int y, int color, String label) {
		graphics.fill(x, y + 2, x + 8, y + 8, color);
		graphics.text(font, label, x + 12, y, 0xFFE6D9C0, false);
		return y + 10;
	}

	private void drawFooterHint(GuiGraphicsExtractor graphics, Font font, int left, int top, int panelWidth, int panelHeight) {
		if (!snapshot.available() || snapshot.hasCartographer()) {
			return;
		}

		int textWidth = font.width(NO_CARTOGRAPHER_HINT);
		int textX = left + (panelWidth - textWidth) / 2;
		int textY = top + panelHeight - PADDING - 2;
		graphics.text(font, NO_CARTOGRAPHER_HINT, textX, textY, 0xFFCFB98E, false);
	}

	private void drawHoveredLabel(GuiGraphicsExtractor graphics, Font font, int mapLeft, int mapTop, int mouseX, int mouseY) {
		String label = "";

		for (PortmasterMapPortView port : snapshot.ports()) {
			ScreenPoint point = mapPoint(mapLeft, mapTop, port.pos().getX(), port.pos().getZ());
			if (Math.abs(mouseX - point.x()) <= 5 && Math.abs(mouseY - point.y()) <= 5) {
				label = markerLabel(port);
				break;
			}
		}

		if (label.isBlank()) {
			for (PortmasterMapPortView port : snapshot.ports()) {
				ClampedIndicator indicator = clampedIndicator(mapLeft, mapTop, port.pos().getX(), port.pos().getZ());

				if (indicator == null || !indicator.offMap()) {
					continue;
				}

				if (Math.abs(mouseX - indicator.x()) <= 6 && Math.abs(mouseY - indicator.y()) <= 6) {
					label = markerLabel(port);
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

	private void drawOffMapPortIndicators(GuiGraphicsExtractor graphics, int mapLeft, int mapTop) {
		for (PortmasterMapPortView port : snapshot.ports()) {
			ClampedIndicator indicator = clampedIndicator(mapLeft, mapTop, port.pos().getX(), port.pos().getZ());

			if (indicator == null || !indicator.offMap()) {
				continue;
			}

			drawPortSymbol(graphics, indicator.x(), indicator.y(), port.kind());
			graphics.outline(indicator.x() - 4, indicator.y() - 4, 8, 8, 0xFF243022);
		}
	}

	private void drawPortSymbol(GuiGraphicsExtractor graphics, int x, int y, String kind) {
		if (MARKER_KNOWN_SETTLEMENT.equals(kind)) {
			int color = 0xFFB7D8A0;
			graphics.fill(x - 2, y - 2, x + 3, y + 3, color);
			graphics.fill(x - 1, y - 1, x + 2, y + 2, 0xFF243022);
			return;
		}

		if (MARKER_LOCAL_LIGHTHOUSE.equals(kind) || MARKER_KNOWN_LIGHTHOUSE.equals(kind)) {
			int color = MARKER_LOCAL_LIGHTHOUSE.equals(kind) ? 0xFFF9D982 : 0xFFD7C9A6;
			graphics.fill(x - 1, y - 3, x + 2, y + 2, color);
			graphics.fill(x - 2, y + 1, x + 3, y + 2, color);
			graphics.fill(x - 1, y - 4, x + 2, y - 3, 0xFFB13D3D);
			graphics.fill(x, y - 5, x + 1, y - 4, 0xFFF8E6BE);
			return;
		}

		int color = MARKER_LOCAL_PORT.equals(kind) ? 0xFFFFE19A : 0xFFF6E9C8;
		graphics.fill(x, y - 3, x + 1, y - 2, color);
		graphics.fill(x - 1, y - 2, x + 2, y - 1, color);
		graphics.fill(x, y - 1, x + 1, y + 3, color);
		graphics.fill(x - 2, y + 1, x + 3, y + 2, color);
		graphics.fill(x - 2, y + 2, x - 1, y + 3, color);
		graphics.fill(x + 1, y + 2, x + 2, y + 3, color);
	}

	private String markerLabel(PortmasterMapPortView port) {
		String suffix = switch (port.kind()) {
			case MARKER_LOCAL_LIGHTHOUSE, MARKER_KNOWN_LIGHTHOUSE -> "lighthouse";
			case MARKER_KNOWN_SETTLEMENT -> "settlement";
			default -> "port";
		};
		if (MARKER_LOCAL_PORT.equals(port.kind()) || MARKER_LOCAL_LIGHTHOUSE.equals(port.kind())) {
			return port.name() + " (" + suffix + ")";
		}

		return port.name() + " - " + port.distanceBlocks() + " blocks (" + suffix + ")";
	}

	private String zoomLabel() {
		return "Range " + currentRadius + " blocks";
	}

	private static int terrainColor(char terrain) {
		return switch (terrain) {
			case 'W' -> 0xFF35546F;
			case 'L' -> 0xFF6F7B5B;
			default -> 0xFF24211C;
		};
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

	private ClampedIndicator clampedIndicator(int mapLeft, int mapTop, int worldX, int worldZ) {
		double[] transformed = rotatedDelta(worldX - snapshot.center().getX(), worldZ - snapshot.center().getZ());
		double scaledX = transformed[0] * mapScale();
		double scaledY = transformed[1] * mapScale();
		double half = MAP_SIZE / 2.0D - 5.0D;

		if (Math.abs(scaledX) <= half && Math.abs(scaledY) <= half) {
			return new ClampedIndicator(0, 0, false);
		}

		double clampedScale = Math.min(half / Math.max(1.0D, Math.abs(scaledX)), half / Math.max(1.0D, Math.abs(scaledY)));
		int x = mapLeft + MAP_SIZE / 2 + (int) Math.round(scaledX * clampedScale);
		int y = mapTop + MAP_SIZE / 2 + (int) Math.round(scaledY * clampedScale);
		return new ClampedIndicator(x, y, true);
	}

	private static boolean insideMap(int mapLeft, int mapTop, int x, int y) {
		return x >= mapLeft && x < mapLeft + MAP_SIZE && y >= mapTop && y < mapTop + MAP_SIZE;
	}

	private double mapScale() {
		return MAP_SIZE / (double) (currentRadius * 2);
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

	private boolean applyZoom(boolean zoomIn) {
		int currentIndex = zoomRadii.indexOf(currentRadius);

		if (currentIndex < 0) {
			return false;
		}

		int nextIndex = zoomIn ? Math.max(0, currentIndex - 1) : Math.min(zoomRadii.size() - 1, currentIndex + 1);

		if (nextIndex == currentIndex) {
			return false;
		}

		currentRadius = zoomRadii.get(nextIndex);
		return true;
	}

	private static List<Integer> buildZoomRadii(int defaultRadius) {
		List<Integer> radii = new ArrayList<>();
		int radius = defaultRadius;
		radii.add(defaultRadius);

		while (radius > 32) {
			int next = roundUpToStep(Math.max(32, radius / 2), 16);

			if (next >= radius) {
				break;
			}

			radii.add(0, next);
			radius = next;
		}

		return List.copyOf(radii);
	}

	private static int roundUpToStep(int value, int step) {
		if (step <= 0) {
			return value;
		}

		return ((value + step - 1) / step) * step;
	}

	private char terrainAtWorldPos(int worldX, int worldZ) {
		int terrainGridSize = snapshot.terrainRows().size();

		if (terrainGridSize <= 0) {
			return 'U';
		}

		double normalizedX = (worldX - snapshot.center().getX()) / (double) (snapshot.terrainRadius() * 2) + 0.5D;
		double normalizedZ = (worldZ - snapshot.center().getZ()) / (double) (snapshot.terrainRadius() * 2) + 0.5D;

		if (normalizedX < 0.0D || normalizedX >= 1.0D || normalizedZ < 0.0D || normalizedZ >= 1.0D) {
			return 'U';
		}

		int gridX = Math.max(0, Math.min(terrainGridSize - 1, (int) Math.floor(normalizedX * terrainGridSize)));
		int gridZ = Math.max(0, Math.min(terrainGridSize - 1, (int) Math.floor(normalizedZ * terrainGridSize)));
		String row = snapshot.terrainRows().get(gridZ);

		if (gridX >= row.length()) {
			return 'U';
		}

		return row.charAt(gridX);
	}

	private record ClampedIndicator(int x, int y, boolean offMap) {
	}

	private record ScreenPoint(int x, int y) {
	}
}
