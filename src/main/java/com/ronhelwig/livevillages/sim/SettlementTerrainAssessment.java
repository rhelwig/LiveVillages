package com.ronhelwig.livevillages.sim;

import java.util.HashMap;
import java.util.Map;

import com.ronhelwig.livevillages.LiveVillages;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/** A cheap, shared estimate of how strongly a settlement needs elevation work. */
public final class SettlementTerrainAssessment {
	private static final long CACHE_TICKS = 1200L;
	private static final int SAMPLE_RADIUS_BLOCKS = 48;
	private static final int SAMPLE_STEP_BLOCKS = 12;
	private static final Map<String, CachedAssessment> CACHE = new HashMap<>();

	private SettlementTerrainAssessment() {
	}

	public static TerrainAssessment assess(ServerLevel level, SettlementState settlement) {
		long tick = level.getServer().getTickCount();
		String key = settlement.dimension().identifier() + "|" + settlement.id();
		CachedAssessment cached = CACHE.get(key);
		if (cached != null && tick - cached.tick() <= CACHE_TICKS) {
			return cached.assessment();
		}

		TerrainAssessment assessment = sample(level, settlement.center());
		CACHE.put(key, new CachedAssessment(assessment, tick));
		LiveVillages.LOGGER.info(
			"Terrain assessment: settlement={} score={} heightRange={} meanStep={}",
			settlement.id(),
			assessment.difficultyScore(),
			assessment.heightRange(),
			String.format(java.util.Locale.ROOT, "%.1f", assessment.meanSampleStep())
		);
		return assessment;
	}

	static TerrainAssessment fromSamples(int minimumY, int maximumY, int adjacentHeightDelta, int adjacentComparisons) {
		int heightRange = Math.max(0, maximumY - minimumY);
		double meanStep = adjacentComparisons <= 0 ? 0.0D : adjacentHeightDelta / (double) adjacentComparisons;
		int score = (int) Math.round(Math.min(100.0D, heightRange * 2.25D + meanStep * 13.0D));
		return new TerrainAssessment(score, heightRange, meanStep);
	}

	private static TerrainAssessment sample(ServerLevel level, BlockPos center) {
		int side = (SAMPLE_RADIUS_BLOCKS * 2 / SAMPLE_STEP_BLOCKS) + 1;
		int[][] heights = new int[side][side];
		boolean[][] present = new boolean[side][side];
		int minimumY = Integer.MAX_VALUE;
		int maximumY = Integer.MIN_VALUE;

		for (int ix = 0; ix < side; ix++) {
			int x = center.getX() - SAMPLE_RADIUS_BLOCKS + ix * SAMPLE_STEP_BLOCKS;
			for (int iz = 0; iz < side; iz++) {
				int z = center.getZ() - SAMPLE_RADIUS_BLOCKS + iz * SAMPLE_STEP_BLOCKS;
				BlockPos probe = new BlockPos(x, center.getY(), z);
				if (!level.hasChunkAt(probe)) {
					continue;
				}
				int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
				heights[ix][iz] = height;
				present[ix][iz] = true;
				minimumY = Math.min(minimumY, height);
				maximumY = Math.max(maximumY, height);
			}
		}

		if (minimumY == Integer.MAX_VALUE) {
			return new TerrainAssessment(0, 0, 0.0D);
		}

		int totalDelta = 0;
		int comparisons = 0;
		for (int ix = 0; ix < side; ix++) {
			for (int iz = 0; iz < side; iz++) {
				if (!present[ix][iz]) {
					continue;
				}
				if (ix + 1 < side && present[ix + 1][iz]) {
					totalDelta += Math.abs(heights[ix][iz] - heights[ix + 1][iz]);
					comparisons++;
				}
				if (iz + 1 < side && present[ix][iz + 1]) {
					totalDelta += Math.abs(heights[ix][iz] - heights[ix][iz + 1]);
					comparisons++;
				}
			}
		}

		return fromSamples(minimumY, maximumY, totalDelta, comparisons);
	}

	public record TerrainAssessment(int difficultyScore, int heightRange, double meanSampleStep) {
		public boolean isSteep() {
			return difficultyScore >= 45;
		}

		public int roadwrightPriorityBonus() {
			return difficultyScore * 2;
		}
	}

	private record CachedAssessment(TerrainAssessment assessment, long tick) {
	}
}
