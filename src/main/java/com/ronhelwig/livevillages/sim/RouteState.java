package com.ronhelwig.livevillages.sim;

import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record RouteState(
	String id,
	ResourceKey<Level> dimension,
	String fromSettlementId,
	String toSettlementId,
	RouteType type,
	RouteTier tier,
	int distanceBlocks,
	double quality,
	double security,
	int throughputBase,
	String lastTransferSummary,
	long lastTradeTick,
	long lastTradeAttemptTick,
	long lastSurveyTick
) {
	public static final Codec<RouteState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.fieldOf("id").forGetter(RouteState::id),
		Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(RouteState::dimension),
		Codec.STRING.fieldOf("from_settlement_id").forGetter(RouteState::fromSettlementId),
		Codec.STRING.fieldOf("to_settlement_id").forGetter(RouteState::toSettlementId),
		RouteType.CODEC.fieldOf("type").forGetter(RouteState::type),
		RouteTier.CODEC.fieldOf("tier").forGetter(RouteState::tier),
		Codec.INT.optionalFieldOf("distance_blocks", 0).forGetter(RouteState::distanceBlocks),
		Codec.DOUBLE.optionalFieldOf("quality", 0.25D).forGetter(RouteState::quality),
		Codec.DOUBLE.optionalFieldOf("security", 1.0D).forGetter(RouteState::security),
		Codec.INT.optionalFieldOf("throughput_base", 64).forGetter(RouteState::throughputBase),
		Codec.STRING.optionalFieldOf("last_transfer_summary", "").forGetter(RouteState::lastTransferSummary),
		Codec.LONG.optionalFieldOf("last_trade_tick", 0L).forGetter(RouteState::lastTradeTick),
		Codec.LONG.optionalFieldOf("last_trade_attempt_tick", 0L).forGetter(RouteState::lastTradeAttemptTick),
		Codec.LONG.optionalFieldOf("last_survey_tick", 0L).forGetter(RouteState::lastSurveyTick)
	).apply(instance, RouteState::new));

	public RouteState {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(dimension, "dimension");
		Objects.requireNonNull(fromSettlementId, "fromSettlementId");
		Objects.requireNonNull(toSettlementId, "toSettlementId");
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(tier, "tier");
	}

	public static RouteState create(String id, ResourceKey<Level> dimension, String fromSettlementId, String toSettlementId, RouteType type) {
		return new RouteState(id, dimension, fromSettlementId, toSettlementId, type, RouteTier.NONE, 0, 0.25D, 1.0D, 64, "", 0L, 0L, 0L);
	}

	public RouteState withLastSurveyTick(long tick) {
		return new RouteState(
			id,
			dimension,
			fromSettlementId,
			toSettlementId,
			type,
			tier,
			distanceBlocks,
			quality,
			security,
			throughputBase,
			lastTransferSummary,
			lastTradeTick,
			lastTradeAttemptTick,
			tick
		);
	}

	public RouteState withSurvey(RouteTier newTier, int newDistanceBlocks, double newQuality, double newSecurity, int newThroughputBase, long tick) {
		return new RouteState(
			id,
			dimension,
			fromSettlementId,
			toSettlementId,
			type,
			newTier,
			newDistanceBlocks,
			newQuality,
			newSecurity,
			newThroughputBase,
			lastTransferSummary,
			lastTradeTick,
			lastTradeAttemptTick,
			tick
		);
	}

	public RouteState withTradeAttemptTick(long tick) {
		return new RouteState(
			id,
			dimension,
			fromSettlementId,
			toSettlementId,
			type,
			tier,
			distanceBlocks,
			quality,
			security,
			throughputBase,
			lastTransferSummary,
			lastTradeTick,
			tick,
			lastSurveyTick
		);
	}

	public RouteState withTradeSummary(String summary, long tick) {
		return new RouteState(
			id,
			dimension,
			fromSettlementId,
			toSettlementId,
			type,
			tier,
			distanceBlocks,
			quality,
			security,
			throughputBase,
			summary,
			tick,
			tick,
			lastSurveyTick
		);
	}
}
