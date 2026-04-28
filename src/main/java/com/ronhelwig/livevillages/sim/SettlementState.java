package com.ronhelwig.livevillages.sim;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record SettlementState(
	String id,
	String name,
	ResourceKey<Level> dimension,
	BlockPos center,
	SettlementKind kind,
	Map<String, Integer> population,
	Map<String, Integer> wealth,
	Map<String, Integer> stock,
	int housingCapacity,
	double comfort,
	double security,
	int defenseLevel,
	double growthProgress,
	List<SettlementProject> projects,
	long lastUpdateTick,
	long createdTick
) {
	public static final Codec<SettlementState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.fieldOf("id").forGetter(SettlementState::id),
		Codec.STRING.optionalFieldOf("name", "Unnamed Settlement").forGetter(SettlementState::name),
		Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(SettlementState::dimension),
		BlockPos.CODEC.fieldOf("center").forGetter(SettlementState::center),
		SettlementKind.CODEC.fieldOf("kind").forGetter(SettlementState::kind),
		Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("population", Map.of()).forGetter(SettlementState::population),
		Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("wealth", Map.of()).forGetter(SettlementState::wealth),
		Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("stock", Map.of()).forGetter(SettlementState::stock),
		Codec.INT.optionalFieldOf("housing_capacity", 0).forGetter(SettlementState::housingCapacity),
		Codec.DOUBLE.optionalFieldOf("comfort", 1.0D).forGetter(SettlementState::comfort),
		Codec.DOUBLE.optionalFieldOf("security", 0.0D).forGetter(SettlementState::security),
		Codec.INT.optionalFieldOf("defense_level", 0).forGetter(SettlementState::defenseLevel),
		Codec.DOUBLE.optionalFieldOf("growth_progress", 0.0D).forGetter(SettlementState::growthProgress),
		SettlementProject.CODEC.listOf().optionalFieldOf("projects", List.of()).forGetter(SettlementState::projects),
		Codec.LONG.optionalFieldOf("last_update_tick", 0L).forGetter(SettlementState::lastUpdateTick),
		Codec.LONG.optionalFieldOf("created_tick", 0L).forGetter(SettlementState::createdTick)
	).apply(instance, SettlementState::new));

	public SettlementState {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(dimension, "dimension");
		Objects.requireNonNull(center, "center");
		Objects.requireNonNull(kind, "kind");
		population = Map.copyOf(population);
		wealth = Map.copyOf(wealth);
		stock = Map.copyOf(stock);
		projects = List.copyOf(projects);
	}

	public static SettlementState create(String id, String name, ResourceKey<Level> dimension, BlockPos center, SettlementKind kind) {
		return new SettlementState(id, name, dimension, center, kind, Map.of(), Map.of(), Map.of(), 0, 1.0D, 0.0D, 0, 0.0D, List.of(), 0L, 0L);
	}

	public RegionKey region() {
		return RegionKey.fromBlockPos(dimension, center);
	}

	public int totalPopulation() {
		return population.values().stream()
			.mapToInt(Integer::intValue)
			.sum();
	}

	public SettlementState withLastUpdateTick(long tick) {
		return new SettlementState(id, name, dimension, center, kind, population, wealth, stock, housingCapacity, comfort, security, defenseLevel, growthProgress, projects, tick, createdTick);
	}

	public SettlementState withHousingCapacity(int newHousingCapacity) {
		return new SettlementState(id, name, dimension, center, kind, population, wealth, stock, newHousingCapacity, comfort, security, defenseLevel, growthProgress, projects, lastUpdateTick, createdTick);
	}

	public SettlementState withPopulation(Map<String, Integer> newPopulation) {
		return new SettlementState(id, name, dimension, center, kind, newPopulation, wealth, stock, housingCapacity, comfort, security, defenseLevel, growthProgress, projects, lastUpdateTick, createdTick);
	}

	public SettlementState withStock(Map<String, Integer> newStock) {
		return new SettlementState(id, name, dimension, center, kind, population, wealth, newStock, housingCapacity, comfort, security, defenseLevel, growthProgress, projects, lastUpdateTick, createdTick);
	}

	public SettlementState withGrowthProgress(double newGrowthProgress) {
		return new SettlementState(id, name, dimension, center, kind, population, wealth, stock, housingCapacity, comfort, security, defenseLevel, newGrowthProgress, projects, lastUpdateTick, createdTick);
	}

	public SettlementState withSimulationState(
		Map<String, Integer> newPopulation,
		Map<String, Integer> newWealth,
		Map<String, Integer> newStock,
		int newHousingCapacity,
		double newComfort,
		double newSecurity,
		int newDefenseLevel,
		double newGrowthProgress,
		List<SettlementProject> newProjects,
		long tick
	) {
		return new SettlementState(
			id,
			name,
			dimension,
			center,
			kind,
			newPopulation,
			newWealth,
			newStock,
			newHousingCapacity,
			newComfort,
			newSecurity,
			newDefenseLevel,
			newGrowthProgress,
			newProjects,
			tick,
			createdTick
		);
	}
}
