package com.ronhelwig.livevillages.sim;

import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record SettlementBuildBlockState(
	String position,
	String blueprintSymbol,
	String expectedMaterialKey,
	SettlementBuildBlockStatus status,
	String blocker
) {
	public static final Codec<SettlementBuildBlockState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.fieldOf("position").forGetter(SettlementBuildBlockState::position),
		Codec.STRING.fieldOf("blueprint_symbol").forGetter(SettlementBuildBlockState::blueprintSymbol),
		Codec.STRING.optionalFieldOf("expected_material_key", "").forGetter(SettlementBuildBlockState::expectedMaterialKey),
		SettlementBuildBlockStatus.CODEC.optionalFieldOf("status", SettlementBuildBlockStatus.PENDING).forGetter(SettlementBuildBlockState::status),
		Codec.STRING.optionalFieldOf("blocker", "").forGetter(SettlementBuildBlockState::blocker)
	).apply(instance, SettlementBuildBlockState::new));

	public SettlementBuildBlockState {
		Objects.requireNonNull(position, "position");
		Objects.requireNonNull(blueprintSymbol, "blueprintSymbol");
		Objects.requireNonNull(expectedMaterialKey, "expectedMaterialKey");
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(blocker, "blocker");
	}

	public static SettlementBuildBlockState placed(String position, char blueprintSymbol, String expectedMaterialKey) {
		return new SettlementBuildBlockState(position, Character.toString(blueprintSymbol), expectedMaterialKey, SettlementBuildBlockStatus.PLACED, "");
	}

	public static SettlementBuildBlockState playerPlaced(String position, char blueprintSymbol, String expectedMaterialKey) {
		return new SettlementBuildBlockState(position, Character.toString(blueprintSymbol), expectedMaterialKey, SettlementBuildBlockStatus.PLAYER_PLACED, "");
	}

	public static SettlementBuildBlockState pending(String position, char blueprintSymbol, String expectedMaterialKey) {
		return new SettlementBuildBlockState(position, Character.toString(blueprintSymbol), expectedMaterialKey, SettlementBuildBlockStatus.PENDING, "");
	}

	public SettlementBuildBlockState withStatus(SettlementBuildBlockStatus newStatus, String newBlocker) {
		return new SettlementBuildBlockState(position, blueprintSymbol, expectedMaterialKey, newStatus, newBlocker == null ? "" : newBlocker);
	}

	public SettlementBuildBlockState withExpectedMaterialKey(String newExpectedMaterialKey) {
		return new SettlementBuildBlockState(position, blueprintSymbol, newExpectedMaterialKey == null ? "" : newExpectedMaterialKey, status, blocker);
	}

	public SettlementBuildBlockState withBlueprintSymbol(char newBlueprintSymbol, String newExpectedMaterialKey) {
		return new SettlementBuildBlockState(position, Character.toString(newBlueprintSymbol), newExpectedMaterialKey == null ? "" : newExpectedMaterialKey, status, blocker);
	}
}
