package com.ronhelwig.livevillages.sim;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class SettlementNavigation {
	private static final int ROAD_WAYPOINT_SEARCH_RADIUS_BLOCKS = 10;
	private static final int ROAD_SURFACE_SCAN_BELOW_BLOCKS = 3;
	private static final int ROAD_SURFACE_SCAN_ABOVE_BLOCKS = 2;
	private static final double MIN_ROAD_BIAS_DISTANCE_SQUARED = 144.0D;
	private static final double MIN_WAYPOINT_STEP_SQUARED = 4.0D;
	private static final double MIN_TARGET_PROGRESS_SQUARED = 9.0D;

	private SettlementNavigation() {
	}

	public static boolean moveToRoutineTarget(ServerLevel level, SettlementState settlement, Villager villager, BlockPos targetPos, double speed) {
		int travelRadiusBlocks = settlement == null ? 0 : SettlementVillagers.settlementRadiusBlocks(settlement);
		return moveToRoutineTarget(level, settlement, villager, targetPos, speed, travelRadiusBlocks);
	}

	public static boolean moveToRoutineTarget(
		ServerLevel level,
		SettlementState settlement,
		Villager villager,
		BlockPos targetPos,
		double speed,
		int travelRadiusBlocks
	) {
		if (level == null || settlement == null || villager == null || targetPos == null) {
			return false;
		}

		BlockPos waypoint = roadBiasedWaypoint(level, settlement, villager.blockPosition(), targetPos, travelRadiusBlocks)
			.orElse(targetPos);
		return villager.getNavigation().moveTo(
			waypoint.getX() + 0.5D,
			waypoint.getY(),
			waypoint.getZ() + 0.5D,
			speed
		);
	}

	private static Optional<BlockPos> roadBiasedWaypoint(
		ServerLevel level,
		SettlementState settlement,
		BlockPos currentPos,
		BlockPos targetPos,
		int travelRadiusBlocks
	) {
		if (currentPos.distSqr(targetPos) < MIN_ROAD_BIAS_DISTANCE_SQUARED
			|| !withinSettlementTravelBounds(settlement, currentPos, targetPos, travelRadiusBlocks)) {
			return Optional.empty();
		}

		RoadStand best = null;
		double currentTargetDistance = currentPos.distSqr(targetPos);
		double bestScore = Double.MAX_VALUE;

		int radius = ROAD_WAYPOINT_SEARCH_RADIUS_BLOCKS;

		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				int x = currentPos.getX() + dx;
				int z = currentPos.getZ() + dz;
				Optional<RoadStand> roadStand = roadStandAt(level, x, z, currentPos.getY());

				if (roadStand.isEmpty()) {
					continue;
				}

				BlockPos standPos = roadStand.get().standPos();
				double currentWaypointDistance = currentPos.distSqr(standPos);
				double waypointTargetDistance = standPos.distSqr(targetPos);

				if (currentWaypointDistance < MIN_WAYPOINT_STEP_SQUARED || waypointTargetDistance > currentTargetDistance - MIN_TARGET_PROGRESS_SQUARED) {
					continue;
				}

				double score = waypointTargetDistance + currentWaypointDistance * 0.35D - roadStand.get().qualityBonus();

				if (score < bestScore) {
					bestScore = score;
					best = roadStand.get();
				}
			}
		}

		return best == null ? Optional.empty() : Optional.of(best.standPos());
	}

	private static boolean withinSettlementTravelBounds(SettlementState settlement, BlockPos currentPos, BlockPos targetPos, int travelRadiusBlocks) {
		double radius = Math.max(SettlementVillagers.settlementRadiusBlocks(settlement), travelRadiusBlocks) + 16.0D;
		double radiusSquared = radius * radius;

		return currentPos.distSqr(settlement.center()) <= radiusSquared
			&& targetPos.distSqr(settlement.center()) <= radiusSquared;
	}

	private static Optional<RoadStand> roadStandAt(ServerLevel level, int x, int z, int referenceY) {
		int minY = Math.max(level.getMinY(), referenceY - ROAD_SURFACE_SCAN_BELOW_BLOCKS);
		int maxY = Math.min(level.getMaxY() - 1, referenceY + ROAD_SURFACE_SCAN_ABOVE_BLOCKS);

		for (int y = maxY; y >= minY; y--) {
			BlockPos surfacePos = new BlockPos(x, y, z);
			BlockState state = level.getBlockState(surfacePos);
			int qualityBonus = roadQualityBonus(state);

			if (qualityBonus <= 0) {
				continue;
			}

			BlockPos standPos = surfacePos.above();

			if (canStandAt(level, standPos)) {
				return Optional.of(new RoadStand(standPos.immutable(), qualityBonus));
			}
		}

		return Optional.empty();
	}

	private static boolean canStandAt(ServerLevel level, BlockPos standPos) {
		BlockState feetState = level.getBlockState(standPos);
		BlockState headState = level.getBlockState(standPos.above());

		return isPassable(feetState) && isPassable(headState);
	}

	private static boolean isPassable(BlockState state) {
		return state.isAir() || state.canBeReplaced();
	}

	private static int roadQualityBonus(BlockState state) {
		if (state.is(Blocks.BRICKS)
			|| state.is(Blocks.BRICK_SLAB)
			|| state.is(Blocks.BRICK_STAIRS)
			|| state.is(Blocks.STONE_BRICKS)
			|| state.is(Blocks.STONE_BRICK_SLAB)
			|| state.is(Blocks.STONE_BRICK_STAIRS)
			|| state.is(Blocks.SMOOTH_STONE)
			|| state.is(Blocks.SMOOTH_STONE_SLAB)) {
			return 6;
		}

		if (state.is(Blocks.COBBLESTONE)
			|| state.is(Blocks.COBBLESTONE_SLAB)
			|| state.is(Blocks.COBBLESTONE_STAIRS)
			|| state.is(Blocks.MOSSY_COBBLESTONE)
			|| state.is(Blocks.MOSSY_COBBLESTONE_SLAB)
			|| state.is(Blocks.MOSSY_COBBLESTONE_STAIRS)
			|| state.is(BlockTags.PLANKS)
			|| state.is(BlockTags.WOODEN_SLABS)
			|| state.is(BlockTags.WOODEN_STAIRS)) {
			return 4;
		}

		if (state.is(Blocks.DIRT_PATH) || state.is(Blocks.GRAVEL)) {
			return 2;
		}

		return 0;
	}

	private record RoadStand(BlockPos standPos, int qualityBonus) {
	}
}
