package com.ronhelwig.livevillages.sim;

import java.util.Map;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;

public final class OutpostTrust {
	private static final int PARLEY_RAIDER_RADIUS_BLOCKS = 32;
	private static final int OUTPOST_INTERACTION_RADIUS_BLOCKS = 96;
	private static final int OUTPOST_INFLUENCE_MARGIN_BLOCKS = 32;
	private static final int TARGET_CLEAR_RADIUS_BLOCKS = 96;
	private static final int ACCEPTED_PLAYER_RELAX_RADIUS_BLOCKS = 24;
	private static final int RECENT_PROVOCATION_TICKS = 200;
	private static final int ALLY_DEFENSE_RADIUS_BLOCKS = 48;
	private static final int RECENT_PLAYER_ATTACK_TICKS = 120;
	private OutpostTrust() {
	}

	public static void maintainServer(MinecraftServer server, LiveVillagesSavedData savedData) {
		long tick = server.getTickCount();

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!(player.level() instanceof ServerLevel level) || player.isSpectator()) {
				continue;
			}

			handleParley(level, savedData, player, tick);
			clearSuppressedTargets(level, player);
			rallyAcceptedOutpostAllies(level, savedData, player, tick);
		}
	}

	public static boolean shouldSuppressTarget(Raider raider, ServerPlayer player) {
		if (raider == null || player == null || !(raider.level() instanceof ServerLevel level)) {
			return false;
		}

		if (recentlyProvoked(raider, player)) {
			return false;
		}

		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(level.getServer());
		Optional<SettlementState> outpost = findOrCreateOutpostForRaider(level, savedData, raider);

		if (outpost.isEmpty()) {
			return false;
		}

		SettlementState settlement = outpost.get();
		return (OutpostSettlementWork.isActiveRaidMember(raider, settlement) || isWithinOutpostInfluence(settlement, player.blockPosition()))
			&& rankFor(savedData, settlement, player).atLeast(OutpostPlayerRank.TOLERATED);
	}

	public static boolean shouldSuppressTarget(Raider raider, LivingEntity target) {
		if (target instanceof ServerPlayer player) {
			return shouldSuppressTarget(raider, player);
		}

		if (raider == null || target == null || !(raider.level() instanceof ServerLevel level) || !(target instanceof Villager)) {
			return false;
		}

		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(level.getServer());
		Optional<SettlementState> raiderOutpost = findOrCreateOutpostForRaider(level, savedData, raider);

		if (raiderOutpost.isEmpty()) {
			return false;
		}

		SettlementState settlement = raiderOutpost.get();
		return isWithinOutpostInfluence(settlement, raider.blockPosition())
			&& isWithinOutpostInfluence(settlement, target.blockPosition());
	}

	public static boolean shouldSuppressIllagerAttack(AbstractIllager illager, LivingEntity target) {
		return illager instanceof Raider raider
			&& shouldSuppressTarget(raider, target);
	}

	public static boolean shouldIgnoreSleepDanger(LivingEntity sleeper, Monster monster) {
		return monster instanceof Raider raider
			&& shouldSuppressTarget(raider, sleeper);
	}

	public static boolean shouldShowAcceptedPlayerBodyLanguage(Raider raider, Player player) {
		if (!(player instanceof ServerPlayer serverPlayer) || !(raider.level() instanceof ServerLevel level)) {
			return false;
		}

		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(level.getServer());
		SettlementState outpost = findOrCreateOutpostForRaider(level, savedData, raider).orElse(null);
		if (outpost == null) {
			return false;
		}

		return shouldSuppressTarget(raider, serverPlayer)
			&& rankFor(savedData, outpost, serverPlayer).atLeast(OutpostPlayerRank.ASSOCIATE);
	}

	public static boolean hasNearbyAcceptedPlayer(Raider raider) {
		if (raider == null || !(raider.level() instanceof ServerLevel level)) {
			return false;
		}

		AABB bounds = raider.getBoundingBox().inflate(ACCEPTED_PLAYER_RELAX_RADIUS_BLOCKS);
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, bounds, player -> !player.isSpectator() && player.isAlive())) {
			if (shouldShowAcceptedPlayerBodyLanguage(raider, player)) {
				return true;
			}
		}

		return false;
	}

	public static Optional<SettlementState> findOrCreateOutpostAt(ServerLevel level, LiveVillagesSavedData savedData, BlockPos position) {
		Optional<SettlementState> knownOutpost = savedData.findNearestSettlement(
			level.dimension(),
			position,
			OUTPOST_INTERACTION_RADIUS_BLOCKS,
			settlement -> settlement.kind() == SettlementKind.OUTPOST
		);

		if (knownOutpost.isPresent() && isWithinOutpostInfluence(knownOutpost.get(), position)) {
			return knownOutpost;
		}

		return pillagerOutpostCenter(level, position)
			.map(center -> createOutpostSettlement(level, savedData, center));
	}

	private static Optional<SettlementState> findOrCreateOutpostForRaider(ServerLevel level, LiveVillagesSavedData savedData, Raider raider) {
		for (SettlementState settlement : savedData.getSettlements()) {
			if (settlement.kind() == SettlementKind.OUTPOST
				&& settlement.dimension().equals(level.dimension())
				&& OutpostSettlementWork.isActiveRaidMember(raider, settlement)) {
				return Optional.of(settlement);
			}
		}

		Optional<SettlementState> knownOutpost = savedData.findNearestSettlement(
			level.dimension(),
			raider.blockPosition(),
			OUTPOST_INTERACTION_RADIUS_BLOCKS,
			settlement -> settlement.kind() == SettlementKind.OUTPOST
		);

		if (knownOutpost.isPresent()) {
			return knownOutpost;
		}

		return findOrCreateOutpostAt(level, savedData, raider.blockPosition());
	}


	public static void recordOutpostBuildSupport(
		ServerLevel level,
		ServerPlayer player,
		SettlementBuildSite buildSite,
		int placedBlocks,
		boolean completedBuildSite
	) {
		if (level == null || player == null || buildSite == null || placedBlocks <= 0) {
			return;
		}

		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(level.getServer());
		SettlementState settlement = savedData.getSettlement(buildSite.settlementId()).orElse(null);
		if (settlement == null || settlement.kind() != SettlementKind.OUTPOST) {
			return;
		}

		SettlementPlayerStandings.recordBuildSupport(level, player, settlement, placedBlocks, completedBuildSite);
	}

	public static OutpostPlayerRank rankFor(LiveVillagesSavedData savedData, SettlementState settlement, ServerPlayer player) {
		if (settlement == null || player == null || settlement.kind() != SettlementKind.OUTPOST) {
			return OutpostPlayerRank.UNKNOWN;
		}

		return savedData.settlementPlayerStanding(settlement, player).rank();
	}

	private static void handleParley(ServerLevel level, LiveVillagesSavedData savedData, ServerPlayer player, long tick) {
		if (!isParleyStance(player) || !hasNearbyRaider(level, player.blockPosition())) {
			return;
		}

		SettlementState settlement = findOrCreateParleyOutpost(level, savedData, player).orElse(null);
		if (settlement == null) {
			return;
		}

		OutpostPlayerStanding previous = savedData.settlementPlayerStanding(settlement, player);
		OutpostPlayerStanding updated = previous.withParley(OutpostPlayerRank.TOLERATED, tick);
		if (updated.equals(previous)) {
			return;
		}

		savedData.setSettlementPlayerStanding(settlement, player, updated);
		if (!previous.rank().atLeast(OutpostPlayerRank.TOLERATED)) {
			player.sendSystemMessage(Component.literal("You parleyed with " + settlement.name() + ". Rank: Tolerated."), true);
		}
	}

	private static Optional<SettlementState> findOrCreateParleyOutpost(ServerLevel level, LiveVillagesSavedData savedData, ServerPlayer player) {
		Optional<SettlementState> knownOutpost = savedData.findNearestSettlement(
			level.dimension(),
			player.blockPosition(),
			OUTPOST_INTERACTION_RADIUS_BLOCKS,
			settlement -> settlement.kind() == SettlementKind.OUTPOST
		);

		if (knownOutpost.isPresent() && isWithinOutpostInfluence(knownOutpost.get(), player.blockPosition())) {
			return knownOutpost;
		}

		return pillagerOutpostCenterNearParley(level, player.blockPosition())
			.map(center -> createOutpostSettlement(level, savedData, center));
	}

	private static SettlementState createOutpostSettlement(ServerLevel level, LiveVillagesSavedData savedData, BlockPos center) {
		Optional<SettlementState> existing = savedData.findNearestSettlement(
			level.dimension(),
			center,
			OUTPOST_INTERACTION_RADIUS_BLOCKS,
			settlement -> settlement.kind() == SettlementKind.OUTPOST
		);

		if (existing.isPresent()) {
			return existing.get();
		}

		String id = createOutpostId(level.dimension(), center);
		SettlementState existingById = savedData.getSettlement(id).orElse(null);
		if (existingById != null) {
			return existingById;
		}

		String name = SettlementNamer.generateUniqueName(SettlementKind.OUTPOST, level, center, savedData.getSettlements());
		SettlementState settlement = new SettlementState(
			id,
			name,
			level.dimension(),
			center,
			SettlementKind.OUTPOST,
			1,
			Map.of(),
			Map.of(),
			Map.of(),
			0,
			0.8D,
			0.35D,
			1,
			0.0D,
			java.util.List.of(),
			level.getServer().getTickCount(),
			level.getServer().getTickCount()
		);
		savedData.putSettlement(settlement);
		return settlement;
	}

	private static Optional<BlockPos> pillagerOutpostCenter(ServerLevel level, BlockPos position) {
		Structure structure = level.registryAccess()
			.lookupOrThrow(Registries.STRUCTURE)
			.getValue(BuiltinStructures.PILLAGER_OUTPOST);

		if (structure == null) {
			return Optional.empty();
		}

		StructureStart start = level.structureManager().getStructureWithPieceAt(position, structure);
		if (start == null || !start.isValid()) {
			return Optional.empty();
		}

		BlockPos center = start.getBoundingBox().getCenter();
		int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, center.getX(), center.getZ());
		return Optional.of(new BlockPos(center.getX(), surfaceY, center.getZ()));
	}

	private static Optional<BlockPos> pillagerOutpostCenterNearParley(ServerLevel level, BlockPos position) {
		Optional<BlockPos> playerStructure = pillagerOutpostCenter(level, position);
		if (playerStructure.isPresent()) {
			return playerStructure;
		}

		AABB bounds = new AABB(position).inflate(PARLEY_RAIDER_RADIUS_BLOCKS);
		for (Raider raider : level.getEntitiesOfClass(Raider.class, bounds, raider -> raider.isAlive() && !raider.isRemoved())) {
			Optional<BlockPos> raiderStructure = pillagerOutpostCenter(level, raider.blockPosition());
			if (raiderStructure.isPresent()) {
				return raiderStructure;
			}
		}

		return Optional.empty();
	}

	private static void clearSuppressedTargets(ServerLevel level, ServerPlayer player) {
		AABB bounds = player.getBoundingBox().inflate(TARGET_CLEAR_RADIUS_BLOCKS);
		for (Raider raider : level.getEntitiesOfClass(Raider.class, bounds, raider -> raider.isAlive() && !raider.isRemoved())) {
			if (raider.getTarget() == player && shouldSuppressTarget(raider, player)) {
				raider.setTarget(null);
			}
		}
	}

	private static void rallyAcceptedOutpostAllies(ServerLevel level, LiveVillagesSavedData savedData, ServerPlayer player, long tick) {
		LivingEntity attacker = player.getLastHurtByMob();
		if (!(attacker instanceof Monster) || attacker instanceof Raider || !attacker.isAlive() || attacker.isRemoved()) {
			return;
		}

		if (tick - player.getLastHurtByMobTimestamp() > RECENT_PLAYER_ATTACK_TICKS) {
			return;
		}

		SettlementState settlement = savedData.findNearestSettlement(
			level.dimension(),
			player.blockPosition(),
			OUTPOST_INTERACTION_RADIUS_BLOCKS,
			candidate -> candidate.kind() == SettlementKind.OUTPOST
		)
			.filter(candidate -> isWithinOutpostInfluence(candidate, player.blockPosition()))
			.orElse(null);

		if (settlement == null || !rankFor(savedData, settlement, player).atLeast(OutpostPlayerRank.BANNER_BEARER)) {
			return;
		}

		AABB bounds = player.getBoundingBox().inflate(ALLY_DEFENSE_RADIUS_BLOCKS);
		for (Raider raider : level.getEntitiesOfClass(Raider.class, bounds, raider -> raider.isAlive() && !raider.isRemoved())) {
			if (!isWithinOutpostInfluence(settlement, raider.blockPosition()) || raider.getTarget() == attacker) {
				continue;
			}

			raider.setTarget(attacker);
		}
	}

	private static boolean hasNearbyRaider(ServerLevel level, BlockPos position) {
		AABB bounds = new AABB(position).inflate(PARLEY_RAIDER_RADIUS_BLOCKS);
		return !level.getEntitiesOfClass(Raider.class, bounds, raider -> raider.isAlive() && !raider.isRemoved()).isEmpty();
	}

	private static boolean isParleyStance(ServerPlayer player) {
		ItemStack mainHand = player.getItemInHand(InteractionHand.MAIN_HAND);
		ItemStack offHand = player.getItemInHand(InteractionHand.OFF_HAND);
		return !isThreateningHeldItem(mainHand)
			&& !isThreateningHeldItem(offHand)
			&& (isParleyOffer(mainHand) || isParleyOffer(offHand));
	}

	private static boolean isParleyOffer(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}

		return stack.is(Items.EMERALD)
			|| stack.is(Items.IRON_INGOT)
			|| stack.is(Items.ARROW)
			|| stack.is(Items.LEATHER)
			|| stack.is(Items.WHEAT)
			|| stack.is(Items.BREAD)
			|| stack.is(Items.COOKED_BEEF)
			|| stack.is(Items.COOKED_PORKCHOP)
			|| stack.is(Items.COOKED_CHICKEN)
			|| stack.is(Items.COOKED_MUTTON)
			|| stack.is(Items.COOKED_RABBIT)
			|| stack.is(Items.COOKED_COD)
			|| stack.is(Items.COOKED_SALMON);
	}

	private static boolean isThreateningHeldItem(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}

		Item item = stack.getItem();
		if (item instanceof AxeItem
			|| item instanceof ProjectileWeaponItem
			|| item instanceof MaceItem
			|| item instanceof ShieldItem
			|| item instanceof TridentItem) {
			return true;
		}

		String itemPath = BuiltInRegistries.ITEM.getKey(item).getPath();
		return itemPath.endsWith("_sword");
	}

	private static boolean recentlyProvoked(Raider raider, ServerPlayer player) {
		LivingEntity lastHurtBy = raider.getLastHurtByMob();
		return lastHurtBy != null
			&& lastHurtBy.getUUID().equals(player.getUUID())
			&& raider.tickCount - raider.getLastHurtByMobTimestamp() <= RECENT_PROVOCATION_TICKS;
	}

	private static boolean isWithinOutpostInfluence(SettlementState settlement, BlockPos position) {
		double radius = SettlementVillagers.settlementRadiusBlocks(settlement) + OUTPOST_INFLUENCE_MARGIN_BLOCKS;
		double dx = (settlement.center().getX() + 0.5D) - (position.getX() + 0.5D);
		double dz = (settlement.center().getZ() + 0.5D) - (position.getZ() + 0.5D);
		return (dx * dx) + (dz * dz) <= radius * radius;
	}

	private static String createOutpostId(ResourceKey<Level> dimension, BlockPos center) {
		String dimensionKey = dimension.identifier().toString().replace(':', '_').replace('/', '_');
		return "outpost:" + dimensionKey + "_" + center.getX() + "_" + center.getZ();
	}
}
