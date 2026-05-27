package com.ronhelwig.livevillages.sim;

import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record OutpostPlayerStanding(
	OutpostPlayerRank rank,
	int supportPoints,
	long lastParleyTick
) {
	public static final Codec<OutpostPlayerStanding> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		OutpostPlayerRank.CODEC.optionalFieldOf("rank", OutpostPlayerRank.UNKNOWN).forGetter(OutpostPlayerStanding::rank),
		Codec.INT.optionalFieldOf("support_points", 0).forGetter(OutpostPlayerStanding::supportPoints),
		Codec.LONG.optionalFieldOf("last_parley_tick", 0L).forGetter(OutpostPlayerStanding::lastParleyTick)
	).apply(instance, OutpostPlayerStanding::new));

	public OutpostPlayerStanding {
		Objects.requireNonNull(rank, "rank");
		supportPoints = Math.max(0, supportPoints);
	}

	public static OutpostPlayerStanding unknown() {
		return new OutpostPlayerStanding(OutpostPlayerRank.UNKNOWN, 0, 0L);
	}

	public OutpostPlayerStanding withRankAtLeast(OutpostPlayerRank minimumRank) {
		return rank.atLeast(minimumRank) ? this : new OutpostPlayerStanding(minimumRank, supportPoints, lastParleyTick);
	}

	public OutpostPlayerStanding withParley(OutpostPlayerRank minimumRank, long tick) {
		OutpostPlayerRank newRank = rank.atLeast(minimumRank) ? rank : minimumRank;
		return new OutpostPlayerStanding(newRank, supportPoints, tick);
	}

	public OutpostPlayerStanding withSupportAdded(int addedSupportPoints, int associateThreshold) {
		return withSupportAdded(addedSupportPoints, associateThreshold, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
	}

	public OutpostPlayerStanding withSupportAdded(
		int addedSupportPoints,
		int associateThreshold,
		int raiderThreshold,
		int bannerBearerThreshold,
		int captainThreshold
	) {
		if (addedSupportPoints <= 0) {
			return this;
		}

		int newSupportPoints = supportPoints + addedSupportPoints;
		OutpostPlayerRank newRank = rank;
		if (newRank.ordinal() < OutpostPlayerRank.ASSOCIATE.ordinal() && newSupportPoints >= associateThreshold) {
			newRank = OutpostPlayerRank.ASSOCIATE;
		}
		if (newRank.ordinal() < OutpostPlayerRank.RAIDER.ordinal() && newSupportPoints >= raiderThreshold) {
			newRank = OutpostPlayerRank.RAIDER;
		}
		if (newRank.ordinal() < OutpostPlayerRank.BANNER_BEARER.ordinal() && newSupportPoints >= bannerBearerThreshold) {
			newRank = OutpostPlayerRank.BANNER_BEARER;
		}
		if (newRank.ordinal() < OutpostPlayerRank.CAPTAIN.ordinal() && newSupportPoints >= captainThreshold) {
			newRank = OutpostPlayerRank.CAPTAIN;
		}
		return new OutpostPlayerStanding(newRank, newSupportPoints, lastParleyTick);
	}
}
