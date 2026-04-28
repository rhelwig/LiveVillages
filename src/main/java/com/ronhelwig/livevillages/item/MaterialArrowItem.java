package com.ronhelwig.livevillages.item;

import java.util.Objects;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.Level;

public class MaterialArrowItem extends ArrowItem {
	private final String materialKey;

	public MaterialArrowItem(String materialKey, Item.Properties properties) {
		super(properties);
		this.materialKey = Objects.requireNonNull(materialKey, "materialKey");
	}

	public String materialKey() {
		return materialKey;
	}

	@Override
	public AbstractArrow createArrow(Level level, ItemStack ammoStack, LivingEntity shooter, ItemStack weaponStack) {
		return new MaterialArrowEntity(level, shooter, ammoStack, weaponStack, materialKey);
	}

	@Override
	public Projectile asProjectile(Level level, Position position, ItemStack ammoStack, Direction direction) {
		MaterialArrowEntity arrow = new MaterialArrowEntity(level, position.x(), position.y(), position.z(), ammoStack, ItemStack.EMPTY, materialKey);
		arrow.pickup = AbstractArrow.Pickup.ALLOWED;
		return arrow;
	}
}
