package com.ronhelwig.livevillages.sim;

import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record OutpostRaidState(
	String outpostSettlementId,
	String targetSettlementId,
	OutpostRaidPhase phase,
	int partySize,
	long createdTick,
	long phaseStartedTick,
	long nextEligibleTick,
	long lastAnnouncementTick,
	int controlProgressTicks,
	String outcome,
	Map<String, Integer> lastLoot,
	Map<String, Integer> lastPlayerRewards
) {
	public static final Codec<OutpostRaidState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.fieldOf("outpost_settlement_id").forGetter(OutpostRaidState::outpostSettlementId),
		Codec.STRING.optionalFieldOf("target_settlement_id", "").forGetter(OutpostRaidState::targetSettlementId),
		OutpostRaidPhase.CODEC.optionalFieldOf("phase", OutpostRaidPhase.COOLDOWN).forGetter(OutpostRaidState::phase),
		Codec.INT.optionalFieldOf("party_size", 0).forGetter(OutpostRaidState::partySize),
		Codec.LONG.optionalFieldOf("created_tick", 0L).forGetter(OutpostRaidState::createdTick),
		Codec.LONG.optionalFieldOf("phase_started_tick", 0L).forGetter(OutpostRaidState::phaseStartedTick),
		Codec.LONG.optionalFieldOf("next_eligible_tick", 0L).forGetter(OutpostRaidState::nextEligibleTick),
		Codec.LONG.optionalFieldOf("last_announcement_tick", 0L).forGetter(OutpostRaidState::lastAnnouncementTick),
		Codec.INT.optionalFieldOf("control_progress_ticks", 0).forGetter(OutpostRaidState::controlProgressTicks),
		Codec.STRING.optionalFieldOf("outcome", "").forGetter(OutpostRaidState::outcome),
		Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("last_loot", Map.of()).forGetter(OutpostRaidState::lastLoot),
		Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("last_player_rewards", Map.of()).forGetter(OutpostRaidState::lastPlayerRewards)
	).apply(instance, OutpostRaidState::new));

	public OutpostRaidState {
		outpostSettlementId = outpostSettlementId == null ? "" : outpostSettlementId;
		targetSettlementId = targetSettlementId == null ? "" : targetSettlementId;
		phase = phase == null ? OutpostRaidPhase.COOLDOWN : phase;
		partySize = Math.max(0, partySize);
		controlProgressTicks = Math.max(0, controlProgressTicks);
		outcome = outcome == null ? "" : outcome;
		lastLoot = lastLoot == null ? Map.of() : Map.copyOf(lastLoot);
		lastPlayerRewards = lastPlayerRewards == null ? Map.of() : Map.copyOf(lastPlayerRewards);
	}

	public static OutpostRaidState mustering(String outpostSettlementId, String targetSettlementId, int partySize, long currentTick, long nextEligibleTick) {
		return new OutpostRaidState(
			outpostSettlementId,
			targetSettlementId,
			OutpostRaidPhase.MUSTERING,
			partySize,
			currentTick,
			currentTick,
			nextEligibleTick,
			0L,
			0,
			"",
			Map.of(),
			Map.of()
		);
	}

	public OutpostRaidState withPhase(OutpostRaidPhase newPhase, long currentTick) {
		return new OutpostRaidState(outpostSettlementId, targetSettlementId, newPhase, partySize, createdTick, currentTick, nextEligibleTick, lastAnnouncementTick, 0, outcome, lastLoot, lastPlayerRewards);
	}

	public OutpostRaidState withAnnouncementTick(long currentTick) {
		return new OutpostRaidState(outpostSettlementId, targetSettlementId, phase, partySize, createdTick, phaseStartedTick, nextEligibleTick, currentTick, controlProgressTicks, outcome, lastLoot, lastPlayerRewards);
	}

	public OutpostRaidState withControlProgress(int newControlProgressTicks) {
		return new OutpostRaidState(outpostSettlementId, targetSettlementId, phase, partySize, createdTick, phaseStartedTick, nextEligibleTick, lastAnnouncementTick, newControlProgressTicks, outcome, lastLoot, lastPlayerRewards);
	}

	public OutpostRaidState withClockOffset(long offsetTicks) {
		if (offsetTicks == 0L) {
			return this;
		}

		return new OutpostRaidState(
			outpostSettlementId,
			targetSettlementId,
			phase,
			partySize,
			createdTick <= 0L ? createdTick : createdTick + offsetTicks,
			phaseStartedTick <= 0L ? phaseStartedTick : phaseStartedTick + offsetTicks,
			nextEligibleTick <= 0L ? nextEligibleTick : nextEligibleTick + offsetTicks,
			lastAnnouncementTick <= 0L ? lastAnnouncementTick : lastAnnouncementTick + offsetTicks,
			controlProgressTicks,
			outcome,
			lastLoot,
			lastPlayerRewards
		);
	}

	public OutpostRaidState completed(String outcome, long currentTick, long nextEligibleTick) {
		return completed(outcome, currentTick, nextEligibleTick, Map.of(), Map.of());
	}

	public OutpostRaidState returning(
		String outcome,
		long currentTick,
		long nextEligibleTick,
		Map<String, Integer> lastLoot,
		Map<String, Integer> lastPlayerRewards
	) {
		return new OutpostRaidState(outpostSettlementId, targetSettlementId, OutpostRaidPhase.RETURNING, partySize, createdTick, currentTick, nextEligibleTick, lastAnnouncementTick, controlProgressTicks, outcome, lastLoot, lastPlayerRewards);
	}

	public OutpostRaidState completed(
		String outcome,
		long currentTick,
		long nextEligibleTick,
		Map<String, Integer> lastLoot,
		Map<String, Integer> lastPlayerRewards
	) {
		return new OutpostRaidState(outpostSettlementId, targetSettlementId, OutpostRaidPhase.COOLDOWN, partySize, createdTick, currentTick, nextEligibleTick, lastAnnouncementTick, controlProgressTicks, outcome, lastLoot, lastPlayerRewards);
	}
}
