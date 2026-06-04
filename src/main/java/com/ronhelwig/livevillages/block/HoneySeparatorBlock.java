package com.ronhelwig.livevillages.block;

import java.util.LinkedHashMap;
import java.util.Map;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

import com.ronhelwig.livevillages.sim.LiveVillagesSavedData;
import com.ronhelwig.livevillages.sim.SettlementBuildSiteType;
import com.ronhelwig.livevillages.sim.SettlementConstruction;
import com.ronhelwig.livevillages.sim.SettlementGoods;
import com.ronhelwig.livevillages.sim.SettlementState;
import com.ronhelwig.livevillages.sim.SettlementVillagers;

public class HoneySeparatorBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<HoneySeparatorBlock> CODEC = simpleCodec(HoneySeparatorBlock::new);

	public HoneySeparatorBlock(BlockBehaviour.Properties properties) {
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
				serverPlayer.sendSystemMessage(Component.literal("No settlement found near this Honey Separator."));
			}

			return;
		}

		Direction facing = state.getValue(FACING);
		Map<String, Integer> stock = new LinkedHashMap<>(settlement.stock());
		SettlementConstruction.WorkstationBuildResult buildResult = SettlementConstruction.tryStartBeekeeperApiaryAtWorkstation(
			serverLevel,
			pos,
			facing,
			settlement.id(),
			stock,
			savedData.findBuildSite(settlement.id(), SettlementBuildSiteType.BEEKEEPER_APIARY, pos)
		);

		if (buildResult.isStarted()) {
			savedData.putBuildSite(buildResult.buildSite());
			SettlementVillagers.ensureWorkforce(serverLevel, settlement);

			if (placer instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendSystemMessage(Component.literal("Beekeeper's Apiary construction started around this Honey Separator."));
			}
		} else if (buildResult.isResumed()) {
			savedData.putBuildSite(buildResult.buildSite());
			SettlementVillagers.ensureWorkforce(serverLevel, settlement);

			if (placer instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendSystemMessage(Component.literal("Beekeeper's Apiary construction is already planned around this Honey Separator."));
			}
		} else if (buildResult.isBlocked() && placer instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendSystemMessage(Component.literal("The settlement can't build a Beekeeper's Apiary here."));
		}
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		if (!stack.is(Items.GLASS_BOTTLE)) {
			return InteractionResult.PASS;
		}

		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}

		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(serverLevel.getServer());
		SettlementState settlement = SettlementConstruction.findWorkstationSettlement(serverLevel, pos).orElse(null);
		if (settlement == null) {
			serverPlayer.sendSystemMessage(Component.literal("No settlement found near this Honey Separator."));
			return InteractionResult.SUCCESS_SERVER;
		}

		Map<String, Integer> stock = new LinkedHashMap<>(settlement.stock());
		if (!SettlementGoods.consumeGoods(stock, "honey_bottle", 1)) {
			serverPlayer.sendSystemMessage(Component.literal(settlement.name() + " has no honey bottles ready to trade."));
			return InteractionResult.SUCCESS_SERVER;
		}

		if (!serverPlayer.getAbilities().instabuild) {
			stack.shrink(1);
			SettlementGoods.addGoods(stock, "glass_bottle", 1);
		}

		savedData.putSettlementAndRefreshBuildSiteMaterialStatus(
			settlement.withStock(stock),
			serverLevel.getServer().getTickCount()
		);
		giveOrDrop(serverPlayer, new ItemStack(Items.HONEY_BOTTLE));
		serverPlayer.sendSystemMessage(Component.literal("Traded 1 glass bottle to " + settlement.name() + "'s Beekeeper for 1 honey bottle."));
		return InteractionResult.SUCCESS_SERVER;
	}

	private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
	}
}
