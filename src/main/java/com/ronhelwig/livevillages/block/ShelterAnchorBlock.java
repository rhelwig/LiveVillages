package com.ronhelwig.livevillages.block;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;

import com.ronhelwig.livevillages.sim.LiveVillagesSavedData;
import com.ronhelwig.livevillages.sim.SettlementBuildSiteType;
import com.ronhelwig.livevillages.sim.SettlementConstruction;
import com.ronhelwig.livevillages.sim.SettlementGoods;
import com.ronhelwig.livevillages.sim.SettlementState;

public abstract class ShelterAnchorBlock extends HorizontalDirectionalBlock {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	private final String structureName;

	protected ShelterAnchorBlock(BlockBehaviour.Properties properties, String structureName) {
		super(properties);
		this.structureName = structureName;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected abstract MapCodec<? extends HorizontalDirectionalBlock> codec();

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
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
				serverPlayer.sendSystemMessage(Component.literal("No settlement found near this " + structureName + " marker."));
			}

			return;
		}

		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(serverLevel.getServer());
		Map<String, Integer> stock = new LinkedHashMap<>(settlement.get().stock());
		boolean contributeRecipeGoods = !(placer instanceof ServerPlayer serverPlayer) || !serverPlayer.getAbilities().instabuild;

		if (contributeRecipeGoods) {
			recipeGoods().forEach((goodsKey, amount) -> SettlementGoods.addGoods(stock, goodsKey, amount));
		}

		SettlementConstruction.WorkstationBuildResult buildResult = tryStartShelterBuild(
			serverLevel,
			pos,
			state.getValue(FACING),
			settlement.get().id(),
			stock,
			savedData.findBuildSite(settlement.get().id(), buildSiteType(), pos)
		);

		if (buildResult.isStarted() || buildResult.isResumed()) {
			savedData.putSettlement(settlement.get().withStock(stock));
			savedData.putBuildSite(buildResult.buildSite());
			savedData.surveyCache.remove(settlement.get().id());

			if (placer instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendSystemMessage(Component.literal(
					buildResult.isStarted()
						? structureName + " construction started for " + settlement.get().name() + "."
						: structureName + " construction is already planned for " + settlement.get().name() + "."
				));
			}
		} else if (placer instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendSystemMessage(Component.literal("The settlement can't fit a " + structureName + " here."));
		}
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

	protected abstract SettlementConstruction.WorkstationBuildResult tryStartShelterBuild(
		ServerLevel level,
		BlockPos doorPos,
		Direction facing,
		String settlementId,
		Map<String, Integer> stock,
		Optional<com.ronhelwig.livevillages.sim.SettlementBuildSite> existingBuildSite
	);

	protected abstract Map<String, Integer> recipeGoods();

	protected abstract SettlementBuildSiteType buildSiteType();
}
