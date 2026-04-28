package com.ronhelwig.livevillages.network;

import java.util.List;
import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record BuildSitePreviewSnapshot(
	String statusMessage,
	String settlementName,
	String buildSiteId,
	String buildSiteType,
	int distanceBlocks,
	boolean prospective,
	boolean placementValid,
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
		BuildSitePreviewBlockView.CODEC.listOf().optionalFieldOf("blocks", List.of()).forGetter(BuildSitePreviewSnapshot::blocks)
	).apply(instance, BuildSitePreviewSnapshot::new));

	public BuildSitePreviewSnapshot {
		Objects.requireNonNull(statusMessage, "statusMessage");
		Objects.requireNonNull(settlementName, "settlementName");
		Objects.requireNonNull(buildSiteId, "buildSiteId");
		Objects.requireNonNull(buildSiteType, "buildSiteType");
		blocks = List.copyOf(blocks);
	}

	public static BuildSitePreviewSnapshot unavailable(String statusMessage) {
		return new BuildSitePreviewSnapshot(statusMessage, "", "", "", -1, false, false, List.of());
	}

	public static BuildSitePreviewSnapshot active(
		String settlementName,
		String buildSiteId,
		String buildSiteType,
		int distanceBlocks,
		List<BuildSitePreviewBlockView> blocks
	) {
		return new BuildSitePreviewSnapshot("", settlementName, buildSiteId, buildSiteType, distanceBlocks, false, true, blocks);
	}

	public static BuildSitePreviewSnapshot prospective(
		String settlementName,
		String buildSiteId,
		String buildSiteType,
		int distanceBlocks,
		boolean placementValid,
		List<BuildSitePreviewBlockView> blocks
	) {
		return new BuildSitePreviewSnapshot("", settlementName, buildSiteId, buildSiteType, distanceBlocks, true, placementValid, blocks);
	}

	public boolean available() {
		return !buildSiteType.isBlank() && !blocks.isEmpty();
	}
}
