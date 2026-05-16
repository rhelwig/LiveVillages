package com.ronhelwig.livevillages.menu;

import java.util.List;
import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record BakeryBountyView(
	String goodsKey,
	List<String> unlockOutputGoodsKeys,
	List<String> usedInOutputGoodsKeys
) {
	public static final Codec<BakeryBountyView> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.fieldOf("goods_key").forGetter(BakeryBountyView::goodsKey),
		Codec.STRING.listOf().optionalFieldOf("unlock_output_goods_keys", List.of()).forGetter(BakeryBountyView::unlockOutputGoodsKeys),
		Codec.STRING.listOf().optionalFieldOf("used_in_output_goods_keys", List.of()).forGetter(BakeryBountyView::usedInOutputGoodsKeys)
	).apply(instance, BakeryBountyView::new));

	public BakeryBountyView {
		Objects.requireNonNull(goodsKey, "goodsKey");
		unlockOutputGoodsKeys = List.copyOf(unlockOutputGoodsKeys);
		usedInOutputGoodsKeys = List.copyOf(usedInOutputGoodsKeys);
	}
}
