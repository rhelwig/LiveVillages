package com.ronhelwig.livevillages.menu;

import java.util.List;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.ronhelwig.livevillages.content.LiveVillagesBlocks;
import com.ronhelwig.livevillages.content.LiveVillagesMenus;

public class CarpenterBenchMenu extends AbstractContainerMenu {
	public static final int INPUT_SLOT = 0;
	public static final int RESULT_SLOT = 1;
	private static final int INV_SLOT_START = 2;
	private static final int INV_SLOT_END = 29;
	private static final int USE_ROW_SLOT_START = 29;
	private static final int USE_ROW_SLOT_END = 38;

	private final ContainerLevelAccess access;
	private final DataSlot selectedRecipeIndex = DataSlot.standalone();
	private List<CarpenterBenchRecipe> visibleRecipes = List.of();
	private ItemStack input = ItemStack.EMPTY;
	private long lastSoundTime;
	private Runnable slotUpdateListener = () -> {
	};

	public final Container container;
	private final ResultContainer resultContainer = new ResultContainer();
	final Slot inputSlot;
	final Slot resultSlot;

	public CarpenterBenchMenu(int syncId, Inventory inventory) {
		this(syncId, inventory, ContainerLevelAccess.NULL);
	}

	public CarpenterBenchMenu(int syncId, Inventory inventory, ContainerLevelAccess access) {
		super(LiveVillagesMenus.CARPENTER_BENCH, syncId);
		this.access = access;
		this.container = new SimpleContainer(1) {
			@Override
			public void setChanged() {
				super.setChanged();
				CarpenterBenchMenu.this.slotsChanged(this);
				CarpenterBenchMenu.this.slotUpdateListener.run();
			}
		};

		this.inputSlot = addSlot(new Slot(container, 0, 20, 33));
		this.resultSlot = addSlot(new Slot(resultContainer, 1, 143, 33) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}

			@Override
			public void onTake(Player player, ItemStack stack) {
				stack.onCraftedBy(player, stack.getCount());
				inputSlot.remove(1);

				if (inputSlot.hasItem()) {
					setupResultSlot(selectedRecipeIndex.get());
				}

				access.execute((level, pos) -> {
					long gameTime = level.getGameTime();

					if (lastSoundTime != gameTime) {
						level.playSound(null, pos, SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.BLOCKS, 1.0F, 1.0F);
						lastSoundTime = gameTime;
					}
				});

				super.onTake(player, stack);
			}
		});
		addStandardInventorySlots(inventory, 8, 84);
		addDataSlot(selectedRecipeIndex);
	}

	public int getSelectedRecipeIndex() {
		return selectedRecipeIndex.get();
	}

	public List<CarpenterBenchRecipe> getVisibleRecipes() {
		return visibleRecipes;
	}

	public int getNumberOfVisibleRecipes() {
		return visibleRecipes.size();
	}

	public boolean hasInputItem() {
		return inputSlot.hasItem() && !visibleRecipes.isEmpty();
	}

	public void registerUpdateListener(Runnable listener) {
		this.slotUpdateListener = listener;
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(access, player, LiveVillagesBlocks.CARPENTER_BENCH);
	}

	@Override
	public boolean clickMenuButton(Player player, int buttonId) {
		if (selectedRecipeIndex.get() == buttonId) {
			return false;
		}

		if (isValidRecipeIndex(buttonId)) {
			selectedRecipeIndex.set(buttonId);
			setupResultSlot(buttonId);
		}

		return true;
	}

	@Override
	public void slotsChanged(Container container) {
		ItemStack stack = inputSlot.getItem();

		if (stack.getItem() != input.getItem()) {
			input = stack.copy();
			setupRecipeList(stack);
		}
	}

	@Override
	public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
		return slot.container != resultContainer && super.canTakeItemForPickAll(stack, slot);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		ItemStack movedStack = ItemStack.EMPTY;
		Slot slot = slots.get(slotIndex);

		if (slot != null && slot.hasItem()) {
			ItemStack stack = slot.getItem();
			Item item = stack.getItem();
			movedStack = stack.copy();

			if (slotIndex == RESULT_SLOT) {
				item.onCraftedBy(stack, player);

				if (!moveItemStackTo(stack, INV_SLOT_START, USE_ROW_SLOT_END, true)) {
					return ItemStack.EMPTY;
				}

				slot.onQuickCraft(stack, movedStack);
			} else if (slotIndex == INPUT_SLOT) {
				if (!moveItemStackTo(stack, INV_SLOT_START, USE_ROW_SLOT_END, false)) {
					return ItemStack.EMPTY;
				}
			} else if (CarpenterBenchRecipes.acceptsInput(stack)) {
				if (!moveItemStackTo(stack, INPUT_SLOT, RESULT_SLOT, false)) {
					return ItemStack.EMPTY;
				}
			} else if (slotIndex >= INV_SLOT_START && slotIndex < INV_SLOT_END) {
				if (!moveItemStackTo(stack, USE_ROW_SLOT_START, USE_ROW_SLOT_END, false)) {
					return ItemStack.EMPTY;
				}
			} else if (slotIndex >= USE_ROW_SLOT_START && slotIndex < USE_ROW_SLOT_END && !moveItemStackTo(stack, INV_SLOT_START, INV_SLOT_END, false)) {
				return ItemStack.EMPTY;
			}

			if (stack.isEmpty()) {
				slot.setByPlayer(ItemStack.EMPTY);
			}

			slot.setChanged();

			if (stack.getCount() == movedStack.getCount()) {
				return ItemStack.EMPTY;
			}

			slot.onTake(player, stack);

			if (slotIndex == RESULT_SLOT) {
				player.drop(stack, false);
			}

			broadcastChanges();
		}

		return movedStack;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		resultContainer.removeItemNoUpdate(1);
		access.execute((level, pos) -> clearContainer(player, container));
	}

	private boolean isValidRecipeIndex(int index) {
		return index >= 0 && index < visibleRecipes.size();
	}

	private void setupRecipeList(ItemStack stack) {
		selectedRecipeIndex.set(-1);
		resultSlot.set(ItemStack.EMPTY);

		if (stack.isEmpty()) {
			visibleRecipes = List.of();
			return;
		}

		visibleRecipes = CarpenterBenchRecipes.forInput(stack);
	}

	private void setupResultSlot(int index) {
		if (!isValidRecipeIndex(index)) {
			resultSlot.set(ItemStack.EMPTY);
			return;
		}

		resultSlot.set(visibleRecipes.get(index).assemble());
		broadcastChanges();
	}
}
