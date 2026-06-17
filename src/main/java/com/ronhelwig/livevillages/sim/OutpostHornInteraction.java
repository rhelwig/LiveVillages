package com.ronhelwig.livevillages.sim;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public final class OutpostHornInteraction {
	private OutpostHornInteraction() {
	}

	public static void register() {
		UseItemCallback.EVENT.register(OutpostHornInteraction::onUseItem);
	}

	private static InteractionResult onUseItem(Player player, Level level, InteractionHand hand) {
		if (player.isSpectator() || !player.getItemInHand(hand).is(Items.GOAT_HORN)) {
			return InteractionResult.PASS;
		}

		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}

		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(level.getServer());
		OutpostRaids.RaidLaunchResult result = OutpostRaids.requestRaidFromPlayer(
			serverPlayer,
			savedData,
			OutpostRaids.currentRaidTick(level.getServer())
		);

		if (!result.message().isBlank()) {
			serverPlayer.sendSystemMessage(Component.literal(result.message()), true);
		}

		return InteractionResult.PASS;
	}
}
