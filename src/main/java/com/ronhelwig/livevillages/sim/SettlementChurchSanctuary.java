package com.ronhelwig.livevillages.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Completed church interiors are sanctuary: intentional harm against anyone
 * standing inside is cancelled, and mobs drop those targets at the door.
 * Environmental damage is left alone. Volumes are cached from completed chapel
 * build sites so combat checks stay a few AABB tests.
 */
public final class SettlementChurchSanctuary {
	private static final AtomicBoolean DIRTY = new AtomicBoolean(true);
	private static final Map<ResourceKey<Level>, AABB[]> VOLUMES = new ConcurrentHashMap<>();

	private SettlementChurchSanctuary() {
	}

	public static void invalidate() {
		DIRTY.set(true);
	}

	public static boolean protectsFrom(LivingEntity victim, DamageSource source) {
		if (victim == null || source == null || !(victim.level() instanceof ServerLevel level)) {
			return false;
		}

		return isIntentionalHarm(source) && isInside(level, victim.position());
	}

	public static boolean shouldSuppressMobTarget(Mob mob, LivingEntity target) {
		if (mob == null || target == null || !(mob.level() instanceof ServerLevel level)) {
			return false;
		}

		return isInside(level, target.position()) || isInside(level, mob.position());
	}

	public static boolean isInside(ServerLevel level, Vec3 position) {
		if (level == null || position == null) {
			return false;
		}

		rebuildIfNeeded(level.getServer());
		AABB[] boxes = VOLUMES.get(level.dimension());
		if (boxes == null) {
			return false;
		}

		for (AABB box : boxes) {
			if (box.contains(position)) {
				return true;
			}
		}

		return false;
	}

	public static boolean isIntentionalHarm(DamageSource source) {
		if (source == null || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}

		if (source.is(DamageTypeTags.IS_FALL)
			|| source.is(DamageTypeTags.IS_DROWNING)
			|| source.is(DamageTypeTags.IS_FREEZING)
			|| source.is(DamageTypeTags.PANIC_ENVIRONMENTAL_CAUSES)) {
			return false;
		}

		if (source.is(DamageTypeTags.IS_FIRE) && source.getEntity() == null && source.getDirectEntity() == null) {
			return false;
		}

		return source.getEntity() != null || source.getDirectEntity() != null;
	}

	public static List<AABB> volumesFor(BlockPos origin, Direction facing, int churchTier) {
		List<AABB> volumes = new ArrayList<>();
		if (churchTier <= 1) {
			volumes.add(box(origin, facing, -3, 3, 2, 9, 1, 3));
			return volumes;
		}

		volumes.add(box(origin, facing, -1, 1, -2, 0, 1, 3));
		volumes.add(box(origin, facing, -3, 3, 2, 11, 1, 4));
		if (churchTier >= 3) {
			volumes.add(box(origin, facing, -1, 1, 9, 11, 5, 12));
		}

		return volumes;
	}

	static AABB box(
		BlockPos origin,
		Direction facing,
		int minRight,
		int maxRight,
		int minForward,
		int maxForward,
		int minUp,
		int maxUp
	) {
		double minX = Double.POSITIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double minZ = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		double maxZ = Double.NEGATIVE_INFINITY;

		for (int right : new int[] {minRight, maxRight}) {
			for (int forward : new int[] {minForward, maxForward}) {
				for (int up : new int[] {minUp, maxUp}) {
					BlockPos pos = origin.relative(facing.getClockWise(), right).relative(facing, forward).above(up);
					minX = Math.min(minX, pos.getX());
					minY = Math.min(minY, pos.getY());
					minZ = Math.min(minZ, pos.getZ());
					maxX = Math.max(maxX, pos.getX() + 1.0D);
					maxY = Math.max(maxY, pos.getY() + 1.0D);
					maxZ = Math.max(maxZ, pos.getZ() + 1.0D);
				}
			}
		}

		return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
	}

	private static void rebuildIfNeeded(MinecraftServer server) {
		if (server == null || !DIRTY.compareAndSet(true, false)) {
			return;
		}

		Map<ResourceKey<Level>, List<AABB>> next = new ConcurrentHashMap<>();
		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(server);
		for (SettlementState settlement : savedData.getSettlements()) {
			for (SettlementBuildSite buildSite : savedData.getBuildSitesForSettlement(settlement.id())) {
				if (buildSite.blueprintId() != SettlementBuildSiteType.CLERIC_SHRINE || !buildSite.complete()) {
					continue;
				}

				int tier = SettlementConstruction.churchCivicTier(buildSite);
				if (tier <= 0) {
					continue;
				}

				next.computeIfAbsent(settlement.dimension(), key -> new ArrayList<>())
					.addAll(volumesFor(buildSite.origin(), buildSite.facing(), tier));
			}
		}

		VOLUMES.clear();
		next.forEach((dimension, boxes) -> VOLUMES.put(dimension, boxes.toArray(AABB[]::new)));
	}
}
