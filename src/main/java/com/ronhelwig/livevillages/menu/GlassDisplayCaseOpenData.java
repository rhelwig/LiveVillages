package com.ronhelwig.livevillages.menu;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;

public record GlassDisplayCaseOpenData(
	BlockPos casePos,
	boolean bakeryContext,
	List<BakeryBountyView> bakeryBounties,
	List<BakeryBarterPriceView> bakeryBarterPrices
) {
	public GlassDisplayCaseOpenData(BlockPos casePos, boolean bakeryContext, List<BakeryBountyView> bakeryBounties) {
		this(casePos, bakeryContext, bakeryBounties, List.of());
	}

	public static final Codec<GlassDisplayCaseOpenData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		BlockPos.CODEC.fieldOf("case_pos").forGetter(GlassDisplayCaseOpenData::casePos),
		Codec.BOOL.optionalFieldOf("bakery_context", false).forGetter(GlassDisplayCaseOpenData::bakeryContext),
		BakeryBountyView.CODEC.listOf().optionalFieldOf("bakery_bounties", List.of()).forGetter(GlassDisplayCaseOpenData::bakeryBounties),
		BakeryBarterPriceView.CODEC.listOf().optionalFieldOf("bakery_barter_prices", List.of()).forGetter(GlassDisplayCaseOpenData::bakeryBarterPrices)
	).apply(instance, GlassDisplayCaseOpenData::new));
}
