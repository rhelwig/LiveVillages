package com.ronhelwig.livevillages.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import com.ronhelwig.livevillages.menu.CarpenterBenchMenu;
import com.ronhelwig.livevillages.menu.CarpenterBenchRecipe;

public class CarpenterBenchScreen extends AbstractContainerScreen<CarpenterBenchMenu> {
	private static final Identifier SCROLLER_SPRITE = Identifier.withDefaultNamespace("container/stonecutter/scroller");
	private static final Identifier SCROLLER_DISABLED_SPRITE = Identifier.withDefaultNamespace("container/stonecutter/scroller_disabled");
	private static final Identifier RECIPE_SELECTED_SPRITE = Identifier.withDefaultNamespace("container/stonecutter/recipe_selected");
	private static final Identifier RECIPE_HIGHLIGHTED_SPRITE = Identifier.withDefaultNamespace("container/stonecutter/recipe_highlighted");
	private static final Identifier RECIPE_SPRITE = Identifier.withDefaultNamespace("container/stonecutter/recipe");
	private static final Identifier BG_LOCATION = Identifier.withDefaultNamespace("textures/gui/container/stonecutter.png");
	private static final int RECIPES_COLUMNS = 4;
	private static final int RECIPES_ROWS = 3;
	private static final int RECIPES_PER_PAGE = RECIPES_COLUMNS * RECIPES_ROWS;
	private static final int RECIPES_X = 52;
	private static final int RECIPES_Y = 14;
	private static final int RECIPE_WIDTH = 16;
	private static final int RECIPE_HEIGHT = 18;
	private static final int SCROLLER_X = 119;
	private static final int SCROLLER_Y = 15;
	private static final int SCROLLER_CLICK_Y = 9;
	private static final int SCROLLER_WIDTH = 12;
	private static final int SCROLLER_HEIGHT = 15;
	private static final int SCROLLER_FULL_HEIGHT = 54;
	private static final int SCROLLER_TRAVEL = 41;

	private float scrollOffs;
	private boolean scrolling;
	private int startIndex;
	private boolean displayRecipes;

	public CarpenterBenchScreen(CarpenterBenchMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		menu.registerUpdateListener(this::containerChanged);
		titleLabelY--;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);

		int x = leftPos;
		int y = topPos;
		graphics.blit(RenderPipelines.GUI_TEXTURED, BG_LOCATION, x, y, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);

		int scrollY = (int)(SCROLLER_TRAVEL * scrollOffs);
		Identifier scrollerSprite = isScrollBarActive() ? SCROLLER_SPRITE : SCROLLER_DISABLED_SPRITE;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, scrollerSprite, x + SCROLLER_X, y + SCROLLER_Y + scrollY, SCROLLER_WIDTH, SCROLLER_HEIGHT);

		int scrollerLeft = x + SCROLLER_X;
		int scrollerTop = y + SCROLLER_Y;
		if (mouseX >= scrollerLeft && mouseY >= scrollerTop && mouseX < scrollerLeft + SCROLLER_WIDTH && mouseY < scrollerTop + SCROLLER_FULL_HEIGHT) {
			if (isScrollBarActive()) {
				graphics.requestCursor(scrolling
					? com.mojang.blaze3d.platform.cursor.CursorTypes.RESIZE_NS
					: com.mojang.blaze3d.platform.cursor.CursorTypes.POINTING_HAND);
			} else {
				graphics.requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.NOT_ALLOWED);
			}
		}

		int recipesLeft = leftPos + RECIPES_X;
		int recipesTop = topPos + RECIPES_Y;
		int endIndex = startIndex + RECIPES_PER_PAGE;
		extractButtons(graphics, mouseX, mouseY, recipesLeft, recipesTop, endIndex);
		extractRecipes(graphics, recipesLeft, recipesTop, endIndex);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);

		if (!displayRecipes) {
			return;
		}

		int recipesLeft = leftPos + RECIPES_X;
		int recipesTop = topPos + RECIPES_Y;
		int endIndex = startIndex + RECIPES_PER_PAGE;

		for (int index = startIndex; index < endIndex && index < menu.getNumberOfVisibleRecipes(); index++) {
			int visibleIndex = index - startIndex;
			int x = recipesLeft + visibleIndex % RECIPES_COLUMNS * RECIPE_WIDTH;
			int y = recipesTop + visibleIndex / RECIPES_COLUMNS * RECIPE_HEIGHT + 2;

			if (mouseX >= x && mouseX < x + RECIPE_WIDTH && mouseY >= y && mouseY < y + RECIPE_HEIGHT) {
				graphics.setTooltipForNextFrame(font, menu.getVisibleRecipes().get(index).assemble(), mouseX, mouseY);
			}
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (displayRecipes) {
			int recipesLeft = leftPos + RECIPES_X;
			int recipesTop = topPos + RECIPES_Y;
			int endIndex = startIndex + RECIPES_PER_PAGE;

			for (int index = startIndex; index < endIndex && index < menu.getNumberOfVisibleRecipes(); index++) {
				int visibleIndex = index - startIndex;
				double recipeX = event.x() - (recipesLeft + visibleIndex % RECIPES_COLUMNS * RECIPE_WIDTH);
				double recipeY = event.y() - (recipesTop + visibleIndex / RECIPES_COLUMNS * RECIPE_HEIGHT);

				if (recipeX >= 0.0D && recipeY >= 0.0D && recipeX < RECIPE_WIDTH && recipeY < RECIPE_HEIGHT && menu.clickMenuButton(minecraft.player, index)) {
					Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.0F));
					minecraft.gameMode.handleInventoryButtonClick(menu.containerId, index);
					return true;
				}
			}

			int scrollerLeft = leftPos + SCROLLER_X;
			int scrollerTop = topPos + SCROLLER_CLICK_Y;
			if (event.x() >= scrollerLeft
				&& event.x() < scrollerLeft + SCROLLER_WIDTH
				&& event.y() >= scrollerTop
				&& event.y() < scrollerTop + SCROLLER_FULL_HEIGHT) {
				scrolling = true;
			}
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (scrolling && isScrollBarActive()) {
			int top = topPos + RECIPES_Y;
			int bottom = top + SCROLLER_FULL_HEIGHT;
			scrollOffs = ((float)event.y() - top - SCROLLER_HEIGHT / 2.0F) / (bottom - top - SCROLLER_HEIGHT);
			scrollOffs = Mth.clamp(scrollOffs, 0.0F, 1.0F);
			startIndex = ((int)((double)(scrollOffs * getOffscreenRows()) + 0.5D)) * RECIPES_COLUMNS;
			return true;
		}

		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		scrolling = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
			return true;
		}

		if (isScrollBarActive()) {
			int offscreenRows = getOffscreenRows();
			float scrollStep = (float)verticalAmount / offscreenRows;
			scrollOffs = Mth.clamp(scrollOffs - scrollStep, 0.0F, 1.0F);
			startIndex = ((int)((double)(scrollOffs * offscreenRows) + 0.5D)) * RECIPES_COLUMNS;
		}

		return true;
	}

	private void extractButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int recipesLeft, int recipesTop, int endIndex) {
		for (int index = startIndex; index < endIndex && index < menu.getNumberOfVisibleRecipes(); index++) {
			int visibleIndex = index - startIndex;
			int x = recipesLeft + visibleIndex % RECIPES_COLUMNS * RECIPE_WIDTH;
			int row = visibleIndex / RECIPES_COLUMNS;
			int y = recipesTop + row * RECIPE_HEIGHT + 2;
			Identifier recipeSprite;

			if (index == menu.getSelectedRecipeIndex()) {
				recipeSprite = RECIPE_SELECTED_SPRITE;
			} else if (mouseX >= x && mouseY >= y && mouseX < x + RECIPE_WIDTH && mouseY < y + RECIPE_HEIGHT) {
				recipeSprite = RECIPE_HIGHLIGHTED_SPRITE;
			} else {
				recipeSprite = RECIPE_SPRITE;
			}

			int spriteY = y - 1;
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, recipeSprite, x, spriteY, RECIPE_WIDTH, RECIPE_HEIGHT);

			if (mouseX >= x && mouseY >= spriteY && mouseX < x + RECIPE_WIDTH && mouseY < spriteY + RECIPE_HEIGHT) {
				graphics.requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.POINTING_HAND);
			}
		}
	}

	private void extractRecipes(GuiGraphicsExtractor graphics, int recipesLeft, int recipesTop, int endIndex) {
		for (int index = startIndex; index < endIndex && index < menu.getNumberOfVisibleRecipes(); index++) {
			CarpenterBenchRecipe recipe = menu.getVisibleRecipes().get(index);
			ItemStack result = recipe.assemble();
			int visibleIndex = index - startIndex;
			int x = recipesLeft + visibleIndex % RECIPES_COLUMNS * RECIPE_WIDTH;
			int y = recipesTop + visibleIndex / RECIPES_COLUMNS * RECIPE_HEIGHT + 2;
			graphics.item(result, x, y);
		}
	}

	private boolean isScrollBarActive() {
		return displayRecipes && menu.getNumberOfVisibleRecipes() > RECIPES_PER_PAGE;
	}

	protected int getOffscreenRows() {
		return (menu.getNumberOfVisibleRecipes() + RECIPES_COLUMNS - 1) / RECIPES_COLUMNS - RECIPES_ROWS;
	}

	private void containerChanged() {
		displayRecipes = menu.hasInputItem();
		scrollOffs = 0.0F;
		startIndex = 0;
	}
}
