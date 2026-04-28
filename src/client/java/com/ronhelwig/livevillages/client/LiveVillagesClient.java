package com.ronhelwig.livevillages.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.gui.screens.MenuScreens;

import com.ronhelwig.livevillages.client.hud.SettlementInventoryOverlay;
import com.ronhelwig.livevillages.client.render.BuildSiteWireframePreview;
import com.ronhelwig.livevillages.client.render.MilepostBlockEntityRenderer;
import com.ronhelwig.livevillages.client.render.TradeBoardBlockEntityRenderer;
import com.ronhelwig.livevillages.client.screen.CarpenterBenchScreen;
import com.ronhelwig.livevillages.client.screen.PortmasterMapScreen;
import com.ronhelwig.livevillages.client.screen.SurveyorMapScreen;
import com.ronhelwig.livevillages.client.screen.TradeBoardScreen;
import com.ronhelwig.livevillages.content.LiveVillagesBlockEntities;
import com.ronhelwig.livevillages.content.LiveVillagesMenus;

public class LiveVillagesClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MenuScreens.register(LiveVillagesMenus.CARPENTER_BENCH, CarpenterBenchScreen::new);
		MenuScreens.register(LiveVillagesMenus.TRADE_BOARD, TradeBoardScreen::new);
		BlockEntityRendererRegistry.register(LiveVillagesBlockEntities.MILEPOST, MilepostBlockEntityRenderer::new);
		BlockEntityRendererRegistry.register(LiveVillagesBlockEntities.TRADE_BOARD, TradeBoardBlockEntityRenderer::new);
		TradeBoardScreen.registerNetworking();
		SurveyorMapScreen.registerNetworking();
		PortmasterMapScreen.registerNetworking();
		SettlementInventoryOverlay.register();
		BuildSiteWireframePreview.register();
	}
}
