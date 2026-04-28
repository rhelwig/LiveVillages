package com.ronhelwig.livevillages.network;

import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;

public record BuildSitePreviewBlockView(
	BlockPos pos,
	String materialKey,
	String blockId
) {
	public static final Codec<BuildSitePreviewBlockView> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		BlockPos.CODEC.fieldOf("pos").forGetter(BuildSitePreviewBlockView::pos),
		Codec.STRING.optionalFieldOf("material_key", "").forGetter(BuildSitePreviewBlockView::materialKey),
		Codec.STRING.optionalFieldOf("block_id", "").forGetter(BuildSitePreviewBlockView::blockId)
	).apply(instance, BuildSitePreviewBlockView::new));

	public BuildSitePreviewBlockView {
		Objects.requireNonNull(pos, "pos");
		Objects.requireNonNull(materialKey, "materialKey");
		Objects.requireNonNull(blockId, "blockId");
		pos = pos.immutable();
	}

	public boolean canUseItem(ItemStack stack) {
		if (stack.isEmpty() || materialKey.isBlank()) {
			return false;
		}

		BlockItem blockItem = stack.getItem() instanceof BlockItem candidate ? candidate : null;

		if (blockItem != null
			&& BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()).toString().equals(blockId)) {
			return true;
		}

		return switch (materialKey) {
			case "logs" -> stack.is(ItemTags.LOGS);
			case "planks" -> stack.is(ItemTags.PLANKS);
			case "cobblestone" -> stack.is(Items.COBBLESTONE);
			case "glass" -> stack.is(Items.GLASS);
			case "stairs" -> blockItem != null && blockItem.getBlock().defaultBlockState().is(BlockTags.WOODEN_STAIRS);
			case "slab" -> blockItem != null && blockItem.getBlock().defaultBlockState().is(BlockTags.WOODEN_SLABS);
			case "door" -> blockItem != null && blockItem.getBlock() instanceof DoorBlock && blockItem.getBlock().defaultBlockState().is(BlockTags.WOODEN_DOORS);
			case "fence" -> blockItem != null && blockItem.getBlock().defaultBlockState().is(BlockTags.WOODEN_FENCES);
			case "fence_gate" -> blockItem != null && blockItem.getBlock().defaultBlockState().is(BlockTags.FENCE_GATES);
			case "bed" -> blockItem != null && blockItem.getBlock() instanceof BedBlock && stack.is(ItemTags.BEDS);
			case "torch" -> stack.is(Items.TORCH);
			default -> false;
		};
	}
}
