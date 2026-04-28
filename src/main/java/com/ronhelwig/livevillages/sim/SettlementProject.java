package com.ronhelwig.livevillages.sim;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record SettlementProject(
	String id,
	SettlementProjectType type,
	String targetSettlementId,
	double progress,
	double requiredProgress
) {
	public static final Codec<SettlementProject> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.fieldOf("id").forGetter(SettlementProject::id),
		SettlementProjectType.CODEC.fieldOf("type").forGetter(SettlementProject::type),
		Codec.STRING.optionalFieldOf("target_settlement_id", "").forGetter(SettlementProject::targetSettlementId),
		Codec.DOUBLE.optionalFieldOf("progress", 0.0D).forGetter(SettlementProject::progress),
		Codec.DOUBLE.optionalFieldOf("required_progress", 1.0D).forGetter(SettlementProject::requiredProgress)
	).apply(instance, SettlementProject::new));

	public SettlementProject withProgress(double newProgress) {
		return new SettlementProject(id, type, targetSettlementId, newProgress, requiredProgress);
	}

	public int progressPercent() {
		if (requiredProgress <= 0.0D) {
			return 100;
		}

		return Math.max(0, Math.min(100, (int) Math.round((progress / requiredProgress) * 100.0D)));
	}
}
