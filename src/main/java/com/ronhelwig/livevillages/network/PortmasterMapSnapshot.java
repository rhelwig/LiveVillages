package com.ronhelwig.livevillages.network;

import java.util.List;
import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;

public record PortmasterMapSnapshot(
	String statusMessage,
	String settlementName,
	String timeLabel,
	boolean hasCartographer,
	BlockPos center,
	int radius,
	int terrainRadius,
	List<String> terrainRows,
	List<PortmasterMapPortView> ports
) {
	public static final Codec<PortmasterMapSnapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.optionalFieldOf("status_message", "").forGetter(PortmasterMapSnapshot::statusMessage),
		Codec.STRING.optionalFieldOf("settlement_name", "").forGetter(PortmasterMapSnapshot::settlementName),
		Codec.STRING.optionalFieldOf("time_label", "").forGetter(PortmasterMapSnapshot::timeLabel),
		Codec.BOOL.optionalFieldOf("has_cartographer", false).forGetter(PortmasterMapSnapshot::hasCartographer),
		BlockPos.CODEC.fieldOf("center").forGetter(PortmasterMapSnapshot::center),
		Codec.INT.optionalFieldOf("radius", 128).forGetter(PortmasterMapSnapshot::radius),
		Codec.INT.optionalFieldOf("terrain_radius", 128).forGetter(PortmasterMapSnapshot::terrainRadius),
		Codec.STRING.listOf().optionalFieldOf("terrain_rows", List.of()).forGetter(PortmasterMapSnapshot::terrainRows),
		PortmasterMapPortView.CODEC.listOf().optionalFieldOf("ports", List.of()).forGetter(PortmasterMapSnapshot::ports)
	).apply(instance, PortmasterMapSnapshot::new));

	public PortmasterMapSnapshot {
		Objects.requireNonNull(statusMessage, "statusMessage");
		Objects.requireNonNull(settlementName, "settlementName");
		Objects.requireNonNull(timeLabel, "timeLabel");
		Objects.requireNonNull(center, "center");
		terrainRows = List.copyOf(terrainRows);
		ports = List.copyOf(ports);
	}

	public static PortmasterMapSnapshot unavailable(String statusMessage, BlockPos center) {
		return new PortmasterMapSnapshot(statusMessage, "", "", false, center, 128, 128, List.of(), List.of());
	}

	public boolean available() {
		return statusMessage.isBlank();
	}
}
