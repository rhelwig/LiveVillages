package com.ronhelwig.livevillages.client.screen;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import com.ronhelwig.livevillages.menu.TradeBoardGoodsView;
import com.ronhelwig.livevillages.menu.TradeBoardMenu;
import com.ronhelwig.livevillages.menu.TradeBoardPlayerGoodsView;
import com.ronhelwig.livevillages.menu.TradeBoardProjectView;
import com.ronhelwig.livevillages.menu.TradeBoardRouteView;
import com.ronhelwig.livevillages.menu.TradeBoardSettlementView;
import com.ronhelwig.livevillages.menu.TradeBoardTradeRules;
import com.ronhelwig.livevillages.menu.TradeBoardTrading;
import com.ronhelwig.livevillages.menu.TradeBoardTrading.GoodsTradeOption;
import com.ronhelwig.livevillages.menu.TradeBoardTrading.PlayerGoodsOption;
import com.ronhelwig.livevillages.network.TradeBoardRefreshPayload;

public class TradeBoardScreen extends AbstractContainerScreen<TradeBoardMenu> {
	private static final int SCREEN_WIDTH = 276;
	private static final int SCREEN_HEIGHT = 238;
	private static final int LEFT_COLUMN_X = 10;
	private static final int RIGHT_COLUMN_X = 144;
	private static final int WIDE_SECTION_X = 10;
	private static final int TAB_BAR_Y = 30;
	private static final int TAB_BUTTON_WIDTH = 60;
	private static final int TAB_BUTTON_HEIGHT = 16;
	private static final int TOP_SUMMARY_Y = 54;
	private static final int PRIMARY_SECTION_TOP_Y = 88;
	private static final int PRIMARY_SECTION_HEIGHT = 60;
	private static final int SECONDARY_SECTION_TOP_Y = PRIMARY_SECTION_TOP_Y + PRIMARY_SECTION_HEIGHT;
	private static final int ROUTE_SECTION_TOP_Y = SECONDARY_SECTION_TOP_Y;
	private static final int TRADE_SECTION_TOP_Y = 54;
	private static final int TRADE_ROW_SPACING = 20;
	private static final int INFO_ROW_SPACING = 10;
	private static final int GOODS_ROW_WIDTH = 124;
	private static final int OPTION_ROW_WIDTH = 122;
	private static final int SELECT_BUTTON_WIDTH = 44;
	private static final int ACTION_BUTTON_WIDTH = 42;
	private static final int ROW_BUTTON_HEIGHT = 16;
	private static final int DONATION_LABEL_Y = SCREEN_HEIGHT - 48;
	private static final int DONATION_BUTTON_Y = SCREEN_HEIGHT - 37;
	private static final int DONATION_BUTTON_WIDTH = 38;
	private static Tab lastActiveTab = Tab.OVERVIEW;

	private final Inventory playerInventory;
	private Tab activeTab = lastActiveTab;
	private int selectedPlayerGoodsIndex;
	private int selectedVillageGoodsIndex;
	private boolean hasPlayerGoodsSnapshot;
	private List<TradeBoardPlayerGoodsView> playerGoodsSnapshot = List.of();
	private String feedbackMessage = "";
	private int feedbackColor = 0xFF9F8E72;

	public TradeBoardScreen(TradeBoardMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title, SCREEN_WIDTH, SCREEN_HEIGHT);
		this.playerInventory = playerInventory;
	}

	public static void registerNetworking() {
		ClientPlayNetworking.registerGlobalReceiver(TradeBoardRefreshPayload.TYPE, (payload, context) -> {
			if (context.client().screen instanceof TradeBoardScreen screen && screen.menu.boardPos().equals(payload.boardPos())) {
				screen.updateFromServer(payload);
			}
		});
	}

	@Override
	protected void init() {
		super.init();
		rebuildTradeBoardWidgets();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		extractTransparentBackground(graphics);
		graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xEE1F1A17);
		graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + 26, 0xFF6B5A36);
		graphics.fill(leftPos + 1, topPos + 27, leftPos + imageWidth - 1, topPos + imageHeight - 1, 0xFF2C241A);
		graphics.outline(leftPos, topPos, imageWidth, imageHeight, 0xFFB69155);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		TradeBoardSettlementView settlement = menu.settlement();
		int growthWidth = font.width(settlement.growthSummary());

		graphics.text(font, settlement.settlementName(), 10, 8, 0xFFF8E6BE, false);
		graphics.text(font, settlement.growthSummary(), imageWidth - 10 - growthWidth, 9, 0xFFF2CF84, false);

		if (!activeTab.isTradeTab()) {
			int y = TOP_SUMMARY_Y;
			graphics.text(
				font,
				"Kind: " + humanizeKey(settlement.settlementKind().getSerializedName()) + "  Tier: " + settlement.tier() + "  Routes: " + settlement.routes().size(),
				10,
				y,
				0xFFE6D6B7,
				false
			);
			y += INFO_ROW_SPACING;
			graphics.text(
				font,
				"Population: " + settlement.population() + "  Housing: " + settlement.housingCapacity(),
				10,
				y,
				0xFFDCC8A4,
				false
			);
			y += INFO_ROW_SPACING;
			graphics.text(
				font,
				"Treasury: " + settlement.emeraldWealth() + "e  Comfort: " + percent(settlement.comfort()) + "  Security: " + percent(settlement.security()),
				10,
				y,
				0xFFDCC8A4,
				false
			);
		}

		switch (activeTab) {
			case OVERVIEW -> drawOverviewTab(graphics, settlement);
			case YOUR_GOODS -> drawYourGoodsTab(graphics, settlement);
			case VILLAGE_GOODS -> drawVillageGoodsTab(graphics, settlement);
			case ROUTES -> drawRoutesTab(graphics, settlement);
		}
	}

	private void rebuildTradeBoardWidgets() {
		clearWidgets();
		clampSelections();
		addTabButtons();

		if (activeTab == Tab.YOUR_GOODS) {
			addPlayerGoodsButtons();
		} else if (activeTab == Tab.VILLAGE_GOODS) {
			addVillageGoodsButtons();
		}
	}

	private void addTabButtons() {
		Tab[] tabs = Tab.values();
		int startX = leftPos + 8;

		for (int index = 0; index < tabs.length; index++) {
			Tab tab = tabs[index];
			Button button = Button.builder(Component.literal(tab.label), clicked -> switchTab(tab))
				.bounds(startX + index * (TAB_BUTTON_WIDTH + 6), topPos + TAB_BAR_Y, TAB_BUTTON_WIDTH, TAB_BUTTON_HEIGHT)
				.build();
			button.active = tab != activeTab;
			addRenderableWidget(button);
		}
	}

	private void addPlayerGoodsButtons() {
		List<PlayerGoodsOption> goodsRows = playerGoodsRows();
		int visibleCount = Math.min(TradeBoardTrading.MAX_TRADE_ROWS, goodsRows.size());

		for (int index = 0; index < visibleCount; index++) {
			int rowY = topPos + TRADE_SECTION_TOP_Y + 17 + index * TRADE_ROW_SPACING;
			int goodsIndex = index;
			Button button = Button.builder(Component.literal(index == selectedPlayerGoodsIndex ? "Chosen" : "Select"), clicked -> selectPlayerGoods(goodsIndex))
				.bounds(leftPos + LEFT_COLUMN_X + 78, rowY - 5, SELECT_BUTTON_WIDTH, ROW_BUTTON_HEIGHT)
				.build();
			button.active = index != selectedPlayerGoodsIndex;
			addRenderableWidget(button);
		}

		if (selectedPlayerGoodsIndex >= 0 && selectedPlayerGoodsIndex < goodsRows.size()) {
			PlayerGoodsOption selected = goodsRows.get(selectedPlayerGoodsIndex);
			List<GoodsTradeOption> payoutOptions = TradeBoardTrading.payoutOptionsForPlayerGoods(selected, menu.settlement());
			int optionCount = Math.min(TradeBoardTrading.MAX_TRADE_OPTIONS, payoutOptions.size());

			for (int index = 0; index < optionCount; index++) {
				int rowY = topPos + TRADE_SECTION_TOP_Y + 17 + index * TRADE_ROW_SPACING;
				int buttonId = TradeBoardTrading.playerTradeButtonId(selectedPlayerGoodsIndex, index);
				addRenderableWidget(
					Button.builder(Component.literal("Take"), clicked -> sendTradeButton(buttonId))
						.bounds(leftPos + RIGHT_COLUMN_X + 76, rowY - 5, ACTION_BUTTON_WIDTH, ROW_BUTTON_HEIGHT)
						.build()
				);
			}

			addDonationButton(selected, TradeBoardTrading.DONATE_ONE_INDEX, "Give 1", 0);
			addDonationButton(selected, TradeBoardTrading.DONATE_BUNDLE_INDEX, "Bundle", 1);
			addDonationButton(selected, TradeBoardTrading.DONATE_ALL_INDEX, "All", 2);
		}
	}

	private void addDonationButton(PlayerGoodsOption selected, int donationIndex, String label, int buttonColumn) {
		int amount = donationAmount(selected, donationIndex);
		Button button = Button.builder(
				Component.literal(label),
				clicked -> sendTradeButton(TradeBoardTrading.donateButtonId(selectedPlayerGoodsIndex, donationIndex))
			)
			.bounds(
				leftPos + RIGHT_COLUMN_X + buttonColumn * (DONATION_BUTTON_WIDTH + 3),
				topPos + DONATION_BUTTON_Y,
				DONATION_BUTTON_WIDTH,
				ROW_BUTTON_HEIGHT
			)
			.build();
		button.active = amount > 0;
		addRenderableWidget(button);
	}

	private void addVillageGoodsButtons() {
		List<TradeBoardGoodsView> goodsRows = villageGoodsRows();
		int visibleCount = Math.min(TradeBoardTrading.MAX_TRADE_ROWS, goodsRows.size());

		for (int index = 0; index < visibleCount; index++) {
			int rowY = topPos + TRADE_SECTION_TOP_Y + 17 + index * TRADE_ROW_SPACING;
			int goodsIndex = index;
			Button button = Button.builder(Component.literal(index == selectedVillageGoodsIndex ? "Chosen" : "Select"), clicked -> selectVillageGoods(goodsIndex))
				.bounds(leftPos + LEFT_COLUMN_X + 78, rowY - 5, SELECT_BUTTON_WIDTH, ROW_BUTTON_HEIGHT)
				.build();
			button.active = index != selectedVillageGoodsIndex;
			addRenderableWidget(button);
		}

		if (selectedVillageGoodsIndex >= 0 && selectedVillageGoodsIndex < goodsRows.size()) {
			TradeBoardGoodsView selected = goodsRows.get(selectedVillageGoodsIndex);
			List<GoodsTradeOption> paymentOptions = paymentOptionsForVillageGoods(selected);
			int optionCount = Math.min(TradeBoardTrading.MAX_TRADE_OPTIONS, paymentOptions.size());

			for (int index = 0; index < optionCount; index++) {
				int rowY = topPos + TRADE_SECTION_TOP_Y + 17 + index * TRADE_ROW_SPACING;
				int buttonId = TradeBoardTrading.villageTradeButtonId(selectedVillageGoodsIndex, index);
				addRenderableWidget(
					Button.builder(Component.literal("Trade"), clicked -> sendTradeButton(buttonId))
						.bounds(leftPos + RIGHT_COLUMN_X + 76, rowY - 5, ACTION_BUTTON_WIDTH, ROW_BUTTON_HEIGHT)
						.build()
				);
			}
		}
	}

	private void drawOverviewTab(GuiGraphicsExtractor graphics, TradeBoardSettlementView settlement) {
		drawTextSection(
			graphics,
			LEFT_COLUMN_X,
			PRIMARY_SECTION_TOP_Y,
			"Town Overview",
			List.of(
				"Tier: " + settlement.tier(),
				"Growth: " + settlement.growthSummary(),
				"Routes: " + settlement.routes().size(),
				"Shortages: " + settlement.shortages().size()
			)
		);
		drawInfoSection(graphics, RIGHT_COLUMN_X, PRIMARY_SECTION_TOP_Y, "Top Needs", firstRows(settlement.shortages(), 4), true);
		drawInfoSection(graphics, LEFT_COLUMN_X, SECONDARY_SECTION_TOP_Y, "Warehouse Stock", settlement.stock(), false);
		drawProjectSection(graphics, RIGHT_COLUMN_X, SECONDARY_SECTION_TOP_Y, "Projects", settlement.projects(), settlement.projects().size());
	}

	private void drawYourGoodsTab(GuiGraphicsExtractor graphics, TradeBoardSettlementView settlement) {
		List<PlayerGoodsOption> goodsRows = playerGoodsRows();
		graphics.text(font, "Your Goods", LEFT_COLUMN_X, TRADE_SECTION_TOP_Y, 0xFFF2CF84, false);
		graphics.text(font, "Village Will Offer", RIGHT_COLUMN_X, TRADE_SECTION_TOP_Y, 0xFFF2CF84, false);

		if (goodsRows.isEmpty()) {
			graphics.text(font, "No tradable goods", LEFT_COLUMN_X, TRADE_SECTION_TOP_Y + 14, 0xFF9F8E72, false);
		} else {
			drawPlayerGoodsRows(graphics, goodsRows);
		}

		if (selectedPlayerGoodsIndex < 0 || selectedPlayerGoodsIndex >= goodsRows.size()) {
			graphics.text(font, "Select one item", RIGHT_COLUMN_X, TRADE_SECTION_TOP_Y + 14, 0xFF9F8E72, false);
		} else {
			drawPayoutOptions(graphics, goodsRows.get(selectedPlayerGoodsIndex), settlement);
			drawDonationControls(graphics, goodsRows.get(selectedPlayerGoodsIndex));
		}

		drawFeedback(graphics);
	}

	private void drawVillageGoodsTab(GuiGraphicsExtractor graphics, TradeBoardSettlementView settlement) {
		List<TradeBoardGoodsView> goodsRows = villageGoodsRows();
		graphics.text(font, "Village Goods", LEFT_COLUMN_X, TRADE_SECTION_TOP_Y, 0xFFF2CF84, false);
		graphics.text(font, "Village Asks", RIGHT_COLUMN_X, TRADE_SECTION_TOP_Y, 0xFFF2CF84, false);

		if (goodsRows.isEmpty()) {
			graphics.text(font, "No protected surplus", LEFT_COLUMN_X, TRADE_SECTION_TOP_Y + 14, 0xFF9F8E72, false);
		} else {
			drawVillageGoodsRows(graphics, goodsRows);
		}

		if (selectedVillageGoodsIndex < 0 || selectedVillageGoodsIndex >= goodsRows.size()) {
			graphics.text(font, "Select one good", RIGHT_COLUMN_X, TRADE_SECTION_TOP_Y + 14, 0xFF9F8E72, false);
		} else {
			drawPaymentOptions(graphics, goodsRows.get(selectedVillageGoodsIndex), settlement);
		}

		drawFeedback(graphics);
	}

	private void drawRoutesTab(GuiGraphicsExtractor graphics, TradeBoardSettlementView settlement) {
		drawTextSection(
			graphics,
			LEFT_COLUMN_X,
			PRIMARY_SECTION_TOP_Y,
			"Network Status",
			List.of(
				"Tier: " + settlement.tier(),
				"Routes: " + settlement.routes().size(),
				"Growth: " + settlement.growthSummary(),
				"Shortages: " + settlement.shortages().size()
			)
		);
		drawRouteSection(graphics, WIDE_SECTION_X, ROUTE_SECTION_TOP_Y, "Recent Trade", settlement.routes(), settlement.routes().size());
	}

	private void drawPlayerGoodsRows(GuiGraphicsExtractor graphics, List<PlayerGoodsOption> entries) {
		int visibleCount = Math.min(TradeBoardTrading.MAX_TRADE_ROWS, entries.size());

		for (int index = 0; index < visibleCount; index++) {
			PlayerGoodsOption entry = entries.get(index);
			int rowY = TRADE_SECTION_TOP_Y + 13 + index * TRADE_ROW_SPACING;
			drawSelectionFill(graphics, LEFT_COLUMN_X, rowY - 4, GOODS_ROW_WIDTH, index == selectedPlayerGoodsIndex);
			drawGoodsIcon(graphics, entry.goodsKey(), LEFT_COLUMN_X + 1, rowY - 4, Math.min(64, entry.playerAmount()));
			String label = TradeBoardTradeRules.compactLabel(entry.goodsKey(), entry.label());
			String line = trimToWidth(label + " x" + entry.playerAmount(), 74);
			graphics.text(font, line, LEFT_COLUMN_X + 20, rowY, entry.wanted() ? 0xFFE8DDC8 : 0xFF9F8E72, false);

			String note = entry.wanted()
				? "Need " + entry.wantedAmount()
				: "Not wanted";
			graphics.text(font, trimToWidth(note, 72), LEFT_COLUMN_X + 20, rowY + 9, entry.wanted() ? 0xFFDCC8A4 : 0xFF806F5A, false);
		}

		drawHiddenCount(graphics, entries.size(), visibleCount, LEFT_COLUMN_X);
	}

	private void drawVillageGoodsRows(GuiGraphicsExtractor graphics, List<TradeBoardGoodsView> entries) {
		int visibleCount = Math.min(TradeBoardTrading.MAX_TRADE_ROWS, entries.size());

		for (int index = 0; index < visibleCount; index++) {
			TradeBoardGoodsView entry = entries.get(index);
			int rowY = TRADE_SECTION_TOP_Y + 13 + index * TRADE_ROW_SPACING;
			drawSelectionFill(graphics, LEFT_COLUMN_X, rowY - 4, GOODS_ROW_WIDTH, index == selectedVillageGoodsIndex);
			drawGoodsIcon(graphics, entry.goodsKey(), LEFT_COLUMN_X + 1, rowY - 4, Math.min(64, entry.current()));
			String label = TradeBoardTradeRules.compactLabel(entry.goodsKey(), entry.label());
			graphics.text(font, trimToWidth(label + " x" + entry.tradeBundleSize(), 74), LEFT_COLUMN_X + 20, rowY, 0xFFE8DDC8, false);
			graphics.text(font, trimToWidth("Stock " + entry.current() + "/" + entry.target(), 72), LEFT_COLUMN_X + 20, rowY + 9, 0xFFDCC8A4, false);
		}

		drawHiddenCount(graphics, entries.size(), visibleCount, LEFT_COLUMN_X);
	}

	private void drawPayoutOptions(GuiGraphicsExtractor graphics, PlayerGoodsOption selected, TradeBoardSettlementView settlement) {
		if (!selected.wanted()) {
			graphics.text(font, trimToWidth(settlement.settlementName() + " is not asking for this.", OPTION_ROW_WIDTH), RIGHT_COLUMN_X, TRADE_SECTION_TOP_Y + 14, 0xFF9F8E72, false);
			return;
		}

		if (!selected.canOfferBundle()) {
			String line = "Need " + selected.tradeBundleSize() + " " + selected.label().toLowerCase();
			graphics.text(font, trimToWidth(line, OPTION_ROW_WIDTH), RIGHT_COLUMN_X, TRADE_SECTION_TOP_Y + 14, 0xFF9F8E72, false);
			return;
		}

		List<GoodsTradeOption> options = TradeBoardTrading.payoutOptionsForPlayerGoods(selected, settlement);
		if (options.isEmpty()) {
			graphics.text(font, "No protected stock", RIGHT_COLUMN_X, TRADE_SECTION_TOP_Y + 14, 0xFF9F8E72, false);
			return;
		}

		for (int index = 0; index < options.size(); index++) {
			GoodsTradeOption option = options.get(index);
			int rowY = TRADE_SECTION_TOP_Y + 13 + index * TRADE_ROW_SPACING;
			drawGoodsIcon(graphics, option.goodsKey(), RIGHT_COLUMN_X + 1, rowY - 4, Math.min(64, option.amount()));
			String label = TradeBoardTradeRules.compactLabel(option.goodsKey(), option.label());
			graphics.text(font, trimToWidth(option.amount() + " " + label, 74), RIGHT_COLUMN_X + 20, rowY, 0xFFE8DDC8, false);
			graphics.text(font, trimToWidth("For " + selected.tradeBundleSize() + " " + selected.label(), 72), RIGHT_COLUMN_X + 20, rowY + 9, 0xFFDCC8A4, false);
		}
	}

	private void drawPaymentOptions(GuiGraphicsExtractor graphics, TradeBoardGoodsView selected, TradeBoardSettlementView settlement) {
		List<GoodsTradeOption> options = paymentOptionsForVillageGoods(selected);
		if (options.isEmpty()) {
			graphics.text(font, "You lack wanted goods", RIGHT_COLUMN_X, TRADE_SECTION_TOP_Y + 14, 0xFF9F8E72, false);
			return;
		}

		for (int index = 0; index < options.size(); index++) {
			GoodsTradeOption option = options.get(index);
			int rowY = TRADE_SECTION_TOP_Y + 13 + index * TRADE_ROW_SPACING;
			drawGoodsIcon(graphics, option.goodsKey(), RIGHT_COLUMN_X + 1, rowY - 4, Math.min(64, option.amount()));
			String label = TradeBoardTradeRules.compactLabel(option.goodsKey(), option.label());
			graphics.text(font, trimToWidth(option.amount() + " " + label, 74), RIGHT_COLUMN_X + 20, rowY, 0xFFE8DDC8, false);
			graphics.text(font, trimToWidth("For " + selected.tradeBundleSize() + " " + selected.label(), 72), RIGHT_COLUMN_X + 20, rowY + 9, 0xFFDCC8A4, false);
		}
	}

	private void drawDonationControls(GuiGraphicsExtractor graphics, PlayerGoodsOption selected) {
		String line = "Donate selected " + TradeBoardTradeRules.compactLabel(selected.goodsKey(), selected.label()).toLowerCase();
		graphics.text(font, trimToWidth(line, OPTION_ROW_WIDTH), RIGHT_COLUMN_X, DONATION_LABEL_Y, 0xFFF2CF84, false);
	}

	private void drawSelectionFill(GuiGraphicsExtractor graphics, int x, int y, int width, boolean selected) {
		if (selected) {
			graphics.fill(x - 2, y - 2, x + width, y + 18, 0x553E6A3B);
			graphics.outline(x - 2, y - 2, width + 2, 20, 0xFF9BC87D);
		}
	}

	private void drawGoodsIcon(GuiGraphicsExtractor graphics, String goodsKey, int x, int y, int amount) {
		ItemStack stack = TradeBoardTradeRules.createGoodsStack(goodsKey, Math.max(1, amount));
		if (!stack.isEmpty()) {
			graphics.item(stack, x, y);
		}
	}

	private void drawHiddenCount(GuiGraphicsExtractor graphics, int total, int visible, int x) {
		int hiddenCount = Math.max(0, total - visible);
		if (hiddenCount > 0) {
			graphics.text(font, "+" + hiddenCount + " more", x, TRADE_SECTION_TOP_Y + 13 + visible * TRADE_ROW_SPACING, 0xFF9F8E72, false);
		}
	}

	private void drawFeedback(GuiGraphicsExtractor graphics) {
		if (!feedbackMessage.isBlank()) {
			graphics.text(font, trimToWidth(feedbackMessage, imageWidth - 20), WIDE_SECTION_X, imageHeight - 16, feedbackColor, false);
		}
	}

	private void drawTextSection(
		GuiGraphicsExtractor graphics,
		int x,
		int y,
		String title,
		List<String> lines
	) {
		graphics.text(font, title, x, y, 0xFFF2CF84, false);

		if (lines.isEmpty()) {
			graphics.text(font, "None recorded", x, y + 12, 0xFF9F8E72, false);
			return;
		}

		int rowY = y + 12;

		for (String line : lines) {
			graphics.text(font, trimToWidth(line, 122), x, rowY, 0xFFE8DDC8, false);
			rowY += INFO_ROW_SPACING;
		}
	}

	private void drawInfoSection(
		GuiGraphicsExtractor graphics,
		int x,
		int y,
		String title,
		List<TradeBoardGoodsView> entries,
		boolean includeTarget
	) {
		graphics.text(font, title, x, y, 0xFFF2CF84, false);

		if (entries.isEmpty()) {
			graphics.text(font, "None recorded", x, y + 12, 0xFF9F8E72, false);
			return;
		}

		int rowY = y + 12;

		for (TradeBoardGoodsView entry : entries) {
			String label = TradeBoardTradeRules.compactLabel(entry.goodsKey(), entry.label());
			String line = includeTarget
				? "%s %d/%d".formatted(label, entry.current(), entry.target())
				: "%s: %d".formatted(label, entry.current());
			graphics.text(font, trimToWidth(line, infoSectionWidth(x)), x, rowY, 0xFFE8DDC8, false);
			rowY += INFO_ROW_SPACING;
		}
	}

	private void drawProjectSection(
		GuiGraphicsExtractor graphics,
		int x,
		int y,
		String title,
		List<TradeBoardProjectView> entries,
		int totalCount
	) {
		graphics.text(font, title, x, y, 0xFFF2CF84, false);

		if (entries.isEmpty()) {
			graphics.text(font, "None recorded", x, y + 12, 0xFF9F8E72, false);
			return;
		}

		int rowY = y + 12;

		for (TradeBoardProjectView entry : entries) {
			graphics.text(font, trimToWidth(entry.label() + " " + entry.progressPercent() + "%", infoSectionWidth(x)), x, rowY, 0xFFE8DDC8, false);
			rowY += INFO_ROW_SPACING;
		}

		int hiddenCount = Math.max(0, totalCount - entries.size());
		if (hiddenCount > 0) {
			graphics.text(font, "+" + hiddenCount + " more", x, rowY, 0xFF9F8E72, false);
		}
	}

	private void drawRouteSection(
		GuiGraphicsExtractor graphics,
		int x,
		int y,
		String title,
		List<TradeBoardRouteView> entries,
		int totalCount
	) {
		graphics.text(font, title, x, y, 0xFFF2CF84, false);

		if (entries.isEmpty()) {
			graphics.text(font, "No active routes recorded", x, y + 12, 0xFF9F8E72, false);
			return;
		}

		int rowY = y + 12;
		int maxWidth = imageWidth - 20;

		for (TradeBoardRouteView entry : entries) {
			String line = trimToWidth(entry.targetSettlementName() + ": " + entry.summary(), maxWidth);
			graphics.text(font, line, x, rowY, 0xFFE8DDC8, false);
			rowY += INFO_ROW_SPACING + 2;
		}

		int hiddenCount = Math.max(0, totalCount - entries.size());
		if (hiddenCount > 0) {
			graphics.text(font, "+" + hiddenCount + " more routes", x, rowY, 0xFF9F8E72, false);
		}
	}

	private void selectPlayerGoods(int index) {
		selectedPlayerGoodsIndex = index;
		rebuildTradeBoardWidgets();
	}

	private void selectVillageGoods(int index) {
		selectedVillageGoodsIndex = index;
		rebuildTradeBoardWidgets();
	}

	private void switchTab(Tab tab) {
		if (tab == activeTab) {
			return;
		}

		activeTab = tab;
		lastActiveTab = tab;
		rebuildTradeBoardWidgets();
	}

	private void updateFromServer(TradeBoardRefreshPayload payload) {
		menu.updateSettlement(payload.settlement());
		playerGoodsSnapshot = payload.playerGoods();
		hasPlayerGoodsSnapshot = true;
		feedbackMessage = payload.message();
		feedbackColor = payload.success() ? 0xFFD5E6B7 : 0xFFFF9A8A;
		rebuildTradeBoardWidgets();
	}

	private void sendTradeButton(int buttonId) {
		if (minecraft != null && minecraft.gameMode != null) {
			minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
		}
	}

	private void clampSelections() {
		int playerRows = playerGoodsRows().size();
		if (playerRows <= 0) {
			selectedPlayerGoodsIndex = 0;
		} else {
			selectedPlayerGoodsIndex = Math.max(0, Math.min(selectedPlayerGoodsIndex, Math.min(playerRows, TradeBoardTrading.MAX_TRADE_ROWS) - 1));
		}

		int villageRows = villageGoodsRows().size();
		if (villageRows <= 0) {
			selectedVillageGoodsIndex = 0;
		} else {
			selectedVillageGoodsIndex = Math.max(0, Math.min(selectedVillageGoodsIndex, Math.min(villageRows, TradeBoardTrading.MAX_TRADE_ROWS) - 1));
		}
	}

	private List<PlayerGoodsOption> playerGoodsRows() {
		List<PlayerGoodsOption> rows = hasPlayerGoodsSnapshot
			? TradeBoardTrading.playerGoodsOptions(playerGoodsSnapshot, menu.settlement())
			: TradeBoardTrading.playerGoodsOptions(playerInventory, menu.settlement());
		return rows.stream()
			.limit(TradeBoardTrading.MAX_TRADE_ROWS)
			.toList();
	}

	private List<TradeBoardGoodsView> villageGoodsRows() {
		return TradeBoardTrading.villageGoodsOptions(menu.settlement());
	}

	private List<GoodsTradeOption> paymentOptionsForVillageGoods(TradeBoardGoodsView selected) {
		return hasPlayerGoodsSnapshot
			? TradeBoardTrading.paymentOptionsForVillageGoods(selected, playerGoodsSnapshot, menu.settlement())
			: TradeBoardTrading.paymentOptionsForVillageGoods(selected, playerInventory, menu.settlement());
	}

	private static int donationAmount(PlayerGoodsOption goods, int donationIndex) {
		if (goods.playerAmount() <= 0) {
			return 0;
		}

		return switch (donationIndex) {
			case TradeBoardTrading.DONATE_ONE_INDEX -> 1;
			case TradeBoardTrading.DONATE_BUNDLE_INDEX -> Math.min(goods.playerAmount(), Math.max(1, goods.tradeBundleSize()));
			case TradeBoardTrading.DONATE_ALL_INDEX -> goods.playerAmount();
			default -> 0;
		};
	}

	private static String percent(double value) {
		return Math.round(value * 100.0D) + "%";
	}

	private static String humanizeKey(String value) {
		String[] parts = value.split("_");
		StringBuilder result = new StringBuilder();

		for (String part : parts) {
			if (part.isEmpty()) {
				continue;
			}

			if (!result.isEmpty()) {
				result.append(' ');
			}

			result.append(Character.toUpperCase(part.charAt(0)));
			result.append(part.substring(1));
		}

		return result.toString();
	}

	private String trimToWidth(String value, int maxWidth) {
		if (font.width(value) <= maxWidth) {
			return value;
		}

		String ellipsis = "...";
		int ellipsisWidth = font.width(ellipsis);
		int length = value.length();

		while (length > 0 && font.width(value.substring(0, length)) + ellipsisWidth > maxWidth) {
			length--;
		}

		return length <= 0 ? ellipsis : value.substring(0, length) + ellipsis;
	}

	private static <T> List<T> firstRows(List<T> entries, int maxRows) {
		return entries.subList(0, Math.min(entries.size(), maxRows));
	}

	private int infoSectionWidth(int x) {
		return x == WIDE_SECTION_X ? imageWidth - 20 : 122;
	}

	private enum Tab {
		OVERVIEW("Overview"),
		YOUR_GOODS("Your Goods"),
		VILLAGE_GOODS("Village"),
		ROUTES("Routes");

		private final String label;

		Tab(String label) {
			this.label = label;
		}

		private boolean isTradeTab() {
			return this == YOUR_GOODS || this == VILLAGE_GOODS;
		}
	}
}
