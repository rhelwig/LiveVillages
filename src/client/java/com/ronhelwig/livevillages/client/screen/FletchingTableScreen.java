package com.ronhelwig.livevillages.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.ronhelwig.livevillages.menu.FletchingTableMenu;
import com.ronhelwig.livevillages.menu.FletchingTableRecipe;

public class FletchingTableScreen extends AbstractContainerScreen<FletchingTableMenu> {
	private static final int SCREEN_WIDTH = 220;
	private static final int SCREEN_HEIGHT = 214;
	private static final int TITLE_BAR_HEIGHT = 22;
	private static final int INPUT_BAND_TOP = 24;
	private static final int INPUT_BAND_BOTTOM = 66;
	private static final int INFO_BAND_TOP = 68;
	private static final int INFO_BAND_BOTTOM = 104;
	private static final Component INPUT_EXPLANATION = Component.literal("Head takes flint, copper, iron, or diamond.");

	public FletchingTableScreen(FletchingTableMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, SCREEN_WIDTH, SCREEN_HEIGHT);
		menu.registerUpdateListener(this::containerChanged);
		this.inventoryLabelX = FletchingTableMenu.PLAYER_INV_X;
		this.inventoryLabelY = 108;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		extractTransparentBackground(graphics);
		graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xEE201915);
		graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + TITLE_BAR_HEIGHT, 0xFF6A4E2B);
		graphics.fill(leftPos + 1, topPos + INPUT_BAND_TOP, leftPos + imageWidth - 1, topPos + INPUT_BAND_BOTTOM, 0xFF2A2119);
		graphics.fill(leftPos + 1, topPos + INFO_BAND_TOP, leftPos + imageWidth - 1, topPos + INFO_BAND_BOTTOM, 0xFF241D17);
		graphics.fill(leftPos + 1, topPos + INFO_BAND_BOTTOM + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, 0xFF1E1812);
		graphics.outline(leftPos, topPos, imageWidth, imageHeight, 0xFFB78B46);

		drawSlotFrame(graphics, leftPos + FletchingTableMenu.SHAFT_SLOT_X, topPos + FletchingTableMenu.INPUT_ROW_Y);
		drawSlotFrame(graphics, leftPos + FletchingTableMenu.FLETCHING_SLOT_X, topPos + FletchingTableMenu.INPUT_ROW_Y);
		drawSlotFrame(graphics, leftPos + FletchingTableMenu.HEAD_SLOT_X, topPos + FletchingTableMenu.INPUT_ROW_Y);
		drawSlotFrame(graphics, leftPos + FletchingTableMenu.RESULT_SLOT_X, topPos + FletchingTableMenu.RESULT_SLOT_Y);
		drawInputLane(graphics);
		drawGhostSlotIcons(graphics);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Font font = this.font;
		graphics.text(font, title, titleLabelX, titleLabelY, 0xFFF5E6C8, false);
		graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFD8C4A0, false);
		centerSlotLabel(graphics, font, "Shaft", FletchingTableMenu.SHAFT_SLOT_X + 8, 26, 0xFFCFB98E);
		centerSlotLabel(graphics, font, "Fletch", FletchingTableMenu.FLETCHING_SLOT_X + 8, 26, 0xFFCFB98E);
		centerSlotLabel(graphics, font, "Head", FletchingTableMenu.HEAD_SLOT_X + 8, 26, 0xFFCFB98E);
		centerSlotLabel(graphics, font, "Batch", FletchingTableMenu.RESULT_SLOT_X + 8, 26, 0xFFCFB98E);

		FletchingTableRecipe recipe = selectedRecipe();
		if (recipe != null) {
			graphics.text(font, recipe.assemble().getHoverName(), 28, 76, 0xFFF2CF84, false);
			graphics.text(font, recipeDescription(recipe), 28, 88, 0xFFE6D7BC, false);
			graphics.text(font, craftHint(), 28, 100, recipeCraftable() ? 0xFFB8D49A : 0xFFC69482, false);
		} else {
			graphics.text(font, "Drop a head material to preview the batch result.", 28, 76, 0xFF9F8E72, false);
			graphics.text(font, INPUT_EXPLANATION, 28, 88, 0xFF9F8E72, false);
		}
	}

	private void drawInputLane(GuiGraphicsExtractor graphics) {
		int arrowMidY = topPos + FletchingTableMenu.INPUT_ROW_Y + 8;
		int startX = leftPos + FletchingTableMenu.HEAD_SLOT_X + 22;
		int endX = leftPos + FletchingTableMenu.RESULT_SLOT_X - 6;
		graphics.fill(startX, arrowMidY, endX, arrowMidY + 1, 0xFF8A6B39);
		graphics.fill(endX - 5, arrowMidY - 2, endX, arrowMidY + 3, 0xFF8A6B39);
	}

	private void drawGhostSlotIcons(GuiGraphicsExtractor graphics) {
		FletchingTableRecipe ghostRecipe = selectedRecipe();
		drawGhostItem(graphics, FletchingTableMenu.SHAFT_SLOT_X, FletchingTableMenu.INPUT_ROW_Y, menu.getSlot(FletchingTableMenu.SHAFT_SLOT).hasItem(), new ItemStack(defaultShaftItem(ghostRecipe)));
		drawGhostItem(graphics, FletchingTableMenu.FLETCHING_SLOT_X, FletchingTableMenu.INPUT_ROW_Y, menu.getSlot(FletchingTableMenu.FLETCHING_SLOT).hasItem(), new ItemStack(defaultFletchingItem(ghostRecipe)));
		drawGhostItem(graphics, FletchingTableMenu.HEAD_SLOT_X, FletchingTableMenu.INPUT_ROW_Y, menu.getSlot(FletchingTableMenu.HEAD_SLOT).hasItem(), new ItemStack(defaultHeadItem(ghostRecipe)));

		if (ghostRecipe != null) {
			drawGhostItem(graphics, FletchingTableMenu.RESULT_SLOT_X, FletchingTableMenu.RESULT_SLOT_Y, menu.getSlot(FletchingTableMenu.RESULT_SLOT).hasItem(), ghostRecipe.assemble());
		}
	}

	private void drawGhostItem(GuiGraphicsExtractor graphics, int x, int y, boolean occupied, ItemStack stack) {
		if (occupied) {
			return;
		}

		int screenX = leftPos + x;
		int screenY = topPos + y;
		graphics.item(stack, screenX, screenY);
		graphics.fill(screenX, screenY, screenX + 16, screenY + 16, 0xBB201915);
	}

	private void drawSlotFrame(GuiGraphicsExtractor graphics, int x, int y) {
		graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF16120F);
		graphics.outline(x - 1, y - 1, 18, 18, 0xFF8A6B39);
	}

	private void centerSlotLabel(GuiGraphicsExtractor graphics, Font font, String label, int centerX, int y, int color) {
		graphics.text(font, label, centerX - font.width(label) / 2, y, color, false);
	}

	private String craftHint() {
		return recipeCraftable()
			? "Ready to batch craft."
			: "Load the matching parts to craft this batch.";
	}

	private boolean recipeCraftable() {
		int selected = menu.getSelectedRecipeIndex();
		return selected >= 0 && menu.isRecipeCraftable(selected);
	}

	private FletchingTableRecipe selectedRecipe() {
		int selected = menu.getSelectedRecipeIndex();
		if (selected >= 0 && selected < menu.getNumberOfVisibleRecipes()) {
			return menu.getVisibleRecipes().get(selected);
		}

		return null;
	}

	private String recipeDescription(FletchingTableRecipe recipe) {
		return switch (recipe.id()) {
			case "arrow" -> "Standard arrows for everyday use.";
			case "copperhead_arrow" -> "Cuts deeper and hits soft targets harder.";
			case "ironhead_arrow" -> "Works better against armor.";
			case "diamondhead_arrow" -> "Cuts deeper and deals more damage.";
			default -> "";
		};
	}

	private Item defaultShaftItem(FletchingTableRecipe recipe) {
		return recipe == null ? Items.STICK : recipe.shaftItem();
	}

	private Item defaultFletchingItem(FletchingTableRecipe recipe) {
		return recipe == null ? Items.FEATHER : recipe.fletchingItem();
	}

	private Item defaultHeadItem(FletchingTableRecipe recipe) {
		return recipe == null ? Items.FLINT : recipe.headItem();
	}

	private void containerChanged() {
	}
}
