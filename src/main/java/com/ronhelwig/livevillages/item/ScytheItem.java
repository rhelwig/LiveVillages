package com.ronhelwig.livevillages.item;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

public class ScytheItem extends Item {
	public ScytheItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		ItemStack stack = context.getItemInHand();
		Player player = context.getPlayer();
		BlockState state = level.getBlockState(pos);

		if (level.isClientSide()) {
			return canHarvestCrop(state) || isTrimmablePlant(state) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}

		if (player == null) {
			return InteractionResult.PASS;
		}

		if (harvestCrop(level, pos, state, player, stack)) {
			stack.hurtAndBreak(1, player, context.getHand());
			return InteractionResult.SUCCESS_SERVER;
		}

		BlockPos trimPos = trimmablePlantRoot(level, pos, state);
		if (trimPos != null && level.destroyBlock(trimPos, true, player)) {
			level.playSound(null, trimPos, SoundEvents.GRASS_BREAK, SoundSource.PLAYERS, 0.8F, 1.0F);
			stack.hurtAndBreak(1, player, context.getHand());
			return InteractionResult.SUCCESS_SERVER;
		}

		return InteractionResult.PASS;
	}

	private static boolean harvestCrop(Level level, BlockPos pos, BlockState state, Player player, ItemStack tool) {
		if (!canHarvestCrop(state) || !(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
			return false;
		}

		CropBlock crop = (CropBlock) state.getBlock();
		List<ItemStack> drops = new ArrayList<>(Block.getDrops(state, serverLevel, pos, null, player, tool));
		consumeReplantItem(drops, replantItem(state));

		for (ItemStack drop : drops) {
			if (!drop.isEmpty()) {
				Block.popResource(level, pos, drop);
			}
		}

		level.setBlock(pos, crop.getStateForAge(0), 3);
		level.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.PLAYERS, 0.8F, 1.1F);
		level.levelEvent(2001, pos, Block.getId(state));
		return true;
	}

	private static boolean canHarvestCrop(BlockState state) {
		return state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
	}

	private static Item replantItem(BlockState state) {
		if (state.is(Blocks.WHEAT)) {
			return Items.WHEAT_SEEDS;
		}

		if (state.is(Blocks.BEETROOTS)) {
			return Items.BEETROOT_SEEDS;
		}

		if (state.is(Blocks.CARROTS)) {
			return Items.CARROT;
		}

		if (state.is(Blocks.POTATOES)) {
			return Items.POTATO;
		}

		return Items.AIR;
	}

	private static void consumeReplantItem(List<ItemStack> drops, Item replantItem) {
		if (replantItem == Items.AIR) {
			return;
		}

		for (ItemStack drop : drops) {
			if (drop.is(replantItem)) {
				drop.shrink(1);
				return;
			}
		}
	}

	private static BlockPos trimmablePlantRoot(Level level, BlockPos pos, BlockState state) {
		if (!isTrimmablePlant(state)) {
			return null;
		}

		Block block = state.getBlock();
		BlockState below = level.getBlockState(pos.below());
		if ((block == Blocks.TALL_GRASS || block == Blocks.LARGE_FERN || block == Blocks.TALL_DRY_GRASS)
			&& below.is(block)) {
			return pos.below();
		}

		return pos;
	}

	private static boolean isTrimmablePlant(BlockState state) {
		return state.is(Blocks.SHORT_GRASS)
			|| state.is(Blocks.FERN)
			|| state.is(Blocks.DEAD_BUSH)
			|| state.is(Blocks.BUSH)
			|| state.is(Blocks.SHORT_DRY_GRASS)
			|| state.is(Blocks.TALL_DRY_GRASS)
			|| state.is(Blocks.TALL_GRASS)
			|| state.is(Blocks.LARGE_FERN)
			|| state.is(Blocks.WILDFLOWERS)
			|| state.is(Blocks.LEAF_LITTER)
			|| state.is(Blocks.FIREFLY_BUSH);
	}
}
