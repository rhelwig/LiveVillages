package com.ronhelwig.livevillages.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

public class CrookedStaffItem extends Item {
	private static final double HERD_RADIUS = 8.0D;
	private static final double HERD_VERTICAL_RADIUS = 4.0D;
	private static final double HERD_SPEED = 1.15D;
	private static final int HERD_COOLDOWN_TICKS = 20;
	private static final int MAX_HERDED_ANIMALS = 12;

	public CrookedStaffItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
		if (!(target instanceof Animal targetAnimal) || !isHerdAnimal(targetAnimal)) {
			return InteractionResult.PASS;
		}

		if (target.level().isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		if (!(target.level() instanceof ServerLevel level) || player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.SUCCESS_SERVER;
		}

		int moved = herdNearbyAnimals(level, targetAnimal);
		if (moved <= 0) {
			return InteractionResult.PASS;
		}

		stack.hurtAndBreak(1, player, hand);
		player.getCooldowns().addCooldown(stack, HERD_COOLDOWN_TICKS);
		return InteractionResult.SUCCESS_SERVER;
	}

	private static int herdNearbyAnimals(ServerLevel level, Animal targetAnimal) {
		AABB bounds = targetAnimal.getBoundingBox().inflate(HERD_RADIUS, HERD_VERTICAL_RADIUS, HERD_RADIUS);
		double radiusSqr = HERD_RADIUS * HERD_RADIUS;
		int moved = 0;

		for (Animal animal : level.getEntities(
			EntityTypeTest.forClass(Animal.class),
			bounds,
			animal -> animal != targetAnimal
				&& animal.getType() == targetAnimal.getType()
				&& !animal.isRemoved()
				&& animal.isAlive()
				&& animal.distanceToSqr(targetAnimal) <= radiusSqr
		)) {
			if (animal.getNavigation().moveTo(targetAnimal, HERD_SPEED)) {
				moved++;
				if (moved >= MAX_HERDED_ANIMALS) {
					break;
				}
			}
		}

		return moved;
	}

	private static boolean isHerdAnimal(Animal animal) {
		EntityType<?> type = animal.getType();
		return type == EntityType.COW
			|| type == EntityType.SHEEP
			|| type == EntityType.PIG
			|| type == EntityType.CHICKEN
			|| type == EntityType.GOAT
			|| type == EntityType.MOOSHROOM;
	}
}
