package com.ronhelwig.livevillages.block.entity;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;

import com.ronhelwig.livevillages.content.LiveVillagesBlockEntities;
import com.ronhelwig.livevillages.menu.TradeBoardLogic;
import com.ronhelwig.livevillages.menu.TradeBoardMenu;
import com.ronhelwig.livevillages.menu.TradeBoardOpenData;
import com.ronhelwig.livevillages.sim.LiveVillagesSavedData;
import com.ronhelwig.livevillages.sim.SettlementBuildSite;
import com.ronhelwig.livevillages.sim.SettlementConstruction;
import com.ronhelwig.livevillages.sim.SettlementKind;
import com.ronhelwig.livevillages.sim.SettlementNamer;
import com.ronhelwig.livevillages.sim.SettlementProject;
import com.ronhelwig.livevillages.sim.SettlementState;
import com.ronhelwig.livevillages.sim.SettlementVillagers;

public class TradeBoardBlockEntity extends BlockEntity implements ExtendedMenuProvider<TradeBoardOpenData> {
	private static final double LINK_RADIUS_BLOCKS = 128.0D;
	private static final int BLOCK_UPDATE_FLAGS = 3;
	private static final long DISPLAY_SYNC_INTERVAL_TICKS = 40L;

	private String linkedSettlementId;
	private String displaySettlementName;

	public TradeBoardBlockEntity(BlockPos pos, BlockState blockState) {
		super(LiveVillagesBlockEntities.TRADE_BOARD, pos, blockState);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		linkedSettlementId = input.getString("linked_settlement_id").orElse(null);
		displaySettlementName = input.getString("display_settlement_name").orElse(null);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);

		if (linkedSettlementId != null && !linkedSettlementId.isBlank()) {
			output.putString("linked_settlement_id", linkedSettlementId);
		}

		if (displaySettlementName != null && !displaySettlementName.isBlank()) {
			output.putString("display_settlement_name", displaySettlementName);
		}
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.live-villages.trade_board");
	}

	@Override
	public TradeBoardOpenData getScreenOpeningData(ServerPlayer player) {
		return createOpenData((ServerLevel) player.level());
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		ServerLevel serverLevel = (ServerLevel) Objects.requireNonNull(level, "Trade Board menu opened without a server level");
		return new TradeBoardMenu(syncId, inventory, createOpenData(serverLevel));
	}

	@Override
	public void setLevel(Level level) {
		super.setLevel(level);

		if (level instanceof ServerLevel serverLevel && linkedSettlementId != null && (displaySettlementName == null || displaySettlementName.isBlank())) {
			LiveVillagesSavedData.get(serverLevel.getServer()).getSettlement(linkedSettlementId)
				.ifPresent(settlement -> syncLinkedSettlement(settlement.id(), settlement.name()));
		}
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}

	public SettlementState resolveSettlement(ServerLevel serverLevel) {
		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(serverLevel.getServer());

		if (linkedSettlementId != null) {
			var linkedSettlement = savedData.getSettlement(linkedSettlementId);

			if (linkedSettlement.isPresent()) {
				syncLinkedSettlement(linkedSettlement.get().id(), linkedSettlement.get().name());
				return linkedSettlement.get();
			}
		}

		SettlementState settlement = SettlementConstruction.findSettlementContainingPosition(serverLevel, worldPosition)
			.or(() -> savedData.findNearestSettlement(
				serverLevel.dimension(),
				worldPosition,
				LINK_RADIUS_BLOCKS,
				existingSettlement -> existingSettlement.kind() != SettlementKind.OUTPOST
			))
			.orElseGet(() -> createCustomSettlement(serverLevel, savedData));

		syncLinkedSettlement(settlement.id(), settlement.name());
		return settlement;
	}

	public void linkSettlement(SettlementState settlement) {
		syncLinkedSettlement(settlement.id(), settlement.name());
	}

	public SettlementState createAndLinkCustomSettlement(ServerLevel serverLevel) {
		SettlementState settlement = createCustomSettlement(serverLevel, LiveVillagesSavedData.get(serverLevel.getServer()));
		syncLinkedSettlement(settlement.id(), settlement.name());
		return settlement;
	}

	public String displaySettlementName() {
		return displaySettlementName;
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, TradeBoardBlockEntity blockEntity) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}

		long tick = serverLevel.getServer().getTickCount();
		long stagger = Math.floorMod(pos.asLong(), DISPLAY_SYNC_INTERVAL_TICKS);

		if ((tick % DISPLAY_SYNC_INTERVAL_TICKS) != stagger) {
			return;
		}

		if (blockEntity.linkedSettlementId == null || blockEntity.linkedSettlementId.isBlank()) {
			blockEntity.resolveSettlement(serverLevel);
			return;
		}

		LiveVillagesSavedData.get(serverLevel.getServer()).getSettlement(blockEntity.linkedSettlementId)
			.ifPresentOrElse(
				settlement -> {
					if (!Objects.equals(blockEntity.displaySettlementName, settlement.name())) {
						blockEntity.syncLinkedSettlement(settlement.id(), settlement.name());
					}
				},
				() -> blockEntity.resolveSettlement(serverLevel)
			);
	}

	private SettlementState createCustomSettlement(ServerLevel serverLevel, LiveVillagesSavedData savedData) {
		String dimensionKey = serverLevel.dimension().identifier().toString().replace(':', '_').replace('/', '_');
		String settlementId = "custom:" + dimensionKey + "_" + worldPosition.getX() + "_" + worldPosition.getZ();
		String settlementName = SettlementNamer.generateUniqueName(SettlementKind.CUSTOM, serverLevel.dimension(), worldPosition, savedData.getSettlements());
		SettlementState settlement = new SettlementState(
			settlementId,
			settlementName,
			serverLevel.dimension(),
			worldPosition.immutable(),
			SettlementKind.CUSTOM,
			Map.of(),
			Map.of(),
			Map.of(),
			0,
			1.0D,
			0.0D,
			0,
			0.0D,
			java.util.List.<SettlementProject>of(),
			serverLevel.getServer().getTickCount(),
			serverLevel.getServer().getTickCount()
		);
		savedData.putSettlement(settlement);
		return settlement;
	}

	private TradeBoardOpenData createOpenData(ServerLevel serverLevel) {
		SettlementState settlement = reconcileLoadedSettlement(serverLevel, resolveSettlement(serverLevel));
		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(serverLevel.getServer());
		List<SettlementBuildSite> buildSites = savedData.getBuildSitesForSettlement(settlement.id());
		return new TradeBoardOpenData(
			worldPosition.immutable(),
			TradeBoardLogic.createSettlementView(
				settlement,
				savedData.getRoutesForSettlement(settlement.id()),
				settlementId -> savedData.getSettlement(settlementId).map(SettlementState::name).orElse("Unknown"),
				5,
				3,
				3,
				SettlementVillagers.nearbyProfessionPopulation(serverLevel, settlement),
				TradeBoardLogic.constructionTradeDemand(buildSites),
				buildSites
			)
		);
	}

	private SettlementState reconcileLoadedSettlement(ServerLevel serverLevel, SettlementState settlement) {
		if (!SettlementVillagers.usesActualVillagers(settlement) || !serverLevel.isLoaded(settlement.center())) {
			return settlement;
		}

		SettlementConstruction.InfrastructureSurvey infrastructure = SettlementConstruction.survey(serverLevel, settlement);
		int actualHousing = infrastructure.housingCapacity();
		boolean shouldSyncExactHousing = settlement.kind() == SettlementKind.CUSTOM;
		int reconciledHousing = shouldSyncExactHousing
			? actualHousing
			: Math.max(settlement.housingCapacity(), actualHousing);

		if (reconciledHousing == settlement.housingCapacity()) {
			return settlement;
		}

		SettlementState updatedSettlement = settlement.withHousingCapacity(reconciledHousing);
		LiveVillagesSavedData.get(serverLevel.getServer()).putSettlement(updatedSettlement);
		return updatedSettlement;
	}

	private void syncLinkedSettlement(String settlementId, String settlementName) {
		String normalizedName = settlementName == null || settlementName.isBlank() ? null : settlementName;

		if (Objects.equals(linkedSettlementId, settlementId) && Objects.equals(displaySettlementName, normalizedName)) {
			return;
		}

		linkedSettlementId = settlementId;
		displaySettlementName = normalizedName;
		setChanged();

		if (level != null && !level.isClientSide()) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), BLOCK_UPDATE_FLAGS);
		}
	}
}
