package com.ronhelwig.livevillages.sim;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;

import com.ronhelwig.livevillages.menu.FletchingTableMenu;
import com.ronhelwig.livevillages.network.LiveVillagesNetworking;

public final class FletchingTableInteraction {
	private static final Component CONTAINER_TITLE = Component.translatable("container.live-villages.fletching_table");

	private FletchingTableInteraction() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register(FletchingTableInteraction::onUseBlock);
	}

	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hitResult) {
		if (!level.getBlockState(hitResult.getBlockPos()).is(Blocks.FLETCHING_TABLE) || player.isSpectator() || player.isSecondaryUseActive()) {
			return InteractionResult.PASS;
		}

		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}

		if (LiveVillagesNetworking.isBuildSitePreviewActive(serverPlayer) && !player.getItemInHand(hand).isEmpty()) {
			return InteractionResult.PASS;
		}

		player.openMenu(new SimpleMenuProvider(
			(syncId, inventory, menuPlayer) -> new FletchingTableMenu(syncId, inventory, ContainerLevelAccess.create(level, hitResult.getBlockPos())),
			CONTAINER_TITLE
		));
		return InteractionResult.SUCCESS_SERVER;
	}
}
