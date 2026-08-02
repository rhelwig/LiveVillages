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
			case "dirt", "farmland" -> isDirtFamilyItem(blockItem) ? ItemMatch.COMPATIBLE_MATERIAL : ItemMatch.NONE;
			case "water" -> stack.is(Items.WATER_BUCKET) ? ItemMatch.EXACT : ItemMatch.NONE;
			case "composter" -> stack.is(Items.COMPOSTER) ? ItemMatch.EXACT : ItemMatch.NONE;
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
			case "candle" -> isCandleItem(stack) ? ItemMatch.COMPATIBLE_MATERIAL : ItemMatch.NONE;
			case "copper_bulb" -> blockItem != null && isCopperBulbItem(blockItem) ? ItemMatch.COMPATIBLE_MATERIAL : ItemMatch.NONE;
			case "end_rod" -> stack.is(Items.END_ROD) ? ItemMatch.EXACT : ItemMatch.NONE;
			case "froglight" -> stack.is(Items.OCHRE_FROGLIGHT) || stack.is(Items.VERDANT_FROGLIGHT) || stack.is(Items.PEARLESCENT_FROGLIGHT)
				? ItemMatch.COMPATIBLE_MATERIAL
				: ItemMatch.NONE;
			case "glowstone" -> stack.is(Items.GLOWSTONE) ? ItemMatch.EXACT : ItemMatch.NONE;
			case "glow_berries" -> stack.is(Items.GLOW_BERRIES) ? ItemMatch.EXACT : ItemMatch.NONE;
			case "jack_o_lantern" -> stack.is(Items.JACK_O_LANTERN) ? ItemMatch.EXACT : ItemMatch.NONE;
			case "sea_lantern" -> stack.is(Items.SEA_LANTERN) ? ItemMatch.EXACT : ItemMatch.NONE;
			case "shroomlight" -> stack.is(Items.SHROOMLIGHT) ? ItemMatch.EXACT : ItemMatch.NONE;
			case "soul_lantern" -> stack.is(Items.SOUL_LANTERN) ? ItemMatch.EXACT : ItemMatch.NONE;
			case "redstone_block" -> stack.is(Items.REDSTONE_BLOCK) ? ItemMatch.EXACT : ItemMatch.NONE;
			case "redstone_lamp" -> stack.is(Items.REDSTONE_LAMP) ? ItemMatch.EXACT : ItemMatch.NONE;
			case "torch" -> stack.is(Items.TORCH) ? ItemMatch.EXACT : ItemMatch.NONE;
			case "soul_torch" -> stack.is(Items.SOUL_TORCH) ? ItemMatch.EXACT : ItemMatch.NONE;
			case "copper_torch" -> stack.is(Items.COPPER_TORCH) ? ItemMatch.EXACT : ItemMatch.NONE;
			case "lantern" -> stack.is(Items.LANTERN) ? ItemMatch.COMPATIBLE_MATERIAL : ItemMatch.NONE;
			default -> ItemMatch.NONE;
		};
	}

	private static boolean isCandleItem(ItemStack stack) {
		return stack.is(Items.CANDLE)
			|| stack.is(Items.WHITE_CANDLE)
			|| stack.is(Items.LIGHT_GRAY_CANDLE)
			|| stack.is(Items.GRAY_CANDLE)
			|| stack.is(Items.BLACK_CANDLE)
			|| stack.is(Items.BROWN_CANDLE)
			|| stack.is(Items.RED_CANDLE)
			|| stack.is(Items.ORANGE_CANDLE)
			|| stack.is(Items.YELLOW_CANDLE)
			|| stack.is(Items.LIME_CANDLE)
			|| stack.is(Items.GREEN_CANDLE)
			|| stack.is(Items.CYAN_CANDLE)
			|| stack.is(Items.LIGHT_BLUE_CANDLE)
			|| stack.is(Items.BLUE_CANDLE)
			|| stack.is(Items.PURPLE_CANDLE)
			|| stack.is(Items.MAGENTA_CANDLE)
			|| stack.is(Items.PINK_CANDLE);
	}

	private static boolean isDirtFamilyItem(BlockItem blockItem) {
		if (blockItem == null) {
			return false;
		}

		return blockItem.getBlock().defaultBlockState().is(BlockTags.DIRT)
			|| blockItem.getBlock().defaultBlockState().is(Blocks.GRASS_BLOCK)
			|| blockItem.getBlock().defaultBlockState().is(Blocks.DIRT)
			|| blockItem.getBlock().defaultBlockState().is(Blocks.COARSE_DIRT)
			|| blockItem.getBlock().defaultBlockState().is(Blocks.ROOTED_DIRT);
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

	private static boolean isCopperBulbItem(BlockItem blockItem) {
		return blockItem.getBlock().defaultBlockState().is(Blocks.COPPER_BULB)
			|| blockItem.getBlock().defaultBlockState().is(Blocks.EXPOSED_COPPER_BULB)
			|| blockItem.getBlock().defaultBlockState().is(Blocks.WEATHERED_COPPER_BULB)
			|| blockItem.getBlock().defaultBlockState().is(Blocks.OXIDIZED_COPPER_BULB)
			|| blockItem.getBlock().defaultBlockState().is(Blocks.WAXED_COPPER_BULB)
			|| blockItem.getBlock().defaultBlockState().is(Blocks.WAXED_EXPOSED_COPPER_BULB)
			|| blockItem.getBlock().defaultBlockState().is(Blocks.WAXED_WEATHERED_COPPER_BULB)
			|| blockItem.getBlock().defaultBlockState().is(Blocks.WAXED_OXIDIZED_COPPER_BULB);
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
