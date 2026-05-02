package com.ronhelwig.livevillages.menu;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.ronhelwig.livevillages.sim.SettlementKind;

public record TradeBoardSettlementView(
	String settlementId,
	String settlementName,
	SettlementKind settlementKind,
	int tier,
	int population,
	int housingCapacity,
	int emeraldWealth,
	double comfort,
	double security,
	String growthSummary,
	List<TradeBoardRoleView> roleCounts,
	List<TradeBoardGoodsView> shortages,
	List<TradeBoardGoodsView> surpluses,
	List<TradeBoardGoodsView> stock,
	List<TradeBoardRouteView> routes,
	List<TradeBoardProjectView> projects
) {
	public static final Codec<TradeBoardSettlementView> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.fieldOf("settlement_id").forGetter(TradeBoardSettlementView::settlementId),
		Codec.STRING.fieldOf("settlement_name").forGetter(TradeBoardSettlementView::settlementName),
		SettlementKind.CODEC.fieldOf("settlement_kind").forGetter(TradeBoardSettlementView::settlementKind),
		Codec.INT.optionalFieldOf("tier", 1).forGetter(TradeBoardSettlementView::tier),
		Codec.INT.optionalFieldOf("population", 0).forGetter(TradeBoardSettlementView::population),
		Codec.INT.optionalFieldOf("housing_capacity", 0).forGetter(TradeBoardSettlementView::housingCapacity),
		Codec.INT.optionalFieldOf("emerald_wealth", 0).forGetter(TradeBoardSettlementView::emeraldWealth),
		Codec.DOUBLE.optionalFieldOf("comfort", 1.0D).forGetter(TradeBoardSettlementView::comfort),
		Codec.DOUBLE.optionalFieldOf("security", 0.0D).forGetter(TradeBoardSettlementView::security),
		Codec.STRING.optionalFieldOf("growth_summary", "Stable").forGetter(TradeBoardSettlementView::growthSummary),
		TradeBoardRoleView.CODEC.listOf().optionalFieldOf("role_counts", List.of()).forGetter(TradeBoardSettlementView::roleCounts),
		TradeBoardGoodsView.CODEC.listOf().optionalFieldOf("shortages", List.of()).forGetter(TradeBoardSettlementView::shortages),
		TradeBoardGoodsView.CODEC.listOf().optionalFieldOf("surpluses", List.of()).forGetter(TradeBoardSettlementView::surpluses),
		TradeBoardGoodsView.CODEC.listOf().optionalFieldOf("stock", List.of()).forGetter(TradeBoardSettlementView::stock),
		TradeBoardRouteView.CODEC.listOf().optionalFieldOf("routes", List.of()).forGetter(TradeBoardSettlementView::routes),
		TradeBoardProjectView.CODEC.listOf().optionalFieldOf("projects", List.of()).forGetter(TradeBoardSettlementView::projects)
	).apply(instance, TradeBoardSettlementView::new));
}
