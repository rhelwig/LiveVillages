package com.ronhelwig.livevillages.menu;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import com.ronhelwig.livevillages.block.entity.TradeBoardBlockEntity;
import com.ronhelwig.livevillages.sim.LiveVillagesSavedData;
import com.ronhelwig.livevillages.sim.SettlementConstruction;
import com.ronhelwig.livevillages.sim.SettlementKind;
import com.ronhelwig.livevillages.sim.SettlementState;
import com.ronhelwig.livevillages.sim.SettlementVillagers;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;

public final class SettlementTradeAccess {
	private static final double LINK_RADIUS_BLOCKS = 128.0D;

	private SettlementTradeAccess() {
	}

	public static Optional<TradeBoardOpenData> openData(ServerLevel level, BlockPos accessPos) {
		return resolveSettlement(level, accessPos).map(settlement -> new TradeBoardOpenData(
			accessPos.immutable(),
			createSettlementView(level, settlement)
		));
	}

	public static boolean openTradeMenu(ServerPlayer player, BlockPos accessPos, Component title) {
		Optional<TradeBoardOpenData> openData = openData((ServerLevel) player.level(), accessPos);
		if (openData.isEmpty()) {
			return false;
		}

		player.openMenu(new TradeAccessMenuProvider(title, openData.get()));
		return true;
	}

	public static Optional<SettlementState> resolveSettlement(ServerLevel level, BlockPos accessPos) {
		if (level.getBlockEntity(accessPos) instanceof TradeBoardBlockEntity tradeBoard) {
			return Optional.of(tradeBoard.resolveSettlement(level));
		}

		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(level.getServer());
		return SettlementConstruction.findWorkstationSettlement(level, accessPos)
			.or(() -> SettlementConstruction.findSettlementContainingPosition(level, accessPos))
			.or(() -> savedData.findNearestSettlement(
				level.dimension(),
				accessPos,
				LINK_RADIUS_BLOCKS,
				settlement -> settlement.kind() != SettlementKind.OUTPOST
			));
	}

	public static TradeBoardSettlementView createSettlementView(ServerLevel level, SettlementState settlement) {
		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(level.getServer());
		List<com.ronhelwig.livevillages.sim.SettlementBuildSite> buildSites = savedData.getBuildSitesForSettlement(settlement.id());
		return TradeBoardLogic.createSettlementView(
			settlement,
			savedData.getRoutesForSettlement(settlement.id()),
			settlementId -> savedData.getSettlement(settlementId).map(SettlementState::name).orElse("Unknown"),
			5,
			3,
			3,
			SettlementVillagers.nearbyProfessionPopulation(level, settlement),
			TradeBoardLogic.constructionTradeDemand(buildSites),
			buildSites
		);
	}

	private record TradeAccessMenuProvider(Component getDisplayName, TradeBoardOpenData openData) implements ExtendedMenuProvider<TradeBoardOpenData> {
		@Override
		public TradeBoardOpenData getScreenOpeningData(ServerPlayer player) {
			return openData;
		}

		@Override
		public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
			return new TradeBoardMenu(syncId, inventory, openData);
		}
	}
}
