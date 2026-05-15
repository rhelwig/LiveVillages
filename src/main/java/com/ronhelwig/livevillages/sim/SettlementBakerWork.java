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

import com.ronhelwig.livevillages.block.entity.GlassDisplayCaseBlockEntity;
import com.ronhelwig.livevillages.content.LiveVillagesBlocks;
import com.ronhelwig.livevillages.menu.TradeBoardTradeRules;

public final class SettlementBakerWork {
	private static final double BAKING_REACH_DISTANCE_SQUARED = 6.25D;
	private static final double BAKING_WALK_SPEED = 0.75D;
	private static final long TASK_MEMORY_TICKS = 80L;
	private static final long BAKING_DECIDE_INTERVAL_TICKS = 2_000L;
	private static final double BATCHES_PER_BAKER_PER_DAY = SettlementEconomyRules.scaledWorkerDailyRate(6.0D);
	private static final String DISPLAY_NAME_PREFIX = "livevillages_bakery_host_";
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

	public static boolean maintainLoadedBaking(
		ServerLevel level,
		SettlementState settlement,
		Map<String, Integer> stock,
		Collection<SettlementBuildSite> buildSites
	) {
		List<SettlementBuildSite> bakeryBuildSites = buildSites.stream()
			.filter(buildSite -> buildSite.blueprintId() == SettlementBuildSiteType.BAKERY)
			.toList();
		boolean stockChanged = restockDisplayCases(level, settlement, stock, bakeryBuildSites);
		syncBakeryDisplays(level, settlement, stock, bakeryBuildSites);

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

			BakeryRecipe recipe = chooseRecipe(settlement, stock, settlementTier);
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

			if (craftRecipe(stock, recipe)) {
				baker.swing(InteractionHand.MAIN_HAND);
				stockChanged = true;
				stockChanged |= restockDisplayCases(level, settlement, stock, bakeryBuildSites);
				syncBakeryDisplays(level, settlement, stock, bakeryBuildSites);
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
			if (recipe == null || !craftRecipe(stock, recipe)) {
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

	private static BakeryRecipe chooseRecipe(SettlementState settlement, Map<String, Integer> stock, int settlementTier) {
		BakeryRecipe bestRecipe = null;
		int bestPriority = Integer.MIN_VALUE;

		for (BakeryRecipe recipe : RECIPES) {
			if (settlementTier < recipe.minTier() || !canCraft(stock, recipe)) {
				continue;
			}

			int current = stock.getOrDefault(recipe.outputGoodsKey(), 0);
			int goal = desiredOutputStock(settlement, recipe.outputGoodsKey());
			if (goal > 0 && current >= goal) {
				continue;
			}

			int shortage = Math.max(1, goal - current);
			int priority = shortage * 100;

			if (recipe.outputGoodsKey().equals("bread")) {
				priority += 500;
			}

			priority += recipe.minTier() * 10;
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

	private static boolean craftRecipe(Map<String, Integer> stock, BakeryRecipe recipe) {
		if (!canCraft(stock, recipe)) {
			return false;
		}

		for (Map.Entry<String, Integer> ingredient : recipe.ingredients().entrySet()) {
			SettlementGoods.consumeGoods(stock, ingredient.getKey(), ingredient.getValue());
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
		Map<String, Integer> stock,
		Collection<SettlementBuildSite> bakeryBuildSites
	) {
		List<DisplayHost> hosts = bakeryDisplayHosts(level, bakeryBuildSites);
		int settlementTier = SettlementTiers.unlockedTier(settlement);
		List<String> displayGoodsKeys = displayableGoodsKeys(stock, settlementTier);
		Map<Long, ItemStack> desiredDisplays = new LinkedHashMap<>();
		int workstationDisplayIndex = 0;

		for (DisplayHost host : hosts) {
			ItemStack desiredStack;
			if (host.workstation()) {
				desiredStack = displayGoodsKeys.isEmpty()
					? ItemStack.EMPTY
					: TradeBoardTradeRules.createGoodsStack(displayGoodsKeys.get(workstationDisplayIndex % displayGoodsKeys.size()), 1);
				workstationDisplayIndex++;
			} else {
				desiredStack = level.getBlockEntity(host.pos()) instanceof GlassDisplayCaseBlockEntity displayCase
					? displayCase.representativeStack()
					: ItemStack.EMPTY;
			}
			desiredDisplays.put(host.pos().asLong(), desiredStack);
		}

		Map<Long, List<Display.ItemDisplay>> existingDisplays = new HashMap<>();
		double scanRadius = SettlementVillagers.settlementRadiusBlocks(settlement) + 16.0D;
		AABB bounds = new AABB(settlement.center()).inflate(scanRadius);

		for (Display.ItemDisplay display : level.getEntitiesOfClass(Display.ItemDisplay.class, bounds, entity -> hostKey(entity) != null)) {
			Long hostKey = hostKey(display);

			if (hostKey == null) {
				display.discard();
				continue;
			}

			existingDisplays.computeIfAbsent(hostKey, ignored -> new ArrayList<>()).add(display);
		}

		Set<Long> validHosts = new LinkedHashSet<>(desiredDisplays.keySet());

		for (Map.Entry<Long, List<Display.ItemDisplay>> entry : existingDisplays.entrySet()) {
			if (validHosts.contains(entry.getKey())) {
				continue;
			}

			for (Display.ItemDisplay display : entry.getValue()) {
				display.discard();
			}
		}

		for (DisplayHost host : hosts) {
			long hostKey = host.pos().asLong();
			List<Display.ItemDisplay> displaysAtHost = existingDisplays.getOrDefault(hostKey, List.of());
			Display.ItemDisplay primary = displaysAtHost.isEmpty() ? null : displaysAtHost.get(0);
			ItemStack desiredStack = desiredDisplays.getOrDefault(hostKey, ItemStack.EMPTY);

			if (desiredStack.isEmpty()) {
				for (Display.ItemDisplay display : displaysAtHost) {
					display.discard();
				}
				continue;
			}

			if (primary == null || primary.isRemoved()) {
				primary = spawnDisplay(level, host, desiredStack);
			} else {
				updateDisplay(primary, host, desiredStack);
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
		List<GlassDisplayCaseBlockEntity> displayCases = bakeryDisplayCases(level, bakeryBuildSites);
		if (displayCases.isEmpty()) {
			return false;
		}

		int settlementTier = SettlementTiers.unlockedTier(settlement);
		Map<String, Integer> availableBundles = new LinkedHashMap<>();
		for (String goodsKey : DISPLAYABLE_GOODS) {
			if (settlementTier < SettlementEconomyRules.requiredTierForGoods(goodsKey)) {
				continue;
			}

			int bundleAmount = displayBundleAmount(goodsKey);
			if (bundleAmount <= 0) {
				continue;
			}

			int current = stock.getOrDefault(goodsKey, 0);
			int target = SettlementEconomyRules.targetForGoods(settlement, goodsKey);
			int surplus = current - target;
			if (surplus >= bundleAmount) {
				availableBundles.put(goodsKey, surplus);
			}
		}

		boolean changed = false;
		for (GlassDisplayCaseBlockEntity displayCase : displayCases) {
			while (true) {
				int emptySlot = displayCase.firstEmptySlot();
				if (emptySlot < 0) {
					break;
				}

				String goodsKey = nextDisplayGoodsKey(availableBundles);
				if (goodsKey == null) {
					break;
				}

				int bundleAmount = displayBundleAmount(goodsKey);
				ItemStack displayStack = TradeBoardTradeRules.createGoodsStack(goodsKey, bundleAmount);
				if (displayStack.isEmpty()) {
					availableBundles.remove(goodsKey);
					continue;
				}

				displayCase.setItem(emptySlot, displayStack);
				SettlementGoods.consumeGoods(stock, goodsKey, bundleAmount);
				int remainingSurplus = availableBundles.getOrDefault(goodsKey, 0) - bundleAmount;
				if (remainingSurplus < bundleAmount) {
					availableBundles.remove(goodsKey);
				} else {
					availableBundles.put(goodsKey, remainingSurplus);
				}
				changed = true;
			}
		}

		return changed;
	}

	private static List<GlassDisplayCaseBlockEntity> bakeryDisplayCases(ServerLevel level, Collection<SettlementBuildSite> bakeryBuildSites) {
		List<GlassDisplayCaseBlockEntity> displayCases = new ArrayList<>();
		for (DisplayHost host : bakeryDisplayHosts(level, bakeryBuildSites)) {
			if (host.workstation()) {
				continue;
			}

			if (level.getBlockEntity(host.pos()) instanceof GlassDisplayCaseBlockEntity displayCase) {
				displayCases.add(displayCase);
			}
		}
		return displayCases;
	}

	private static int displayBundleAmount(String goodsKey) {
		return Math.max(1, TradeBoardTradeRules.bundleSize(goodsKey));
	}

	private static String nextDisplayGoodsKey(Map<String, Integer> availableBundles) {
		return availableBundles.entrySet().stream()
			.sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed())
			.map(Map.Entry::getKey)
			.findFirst()
			.orElse(null);
	}

	private static Display.ItemDisplay spawnDisplay(ServerLevel level, DisplayHost host, ItemStack stack) {
		Display.ItemDisplay display = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
		display.setInvulnerable(true);
		display.setNoGravity(true);
		display.setSilent(true);
		updateDisplay(display, host, stack);
		level.addFreshEntity(display);
		return display;
	}

	private static void updateDisplay(Display.ItemDisplay display, DisplayHost host, ItemStack stack) {
		double yOffset = host.workstation() ? 0.95D : 0.35D;
		display.setPos(host.pos().getX() + 0.5D, host.pos().getY() + yOffset, host.pos().getZ() + 0.5D);
		display.setCustomName(Component.literal(hostTag(host.pos())));
		display.setCustomNameVisible(false);
		display.setItemTransform(ItemDisplayContext.GROUND);
		display.setItemStack(stack.copy());
	}

	private static Long hostKey(Display.ItemDisplay display) {
		if (!display.hasCustomName()) {
			return null;
		}

		String name = display.getCustomName().getString();
		if (!name.startsWith(DISPLAY_NAME_PREFIX)) {
			return null;
		}

		try {
			return Long.parseLong(name.substring(DISPLAY_NAME_PREFIX.length()));
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	private static String hostTag(BlockPos pos) {
		return DISPLAY_NAME_PREFIX + pos.asLong();
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

	private record TimedTask(String taskKey, long tick) {
	}
}
