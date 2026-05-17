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
	private static final int BOUNTY_INFO_X = 12;
	private static final int BOUNTY_INFO_WIDTH = 212;
	private static final int BOUNTY_LIST_LEFT_X = 12;
	private static final int BOUNTY_LIST_RIGHT_X = 122;
	private static final int BOUNTY_LIST_COLUMN_WIDTH = 98;
	private static final int BOUNTY_DETAIL_X = 232;
	private static final int BOUNTY_DETAIL_WIDTH = SCREEN_WIDTH - BOUNTY_DETAIL_X - 10;
	private static final int BOUNTY_ROW_HEIGHT = 14;
	private static final int BOUNTY_LIST_Y = 92;
	private static final int BOUNTY_DETAIL_Y = 68;
	private static final int WRAPPED_TEXT_LINE_HEIGHT = 10;
	private static Tab lastActiveTab = Tab.OVERVIEW;
	private static final Component BOUNTY_LABEL = Component.literal("Settlement Bounties");
	private static final Component BOUNTY_HINT = Component.literal("Hover a wanted good to see how much the settlement still needs.");
	private static final Component BOUNTY_TRADE_NOTE = Component.literal("Use Trade to donate goods the settlement currently wants.");

	private final Inventory playerInventory;
	private Tab activeTab = lastActiveTab;
	private String selectedPlayerGoodsKey;
	private boolean hasPlayerInventoryRowsSnapshot;
	private List<TradeBoardInventoryEntryView> playerInventoryRowsSnapshot = List.of();
	private String feedbackMessage = "";
	private int feedbackColor = 0xFF9F8E72;
	private PlayerInventoryListWidget playerInventoryList;

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

		if (activeTab.usesTradeDivider()) {
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

		if (!activeTab.hidesTopSummary()) {
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
			case TRADE -> drawTradeTab(graphics, settlement);
			case BOUNTIES -> drawBountiesTab(graphics, mouseX, mouseY, settlement);
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
		if (activeTab == Tab.TRADE && playerInventoryList != null && playerInventoryList.isMouseOver(mouseX, mouseY)) {
			if (playerInventoryList.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
				return true;
			}
		}

		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);

		if (activeTab != Tab.TRADE || playerInventoryList == null) {
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

		if (activeTab == Tab.TRADE) {
			rebuildPlayerInventoryList();
			addPlayerGoodsButtons();
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

	private void drawTradeTab(GuiGraphicsExtractor graphics, TradeBoardSettlementView settlement) {
		graphics.text(font, "Trade", LEFT_COLUMN_X, TRADE_SECTION_TOP_Y, 0xFFF2CF84, false);
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

	private void drawBountiesTab(GuiGraphicsExtractor graphics, int mouseX, int mouseY, TradeBoardSettlementView settlement) {
		List<TradeBoardGoodsView> shortages = settlement.shortages();
		graphics.text(font, BOUNTY_LABEL, BOUNTY_INFO_X, TRADE_SECTION_TOP_Y, 0xFFF2CF84, false);

		if (shortages.isEmpty()) {
			graphics.fill(8, TRADE_SECTION_TOP_Y + 18, imageWidth - 10, imageHeight - 10, 0x11FFF5E6);
			int infoBottom = drawWrappedText(graphics, BOUNTY_TRADE_NOTE, BOUNTY_INFO_X, TRADE_SECTION_TOP_Y + 14, 224, 0xFFDCC8A4);
			drawWrappedText(graphics, Component.literal("No missing settlement goods right now."), BOUNTY_INFO_X, infoBottom + 10, 224, 0xFFE8DDC8);
			drawWrappedText(graphics, Component.literal("Current stock already covers every known reserve target."), BOUNTY_INFO_X, infoBottom + 24, 224, 0xFF9F8E72);
			drawFeedback(graphics);
			return;
		}

		graphics.fill(8, TRADE_SECTION_TOP_Y + 4, 224, imageHeight - 10, 0x11FFF5E6);
		graphics.fill(228, TRADE_SECTION_TOP_Y + 4, imageWidth - 10, imageHeight - 10, 0x22FFF5E6);
		graphics.outline(8, 80, 216, imageHeight - 90, 0x447A633F);
		graphics.outline(228, TRADE_SECTION_TOP_Y + 4, imageWidth - 238, imageHeight - TRADE_SECTION_TOP_Y - 14, 0x447A633F);
		drawWrappedText(graphics, BOUNTY_TRADE_NOTE, BOUNTY_INFO_X, TRADE_SECTION_TOP_Y + 14, BOUNTY_INFO_WIDTH, 0xFFDCC8A4);

		TradeBoardGoodsView hoveredBounty = hoveredBounty(mouseX, mouseY, shortages);
		for (int index = 0; index < shortages.size(); index++) {
			TradeBoardGoodsView bounty = shortages.get(index);
			int columnX = bountyColumnX(index, shortages.size());
			int rowY = bountyRowY(index, shortages.size());
			drawBountyEntry(graphics, columnX, rowY, bounty, bounty.equals(hoveredBounty));
		}

		if (hoveredBounty == null) {
			drawWrappedText(graphics, BOUNTY_HINT, BOUNTY_DETAIL_X, BOUNTY_DETAIL_Y, BOUNTY_DETAIL_WIDTH, 0xFFDCC8A4);
			drawFeedback(graphics);
			return;
		}

		List<Component> detailLines = bountyDetailLines(hoveredBounty, settlement);
		int detailY = BOUNTY_DETAIL_Y;
		for (int index = 0; index < detailLines.size(); index++) {
			Component line = detailLines.get(index);
			int color = index == 0 ? 0xFFE8DDC8 : 0xFFDCC8A4;
			detailY = drawWrappedText(graphics, line, BOUNTY_DETAIL_X, detailY, BOUNTY_DETAIL_WIDTH, color) + 2;
		}

		drawFeedback(graphics);
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

	private void drawBountyEntry(GuiGraphicsExtractor graphics, int x, int y, TradeBoardGoodsView bounty, boolean hovered) {
		graphics.fill(x - 2, y - 1, x + BOUNTY_LIST_COLUMN_WIDTH, y + 12, hovered ? 0x553E6A3B : 0x22FFF5E6);
		graphics.outline(x - 2, y - 1, BOUNTY_LIST_COLUMN_WIDTH + 2, 13, hovered ? 0xFF9BC87D : 0x447A633F);
		drawGoodsIcon(graphics, bounty.goodsKey(), x, y - 2, Math.max(1, bounty.current()));
		String label = TradeBoardTradeRules.compactLabel(bounty.goodsKey(), bounty.label());
		graphics.text(font, trimToWidth(label, BOUNTY_LIST_COLUMN_WIDTH - 24), x + 18, y + 1, 0xFFE8DDC8, false);
	}

	private TradeBoardGoodsView hoveredBounty(int mouseX, int mouseY, List<TradeBoardGoodsView> shortages) {
		for (int index = 0; index < shortages.size(); index++) {
			int x = leftPos + bountyColumnX(index, shortages.size()) - 2;
			int y = topPos + bountyRowY(index, shortages.size()) - 1;
			if (mouseX >= x && mouseX < x + BOUNTY_LIST_COLUMN_WIDTH + 2 && mouseY >= y && mouseY < y + 13) {
				return shortages.get(index);
			}
		}

		return null;
	}

	private int bountyColumnX(int index, int totalBounties) {
		int rowsPerColumn = Math.max(1, (totalBounties + 1) / 2);
		return index < rowsPerColumn ? BOUNTY_LIST_LEFT_X : BOUNTY_LIST_RIGHT_X;
	}

	private int bountyRowY(int index, int totalBounties) {
		int rowsPerColumn = Math.max(1, (totalBounties + 1) / 2);
		int row = index % rowsPerColumn;
		return BOUNTY_LIST_Y + row * BOUNTY_ROW_HEIGHT;
	}

	private List<Component> bountyDetailLines(TradeBoardGoodsView bounty, TradeBoardSettlementView settlement) {
		List<Component> lines = new ArrayList<>();
		String label = TradeBoardTradeRules.compactLabel(bounty.goodsKey(), bounty.label());
		int shortageAmount = Math.max(0, bounty.target() - bounty.current());
		lines.add(Component.literal("Needs " + shortageAmount + " more " + label.toLowerCase() + "."));
		lines.add(Component.literal("Stock: " + bounty.current() + " on hand, target reserve " + bounty.target() + "."));
		if (bounty.tradeBundleSize() > 0) {
			lines.add(Component.literal("Trade counts bundles of " + bounty.tradeBundleSize() + " " + label.toLowerCase() + "."));
		}
		if (bounty.tradePricePercent() > 100) {
			lines.add(Component.literal("This shortage pays " + bounty.tradePricePercent() + "% of base trade value."));
		} else {
			lines.add(Component.literal(settlement.settlementName() + " will still take direct donations for this good."));
		}
		return lines;
	}

	private int drawWrappedText(GuiGraphicsExtractor graphics, Component text, int x, int y, int width, int color) {
		int lineY = y;
		for (String line : wrapText(text.getString(), width)) {
			graphics.text(font, Component.literal(line), x, lineY, color, false);
			lineY += WRAPPED_TEXT_LINE_HEIGHT;
		}
		return lineY;
	}

	private List<String> wrapText(String value, int width) {
		if (value.isBlank()) {
			return List.of("");
		}

		List<String> lines = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		for (String word : value.split(" ")) {
			String candidate = current.isEmpty() ? word : current + " " + word;
			if (font.width(candidate) <= width) {
				current.setLength(0);
				current.append(candidate);
				continue;
			}

			if (!current.isEmpty()) {
				lines.add(current.toString());
				current.setLength(0);
			}

			if (font.width(word) <= width) {
				current.append(word);
				continue;
			}

			lines.add(trimToWidth(word, width));
		}

		if (!current.isEmpty()) {
			lines.add(current.toString());
		}

		return lines;
	}

	private enum Tab {
		OVERVIEW("Overview"),
		TRADE("Trade"),
		BOUNTIES("Bounties"),
		ROUTES("Routes");

		private final String label;

		Tab(String label) {
			this.label = label;
		}

		private boolean usesTradeDivider() {
			return this == TRADE;
		}

		private boolean hidesTopSummary() {
			return this == TRADE || this == BOUNTIES;
		}
	}
}
