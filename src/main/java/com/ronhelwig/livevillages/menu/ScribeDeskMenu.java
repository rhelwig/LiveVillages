package com.ronhelwig.livevillages.menu;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;

import com.ronhelwig.livevillages.content.LiveVillagesMenus;
import com.ronhelwig.livevillages.content.LiveVillagesBlocks;
import com.ronhelwig.livevillages.sim.LiveVillagesSavedData;
import com.ronhelwig.livevillages.sim.SettlementConstruction;
import com.ronhelwig.livevillages.sim.SettlementPlayerStandings;
import com.ronhelwig.livevillages.sim.SettlementRecipeKnowledge;
import com.ronhelwig.livevillages.sim.SettlementState;

public class ScribeDeskMenu extends AbstractContainerMenu {
	private static final int BUTTON_INDEX_BITS = 14;
	private static final int BUTTON_INDEX_MASK = (1 << BUTTON_INDEX_BITS) - 1;
	private static final int BUTTON_ACTION_SHIFT = BUTTON_INDEX_BITS;
	private static final int BUTTON_FINGERPRINT_SHIFT = BUTTON_INDEX_BITS + 1;
	private static final int BUTTON_FINGERPRINT_MASK = (1 << 16) - 1;
	private static final int ACTION_LEARN = 0;
	private static final int ACTION_CONTRIBUTE = 1;
	private static final int MAX_RECIPE_CONTRIBUTION_SUPPORT = 8;
	private final BlockPos deskPos;
	private final String settlementId;
	private final String settlementName;
	private final int settlementTier;
	private List<ScribeRecipeView> recipes;
	private List<ScribeRecipeView> playerRecipes;

	public ScribeDeskMenu(int syncId, Inventory inventory, ScribeDeskOpenData openData) {
		super(LiveVillagesMenus.SCRIBE_DESK, syncId);
		this.deskPos = openData.deskPos().immutable();
		this.settlementId = openData.settlementId();
		this.settlementName = openData.settlementName();
		this.settlementTier = openData.settlementTier();
		this.recipes = List.copyOf(openData.recipes());
		this.playerRecipes = List.copyOf(openData.playerRecipes());
	}

	public BlockPos deskPos() {
		return deskPos;
	}

	public String settlementName() {
		return settlementName;
	}

	public int settlementTier() {
		return settlementTier;
	}

	public List<ScribeRecipeView> recipes() {
		return recipes;
	}

	public List<ScribeRecipeView> playerRecipes() {
		return playerRecipes;
	}

	public int recipeCount() {
		return recipes.size();
	}

	public int playerRecipeCount() {
		return playerRecipes.size();
	}

	public ScribeRecipeView recipe(int index) {
		return recipes.get(index);
	}

	public ScribeRecipeView playerRecipe(int index) {
		return playerRecipes.get(index);
	}

	public int buttonIdForRecipe(int index) {
		if (!isValidRecipeIndex(index)) {
			return -1;
		}

		return buttonIdForRecipe(ACTION_LEARN, index, recipes.get(index));
	}

	public int buttonIdForPlayerRecipe(int index) {
		if (!isValidPlayerRecipeIndex(index)) {
			return -1;
		}

		return buttonIdForRecipe(ACTION_CONTRIBUTE, index, playerRecipes.get(index));
	}

	public void removeRecipeAt(int index) {
		if (!isValidRecipeIndex(index)) {
			return;
		}

		recipes = java.util.stream.IntStream.range(0, recipes.size())
			.filter(candidateIndex -> candidateIndex != index)
			.mapToObj(recipes::get)
			.toList();
	}

	public void removePlayerRecipeAt(int index) {
		if (!isValidPlayerRecipeIndex(index)) {
			return;
		}

		playerRecipes = java.util.stream.IntStream.range(0, playerRecipes.size())
			.filter(candidateIndex -> candidateIndex != index)
			.mapToObj(playerRecipes::get)
			.toList();
	}

	@Override
	public boolean clickMenuButton(Player player, int buttonId) {
		int recipeIndex = buttonIndex(buttonId);
		int action = buttonAction(buttonId);

		if (action == ACTION_LEARN) {
			if (!isValidRecipeIndex(recipeIndex)) {
				return false;
			}

			ScribeRecipeView view = recipes.get(recipeIndex);

			if (buttonFingerprint(buttonId) != recipeFingerprint(view)) {
				return false;
			}

			if (player instanceof ServerPlayer serverPlayer) {
				if (teachRecipe(serverPlayer, view)) {
					removeRecipeAt(recipeIndex);
					return true;
				}

				return false;
			}

			return true;
		}

		if (action == ACTION_CONTRIBUTE) {
			if (!isValidPlayerRecipeIndex(recipeIndex)) {
				return false;
			}

			ScribeRecipeView view = playerRecipes.get(recipeIndex);

			if (buttonFingerprint(buttonId) != recipeFingerprint(view)) {
				return false;
			}

			if (player instanceof ServerPlayer serverPlayer) {
				if (contributeRecipe(serverPlayer, view)) {
					removePlayerRecipeAt(recipeIndex);
					return true;
				}

				return false;
			}

			return true;
		}

		return false;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		return player.blockPosition().distSqr(deskPos) <= 64.0D;
	}

	private boolean teachRecipe(ServerPlayer player, ScribeRecipeView view) {
		ServerLevel serverLevel = (ServerLevel) player.level();
		if (liveDeskSettlement(serverLevel, player).isEmpty()) {
			return false;
		}

		Optional<RecipeHolder<?>> recipe = SettlementRecipeKnowledge.recipeKey(view.recipeId())
			.flatMap(key -> serverLevel.getServer().getRecipeManager().byKey(key));

		if (recipe.isEmpty()) {
			player.sendSystemMessage(Component.literal("The Scribe's notes for that recipe are no longer valid."));
			return false;
		}

		if (player.getRecipeBook().contains(recipe.get().id())) {
			player.sendSystemMessage(Component.literal("You already know that recipe."));
			return false;
		}

		Item paymentItem = itemForId(view.paymentItemId());
		int paymentCount = Math.max(1, view.paymentCount());

		if (!consumePayment(player.getInventory(), paymentItem, paymentCount)) {
			player.sendSystemMessage(Component.literal("You need " + paymentCount + " " + new ItemStack(paymentItem).getHoverName().getString() + " to trade for that recipe."));
			return false;
		}

		int awarded = player.awardRecipes(List.of(recipe.get()));

		if (awarded <= 0) {
			player.getInventory().add(new ItemStack(paymentItem, paymentCount));
			player.sendSystemMessage(Component.literal("The Scribe could not teach that recipe."));
			return false;
		}

		player.level().playSound(null, deskPos, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.BLOCKS, 0.8F, 1.0F);
		return true;
	}

	private boolean contributeRecipe(ServerPlayer player, ScribeRecipeView view) {
		ServerLevel serverLevel = (ServerLevel) player.level();
		Optional<SettlementState> settlement = liveDeskSettlement(serverLevel, player);

		if (settlement.isEmpty()) {
			return false;
		}

		Optional<RecipeHolder<?>> recipe = SettlementRecipeKnowledge.recipeKey(view.recipeId())
			.flatMap(key -> serverLevel.getServer().getRecipeManager().byKey(key));

		if (recipe.isEmpty()) {
			player.sendSystemMessage(Component.literal("That recipe is no longer valid."));
			return false;
		}

		if (!player.getRecipeBook().contains(recipe.get().id())) {
			player.sendSystemMessage(Component.literal("You do not know that recipe."));
			return false;
		}

		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(serverLevel.getServer());

		if (!savedData.addKnownScribeRecipe(settlementId, view.recipeId())) {
			player.sendSystemMessage(Component.literal("The settlement already knows that recipe."));
			return false;
		}

		int support = recipeContributionSupport(view);
		SettlementPlayerStandings.recordSupport(serverLevel, player, settlement.get(), support);
		player.sendSystemMessage(Component.literal("The Scribe copied your recipe. (+" + support + " support)"), true);
		player.level().playSound(null, deskPos, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.BLOCKS, 0.8F, 1.15F);
		return true;
	}

	private Optional<SettlementState> liveDeskSettlement(ServerLevel level, ServerPlayer player) {
		if (!level.hasChunkAt(deskPos) || !level.getBlockState(deskPos).is(LiveVillagesBlocks.SCRIBE_DESK)) {
			player.sendSystemMessage(Component.literal("That Scribe Desk is no longer available."));
			return Optional.empty();
		}

		Optional<SettlementState> settlement = SettlementConstruction.findWorkstationSettlement(level, deskPos)
			.filter(candidate -> candidate.id().equals(settlementId));

		if (settlement.isEmpty()) {
			player.sendSystemMessage(Component.literal("That Scribe Desk is no longer linked to this settlement."));
		}

		return settlement;
	}

	public static int recipeContributionSupport(ScribeRecipeView view) {
		int support = Math.max(2, view.paymentCount() + 1);

		if (view.paymentItemId().equals(BuiltInRegistries.ITEM.getKey(Items.BOOK).toString())) {
			support += 2;
		}

		return Math.min(MAX_RECIPE_CONTRIBUTION_SUPPORT, support);
	}

	private static boolean consumePayment(Inventory inventory, Item item, int count) {
		int remaining = count;

		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);

			if (!stack.is(item)) {
				continue;
			}

			remaining -= stack.getCount();

			if (remaining <= 0) {
				break;
			}
		}

		if (remaining > 0) {
			return false;
		}

		remaining = count;
		for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
			ItemStack stack = inventory.getItem(slot);

			if (!stack.is(item)) {
				continue;
			}

			int removed = Math.min(remaining, stack.getCount());
			stack.shrink(removed);
			remaining -= removed;

			if (stack.isEmpty()) {
				inventory.setItem(slot, ItemStack.EMPTY);
			}
		}

		inventory.setChanged();
		return true;
	}

	private static Item itemForId(String itemId) {
		Identifier identifier = Identifier.tryParse(itemId);
		return identifier == null
			? Items.PAPER
			: BuiltInRegistries.ITEM.getOptional(identifier).orElse(Items.PAPER);
	}

	private boolean isValidRecipeIndex(int index) {
		return index >= 0 && index < recipes.size();
	}

	private boolean isValidPlayerRecipeIndex(int index) {
		return index >= 0 && index < playerRecipes.size();
	}

	private static int buttonIdForRecipe(int action, int index, ScribeRecipeView view) {
		if (index < 0 || index > BUTTON_INDEX_MASK) {
			return -1;
		}

		return (recipeFingerprint(view) << BUTTON_FINGERPRINT_SHIFT) | (action << BUTTON_ACTION_SHIFT) | index;
	}

	private static int buttonIndex(int buttonId) {
		return buttonId & BUTTON_INDEX_MASK;
	}

	private static int buttonAction(int buttonId) {
		return (buttonId >>> BUTTON_ACTION_SHIFT) & 1;
	}

	private static int buttonFingerprint(int buttonId) {
		return (buttonId >>> BUTTON_FINGERPRINT_SHIFT) & BUTTON_FINGERPRINT_MASK;
	}

	private static int recipeFingerprint(ScribeRecipeView view) {
		return Math.floorMod(java.util.Objects.hash(
			view.recipeId(),
			view.outputItemId(),
			view.paymentItemId(),
			view.paymentCount()
		), BUTTON_FINGERPRINT_MASK + 1);
	}

}
