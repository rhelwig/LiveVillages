package com.ronhelwig.livevillages.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.ronhelwig.livevillages.block.entity.GlassDisplayCaseBlockEntity;
import com.ronhelwig.livevillages.content.LiveVillagesBlocks;
import com.ronhelwig.livevillages.content.LiveVillagesMenus;

public class GlassDisplayCaseMenu extends AbstractContainerMenu {
	public static final int CASE_SLOT_COUNT = GlassDisplayCaseBlockEntity.SLOT_COUNT;
	public static final int CASE_COLUMNS = 3;
	public static final int CASE_ROWS = 2;
	public static final int CASE_START_X = 62;
	public static final int CASE_START_Y = 20;
	public static final int SLOT_SPACING = 18;
	public static final int PLAYER_INV_X = 8;
	public static final int PLAYER_INV_Y = 84;
	public static final int HOTBAR_Y = 142;

	private final Container caseInventory;
	private final ContainerLevelAccess access;
	private final BlockPos casePos;

	public GlassDisplayCaseMenu(int syncId, Inventory inventory) {
		this(syncId, inventory, new SimpleContainer(CASE_SLOT_COUNT), ContainerLevelAccess.NULL);
	}

	public GlassDisplayCaseMenu(int syncId, Inventory inventory, Container caseInventory, ContainerLevelAccess access) {
		super(LiveVillagesMenus.GLASS_DISPLAY_CASE, syncId);
		this.caseInventory = caseInventory;
		this.access = access;
		this.casePos = access.evaluate((level, pos) -> pos.immutable(), BlockPos.ZERO);
		checkContainerSize(caseInventory, CASE_SLOT_COUNT);
		caseInventory.startOpen(inventory.player);

		for (int row = 0; row < CASE_ROWS; row++) {
			for (int column = 0; column < CASE_COLUMNS; column++) {
				int slot = column + row * CASE_COLUMNS;
				int x = CASE_START_X + column * SLOT_SPACING;
				int y = CASE_START_Y + row * SLOT_SPACING;
				addSlot(new Slot(caseInventory, slot, x, y) {
					@Override
					public boolean mayPickup(Player player) {
						return false;
					}

					@Override
					public boolean mayPlace(ItemStack stack) {
						return getItem().isEmpty() && !stack.isEmpty();
					}
				});
			}
		}

		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				addSlot(new Slot(inventory, column + row * 9 + 9, PLAYER_INV_X + column * SLOT_SPACING, PLAYER_INV_Y + row * SLOT_SPACING));
			}
		}

		for (int column = 0; column < 9; column++) {
			addSlot(new Slot(inventory, column, PLAYER_INV_X + column * SLOT_SPACING, HOTBAR_Y));
		}
	}

	public BlockPos casePos() {
		return casePos;
	}

	public ItemStack caseStack(int slot) {
		return slot < 0 || slot >= CASE_SLOT_COUNT ? ItemStack.EMPTY : caseInventory.getItem(slot);
	}

	public int priceForSlot(int slot) {
		ItemStack stack = caseStack(slot);
		if (stack.isEmpty()) {
			return 0;
		}

		String stockKey = TradeBoardTradeRules.stockKeyForStack(stack);
		if (stockKey == null) {
			return 0;
		}

		int bundleSize = Math.max(1, TradeBoardTradeRules.bundleSize(stockKey));
		int bundlePrice = Math.max(1, TradeBoardTradeRules.bundlePriceEmeralds(stockKey, 100));
		return Math.max(1, (int) Math.ceil(stack.getCount() * (bundlePrice / (double) bundleSize)));
	}

	@Override
	public boolean clickMenuButton(Player player, int buttonId) {
		if (buttonId < 0 || buttonId >= CASE_SLOT_COUNT) {
			return false;
		}

		ItemStack caseStack = caseInventory.getItem(buttonId);
		if (caseStack.isEmpty()) {
			return false;
		}

		int price = priceForSlot(buttonId);
		if (price <= 0) {
			return false;
		}

		if (countEmeralds(player.getInventory()) < price) {
			player.sendSystemMessage(net.minecraft.network.chat.Component.literal("You need " + price + " emeralds to buy that."));
			return false;
		}

		if (!removeEmeralds(player.getInventory(), price)) {
			return false;
		}

		ItemStack purchased = caseStack.copy();
		caseInventory.setItem(buttonId, ItemStack.EMPTY);
		if (!player.getInventory().add(purchased)) {
			player.drop(purchased, false);
		}
		caseInventory.setChanged();
		return true;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		Slot slot = slots.get(slotIndex);
		if (slot == null || !slot.hasItem()) {
			return ItemStack.EMPTY;
		}

		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();

		if (slotIndex < CASE_SLOT_COUNT) {
			return ItemStack.EMPTY;
		}

		if (moveItemStackTo(stack, 0, CASE_SLOT_COUNT, false)) {
			slot.setByPlayer(ItemStack.EMPTY);
			return original;
		}

		int inventoryStart = CASE_SLOT_COUNT;
		int inventoryEnd = inventoryStart + 27;
		int hotbarStart = inventoryEnd;
		int hotbarEnd = hotbarStart + 9;

		if (slotIndex < inventoryEnd) {
			if (!moveItemStackTo(stack, hotbarStart, hotbarEnd, false)) {
				return ItemStack.EMPTY;
			}
		} else if (slotIndex < hotbarEnd) {
			if (!moveItemStackTo(stack, inventoryStart, inventoryEnd, false)) {
				return ItemStack.EMPTY;
			}
		}

		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}

		return original;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		caseInventory.stopOpen(player);
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(access, player, LiveVillagesBlocks.GLASS_DISPLAY_CASE);
	}

	private static int countEmeralds(Inventory inventory) {
		int emeralds = 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(Items.EMERALD)) {
				emeralds += stack.getCount();
			}
		}
		return emeralds;
	}

	private static boolean removeEmeralds(Inventory inventory, int amount) {
		int remaining = amount;
		for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.is(Items.EMERALD)) {
				continue;
			}

			int remove = Math.min(remaining, stack.getCount());
			stack.shrink(remove);
			remaining -= remove;
			if (stack.isEmpty()) {
				inventory.setItem(slot, ItemStack.EMPTY);
			}
		}

		return remaining == 0;
	}
}
