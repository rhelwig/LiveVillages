package com.ronhelwig.livevillages.client.screen;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import com.ronhelwig.livevillages.menu.GlassDisplayCaseMenu;

public class GlassDisplayCaseScreen extends AbstractContainerScreen<GlassDisplayCaseMenu> {
	private static final int SCREEN_WIDTH = 194;
	private static final int SCREEN_HEIGHT = 166;
	private static final Component BUY_LABEL = Component.literal("Buy");
	private static final Component DONATE_NOTE = Component.literal("Place items into empty slots to donate them to the case.");
	private static final Component EMPTY_NOTE = Component.literal("Empty slots can be stocked by players or by the bakery.");

	private final List<Button> buyButtons = new ArrayList<>();

	public GlassDisplayCaseScreen(GlassDisplayCaseMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, SCREEN_WIDTH, SCREEN_HEIGHT);
		this.inventoryLabelX = GlassDisplayCaseMenu.PLAYER_INV_X;
		this.inventoryLabelY = 74;
	}

	@Override
	protected void init() {
		super.init();
		rebuildButtons();
	}

	@Override
	public void containerTick() {
		super.containerTick();
		updateButtons();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		extractTransparentBackground(graphics);
		graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xEE2B241A);
		graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + 18, 0xFF7A633F);
		graphics.fill(leftPos + 1, topPos + 19, leftPos + imageWidth - 1, topPos + 66, 0xFFB39A6A);
		graphics.fill(leftPos + 1, topPos + 67, leftPos + imageWidth - 1, topPos + 73, 0xFF3E3223);
		graphics.fill(leftPos + 1, topPos + 74, leftPos + imageWidth - 1, topPos + imageHeight - 1, 0xFF251E16);
		graphics.outline(leftPos, topPos, imageWidth, imageHeight, 0xFFD8C497);

		for (int slot = 0; slot < GlassDisplayCaseMenu.CASE_SLOT_COUNT; slot++) {
			int column = slot % GlassDisplayCaseMenu.CASE_COLUMNS;
			int row = slot / GlassDisplayCaseMenu.CASE_COLUMNS;
			int x = leftPos + GlassDisplayCaseMenu.CASE_START_X + column * GlassDisplayCaseMenu.SLOT_SPACING;
			int y = topPos + GlassDisplayCaseMenu.CASE_START_Y + row * GlassDisplayCaseMenu.SLOT_SPACING;
			drawSlotFrame(graphics, x, y);
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(font, title, titleLabelX, titleLabelY, 0xFFF5E6C8, false);
		graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFD8C4A0, false);
		graphics.text(font, "Bakery Display Case", 8, 22, 0xFF4E381A, false);
		graphics.text(font, DONATE_NOTE, 8, 52, 0xFF4E381A, false);
		graphics.text(font, EMPTY_NOTE, 8, 62, 0xFF9D845A, false);

		for (int slot = 0; slot < GlassDisplayCaseMenu.CASE_SLOT_COUNT; slot++) {
			ItemStack stack = menu.caseStack(slot);
			if (stack.isEmpty()) {
				continue;
			}

			int column = slot % GlassDisplayCaseMenu.CASE_COLUMNS;
			int row = slot / GlassDisplayCaseMenu.CASE_COLUMNS;
			int x = GlassDisplayCaseMenu.CASE_START_X + column * GlassDisplayCaseMenu.SLOT_SPACING - 10;
			int y = GlassDisplayCaseMenu.CASE_START_Y + row * GlassDisplayCaseMenu.SLOT_SPACING + 20;
			graphics.text(font, menu.priceForSlot(slot) + "e", x, y, 0xFFF2CF84, false);
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
		buyButtons.clear();

		for (int slot = 0; slot < GlassDisplayCaseMenu.CASE_SLOT_COUNT; slot++) {
			int column = slot % GlassDisplayCaseMenu.CASE_COLUMNS;
			int row = slot / GlassDisplayCaseMenu.CASE_COLUMNS;
			int x = leftPos + 116 + column * 24;
			int y = topPos + 19 + row * 18;
			final int caseSlot = slot;
			Button button = Button.builder(BUY_LABEL, ignored -> {
				if (minecraft != null && minecraft.gameMode != null && minecraft.player != null && menu.clickMenuButton(minecraft.player, caseSlot)) {
					minecraft.gameMode.handleInventoryButtonClick(menu.containerId, caseSlot);
				}
			}).bounds(x, y, 22, 16).build();
			buyButtons.add(button);
			addRenderableWidget(button);
		}

		updateButtons();
	}

	private void updateButtons() {
		for (int slot = 0; slot < buyButtons.size(); slot++) {
			Button button = buyButtons.get(slot);
			ItemStack stack = menu.caseStack(slot);
			button.active = !stack.isEmpty();
			button.visible = !stack.isEmpty();
		}
	}

	private void drawSlotFrame(GuiGraphicsExtractor graphics, int x, int y) {
		graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF16120F);
		graphics.outline(x - 1, y - 1, 18, 18, 0xFF8A6B39);
	}
}
