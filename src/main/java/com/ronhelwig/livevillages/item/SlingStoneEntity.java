package com.ronhelwig.livevillages.item;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import com.ronhelwig.livevillages.content.LiveVillagesEntityTypes;

public class SlingStoneEntity extends ThrowableItemProjectile {
	public static final float DAMAGE = 2.0F;

	public SlingStoneEntity(EntityType<? extends SlingStoneEntity> entityType, Level level) {
		super(entityType, level);
	}

	public SlingStoneEntity(Level level, LivingEntity owner, ItemStack stack) {
		super(LiveVillagesEntityTypes.SLING_STONE, owner, level, stack);
	}

	@Override
	protected Item getDefaultItem() {
		return Items.COBBLESTONE;
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id != 3) {
			return;
		}

		ItemParticleOption particle = new ItemParticleOption(ParticleTypes.ITEM, Items.COBBLESTONE);
		for (int i = 0; i < 6; i++) {
			level().addParticle(particle, getX(), getY(), getZ(), 0.0D, 0.0D, 0.0D);
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult hitResult) {
		super.onHitEntity(hitResult);

		Entity hitEntity = hitResult.getEntity();
		if (!(level() instanceof ServerLevel serverLevel)) {
			return;
		}

		DamageSource damageSource = damageSources().thrown(this, getOwner());
		hitEntity.hurtServer(serverLevel, damageSource, DAMAGE);
	}

	@Override
	protected void onHit(HitResult hitResult) {
		super.onHit(hitResult);

		if (!level().isClientSide()) {
			level().broadcastEntityEvent(this, (byte) 3);
			discard();
		}
	}
}
