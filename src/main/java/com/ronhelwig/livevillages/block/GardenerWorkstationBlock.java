package com.ronhelwig.livevillages.block;

import java.util.LinkedHashMap;
import java.util.Map;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

import com.ronhelwig.livevillages.sim.LiveVillagesSavedData;
import com.ronhelwig.livevillages.sim.SettlementBuildSiteType;
import com.ronhelwig.livevillages.sim.SettlementConstruction;
import com.ronhelwig.livevillages.sim.SettlementState;
import com.ronhelwig.livevillages.sim.SettlementVillagers;

public class GardenerWorkstationBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<GardenerWorkstationBlock> CODEC = simpleCodec(GardenerWorkstationBlock::new);

	public GardenerWorkstationBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
				serverPlayer.sendSystemMessage(Component.literal("No settlement found near this Gardener Workstation."));
			}

			return;
		}

		Direction facing = state.getValue(FACING);
		Map<String, Integer> stock = new LinkedHashMap<>(settlement.stock());
		SettlementConstruction.WorkstationBuildResult buildResult = SettlementConstruction.tryStartGardenerShedAtWorkstation(
			serverLevel,
			pos,
			facing,
			settlement.id(),
			stock,
			savedData.findBuildSite(settlement.id(), SettlementBuildSiteType.GARDENER_SHED, pos)
		);

		if (buildResult.isStarted()) {
			savedData.putBuildSite(buildResult.buildSite());
			SettlementVillagers.ensureWorkforce(serverLevel, settlement);

			if (placer instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendSystemMessage(Component.literal("Gardener's Shed construction started around this Gardener Workstation."));
			}
		} else if (buildResult.isResumed()) {
			savedData.putBuildSite(buildResult.buildSite());
			SettlementVillagers.ensureWorkforce(serverLevel, settlement);

			if (placer instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendSystemMessage(Component.literal("Gardener's Shed construction is already planned around this Gardener Workstation."));
			}
		} else if (buildResult.isBlocked() && placer instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendSystemMessage(Component.literal("The settlement can't build a Gardener's Shed here."));
		}
	}
}
