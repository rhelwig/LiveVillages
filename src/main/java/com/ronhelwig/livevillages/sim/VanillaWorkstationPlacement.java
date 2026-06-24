package com.ronhelwig.livevillages.sim;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class VanillaWorkstationPlacement {
	private static final int MAX_PENDING_AGE_TICKS = 5;
	private static final List<PendingPlacement> PENDING_PLACEMENTS = new ArrayList<>();

	private VanillaWorkstationPlacement() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register(VanillaWorkstationPlacement::onUseBlock);
		ServerTickEvents.END_SERVER_TICK.register(VanillaWorkstationPlacement::onEndServerTick);
	}

	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hitResult) {
		if (level.isClientSide() || !(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer) || player.isSpectator()) {
			return InteractionResult.PASS;
		}

		ItemStack stack = player.getItemInHand(hand);
		WorkstationPlacementType type = WorkstationPlacementType.forItem(stack.getItem());

		if (type == null) {
			return InteractionResult.PASS;
		}

		BlockPos placementPos = targetPlacementPos(level, hitResult);
		if (SettlementConstruction.isPositionInExistingShelteredStructure(serverLevel, placementPos)) {
			return InteractionResult.PASS;
		}

		Direction facing = player.getDirection().getAxis() == Direction.Axis.Y ? Direction.NORTH : player.getDirection().getOpposite();
		PENDING_PLACEMENTS.add(new PendingPlacement(serverLevel.dimension(), placementPos.immutable(), type, facing, serverPlayer.getUUID(), serverLevel.getServer().getTickCount()));
		return InteractionResult.PASS;
	}

	private static BlockPos targetPlacementPos(Level level, BlockHitResult hitResult) {
		BlockState clickedState = level.getBlockState(hitResult.getBlockPos());
		return SettlementConstruction.isBuildSiteReplaceable(clickedState)
			? hitResult.getBlockPos()
			: hitResult.getBlockPos().relative(hitResult.getDirection());
	}

	private static void onEndServerTick(MinecraftServer server) {
		if (PENDING_PLACEMENTS.isEmpty()) {
			return;
		}

		long currentTick = server.getTickCount();
		Iterator<PendingPlacement> iterator = PENDING_PLACEMENTS.iterator();

		while (iterator.hasNext()) {
			PendingPlacement pending = iterator.next();
			if (currentTick <= pending.createdTick()) {
				continue;
			}

			iterator.remove();
			if (currentTick - pending.createdTick() > MAX_PENDING_AGE_TICKS) {
				continue;
			}

			ServerLevel level = server.getLevel(pending.dimension());
			if (level == null || !pending.type().matches(level.getBlockState(pending.pos()))) {
				continue;
			}

			startBuildSite(level, pending, server.getPlayerList().getPlayer(pending.playerId()));
		}
	}

	private static void startBuildSite(ServerLevel level, PendingPlacement pending, ServerPlayer player) {
		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(level.getServer());
		SettlementState settlement = SettlementConstruction.findWorkstationSettlement(level, pending.pos()).orElse(null);

		if (settlement == null) {
			if (player != null) {
				player.sendSystemMessage(Component.literal("No settlement found near this " + pending.type().workstationName() + "."));
			}
			return;
		}

		Map<String, Integer> stock = new LinkedHashMap<>(settlement.stock());
		Optional<SettlementBuildSite> existingBuildSite = savedData.findBuildSite(settlement.id(), pending.type().buildSiteType(), pending.pos());
		SettlementConstruction.WorkstationBuildResult buildResult = pending.type().start(level, settlement, stock, pending.pos(), pending.facing(), existingBuildSite);

		if (buildResult.isStarted()) {
			savedData.putBuildSite(buildResult.buildSite());
			SettlementVillagers.ensureWorkforce(level, settlement);

			if (player != null) {
				player.sendSystemMessage(Component.literal(pending.type().structureName() + " construction started around this " + pending.type().workstationName() + "."));
			}
		} else if (buildResult.isResumed()) {
			savedData.putBuildSite(buildResult.buildSite());
			SettlementVillagers.ensureWorkforce(level, settlement);

			if (player != null) {
				player.sendSystemMessage(Component.literal(pending.type().structureName() + " construction is already planned around this " + pending.type().workstationName() + "."));
			}
		} else if (buildResult.isBlocked() && player != null) {
			player.sendSystemMessage(Component.literal("The settlement can't build a " + pending.type().structureName() + " here."));
		}
	}

	private record PendingPlacement(
		ResourceKey<Level> dimension,
		BlockPos pos,
		WorkstationPlacementType type,
		Direction facing,
		UUID playerId,
		long createdTick
	) {
	}

	private enum WorkstationPlacementType {
		CARTOGRAPHER(Items.CARTOGRAPHY_TABLE, SettlementBuildSiteType.CARTOGRAPHER_HOUSE, "Cartography Table", "Cartographer's House", state -> state.is(Blocks.CARTOGRAPHY_TABLE)) {
			@Override
			SettlementConstruction.WorkstationBuildResult start(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, BlockPos pos, Direction facing, Optional<SettlementBuildSite> existingBuildSite) {
				return SettlementConstruction.tryStartCartographerHouseAtWorkstation(level, pos, facing, settlement.id(), stock, existingBuildSite);
			}
		},
		BUTCHER(Items.SMOKER, SettlementBuildSiteType.BUTCHER_SHOP, "Smoker", "Butcher Shop", state -> state.is(Blocks.SMOKER)) {
			@Override
			SettlementConstruction.WorkstationBuildResult start(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, BlockPos pos, Direction facing, Optional<SettlementBuildSite> existingBuildSite) {
				return SettlementConstruction.tryStartButcherShopAtWorkstation(level, pos, facing, settlement.id(), stock, existingBuildSite);
			}
		},
		MASON(Items.STONECUTTER, SettlementBuildSiteType.MASON_WORKSHOP, "Stonecutter", "Mason's Workshop", state -> state.is(Blocks.STONECUTTER)) {
			@Override
			SettlementConstruction.WorkstationBuildResult start(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, BlockPos pos, Direction facing, Optional<SettlementBuildSite> existingBuildSite) {
				return SettlementConstruction.tryStartMasonWorkshopAtWorkstation(level, pos, facing, settlement.id(), stock, existingBuildSite);
			}
		},
		FLETCHER(Items.FLETCHING_TABLE, SettlementBuildSiteType.FLETCHER_HUT, "Fletching Table", "Fletcher Hut", state -> state.is(Blocks.FLETCHING_TABLE)) {
			@Override
			SettlementConstruction.WorkstationBuildResult start(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, BlockPos pos, Direction facing, Optional<SettlementBuildSite> existingBuildSite) {
				return SettlementConstruction.tryStartFletcherHutAtWorkstation(level, pos, SettlementConstruction.fletcherHutFacingFor(settlement, pos), settlement.id(), stock, existingBuildSite);
			}
		},
		CLERIC(Items.BREWING_STAND, SettlementBuildSiteType.CLERIC_SHRINE, "Brewing Stand", "Cleric Shrine", state -> state.is(Blocks.BREWING_STAND)) {
			@Override
			SettlementConstruction.WorkstationBuildResult start(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, BlockPos pos, Direction facing, Optional<SettlementBuildSite> existingBuildSite) {
				return SettlementConstruction.tryStartClericShrineAtWorkstation(level, pos, facing, settlement.id(), stock, existingBuildSite);
			}
		},
		LEATHERWORKER(Items.CAULDRON, SettlementBuildSiteType.LEATHERWORKER_WORKSHOP, "Cauldron", "Leatherworker's Workshop", state -> state.is(Blocks.CAULDRON)) {
			@Override
			SettlementConstruction.WorkstationBuildResult start(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, BlockPos pos, Direction facing, Optional<SettlementBuildSite> existingBuildSite) {
				return SettlementConstruction.tryStartLeatherworkerWorkshopAtWorkstation(level, pos, facing, settlement.id(), stock, existingBuildSite);
			}
		},
		LIBRARIAN(Items.LECTERN, SettlementBuildSiteType.LIBRARY, "Lectern", "Library", state -> state.is(Blocks.LECTERN)) {
			@Override
			SettlementConstruction.WorkstationBuildResult start(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, BlockPos pos, Direction facing, Optional<SettlementBuildSite> existingBuildSite) {
				return SettlementConstruction.tryStartLibraryAtWorkstation(level, pos, facing, settlement.id(), stock, existingBuildSite);
			}
		},
		SHEPHERD(Items.LOOM, SettlementBuildSiteType.SHEPHERD_HUT, "Loom", "Shepherd's Hut", state -> state.is(Blocks.LOOM)) {
			@Override
			SettlementConstruction.WorkstationBuildResult start(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, BlockPos pos, Direction facing, Optional<SettlementBuildSite> existingBuildSite) {
				return SettlementConstruction.tryStartShepherdHutAtWorkstation(level, pos, facing, settlement.id(), stock, existingBuildSite);
			}
		},
		ARMORER(Items.BLAST_FURNACE, SettlementBuildSiteType.SMITHY, "Blast Furnace", "Smithy", state -> state.is(Blocks.BLAST_FURNACE)) {
			@Override
			SettlementConstruction.WorkstationBuildResult start(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, BlockPos pos, Direction facing, Optional<SettlementBuildSite> existingBuildSite) {
				return SettlementConstruction.tryStartSmithyAtWorkstation(level, pos, facing, settlement.id(), stock, existingBuildSite);
			}
		},
		TOOLSMITH(Items.SMITHING_TABLE, SettlementBuildSiteType.SMITHY, "Smithing Table", "Smithy", state -> state.is(Blocks.SMITHING_TABLE)) {
			@Override
			SettlementConstruction.WorkstationBuildResult start(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, BlockPos pos, Direction facing, Optional<SettlementBuildSite> existingBuildSite) {
				return SettlementConstruction.tryStartSmithyAtWorkstation(level, pos, facing, settlement.id(), stock, existingBuildSite);
			}
		},
		WEAPONSMITH(Items.GRINDSTONE, SettlementBuildSiteType.SMITHY, "Grindstone", "Smithy", state -> state.is(Blocks.GRINDSTONE)) {
			@Override
			SettlementConstruction.WorkstationBuildResult start(ServerLevel level, SettlementState settlement, Map<String, Integer> stock, BlockPos pos, Direction facing, Optional<SettlementBuildSite> existingBuildSite) {
				return SettlementConstruction.tryStartSmithyAtWorkstation(level, pos, facing, settlement.id(), stock, existingBuildSite);
			}
		};

		private final Item item;
		private final SettlementBuildSiteType buildSiteType;
		private final String workstationName;
		private final String structureName;
		private final java.util.function.Predicate<BlockState> blockPredicate;

		WorkstationPlacementType(Item item, SettlementBuildSiteType buildSiteType, String workstationName, String structureName, java.util.function.Predicate<BlockState> blockPredicate) {
			this.item = item;
			this.buildSiteType = buildSiteType;
			this.workstationName = workstationName;
			this.structureName = structureName;
			this.blockPredicate = blockPredicate;
		}

		static WorkstationPlacementType forItem(Item item) {
			for (WorkstationPlacementType type : values()) {
				if (type.item == item) {
					return type;
				}
			}

			return null;
		}

		boolean matches(BlockState state) {
			return blockPredicate.test(state);
		}

		SettlementBuildSiteType buildSiteType() {
			return buildSiteType;
		}

		String workstationName() {
			return workstationName;
		}

		String structureName() {
			return structureName;
		}

		abstract SettlementConstruction.WorkstationBuildResult start(
			ServerLevel level,
			SettlementState settlement,
			Map<String, Integer> stock,
			BlockPos pos,
			Direction facing,
			Optional<SettlementBuildSite> existingBuildSite
		);
	}
}
