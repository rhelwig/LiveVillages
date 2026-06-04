package com.ronhelwig.livevillages.sim;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import com.ronhelwig.livevillages.content.LiveVillagesBlocks;
import com.ronhelwig.livevillages.menu.FletchingTableRecipes;
import com.ronhelwig.livevillages.menu.TradeBoardTradeRules;
import com.ronhelwig.livevillages.network.LiveVillagesNetworking;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class ProfessionWorkstationTrades {
	private static final String[] BUTCHER_OUTPUT_PRIORITY = {
		"beef",
		"pork",
		"leather"
	};
	private static final LeatherArmorTrade[] LEATHERWORKER_OUTPUT_PRIORITY = {
		new LeatherArmorTrade("leather_chestplate", 8),
		new LeatherArmorTrade("leather_leggings", 7),
		new LeatherArmorTrade("leather_helmet", 5),
		new LeatherArmorTrade("leather_boots", 4)
	};
	private static final int MASON_ROUGH_STONE_PAYMENT = 8;

	private ProfessionWorkstationTrades() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register(ProfessionWorkstationTrades::onUseBlock);
	}

	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hitResult) {
		if (player.isSpectator() || player.isSecondaryUseActive()) {
			return InteractionResult.PASS;
		}

		BlockState state = level.getBlockState(hitResult.getBlockPos());
		ItemStack heldStack = player.getItemInHand(hand);
		if (state.is(LiveVillagesBlocks.CARPENTER_BENCH) && (heldStack.is(ItemTags.LOGS) || heldStack.is(ItemTags.PLANKS))) {
			return tradeWithCarpenter(player, level, heldStack, hitResult);
		}

		if (state.is(Blocks.SMOKER) && heldStack.is(Items.WHEAT)) {
			return tradeWithButcher(player, level, heldStack, hitResult);
		}

		if (state.is(Blocks.FLETCHING_TABLE) && isFletcherArrowheadPayment(heldStack)) {
			return tradeWithFletcher(player, level, heldStack, hitResult);
		}

		if (state.is(Blocks.STONECUTTER) && isRoughStonePayment(heldStack)) {
			return tradeWithMason(player, level, heldStack, hitResult);
		}

		if (isLeatherworkerCauldron(state) && heldStack.is(Items.LEATHER)) {
			return tradeWithLeatherworker(player, level, heldStack, hitResult);
		}

		if (state.is(Blocks.BREWING_STAND) && heldStack.is(Items.GLASS_BOTTLE)) {
			return tradeWithCleric(player, level, heldStack, hitResult);
		}

		if (state.is(Blocks.LECTERN) && (heldStack.is(Items.PAPER) || heldStack.is(Items.BOOK))) {
			return tradeWithLibrarian(player, level, heldStack, hitResult);
		}

		if (state.is(Blocks.LOOM) && (heldStack.is(Items.WHEAT) || heldStack.is(ItemTags.WOOL))) {
			return tradeWithShepherd(player, level, heldStack, hitResult);
		}

		if (state.is(Blocks.BLAST_FURNACE) && heldStack.is(Items.IRON_INGOT)) {
			return tradeWithArmorer(player, level, heldStack, hitResult);
		}

		if (state.is(Blocks.SMITHING_TABLE) && heldStack.is(Items.IRON_INGOT)) {
			return tradeWithToolsmith(player, level, heldStack, hitResult);
		}

		if (state.is(Blocks.GRINDSTONE) && heldStack.is(Items.IRON_INGOT)) {
			return tradeWithWeaponsmith(player, level, heldStack, hitResult);
		}

		return InteractionResult.PASS;
	}

	private static InteractionResult tradeWithButcher(Player player, Level level, ItemStack paymentStack, BlockHitResult hitResult) {
		if (level.isClientSide()) {
			return InteractionResult.PASS;
		}

		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}

		if (LiveVillagesNetworking.isBuildSitePreviewActive(serverPlayer) && !paymentStack.isEmpty()) {
			return InteractionResult.PASS;
		}

		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(serverLevel.getServer());
		SettlementState settlement = SettlementConstruction.findWorkstationSettlement(serverLevel, hitResult.getBlockPos()).orElse(null);
		if (settlement == null) {
			return InteractionResult.PASS;
		}

		Map<String, Integer> stock = new LinkedHashMap<>(settlement.stock());
		String outputGoods = firstAvailable(stock, BUTCHER_OUTPUT_PRIORITY);
		if (outputGoods == null) {
			serverPlayer.sendSystemMessage(Component.literal(settlement.name() + "'s Butcher has no meat or leather ready to trade."));
			return InteractionResult.SUCCESS_SERVER;
		}

		if (!SettlementGoods.consumeGoods(stock, outputGoods, 1)) {
			return InteractionResult.SUCCESS_SERVER;
		}

		if (!serverPlayer.getAbilities().instabuild) {
			paymentStack.shrink(1);
			SettlementGoods.addGoods(stock, "wheat", 1);
		}

		savedData.putSettlementAndRefreshBuildSiteMaterialStatus(
			settlement.withStock(stock),
			serverLevel.getServer().getTickCount()
		);
		giveOrDrop(serverPlayer, TradeBoardTradeRules.createGoodsStack(outputGoods, 1));
		serverPlayer.sendSystemMessage(Component.literal(
			"Traded 1 wheat to " + settlement.name() + "'s Butcher for 1 " + displayGoods(outputGoods) + "."
		));
		return InteractionResult.SUCCESS_SERVER;
	}

	private static InteractionResult tradeWithFletcher(Player player, Level level, ItemStack paymentStack, BlockHitResult hitResult) {
		if (paymentStack.is(Items.FLINT)) {
			return tradeWithRecipeBundle(
				player,
				level,
				paymentStack,
				hitResult,
				"Fletching Table",
				"Fletcher",
				"arrow",
				8,
				List.of(
					exactPayment("stick", 1, "stick", Items.STICK),
					exactPayment("feather", 1, "feather", Items.FEATHER),
					exactPayment("flint", 1, "flint", Items.FLINT)
				)
			);
		}

		if (FletchingTableRecipes.isCopperHeadMaterial(paymentStack)) {
			return tradeWithRecipeBundle(
				player,
				level,
				paymentStack,
				hitResult,
				"Fletching Table",
				"Fletcher",
				"copperhead_arrow",
				8,
				List.of(
					exactPayment("stick", 1, "stick", Items.STICK),
					exactPayment("feather", 1, "feather", Items.FEATHER),
					matchingPayment("copper_nugget", 1, "copper nugget", FletchingTableRecipes::isCopperHeadMaterial)
				)
			);
		}

		if (paymentStack.is(Items.IRON_NUGGET)) {
			return tradeWithRecipeBundle(
				player,
				level,
				paymentStack,
				hitResult,
				"Fletching Table",
				"Fletcher",
				"ironhead_arrow",
				8,
				List.of(
					exactPayment("stick", 1, "stick", Items.STICK),
					exactPayment("feather", 1, "feather", Items.FEATHER),
					exactPayment("iron_nugget", 1, "iron nugget", Items.IRON_NUGGET)
				)
			);
		}

		return tradeWithRecipeBundle(
			player,
			level,
			paymentStack,
			hitResult,
			"Fletching Table",
			"Fletcher",
			"diamondhead_arrow",
			4,
			List.of(
				exactPayment("stick", 1, "stick", Items.STICK),
				exactPayment("feather", 1, "feather", Items.FEATHER),
				exactPayment("diamond", 1, "diamond", Items.DIAMOND)
			)
		);
	}

	private static InteractionResult tradeWithMason(Player player, Level level, ItemStack paymentStack, BlockHitResult hitResult) {
		if (level.isClientSide()) {
			return InteractionResult.PASS;
		}

		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}

		if (LiveVillagesNetworking.isBuildSitePreviewActive(serverPlayer) && !paymentStack.isEmpty()) {
			return InteractionResult.PASS;
		}

		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(serverLevel.getServer());
		SettlementState settlement = SettlementConstruction.findWorkstationSettlement(serverLevel, hitResult.getBlockPos()).orElse(null);
		if (settlement == null) {
			return InteractionResult.PASS;
		}

		if (!serverPlayer.getAbilities().instabuild && paymentStack.getCount() < MASON_ROUGH_STONE_PAYMENT) {
			serverPlayer.sendSystemMessage(Component.literal(
				"Mason trades need " + MASON_ROUGH_STONE_PAYMENT + " rough stone-family blocks. Sneak-use for normal Stonecutter work."
			));
			return InteractionResult.SUCCESS_SERVER;
		}

		Map<String, Integer> stock = new LinkedHashMap<>(settlement.stock());
		if (!SettlementGoods.consumeGoods(stock, "milepost", 1)) {
			serverPlayer.sendSystemMessage(Component.literal(settlement.name() + "'s Mason has no Mileposts ready to trade."));
			return InteractionResult.SUCCESS_SERVER;
		}

		if (!serverPlayer.getAbilities().instabuild) {
			paymentStack.shrink(MASON_ROUGH_STONE_PAYMENT);
			SettlementGoods.addGoods(stock, "cobblestone", MASON_ROUGH_STONE_PAYMENT);
		}

		savedData.putSettlementAndRefreshBuildSiteMaterialStatus(
			settlement.withStock(stock),
			serverLevel.getServer().getTickCount()
		);
		giveOrDrop(serverPlayer, TradeBoardTradeRules.createGoodsStack("milepost", 1));
		serverPlayer.sendSystemMessage(Component.literal(
			"Traded " + MASON_ROUGH_STONE_PAYMENT + " rough stone-family blocks to " + settlement.name() + "'s Mason for 1 Milepost."
		));
		return InteractionResult.SUCCESS_SERVER;
	}

	private static InteractionResult tradeWithLeatherworker(Player player, Level level, ItemStack paymentStack, BlockHitResult hitResult) {
		if (level.isClientSide()) {
			return InteractionResult.PASS;
		}

		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}

		if (LiveVillagesNetworking.isBuildSitePreviewActive(serverPlayer) && !paymentStack.isEmpty()) {
			return InteractionResult.PASS;
		}

		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(serverLevel.getServer());
		SettlementState settlement = SettlementConstruction.findWorkstationSettlement(serverLevel, hitResult.getBlockPos()).orElse(null);
		if (settlement == null) {
			return InteractionResult.PASS;
		}

		Map<String, Integer> stock = new LinkedHashMap<>(settlement.stock());
		LeatherArmorTrade trade = firstAvailableLeatherArmor(
			stock,
			paymentStack.getCount(),
			serverPlayer.getAbilities().instabuild
		);

		if (trade == null) {
			LeatherArmorTrade firstStocked = firstStockedLeatherArmor(stock);
			if (firstStocked == null) {
				serverPlayer.sendSystemMessage(Component.literal(settlement.name() + "'s Leatherworker has no leather armor ready to trade."));
			} else {
				serverPlayer.sendSystemMessage(Component.literal(
					"This leather armor trade needs " + firstStocked.leatherCost() + " leather."
				));
			}

			return InteractionResult.SUCCESS_SERVER;
		}

		if (!SettlementGoods.consumeGoods(stock, trade.goodsKey(), 1)) {
			return InteractionResult.SUCCESS_SERVER;
		}

		if (!serverPlayer.getAbilities().instabuild) {
			paymentStack.shrink(trade.leatherCost());
			SettlementGoods.addGoods(stock, "leather", trade.leatherCost());
		}

		savedData.putSettlementAndRefreshBuildSiteMaterialStatus(
			settlement.withStock(stock),
			serverLevel.getServer().getTickCount()
		);
		giveOrDrop(serverPlayer, TradeBoardTradeRules.createGoodsStack(trade.goodsKey(), 1));
		serverPlayer.sendSystemMessage(Component.literal(
			"Traded " + trade.leatherCost() + " leather to " + settlement.name() + "'s Leatherworker for 1 "
				+ displayGoods(trade.goodsKey()) + "."
		));
		return InteractionResult.SUCCESS_SERVER;
	}

	private static InteractionResult tradeWithCleric(Player player, Level level, ItemStack paymentStack, BlockHitResult hitResult) {
		return tradeWithRecipeBundle(
			player,
			level,
			paymentStack,
			hitResult,
			"Brewing Stand",
			"Cleric",
			"healing_potion",
			1,
			List.of(
				exactPayment("glass_bottle", 1, "glass bottle", Items.GLASS_BOTTLE),
				exactPayment("nether_wart", 1, "nether wart", Items.NETHER_WART),
				exactPayment("glistering_melon_slice", 1, "glistering melon slice", Items.GLISTERING_MELON_SLICE)
			)
		);
	}

	private static InteractionResult tradeWithLibrarian(Player player, Level level, ItemStack paymentStack, BlockHitResult hitResult) {
		if (paymentStack.is(Items.BOOK)) {
			return tradeWithRecipeBundle(
				player,
				level,
				paymentStack,
				hitResult,
				"Lectern",
				"Librarian",
				"bookshelf",
				1,
				List.of(
					exactPayment("book", 3, "books", Items.BOOK),
					matchingPayment("planks", 6, "planks", stack -> stack.is(ItemTags.PLANKS))
				)
			);
		}

		return tradeWithRecipeBundle(
			player,
			level,
			paymentStack,
			hitResult,
			"Lectern",
			"Librarian",
			"book",
			1,
			List.of(
				exactPayment("paper", 3, "paper", Items.PAPER),
				exactPayment("leather", 1, "leather", Items.LEATHER)
			)
		);
	}

	private static InteractionResult tradeWithShepherd(Player player, Level level, ItemStack paymentStack, BlockHitResult hitResult) {
		if (paymentStack.is(Items.WHEAT)) {
			return tradeWithRecipeBundle(
				player,
				level,
				paymentStack,
				hitResult,
				"Loom",
				"Shepherd",
				"wool",
				1,
				List.of(exactPayment("wheat", 1, "wheat", Items.WHEAT))
			);
		}

		return tradeWithRecipeBundle(
			player,
			level,
			paymentStack,
			hitResult,
			"Loom",
			"Shepherd",
			"bed",
			1,
			List.of(
				matchingPayment("wool", 3, "wool", stack -> stack.is(ItemTags.WOOL)),
				matchingPayment("planks", 3, "planks", stack -> stack.is(ItemTags.PLANKS))
			)
		);
	}

	private static InteractionResult tradeWithArmorer(Player player, Level level, ItemStack paymentStack, BlockHitResult hitResult) {
		return tradeWithFirstAvailableRecipe(
			player,
			level,
			paymentStack,
			hitResult,
			"Blast Furnace",
			"Armorer",
			List.of(
				recipePlan("iron_chestplate", exactPayment("iron_ingot", 8, "iron ingots", Items.IRON_INGOT)),
				recipePlan("iron_leggings", exactPayment("iron_ingot", 7, "iron ingots", Items.IRON_INGOT)),
				recipePlan("iron_helmet", exactPayment("iron_ingot", 5, "iron ingots", Items.IRON_INGOT)),
				recipePlan("iron_boots", exactPayment("iron_ingot", 4, "iron ingots", Items.IRON_INGOT)),
				recipePlan(
					"shield",
					exactPayment("iron_ingot", 1, "iron ingot", Items.IRON_INGOT),
					matchingPayment("planks", 6, "planks", stack -> stack.is(ItemTags.PLANKS))
				)
			)
		);
	}

	private static InteractionResult tradeWithToolsmith(Player player, Level level, ItemStack paymentStack, BlockHitResult hitResult) {
		return tradeWithRecipeBundle(
			player,
			level,
			paymentStack,
			hitResult,
			"Smithing Table",
			"Toolsmith",
			"iron_pickaxe",
			1,
			List.of(
				exactPayment("iron_ingot", 3, "iron ingots", Items.IRON_INGOT),
				exactPayment("stick", 2, "sticks", Items.STICK)
			)
		);
	}

	private static InteractionResult tradeWithWeaponsmith(Player player, Level level, ItemStack paymentStack, BlockHitResult hitResult) {
		return tradeWithRecipeBundle(
			player,
			level,
			paymentStack,
			hitResult,
			"Grindstone",
			"Weaponsmith",
			"iron_sword",
			1,
			List.of(
				exactPayment("iron_ingot", 2, "iron ingots", Items.IRON_INGOT),
				exactPayment("stick", 1, "stick", Items.STICK)
			)
		);
	}

	private static InteractionResult tradeWithCarpenter(Player player, Level level, ItemStack paymentStack, BlockHitResult hitResult) {
		List<CarpenterTradePlan> plans = paymentStack.is(ItemTags.LOGS)
			? List.of(
				carpenterPlan("planks", 4, matchingPayment("logs", 1, "log", stack -> stack.is(ItemTags.LOGS))),
				carpenterPlan("slab", 8, matchingPayment("logs", 1, "log", stack -> stack.is(ItemTags.LOGS))),
				carpenterPlan("stairs", 4, matchingPayment("logs", 2, "logs", stack -> stack.is(ItemTags.LOGS))),
				carpenterPlan("stick", 8, matchingPayment("logs", 1, "log", stack -> stack.is(ItemTags.LOGS))),
				carpenterPlan("chest", 1, matchingPayment("logs", 1, "log", stack -> stack.is(ItemTags.LOGS)))
			)
			: List.of(
				carpenterPlan("slab", 2, matchingPayment("planks", 1, "plank", stack -> stack.is(ItemTags.PLANKS))),
				carpenterPlan("stairs", 1, matchingPayment("planks", 1, "plank", stack -> stack.is(ItemTags.PLANKS))),
				carpenterPlan("stick", 2, matchingPayment("planks", 1, "plank", stack -> stack.is(ItemTags.PLANKS))),
				carpenterPlan("trapdoor", 2, matchingPayment("planks", 6, "planks", stack -> stack.is(ItemTags.PLANKS))),
				carpenterPlan("chest", 1, matchingPayment("planks", 8, "planks", stack -> stack.is(ItemTags.PLANKS)))
			);
		return tradeWithFirstAvailableCarpenterPlan(player, level, paymentStack, hitResult, plans);
	}

	private static InteractionResult tradeWithFirstAvailableCarpenterPlan(
		Player player,
		Level level,
		ItemStack paymentStack,
		BlockHitResult hitResult,
		List<CarpenterTradePlan> plans
	) {
		if (level.isClientSide()) {
			return InteractionResult.PASS;
		}

		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}

		if (LiveVillagesNetworking.isBuildSitePreviewActive(serverPlayer) && !paymentStack.isEmpty()) {
			return InteractionResult.PASS;
		}

		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(serverLevel.getServer());
		SettlementState settlement = SettlementConstruction.findWorkstationSettlement(serverLevel, hitResult.getBlockPos()).orElse(null);
		if (settlement == null) {
			return InteractionResult.PASS;
		}

		Map<String, Integer> stock = new LinkedHashMap<>(settlement.stock());
		CarpenterTradePlan firstStocked = null;
		CarpenterTradePlan selected = null;

		for (CarpenterTradePlan plan : plans) {
			if (!hasStockedOutput(stock, plan.outputGoodsKey(), plan.outputAmount())) {
				continue;
			}

			if (firstStocked == null) {
				firstStocked = plan;
			}

			if (serverPlayer.getAbilities().instabuild || hasPayment(serverPlayer, plan.payments())) {
				selected = plan;
				break;
			}
		}

		if (selected == null) {
			if (firstStocked == null) {
				serverPlayer.sendSystemMessage(Component.literal(settlement.name() + "'s Carpenter has no stocked woodwork ready to trade."));
			} else {
				serverPlayer.sendSystemMessage(Component.literal(
					"This Carpenter trade needs " + paymentsDescription(firstStocked.payments()) + "."
				));
			}

			return InteractionResult.SUCCESS_SERVER;
		}

		if (!SettlementGoods.consumeGoods(stock, selected.outputGoodsKey(), selected.outputAmount())) {
			return InteractionResult.SUCCESS_SERVER;
		}

		if (!serverPlayer.getAbilities().instabuild) {
			for (TradePayment payment : selected.payments()) {
				removeMatchingItems(serverPlayer, payment.matcher(), payment.amount());
				SettlementGoods.addGoods(stock, payment.goodsKey(), payment.amount());
			}
		}

		savedData.putSettlementAndRefreshBuildSiteMaterialStatus(
			settlement.withStock(stock),
			serverLevel.getServer().getTickCount()
		);
		giveOrDrop(serverPlayer, TradeBoardTradeRules.createGoodsStack(selected.outputGoodsKey(), selected.outputAmount()));
		serverPlayer.sendSystemMessage(Component.literal(
			"Traded " + paymentsDescription(selected.payments()) + " to " + settlement.name() + "'s Carpenter for "
				+ selected.outputAmount() + " " + displayGoods(selected.outputGoodsKey()) + "."
		));
		return InteractionResult.SUCCESS_SERVER;
	}

	private static InteractionResult tradeWithRecipeBundle(
		Player player,
		Level level,
		ItemStack paymentStack,
		BlockHitResult hitResult,
		String blockName,
		String professionName,
		String outputGoodsKey,
		int outputAmount,
		List<TradePayment> payments
	) {
		if (level.isClientSide()) {
			return InteractionResult.PASS;
		}

		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}

		if (LiveVillagesNetworking.isBuildSitePreviewActive(serverPlayer) && !paymentStack.isEmpty()) {
			return InteractionResult.PASS;
		}

		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(serverLevel.getServer());
		SettlementState settlement = SettlementConstruction.findWorkstationSettlement(serverLevel, hitResult.getBlockPos()).orElse(null);
		if (settlement == null) {
			return InteractionResult.PASS;
		}

		Map<String, Integer> stock = new LinkedHashMap<>(settlement.stock());
		if (!hasStockedOutput(stock, outputGoodsKey, outputAmount)) {
			serverPlayer.sendSystemMessage(Component.literal(
				settlement.name() + "'s " + professionName + " has no " + displayGoods(outputGoodsKey) + " ready to trade."
			));
			return InteractionResult.SUCCESS_SERVER;
		}

		if (!serverPlayer.getAbilities().instabuild && !hasPayment(serverPlayer, payments)) {
			serverPlayer.sendSystemMessage(Component.literal(
				"This " + professionName + " trade needs " + paymentsDescription(payments) + "."
			));
			return InteractionResult.SUCCESS_SERVER;
		}

		if (!SettlementGoods.consumeGoods(stock, outputGoodsKey, outputAmount)) {
			return InteractionResult.SUCCESS_SERVER;
		}

		if (!serverPlayer.getAbilities().instabuild) {
			for (TradePayment payment : payments) {
				removeMatchingItems(serverPlayer, payment.matcher(), payment.amount());
				SettlementGoods.addGoods(stock, payment.goodsKey(), payment.amount());
			}
		}

		savedData.putSettlementAndRefreshBuildSiteMaterialStatus(
			settlement.withStock(stock),
			serverLevel.getServer().getTickCount()
		);
		giveOrDrop(serverPlayer, TradeBoardTradeRules.createGoodsStack(outputGoodsKey, outputAmount));
		serverPlayer.sendSystemMessage(Component.literal(
			"Traded " + paymentsDescription(payments) + " to " + settlement.name() + "'s " + professionName + " for "
				+ outputAmount + " " + displayGoods(outputGoodsKey) + "."
		));
		return InteractionResult.SUCCESS_SERVER;
	}

	private static InteractionResult tradeWithFirstAvailableRecipe(
		Player player,
		Level level,
		ItemStack paymentStack,
		BlockHitResult hitResult,
		String blockName,
		String professionName,
		List<RecipeTradePlan> plans
	) {
		if (level.isClientSide()) {
			return InteractionResult.PASS;
		}

		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}

		if (LiveVillagesNetworking.isBuildSitePreviewActive(serverPlayer) && !paymentStack.isEmpty()) {
			return InteractionResult.PASS;
		}

		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(serverLevel.getServer());
		SettlementState settlement = SettlementConstruction.findWorkstationSettlement(serverLevel, hitResult.getBlockPos()).orElse(null);
		if (settlement == null) {
			return InteractionResult.PASS;
		}

		Map<String, Integer> stock = new LinkedHashMap<>(settlement.stock());
		RecipeTradePlan firstStocked = null;
		RecipeTradePlan selected = null;

		for (RecipeTradePlan plan : plans) {
			if (!hasStockedOutput(stock, plan.outputGoodsKey(), 1)) {
				continue;
			}

			if (firstStocked == null) {
				firstStocked = plan;
			}

			if (serverPlayer.getAbilities().instabuild || hasPayment(serverPlayer, plan.payments())) {
				selected = plan;
				break;
			}
		}

		if (selected == null) {
			if (firstStocked == null) {
				serverPlayer.sendSystemMessage(Component.literal(settlement.name() + "'s " + professionName + " has no gear ready to trade."));
			} else {
				serverPlayer.sendSystemMessage(Component.literal(
					"This " + professionName + " trade needs " + paymentsDescription(firstStocked.payments()) + "."
				));
			}

			return InteractionResult.SUCCESS_SERVER;
		}

		if (!SettlementGoods.consumeGoods(stock, selected.outputGoodsKey(), 1)) {
			return InteractionResult.SUCCESS_SERVER;
		}

		if (!serverPlayer.getAbilities().instabuild) {
			for (TradePayment payment : selected.payments()) {
				removeMatchingItems(serverPlayer, payment.matcher(), payment.amount());
				SettlementGoods.addGoods(stock, payment.goodsKey(), payment.amount());
			}
		}

		savedData.putSettlementAndRefreshBuildSiteMaterialStatus(
			settlement.withStock(stock),
			serverLevel.getServer().getTickCount()
		);
		giveOrDrop(serverPlayer, TradeBoardTradeRules.createGoodsStack(selected.outputGoodsKey(), 1));
		serverPlayer.sendSystemMessage(Component.literal(
			"Traded " + paymentsDescription(selected.payments()) + " to " + settlement.name() + "'s " + professionName + " for 1 "
				+ displayGoods(selected.outputGoodsKey()) + "."
		));
		return InteractionResult.SUCCESS_SERVER;
	}

	private static String firstAvailable(Map<String, Integer> stock, String[] goodsKeys) {
		for (String goodsKey : goodsKeys) {
			if (stock.getOrDefault(goodsKey, 0) > 0) {
				return goodsKey;
			}
		}

		return null;
	}

	private static LeatherArmorTrade firstAvailableLeatherArmor(Map<String, Integer> stock, int leatherCount, boolean instabuild) {
		for (LeatherArmorTrade trade : LEATHERWORKER_OUTPUT_PRIORITY) {
			if (stock.getOrDefault(trade.goodsKey(), 0) > 0 && (instabuild || leatherCount >= trade.leatherCost())) {
				return trade;
			}
		}

		return null;
	}

	private static LeatherArmorTrade firstStockedLeatherArmor(Map<String, Integer> stock) {
		for (LeatherArmorTrade trade : LEATHERWORKER_OUTPUT_PRIORITY) {
			if (stock.getOrDefault(trade.goodsKey(), 0) > 0) {
				return trade;
			}
		}

		return null;
	}

	private static boolean isRoughStonePayment(ItemStack stack) {
		return stack.is(Items.COBBLESTONE)
			|| stack.is(Items.GRANITE)
			|| stack.is(Items.DIORITE)
			|| stack.is(Items.ANDESITE);
	}

	private static boolean isFletcherArrowheadPayment(ItemStack stack) {
		return stack.is(Items.FLINT)
			|| FletchingTableRecipes.isCopperHeadMaterial(stack)
			|| stack.is(Items.IRON_NUGGET)
			|| stack.is(Items.DIAMOND);
	}

	private static boolean isLeatherworkerCauldron(BlockState state) {
		return state.is(Blocks.CAULDRON) || state.is(Blocks.WATER_CAULDRON);
	}

	private static boolean hasStockedOutput(Map<String, Integer> stock, String goodsKey, int amount) {
		return stock.getOrDefault(goodsKey, 0) >= amount;
	}

	private static TradePayment exactPayment(String goodsKey, int amount, String label, Item item) {
		return matchingPayment(goodsKey, amount, label, stack -> stack.is(item));
	}

	private static TradePayment matchingPayment(String goodsKey, int amount, String label, Predicate<ItemStack> matcher) {
		return new TradePayment(goodsKey, amount, label, matcher);
	}

	private static RecipeTradePlan recipePlan(String outputGoodsKey, TradePayment... payments) {
		return new RecipeTradePlan(outputGoodsKey, List.of(payments));
	}

	private static CarpenterTradePlan carpenterPlan(String outputGoodsKey, int outputAmount, TradePayment... payments) {
		return new CarpenterTradePlan(outputGoodsKey, outputAmount, List.of(payments));
	}

	private static boolean hasPayment(ServerPlayer player, List<TradePayment> payments) {
		for (TradePayment payment : payments) {
			if (countMatchingItems(player, payment.matcher()) < payment.amount()) {
				return false;
			}
		}

		return true;
	}

	private static int countMatchingItems(ServerPlayer player, Predicate<ItemStack> matcher) {
		int count = 0;

		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (!stack.isEmpty() && matcher.test(stack)) {
				count += stack.getCount();
			}
		}

		return count;
	}

	private static void removeMatchingItems(ServerPlayer player, Predicate<ItemStack> matcher, int amount) {
		int remaining = amount;

		for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.isEmpty() || !matcher.test(stack)) {
				continue;
			}

			int removed = Math.min(stack.getCount(), remaining);
			stack.shrink(removed);
			remaining -= removed;

			if (stack.isEmpty()) {
				player.getInventory().setItem(slot, ItemStack.EMPTY);
			}
		}
	}

	private static String paymentsDescription(List<TradePayment> payments) {
		StringBuilder builder = new StringBuilder();

		for (int i = 0; i < payments.size(); i++) {
			TradePayment payment = payments.get(i);
			if (i > 0) {
				builder.append(i == payments.size() - 1 ? " and " : ", ");
			}

			builder.append(payment.amount()).append(' ').append(payment.label());
		}

		return builder.toString();
	}

	private static String displayGoods(String goodsKey) {
		return TradeBoardTradeRules.createGoodsStack(goodsKey, 1).getHoverName().getString();
	}

	private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}

		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
	}

	private record LeatherArmorTrade(String goodsKey, int leatherCost) {
	}

	private record TradePayment(String goodsKey, int amount, String label, Predicate<ItemStack> matcher) {
	}

	private record RecipeTradePlan(String outputGoodsKey, List<TradePayment> payments) {
	}

	private record CarpenterTradePlan(String outputGoodsKey, int outputAmount, List<TradePayment> payments) {
	}
}
