package com.ronhelwig.livevillages.menu;

import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record TradeBoardRaidView(
	String status,
	String targetSettlementName,
	String outcome,
	String phaseLabel,
	int partySize,
	List<TradeBoardGoodsView> loot,
	Map<String, Integer> playerRewards
) {
	public static final TradeBoardRaidView EMPTY = new TradeBoardRaidView("", "", "", "", 0, List.of(), Map.of());

	public static final Codec<TradeBoardRaidView> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.optionalFieldOf("status", "").forGetter(TradeBoardRaidView::status),
		Codec.STRING.optionalFieldOf("target_settlement_name", "").forGetter(TradeBoardRaidView::targetSettlementName),
		Codec.STRING.optionalFieldOf("outcome", "").forGetter(TradeBoardRaidView::outcome),
		Codec.STRING.optionalFieldOf("phase_label", "").forGetter(TradeBoardRaidView::phaseLabel),
		Codec.INT.optionalFieldOf("party_size", 0).forGetter(TradeBoardRaidView::partySize),
		TradeBoardGoodsView.CODEC.listOf().optionalFieldOf("loot", List.of()).forGetter(TradeBoardRaidView::loot),
		Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("player_rewards", Map.of()).forGetter(TradeBoardRaidView::playerRewards)
	).apply(instance, TradeBoardRaidView::new));

	public TradeBoardRaidView {
		status = status == null ? "" : status;
		targetSettlementName = targetSettlementName == null ? "" : targetSettlementName;
		outcome = outcome == null ? "" : outcome;
		phaseLabel = phaseLabel == null ? "" : phaseLabel;
		partySize = Math.max(0, partySize);
		loot = loot == null ? List.of() : List.copyOf(loot);
		playerRewards = playerRewards == null ? Map.of() : Map.copyOf(playerRewards);
	}
}
