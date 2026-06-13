package com.ronhelwig.livevillages.network;

import java.util.List;
import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;

public record SurveyorMapSnapshot(
	String statusMessage,
	String settlementName,
	int settlementTier,
	String timeLabel,
	BlockPos center,
	int radius,
	int boundaryRadius,
	List<SurveyorMapRoadView> roads,
	List<BlockPos> water,
	List<SurveyorMapStructureView> structures,
	List<SurveyorMapPointView> points,
	List<BlockPos> roadwrights,
	List<BlockPos> roadwrightRoutes,
	List<BlockPos> plannedRoadwork,
	boolean fogOfWarEnabled,
	List<BlockPos> observedAreas
) {
	public static final Codec<SurveyorMapSnapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.optionalFieldOf("status_message", "").forGetter(SurveyorMapSnapshot::statusMessage),
		Codec.STRING.optionalFieldOf("settlement_name", "").forGetter(SurveyorMapSnapshot::settlementName),
		Codec.INT.optionalFieldOf("settlement_tier", 1).forGetter(SurveyorMapSnapshot::settlementTier),
		Codec.STRING.optionalFieldOf("time_label", "").forGetter(SurveyorMapSnapshot::timeLabel),
		BlockPos.CODEC.fieldOf("center").forGetter(SurveyorMapSnapshot::center),
		Codec.INT.optionalFieldOf("radius", 64).forGetter(SurveyorMapSnapshot::radius),
		Codec.INT.optionalFieldOf("boundary_radius", 64).forGetter(SurveyorMapSnapshot::boundaryRadius),
		SurveyorMapRoadView.CODEC.listOf().optionalFieldOf("roads", List.of()).forGetter(SurveyorMapSnapshot::roads),
		BlockPos.CODEC.listOf().optionalFieldOf("water", List.of()).forGetter(SurveyorMapSnapshot::water),
		SurveyorMapStructureView.CODEC.listOf().optionalFieldOf("structures", List.of()).forGetter(SurveyorMapSnapshot::structures),
		SurveyorMapPointView.CODEC.listOf().optionalFieldOf("points", List.of()).forGetter(SurveyorMapSnapshot::points),
		BlockPos.CODEC.listOf().optionalFieldOf("roadwrights", List.of()).forGetter(SurveyorMapSnapshot::roadwrights),
		BlockPos.CODEC.listOf().optionalFieldOf("roadwright_routes", List.of()).forGetter(SurveyorMapSnapshot::roadwrightRoutes),
		BlockPos.CODEC.listOf().optionalFieldOf("planned_roadwork", List.of()).forGetter(SurveyorMapSnapshot::plannedRoadwork),
		Codec.BOOL.optionalFieldOf("fog_of_war_enabled", false).forGetter(SurveyorMapSnapshot::fogOfWarEnabled),
		BlockPos.CODEC.listOf().optionalFieldOf("observed_areas", List.of()).forGetter(SurveyorMapSnapshot::observedAreas)
	).apply(instance, SurveyorMapSnapshot::new));

	public SurveyorMapSnapshot {
		Objects.requireNonNull(statusMessage, "statusMessage");
		Objects.requireNonNull(settlementName, "settlementName");
		Objects.requireNonNull(timeLabel, "timeLabel");
		Objects.requireNonNull(center, "center");
		roads = List.copyOf(roads);
		water = List.copyOf(water);
		structures = List.copyOf(structures);
		points = List.copyOf(points);
		roadwrights = List.copyOf(roadwrights);
		roadwrightRoutes = List.copyOf(roadwrightRoutes);
		plannedRoadwork = List.copyOf(plannedRoadwork);
		observedAreas = List.copyOf(observedAreas);
	}

	public static SurveyorMapSnapshot unavailable(String statusMessage, BlockPos center) {
		return new SurveyorMapSnapshot(statusMessage, "", 1, "", center, 64, 64, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), false, List.of());
	}

	public boolean available() {
		return statusMessage.isBlank();
	}
}
