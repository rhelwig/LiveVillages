package com.ronhelwig.livevillages.network;

import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;

public record PortmasterMapPortView(
	BlockPos pos,
	String name,
	int distanceBlocks,
	String kind
) {
	public static final Codec<PortmasterMapPortView> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		BlockPos.CODEC.fieldOf("pos").forGetter(PortmasterMapPortView::pos),
		Codec.STRING.optionalFieldOf("name", "").forGetter(PortmasterMapPortView::name),
		Codec.INT.optionalFieldOf("distance_blocks", 0).forGetter(PortmasterMapPortView::distanceBlocks),
		Codec.STRING.optionalFieldOf("kind", "known_port").forGetter(PortmasterMapPortView::kind)
	).apply(instance, PortmasterMapPortView::new));

	public PortmasterMapPortView {
		Objects.requireNonNull(pos, "pos");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(kind, "kind");
	}
}
