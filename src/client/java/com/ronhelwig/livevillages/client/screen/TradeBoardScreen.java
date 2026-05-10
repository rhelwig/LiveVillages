package com.ronhelwig.livevillages.client.screen;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import com.ronhelwig.livevillages.menu.TradeBoardGoodsView;
import com.ronhelwig.livevillages.menu.TradeBoardInventoryEntryView;
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
	private static final int SCREEN_WIDTH = 360;
	private static final int SCREEN_HEIGHT = 248;
	private static final int LEFT_COLUMN_X = 10;
	private static final int RIGHT_COLUMN_X = 172;
	private static final int WIDE_SECTION_X = 10;
	private static final int TAB_BAR_Y = 30;
	private static final int TAB_BUTTON_WIDTH = 77;
	private static final int TAB_BUTTON_HEIGHT = 16;
	private static final int TOP_SUMMARY_Y = 54;
	private static final int PRIMARY_SECTION_TOP_Y = 88;
	private static final int PRIMARY_SECTION_HEIGHT = 60;
	private static final int SECONDARY_SECTION_TOP_Y = PRIMARY_SECTION_TOP_Y + PRIMARY_SECTION_HEIGHT;
	private static final int ROUTE_SECTION_TOP_Y = SECONDARY_SECTION_TOP_Y;
	private static final int TRADE_SECTION_TOP_Y = 54;
	private static final int TRADE_ROW_SPACING = 20;
	private static final int INFO_ROW_SPACING = 10;
	private static final int GOODS_ROW_WIDTH = 150;
	private static final int GOODS_LIST_WIDTH = GOODS_ROW_WIDTH + 18;
	private static final int GOODS_LIST_TOP_Y = TRADE_SECTION_TOP_Y + 18;
	private static final int GOODS_LIST_HEIGHT = SCREEN_HEIGHT - GOODS_LIST_TOP_Y - 18;
	private static final int GOODS_LIST_ITEM_HEIGHT = 20;
	private static final int DETAIL_SECTION_WIDTH = SCREEN_WIDTH - RIGHT_COLUMN_X - 10;
	private static final int DETAIL_TEXT_WIDTH = DETAIL_SECTION_WIDTH - 24;
	private static final int DETAIL_ACTION_TEXT_WIDTH = DETAIL_SECTION_WIDTH - 24 - 52;
	private static final int DETAIL_OPTION_ROWS = 4;
	private static final int ACTION_BUTTON_WIDTH = 48;
	private static final int ROW_BUTTON_HEIGHT = 16;
	private static final int GOODS_ROW_HIGHLIGHT_Y_OFFSET = 5;
	private static final int GOODS_ROW_HIGHLIGHT_HEIGHT = 24;
	private static final int GOODS_NOTE_Y_OFFSET = 8;
	private static final int DETAIL_SUMMARY_Y = TRADE_SECTION_TOP_Y + 14;
	private static final int DETAIL_SECTION_TITLE_Y = TRADE_SECTION_TOP_Y + 40;
	private static final int DETAIL_FIRST_ROW_Y = TRADE_SECTION_TOP_Y + 56;
	private static final int DONATION_LABEL_Y = SCREEN_HEIGHT - 56;
	private static final int DONATION_NOTE_Y = SCREEN_HEIGHT - 46;
	private static final int DONATION_BUTTON_Y = SCREEN_HEIGHT - 32;
	private static final int DONATION_BUTTON_WIDTH = 50;
	private static final int DONATE_CONTENTS_BUTTON_WIDTH = 104;
	private static Tab lastActiveTab = Tab.OVERVIEW;

	private final Inventory playerInventory;
	private Tab activeTab = lastActiveTab;
	private String selectedPlayerGoodsKey;
	private int selectedVillageGoodsIndex = -1;
	private boolean hasPlayerGoodsSnapshot;
	private List<TradeBoardPlayerGoodsView> playerGoodsSnapshot = List.of();
	private boolean hasPlayerInventoryRowsSnapshot;
	private List<TradeBoardInventoryEntryView> playerInventoryRowsSnapshot = List.of();
	private String feedbackMessage = "";
	private int feedbackColor = 0xFF9F8E72;
	private PlayerInventoryListWidget playerInventoryList;
	private VillageGoodsListWidget villageGoodsList;

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

		if (activeTab.isTradeTab()) {
			int dividerX = leftPos + RIGHT_COLUMN_X - 8;
			graphics.fill(dividerX, topPos + TRADE_SECTION_TOP_Y - 6, dividerX + 1, topPos + imageHeight - 18, 0xFF4A3A26);
		}
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

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (activeTab == Tab.YOUR_GOODS && playerInventoryList != null && playerInventoryList.isMouseOver(mouseX, mouseY)) {
			if (playerInventoryList.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
				return true;
			}
		}

		if (activeTab == Tab.VILLAGE_GOODS && villageGoodsList != null && villageGoodsList.isMouseOver(mouseX, mouseY)) {
			if (villageGoodsList.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
				return true;
			}
		}

		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);

		if (activeTab != Tab.YOUR_GOODS || playerInventoryList == null) {
			return;
		}

		PlayerInventoryListEntry hoveredEntry = playerInventoryList.hoveredEntry();
		if (hoveredEntry == null || !hoveredEntry.view.hasStoredContents()) {
			return;
		}

		graphics.setTooltipForNextFrame(font, representativeStack(hoveredEntry.view), mouseX, mouseY);
	}

	private void rebuildTradeBoardWidgets() {
		clearWidgets();
		clampSelections();
		addTabButtons();

		if (activeTab == Tab.YOUR_GOODS) {
			rebuildPlayerInventoryList();
			addPlayerGoodsButtons();
		} else if (activeTab == Tab.VILLAGE_GOODS) {
			rebuildVillageGoodsList();
			addVillageGoodsButtons();
		}
	}

	private void rebuildPlayerInventoryList() {
		if (minecraft == null) {
			return;
		}

		if (playerInventoryList == null) {
			playerInventoryList = new PlayerInventoryListWidget(minecraft);
		} else {
			playerInventoryList.updateBounds();
		}

		playerInventoryList.rebuild(playerInventoryRows(), selectedPlayerGoodsKey);
		addRenderableWidget(playerInventoryList);
	}

	private void rebuildVillageGoodsList() {
		if (minecraft == null) {
			return;
		}

		if (villageGoodsList == null) {
			villageGoodsList = new VillageGoodsListWidget(minecraft);
		} else {
			villageGoodsList.updateBounds();
		}

		villageGoodsList.rebuild(villageGoodsRows(), selectedVillageGoodsIndex);
		addRenderableWidget(villageGoodsList);
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
		TradeBoardInventoryEntryView selected = selectedPlayerEntry();
		int selectedIndex = selectedPlayerEntryIndex();

		if (selected != null && selectedIndex >= 0) {
			if (selected.hasStoredContents()) {
				addRenderableWidget(
					Button.builder(Component.literal("Donate contents"), clicked -> sendTradeButton(TradeBoardTrading.donateContentsButtonId(selectedIndex)))
						.bounds(leftPos + RIGHT_COLUMN_X, topPos + DONATION_BUTTON_Y, DONATE_CONTENTS_BUTTON_WIDTH, ROW_BUTTON_HEIGHT)
						.build()
				);
				return;
			}

			PlayerGoodsOption tradeOption = tradeOptionForRow(selected);
			List<GoodsTradeOption> payoutOptions = tradeOption == null
				? List.of()
				: TradeBoardTrading.payoutOptionsForPlayerGoods(tradeOption, menu.settlement());
			int optionCount = Math.min(DETAIL_OPTION_ROWS, payoutOptions.size());

			for (int index = 0; index < optionCount; index++) {
				int buttonId = TradeBoardTrading.playerTradeButtonId(selectedIndex, index);
				addRenderableWidget(
					Button.builder(Component.literal("Take"), clicked -> sendTradeButton(buttonId))
						.bounds(detailButtonX(), topPos + detailOptionRowY(index) - 5, ACTION_BUTTON_WIDTH, ROW_BUTTON_HEIGHT)
						.build()
				);
			}

			addDonationButton(selected, TradeBoardTrading.DONATE_ONE_INDEX, "Give 1", 0);
			addDonationButton(selected, TradeBoardTrading.DONATE_BUNDLE_INDEX, "Bundle", 1);
			addDonationButton(selected, TradeBoardTrading.DONATE_ALL_INDEX, "All", 2);
		}
	}

	private void addDonationButton(TradeBoardInventoryEntryView selected, int donationIndex, String label, int buttonColumn) {
		int selectedIndex = selectedPlayerEntryIndex();
		int amount = donationAmount(selected, donationIndex);
		Button button = Button.builder(
				Component.literal(label),
				clicked -> sendTradeButton(TradeBoardTrading.donateButtonId(selectedIndex, donationIndex))
			)
			.bounds(
				leftPos + RIGHT_COLUMN_X + buttonColumn * (DONATION_BUTTON_WIDTH + 4),
				topPos + DONATION_BUTTON_Y,
				DONATION_BUTTON_WIDTH,
				ROW_BUTTON_HEIGHT
			)
			.build();
		button.active = selectedIndex >= 0 && amount > 0;
		addRenderableWidget(button);
	}

	private void addVillageGoodsButtons() {
		List<TradeBoardGoodsView> goodsRows = villageGoodsRows();

		if (selectedVillageGoodsIndex >= 0 && selectedVillageGoodsIndex < goodsRows.size()) {
			TradeBoardGoodsView selected = goodsRows.get(selectedVillageGoodsIndex);
			List<GoodsTradeOption> paymentOptions = paymentOptionsForVillageGoods(selected);
			int optionCount = Math.min(DETAIL_OPTION_ROWS, paymentOptions.size());

			for (int index = 0; index < optionCount; index++) {
				int buttonId = TradeBoardTrading.villageTradeButtonId(selectedVillageGoodsIndex, index);
				addRenderableWidget(
					Button.builder(Component.literal("Trade"), clicked -> sendTradeButton(buttonId))
						.bounds(detailButtonX(), topPos + detailOptionRowY(index) - 5, ACTION_BUTTON_WIDTH, ROW_BUTTON_HEIGHT)
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
		drawProjectSection(graphics, RIGHT_COLUMN_X, SECONDARY_SECTION_TOP_Y, "Projects & Sites", settlement.projects(), settlement.projects().size());
	}

	private void drawYourGoodsTab(GuiGraphicsExtractor graphics, TradeBoardSettlementView settlement) {
		graphics.text(font, "Inventory Goods", LEFT_COLUMN_X, TRADE_SECTION_TOP_Y, 0xFFF2CF84, false);
		graphics.text(font, "Selected Good", RIGHT_COLUMN_X, TRADE_SECTION_TOP_Y, 0xFFF2CF84, false);

		TradeBoardInventoryEntryView selected = selectedPlayerEntry();
		if (playerInventoryRows().isEmpty()) {
			graphics.text(font, "No inventory goods", LEFT_COLUMN_X, TRADE_SECTION_TOP_Y + 14, 0xFF9F8E72, false);
		}

		if (selected == null) {
			graphics.text(font, "Choose one of your goods on the left.", RIGHT_COLUMN_X, TRADE_SECTION_TOP_Y + 14, 0xFF9F8E72, false);
		} else {
			drawPlayerGoodsDetail(graphics, selected, settlement);
		}

		drawFeedback(graphics);
	}

	private void drawVillageGoodsTab(GuiGraphicsExtractor graphics, TradeBoardSettlementView settlement) {
		List<TradeBoardGoodsView> goodsRows = villageGoodsRows();
		graphics.text(font, "Village Goods", LEFT_COLUMN_X, TRADE_SECTION_TOP_Y, 0xFFF2CF84, false);
		graphics.text(font, "Selected Good", RIGHT_COLUMN_X, TRADE_SECTION_TOP_Y, 0xFFF2CF84, false);

		if (goodsRows.isEmpty()) {
			graphics.text(font, "No protected surplus", LEFT_COLUMN_X, TRADE_SECTION_TOP_Y + 14, 0xFF9F8E72, false);
		}

		if (selectedVillageGoodsIndex < 0 || selectedVillageGoodsIndex >= goodsRows.size()) {
			graphics.text(font, "Choose one village good on the left.", RIGHT_COLUMN_X, TRADE_SECTION_TOP_Y + 14, 0xFF9F8E72, false);
		} else {
			drawVillageGoodsDetail(graphics, goodsRows.get(selectedVillageGoodsIndex), settlement);
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

	private void drawPlayerGoodsDetail(GuiGraphicsExtractor graphics, TradeBoardInventoryEntryView selected, TradeBoardSettlementView settlement) {
		drawSelectionFill(graphics, RIGHT_COLUMN_X, DETAIL_SUMMARY_Y - GOODS_ROW_HIGHLIGHT_Y_OFFSET, DETAIL_SECTION_WIDTH, GOODS_ROW_HIGHLIGHT_HEIGHT, true);
		drawItemStack(graphics, representativeStack(selected), RIGHT_COLUMN_X + 1, DETAIL_SUMMARY_Y - 4);
		graphics.text(font, trimToWidth(selected.label() + " x" + selected.totalCount(), DETAIL_TEXT_WIDTH), RIGHT_COLUMN_X + 20, DETAIL_SUMMARY_Y, 0xFFE8DDC8, false);

		PlayerGoodsOption tradeOption = tradeOptionForRow(selected);
		if (selected.hasStoredContents()) {
			graphics.text(font, trimToWidth(selected.label() + " is carrying stored contents.", DETAIL_TEXT_WIDTH), RIGHT_COLUMN_X + 20, DETAIL_SUMMARY_Y + GOODS_NOTE_Y_OFFSET, 0xFFDCC8A4, false);
			graphics.text(font, "Stored contents", RIGHT_COLUMN_X, DETAIL_SECTION_TITLE_Y, 0xFFF2CF84, false);
			String containerNote = selected.storedContentStacks() == 1
				? "This container holds 1 stored stack. Hover the row to preview it, then use Donate contents to empty it into village stock."
				: "This container holds " + selected.storedContentStacks() + " stored stacks. Hover the row to preview them, then use Donate contents to empty it into village stock.";
			graphics.text(font, trimToWidth(containerNote, DETAIL_TEXT_WIDTH), RIGHT_COLUMN_X, DETAIL_FIRST_ROW_Y, 0xFF9F8E72, false);
			drawDonationControls(graphics, selected);
			return;
		}

		String note = tradeOption != null && tradeOption.wanted()
			? settlement.settlementName() + " needs " + tradeOption.wantedAmount() + " more."
			: tradeOption != null
				? settlement.settlementName() + " is not asking for this right now."
				: settlement.settlementName() + " does not have a known trade use for this yet.";
		int noteColor = tradeOption != null && tradeOption.wanted() ? 0xFFDCC8A4 : 0xFF9F8E72;
		graphics.text(font, trimToWidth(note, DETAIL_TEXT_WIDTH), RIGHT_COLUMN_X + 20, DETAIL_SUMMARY_Y + GOODS_NOTE_Y_OFFSET, noteColor, false);

		if (tradeOption == null) {
			graphics.text(font, "Unknown settlement pricing", RIGHT_COLUMN_X, DETAIL_SECTION_TITLE_Y, 0xFFF2CF84, false);
			graphics.text(font, trimToWidth("You can still donate this stack so the village can hold it for later use or barter.", DETAIL_TEXT_WIDTH), RIGHT_COLUMN_X, DETAIL_FIRST_ROW_Y, 0xFF9F8E72, false);
			drawDonationControls(graphics, selected);
			return;
		}

		if (!tradeOption.wanted()) {
			graphics.text(font, "No current trade demand", RIGHT_COLUMN_X, DETAIL_SECTION_TITLE_Y, 0xFFF2CF84, false);
			graphics.text(font, trimToWidth("You can still donate it directly to help future stock.", DETAIL_TEXT_WIDTH), RIGHT_COLUMN_X, DETAIL_FIRST_ROW_Y, 0xFF9F8E72, false);
			drawDonationControls(graphics, selected);
			return;
		}

		if (!tradeOption.canOfferBundle()) {
			graphics.text(font, "Bundle trade requirement", RIGHT_COLUMN_X, DETAIL_SECTION_TITLE_Y, 0xFFF2CF84, false);
			String requirement = "Need " + tradeOption.tradeBundleSize() + " " + tradeOption.label().toLowerCase() + " across your inventory for a full trade.";
			graphics.text(font, trimToWidth(requirement, DETAIL_TEXT_WIDTH), RIGHT_COLUMN_X, DETAIL_FIRST_ROW_Y, 0xFF9F8E72, false);
			drawDonationControls(graphics, selected);
			return;
		}

		graphics.text(font, "Village offers for one bundle", RIGHT_COLUMN_X, DETAIL_SECTION_TITLE_Y, 0xFFF2CF84, false);
		drawPayoutOptions(graphics, tradeOption, settlement);
		drawDonationControls(graphics, selected);
	}

	private void drawVillageGoodsDetail(GuiGraphicsExtractor graphics, TradeBoardGoodsView selected, TradeBoardSettlementView settlement) {
		drawSelectionFill(graphics, RIGHT_COLUMN_X, DETAIL_SUMMARY_Y - GOODS_ROW_HIGHLIGHT_Y_OFFSET, DETAIL_SECTION_WIDTH, GOODS_ROW_HIGHLIGHT_HEIGHT, true);
		drawGoodsIcon(graphics, selected.goodsKey(), RIGHT_COLUMN_X + 1, DETAIL_SUMMARY_Y - 4, Math.min(64, selected.current()));
		String label = TradeBoardTradeRules.compactLabel(selected.goodsKey(), selected.label());
		graphics.text(font, trimToWidth(label + " x" + selected.tradeBundleSize(), DETAIL_TEXT_WIDTH), RIGHT_COLUMN_X + 20, DETAIL_SUMMARY_Y, 0xFFE8DDC8, false);
		String note = "Can spare " + availableVillageGoods(selected) + ", keeps " + selected.target() + " in reserve.";
		graphics.text(font, trimToWidth(note, DETAIL_TEXT_WIDTH), RIGHT_COLUMN_X + 20, DETAIL_SUMMARY_Y + GOODS_NOTE_Y_OFFSET, 0xFFDCC8A4, false);

		graphics.text(font, "Village wants in return", RIGHT_COLUMN_X, DETAIL_SECTION_TITLE_Y, 0xFFF2CF84, false);
		drawPaymentOptions(graphics, selected, settlement);
	}

	private void drawPayoutOptions(GuiGraphicsExtractor graphics, PlayerGoodsOption selected, TradeBoardSettlementView settlement) {
		List<GoodsTradeOption> options = TradeBoardTrading.payoutOptionsForPlayerGoods(selected, settlement);
		if (options.isEmpty()) {
			graphics.text(font, "No protected surplus to trade back yet.", RIGHT_COLUMN_X, DETAIL_FIRST_ROW_Y, 0xFF9F8E72, false);
			return;
		}

		int visibleCount = Math.min(DETAIL_OPTION_ROWS, options.size());

		for (int index = 0; index < visibleCount; index++) {
			GoodsTradeOption option = options.get(index);
			int rowY = detailOptionRowY(index);
			drawGoodsIcon(graphics, option.goodsKey(), RIGHT_COLUMN_X + 1, rowY - 4, Math.min(64, option.amount()));
			String optionLabel = TradeBoardTradeRules.compactLabel(option.goodsKey(), option.label());
			graphics.text(font, trimToWidth(option.amount() + " " + optionLabel, DETAIL_ACTION_TEXT_WIDTH), RIGHT_COLUMN_X + 20, rowY, 0xFFE8DDC8, false);
			graphics.text(font, trimToWidth("For " + selected.tradeBundleSize() + " " + selected.label().toLowerCase(), DETAIL_ACTION_TEXT_WIDTH), RIGHT_COLUMN_X + 20, rowY + 9, 0xFFDCC8A4, false);
		}

		drawDetailHiddenCount(graphics, options.size(), visibleCount);
	}

	private void drawPaymentOptions(GuiGraphicsExtractor graphics, TradeBoardGoodsView selected, TradeBoardSettlementView settlement) {
		List<GoodsTradeOption> options = paymentOptionsForVillageGoods(selected);
		if (options.isEmpty()) {
			String message = "You do not have any goods " + settlement.settlementName() + " wants for this trade yet.";
			graphics.text(font, trimToWidth(message, DETAIL_TEXT_WIDTH), RIGHT_COLUMN_X, DETAIL_FIRST_ROW_Y, 0xFF9F8E72, false);
			return;
		}

		int visibleCount = Math.min(DETAIL_OPTION_ROWS, options.size());

		for (int index = 0; index < visibleCount; index++) {
			GoodsTradeOption option = options.get(index);
			int rowY = detailOptionRowY(index);
			drawGoodsIcon(graphics, option.goodsKey(), RIGHT_COLUMN_X + 1, rowY - 4, Math.min(64, option.amount()));
			String optionLabel = TradeBoardTradeRules.compactLabel(option.goodsKey(), option.label());
			graphics.text(font, trimToWidth(option.amount() + " " + optionLabel, DETAIL_ACTION_TEXT_WIDTH), RIGHT_COLUMN_X + 20, rowY, 0xFFE8DDC8, false);
			graphics.text(font, trimToWidth("For " + selected.tradeBundleSize() + " " + selected.label().toLowerCase(), DETAIL_ACTION_TEXT_WIDTH), RIGHT_COLUMN_X + 20, rowY + 9, 0xFFDCC8A4, false);
		}

		drawDetailHiddenCount(graphics, options.size(), visibleCount);
	}

	private void drawDonationControls(GuiGraphicsExtractor graphics, TradeBoardInventoryEntryView selected) {
		if (selected.hasStoredContents()) {
			String stackText = selected.storedContentStacks() == 1 ? "1 stored stack." : selected.storedContentStacks() + " stored stacks.";
			graphics.text(font, trimToWidth("Donate contents of selected " + selected.label().toLowerCase(), DETAIL_TEXT_WIDTH), RIGHT_COLUMN_X, DONATION_LABEL_Y, 0xFFF2CF84, false);
			graphics.text(font, trimToWidth(stackText + " Hover the row to inspect them first.", DETAIL_TEXT_WIDTH), RIGHT_COLUMN_X, DONATION_NOTE_Y, 0xFF9F8E72, false);
			return;
		}

		graphics.text(font, trimToWidth("Donate selected " + selected.label().toLowerCase(), DETAIL_TEXT_WIDTH), RIGHT_COLUMN_X, DONATION_LABEL_Y, 0xFFF2CF84, false);
		String bundleText = selected.bundleSize() == 1 ? "Bundle: 1 item." : "Bundle: " + selected.bundleSize() + " items.";
		graphics.text(font, trimToWidth(bundleText + " Adds directly to village stock.", DETAIL_TEXT_WIDTH), RIGHT_COLUMN_X, DONATION_NOTE_Y, 0xFF9F8E72, false);
	}

	private void drawSelectionFill(GuiGraphicsExtractor graphics, int x, int y, int width, int height, boolean selected) {
		if (selected) {
			graphics.fill(x - 2, y - 2, x + width, y + height, 0x553E6A3B);
			graphics.outline(x - 2, y - 2, width + 2, height + 2, 0xFF9BC87D);
		}
	}

	private void drawGoodsIcon(GuiGraphicsExtractor graphics, String goodsKey, int x, int y, int amount) {
		ItemStack stack = TradeBoardTradeRules.createGoodsStack(goodsKey, Math.max(1, amount));
		if (!stack.isEmpty()) {
			graphics.item(stack, x, y);
		}
	}

	private void drawItemStack(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
		if (!stack.isEmpty()) {
			graphics.item(stack, x, y);
		}
	}

	private void drawDetailHiddenCount(GuiGraphicsExtractor graphics, int total, int visible) {
		int hiddenCount = Math.max(0, total - visible);
		if (hiddenCount > 0) {
			graphics.text(font, "+" + hiddenCount + " more offers", RIGHT_COLUMN_X, detailOptionRowY(visible), 0xFF9F8E72, false);
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
			graphics.text(font, trimToWidth(line, infoSectionWidth(x)), x, rowY, 0xFFE8DDC8, false);
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

	private void selectPlayerGoods(String rowKey) {
		selectedPlayerGoodsKey = rowKey;
		if (playerInventoryList != null) {
			playerInventoryList.selectKey(selectedPlayerGoodsKey);
		}
		rebuildTradeBoardWidgets();
	}

	private void selectVillageGoods(int index) {
		selectedVillageGoodsIndex = index;
		if (villageGoodsList != null) {
			villageGoodsList.selectIndex(index);
		}
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
		playerInventoryRowsSnapshot = payload.inventoryRows();
		hasPlayerInventoryRowsSnapshot = true;
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
		List<TradeBoardInventoryEntryView> playerRows = playerInventoryRows();
		if (playerRows.isEmpty()) {
			selectedPlayerGoodsKey = null;
		} else {
			boolean found = false;
			for (TradeBoardInventoryEntryView entry : playerRows) {
				if (entry.rowKey().equals(selectedPlayerGoodsKey)) {
					found = true;
					break;
				}
			}

			if (!found) {
				selectedPlayerGoodsKey = playerRows.get(0).rowKey();
			}
		}

		int villageRows = villageGoodsRows().size();
		if (villageRows <= 0) {
			selectedVillageGoodsIndex = 0;
		} else {
			selectedVillageGoodsIndex = Math.max(0, Math.min(selectedVillageGoodsIndex, villageRows - 1));
		}
	}

	private List<TradeBoardInventoryEntryView> playerInventoryRows() {
		return hasPlayerInventoryRowsSnapshot
			? playerInventoryRowsSnapshot
			: TradeBoardTrading.playerInventoryRows(playerInventory, menu.settlement());
	}

	private TradeBoardInventoryEntryView selectedPlayerEntry() {
		for (TradeBoardInventoryEntryView entry : playerInventoryRows()) {
			if (entry.rowKey().equals(selectedPlayerGoodsKey)) {
				return entry;
			}
		}

		return null;
	}

	private int selectedPlayerEntryIndex() {
		List<TradeBoardInventoryEntryView> rows = playerInventoryRows();
		for (int index = 0; index < rows.size(); index++) {
			if (rows.get(index).rowKey().equals(selectedPlayerGoodsKey)) {
				return index;
			}
		}

		return -1;
	}

	private List<TradeBoardGoodsView> villageGoodsRows() {
		return TradeBoardTrading.villageGoodsOptions(menu.settlement());
	}

	private List<GoodsTradeOption> paymentOptionsForVillageGoods(TradeBoardGoodsView selected) {
		return hasPlayerGoodsSnapshot
			? TradeBoardTrading.paymentOptionsForVillageGoods(selected, playerGoodsSnapshot, menu.settlement())
			: TradeBoardTrading.paymentOptionsForVillageGoods(selected, playerInventory, menu.settlement());
	}

	private static int donationAmount(TradeBoardInventoryEntryView selected, int donationIndex) {
		if (selected == null || selected.totalCount() <= 0) {
			return 0;
		}

		int bundleSize = Math.max(1, selected.bundleSize());
		return switch (donationIndex) {
			case TradeBoardTrading.DONATE_ONE_INDEX -> 1;
			case TradeBoardTrading.DONATE_BUNDLE_INDEX -> bundleSize > 0 ? Math.min(selected.totalCount(), bundleSize) : 0;
			case TradeBoardTrading.DONATE_ALL_INDEX -> selected.totalCount();
			default -> 0;
		};
	}

	private static int availableVillageGoods(TradeBoardGoodsView goods) {
		return Math.max(0, goods.current() - goods.target());
	}

	private PlayerGoodsOption tradeOptionForRow(TradeBoardInventoryEntryView entry) {
		if (entry == null || !entry.hasTradeGoodsKey()) {
			return null;
		}

		return new PlayerGoodsOption(
			entry.tradeGoodsKey(),
			entry.label(),
			entry.totalCount(),
			entry.wantedAmount(),
			entry.bundleSize(),
			entry.tradePricePercent(),
			entry.wanted(),
			entry.canOfferBundle()
		);
	}

	private ItemStack representativeStack(TradeBoardInventoryEntryView entry) {
		if (entry == null) {
			return ItemStack.EMPTY;
		}

		if (entry.hasStoredContents()) {
			int slotIndex = entry.slotIndex();
			if (slotIndex >= 0 && slotIndex < playerInventory.getContainerSize()) {
				ItemStack stack = playerInventory.getItem(slotIndex);
				if (!stack.isEmpty()) {
					return stack;
				}
			}
		}

		return TradeBoardTradeRules.createGoodsStack(entry.exactItemKey(), 1);
	}

	private int detailButtonX() {
		return leftPos + RIGHT_COLUMN_X + DETAIL_SECTION_WIDTH - ACTION_BUTTON_WIDTH - 2;
	}

	private static int detailOptionRowY(int index) {
		return DETAIL_FIRST_ROW_Y + index * TRADE_ROW_SPACING;
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
		if (x == WIDE_SECTION_X) {
			return imageWidth - 20;
		}

		return x >= RIGHT_COLUMN_X ? DETAIL_SECTION_WIDTH : GOODS_ROW_WIDTH;
	}

	private record VillageGoodsEntryView(
		int goodsIndex,
		TradeBoardGoodsView goods
	) {
	}

	private final class PlayerInventoryListWidget extends ObjectSelectionList<PlayerInventoryListEntry> {
		private PlayerInventoryListWidget(net.minecraft.client.Minecraft minecraft) {
			super(minecraft, GOODS_LIST_WIDTH, GOODS_LIST_HEIGHT, topPos + GOODS_LIST_TOP_Y, GOODS_LIST_ITEM_HEIGHT);
			centerListVertically = false;
			updateBounds();
		}

		private void updateBounds() {
			updateSizeAndPosition(GOODS_LIST_WIDTH, GOODS_LIST_HEIGHT, leftPos + LEFT_COLUMN_X - 2, topPos + GOODS_LIST_TOP_Y);
		}

		private void rebuild(List<TradeBoardInventoryEntryView> entries, String selectedKey) {
			updateBounds();
			replaceEntries(entries.stream().map(PlayerInventoryListEntry::new).toList());

			for (PlayerInventoryListEntry entry : children()) {
				if (entry.view.rowKey().equals(selectedKey)) {
					setSelected(entry);
					break;
				}
			}
		}

		private void selectKey(String rowKey) {
			if (rowKey == null) {
				return;
			}

			for (PlayerInventoryListEntry entry : children()) {
				if (entry.view.rowKey().equals(rowKey)) {
					setSelected(entry);
					scrollToEntry(entry);
					break;
				}
			}
		}

		private PlayerInventoryListEntry hoveredEntry() {
			return getHovered();
		}

		@Override
		public int getRowWidth() {
			return GOODS_ROW_WIDTH;
		}

		@Override
		protected int scrollBarX() {
			return leftPos + LEFT_COLUMN_X + GOODS_ROW_WIDTH + 4;
		}

		@Override
		protected void extractListBackground(GuiGraphicsExtractor graphics) {
		}

		@Override
		protected void extractListSeparators(GuiGraphicsExtractor graphics) {
		}
	}

	private final class VillageGoodsListWidget extends ObjectSelectionList<VillageGoodsListEntry> {
		private VillageGoodsListWidget(net.minecraft.client.Minecraft minecraft) {
			super(minecraft, GOODS_LIST_WIDTH, GOODS_LIST_HEIGHT, topPos + GOODS_LIST_TOP_Y, GOODS_LIST_ITEM_HEIGHT);
			centerListVertically = false;
			updateBounds();
		}

		private void updateBounds() {
			updateSizeAndPosition(GOODS_LIST_WIDTH, GOODS_LIST_HEIGHT, leftPos + LEFT_COLUMN_X - 2, topPos + GOODS_LIST_TOP_Y);
		}

		private void rebuild(List<TradeBoardGoodsView> entries, int selectedIndex) {
			updateBounds();
			List<VillageGoodsEntryView> rows = new ArrayList<>();
			for (int index = 0; index < entries.size(); index++) {
				rows.add(new VillageGoodsEntryView(index, entries.get(index)));
			}
			replaceEntries(rows.stream().map(VillageGoodsListEntry::new).toList());

			for (VillageGoodsListEntry entry : children()) {
				if (entry.view.goodsIndex() == selectedIndex) {
					setSelected(entry);
					break;
				}
			}
		}

		private void selectIndex(int goodsIndex) {
			for (VillageGoodsListEntry entry : children()) {
				if (entry.view.goodsIndex() == goodsIndex) {
					setSelected(entry);
					scrollToEntry(entry);
					break;
				}
			}
		}

		@Override
		public int getRowWidth() {
			return GOODS_ROW_WIDTH;
		}

		@Override
		protected int scrollBarX() {
			return leftPos + LEFT_COLUMN_X + GOODS_ROW_WIDTH + 4;
		}

		@Override
		protected void extractListBackground(GuiGraphicsExtractor graphics) {
		}

		@Override
		protected void extractListSeparators(GuiGraphicsExtractor graphics) {
		}
	}

	private final class PlayerInventoryListEntry extends ObjectSelectionList.Entry<PlayerInventoryListEntry> {
		private final TradeBoardInventoryEntryView view;

		private PlayerInventoryListEntry(TradeBoardInventoryEntryView view) {
			this.view = view;
		}

		@Override
		public Component getNarration() {
			return Component.literal(view.label() + " x" + view.totalCount());
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
			int x = getContentX() + 4;
			int y = getContentY() + 2;
			PlayerGoodsOption tradeOption = tradeOptionForRow(view);

			drawItemStack(graphics, representativeStack(view), x, y);
			graphics.text(font, trimToWidth(view.label() + " x" + view.totalCount(), GOODS_ROW_WIDTH - 24), x + 19, y + 2, tradeOption != null && tradeOption.wanted() ? 0xFFE8DDC8 : 0xFF9F8E72, false);

			String note = view.hasStoredContents()
				? "Stored contents ready to donate"
				: tradeOption != null
				? tradeOption.wanted()
					? "Needs " + tradeOption.wantedAmount() + " more"
					: "Not currently wanted"
				: "Unknown to settlement pricing";
			int noteColor = view.hasStoredContents()
				? 0xFFDCC8A4
				: tradeOption != null && tradeOption.wanted() ? 0xFFDCC8A4 : 0xFF806F5A;
			graphics.text(font, trimToWidth(note, GOODS_ROW_WIDTH - 24), x + 19, y + 10, noteColor, false);
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			if (event.button() != 0) {
				return false;
			}

			selectPlayerGoods(view.rowKey());
			return true;
		}
	}

	private final class VillageGoodsListEntry extends ObjectSelectionList.Entry<VillageGoodsListEntry> {
		private final VillageGoodsEntryView view;

		private VillageGoodsListEntry(VillageGoodsEntryView view) {
			this.view = view;
		}

		@Override
		public Component getNarration() {
			String label = TradeBoardTradeRules.compactLabel(view.goods().goodsKey(), view.goods().label());
			return Component.literal(label + " x" + view.goods().tradeBundleSize());
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
			int x = getContentX() + 4;
			int y = getContentY() + 2;
			TradeBoardGoodsView goods = view.goods();
			String label = TradeBoardTradeRules.compactLabel(goods.goodsKey(), goods.label());

			drawGoodsIcon(graphics, goods.goodsKey(), x, y, Math.min(64, goods.current()));
			graphics.text(font, trimToWidth(label + " x" + goods.tradeBundleSize(), GOODS_ROW_WIDTH - 24), x + 19, y + 2, 0xFFE8DDC8, false);
			graphics.text(font, trimToWidth("Spare " + availableVillageGoods(goods) + ", keeps " + goods.target(), GOODS_ROW_WIDTH - 24), x + 19, y + 10, 0xFFDCC8A4, false);
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			if (event.button() != 0) {
				return false;
			}

			selectVillageGoods(view.goodsIndex());
			return true;
		}
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
