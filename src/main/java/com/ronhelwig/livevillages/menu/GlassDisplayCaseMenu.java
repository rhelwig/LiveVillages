package com.ronhelwig.livevillages.menu;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.ronhelwig.livevillages.block.entity.SaleDisplayBlockEntity;
import com.ronhelwig.livevillages.content.LiveVillagesBlocks;
import com.ronhelwig.livevillages.content.LiveVillagesMenus;
import com.ronhelwig.livevillages.sim.SettlementBakerWork;

public class GlassDisplayCaseMenu extends AbstractContainerMenu {
	private static final Component BUY_LABEL = Component.literal("Buy");
	private static final Component FREE_LABEL = Component.literal("Free");
	private static final int BULK_PURCHASE_LIMIT = 12;
	public static final int STACK_BARTER_BUTTON_ID_BASE = 100;
	public static final int SINGLE_BARTER_BUTTON_ID_BASE = 200;
	public static final int FREE_CLAIM_BUTTON_ID_BASE = 300;
	public static final int CASE_SLOT_COUNT = SaleDisplayBlockEntity.SLOT_COUNT;
	public static final int CASE_COLUMNS = 3;
	public static final int CASE_ROWS = 2;
	public static final int CASE_START_X = 156;
	public static final int CASE_START_Y = 30;
	public static final int SLOT_SPACING = 18;
	public static final int PLAYER_INV_X = 14;
	public static final int PLAYER_INV_Y = 124;
	public static final int HOTBAR_Y = 182;

	private final Container caseInventory;
	private final ContainerLevelAccess access;
	private final BlockPos casePos;
	private final UUID viewerId;
	private final DataSlot freeClaimCount = new DataSlot() {
		private int value;

		@Override
		public int get() {
			value = access.evaluate((level, pos) -> {
				if (level instanceof ServerLevel serverLevel) {
					return SettlementBakerWork.bakeryFreebiesOwed(serverLevel, pos, viewerId);
				}

				return value;
			}, value);
			return value;
		}

		@Override
		public void set(int value) {
			this.value = value;
		}
	};

	public GlassDisplayCaseMenu(int syncId, Inventory inventory) {
		this(syncId, inventory, new SimpleContainer(CASE_SLOT_COUNT), ContainerLevelAccess.NULL);
	}

	public GlassDisplayCaseMenu(int syncId, Inventory inventory, Container caseInventory, ContainerLevelAccess access) {
		super(LiveVillagesMenus.GLASS_DISPLAY_CASE, syncId);
		this.caseInventory = caseInventory;
		this.access = access;
		this.casePos = access.evaluate((level, pos) -> pos.immutable(), BlockPos.ZERO);
		this.viewerId = inventory.player.getUUID();
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
						return SaleDisplayBlockEntity.canAddToDisplaySlot(getItem(), stack, getMaxStackSize(stack));
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

		addDataSlot(freeClaimCount);
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
		return Math.max(1, (int) Math.ceil(bulkPurchaseCount(slot) * (bundlePrice / (double) bundleSize)));
	}

	public int bulkPurchaseCount(int slot) {
		ItemStack stack = caseStack(slot);
		return stack.isEmpty() ? 0 : Math.min(BULK_PURCHASE_LIMIT, stack.getCount());
	}

	public int freeClaimCount() {
		return freeClaimCount.get();
	}

	public boolean hasFreeClaimAvailable(int slot) {
		return freeClaimCount() > 0 && SettlementBakerWork.isBakedGoods(caseStack(slot));
	}

	public BakeryTradeOffer stackBarterOfferForSlot(int slot) {
		return barterOfferForSlot(slot, false);
	}

	public BakeryTradeOffer singleBarterOfferForSlot(int slot) {
		return barterOfferForSlot(slot, true);
	}

	public Component emeraldButtonLabel(int slot) {
		int price = priceForSlot(slot);
		if (price <= 0) {
			return BUY_LABEL;
		}

		return Component.literal(price + " " + (price == 1 ? "Emerald" : "Emeralds"));
	}

	public Component stackBarterButtonLabel(int slot) {
		BakeryTradeOffer offer = stackBarterOfferForSlot(slot);
		if (offer == null) {
			return BUY_LABEL;
		}

		return barterButtonLabel(offer);
	}

	public Component singleBarterButtonLabel(int slot) {
		if (hasFreeClaimAvailable(slot)) {
			return FREE_LABEL;
		}

		BakeryTradeOffer offer = singleBarterOfferForSlot(slot);
		if (offer == null) {
			return BUY_LABEL;
		}

		return barterButtonLabel(offer);
	}

	private Component barterButtonLabel(BakeryTradeOffer offer) {
		String goodsLabel = TradeBoardTradeRules.compactLabel(
			offer.paymentGoodsKey(),
			humanizeGoodsLabel(offer.paymentGoodsKey())
		);
		return Component.literal(offer.amount() + " " + goodsLabel);
	}

	@Override
	public boolean clickMenuButton(Player player, int buttonId) {
		boolean stackBarterPurchase = buttonId >= STACK_BARTER_BUTTON_ID_BASE && buttonId < STACK_BARTER_BUTTON_ID_BASE + CASE_SLOT_COUNT;
		boolean singleBarterPurchase = buttonId >= SINGLE_BARTER_BUTTON_ID_BASE && buttonId < SINGLE_BARTER_BUTTON_ID_BASE + CASE_SLOT_COUNT;
		boolean freeClaimPurchase = buttonId >= FREE_CLAIM_BUTTON_ID_BASE && buttonId < FREE_CLAIM_BUTTON_ID_BASE + CASE_SLOT_COUNT;
		boolean barterPurchase = stackBarterPurchase || singleBarterPurchase;
		int slot = stackBarterPurchase
			? buttonId - STACK_BARTER_BUTTON_ID_BASE
			: singleBarterPurchase
				? buttonId - SINGLE_BARTER_BUTTON_ID_BASE
				: freeClaimPurchase
					? buttonId - FREE_CLAIM_BUTTON_ID_BASE
					: buttonId;

		if (slot < 0 || slot >= CASE_SLOT_COUNT) {
			return false;
		}

		ItemStack purchased = caseInventory.getItem(slot);
		if (purchased.isEmpty()) {
			return false;
		}

		String paymentGoodsKey = null;
		int paymentAmount = 0;
		int soldCount = bulkPurchaseCount(slot);

		if (freeClaimPurchase) {
			if (!claimFreeBakedGood(player, slot)) {
				return false;
			}
			soldCount = 1;
		} else if (barterPurchase) {
			BakeryTradeOffer offer = stackBarterPurchase ? stackBarterOfferForSlot(slot) : singleBarterOfferForSlot(slot);
			if (offer == null || !removePlayerGoods(player.getInventory(), offer.paymentGoodsKey(), offer.amount())) {
				if (offer != null) {
					player.sendSystemMessage(Component.literal(
						"You need " + offer.amount() + " " + humanizeGoodsLabel(offer.paymentGoodsKey()) + " to trade for that."
					));
				}
				return false;
			}
			paymentGoodsKey = offer.paymentGoodsKey();
			paymentAmount = offer.amount();
			soldCount = offer.soldCount();
		} else {
			int price = priceForSlot(slot);
			if (price <= 0) {
				return false;
			}

			if (countEmeralds(player.getInventory()) < price) {
				player.sendSystemMessage(Component.literal("You need " + price + " emeralds to buy that."));
				return false;
			}

			if (!removeEmeralds(player.getInventory(), price)) {
				return false;
			}
			paymentGoodsKey = "emerald";
			paymentAmount = price;
		}

		ItemStack soldStack = purchased.copyWithCount(Math.min(soldCount, purchased.getCount()));
		ItemStack remainingStack = purchased.copy();
		remainingStack.shrink(soldStack.getCount());
		caseInventory.setItem(slot, remainingStack.isEmpty() ? ItemStack.EMPTY : remainingStack);
		if (!player.getInventory().add(soldStack)) {
			player.drop(soldStack, false);
		}
		caseInventory.setChanged();
		recordSalePayment(paymentGoodsKey, paymentAmount);
		return true;
	}

	@Override
	public void clicked(int slotIndex, int button, ContainerInput input, Player player) {
		if (input == ContainerInput.PICKUP && (button == 0 || button == 1) && slotIndex >= 0 && slotIndex < CASE_SLOT_COUNT) {
			Slot slot = getSlot(slotIndex);
			ItemStack carried = getCarried();

			if (!carried.isEmpty() && slot.hasItem() && slot.mayPlace(carried)) {
				int insertAmount = button == 0 ? carried.getCount() : 1;
				setCarried(slot.safeInsert(carried, insertAmount));
				slot.setChanged();
				broadcastChanges();
				return;
			}
		}

		super.clicked(slotIndex, button, input, player);
	}

	private boolean claimFreeBakedGood(Player player, int slot) {
		if (!(player.level() instanceof ServerLevel)) {
			return hasFreeClaimAvailable(slot);
		}

		return access.evaluate((level, pos) -> {
			if (!(level instanceof ServerLevel serverLevel)) {
				return false;
			}

			if (!SettlementBakerWork.isBakedGoods(caseStack(slot))) {
				player.sendSystemMessage(Component.literal("Free bakery claims only apply to baked goods."));
				return false;
			}

			if (!SettlementBakerWork.consumeBakeryFreebie(serverLevel, pos, player.getUUID())) {
				player.sendSystemMessage(Component.literal("You do not currently have a free baked good claim here."));
				return false;
			}

			return true;
		}, false);
	}

	private void recordSalePayment(String goodsKey, int amount) {
		access.execute((level, pos) -> {
			if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
				SettlementBakerWork.recordBakerySale(serverLevel, pos, goodsKey, amount);
			}
		});
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
		return access.evaluate((level, pos) -> {
			boolean validBlock = level.getBlockState(pos).is(LiveVillagesBlocks.GLASS_DISPLAY_CASE)
				|| level.getBlockState(pos).is(LiveVillagesBlocks.BAKERS_COUNTER);
			return validBlock && player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D;
		}, true);
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

	private static boolean removePlayerGoods(Inventory inventory, String goodsKey, int amount) {
		return TradeBoardTradeRules.removePlayerGoods(inventory, goodsKey, amount);
	}

	private static int requiredBakeryPaymentAmount(String soldGoodsKey, int soldCount, String paymentGoodsKey) {
		if (soldGoodsKey == null || paymentGoodsKey == null || soldCount <= 0) {
			return 0;
		}

		int saleItemValue = TradeBoardTradeRules.itemValuePoints(soldGoodsKey, 100);
		if (saleItemValue <= 0) {
			return 0;
		}

		int totalValue = saleItemValue * soldCount;
		return TradeBoardTradeRules.requiredItemsForValue(paymentGoodsKey, 100, totalValue);
	}

	private static String preferredBakeryPaymentGoods(String soldGoodsKey) {
		if (soldGoodsKey == null) {
			return null;
		}

		return switch (soldGoodsKey) {
			case "bread" -> "wheat";
			case "baked_potato" -> "potato";
			case "cookie" -> "wheat";
			case "pumpkin_pie" -> "pumpkin";
			case "cake" -> "wheat";
			case "golden_apple" -> "apple";
			default -> null;
		};
	}

	private static String humanizeGoodsLabel(String goodsKey) {
		ItemStack labelStack = TradeBoardTradeRules.createGoodsStack(goodsKey, 1);
		if (!labelStack.isEmpty()) {
			return labelStack.getHoverName().getString();
		}

		return goodsKey.replace('_', ' ');
	}

	private BakeryTradeOffer barterOfferForSlot(int slot, boolean singleItem) {
		ItemStack stack = caseStack(slot);
		if (stack.isEmpty()) {
			return null;
		}

		String soldGoodsKey = TradeBoardTradeRules.goodsKeyForStack(stack);
		String paymentGoodsKey = preferredBakeryPaymentGoods(soldGoodsKey);
		if (paymentGoodsKey == null) {
			return null;
		}

		int soldCount = singleItem ? 1 : bulkPurchaseCount(slot);
		int requiredAmount = requiredBakeryPaymentAmount(soldGoodsKey, soldCount, paymentGoodsKey);
		if (requiredAmount <= 0) {
			return null;
		}

		return new BakeryTradeOffer(paymentGoodsKey, requiredAmount, soldCount);
	}

	public record BakeryTradeOffer(String paymentGoodsKey, int amount, int soldCount) {
	}
}
