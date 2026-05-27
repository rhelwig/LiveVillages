package com.ronhelwig.livevillages.network;

import java.util.Optional;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.ronhelwig.livevillages.menu.TradeBoardSettlementView;

public record SettlementOverlaySnapshot(
	String statusMessage,
	int distanceBlocks,
	String playerStandingLabel,
	Optional<TradeBoardSettlementView> settlement,
	Optional<SettlementOverlayConstructionView> construction,
	List<SettlementOverlayTaskView> tasks,
	List<SettlementOverlayWorkerView> workers
) {
	public static final Codec<SettlementOverlaySnapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.optionalFieldOf("status_message", "").forGetter(SettlementOverlaySnapshot::statusMessage),
		Codec.INT.optionalFieldOf("distance_blocks", -1).forGetter(SettlementOverlaySnapshot::distanceBlocks),
		Codec.STRING.optionalFieldOf("player_standing_label", "").forGetter(SettlementOverlaySnapshot::playerStandingLabel),
		TradeBoardSettlementView.CODEC.optionalFieldOf("settlement").forGetter(SettlementOverlaySnapshot::settlement),
		SettlementOverlayConstructionView.CODEC.optionalFieldOf("construction").forGetter(SettlementOverlaySnapshot::construction),
		SettlementOverlayTaskView.CODEC.listOf().optionalFieldOf("tasks", List.of()).forGetter(SettlementOverlaySnapshot::tasks),
		SettlementOverlayWorkerView.CODEC.listOf().optionalFieldOf("workers", List.of()).forGetter(SettlementOverlaySnapshot::workers)
	).apply(instance, SettlementOverlaySnapshot::new));

	public static SettlementOverlaySnapshot unavailable(String statusMessage) {
		return new SettlementOverlaySnapshot(statusMessage, -1, "", Optional.empty(), Optional.empty(), List.of(), List.of());
	}

	public static SettlementOverlaySnapshot available(
		TradeBoardSettlementView settlement,
		int distanceBlocks,
		String playerStandingLabel,
		SettlementOverlayConstructionView construction,
		List<SettlementOverlayTaskView> tasks,
		List<SettlementOverlayWorkerView> workers
	) {
		return new SettlementOverlaySnapshot("", distanceBlocks, playerStandingLabel, Optional.of(settlement), Optional.of(construction), tasks, workers);
	}
}
