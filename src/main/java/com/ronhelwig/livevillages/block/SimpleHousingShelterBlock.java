package com.ronhelwig.livevillages.block;

import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockBehaviour;

import com.ronhelwig.livevillages.sim.SettlementConstruction;
import com.ronhelwig.livevillages.sim.SettlementBuildSite;
import com.ronhelwig.livevillages.sim.SettlementBuildSiteType;

public class SimpleHousingShelterBlock extends ShelterAnchorBlock {
	public static final MapCodec<SimpleHousingShelterBlock> CODEC = simpleCodec(SimpleHousingShelterBlock::new);

	public SimpleHousingShelterBlock(BlockBehaviour.Properties properties) {
		super(properties, "Simple Housing Shelter");
	}

	@Override
	protected MapCodec<? extends net.minecraft.world.level.block.HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected SettlementConstruction.WorkstationBuildResult tryStartShelterBuild(
		ServerLevel level,
		BlockPos doorPos,
		Direction facing,
		String settlementId,
		Map<String, Integer> stock,
		Optional<SettlementBuildSite> existingBuildSite
	) {
		return SettlementConstruction.tryStartSimpleHousingShelterAtDoor(level, doorPos, facing, settlementId, stock, existingBuildSite);
	}

	@Override
	protected Map<String, Integer> recipeGoods() {
		return Map.of("bed", 1, "planks", 8);
	}

	@Override
	protected SettlementBuildSiteType buildSiteType() {
		return SettlementBuildSiteType.SIMPLE_HOUSING_SHELTER;
	}
}
