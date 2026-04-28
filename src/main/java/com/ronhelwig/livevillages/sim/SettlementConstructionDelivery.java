package com.ronhelwig.livevillages.sim;

import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record SettlementConstructionDelivery(
	String villagerId,
	String settlementId,
	String buildSiteId,
	String blockPosition,
	String materialKey,
	int amount,
	long pickedUpTick
) {
	public static final Codec<SettlementConstructionDelivery> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.fieldOf("villager_id").forGetter(SettlementConstructionDelivery::villagerId),
		Codec.STRING.fieldOf("settlement_id").forGetter(SettlementConstructionDelivery::settlementId),
		Codec.STRING.fieldOf("build_site_id").forGetter(SettlementConstructionDelivery::buildSiteId),
		Codec.STRING.fieldOf("block_position").forGetter(SettlementConstructionDelivery::blockPosition),
		Codec.STRING.optionalFieldOf("material_key", "").forGetter(SettlementConstructionDelivery::materialKey),
		Codec.INT.optionalFieldOf("amount", 1).forGetter(SettlementConstructionDelivery::amount),
		Codec.LONG.optionalFieldOf("picked_up_tick", 0L).forGetter(SettlementConstructionDelivery::pickedUpTick)
	).apply(instance, SettlementConstructionDelivery::new));

	public SettlementConstructionDelivery {
		Objects.requireNonNull(villagerId, "villagerId");
		Objects.requireNonNull(settlementId, "settlementId");
		Objects.requireNonNull(buildSiteId, "buildSiteId");
		Objects.requireNonNull(blockPosition, "blockPosition");
		Objects.requireNonNull(materialKey, "materialKey");
		amount = Math.max(1, amount);
	}

	public SettlementConstructionDelivery withAssignment(String newBlockPosition, int newAmount, long tick) {
		return new SettlementConstructionDelivery(villagerId, settlementId, buildSiteId, newBlockPosition, materialKey, newAmount, tick);
	}
}
