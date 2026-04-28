package com.ronhelwig.livevillages.sim;

import java.util.Comparator;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record RegionKey(ResourceKey<Level> dimension, int x, int z) {
	public static final int REGION_SIZE_BLOCKS = 512;
	public static final Codec<RegionKey> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(RegionKey::dimension),
		Codec.INT.fieldOf("x").forGetter(RegionKey::x),
		Codec.INT.fieldOf("z").forGetter(RegionKey::z)
	).apply(instance, RegionKey::new));
	public static final Comparator<RegionKey> COMPARATOR = Comparator
		.comparing((RegionKey region) -> region.dimension().identifier().toString())
		.thenComparingInt(RegionKey::x)
		.thenComparingInt(RegionKey::z);

	public static RegionKey fromBlockPos(ResourceKey<Level> dimension, BlockPos pos) {
		return new RegionKey(dimension, Math.floorDiv(pos.getX(), REGION_SIZE_BLOCKS), Math.floorDiv(pos.getZ(), REGION_SIZE_BLOCKS));
	}

	public static RegionKey midpoint(ResourceKey<Level> dimension, BlockPos from, BlockPos to) {
		long midX = (((long) from.getX()) + to.getX()) / 2L;
		long midZ = (((long) from.getZ()) + to.getZ()) / 2L;
		return new RegionKey(dimension, Math.floorDiv((int) midX, REGION_SIZE_BLOCKS), Math.floorDiv((int) midZ, REGION_SIZE_BLOCKS));
	}

	public String asDebugString() {
		return dimension.identifier() + ":" + x + "," + z;
	}
}
