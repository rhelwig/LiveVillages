package com.ronhelwig.livevillages.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;

import com.ronhelwig.livevillages.network.BuildSitePreviewBlockView;
import com.ronhelwig.livevillages.network.LiveVillagesNetworking;

public final class BuildSiteAssistedPlacement {
	private static final int BLOCK_UPDATE_FLAGS = 3;

	private BuildSiteAssistedPlacement() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register(BuildSiteAssistedPlacement::onUseBlock);
	}

	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hitResult) {
		if (level.isClientSide() || !(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}

		if (!LiveVillagesNetworking.isBuildSitePreviewActive(serverPlayer) || player.isSpectator()) {
			return InteractionResult.PASS;
		}

		ItemStack stack = player.getItemInHand(hand);

		if (stack.isEmpty()) {
			return InteractionResult.PASS;
		}

		BlockPos targetPos = targetPlacementPos(level, hitResult);

		if (!SettlementConstruction.isBuildSiteReplaceable(level.getBlockState(targetPos))) {
			return InteractionResult.PASS;
		}

		return assistedPlacement(serverLevel, serverPlayer, stack, targetPos);
	}

	private static BlockPos targetPlacementPos(Level level, BlockHitResult hitResult) {
		BlockState clickedState = level.getBlockState(hitResult.getBlockPos());
		return SettlementConstruction.isBuildSiteReplaceable(clickedState)
			? hitResult.getBlockPos()
			: hitResult.getBlockPos().relative(hitResult.getDirection());
	}

	private static InteractionResult assistedPlacement(ServerLevel level, ServerPlayer player, ItemStack stack, BlockPos targetPos) {
		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(level.getServer());
		long tick = level.getServer().getTickCount();

		for (SettlementBuildSite buildSite : savedData.getBuildSites()) {
			if (buildSite.complete()) {
				continue;
			}

			Optional<SettlementState> settlement = savedData.getSettlement(buildSite.settlementId());
			if (settlement.isEmpty() || !settlement.get().dimension().equals(level.dimension())) {
				continue;
			}

			AssistedBuildBlock rootBlock = assistedBuildBlock(level, buildSite, targetPos, stack).orElse(null);

			if (rootBlock == null) {
				continue;
			}

			List<SettlementBuildBlockState> updatedBlocks = new ArrayList<>(buildSite.blocks());
			placePlannedBlock(level, buildSite, rootBlock.block(), rootBlock.pos(), stack);
			updatedBlocks.set(rootBlock.index(), rootBlock.block().withStatus(SettlementBuildBlockStatus.PLAYER_PLACED, ""));
			savedData.releaseConstructionDeliveriesForBlock(buildSite.settlementId(), buildSite.id(), rootBlock.block().position());

			AssistedBuildBlock pairedBlock = pairedBuildBlock(level, buildSite, rootBlock).orElse(null);

			if (pairedBlock != null) {
				placePlannedBlock(level, buildSite, pairedBlock.block(), pairedBlock.pos(), stack);
				updatedBlocks.set(pairedBlock.index(), pairedBlock.block().withStatus(SettlementBuildBlockStatus.PLAYER_PLACED, ""));
				savedData.releaseConstructionDeliveriesForBlock(buildSite.settlementId(), buildSite.id(), pairedBlock.block().position());
			}

			savedData.putBuildSite(buildSite.withBlocks(updatedBlocks, isComplete(updatedBlocks), tick));

			if (!player.getAbilities().instabuild) {
				stack.shrink(1);
			}

			return InteractionResult.SUCCESS;
		}

		return InteractionResult.PASS;
	}

	private static Optional<AssistedBuildBlock> assistedBuildBlock(ServerLevel level, SettlementBuildSite buildSite, BlockPos targetPos, ItemStack stack) {
		for (int i = 0; i < buildSite.blocks().size(); i++) {
			SettlementBuildBlockState block = buildSite.blocks().get(i);

			if (block.status() == SettlementBuildBlockStatus.PLACED
				|| block.status() == SettlementBuildBlockStatus.PLAYER_PLACED
				|| block.expectedMaterialKey().isBlank()) {
				continue;
			}

			Optional<BlockPos> blockPos = SettlementConstruction.buildSiteBlockPos(buildSite, block);
			BlockState plannedState = SettlementConstruction.plannedBuildSiteBlockState(buildSite, block);

			if (blockPos.isEmpty() || plannedState == null || !blockPos.get().equals(targetPos)) {
				continue;
			}

			BuildSitePreviewBlockView previewBlock = new BuildSitePreviewBlockView(
				blockPos.get(),
				block.expectedMaterialKey(),
				net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(plannedState.getBlock()).toString()
			);

			if (previewBlock.canUseItem(stack)) {
				return Optional.of(new AssistedBuildBlock(i, block, blockPos.get()));
			}
		}

		return Optional.empty();
	}

	private static Optional<AssistedBuildBlock> pairedBuildBlock(ServerLevel level, SettlementBuildSite buildSite, AssistedBuildBlock rootBlock) {
		BlockState rootState = SettlementConstruction.plannedBuildSiteBlockState(buildSite, rootBlock.block());

		if (rootState == null) {
			return Optional.empty();
		}

		for (int i = 0; i < buildSite.blocks().size(); i++) {
			SettlementBuildBlockState candidate = buildSite.blocks().get(i);

			if (candidate.status() == SettlementBuildBlockStatus.PLACED || candidate.status() == SettlementBuildBlockStatus.PLAYER_PLACED) {
				continue;
			}

			Optional<BlockPos> candidatePos = SettlementConstruction.buildSiteBlockPos(buildSite, candidate);
			BlockState candidateState = SettlementConstruction.plannedBuildSiteBlockState(buildSite, candidate);

			if (candidatePos.isEmpty() || candidateState == null) {
				continue;
			}

			BlockState currentState = level.getBlockState(candidatePos.get());

			if (!currentState.equals(candidateState) && !SettlementConstruction.isBuildSiteReplaceable(currentState)) {
				continue;
			}

			if (isPairedDoor(rootState, rootBlock.pos(), candidateState, candidatePos.get())
				|| isPairedBed(rootState, rootBlock.pos(), candidateState, candidatePos.get())) {
				return Optional.of(new AssistedBuildBlock(i, candidate, candidatePos.get()));
			}
		}

		return Optional.empty();
	}

	private static boolean isPairedDoor(BlockState rootState, BlockPos rootPos, BlockState candidateState, BlockPos candidatePos) {
		return rootState.getBlock() instanceof DoorBlock
			&& candidateState.getBlock() instanceof DoorBlock
			&& rootState.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
			&& candidateState.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER
			&& candidatePos.equals(rootPos.above());
	}

	private static boolean isPairedBed(BlockState rootState, BlockPos rootPos, BlockState candidateState, BlockPos candidatePos) {
		return rootState.getBlock() instanceof BedBlock
			&& candidateState.getBlock() instanceof BedBlock
			&& rootState.getValue(BedBlock.PART) == BedPart.FOOT
			&& candidateState.getValue(BedBlock.PART) == BedPart.HEAD
			&& candidatePos.equals(rootPos.relative(rootState.getValue(BedBlock.FACING)));
	}

	private static void placePlannedBlock(ServerLevel level, SettlementBuildSite buildSite, SettlementBuildBlockState block, BlockPos pos, ItemStack stack) {
		BlockState plannedState = SettlementConstruction.plannedBuildSiteBlockState(buildSite, block);

		if (plannedState != null) {
			level.setBlock(pos, assistedPlacementState(plannedState, block.expectedMaterialKey(), stack), BLOCK_UPDATE_FLAGS);
			SettlementConstruction.updateChestStateAfterPlacement(level, pos);
		}
	}

	private static BlockState assistedPlacementState(BlockState plannedState, String expectedMaterialKey, ItemStack stack) {
		if (!(stack.getItem() instanceof BlockItem blockItem)) {
			return plannedState;
		}

		BlockState heldState = blockItem.getBlock().defaultBlockState();
		if (!SettlementConstruction.isFlexibleMaterialMatch(heldState, plannedState, expectedMaterialKey)) {
			return plannedState;
		}

		return SettlementConstruction.copySharedPlacementProperties(heldState, plannedState);
	}

	private static boolean isComplete(List<SettlementBuildBlockState> blocks) {
		for (SettlementBuildBlockState block : blocks) {
			if (block.status() != SettlementBuildBlockStatus.PLACED && block.status() != SettlementBuildBlockStatus.PLAYER_PLACED) {
				return false;
			}
		}

		return true;
	}

	private record AssistedBuildBlock(int index, SettlementBuildBlockState block, BlockPos pos) {
	}
}
