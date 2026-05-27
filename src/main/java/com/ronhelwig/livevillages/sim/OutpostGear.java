package com.ronhelwig.livevillages.sim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

public final class OutpostGear {
	private static final String ITEM_KEY_PREFIX = "item:";
	private static final int EQUIPMENT_DONATION_RADIUS_BLOCKS = 96;
	private static final int RANGED_AMMO_BUNDLE = 8;

	private OutpostGear() {
	}

	public static boolean isValuedExactItem(String goodsKey) {
		return equipmentSlotForExactItem(goodsKey) != null;
	}

	public static boolean isValuedStack(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}

		return isValuedExactItem(exactItemKey(stack));
	}

	public static int equipDonatedGear(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, String exactItemKey, int amount) {
		if (level == null
			|| settlement == null
			|| settlement.kind() != SettlementKind.OUTPOST
			|| amount <= 0
			|| !isValuedExactItem(exactItemKey)) {
			return 0;
		}

		Item item = itemForExactKey(exactItemKey);
		if (item == Items.AIR) {
			return 0;
		}

		EquipmentSlot slot = equipmentSlotForExactItem(exactItemKey);
		int equipped = 0;
		AABB bounds = new AABB(settlement.center()).inflate(EQUIPMENT_DONATION_RADIUS_BLOCKS);

		for (Raider raider : level.getEntitiesOfClass(Raider.class, bounds, raider -> raider.isAlive() && !raider.isRemoved())) {
			if (equipped >= amount) {
				break;
			}

			ItemStack offered = new ItemStack(item);
			if (!shouldEquip(raider.getItemBySlot(slot), offered)) {
				continue;
			}

			if (!consumeSuppliesForGear(stock, offered)) {
				continue;
			}

			raider.setItemSlot(slot, offered);
			equipped++;
		}

		return equipped;
	}

	public static int maintainOutpostEquipment(ServerLevel level, SettlementState settlement, Map<String, Integer> stock) {
		if (level == null || settlement == null || settlement.kind() != SettlementKind.OUTPOST || stock == null || stock.isEmpty()) {
			return 0;
		}

		int equipped = 0;
		AABB bounds = new AABB(settlement.center()).inflate(EQUIPMENT_DONATION_RADIUS_BLOCKS);

		for (Raider raider : level.getEntitiesOfClass(Raider.class, bounds, raider -> raider.isAlive() && !raider.isRemoved())) {
			equipped += equipFromStock(raider, stock);
		}

		for (net.minecraft.world.entity.npc.villager.Villager villager : level.getEntitiesOfClass(net.minecraft.world.entity.npc.villager.Villager.class, bounds, villager -> villager.isAlive() && !villager.isRemoved())) {
			equipped += equipFromStock(villager, stock);
		}

		return equipped;
	}

	public static int equipFromStock(LivingEntity entity, Map<String, Integer> stock) {
		if (entity == null || stock == null || stock.isEmpty()) {
			return 0;
		}

		int equipped = 0;

		for (EquipmentSlot slot : equipmentSlotsInPriorityOrder()) {
			String exactItemKey = bestStockGearKey(stock, slot, entity.getItemBySlot(slot));

			if (exactItemKey == null) {
				continue;
			}

			Item item = itemForExactKey(exactItemKey);
			if (item == Items.AIR) {
				continue;
			}

			ItemStack offered = new ItemStack(item);
			if (!consumeSuppliesForGear(stock, offered) || !SettlementGoods.consumeGoods(stock, exactItemKey, 1)) {
				continue;
			}

			entity.setItemSlot(slot, offered);
			equipped++;
		}

		return equipped;
	}

	public static void clearSpawnedEquipment(LivingEntity entity) {
		if (entity == null) {
			return;
		}

		for (EquipmentSlot slot : equipmentSlotsInPriorityOrder()) {
			entity.setItemSlot(slot, ItemStack.EMPTY);
		}
	}

	public static int supportUnitForExactItem(String goodsKey) {
		if (!isValuedExactItem(goodsKey)) {
			return 0;
		}

		return equipmentSlotForExactItem(goodsKey) == EquipmentSlot.MAINHAND ? 1 : 2;
	}

	private static boolean shouldEquip(ItemStack current, ItemStack offered) {
		if (offered.isEmpty()) {
			return false;
		}

		if (current == null || current.isEmpty()) {
			return true;
		}

		if (!isValuedStack(current)) {
			return true;
		}

		return gearScore(offered) > gearScore(current);
	}

	private static String bestStockGearKey(Map<String, Integer> stock, EquipmentSlot slot, ItemStack current) {
		List<String> candidates = new ArrayList<>();

		for (Map.Entry<String, Integer> entry : stock.entrySet()) {
			if (entry.getValue() == null || entry.getValue() <= 0) {
				continue;
			}

			String exactItemKey = entry.getKey();
			if (equipmentSlotForExactItem(exactItemKey) == slot) {
				candidates.add(exactItemKey);
			}
		}

		return candidates.stream()
			.map(key -> new GearCandidate(key, new ItemStack(itemForExactKey(key))))
			.filter(candidate -> candidate.stack().getItem() != Items.AIR)
			.filter(candidate -> shouldEquip(current, candidate.stack()))
			.filter(candidate -> hasSuppliesForGear(stock, candidate.stack()))
			.max(Comparator.comparingInt(candidate -> gearScore(candidate.stack())))
			.map(GearCandidate::exactItemKey)
			.orElse(null);
	}

	private static boolean hasSuppliesForGear(Map<String, Integer> stock, ItemStack gear) {
		if (!requiresArrows(gear)) {
			return true;
		}

		return stock != null && stock.getOrDefault("arrow", 0) >= RANGED_AMMO_BUNDLE;
	}

	private static boolean consumeSuppliesForGear(Map<String, Integer> stock, ItemStack gear) {
		if (!requiresArrows(gear)) {
			return true;
		}

		return stock != null && SettlementGoods.consumeGoods(stock, "arrow", RANGED_AMMO_BUNDLE);
	}

	private static boolean requiresArrows(ItemStack gear) {
		if (gear == null || gear.isEmpty()) {
			return false;
		}

		Identifier itemId = BuiltInRegistries.ITEM.getKey(gear.getItem());
		if (itemId == null) {
			return false;
		}

		String path = itemId.getPath().toLowerCase(java.util.Locale.ROOT);
		return path.equals("bow") || path.equals("crossbow");
	}

	private static int gearScore(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return 0;
		}

		Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
		if (itemId == null) {
			return 0;
		}

		String path = itemId.getPath().toLowerCase(java.util.Locale.ROOT);
		int materialScore = materialScore(path);
		EquipmentSlot slot = equipmentSlotForPath(path);
		if (slot == null) {
			return materialScore;
		}
		int typeScore = switch (slot) {
			case MAINHAND -> 10;
			case CHEST -> 8;
			case LEGS -> 7;
			case HEAD -> 6;
			case FEET -> 5;
			case OFFHAND -> 4;
			default -> 0;
		};
		return materialScore + typeScore;
	}

	private static int materialScore(String path) {
		if (path.contains("netherite")) {
			return 80;
		}
		if (path.contains("diamond")) {
			return 70;
		}
		if (path.contains("iron")) {
			return 55;
		}
		if (path.contains("copper")) {
			return 45;
		}
		if (path.contains("chainmail")) {
			return 42;
		}
		if (path.contains("stone")) {
			return 35;
		}
		if (path.contains("gold")) {
			return 32;
		}
		if (path.contains("leather")) {
			return 25;
		}
		if (path.contains("wood") || path.contains("wooden")) {
			return 18;
		}
		return 30;
	}

	private static EquipmentSlot equipmentSlotForExactItem(String goodsKey) {
		if (goodsKey == null || !goodsKey.startsWith(ITEM_KEY_PREFIX)) {
			return null;
		}

		Identifier itemId = Identifier.tryParse(goodsKey.substring(ITEM_KEY_PREFIX.length()));
		if (itemId == null || BuiltInRegistries.ITEM.getOptional(itemId).isEmpty()) {
			return null;
		}

		return equipmentSlotForPath(itemId.getPath().toLowerCase(java.util.Locale.ROOT));
	}

	private static EquipmentSlot equipmentSlotForPath(String path) {
		if (path.endsWith("_helmet") || path.equals("turtle_helmet")) {
			return EquipmentSlot.HEAD;
		}
		if (path.endsWith("_chestplate") || path.equals("elytra")) {
			return EquipmentSlot.CHEST;
		}
		if (path.endsWith("_leggings")) {
			return EquipmentSlot.LEGS;
		}
		if (path.endsWith("_boots")) {
			return EquipmentSlot.FEET;
		}
		if (path.equals("shield")) {
			return EquipmentSlot.OFFHAND;
		}
		if (path.endsWith("_sword")
			|| path.endsWith("_axe")
			|| path.endsWith("_pickaxe")
			|| path.endsWith("_shovel")
			|| path.endsWith("_hoe")
			|| path.equals("bow")
			|| path.equals("crossbow")
			|| path.equals("spear")
			|| path.endsWith("_spear")
			|| path.equals("trident")
			|| path.equals("mace")) {
			return EquipmentSlot.MAINHAND;
		}

		return null;
	}

	private static String exactItemKey(ItemStack stack) {
		Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return itemId == null ? null : ITEM_KEY_PREFIX + itemId;
	}

	private static Item itemForExactKey(String goodsKey) {
		if (goodsKey == null || !goodsKey.startsWith(ITEM_KEY_PREFIX)) {
			return Items.AIR;
		}

		Identifier itemId = Identifier.tryParse(goodsKey.substring(ITEM_KEY_PREFIX.length()));
		if (itemId == null) {
			return Items.AIR;
		}

		return BuiltInRegistries.ITEM.getOptional(itemId).orElse(Items.AIR);
	}

	private static List<EquipmentSlot> equipmentSlotsInPriorityOrder() {
		return List.of(
			EquipmentSlot.MAINHAND,
			EquipmentSlot.OFFHAND,
			EquipmentSlot.HEAD,
			EquipmentSlot.CHEST,
			EquipmentSlot.LEGS,
			EquipmentSlot.FEET
		);
	}

	private record GearCandidate(String exactItemKey, ItemStack stack) {
	}
}
