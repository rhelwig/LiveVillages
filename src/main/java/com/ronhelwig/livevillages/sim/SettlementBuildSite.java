package com.ronhelwig.livevillages.sim;

import java.util.List;
import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record SettlementBuildSite(
	String id,
	String settlementId,
	SettlementBuildSiteType blueprintId,
	BlockPos origin,
	BlockPos workstationPos,
	Direction facing,
	String woodFamily,
	String stoneMaterial,
	List<SettlementBuildBlockState> blocks,
	boolean complete,
	long createdTick,
	long updatedTick
) {
	public static final Codec<SettlementBuildSite> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.fieldOf("id").forGetter(SettlementBuildSite::id),
		Codec.STRING.optionalFieldOf("settlement_id", "").forGetter(SettlementBuildSite::settlementId),
		SettlementBuildSiteType.CODEC.fieldOf("blueprint_id").forGetter(SettlementBuildSite::blueprintId),
		BlockPos.CODEC.fieldOf("origin").forGetter(SettlementBuildSite::origin),
		BlockPos.CODEC.fieldOf("workstation_pos").forGetter(SettlementBuildSite::workstationPos),
		Direction.CODEC.fieldOf("facing").forGetter(SettlementBuildSite::facing),
		Codec.STRING.optionalFieldOf("wood_family", "oak").forGetter(SettlementBuildSite::woodFamily),
		Codec.STRING.optionalFieldOf("stone_material", "cobblestone").forGetter(SettlementBuildSite::stoneMaterial),
		SettlementBuildBlockState.CODEC.listOf().optionalFieldOf("blocks", List.of()).forGetter(SettlementBuildSite::blocks),
		Codec.BOOL.optionalFieldOf("complete", false).forGetter(SettlementBuildSite::complete),
		Codec.LONG.optionalFieldOf("created_tick", 0L).forGetter(SettlementBuildSite::createdTick),
		Codec.LONG.optionalFieldOf("updated_tick", 0L).forGetter(SettlementBuildSite::updatedTick)
	).apply(instance, SettlementBuildSite::new));

	public SettlementBuildSite {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(settlementId, "settlementId");
		Objects.requireNonNull(blueprintId, "blueprintId");
		Objects.requireNonNull(origin, "origin");
		Objects.requireNonNull(workstationPos, "workstationPos");
		Objects.requireNonNull(facing, "facing");
		Objects.requireNonNull(woodFamily, "woodFamily");
		Objects.requireNonNull(stoneMaterial, "stoneMaterial");
		blocks = List.copyOf(blocks);
	}

	public SettlementBuildSite withBlocks(List<SettlementBuildBlockState> newBlocks, boolean newComplete, long tick) {
		return new SettlementBuildSite(
			id,
			settlementId,
			blueprintId,
			origin,
			workstationPos,
			facing,
			woodFamily,
			stoneMaterial,
			newBlocks,
			newComplete,
			createdTick,
			tick
		);
	}
}
