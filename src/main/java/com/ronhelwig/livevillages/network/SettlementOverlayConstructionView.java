package com.ronhelwig.livevillages.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record SettlementOverlayConstructionView(
	int activeBuildSites,
	int eligibleWorkers,
	int pendingBlocks,
	int missingMaterialBlocks,
	int blockedBlocks,
	int carriedSupplies
) {
	public static final Codec<SettlementOverlayConstructionView> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.INT.optionalFieldOf("active_build_sites", 0).forGetter(SettlementOverlayConstructionView::activeBuildSites),
		Codec.INT.optionalFieldOf("eligible_workers", 0).forGetter(SettlementOverlayConstructionView::eligibleWorkers),
		Codec.INT.optionalFieldOf("pending_blocks", 0).forGetter(SettlementOverlayConstructionView::pendingBlocks),
		Codec.INT.optionalFieldOf("missing_material_blocks", 0).forGetter(SettlementOverlayConstructionView::missingMaterialBlocks),
		Codec.INT.optionalFieldOf("blocked_blocks", 0).forGetter(SettlementOverlayConstructionView::blockedBlocks),
		Codec.INT.optionalFieldOf("carried_supplies", 0).forGetter(SettlementOverlayConstructionView::carriedSupplies)
	).apply(instance, SettlementOverlayConstructionView::new));
}
