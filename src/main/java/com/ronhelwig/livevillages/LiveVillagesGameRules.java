package com.ronhelwig.livevillages;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

import com.ronhelwig.livevillages.sim.SettlementEconomyRules;
import com.ronhelwig.livevillages.sim.SettlementVillagerTextureScale;

public final class LiveVillagesGameRules {
	public static final GameRule<Boolean> SURVEYOR_MAP_FOG = GameRuleBuilder.forBoolean(false)
		.category(GameRuleCategory.MISC)
		.buildAndRegister(Identifier.fromNamespaceAndPath(LiveVillages.MOD_ID, "surveyor_map_fog"));
	public static final GameRule<Boolean> DAILY_SETTLEMENT_REPORTS = GameRuleBuilder.forBoolean(false)
		.category(GameRuleCategory.MISC)
		.buildAndRegister(Identifier.fromNamespaceAndPath(LiveVillages.MOD_ID, "daily_settlement_reports"));
	public static final GameRule<Double> WORKER_PRODUCTIVITY = GameRuleBuilder
		.forDouble(SettlementEconomyRules.DEFAULT_WORKER_PRODUCTIVITY_MULTIPLIER)
		.range(SettlementEconomyRules.MIN_WORKER_PRODUCTIVITY_MULTIPLIER, SettlementEconomyRules.MAX_WORKER_PRODUCTIVITY_MULTIPLIER)
		.category(GameRuleCategory.MISC)
		.buildAndRegister(Identifier.fromNamespaceAndPath(LiveVillages.MOD_ID, "worker_productivity"));
	public static final GameRule<SettlementVillagerTextureScale.Option> VILLAGER_TEXTURE_SCALE = GameRuleBuilder
		.forEnum(SettlementVillagerTextureScale.NEW_WORLD_DEFAULT)
		.argumentType(SettlementVillagerTextureScale.optionArgumentType())
		.category(GameRuleCategory.MISC)
		.buildAndRegister(Identifier.fromNamespaceAndPath(LiveVillages.MOD_ID, "villager_texture_scale"));

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

	public static double workerProductivityMultiplier(MinecraftServer server) {
		if (server == null) {
			return SettlementEconomyRules.DEFAULT_WORKER_PRODUCTIVITY_MULTIPLIER;
		}

		return workerProductivityMultiplier(server.getLevel(Level.OVERWORLD));
	}

	public static double workerProductivityMultiplier(ServerLevel level) {
		if (level == null) {
			return SettlementEconomyRules.DEFAULT_WORKER_PRODUCTIVITY_MULTIPLIER;
		}

		return SettlementEconomyRules.sanitizeWorkerProductivityMultiplier(level.getGameRules().get(WORKER_PRODUCTIVITY));
	}

	public static int villagerTextureScale(MinecraftServer server) {
		if (server == null) {
			return SettlementVillagerTextureScale.DEFAULT_SCALE;
		}

		return villagerTextureScale(server.getLevel(Level.OVERWORLD));
	}

	public static int villagerTextureScale(ServerLevel level) {
		if (level == null) {
			return SettlementVillagerTextureScale.DEFAULT_SCALE;
		}

		return level.getGameRules().get(VILLAGER_TEXTURE_SCALE).pixels();
	}
}
