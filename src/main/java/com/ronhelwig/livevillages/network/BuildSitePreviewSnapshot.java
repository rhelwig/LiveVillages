package com.ronhelwig.livevillages.network;

import java.util.List;
import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;

public record BuildSitePreviewSnapshot(
	String statusMessage,
	String settlementName,
	String buildSiteId,
	String buildSiteType,
	int distanceBlocks,
	boolean prospective,
	boolean placementValid,
	List<BlockPos> blockerPositions,
	List<BuildSitePreviewBlockView> blocks
) {
	public static final Codec<BuildSitePreviewSnapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.optionalFieldOf("status_message", "").forGetter(BuildSitePreviewSnapshot::statusMessage),
		Codec.STRING.optionalFieldOf("settlement_name", "").forGetter(BuildSitePreviewSnapshot::settlementName),
		Codec.STRING.optionalFieldOf("build_site_id", "").forGetter(BuildSitePreviewSnapshot::buildSiteId),
		Codec.STRING.optionalFieldOf("build_site_type", "").forGetter(BuildSitePreviewSnapshot::buildSiteType),
		Codec.INT.optionalFieldOf("distance_blocks", -1).forGetter(BuildSitePreviewSnapshot::distanceBlocks),
		Codec.BOOL.optionalFieldOf("prospective", false).forGetter(BuildSitePreviewSnapshot::prospective),
		Codec.BOOL.optionalFieldOf("placement_valid", true).forGetter(BuildSitePreviewSnapshot::placementValid),
		BlockPos.CODEC.listOf().optionalFieldOf("blocker_positions", List.of()).forGetter(BuildSitePreviewSnapshot::blockerPositions),
		BuildSitePreviewBlockView.CODEC.listOf().optionalFieldOf("blocks", List.of()).forGetter(BuildSitePreviewSnapshot::blocks)
	).apply(instance, BuildSitePreviewSnapshot::new));

	public BuildSitePreviewSnapshot {
		Objects.requireNonNull(statusMessage, "statusMessage");
		Objects.requireNonNull(settlementName, "settlementName");
		Objects.requireNonNull(buildSiteId, "buildSiteId");
		Objects.requireNonNull(buildSiteType, "buildSiteType");
		blockerPositions = blockerPositions.stream().map(BlockPos::immutable).toList();
		blocks = List.copyOf(blocks);
	}

	public static BuildSitePreviewSnapshot unavailable(String statusMessage) {
		return new BuildSitePreviewSnapshot(statusMessage, "", "", "", -1, false, false, List.of(), List.of());
	}

	public static BuildSitePreviewSnapshot active(
		String statusMessage,
		String settlementName,
		String buildSiteId,
		String buildSiteType,
		int distanceBlocks,
		List<BlockPos> blockerPositions,
		List<BuildSitePreviewBlockView> blocks
	) {
		return new BuildSitePreviewSnapshot(statusMessage, settlementName, buildSiteId, buildSiteType, distanceBlocks, false, true, blockerPositions, blocks);
	}

	public static BuildSitePreviewSnapshot prospective(
		String statusMessage,
		String settlementName,
		String buildSiteId,
		String buildSiteType,
		int distanceBlocks,
		boolean placementValid,
		List<BlockPos> blockerPositions,
		List<BuildSitePreviewBlockView> blocks
	) {
		return new BuildSitePreviewSnapshot(statusMessage, settlementName, buildSiteId, buildSiteType, distanceBlocks, true, placementValid, blockerPositions, blocks);
	}

	public boolean available() {
		return !buildSiteType.isBlank() && !blocks.isEmpty();
	}
}
