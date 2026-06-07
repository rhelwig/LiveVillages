package com.ronhelwig.livevillages.block;

import java.util.LinkedHashMap;
import java.util.Map;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import com.ronhelwig.livevillages.sim.SettlementState;
import com.ronhelwig.livevillages.sim.SettlementVillagers;

public class GuardPostBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<GuardPostBlock> CODEC = simpleCodec(GuardPostBlock::new);
	private static final VoxelShape SHAPE = Shapes.or(
		Block.box(1.0D, 0.0D, 1.0D, 15.0D, 2.0D, 15.0D),
		Block.box(2.0D, 2.0D, 2.0D, 14.0D, 10.0D, 14.0D),
		Block.box(0.0D, 10.0D, 0.0D, 16.0D, 12.0D, 16.0D),
		Block.box(6.0D, 12.0D, 2.0D, 10.0D, 16.0D, 14.0D)
	);

	public GuardPostBlock(BlockBehaviour.Properties properties) {
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
		return SHAPE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
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
				serverPlayer.sendSystemMessage(Component.literal("No settlement found near this Guard Post."));
			}

			return;
		}

		Direction facing = state.getValue(FACING);
		Map<String, Integer> stock = new LinkedHashMap<>(settlement.stock());
		SettlementConstruction.WorkstationBuildResult buildResult = SettlementConstruction.tryStartGuardPostAtWorkstation(
			serverLevel,
			pos,
			facing,
			settlement.id(),
			stock,
			savedData.findBuildSite(settlement.id(), SettlementBuildSiteType.GUARD_POST, pos)
		);

		if (buildResult.isStarted()) {
			savedData.putBuildSite(buildResult.buildSite());
			SettlementVillagers.ensureWorkforce(serverLevel, settlement);

			if (placer instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendSystemMessage(Component.literal("Guard Post construction started around this Guard Post."));
			}
		} else if (buildResult.isResumed()) {
			savedData.putBuildSite(buildResult.buildSite());
			SettlementVillagers.ensureWorkforce(serverLevel, settlement);

			if (placer instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendSystemMessage(Component.literal("Guard Post construction is already planned around this Guard Post."));
			}
		} else if (buildResult.isBlocked() && placer instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendSystemMessage(Component.literal("The settlement can't build a Guard Post here."));
		}
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()) {
			return InteractionResult.PASS;
		}

		GuardDonation donation = guardDonationFor(stack);
		if (donation == null) {
			return InteractionResult.PASS;
		}

		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}

		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(serverLevel.getServer());
		SettlementState settlement = SettlementConstruction.findWorkstationSettlement(serverLevel, pos).orElse(null);
		if (settlement == null) {
			serverPlayer.sendSystemMessage(Component.literal("No settlement found near this Guard Post."));
			return InteractionResult.SUCCESS_SERVER;
		}

		Map<String, Integer> stock = new LinkedHashMap<>(settlement.stock());
		stock.merge(donation.stockKey(), 1, Integer::sum);
		savedData.putSettlementAndRefreshBuildSiteMaterialStatus(
			settlement.withStock(stock),
			serverLevel.getServer().getTickCount()
		);

		if (!serverPlayer.getAbilities().instabuild) {
			stack.shrink(1);
		}

		serverPlayer.sendSystemMessage(Component.literal("Donated " + donation.displayName() + " to " + settlement.name() + "'s Guard Post."));
		return InteractionResult.SUCCESS_SERVER;
	}

	private static GuardDonation guardDonationFor(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}

		if (stack.is(Items.WOODEN_SWORD)) {
			return new GuardDonation("wooden_sword", "Wooden Sword");
		}

		if (stack.is(Items.STONE_SWORD)) {
			return new GuardDonation("stone_sword", "Stone Sword");
		}

		if (stack.is(Items.IRON_SWORD)) {
			return new GuardDonation("iron_sword", "Iron Sword");
		}

		if (stack.is(Items.GOLDEN_SWORD)) {
			return new GuardDonation("golden_sword", "Golden Sword");
		}

		if (stack.is(Items.DIAMOND_SWORD)) {
			return new GuardDonation("diamond_sword", "Diamond Sword");
		}

		if (stack.is(Items.NETHERITE_SWORD)) {
			return new GuardDonation("netherite_sword", "Netherite Sword");
		}

		if (stack.is(Items.BOW)) {
			return new GuardDonation("bow", "Bow");
		}

		if (stack.is(Items.CROSSBOW)) {
			return new GuardDonation("crossbow", "Crossbow");
		}

		if (stack.is(Items.TRIDENT)) {
			return new GuardDonation("trident", "Trident");
		}

		if (stack.is(Items.MACE)) {
			return new GuardDonation("mace", "Mace");
		}

		if (stack.is(Items.SHIELD)) {
			return new GuardDonation("shield", "Shield");
		}

		if (stack.is(Items.ARROW)) {
			return new GuardDonation("arrow", "Arrow");
		}

		if (stack.is(Items.SPECTRAL_ARROW)) {
			return new GuardDonation("spectral_arrow", "Spectral Arrow");
		}

		if (stack.is(Items.LEATHER_HELMET)) {
			return new GuardDonation("leather_helmet", "Leather Helmet");
		}

		if (stack.is(Items.LEATHER_CHESTPLATE)) {
			return new GuardDonation("leather_chestplate", "Leather Tunic");
		}

		if (stack.is(Items.LEATHER_LEGGINGS)) {
			return new GuardDonation("leather_leggings", "Leather Pants");
		}

		if (stack.is(Items.LEATHER_BOOTS)) {
			return new GuardDonation("leather_boots", "Leather Boots");
		}

		if (stack.is(Items.CHAINMAIL_HELMET)) {
			return new GuardDonation("chainmail_helmet", "Chainmail Helmet");
		}

		if (stack.is(Items.CHAINMAIL_CHESTPLATE)) {
			return new GuardDonation("chainmail_chestplate", "Chainmail Chestplate");
		}

		if (stack.is(Items.CHAINMAIL_LEGGINGS)) {
			return new GuardDonation("chainmail_leggings", "Chainmail Leggings");
		}

		if (stack.is(Items.CHAINMAIL_BOOTS)) {
			return new GuardDonation("chainmail_boots", "Chainmail Boots");
		}

		if (stack.is(Items.IRON_HELMET)) {
			return new GuardDonation("iron_helmet", "Iron Helmet");
		}

		if (stack.is(Items.IRON_CHESTPLATE)) {
			return new GuardDonation("iron_chestplate", "Iron Chestplate");
		}

		if (stack.is(Items.IRON_LEGGINGS)) {
			return new GuardDonation("iron_leggings", "Iron Leggings");
		}

		if (stack.is(Items.IRON_BOOTS)) {
			return new GuardDonation("iron_boots", "Iron Boots");
		}

		if (stack.is(Items.GOLDEN_HELMET)) {
			return new GuardDonation("golden_helmet", "Golden Helmet");
		}

		if (stack.is(Items.GOLDEN_CHESTPLATE)) {
			return new GuardDonation("golden_chestplate", "Golden Chestplate");
		}

		if (stack.is(Items.GOLDEN_LEGGINGS)) {
			return new GuardDonation("golden_leggings", "Golden Leggings");
		}

		if (stack.is(Items.GOLDEN_BOOTS)) {
			return new GuardDonation("golden_boots", "Golden Boots");
		}

		if (stack.is(Items.DIAMOND_HELMET)) {
			return new GuardDonation("diamond_helmet", "Diamond Helmet");
		}

		if (stack.is(Items.DIAMOND_CHESTPLATE)) {
			return new GuardDonation("diamond_chestplate", "Diamond Chestplate");
		}

		if (stack.is(Items.DIAMOND_LEGGINGS)) {
			return new GuardDonation("diamond_leggings", "Diamond Leggings");
		}

		if (stack.is(Items.DIAMOND_BOOTS)) {
			return new GuardDonation("diamond_boots", "Diamond Boots");
		}

		if (stack.is(Items.NETHERITE_HELMET)) {
			return new GuardDonation("netherite_helmet", "Netherite Helmet");
		}

		if (stack.is(Items.NETHERITE_CHESTPLATE)) {
			return new GuardDonation("netherite_chestplate", "Netherite Chestplate");
		}

		if (stack.is(Items.NETHERITE_LEGGINGS)) {
			return new GuardDonation("netherite_leggings", "Netherite Leggings");
		}

		if (stack.is(Items.NETHERITE_BOOTS)) {
			return new GuardDonation("netherite_boots", "Netherite Boots");
		}

		if (stack.getItem() instanceof BannerItem) {
			return new GuardDonation("desecrated_enemy_banner", "Banner Trophy");
		}

		return null;
	}

	private record GuardDonation(String stockKey, String displayName) {
	}
}
