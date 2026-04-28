package com.ronhelwig.livevillages.item;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

public class MaterialArrowEntity extends Arrow {
	private static final TagKey<EntityType<?>> IRONHEAD_BONUS_TARGETS = TagKey.create(
		Registries.ENTITY_TYPE,
		Identifier.fromNamespaceAndPath("live-villages", "ironhead_bonus_targets")
	);
	private static final TagKey<EntityType<?>> DIAMONDHEAD_BONUS_TARGETS = TagKey.create(
		Registries.ENTITY_TYPE,
		Identifier.fromNamespaceAndPath("live-villages", "diamondhead_bonus_targets")
	);

	private final String materialKey;
	private final ItemStack pickupStack;

	public MaterialArrowEntity(Level level, LivingEntity shooter, ItemStack pickupStack, ItemStack weaponStack, String materialKey) {
		super(level, shooter, pickupStack.copyWithCount(1), weaponStack);
		this.materialKey = materialKey;
		this.pickupStack = pickupStack.copyWithCount(1);
		setPickupItemStack(this.pickupStack);
	}

	public MaterialArrowEntity(Level level, double x, double y, double z, ItemStack pickupStack, ItemStack weaponStack, String materialKey) {
		super(level, x, y, z, pickupStack.copyWithCount(1), weaponStack);
		this.materialKey = materialKey;
		this.pickupStack = pickupStack.copyWithCount(1);
		setPickupItemStack(this.pickupStack);
	}

	@Override
	protected ItemStack getDefaultPickupItem() {
		return pickupStack.copy();
	}

	@Override
	protected void onHitEntity(EntityHitResult hitResult) {
		super.onHitEntity(hitResult);

		Entity hitEntity = hitResult.getEntity();
		if (!(level() instanceof ServerLevel serverLevel)
			|| !(hitEntity instanceof LivingEntity target)
			|| !target.isAlive()) {
			return;
		}

		float extraDamage = extraDamageAgainst(target);
		if (extraDamage <= 0.0F) {
			return;
		}

		Entity owner = getOwner();
		DamageSource damageSource = damageSources().arrow(this, owner == null ? this : owner);
		target.invulnerableTime = 0;
		target.hurtTime = 0;
		target.hurtServer(serverLevel, damageSource, extraDamage);
	}

	private float extraDamageAgainst(LivingEntity target) {
		return switch (materialKey) {
			case "copper" -> target.getArmorValue() <= 0 ? 1.0F : 0.0F;
			case "iron" -> ironheadDamageBonus(target);
			case "diamond" -> diamondheadDamageBonus(target);
			default -> 0.0F;
		};
	}

	private float ironheadDamageBonus(LivingEntity target) {
		if (target.getType().builtInRegistryHolder().is(IRONHEAD_BONUS_TARGETS)) {
			return 2.0F;
		}

		return target.getArmorValue() > 0 ? 1.5F : 0.0F;
	}

	private float diamondheadDamageBonus(LivingEntity target) {
		if (target.getType().builtInRegistryHolder().is(DIAMONDHEAD_BONUS_TARGETS)) {
			return 2.0F;
		}

		return target.getArmorValue() <= 0 ? 1.5F : 0.5F;
	}
}
