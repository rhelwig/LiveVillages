package com.ronhelwig.livevillages.menu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import com.ronhelwig.livevillages.block.entity.TradeBoardBlockEntity;
import com.ronhelwig.livevillages.network.TradeBoardRefreshPayload;
import com.ronhelwig.livevillages.sim.LiveVillagesSavedData;
import com.ronhelwig.livevillages.sim.RouteState;
import com.ronhelwig.livevillages.sim.SettlementState;
import com.ronhelwig.livevillages.sim.SettlementVillagers;

public final class TradeBoardTrading {
	public static final int MAX_TRADE_ROWS = 6;
	public static final int MAX_TRADE_OPTIONS = 5;
	public static final int DONATE_ONE_INDEX = 0;
	public static final int DONATE_BUNDLE_INDEX = 1;
	public static final int DONATE_ALL_INDEX = 2;

	private static final int PLAYER_TRADE_BUTTON_BASE = 1_000;
	private static final int VILLAGE_TRADE_BUTTON_BASE = 2_000;
	private static final int DONATE_BUTTON_BASE = 3_000;
	private static final int DONATE_OPTION_COUNT = 3;

	private TradeBoardTrading() {
	}

	public static int playerTradeButtonId(int playerGoodsIndex, int payoutIndex) {
		return PLAYER_TRADE_BUTTON_BASE + playerGoodsIndex * MAX_TRADE_OPTIONS + payoutIndex;
	}

	public static int villageTradeButtonId(int villageGoodsIndex, int paymentIndex) {
		return VILLAGE_TRADE_BUTTON_BASE + villageGoodsIndex * MAX_TRADE_OPTIONS + paymentIndex;
	}

	public static int donateButtonId(int playerGoodsIndex, int donationIndex) {
		return DONATE_BUTTON_BASE + playerGoodsIndex * DONATE_OPTION_COUNT + donationIndex;
	}

	public static List<PlayerGoodsOption> playerGoodsOptions(Inventory inventory, TradeBoardSettlementView view) {
		return playerGoodsOptions(playerGoodsCounts(inventory), view);
	}

	public static List<PlayerGoodsOption> playerGoodsOptions(List<TradeBoardPlayerGoodsView> playerGoods, TradeBoardSettlementView view) {
		return playerGoods.stream()
			.filter(entry -> entry.amount() > 0)
			.map(entry -> playerGoodsOption(entry.goodsKey(), entry.amount(), view))
			.sorted(
				Comparator.comparingInt((PlayerGoodsOption option) -> option.wanted() ? 0 : 1)
					.thenComparingInt(option -> option.canOfferBundle() ? 0 : 1)
					.thenComparing(PlayerGoodsOption::label)
			)
			.toList();
	}

	public static List<TradeBoardPlayerGoodsView> playerGoodsCounts(Inventory inventory) {
		Map<String, Integer> counts = new LinkedHashMap<>();

		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			String goodsKey = TradeBoardTradeRules.goodsKeyForStack(stack);

			if (goodsKey != null && TradeBoardTradeRules.isTradeableGoods(goodsKey)) {
				counts.merge(goodsKey, stack.getCount(), Integer::sum);
			}
		}

		return counts.entrySet().stream()
			.map(entry -> new TradeBoardPlayerGoodsView(entry.getKey(), humanizeKey(entry.getKey()), entry.getValue()))
			.toList();
	}

	public static List<TradeBoardGoodsView> villageGoodsOptions(TradeBoardSettlementView view) {
		return view.surpluses().stream()
			.filter(entry -> entry.tradeBundleSize() > 0)
			.filter(entry -> availableSettlementGoodsForTrade(entry) >= entry.tradeBundleSize())
			.limit(MAX_TRADE_ROWS)
			.toList();
	}

	public static List<GoodsTradeOption> payoutOptionsForPlayerGoods(PlayerGoodsOption playerGoods, TradeBoardSettlementView view) {
		if (!playerGoods.wanted() || !playerGoods.canOfferBundle()) {
			return List.of();
		}

		int offeredValuePoints = TradeBoardTradeRules.bundleValuePoints(playerGoods.goodsKey(), playerGoods.tradePricePercent());
		if (offeredValuePoints <= 0) {
			return List.of();
		}

		return payoutOptions(view, playerGoods.goodsKey(), offeredValuePoints);
	}

	public static List<GoodsTradeOption> paymentOptionsForVillageGoods(
		TradeBoardGoodsView villageGoods,
		Inventory inventory,
		TradeBoardSettlementView view
	) {
		return paymentOptionsForVillageGoods(villageGoods, playerGoodsCounts(inventory), view);
	}

	public static List<GoodsTradeOption> paymentOptionsForVillageGoods(
		TradeBoardGoodsView villageGoods,
		List<TradeBoardPlayerGoodsView> playerGoods,
		TradeBoardSettlementView view
	) {
		if (villageGoods.tradeBundleSize() <= 0 || availableSettlementGoodsForTrade(villageGoods) < villageGoods.tradeBundleSize()) {
			return List.of();
		}

		int requiredValuePoints = TradeBoardTradeRules.bundleValuePoints(villageGoods.goodsKey(), villageGoods.tradePricePercent());
		if (requiredValuePoints <= 0) {
			return List.of();
		}

		List<GoodsTradeOption> options = new ArrayList<>();

		for (TradeBoardGoodsView shortage : view.shortages()) {
			if (shortage.goodsKey().equals(villageGoods.goodsKey())) {
				continue;
			}

			int itemValuePoints = TradeBoardTradeRules.itemValuePoints(shortage.goodsKey(), shortage.tradePricePercent());
			if (itemValuePoints <= 0) {
				continue;
			}

			int requiredAmount = TradeBoardTradeRules.requiredItemsForValue(shortage.goodsKey(), shortage.tradePricePercent(), requiredValuePoints);
			int availableAmount = countPlayerGoods(playerGoods, shortage.goodsKey());
			if (requiredAmount <= 0 || availableAmount < requiredAmount) {
				continue;
			}

			options.add(new GoodsTradeOption(
				shortage.goodsKey(),
				shortage.label(),
				requiredAmount,
				requiredAmount * itemValuePoints,
				(requiredAmount * itemValuePoints) - requiredValuePoints
			));
		}

		options.sort(
			Comparator.comparingInt(GoodsTradeOption::overpayValuePoints)
				.thenComparingInt(GoodsTradeOption::amount)
				.thenComparing(GoodsTradeOption::label)
		);

		return options.stream().limit(MAX_TRADE_OPTIONS).toList();
	}

	public static boolean handleTradeButton(ServerPlayer player, BlockPos boardPos, int buttonId) {
		if (!(player.level() instanceof ServerLevel serverLevel)) {
			return false;
		}

		if (!(serverLevel.getBlockEntity(boardPos) instanceof TradeBoardBlockEntity tradeBoard)) {
			return false;
		}

		boolean isPlayerTrade = buttonId >= PLAYER_TRADE_BUTTON_BASE
			&& buttonId < PLAYER_TRADE_BUTTON_BASE + MAX_TRADE_ROWS * MAX_TRADE_OPTIONS;
		boolean isVillageTrade = buttonId >= VILLAGE_TRADE_BUTTON_BASE
			&& buttonId < VILLAGE_TRADE_BUTTON_BASE + MAX_TRADE_ROWS * MAX_TRADE_OPTIONS;
		boolean isDonation = buttonId >= DONATE_BUTTON_BASE
			&& buttonId < DONATE_BUTTON_BASE + MAX_TRADE_ROWS * DONATE_OPTION_COUNT;

		if (!isPlayerTrade && !isVillageTrade && !isDonation) {
			return false;
		}

		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(serverLevel.getServer());
		SettlementState settlement = tradeBoard.resolveSettlement(serverLevel);
		TradeBoardSettlementView view = createTradeView(serverLevel, savedData, settlement);
		TradeResult result;
		if (isPlayerTrade) {
			result = handlePlayerGoodsTrade(player, settlement, view, buttonId - PLAYER_TRADE_BUTTON_BASE, serverLevel.getServer().getTickCount());
		} else if (isVillageTrade) {
			result = handleVillageGoodsTrade(player, settlement, view, buttonId - VILLAGE_TRADE_BUTTON_BASE, serverLevel.getServer().getTickCount());
		} else {
			result = handleDonation(player, settlement, view, buttonId - DONATE_BUTTON_BASE, serverLevel.getServer().getTickCount());
		}

		if (!result.success()) {
			sendTradeResult(player, boardPos, view, result);
			return true;
		}

		savedData.putSettlement(result.updatedSettlement());
		TradeBoardSettlementView refreshedView = createTradeView(serverLevel, savedData, result.updatedSettlement());
		sendTradeResult(player, boardPos, refreshedView, result);
		return true;
	}

	private static TradeResult handleDonation(
		ServerPlayer player,
		SettlementState settlement,
		TradeBoardSettlementView view,
		int encodedButtonId,
		long currentTick
	) {
		int playerGoodsIndex = encodedButtonId / DONATE_OPTION_COUNT;
		int donationIndex = encodedButtonId % DONATE_OPTION_COUNT;
		List<PlayerGoodsOption> playerGoods = playerGoodsOptions(player.getInventory(), view);

		if (playerGoodsIndex < 0 || playerGoodsIndex >= playerGoods.size()) {
			return TradeResult.failure("That inventory good is no longer available.");
		}

		PlayerGoodsOption donatedGoods = playerGoods.get(playerGoodsIndex);
		int amount = donationAmount(donatedGoods, donationIndex);
		if (amount <= 0) {
			return TradeResult.failure("There is nothing to donate.");
		}

		if (!TradeBoardTradeRules.removePlayerGoods(player.getInventory(), donatedGoods.goodsKey(), amount)) {
			return TradeResult.failure("The donation failed while removing goods from your inventory.");
		}

		Map<String, Integer> stock = new LinkedHashMap<>(settlement.stock());
		stock.merge(donatedGoods.goodsKey(), amount, Integer::sum);
		cleanMap(stock);

		return TradeResult.success(
			updateSettlementStock(settlement, stock, currentTick),
			"Donated %d %s to %s.".formatted(
				amount,
				donatedGoods.label().toLowerCase(Locale.ROOT),
				settlement.name()
			)
		);
	}

	private static TradeResult handlePlayerGoodsTrade(
		ServerPlayer player,
		SettlementState settlement,
		TradeBoardSettlementView view,
		int encodedButtonId,
		long currentTick
	) {
		int playerGoodsIndex = encodedButtonId / MAX_TRADE_OPTIONS;
		int payoutIndex = encodedButtonId % MAX_TRADE_OPTIONS;
		List<PlayerGoodsOption> playerGoods = playerGoodsOptions(player.getInventory(), view);

		if (playerGoodsIndex < 0 || playerGoodsIndex >= playerGoods.size()) {
			return TradeResult.failure("That inventory good is no longer available.");
		}

		PlayerGoodsOption offeredGoods = playerGoods.get(playerGoodsIndex);
		if (!offeredGoods.wanted()) {
			return TradeResult.failure("%s is not asking for %s right now.".formatted(settlement.name(), offeredGoods.label().toLowerCase(Locale.ROOT)));
		}

		int amount = offeredGoods.tradeBundleSize();
		if (amount <= 0 || !offeredGoods.canOfferBundle()) {
			return TradeResult.failure("You need %d %s to offer that bundle.".formatted(amount, offeredGoods.label().toLowerCase(Locale.ROOT)));
		}

		List<GoodsTradeOption> payoutOptions = payoutOptionsForPlayerGoods(offeredGoods, view);
		if (payoutIndex < 0 || payoutIndex >= payoutOptions.size()) {
			return TradeResult.failure("%s is not offering that payout anymore.".formatted(settlement.name()));
		}

		GoodsTradeOption payout = payoutOptions.get(payoutIndex);
		Map<String, Integer> stock = new LinkedHashMap<>(settlement.stock());
		int availablePayout = Math.max(0, stock.getOrDefault(payout.goodsKey(), 0) - targetForGoods(view, payout.goodsKey()));
		if (availablePayout < payout.amount()) {
			return TradeResult.failure("%s needs to keep its remaining %s.".formatted(settlement.name(), payout.label().toLowerCase(Locale.ROOT)));
		}

		if (!TradeBoardTradeRules.removePlayerGoods(player.getInventory(), offeredGoods.goodsKey(), amount)) {
			return TradeResult.failure("The trade failed while removing goods from your inventory.");
		}

		stock.merge(offeredGoods.goodsKey(), amount, Integer::sum);
		stock.put(payout.goodsKey(), stock.getOrDefault(payout.goodsKey(), 0) - payout.amount());
		giveStack(player, TradeBoardTradeRules.createGoodsStack(payout.goodsKey(), payout.amount()));
		cleanMap(stock);

		return TradeResult.success(
			updateSettlementStock(settlement, stock, currentTick),
			"Traded %d %s to %s for %d %s.".formatted(
				amount,
				offeredGoods.label().toLowerCase(Locale.ROOT),
				settlement.name(),
				payout.amount(),
				payout.label().toLowerCase(Locale.ROOT)
			)
		);
	}

	private static TradeResult handleVillageGoodsTrade(
		ServerPlayer player,
		SettlementState settlement,
		TradeBoardSettlementView view,
		int encodedButtonId,
		long currentTick
	) {
		int villageGoodsIndex = encodedButtonId / MAX_TRADE_OPTIONS;
		int paymentIndex = encodedButtonId % MAX_TRADE_OPTIONS;
		List<TradeBoardGoodsView> villageGoods = villageGoodsOptions(view);

		if (villageGoodsIndex < 0 || villageGoodsIndex >= villageGoods.size()) {
			return TradeResult.failure("That village good is no longer available.");
		}

		TradeBoardGoodsView requestedGoods = villageGoods.get(villageGoodsIndex);
		int amount = requestedGoods.tradeBundleSize();
		Map<String, Integer> stock = new LinkedHashMap<>(settlement.stock());
		int availableGoods = Math.max(0, stock.getOrDefault(requestedGoods.goodsKey(), 0) - targetForGoods(view, requestedGoods.goodsKey()));

		if (amount <= 0 || availableGoods < amount) {
			return TradeResult.failure("%s needs to keep its remaining %s.".formatted(settlement.name(), requestedGoods.label().toLowerCase(Locale.ROOT)));
		}

		List<GoodsTradeOption> paymentOptions = paymentOptionsForVillageGoods(requestedGoods, player.getInventory(), view);
		if (paymentIndex < 0 || paymentIndex >= paymentOptions.size()) {
			return TradeResult.failure(describePaymentNeeds(view.shortages(), TradeBoardTradeRules.bundleValuePoints(requestedGoods.goodsKey(), requestedGoods.tradePricePercent())));
		}

		GoodsTradeOption payment = paymentOptions.get(paymentIndex);
		if (!TradeBoardTradeRules.removePlayerGoods(player.getInventory(), payment.goodsKey(), payment.amount())) {
			return TradeResult.failure("The trade failed while removing goods from your inventory.");
		}

		stock.put(requestedGoods.goodsKey(), stock.getOrDefault(requestedGoods.goodsKey(), 0) - amount);
		stock.merge(payment.goodsKey(), payment.amount(), Integer::sum);
		giveStack(player, TradeBoardTradeRules.createGoodsStack(requestedGoods.goodsKey(), amount));
		cleanMap(stock);

		return TradeResult.success(
			updateSettlementStock(settlement, stock, currentTick),
			"Traded %d %s to %s for %d %s.".formatted(
				payment.amount(),
				payment.label().toLowerCase(Locale.ROOT),
				settlement.name(),
				amount,
				requestedGoods.label().toLowerCase(Locale.ROOT)
			)
		);
	}

	private static TradeBoardSettlementView createTradeView(ServerLevel serverLevel, LiveVillagesSavedData savedData, SettlementState settlement) {
		List<RouteState> routes = savedData.getRoutesForSettlement(settlement.id());
		return TradeBoardLogic.createSettlementView(
			settlement,
			routes,
			settlementId -> savedData.getSettlement(settlementId).map(SettlementState::name).orElse("Unknown"),
			MAX_TRADE_ROWS * 2,
			3,
			3,
			SettlementVillagers.nearbyProfessionPopulation(serverLevel, settlement),
			TradeBoardLogic.constructionTradeDemand(savedData.getBuildSitesForSettlement(settlement.id()))
		);
	}

	private static PlayerGoodsOption playerGoodsOption(String goodsKey, int playerAmount, TradeBoardSettlementView view) {
		TradeBoardGoodsView shortage = goodsView(view.shortages(), goodsKey);
		int tradeBundleSize = TradeBoardTradeRules.bundleSize(goodsKey);

		if (shortage == null) {
			return new PlayerGoodsOption(
				goodsKey,
				humanizeKey(goodsKey),
				playerAmount,
				0,
				tradeBundleSize,
				100,
				false,
				playerAmount >= tradeBundleSize
			);
		}

		return new PlayerGoodsOption(
			goodsKey,
			shortage.label(),
			playerAmount,
			Math.max(0, shortage.target() - shortage.current()),
			shortage.tradeBundleSize(),
			shortage.tradePricePercent(),
			true,
			playerAmount >= shortage.tradeBundleSize()
		);
	}

	private static List<GoodsTradeOption> payoutOptions(TradeBoardSettlementView view, String offeredGoodsKey, int offeredValuePoints) {
		List<GoodsTradeOption> options = new ArrayList<>();

		for (TradeBoardGoodsView surplus : view.surpluses()) {
			if (surplus.goodsKey().equals(offeredGoodsKey)) {
				continue;
			}

			int availableAmount = availableSettlementGoodsForTrade(surplus);
			int itemValuePoints = TradeBoardTradeRules.itemValuePoints(surplus.goodsKey(), surplus.tradePricePercent());
			if (availableAmount <= 0 || itemValuePoints <= 0) {
				continue;
			}

			int payoutAmount = Math.min(availableAmount, offeredValuePoints / itemValuePoints);
			if (payoutAmount <= 0) {
				continue;
			}

			options.add(new GoodsTradeOption(
				surplus.goodsKey(),
				surplus.label(),
				payoutAmount,
				payoutAmount * itemValuePoints,
				offeredValuePoints - payoutAmount * itemValuePoints
			));
		}

		options.sort(
			Comparator.comparingInt((GoodsTradeOption option) -> -option.valuePoints())
				.thenComparingInt(option -> -option.amount())
				.thenComparing(GoodsTradeOption::label)
		);

		return options.stream().limit(MAX_TRADE_OPTIONS).toList();
	}

	private static int availableSettlementGoodsForTrade(TradeBoardGoodsView goods) {
		return Math.max(0, goods.current() - goods.target());
	}

	private static int countPlayerGoods(List<TradeBoardPlayerGoodsView> playerGoods, String goodsKey) {
		for (TradeBoardPlayerGoodsView entry : playerGoods) {
			if (entry.goodsKey().equals(goodsKey)) {
				return entry.amount();
			}
		}

		return 0;
	}

	private static int donationAmount(PlayerGoodsOption goods, int donationIndex) {
		if (goods.playerAmount() <= 0) {
			return 0;
		}

		return switch (donationIndex) {
			case DONATE_ONE_INDEX -> 1;
			case DONATE_BUNDLE_INDEX -> Math.min(goods.playerAmount(), Math.max(1, goods.tradeBundleSize()));
			case DONATE_ALL_INDEX -> goods.playerAmount();
			default -> 0;
		};
	}

	private static int targetForGoods(TradeBoardSettlementView view, String goodsKey) {
		TradeBoardGoodsView shortage = goodsView(view.shortages(), goodsKey);
		if (shortage != null) {
			return shortage.target();
		}

		TradeBoardGoodsView surplus = goodsView(view.surpluses(), goodsKey);
		if (surplus != null) {
			return surplus.target();
		}

		return 0;
	}

	private static TradeBoardGoodsView goodsView(List<TradeBoardGoodsView> entries, String goodsKey) {
		for (TradeBoardGoodsView entry : entries) {
			if (entry.goodsKey().equals(goodsKey)) {
				return entry;
			}
		}

		return null;
	}

	private static String describePaymentNeeds(List<TradeBoardGoodsView> shortages, int requiredValuePoints) {
		List<String> options = shortages.stream()
			.filter(shortage -> TradeBoardTradeRules.itemValuePoints(shortage.goodsKey(), shortage.tradePricePercent()) > 0)
			.limit(3)
			.map(shortage -> "%d %s".formatted(
				TradeBoardTradeRules.requiredItemsForValue(shortage.goodsKey(), shortage.tradePricePercent(), requiredValuePoints),
				shortage.label().toLowerCase(Locale.ROOT)
			))
			.collect(Collectors.toList());

		if (options.isEmpty()) {
			return "This village is not requesting any goods in exchange right now.";
		}

		return "You need goods this village currently wants, such as " + String.join(" or ", options) + ".";
	}

	private static SettlementState updateSettlementStock(SettlementState settlement, Map<String, Integer> stock, long currentTick) {
		return settlement.withSimulationState(
			settlement.population(),
			settlement.wealth(),
			stock,
			settlement.housingCapacity(),
			settlement.comfort(),
			settlement.security(),
			settlement.defenseLevel(),
			settlement.growthProgress(),
			settlement.projects(),
			currentTick
		);
	}

	private static void sendTradeResult(ServerPlayer player, BlockPos boardPos, TradeBoardSettlementView view, TradeResult result) {
		player.sendSystemMessage(Component.literal(result.message()));
		ServerPlayNetworking.send(player, new TradeBoardRefreshPayload(boardPos.immutable(), view, result.message(), result.success(), playerGoodsCounts(player.getInventory())));
	}

	private static void giveStack(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}

		if (!player.addItem(stack)) {
			player.drop(stack, false);
		}
	}

	private static void cleanMap(Map<String, Integer> values) {
		values.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() <= 0);
	}

	private static String humanizeKey(String key) {
		String[] parts = key.split("_");
		StringBuilder result = new StringBuilder();

		for (String part : parts) {
			if (part.isEmpty()) {
				continue;
			}

			if (!result.isEmpty()) {
				result.append(' ');
			}

			result.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
			result.append(part.substring(1));
		}

		return result.toString();
	}

	public record PlayerGoodsOption(
		String goodsKey,
		String label,
		int playerAmount,
		int wantedAmount,
		int tradeBundleSize,
		int tradePricePercent,
		boolean wanted,
		boolean canOfferBundle
	) {
	}

	public record GoodsTradeOption(String goodsKey, String label, int amount, int valuePoints, int overpayValuePoints) {
	}

	private record TradeResult(boolean success, SettlementState updatedSettlement, String message) {
		private static TradeResult success(SettlementState updatedSettlement, String message) {
			return new TradeResult(true, updatedSettlement, message);
		}

		private static TradeResult failure(String message) {
			return new TradeResult(false, null, message);
		}
	}
}
