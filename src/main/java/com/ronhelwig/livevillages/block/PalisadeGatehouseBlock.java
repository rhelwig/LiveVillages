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
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;

import com.ronhelwig.livevillages.sim.LiveVillagesSavedData;
import com.ronhelwig.livevillages.sim.SettlementBuildSite;
import com.ronhelwig.livevillages.sim.SettlementBuildSiteType;
import com.ronhelwig.livevillages.sim.SettlementConstruction;
import com.ronhelwig.livevillages.sim.SettlementState;

public class PalisadeGatehouseBlock extends Block {
	public static final MapCodec<PalisadeGatehouseBlock> CODEC = simpleCodec(properties -> new PalisadeGatehouseBlock(properties, false));
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	private final boolean copperBars;

	public PalisadeGatehouseBlock(BlockBehaviour.Properties properties, boolean copperBars) {
		super(properties);
		this.copperBars = copperBars;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	public MapCodec<PalisadeGatehouseBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction facing = context.getHorizontalDirection().getOpposite();
		if (context.getLevel() instanceof ServerLevel serverLevel) {
			Optional<SettlementState> settlement = SettlementConstruction.findWorkstationSettlement(serverLevel, context.getClickedPos());
			if (settlement.isPresent()) {
				facing = SettlementConstruction.facingAwayFrom(settlement.get().center(), context.getClickedPos());
			}
		}
		return defaultBlockState().setValue(FACING, facing);
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
				serverPlayer.sendSystemMessage(Component.literal("No settlement found near this Palisade Gatehouse marker."));
			}
			return;
		}

		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(serverLevel.getServer());
		Map<String, Integer> stock = new LinkedHashMap<>(settlement.get().stock());
		SettlementBuildSiteType buildSiteType = copperBars
			? SettlementBuildSiteType.COPPER_PALISADE_GATEHOUSE
			: SettlementBuildSiteType.PALISADE_GATEHOUSE;
		Optional<SettlementBuildSite> existingBuildSite = savedData.findBuildSite(settlement.get().id(), buildSiteType, pos);
		boolean contributeRecipeGoods = !(placer instanceof ServerPlayer serverPlayer) || !serverPlayer.getAbilities().instabuild;
		SettlementConstruction.WorkstationBuildResult buildResult = SettlementConstruction.tryStartPalisadeGatehouseAtDoor(
			serverLevel,
			pos,
			state.getValue(FACING),
			settlement.get().id(),
			copperBars,
			stock,
			existingBuildSite
		);

		if (buildResult.isStarted() && contributeRecipeGoods && existingBuildSite.isEmpty()) {
			long tick = serverLevel.getServer().getTickCount();
			buildResult = SettlementConstruction.WorkstationBuildResult.started(
				SettlementConstruction.updateBuildSiteMaterialStatus(
					buildResult.buildSite().withAddedSiteMaterials(recipeGoods(), tick),
					stock,
					tick
				)
			);
		}

		if (buildResult.isStarted() || buildResult.isResumed()) {
			savedData.putSettlement(settlement.get().withStock(stock));
			savedData.putBuildSite(buildResult.buildSite());
			savedData.surveyCache.remove(settlement.get().id());
		}

		if (placer instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendSystemMessage(Component.literal(
				buildResult.isStarted()
					? "Palisade Gatehouse construction started for " + settlement.get().name() + "."
					: buildResult.isResumed()
					? "Palisade Gatehouse construction is already planned for " + settlement.get().name() + "."
					: "The settlement can't fit a Palisade Gatehouse here."
			));
		}
	}

	private Map<String, Integer> recipeGoods() {
		return Map.of("logs", 6, "door", 1, copperBars ? "copper_bars" : "iron_bars", 2);
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
