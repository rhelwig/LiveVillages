package com.ronhelwig.livevillages.sim;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
	BlockPos anchorPos,
	Direction facing,
	String woodFamily,
	String stoneMaterial,
	Map<String, Integer> siteMaterials,
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
		BlockPos.CODEC.optionalFieldOf("anchor_pos").forGetter(buildSite -> Optional.of(buildSite.anchorPos())),
		Direction.CODEC.fieldOf("facing").forGetter(SettlementBuildSite::facing),
		Codec.STRING.optionalFieldOf("wood_family", "oak").forGetter(SettlementBuildSite::woodFamily),
		Codec.STRING.optionalFieldOf("stone_material", "cobblestone").forGetter(SettlementBuildSite::stoneMaterial),
		Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("site_materials", Map.of()).forGetter(SettlementBuildSite::siteMaterials),
		SettlementBuildBlockState.CODEC.listOf().optionalFieldOf("blocks", List.of()).forGetter(SettlementBuildSite::blocks),
		Codec.BOOL.optionalFieldOf("complete", false).forGetter(SettlementBuildSite::complete),
		Codec.LONG.optionalFieldOf("created_tick", 0L).forGetter(SettlementBuildSite::createdTick),
		Codec.LONG.optionalFieldOf("updated_tick", 0L).forGetter(SettlementBuildSite::updatedTick)
	).apply(instance, SettlementBuildSite::decode));

	private static SettlementBuildSite decode(
		String id,
		String settlementId,
		SettlementBuildSiteType blueprintId,
		BlockPos origin,
		BlockPos workstationPos,
		Optional<BlockPos> anchorPos,
		Direction facing,
		String woodFamily,
		String stoneMaterial,
		Map<String, Integer> siteMaterials,
		List<SettlementBuildBlockState> blocks,
		boolean complete,
		long createdTick,
		long updatedTick
	) {
		return new SettlementBuildSite(
			id,
			settlementId,
			blueprintId,
			origin,
			workstationPos,
			anchorPos.orElse(workstationPos),
			facing,
			woodFamily,
			stoneMaterial,
			siteMaterials,
			blocks,
			complete,
			createdTick,
			updatedTick
		);
	}

	public SettlementBuildSite {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(settlementId, "settlementId");
		Objects.requireNonNull(blueprintId, "blueprintId");
		Objects.requireNonNull(origin, "origin");
		Objects.requireNonNull(workstationPos, "workstationPos");
		Objects.requireNonNull(anchorPos, "anchorPos");
		Objects.requireNonNull(facing, "facing");
		Objects.requireNonNull(woodFamily, "woodFamily");
		Objects.requireNonNull(stoneMaterial, "stoneMaterial");
		Objects.requireNonNull(siteMaterials, "siteMaterials");
		siteMaterials = copyPositiveMaterials(siteMaterials);
		blocks = List.copyOf(blocks);
	}

	public SettlementBuildSite withBlocks(List<SettlementBuildBlockState> newBlocks, boolean newComplete, long tick) {
		return new SettlementBuildSite(
			id,
			settlementId,
			blueprintId,
			origin,
			workstationPos,
			anchorPos,
			facing,
			woodFamily,
			stoneMaterial,
			siteMaterials,
			newBlocks,
			newComplete,
			createdTick,
			tick
		);
	}

	public SettlementBuildSite withMaterials(String newWoodFamily, String newStoneMaterial, long tick) {
		return new SettlementBuildSite(
			id,
			settlementId,
			blueprintId,
			origin,
			workstationPos,
			anchorPos,
			facing,
			newWoodFamily,
			newStoneMaterial,
			siteMaterials,
			blocks,
			complete,
			createdTick,
			tick
		);
	}

	public SettlementBuildSite withSiteMaterials(Map<String, Integer> newSiteMaterials, long tick) {
		return new SettlementBuildSite(
			id,
			settlementId,
			blueprintId,
			origin,
			workstationPos,
			anchorPos,
			facing,
			woodFamily,
			stoneMaterial,
			newSiteMaterials,
			blocks,
			complete,
			createdTick,
			tick
		);
	}

	public SettlementBuildSite withAddedSiteMaterials(Map<String, Integer> addedSiteMaterials, long tick) {
		Map<String, Integer> merged = new LinkedHashMap<>(siteMaterials);
		addedSiteMaterials.forEach((goodsKey, amount) -> {
			if (goodsKey != null && !goodsKey.isBlank() && amount != null && amount > 0) {
				merged.merge(goodsKey, amount, Integer::sum);
			}
		});
		return withSiteMaterials(merged, tick);
	}

	public boolean referencesWorkstation(BlockPos pos) {
		return anchorPos.equals(pos) || workstationPos.equals(pos);
	}

	private static Map<String, Integer> copyPositiveMaterials(Map<String, Integer> materials) {
		Map<String, Integer> copy = new LinkedHashMap<>();
		materials.forEach((goodsKey, amount) -> {
			if (goodsKey != null && !goodsKey.isBlank() && amount != null && amount > 0) {
				copy.put(goodsKey, amount);
			}
		});
		return Map.copyOf(copy);
	}
}
