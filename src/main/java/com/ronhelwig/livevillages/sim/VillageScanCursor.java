package com.ronhelwig.livevillages.sim;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

public record VillageScanCursor(
	ResourceKey<Level> dimension,
	int centerChunkX,
	int centerChunkZ,
	int ring,
	int indexInRing
) {
	public static final Codec<VillageScanCursor> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(VillageScanCursor::dimension),
		Codec.INT.fieldOf("center_chunk_x").forGetter(VillageScanCursor::centerChunkX),
		Codec.INT.fieldOf("center_chunk_z").forGetter(VillageScanCursor::centerChunkZ),
		Codec.INT.optionalFieldOf("ring", 0).forGetter(VillageScanCursor::ring),
		Codec.INT.optionalFieldOf("index_in_ring", 0).forGetter(VillageScanCursor::indexInRing)
	).apply(instance, VillageScanCursor::new));

	public static VillageScanCursor create(ResourceKey<Level> dimension, ChunkPos centerChunk) {
		return new VillageScanCursor(dimension, centerChunk.x(), centerChunk.z(), 0, 0);
	}

	public boolean matchesOrigin(ResourceKey<Level> dimension, ChunkPos centerChunk) {
		return this.dimension.equals(dimension) && centerChunkX == centerChunk.x() && centerChunkZ == centerChunk.z();
	}

	public ChunkPos currentChunk() {
		if (ring <= 0) {
			return new ChunkPos(centerChunkX, centerChunkZ);
		}

		int topEdgeLength = ring * 2 + 1;
		int rightEdgeLength = ring * 2;
		int bottomEdgeLength = ring * 2;
		int step = Math.floorMod(indexInRing, ring * 8);

		if (step < topEdgeLength) {
			return new ChunkPos(centerChunkX - ring + step, centerChunkZ - ring);
		}

		step -= topEdgeLength;

		if (step < rightEdgeLength) {
			return new ChunkPos(centerChunkX + ring, centerChunkZ - ring + 1 + step);
		}

		step -= rightEdgeLength;

		if (step < bottomEdgeLength) {
			return new ChunkPos(centerChunkX + ring - 1 - step, centerChunkZ + ring);
		}

		step -= bottomEdgeLength;
		return new ChunkPos(centerChunkX - ring, centerChunkZ + ring - 1 - step);
	}

	public VillageScanCursor advance() {
		if (ring <= 0) {
			return new VillageScanCursor(dimension, centerChunkX, centerChunkZ, 1, 0);
		}

		int nextIndex = indexInRing + 1;
		int perimeterLength = ring * 8;

		if (nextIndex < perimeterLength) {
			return new VillageScanCursor(dimension, centerChunkX, centerChunkZ, ring, nextIndex);
		}

		return new VillageScanCursor(dimension, centerChunkX, centerChunkZ, ring + 1, 0);
	}
}
