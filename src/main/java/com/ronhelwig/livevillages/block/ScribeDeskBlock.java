package com.ronhelwig.livevillages.block;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.MapCodec;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.ronhelwig.livevillages.sim.LiveVillagesSavedData;
import com.ronhelwig.livevillages.sim.SettlementBuildSiteType;
import com.ronhelwig.livevillages.sim.SettlementConstruction;
import com.ronhelwig.livevillages.sim.SettlementRecipeKnowledge;
import com.ronhelwig.livevillages.sim.SettlementState;
import com.ronhelwig.livevillages.sim.SettlementTiers;
import com.ronhelwig.livevillages.sim.SettlementVillagers;
import com.ronhelwig.livevillages.menu.ScribeDeskMenu;
import com.ronhelwig.livevillages.menu.ScribeDeskOpenData;
import com.ronhelwig.livevillages.menu.ScribeRecipeView;

public class ScribeDeskBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<ScribeDeskBlock> CODEC = simpleCodec(ScribeDeskBlock::new);
	private static final VoxelShape NORTH_SOUTH_SHAPE = Shapes.box(0.0D, 0.0D, -1.0D, 1.0D, 1.0D, 2.0D);
	private static final VoxelShape EAST_WEST_SHAPE = Shapes.box(-1.0D, 0.0D, 0.0D, 2.0D, 1.0D, 1.0D);

	public ScribeDeskBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return isNorthSouth(state) ? EAST_WEST_SHAPE : NORTH_SOUTH_SHAPE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return isNorthSouth(state) ? EAST_WEST_SHAPE : NORTH_SOUTH_SHAPE;
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);

		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}

		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(serverLevel.getServer());
		SettlementState settlement = SettlementConstruction.findWorkstationSettlement(serverLevel, pos).orElse(null);

		if (settlement == null) {
			if (placer instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendSystemMessage(Component.literal("No settlement found near this Scribe Desk."));
			}

			return;
		}

		Direction facing = state.getValue(FACING);
		Map<String, Integer> stock = new LinkedHashMap<>(settlement.stock());
		SettlementConstruction.WorkstationBuildResult buildResult = SettlementConstruction.tryStartScribeOfficeAtWorkstation(
			serverLevel,
			pos,
			facing,
			settlement.id(),
			stock,
			savedData.findBuildSite(settlement.id(), SettlementBuildSiteType.SCRIBE_OFFICE, pos)
		);

		if (buildResult.isStarted()) {
			savedData.putBuildSite(buildResult.buildSite());
			SettlementVillagers.ensureWorkforce(serverLevel, settlement);

			if (placer instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendSystemMessage(Component.literal("Scribe Office construction started around this Scribe Desk."));
			}
		} else if (buildResult.isResumed()) {
			savedData.putBuildSite(buildResult.buildSite());
			SettlementVillagers.ensureWorkforce(serverLevel, settlement);

			if (placer instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendSystemMessage(Component.literal("Scribe Office construction is already planned around this Scribe Desk."));
			}
		} else if (buildResult.isBlocked() && placer instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendSystemMessage(Component.literal("The settlement can't build a Scribe Office here."));
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
			LiveVillagesSavedData savedData = LiveVillagesSavedData.get(serverLevel.getServer());
			SettlementState settlement = SettlementConstruction.findWorkstationSettlement(serverLevel, pos).orElse(null);

			if (settlement == null) {
				serverPlayer.sendSystemMessage(Component.literal("No settlement found near this Scribe Desk."));
				return InteractionResult.SUCCESS_SERVER;
			}

			ScribeDeskOpenData openData = openData(
				serverLevel,
				serverPlayer,
				settlement.id(),
				savedData.ensureScribeStarterRecipes(settlement.id(), SettlementRecipeKnowledge.recipeIdsForTier(SettlementTiers.unlockedTier(settlement))),
				settlement.name(),
				settlement.tier(),
				pos
			);
			serverPlayer.openMenu(new ScribeDeskMenuProvider(Component.translatable("container.live-villages.scribe_desk"), openData));
			return InteractionResult.SUCCESS_SERVER;
		}

		return InteractionResult.PASS;
	}

	private static ScribeDeskOpenData openData(ServerLevel level, ServerPlayer player, String settlementId, List<String> knownRecipeIds, String settlementName, int settlementTier, BlockPos deskPos) {
		java.util.Set<String> knownRecipeIdSet = new java.util.LinkedHashSet<>(knownRecipeIds);
		List<ScribeRecipeView> recipes = knownRecipeIds.stream()
			.map(SettlementRecipeKnowledge::recipeKey)
			.flatMap(Optional::stream)
			.flatMap(key -> level.getServer().getRecipeManager().byKey(key).stream())
			.filter(holder -> !player.getRecipeBook().contains(holder.id()))
			.map(holder -> viewForRecipe(level, holder))
			.toList();

		List<ScribeRecipeView> playerRecipes = level.getServer().getRecipeManager().getRecipes().stream()
			.filter(holder -> !holder.value().isSpecial())
			.filter(holder -> player.getRecipeBook().contains(holder.id()))
			.filter(holder -> !knownRecipeIdSet.contains(holder.id().identifier().toString()))
			.sorted(java.util.Comparator.comparing(holder -> holder.id().identifier().toString()))
			.map(holder -> viewForRecipe(level, holder))
			.toList();

		return new ScribeDeskOpenData(deskPos.immutable(), settlementId, settlementName, settlementTier, recipes, playerRecipes);
	}

	private static ScribeRecipeView viewForRecipe(ServerLevel level, RecipeHolder<?> holder) {
		String recipeId = holder.id().identifier().toString();
		String outputItemId = outputItemIdForRecipe(level, holder);
		SettlementRecipeKnowledge.ScribeRecipePrice price = SettlementRecipeKnowledge.scribeRecipePrice(recipeId, outputItemId);
		String label = humanizeRecipeId(recipeId);

		return new ScribeRecipeView(recipeId, label, outputItemId, price.paymentItemId(), price.paymentCount());
	}

	private static String outputItemIdForRecipe(ServerLevel level, RecipeHolder<?> holder) {
		try {
			ItemStack result = holder.value().display().stream()
				.map(display -> display.result().resolveForFirstStack(SlotDisplayContext.fromLevel(level)))
				.filter(stack -> !stack.isEmpty())
				.findFirst()
				.orElse(ItemStack.EMPTY);

			if (!result.isEmpty()) {
				return BuiltInRegistries.ITEM.getKey(result.getItem()).toString();
			}
		} catch (Exception ignored) {
			// Some datapack or modded recipe displays may need context this first pass does not provide.
		}

		return outputItemIdForRecipe(holder.id().identifier().toString());
	}

	private static String outputItemIdForRecipe(String recipeId) {
		Identifier identifier = Identifier.tryParse(recipeId);
		String path = identifier == null ? recipeId : identifier.getPath();
		return "minecraft:" + switch (path) {
			case "oak_boat" -> "oak_boat";
			case "oak_chest_boat" -> "oak_chest_boat";
			case "chest" -> "chest";
			case "crafting_table" -> "crafting_table";
			case "furnace" -> "furnace";
			case "bread" -> "bread";
			case "book" -> "book";
			case "bookshelf" -> "bookshelf";
			case "paper" -> "paper";
			case "map" -> "map";
			case "compass" -> "compass";
			case "ladder" -> "ladder";
			case "torch" -> "torch";
			case "campfire" -> "campfire";
			case "lectern" -> "lectern";
			case "cartography_table" -> "cartography_table";
			case "fletching_table" -> "fletching_table";
			case "loom" -> "loom";
			case "cobblestone_stairs" -> "cobblestone_stairs";
			default -> "book";
		};
	}

	private static String humanizeRecipeId(String recipeId) {
		Identifier identifier = Identifier.tryParse(recipeId);
		String path = identifier == null ? recipeId : identifier.getPath();
		String[] parts = path.replace('/', '_').split("_");
		StringBuilder builder = new StringBuilder();

		for (String part : parts) {
			if (part.isBlank()) {
				continue;
			}

			if (!builder.isEmpty()) {
				builder.append(' ');
			}

			builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
		}

		return builder.isEmpty() ? recipeId : builder.toString();
	}

	private record ScribeDeskMenuProvider(Component getDisplayName, ScribeDeskOpenData openData) implements ExtendedMenuProvider<ScribeDeskOpenData> {
		@Override
		public ScribeDeskOpenData getScreenOpeningData(ServerPlayer player) {
			return openData;
		}

		@Override
		public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
			return new ScribeDeskMenu(syncId, inventory, openData);
		}
	}

	private static boolean isNorthSouth(BlockState state) {
		Direction facing = state.getValue(FACING);
		return facing == Direction.NORTH || facing == Direction.SOUTH;
	}
}
