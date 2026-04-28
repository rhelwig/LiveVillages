package com.ronhelwig.livevillages.block.entity;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import com.ronhelwig.livevillages.content.LiveVillagesBlockEntities;
import com.ronhelwig.livevillages.sim.LiveVillagesSavedData;
import com.ronhelwig.livevillages.sim.SettlementKind;
import com.ronhelwig.livevillages.sim.SettlementState;

public class MilepostBlockEntity extends BlockEntity {
	private static final int BLOCK_UPDATE_FLAGS = 3;
	private static final long LABEL_SYNC_INTERVAL_TICKS = 100L;

	private String northLabel;
	private String eastLabel;
	private String southLabel;
	private String westLabel;

	public MilepostBlockEntity(BlockPos pos, BlockState blockState) {
		super(LiveVillagesBlockEntities.MILEPOST, pos, blockState);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		northLabel = input.getString("north_label").orElse(null);
		eastLabel = input.getString("east_label").orElse(null);
		southLabel = input.getString("south_label").orElse(null);
		westLabel = input.getString("west_label").orElse(null);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		putLabel(output, "north_label", northLabel);
		putLabel(output, "east_label", eastLabel);
		putLabel(output, "south_label", southLabel);
		putLabel(output, "west_label", westLabel);
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}

	public String labelFor(Direction direction) {
		return switch (direction) {
			case NORTH -> northLabel;
			case EAST -> eastLabel;
			case SOUTH -> southLabel;
			case WEST -> westLabel;
			default -> null;
		};
	}

	public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, MilepostBlockEntity blockEntity) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}

		long tick = serverLevel.getServer().getTickCount();
		long stagger = Math.floorMod(pos.asLong(), LABEL_SYNC_INTERVAL_TICKS);

		if ((tick % LABEL_SYNC_INTERVAL_TICKS) != stagger) {
			return;
		}

		blockEntity.refreshLabels(serverLevel);
	}

	public void refreshLabelsNow(ServerLevel serverLevel) {
		refreshLabels(serverLevel);
	}

	private void refreshLabels(ServerLevel serverLevel) {
		EnumMap<Direction, SettlementCandidate> nearest = new EnumMap<>(Direction.class);
		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(serverLevel.getServer());

		for (SettlementState settlement : savedData.getSettlements()) {
			if (!settlement.dimension().equals(serverLevel.dimension()) || settlement.kind() == SettlementKind.OUTPOST) {
				continue;
			}

			DirectionAssignment assignment = facesToward(settlement.center());

			if (assignment == null) {
				continue;
			}

			double distanceSquared = settlement.center().distSqr(worldPosition);

			for (Direction face : assignment.faces()) {
				SettlementCandidate current = nearest.get(face);

				if (current == null || distanceSquared < current.distanceSquared()) {
					nearest.put(face, new SettlementCandidate(settlement.name(), distanceSquared));
				}
			}
		}

		syncLabels(
			label(nearest, Direction.NORTH),
			label(nearest, Direction.EAST),
			label(nearest, Direction.SOUTH),
			label(nearest, Direction.WEST)
		);
	}

	private DirectionAssignment facesToward(BlockPos settlementPos) {
		int dx = settlementPos.getX() - worldPosition.getX();
		int dz = settlementPos.getZ() - worldPosition.getZ();

		if (dx == 0 && dz == 0) {
			return null;
		}

		if (dx == 0) {
			return new DirectionAssignment(new Direction[] {dz > 0 ? Direction.SOUTH : Direction.NORTH});
		}

		if (dz == 0) {
			return new DirectionAssignment(new Direction[] {dx > 0 ? Direction.EAST : Direction.WEST});
		}

		return new DirectionAssignment(new Direction[] {
			dx > 0 ? Direction.EAST : Direction.WEST,
			dz > 0 ? Direction.SOUTH : Direction.NORTH
		});
	}

	private void syncLabels(String north, String east, String south, String west) {
		if (Objects.equals(northLabel, north)
			&& Objects.equals(eastLabel, east)
			&& Objects.equals(southLabel, south)
			&& Objects.equals(westLabel, west)) {
			return;
		}

		northLabel = north;
		eastLabel = east;
		southLabel = south;
		westLabel = west;
		setChanged();

		if (level != null && !level.isClientSide()) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), BLOCK_UPDATE_FLAGS);
		}
	}

	private static String label(Map<Direction, SettlementCandidate> nearest, Direction direction) {
		SettlementCandidate candidate = nearest.get(direction);
		return candidate == null || candidate.name().isBlank() ? null : candidate.name();
	}

	private static void putLabel(ValueOutput output, String key, String label) {
		if (label != null && !label.isBlank()) {
			output.putString(key, label);
		}
	}

	private record SettlementCandidate(String name, double distanceSquared) {
	}

	private record DirectionAssignment(Direction[] faces) {
	}
}
