package com.ronhelwig.livevillages.block;

import java.util.LinkedHashMap;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.ronhelwig.livevillages.block.entity.BakersCounterBlockEntity;
import com.ronhelwig.livevillages.block.entity.SaleDisplayBlockEntity;
import com.ronhelwig.livevillages.sim.LiveVillagesSavedData;
import com.ronhelwig.livevillages.sim.SettlementBakerWork;
import com.ronhelwig.livevillages.sim.SettlementBuildSiteType;
import com.ronhelwig.livevillages.sim.SettlementConstruction;
import com.ronhelwig.livevillages.sim.SettlementState;
import com.ronhelwig.livevillages.sim.SettlementVillagers;

public class BakersCounterBlock extends BaseEntityBlock {
	public static final MapCodec<BakersCounterBlock> CODEC = simpleCodec(BakersCounterBlock::new);
	public static final VoxelShape SHAPE = Shapes.block();
	public static final net.minecraft.world.level.block.state.properties.EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

	public BakersCounterBlock(BlockBehaviour.Properties properties) {
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
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new BakersCounterBlockEntity(pos, state);
	}

	@Override
	public void destroy(LevelAccessor level, BlockPos pos, BlockState state) {
		if (level instanceof Level world && world.getBlockEntity(pos) instanceof SaleDisplayBlockEntity display) {
			Containers.dropContents(world, pos, display);
			world.updateNeighbourForOutputSignal(pos, this);
			if (world instanceof ServerLevel serverLevel) {
				SettlementBakerWork.clearDisplayVisualsAt(serverLevel, pos);
			}
		}

		super.destroy(level, pos, state);
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
				serverPlayer.sendSystemMessage(Component.literal("No settlement found near this Baker's Counter."));
			}

			return;
		}

		Direction facing = state.getValue(FACING);
		SettlementConstruction.WorkstationBuildResult buildResult = SettlementConstruction.tryStartBakeryAtWorkstation(
			serverLevel,
			pos,
			facing,
			settlement.id(),
			new LinkedHashMap<>(settlement.stock()),
			savedData.findBuildSite(settlement.id(), SettlementBuildSiteType.BAKERY, pos)
		);

		if (buildResult.isStarted()) {
			savedData.putBuildSite(buildResult.buildSite());
			SettlementVillagers.ensureWorkforce(serverLevel, settlement);

			if (placer instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendSystemMessage(Component.literal("Bakery construction started around this Baker's Counter."));
			}
		} else if (buildResult.isResumed()) {
			savedData.putBuildSite(buildResult.buildSite());
			SettlementVillagers.ensureWorkforce(serverLevel, settlement);

			if (placer instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendSystemMessage(Component.literal("Bakery construction is already planned around this Baker's Counter."));
			}
		} else if (buildResult.isBlocked() && placer instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendSystemMessage(Component.literal("The settlement can't build a Bakery here."));
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		if (!(level instanceof ServerLevel)) {
			return InteractionResult.PASS;
		}

		if (level.getBlockEntity(pos) instanceof BakersCounterBlockEntity bakersCounter) {
			SettlementBakerWork.reconcileBakeryIngredientDisplaysNear((ServerLevel) level, pos);
			player.openMenu(bakersCounter);
			return InteractionResult.SUCCESS_SERVER;
		}

		return InteractionResult.PASS;
	}
}
