package com.ronhelwig.livevillages.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import com.ronhelwig.livevillages.content.LiveVillagesMenus;

public class TradeBoardMenu extends AbstractContainerMenu {
	private final BlockPos boardPos;
	private TradeBoardSettlementView settlement;

	public TradeBoardMenu(int syncId, Inventory inventory, TradeBoardOpenData openData) {
		super(LiveVillagesMenus.TRADE_BOARD, syncId);
		this.boardPos = openData.boardPos();
		this.settlement = openData.settlement();
	}

	public BlockPos boardPos() {
		return boardPos;
	}

	public TradeBoardSettlementView settlement() {
		return settlement;
	}

	public void updateSettlement(TradeBoardSettlementView updatedSettlement) {
		this.settlement = updatedSettlement;
	}

	@Override
	public boolean clickMenuButton(Player player, int buttonId) {
		if (player instanceof ServerPlayer serverPlayer) {
			return TradeBoardTrading.handleTradeButton(serverPlayer, boardPos, buttonId);
		}

		return false;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		return player.blockPosition().distSqr(boardPos) <= 64.0D;
	}
}
