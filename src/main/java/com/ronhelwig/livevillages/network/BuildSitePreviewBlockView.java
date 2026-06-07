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
import net.minecraft.world.level.block.Blocks;

import com.ronhelwig.livevillages.content.LiveVillagesBlocks;

public record BuildSitePreviewBlockView(
	BlockPos pos,
	String materialKey,
	String blockId
) {
	public enum ItemMatch {
		NONE,
		COMPATIBLE_MATERIAL,
		EXACT
	}

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
		return itemMatch(stack) != ItemMatch.NONE;
	}

	public boolean isExcavation() {
		return materialKey.isBlank() && blockId.equals("minecraft:air");
	}

	public ItemMatch itemMatch(ItemStack stack) {
		if (stack.isEmpty() || materialKey.isBlank()) {
			return ItemMatch.NONE;
		}

		BlockItem blockItem = stack.getItem() instanceof BlockItem candidate ? candidate : null;

		if (blockItem != null
			&& BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()).toString().equals(blockId)) {
			return ItemMatch.EXACT;
		}

		return switch (materialKey) {
			case "logs" -> stack.is(ItemTags.LOGS) ? ItemMatch.COMPATIBLE_MATERIAL : ItemMatch.NONE;
			case "planks" -> stack.is(ItemTags.PLANKS) ? ItemMatch.COMPATIBLE_MATERIAL : ItemMatch.NONE;
			case "cobblestone" -> isStoneFamilyItem(blockItem) ? ItemMatch.COMPATIBLE_MATERIAL : ItemMatch.NONE;
			case "glass" -> stack.is(Items.GLASS) ? ItemMatch.EXACT : ItemMatch.NONE;
			case "glass_display_case" -> blockItem != null && LiveVillagesBlocks.isGlassDisplayCase(blockItem.getBlock().defaultBlockState()) ? ItemMatch.COMPATIBLE_MATERIAL : ItemMatch.NONE;
			case "smithing_station" -> isSmithingStationItem(blockItem) ? ItemMatch.COMPATIBLE_MATERIAL : ItemMatch.NONE;
			case "iron_bars" -> stack.is(Items.IRON_BARS) ? ItemMatch.EXACT : ItemMatch.NONE;
			case "copper_bars" -> blockItem != null && isCopperBars(blockItem) ? ItemMatch.EXACT : ItemMatch.NONE;
			case "stairs" -> blockItem != null && blockItem.getBlock().defaultBlockState().is(BlockTags.WOODEN_STAIRS) ? ItemMatch.COMPATIBLE_MATERIAL : ItemMatch.NONE;
			case "slab" -> blockItem != null && blockItem.getBlock().defaultBlockState().is(BlockTags.WOODEN_SLABS) ? ItemMatch.COMPATIBLE_MATERIAL : ItemMatch.NONE;
			case "door" -> blockItem != null && blockItem.getBlock() instanceof DoorBlock && blockItem.getBlock().defaultBlockState().is(BlockTags.WOODEN_DOORS) ? ItemMatch.COMPATIBLE_MATERIAL : ItemMatch.NONE;
			case "fence" -> blockItem != null && blockItem.getBlock().defaultBlockState().is(BlockTags.WOODEN_FENCES) ? ItemMatch.COMPATIBLE_MATERIAL : ItemMatch.NONE;
			case "fence_gate" -> blockItem != null && blockItem.getBlock().defaultBlockState().is(BlockTags.FENCE_GATES) ? ItemMatch.COMPATIBLE_MATERIAL : ItemMatch.NONE;
			case "bed" -> blockItem != null && blockItem.getBlock() instanceof BedBlock && stack.is(ItemTags.BEDS) ? ItemMatch.COMPATIBLE_MATERIAL : ItemMatch.NONE;
			case "torch" -> stack.is(Items.TORCH) ? ItemMatch.EXACT : ItemMatch.NONE;
			case "lantern" -> stack.is(Items.LANTERN) ? ItemMatch.COMPATIBLE_MATERIAL : ItemMatch.NONE;
			default -> ItemMatch.NONE;
		};
	}

	private static boolean isStoneFamilyItem(BlockItem blockItem) {
		if (blockItem == null) {
			return false;
		}

		return blockItem.getBlock().defaultBlockState().is(Blocks.COBBLESTONE)
			|| blockItem.getBlock().defaultBlockState().is(Blocks.MOSSY_COBBLESTONE)
			|| blockItem.getBlock().defaultBlockState().is(Blocks.STONE)
			|| blockItem.getBlock().defaultBlockState().is(Blocks.SMOOTH_STONE)
			|| blockItem.getBlock().defaultBlockState().is(Blocks.GRANITE)
			|| blockItem.getBlock().defaultBlockState().is(Blocks.DIORITE)
			|| blockItem.getBlock().defaultBlockState().is(Blocks.ANDESITE)
			|| blockItem.getBlock().defaultBlockState().is(Blocks.TUFF)
			|| blockItem.getBlock().defaultBlockState().is(Blocks.CALCITE)
			|| blockItem.getBlock().defaultBlockState().is(Blocks.DEEPSLATE)
			|| blockItem.getBlock().defaultBlockState().is(Blocks.COBBLED_DEEPSLATE)
			|| blockItem.getBlock().defaultBlockState().is(Blocks.SANDSTONE)
			|| blockItem.getBlock().defaultBlockState().is(Blocks.RED_SANDSTONE)
			|| blockItem.getBlock().defaultBlockState().is(BlockTags.BASE_STONE_OVERWORLD);
	}

	private static boolean isCopperBars(BlockItem blockItem) {
		return BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()).getPath().endsWith("copper_bars");
	}

	private static boolean isSmithingStationItem(BlockItem blockItem) {
		if (blockItem == null) {
			return false;
		}

		return blockItem.getBlock().defaultBlockState().is(Blocks.BLAST_FURNACE)
			|| blockItem.getBlock().defaultBlockState().is(Blocks.SMITHING_TABLE)
			|| blockItem.getBlock().defaultBlockState().is(Blocks.GRINDSTONE);
	}
}
