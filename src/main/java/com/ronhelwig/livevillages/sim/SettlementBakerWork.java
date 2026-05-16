package com.ronhelwig.livevillages.sim;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import com.ronhelwig.livevillages.block.entity.SaleDisplayBlockEntity;
import com.ronhelwig.livevillages.content.LiveVillagesBlocks;
import com.ronhelwig.livevillages.menu.TradeBoardTradeRules;

public final class SettlementBakerWork {
	private static final double BAKING_REACH_DISTANCE_SQUARED = 6.25D;
	private static final double BAKING_WALK_SPEED = 0.75D;
	private static final long TASK_MEMORY_TICKS = 80L;
	private static final long BAKING_DECIDE_INTERVAL_TICKS = 400L;
	private static final double BATCHES_PER_BAKER_PER_DAY = SettlementEconomyRules.scaledWorkerDailyRate(6.0D);
	private static final String DISPLAY_NAME_PREFIX = "livevillages_bakery_display_";
	private static final List<String> DISPLAYABLE_GOODS = List.of("golden_apple", "cake", "pumpkin_pie", "cookie", "baked_potato", "bread");
	private static final List<BakeryRecipe> RECIPES = List.of(
		new BakeryRecipe("baking_bread", 1, "bread", 1, Map.of("wheat", 3)),
		new BakeryRecipe("baking_potatoes", 1, "baked_potato", 1, Map.of("potato", 1)),
		new BakeryRecipe("baking_cookies", 2, "cookie", 8, Map.of("wheat", 2, "cocoa_beans", 1)),
		new BakeryRecipe("baking_pies", 2, "pumpkin_pie", 1, Map.of("pumpkin", 1, "egg", 1, "sugar", 1)),
		new BakeryRecipe("baking_cakes", 3, "cake", 1, Map.of("milk_bucket", 3, "sugar", 2, "egg", 1, "wheat", 3)),
		new BakeryRecipe("crafting_golden_apples", 4, "golden_apple", 1, Map.of("apple", 1, "gold_ingot", 8))
	);
	private static final Map<String, TimedTask> ACTIVE_TASKS = new HashMap<>();

	private SettlementBakerWork() {
	}

	public static void syncBakeryDisplaysNear(ServerLevel level, BlockPos pos) {
		resolveBakeryContext(level, pos).ifPresent(context -> syncBakeryDisplays(level, context.settlement(), context.bakeryBuildSites()));
	}

	public static int bakeryFreebiesOwed(ServerLevel level, BlockPos pos, UUID playerId) {
		return resolveBakeryContext(level, pos)
			.map(context -> LiveVillagesSavedData.get(level.getServer()).bakeryFreebiesOwed(context.settlement().id(), playerId))
			.orElse(0);
	}

	public static boolean consumeBakeryFreebie(ServerLevel level, BlockPos pos, UUID playerId) {
		return resolveBakeryContext(level, pos)
			.map(context -> LiveVillagesSavedData.get(level.getServer()).consumeBakeryFreebie(context.settlement().id(), playerId))
			.orElse(false);
	}

	public static boolean isBakedGoods(ItemStack stack) {
		return isBakedGoodsKey(TradeBoardTradeRules.goodsKeyForStack(stack));
	}

	public static boolean isBakedGoodsKey(String goodsKey) {
		return goodsKey != null && DISPLAYABLE_GOODS.contains(goodsKey);
	}

	public static void recordBakerySale(ServerLevel level, BlockPos pos, String paymentGoodsKey, int paymentAmount) {
		resolveBakeryContext(level, pos).ifPresent(context -> {
			Map<String, Integer> stock = new LinkedHashMap<>(context.settlement().stock());
			boolean stockChanged = false;

			if (paymentGoodsKey != null && !paymentGoodsKey.isBlank() && paymentAmount > 0) {
				SettlementGoods.addGoods(stock, paymentGoodsKey, paymentAmount);
				stockChanged = true;
			}

			stockChanged |= restockDisplayCases(level, context.settlement(), stock, context.bakeryBuildSites());
			syncBakeryDisplays(level, context.settlement(), context.bakeryBuildSites());

			if (stockChanged) {
				LiveVillagesSavedData.get(level.getServer()).putSettlement(context.settlement().withStock(stock));
			}
		});
	}

	public static boolean maintainLoadedBaking(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Collection<SettlementBuildSite> buildSites
	) {
		List<SettlementBuildSite> bakeryBuildSites = buildSites.stream()
			.filter(buildSite -> buildSite.blueprintId() == SettlementBuildSiteType.BAKERY)
			.toList();
		List<SaleDisplayBlockEntity> displayInventories = bakeryDisplayInventories(level, bakeryBuildSites);
		boolean stockChanged = restockDisplayCases(level, settlement, stock, bakeryBuildSites);
		syncBakeryDisplays(level, settlement, bakeryBuildSites);

		List<Villager> bakers = SettlementVillagers.nearbyBakers(level, settlement);
		if (bakers.isEmpty()) {
			return stockChanged;
		}

		int settlementTier = SettlementTiers.unlockedTier(settlement);
		long tick = level.getServer().getTickCount();

		for (Villager baker : bakers) {
			if (!SettlementVillagerWorkSchedule.shouldStartNewWork(level, baker, "baking", BAKING_DECIDE_INTERVAL_TICKS)) {
				if (SettlementVillagerWorkSchedule.isTakingBreak(level, baker)) {
					baker.getNavigation().stop();
				}

				continue;
			}

			Map<String, Integer> pantry = bakeryPantrySnapshot(stock, displayInventories);
			BakeryRecipe recipe = chooseRecipe(settlement, pantry, settlementTier);
			BlockPos workPos = SettlementVillagers.bakerJobSite(level, baker)
				.or(() -> bakeryBuildSites.stream()
					.map(buildSite -> SettlementConstruction.currentPlacedWorkstationPos(level, buildSite))
					.findFirst())
				.or(() -> SettlementStockAccess.findStockAccessPos(level, settlement, bakeryBuildSites))
				.orElse(settlement.center());
			String taskKey = recipe == null ? (displayableGoodsKeys(stock, settlementTier).isEmpty() ? "baking_goods" : "arranging_displays") : recipe.taskKey();
			showBakerTool(baker, recipe);
			baker.getNavigation().moveTo(workPos.getX() + 0.5D, workPos.getY(), workPos.getZ() + 0.5D, BAKING_WALK_SPEED);
			ACTIVE_TASKS.put(baker.getUUID().toString(), new TimedTask(taskKey, tick));

			if (recipe == null || !isWithinWorkReach(baker, workPos)) {
				continue;
			}

			if (craftRecipe(stock, displayInventories, recipe)) {
				baker.swing(InteractionHand.MAIN_HAND);
				stockChanged = true;
				stockChanged |= restockDisplayCases(level, settlement, stock, bakeryBuildSites);
				syncBakeryDisplays(level, settlement, bakeryBuildSites);
			}
		}

		return stockChanged;
	}

	public static void applyBakingProduction(SettlementState settlement, Map<String, Integer> stock, double elapsedDays, int bakerCount) {
		if (bakerCount <= 0 || elapsedDays <= 0.0D) {
			return;
		}

		int settlementTier = SettlementTiers.unlockedTier(settlement);
		int batches = scaledAmount(BATCHES_PER_BAKER_PER_DAY * bakerCount, elapsedDays);

		for (int i = 0; i < batches; i++) {
			BakeryRecipe recipe = chooseRecipe(settlement, stock, settlementTier);
			if (recipe == null || !craftRecipe(stock, List.of(), recipe)) {
				break;
			}
		}
	}

	public static Optional<String> loadedBakerTaskKey(ServerLevel level, Villager villager) {
		TimedTask task = ACTIVE_TASKS.get(villager.getUUID().toString());

		if (task == null || level.getServer().getTickCount() - task.tick() > TASK_MEMORY_TICKS) {
			return Optional.empty();
		}

		return Optional.of(task.taskKey());
	}

	private static BakeryRecipe chooseRecipe(SettlementState settlement, Map<String, Integer> availableGoods, int settlementTier) {
		BakeryRecipe bestRecipe = null;
		int bestPriority = Integer.MIN_VALUE;

		for (BakeryRecipe recipe : RECIPES) {
			if (settlementTier < recipe.minTier() || !canCraft(availableGoods, recipe)) {
				continue;
			}

			int current = availableGoods.getOrDefault(recipe.outputGoodsKey(), 0);
			int goal = desiredOutputStock(settlement, recipe.outputGoodsKey());
			if (goal > 0 && current >= goal) {
				continue;
			}

			int shortage = Math.max(1, goal - current);
			int priority = shortage * 100;

			if (current <= 0) {
				priority += 220 + (recipe.minTier() * 30);
			}

			if (recipe.outputGoodsKey().equals("bread")) {
				priority += current < Math.max(1, goal / 2) ? 180 : -80;
			}

			priority += recipe.minTier() * 20;
			priority += recipe.outputAmount();

			if (priority > bestPriority) {
				bestPriority = priority;
				bestRecipe = recipe;
			}
		}

		return bestRecipe;
	}

	private static int desiredOutputStock(SettlementState settlement, String goodsKey) {
		int target = SettlementEconomyRules.targetForGoods(settlement, goodsKey);

		if (goodsKey.equals("bread")) {
			return target;
		}

		return target <= 0 ? 1 : (target * 2) + 1;
	}

	private static boolean canCraft(Map<String, Integer> stock, BakeryRecipe recipe) {
		for (Map.Entry<String, Integer> ingredient : recipe.ingredients().entrySet()) {
			if (stock.getOrDefault(ingredient.getKey(), 0) < ingredient.getValue()) {
				return false;
			}
		}

		return true;
	}

	private static boolean craftRecipe(Map<String, Integer> stock, List<SaleDisplayBlockEntity> displayInventories, BakeryRecipe recipe) {
		if (!canCraft(bakeryPantrySnapshot(stock, displayInventories), recipe)) {
			return false;
		}

		for (Map.Entry<String, Integer> ingredient : recipe.ingredients().entrySet()) {
			consumeBakeryIngredient(stock, displayInventories, ingredient.getKey(), ingredient.getValue());
		}

		SettlementGoods.addGoods(stock, recipe.outputGoodsKey(), recipe.outputAmount());
		return true;
	}

	private static int scaledAmount(double ratePerDay, double elapsedDays) {
		if (ratePerDay <= 0.0D || elapsedDays <= 0.0D) {
			return 0;
		}

		return Math.max(0, (int) Math.round(ratePerDay * elapsedDays));
	}

	private static boolean isWithinWorkReach(Villager baker, BlockPos workPos) {
		return baker.distanceToSqr(workPos.getX() + 0.5D, workPos.getY() + 0.5D, workPos.getZ() + 0.5D) <= BAKING_REACH_DISTANCE_SQUARED;
	}

	private static void showBakerTool(Villager baker, BakeryRecipe recipe) {
		ItemStack held = switch (recipe == null ? "" : recipe.outputGoodsKey()) {
			case "cookie" -> new ItemStack(Items.COOKIE);
			case "cake" -> new ItemStack(Items.CAKE);
			case "pumpkin_pie" -> new ItemStack(Items.PUMPKIN_PIE);
			case "golden_apple" -> new ItemStack(Items.GOLDEN_APPLE);
			case "baked_potato" -> new ItemStack(Items.BAKED_POTATO);
			default -> new ItemStack(Items.BREAD);
		};

		if (!ItemStack.isSameItemSameComponents(baker.getMainHandItem(), held)) {
			baker.setItemSlot(EquipmentSlot.MAINHAND, held);
		}
	}

	private static void syncBakeryDisplays(
		ServerLevel level,
		SettlementState settlement,
		Collection<SettlementBuildSite> bakeryBuildSites
	) {
		List<DisplayHost> hosts = bakeryDisplayHosts(level, bakeryBuildSites);
		Map<String, DesiredDisplay> desiredDisplays = new LinkedHashMap<>();

		for (DisplayHost host : hosts) {
			if (!(level.getBlockEntity(host.pos()) instanceof SaleDisplayBlockEntity displayInventory)) {
				continue;
			}

			for (int slot = 0; slot < SaleDisplayBlockEntity.SLOT_COUNT; slot++) {
				ItemStack desiredStack = displayInventory.getItem(slot);
				if (desiredStack.isEmpty()) {
					continue;
				}

				desiredDisplays.put(displaySlotTag(host.pos(), slot), new DesiredDisplay(host, slot, desiredStack.copy()));
			}
		}

		Map<String, List<Display.ItemDisplay>> existingDisplays = new HashMap<>();
		double scanRadius = SettlementVillagers.settlementRadiusBlocks(settlement) + 16.0D;
		AABB bounds = new AABB(settlement.center()).inflate(scanRadius);

		for (Display.ItemDisplay display : level.getEntitiesOfClass(Display.ItemDisplay.class, bounds, entity -> displayTag(entity) != null)) {
			String displayTag = displayTag(display);
			if (displayTag != null) {
				existingDisplays.computeIfAbsent(displayTag, ignored -> new ArrayList<>()).add(display);
			}
		}

		Set<String> validDisplays = new LinkedHashSet<>(desiredDisplays.keySet());

		for (Map.Entry<String, List<Display.ItemDisplay>> entry : existingDisplays.entrySet()) {
			if (validDisplays.contains(entry.getKey())) {
				continue;
			}

			for (Display.ItemDisplay display : entry.getValue()) {
				display.discard();
			}
		}

		for (Map.Entry<String, DesiredDisplay> entry : desiredDisplays.entrySet()) {
			List<Display.ItemDisplay> displaysAtHost = existingDisplays.getOrDefault(entry.getKey(), List.of());
			Display.ItemDisplay primary = displaysAtHost.isEmpty() ? null : displaysAtHost.get(0);
			DesiredDisplay desiredDisplay = entry.getValue();

			if (primary == null || primary.isRemoved()) {
				primary = spawnDisplay(level, desiredDisplay);
			} else {
				updateDisplay(primary, desiredDisplay);
			}

			for (int i = 1; i < displaysAtHost.size(); i++) {
				displaysAtHost.get(i).discard();
			}
		}
	}

	private static List<DisplayHost> bakeryDisplayHosts(ServerLevel level, Collection<SettlementBuildSite> bakeryBuildSites) {
		LinkedHashMap<Long, DisplayHost> hosts = new LinkedHashMap<>();

		for (SettlementBuildSite buildSite : bakeryBuildSites) {
			BlockPos workstationPos = SettlementConstruction.currentPlacedWorkstationPos(level, buildSite);
			if (level.hasChunkAt(workstationPos) && level.getBlockState(workstationPos).is(LiveVillagesBlocks.BAKERS_COUNTER)) {
				hosts.put(workstationPos.asLong(), new DisplayHost(workstationPos.immutable(), true));
			}

			for (SettlementBuildBlockState block : buildSite.blocks()) {
				if (!"glass_display_case".equals(block.expectedMaterialKey())) {
					continue;
				}

				SettlementConstruction.buildSiteBlockPos(buildSite, block)
					.filter(level::hasChunkAt)
					.filter(pos -> level.getBlockState(pos).is(LiveVillagesBlocks.GLASS_DISPLAY_CASE))
					.ifPresent(pos -> hosts.putIfAbsent(pos.asLong(), new DisplayHost(pos.immutable(), false)));
			}
		}

		return hosts.values().stream()
			.sorted(Comparator.<DisplayHost>comparingInt(host -> host.workstation() ? 0 : 1)
				.thenComparingInt(host -> host.pos().getX())
				.thenComparingInt(host -> host.pos().getZ()))
			.toList();
	}

	private static List<String> displayableGoodsKeys(Map<String, Integer> stock, int settlementTier) {
		return DISPLAYABLE_GOODS.stream()
			.filter(goodsKey -> settlementTier >= SettlementEconomyRules.requiredTierForGoods(goodsKey))
			.filter(goodsKey -> stock.getOrDefault(goodsKey, 0) > 0)
			.sorted(Comparator
				.<String>comparingInt(goodsKey -> stock.getOrDefault(goodsKey, 0))
				.reversed()
				.thenComparingInt(DISPLAYABLE_GOODS::indexOf))
			.toList();
	}

	private static boolean restockDisplayCases(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Collection<SettlementBuildSite> bakeryBuildSites
	) {
		List<SaleDisplayBlockEntity> displayInventories = bakeryDisplayInventories(level, bakeryBuildSites);
		if (displayInventories.isEmpty()) {
			return false;
		}

		Map<String, Integer> availableSurplus = availableDisplaySurplus(settlement, stock);
		if (availableSurplus.isEmpty()) {
			return false;
		}

		boolean changed = topOffMatchingDisplayStacks(displayInventories, stock, availableSurplus);

		while (true) {
			String goodsKey = nextNewDisplayGoodsKey(displayInventories, availableSurplus);
			if (goodsKey == null) {
				break;
			}

			SaleDisplayBlockEntity targetDisplay = nextDisplayForNewStack(displayInventories);
			if (targetDisplay == null) {
				break;
			}

			int emptySlot = targetDisplay.firstEmptySlot();
			if (emptySlot < 0) {
				break;
			}

			int displayAmount = Math.min(displayNewStackAmount(goodsKey), availableSurplus.getOrDefault(goodsKey, 0));
			ItemStack displayStack = TradeBoardTradeRules.createGoodsStack(goodsKey, displayAmount);
			if (displayAmount <= 0 || displayStack.isEmpty()) {
				availableSurplus.remove(goodsKey);
				continue;
			}

			targetDisplay.setItem(emptySlot, displayStack);
			consumeDisplayGoods(stock, availableSurplus, goodsKey, displayAmount);
			changed = true;
		}

		return changed;
	}

	private static List<SaleDisplayBlockEntity> bakeryDisplayInventories(ServerLevel level, Collection<SettlementBuildSite> bakeryBuildSites) {
		List<SaleDisplayBlockEntity> displayInventories = new ArrayList<>();
		for (DisplayHost host : bakeryDisplayHosts(level, bakeryBuildSites)) {
			if (level.getBlockEntity(host.pos()) instanceof SaleDisplayBlockEntity displayInventory) {
				displayInventories.add(displayInventory);
			}
		}
		return displayInventories;
	}

	private static Optional<BakeryContext> resolveBakeryContext(ServerLevel level, BlockPos pos) {
		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(level.getServer());
		Optional<SettlementState> settlement = savedData.findSettlementForPosition(level.dimension(), pos, candidate -> true);
		if (settlement.isEmpty()) {
			return Optional.empty();
		}

		List<SettlementBuildSite> bakeryBuildSites = savedData.getBuildSitesForSettlement(settlement.get().id()).stream()
			.filter(buildSite -> buildSite.blueprintId() == SettlementBuildSiteType.BAKERY)
			.toList();
		if (bakeryBuildSites.isEmpty()) {
			return Optional.empty();
		}

		return Optional.of(new BakeryContext(settlement.get(), bakeryBuildSites));
	}

	private static Map<String, Integer> bakeryPantrySnapshot(Map<String, Integer> stock, List<SaleDisplayBlockEntity> displayInventories) {
		Map<String, Integer> pantry = new LinkedHashMap<>(stock);

		for (SaleDisplayBlockEntity displayInventory : displayInventories) {
			for (int slot = 0; slot < SaleDisplayBlockEntity.SLOT_COUNT; slot++) {
				ItemStack stack = displayInventory.getItem(slot);
				String goodsKey = TradeBoardTradeRules.goodsKeyForStack(stack);
				if (stack.isEmpty() || goodsKey == null) {
					continue;
				}

				SettlementGoods.addGoods(pantry, goodsKey, stack.getCount());
			}
		}

		return pantry;
	}

	private static void consumeBakeryIngredient(
		Map<String, Integer> stock,
		List<SaleDisplayBlockEntity> displayInventories,
		String goodsKey,
		int amount
	) {
		int remaining = amount;
		int stockAvailable = stock.getOrDefault(goodsKey, 0);
		if (stockAvailable > 0) {
			int stockConsumed = Math.min(stockAvailable, remaining);
			SettlementGoods.consumeGoods(stock, goodsKey, stockConsumed);
			remaining -= stockConsumed;
		}

		if (remaining <= 0) {
			return;
		}

		for (SaleDisplayBlockEntity displayInventory : displayInventories) {
			for (int slot = 0; slot < SaleDisplayBlockEntity.SLOT_COUNT && remaining > 0; slot++) {
				ItemStack displayStack = displayInventory.getItem(slot);
				if (displayStack.isEmpty() || !goodsKey.equals(TradeBoardTradeRules.goodsKeyForStack(displayStack))) {
					continue;
				}

				int consumed = Math.min(remaining, displayStack.getCount());
				ItemStack updatedStack = displayStack.copy();
				updatedStack.shrink(consumed);
				displayInventory.setItem(slot, updatedStack.isEmpty() ? ItemStack.EMPTY : updatedStack);
				remaining -= consumed;
			}

			if (remaining <= 0) {
				return;
			}
		}
	}

	private static int displayBundleAmount(String goodsKey) {
		return Math.max(1, TradeBoardTradeRules.bundleSize(goodsKey));
	}

	private static int displayNewStackAmount(String goodsKey) {
		ItemStack prototype = TradeBoardTradeRules.createGoodsStack(goodsKey, 1);
		if (prototype.isEmpty()) {
			return 0;
		}

		return Math.min(displayBundleAmount(goodsKey), prototype.getMaxStackSize());
	}

	private static Map<String, Integer> availableDisplaySurplus(SettlementState settlement, Map<String, Integer> stock) {
		int settlementTier = SettlementTiers.unlockedTier(settlement);
		Map<String, Integer> availableSurplus = new LinkedHashMap<>();

		for (String goodsKey : DISPLAYABLE_GOODS) {
			if (settlementTier < SettlementEconomyRules.requiredTierForGoods(goodsKey)) {
				continue;
			}

			int current = stock.getOrDefault(goodsKey, 0);
			int target = SettlementEconomyRules.targetForGoods(settlement, goodsKey);
			int surplus = current - target;
			if (surplus > 0) {
				availableSurplus.put(goodsKey, surplus);
			}
		}

		return availableSurplus;
	}

	private static boolean topOffMatchingDisplayStacks(
		List<SaleDisplayBlockEntity> displayInventories,
		Map<String, Integer> stock,
		Map<String, Integer> availableSurplus
	) {
		boolean changed = false;

		for (SaleDisplayBlockEntity displayInventory : displayInventories) {
			for (int slot = 0; slot < SaleDisplayBlockEntity.SLOT_COUNT; slot++) {
				ItemStack displayStack = displayInventory.getItem(slot);
				if (displayStack.isEmpty()) {
					continue;
				}

				String goodsKey = TradeBoardTradeRules.goodsKeyForStack(displayStack);
				if (goodsKey == null || !DISPLAYABLE_GOODS.contains(goodsKey)) {
					continue;
				}

				int available = availableSurplus.getOrDefault(goodsKey, 0);
				if (available <= 0) {
					continue;
				}

				int capacity = displayStack.getMaxStackSize() - displayStack.getCount();
				if (capacity <= 0) {
					continue;
				}

				int addAmount = Math.min(available, capacity);
				ItemStack updatedStack = displayStack.copy();
				updatedStack.grow(addAmount);
				displayInventory.setItem(slot, updatedStack);
				consumeDisplayGoods(stock, availableSurplus, goodsKey, addAmount);
				changed = true;
			}
		}

		return changed;
	}

	private static String nextNewDisplayGoodsKey(List<SaleDisplayBlockEntity> displayInventories, Map<String, Integer> availableSurplus) {
		return availableSurplus.entrySet().stream()
			.filter(entry -> entry.getValue() >= displayNewStackAmount(entry.getKey()))
			.sorted(Comparator
				.<Map.Entry<String, Integer>>comparingInt(entry -> displayedSlotCount(displayInventories, entry.getKey()))
				.thenComparing(Map.Entry<String, Integer>::getValue, Comparator.reverseOrder())
				.thenComparingInt(entry -> DISPLAYABLE_GOODS.indexOf(entry.getKey())))
			.map(Map.Entry::getKey)
			.findFirst()
			.orElse(null);
	}

	private static SaleDisplayBlockEntity nextDisplayForNewStack(List<SaleDisplayBlockEntity> displayInventories) {
		return displayInventories.stream()
			.filter(displayInventory -> displayInventory.firstEmptySlot() >= 0)
			.min(Comparator.comparingInt(SettlementBakerWork::occupiedSlotCount))
			.orElse(null);
	}

	private static int occupiedSlotCount(SaleDisplayBlockEntity displayInventory) {
		int occupied = 0;
		for (int slot = 0; slot < SaleDisplayBlockEntity.SLOT_COUNT; slot++) {
			if (!displayInventory.getItem(slot).isEmpty()) {
				occupied++;
			}
		}
		return occupied;
	}

	private static int displayedSlotCount(List<SaleDisplayBlockEntity> displayInventories, String goodsKey) {
		int displayedSlots = 0;

		for (SaleDisplayBlockEntity displayInventory : displayInventories) {
			for (int slot = 0; slot < SaleDisplayBlockEntity.SLOT_COUNT; slot++) {
				if (goodsKey.equals(TradeBoardTradeRules.goodsKeyForStack(displayInventory.getItem(slot)))) {
					displayedSlots++;
				}
			}
		}

		return displayedSlots;
	}

	private static void consumeDisplayGoods(
		Map<String, Integer> stock,
		Map<String, Integer> availableSurplus,
		String goodsKey,
		int amount
	) {
		if (amount <= 0) {
			return;
		}

		SettlementGoods.consumeGoods(stock, goodsKey, amount);
		int remaining = availableSurplus.getOrDefault(goodsKey, 0) - amount;
		if (remaining > 0) {
			availableSurplus.put(goodsKey, remaining);
		} else {
			availableSurplus.remove(goodsKey);
		}
	}

	private static Display.ItemDisplay spawnDisplay(ServerLevel level, DesiredDisplay desiredDisplay) {
		Display.ItemDisplay display = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
		display.setInvulnerable(true);
		display.setNoGravity(true);
		display.setSilent(true);
		updateDisplay(display, desiredDisplay);
		level.addFreshEntity(display);
		return display;
	}

	private static void updateDisplay(Display.ItemDisplay display, DesiredDisplay desiredDisplay) {
		DisplayHost host = desiredDisplay.host();
		double x = host.pos().getX() + 0.5D + shelfColumnXOffset(desiredDisplay.slot());
		double y = host.pos().getY() + shelfYOffset(host.workstation(), desiredDisplay.slot());
		double z = host.pos().getZ() + 0.5D;

		display.setPos(x, y, z);
		display.setCustomName(Component.literal(desiredDisplay.tag()));
		display.setCustomNameVisible(false);
		display.setItemTransform(ItemDisplayContext.GROUND);
		display.setItemStack(desiredDisplay.stack().copy());
	}

	private static String displayTag(Display.ItemDisplay display) {
		if (!display.hasCustomName()) {
			return null;
		}

		String name = display.getCustomName().getString();
		return name.startsWith(DISPLAY_NAME_PREFIX) ? name : null;
	}

	private static String displaySlotTag(BlockPos pos, int slot) {
		return DISPLAY_NAME_PREFIX + pos.asLong() + "_slot_" + slot;
	}

	private static double shelfColumnXOffset(int slot) {
		int column = Math.floorMod(slot, SaleDisplayBlockEntity.SLOT_COUNT / 2);
		return switch (column) {
			case 0 -> -0.20D;
			case 1 -> 0.0D;
			default -> 0.20D;
		};
	}

	private static double shelfYOffset(boolean workstation, int slot) {
		int row = slot / (SaleDisplayBlockEntity.SLOT_COUNT / 2);
		if (workstation) {
			return row == 0 ? 0.78D : 0.28D;
		}

		return row == 0 ? 0.58D : 0.12D;
	}

	private record BakeryRecipe(
		String taskKey,
		int minTier,
		String outputGoodsKey,
		int outputAmount,
		Map<String, Integer> ingredients
	) {
	}

	private record DisplayHost(BlockPos pos, boolean workstation) {
	}

	private record DesiredDisplay(DisplayHost host, int slot, ItemStack stack) {
		private String tag() {
			return displaySlotTag(host.pos(), slot);
		}
	}

	private record TimedTask(String taskKey, long tick) {
	}

	private record BakeryContext(SettlementState settlement, List<SettlementBuildSite> bakeryBuildSites) {
	}
}
