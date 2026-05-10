package com.ronhelwig.livevillages.client.hud;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import com.ronhelwig.livevillages.LiveVillages;
import com.ronhelwig.livevillages.client.LiveVillagesClientKeys;
import com.ronhelwig.livevillages.menu.TradeBoardGoodsView;
import com.ronhelwig.livevillages.menu.TradeBoardProjectView;
import com.ronhelwig.livevillages.menu.TradeBoardRoleView;
import com.ronhelwig.livevillages.menu.TradeBoardRouteView;
import com.ronhelwig.livevillages.menu.TradeBoardSettlementView;
import com.ronhelwig.livevillages.menu.TradeBoardTradeRules;
import com.ronhelwig.livevillages.network.SettlementOverlayConstructionView;
import com.ronhelwig.livevillages.network.SettlementOverlayRequestPayload;
import com.ronhelwig.livevillages.network.SettlementOverlaySnapshot;
import com.ronhelwig.livevillages.network.SettlementOverlayStatePayload;
import com.ronhelwig.livevillages.network.SettlementOverlayTaskView;
import com.ronhelwig.livevillages.network.SettlementOverlayWorkerView;

public final class SettlementInventoryOverlay {
	private static final KeyMapping TOGGLE_KEY = KeyMappingHelper.registerKeyMapping(
		new KeyMapping(
			"key.live-villages.toggle_settlement_overlay",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_I,
			LiveVillagesClientKeys.CONTROLS
		)
	);
	private static final int REFRESH_INTERVAL_TICKS = 20;
	private static final int OVERLAY_PADDING = 8;
	private static final int COLUMN_GAP = 22;
	private static final int LINE_HEIGHT = 10;
	private static final int LEFT_COLUMN_WIDTH = 180;
	private static final int RIGHT_COLUMN_WIDTH = 132;
	private static final int POPULATION_TASK_COLUMN_WIDTH = 228;
	private static final int MAX_OVERVIEW_ROLE_ROWS = 8;
	private static final int MAX_INVENTORY_ROWS = 18;
	private static final int MAX_NEED_ROWS = 12;
	private static final int MAX_TASK_ROWS = 14;
	private static final int MAX_ROUTE_ROWS = 12;
	private static final int MAX_PROJECT_ROWS = 8;
	private static final int MAX_WORKER_ROWS = 6;

	private static boolean visible;
	private static boolean waitingForSnapshot;
	private static long nextRefreshTick;
	private static SettlementOverlaySnapshot latestSnapshot;
	private static OverlayPage currentPage = OverlayPage.OVERVIEW;

	private SettlementInventoryOverlay() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(SettlementInventoryOverlay::tick);
		ClientPlayNetworking.registerGlobalReceiver(SettlementOverlayStatePayload.TYPE, (payload, context) -> {
			latestSnapshot = payload.snapshot();
			waitingForSnapshot = false;
		});
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, LiveVillages.id("settlement_inventory_overlay"), SettlementInventoryOverlay::render);
	}

	private static void tick(Minecraft client) {
		while (TOGGLE_KEY.consumeClick()) {
			if (!visible) {
				visible = true;
				currentPage = OverlayPage.OVERVIEW;
				latestSnapshot = null;
				waitingForSnapshot = false;
				nextRefreshTick = 0L;
				requestSnapshot(client);
			} else if (currentPage == OverlayPage.TRADES) {
				visible = false;
			} else {
				currentPage = currentPage.next();
				requestSnapshot(client);
			}
		}

		if (client.player == null || client.level == null) {
			latestSnapshot = null;
			waitingForSnapshot = false;
			nextRefreshTick = 0L;
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

		if (!ClientPlayNetworking.canSend(SettlementOverlayRequestPayload.TYPE)) {
			latestSnapshot = SettlementOverlaySnapshot.unavailable("Server does not support the Live Villages overlay.");
			waitingForSnapshot = false;
			nextRefreshTick = client.level.getGameTime() + 100L;
			return;
		}

		ClientPlayNetworking.send(SettlementOverlayRequestPayload.INSTANCE);
		waitingForSnapshot = true;
		nextRefreshTick = client.level.getGameTime() + REFRESH_INTERVAL_TICKS;
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!visible) {
			return;
		}

		Minecraft client = Minecraft.getInstance();

		if (client.player == null || client.level == null || client.screen != null) {
			return;
		}

		Font font = client.font;
		int leftX = OVERLAY_PADDING;
		int rightX = leftX + LEFT_COLUMN_WIDTH + COLUMN_GAP;
		int leftY = OVERLAY_PADDING;
		int rightY = OVERLAY_PADDING;

		if (latestSnapshot == null) {
			drawStatusMessage(graphics, font, leftX, leftY, currentPage.title(), "Fetching settlement ledger...");
			return;
		}

		if (latestSnapshot.settlement().isEmpty()) {
			drawStatusMessage(graphics, font, leftX, leftY, currentPage.title(), latestSnapshot.statusMessage());
			return;
		}

		TradeBoardSettlementView settlement = latestSnapshot.settlement().get();
		String distanceSummary = latestSnapshot.distanceBlocks() >= 0 ? latestSnapshot.distanceBlocks() + "b away" : "distance unknown";

		switch (currentPage) {
			case OVERVIEW -> renderOverview(graphics, font, leftX, rightX, leftY, rightY, settlement, distanceSummary, latestSnapshot);
			case INVENTORY -> renderInventory(graphics, font, leftX, rightX, leftY, rightY, settlement);
			case POPULATION -> renderPopulation(graphics, font, leftX, rightX, leftY, rightY, settlement, latestSnapshot);
			case WORKERS -> renderWorkers(graphics, font, leftX, rightX, leftY, rightY, settlement, latestSnapshot);
			case TRADES -> renderTrades(graphics, font, leftX, rightX, leftY, rightY, settlement);
		}
	}

	private static void renderOverview(
		GuiGraphicsExtractor graphics,
		Font font,
		int leftX,
		int rightX,
		int leftY,
		int rightY,
		TradeBoardSettlementView settlement,
		String distanceSummary,
		SettlementOverlaySnapshot snapshot
	) {
		leftY = renderLine(graphics, font, leftX, leftY, settlement.settlementName() + " [" + distanceSummary + "]", 0xFFF8E6BE);
		leftY = renderLine(graphics, font, leftX, leftY, "Kind: " + humanizeKey(settlement.settlementKind().getSerializedName()), 0xFFE6D6B7);
		leftY = renderLine(graphics, font, leftX, leftY, "Growth: " + settlement.growthSummary(), 0xFFE6D6B7);
		leftY = renderLine(graphics, font, leftX, leftY, "Population: " + settlement.population() + "  Housing: " + settlement.housingCapacity(), 0xFFDCC8A4);
		leftY = renderLine(graphics, font, leftX, leftY, "Treasury: " + settlement.emeraldWealth() + "e", 0xFFDCC8A4);
		leftY = renderLine(graphics, font, leftX, leftY, "Comfort: " + percent(settlement.comfort()) + "  Security: " + percent(settlement.security()), 0xFFDCC8A4);
		leftY = renderLine(graphics, font, leftX, leftY, "Routes: " + settlement.routes().size(), 0xFFDCC8A4);
		leftY += LINE_HEIGHT;
		leftY = drawRoleSection(graphics, font, leftX, leftY, LEFT_COLUMN_WIDTH, "Workforce", settlement.roleCounts(), MAX_OVERVIEW_ROLE_ROWS);
		leftY += LINE_HEIGHT;
		leftY = drawConstructionSection(graphics, font, leftX, leftY, LEFT_COLUMN_WIDTH, snapshot.construction().orElse(null));
		drawFooter(graphics, font, leftX, leftY + LINE_HEIGHT);

		rightY = drawGoodsSection(graphics, font, rightX, rightY, RIGHT_COLUMN_WIDTH, "Top Needs", settlement.shortages(), 6, true, 0xFFF0D6B2);
		rightY += LINE_HEIGHT;
		drawProjectSection(graphics, font, rightX, rightY, RIGHT_COLUMN_WIDTH, "Projects & Sites", settlement.projects(), 4);
	}

	private static void renderInventory(
		GuiGraphicsExtractor graphics,
		Font font,
		int leftX,
		int rightX,
		int leftY,
		int rightY,
		TradeBoardSettlementView settlement
	) {
		leftY = renderLine(graphics, font, leftX, leftY, settlement.settlementName() + " Inventory", 0xFFF8E6BE);
		leftY += LINE_HEIGHT;
		leftY = drawGoodsSection(graphics, font, leftX, leftY, LEFT_COLUMN_WIDTH, "Warehouse Stock", settlement.stock(), MAX_INVENTORY_ROWS, false, 0xFFE8DDC8);
		drawFooter(graphics, font, leftX, leftY + LINE_HEIGHT);

		rightY = renderLine(graphics, font, rightX, rightY, "Trade Pressure", 0xFFF8E6BE);
		rightY += LINE_HEIGHT;
		rightY = drawGoodsSection(graphics, font, rightX, rightY, RIGHT_COLUMN_WIDTH, "Needs", settlement.shortages(), MAX_NEED_ROWS, true, 0xFFF0D6B2);
		rightY += LINE_HEIGHT;
		drawGoodsSection(graphics, font, rightX, rightY, RIGHT_COLUMN_WIDTH, "Offers", settlement.surpluses(), MAX_NEED_ROWS, true, 0xFFD5E6B7);
	}

	private static void renderPopulation(
		GuiGraphicsExtractor graphics,
		Font font,
		int leftX,
		int rightX,
		int leftY,
		int rightY,
		TradeBoardSettlementView settlement,
		SettlementOverlaySnapshot snapshot
	) {
		leftY = renderLine(graphics, font, leftX, leftY, settlement.settlementName() + " Population", 0xFFF8E6BE);
		leftY = renderLine(graphics, font, leftX, leftY, "Population: " + settlement.population() + "  Housing: " + settlement.housingCapacity(), 0xFFDCC8A4);
		leftY += LINE_HEIGHT;
		leftY = drawRoleSection(graphics, font, leftX, leftY, LEFT_COLUMN_WIDTH, "Roles", settlement.roleCounts(), MAX_TASK_ROWS);
		drawFooter(graphics, font, leftX, leftY + LINE_HEIGHT);

		rightY = drawTaskSection(graphics, font, rightX, rightY, POPULATION_TASK_COLUMN_WIDTH, "Current Tasks", snapshot.tasks(), MAX_TASK_ROWS);
		rightY += LINE_HEIGHT;
		drawConstructionSection(graphics, font, rightX, rightY, POPULATION_TASK_COLUMN_WIDTH, snapshot.construction().orElse(null));
	}

	private static void renderTrades(
		GuiGraphicsExtractor graphics,
		Font font,
		int leftX,
		int rightX,
		int leftY,
		int rightY,
		TradeBoardSettlementView settlement
	) {
		leftY = renderLine(graphics, font, leftX, leftY, settlement.settlementName() + " Village Trades", 0xFFF8E6BE);
		leftY = renderLine(graphics, font, leftX, leftY, "Routes: " + settlement.routes().size(), 0xFFDCC8A4);
		leftY += LINE_HEIGHT;
		leftY = drawRouteSection(graphics, font, leftX, leftY, LEFT_COLUMN_WIDTH, "Trades With Other Villages", settlement.routes(), MAX_ROUTE_ROWS);
		drawFooter(graphics, font, leftX, leftY + LINE_HEIGHT);

		rightY = drawGoodsSection(graphics, font, rightX, rightY, RIGHT_COLUMN_WIDTH, "Needs", settlement.shortages(), 7, true, 0xFFF0D6B2);
		rightY += LINE_HEIGHT;
		drawGoodsSection(graphics, font, rightX, rightY, RIGHT_COLUMN_WIDTH, "Offers", settlement.surpluses(), 7, true, 0xFFD5E6B7);
	}

	private static void renderWorkers(
		GuiGraphicsExtractor graphics,
		Font font,
		int leftX,
		int rightX,
		int leftY,
		int rightY,
		TradeBoardSettlementView settlement,
		SettlementOverlaySnapshot snapshot
	) {
		leftY = renderLine(graphics, font, leftX, leftY, settlement.settlementName() + " Workers", 0xFFF8E6BE);
		leftY = renderLine(graphics, font, leftX, leftY, "Population: " + settlement.population() + "  Housing: " + settlement.housingCapacity(), 0xFFDCC8A4);
		leftY += LINE_HEIGHT;
		leftY = drawRoleSection(graphics, font, leftX, leftY, LEFT_COLUMN_WIDTH, "Roles", settlement.roleCounts(), MAX_TASK_ROWS);
		drawFooter(graphics, font, leftX, leftY + LINE_HEIGHT);

		rightY = drawWorkerSection(graphics, font, rightX, rightY, POPULATION_TASK_COLUMN_WIDTH, "Worker Details", snapshot.workers(), MAX_WORKER_ROWS);
		rightY += LINE_HEIGHT;
		drawConstructionSection(graphics, font, rightX, rightY, POPULATION_TASK_COLUMN_WIDTH, snapshot.construction().orElse(null));
	}

	private static int drawRoleSection(
		GuiGraphicsExtractor graphics,
		Font font,
		int x,
		int y,
		int width,
		String title,
		List<TradeBoardRoleView> entries,
		int maxRows
	) {
		int currentY = renderLine(graphics, font, x, y, title + ":", 0xFFF2CF84);

		if (entries.isEmpty()) {
			return renderLine(graphics, font, x, currentY, "None recorded", 0xFF9F8E72);
		}

		int visibleRows = Math.min(maxRows, entries.size());

		for (int index = 0; index < visibleRows; index++) {
			TradeBoardRoleView entry = entries.get(index);
			String countText = Integer.toString(entry.count());
			String line = trimToWidth(font, entry.label(), width - font.width(countText) - 6) + " " + countText;
			currentY = renderLine(graphics, font, x, currentY, line, 0xFFE8DDC8);
		}

		int hiddenCount = entries.size() - visibleRows;
		if (hiddenCount > 0) {
			currentY = renderLine(graphics, font, x, currentY, "+" + hiddenCount + " more", 0xFF9F8E72);
		}

		return currentY;
	}

	private static int drawTaskSection(
		GuiGraphicsExtractor graphics,
		Font font,
		int x,
		int y,
		int width,
		String title,
		List<SettlementOverlayTaskView> entries,
		int maxRows
	) {
		int currentY = renderLine(graphics, font, x, y, title + ":", 0xFFF2CF84);

		if (entries.isEmpty()) {
			return renderLine(graphics, font, x, currentY, "None recorded", 0xFF9F8E72);
		}

		int visibleRows = Math.min(maxRows, entries.size());

		for (int index = 0; index < visibleRows; index++) {
			SettlementOverlayTaskView entry = entries.get(index);
			String countText = Integer.toString(entry.count());
			String line = trimToWidth(font, entry.label(), width - font.width(countText) - 6) + " " + countText;
			currentY = renderLine(graphics, font, x, currentY, line, 0xFFE8DDC8);
		}

		int hiddenCount = entries.size() - visibleRows;
		if (hiddenCount > 0) {
			currentY = renderLine(graphics, font, x, currentY, "+" + hiddenCount + " more", 0xFF9F8E72);
		}

		return currentY;
	}

	private static int drawWorkerSection(
		GuiGraphicsExtractor graphics,
		Font font,
		int x,
		int y,
		int width,
		String title,
		List<SettlementOverlayWorkerView> entries,
		int maxRows
	) {
		int currentY = renderLine(graphics, font, x, y, title + ":", 0xFFF2CF84);

		if (entries.isEmpty()) {
			return renderLine(graphics, font, x, currentY, "No worker details recorded", 0xFF9F8E72);
		}

		int visibleRows = Math.min(maxRows, entries.size());

		for (int index = 0; index < visibleRows; index++) {
			SettlementOverlayWorkerView entry = entries.get(index);
			currentY = renderLine(graphics, font, x, currentY, trimToWidth(font, entry.workerLabel(), width), 0xFFE8DDC8);
			currentY = renderLine(graphics, font, x + 8, currentY, trimToWidth(font, entry.taskLabel(), Math.max(0, width - 8)), 0xFFBFAF91);

			if (!entry.targetLabel().isBlank()) {
				currentY = renderLine(graphics, font, x + 8, currentY, trimToWidth(font, entry.targetLabel(), Math.max(0, width - 8)), 0xFF9FD7E8);
			}

			if (!entry.detailLabel().isBlank()) {
				currentY = renderLine(graphics, font, x + 8, currentY, trimToWidth(font, entry.detailLabel(), Math.max(0, width - 8)), 0xFF9F8E72);
			}
		}

		int hiddenCount = entries.size() - visibleRows;
		if (hiddenCount > 0) {
			currentY = renderLine(graphics, font, x, currentY, "+" + hiddenCount + " more", 0xFF9F8E72);
		}

		return currentY;
	}

	private static int drawConstructionSection(
		GuiGraphicsExtractor graphics,
		Font font,
		int x,
		int y,
		int width,
		SettlementOverlayConstructionView construction
	) {
		int currentY = renderLine(graphics, font, x, y, "Construction:", 0xFFF2CF84);

		if (construction == null || construction.activeBuildSites() <= 0) {
			return renderLine(graphics, font, x, currentY, "No active build sites", 0xFF9F8E72);
		}

		currentY = renderLine(graphics, font, x, currentY, "Sites: " + construction.activeBuildSites() + "  Workers: " + construction.eligibleWorkers(), 0xFFE8DDC8);
		currentY = renderLine(graphics, font, x, currentY, trimToWidth(font, "Pending: " + construction.pendingBlocks() + "  Missing: " + construction.missingMaterialBlocks(), width), 0xFFE8DDC8);
		currentY = renderLine(graphics, font, x, currentY, "Blocked: " + construction.blockedBlocks() + "  Carrying: " + construction.carriedSupplies(), 0xFFE8DDC8);
		return currentY;
	}

	private static int drawRouteSection(
		GuiGraphicsExtractor graphics,
		Font font,
		int x,
		int y,
		int width,
		String title,
		List<TradeBoardRouteView> entries,
		int maxRows
	) {
		int currentY = renderLine(graphics, font, x, y, title + ":", 0xFFF2CF84);

		if (entries.isEmpty()) {
			return renderLine(graphics, font, x, currentY, "No routes recorded", 0xFF9F8E72);
		}

		int visibleRows = Math.min(maxRows, entries.size());

		for (int index = 0; index < visibleRows; index++) {
			TradeBoardRouteView entry = entries.get(index);
			currentY = renderLine(graphics, font, x, currentY, trimToWidth(font, entry.targetSettlementName(), width), 0xFFE8DDC8);
			currentY = renderLine(graphics, font, x + 8, currentY, trimToWidth(font, entry.summary(), Math.max(0, width - 8)), 0xFFBFAF91);
		}

		int hiddenCount = entries.size() - visibleRows;
		if (hiddenCount > 0) {
			currentY = renderLine(graphics, font, x, currentY, "+" + hiddenCount + " more", 0xFF9F8E72);
		}

		return currentY;
	}

	private static int drawProjectSection(
		GuiGraphicsExtractor graphics,
		Font font,
		int x,
		int y,
		int width,
		String title,
		List<TradeBoardProjectView> entries,
		int maxRows
	) {
		int currentY = renderLine(graphics, font, x, y, title + ":", 0xFFF2CF84);

		if (entries.isEmpty()) {
			return renderLine(graphics, font, x, currentY, "No queued projects", 0xFF9F8E72);
		}

		int visibleRows = Math.min(maxRows, entries.size());

		for (int index = 0; index < visibleRows; index++) {
			TradeBoardProjectView entry = entries.get(index);
			String progress = entry.progressPercent() + "%";
			String line = trimToWidth(font, entry.label(), width - font.width(progress) - 6) + " " + progress;
			currentY = renderLine(graphics, font, x, currentY, line, 0xFFE8DDC8);
		}

		int hiddenCount = entries.size() - visibleRows;
		if (hiddenCount > 0) {
			currentY = renderLine(graphics, font, x, currentY, "+" + hiddenCount + " more", 0xFF9F8E72);
		}

		return currentY;
	}

	private static int drawGoodsSection(
		GuiGraphicsExtractor graphics,
		Font font,
		int x,
		int y,
		int width,
		String title,
		List<TradeBoardGoodsView> entries,
		int maxRows,
		boolean includeTarget,
		int textColor
	) {
		int currentY = renderLine(graphics, font, x, y, title + ":", 0xFFF2CF84);

		if (entries.isEmpty()) {
			return renderLine(graphics, font, x, currentY, "None recorded", 0xFF9F8E72);
		}

		int visibleRows = Math.min(maxRows, entries.size());

		for (int index = 0; index < visibleRows; index++) {
			TradeBoardGoodsView entry = entries.get(index);
			String label = TradeBoardTradeRules.compactLabel(entry.goodsKey(), entry.label());
			String amount = includeTarget ? entry.current() + "/" + entry.target() : Integer.toString(entry.current());
			String line = trimToWidth(font, label, width - font.width(amount) - 6) + " " + amount;
			currentY = renderLine(graphics, font, x, currentY, line, textColor);
		}

		int hiddenCount = entries.size() - visibleRows;
		if (hiddenCount > 0) {
			currentY = renderLine(graphics, font, x, currentY, "+" + hiddenCount + " more", 0xFF9F8E72);
		}

		return currentY;
	}

	private static void drawStatusMessage(GuiGraphicsExtractor graphics, Font font, int x, int y, String title, String message) {
		int currentY = renderLine(graphics, font, x, y, title, 0xFFF8E6BE);
		currentY = renderLine(graphics, font, x, currentY, message, 0xFFE8DDC8);
		drawFooter(graphics, font, x, currentY + LINE_HEIGHT);
	}

	private static void drawFooter(GuiGraphicsExtractor graphics, Font font, int x, int y) {
		String action = currentPage == OverlayPage.TRADES ? "hide overlay" : "next: " + currentPage.next().title();
		renderLine(graphics, font, x, y, "[" + TOGGLE_KEY.getTranslatedKeyMessage().getString() + "] " + action, 0xFF9F8E72);
	}

	private static int renderLine(GuiGraphicsExtractor graphics, Font font, int x, int y, String text, int color) {
		graphics.text(font, text, x, y, color, true);
		return y + LINE_HEIGHT;
	}

	private static String trimToWidth(Font font, String text, int maxWidth) {
		if (font.width(text) <= maxWidth) {
			return text;
		}

		return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
	}

	private static String humanizeKey(String key) {
		String[] parts = key.split("_");
		StringBuilder result = new StringBuilder();

		for (String part : parts) {
			if (part.isEmpty()) {
				continue;
			}

			if (!result.isEmpty()) {
				result.append(' ');
			}

			result.append(Character.toUpperCase(part.charAt(0)));
			if (part.length() > 1) {
				result.append(part.substring(1));
			}
		}

		return result.toString();
	}

	private static String percent(double value) {
		return Math.round(value * 100.0D) + "%";
	}

	private enum OverlayPage {
		OVERVIEW("Overview"),
		INVENTORY("Inventory"),
		POPULATION("Population"),
		WORKERS("Workers"),
		TRADES("Trades With Villages");

		private final String title;

		OverlayPage(String title) {
			this.title = title;
		}

		private String title() {
			return title;
		}

		private OverlayPage next() {
			return switch (this) {
				case OVERVIEW -> INVENTORY;
				case INVENTORY -> POPULATION;
				case POPULATION -> WORKERS;
				case WORKERS -> TRADES;
				case TRADES -> OVERVIEW;
			};
		}
	}
}
