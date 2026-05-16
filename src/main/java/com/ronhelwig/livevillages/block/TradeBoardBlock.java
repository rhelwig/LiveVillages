package com.ronhelwig.livevillages.block;

import java.util.LinkedHashMap;
import java.util.Map;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.ronhelwig.livevillages.block.entity.TradeBoardBlockEntity;
import com.ronhelwig.livevillages.content.LiveVillagesBlockEntities;
import com.ronhelwig.livevillages.sim.LiveVillagesSavedData;
import com.ronhelwig.livevillages.sim.SettlementBuildSiteType;
import com.ronhelwig.livevillages.sim.SettlementConstruction;
import com.ronhelwig.livevillages.sim.SettlementState;
import com.ronhelwig.livevillages.sim.SettlementVillagers;

public class TradeBoardBlock extends BaseEntityBlock {
	public static final MapCodec<TradeBoardBlock> CODEC = simpleCodec(TradeBoardBlock::new);
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	private static final VoxelShape NORTH_SOUTH_SHAPE = Shapes.or(
		Block.box(3.0D, 4.0D, 7.0D, 13.0D, 14.0D, 9.0D),
		Block.box(1.0D, 0.0D, 8.0D, 3.0D, 16.0D, 10.0D),
		Block.box(13.0D, 0.0D, 8.0D, 15.0D, 16.0D, 10.0D)
	);
	private static final VoxelShape EAST_WEST_SHAPE = Shapes.or(
		Block.box(7.0D, 4.0D, 3.0D, 9.0D, 14.0D, 13.0D),
		Block.box(8.0D, 0.0D, 1.0D, 10.0D, 16.0D, 3.0D),
		Block.box(8.0D, 0.0D, 13.0D, 10.0D, 16.0D, 15.0D)
	);

	public TradeBoardBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		for (Direction direction : context.getNearestLookingDirections()) {
			if (direction.getAxis() != Direction.Axis.Y) {
				return defaultBlockState().setValue(FACING, direction.getOpposite());
			}
		}

		return defaultBlockState();
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
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return level.getBlockState(pos.below()).isSolid();
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
		if (direction == Direction.DOWN && !canSurvive(state, level, pos)) {
			return Blocks.AIR.defaultBlockState();
		}

		return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch (state.getValue(FACING)) {
			case EAST, WEST -> EAST_WEST_SHAPE;
			default -> NORTH_SOUTH_SHAPE;
		};
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return getShape(state, level, pos, context);
	}

	@Override
	protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
		return Shapes.block();
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TradeBoardBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
		return level.isClientSide()
			? null
			: createTickerHelper(blockEntityType, LiveVillagesBlockEntities.TRADE_BOARD, TradeBoardBlockEntity::serverTick);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);

		if (!(level instanceof ServerLevel serverLevel) || !(level.getBlockEntity(pos) instanceof TradeBoardBlockEntity tradeBoard)) {
			return;
		}

		SettlementConstruction.TradeBoardPlacementDecision placementDecision = SettlementConstruction.evaluateTradeBoardPlacement(serverLevel, pos);

		if (placementDecision.blocked()) {
			serverLevel.removeBlock(pos, false);

			if (placer instanceof Player player && !player.getAbilities().instabuild) {
				ItemStack refund = new ItemStack(asItem());
				if (!player.getInventory().add(refund) && placer instanceof ServerPlayer serverPlayer) {
					serverPlayer.drop(refund, false);
				}
			}

			if (placer instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendSystemMessage(Component.literal(placementDecision.statusMessage()));
			}

			return;
		}

		SettlementState settlement = placementDecision.settlement()
			.map(existingSettlement -> {
				tradeBoard.linkSettlement(existingSettlement);
				return existingSettlement;
			})
			.orElseGet(() -> tradeBoard.createAndLinkCustomSettlement(serverLevel));
		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(serverLevel.getServer());
		Map<String, Integer> stock = new LinkedHashMap<>(settlement.stock());
		SettlementConstruction.WorkstationBuildResult buildResult = SettlementConstruction.tryStartTradingPostAtWorkstation(
			serverLevel,
			pos,
			state.getValue(FACING),
			settlement.id(),
			stock,
			savedData.findBuildSite(settlement.id(), SettlementBuildSiteType.TRADING_POST, pos)
		);

		if (buildResult.isStarted()) {
			savedData.putBuildSite(buildResult.buildSite());
			SettlementVillagers.ensureWorkforce(serverLevel, settlement);
		} else if (buildResult.isResumed()) {
			savedData.putBuildSite(buildResult.buildSite());
			SettlementVillagers.ensureWorkforce(serverLevel, settlement);
		}

		if (placer instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendSystemMessage(Component.literal(
				"Trade Board linked to " + settlement.name() + " (" + settlement.kind().getSerializedName() + ")."
			));

			if (placementDecision.foundsNewSettlement()) {
				serverPlayer.sendSystemMessage(Component.literal("Founded a new Tier 1 settlement from this Trade Board."));
			}

			if (buildResult.isStarted()) {
				serverPlayer.sendSystemMessage(Component.literal("Trading Post construction started around this Trade Board."));
			} else if (buildResult.isResumed()) {
				serverPlayer.sendSystemMessage(Component.literal("Trading Post construction is already planned around this Trade Board."));
			} else if (buildResult.isBlocked()) {
				serverPlayer.sendSystemMessage(Component.literal("The settlement can't build a Trading Post here."));
			}
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		if (level.getBlockEntity(pos) instanceof TradeBoardBlockEntity tradeBoard) {
			player.openMenu(tradeBoard);
			return InteractionResult.SUCCESS_SERVER;
		}

		return InteractionResult.PASS;
	}
}
