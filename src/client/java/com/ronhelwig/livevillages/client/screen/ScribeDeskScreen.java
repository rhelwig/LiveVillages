package com.ronhelwig.livevillages.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.ronhelwig.livevillages.menu.ScribeDeskMenu;
import com.ronhelwig.livevillages.menu.ScribeRecipeView;

public class ScribeDeskScreen extends AbstractContainerScreen<ScribeDeskMenu> {
	private static final int SCREEN_WIDTH = 224;
	private static final int SCREEN_HEIGHT = 172;
	private static final int LIST_X = 12;
	private static final int LIST_Y = 28;
	private static final int ROW_WIDTH = 200;
	private static final int ROW_HEIGHT = 22;
	private static final int VISIBLE_ROWS = 5;
	private static final int SCROLL_X = 211;
	private static final int SCROLL_Y = LIST_Y;
	private static final int SCROLL_HEIGHT = ROW_HEIGHT * VISIBLE_ROWS;
	private static final int SCROLL_HANDLE_HEIGHT = 16;
	private static final int LEARN_TAB_X = 112;
	private static final int CONTRIBUTE_TAB_X = 152;
	private static final int TAB_Y = 5;
	private static final int LEARN_TAB_WIDTH = 36;
	private static final int CONTRIBUTE_TAB_WIDTH = 64;
	private static final int TAB_HEIGHT = 14;

	private int startIndex;
	private boolean contributionMode;
	private final Inventory playerInventory;
	private final SettlementScreenTheme theme;

	public ScribeDeskScreen(ScribeDeskMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, SCREEN_WIDTH, SCREEN_HEIGHT);
		this.playerInventory = inventory;
		this.theme = SettlementScreenTheme.forTier(menu.settlementTier());
		this.inventoryLabelY = 1000;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		extractTransparentBackground(graphics);
		graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, theme.overlay());
		graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + 22, theme.header());
		graphics.fill(leftPos + 1, topPos + 24, leftPos + imageWidth - 1, topPos + imageHeight - 1, theme.body());
		graphics.outline(leftPos, topPos, imageWidth, imageHeight, theme.border());
		drawTabs(graphics, mouseX, mouseY);
		drawRecipeRows(graphics, mouseX, mouseY);
		drawScrollbar(graphics, mouseX, mouseY);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Font font = this.font;
		graphics.text(font, title, titleLabelX, titleLabelY, theme.titleText(), false);

		String settlementName = menu.settlementName().isBlank() ? "Settlement" : menu.settlementName();
		graphics.text(font, fitText(settlementName, 98), 12, 16, theme.secondaryText(), false);

		if (activeRecipeCount() == 0) {
			graphics.text(font, contributionMode ? "No player recipes to contribute." : "No unknown settlement recipes.", 14, 43, theme.mutedText(), false);
		}
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);

		int hoveredIndex = hoveredRecipeIndex(mouseX, mouseY);
		if (hoveredIndex < 0) {
			return;
		}

		ScribeRecipeView recipe = activeRecipe(hoveredIndex);
		Component tooltip = contributionMode
			? Component.literal(recipe.label() + " - Reward: +" + ScribeDeskMenu.recipeContributionSupport(recipe) + " support")
			: Component.literal(recipe.label() + " - Cost: " + recipe.paymentCount() + " " + paymentItem(recipe).getHoverName().getString());
		graphics.setTooltipForNextFrame(font, tooltip, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (selectTab((int) event.x(), (int) event.y())) {
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			startIndex = 0;
			return true;
		}

		int hoveredIndex = hoveredRecipeIndex((int) event.x(), (int) event.y());
		int buttonId = hoveredIndex >= 0 ? activeButtonId(hoveredIndex) : -1;

		if (buttonId >= 0 && canUseActiveRecipe(hoveredIndex) && menu.clickMenuButton(minecraft.player, buttonId)) {
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
			removeActiveRecipeAt(hoveredIndex);
			startIndex = Math.min(startIndex, maxStartIndex());
			return true;
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
			return true;
		}

		if (activeRecipeCount() > VISIBLE_ROWS) {
			startIndex = Mth.clamp(startIndex - (int) Math.signum(verticalAmount), 0, maxStartIndex());
		}

		return true;
	}

	private void drawTabs(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		drawTab(graphics, leftPos + LEARN_TAB_X, topPos + TAB_Y, LEARN_TAB_WIDTH, "Learn", !contributionMode, mouseX, mouseY);
		drawTab(graphics, leftPos + CONTRIBUTE_TAB_X, topPos + TAB_Y, CONTRIBUTE_TAB_WIDTH, "Contribute", contributionMode, mouseX, mouseY);
	}

	private void drawTab(GuiGraphicsExtractor graphics, int x, int y, int width, String label, boolean active, int mouseX, int mouseY) {
		boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + TAB_HEIGHT;
		graphics.fill(x, y, x + width, y + TAB_HEIGHT, active ? theme.selectionFill() : hovered ? theme.panelStrongFill() : theme.panelFill());
		graphics.outline(x, y, width, TAB_HEIGHT, active ? theme.selectionOutline() : theme.panelOutline());
		graphics.text(font, label, x + Math.max(2, (width - font.width(label)) / 2), y + 3, active ? theme.titleText() : theme.secondaryText(), false);

		if (hovered) {
			graphics.requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.POINTING_HAND);
		}
	}

	private boolean selectTab(int mouseX, int mouseY) {
		if (mouseY < topPos + TAB_Y || mouseY >= topPos + TAB_Y + TAB_HEIGHT) {
			return false;
		}

		if (mouseX >= leftPos + LEARN_TAB_X && mouseX < leftPos + LEARN_TAB_X + LEARN_TAB_WIDTH) {
			contributionMode = false;
			return true;
		}

		if (mouseX >= leftPos + CONTRIBUTE_TAB_X && mouseX < leftPos + CONTRIBUTE_TAB_X + CONTRIBUTE_TAB_WIDTH) {
			contributionMode = true;
			return true;
		}

		return false;
	}

	private void drawRecipeRows(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int maxIndex = Math.min(activeRecipeCount(), startIndex + VISIBLE_ROWS);

		for (int index = startIndex; index < maxIndex; index++) {
			ScribeRecipeView recipe = activeRecipe(index);
			int row = index - startIndex;
			int x = leftPos + LIST_X;
			int y = topPos + LIST_Y + row * ROW_HEIGHT;
			boolean hovered = hoveredRecipeIndex(mouseX, mouseY) == index;
			int fill = hovered ? theme.panelStrongFill() : theme.panelFill();
			graphics.fill(x, y, x + ROW_WIDTH, y + ROW_HEIGHT - 1, fill);
			graphics.outline(x, y, ROW_WIDTH, ROW_HEIGHT - 1, hovered ? theme.selectionOutline() : theme.panelOutline());
			graphics.item(outputItem(recipe), x + 4, y + 2);
			graphics.text(font, fitText(recipe.label(), 132), x + 25, y + 4, theme.bodyText(), false);
			if (contributionMode) {
				graphics.text(font, "Share", x + 165, y + 7, theme.secondaryText(), false);
			} else {
				graphics.item(paymentItem(recipe), x + 160, y + 2);
				graphics.text(font, "x" + recipe.paymentCount(), x + 179, y + 7, canAfford(recipe) ? theme.secondaryText() : theme.failureText(), false);
			}

			if (hovered) {
				graphics.requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.POINTING_HAND);
			}
		}
	}

	private void drawScrollbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (activeRecipeCount() <= VISIBLE_ROWS) {
			return;
		}

		int trackX = leftPos + SCROLL_X;
		int trackY = topPos + SCROLL_Y;
		graphics.fill(trackX, trackY, trackX + 4, trackY + SCROLL_HEIGHT, theme.divider());

		int maxStart = maxStartIndex();
		int travel = SCROLL_HEIGHT - SCROLL_HANDLE_HEIGHT;
		int handleY = trackY + (maxStart == 0 ? 0 : (int) ((double) startIndex / maxStart * travel));
		graphics.fill(trackX, handleY, trackX + 4, handleY + SCROLL_HANDLE_HEIGHT, theme.border());

		if (mouseX >= trackX && mouseX < trackX + 4 && mouseY >= trackY && mouseY < trackY + SCROLL_HEIGHT) {
			graphics.requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.RESIZE_NS);
		}
	}

	private int hoveredRecipeIndex(int mouseX, int mouseY) {
		int listLeft = leftPos + LIST_X;
		int listTop = topPos + LIST_Y;

		if (mouseX < listLeft || mouseX >= listLeft + ROW_WIDTH || mouseY < listTop || mouseY >= listTop + ROW_HEIGHT * VISIBLE_ROWS) {
			return -1;
		}

		int index = startIndex + (mouseY - listTop) / ROW_HEIGHT;
		return index >= 0 && index < activeRecipeCount() ? index : -1;
	}

	private int maxStartIndex() {
		return Math.max(0, activeRecipeCount() - VISIBLE_ROWS);
	}

	private int activeRecipeCount() {
		return contributionMode ? menu.playerRecipeCount() : menu.recipeCount();
	}

	private ScribeRecipeView activeRecipe(int index) {
		return contributionMode ? menu.playerRecipe(index) : menu.recipe(index);
	}

	private int activeButtonId(int index) {
		return contributionMode ? menu.buttonIdForPlayerRecipe(index) : menu.buttonIdForRecipe(index);
	}

	private void removeActiveRecipeAt(int index) {
		if (contributionMode) {
			menu.removePlayerRecipeAt(index);
		} else {
			menu.removeRecipeAt(index);
		}
	}

	private boolean canUseActiveRecipe(int index) {
		return contributionMode || canAfford(activeRecipe(index));
	}

	private String fitText(String text, int width) {
		if (font.width(text) <= width) {
			return text;
		}

		String marker = "...";
		return font.plainSubstrByWidth(text, Math.max(0, width - font.width(marker))) + marker;
	}

	private boolean canAfford(ScribeRecipeView recipe) {
		Item paymentItem = paymentItem(recipe).getItem();
		int remaining = Math.max(1, recipe.paymentCount());

		for (int slot = 0; slot < playerInventory.getContainerSize(); slot++) {
			ItemStack stack = playerInventory.getItem(slot);

			if (!stack.is(paymentItem)) {
				continue;
			}

			remaining -= stack.getCount();

			if (remaining <= 0) {
				return true;
			}
		}

		return false;
	}

	private static ItemStack outputItem(ScribeRecipeView recipe) {
		return itemStack(recipe.outputItemId(), Items.BOOK);
	}

	private static ItemStack paymentItem(ScribeRecipeView recipe) {
		return itemStack(recipe.paymentItemId(), Items.PAPER);
	}

	private static ItemStack itemStack(String itemId, Item fallback) {
		Identifier identifier = Identifier.tryParse(itemId);
		Item item = identifier == null
			? fallback
			: BuiltInRegistries.ITEM.getOptional(identifier).orElse(fallback);
		return new ItemStack(item);
	}
}
