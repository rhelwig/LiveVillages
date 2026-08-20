package com.ronhelwig.livevillages.command;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import com.ronhelwig.livevillages.content.LiveVillagesBlocks;
import com.ronhelwig.livevillages.mixin.CommandNodeAccessor;
import com.ronhelwig.livevillages.sim.LiveVillagesSavedData;
import com.ronhelwig.livevillages.sim.SettlementState;
import com.ronhelwig.livevillages.sim.SettlementVillagers;

public final class SettlementTeleportCommands {
	private static final String INTERNAL_COMMAND = "livevillages_settlement_tp";
	private static final int CIVIC_ANCHOR_SEARCH_RADIUS = 48;
	private static final int CIVIC_ANCHOR_SEARCH_HALF_HEIGHT = 32;

	private SettlementTeleportCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register(SettlementTeleportCommands::registerCommands);
	}

	private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection environment) {
		var settlementArgument = Commands.argument("settlementName", StringArgumentType.greedyString())
			.suggests(SettlementTeleportCommands::suggestSettlements)
			.executes(SettlementTeleportCommands::teleportToNamedSettlement);

		dispatcher.register(Commands.literal("tp").then(settlementArgument));
		dispatcher.register(Commands.literal("teleport")
			.then(Commands.argument("settlementName", StringArgumentType.greedyString())
				.suggests(SettlementTeleportCommands::suggestSettlements)
				.executes(SettlementTeleportCommands::teleportToNamedSettlement)));
		allowCreativePlayersToSee(dispatcher.getRoot().getChild("tp"));
		allowCreativePlayersToSee(dispatcher.getRoot().getChild("teleport"));
		dispatcher.register(Commands.literal(INTERNAL_COMMAND)
			.requires(SettlementTeleportCommands::mayTeleportToSettlement)
			.then(Commands.argument("settlementName", StringArgumentType.greedyString())
				.suggests(SettlementTeleportCommands::suggestSettlements)
				.executes(SettlementTeleportCommands::teleportToNamedSettlement)));
	}

	@SuppressWarnings("unchecked")
	private static void allowCreativePlayersToSee(CommandNode<CommandSourceStack> node) {
		if (node == null) {
			return;
		}

		var vanillaRequirement = node.getRequirement();
		((CommandNodeAccessor<CommandSourceStack>) node).livevillages$setRequirement(source ->
			vanillaRequirement.test(source) || isCreativePlayer(source)
		);
	}

	public static Optional<String> rewriteSettlementTeleport(CommandSourceStack source, String command) {
		if (!mayTeleportToSettlement(source)) {
			return Optional.empty();
		}

		String unprefixed = Commands.trimOptionalPrefix(command);
		int separator = unprefixed.indexOf(' ');
		if (separator < 0) {
			return Optional.empty();
		}

		String root = unprefixed.substring(0, separator);
		if (!root.equals("tp") && !root.equals("teleport")) {
			return Optional.empty();
		}

		String requestedName = unprefixed.substring(separator + 1).trim();
		Optional<SettlementState> settlement = findSettlement(source, requestedName);
		return settlement.map(value -> INTERNAL_COMMAND + " " + value.name());
	}

	private static java.util.concurrent.CompletableFuture<Suggestions> suggestSettlements(
		CommandContext<CommandSourceStack> context,
		SuggestionsBuilder builder
	) {
		List<String> names = LiveVillagesSavedData.get(context.getSource().getServer()).getSettlements().stream()
			.map(SettlementState::name)
			.sorted(String.CASE_INSENSITIVE_ORDER)
			.toList();
		return SharedSuggestionProvider.suggest(names, builder);
	}

	private static int teleportToNamedSettlement(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		if (!mayTeleportToSettlement(source)) {
			source.sendFailure(Component.literal("Settlement teleporting requires cheats or Creative mode."));
			return 0;
		}

		String requestedName = StringArgumentType.getString(context, "settlementName");
		Optional<SettlementState> match = findSettlement(source, requestedName);
		if (match.isEmpty()) {
			source.sendFailure(Component.literal("No known settlement is named '" + requestedName.trim() + "'."));
			return 0;
		}

		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.literal("Only a player can teleport to a settlement."));
			return 0;
		}

		SettlementState settlement = match.get();
		ServerLevel destinationLevel = source.getServer().getLevel(settlement.dimension());
		if (destinationLevel == null) {
			source.sendFailure(Component.literal("The dimension containing " + settlement.name() + " is unavailable."));
			return 0;
		}

		destinationLevel.getChunkAt(settlement.center());
		BlockPos anchor = preferredCivicAnchor(destinationLevel, settlement).orElse(settlement.center());
		BlockPos destination = SettlementVillagers.findSpawnPos(destinationLevel, anchor, 8)
			.or(() -> SettlementVillagers.findSpawnPos(destinationLevel, settlement.center(), 16))
			.orElse(null);
		if (destination == null) {
			source.sendFailure(Component.literal("No safe open spot was found near " + settlement.name() + "."));
			return 0;
		}

		boolean teleported = player.teleportTo(
			destinationLevel,
			destination.getX() + 0.5D,
			destination.getY(),
			destination.getZ() + 0.5D,
			Set.<Relative>of(),
			player.getYRot(),
			player.getXRot(),
			true
		);
		if (!teleported) {
			source.sendFailure(Component.literal("Teleporting to " + settlement.name() + " failed."));
			return 0;
		}

		source.sendSuccess(() -> Component.literal("Teleported to " + settlement.name() + "."), false);
		return 1;
	}

	private static boolean mayTeleportToSettlement(CommandSourceStack source) {
		return Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(source)
			|| isCreativePlayer(source);
	}

	private static boolean isCreativePlayer(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		return player != null && player.gameMode.isCreative();
	}

	private static Optional<SettlementState> findSettlement(CommandSourceStack source, String requestedName) {
		String normalized = normalizeName(requestedName);
		if (normalized.isEmpty()) {
			return Optional.empty();
		}

		return LiveVillagesSavedData.get(source.getServer()).getSettlements().stream()
			.filter(settlement -> normalizeName(settlement.name()).equals(normalized))
			.sorted(Comparator.comparing(SettlementState::id))
			.findFirst();
	}

	static String normalizeName(String name) {
		return name == null ? "" : name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	private static Optional<BlockPos> preferredCivicAnchor(ServerLevel level, SettlementState settlement) {
		Optional<BlockPos> bell = nearestAnchor(level, settlement, Blocks.BELL, LiveVillagesBlocks.COPPER_BELL);
		return bell.isPresent() ? bell : nearestAnchor(level, settlement, LiveVillagesBlocks.TRADE_BOARD);
	}

	private static Optional<BlockPos> nearestAnchor(ServerLevel level, SettlementState settlement, Block... blocks) {
		BlockPos center = settlement.center();
		BlockPos nearest = null;
		double nearestDistance = Double.POSITIVE_INFINITY;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int minY = Math.max(level.getMinY(), center.getY() - CIVIC_ANCHOR_SEARCH_HALF_HEIGHT);
		int maxY = Math.min(level.getMaxY() - 1, center.getY() + CIVIC_ANCHOR_SEARCH_HALF_HEIGHT);
		int radiusSquared = CIVIC_ANCHOR_SEARCH_RADIUS * CIVIC_ANCHOR_SEARCH_RADIUS;

		for (int x = center.getX() - CIVIC_ANCHOR_SEARCH_RADIUS; x <= center.getX() + CIVIC_ANCHOR_SEARCH_RADIUS; x++) {
			for (int z = center.getZ() - CIVIC_ANCHOR_SEARCH_RADIUS; z <= center.getZ() + CIVIC_ANCHOR_SEARCH_RADIUS; z++) {
				if (center.distToCenterSqr(x + 0.5D, center.getY() + 0.5D, z + 0.5D) > radiusSquared) {
					continue;
				}
				cursor.set(x, center.getY(), z);
				if (!level.hasChunkAt(cursor)) {
					continue;
				}

				for (int y = minY; y <= maxY; y++) {
					cursor.set(x, y, z);
					Block stateBlock = level.getBlockState(cursor).getBlock();
					for (Block candidate : blocks) {
						if (stateBlock == candidate) {
							double distance = cursor.distSqr(center);
							if (distance < nearestDistance) {
								nearest = cursor.immutable();
								nearestDistance = distance;
							}
							break;
						}
					}
				}
			}
		}

		return Optional.ofNullable(nearest);
	}
}
