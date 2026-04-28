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

	private LiveVillagesGameRules() {
	}

	public static void register() {
		// Static initialization above performs the actual registration.
	}

	public static boolean surveyorMapFogEnabled(ServerLevel level) {
		return level.getGameRules().get(SURVEYOR_MAP_FOG);
	}
}
