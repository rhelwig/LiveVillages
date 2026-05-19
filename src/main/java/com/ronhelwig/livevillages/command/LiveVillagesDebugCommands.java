package com.ronhelwig.livevillages.command;

import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import com.ronhelwig.livevillages.menu.TradeBoardGoodsView;
import com.ronhelwig.livevillages.menu.TradeBoardLogic;
import com.ronhelwig.livevillages.menu.TradeBoardProjectView;
import com.ronhelwig.livevillages.menu.TradeBoardRoleView;
import com.ronhelwig.livevillages.menu.TradeBoardSettlementView;
import com.ronhelwig.livevillages.sim.LiveVillagesSavedData;
import com.ronhelwig.livevillages.sim.SettlementState;
import com.ronhelwig.livevillages.sim.SettlementVillagers;
import com.ronhelwig.livevillages.sim.SettlementKind;
import com.ronhelwig.livevillages.sim.VillageAutodetector;

public final class LiveVillagesDebugCommands {
	private LiveVillagesDebugCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register(LiveVillagesDebugCommands::registerCommands);
	}

	private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection environment) {
		dispatcher.register(Commands.literal("livevillages")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("settlements")
				.executes(LiveVillagesDebugCommands::listSettlements)
				.then(Commands.literal("list")
					.executes(LiveVillagesDebugCommands::listSettlements))
				.then(Commands.literal("inspect")
					.executes(LiveVillagesDebugCommands::inspectNearestSettlement))
				.then(Commands.literal("rescan")
					.executes(context -> rescanSettlements(context, 8))
					.then(Commands.argument("radiusChunks", IntegerArgumentType.integer(0, 256))
						.executes(context -> rescanSettlements(context, IntegerArgumentType.getInteger(context, "radiusChunks")))))
				.then(Commands.literal("validate")
					.executes(LiveVillagesDebugCommands::validateSettlements))));
	}

	private static int listSettlements(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(source.getServer());
		List<SettlementState> settlements = savedData.getSettlements().stream()
			.sorted(Comparator
				.comparing((SettlementState settlement) -> settlement.dimension().identifier().toString())
				.thenComparing(SettlementState::name)
				.thenComparing(SettlementState::id))
			.toList();

		if (settlements.isEmpty()) {
			source.sendFailure(Component.literal("No settlements have been detected yet."));
			return 0;
		}

		source.sendSuccess(() -> Component.literal("Detected settlements: " + settlements.size()), false);

		for (SettlementState settlement : settlements) {
			String line = "%s [%s] @ %d, %d, %d | kind=%s | housing=%d"
				.formatted(
					settlement.name(),
					settlement.dimension().identifier(),
					settlement.center().getX(),
					settlement.center().getY(),
					settlement.center().getZ(),
					settlement.kind().getSerializedName(),
					settlement.housingCapacity()
				);
			source.sendSystemMessage(Component.literal(line));
		}

		return settlements.size();
	}

	private static int inspectNearestSettlement(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(source.getServer());
		BlockPos position = BlockPos.containing(source.getPosition());
		var nearestSettlement = savedData.findNearestSettlement(source.getLevel().dimension(), position, Double.POSITIVE_INFINITY, settlement -> true);

		if (nearestSettlement.isEmpty()) {
			source.sendFailure(Component.literal("No settlements are known in this dimension."));
			return 0;
		}

		SettlementState settlement = nearestSettlement.get();
		List<com.ronhelwig.livevillages.sim.RouteState> routes = savedData.getRoutesForSettlement(settlement.id());
		List<com.ronhelwig.livevillages.sim.SettlementBuildSite> buildSites = savedData.getBuildSitesForSettlement(settlement.id());
		TradeBoardSettlementView view = TradeBoardLogic.createSettlementView(
			source.getLevel(),
			settlement,
			routes,
			settlementId -> savedData.getSettlement(settlementId).map(SettlementState::name).orElse("Unknown"),
			5,
			3,
			3,
			SettlementVillagers.nearbyProfessionPopulation(source.getLevel(), settlement),
			TradeBoardLogic.constructionTradeDemand(buildSites),
			buildSites
		);
		source.sendSystemMessage(Component.literal(
			"Settlement: %s [%s] @ %d, %d, %d"
				.formatted(
					settlement.name(),
					settlement.kind().getSerializedName(),
					settlement.center().getX(),
					settlement.center().getY(),
					settlement.center().getZ()
				)
		));
		source.sendSystemMessage(Component.literal(
			"Population=%d Housing=%d Comfort=%s Security=%s Routes=%d Growth=%s Progress=%.2f"
				.formatted(
					settlement.totalPopulation(),
					settlement.housingCapacity(),
					percent(settlement.comfort()),
					percent(settlement.security()),
					routes.size(),
					view.growthSummary(),
					settlement.growthProgress()
				)
		));
		sendRoleSection(source, view.roleCounts());
		source.sendSystemMessage(Component.literal("Wealth: " + summarizeMap(settlement.wealth())));
		source.sendSystemMessage(Component.literal("Stock: " + summarizeMap(settlement.stock())));
		sendRouteSection(source, settlement, routes, savedData);
		sendGoodsSection(source, "Shortages", view.shortages(), true);
		sendGoodsSection(source, "Surpluses", view.surpluses(), true);
		sendProjectSection(source, view.projects());
		return 1;
	}

	private static int rescanSettlements(CommandContext<CommandSourceStack> context, int radiusChunks) {
		CommandSourceStack source = context.getSource();
		BlockPos origin = BlockPos.containing(source.getPosition());
		int scannedChunks = VillageAutodetector.rescanAround(source.getLevel(), origin, radiusChunks, source.getServer().getTickCount());
		int settlementCount = LiveVillagesSavedData.get(source.getServer()).settlementCount();

		source.sendSuccess(() -> Component.literal(
			"Rescanned " + scannedChunks + " chunks within " + radiusChunks + " chunks of your position. Total detected settlements: " + settlementCount
		), true);
		return settlementCount;
	}

	private static int validateSettlements(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(source.getServer());
		List<SettlementState> settlements = List.copyOf(savedData.getSettlements());
		List<String> removedNames = new ArrayList<>();

		for (SettlementState settlement : settlements) {
			if (settlement.kind() != SettlementKind.VILLAGE) {
				continue;
			}

			var level = source.getServer().getLevel(settlement.dimension());

			if (level == null || VillageAutodetector.isLikelyVillage(level, settlement.center())) {
				continue;
			}

			savedData.removeSettlement(settlement.id());
			removedNames.add(settlement.name());
		}

		if (removedNames.isEmpty()) {
			source.sendSuccess(() -> Component.literal("No invalid villages were found."), false);
			return 0;
		}

		source.sendSuccess(() -> Component.literal("Removed invalid villages: " + String.join(", ", removedNames)), true);
		return removedNames.size();
	}

	private static void sendGoodsSection(CommandSourceStack source, String title, List<TradeBoardGoodsView> entries, boolean includeTarget) {
		source.sendSystemMessage(Component.literal(title + ":"));

		if (entries.isEmpty()) {
			source.sendSystemMessage(Component.literal("  None"));
			return;
		}

		for (TradeBoardGoodsView entry : entries) {
			String line = includeTarget
				? "  %s %d / %d @ %d%%".formatted(entry.label(), entry.current(), entry.target(), entry.tradePricePercent())
				: "  %s %d".formatted(entry.label(), entry.current());
			source.sendSystemMessage(Component.literal(line));
		}
	}

	private static void sendProjectSection(CommandSourceStack source, List<TradeBoardProjectView> projects) {
		source.sendSystemMessage(Component.literal("Projects:"));

		if (projects.isEmpty()) {
			source.sendSystemMessage(Component.literal("  None"));
			return;
		}

		for (TradeBoardProjectView project : projects) {
			source.sendSystemMessage(Component.literal("  %s %d%%".formatted(project.label(), project.progressPercent())));
		}
	}

	private static void sendRoleSection(CommandSourceStack source, List<TradeBoardRoleView> roleCounts) {
		source.sendSystemMessage(Component.literal("Workforce:"));

		if (roleCounts.isEmpty()) {
			source.sendSystemMessage(Component.literal("  None"));
			return;
		}

		for (TradeBoardRoleView role : roleCounts) {
			source.sendSystemMessage(Component.literal("  %s %d".formatted(role.label(), role.count())));
		}
	}

	private static void sendRouteSection(
		CommandSourceStack source,
		SettlementState settlement,
		List<com.ronhelwig.livevillages.sim.RouteState> routes,
		LiveVillagesSavedData savedData
	) {
		source.sendSystemMessage(Component.literal("Routes:"));

		if (routes.isEmpty()) {
			source.sendSystemMessage(Component.literal("  None"));
			return;
		}

		for (com.ronhelwig.livevillages.sim.RouteState route : routes) {
			String otherSettlementId = route.fromSettlementId().equals(settlement.id()) ? route.toSettlementId() : route.fromSettlementId();
			String otherSettlementName = savedData.getSettlement(otherSettlementId)
				.map(SettlementState::name)
				.orElse(otherSettlementId);
			String transferSummary = route.lastTransferSummary().isBlank() ? "No recent trade" : route.lastTransferSummary();
			String line = "  %s via %s (%d blocks, q=%s, s=%s) | %s"
				.formatted(
					otherSettlementName,
					route.tier().getSerializedName(),
					route.distanceBlocks(),
					percent(route.quality()),
					percent(route.security()),
					transferSummary
				);
			source.sendSystemMessage(Component.literal(line));
		}
	}

	private static String summarizeMap(java.util.Map<String, Integer> values) {
		if (values.isEmpty()) {
			return "none";
		}

		return values.entrySet().stream()
			.sorted(java.util.Map.Entry.<String, Integer>comparingByValue().reversed())
			.limit(6)
			.map(entry -> entry.getKey() + "=" + entry.getValue())
			.collect(java.util.stream.Collectors.joining(", "));
	}

	private static String percent(double value) {
		return Math.round(value * 100.0D) + "%";
	}
}
