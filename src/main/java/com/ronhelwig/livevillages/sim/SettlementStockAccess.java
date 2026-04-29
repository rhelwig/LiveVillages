package com.ronhelwig.livevillages.sim;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

import com.ronhelwig.livevillages.content.LiveVillagesBlocks;

final class SettlementStockAccess {
	private static final int STOCK_ACCESS_SCAN_RADIUS_BLOCKS = 48;
	private static final int STOCK_ACCESS_SCAN_Y_RANGE_BLOCKS = 24;
	private static final long STOCK_ACCESS_CACHE_TICKS = 200L;
	private static final Map<String, CachedStockAccess> STOCK_ACCESS_CACHE = new HashMap<>();

	private SettlementStockAccess() {
	}

	static Optional<BlockPos> findStockAccessPos(ServerLevel level, SettlementState settlement, List<SettlementBuildSite> buildSites) {
		for (SettlementBuildSite buildSite : buildSites) {
			if (buildSite.blueprintId() == SettlementBuildSiteType.TRADING_POST
				&& (level.hasChunkAt(buildSite.workstationPos()) || level.hasChunkAt(buildSite.anchorPos()))) {
				BlockPos workstationPos = SettlementConstruction.currentPlacedWorkstationPos(level, buildSite);
				return stockAccessStandPos(level, workstationPos).or(() -> Optional.of(settlement.center()));
			}
		}

		long tick = level.getServer().getTickCount();
		String cacheKey = stockAccessCacheKey(settlement);
		CachedStockAccess cachedAccess = STOCK_ACCESS_CACHE.get(cacheKey);

		if (cachedAccess != null
			&& tick - cachedAccess.tick() <= STOCK_ACCESS_CACHE_TICKS
			&& cachedAccess.accessPos().map(pos -> pos.equals(settlement.center()) || isStandable(level, pos)).orElse(true)) {
			return cachedAccess.accessPos();
		}

		BlockPos nearestTradeBoardAccess = null;
		double nearestDistanceSquared = Double.POSITIVE_INFINITY;
		BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();
		int minY = Math.max(level.getMinY(), settlement.center().getY() - STOCK_ACCESS_SCAN_Y_RANGE_BLOCKS);
		int maxY = Math.min(level.getMaxY() - 1, settlement.center().getY() + STOCK_ACCESS_SCAN_Y_RANGE_BLOCKS);

		for (int x = settlement.center().getX() - STOCK_ACCESS_SCAN_RADIUS_BLOCKS; x <= settlement.center().getX() + STOCK_ACCESS_SCAN_RADIUS_BLOCKS; x++) {
			for (int z = settlement.center().getZ() - STOCK_ACCESS_SCAN_RADIUS_BLOCKS; z <= settlement.center().getZ() + STOCK_ACCESS_SCAN_RADIUS_BLOCKS; z++) {
				if (!level.hasChunkAt(new BlockPos(x, settlement.center().getY(), z))) {
					continue;
				}

				for (int y = minY; y <= maxY; y++) {
					scanPos.set(x, y, z);

					if (!level.getBlockState(scanPos).is(LiveVillagesBlocks.TRADE_BOARD)) {
						continue;
					}

					Optional<BlockPos> accessPos = stockAccessStandPos(level, scanPos.immutable());

					if (accessPos.isEmpty()) {
						continue;
					}

					double distanceSquared = scanPos.distSqr(settlement.center());

					if (nearestTradeBoardAccess == null || distanceSquared < nearestDistanceSquared) {
						nearestTradeBoardAccess = accessPos.get();
						nearestDistanceSquared = distanceSquared;
					}
				}
			}
		}

		Optional<BlockPos> accessPos = nearestTradeBoardAccess == null ? Optional.of(settlement.center()) : Optional.of(nearestTradeBoardAccess);
		STOCK_ACCESS_CACHE.put(cacheKey, new CachedStockAccess(accessPos, tick));
		return accessPos;
	}

	private static String stockAccessCacheKey(SettlementState settlement) {
		return settlement.dimension().identifier() + "|" + settlement.id();
	}

	static Optional<BlockPos> stockAccessStandPos(ServerLevel level, BlockPos workstationPos) {
		BlockPos fallback = null;
		double fallbackDistanceSquared = Double.POSITIVE_INFINITY;

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos candidate = workstationPos.relative(direction);

			if (!isStandable(level, candidate)) {
				continue;
			}

			if (isRoadSurfaceForAccess(level.getBlockState(candidate.below()))) {
				return Optional.of(candidate);
			}

			double distanceSquared = candidate.distSqr(workstationPos);

			if (distanceSquared < fallbackDistanceSquared) {
				fallback = candidate;
				fallbackDistanceSquared = distanceSquared;
			}
		}

		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (Math.max(Math.abs(dx), Math.abs(dz)) != 2) {
					continue;
				}

				BlockPos candidate = workstationPos.offset(dx, 0, dz);

				if (!isStandable(level, candidate)) {
					continue;
				}

				double distanceSquared = candidate.distSqr(workstationPos);

				if (distanceSquared < fallbackDistanceSquared) {
					fallback = candidate;
					fallbackDistanceSquared = distanceSquared;
				}
			}
		}

		return Optional.ofNullable(fallback);
	}

	private static boolean isStandable(ServerLevel level, BlockPos pos) {
		BlockState footState = level.getBlockState(pos);
		BlockState headState = level.getBlockState(pos.above());
		BlockState belowState = level.getBlockState(pos.below());
		return footState.isAir() && headState.isAir() && !belowState.isAir();
	}

	private static boolean isRoadSurfaceForAccess(BlockState state) {
		return state.is(Blocks.DIRT_PATH)
			|| state.is(Blocks.GRAVEL)
			|| state.is(Blocks.COBBLESTONE)
			|| state.is(Blocks.STONE)
			|| state.is(Blocks.SMOOTH_STONE)
			|| state.is(Blocks.STONE_BRICKS)
			|| state.is(Blocks.BRICKS)
			|| state.getBlock() instanceof SlabBlock
			|| state.getBlock() instanceof StairBlock;
	}

	private record CachedStockAccess(Optional<BlockPos> accessPos, long tick) {
	}
}
