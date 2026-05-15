package com.ronhelwig.livevillages.sim;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.LevelResource;

import com.ronhelwig.livevillages.content.LiveVillagesBlocks;

public final class StructureBlueprintCapture {
	private static final int MAX_CAPTURE_DISTANCE_BLOCKS = 24;
	private static final int MAX_CAPTURE_RADIUS_BLOCKS = 16;
	private static final int MAX_CAPTURE_BLOCKS = 768;
	private static final int MAX_CAPTURE_VOLUME = 4096;
	private static final String CAPTURE_SYMBOLS = "abcdefghijklmnopqrstuvwxyz0123456789!$%&*+-=?@^~";
	private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private StructureBlueprintCapture() {
	}

	public static void exportLookedAtStructure(ServerPlayer player, Optional<BlockPos> targetPos) {
		if (targetPos.isEmpty()) {
			player.sendSystemMessage(Component.literal("No block targeted for structure capture."));
			return;
		}

		ServerLevel level = (ServerLevel) player.level();
		BlockPos seedPos = targetPos.get();

		if (!level.isLoaded(seedPos)) {
			player.sendSystemMessage(Component.literal("That structure is not loaded."));
			return;
		}

		if (player.blockPosition().distSqr(seedPos) > (double) MAX_CAPTURE_DISTANCE_BLOCKS * MAX_CAPTURE_DISTANCE_BLOCKS) {
			player.sendSystemMessage(Component.literal("Move closer to the structure before capturing it."));
			return;
		}

		BlockState seedState = level.getBlockState(seedPos);

		if (seedState.isAir()) {
			player.sendSystemMessage(Component.literal("Target a solid structure block to capture a blueprint."));
			return;
		}

		Direction captureFacing = player.getDirection().getOpposite();
		Optional<CapturedStructure> captured = capture(level, seedPos, captureFacing);

		if (captured.isEmpty()) {
			player.sendSystemMessage(Component.literal("Could not find a bounded exposed structure around that block."));
			return;
		}

		try {
			Path exportFile = writeCapture(level, player, captured.get());
			Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT);
			String relativePath = worldRoot.relativize(exportFile).toString().replace('\\', '/');
			player.sendSystemMessage(Component.literal(
				"Captured " + captured.get().blockCount() + " blocks to " + relativePath
			));
		} catch (IOException exception) {
			player.sendSystemMessage(Component.literal("Failed to write structure capture: " + exception.getMessage()));
		}
	}

	private static Optional<CapturedStructure> capture(ServerLevel level, BlockPos seedPos, Direction captureFacing) {
		Set<BlockPos> capturedPositions = connectedExposedStructure(level, seedPos);

		if (capturedPositions.isEmpty()) {
			return Optional.empty();
		}

		Direction rightDirection = captureFacing.getCounterClockWise();
		Map<RelativePos, BlockState> statesByRelativePos = new HashMap<>();
		Map<String, Character> symbolByMaterialKey = new HashMap<>();
		LinkedHashMap<Character, LegendEntry> legendEntries = new LinkedHashMap<>();
		int minRight = Integer.MAX_VALUE;
		int maxRight = Integer.MIN_VALUE;
		int minForward = Integer.MAX_VALUE;
		int maxForward = Integer.MIN_VALUE;
		int minY = Integer.MAX_VALUE;

		for (BlockPos pos : capturedPositions) {
			minY = Math.min(minY, pos.getY());
		}

		for (BlockPos pos : capturedPositions) {
			int rawRight = pos.getX() * rightDirection.getStepX() + pos.getZ() * rightDirection.getStepZ();
			int rawForward = pos.getX() * captureFacing.getStepX() + pos.getZ() * captureFacing.getStepZ();
			minRight = Math.min(minRight, rawRight);
			maxRight = Math.max(maxRight, rawRight);
			minForward = Math.min(minForward, rawForward);
			maxForward = Math.max(maxForward, rawForward);
			statesByRelativePos.put(
				new RelativePos(rawRight, rawForward, pos.getY() - minY),
				level.getBlockState(pos)
			);
		}

		int width = maxRight - minRight + 1;
		int depth = maxForward - minForward + 1;
		int height = capturedPositions.stream().mapToInt(BlockPos::getY).max().orElse(minY) - minY + 1;

		if (width * depth * height > MAX_CAPTURE_VOLUME) {
			return Optional.empty();
		}

		List<BlockLayer> layers = new ArrayList<>();
		int nextSymbolIndex = 0;

		for (int up = 0; up < height; up++) {
			List<String> symbolRows = new ArrayList<>();
			List<String> orientationRows = new ArrayList<>();

			for (int forward = minForward; forward <= maxForward; forward++) {
				StringBuilder symbolRow = new StringBuilder();
				StringBuilder orientationRow = new StringBuilder();

				for (int right = minRight; right <= maxRight; right++) {
					BlockState state = statesByRelativePos.get(new RelativePos(right, forward, up));

					if (state == null) {
						symbolRow.append('A');
						orientationRow.append('.');
						continue;
					}

					CapturedMaterial material = classifyMaterial(state);
					Character symbol = symbolByMaterialKey.get(material.key());

					if (symbol == null) {
						if (material.preferredSymbol() != null) {
							symbol = material.preferredSymbol();
						} else {
							symbol = nextAvailableFallbackSymbol(legendEntries.keySet(), nextSymbolIndex);

							if (symbol == null) {
								return Optional.empty();
							}

							nextSymbolIndex = CAPTURE_SYMBOLS.indexOf(symbol.charValue()) + 1;
						}

						symbolByMaterialKey.put(material.key(), symbol);
						legendEntries.put(symbol, new LegendEntry(material.legendLabel(), material.internalLegend(), new LinkedHashSet<>(), new LinkedHashSet<>()));
					}

					LegendEntry legendEntry = legendEntries.get(symbol);
					legendEntry.blockIds().add(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
					legendEntry.exactStates().add(serializeBlockState(state));
					symbolRow.append(symbol.charValue());
					orientationRow.append(orientationChar(state, captureFacing));
				}

				symbolRows.add(symbolRow.toString());
				orientationRows.add(orientationRow.toString());
			}

			layers.add(new BlockLayer(symbolRows, orientationRows));
		}

		return Optional.of(new CapturedStructure(captureFacing, width, depth, height, capturedPositions.size(), layers, legendEntries));
	}

	private static Set<BlockPos> connectedExposedStructure(ServerLevel level, BlockPos seedPos) {
		Set<BlockPos> visited = new HashSet<>();
		Set<BlockPos> captured = new LinkedHashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(seedPos.immutable());
		visited.add(seedPos.immutable());

		while (!queue.isEmpty()) {
			BlockPos currentPos = queue.removeFirst();

			if (!withinCaptureBounds(seedPos, currentPos)) {
				continue;
			}

			BlockState currentState = level.getBlockState(currentPos);

			if (!isStructureBlock(currentState)) {
				continue;
			}

			captured.add(currentPos.immutable());

			if (captured.size() > MAX_CAPTURE_BLOCKS) {
				return Set.of();
			}

			for (Direction direction : Direction.values()) {
				BlockPos nextPos = currentPos.relative(direction);

				if (visited.add(nextPos.immutable())) {
					queue.addLast(nextPos.immutable());
				}
			}
		}

		return captured;
	}

	private static boolean withinCaptureBounds(BlockPos seedPos, BlockPos candidatePos) {
		return Math.abs(candidatePos.getX() - seedPos.getX()) <= MAX_CAPTURE_RADIUS_BLOCKS
			&& Math.abs(candidatePos.getY() - seedPos.getY()) <= MAX_CAPTURE_RADIUS_BLOCKS
			&& Math.abs(candidatePos.getZ() - seedPos.getZ()) <= MAX_CAPTURE_RADIUS_BLOCKS;
	}

	private static boolean isExposedStructureBlock(ServerLevel level, BlockPos pos, BlockState state) {
		return isStructureBlock(state);
	}

	private static boolean isStructureBlock(BlockState state) {
		return !state.isAir()
			&& state.getFluidState().isEmpty()
			&& !isEphemeralNaturalBlock(state);
	}

	private static boolean isEphemeralNaturalBlock(BlockState state) {
		return state.is(Blocks.SHORT_GRASS)
			|| state.is(Blocks.TALL_GRASS)
			|| state.is(Blocks.FERN)
			|| state.is(Blocks.LARGE_FERN)
			|| state.is(Blocks.GRASS_BLOCK)
			|| state.is(Blocks.DIRT)
			|| state.is(Blocks.COARSE_DIRT)
			|| state.is(Blocks.PODZOL)
			|| state.is(Blocks.MYCELIUM)
			|| state.is(Blocks.ROOTED_DIRT)
			|| state.is(Blocks.MOSS_BLOCK)
			|| state.is(Blocks.MOSS_CARPET)
			|| state.is(Blocks.DIRT_PATH)
			|| state.is(BlockTags.FLOWERS)
			|| state.is(BlockTags.LEAVES)
			|| state.is(Blocks.VINE)
			|| state.is(Blocks.CAVE_VINES)
			|| state.is(Blocks.CAVE_VINES_PLANT)
			|| state.is(Blocks.TWISTING_VINES)
			|| state.is(Blocks.TWISTING_VINES_PLANT)
			|| state.is(Blocks.WEEPING_VINES)
			|| state.is(Blocks.WEEPING_VINES_PLANT)
			|| state.is(Blocks.SNOW)
			|| state.is(Blocks.WATER)
			|| state.is(Blocks.LAVA);
	}

	private static Path writeCapture(ServerLevel level, ServerPlayer player, CapturedStructure captured) throws IOException {
		Path exportDir = level.getServer().getWorldPath(LevelResource.ROOT).resolve("livevillages_exports");
		Files.createDirectories(exportDir);
		String playerLabel = sanitizeLabel(player.getScoreboardName());
		String facingLabel = captured.captureFacing().getSerializedName();
		String timestamp = FILE_TIMESTAMP.format(LocalDateTime.now());
		Path exportPath = exportDir.resolve("structure-" + timestamp + "-" + playerLabel + "-" + facingLabel + ".txt");
		Files.writeString(exportPath, renderCapture(captured, player, level), StandardCharsets.UTF_8);
		return exportPath;
	}

	private static String renderCapture(CapturedStructure captured, ServerPlayer player, ServerLevel level) {
		StringBuilder builder = new StringBuilder();
		builder.append("# Live Villages structure capture\n");
		builder.append("# Player: ").append(player.getScoreboardName()).append('\n');
		builder.append("# Dimension: ").append(level.dimension().identifier()).append('\n');
		builder.append("# Captured: ").append(LocalDateTime.now()).append('\n');
		builder.append("# Front facing: ").append(captured.captureFacing().getSerializedName()).append('\n');
		builder.append("# Size: width=").append(captured.width())
			.append(", depth=").append(captured.depth())
			.append(", height=").append(captured.height())
			.append(", blocks=").append(captured.blockCount())
			.append('\n');
		builder.append("# Rows are ordered back-to-front relative to the captured front facing.\n");
		builder.append("# Legend prefers existing Live Villages blueprint families before introducing new symbols.\n");
		builder.append("# Uppercase symbols are existing Live Villages blueprint vocabulary. Lowercase symbols are capture-only fallback symbols.\n");
		builder.append("# Orientation rows use: . = none, F = front, B = back, R = right, L = left.\n");
		builder.append("# Lowercase orientation letters on stairs/trapdoors mean the top half. For walls, uppercase = low side connection and lowercase = tall side connection.\n");
		builder.append("# Wall-only orientation: U = center post with no horizontal side orientation.\n");
		builder.append("# Layers are intended for later curation.\n\n");
		builder.append("new StructureBlueprint(\n");
		builder.append("\t0,\n");
		builder.append("\t0,\n");
		builder.append("\t0,\n");
		builder.append("\t").append(captured.height()).append(",\n");
		builder.append("\tnew String[][] {\n");

		for (int layerIndex = 0; layerIndex < captured.layers().size(); layerIndex++) {
			BlockLayer layer = captured.layers().get(layerIndex);
			builder.append("\t\t{\n");

			for (int rowIndex = 0; rowIndex < layer.symbolRows().size(); rowIndex++) {
				builder.append("\t\t\t\"").append(layer.symbolRows().get(rowIndex)).append("\"");
				builder.append(rowIndex + 1 < layer.symbolRows().size() ? ",\n" : "\n");
			}

			builder.append(layerIndex + 1 < captured.layers().size() ? "\t\t},\n" : "\t\t}\n");
		}

		builder.append("\t},\n");
		builder.append("\tnew String[][] {\n");

		for (int layerIndex = 0; layerIndex < captured.layers().size(); layerIndex++) {
			BlockLayer layer = captured.layers().get(layerIndex);
			builder.append("\t\t{\n");

			for (int rowIndex = 0; rowIndex < layer.orientationRows().size(); rowIndex++) {
				builder.append("\t\t\t\"").append(layer.orientationRows().get(rowIndex)).append("\"");
				builder.append(rowIndex + 1 < layer.orientationRows().size() ? ",\n" : "\n");
			}

			builder.append(layerIndex + 1 < captured.layers().size() ? "\t\t},\n" : "\t\t}\n");
		}

		builder.append("\t}\n");
		builder.append(");\n\n");
		builder.append("Legend:\n");
		builder.append("A = air\n");

		for (Map.Entry<Character, LegendEntry> entry : captured.legendEntries().entrySet()) {
			builder.append(entry.getKey()).append(" = ").append(renderLegendEntry(entry.getValue())).append('\n');
		}

		return builder.toString();
	}

	private static String renderLegendEntry(LegendEntry entry) {
		if (!entry.internalLegend() && entry.exactStates().size() == 1 && entry.blockIds().size() <= 1) {
			return entry.exactStates().iterator().next();
		}

		StringBuilder builder = new StringBuilder(entry.legendLabel());
		builder.append("; ");
		builder.append(entry.blockIds().size() == 1 ? "block used: " : "blocks used: ");
		builder.append(String.join(", ", entry.blockIds()));

		if (entry.exactStates().size() > 1) {
			builder.append("; states seen: ");
			builder.append(String.join(", ", entry.exactStates()));
		}

		return builder.toString();
	}

	private static Character nextAvailableFallbackSymbol(Set<Character> usedSymbols, int nextSymbolIndex) {
		for (int index = Math.max(0, nextSymbolIndex); index < CAPTURE_SYMBOLS.length(); index++) {
			char candidate = CAPTURE_SYMBOLS.charAt(index);

			if (!usedSymbols.contains(candidate)) {
				return candidate;
			}
		}

		return null;
	}

	private static char orientationChar(BlockState state, Direction captureFacing) {
		Character wallOrientation = wallOrientationChar(state, captureFacing);

		if (wallOrientation != null) {
			return wallOrientation.charValue();
		}

		Direction explicitFacing = facingForOrientation(state, captureFacing);

		if (explicitFacing == null) {
			return '.';
		}

		char orientation = relativeFacingChar(explicitFacing, captureFacing);

		if (orientation == '.') {
			return orientation;
		}

		if (state.getBlock() instanceof StairBlock) {
			return state.hasProperty(StairBlock.HALF) && state.getValue(StairBlock.HALF) == Half.TOP
				? Character.toLowerCase(orientation)
				: orientation;
		}

		if (state.getBlock() instanceof TrapDoorBlock) {
			return state.hasProperty(TrapDoorBlock.HALF) && state.getValue(TrapDoorBlock.HALF) == Half.TOP
				? Character.toLowerCase(orientation)
				: orientation;
		}

		return orientation;
	}

	private static Character wallOrientationChar(BlockState state, Direction captureFacing) {
		if (!state.is(BlockTags.WALLS)) {
			return null;
		}

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			Property<?> property = wallProperty(direction);

			if (property == null || !state.hasProperty(property)) {
				continue;
			}

			String valueName = wallValueName(state, property);

			if ("none".equals(valueName)) {
				continue;
			}

			char orientation = relativeFacingChar(direction, captureFacing);
			return "tall".equals(valueName) ? Character.toLowerCase(orientation) : orientation;
		}

		return state.getValue(BlockStateProperties.UP) ? 'U' : '.';
	}

	private static Property<?> wallProperty(Direction direction) {
		return switch (direction) {
			case NORTH -> BlockStateProperties.NORTH_WALL;
			case SOUTH -> BlockStateProperties.SOUTH_WALL;
			case EAST -> BlockStateProperties.EAST_WALL;
			case WEST -> BlockStateProperties.WEST_WALL;
			default -> null;
		};
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static String wallValueName(BlockState state, Property<?> property) {
		return ((Property) property).getName(state.getValue((Property) property));
	}

	private static Direction facingForOrientation(BlockState state, Direction captureFacing) {
		if (state.hasProperty(HorizontalDirectionalBlock.FACING)) {
			return state.getValue(HorizontalDirectionalBlock.FACING);
		}

		if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
			return state.getValue(BlockStateProperties.HORIZONTAL_FACING);
		}

		if (state.hasProperty(BlockStateProperties.FACING)) {
			Direction facing = state.getValue(BlockStateProperties.FACING);
			return facing.getAxis().isHorizontal() ? facing : null;
		}

		if (state.hasProperty(RotatedPillarBlock.AXIS)) {
			return switch (state.getValue(RotatedPillarBlock.AXIS)) {
				case X -> captureFacing.getClockWise();
				case Z -> captureFacing;
				case Y -> null;
			};
		}

		return null;
	}

	private static char relativeFacingChar(Direction explicitFacing, Direction captureFacing) {
		if (explicitFacing == captureFacing) {
			return 'F';
		}

		if (explicitFacing == captureFacing.getOpposite()) {
			return 'B';
		}

		if (explicitFacing == captureFacing.getClockWise()) {
			return 'R';
		}

		if (explicitFacing == captureFacing.getCounterClockWise()) {
			return 'L';
		}

		return '.';
	}

	private static CapturedMaterial classifyMaterial(BlockState state) {
		if (state.is(BlockTags.LOGS)) {
			return CapturedMaterial.internal("logs", 'L');
		}

		if (state.is(BlockTags.PLANKS)) {
			return CapturedMaterial.internal("planks", 'P');
		}

		if (state.getBlock() instanceof StairBlock) {
			return CapturedMaterial.internal("stairs", 'S');
		}

		if (state.is(BlockTags.SLABS)) {
			return CapturedMaterial.internal("slabs", 'B');
		}

		if (state.is(BlockTags.WALLS)) {
			return CapturedMaterial.grouped(
				"wall:" + BuiltInRegistries.BLOCK.getKey(state.getBlock()),
				BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()
			);
		}

		if (isCobblestoneFamily(state)) {
			return CapturedMaterial.internal("cobblestone-family", 'M');
		}

		if (state.is(BlockTags.WOODEN_DOORS)) {
			return CapturedMaterial.internal("doors", 'D');
		}

		if (state.is(BlockTags.WOODEN_FENCES)) {
			return CapturedMaterial.internal("fences", 'F');
		}

		if (state.is(BlockTags.FENCE_GATES)) {
			return CapturedMaterial.internal("fence_gates", 'G');
		}

		if (state.getBlock() instanceof ChestBlock) {
			return CapturedMaterial.internal("chests", 'H');
		}

		if (state.getBlock() instanceof CampfireBlock) {
			return CapturedMaterial.internal("campfires", 'K');
		}

		if (state.getBlock() instanceof LanternBlock) {
			return CapturedMaterial.internal("lanterns", 'N');
		}

		if (state.getBlock() instanceof LadderBlock) {
			return CapturedMaterial.internal("ladders", 'R');
		}

		if (state.getBlock() instanceof TrapDoorBlock) {
			return CapturedMaterial.grouped(
				"trapdoor:" + BuiltInRegistries.BLOCK.getKey(state.getBlock()),
				BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()
			);
		}

		if (state.getBlock() instanceof TorchBlock || state.getBlock() instanceof WallTorchBlock) {
			return CapturedMaterial.internal("torches", 'T');
		}

		if (state.is(Blocks.GLASS) || state.is(Blocks.GLASS_PANE)) {
			return CapturedMaterial.internal("glass", 'V');
		}

		if (isKnownWorkstation(state)) {
			return CapturedMaterial.internal("workstations", 'W');
		}

		return CapturedMaterial.exact(serializeBlockState(state));
	}

	private static boolean isCobblestoneFamily(BlockState state) {
		return state.is(Blocks.COBBLESTONE)
			|| state.is(Blocks.MOSSY_COBBLESTONE)
			|| state.is(Blocks.COBBLESTONE_STAIRS)
			|| state.is(Blocks.MOSSY_COBBLESTONE_STAIRS)
			|| state.is(Blocks.COBBLESTONE_SLAB)
			|| state.is(Blocks.MOSSY_COBBLESTONE_SLAB)
			|| state.is(Blocks.COBBLED_DEEPSLATE)
			|| state.is(Blocks.COBBLED_DEEPSLATE_STAIRS)
			|| state.is(Blocks.COBBLED_DEEPSLATE_SLAB);
	}

	private static boolean isKnownWorkstation(BlockState state) {
		return state.is(LiveVillagesBlocks.BAKERS_COUNTER)
			|| state.is(LiveVillagesBlocks.CARPENTER_BENCH)
			|| state.is(LiveVillagesBlocks.FORESTER_TABLE)
			|| state.is(LiveVillagesBlocks.MINER_WORKSTATION)
			|| state.is(LiveVillagesBlocks.SURVEYOR_TABLE)
			|| state.is(LiveVillagesBlocks.TRADE_BOARD)
			|| state.is(LiveVillagesBlocks.PORTMASTER_ANCHOR)
			|| state.is(Blocks.CARTOGRAPHY_TABLE)
			|| state.is(Blocks.FLETCHING_TABLE)
			|| state.is(Blocks.SMOKER);
	}

	private static String serializeBlockState(BlockState state) {
		String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();

		if (state.getProperties().isEmpty()) {
			return blockId;
		}

		List<String> properties = new ArrayList<>();

		for (Property<?> property : state.getProperties()) {
			properties.add(property.getName() + "=" + propertyValueName(state, property));
		}

		properties.sort(Comparator.naturalOrder());
		return blockId + "[" + String.join(",", properties) + "]";
	}

	private static <T extends Comparable<T>> String propertyValueName(BlockState state, Property<T> property) {
		return property.getName(state.getValue(property));
	}

	private static String sanitizeLabel(String rawLabel) {
		StringBuilder builder = new StringBuilder();

		for (int index = 0; index < rawLabel.length(); index++) {
			char c = rawLabel.charAt(index);

			if ((c >= 'a' && c <= 'z')
				|| (c >= 'A' && c <= 'Z')
				|| (c >= '0' && c <= '9')
				|| c == '-'
				|| c == '_') {
				builder.append(c);
			} else {
				builder.append('_');
			}
		}

		return builder.isEmpty() ? "player" : builder.toString();
	}

	private record RelativePos(int right, int forward, int up) {
	}

	private record BlockLayer(List<String> symbolRows, List<String> orientationRows) {
		private BlockLayer {
			symbolRows = List.copyOf(symbolRows);
			orientationRows = List.copyOf(orientationRows);
		}
	}

	private record CapturedStructure(
		Direction captureFacing,
		int width,
		int depth,
		int height,
		int blockCount,
		List<BlockLayer> layers,
		LinkedHashMap<Character, LegendEntry> legendEntries
	) {
		private CapturedStructure {
			layers = List.copyOf(layers);
			legendEntries = new LinkedHashMap<>(legendEntries);
		}
	}

	private record CapturedMaterial(String key, String legendLabel, Character preferredSymbol, boolean internalLegend) {
		private static CapturedMaterial internal(String legendLabel, char preferredSymbol) {
			return new CapturedMaterial("internal:" + preferredSymbol, legendLabel, preferredSymbol, true);
		}

		private static CapturedMaterial grouped(String key, String legendLabel) {
			return new CapturedMaterial(key, legendLabel, null, false);
		}

		private static CapturedMaterial exact(String exactState) {
			return new CapturedMaterial("exact:" + exactState, exactState, null, false);
		}
	}

	private record LegendEntry(
		String legendLabel,
		boolean internalLegend,
		LinkedHashSet<String> blockIds,
		LinkedHashSet<String> exactStates
	) {
	}
}
