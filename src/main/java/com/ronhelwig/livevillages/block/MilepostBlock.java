package com.ronhelwig.livevillages.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.ronhelwig.livevillages.block.entity.MilepostBlockEntity;
import com.ronhelwig.livevillages.content.LiveVillagesBlockEntities;

public class MilepostBlock extends BaseEntityBlock {
	public static final MapCodec<MilepostBlock> CODEC = simpleCodec(MilepostBlock::new);
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
	private static final VoxelShape LOWER_SHAPE = Shapes.or(
		Block.box(0.0D, 0.0D, 0.0D, 16.0D, 4.0D, 16.0D),
		Block.box(1.0D, 4.0D, 1.0D, 15.0D, 6.0D, 15.0D),
		Block.box(3.0D, 6.0D, 3.0D, 13.0D, 16.0D, 13.0D)
	);
	private static final VoxelShape MIDDLE_SHAPE = Block.box(3.0D, 0.0D, 3.0D, 13.0D, 16.0D, 13.0D);
	private static final VoxelShape UPPER_SHAPE = Shapes.or(
		Block.box(3.0D, 0.0D, 3.0D, 13.0D, 12.0D, 13.0D),
		Block.box(2.0D, 12.0D, 2.0D, 14.0D, 14.0D, 14.0D),
		Block.box(4.0D, 14.0D, 4.0D, 12.0D, 16.0D, 12.0D)
	);

	public MilepostBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, Part.LOWER));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, PART);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockPos pos = context.getClickedPos();

		if (!upperSpaceClear(context.getLevel(), pos)) {
			return null;
		}

		return defaultBlockState()
			.setValue(FACING, context.getHorizontalDirection().getOpposite())
			.setValue(PART, Part.LOWER);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);

		if (level.isClientSide()) {
			return;
		}

		BlockState middle = state.setValue(PART, Part.MIDDLE);
		BlockState upper = state.setValue(PART, Part.UPPER);
		level.setBlock(pos.above(), middle, UPDATE_ALL);
		level.setBlock(pos.above(2), upper, UPDATE_ALL);

		if (level.getBlockEntity(pos) instanceof MilepostBlockEntity milepostBlockEntity && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
			milepostBlockEntity.refreshLabelsNow(serverLevel);
		}
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
		return state.getValue(PART) == Part.LOWER ? new MilepostBlockEntity(pos, state) : null;
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
		if (level.isClientSide() || state.getValue(PART) != Part.LOWER) {
			return null;
		}

		return createTickerHelper(blockEntityType, LiveVillagesBlockEntities.MILEPOST, MilepostBlockEntity::serverTick);
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return switch (state.getValue(PART)) {
			case LOWER -> level.getBlockState(pos.below()).isSolid();
			case MIDDLE -> isPart(level.getBlockState(pos.below()), Part.LOWER);
			case UPPER -> isPart(level.getBlockState(pos.below()), Part.MIDDLE);
		};
	}

	@Override
	protected BlockState updateShape(
		BlockState state,
		LevelReader level,
		ScheduledTickAccess scheduledTickAccess,
		BlockPos pos,
		Direction direction,
		BlockPos neighborPos,
		BlockState neighborState,
		RandomSource random
	) {
		if ((direction == Direction.DOWN || direction == Direction.UP) && !isStackIntact(state, level, pos)) {
			return Blocks.AIR.defaultBlockState();
		}

		return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch (state.getValue(PART)) {
			case LOWER -> LOWER_SHAPE;
			case MIDDLE -> MIDDLE_SHAPE;
			case UPPER -> UPPER_SHAPE;
		};
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return getShape(state, level, pos, context);
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	public static boolean isLowerPart(BlockState state) {
		return state.is(com.ronhelwig.livevillages.content.LiveVillagesBlocks.MILEPOST)
			&& state.hasProperty(PART)
			&& state.getValue(PART) == Part.LOWER;
	}

	private static boolean upperSpaceClear(LevelReader level, BlockPos pos) {
		BlockState aboveState = level.getBlockState(pos.above());
		BlockState aboveTwoState = level.getBlockState(pos.above(2));
		return (aboveState.isAir() || aboveState.canBeReplaced())
			&& (aboveTwoState.isAir() || aboveTwoState.canBeReplaced());
	}

	private static boolean isPart(BlockState state, Part part) {
		return state.getBlock() instanceof MilepostBlock
			&& state.hasProperty(PART)
			&& state.getValue(PART) == part;
	}

	private boolean isStackIntact(BlockState state, LevelReader level, BlockPos pos) {
		if (!canSurvive(state, level, pos)) {
			return false;
		}

		return switch (state.getValue(PART)) {
			case LOWER -> isPart(level.getBlockState(pos.above()), Part.MIDDLE);
			case MIDDLE -> isPart(level.getBlockState(pos.above()), Part.UPPER);
			case UPPER -> true;
		};
	}

	public enum Part implements StringRepresentable {
		LOWER("lower"),
		MIDDLE("middle"),
		UPPER("upper");

		private final String name;

		Part(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}
}
