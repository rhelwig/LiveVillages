package com.ronhelwig.livevillages.block;

import java.util.LinkedHashMap;
import java.util.Map;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import com.ronhelwig.livevillages.menu.CarpenterBenchMenu;
import com.ronhelwig.livevillages.sim.LiveVillagesSavedData;
import com.ronhelwig.livevillages.sim.SettlementBuildSiteType;
import com.ronhelwig.livevillages.sim.SettlementConstruction;
import com.ronhelwig.livevillages.sim.SettlementState;
import com.ronhelwig.livevillages.sim.SettlementVillagers;

public class CarpenterBenchBlock extends Block {
	public static final MapCodec<CarpenterBenchBlock> CODEC = simpleCodec(CarpenterBenchBlock::new);
	private static final Component CONTAINER_TITLE = Component.translatable("container.live-villages.carpenter_bench");

	public CarpenterBenchBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);

		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}

		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(serverLevel.getServer());
		SettlementState settlement = SettlementConstruction.findWorkstationSettlement(serverLevel, pos).orElse(null);

		if (settlement == null) {
			if (placer instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendSystemMessage(Component.literal("No settlement found near this Carpenter's Bench."));
			}

			return;
		}

		Direction facing = placer == null ? Direction.NORTH : placer.getDirection().getOpposite();
		Map<String, Integer> stock = new LinkedHashMap<>(settlement.stock());
		SettlementConstruction.WorkstationBuildResult buildResult = SettlementConstruction.tryStartCarpenterWorkshopAtWorkstation(
			serverLevel,
			pos,
			facing,
			settlement.id(),
			stock,
			savedData.findBuildSite(settlement.id(), SettlementBuildSiteType.CARPENTER_WORKSHOP, pos)
		);

		if (buildResult.isStarted()) {
			savedData.putBuildSite(buildResult.buildSite());
			SettlementVillagers.ensureWorkforce(serverLevel, settlement);

			if (placer instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendSystemMessage(Component.literal("Carpenter's Workshop construction started around this Carpenter's Bench."));
			}
		} else if (buildResult.isResumed()) {
			savedData.putBuildSite(buildResult.buildSite());
			SettlementVillagers.ensureWorkforce(serverLevel, settlement);

			if (placer instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendSystemMessage(Component.literal("Carpenter's Workshop construction is already planned around this Carpenter's Bench."));
			}
		} else if (buildResult.isBlocked() && placer instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendSystemMessage(Component.literal("The settlement can't build a Carpenter's Workshop here."));
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		player.openMenu(new SimpleMenuProvider(
			(syncId, inventory, menuPlayer) -> new CarpenterBenchMenu(syncId, inventory, ContainerLevelAccess.create(level, pos)),
			CONTAINER_TITLE
		));
		return InteractionResult.SUCCESS_SERVER;
	}
}
