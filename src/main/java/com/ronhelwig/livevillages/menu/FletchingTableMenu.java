package com.ronhelwig.livevillages.menu;

import java.util.List;
import java.util.Optional;

import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import com.ronhelwig.livevillages.content.LiveVillagesMenus;
import com.ronhelwig.livevillages.sim.SettlementConstruction;
import com.ronhelwig.livevillages.sim.SettlementState;

public class FletchingTableMenu extends AbstractContainerMenu {
	public static final int SHAFT_SLOT = 0;
	public static final int FLETCHING_SLOT = 1;
	public static final int HEAD_SLOT = 2;
	public static final int RESULT_SLOT = 3;
	public static final int SHAFT_SLOT_X = 28;
	public static final int FLETCHING_SLOT_X = 60;
	public static final int HEAD_SLOT_X = 92;
	public static final int INPUT_ROW_Y = 36;
	public static final int RESULT_SLOT_X = 176;
	public static final int RESULT_SLOT_Y = 36;
	public static final int PLAYER_INV_X = 29;
	public static final int PLAYER_INV_Y = 136;
	private static final int INV_SLOT_START = 4;
	private static final int INV_SLOT_END = 31;
	private static final int USE_ROW_SLOT_START = 31;
	private static final int USE_ROW_SLOT_END = 40;

	private final ContainerLevelAccess access;
	private final DataSlot selectedRecipeIndex = DataSlot.standalone();
	private final DataSlot settlementTier = DataSlot.standalone();
	private final List<FletchingTableRecipe> visibleRecipes = FletchingTableRecipes.all();
	private final ResultContainer resultContainer = new ResultContainer();
	private long lastSoundTime;
	private Runnable slotUpdateListener = () -> {
	};

	public final Container container;
	final Slot shaftSlot;
	final Slot fletchingSlot;
	final Slot headSlot;
	final Slot resultSlot;

	public FletchingTableMenu(int syncId, Inventory inventory) {
		this(syncId, inventory, ContainerLevelAccess.NULL);
	}

	public FletchingTableMenu(int syncId, Inventory inventory, ContainerLevelAccess access) {
		super(LiveVillagesMenus.FLETCHING_TABLE, syncId);
		this.access = access;
		this.container = new SimpleContainer(3) {
			@Override
			public void setChanged() {
				super.setChanged();
				FletchingTableMenu.this.slotsChanged(this);
				FletchingTableMenu.this.slotUpdateListener.run();
			}
		};

		this.shaftSlot = addSlot(new Slot(container, SHAFT_SLOT, SHAFT_SLOT_X, INPUT_ROW_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return FletchingTableRecipes.acceptsShaft(stack);
			}
		});
		this.fletchingSlot = addSlot(new Slot(container, FLETCHING_SLOT, FLETCHING_SLOT_X, INPUT_ROW_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return FletchingTableRecipes.acceptsFletching(stack);
			}
		});
		this.headSlot = addSlot(new Slot(container, HEAD_SLOT, HEAD_SLOT_X, INPUT_ROW_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return FletchingTableRecipes.acceptsHead(stack);
			}
		});
		this.resultSlot = addSlot(new Slot(resultContainer, 1, RESULT_SLOT_X, RESULT_SLOT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}

			@Override
			public void onTake(Player player, ItemStack stack) {
				stack.onCraftedBy(player, stack.getCount());
				consumeSelectedRecipeInputs();
				selectBestRecipe();
				setupResultSlot(selectedRecipeIndex.get());

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
		addStandardInventorySlots(inventory, PLAYER_INV_X, PLAYER_INV_Y);
		addDataSlot(selectedRecipeIndex);
		access.execute((level, pos) -> {
			if (level instanceof ServerLevel serverLevel) {
				Optional<SettlementState> settlement = SettlementConstruction.findWorkstationSettlement(serverLevel, pos);
				settlementTier.set(settlement.map(SettlementState::tier).orElse(1));
			}
		});
		addDataSlot(settlementTier);
		selectedRecipeIndex.set(-1);
	}

	public int getSelectedRecipeIndex() {
		return selectedRecipeIndex.get();
	}

	public List<FletchingTableRecipe> getVisibleRecipes() {
		return visibleRecipes;
	}

	public int getNumberOfVisibleRecipes() {
		return visibleRecipes.size();
	}

	public boolean isRecipeCraftable(int index) {
		return isValidRecipeIndex(index) && visibleRecipes.get(index).matches(shaftSlot.getItem(), fletchingSlot.getItem(), headSlot.getItem());
	}

	public int settlementTier() {
		return Math.max(1, settlementTier.get());
	}

	public void registerUpdateListener(Runnable listener) {
		this.slotUpdateListener = listener;
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(access, player, Blocks.FLETCHING_TABLE);
	}

	@Override
	public boolean clickMenuButton(Player player, int buttonId) {
		return false;
	}

	@Override
	public void slotsChanged(Container container) {
		selectBestRecipe();
		setupResultSlot(selectedRecipeIndex.get());
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
			movedStack = stack.copy();

			if (slotIndex == RESULT_SLOT) {
				if (!moveItemStackTo(stack, INV_SLOT_START, USE_ROW_SLOT_END, true)) {
					return ItemStack.EMPTY;
				}

				slot.onQuickCraft(stack, movedStack);
			} else if (slotIndex == SHAFT_SLOT || slotIndex == FLETCHING_SLOT || slotIndex == HEAD_SLOT) {
				if (!moveItemStackTo(stack, INV_SLOT_START, USE_ROW_SLOT_END, false)) {
					return ItemStack.EMPTY;
				}
			} else if (FletchingTableRecipes.acceptsShaft(stack)) {
				if (!moveItemStackTo(stack, SHAFT_SLOT, FLETCHING_SLOT, false)) {
					return ItemStack.EMPTY;
				}
			} else if (FletchingTableRecipes.acceptsFletching(stack)) {
				if (!moveItemStackTo(stack, FLETCHING_SLOT, HEAD_SLOT, false)) {
					return ItemStack.EMPTY;
				}
			} else if (FletchingTableRecipes.acceptsHead(stack)) {
				if (!moveItemStackTo(stack, HEAD_SLOT, RESULT_SLOT, false)) {
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

	private void consumeSelectedRecipeInputs() {
		if (!isValidRecipeIndex(selectedRecipeIndex.get())) {
			return;
		}

		FletchingTableRecipe recipe = visibleRecipes.get(selectedRecipeIndex.get());

		if (!recipe.matches(shaftSlot.getItem(), fletchingSlot.getItem(), headSlot.getItem())) {
			return;
		}

		shaftSlot.remove(recipe.shaftCount());
		fletchingSlot.remove(recipe.fletchingCount());
		headSlot.remove(recipe.headCount());
	}

	private void selectBestRecipe() {
		int headSelectedIndex = FletchingTableRecipes.recipeIndexForHead(headSlot.getItem());
		if (headSelectedIndex >= 0) {
			selectedRecipeIndex.set(headSelectedIndex);
			return;
		}

		selectedRecipeIndex.set(FletchingTableRecipes.firstCraftableRecipeIndex(shaftSlot.getItem(), fletchingSlot.getItem(), headSlot.getItem()));
	}

	private void setupResultSlot(int index) {
		if (!isRecipeCraftable(index)) {
			resultSlot.set(ItemStack.EMPTY);
			broadcastChanges();
			return;
		}

		resultSlot.set(visibleRecipes.get(index).assemble());
		broadcastChanges();
	}
}
