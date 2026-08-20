package com.ronhelwig.livevillages.mixin;

import com.mojang.brigadier.ParseResults;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import com.ronhelwig.livevillages.command.SettlementTeleportCommands;

@Mixin(Commands.class)
public abstract class CommandsMixin {
	@Inject(method = "performPrefixedCommand", at = @At("HEAD"), cancellable = true)
	private void livevillages$resolveSettlementTeleport(CommandSourceStack source, String command, CallbackInfo callback) {
		var rewritten = SettlementTeleportCommands.rewriteSettlementTeleport(source, command);
		if (rewritten.isEmpty()) {
			return;
		}

		callback.cancel();
		((Commands) (Object) this).performPrefixedCommand(source, rewritten.get());
	}

	@Inject(method = "performCommand", at = @At("HEAD"), cancellable = true)
	private void livevillages$resolveParsedSettlementTeleport(
		ParseResults<CommandSourceStack> parseResults,
		String command,
		CallbackInfo callback
	) {
		CommandSourceStack source = parseResults.getContext().getSource();
		var rewritten = SettlementTeleportCommands.rewriteSettlementTeleport(source, command);
		if (rewritten.isEmpty()) {
			return;
		}

		callback.cancel();
		((Commands) (Object) this).performPrefixedCommand(source, rewritten.get());
	}
}
