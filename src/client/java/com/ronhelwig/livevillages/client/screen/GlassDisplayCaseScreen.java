package com.ronhelwig.livevillages.client.screen;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.ronhelwig.livevillages.menu.BakeryBountyView;
import com.ronhelwig.livevillages.menu.GlassDisplayCaseMenu;
import com.ronhelwig.livevillages.menu.TradeBoardTradeRules;

public class GlassDisplayCaseScreen extends AbstractContainerScreen<GlassDisplayCaseMenu> {
	private static final int SCREEN_WIDTH = 372;
	private static final int SCREEN_HEIGHT = 210;
	private static final int TAB_BAR_Y = 22;
	private static final int TAB_BUTTON_WIDTH = 64;
	private static final int TAB_BUTTON_HEIGHT = 16;
	private static final int STOCK_SECTION_BOTTOM = 110;
	private static final int INVENTORY_SECTION_TOP = 112;
	private static final int ACTION_PANEL_X = 224;
	private static final int ACTION_PANEL_Y = 24;
	private static final int ACTION_PANEL_WIDTH = 136;
	private static final int ACTION_PANEL_HEIGHT = 84;
	private static final int ACTION_BUTTON_WIDTH = 120;
	private static final int SPLIT_ACTION_BUTTON_WIDTH = 58;
	private static final int ACTION_BUTTON_HEIGHT = 16;
	private static final int INFO_X = 12;
	private static final int INFO_WIDTH = 116;
	private static final int INFO_TITLE_Y = 42;
	private static final int INFO_BODY_Y = 56;
	private static final int INFO_LINE_HEIGHT = 10;
	private static final int BOUNTY_INFO_X = 12;
	private static final int BOUNTY_INFO_WIDTH = 228;
	private static final int BOUNTY_LIST_LEFT_X = 12;
	private static final int BOUNTY_LIST_RIGHT_X = 122;
	private static final int BOUNTY_LIST_COLUMN_WIDTH = 98;
	private static final int BOUNTY_DETAIL_X = 232;
	private static final int BOUNTY_DETAIL_WIDTH = 128;
	private static final int BOUNTY_ROW_HEIGHT = 14;
	private static final int BOUNTY_LIST_Y = 92;
	private static final int BOUNTY_DETAIL_Y = 42;
	private static final Component STORAGE_LABEL = Component.literal("Storage");
	private static final Component STORAGE_NOTE_1 = Component.literal("Standalone cases work like");
	private static final Component STORAGE_NOTE_2 = Component.literal("a 6-slot container.");
	private static final Component STOCK_LABEL = Component.literal("Sale Stock");
	private static final Component DONATE_NOTE_1 = Component.literal("Donate into empty slots");
	private static final Component DONATE_NOTE_2 = Component.literal("or matching stacks.");
	private static final Component DONATE_NOTE_3 = Component.literal("Left click fills stack.");
	private static final Component DONATE_NOTE_4 = Component.literal("Right click adds one.");
	private static final Component ACTION_LABEL = Component.literal("Selected Trade");
	private static final Component ACTION_HINT = Component.literal("Hover a stocked slot to trade.");
	private static final Component BOUNTY_LABEL = Component.literal("Ingredient Bounties");
	private static final Component BOUNTY_HINT = Component.literal("Hover an ingredient to see what the baker could make with it.");
	private static final Component BOUNTY_SHOP_NOTE = Component.literal("Use Shop to donate useful bakery ingredients.");

	private Button shopTabButton;
	private Button bountyTabButton;
	private Button stackBarterButton;
	private Button singleBarterButton;
	private Button emeraldButton;
	private int focusedSlot = -1;
	private Tab activeTab = Tab.SHOP;
	private final SettlementScreenTheme theme;

	public GlassDisplayCaseScreen(GlassDisplayCaseMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, SCREEN_WIDTH, SCREEN_HEIGHT);
		this.theme = SettlementScreenTheme.forTier(menu.settlementTier());
		this.inventoryLabelX = GlassDisplayCaseMenu.PLAYER_INV_X;
		this.inventoryLabelY = 112;
	}

	@Override
	protected void init() {
		super.init();

		if (!menu.bakeryContext()) {
			activeTab = Tab.SHOP;
		}

		addTabButtons();
		rebuildButtons();
		applyActiveTabLayout();
	}

	@Override
	public void containerTick() {
		super.containerTick();
		refreshButtons();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		extractTransparentBackground(graphics);
		updateFocusedSlot(mouseX, mouseY);

		graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, theme.overlay());
		graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + 18, theme.header());
		if (activeTab == Tab.BOUNTIES && menu.bakeryContext()) {
			graphics.fill(leftPos + 1, topPos + 19, leftPos + imageWidth - 1, topPos + imageHeight - 1, theme.body());
		} else {
			graphics.fill(leftPos + 1, topPos + 19, leftPos + imageWidth - 1, topPos + STOCK_SECTION_BOTTOM, theme.body());
			graphics.fill(leftPos + 1, topPos + STOCK_SECTION_BOTTOM + 1, leftPos + imageWidth - 1, topPos + INVENTORY_SECTION_TOP - 1, theme.divider());
			graphics.fill(leftPos + 1, topPos + INVENTORY_SECTION_TOP, leftPos + imageWidth - 1, topPos + imageHeight - 1, theme.body());
		}
		graphics.outline(leftPos, topPos, imageWidth, imageHeight, theme.border());

		if (activeTab != Tab.BOUNTIES || !menu.bakeryContext()) {
			graphics.fill(leftPos + 10, topPos + 22, leftPos + 144, topPos + STOCK_SECTION_BOTTOM - 8, theme.panelFill());
			graphics.fill(
				leftPos + GlassDisplayCaseMenu.CASE_START_X - 8,
				topPos + 22,
				leftPos + 216,
				topPos + STOCK_SECTION_BOTTOM - 8,
				theme.panelStrongFill()
			);
			if (menu.bakeryContext()) {
				graphics.fill(
					leftPos + ACTION_PANEL_X,
					topPos + ACTION_PANEL_Y,
					leftPos + ACTION_PANEL_X + ACTION_PANEL_WIDTH,
					topPos + ACTION_PANEL_Y + ACTION_PANEL_HEIGHT,
					theme.panelStrongFill()
				);
				graphics.outline(
					leftPos + ACTION_PANEL_X,
					topPos + ACTION_PANEL_Y,
					ACTION_PANEL_WIDTH,
					ACTION_PANEL_HEIGHT,
					theme.panelOutline()
				);
			}

			for (int slot = 0; slot < GlassDisplayCaseMenu.CASE_SLOT_COUNT; slot++) {
				int column = slot % GlassDisplayCaseMenu.CASE_COLUMNS;
				int row = slot / GlassDisplayCaseMenu.CASE_COLUMNS;
				int x = leftPos + GlassDisplayCaseMenu.CASE_START_X + column * GlassDisplayCaseMenu.SLOT_SPACING;
				int y = topPos + GlassDisplayCaseMenu.CASE_START_Y + row * GlassDisplayCaseMenu.SLOT_SPACING;
				drawSlotFrame(graphics, x, y, slot == focusedSlot);
			}
		}

		refreshButtons();
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(font, title, titleLabelX, titleLabelY, theme.titleText(), false);

		if (activeTab == Tab.BOUNTIES && menu.bakeryContext()) {
			drawBountiesTab(graphics, mouseX, mouseY);
			return;
		}

		graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, theme.secondaryText(), false);
		if (!menu.bakeryContext()) {
			graphics.text(font, STORAGE_LABEL, INFO_X, INFO_TITLE_Y, theme.accentText(), false);
			int nextInfoY = drawWrappedText(graphics, STORAGE_NOTE_1, INFO_X, INFO_BODY_Y, INFO_WIDTH, theme.bodyText());
			drawWrappedText(graphics, STORAGE_NOTE_2, INFO_X, nextInfoY + 2, INFO_WIDTH, theme.bodyText());
			return;
		}

		graphics.text(font, STOCK_LABEL, INFO_X, INFO_TITLE_Y, theme.accentText(), false);
		int nextInfoY = drawWrappedText(graphics, DONATE_NOTE_1, INFO_X, INFO_BODY_Y, INFO_WIDTH, theme.bodyText());
		nextInfoY = drawWrappedText(graphics, DONATE_NOTE_2, INFO_X, nextInfoY + 2, INFO_WIDTH, theme.bodyText());
		nextInfoY = drawWrappedText(graphics, DONATE_NOTE_3, INFO_X, nextInfoY + 2, INFO_WIDTH, theme.bodyText());
		drawWrappedText(graphics, DONATE_NOTE_4, INFO_X, nextInfoY + 2, INFO_WIDTH, theme.secondaryText());
		graphics.text(font, ACTION_LABEL, ACTION_PANEL_X + 8, ACTION_PANEL_Y + 6, theme.accentText(), false);

		ItemStack selectedStack = selectedStack();
		if (selectedStack.isEmpty()) {
			graphics.text(font, ACTION_HINT, ACTION_PANEL_X + 8, ACTION_PANEL_Y + 22, theme.secondaryText(), false);
			return;
		}

		graphics.text(font, selectedStack.getHoverName(), ACTION_PANEL_X + 8, ACTION_PANEL_Y + 22, theme.bodyText(), false);
		graphics.text(font, Component.literal("Stack: " + selectedStack.getCount()), ACTION_PANEL_X + 8, ACTION_PANEL_Y + 32, theme.secondaryText(), false);
		if (emeraldButton != null && emeraldButton.visible) {
			graphics.item(new ItemStack(Items.EMERALD), ACTION_PANEL_X + 14, ACTION_PANEL_Y + 66);
		}
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);

		if (activeTab == Tab.BOUNTIES && menu.bakeryContext()) {
			BakeryBountyView hoveredBounty = hoveredBounty(mouseX, mouseY);
			if (hoveredBounty != null) {
				graphics.setTooltipForNextFrame(font, bountyTooltipLines(hoveredBounty), mouseX, mouseY);
			}
			return;
		}

		for (int slot = 0; slot < GlassDisplayCaseMenu.CASE_SLOT_COUNT; slot++) {
			ItemStack stack = menu.caseStack(slot);
			if (stack.isEmpty()) {
				continue;
			}

			int column = slot % GlassDisplayCaseMenu.CASE_COLUMNS;
			int row = slot / GlassDisplayCaseMenu.CASE_COLUMNS;
			int x = leftPos + GlassDisplayCaseMenu.CASE_START_X + column * GlassDisplayCaseMenu.SLOT_SPACING;
			int y = topPos + GlassDisplayCaseMenu.CASE_START_Y + row * GlassDisplayCaseMenu.SLOT_SPACING;
			if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
				graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
				return;
			}
		}
	}

	private void addTabButtons() {
		if (!menu.bakeryContext()) {
			return;
		}

		shopTabButton = Button.builder(Component.literal(Tab.SHOP.label), ignored -> switchTab(Tab.SHOP))
			.bounds(leftPos + 12, topPos + TAB_BAR_Y, TAB_BUTTON_WIDTH, TAB_BUTTON_HEIGHT)
			.build();
		addRenderableWidget(shopTabButton);

		bountyTabButton = Button.builder(Component.literal(Tab.BOUNTIES.label), ignored -> switchTab(Tab.BOUNTIES))
			.bounds(leftPos + 12 + TAB_BUTTON_WIDTH + 6, topPos + TAB_BAR_Y, TAB_BUTTON_WIDTH, TAB_BUTTON_HEIGHT)
			.build();
		addRenderableWidget(bountyTabButton);

		refreshTabButtons();
	}

	private void switchTab(Tab tab) {
		if (!menu.bakeryContext()) {
			activeTab = Tab.SHOP;
			return;
		}

		activeTab = tab;
		applyActiveTabLayout();
		refreshTabButtons();
		refreshButtons();
	}

	private void applyActiveTabLayout() {
		menu.setBountiesViewActive(activeTab == Tab.BOUNTIES && menu.bakeryContext());
	}

	private void refreshTabButtons() {
		if (shopTabButton != null) {
			shopTabButton.active = activeTab != Tab.SHOP;
		}

		if (bountyTabButton != null) {
			bountyTabButton.active = activeTab != Tab.BOUNTIES;
		}
	}

	private void rebuildButtons() {
		if (!menu.bakeryContext()) {
			return;
		}

		stackBarterButton = Button.builder(Component.literal("Stack"), ignored -> {
			int slot = actionableSlot();
			int buttonId = GlassDisplayCaseMenu.STACK_BARTER_BUTTON_ID_BASE + slot;
			if (slot >= 0 && minecraft != null && minecraft.gameMode != null && minecraft.player != null && menu.clickMenuButton(minecraft.player, buttonId)) {
				minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
			}
		}).bounds(leftPos + ACTION_PANEL_X + 8, topPos + ACTION_PANEL_Y + 46, SPLIT_ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT).build();
		addRenderableWidget(stackBarterButton);

		singleBarterButton = Button.builder(Component.literal("Single"), ignored -> {
			int slot = actionableSlot();
			int buttonId = menu.hasFreeClaimAvailable(slot)
				? GlassDisplayCaseMenu.FREE_CLAIM_BUTTON_ID_BASE + slot
				: GlassDisplayCaseMenu.SINGLE_BARTER_BUTTON_ID_BASE + slot;
			if (slot >= 0 && minecraft != null && minecraft.gameMode != null && minecraft.player != null && menu.clickMenuButton(minecraft.player, buttonId)) {
				minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
			}
		}).bounds(leftPos + ACTION_PANEL_X + 70, topPos + ACTION_PANEL_Y + 46, SPLIT_ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT).build();
		addRenderableWidget(singleBarterButton);

		emeraldButton = Button.builder(Component.literal("Buy"), ignored -> {
			int slot = actionableSlot();
			if (slot >= 0 && minecraft != null && minecraft.gameMode != null && minecraft.player != null && menu.clickMenuButton(minecraft.player, slot)) {
				minecraft.gameMode.handleInventoryButtonClick(menu.containerId, slot);
			}
		}).bounds(leftPos + ACTION_PANEL_X + 8, topPos + ACTION_PANEL_Y + 66, ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT).build();
		addRenderableWidget(emeraldButton);

		refreshButtons();
	}

	private void refreshButtons() {
		if (stackBarterButton == null || singleBarterButton == null || emeraldButton == null) {
			return;
		}

		if (activeTab != Tab.SHOP) {
			stackBarterButton.visible = false;
			singleBarterButton.visible = false;
			emeraldButton.visible = false;
			return;
		}

		normalizeFocusedSlot();
		ItemStack selectedStack = selectedStack();
		boolean hasStack = !selectedStack.isEmpty();
		int slot = actionableSlot();
		boolean hasStackBarter = slot >= 0 && menu.stackBarterOfferForSlot(slot) != null;
		boolean hasSingleBarter = slot >= 0 && (menu.hasFreeClaimAvailable(slot) || menu.singleBarterOfferForSlot(slot) != null);

		stackBarterButton.visible = hasStack && hasStackBarter;
		stackBarterButton.active = hasStack && hasStackBarter;
		if (hasStackBarter) {
			stackBarterButton.setMessage(menu.stackBarterButtonLabel(slot));
		}

		singleBarterButton.visible = hasStack && hasSingleBarter;
		singleBarterButton.active = hasStack && hasSingleBarter;
		if (hasSingleBarter) {
			singleBarterButton.setMessage(menu.singleBarterButtonLabel(slot));
		}

		emeraldButton.visible = hasStack;
		emeraldButton.active = hasStack;
		if (hasStack) {
			emeraldButton.setMessage(menu.emeraldButtonLabel(slot));
		}
	}

	private void updateFocusedSlot(int mouseX, int mouseY) {
		int hoveredSlot = hoveredCaseSlot(mouseX, mouseY);
		if (hoveredSlot >= 0 && !menu.caseStack(hoveredSlot).isEmpty()) {
			focusedSlot = hoveredSlot;
			return;
		}

		normalizeFocusedSlot();
	}

	private void normalizeFocusedSlot() {
		if (focusedSlot >= 0 && !menu.caseStack(focusedSlot).isEmpty()) {
			return;
		}

		focusedSlot = firstStockedSlot();
	}

	private int actionableSlot() {
		normalizeFocusedSlot();
		return focusedSlot;
	}

	private ItemStack selectedStack() {
		int slot = actionableSlot();
		return slot < 0 ? ItemStack.EMPTY : menu.caseStack(slot);
	}

	private int firstStockedSlot() {
		for (int slot = 0; slot < GlassDisplayCaseMenu.CASE_SLOT_COUNT; slot++) {
			if (!menu.caseStack(slot).isEmpty()) {
				return slot;
			}
		}

		return -1;
	}

	private int hoveredCaseSlot(int mouseX, int mouseY) {
		for (int slot = 0; slot < GlassDisplayCaseMenu.CASE_SLOT_COUNT; slot++) {
			int column = slot % GlassDisplayCaseMenu.CASE_COLUMNS;
			int row = slot / GlassDisplayCaseMenu.CASE_COLUMNS;
			int x = leftPos + GlassDisplayCaseMenu.CASE_START_X + column * GlassDisplayCaseMenu.SLOT_SPACING;
			int y = topPos + GlassDisplayCaseMenu.CASE_START_Y + row * GlassDisplayCaseMenu.SLOT_SPACING;
			if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
				return slot;
			}
		}

		return -1;
	}

	private void drawBountiesTab(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(font, BOUNTY_LABEL, BOUNTY_INFO_X, INFO_TITLE_Y, theme.accentText(), false);

		if (menu.bakeryBounties().isEmpty()) {
			graphics.fill(8, 22, imageWidth - 10, imageHeight - 10, theme.panelFill());
			int infoBottom = drawWrappedText(graphics, BOUNTY_SHOP_NOTE, BOUNTY_INFO_X, INFO_BODY_Y, BOUNTY_INFO_WIDTH, theme.secondaryText());
			drawWrappedText(graphics, Component.literal("No missing bakery ingredients right now."), BOUNTY_INFO_X, infoBottom + 10, BOUNTY_INFO_WIDTH, theme.bodyText());
			drawWrappedText(graphics, Component.literal("The baker can already reach every unlocked recipe ingredient."), BOUNTY_INFO_X, infoBottom + 26, BOUNTY_INFO_WIDTH, theme.secondaryText());
			return;
		}

		graphics.fill(8, 22, 224, imageHeight - 10, theme.panelFill());
		graphics.fill(228, 22, imageWidth - 10, imageHeight - 10, theme.panelStrongFill());
		graphics.outline(8, 80, 216, imageHeight - 90, theme.panelOutline());
		graphics.outline(228, 22, imageWidth - 238, imageHeight - 32, theme.panelOutline());
		drawWrappedText(graphics, BOUNTY_SHOP_NOTE, BOUNTY_INFO_X, INFO_BODY_Y, BOUNTY_INFO_WIDTH, theme.secondaryText());
		BakeryBountyView hoveredBounty = hoveredBounty(mouseX, mouseY);

		for (int index = 0; index < menu.bakeryBounties().size(); index++) {
			BakeryBountyView bounty = menu.bakeryBounties().get(index);
			int columnX = bountyColumnX(index);
			int rowY = bountyRowY(index);
			boolean hovered = bounty.equals(hoveredBounty);
			drawBountyEntry(graphics, columnX, rowY, bounty, hovered);
		}

		if (hoveredBounty == null) {
			drawWrappedText(graphics, BOUNTY_HINT, BOUNTY_DETAIL_X, BOUNTY_DETAIL_Y, BOUNTY_DETAIL_WIDTH, theme.secondaryText());
			return;
		}

		List<Component> detailLines = bountyTooltip(hoveredBounty);
		int detailY = BOUNTY_DETAIL_Y;
		for (int index = 0; index < detailLines.size(); index++) {
			int color = index == 0 ? theme.bodyText() : theme.secondaryText();
			detailY = drawWrappedText(graphics, detailLines.get(index), BOUNTY_DETAIL_X, detailY, BOUNTY_DETAIL_WIDTH, color) + 2;
		}
	}

	private void drawBountyEntry(GuiGraphicsExtractor graphics, int x, int y, BakeryBountyView bounty, boolean hovered) {
		graphics.fill(x - 2, y - 1, x + BOUNTY_LIST_COLUMN_WIDTH, y + 12, hovered ? theme.selectionFill() : theme.panelStrongFill());
		graphics.outline(x - 2, y - 1, BOUNTY_LIST_COLUMN_WIDTH + 2, 13, hovered ? theme.selectionOutline() : theme.panelOutline());

		ItemStack stack = goodsStack(bounty.goodsKey());
		if (!stack.isEmpty()) {
			graphics.item(stack, x, y - 2);
		}

		String suffix = bounty.unlockOutputGoodsKeys().isEmpty() ? "" : " *";
		graphics.text(font, trimToWidth(goodsLabel(bounty.goodsKey()) + suffix, BOUNTY_LIST_COLUMN_WIDTH - 24), x + 18, y + 1, theme.bodyText(), false);
	}

	private BakeryBountyView hoveredBounty(int mouseX, int mouseY) {
		for (int index = 0; index < menu.bakeryBounties().size(); index++) {
			int x = leftPos + bountyColumnX(index) - 2;
			int y = topPos + bountyRowY(index) - 1;
			if (mouseX >= x && mouseX < x + BOUNTY_LIST_COLUMN_WIDTH + 2 && mouseY >= y && mouseY < y + 13) {
				return menu.bakeryBounties().get(index);
			}
		}

		return null;
	}

	private int bountyColumnX(int index) {
		int rowsPerColumn = Math.max(1, (menu.bakeryBounties().size() + 1) / 2);
		return index < rowsPerColumn ? BOUNTY_LIST_LEFT_X : BOUNTY_LIST_RIGHT_X;
	}

	private int bountyRowY(int index) {
		int rowsPerColumn = Math.max(1, (menu.bakeryBounties().size() + 1) / 2);
		int row = index % rowsPerColumn;
		return BOUNTY_LIST_Y + row * BOUNTY_ROW_HEIGHT;
	}

	private ItemStack goodsStack(String goodsKey) {
		return TradeBoardTradeRules.createGoodsStack(goodsKey, 1);
	}

	private String goodsLabel(String goodsKey) {
		ItemStack stack = goodsStack(goodsKey);
		if (!stack.isEmpty()) {
			return stack.getHoverName().getString();
		}

		return goodsKey.replace('_', ' ');
	}

	private String goodsListLabel(java.util.List<String> goodsKeys) {
		return goodsKeys.stream()
			.map(this::goodsLabel)
			.reduce((first, second) -> first + ", " + second)
			.orElse("nothing");
	}

	private List<Component> bountyTooltip(BakeryBountyView bounty) {
		List<Component> tooltip = new java.util.ArrayList<>();
		if (!bounty.unlockOutputGoodsKeys().isEmpty()) {
			tooltip.add(Component.literal("Could bake now: " + goodsListLabel(bounty.unlockOutputGoodsKeys())));
		} else {
			tooltip.add(Component.literal("Used in unlocked recipes: " + goodsListLabel(bounty.usedInOutputGoodsKeys())));
		}
		if (!bounty.unlockOutputGoodsKeys().isEmpty()
			&& bounty.usedInOutputGoodsKeys().size() > bounty.unlockOutputGoodsKeys().size()) {
			tooltip.add(Component.literal("Also used in: " + goodsListLabel(bounty.usedInOutputGoodsKeys())));
		}
		return tooltip;
	}

	private List<FormattedCharSequence> bountyTooltipLines(BakeryBountyView bounty) {
		List<FormattedCharSequence> lines = new java.util.ArrayList<>();
		for (Component line : bountyTooltip(bounty)) {
			lines.addAll(font.split(line, 180));
		}
		return lines;
	}

	private int drawWrappedText(GuiGraphicsExtractor graphics, Component text, int x, int y, int width, int color) {
		int lineY = y;
		for (String line : wrapText(text.getString(), width)) {
			graphics.text(font, Component.literal(line), x, lineY, color, false);
			lineY += INFO_LINE_HEIGHT;
		}
		return lineY;
	}

	private List<String> wrapText(String value, int width) {
		if (value.isBlank()) {
			return List.of("");
		}

		List<String> lines = new java.util.ArrayList<>();
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

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (activeTab == Tab.BOUNTIES && menu.bakeryContext()) {
			double mouseX = event.x();
			double mouseY = event.y();
			boolean clickedTab = (shopTabButton != null && shopTabButton.isMouseOver(mouseX, mouseY))
				|| (bountyTabButton != null && bountyTabButton.isMouseOver(mouseX, mouseY));
			return clickedTab && super.mouseClicked(event, doubleClick);
		}

		return super.mouseClicked(event, doubleClick);
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

	private void drawSlotFrame(GuiGraphicsExtractor graphics, int x, int y, boolean focused) {
		graphics.fill(x - 1, y - 1, x + 17, y + 17, theme.divider());
		graphics.outline(x - 1, y - 1, 18, 18, focused ? theme.selectionOutline() : theme.panelOutline());
	}

	private enum Tab {
		SHOP("Shop"),
		BOUNTIES("Bounties");

		private final String label;

		Tab(String label) {
			this.label = label;
		}
	}
}
