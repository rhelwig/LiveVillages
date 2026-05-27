package com.ronhelwig.livevillages.sim;

import java.util.Collection;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class SettlementPlayerStandings {
	private static final RankThresholds BASE_SUPPORT_THRESHOLDS = new RankThresholds(24, 100, 240, 500);
	private static final int COMPLETED_BUILD_SITE_SUPPORT_BONUS = 8;

	private SettlementPlayerStandings() {
	}

	public static OutpostPlayerStanding defaultStanding(SettlementKind settlementKind) {
		return settlementKind == SettlementKind.OUTPOST
			? OutpostPlayerStanding.unknown()
			: new OutpostPlayerStanding(OutpostPlayerRank.ASSOCIATE, 0, 0L);
	}

	public static String displayStanding(LiveVillagesSavedData savedData, SettlementState settlement, ServerPlayer player) {
		if (savedData == null || settlement == null || player == null) {
			return "";
		}

		return displayLabelWithProgress(settlement.kind(), savedData.settlementPlayerStanding(settlement, player), rankThresholds((ServerLevel) player.level()));
	}

	public static String debugStanding(LiveVillagesSavedData savedData, SettlementState settlement, ServerPlayer player) {
		if (savedData == null || settlement == null || player == null) {
			return "";
		}

		OutpostPlayerStanding standing = savedData.settlementPlayerStanding(settlement, player);
		int nextThreshold = nextSupportThreshold(standing.rank(), rankThresholds((ServerLevel) player.level()));
		String progress = nextThreshold == Integer.MAX_VALUE
			? standing.supportPoints() + " support"
			: standing.supportPoints() + "/" + nextThreshold + " support";
		return displayLabel(settlement.kind(), standing.rank()) + " (" + progress + ")";
	}

	public static String displayLabel(SettlementKind settlementKind, OutpostPlayerRank rank) {
		if (rank == null) {
			return "";
		}

		if (settlementKind == SettlementKind.OUTPOST) {
			return switch (rank) {
				case UNKNOWN -> "Unknown";
				case TOLERATED -> "Tolerated";
				case ASSOCIATE -> "Associate";
				case RAIDER -> "Raider";
				case BANNER_BEARER -> "Banner Bearer";
				case CAPTAIN -> "Captain";
			};
		}

		return switch (rank) {
			case UNKNOWN -> "Stranger";
			case TOLERATED -> "Visitor";
			case ASSOCIATE -> "Friend";
			case RAIDER -> "Ally";
			case BANNER_BEARER -> "Hero";
			case CAPTAIN -> "Champion";
		};
	}

	private static String displayLabelWithProgress(SettlementKind settlementKind, OutpostPlayerStanding standing, RankThresholds thresholds) {
		if (standing == null) {
			return "";
		}

		String label = displayLabel(settlementKind, standing.rank());
		int nextThreshold = nextSupportThreshold(standing.rank(), thresholds);
		if (nextThreshold == Integer.MAX_VALUE) {
			return label;
		}

		return label + " " + standing.supportPoints() + "/" + nextThreshold;
	}

	public static OutpostPlayerStanding mergeHistoricalStandings(
		SettlementKind settlementKind,
		Collection<OutpostPlayerStanding> standings
	) {
		if (standings == null || standings.isEmpty()) {
			return defaultStanding(settlementKind);
		}

		OutpostPlayerRank rank = defaultStanding(settlementKind).rank();
		int supportPoints = 0;
		long lastParleyTick = 0L;

		for (OutpostPlayerStanding standing : standings) {
			if (standing == null) {
				continue;
			}

			if (standing.rank().ordinal() > rank.ordinal()) {
				rank = standing.rank();
			}

			supportPoints += standing.supportPoints();
			lastParleyTick = Math.max(lastParleyTick, standing.lastParleyTick());
		}

		rank = higherRank(rank, rankForSupport(supportPoints, BASE_SUPPORT_THRESHOLDS));
		return new OutpostPlayerStanding(rank, supportPoints, lastParleyTick);
	}

	public static OutpostPlayerStanding bestStanding(
		SettlementKind settlementKind,
		Collection<OutpostPlayerStanding> standings
	) {
		OutpostPlayerStanding best = defaultStanding(settlementKind);

		if (standings == null) {
			return best;
		}

		for (OutpostPlayerStanding standing : standings) {
			if (standing == null) {
				continue;
			}

			if (standing.rank().ordinal() > best.rank().ordinal()
				|| (standing.rank() == best.rank() && standing.supportPoints() > best.supportPoints())) {
				best = standing;
			}
		}

		return best;
	}

	public static void recordBuildSupport(
		ServerLevel level,
		ServerPlayer player,
		SettlementState settlement,
		int placedBlocks,
		boolean completedBuildSite
	) {
		if (level == null || player == null || settlement == null || placedBlocks <= 0) {
			return;
		}

		int support = placedBlocks + (completedBuildSite ? COMPLETED_BUILD_SITE_SUPPORT_BONUS : 0);
		recordSupport(level, player, settlement, support);
	}

	public static void recordTradeSupport(ServerLevel level, ServerPlayer player, SettlementState settlement) {
		recordSupport(level, player, settlement, 1);
	}

	public static void recordTradeSupport(ServerLevel level, ServerPlayer player, SettlementState settlement, int support) {
		recordSupport(level, player, settlement, support);
	}

	public static void recordSupport(ServerLevel level, ServerPlayer player, SettlementState settlement, int support) {
		if (level == null || player == null || settlement == null || support <= 0) {
			return;
		}

		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(level.getServer());
		OutpostPlayerStanding previous = savedData.settlementPlayerStanding(settlement, player);
		if (settlement.kind() == SettlementKind.OUTPOST && !previous.rank().atLeast(OutpostPlayerRank.TOLERATED)) {
			return;
		}

		RankThresholds thresholds = rankThresholds(level);
		OutpostPlayerStanding updated = previous.withSupportAdded(
			support,
			thresholds.associate(),
			thresholds.raider(),
			thresholds.bannerBearer(),
			thresholds.captain()
		);

		if (updated.equals(previous)) {
			return;
		}

		savedData.setSettlementPlayerStanding(settlement, player, updated);
		if (updated.rank() != previous.rank()) {
			player.sendSystemMessage(Component.literal(settlement.name() + " now treats you as " + displayLabel(settlement.kind(), updated.rank()) + "."), true);
		}
	}

	private static OutpostPlayerRank rankForSupport(int supportPoints, RankThresholds thresholds) {
		if (supportPoints >= thresholds.captain()) {
			return OutpostPlayerRank.CAPTAIN;
		}

		if (supportPoints >= thresholds.bannerBearer()) {
			return OutpostPlayerRank.BANNER_BEARER;
		}

		if (supportPoints >= thresholds.raider()) {
			return OutpostPlayerRank.RAIDER;
		}

		if (supportPoints >= thresholds.associate()) {
			return OutpostPlayerRank.ASSOCIATE;
		}

		return OutpostPlayerRank.UNKNOWN;
	}

	private static OutpostPlayerRank higherRank(OutpostPlayerRank first, OutpostPlayerRank second) {
		return first.ordinal() >= second.ordinal() ? first : second;
	}

	private static int nextSupportThreshold(OutpostPlayerRank rank, RankThresholds thresholds) {
		return switch (rank) {
			case UNKNOWN, TOLERATED -> thresholds.associate();
			case ASSOCIATE -> thresholds.raider();
			case RAIDER -> thresholds.bannerBearer();
			case BANNER_BEARER -> thresholds.captain();
			case CAPTAIN -> Integer.MAX_VALUE;
		};
	}

	private static RankThresholds rankThresholds(ServerLevel level) {
		if (level == null) {
			return BASE_SUPPORT_THRESHOLDS;
		}

		double multiplier = switch (level.getDifficulty()) {
			case PEACEFUL -> 0.75D;
			case EASY -> 1.0D;
			case NORMAL -> 1.35D;
			case HARD -> 1.75D;
		};
		return BASE_SUPPORT_THRESHOLDS.scaled(multiplier);
	}

	private record RankThresholds(int associate, int raider, int bannerBearer, int captain) {
		private RankThresholds scaled(double multiplier) {
			return new RankThresholds(
				scaleThreshold(associate, multiplier),
				scaleThreshold(raider, multiplier),
				scaleThreshold(bannerBearer, multiplier),
				scaleThreshold(captain, multiplier)
			);
		}

		private static int scaleThreshold(int threshold, double multiplier) {
			return Math.max(1, (int) Math.ceil(threshold * multiplier));
		}
	}
}
