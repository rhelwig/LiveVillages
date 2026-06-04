package com.ronhelwig.livevillages;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

public final class LiveVillagesGameRules {
	public static final GameRule<Boolean> SURVEYOR_MAP_FOG = GameRuleBuilder.forBoolean(false)
		.category(GameRuleCategory.MISC)
		.buildAndRegister(Identifier.fromNamespaceAndPath(LiveVillages.MOD_ID, "surveyor_map_fog"));
	public static final GameRule<Boolean> DAILY_SETTLEMENT_REPORTS = GameRuleBuilder.forBoolean(false)
		.category(GameRuleCategory.MISC)
		.buildAndRegister(Identifier.fromNamespaceAndPath(LiveVillages.MOD_ID, "daily_settlement_reports"));

	private LiveVillagesGameRules() {
	}

	public static void register() {
		// Static initialization above performs the actual registration.
	}

	public static boolean surveyorMapFogEnabled(ServerLevel level) {
		return level.getGameRules().get(SURVEYOR_MAP_FOG);
	}

	public static boolean dailySettlementReportsEnabled(ServerLevel level) {
		return LiveVillagesConfig.dailySettlementReportsEnabled() || level.getGameRules().get(DAILY_SETTLEMENT_REPORTS);
	}
}
