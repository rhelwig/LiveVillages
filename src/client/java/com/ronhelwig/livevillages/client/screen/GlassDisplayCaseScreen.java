package com.ronhelwig.livevillages.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.ronhelwig.livevillages.menu.GlassDisplayCaseMenu;

public class GlassDisplayCaseScreen extends AbstractContainerScreen<GlassDisplayCaseMenu> {
	private static final int SCREEN_WIDTH = 372;
	private static final int SCREEN_HEIGHT = 210;
	private static final int STOCK_SECTION_BOTTOM = 110;
	private static final int INVENTORY_SECTION_TOP = 112;
	private static final int ACTION_PANEL_X = 224;
	private static final int ACTION_PANEL_Y = 24;
	private static final int ACTION_PANEL_WIDTH = 136;
	private static final int ACTION_PANEL_HEIGHT = 84;
	private static final int ACTION_BUTTON_WIDTH = 120;
	private static final int SPLIT_ACTION_BUTTON_WIDTH = 58;
	private static final int ACTION_BUTTON_HEIGHT = 16;
	private static final Component STOCK_LABEL = Component.literal("Sale Stock");
	private static final Component DONATE_NOTE_1 = Component.literal("Drop items into empty slots");
	private static final Component DONATE_NOTE_2 = Component.literal("to donate them to this display.");
	private static final Component EMPTY_NOTE = Component.literal("Bakery can restock empty slots.");
	private static final Component ACTION_LABEL = Component.literal("Selected Trade");
	private static final Component ACTION_HINT = Component.literal("Hover a stocked slot to trade.");

	private Button stackBarterButton;
	private Button singleBarterButton;
	private Button emeraldButton;
	private int focusedSlot = -1;

	public GlassDisplayCaseScreen(GlassDisplayCaseMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, SCREEN_WIDTH, SCREEN_HEIGHT);
		this.inventoryLabelX = GlassDisplayCaseMenu.PLAYER_INV_X;
		this.inventoryLabelY = 112;
	}

	@Override
	protected void init() {
		super.init();
		rebuildButtons();
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

		graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xEE2B241A);
		graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + 18, 0xFF7A633F);
		graphics.fill(leftPos + 1, topPos + 19, leftPos + imageWidth - 1, topPos + STOCK_SECTION_BOTTOM, 0xFFB39A6A);
		graphics.fill(leftPos + 1, topPos + STOCK_SECTION_BOTTOM + 1, leftPos + imageWidth - 1, topPos + INVENTORY_SECTION_TOP - 1, 0xFF3E3223);
		graphics.fill(leftPos + 1, topPos + INVENTORY_SECTION_TOP, leftPos + imageWidth - 1, topPos + imageHeight - 1, 0xFF251E16);
		graphics.outline(leftPos, topPos, imageWidth, imageHeight, 0xFFD8C497);

		graphics.fill(leftPos + 10, topPos + 22, leftPos + 144, topPos + STOCK_SECTION_BOTTOM - 8, 0x11FFF5E6);
		graphics.fill(
			leftPos + GlassDisplayCaseMenu.CASE_START_X - 8,
			topPos + 22,
			leftPos + 216,
			topPos + STOCK_SECTION_BOTTOM - 8,
			0x22FFF5E6
		);
		graphics.fill(
			leftPos + ACTION_PANEL_X,
			topPos + ACTION_PANEL_Y,
			leftPos + ACTION_PANEL_X + ACTION_PANEL_WIDTH,
			topPos + ACTION_PANEL_Y + ACTION_PANEL_HEIGHT,
			0x22FFF5E6
		);
		graphics.outline(
			leftPos + ACTION_PANEL_X,
			topPos + ACTION_PANEL_Y,
			ACTION_PANEL_WIDTH,
			ACTION_PANEL_HEIGHT,
			0x447A633F
		);

		for (int slot = 0; slot < GlassDisplayCaseMenu.CASE_SLOT_COUNT; slot++) {
			int column = slot % GlassDisplayCaseMenu.CASE_COLUMNS;
			int row = slot / GlassDisplayCaseMenu.CASE_COLUMNS;
			int x = leftPos + GlassDisplayCaseMenu.CASE_START_X + column * GlassDisplayCaseMenu.SLOT_SPACING;
			int y = topPos + GlassDisplayCaseMenu.CASE_START_Y + row * GlassDisplayCaseMenu.SLOT_SPACING;
			drawSlotFrame(graphics, x, y, slot == focusedSlot);
		}

		refreshButtons();
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(font, title, titleLabelX, titleLabelY, 0xFFF5E6C8, false);
		graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFD8C4A0, false);
		graphics.text(font, STOCK_LABEL, 12, 24, 0xFF4E381A, false);
		graphics.text(font, DONATE_NOTE_1, 12, 46, 0xFF4E381A, false);
		graphics.text(font, DONATE_NOTE_2, 12, 56, 0xFF4E381A, false);
		graphics.text(font, EMPTY_NOTE, 12, 72, 0xFF836B43, false);
		graphics.text(font, ACTION_LABEL, ACTION_PANEL_X + 8, ACTION_PANEL_Y + 6, 0xFF4E381A, false);

		ItemStack selectedStack = selectedStack();
		if (selectedStack.isEmpty()) {
			graphics.text(font, ACTION_HINT, ACTION_PANEL_X + 8, ACTION_PANEL_Y + 22, 0xFF836B43, false);
			return;
		}

		graphics.text(font, selectedStack.getHoverName(), ACTION_PANEL_X + 8, ACTION_PANEL_Y + 22, 0xFF4E381A, false);
		graphics.text(font, Component.literal("Stack: " + selectedStack.getCount()), ACTION_PANEL_X + 8, ACTION_PANEL_Y + 32, 0xFF836B43, false);
		if (emeraldButton != null && emeraldButton.visible) {
			graphics.item(new ItemStack(Items.EMERALD), ACTION_PANEL_X + 14, ACTION_PANEL_Y + 66);
		}
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);

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

	private void rebuildButtons() {
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
			int buttonId = GlassDisplayCaseMenu.SINGLE_BARTER_BUTTON_ID_BASE + slot;
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

		normalizeFocusedSlot();
		ItemStack selectedStack = selectedStack();
		boolean hasStack = !selectedStack.isEmpty();
		int slot = actionableSlot();
		boolean hasStackBarter = slot >= 0 && menu.stackBarterOfferForSlot(slot) != null;
		boolean hasSingleBarter = slot >= 0 && menu.singleBarterOfferForSlot(slot) != null;

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

	private void drawSlotFrame(GuiGraphicsExtractor graphics, int x, int y, boolean focused) {
		graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF16120F);
		graphics.outline(x - 1, y - 1, 18, 18, focused ? 0xFFF5E6C8 : 0xFF8A6B39);
	}
}
