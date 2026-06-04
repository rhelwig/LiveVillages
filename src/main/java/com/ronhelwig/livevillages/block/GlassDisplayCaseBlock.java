package com.ronhelwig.livevillages.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

import com.ronhelwig.livevillages.block.entity.GlassDisplayCaseBlockEntity;
import com.ronhelwig.livevillages.sim.SettlementBakerWork;

public class GlassDisplayCaseBlock extends BaseEntityBlock {
	public static final MapCodec<GlassDisplayCaseBlock> CODEC = simpleCodec(GlassDisplayCaseBlock::new);
	public static final net.minecraft.world.level.block.state.properties.EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

	public GlassDisplayCaseBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
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
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new GlassDisplayCaseBlockEntity(pos, state);
	}

	@Override
	public void destroy(LevelAccessor level, BlockPos pos, BlockState state) {
		if (level instanceof Level world && world.getBlockEntity(pos) instanceof GlassDisplayCaseBlockEntity displayCase) {
			Containers.dropContents(world, pos, displayCase);
			world.updateNeighbourForOutputSignal(pos, this);
			if (world instanceof ServerLevel serverLevel) {
				com.ronhelwig.livevillages.sim.SettlementBakerWork.clearDisplayVisualsAt(serverLevel, pos);
			}
		}

		super.destroy(level, pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		if (!(level instanceof ServerLevel)) {
			return InteractionResult.PASS;
		}

		if (level.getBlockEntity(pos) instanceof GlassDisplayCaseBlockEntity displayCase) {
			SettlementBakerWork.reconcileBakeryIngredientDisplaysNear((ServerLevel) level, pos);
			player.openMenu(displayCase);
			return InteractionResult.SUCCESS_SERVER;
		}

		return InteractionResult.PASS;
	}
}
