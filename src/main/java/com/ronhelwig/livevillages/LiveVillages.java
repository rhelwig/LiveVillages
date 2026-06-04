package com.ronhelwig.livevillages;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ronhelwig.livevillages.command.LiveVillagesDebugCommands;
import com.ronhelwig.livevillages.content.LiveVillagesBlockEntities;
import com.ronhelwig.livevillages.content.LiveVillagesBlocks;
import com.ronhelwig.livevillages.content.LiveVillagesItems;
import com.ronhelwig.livevillages.content.LiveVillagesMenus;
import com.ronhelwig.livevillages.content.LiveVillagesVillagerProfessions;
import com.ronhelwig.livevillages.network.LiveVillagesNetworking;
import com.ronhelwig.livevillages.sim.BuildSiteAssistedPlacement;
import com.ronhelwig.livevillages.sim.FletchingTableInteraction;
import com.ronhelwig.livevillages.sim.LiveVillagesScheduler;
import com.ronhelwig.livevillages.sim.ProfessionWorkstationTrades;
import com.ronhelwig.livevillages.sim.SettlementDefenseWork;
import com.ronhelwig.livevillages.sim.SettlementProfessionReports;
import com.ronhelwig.livevillages.sim.VillageAutodetector;

public class LiveVillages implements ModInitializer {
	public static final String MOD_ID = "live-villages";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		LiveVillagesConfig.load();
		LiveVillagesGameRules.register();
		LiveVillagesItems.register();
		LiveVillagesBlocks.register();
		LiveVillagesVillagerProfessions.register();
		LiveVillagesBlockEntities.register();
		LiveVillagesMenus.register();
		LiveVillagesNetworking.register();
		BuildSiteAssistedPlacement.register();
		ProfessionWorkstationTrades.register();
		FletchingTableInteraction.register();
		SettlementDefenseWork.register();
		SettlementProfessionReports.register();
		LiveVillagesDebugCommands.register();
		LiveVillagesScheduler.register();
		VillageAutodetector.register();
		LOGGER.info("Initialized Live Villages!");
	}
}
