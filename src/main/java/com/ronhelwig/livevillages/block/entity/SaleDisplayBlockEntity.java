package com.ronhelwig.livevillages.block.entity;

import com.ronhelwig.livevillages.sim.SettlementBakerWork;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;

import com.ronhelwig.livevillages.menu.GlassDisplayCaseMenu;
import com.ronhelwig.livevillages.menu.GlassDisplayCaseOpenData;

public abstract class SaleDisplayBlockEntity extends BlockEntity implements Container, ExtendedMenuProvider<GlassDisplayCaseOpenData> {
	public static final int SLOT_COUNT = 6;
	private static final int BLOCK_UPDATE_FLAGS = 3;

	private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

	protected SaleDisplayBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
		super(type, pos, blockState);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		for (int i = 0; i < SLOT_COUNT; i++) {
			items.set(i, ItemStack.EMPTY);
		}
		ContainerHelper.loadAllItems(input, items);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, items);
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable(getBlockState().getBlock().getDescriptionId());
	}

	@Override
	public GlassDisplayCaseOpenData getScreenOpeningData(ServerPlayer player) {
		ServerLevel serverLevel = (ServerLevel) player.level();
		return new GlassDisplayCaseOpenData(
			worldPosition.immutable(),
			SettlementBakerWork.hasBakeryContext(serverLevel, worldPosition),
			SettlementBakerWork.bakeryBountiesAt(serverLevel, worldPosition),
			SettlementBakerWork.bakeryBarterPricesAt(serverLevel, worldPosition)
		);
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		ServerLevel serverLevel = (ServerLevel) player.level();
		return new GlassDisplayCaseMenu(
			syncId,
			inventory,
			this,
			ContainerLevelAccess.create(level, worldPosition),
			new GlassDisplayCaseOpenData(
				worldPosition.immutable(),
				SettlementBakerWork.hasBakeryContext(serverLevel, worldPosition),
				SettlementBakerWork.bakeryBountiesAt(serverLevel, worldPosition),
				SettlementBakerWork.bakeryBarterPricesAt(serverLevel, worldPosition)
			)
		);
	}

	@Override
	public int getContainerSize() {
		return SLOT_COUNT;
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack stack : items) {
			if (!stack.isEmpty()) {
				return false;
			}
		}

		return true;
	}

	@Override
	public ItemStack getItem(int slot) {
		return slot >= 0 && slot < SLOT_COUNT ? items.get(slot) : ItemStack.EMPTY;
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
		if (!removed.isEmpty()) {
			setChanged();
			syncClient();
		}
		return removed;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return ContainerHelper.takeItem(items, slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		if (slot < 0 || slot >= SLOT_COUNT) {
			return;
		}

		items.set(slot, stack);
		if (!stack.isEmpty()) {
			stack.limitSize(getMaxStackSize(stack));
		}
		setChanged();
		syncClient();
	}

	@Override
	public boolean stillValid(Player player) {
		return Container.stillValidBlockEntity(this, player);
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return slot >= 0 && slot < SLOT_COUNT && canAddToDisplaySlot(items.get(slot), stack, getMaxStackSize(stack));
	}

	@Override
	public void clearContent() {
		for (int i = 0; i < SLOT_COUNT; i++) {
			items.set(i, ItemStack.EMPTY);
		}
		setChanged();
		syncClient();
	}

	public int firstEmptySlot() {
		for (int i = 0; i < SLOT_COUNT; i++) {
			if (items.get(i).isEmpty()) {
				return i;
			}
		}

		return -1;
	}

	public static boolean canAddToDisplaySlot(ItemStack existing, ItemStack incoming, int slotLimit) {
		if (incoming.isEmpty()) {
			return false;
		}

		if (existing.isEmpty()) {
			return true;
		}

		if (!ItemStack.isSameItemSameComponents(existing, incoming)) {
			return false;
		}

		int maxStackSize = Math.min(slotLimit, Math.min(existing.getMaxStackSize(), incoming.getMaxStackSize()));
		return existing.getCount() < maxStackSize;
	}

	private void syncClient() {
		if (level != null && !level.isClientSide()) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), BLOCK_UPDATE_FLAGS);
			if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
				SettlementBakerWork.syncDisplayVisualsNear(serverLevel, worldPosition);
			}
		}
	}
}
