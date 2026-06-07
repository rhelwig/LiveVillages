package com.ronhelwig.livevillages.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public class SlingItem extends Item {
	public static final int COOLDOWN_TICKS = 12;
	public static final float SHOT_VELOCITY = 2.25F;
	private static final float SHOT_INACCURACY = 1.5F;

	public SlingItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);

		if (player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.FAIL;
		}

		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		if (!(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.PASS;
		}

		SlingStoneEntity stone = new SlingStoneEntity(serverLevel, player, new ItemStack(Items.COBBLESTONE));
		stone.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, SHOT_VELOCITY, SHOT_INACCURACY);
		serverLevel.addFreshEntity(stone);
		level.playSound(
			null,
			player.getX(),
			player.getY(),
			player.getZ(),
			SoundEvents.SNOWBALL_THROW,
			SoundSource.PLAYERS,
			0.55F,
			0.85F + level.getRandom().nextFloat() * 0.25F
		);
		stack.hurtAndBreak(1, player, hand);
		player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
		return InteractionResult.SUCCESS_SERVER;
	}
}
