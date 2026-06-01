package com.ronhelwig.livevillages.block;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
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
import com.ronhelwig.livevillages.sim.SettlementBuildSite;
import com.ronhelwig.livevillages.sim.SettlementConstruction;
import com.ronhelwig.livevillages.sim.SettlementState;

public class PalisadePointBlock extends Block {
	public static final MapCodec<PalisadePointBlock> CODEC = simpleCodec(PalisadePointBlock::new);

	public PalisadePointBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	public MapCodec<PalisadePointBlock> codec() {
		return CODEC;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		return InteractionResult.PASS;
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
				serverPlayer.sendSystemMessage(Component.literal("No settlement found near this Palisade Point marker."));
			}
			return;
		}

		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(serverLevel.getServer());
		Map<String, Integer> stock = new LinkedHashMap<>(settlement.get().stock());
		SettlementConstruction.PalisadeWallReplan replan = SettlementConstruction.planPalisadeWallsToPoint(
			serverLevel,
			settlement.get(),
			pos,
			stock,
			savedData.getBuildSitesForSettlement(settlement.get().id())
		);

		if (!replan.emptyPlan()) {
			savedData.putSettlement(settlement.get().withStock(stock));
			for (String buildSiteId : replan.obsoleteBuildSiteIds()) {
				savedData.removeBuildSite(buildSiteId);
			}
			for (SettlementBuildSite buildSite : replan.plannedSites()) {
				savedData.putBuildSite(buildSite);
			}
			savedData.surveyCache.remove(settlement.get().id());
		}

		if (placer instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendSystemMessage(Component.literal(
				replan.plannedSites().isEmpty()
					? "The settlement needs another Palisade Point or Gatehouse before it can plan a wall here."
					: "Palisade wall planning added " + replan.plannedSites().size() + " segment" + (replan.plannedSites().size() == 1 ? "" : "s") + " for " + settlement.get().name() + "."
			));
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
}
