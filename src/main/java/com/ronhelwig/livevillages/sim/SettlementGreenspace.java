package com.ronhelwig.livevillages.sim;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;

final class SettlementGreenspace {
	private static final int VILLAGE_GREENSPACE_RADIUS_BLOCKS = 3;
	private static final int PATH_CORRIDOR_RADIUS_BLOCKS = 1;
	private static final int MAX_NEARBY_PATH_BLOCKS = 12;

	private SettlementGreenspace() {
	}

	static boolean canConvertToPath(ServerLevel level, SettlementState settlement, BlockPos surfacePos) {
		if (settlement == null) {
			return true;
		}

		int settlementRadius = SettlementVillagers.settlementRadiusBlocks(settlement);

		if (surfacePos.distSqr(settlement.center()) > settlementRadius * settlementRadius) {
			return true;
		}

		BlockState surfaceState = level.getBlockState(surfacePos);
		BlockState aboveState = level.getBlockState(surfacePos.above());

		if (isDecorativePlant(aboveState)) {
			return false;
		}

		if (hasProtectedVillageGreen(level, surfacePos) && nearbyPathBlocks(level, surfacePos, PATH_CORRIDOR_RADIUS_BLOCKS) == 0) {
			return false;
		}

		if (surfaceState.is(Blocks.GRASS_BLOCK) && nearbyPathBlocks(level, surfacePos, VILLAGE_GREENSPACE_RADIUS_BLOCKS) > MAX_NEARBY_PATH_BLOCKS) {
			return false;
		}

		return true;
	}

	static boolean canCutTree(ServerLevel level, SettlementState settlement, BlockPos treeBase, int nearbyMatureTrees, int preserveTreeCount) {
		if (settlement == null) {
			return nearbyMatureTrees > preserveTreeCount;
		}

		int settlementRadius = SettlementVillagers.settlementRadiusBlocks(settlement);

		if (treeBase.distSqr(settlement.center()) <= settlementRadius * settlementRadius && hasNearbySapling(level, treeBase, 12)) {
			return false;
		}

		return nearbyMatureTrees > preserveTreeCount;
	}

	private static boolean hasProtectedVillageGreen(ServerLevel level, BlockPos center) {
		int flowerOrSaplingCount = 0;
		int treeStructureCount = 0;

		for (BlockPos scanPos : BlockPos.betweenClosed(
			center.offset(-VILLAGE_GREENSPACE_RADIUS_BLOCKS, 0, -VILLAGE_GREENSPACE_RADIUS_BLOCKS),
			center.offset(VILLAGE_GREENSPACE_RADIUS_BLOCKS, 3, VILLAGE_GREENSPACE_RADIUS_BLOCKS)
		)) {
			BlockState state = level.getBlockState(scanPos);

			if (state.is(BlockTags.FLOWERS) || state.getBlock() instanceof SaplingBlock) {
				flowerOrSaplingCount++;
			} else if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) {
				treeStructureCount++;
			}
		}

		return flowerOrSaplingCount > 0 || treeStructureCount >= 8;
	}

	private static boolean hasNearbySapling(ServerLevel level, BlockPos center, int radius) {
		for (BlockPos scanPos : BlockPos.betweenClosed(center.offset(-radius, -1, -radius), center.offset(radius, 2, radius))) {
			if (level.getBlockState(scanPos).getBlock() instanceof SaplingBlock) {
				return true;
			}
		}

		return false;
	}

	private static boolean isDecorativePlant(BlockState state) {
		return state.is(BlockTags.FLOWERS)
			|| state.is(Blocks.SHORT_GRASS)
			|| state.is(Blocks.TALL_GRASS)
			|| state.getBlock() instanceof SaplingBlock
			|| state.getBlock() instanceof BushBlock;
	}

	private static int nearbyPathBlocks(ServerLevel level, BlockPos center, int radius) {
		int count = 0;

		for (BlockPos scanPos : BlockPos.betweenClosed(center.offset(-radius, -1, -radius), center.offset(radius, 1, radius))) {
			BlockState state = level.getBlockState(scanPos);

			if (state.is(Blocks.DIRT_PATH)
				|| state.is(Blocks.GRAVEL)
				|| state.is(Blocks.COBBLESTONE)
				|| state.is(Blocks.STONE)
				|| state.is(Blocks.SMOOTH_STONE)
				|| state.is(Blocks.STONE_BRICKS)
				|| state.is(Blocks.BRICKS)) {
				count++;
			}
		}

		return count;
	}
}
