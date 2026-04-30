package com.ronhelwig.livevillages.block;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;

import com.ronhelwig.livevillages.sim.LiveVillagesSavedData;
import com.ronhelwig.livevillages.sim.SettlementBuildSiteType;
import com.ronhelwig.livevillages.sim.SettlementConstruction;
import com.ronhelwig.livevillages.sim.SettlementState;

public class LighthouseBlock extends Block {
	public static final MapCodec<LighthouseBlock> CODEC = simpleCodec(LighthouseBlock::new);

	public LighthouseBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	public MapCodec<LighthouseBlock> codec() {
		return CODEC;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);

		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}

		Optional<SettlementState> settlement = SettlementConstruction.findWorkstationSettlement(serverLevel, pos);

		if (settlement.isEmpty()) {
			if (placer instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendSystemMessage(Component.literal("No settlement found near this Lighthouse marker."));
			}

			return;
		}

		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(serverLevel.getServer());
		Map<String, Integer> stock = new LinkedHashMap<>(settlement.get().stock());
		SettlementConstruction.WorkstationBuildResult buildResult = SettlementConstruction.tryStartLighthouseAtMarker(
			serverLevel,
			pos,
			settlement.get().id(),
			stock,
			savedData.findBuildSite(settlement.get().id(), SettlementBuildSiteType.LIGHTHOUSE, pos)
		);

		if (buildResult.isStarted() || buildResult.isResumed()) {
			savedData.putBuildSite(buildResult.buildSite());
			savedData.surveyCache.remove(settlement.get().id());
		}

		if (!(placer instanceof ServerPlayer serverPlayer)) {
			return;
		}

		serverPlayer.sendSystemMessage(Component.literal(
			buildResult.isStarted()
				? "Lighthouse construction started from this marker."
				: buildResult.isResumed()
				? "Lighthouse construction is already planned from this marker."
				: "The settlement cannot fit a Lighthouse here."
		));
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return level.getBlockState(pos.below()).isSolid();
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	@Override
	public boolean isCollisionShapeFullBlock(BlockState state, BlockGetter level, BlockPos pos) {
		return true;
	}
}
