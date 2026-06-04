package com.ronhelwig.livevillages.sim;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public final class SettlementAccessWork {
	private static final int BLOCK_UPDATE_FLAGS = 3;
	private static final double OPEN_RANGE_SQUARED = 6.25D;
	private static final double HOLD_OPEN_RANGE_SQUARED = 12.25D;
	private static final Set<String> MANAGED_OPEN_ACCESS_BLOCKS = new LinkedHashSet<>();

	private SettlementAccessWork() {
	}

	public static boolean maintainLoadedDefensiveAccess(ServerLevel level, SettlementState settlement, Collection<SettlementBuildSite> buildSites) {
		if (level == null || settlement == null || buildSites == null || buildSites.isEmpty()) {
			return false;
		}

		List<BlockPos> memberPositions = accessMemberPositions(level, settlement);

		boolean changed = false;

		for (BlockPos accessPos : defensiveAccessPositions(level, buildSites)) {
			changed |= maintainAccessBlock(level, accessPos, memberPositions);
		}

		return changed;
	}

	private static List<BlockPos> accessMemberPositions(ServerLevel level, SettlementState settlement) {
		if (settlement.kind() == SettlementKind.OUTPOST) {
			return OutpostSettlementWork.constructionMemberPositions(level, settlement);
		}

		return SettlementVillagers.reportableVillagers(level, settlement).stream()
			.filter(villager -> villager.isAlive() && !villager.isRemoved())
			.map(Villager::blockPosition)
			.map(BlockPos::immutable)
			.toList();
	}

	private static Set<BlockPos> defensiveAccessPositions(ServerLevel level, Collection<SettlementBuildSite> buildSites) {
		LinkedHashSet<BlockPos> positions = new LinkedHashSet<>();

		for (SettlementBuildSite buildSite : buildSites) {
			if (!isDefensiveAccessBuildSite(buildSite)) {
				continue;
			}

			boolean foundAccessBlock = false;

			for (SettlementBuildBlockState block : buildSite.blocks()) {
				if (!isAccessBlueprintBlock(block)) {
					continue;
				}

				if (buildSiteWorldPos(buildSite, block.position())
					.flatMap(pos -> normalizedAccessPos(level, pos))
					.map(positions::add)
					.orElse(false)) {
					foundAccessBlock = true;
				}
			}

			if (!foundAccessBlock) {
				normalizedAccessPos(level, buildSite.origin()).ifPresent(positions::add);
			}
		}

		return positions;
	}

	private static boolean isDefensiveAccessBuildSite(SettlementBuildSite buildSite) {
		return buildSite != null
			&& buildSite.complete()
			&& (buildSite.blueprintId() == SettlementBuildSiteType.PALISADE_GATEHOUSE
				|| buildSite.blueprintId() == SettlementBuildSiteType.COPPER_PALISADE_GATEHOUSE);
	}

	private static boolean isAccessBlueprintBlock(SettlementBuildBlockState block) {
		return block != null
			&& ("door".equals(block.expectedMaterialKey()) || "fence_gate".equals(block.expectedMaterialKey()));
	}

	private static Optional<BlockPos> normalizedAccessPos(ServerLevel level, BlockPos pos) {
		if (pos == null || !level.isLoaded(pos)) {
			return Optional.empty();
		}

		BlockState state = level.getBlockState(pos);

		if (state.getBlock() instanceof DoorBlock) {
			if (state.hasProperty(DoorBlock.HALF) && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
				BlockPos below = pos.below();
				return level.isLoaded(below) && level.getBlockState(below).getBlock() instanceof DoorBlock
					? Optional.of(below.immutable())
					: Optional.empty();
			}

			return Optional.of(pos.immutable());
		}

		if (state.getBlock() instanceof FenceGateBlock) {
			return Optional.of(pos.immutable());
		}

		return Optional.empty();
	}

	private static boolean maintainAccessBlock(ServerLevel level, BlockPos accessPos, List<BlockPos> memberPositions) {
		BlockState state = level.getBlockState(accessPos);

		if (!(state.getBlock() instanceof DoorBlock) && !(state.getBlock() instanceof FenceGateBlock)) {
			MANAGED_OPEN_ACCESS_BLOCKS.remove(accessKey(level, accessPos));
			return false;
		}

		String key = accessKey(level, accessPos);
		boolean currentlyOpen = isOpen(state);
		boolean managedOpen = MANAGED_OPEN_ACCESS_BLOCKS.contains(key);
		double rangeSquared = currentlyOpen || managedOpen ? HOLD_OPEN_RANGE_SQUARED : OPEN_RANGE_SQUARED;

		if (hasMemberWithin(accessPos, memberPositions, rangeSquared)) {
			MANAGED_OPEN_ACCESS_BLOCKS.add(key);

			if (!currentlyOpen) {
				level.setBlock(accessPos, withOpen(state, true), BLOCK_UPDATE_FLAGS);
				return true;
			}

			return false;
		}

		if (managedOpen) {
			MANAGED_OPEN_ACCESS_BLOCKS.remove(key);

			if (currentlyOpen) {
				level.setBlock(accessPos, withOpen(state, false), BLOCK_UPDATE_FLAGS);
				return true;
			}
		}

		return false;
	}

	private static boolean hasMemberWithin(BlockPos accessPos, List<BlockPos> memberPositions, double rangeSquared) {
		for (BlockPos memberPos : memberPositions) {
			if (memberPos.distSqr(accessPos) <= rangeSquared) {
				return true;
			}
		}

		return false;
	}

	private static boolean isOpen(BlockState state) {
		if (state.getBlock() instanceof DoorBlock && state.hasProperty(DoorBlock.OPEN)) {
			return state.getValue(DoorBlock.OPEN);
		}

		if (state.getBlock() instanceof FenceGateBlock && state.hasProperty(FenceGateBlock.OPEN)) {
			return state.getValue(FenceGateBlock.OPEN);
		}

		return false;
	}

	private static BlockState withOpen(BlockState state, boolean open) {
		if (state.getBlock() instanceof DoorBlock && state.hasProperty(DoorBlock.OPEN)) {
			return state.setValue(DoorBlock.OPEN, open);
		}

		if (state.getBlock() instanceof FenceGateBlock && state.hasProperty(FenceGateBlock.OPEN)) {
			return state.setValue(FenceGateBlock.OPEN, open);
		}

		return state;
	}

	private static String accessKey(ServerLevel level, BlockPos pos) {
		return level.dimension().identifier() + ":" + pos.asLong();
	}

	private static Optional<BlockPos> buildSiteWorldPos(SettlementBuildSite buildSite, String relativePosition) {
		BuildSiteRelativePos relativePos = parseBuildSiteRelativePos(relativePosition);

		if (relativePos == null) {
			return Optional.empty();
		}

		return Optional.of(
			buildSite.origin()
				.relative(buildSite.facing().getClockWise(), relativePos.right())
				.relative(buildSite.facing(), relativePos.forward())
				.above(relativePos.up())
				.immutable()
		);
	}

	private static BuildSiteRelativePos parseBuildSiteRelativePos(String position) {
		String[] parts = position.split(",");

		if (parts.length != 3) {
			return null;
		}

		try {
			return new BuildSiteRelativePos(
				Integer.parseInt(parts[0]),
				Integer.parseInt(parts[1]),
				Integer.parseInt(parts[2])
			);
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	private record BuildSiteRelativePos(int right, int forward, int up) {
	}
}
