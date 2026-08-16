package com.ronhelwig.livevillages.menu;

import static org.junit.jupiter.api.Assertions.*;

import com.ronhelwig.livevillages.MinecraftBootstrapTestSupport;
import com.ronhelwig.livevillages.sim.SettlementBuildBlockState;
import com.ronhelwig.livevillages.sim.SettlementBuildSite;
import com.ronhelwig.livevillages.sim.SettlementBuildSiteType;
import com.ronhelwig.livevillages.sim.SettlementKind;
import com.ronhelwig.livevillages.sim.SettlementState;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class TradeBoardLogicTest extends MinecraftBootstrapTestSupport {
	@Test
	void constructionDemandKeepsNeededStairsAndSlabsOffTheSurplusList() {
		SettlementState settlement = new SettlementState(
			"ford",
			"Fordham",
			Level.OVERWORLD,
			BlockPos.ZERO,
			SettlementKind.CUSTOM,
			1,
			Map.of("trademaster", 2),
			Map.of(),
			Map.of("stairs", 3, "slab", 2, "planks", 40),
			2,
			1.0D,
			0.1D,
			0,
			0.0D,
			List.of(),
			0L,
			0L
		);
		List<SettlementBuildSite> buildSites = List.of(new SettlementBuildSite(
			"trade-post",
			settlement.id(),
			SettlementBuildSiteType.TRADING_POST,
			BlockPos.ZERO,
			BlockPos.ZERO,
			BlockPos.ZERO,
			Direction.NORTH,
			"oak",
			"cobblestone",
			Map.of(),
			List.of(
				SettlementBuildBlockState.pending("0,0,3", 'S', "stairs"),
				SettlementBuildBlockState.pending("1,0,3", 'S', "stairs"),
				SettlementBuildBlockState.pending("2,0,3", 'S', "stairs"),
				SettlementBuildBlockState.pending("0,0,4", 'B', "slab"),
				SettlementBuildBlockState.pending("1,0,4", 'B', "slab")
			),
			false,
			0L,
			0L
		));
		Map<String, Integer> demand = TradeBoardLogic.constructionTradeDemand(buildSites);

		assertEquals(3, demand.getOrDefault("stairs", 0));
		assertEquals(2, demand.getOrDefault("slab", 0));

		TradeBoardSettlementView view = TradeBoardLogic.createSettlementView(
			settlement,
			List.of(),
			id -> "",
			5,
			3,
			3,
			settlement.population(),
			demand,
			buildSites
		);

		assertTrue(view.surpluses().stream().noneMatch(goods -> goods.goodsKey().equals("stairs")));
		assertTrue(view.surpluses().stream().noneMatch(goods -> goods.goodsKey().equals("slab")));
	}

	@Test
	void extraConstructionMaterialsRemainTradableAfterTheBuildReserve() {
		SettlementState settlement = new SettlementState(
			"ford",
			"Fordham",
			Level.OVERWORLD,
			BlockPos.ZERO,
			SettlementKind.CUSTOM,
			1,
			Map.of("trademaster", 2),
			Map.of(),
			Map.of("stairs", 20),
			2,
			1.0D,
			0.1D,
			0,
			0.0D,
			List.of(),
			0L,
			0L
		);
		List<SettlementBuildSite> buildSites = List.of(new SettlementBuildSite(
			"trade-post",
			settlement.id(),
			SettlementBuildSiteType.TRADING_POST,
			BlockPos.ZERO,
			BlockPos.ZERO,
			BlockPos.ZERO,
			Direction.NORTH,
			"oak",
			"cobblestone",
			Map.of(),
			List.of(SettlementBuildBlockState.pending("0,0,3", 'S', "stairs")),
			false,
			0L,
			0L
		));

		TradeBoardSettlementView view = TradeBoardLogic.createSettlementView(
			settlement,
			List.of(),
			id -> "",
			5,
			3,
			3,
			settlement.population(),
			TradeBoardLogic.constructionTradeDemand(buildSites),
			buildSites
		);

		TradeBoardGoodsView stairs = view.surpluses().stream()
			.filter(goods -> goods.goodsKey().equals("stairs"))
			.findFirst()
			.orElseThrow();
		assertEquals(1, stairs.target());
		assertEquals(20, stairs.current());
	}
}
