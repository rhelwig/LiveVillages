package com.ronhelwig.livevillages.menu;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record TradeBoardInventoryEntryView(
	String rowKey,
	int slotIndex,
	String exactItemKey,
	String stockKey,
	String tradeGoodsKey,
	String label,
	int totalCount,
	int bundleSize,
	boolean hasStoredContents,
	int storedContentStacks,
	int wantedAmount,
	int tradePricePercent,
	boolean wanted,
	boolean canOfferBundle
) {
	public static final Codec<TradeBoardInventoryEntryView> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.fieldOf("row_key").forGetter(TradeBoardInventoryEntryView::rowKey),
		Codec.INT.optionalFieldOf("slot_index", -1).forGetter(TradeBoardInventoryEntryView::slotIndex),
		Codec.STRING.optionalFieldOf("exact_item_key", "").forGetter(TradeBoardInventoryEntryView::exactItemKey),
		Codec.STRING.optionalFieldOf("stock_key", "").forGetter(TradeBoardInventoryEntryView::stockKey),
		Codec.STRING.optionalFieldOf("trade_goods_key", "").forGetter(TradeBoardInventoryEntryView::tradeGoodsKey),
		Codec.STRING.fieldOf("label").forGetter(TradeBoardInventoryEntryView::label),
		Codec.INT.optionalFieldOf("total_count", 0).forGetter(TradeBoardInventoryEntryView::totalCount),
		Codec.INT.optionalFieldOf("bundle_size", 0).forGetter(TradeBoardInventoryEntryView::bundleSize),
		Codec.BOOL.optionalFieldOf("has_stored_contents", false).forGetter(TradeBoardInventoryEntryView::hasStoredContents),
		Codec.INT.optionalFieldOf("stored_content_stacks", 0).forGetter(TradeBoardInventoryEntryView::storedContentStacks),
		Codec.INT.optionalFieldOf("wanted_amount", 0).forGetter(TradeBoardInventoryEntryView::wantedAmount),
		Codec.INT.optionalFieldOf("trade_price_percent", 100).forGetter(TradeBoardInventoryEntryView::tradePricePercent),
		Codec.BOOL.optionalFieldOf("wanted", false).forGetter(TradeBoardInventoryEntryView::wanted),
		Codec.BOOL.optionalFieldOf("can_offer_bundle", false).forGetter(TradeBoardInventoryEntryView::canOfferBundle)
	).apply(instance, TradeBoardInventoryEntryView::new));

	public boolean hasTradeGoodsKey() {
		return !tradeGoodsKey.isBlank();
	}
}
