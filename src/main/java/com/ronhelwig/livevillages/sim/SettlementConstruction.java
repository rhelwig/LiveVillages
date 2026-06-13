package com.ronhelwig.livevillages.sim;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.ShelfBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;

import com.ronhelwig.livevillages.block.BakersCounterBlock;
import com.ronhelwig.livevillages.block.GardenerWorkstationBlock;
import com.ronhelwig.livevillages.block.GlassDisplayCaseBlock;
import com.ronhelwig.livevillages.block.GuardPostBlock;
import com.ronhelwig.livevillages.block.HoneySeparatorBlock;
import com.ronhelwig.livevillages.block.PalisadeGatehouseBlock;
import com.ronhelwig.livevillages.block.PortmasterAnchorBlock;
import com.ronhelwig.livevillages.block.ScribeDeskBlock;
import com.ronhelwig.livevillages.block.TradeBoardBlock;
import com.ronhelwig.livevillages.block.ShelterAnchorBlock;
import com.ronhelwig.livevillages.content.LiveVillagesBlocks;

public final class SettlementConstruction {
	private static final int STANDARD_BUILD_RADIUS_BLOCKS = 18;
	private static final int VILLAGE_BUILD_RADIUS_BLOCKS = 28;
	private static final int SCAN_DEPTH_BLOCKS = 12;
	private static final int SCAN_HEIGHT_BLOCKS = 20;
	private static final int MAX_TERRAIN_CUT_BLOCKS = 3;
	private static final int MAX_TERRAIN_FILL_BLOCKS = 3;
	private static final int MAX_SITE_LANDSCAPING_BLOCKS = 40;
	private static final int MAX_WORKSHOP_SITE_LANDSCAPING_BLOCKS = 72;
	private static final int MAX_MINE_ENTRANCE_SITE_LANDSCAPING_BLOCKS = 140;
	private static final int MAX_MINE_ENTRANCE_TERRAIN_CUT_BLOCKS = 8;
	private static final int MIN_STRUCTURE_SPACING_BLOCKS = 3;
	private static final int MINE_ENTRANCE_FRONT_ACCESS_DEPTH_BLOCKS = 3;
	private static final int MINE_ENTRANCE_FRONT_ACCESS_HEIGHT_BLOCKS = 2;
	private static final int MAX_CONSTRUCTION_TREE_BLOCKS = 192;
	private static final double WORKSTATION_SETTLEMENT_LINK_RADIUS_BLOCKS = 128.0D;
	private static final double TRADE_BOARD_FOUNDING_MIN_SPACING_BLOCKS = 100.0D;
	private static final int VANILLA_WORKSTATION_SCAN_RADIUS_BLOCKS = 64;
	private static final int VANILLA_WORKSTATION_SCAN_DEPTH_BELOW_SURFACE_BLOCKS = 64;
	private static final int DOCK_LENGTH_BLOCKS = 8;
	private static final int DOCK_HALF_WIDTH_BLOCKS = 1;
	private static final int DOCK_SUPPORT_SPACING_BLOCKS = 4;
	private static final int MAX_DOCK_SHORE_REPLACEMENT_BLOCKS = 2;
	private static final int DOCK_SHORE_REPLACEMENT_ROWS = 2;
	private static final int DOCK_DEEP_WATER_END_ROWS = 2;
	private static final int PALISADE_WALL_HEIGHT_BLOCKS = 4;
	private static final int PALISADE_POINT_SCAN_RADIUS_BLOCKS = 64;
	private static final int PALISADE_CONTROL_TOUCH_DISTANCE_BLOCKS = 4;
	private static final int PALISADE_POINT_REPLAN_RADIUS_BLOCKS = 10;
	private static final int PALISADE_DUPLICATE_WALL_RADIUS_BLOCKS = 8;
	private static final int PALISADE_DUPLICATE_WALL_MIN_COLUMNS = 6;
	private static final int PALISADE_DUPLICATE_WALL_COVERAGE_PERCENT = 55;
	private static final int PALISADE_ROUTE_GAP_MAX_DISTANCE_SQUARED = 8;
	private static final int PALISADE_INVALID_WATER_MIN_COLUMNS = 2;
	private static final int PALISADE_INVALID_WATER_COVERAGE_PERCENT = 15;
	private static final int PALISADE_PLAYER_OVERRIDE_RADIUS_BLOCKS = 3;
	private static final int PALISADE_PLAYER_OVERRIDE_MIN_LOGS = 2;
	private static final double PALISADE_SECURITY_TARGET_RADIUS_RATIO = 0.80D;
	private static final int LIGHTHOUSE_BASE_LEVELS = 4;
	private static final int LIGHTHOUSE_TOWER_LEVELS = 4;
	private static final int LIGHTHOUSE_WATER_RADIUS_BLOCKS = 6;
	private static final int MIN_HARBOR_WATER_SURFACE_COLUMNS = 32;
	private static final int MIN_HARBOR_DEEP_WATER_COLUMNS = 12;
	private static final int BLOCK_UPDATE_FLAGS = 3;
	private static final String STRUCTURE_MARGIN_GROUND_BACKFILL_KEY = "__margin_ground_backfill__";
	private static final ThreadLocal<Boolean> SUPPRESS_BLOCKED_STRUCTURE_SIGNS = ThreadLocal.withInitial(() -> false);
	/*
	 * Blueprint legend:
	 *
	 * Placement symbols:
	 * A = empty space
	 * B = bed at bed-designated coordinates, otherwise slab
	 * C = cobblestone-family block, except logs for Forester workshops and planks for wood-heavy workshops
	 * D = door
	 * E = excavate / clear to air
	 * F = fence
	 * G = fence gate
	 * H = chest
	 * I = iron/copper bars
	 * K = campfire
	 * L = log
	 * M = stone / cobblestone-family block
	 * N = hanging lantern
	 * P = planks
	 * Q = glass display case
	 * R = ladder
	 * S = stairs
	 * T = wall torch
	 * V = glass / window block
	 * W = workstation
	 *
	 * Orientation symbols:
	 * . = no explicit orientation
	 * F/B/R/L = face forward / backward / right / left
	 * f/b/r/l = same facing, but use top-half stairs
	 */
	private static final String[][] FIVE_BY_EIGHT_GABLED_HUT_ORIENTATIONS = new String[][] {
		{
			".....",
			".....",
			".....",
			".....",
			".....",
			".....",
			".....",
			"....."
		},
		{
			".....",
			".....",
			".....",
			".....",
			".....",
			".....",
			".....",
			"....."
		},
		{
			".....",
			".....",
			".....",
			".....",
			".....",
			".....",
			".....",
			"....."
		},
		{
			".....",
			".....",
			".....",
			".....",
			".....",
			".....",
			".....",
			"....."
		},
		{
			"R...L",
			"R...L",
			"R...L",
			"R...L",
			"R...L",
			"R...L",
			"R...L",
			"R...L"
		},
		{
			".R.L.",
			".R.L.",
			".R.L.",
			".R.L.",
			".R.L.",
			".R.L.",
			".R.L.",
			".R.L."
		},
		{
			".....",
			".....",
			".....",
			".....",
			".....",
			".....",
			".....",
			"....."
		}
	};
	private static final String[][] FIVE_BY_EIGHT_GABLED_HUT_WITH_PORCH_STAIR_ORIENTATIONS = new String[][] {
		{
			".....",
			".....",
			".....",
			".....",
			".....",
			".....",
			".....",
			"....."
		},
		{
			".....",
			".....",
			".....",
			".....",
			".....",
			".....",
			".....",
			"....."
		},
		{
			".....",
			".....",
			".....",
			".....",
			".....",
			".....",
			".....",
			"....."
		},
		{
			".....",
			".....",
			".....",
			".....",
			".....",
			"l...r",
			"l...r",
			".fff."
		},
		{
			"R...L",
			"R...L",
			"R...L",
			"R...L",
			"R...L",
			"R...L",
			"R...L",
			"R...L"
		},
		{
			".R.L.",
			".R.L.",
			".R.L.",
			".R.L.",
			".R.L.",
			".R.L.",
			".R.L.",
			".R.L."
		},
		{
			".....",
			".....",
			".....",
			".....",
			".....",
			".....",
			".....",
			"....."
		}
	};
	private static final StructureBlueprint CARPENTER_WORKSHOP_BLUEPRINT = new StructureBlueprint(
		-2,
		-4,
		6,
		new String[][] {
			{
				"PPPPP",
				"PPPPP",
				"PPPPP",
				"PPPPP",
				"PPPPP",
				"PPPPP",
				"PPPPP",
				"PPPPP"
			},
			{
				"LPPPL",
				"PBABP",
				"PBABP",
				"PAAAP",
				"LPDPL",
				"HAAAA",
				"AAAAA",
				"LAWAL"
			},
			{
				"LPVPL",
				"PAAAP",
				"VAAAV",
				"PAAAP",
				"LVDVL",
				"AAAAA",
				"AAAAA",
				"LAAAL"
			},
			{
				"LPPPL",
				"PATAP",
				"PAAAP",
				"PAAAP",
				"LPPPL",
				"SAAAS",
				"SAAAS",
				"LSSSL"
			},
			{
				"SPPPS",
				"SAAAS",
				"SAAAS",
				"SAAAS",
				"SPPPS",
				"SAAAS",
				"SAAAS",
				"SPPPS"
			},
			{
				"ASPSA",
				"ASBSA",
				"ASBSA",
				"ASBSA",
				"ASBSA",
				"ASASA",
				"ASASA",
				"ASPSA"
			},
			{
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA"
			}
		},
		FIVE_BY_EIGHT_GABLED_HUT_WITH_PORCH_STAIR_ORIENTATIONS
	);
	private static final StructureBlueprint ROADWRIGHT_WORKSHOP_BLUEPRINT = new StructureBlueprint(
		-2,
		-4,
		6,
		new String[][] {
			{
				"PPPPP",
				"PPPPP",
				"PPPPP",
				"PPPPP",
				"PPPPP",
				"PPPPP",
				"PPPPP",
				"PPPPP"
			},
			{
				"LMMML",
				"MBABM",
				"MBABM",
				"MAAAM",
				"LMDML",
				"AAAAA",
				"AAAAA",
				"LAWAL"
			},
			{
				"LMVML",
				"MAAAM",
				"VAAAV",
				"MAAAM",
				"LVDVL",
				"AAAAA",
				"AAAAA",
				"LAAAL"
			},
			{
				"LMMML",
				"MATAM",
				"MAAAM",
				"MAAAM",
				"LMMML",
				"SAAAS",
				"SAAAS",
				"LSSSL"
			},
			{
				"SPPPS",
				"SAAAS",
				"SAAAS",
				"SAAAS",
				"SPPPS",
				"SAAAS",
				"SAAAS",
				"SPPPS"
			},
			{
				"ASPSA",
				"ASBSA",
				"ASBSA",
				"ASBSA",
				"ASBSA",
				"ASASA",
				"ASASA",
				"ASPSA"
			},
			{
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA"
			}
		},
		FIVE_BY_EIGHT_GABLED_HUT_WITH_PORCH_STAIR_ORIENTATIONS
	);
	private static final StructureBlueprint MASON_WORKSHOP_BLUEPRINT = new StructureBlueprint(
		-2,
		-4,
		6,
		new String[][] {
			{
				"MMMMM",
				"MMMMM",
				"MMMMM",
				"MMMMM",
				"MMMMM",
				"MMMMM",
				"MMMMM",
				"MMMMM"
			},
			{
				"LMMML",
				"MBABM",
				"MBABM",
				"MAAAM",
				"LMDML",
				"MHAAM",
				"MAAAM",
				"LAWAL"
			},
			{
				"LMVML",
				"MAAAM",
				"VAAAV",
				"MAAAM",
				"LVDVL",
				"MAAAM",
				"MAAAM",
				"LAAAL"
			},
			{
				"LMMML",
				"MATAM",
				"MAAAM",
				"MAAAM",
				"LMMML",
				"SAAAS",
				"SAAAS",
				"LSSSL"
			},
			{
				"SPPPS",
				"SAAAS",
				"SAAAS",
				"SAAAS",
				"SPPPS",
				"SAAAS",
				"SAAAS",
				"SPPPS"
			},
			{
				"ASPSA",
				"ASBSA",
				"ASBSA",
				"ASBSA",
				"ASBSA",
				"ASASA",
				"ASASA",
				"ASPSA"
			},
			{
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA"
			}
		},
		FIVE_BY_EIGHT_GABLED_HUT_WITH_PORCH_STAIR_ORIENTATIONS
	);
	private static final StructureBlueprint FORESTER_WORKSHOP_BLUEPRINT = new StructureBlueprint(
		-2,
		-4,
		6,
		new String[][] {
			{
				"LLLLL",
				"LLLLL",
				"LLLLL",
				"LLLLL",
				"LLLLL",
				"LLLLL",
				"LLLLL",
				"LLLLL"
			},
			{
				"LLLLL",
				"LBABL",
				"LBABL",
				"LAAAL",
				"LLDLL",
				"AAAAA",
				"AAAAA",
				"LAWAL"
			},
			{
				"LLVLL",
				"LAAAL",
				"VAAAV",
				"LAAAL",
				"LVDVL",
				"AAAAA",
				"AAAAA",
				"LAAAL"
			},
			{
				"LLLLL",
				"LATLL",
				"LAAAL",
				"LAAAL",
				"LLLLL",
				"SAAAS",
				"SAAAS",
				"LSSSL"
			},
			{
				"SPPPS",
				"SAAAS",
				"SAAAS",
				"SAAAS",
				"SPPPS",
				"SAAAS",
				"SAAAS",
				"SPPPS"
			},
			{
				"ASPSA",
				"ASBSA",
				"ASBSA",
				"ASBSA",
				"ASBSA",
				"ASASA",
				"ASASA",
				"ASPSA"
			},
			{
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA"
			}
		},
		FIVE_BY_EIGHT_GABLED_HUT_WITH_PORCH_STAIR_ORIENTATIONS
	);
	private static final StructureBlueprint MINE_ENTRANCE_BLUEPRINT = new StructureBlueprint(
		-2,
		-3,
		-4,
		5,
		new String[][] {
			{
				"AAAAAA",
				"AAAAAA",
				"AARRAA",
				"AAEEAA",
				"AAAAAA",
				"AAAAAA"
			},
			{
				"AAAAAA",
				"AAAAAA",
				"AARRAA",
				"AAEEAA",
				"AAAAAA",
				"AAAAAA"
			},
			{
				"AAAAAA",
				"AAAAAA",
				"AARRAA",
				"AAEEAA",
				"AAAAAA",
				"AAAAAA"
			},
			{
				"AAAAAA",
				"AAAAAA",
				"AARRAA",
				"AAEEAA",
				"AAAAAA",
				"AAAAAA"
			},
			{
				"LMMMML",
				"MMMMMM",
				"MERREM",
				"MMEEMM",
				"MMMMMM",
				"LLMMLL"
			},
			{
				"LMMMML",
				"MEMMEM",
				"MERREM",
				"MEEEEM",
				"MEEEEM",
				"LLDDLL"
			},
			{
				"LMMMML",
				"MEEEEM",
				"MEEEEM",
				"MEEEEM",
				"MEEEEM",
				"LLDDLL"
			},
			{
				"LMMMML",
				"MEEEEM",
				"MENEEM",
				"MEEEEM",
				"MEEEEM",
				"LLLLLL"
			},
			{
				"LMMMML",
				"MMMMMM",
				"MMMMMM",
				"MMMMMM",
				"MMMMMM",
				"LMMMML"
			}
		},
		new String[][] {
			{
				"......",
				"......",
				"......",
				"..FF..",
				"......",
				"......"
			},
			{
				"......",
				"......",
				"......",
				"..FF..",
				"......",
				"......"
			},
			{
				"......",
				"......",
				"......",
				"..FF..",
				"......",
				"......"
			},
			{
				"......",
				"......",
				"......",
				"..FF..",
				"......",
				"......"
			},
			{
				"......",
				"......",
				"......",
				"......",
				"......",
				"......"
			},
			{
				"......",
				"......",
				"......",
				"......",
				"......",
				"......"
			},
			{
				"......",
				"......",
				"......",
				"......",
				"......",
				"......"
			},
			{
				"......",
				"......",
				"......",
				"......",
				"......",
				".RRRR."
			},
			{
				"......",
				"......",
				"......",
				"......",
				"......",
				"......"
			}
		}
	);
	private static final StructureBlueprint FLETCHER_HUT_BLUEPRINT = new StructureBlueprint(
		-2,
		-4,
		6,
		new String[][] {
			{
				"MMMMM",
				"MMMMM",
				"MMMMM",
				"MMMMM",
				"MMMMM",
				"MMMMM",
				"MMMMM",
				"MMMMM"
			},
			{
				"LPPPL",
				"PBABP",
				"PBABP",
				"PAAAP",
				"LPDPL",
				"PAAAP",
				"PAAAP",
				"LAWAL"
			},
			{
				"LPVPL",
				"PAAAP",
				"VAAAV",
				"PAAAP",
				"LVDVL",
				"PAAAP",
				"PAAAP",
				"LAAAL"
			},
			{
				"LPPPL",
				"PATAP",
				"PAAAP",
				"PAAAP",
				"LPPPL",
				"SAAAS",
				"SAAAS",
				"LSSSL"
			},
			{
				"SPPPS",
				"SAAAS",
				"SAAAS",
				"SAAAS",
				"SPPPS",
				"SAAAS",
				"SAAAS",
				"SPPPS"
			},
			{
				"ASPSA",
				"ASBSA",
				"ASBSA",
				"ASBSA",
				"ASBSA",
				"ASASA",
				"ASASA",
				"ASPSA"
			},
			{
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA"
			}
		},
		FIVE_BY_EIGHT_GABLED_HUT_WITH_PORCH_STAIR_ORIENTATIONS
	);
	private static final StructureBlueprint SCRIBE_OFFICE_BLUEPRINT = new StructureBlueprint(
		-2,
		-4,
		6,
		new String[][] {
			{
				"MMMMM",
				"MMMMM",
				"MMMMM",
				"MMMMM",
				"MMMMM",
				"MMMMM",
				"MMMMM",
				"MMMMM"
			},
			{
				"LPPPL",
				"PBABP",
				"PBABP",
				"PAAAP",
				"LPDPL",
				"HAAAH",
				"PAAAP",
				"LAWAL"
			},
			{
				"LVVVL",
				"PAAAP",
				"VAAAV",
				"PAAAP",
				"LVDVL",
				"PAAAP",
				"PAAAP",
				"LVVVL"
			},
			{
				"LPPPL",
				"PATAP",
				"PAAAP",
				"PAAAP",
				"LPPPL",
				"PHAHP",
				"PAAAP",
				"LPPPL"
			},
			{
				"SPPPS",
				"SAAAS",
				"SAAAS",
				"SAAAS",
				"SPPPS",
				"SAAAS",
				"SAAAS",
				"SPPPS"
			},
			{
				"ASPSA",
				"ASBSA",
				"ASBSA",
				"ASBSA",
				"ASBSA",
				"ASASA",
				"ASASA",
				"ASPSA"
			},
			{
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA"
			}
		},
		FIVE_BY_EIGHT_GABLED_HUT_ORIENTATIONS
	);
	private static final StructureBlueprint GUARD_POST_BLUEPRINT = new StructureBlueprint(
		-2,
		-4,
		6,
		new String[][] {
			{
				"MMMMM",
				"MMMMM",
				"MMMMM",
				"MMMMM",
				"MMMMM",
				"MMMMM",
				"MMMMM",
				"MMMMM"
			},
			{
				"LPPPL",
				"PBABP",
				"PBABP",
				"PAAAP",
				"LPDPL",
				"GAAAG",
				"FAAAF",
				"LFWFL"
			},
			{
				"LPVPL",
				"PAAAP",
				"VAAAV",
				"PAAAP",
				"LIDIL",
				"AAAAA",
				"AAAAA",
				"LAAAL"
			},
			{
				"LPPPL",
				"PATAP",
				"PAAAP",
				"PAAAP",
				"LPPPL",
				"FAAAF",
				"FAAAF",
				"LFFFL"
			},
			{
				"SPPPS",
				"SAAAS",
				"SAAAS",
				"SAAAS",
				"SPPPS",
				"SAAAS",
				"SAAAS",
				"SPPPS"
			},
			{
				"ASPSA",
				"ASBSA",
				"ASBSA",
				"ASBSA",
				"ASBSA",
				"ASASA",
				"ASASA",
				"ASPSA"
			},
			{
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA"
			}
		},
		FIVE_BY_EIGHT_GABLED_HUT_ORIENTATIONS
	);
	private static final StructureBlueprint GARDENER_SHED_BLUEPRINT = new StructureBlueprint(
		-2,
		-4,
		6,
		new String[][] {
			{
				"MMMMM",
				"MMMMM",
				"MMMMM",
				"MMMMM",
				"MMMMM",
				"MMMMM",
				"MMMMM",
				"MMMMM"
			},
			{
				"LPPPL",
				"PBABP",
				"PBABP",
				"PAAAP",
				"LPDPL",
				"FAAAF",
				"FAAAG",
				"LAWAL"
			},
			{
				"LPVPL",
				"PAAAP",
				"VAAAV",
				"PAAAP",
				"LVDVL",
				"FAAAF",
				"FAAAG",
				"LAAAL"
			},
			{
				"LPPPL",
				"PATAP",
				"PAAAP",
				"PAAAP",
				"LPPPL",
				"FAAAF",
				"FAAAG",
				"LFFFL"
			},
			{
				"SPPPS",
				"SAAAS",
				"SAAAS",
				"SAAAS",
				"SPPPS",
				"SAAAS",
				"SAAAS",
				"SPPPS"
			},
			{
				"ASPSA",
				"ASBSA",
				"ASBSA",
				"ASBSA",
				"ASBSA",
				"ASASA",
				"ASASA",
				"ASPSA"
			},
			{
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA"
			}
		},
		FIVE_BY_EIGHT_GABLED_HUT_ORIENTATIONS
	);
	private static final StructureBlueprint BEEKEEPER_APIARY_BLUEPRINT = new StructureBlueprint(
		-2,
		-4,
		6,
		new String[][] {
			{
				"MMMMM",
				"MMMMM",
				"MMMMM",
				"MMMMM",
				"MMMMM",
				"MMMMM",
				"MMMMM",
				"MMMMM"
			},
			{
				"LPPPL",
				"PBABP",
				"PBABP",
				"PAAAP",
				"LPDPL",
				"FAHAF",
				"FAAAG",
				"LAWAL"
			},
			{
				"LPVPL",
				"PAAAP",
				"VAAAV",
				"PAAAP",
				"LVDVL",
				"FAAAF",
				"FAAAG",
				"LAAAL"
			},
			{
				"LPPPL",
				"PATAP",
				"PAAAP",
				"PAAAP",
				"LPPPL",
				"FAAAF",
				"FAAAG",
				"LFFFL"
			},
			{
				"SPPPS",
				"SAAAS",
				"SAAAS",
				"SAAAS",
				"SPPPS",
				"SAAAS",
				"SAAAS",
				"SPPPS"
			},
			{
				"ASPSA",
				"ASBSA",
				"ASBSA",
				"ASBSA",
				"ASBSA",
				"ASASA",
				"ASASA",
				"ASPSA"
			},
			{
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA",
				"AABAA"
			}
		},
		FIVE_BY_EIGHT_GABLED_HUT_ORIENTATIONS
	);
	private static final StructureBlueprint CARTOGRAPHER_HOUSE_BLUEPRINT = new StructureBlueprint(
		-6,
		-3,
		7,
		new String[][] {
			{
				"AAAAAAAAAA",
				"AAMMMMMMMA",
				"AAMPPPPPMA",
				"AAMPPPPPMA",
				"AAMPPPPPMA",
				"AAMMMMMMMA",
				"AAAAAAAAAA"
			},
			{
				"AAAAAAAAAA",
				"AALMMMMMLA",
				"AAMFAAAAMA",
				"AADAAAWAMA",
				"AAMHAAAAMA",
				"AALMMMMMLA",
				"AAAAAAAAAA"
			},
			{
				"AAAAAAAAAA",
				"AALMVMVMLA",
				"AAMAAAAAMA",
				"AADAAAAAVA",
				"AAMAAAAAMA",
				"AALMVMVMLA",
				"AAAAAAAAAA"
			},
			{
				"AAAAAAAAAA",
				"AALMMMMMLA",
				"AAMAAAAAMA",
				"AAMAAAAAMA",
				"AAMAAAAAMA",
				"AALMMMMMLA",
				"AAAAAAAAAA"
			},
			{
				"ASSSSSSSSS",
				"ASLLLLLLLS",
				"AALAAAAALA",
				"ATLTAAATLA",
				"AALAAAAALA",
				"ASLLLLLLLS",
				"ASSSSSSSSS"
			},
			{
				"AAAAAAAAAA",
				"ASSSSSSSSS",
				"ASMAAAAAMS",
				"AAMAAAAAMA",
				"ASMAAAAAMS",
				"ASSSSSSSSS",
				"AAAAAAAAAA"
			},
			{
				"AAAAAAAAAA",
				"AAAAAAAAAA",
				"ASSSSSSSSS",
				"APPPPPPPPP",
				"ASSSSSSSSS",
				"AAAAAAAAAA",
				"AAAAAAAAAA"
			},
			{
				"AAAAAAAAAA",
				"AAAAAAAAAA",
				"AAAAAAAAAA",
				"ABBBBBBBBB",
				"AAAAAAAAAA",
				"AAAAAAAAAA",
				"AAAAAAAAAA"
			}
		},
		new String[][] {
			{
				"..........",
				"..........",
				"..........",
				"..........",
				"..........",
				"..........",
				".........."
			},
			{
				"..........",
				"..........",
				"..........",
				"..........",
				"..........",
				"..........",
				".........."
			},
			{
				"..........",
				"..........",
				"..........",
				"..........",
				"..........",
				"..........",
				".........."
			},
			{
				"..........",
				"..........",
				"..........",
				"..........",
				"..........",
				"..........",
				".........."
			},
			{
				".FFFFFFFFF",
				".b.......b",
				"..........",
				"..........",
				"..........",
				".f.......f",
				".BBBBBBBBB"
			},
			{
				"..........",
				".FFFFFFFFF",
				".b.......b",
				"..........",
				".f.......f",
				".BBBBBBBBB",
				".........."
			},
			{
				"..........",
				"..........",
				".FFFFFFFFF",
				"..........",
				".BBBBBBBBB",
				"..........",
				".........."
			},
			{
				"..........",
				"..........",
				"..........",
				"..........",
				"..........",
				"..........",
				".........."
			}
		}
	);
	private static final StructureBlueprint LIGHTHOUSE_BLUEPRINT = new StructureBlueprint(
		-1,
		-1,
		9,
		new String[][] {
			{
				"MMM",
				"MMM",
				"MMM"
			},
			{
				"MMM",
				"MMM",
				"MMM"
			},
			{
				"MMM",
				"MMM",
				"MMM"
			},
			{
				"MMM",
				"MMM",
				"MMM"
			},
			{
				"A",
				"M",
				"A"
			},
			{
				"A",
				"M",
				"A"
			},
			{
				"A",
				"M",
				"A"
			},
			{
				"A",
				"M",
				"A"
			},
			{
				"A",
				"K",
				"A"
			}
		}
	);
	private static final StructureBlueprint TRADING_POST_BLUEPRINT = new StructureBlueprint(
		-2,
		-4,
		6,
		new String[][] {
			{
				"MMMMM",
				"MMMMM",
				"MMMMM",
				"MMMMM",
				"MMMMM",
				"MMMMM",
				"MMMMM",
				"MMMMM"
			},
			{
				"LMMML",
				"MBAHM",
				"MBAHM",
				"MAAAM",
				"LMDML",
				"GAAAG",
				"FAAAF",
				"LFWFL"
			},
			{
				"LVVVL",
				"PAAAP",
				"VAAAV",
				"PAAAP",
				"LVDVL",
				"AAAAA",
				"AAAAA",
				"LAAAL"
			},
			{
				"LPPPL",
				"PATAP",
				"PAAAP",
				"PAAAP",
				"LPPPL",
				"SAAAS",
				"SAAAS",
				"LSSSL"
			},
			{
				"SSSSS",
				"PAAAP",
				"PAAAP",
				"PAAAP",
				"SSSSS",
				"BBBBB",
				"BBBBB",
				"BBBBB"
			},
			{
				"AAAAA",
				"SSSSS",
				"PPPPP",
				"SSSSS",
				"AAAAA",
				"AAAAA",
				"AAAAA",
				"AAAAA"
			},
			{
				"AAAAA",
				"AAAAA",
				"BBBBB",
				"AAAAA",
				"AAAAA",
				"AAAAA",
				"AAAAA",
				"AAAAA"
			}
		},
		new String[][] {
			{
				".....",
				".....",
				".....",
				".....",
				".....",
				".....",
				".....",
				"....."
			},
			{
				".....",
				".....",
				".....",
				".....",
				".....",
				".....",
				".....",
				"....."
			},
			{
				".....",
				".....",
				".....",
				".....",
				".....",
				".....",
				".....",
				"....."
			},
			{
				".....",
				".....",
				".....",
				".....",
				".....",
				"l...r",
				"l...r",
				".fff."
			},
			{
				"FFFFF",
				".....",
				".....",
				".....",
				"BBBBB",
				".....",
				".....",
				"....."
			},
			{
				".....",
				"FFFFF",
				".....",
				"BBBBB",
				".....",
				".....",
				".....",
				"....."
			},
			{
				".....",
				".....",
				".....",
				".....",
				".....",
				".....",
				".....",
				"....."
			}
		}
	);
	private static final StructureBlueprint BAKERY_BLUEPRINT = new StructureBlueprint(
		-2,
		-4,
		6,
		new String[][] {
			{
				"MMMMM",
				"MMMMM",
				"MMMMM",
				"MMMMM",
				"MMMMM",
				"MMMMM",
				"MMMMM",
				"MMMMM"
			},
			{
				"LMMML",
				"MBAHM",
				"MBAHM",
				"MAAAM",
				"LMDML",
				"GAAAG",
				"QAAAQ",
				"LQWQL"
			},
			{
				"LVVVL",
				"PAAAP",
				"VAAAV",
				"PAAAP",
				"LVDVL",
				"AAAAA",
				"AAAAA",
				"LAAAL"
			},
			{
				"LPPPL",
				"PATAP",
				"PAAAP",
				"PAAAP",
				"LPPPL",
				"SAAAS",
				"SAAAS",
				"LSSSL"
			},
			{
				"SSSSS",
				"PAAAP",
				"PAAAP",
				"PAAAP",
				"SSSSS",
				"BBBBB",
				"BBBBB",
				"BBBBB"
			},
			{
				"AAAAA",
				"SSSSS",
				"PPPPP",
				"SSSSS",
				"AAAAA",
				"AAAAA",
				"AAAAA",
				"AAAAA"
			},
			{
				"AAAAA",
				"AAAAA",
				"BBBBB",
				"AAAAA",
				"AAAAA",
				"AAAAA",
				"AAAAA",
				"AAAAA"
			}
		},
		new String[][] {
			{
				".....",
				".....",
				".....",
				".....",
				".....",
				".....",
				".....",
				"....."
			},
			{
				".....",
				".....",
				".....",
				".....",
				".....",
				".....",
				"L...R",
				".F.F."
			},
			{
				".....",
				".....",
				".....",
				".....",
				".....",
				".....",
				".....",
				"....."
			},
			{
				".....",
				".....",
				".....",
				".....",
				".....",
				"l...r",
				"l...r",
				".fff."
			},
			{
				"FFFFF",
				".....",
				".....",
				".....",
				"BBBBB",
				".....",
				".....",
				"....."
			},
			{
				".....",
				"FFFFF",
				".....",
				"BBBBB",
				".....",
				".....",
				".....",
				"....."
			},
			{
				".....",
				".....",
				".....",
				".....",
				".....",
				".....",
				".....",
				"....."
			}
		}
	);
	private static final StructureBlueprint HOUSING_SHELTER_BLUEPRINT = new StructureBlueprint(
		-2,
		-2,
		4,
		new String[][] {
			{
				"PPPPP",
				"PPPPP",
				"PPPPP",
				"PPPPP",
				"PPPPP"
			},
			{
				"LPPPL",
				"PBABP",
				"PBABP",
				"PAAAP",
				"LPDPL"
			},
			{
				"LPVPL",
				"VAAAV",
				"VAAAV",
				"VAAAV",
				"LVDVL"
			},
			{
				"PPPPP",
				"PAAAP",
				"PANAP",
				"PAAAP",
				"PPPPP"
			},
			{
				"PPPPP",
				"PPPPP",
				"PPPPP",
				"PPPPP",
				"PPPPP"
			}
		},
		new String[][] {
			{
				".....",
				".....",
				".....",
				".....",
				"....."
			},
			{
				".....",
				".....",
				".....",
				".....",
				"....."
			},
			{
				".....",
				".....",
				".....",
				".....",
				"....."
			},
			{
				".....",
				".....",
				".....",
				".....",
				"....."
			},
			{
				".....",
				".....",
				".....",
				".....",
				"..f.."
			}
		}
	);
	private static final StructureBlueprint SIMPLE_HOUSING_SHELTER_BLUEPRINT = new StructureBlueprint(
		-2,
		-1,
		3,
		new String[][] {
			{
				"PPPPP",
				"PPPPP",
				"PPPPP",
				"PPPPP"
			},
			{
				"PPPPP",
				"PBAAP",
				"PBAAP",
				"LPDPL"
			},
			{
				"PPPPP",
				"PATAP",
				"PAAAP",
				"LPDPL"
			},
			{
				"PPPPP",
				"PBBBP",
				"PBBBP",
				"PPSPP"
			}
		},
		new String[][] {
			{
				".....",
				".....",
				".....",
				"....."
			},
			{
				".....",
				".....",
				".....",
				"....."
			},
			{
				".....",
				"..F..",
				".....",
				"....."
			},
			{
				".....",
				".....",
				".....",
				"..f.."
			}
		}
	);
	// Facing points away from the settlement center; rows run from inside (-forward) to outside (+forward).
	private static final StructureBlueprint PALISADE_GATEHOUSE_BLUEPRINT = new StructureBlueprint(
		-3,
		-1,
		4,
		new String[][] {
			{
				"AAAAAAA",
				"LLLDLLL",
				"ALAAALA",
				"AAAAAAA"
			},
			{
				"AAAAAAA",
				"LLIDILL",
				"ALAAALA",
				"AAAAAAA"
			},
			{
				"BBBBBBB",
				"LLLLLLL",
				"ALLLLLA",
				"ATAAATA"
			},
			{
				"AAAAAAA",
				"LLLLLLL",
				"AAAAAAA",
				"AAAAAAA"
			}
		},
		new String[][] {
			{
				".......",
				".......",
				".......",
				"......."
			},
			{
				".......",
				".......",
				".......",
				"......."
			},
			{
				".......",
				"lllllll",
				"..lll..",
				".F...F."
			},
			{
				".......",
				"lllllll",
				".......",
				"......."
			}
		}
	);

	private SettlementConstruction() {
	}

	public static InfrastructureSurvey survey(ServerLevel level, SettlementState settlement) {
		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(level.getServer());
		String settlementId = settlement.id();
		long currentTick = level.getServer().getTickCount();
		LiveVillagesSavedData.CachedSurvey cached = savedData.surveyCache.get(settlementId);
		if (cached != null && (currentTick - cached.lastSurveyTick()) < LiveVillagesSavedData.SURVEY_CACHE_DURATION_TICKS) {
			return cached.survey();
		}
		InfrastructureSurvey survey = surveyUncached(level, settlement);
		savedData.surveyCache.put(settlementId, new LiveVillagesSavedData.CachedSurvey(survey, currentTick));
		return survey;
	}

	private static InfrastructureSurvey surveyUncached(ServerLevel level, SettlementState settlement) {
		BlockPos center = settlement.center();
		int radiusBlocks = buildRadius(settlement);
		int minY = Math.max(level.getMinY(), center.getY() - SCAN_DEPTH_BLOCKS);
		int maxY = Math.min(level.getMaxY() - 1, center.getY() + SCAN_HEIGHT_BLOCKS);
		BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();
		BuildSiteInfrastructure buildSiteInfrastructure = buildSiteInfrastructure(level, settlement);
		int housingCapacity = 0;
		int composters = 0;
		int storageBlocks = 0;
		int carpenterBenches = 0;
		int docks = buildSiteInfrastructure.completedDocks();
		int lighthouses = buildSiteInfrastructure.completedLighthouses();
		int waterSurfaceColumns = 0;
		int deepWaterColumns = 0;
		Set<BlockPos> countedLighthouseTops = new HashSet<>();
		Set<BlockPos> countedDockOrigins = new HashSet<>();

		for (int x = center.getX() - radiusBlocks; x <= center.getX() + radiusBlocks; x++) {
			for (int z = center.getZ() - radiusBlocks; z <= center.getZ() + radiusBlocks; z++) {
				BlockPos surfaceColumnPos = new BlockPos(x, center.getY(), z);

				if (!level.hasChunkAt(surfaceColumnPos)) {
					continue;
				}

				WaterColumnSurvey waterColumn = surveyWaterColumn(level, x, z);

				if (waterColumn.surfaceY() >= 0) {
					waterSurfaceColumns++;

					if (waterColumn.depth() >= 2) {
						deepWaterColumns++;
					}

					if (docks <= buildSiteInfrastructure.completedDocks()) {
						BlockPos candidateOrigin = new BlockPos(x, waterColumn.surfaceY(), z);

						for (Direction facing : Direction.Plane.HORIZONTAL) {
							if (isMinimalDock(level, candidateOrigin, facing) && countedDockOrigins.add(candidateOrigin.immutable())) {
								docks++;
								break;
							}
						}
					}
				}

				for (int y = minY; y <= maxY; y++) {
					scanPos.set(x, y, z);

					if (!level.isLoaded(scanPos)) {
						continue;
					}

					BlockState state = level.getBlockState(scanPos);

					if (state.getBlock() instanceof BedBlock && state.hasProperty(BedBlock.PART) && state.getValue(BedBlock.PART) == BedPart.FOOT) {
						housingCapacity++;
					} else if (state.is(Blocks.COMPOSTER)) {
						composters++;
					} else if (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST) || state.is(Blocks.BARREL)) {
						storageBlocks++;
					} else if (state.is(LiveVillagesBlocks.CARPENTER_BENCH)) {
						if (!buildSiteInfrastructure.incompleteCarpenterWorkshopWorkstations().contains(scanPos)) {
							carpenterBenches++;
						}
					} else if (state.is(Blocks.CAMPFIRE) && countedLighthouseTops.add(scanPos.immutable()) && isMinimalLighthouse(level, scanPos)) {
						lighthouses++;
					}
				}
			}
		}

		return new InfrastructureSurvey(
			true,
			housingCapacity,
			composters,
			storageBlocks,
			carpenterBenches,
			docks,
			lighthouses,
			waterSurfaceColumns,
			deepWaterColumns,
			buildSiteInfrastructure.incompleteDocks(),
			buildSiteInfrastructure.incompleteLighthouses(),
			buildSiteInfrastructure.completedTradingPosts(),
			buildSiteInfrastructure.incompleteTradingPosts(),
			buildSiteInfrastructure.incompleteCarpenterWorkshops(),
			buildSiteInfrastructure.completedPalisadeGatehouses(),
			buildSiteInfrastructure.completedPalisadeWallColumns(),
			buildSiteInfrastructure.expectedPalisadeWallColumns()
		);
	}

	public static CompletionResult tryCompleteProject(ServerLevel level, SettlementState settlement, SettlementProject project, Map<String, Integer> stock) {
		return switch (project.type()) {
			case TRADING_POST -> CompletionResult.notCompleted();
			case HOUSING -> tryBuildHousingShelter(level, settlement, stock);
			case CARPENTER_WORKSHOP -> tryBuildCarpenterWorkshop(level, settlement, stock);
			case DOCK -> tryBuildDock(level, settlement, stock);
			case LIGHTHOUSE -> tryBuildLighthouse(level, settlement, stock);
			case COMPOSTER -> tryPlaceComposter(level, settlement, stock);
			case STORAGE -> tryPlaceStorage(level, settlement, stock);
			case DEFENSE, ROAD -> CompletionResult.notCompleted();
		};
	}

	public static Optional<SettlementState> findWorkstationSettlement(ServerLevel level, BlockPos pos) {
		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(level.getServer());
		Optional<SettlementState> containingSettlement = savedData.findSettlementForPosition(
			level.dimension(),
			pos,
			settlement -> true
		);

		if (containingSettlement.isPresent()) {
			return containingSettlement;
		}

		return savedData.findNearestSettlement(
			level.dimension(),
			pos,
			WORKSTATION_SETTLEMENT_LINK_RADIUS_BLOCKS,
			settlement -> settlement.kind() != SettlementKind.OUTPOST
		);
	}

	public static Optional<SettlementState> findSettlementContainingPosition(ServerLevel level, BlockPos pos) {
		return LiveVillagesSavedData.get(level.getServer()).findSettlementForPosition(
			level.dimension(),
			pos,
			settlement -> settlement.kind() != SettlementKind.OUTPOST
		);
	}

	public static TradeBoardPlacementDecision evaluateTradeBoardPlacement(ServerLevel level, BlockPos boardPos) {
		LiveVillagesSavedData savedData = LiveVillagesSavedData.get(level.getServer());
		Optional<SettlementState> outpost = OutpostTrust.findOrCreateOutpostAt(level, savedData, boardPos);

		if (outpost.isPresent()) {
			SettlementState settlement = outpost.get();
			return TradeBoardPlacementDecision.linked(
				settlement,
				"This Trade Board will join " + settlement.name() + "."
			);
		}

		Optional<SettlementState> containingSettlement = findSettlementContainingPosition(level, boardPos);

		if (containingSettlement.isPresent()) {
			SettlementState settlement = containingSettlement.get();
			return TradeBoardPlacementDecision.linked(
				settlement,
				"This Trade Board will join " + settlement.name() + "."
			);
		}

		Optional<SettlementState> nearbySettlement = savedData.findNearestSettlement(
			level.dimension(),
			boardPos,
			TRADE_BOARD_FOUNDING_MIN_SPACING_BLOCKS,
			settlement -> settlement.kind() != SettlementKind.OUTPOST
		);

		if (nearbySettlement.isPresent()) {
			return TradeBoardPlacementDecision.blocked(
				"Too close to " + nearbySettlement.get().name() + " to found a new settlement here."
			);
		}

		return TradeBoardPlacementDecision.founding("This Trade Board will found a new Tier 1 settlement here.");
	}

	private static BuildSiteInfrastructure buildSiteInfrastructure(ServerLevel level, SettlementState settlement) {
		Set<BlockPos> incompleteCarpenterWorkshopWorkstations = new HashSet<>();
		int completedDocks = 0;
		int completedLighthouses = 0;
		int completedTradingPosts = 0;
		int incompleteDocks = 0;
		int incompleteLighthouses = 0;
		int incompleteTradingPosts = 0;
		int incompleteCarpenterWorkshops = 0;
		int completedPalisadeGatehouses = 0;
		Set<String> completedPalisadeWallColumns = new HashSet<>();

		for (SettlementBuildSite buildSite : LiveVillagesSavedData.get(level.getServer()).getBuildSitesForSettlement(settlement.id())) {
			if (buildSite.blueprintId() == SettlementBuildSiteType.DOCK) {
				if (buildSite.complete()) {
					completedDocks++;
				} else {
					incompleteDocks++;
				}
			} else if (buildSite.blueprintId() == SettlementBuildSiteType.LIGHTHOUSE) {
				if (buildSite.complete()) {
					completedLighthouses++;
				} else {
					incompleteLighthouses++;
				}
			} else if (buildSite.blueprintId() == SettlementBuildSiteType.TRADING_POST) {
				if (buildSite.complete()) {
					completedTradingPosts++;
				} else {
					incompleteTradingPosts++;
				}
			} else if (buildSite.blueprintId() == SettlementBuildSiteType.CARPENTER_WORKSHOP && !buildSite.complete()) {
				incompleteCarpenterWorkshops++;
				incompleteCarpenterWorkshopWorkstations.add(buildSite.workstationPos());
				incompleteCarpenterWorkshopWorkstations.add(buildSite.anchorPos());
			} else if (buildSite.blueprintId() == SettlementBuildSiteType.PALISADE_GATEHOUSE
				|| buildSite.blueprintId() == SettlementBuildSiteType.COPPER_PALISADE_GATEHOUSE) {
				if (buildSite.complete()) {
					completedPalisadeGatehouses++;
				}
			} else if (buildSite.blueprintId() == SettlementBuildSiteType.PALISADE_WALL) {
				addCompletedPalisadeWallColumns(completedPalisadeWallColumns, buildSite);
			}
		}

		return new BuildSiteInfrastructure(
			Set.copyOf(incompleteCarpenterWorkshopWorkstations),
			completedDocks,
			completedLighthouses,
			completedTradingPosts,
			incompleteDocks,
			incompleteLighthouses,
			incompleteTradingPosts,
			incompleteCarpenterWorkshops,
			completedPalisadeGatehouses,
			completedPalisadeWallColumns.size(),
			expectedPalisadeWallColumns(settlement)
		);
	}

	private static void addCompletedPalisadeWallColumns(Set<String> completedColumns, SettlementBuildSite buildSite) {
		if (buildSite.complete()) {
			palisadeWallLogColumns(buildSite).stream()
				.map(SettlementConstruction::columnKey)
				.forEach(completedColumns::add);
			return;
		}

		Map<String, Integer> placedLogBlocksByColumn = new LinkedHashMap<>();

		for (SettlementBuildBlockState block : buildSite.blocks()) {
			if (block.blueprintSymbol().isBlank()
				|| block.blueprintSymbol().charAt(0) != 'L'
				|| !isDefensivePalisadeBlockStatus(block.status())) {
				continue;
			}

			Optional<BlockPos> blockPos = buildSiteBlockPos(buildSite, block);
			if (blockPos.isEmpty()) {
				continue;
			}

			String columnKey = columnKey(blockPos.get());
			int placedLogBlocks = placedLogBlocksByColumn.getOrDefault(columnKey, 0) + 1;
			placedLogBlocksByColumn.put(columnKey, placedLogBlocks);

			if (placedLogBlocks >= PALISADE_WALL_HEIGHT_BLOCKS) {
				completedColumns.add(columnKey);
			}
		}
	}

	private static boolean isDefensivePalisadeBlockStatus(SettlementBuildBlockStatus status) {
		return status == SettlementBuildBlockStatus.PLACED || status == SettlementBuildBlockStatus.PLAYER_PLACED;
	}

	private static int expectedPalisadeWallColumns(SettlementState settlement) {
		double targetRadius = SettlementVillagers.settlementRadiusBlocks(settlement) * PALISADE_SECURITY_TARGET_RADIUS_RATIO;
		return Math.max(1, (int) Math.round(Math.PI * 2.0D * targetRadius));
	}

	public static WorkstationBuildResult tryStartCarpenterWorkshopAtWorkstation(
		ServerLevel level,
		BlockPos benchPos,
		Direction facing,
		String settlementId,
		Map<String, Integer> stock,
		Optional<SettlementBuildSite> existingBuildSite
	) {
		if (existingBuildSite.isPresent()) {
			return WorkstationBuildResult.resumed(updateBuildSiteMaterialStatus(existingBuildSite.get(), stock, level.getServer().getTickCount()));
		}

		if (isPositionInExistingShelteredStructure(level, benchPos)) {
			return WorkstationBuildResult.completed();
		}

		Direction horizontalFacing = facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : facing;
		BlockPos origin = benchPos.relative(horizontalFacing.getOpposite(), 3).below();
		AnchoredStructureSite site = findAnchoredStructureSiteOrPlaceBlockedSign(
			level,
			origin,
			horizontalFacing,
			StructureKind.CARPENTER_WORKSHOP,
			CARPENTER_WORKSHOP_BLUEPRINT,
			MAX_WORKSHOP_SITE_LANDSCAPING_BLOCKS,
			Set.of(benchPos.immutable()),
			benchPos,
			horizontalFacing
		);

		if (site == null) {
			return WorkstationBuildResult.blocked();
		}

		return WorkstationBuildResult.started(updateBuildSiteMaterialStatus(createPendingBuildSite(
			level,
			StructureKind.CARPENTER_WORKSHOP,
			CARPENTER_WORKSHOP_BLUEPRINT,
			settlementId,
			site.origin(),
			benchPos.immutable(),
			benchPos.immutable(),
			site.facing(),
			level.getServer().getTickCount()
		), stock, level.getServer().getTickCount()));
	}

	public static WorkstationBuildResult withBlockedStructureSignsSuppressed(Supplier<WorkstationBuildResult> starter) {
		boolean previous = SUPPRESS_BLOCKED_STRUCTURE_SIGNS.get();
		SUPPRESS_BLOCKED_STRUCTURE_SIGNS.set(true);

		try {
			return starter.get();
		} finally {
			SUPPRESS_BLOCKED_STRUCTURE_SIGNS.set(previous);
		}
	}

	public static WorkstationBuildResult tryStartRoadwrightWorkshopAtWorkstation(
		ServerLevel level,
		BlockPos tablePos,
		Direction facing,
		String settlementId,
		Map<String, Integer> stock,
		Optional<SettlementBuildSite> existingBuildSite
	) {
		if (existingBuildSite.isPresent()) {
			return WorkstationBuildResult.resumed(updateBuildSiteMaterialStatus(existingBuildSite.get(), stock, level.getServer().getTickCount()));
		}

		if (isPositionInExistingShelteredStructure(level, tablePos)) {
			return WorkstationBuildResult.completed();
		}

		Direction horizontalFacing = facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : facing;
		BlockPos origin = tablePos.relative(horizontalFacing.getOpposite(), 3).below();
		AnchoredStructureSite site = findAnchoredStructureSiteOrPlaceBlockedSign(
			level,
			origin,
			horizontalFacing,
			StructureKind.ROADWRIGHT_WORKSHOP,
			ROADWRIGHT_WORKSHOP_BLUEPRINT,
			MAX_WORKSHOP_SITE_LANDSCAPING_BLOCKS,
			Set.of(tablePos.immutable()),
			tablePos,
			horizontalFacing
		);

		if (site == null) {
			return WorkstationBuildResult.blocked();
		}

		return WorkstationBuildResult.started(updateBuildSiteMaterialStatus(createPendingBuildSite(
			level,
			StructureKind.ROADWRIGHT_WORKSHOP,
			ROADWRIGHT_WORKSHOP_BLUEPRINT,
			settlementId,
			site.origin(),
			tablePos.immutable(),
			tablePos.immutable(),
			site.facing(),
			level.getServer().getTickCount()
		), stock, level.getServer().getTickCount()));
	}

	public static WorkstationBuildResult tryStartMasonWorkshopAtWorkstation(
		ServerLevel level,
		BlockPos stonecutterPos,
		Direction facing,
		String settlementId,
		Map<String, Integer> stock,
		Optional<SettlementBuildSite> existingBuildSite
	) {
		if (existingBuildSite.isPresent()) {
			return WorkstationBuildResult.resumed(updateBuildSiteMaterialStatus(existingBuildSite.get(), stock, level.getServer().getTickCount()));
		}

		if (isPositionInExistingShelteredStructure(level, stonecutterPos)) {
			return WorkstationBuildResult.completed();
		}

		Direction horizontalFacing = facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : facing;
		BlockPos origin = stonecutterPos.relative(horizontalFacing.getOpposite(), 3).below();
		AnchoredStructureSite site = findAnchoredStructureSiteOrPlaceBlockedSign(
			level,
			origin,
			horizontalFacing,
			StructureKind.MASON_WORKSHOP,
			MASON_WORKSHOP_BLUEPRINT,
			MAX_WORKSHOP_SITE_LANDSCAPING_BLOCKS,
			Set.of(stonecutterPos.immutable()),
			stonecutterPos,
			horizontalFacing
		);

		if (site == null) {
			return WorkstationBuildResult.blocked();
		}

		return WorkstationBuildResult.started(updateBuildSiteMaterialStatus(createPendingBuildSite(
			level,
			StructureKind.MASON_WORKSHOP,
			MASON_WORKSHOP_BLUEPRINT,
			settlementId,
			site.origin(),
			stonecutterPos.immutable(),
			stonecutterPos.immutable(),
			site.facing(),
			level.getServer().getTickCount()
		), stock, level.getServer().getTickCount()));
	}

	public static WorkstationBuildResult tryStartForesterWorkshopAtWorkstation(
		ServerLevel level,
		BlockPos tablePos,
		Direction facing,
		String settlementId,
		Map<String, Integer> stock,
		Optional<SettlementBuildSite> existingBuildSite
	) {
		if (existingBuildSite.isPresent()) {
			return WorkstationBuildResult.resumed(updateBuildSiteMaterialStatus(existingBuildSite.get(), stock, level.getServer().getTickCount()));
		}

		if (isPositionInExistingShelteredStructure(level, tablePos)) {
			return WorkstationBuildResult.completed();
		}

		Direction horizontalFacing = facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : facing;
		BlockPos origin = tablePos.relative(horizontalFacing.getOpposite(), 3).below();
		AnchoredStructureSite site = findAnchoredStructureSiteOrPlaceBlockedSign(
			level,
			origin,
			horizontalFacing,
			StructureKind.FORESTER_WORKSHOP,
			FORESTER_WORKSHOP_BLUEPRINT,
			MAX_WORKSHOP_SITE_LANDSCAPING_BLOCKS,
			Set.of(tablePos.immutable()),
			tablePos,
			horizontalFacing
		);

		if (site == null) {
			return WorkstationBuildResult.blocked();
		}

		return WorkstationBuildResult.started(updateBuildSiteMaterialStatus(createPendingBuildSite(
			level,
			StructureKind.FORESTER_WORKSHOP,
			FORESTER_WORKSHOP_BLUEPRINT,
			settlementId,
			site.origin(),
			tablePos.immutable(),
			tablePos.immutable(),
			site.facing(),
			level.getServer().getTickCount()
		), stock, level.getServer().getTickCount()));
	}

	public static WorkstationBuildResult tryStartMineEntranceAtWorkstation(
		ServerLevel level,
		BlockPos workstationPos,
		Direction facing,
		String settlementId,
		Map<String, Integer> stock,
		Optional<SettlementBuildSite> existingBuildSite
	) {
		if (existingBuildSite.isPresent()) {
			return WorkstationBuildResult.resumed(updateBuildSiteMaterialStatus(existingBuildSite.get(), stock, level.getServer().getTickCount()));
		}

		if (isPositionInExistingShelteredStructure(level, workstationPos)) {
			return WorkstationBuildResult.completed();
		}

		Direction horizontalFacing = facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : facing;
		BlockPos origin = workstationPos.relative(horizontalFacing.getOpposite(), 3);
		AnchoredStructureSite site = findAnchoredStructureSiteOrPlaceBlockedSign(
			level,
			origin,
			horizontalFacing,
			StructureKind.MINE_ENTRANCE,
			MINE_ENTRANCE_BLUEPRINT,
			MAX_MINE_ENTRANCE_SITE_LANDSCAPING_BLOCKS,
			MAX_MINE_ENTRANCE_TERRAIN_CUT_BLOCKS,
			Set.of(workstationPos.immutable()),
			workstationPos,
			horizontalFacing
		);

		if (site == null) {
			return WorkstationBuildResult.blocked();
		}

		return WorkstationBuildResult.started(updateBuildSiteMaterialStatus(createPendingBuildSite(
			level,
			StructureKind.MINE_ENTRANCE,
			MINE_ENTRANCE_BLUEPRINT,
			settlementId,
			site.origin(),
			workstationPos.immutable(),
			workstationPos.immutable(),
			site.facing(),
			level.getServer().getTickCount()
		), stock, level.getServer().getTickCount()));
	}

	public static WorkstationBuildResult tryStartFletcherHutAtWorkstation(
		ServerLevel level,
		BlockPos tablePos,
		Direction facing,
		String settlementId,
		Map<String, Integer> stock,
		Optional<SettlementBuildSite> existingBuildSite
	) {
		if (existingBuildSite.isPresent()) {
			repairFletcherHutFrontAccess(level, existingBuildSite.get(), stock);
			return WorkstationBuildResult.resumed(updateBuildSiteMaterialStatus(existingBuildSite.get(), stock, level.getServer().getTickCount()));
		}

		if (isPositionInExistingShelteredStructure(level, tablePos)) {
			return WorkstationBuildResult.completed();
		}

		Direction horizontalFacing = facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : facing;
		BlockPos origin = tablePos.relative(horizontalFacing.getOpposite(), 3).below();
		AnchoredStructureSite site = findAnchoredStructureSiteOrPlaceBlockedSign(
			level,
			origin,
			horizontalFacing,
			StructureKind.FLETCHER_HUT,
			FLETCHER_HUT_BLUEPRINT,
			MAX_WORKSHOP_SITE_LANDSCAPING_BLOCKS,
			Set.of(tablePos.immutable()),
			tablePos,
			horizontalFacing
		);

		if (site == null) {
			return WorkstationBuildResult.blocked();
		}

		return WorkstationBuildResult.started(updateBuildSiteMaterialStatus(createPendingBuildSite(
			level,
			StructureKind.FLETCHER_HUT,
			FLETCHER_HUT_BLUEPRINT,
			settlementId,
			site.origin(),
			tablePos.immutable(),
			tablePos.immutable(),
			site.facing(),
			level.getServer().getTickCount()
		), stock, level.getServer().getTickCount()));
	}

	public static WorkstationBuildResult tryStartButcherShopAtWorkstation(
		ServerLevel level,
		BlockPos smokerPos,
		Direction facing,
		String settlementId,
		Map<String, Integer> stock,
		Optional<SettlementBuildSite> existingBuildSite
	) {
		if (existingBuildSite.isPresent()) {
			return WorkstationBuildResult.resumed(updateBuildSiteMaterialStatus(existingBuildSite.get(), stock, level.getServer().getTickCount()));
		}

		if (isPositionInExistingShelteredStructure(level, smokerPos)) {
			return WorkstationBuildResult.completed();
		}

		Direction horizontalFacing = facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : facing;
		BlockPos origin = smokerPos.relative(horizontalFacing.getOpposite(), 3).below();
		AnchoredStructureSite site = findAnchoredStructureSiteOrPlaceBlockedSign(
			level,
			origin,
			horizontalFacing,
			StructureKind.BUTCHER_SHOP,
			FLETCHER_HUT_BLUEPRINT,
			MAX_WORKSHOP_SITE_LANDSCAPING_BLOCKS,
			Set.of(smokerPos.immutable()),
			smokerPos,
			horizontalFacing
		);

		if (site == null) {
			return WorkstationBuildResult.blocked();
		}

		return WorkstationBuildResult.started(updateBuildSiteMaterialStatus(createPendingBuildSite(
			level,
			StructureKind.BUTCHER_SHOP,
			FLETCHER_HUT_BLUEPRINT,
			settlementId,
			site.origin(),
			smokerPos.immutable(),
			smokerPos.immutable(),
			site.facing(),
			level.getServer().getTickCount()
		), stock, level.getServer().getTickCount()));
	}

	public static WorkstationBuildResult tryStartClericShrineAtWorkstation(
		ServerLevel level,
		BlockPos brewingStandPos,
		Direction facing,
		String settlementId,
		Map<String, Integer> stock,
		Optional<SettlementBuildSite> existingBuildSite
	) {
		return tryStartBeddedHutAtWorkstation(level, brewingStandPos, facing, settlementId, stock, existingBuildSite, StructureKind.CLERIC_SHRINE);
	}

	public static WorkstationBuildResult tryStartLeatherworkerWorkshopAtWorkstation(
		ServerLevel level,
		BlockPos cauldronPos,
		Direction facing,
		String settlementId,
		Map<String, Integer> stock,
		Optional<SettlementBuildSite> existingBuildSite
	) {
		return tryStartBeddedHutAtWorkstation(level, cauldronPos, facing, settlementId, stock, existingBuildSite, StructureKind.LEATHERWORKER_WORKSHOP);
	}

	public static WorkstationBuildResult tryStartLibraryAtWorkstation(
		ServerLevel level,
		BlockPos lecternPos,
		Direction facing,
		String settlementId,
		Map<String, Integer> stock,
		Optional<SettlementBuildSite> existingBuildSite
	) {
		return tryStartBeddedHutAtWorkstation(level, lecternPos, facing, settlementId, stock, existingBuildSite, StructureKind.LIBRARY);
	}

	public static WorkstationBuildResult tryStartScribeOfficeAtWorkstation(
		ServerLevel level,
		BlockPos deskPos,
		Direction facing,
		String settlementId,
		Map<String, Integer> stock,
		Optional<SettlementBuildSite> existingBuildSite
	) {
		return tryStartBeddedHutAtWorkstation(level, deskPos, facing, settlementId, stock, existingBuildSite, StructureKind.SCRIBE_OFFICE);
	}

	public static WorkstationBuildResult tryStartGuardPostAtWorkstation(
		ServerLevel level,
		BlockPos guardPostPos,
		Direction facing,
		String settlementId,
		Map<String, Integer> stock,
		Optional<SettlementBuildSite> existingBuildSite
	) {
		return tryStartBeddedHutAtWorkstation(level, guardPostPos, facing, settlementId, stock, existingBuildSite, StructureKind.GUARD_POST);
	}

	public static WorkstationBuildResult tryStartGardenerShedAtWorkstation(
		ServerLevel level,
		BlockPos workstationPos,
		Direction facing,
		String settlementId,
		Map<String, Integer> stock,
		Optional<SettlementBuildSite> existingBuildSite
	) {
		return tryStartBeddedHutAtWorkstation(level, workstationPos, facing, settlementId, stock, existingBuildSite, StructureKind.GARDENER_SHED);
	}

	public static WorkstationBuildResult tryStartBeekeeperApiaryAtWorkstation(
		ServerLevel level,
		BlockPos separatorPos,
		Direction facing,
		String settlementId,
		Map<String, Integer> stock,
		Optional<SettlementBuildSite> existingBuildSite
	) {
		return tryStartBeddedHutAtWorkstation(level, separatorPos, facing, settlementId, stock, existingBuildSite, StructureKind.BEEKEEPER_APIARY);
	}

	public static WorkstationBuildResult tryStartShepherdHutAtWorkstation(
		ServerLevel level,
		BlockPos loomPos,
		Direction facing,
		String settlementId,
		Map<String, Integer> stock,
		Optional<SettlementBuildSite> existingBuildSite
	) {
		return tryStartBeddedHutAtWorkstation(level, loomPos, facing, settlementId, stock, existingBuildSite, StructureKind.SHEPHERD_HUT);
	}

	public static WorkstationBuildResult tryStartSmithyAtWorkstation(
		ServerLevel level,
		BlockPos workstationPos,
		Direction facing,
		String settlementId,
		Map<String, Integer> stock,
		Optional<SettlementBuildSite> existingBuildSite
	) {
		return tryStartBeddedHutAtWorkstation(level, workstationPos, facing, settlementId, stock, existingBuildSite, StructureKind.SMITHY);
	}

	private static WorkstationBuildResult tryStartBeddedHutAtWorkstation(
		ServerLevel level,
		BlockPos workstationPos,
		Direction facing,
		String settlementId,
		Map<String, Integer> stock,
		Optional<SettlementBuildSite> existingBuildSite,
		StructureKind structureKind
	) {
		if (existingBuildSite.isPresent()) {
			return WorkstationBuildResult.resumed(updateBuildSiteMaterialStatus(existingBuildSite.get(), stock, level.getServer().getTickCount()));
		}

		if (isPositionInExistingShelteredStructure(level, workstationPos)) {
			return WorkstationBuildResult.completed();
		}

		Direction horizontalFacing = facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : facing;
		BlockPos origin = workstationPos.relative(horizontalFacing.getOpposite(), 3).below();
		StructureBlueprint blueprint = blueprintFor(structureKind);
		AnchoredStructureSite site = findAnchoredStructureSiteOrPlaceBlockedSign(
			level,
			origin,
			horizontalFacing,
			structureKind,
			blueprint,
			MAX_WORKSHOP_SITE_LANDSCAPING_BLOCKS,
			Set.of(workstationPos.immutable()),
			workstationPos,
			horizontalFacing
		);

		if (site == null) {
			return WorkstationBuildResult.blocked();
		}

		return WorkstationBuildResult.started(updateBuildSiteMaterialStatus(createPendingBuildSite(
			level,
			structureKind,
			blueprint,
			settlementId,
			site.origin(),
			workstationPos.immutable(),
			workstationPos.immutable(),
			site.facing(),
			level.getServer().getTickCount()
		), stock, level.getServer().getTickCount()));
	}

	public static WorkstationBuildResult tryStartCartographerHouseAtWorkstation(
		ServerLevel level,
		BlockPos tablePos,
		Direction facing,
		String settlementId,
		Map<String, Integer> stock,
		Optional<SettlementBuildSite> existingBuildSite
	) {
		if (existingBuildSite.isPresent()) {
			return WorkstationBuildResult.resumed(updateBuildSiteMaterialStatus(existingBuildSite.get(), stock, level.getServer().getTickCount()));
		}

		if (isPositionInExistingShelteredStructure(level, tablePos)) {
			return WorkstationBuildResult.completed();
		}

		Direction horizontalFacing = facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : facing;
		BlockPos origin = tablePos.below();
		AnchoredStructureSite site = findAnchoredStructureSiteOrPlaceBlockedSign(
			level,
			origin,
			horizontalFacing,
			StructureKind.CARTOGRAPHER_HOUSE,
			CARTOGRAPHER_HOUSE_BLUEPRINT,
			MAX_WORKSHOP_SITE_LANDSCAPING_BLOCKS,
			Set.of(tablePos.immutable()),
			tablePos,
			horizontalFacing
		);

		if (site == null) {
			return WorkstationBuildResult.blocked();
		}

		return WorkstationBuildResult.started(updateBuildSiteMaterialStatus(createPendingBuildSite(
			level,
			StructureKind.CARTOGRAPHER_HOUSE,
			CARTOGRAPHER_HOUSE_BLUEPRINT,
			settlementId,
			site.origin(),
			tablePos.immutable(),
			tablePos.immutable(),
			site.facing(),
			level.getServer().getTickCount()
		), stock, level.getServer().getTickCount()));
	}

	public static WorkstationBuildResult tryStartTradingPostAtWorkstation(
		ServerLevel level,
		BlockPos boardPos,
		Direction facing,
		String settlementId,
		Map<String, Integer> stock,
		Optional<SettlementBuildSite> existingBuildSite
	) {
		return tryStartTradingPostAtWorkstation(level, boardPos, facing, settlementId, stock, existingBuildSite, true);
	}

	public static WorkstationBuildResult tryStartTradingPostAtWorkstation(
		ServerLevel level,
		BlockPos boardPos,
		Direction facing,
		String settlementId,
		Map<String, Integer> stock,
		Optional<SettlementBuildSite> existingBuildSite,
		boolean placeBlockedSign
	) {
		Direction boardFacing = facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : facing;
		Direction structureFacing = boardFacing;
		BlockPos origin = boardPos.relative(boardFacing.getOpposite(), 3).below();
		long tick = level.getServer().getTickCount();

		if (existingBuildSite.isPresent()) {
			SettlementBuildSite buildSite = existingBuildSite.get();

			if (buildSite.origin().equals(origin)
				&& buildSite.facing() == structureFacing
				&& anchoredWorkstationStillMapsToBlueprint(buildSite, StructureKind.TRADING_POST, boardPos)) {
				return WorkstationBuildResult.resumed(updateBuildSiteMaterialStatus(buildSite, stock, tick));
			}

			return WorkstationBuildResult.started(updateBuildSiteMaterialStatus(createPendingBuildSite(
				level,
				StructureKind.TRADING_POST,
				TRADING_POST_BLUEPRINT,
				settlementId,
				origin,
				boardPos.immutable(),
				boardPos.immutable(),
				structureFacing,
				tick
			), stock, tick));
		}

		if (isPositionInExistingShelteredStructure(level, boardPos)) {
			return WorkstationBuildResult.completed();
		}

		AnchoredStructureSite site = findAnchoredStructureSiteOrPlaceBlockedSign(
			level,
			origin,
			structureFacing,
			StructureKind.TRADING_POST,
			TRADING_POST_BLUEPRINT,
			MAX_WORKSHOP_SITE_LANDSCAPING_BLOCKS,
			Set.of(boardPos.immutable()),
			boardPos,
			boardFacing,
			placeBlockedSign
		);

		if (site == null) {
			return WorkstationBuildResult.blocked();
		}

		return WorkstationBuildResult.started(updateBuildSiteMaterialStatus(createPendingBuildSite(
			level,
			StructureKind.TRADING_POST,
			TRADING_POST_BLUEPRINT,
			settlementId,
			site.origin(),
			boardPos.immutable(),
			boardPos.immutable(),
			site.facing(),
			tick
		), stock, tick));
	}

	public static WorkstationBuildResult tryStartBakeryAtWorkstation(
		ServerLevel level,
		BlockPos workstationPos,
		Direction facing,
		String settlementId,
		Map<String, Integer> stock,
		Optional<SettlementBuildSite> existingBuildSite
	) {
		Direction counterFacing = facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : facing;
		Direction structureFacing = counterFacing;
		BlockPos origin = workstationPos.relative(counterFacing.getOpposite(), 3).below();
		long tick = level.getServer().getTickCount();

		if (existingBuildSite.isPresent()) {
			SettlementBuildSite buildSite = existingBuildSite.get();

			if (buildSite.origin().equals(origin)
				&& buildSite.facing() == structureFacing
				&& anchoredWorkstationStillMapsToBlueprint(buildSite, StructureKind.BAKERY, workstationPos)) {
				return WorkstationBuildResult.resumed(updateBuildSiteMaterialStatus(buildSite, stock, tick));
			}

			return WorkstationBuildResult.started(updateBuildSiteMaterialStatus(createPendingBuildSite(
				level,
				StructureKind.BAKERY,
				BAKERY_BLUEPRINT,
				settlementId,
				origin,
				workstationPos.immutable(),
				workstationPos.immutable(),
				structureFacing,
				tick
			), stock, tick));
		}

		if (isPositionInExistingShelteredStructure(level, workstationPos)) {
			return WorkstationBuildResult.completed();
		}

		AnchoredStructureSite site = findAnchoredStructureSiteOrPlaceBlockedSign(
			level,
			origin,
			structureFacing,
			StructureKind.BAKERY,
			BAKERY_BLUEPRINT,
			MAX_WORKSHOP_SITE_LANDSCAPING_BLOCKS,
			Set.of(workstationPos.immutable()),
			workstationPos,
			counterFacing
		);

		if (site == null) {
			return WorkstationBuildResult.blocked();
		}

		return WorkstationBuildResult.started(updateBuildSiteMaterialStatus(createPendingBuildSite(
			level,
			StructureKind.BAKERY,
			BAKERY_BLUEPRINT,
			settlementId,
			site.origin(),
			workstationPos.immutable(),
			workstationPos.immutable(),
			site.facing(),
			tick
		), stock, tick));
	}

	public static WorkstationBuildResult tryStartDockAtPortmasterAnchor(
		ServerLevel level,
		BlockPos anchorPos,
		Direction facing,
		String settlementId,
		Map<String, Integer> stock,
		Optional<SettlementBuildSite> existingBuildSite
	) {
		if (existingBuildSite.isPresent()) {
			return WorkstationBuildResult.resumed(updateBuildSiteMaterialStatus(existingBuildSite.get(), stock, level.getServer().getTickCount()));
		}

		if (hasCompletedDockNearPortmasterAnchor(level, anchorPos, facing)) {
			return WorkstationBuildResult.completed();
		}

		DockSite site = findDockPreviewSiteNearPortmasterAnchor(level, anchorPos, facing);
		if (site == null) {
			return WorkstationBuildResult.blocked();
		}

		SettlementBuildSite buildSite = createPendingDockBuildSite(level, settlementId, anchorPos, site, level.getServer().getTickCount());
		return WorkstationBuildResult.started(updateBuildSiteMaterialStatus(buildSite, stock, level.getServer().getTickCount()));
	}

	public static WorkstationBuildResult tryStartLighthouseAtMarker(
		ServerLevel level,
		BlockPos markerPos,
		String settlementId,
		Map<String, Integer> stock,
		Optional<SettlementBuildSite> existingBuildSite
	) {
		if (existingBuildSite.isPresent()) {
			SettlementBuildSite buildSite = existingBuildSite.get();
			long tick = level.getServer().getTickCount();

			if (lighthouseMarkerRecipeCreditMissing(buildSite)) {
				buildSite = buildSite.withAddedSiteMaterials(Map.of("cobblestone", 8, "campfire", 1), tick);
			}

			return WorkstationBuildResult.resumed(updateBuildSiteMaterialStatus(buildSite, stock, tick));
		}

		LighthouseSite site = evaluateLighthouseMarkerSite(level, markerPos);

		if (site == null) {
			return WorkstationBuildResult.blocked();
		}

		return WorkstationBuildResult.started(updateBuildSiteMaterialStatus(createPendingBuildSite(
			level,
			StructureKind.LIGHTHOUSE,
			LIGHTHOUSE_BLUEPRINT,
			settlementId,
			site.baseCenter(),
			markerPos.immutable(),
			markerPos.immutable(),
			Direction.NORTH,
			level.getServer().getTickCount()
		), stock, level.getServer().getTickCount()));
	}

	public static StructurePreview previewCarpenterWorkshopAtWorkstation(ServerLevel level, String settlementId, BlockPos benchPos, Direction facing) {
		Direction horizontalFacing = facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : facing;
		BlockPos origin = benchPos.relative(horizontalFacing.getOpposite(), 3).below();
		return previewAnchoredStructure(
			level,
			settlementId,
			StructureKind.CARPENTER_WORKSHOP,
			CARPENTER_WORKSHOP_BLUEPRINT,
			"Carpenter's Workshop",
			origin,
			benchPos,
			benchPos,
			horizontalFacing,
			MAX_WORKSHOP_SITE_LANDSCAPING_BLOCKS
		);
	}

	public static StructurePreview previewRoadwrightWorkshopAtWorkstation(ServerLevel level, String settlementId, BlockPos tablePos, Direction facing) {
		Direction horizontalFacing = facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : facing;
		BlockPos origin = tablePos.relative(horizontalFacing.getOpposite(), 3).below();
		return previewAnchoredStructure(
			level,
			settlementId,
			StructureKind.ROADWRIGHT_WORKSHOP,
			ROADWRIGHT_WORKSHOP_BLUEPRINT,
			"Roadwright's Workshop",
			origin,
			tablePos,
			tablePos,
			horizontalFacing,
			MAX_WORKSHOP_SITE_LANDSCAPING_BLOCKS
		);
	}

	public static StructurePreview previewForesterWorkshopAtWorkstation(ServerLevel level, String settlementId, BlockPos tablePos, Direction facing) {
		Direction horizontalFacing = facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : facing;
		BlockPos origin = tablePos.relative(horizontalFacing.getOpposite(), 3).below();
		return previewAnchoredStructure(
			level,
			settlementId,
			StructureKind.FORESTER_WORKSHOP,
			FORESTER_WORKSHOP_BLUEPRINT,
			"Forester's Workshop",
			origin,
			tablePos,
			tablePos,
			horizontalFacing,
			MAX_WORKSHOP_SITE_LANDSCAPING_BLOCKS
		);
	}

	public static StructurePreview previewMasonWorkshopAtWorkstation(ServerLevel level, String settlementId, BlockPos stonecutterPos, Direction facing) {
		Direction horizontalFacing = facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : facing;
		BlockPos origin = stonecutterPos.relative(horizontalFacing.getOpposite(), 3).below();
		return previewAnchoredStructure(
			level,
			settlementId,
			StructureKind.MASON_WORKSHOP,
			MASON_WORKSHOP_BLUEPRINT,
			"Mason's Workshop",
			origin,
			stonecutterPos,
			stonecutterPos,
			horizontalFacing,
			MAX_WORKSHOP_SITE_LANDSCAPING_BLOCKS
		);
	}

	public static StructurePreview previewMineEntranceAtWorkstation(ServerLevel level, String settlementId, BlockPos workstationPos, Direction facing) {
		Direction horizontalFacing = facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : facing;
		BlockPos origin = workstationPos.relative(horizontalFacing.getOpposite(), 3);
		return previewAnchoredStructure(
			level,
			settlementId,
			StructureKind.MINE_ENTRANCE,
			MINE_ENTRANCE_BLUEPRINT,
			"mine_entrance",
			origin,
			workstationPos.immutable(),
			workstationPos.immutable(),
			horizontalFacing,
			MAX_MINE_ENTRANCE_SITE_LANDSCAPING_BLOCKS,
			MAX_MINE_ENTRANCE_TERRAIN_CUT_BLOCKS
		);
	}

	public static StructurePreview previewFletcherHutAtWorkstation(ServerLevel level, String settlementId, BlockPos tablePos, Direction facing) {
		Direction horizontalFacing = facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : facing;
		BlockPos origin = tablePos.relative(horizontalFacing.getOpposite(), 3).below();
		return previewAnchoredStructure(
			level,
			settlementId,
			StructureKind.FLETCHER_HUT,
			FLETCHER_HUT_BLUEPRINT,
			"Fletcher's Hut",
			origin,
			tablePos,
			tablePos,
			horizontalFacing,
			MAX_WORKSHOP_SITE_LANDSCAPING_BLOCKS
		);
	}

	public static StructurePreview previewButcherShopAtWorkstation(ServerLevel level, String settlementId, BlockPos smokerPos, Direction facing) {
		Direction horizontalFacing = facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : facing;
		BlockPos origin = smokerPos.relative(horizontalFacing.getOpposite(), 3).below();
		return previewAnchoredStructure(
			level,
			settlementId,
			StructureKind.BUTCHER_SHOP,
			FLETCHER_HUT_BLUEPRINT,
			"Butcher Shop",
			origin,
			smokerPos,
			smokerPos,
			horizontalFacing,
			MAX_WORKSHOP_SITE_LANDSCAPING_BLOCKS
		);
	}

	public static StructurePreview previewClericShrineAtWorkstation(ServerLevel level, String settlementId, BlockPos brewingStandPos, Direction facing) {
		return previewBeddedHutAtWorkstation(level, settlementId, brewingStandPos, facing, StructureKind.CLERIC_SHRINE, "Cleric Shrine");
	}

	public static StructurePreview previewLeatherworkerWorkshopAtWorkstation(ServerLevel level, String settlementId, BlockPos cauldronPos, Direction facing) {
		return previewBeddedHutAtWorkstation(level, settlementId, cauldronPos, facing, StructureKind.LEATHERWORKER_WORKSHOP, "Leatherworker's Workshop");
	}

	public static StructurePreview previewLibraryAtWorkstation(ServerLevel level, String settlementId, BlockPos lecternPos, Direction facing) {
		return previewBeddedHutAtWorkstation(level, settlementId, lecternPos, facing, StructureKind.LIBRARY, "Library");
	}

	public static StructurePreview previewScribeOfficeAtWorkstation(ServerLevel level, String settlementId, BlockPos deskPos, Direction facing) {
		return previewBeddedHutAtWorkstation(level, settlementId, deskPos, facing, StructureKind.SCRIBE_OFFICE, "Scribe Office");
	}

	public static StructurePreview previewGuardPostAtWorkstation(ServerLevel level, String settlementId, BlockPos guardPostPos, Direction facing) {
		return previewBeddedHutAtWorkstation(level, settlementId, guardPostPos, facing, StructureKind.GUARD_POST, "Guard Post");
	}

	public static StructurePreview previewGardenerShedAtWorkstation(ServerLevel level, String settlementId, BlockPos workstationPos, Direction facing) {
		return previewBeddedHutAtWorkstation(level, settlementId, workstationPos, facing, StructureKind.GARDENER_SHED, "Gardener's Shed");
	}

	public static StructurePreview previewBeekeeperApiaryAtWorkstation(ServerLevel level, String settlementId, BlockPos separatorPos, Direction facing) {
		return previewBeddedHutAtWorkstation(level, settlementId, separatorPos, facing, StructureKind.BEEKEEPER_APIARY, "Beekeeper's Apiary");
	}

	public static StructurePreview previewShepherdHutAtWorkstation(ServerLevel level, String settlementId, BlockPos loomPos, Direction facing) {
		return previewBeddedHutAtWorkstation(level, settlementId, loomPos, facing, StructureKind.SHEPHERD_HUT, "Shepherd's Hut");
	}

	public static StructurePreview previewSmithyAtWorkstation(ServerLevel level, String settlementId, BlockPos workstationPos, Direction facing) {
		return previewBeddedHutAtWorkstation(level, settlementId, workstationPos, facing, StructureKind.SMITHY, "Smithy");
	}

	private static StructurePreview previewBeddedHutAtWorkstation(
		ServerLevel level,
		String settlementId,
		BlockPos workstationPos,
		Direction facing,
		StructureKind structureKind,
		String label
	) {
		Direction horizontalFacing = facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : facing;
		BlockPos origin = workstationPos.relative(horizontalFacing.getOpposite(), 3).below();
		StructureBlueprint blueprint = blueprintFor(structureKind);
		return previewAnchoredStructure(
			level,
			settlementId,
			structureKind,
			blueprint,
			label,
			origin,
			workstationPos,
			workstationPos,
			horizontalFacing,
			MAX_WORKSHOP_SITE_LANDSCAPING_BLOCKS
		);
	}

	public static StructurePreview previewCartographerHouseAtWorkstation(ServerLevel level, SettlementState settlement, BlockPos tablePos) {
		BlockPos origin = tablePos.below();
		return previewAnchoredStructure(
			level,
			settlement.id(),
			StructureKind.CARTOGRAPHER_HOUSE,
			CARTOGRAPHER_HOUSE_BLUEPRINT,
			"Cartographer's House",
			origin,
			tablePos,
			tablePos,
			cartographerHouseFacingFor(settlement, tablePos),
			MAX_WORKSHOP_SITE_LANDSCAPING_BLOCKS
		);
	}

	public static StructurePreview previewTradingPostAtWorkstation(ServerLevel level, String settlementId, BlockPos boardPos, Direction facing) {
		Direction boardFacing = facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : facing;
		Direction structureFacing = boardFacing;
		BlockPos origin = boardPos.relative(boardFacing.getOpposite(), 3).below();
		return previewAnchoredStructure(
			level,
			settlementId,
			StructureKind.TRADING_POST,
			TRADING_POST_BLUEPRINT,
			"Trade Post",
			origin,
			boardPos,
			boardPos,
			structureFacing,
			MAX_WORKSHOP_SITE_LANDSCAPING_BLOCKS
		);
	}

	public static StructurePreview previewBakeryAtWorkstation(ServerLevel level, String settlementId, BlockPos workstationPos, Direction facing) {
		Direction counterFacing = facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : facing;
		Direction structureFacing = counterFacing;
		BlockPos origin = workstationPos.relative(counterFacing.getOpposite(), 3).below();
		return previewAnchoredStructure(
			level,
			settlementId,
			StructureKind.BAKERY,
			BAKERY_BLUEPRINT,
			"Bakery",
			origin,
			workstationPos,
			workstationPos,
			structureFacing,
			MAX_WORKSHOP_SITE_LANDSCAPING_BLOCKS
		);
	}

	public static StructurePreview previewSimpleHousingShelterAtDoor(ServerLevel level, String settlementId, BlockPos doorPos, Direction facing) {
		return previewDoorAnchoredStructure(
			level,
			settlementId,
			StructureKind.SIMPLE_HOUSING_SHELTER,
			SIMPLE_HOUSING_SHELTER_BLUEPRINT,
			"Simple Housing Shelter",
			doorPos,
			facing,
			MAX_SITE_LANDSCAPING_BLOCKS
		);
	}

	public static StructurePreview previewHousingShelterAtDoor(ServerLevel level, String settlementId, BlockPos doorPos, Direction facing) {
		return previewDoorAnchoredStructure(
			level,
			settlementId,
			StructureKind.HOUSING_SHELTER,
			HOUSING_SHELTER_BLUEPRINT,
			"Housing Shelter",
			doorPos,
			facing,
			MAX_SITE_LANDSCAPING_BLOCKS
		);
	}

	public static WorkstationBuildResult tryStartSimpleHousingShelterAtDoor(
		ServerLevel level,
		BlockPos doorPos,
		Direction facing,
		String settlementId,
		Map<String, Integer> stock,
		Optional<SettlementBuildSite> existingBuildSite
	) {
		return tryStartDoorAnchoredStructure(
			level,
			StructureKind.SIMPLE_HOUSING_SHELTER,
			SIMPLE_HOUSING_SHELTER_BLUEPRINT,
			doorPos,
			facing,
			settlementId,
			stock,
			existingBuildSite,
			MAX_SITE_LANDSCAPING_BLOCKS
		);
	}

	public static WorkstationBuildResult tryStartHousingShelterAtDoor(
		ServerLevel level,
		BlockPos doorPos,
		Direction facing,
		String settlementId,
		Map<String, Integer> stock,
		Optional<SettlementBuildSite> existingBuildSite
	) {
		return tryStartDoorAnchoredStructure(
			level,
			StructureKind.HOUSING_SHELTER,
			HOUSING_SHELTER_BLUEPRINT,
			doorPos,
			facing,
			settlementId,
			stock,
			existingBuildSite,
			MAX_SITE_LANDSCAPING_BLOCKS
		);
	}

	public static StructurePreview previewPalisadeGatehouseAtDoor(
		ServerLevel level,
		String settlementId,
		BlockPos doorPos,
		Direction facing,
		boolean copperBars
	) {
		return previewDoorAnchoredStructure(
			level,
			settlementId,
			copperBars ? StructureKind.COPPER_PALISADE_GATEHOUSE : StructureKind.PALISADE_GATEHOUSE,
			PALISADE_GATEHOUSE_BLUEPRINT,
			"Palisade Gatehouse",
			doorPos,
			facing,
			MAX_SITE_LANDSCAPING_BLOCKS
		);
	}

	public static WorkstationBuildResult tryStartPalisadeGatehouseAtDoor(
		ServerLevel level,
		BlockPos doorPos,
		Direction facing,
		String settlementId,
		boolean copperBars,
		Map<String, Integer> stock,
		Optional<SettlementBuildSite> existingBuildSite
	) {
		return tryStartDoorAnchoredStructure(
			level,
			copperBars ? StructureKind.COPPER_PALISADE_GATEHOUSE : StructureKind.PALISADE_GATEHOUSE,
			PALISADE_GATEHOUSE_BLUEPRINT,
			doorPos,
			facing,
			settlementId,
			stock,
			existingBuildSite,
			MAX_SITE_LANDSCAPING_BLOCKS
		);
	}

	public static PalisadeWallReplan planPalisadeWallsToPoint(
		ServerLevel level,
		SettlementState settlement,
		BlockPos pointPos,
		Map<String, Integer> stock,
		List<SettlementBuildSite> existingBuildSites
	) {
		List<PalisadeControlPoint> controls = palisadeControlPoints(level, settlement, pointPos, existingBuildSites);
		if (controls.isEmpty()) {
			return PalisadeWallReplan.empty();
		}

		List<PalisadeControlPoint> selectedControls = nearestAngularPalisadeControls(settlement.center(), pointPos, controls);
		if (selectedControls.isEmpty()) {
			return PalisadeWallReplan.empty();
		}

		Set<String> obsoleteBuildSiteIds = obsoletePalisadeWallBuildSiteIds(pointPos, selectedControls, existingBuildSites);
		List<SettlementBuildSite> plannedSites = new ArrayList<>();
		List<SettlementBuildSite> workingBuildSites = existingBuildSites.stream()
			.filter(buildSite -> !obsoleteBuildSiteIds.contains(buildSite.id()))
			.collect(java.util.stream.Collectors.toCollection(ArrayList::new));
		long tick = level.getServer().getTickCount();

		for (PalisadeControlPoint control : selectedControls) {
			BlockPos segmentStart = palisadeWallConnectionEndpoint(control.pos(), pointPos, workingBuildSites);
			SettlementBuildSite buildSite = createPendingPalisadeWallBuildSite(level, settlement, segmentStart, pointPos, stock, workingBuildSites, tick);
			if (buildSite == null || hasBuildSiteWithId(workingBuildSites, buildSite.id())) {
				continue;
			}

			SettlementBuildSite updatedBuildSite = updateBuildSiteMaterialStatus(buildSite, stock, tick);
			plannedSites.add(updatedBuildSite);
			workingBuildSites.add(updatedBuildSite);
		}

		return new PalisadeWallReplan(List.copyOf(plannedSites), Set.copyOf(obsoleteBuildSiteIds));
	}

	public static StructurePreview previewDockAtPortmasterAnchor(ServerLevel level, String settlementId, BlockPos anchorPos, Direction facing) {
		Direction horizontalFacing = facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : facing;
		Direction dockFacing = horizontalFacing.getOpposite();
		if (hasCompletedDockNearPortmasterAnchor(level, anchorPos, horizontalFacing)) {
			return new StructurePreview(
				settlementId + ":dock:" + anchorPos.getX() + "_" + anchorPos.getY() + "_" + anchorPos.getZ(),
				"Dock",
				true,
				"",
				List.of(),
				List.of()
			);
		}

		DockSite site = findDockPreviewSiteNearPortmasterAnchor(level, anchorPos, horizontalFacing);
		BlockPos origin = site != null ? site.origin() : previewDockOrigin(level, anchorPos, dockFacing);
		return new StructurePreview(
			settlementId + ":dock:" + anchorPos.getX() + "_" + anchorPos.getY() + "_" + anchorPos.getZ(),
			"Dock",
			site != null,
			site != null ? "" : "Dock needs deeper adjacent water and shoreline access.",
			site != null ? List.of() : List.of(anchorPos.immutable()),
			buildDockPreviewBlocks(level, anchorPos, origin, dockFacing, site != null)
		);
	}

	public static StructurePreview previewLighthouseAtMarker(ServerLevel level, String settlementId, BlockPos markerPos) {
		SettlementBuildSite previewBuildSite = createPendingBuildSite(
			level,
			StructureKind.LIGHTHOUSE,
			LIGHTHOUSE_BLUEPRINT,
			settlementId,
			markerPos.immutable(),
			markerPos.immutable(),
			markerPos.immutable(),
			Direction.NORTH,
			0L
		);
		return previewBuildSite(
			previewBuildSite,
			"Lighthouse",
			evaluateLighthouseMarkerSite(level, markerPos) != null
				? PlacementPreviewResult.valid(null)
				: PlacementPreviewResult.invalid("Lighthouse needs a better shoreline marker site.", markerPos)
		);
	}

	public static List<BlockPos> findPlacedCartographyTables(ServerLevel level, SettlementState settlement) {
		return findPlacedWorkstations(level, settlement, state -> state.is(Blocks.CARTOGRAPHY_TABLE));
	}

	public static List<BlockPos> findPlacedFletchingTables(ServerLevel level, SettlementState settlement) {
		return findPlacedWorkstations(level, settlement, state -> state.is(Blocks.FLETCHING_TABLE));
	}

	public static List<BlockPos> findPlacedSmokers(ServerLevel level, SettlementState settlement) {
		return findPlacedWorkstations(level, settlement, state -> state.is(Blocks.SMOKER));
	}

	public static List<BlockPos> findPlacedStonecutters(ServerLevel level, SettlementState settlement) {
		return findPlacedWorkstations(level, settlement, state -> state.is(Blocks.STONECUTTER));
	}

	public static List<BlockPos> findPlacedBrewingStands(ServerLevel level, SettlementState settlement) {
		return findPlacedWorkstations(level, settlement, state -> state.is(Blocks.BREWING_STAND));
	}

	public static List<BlockPos> findPlacedCauldrons(ServerLevel level, SettlementState settlement) {
		return findPlacedWorkstations(
			level,
			settlement,
			state -> state.is(Blocks.CAULDRON)
				|| state.is(Blocks.WATER_CAULDRON)
				|| state.is(Blocks.LAVA_CAULDRON)
				|| state.is(Blocks.POWDER_SNOW_CAULDRON)
		);
	}

	public static List<BlockPos> findPlacedLecterns(ServerLevel level, SettlementState settlement) {
		return findPlacedWorkstations(level, settlement, state -> state.is(Blocks.LECTERN));
	}

	public static List<BlockPos> findPlacedLooms(ServerLevel level, SettlementState settlement) {
		return findPlacedWorkstations(level, settlement, state -> state.is(Blocks.LOOM));
	}

	public static List<BlockPos> findPlacedSmithingWorkstations(ServerLevel level, SettlementState settlement) {
		return findPlacedWorkstations(
			level,
			settlement,
			state -> state.is(Blocks.BLAST_FURNACE)
				|| state.is(Blocks.SMITHING_TABLE)
				|| state.is(Blocks.GRINDSTONE)
		);
	}

	public static List<BlockPos> findPlacedTradeBoards(ServerLevel level, SettlementState settlement) {
		return findPlacedWorkstations(level, settlement, state -> state.is(LiveVillagesBlocks.TRADE_BOARD));
	}

	public static List<BlockPos> findPlacedCarpenterBenches(ServerLevel level, SettlementState settlement) {
		return findPlacedWorkstations(level, settlement, state -> state.is(LiveVillagesBlocks.CARPENTER_BENCH));
	}

	public static List<BlockPos> findPlacedScribeDesks(ServerLevel level, SettlementState settlement) {
		return findPlacedWorkstations(level, settlement, state -> state.is(LiveVillagesBlocks.SCRIBE_DESK));
	}

	public static List<BlockPos> findPlacedGuardPosts(ServerLevel level, SettlementState settlement) {
		return findPlacedWorkstations(level, settlement, state -> state.is(LiveVillagesBlocks.GUARD_POST));
	}

	public static List<BlockPos> findPlacedGardenerWorkstations(ServerLevel level, SettlementState settlement) {
		return findPlacedWorkstations(level, settlement, state -> state.is(LiveVillagesBlocks.GARDENER_WORKSTATION));
	}

	public static List<BlockPos> findPlacedHoneySeparators(ServerLevel level, SettlementState settlement) {
		return findPlacedWorkstations(level, settlement, state -> state.is(LiveVillagesBlocks.HONEY_SEPARATOR));
	}

	public static List<BlockPos> findPlacedSurveyorTables(ServerLevel level, SettlementState settlement) {
		return findPlacedWorkstations(level, settlement, state -> state.is(LiveVillagesBlocks.SURVEYOR_TABLE));
	}

	public static List<BlockPos> findPlacedForesterTables(ServerLevel level, SettlementState settlement) {
		return findPlacedWorkstations(level, settlement, state -> state.is(LiveVillagesBlocks.FORESTER_TABLE));
	}

	public static List<BlockPos> findPlacedBakersCounters(ServerLevel level, SettlementState settlement) {
		return findPlacedWorkstations(level, settlement, state -> state.is(LiveVillagesBlocks.BAKERS_COUNTER));
	}

	public static List<BlockPos> findPlacedMinerWorkstations(ServerLevel level, SettlementState settlement) {
		return findPlacedWorkstations(level, settlement, state -> state.is(LiveVillagesBlocks.MINER_WORKSTATION));
	}

	public static List<BlockPos> findPlacedPortmasterAnchors(ServerLevel level, SettlementState settlement) {
		return findPlacedWorkstations(level, settlement, state -> state.is(LiveVillagesBlocks.PORTMASTER_ANCHOR));
	}

	public static List<BlockPos> findPlacedLighthouses(ServerLevel level, SettlementState settlement) {
		return findPlacedWorkstations(level, settlement, state -> state.is(LiveVillagesBlocks.LIGHTHOUSE));
	}

	public static List<BlockPos> findPlacedPalisadePoints(ServerLevel level, SettlementState settlement) {
		return findPlacedWorkstations(
			level,
			settlement,
			state -> state.is(LiveVillagesBlocks.PALISADE_POINT),
			PALISADE_POINT_SCAN_RADIUS_BLOCKS
		);
	}

	private static List<BlockPos> findPlacedWorkstations(ServerLevel level, SettlementState settlement, Predicate<BlockState> matcher) {
		return findPlacedWorkstations(level, settlement, matcher, VANILLA_WORKSTATION_SCAN_RADIUS_BLOCKS);
	}

	private static List<BlockPos> findPlacedWorkstations(ServerLevel level, SettlementState settlement, Predicate<BlockState> matcher, int scanRadiusBlocks) {
		List<BlockPos> tablePositions = new ArrayList<>();
		BlockPos center = settlement.center();
		int radiusSquared = scanRadiusBlocks * scanRadiusBlocks;
		BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();

		for (int x = center.getX() - scanRadiusBlocks; x <= center.getX() + scanRadiusBlocks; x++) {
			for (int z = center.getZ() - scanRadiusBlocks; z <= center.getZ() + scanRadiusBlocks; z++) {
				if (center.distToCenterSqr(x + 0.5D, center.getY() + 0.5D, z + 0.5D) > radiusSquared) {
					continue;
				}

				if (!level.hasChunkAt(new BlockPos(x, center.getY(), z))) {
					continue;
				}

				int columnTopY = Math.min(level.getMaxY() - 1, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1);
				int columnMinY = Math.max(level.getMinY(), columnTopY - VANILLA_WORKSTATION_SCAN_DEPTH_BELOW_SURFACE_BLOCKS);

				for (int y = columnTopY; y >= columnMinY; y--) {
					scanPos.set(x, y, z);

					if (matcher.test(level.getBlockState(scanPos))) {
						tablePositions.add(scanPos.immutable());
						break;
					}
				}
			}
		}

		tablePositions.sort((left, right) -> Double.compare(left.distSqr(center), right.distSqr(center)));
		return tablePositions;
	}

	public static Direction tradeBoardFacingFor(ServerLevel level, BlockPos boardPos) {
		BlockState state = level.getBlockState(boardPos);
		if (state.is(LiveVillagesBlocks.TRADE_BOARD)) {
			return state.getValue(TradeBoardBlock.FACING);
		}

		return Direction.NORTH;
	}

	public static Direction cartographerHouseFacingFor(SettlementState settlement, BlockPos tablePos) {
		return facingToward(settlement.center(), tablePos).getClockWise();
	}

	public static Direction fletcherHutFacingFor(SettlementState settlement, BlockPos tablePos) {
		return facingToward(settlement.center(), tablePos);
	}

	public static Direction facingAwayFrom(BlockPos center, BlockPos pos) {
		return facingToward(center, pos).getOpposite();
	}

	public static Direction portmasterAnchorFacingFor(ServerLevel level, BlockPos anchorPos) {
		BlockState state = level.getBlockState(anchorPos);
		if (state.is(LiveVillagesBlocks.PORTMASTER_ANCHOR)) {
			return state.getValue(PortmasterAnchorBlock.FACING);
		}

		return Direction.NORTH;
	}

	public static boolean hasCompletedDockNearPortmasterAnchor(ServerLevel level, BlockPos anchorPos, Direction facing) {
		Direction horizontalFacing = facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : facing;
		Direction dockFacing = horizontalFacing.getOpposite();

		for (int step = 1; step <= 10; step++) {
			BlockPos columnPos = anchorPos.relative(dockFacing, step);

			if (!level.hasChunkAt(columnPos)) {
				continue;
			}

			int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, columnPos.getX(), columnPos.getZ()) - 1;
			if (surfaceY < level.getMinY()) {
				continue;
			}

			BlockPos candidateOrigin = new BlockPos(columnPos.getX(), surfaceY, columnPos.getZ());
			if (isMinimalDock(level, candidateOrigin, dockFacing)) {
				return true;
			}
		}

		return false;
	}

	public static List<BlockPos> findDockOrigins(ServerLevel level, SettlementState settlement) {
		List<BlockPos> dockOrigins = new ArrayList<>();
		Set<BlockPos> countedDockOrigins = new HashSet<>();
		BlockPos center = settlement.center();
		int radiusBlocks = buildRadius(settlement);

		for (int x = center.getX() - radiusBlocks; x <= center.getX() + radiusBlocks; x++) {
			for (int z = center.getZ() - radiusBlocks; z <= center.getZ() + radiusBlocks; z++) {
				BlockPos surfaceColumnPos = new BlockPos(x, center.getY(), z);

				if (!level.hasChunkAt(surfaceColumnPos)) {
					continue;
				}

				WaterColumnSurvey waterColumn = surveyWaterColumn(level, x, z);
				if (waterColumn.surfaceY() < 0) {
					continue;
				}

				BlockPos candidateOrigin = new BlockPos(x, waterColumn.surfaceY(), z);
				for (Direction facing : Direction.Plane.HORIZONTAL) {
					if (isMinimalDock(level, candidateOrigin, facing) && countedDockOrigins.add(candidateOrigin.immutable())) {
						dockOrigins.add(candidateOrigin.immutable());
						break;
					}
				}
			}
		}

		dockOrigins.sort((left, right) -> Double.compare(left.distSqr(center), right.distSqr(center)));
		return dockOrigins;
	}

	public static Optional<Direction> dockFacing(ServerLevel level, BlockPos dockOrigin) {
		for (Direction facing : Direction.Plane.HORIZONTAL) {
			if (isMinimalDock(level, dockOrigin, facing)) {
				return Optional.of(facing);
			}
		}

		return Optional.empty();
	}

	public static List<BlockPos> findLighthouseTops(ServerLevel level, SettlementState settlement) {
		List<BlockPos> lighthouseTops = new ArrayList<>();
		Set<BlockPos> countedLighthouseTops = new HashSet<>();
		BlockPos center = settlement.center();
		int radiusBlocks = buildRadius(settlement);
		int minY = Math.max(level.getMinY(), center.getY() - SCAN_DEPTH_BLOCKS);
		int maxY = Math.min(level.getMaxY() - 1, center.getY() + SCAN_HEIGHT_BLOCKS);
		BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();

		for (int x = center.getX() - radiusBlocks; x <= center.getX() + radiusBlocks; x++) {
			for (int z = center.getZ() - radiusBlocks; z <= center.getZ() + radiusBlocks; z++) {
				BlockPos surfaceColumnPos = new BlockPos(x, center.getY(), z);

				if (!level.hasChunkAt(surfaceColumnPos)) {
					continue;
				}

				for (int y = minY; y <= maxY; y++) {
					scanPos.set(x, y, z);
					if (!level.isLoaded(scanPos)) {
						continue;
					}

					if (level.getBlockState(scanPos).is(Blocks.CAMPFIRE)
						&& countedLighthouseTops.add(scanPos.immutable())
						&& isMinimalLighthouse(level, scanPos)) {
						lighthouseTops.add(scanPos.immutable());
					}
				}
			}
		}

		lighthouseTops.sort((left, right) -> Double.compare(left.distSqr(center), right.distSqr(center)));
		return lighthouseTops;
	}

	public static Optional<BlockPos> buildSiteBlockPos(SettlementBuildSite buildSite, SettlementBuildBlockState buildBlock) {
		BlueprintRelativePos relativePos = parseRelativeBlueprintPosition(buildBlock.position());

		if (relativePos == null) {
			return Optional.empty();
		}

		if (buildSite.blueprintId() == SettlementBuildSiteType.PALISADE_WALL) {
			relativePos = normalizedPalisadeWallRelativePos(buildSite, buildBlock, relativePos);
		}

		return Optional.of(offset(buildSite.origin(), buildSite.facing(), relativePos.right(), relativePos.forward(), relativePos.up()));
	}

	public static BlockPos currentPlacedWorkstationPos(ServerLevel level, SettlementBuildSite buildSite) {
		Optional<SettlementBuildBlockState> workstationBlock = buildSite.blocks().stream()
			.filter(block -> isAnchoredWorkstationBlock(buildSite, block))
			.findFirst();

		if (workstationBlock.isEmpty()) {
			return buildSite.workstationPos();
		}

		BlockState plannedState = plannedBuildSiteBlockState(buildSite, workstationBlock.get());
		if (plannedState == null) {
			return buildSite.workstationPos();
		}

		if (level.hasChunkAt(buildSite.workstationPos()) && level.getBlockState(buildSite.workstationPos()).is(plannedState.getBlock())) {
			return buildSite.workstationPos();
		}

		if (!buildSite.anchorPos().equals(buildSite.workstationPos())
			&& level.hasChunkAt(buildSite.anchorPos())
			&& level.getBlockState(buildSite.anchorPos()).is(plannedState.getBlock())) {
			return buildSite.anchorPos();
		}

		return buildSite.workstationPos();
	}

	public static BlockState plannedBuildSiteBlockState(SettlementBuildSite buildSite, SettlementBuildBlockState buildBlock) {
		BlueprintRelativePos relativePos = parseRelativeBlueprintPosition(buildBlock.position());

		if (relativePos == null || buildBlock.blueprintSymbol().isBlank()) {
			return null;
		}

		if (buildSite.blueprintId() == SettlementBuildSiteType.DOCK) {
			return dockBuildState(buildBlock.blueprintSymbol().charAt(0), buildSite.woodFamily());
		}

		if (buildSite.blueprintId() == SettlementBuildSiteType.PALISADE_WALL) {
			return palisadeWallBuildState(buildBlock.blueprintSymbol().charAt(0), buildSite.woodFamily());
		}

		StructureKind structureKind = structureKindFor(buildSite.blueprintId());
		StructureBlueprint blueprint = blueprintFor(structureKind);
		return plannedBlueprintBlockState(
			structureKind,
			blueprint,
			buildSite.facing(),
			buildSite.woodFamily(),
			buildSite.stoneMaterial(),
			buildBlock.blueprintSymbol().charAt(0),
			relativePos.right(),
			relativePos.forward(),
			relativePos.up()
		);
	}

	private static StructurePreview previewAnchoredStructure(
		ServerLevel level,
		String settlementId,
		StructureKind structureKind,
		StructureBlueprint blueprint,
		String previewType,
		BlockPos origin,
		BlockPos anchorPos,
		BlockPos workstationPos,
		Direction facing,
		int maxLandscapingBlocks
	) {
		return previewAnchoredStructure(
			level,
			settlementId,
			structureKind,
			blueprint,
			previewType,
			origin,
			anchorPos,
			workstationPos,
			facing,
			maxLandscapingBlocks,
			MAX_TERRAIN_CUT_BLOCKS
		);
	}

	private static StructurePreview previewAnchoredStructure(
		ServerLevel level,
		String settlementId,
		StructureKind structureKind,
		StructureBlueprint blueprint,
		String previewType,
		BlockPos origin,
		BlockPos anchorPos,
		BlockPos workstationPos,
		Direction facing,
		int maxLandscapingBlocks,
		int maxTerrainCutBlocks
	) {
		PlacementPreviewResult placement = evaluateAnchoredStructureSite(
			level,
			origin,
			facing,
			structureKind,
			blueprint,
			maxLandscapingBlocks,
			maxTerrainCutBlocks,
			Set.of(anchorPos.immutable())
		);
		SettlementBuildSite previewBuildSite = createPendingBuildSite(
			level,
			structureKind,
			blueprint,
			settlementId,
			origin,
			anchorPos.immutable(),
			workstationPos.immutable(),
			facing,
			0L
		);
		return previewBuildSite(previewBuildSite, previewType, placement);
	}

	private static StructurePreview previewDoorAnchoredStructure(
		ServerLevel level,
		String settlementId,
		StructureKind structureKind,
		StructureBlueprint blueprint,
		String previewType,
		BlockPos doorPos,
		Direction facing,
		int maxLandscapingBlocks
	) {
		Direction horizontalFacing = facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : facing;
		BlockPos origin = doorAnchoredOriginFor(structureKind, doorPos, horizontalFacing);
		PlacementPreviewResult placement = evaluateAnchoredStructureSite(
			level,
			origin,
			horizontalFacing,
			structureKind,
			blueprint,
			maxLandscapingBlocks,
			Set.of(doorPos.immutable())
		);
		SettlementBuildSite previewBuildSite = createPendingBuildSite(
			level,
			structureKind,
			blueprint,
			settlementId,
			origin,
			doorPos.immutable(),
			doorPos.immutable(),
			horizontalFacing,
			0L
		);
		return previewBuildSite(previewBuildSite, previewType, placement);
	}

	private static WorkstationBuildResult tryStartDoorAnchoredStructure(
		ServerLevel level,
		StructureKind structureKind,
		StructureBlueprint blueprint,
		BlockPos doorPos,
		Direction facing,
		String settlementId,
		Map<String, Integer> stock,
		Optional<SettlementBuildSite> existingBuildSite,
		int maxLandscapingBlocks
	) {
		if (existingBuildSite.isPresent()) {
			long tick = level.getServer().getTickCount();
			SettlementBuildSite buildSite = withPalisadeGatehouseDoorMarkerBlock(existingBuildSite.get(), structureKind, tick);
			return WorkstationBuildResult.resumed(updateBuildSiteMaterialStatus(buildSite, stock, tick));
		}

		Direction horizontalFacing = facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : facing;
		BlockPos origin = doorAnchoredOriginFor(structureKind, doorPos, horizontalFacing);
		AnchoredStructureSite site = findAnchoredStructureSite(
			level,
			origin,
			horizontalFacing,
			structureKind,
			blueprint,
			maxLandscapingBlocks,
			Set.of(doorPos.immutable())
		);

		if (site == null) {
			return WorkstationBuildResult.blocked();
		}

		return WorkstationBuildResult.started(updateBuildSiteMaterialStatus(createPendingBuildSite(
			level,
			structureKind,
			blueprint,
			settlementId,
			site.origin(),
			doorPos.immutable(),
			doorPos.immutable(),
			site.facing(),
			level.getServer().getTickCount()
		), stock, level.getServer().getTickCount()));
	}

	private static StructurePreview previewBuildSite(SettlementBuildSite buildSite, String previewType, boolean placementValid) {
		return previewBuildSite(
			buildSite,
			previewType,
			placementValid ? PlacementPreviewResult.valid(null) : PlacementPreviewResult.invalid("This structure cannot be placed here.", buildSite.origin())
		);
	}

	private static StructurePreview previewBuildSite(SettlementBuildSite buildSite, String previewType, PlacementPreviewResult placement) {
		List<StructurePreviewBlock> blocks = new ArrayList<>();

		for (SettlementBuildBlockState block : buildSite.blocks()) {
			Optional<StructurePreviewBlock> previewBlock = structurePreviewBlock(buildSite, block);
			previewBlock.ifPresent(blocks::add);
		}

		return new StructurePreview(buildSite.id(), previewType, placement.valid(), placement.statusMessage(), placement.blockerPositions(), blocks);
	}

	private static Optional<StructurePreviewBlock> structurePreviewBlock(SettlementBuildSite buildSite, SettlementBuildBlockState block) {
		Optional<BlockPos> pos = buildSiteBlockPos(buildSite, block);
		BlockState plannedState = plannedBuildSiteBlockState(buildSite, block);

		if (pos.isEmpty() || plannedState == null || plannedState.isAir()) {
			return Optional.empty();
		}

		return Optional.of(new StructurePreviewBlock(
			pos.get(),
			block.expectedMaterialKey(),
			BuiltInRegistries.BLOCK.getKey(plannedState.getBlock()).toString()
		));
	}

	private static List<StructurePreviewBlock> previewStructureBlocks(
		BlockPos origin,
		Direction facing,
		StructureKind structureKind,
		StructureBlueprint blueprint
	) {
		List<StructurePreviewBlock> blocks = new ArrayList<>();

		for (int layerIndex = 0; layerIndex < blueprint.layers().length; layerIndex++) {
			int up = blueprint.minUp() + layerIndex;
			String[] rows = blueprint.layers()[layerIndex];

			for (int row = 0; row < rows.length; row++) {
				String rowPattern = rows[row];
				int forward = blueprint.minForward() + row;

				for (int column = 0; column < rowPattern.length(); column++) {
					int right = blueprint.minRight() + column;
					char symbol = rowPattern.charAt(column);

					if (symbol == 'A') {
						continue;
					}

					BlockState plannedState = plannedBlueprintBlockState(
						structureKind,
						blueprint,
						facing,
						"oak",
						"cobblestone",
						symbol,
						right,
						forward,
						up
					);

					if (plannedState == null || plannedState.isAir()) {
						continue;
					}

					blocks.add(new StructurePreviewBlock(
						offset(origin, facing, right, forward, up),
						blueprintMaterialKey(structureKind, symbol, right, forward, up),
						BuiltInRegistries.BLOCK.getKey(plannedState.getBlock()).toString()
					));
				}
			}
		}

		return blocks;
	}

	private static BlockPos previewDockOrigin(ServerLevel level, BlockPos anchorPos, Direction facing) {
		for (int step = 1; step <= 8; step++) {
			BlockPos columnPos = anchorPos.relative(facing, step);

			if (!level.hasChunkAt(columnPos)) {
				continue;
			}

			WaterColumnSurvey waterColumn = surveyWaterColumn(level, columnPos.getX(), columnPos.getZ());
			if (waterColumn.surfaceY() >= 0) {
				return new BlockPos(columnPos.getX(), waterColumn.surfaceY(), columnPos.getZ());
			}
		}

		BlockPos frontPos = anchorPos.relative(facing, 2);
		if (!level.hasChunkAt(frontPos)) {
			return frontPos.immutable();
		}

		int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, frontPos.getX(), frontPos.getZ()) - 1;
		return new BlockPos(frontPos.getX(), Math.max(anchorPos.getY(), groundY + 1), frontPos.getZ());
	}

	private static List<StructurePreviewBlock> buildDockPreviewBlocks(ServerLevel level, BlockPos anchorPos, BlockPos origin, Direction facing, boolean placementValid) {
		String woodFamily = materialPaletteFor(level, anchorPos).woodFamily();
		List<StructurePreviewBlock> blocks = new ArrayList<>();

		for (int forward = 0; forward < DOCK_LENGTH_BLOCKS; forward++) {
			for (int right = -DOCK_HALF_WIDTH_BLOCKS; right <= DOCK_HALF_WIDTH_BLOCKS; right++) {
				BlockPos deckPos = offset(origin, facing, right, forward, 0);
				blocks.add(new StructurePreviewBlock(
					deckPos,
					"planks",
					BuiltInRegistries.BLOCK.getKey(woodPlankBlock(woodFamily)).toString()
				));

				if (isDockSupportRow(forward) && Math.abs(right) == DOCK_HALF_WIDTH_BLOCKS) {
					BlockPos footing = placementValid ? findDockFooting(level, deckPos) : null;
					int bottomY = footing != null ? footing.getY() + 1 : Math.max(level.getMinY(), deckPos.getY() - 2);

					for (int y = deckPos.getY() - 1; y >= bottomY; y--) {
						blocks.add(new StructurePreviewBlock(
							new BlockPos(deckPos.getX(), y, deckPos.getZ()),
							"logs",
							BuiltInRegistries.BLOCK.getKey(woodLogBlock(woodFamily)).toString()
						));
					}
				}
			}
		}

		return blocks;
	}

	public static char currentBlueprintSymbol(SettlementBuildSite buildSite, SettlementBuildBlockState buildBlock) {
		if (buildSite.blueprintId() == SettlementBuildSiteType.DOCK
			|| buildSite.blueprintId() == SettlementBuildSiteType.PALISADE_WALL) {
			return buildBlock.blueprintSymbol().isBlank() ? 'A' : buildBlock.blueprintSymbol().charAt(0);
		}

		BlueprintRelativePos relativePos = parseRelativeBlueprintPosition(buildBlock.position());

		if (relativePos == null) {
			return 'A';
		}

		StructureBlueprint blueprint = blueprintFor(structureKindFor(buildSite.blueprintId()));
		return blueprintSymbolAt(blueprint, relativePos.right(), relativePos.forward(), relativePos.up());
	}

	public static String currentBlueprintMaterialKey(SettlementBuildSite buildSite, SettlementBuildBlockState buildBlock, char symbol) {
		if (buildSite.blueprintId() == SettlementBuildSiteType.DOCK) {
			return dockMaterialKey(symbol);
		}

		if (buildSite.blueprintId() == SettlementBuildSiteType.PALISADE_WALL) {
			return palisadeWallMaterialKey(symbol);
		}

		BlueprintRelativePos relativePos = parseRelativeBlueprintPosition(buildBlock.position());

		if (relativePos == null) {
			return "";
		}

		return blueprintMaterialKey(
			structureKindFor(buildSite.blueprintId()),
			symbol,
			relativePos.right(),
			relativePos.forward(),
			relativePos.up()
		);
	}

	public static List<SettlementBuildBlockState> currentBlueprintBlocks(SettlementBuildSite buildSite) {
		if (buildSite.blueprintId() == SettlementBuildSiteType.DOCK
			|| buildSite.blueprintId() == SettlementBuildSiteType.PALISADE_WALL) {
			return List.copyOf(buildSite.blocks());
		}

		StructureKind structureKind = structureKindFor(buildSite.blueprintId());
		StructureBlueprint blueprint = blueprintFor(structureKind);
		List<SettlementBuildBlockState> blocks = new ArrayList<>();
		Set<String> plannedPositions = new HashSet<>();

		for (int layerIndex = 0; layerIndex < blueprint.layers().length; layerIndex++) {
			int up = blueprint.minUp() + layerIndex;
			String[] rows = blueprint.layers()[layerIndex];

			for (int row = 0; row < rows.length; row++) {
				String rowPattern = rows[row];
				int forward = blueprint.minForward() + row;

				for (int column = 0; column < rowPattern.length(); column++) {
					char symbol = rowPattern.charAt(column);

					if (symbol == 'A') {
						continue;
					}

					int right = blueprint.minRight() + column;
					String materialKey = blueprintMaterialKey(structureKind, symbol, right, forward, up);
					String position = relativeBlueprintPosition(right, forward, up);

					if (isAnchoredWorkstationSymbol(structureKind, symbol, up)
						&& buildSite.anchorPos().equals(buildSite.workstationPos())) {
						blocks.add(SettlementBuildBlockState.playerPlaced(position, symbol, materialKey));
					} else {
						blocks.add(SettlementBuildBlockState.pending(position, symbol, materialKey));
					}
				}
			}
		}

		return blocks;
	}

	public static boolean isBuildSiteReplaceable(BlockState state) {
		return isReplaceable(state) || isShovelPathSurface(state);
	}

	static boolean isStructureMarginGroundBackfillBlock(SettlementBuildBlockState block) {
		return STRUCTURE_MARGIN_GROUND_BACKFILL_KEY.equals(block.expectedMaterialKey());
	}

	static boolean matchesStructureMarginGroundReplacement(BlockState state) {
		if (!state.isSolid() || isNaturalOreBlock(state)) {
			return false;
		}

		return isBuildGroundSurface(state) || isShovelPathSurface(state) || isRouteFloorReplacementSurface(state);
	}

	static boolean tryHarvestStructureMarginGroundResource(ServerLevel level, BlockPos pos, Map<String, Integer> stock) {
		BlockState state = level.getBlockState(pos);

		if (!canHarvestStructureMarginGroundResource(state)) {
			return false;
		}

		clearBlockToWarehouse(level, pos, stock);
		return true;
	}

	static boolean canHarvestStructureMarginGroundResource(BlockState state) {
		return shouldHarvestStructureMarginGroundResource(state);
	}

	static void placeStructureMarginGroundReplacement(ServerLevel level, BlockPos pos) {
		level.setBlock(pos, structureMarginGroundReplacementState(level, pos), BLOCK_UPDATE_FLAGS);
	}

	public static boolean isFlexibleMaterialMatch(BlockState currentState, BlockState plannedState, String materialKey) {
		if (materialKey == null || materialKey.isBlank()) {
			return false;
		}

		if ("smithing_station".equals(materialKey)) {
			return isSmithingStation(currentState) && isSmithingStation(plannedState);
		}

		if (!matchesMaterialFamily(currentState, plannedState, materialKey)) {
			return false;
		}

		return sharesPlacementProperties(currentState, plannedState);
	}

	public static BlockState copySharedPlacementProperties(BlockState targetState, BlockState plannedState) {
		BlockState result = targetState;

		for (Property<?> property : targetState.getProperties()) {
			if (plannedState.hasProperty(property)) {
				result = copyPropertyValue(result, plannedState, property);
			}
		}

		return result;
	}

	public static boolean isMineEntranceIntegratedStone(BlockState state) {
		return isFlexibleStoneMaterial(state);
	}

	public static boolean tryClearBuildSiteBlock(ServerLevel level, BlockPos pos, Map<String, Integer> stock) {
		BlockState state = level.getBlockState(pos);

		if (isReplaceable(state)) {
			return true;
		}

		if (!canClearForConstruction(level, pos, state)) {
			return false;
		}

		clearBlockToWarehouse(level, pos, stock);
		return true;
	}

	private static boolean matchesMaterialFamily(BlockState currentState, BlockState plannedState, String materialKey) {
		return switch (materialKey) {
			case "bed" -> currentState.getBlock() instanceof BedBlock && plannedState.getBlock() instanceof BedBlock;
			case "cobblestone" -> isFlexibleStoneMaterial(currentState) && isFlexibleStoneMaterial(plannedState);
			case "door" -> currentState.getBlock() instanceof DoorBlock && currentState.is(BlockTags.WOODEN_DOORS) && plannedState.getBlock() instanceof DoorBlock;
			case "fence" -> currentState.is(BlockTags.WOODEN_FENCES) && plannedState.is(BlockTags.WOODEN_FENCES);
			case "fence_gate" -> currentState.is(BlockTags.FENCE_GATES) && plannedState.getBlock() instanceof FenceGateBlock;
			case "glass" -> currentState.is(plannedState.getBlock());
			case "glass_display_case" -> LiveVillagesBlocks.isGlassDisplayCase(currentState) && LiveVillagesBlocks.isGlassDisplayCase(plannedState);
			case "iron_bars" -> currentState.is(Blocks.IRON_BARS) && plannedState.is(Blocks.IRON_BARS);
			case "copper_bars" -> isCopperBars(currentState) && isCopperBars(plannedState);
			case "lantern" -> currentState.hasProperty(LanternBlock.HANGING) && plannedState.hasProperty(LanternBlock.HANGING);
			case "logs" -> currentState.is(BlockTags.LOGS) && plannedState.is(BlockTags.LOGS);
			case "planks" -> currentState.is(BlockTags.PLANKS) && plannedState.is(BlockTags.PLANKS);
			case "slab" -> currentState.is(BlockTags.WOODEN_SLABS) && plannedState.getBlock() instanceof SlabBlock;
			case "smithing_station" -> isSmithingStation(currentState) && isSmithingStation(plannedState);
			case "stairs" -> currentState.is(BlockTags.WOODEN_STAIRS) && plannedState.getBlock() instanceof StairBlock;
			default -> false;
		};
	}

	private static boolean isSmithingStation(BlockState state) {
		return state.is(Blocks.BLAST_FURNACE)
			|| state.is(Blocks.SMITHING_TABLE)
			|| state.is(Blocks.GRINDSTONE);
	}

	private static boolean sharesPlacementProperties(BlockState currentState, BlockState plannedState) {
		for (Property<?> property : plannedState.getProperties()) {
			if (shouldIgnorePlacementProperty(currentState, plannedState, property)) {
				continue;
			}

			if (currentState.hasProperty(property) && !samePropertyValue(currentState, plannedState, property)) {
				return false;
			}
		}

		return true;
	}

	private static boolean shouldIgnorePlacementProperty(BlockState currentState, BlockState plannedState, Property<?> property) {
		String name = property.getName();

		if ("waterlogged".equals(name)) {
			return true;
		}

		if (currentState.getBlock() instanceof DoorBlock && plannedState.getBlock() instanceof DoorBlock) {
			return "open".equals(name) || "powered".equals(name) || "hinge".equals(name);
		}

		if (currentState.getBlock() instanceof FenceGateBlock && plannedState.getBlock() instanceof FenceGateBlock) {
			return "open".equals(name) || "powered".equals(name) || "in_wall".equals(name);
		}

		if (currentState.getBlock() instanceof StairBlock && plannedState.getBlock() instanceof StairBlock) {
			return "shape".equals(name);
		}

		if (currentState.getBlock() instanceof CrossCollisionBlock && plannedState.getBlock() instanceof CrossCollisionBlock) {
			return "north".equals(name) || "south".equals(name) || "east".equals(name) || "west".equals(name);
		}

		return false;
	}

	private static boolean isFlexibleStoneMaterial(BlockState state) {
		return state.is(Blocks.COBBLESTONE)
			|| state.is(Blocks.MOSSY_COBBLESTONE)
			|| state.is(Blocks.STONE)
			|| state.is(Blocks.SMOOTH_STONE)
			|| state.is(Blocks.GRANITE)
			|| state.is(Blocks.DIORITE)
			|| state.is(Blocks.ANDESITE)
			|| state.is(Blocks.TUFF)
			|| state.is(Blocks.CALCITE)
			|| state.is(Blocks.DEEPSLATE)
			|| state.is(Blocks.COBBLED_DEEPSLATE)
			|| state.is(Blocks.SANDSTONE)
			|| state.is(Blocks.RED_SANDSTONE)
			|| isInTag(state, BlockTags.BASE_STONE_OVERWORLD);
	}

	private static <T extends Comparable<T>> BlockState copyPropertyValue(BlockState targetState, BlockState sourceState, Property<T> property) {
		return targetState.setValue(property, sourceState.getValue(property));
	}

	private static <T extends Comparable<T>> boolean samePropertyValue(BlockState firstState, BlockState secondState, Property<T> property) {
		return firstState.getValue(property).equals(secondState.getValue(property));
	}

	public static boolean tryReplaceBuildSiteBlock(ServerLevel level, BlockPos pos, BlockState plannedState, Map<String, Integer> stock) {
		BlockState currentState = level.getBlockState(pos);

		if (currentState.equals(plannedState)) {
			return true;
		}

		if (isBuildSiteReplaceable(currentState)) {
			level.setBlock(pos, plannedState, BLOCK_UPDATE_FLAGS);
			updateChestStateAfterPlacement(level, pos);
			return true;
		}

		if (!canClearForConstruction(level, pos, currentState)) {
			return false;
		}

		String goodsKey = recoveredGoodsKey(currentState);

		if (goodsKey != null) {
			stock.merge(goodsKey, 1, Integer::sum);
		}

		level.setBlock(pos, plannedState, BLOCK_UPDATE_FLAGS);
		updateChestStateAfterPlacement(level, pos);
		return true;
	}

	private static CompletionResult tryBuildHousingShelter(ServerLevel level, SettlementState settlement, Map<String, Integer> stock) {
		Map<String, Integer> cost = SettlementProjectType.HOUSING.stockCost();

		if (!canAfford(stock, cost)) {
			return CompletionResult.notCompleted();
		}

		for (int radius = 5; radius <= buildRadius(settlement); radius++) {
			for (int offsetX = -radius; offsetX <= radius; offsetX++) {
				for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
					if (Math.max(Math.abs(offsetX), Math.abs(offsetZ)) != radius) {
						continue;
					}

					BlockPos anchor = settlement.center().offset(offsetX, 0, offsetZ);

					for (Direction facing : Direction.Plane.HORIZONTAL) {
						HousingSite site = findHousingSite(level, anchor, facing);

						if (site == null) {
							continue;
						}

						consumeCost(stock, cost);
						placeHousingShelter(level, site.origin(), site.facing(), stock);
						return CompletionResult.completed(2);
					}
				}
			}
		}

		HousingSite fallbackSite = findSimpleHousingSite(level, settlement);

		if (fallbackSite != null) {
			consumeCost(stock, cost);
			placeSimpleHousingShelter(level, fallbackSite.origin(), fallbackSite.facing(), stock);
			return CompletionResult.completed(1);
		}

		return CompletionResult.notCompleted();
	}

	private static CompletionResult tryBuildCarpenterWorkshop(ServerLevel level, SettlementState settlement, Map<String, Integer> stock) {
		Map<String, Integer> cost = SettlementProjectType.CARPENTER_WORKSHOP.stockCost();

		if (!canAfford(stock, cost)) {
			return CompletionResult.notCompleted();
		}

		for (int radius = 5; radius <= buildRadius(settlement); radius++) {
			for (int offsetX = -radius; offsetX <= radius; offsetX++) {
				for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
					if (Math.max(Math.abs(offsetX), Math.abs(offsetZ)) != radius) {
						continue;
					}

					BlockPos anchor = settlement.center().offset(offsetX, 0, offsetZ);

					for (Direction facing : Direction.Plane.HORIZONTAL) {
						HousingSite site = findCarpenterWorkshopSite(level, anchor, facing);

						if (site == null) {
							continue;
						}

						consumeCost(stock, cost);
						placeCarpenterWorkshop(level, site.origin(), site.facing(), stock);
						return CompletionResult.completed(0);
					}
				}
			}
		}

		return CompletionResult.notCompleted();
	}

	private static AnchoredStructureSite findAnchoredStructureSite(
		ServerLevel level,
		BlockPos origin,
		Direction facing,
		StructureKind structureKind,
		StructureBlueprint blueprint,
		int maxLandscapingBlocks,
		Set<BlockPos> protectedPositions
	) {
		return findAnchoredStructureSite(
			level,
			origin,
			facing,
			structureKind,
			blueprint,
			maxLandscapingBlocks,
			MAX_TERRAIN_CUT_BLOCKS,
			protectedPositions
		);
	}

	private static AnchoredStructureSite findAnchoredStructureSite(
		ServerLevel level,
		BlockPos origin,
		Direction facing,
		StructureKind structureKind,
		StructureBlueprint blueprint,
		int maxLandscapingBlocks,
		int maxTerrainCutBlocks,
		Set<BlockPos> protectedPositions
	) {
		return evaluateAnchoredStructureSite(
			level,
			origin,
			facing,
			structureKind,
			blueprint,
			maxLandscapingBlocks,
			maxTerrainCutBlocks,
			protectedPositions
		).site();
	}

	private static AnchoredStructureSite findAnchoredStructureSiteOrPlaceBlockedSign(
		ServerLevel level,
		BlockPos origin,
		Direction facing,
		StructureKind structureKind,
		StructureBlueprint blueprint,
		int maxLandscapingBlocks,
		Set<BlockPos> protectedPositions,
		BlockPos workstationPos,
		Direction signFacing
	) {
		return findAnchoredStructureSiteOrPlaceBlockedSign(
			level,
			origin,
			facing,
			structureKind,
			blueprint,
			maxLandscapingBlocks,
			MAX_TERRAIN_CUT_BLOCKS,
			protectedPositions,
			workstationPos,
			signFacing,
			true
		);
	}

	private static AnchoredStructureSite findAnchoredStructureSiteOrPlaceBlockedSign(
		ServerLevel level,
		BlockPos origin,
		Direction facing,
		StructureKind structureKind,
		StructureBlueprint blueprint,
		int maxLandscapingBlocks,
		int maxTerrainCutBlocks,
		Set<BlockPos> protectedPositions,
		BlockPos workstationPos,
		Direction signFacing
	) {
		return findAnchoredStructureSiteOrPlaceBlockedSign(
			level,
			origin,
			facing,
			structureKind,
			blueprint,
			maxLandscapingBlocks,
			maxTerrainCutBlocks,
			protectedPositions,
			workstationPos,
			signFacing,
			true
		);
	}

	private static AnchoredStructureSite findAnchoredStructureSiteOrPlaceBlockedSign(
		ServerLevel level,
		BlockPos origin,
		Direction facing,
		StructureKind structureKind,
		StructureBlueprint blueprint,
		int maxLandscapingBlocks,
		Set<BlockPos> protectedPositions,
		BlockPos workstationPos,
		Direction signFacing,
		boolean placeBlockedSign
	) {
		return findAnchoredStructureSiteOrPlaceBlockedSign(
			level,
			origin,
			facing,
			structureKind,
			blueprint,
			maxLandscapingBlocks,
			MAX_TERRAIN_CUT_BLOCKS,
			protectedPositions,
			workstationPos,
			signFacing,
			placeBlockedSign
		);
	}

	private static AnchoredStructureSite findAnchoredStructureSiteOrPlaceBlockedSign(
		ServerLevel level,
		BlockPos origin,
		Direction facing,
		StructureKind structureKind,
		StructureBlueprint blueprint,
		int maxLandscapingBlocks,
		int maxTerrainCutBlocks,
		Set<BlockPos> protectedPositions,
		BlockPos workstationPos,
		Direction signFacing,
		boolean placeBlockedSign
	) {
		PlacementPreviewResult placement = evaluateAnchoredStructureSite(
			level,
			origin,
			facing,
			structureKind,
			blueprint,
			maxLandscapingBlocks,
			maxTerrainCutBlocks,
			protectedPositions
		);

		if (!placement.valid() || placement.site() == null) {
			if (placeBlockedSign && !SUPPRESS_BLOCKED_STRUCTURE_SIGNS.get()) {
				placeCantBuildHereSign(level, workstationPos, signFacing, placement.statusMessage());
			}

			return null;
		}

		return placement.site();
	}

	private static PlacementPreviewResult evaluateAnchoredStructureSite(
		ServerLevel level,
		BlockPos origin,
		Direction facing,
		StructureKind structureKind,
		StructureBlueprint blueprint,
		int maxLandscapingBlocks,
		Set<BlockPos> protectedPositions
	) {
		return evaluateAnchoredStructureSite(
			level,
			origin,
			facing,
			structureKind,
			blueprint,
			maxLandscapingBlocks,
			MAX_TERRAIN_CUT_BLOCKS,
			protectedPositions
		);
	}

	private static PlacementPreviewResult evaluateAnchoredStructureSite(
		ServerLevel level,
		BlockPos origin,
		Direction facing,
		StructureKind structureKind,
		StructureBlueprint blueprint,
		int maxLandscapingBlocks,
		int maxTerrainCutBlocks,
		Set<BlockPos> protectedPositions
	) {
		int baseY = origin.getY();
		int landscapingBlocks = 0;
		int minRight = blueprint.minRight();
		int maxRight = blueprint.maxRight();
		int minForward = blueprint.minForward();
		int maxForward = blueprint.maxForward();
		int maxClearHeight = blueprint.clearHeight();

		PlacementPreviewResult spacingResult = evaluateStructureSpacingClearance(level, origin, facing, minRight, maxRight, minForward, maxForward, maxClearHeight, protectedPositions);
		if (!spacingResult.valid()) {
			return spacingResult;
		}

		if (structureKind == StructureKind.MINE_ENTRANCE) {
			PlacementPreviewResult frontAccessResult = evaluateMineEntranceFrontAccessClearance(level, origin, facing, protectedPositions);
			if (!frontAccessResult.valid()) {
				return frontAccessResult;
			}
		}

		for (int right = minRight; right <= maxRight; right++) {
			for (int forward = minForward; forward <= maxForward; forward++) {
				BlockPos floorPos = offset(origin, facing, right, forward, 0);

				if (!level.hasChunkAt(floorPos)) {
					return PlacementPreviewResult.invalid("Site extends into unloaded terrain.", floorPos);
				}

				BlockPos groundPos = resolveStructureGroundPos(level, floorPos);

				if (groundPos == null) {
					return PlacementPreviewResult.invalid("Could not find suitable ground for this structure.", floorPos);
				}
				int groundY = groundPos.getY();

				while (protectedPositions.contains(groundPos) && groundY > level.getMinY()) {
					groundY--;
					groundPos = groundPos.below();
				}

				if (groundY > baseY + maxTerrainCutBlocks || groundY < baseY - MAX_TERRAIN_FILL_BLOCKS) {
					return PlacementPreviewResult.invalid("Terrain is too uneven for this structure.", groundPos);
				}

				if (!hasStableBuildGround(level, groundPos)) {
					return PlacementPreviewResult.invalid("Ground is not stable enough to support this structure.", groundPos);
				}

				if (groundY < baseY) {
					for (int y = groundY + 1; y <= baseY; y++) {
						BlockPos fillPos = new BlockPos(floorPos.getX(), y, floorPos.getZ());

						if (protectedPositions.contains(fillPos)) {
							continue;
						}

						BlockState fillState = level.getBlockState(fillPos);

						if (!isAllowedFootprintFillBlock(level, fillPos, fillState)) {
							return PlacementPreviewResult.invalid(footprintFillFailureMessage(level, fillPos, fillState), fillPos);
						}

						landscapingBlocks++;
					}
				} else {
					BlockPos supportPos = new BlockPos(floorPos.getX(), baseY, floorPos.getZ());
					BlockState supportState = level.getBlockState(supportPos);
					char baseSymbol = blueprintSymbolAt(blueprint, right, forward, 0);

					if (!supportState.isSolid()) {
						return PlacementPreviewResult.invalid("Ground support is too weak at the structure base.", supportPos);
					}

					if (!isAllowedFootprintBaseBlock(level, supportPos, supportState, structureKind, baseSymbol)) {
						return PlacementPreviewResult.invalid(footprintBaseFailureMessage(level, supportPos, supportState), supportPos);
					}
				}

				for (int y = baseY + 1; y <= Math.max(baseY + maxClearHeight, groundY); y++) {
					BlockPos clearPos = new BlockPos(floorPos.getX(), y, floorPos.getZ());

					if (protectedPositions.contains(clearPos)) {
						continue;
					}

					BlockState clearState = level.getBlockState(clearPos);

					if (isStructuredGardenBlock(level, clearPos, clearState)) {
						return PlacementPreviewResult.invalid("Blocked by an existing structured garden or decorative planting.", clearPos);
					}

					if (isReplaceable(clearState)) {
						continue;
					}

					if (!canLandscapeRemove(level, clearPos, clearState)) {
						return PlacementPreviewResult.invalid(clearanceFailureMessage(level, clearPos, clearState), clearPos);
					}

					landscapingBlocks++;
				}

				if (landscapingBlocks > maxLandscapingBlocks) {
					return PlacementPreviewResult.invalid("Too much clearing or fill would be required here.", floorPos);
				}
			}
		}

		return PlacementPreviewResult.valid(new AnchoredStructureSite(origin, facing));
	}

	private static HousingSite findFreestandingStructureSite(
		ServerLevel level,
		BlockPos anchor,
		Direction facing,
		StructureKind structureKind,
		StructureBlueprint blueprint,
		int maxLandscapingBlocks
	) {
		int minGroundY = Integer.MAX_VALUE;
		int maxGroundY = Integer.MIN_VALUE;
		int groundTotal = 0;
		int footprintBlocks = 0;

		for (int right = blueprint.minRight(); right <= blueprint.maxRight(); right++) {
			for (int forward = blueprint.minForward(); forward <= blueprint.maxForward(); forward++) {
				BlockPos worldPos = offset(anchor, facing, right, forward, 0);

				if (!level.hasChunkAt(worldPos)) {
					return null;
				}

				BlockPos groundPos = resolveStructureGroundPos(level, worldPos);

				if (groundPos == null) {
					return null;
				}
				int groundY = groundPos.getY();

				minGroundY = Math.min(minGroundY, groundY);
				maxGroundY = Math.max(maxGroundY, groundY);
				groundTotal += groundY;
				footprintBlocks++;
			}
		}

		if (footprintBlocks <= 0) {
			return null;
		}

		int minBuildY = maxGroundY - MAX_TERRAIN_CUT_BLOCKS;
		int maxBuildY = minGroundY + MAX_TERRAIN_FILL_BLOCKS;

		if (minBuildY > maxBuildY) {
			return null;
		}

		int averageGroundY = Math.round(groundTotal / (float) footprintBlocks);
		int baseY = clamp(averageGroundY, minBuildY, maxBuildY);
		int landscapingBlocks = 0;
		BlockPos origin = new BlockPos(anchor.getX(), baseY, anchor.getZ());

		if (!hasStructureSpacingClearance(
			level,
			origin,
			facing,
			blueprint.minRight(),
			blueprint.maxRight(),
			blueprint.minForward(),
			blueprint.maxForward(),
			blueprint.clearHeight(),
			Set.of()
		)) {
			return null;
		}

		for (int right = blueprint.minRight(); right <= blueprint.maxRight(); right++) {
			for (int forward = blueprint.minForward(); forward <= blueprint.maxForward(); forward++) {
				BlockPos floorPos = offset(origin, facing, right, forward, 0);
				BlockPos groundPos = resolveStructureGroundPos(level, floorPos);

				if (groundPos == null) {
					return null;
				}
				int groundY = groundPos.getY();

				if (!hasStableBuildGround(level, groundPos)) {
					return null;
				}

				if (groundY < baseY) {
					for (int y = groundY + 1; y <= baseY; y++) {
						BlockPos fillPos = new BlockPos(floorPos.getX(), y, floorPos.getZ());

						BlockState fillState = level.getBlockState(fillPos);

						if (!isAllowedFootprintFillBlock(level, fillPos, fillState)) {
							return null;
						}

						landscapingBlocks++;
					}
				} else {
					BlockPos supportPos = new BlockPos(floorPos.getX(), baseY, floorPos.getZ());
					BlockState supportState = level.getBlockState(supportPos);
					char baseSymbol = blueprintSymbolAt(blueprint, right, forward, 0);

					if (!supportState.isSolid() || !isAllowedFootprintBaseBlock(level, supportPos, supportState, structureKind, baseSymbol)) {
						return null;
					}
				}

				for (int y = baseY + 1; y <= Math.max(baseY + blueprint.clearHeight(), groundY); y++) {
					BlockPos clearPos = new BlockPos(floorPos.getX(), y, floorPos.getZ());
					BlockState clearState = level.getBlockState(clearPos);

					if (isStructuredGardenBlock(level, clearPos, clearState)) {
						return null;
					}

					if (isReplaceable(clearState)) {
						continue;
					}

					if (!canLandscapeRemove(level, clearPos, clearState)) {
						return null;
					}

					landscapingBlocks++;
				}

				if (landscapingBlocks > maxLandscapingBlocks) {
					return null;
				}
			}
		}

		return new HousingSite(origin, facing);
	}

	private static HousingSite findCarpenterWorkshopSite(ServerLevel level, BlockPos anchor, Direction facing) {
		return findFreestandingStructureSite(level, anchor, facing, StructureKind.CARPENTER_WORKSHOP, CARPENTER_WORKSHOP_BLUEPRINT, MAX_WORKSHOP_SITE_LANDSCAPING_BLOCKS);
	}

	private static CompletionResult tryPlaceComposter(ServerLevel level, SettlementState settlement, Map<String, Integer> stock) {
		Map<String, Integer> cost = SettlementProjectType.COMPOSTER.stockCost();

		if (!canAfford(stock, cost)) {
			return CompletionResult.notCompleted();
		}

		BlockPos placementPos = findComposterSite(level, settlement);

		if (placementPos == null) {
			return CompletionResult.notCompleted();
		}

		consumeCost(stock, cost);
		level.setBlock(placementPos, Blocks.COMPOSTER.defaultBlockState(), BLOCK_UPDATE_FLAGS);
		return CompletionResult.completed(0);
	}

	private static CompletionResult tryPlaceStorage(ServerLevel level, SettlementState settlement, Map<String, Integer> stock) {
		Map<String, Integer> cost = SettlementProjectType.STORAGE.stockCost();

		if (!canAfford(stock, cost)) {
			return CompletionResult.notCompleted();
		}

		StorageSite site = findStorageSite(level, settlement);

		if (site == null) {
			return CompletionResult.notCompleted();
		}

		consumeCost(stock, cost);
		level.setBlock(site.pos().below(), Blocks.COBBLESTONE.defaultBlockState(), BLOCK_UPDATE_FLAGS);
		level.setBlock(site.pos(), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, site.facing()), BLOCK_UPDATE_FLAGS);
		return CompletionResult.completed(0);
	}

	private static HousingSite findHousingSite(ServerLevel level, BlockPos anchor, Direction facing) {
		return findFreestandingStructureSite(level, anchor, facing, StructureKind.HOUSING_SHELTER, HOUSING_SHELTER_BLUEPRINT, MAX_SITE_LANDSCAPING_BLOCKS);
	}

	private static void placeHousingShelter(ServerLevel level, BlockPos origin, Direction facing, Map<String, Integer> stock) {
		placeStructureBlueprint(level, origin, facing, stock, null, HOUSING_SHELTER_BLUEPRINT, StructureKind.HOUSING_SHELTER);
	}

	private static HousingSite findSimpleHousingSite(ServerLevel level, SettlementState settlement) {
		for (int radius = 2; radius <= buildRadius(settlement); radius++) {
			for (int offsetX = -radius; offsetX <= radius; offsetX++) {
				for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
					if (Math.max(Math.abs(offsetX), Math.abs(offsetZ)) != radius) {
						continue;
					}

					BlockPos footPos = topPlacementPos(level, settlement.center().offset(offsetX, 0, offsetZ));

					if (footPos == null) {
						continue;
					}

					for (Direction shelterFacing : Direction.Plane.HORIZONTAL) {
						HousingSite site = findFreestandingStructureSite(
							level,
							footPos,
							shelterFacing,
							StructureKind.SIMPLE_HOUSING_SHELTER,
							SIMPLE_HOUSING_SHELTER_BLUEPRINT,
							MAX_SITE_LANDSCAPING_BLOCKS
						);

						if (site != null) {
							return site;
						}
					}
				}
			}
		}

		return null;
	}

	private static void placeSimpleHousingShelter(ServerLevel level, BlockPos origin, Direction facing, Map<String, Integer> stock) {
		placeStructureBlueprint(level, origin, facing, stock, null, SIMPLE_HOUSING_SHELTER_BLUEPRINT, StructureKind.SIMPLE_HOUSING_SHELTER);
	}

	private static void placeCarpenterWorkshop(ServerLevel level, BlockPos origin, Direction facing, Map<String, Integer> stock) {
		placeCarpenterWorkshop(level, origin, facing, stock, null);
	}

	private static void placeCarpenterWorkshop(ServerLevel level, BlockPos origin, Direction facing, Map<String, Integer> stock, BlockPos protectedWorkstationPos) {
		placeStructureBlueprint(level, origin, facing, stock, protectedWorkstationPos, CARPENTER_WORKSHOP_BLUEPRINT, StructureKind.CARPENTER_WORKSHOP);
	}

	private static void placeTradingPost(ServerLevel level, BlockPos origin, Direction facing, Map<String, Integer> stock, BlockPos protectedWorkstationPos) {
		placeStructureBlueprint(level, origin, facing, stock, protectedWorkstationPos, TRADING_POST_BLUEPRINT, StructureKind.TRADING_POST);
	}

	private static BlockPos shelterOriginForDoor(BlockPos doorPos, Direction facing) {
		return doorPos.below().relative(facing.getOpposite(), 2);
	}

	private static BlockPos doorAnchoredOriginFor(StructureKind structureKind, BlockPos doorPos, Direction facing) {
		if (isPalisadeGatehouseKind(structureKind)) {
			return doorPos.immutable();
		}
		return shelterOriginForDoor(doorPos, facing);
	}

	private static boolean isPalisadeGatehouseKind(StructureKind structureKind) {
		return structureKind == StructureKind.PALISADE_GATEHOUSE || structureKind == StructureKind.COPPER_PALISADE_GATEHOUSE;
	}

	private static SettlementBuildSite withPalisadeGatehouseDoorMarkerBlock(SettlementBuildSite buildSite, StructureKind structureKind, long tick) {
		if (!isPalisadeGatehouseKind(structureKind)) {
			return buildSite;
		}

		return withPalisadeGatehouseDoorBlocks(buildSite, tick);
	}

	private static void placeStructureBlueprint(
		ServerLevel level,
		BlockPos origin,
		Direction facing,
		Map<String, Integer> stock,
		BlockPos protectedWorkstationPos,
		StructureBlueprint blueprint,
		StructureKind structureKind
	) {
		prepareStructureFootprint(level, origin, facing, stock, protectedWorkstationPos, blueprint);

		for (int layerIndex = 0; layerIndex < blueprint.layers().length; layerIndex++) {
			int up = blueprint.minUp() + layerIndex;
			String[] rows = blueprint.layers()[layerIndex];

			for (int row = 0; row < rows.length; row++) {
				String rowPattern = rows[row];
				int forward = blueprint.minForward() + row;

				for (int column = 0; column < rowPattern.length(); column++) {
					int right = blueprint.minRight() + column;
					char symbol = rowPattern.charAt(column);
					BlockPos pos = offset(origin, facing, right, forward, up);
					placeBlueprintSymbol(level, pos, facing, stock, protectedWorkstationPos, blueprint, structureKind, symbol, right, forward, up);
				}
			}
		}

		placeBlueprintBeds(level, origin, facing, structureKind);
	}

	private static void prepareStructureFootprint(
		ServerLevel level,
		BlockPos origin,
		Direction facing,
		Map<String, Integer> stock,
		BlockPos protectedWorkstationPos,
		StructureBlueprint blueprint
	) {
		for (int right = blueprint.minRight(); right <= blueprint.maxRight(); right++) {
			for (int forward = blueprint.minForward(); forward <= blueprint.maxForward(); forward++) {
				BlockPos floorPos = offset(origin, facing, right, forward, 0);
				int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, floorPos.getX(), floorPos.getZ()) - 1;
				BlockPos groundPos = new BlockPos(floorPos.getX(), groundY, floorPos.getZ());

				while (groundY > level.getMinY() && groundPos.equals(protectedWorkstationPos)) {
					groundY--;
					groundPos = groundPos.below();
				}

				for (int y = groundY + 1; y < origin.getY(); y++) {
					BlockPos fillPos = new BlockPos(floorPos.getX(), y, floorPos.getZ());

					if (!fillPos.equals(protectedWorkstationPos)) {
						level.setBlock(fillPos, Blocks.COBBLESTONE.defaultBlockState(), BLOCK_UPDATE_FLAGS);
					}
				}

				for (int y = origin.getY() + 1; y <= Math.max(origin.getY() + blueprint.clearHeight(), groundY); y++) {
					BlockPos clearPos = new BlockPos(floorPos.getX(), y, floorPos.getZ());

					if (!clearPos.equals(protectedWorkstationPos)) {
						clearBlockToWarehouse(level, clearPos, stock);
					}
				}
			}
		}
	}

	private static void placeBlueprintSymbol(
		ServerLevel level,
		BlockPos pos,
		Direction facing,
		Map<String, Integer> stock,
		BlockPos protectedWorkstationPos,
		StructureBlueprint blueprint,
		StructureKind structureKind,
		char symbol,
		int right,
		int forward,
		int up
	) {
		if (pos.equals(protectedWorkstationPos)) {
			return;
		}

		if (symbol == 'A' || isBedSymbol(structureKind, symbol, up)) {
			clearBlockToWarehouse(level, pos, stock);
			return;
		}

		if (symbol == 'D') {
			if (up == 1) {
				level.setBlock(
					pos,
					woodDoorBlock(materialPaletteFor(level, pos).woodFamily()).defaultBlockState()
						.setValue(DoorBlock.FACING, blueprintDoorFacing(structureKind, facing))
						.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER),
					BLOCK_UPDATE_FLAGS
				);
				level.setBlock(
					pos.above(),
					woodDoorBlock(materialPaletteFor(level, pos).woodFamily()).defaultBlockState()
						.setValue(DoorBlock.FACING, blueprintDoorFacing(structureKind, facing))
						.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER),
					BLOCK_UPDATE_FLAGS
				);
			}

			return;
		}

		StructureMaterialPalette palette = materialPaletteFor(level, pos);
		BlockState state = blueprintStateFor(facing, blueprint, structureKind, palette.woodFamily(), palette.stoneMaterial(), symbol, right, forward, up);

		if (state == null) {
			clearBlockToWarehouse(level, pos, stock);
			return;
		}

		setBlueprintBlock(level, pos, state, stock);
	}

	private static BlockState blueprintStateFor(
		Direction facing,
		StructureBlueprint blueprint,
		StructureKind structureKind,
		String woodFamily,
		String stoneMaterial,
		char symbol,
		int right,
		int forward,
		int up
	) {
		return switch (symbol) {
			case 'E' -> Blocks.AIR.defaultBlockState();
			case 'C' -> structureKind == StructureKind.FORESTER_WORKSHOP
				? woodLogBlock(woodFamily).defaultBlockState()
				: (structureKind == StructureKind.LIGHTHOUSE
					|| structureKind == StructureKind.MINE_ENTRANCE
					|| structureKind == StructureKind.MASON_WORKSHOP
			|| structureKind == StructureKind.TRADING_POST
			|| structureKind == StructureKind.CARTOGRAPHER_HOUSE
			|| structureKind == StructureKind.FLETCHER_HUT
			|| structureKind == StructureKind.BUTCHER_SHOP
			|| structureKind == StructureKind.BEEKEEPER_APIARY
			|| structureKind == StructureKind.CLERIC_SHRINE
			|| structureKind == StructureKind.LEATHERWORKER_WORKSHOP
			|| structureKind == StructureKind.LIBRARY
			|| structureKind == StructureKind.GARDENER_SHED
			|| structureKind == StructureKind.GUARD_POST
			|| structureKind == StructureKind.SCRIBE_OFFICE
			|| structureKind == StructureKind.SHEPHERD_HUT
			|| structureKind == StructureKind.SMITHY)
				? stoneBlock(stoneMaterial).defaultBlockState()
				: woodPlankBlock(woodFamily).defaultBlockState();
			case 'H' -> chestStateFor(blueprint, facing, right, forward, up);
			case 'I' -> structureKind == StructureKind.COPPER_PALISADE_GATEHOUSE
				? Blocks.COPPER_BARS.unaffected().defaultBlockState()
				: Blocks.IRON_BARS.defaultBlockState();
			case 'L' -> logStateFor(blueprint, facing, woodFamily, right, forward, up);
			case 'M' -> stoneBlock(stoneMaterial).defaultBlockState();
			case 'P' -> woodPlankBlock(woodFamily).defaultBlockState();
			case 'F' -> fenceStateFor(blueprint, structureKind, facing, woodFamily, right, forward, up);
			case 'G' -> woodFenceGateBlock(woodFamily).defaultBlockState().setValue(FenceGateBlock.FACING, fenceGateFacingFor(blueprint, structureKind, facing, right, forward, up));
			case 'N' -> lanternStateFor(structureKind);
			case 'R' -> ladderStateFor(blueprint, facing, right, forward, up);
			case 'T' -> torchStateFor(blueprint, facing, right, forward, up);
			case 'V' -> structureKind == StructureKind.CARTOGRAPHER_HOUSE ? Blocks.GLASS_PANE.defaultBlockState() : Blocks.GLASS.defaultBlockState();
			case 'K' -> Blocks.CAMPFIRE.defaultBlockState();
			case 'W' -> workstationStateFor(structureKind, facing);
			case 'Q' -> displayCaseStateFor(blueprint, facing, right, forward, up);
			case 'B' -> slabStateFor(structureKind, woodFamily, up);
			case 'S' -> stairStateFor(blueprint, structureKind, facing, woodFamily, right, forward, up);
			default -> null;
		};
	}

	private static BlockState chestStateFor(StructureBlueprint blueprint, Direction facing, int right, int forward, int up) {
		return Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, chestFacingFor(blueprint, facing, right, forward, up));
	}

	private static BlockState displayCaseStateFor(StructureBlueprint blueprint, Direction facing, int right, int forward, int up) {
		Direction explicitFacing = explicitBlueprintFacing(blueprint, facing, right, forward, up);
		return LiveVillagesBlocks.GLASS_DISPLAY_CASE.defaultBlockState().setValue(
			GlassDisplayCaseBlock.FACING,
			explicitFacing == null ? facing : explicitFacing
		);
	}

	private static Direction chestFacingFor(StructureBlueprint blueprint, Direction facing, int right, int forward, int up) {
		boolean pairAlongRight = isChestSymbol(blueprintSymbolAt(blueprint, right - 1, forward, up))
			|| isChestSymbol(blueprintSymbolAt(blueprint, right + 1, forward, up));
		boolean pairAlongForward = isChestSymbol(blueprintSymbolAt(blueprint, right, forward - 1, up))
			|| isChestSymbol(blueprintSymbolAt(blueprint, right, forward + 1, up));

		if (pairAlongRight) {
			if (isChestFrontOpen(blueprint, facing, right, forward, up, facing)) {
				return facing;
			}

			if (isChestFrontOpen(blueprint, facing, right, forward, up, facing.getOpposite())) {
				return facing.getOpposite();
			}

			return facing.getOpposite();
		}

		if (pairAlongForward) {
			if (isChestFrontOpen(blueprint, facing, right, forward, up, facing.getCounterClockWise())) {
				return facing.getCounterClockWise();
			}

			if (isChestFrontOpen(blueprint, facing, right, forward, up, facing.getClockWise())) {
				return facing.getClockWise();
			}

			return facing.getCounterClockWise();
		}

		for (Direction direction : new Direction[] {facing.getOpposite(), facing, facing.getCounterClockWise(), facing.getClockWise()}) {
			if (isChestFrontOpen(blueprint, facing, right, forward, up, direction)) {
				return direction;
			}
		}

		return facing.getOpposite();
	}

	private static boolean isChestFrontOpen(StructureBlueprint blueprint, Direction structureFacing, int right, int forward, int up, Direction chestFacing) {
		BlueprintRelativeStep step = relativeStepForDirection(structureFacing, chestFacing);
		char symbol = blueprintSymbolAt(blueprint, right + step.right(), forward + step.forward(), up);
		return symbol == 'A' || symbol == 'D' || symbol == 'E';
	}

	private static boolean isChestSymbol(char symbol) {
		return symbol == 'H';
	}

	private static BlockState logStateFor(StructureBlueprint blueprint, Direction facing, String woodFamily, int right, int forward, int up) {
		BlockState state = woodLogBlock(woodFamily).defaultBlockState();
		Direction explicitFacing = explicitBlueprintFacing(blueprint, facing, right, forward, up);

		if (explicitFacing == null) {
			return state;
		}

		return state.setValue(RotatedPillarBlock.AXIS, explicitFacing.getAxis());
	}

	private static BlockState ladderStateFor(StructureBlueprint blueprint, Direction facing, int right, int forward, int up) {
		Direction explicitFacing = explicitBlueprintFacing(blueprint, facing, right, forward, up);
		return Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, explicitFacing == null ? facing : explicitFacing);
	}

	private static BlockState fenceStateFor(
		StructureBlueprint blueprint,
		StructureKind structureKind,
		Direction facing,
		String woodFamily,
		int right,
		int forward,
		int up
	) {
		BlockState state = woodFenceBlock(woodFamily).defaultBlockState();

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlueprintRelativeStep step = relativeStepForDirection(facing, direction);
			char adjacentSymbol = blueprintSymbolAt(blueprint, right + step.right(), forward + step.forward(), up);

			if (fenceConnectsToSymbol(structureKind, adjacentSymbol, up)) {
				state = state.setValue(CrossCollisionBlock.PROPERTY_BY_DIRECTION.get(direction), true);
			}
		}

		return state;
	}

	private static Direction fenceGateFacingFor(
		StructureBlueprint blueprint,
		StructureKind structureKind,
		Direction facing,
		int right,
		int forward,
		int up
	) {
		boolean supportedLeft = fenceConnectsToSymbol(structureKind, blueprintSymbolAt(blueprint, right - 1, forward, up), up);
		boolean supportedRight = fenceConnectsToSymbol(structureKind, blueprintSymbolAt(blueprint, right + 1, forward, up), up);
		boolean supportedBack = fenceConnectsToSymbol(structureKind, blueprintSymbolAt(blueprint, right, forward - 1, up), up);
		boolean supportedFront = fenceConnectsToSymbol(structureKind, blueprintSymbolAt(blueprint, right, forward + 1, up), up);

		if ((supportedBack || supportedFront) && !(supportedLeft || supportedRight)) {
			int midRight = blueprint.minRight() + ((blueprint.maxRight() - blueprint.minRight()) / 2);
			return right <= midRight ? facing.getClockWise() : facing.getCounterClockWise();
		}

		if ((supportedLeft || supportedRight) && !(supportedBack || supportedFront)) {
			return facing;
		}

		return facing;
	}

	private static BlockState workstationStateFor(StructureKind structureKind, Direction facing) {
		if (structureKind == StructureKind.LIGHTHOUSE) {
			return LiveVillagesBlocks.LIGHTHOUSE.defaultBlockState();
		}

		if (structureKind == StructureKind.BAKERY) {
			return LiveVillagesBlocks.BAKERS_COUNTER.defaultBlockState().setValue(BakersCounterBlock.FACING, facing);
		}

		if (structureKind == StructureKind.TRADING_POST) {
			return LiveVillagesBlocks.TRADE_BOARD.defaultBlockState().setValue(TradeBoardBlock.FACING, facing);
		}

		if (structureKind == StructureKind.ROADWRIGHT_WORKSHOP) {
			return LiveVillagesBlocks.SURVEYOR_TABLE.defaultBlockState();
		}

		if (structureKind == StructureKind.SCRIBE_OFFICE) {
			return LiveVillagesBlocks.SCRIBE_DESK.defaultBlockState().setValue(ScribeDeskBlock.FACING, facing);
		}

		if (structureKind == StructureKind.GUARD_POST) {
			return LiveVillagesBlocks.GUARD_POST.defaultBlockState().setValue(GuardPostBlock.FACING, facing);
		}

		if (structureKind == StructureKind.GARDENER_SHED) {
			return LiveVillagesBlocks.GARDENER_WORKSTATION.defaultBlockState().setValue(GardenerWorkstationBlock.FACING, facing);
		}

		if (structureKind == StructureKind.BEEKEEPER_APIARY) {
			return LiveVillagesBlocks.HONEY_SEPARATOR.defaultBlockState().setValue(HoneySeparatorBlock.FACING, facing);
		}

		if (structureKind == StructureKind.MASON_WORKSHOP) {
			return Blocks.STONECUTTER.defaultBlockState();
		}

		if (structureKind == StructureKind.FORESTER_WORKSHOP) {
			return LiveVillagesBlocks.FORESTER_TABLE.defaultBlockState();
		}

		if (structureKind == StructureKind.MINE_ENTRANCE) {
			return LiveVillagesBlocks.MINER_WORKSTATION.defaultBlockState();
		}

		if (structureKind == StructureKind.CARTOGRAPHER_HOUSE) {
			return Blocks.CARTOGRAPHY_TABLE.defaultBlockState();
		}

		if (structureKind == StructureKind.FLETCHER_HUT) {
			return Blocks.FLETCHING_TABLE.defaultBlockState();
		}

		if (structureKind == StructureKind.BUTCHER_SHOP) {
			return Blocks.SMOKER.defaultBlockState();
		}

		if (structureKind == StructureKind.CLERIC_SHRINE) {
			return Blocks.BREWING_STAND.defaultBlockState();
		}

		if (structureKind == StructureKind.LEATHERWORKER_WORKSHOP) {
			return Blocks.CAULDRON.defaultBlockState();
		}

		if (structureKind == StructureKind.LIBRARY) {
			return Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, facing.getOpposite());
		}

		if (structureKind == StructureKind.SHEPHERD_HUT) {
			return Blocks.LOOM.defaultBlockState();
		}

		if (structureKind == StructureKind.SMITHY) {
			return Blocks.BLAST_FURNACE.defaultBlockState();
		}

		return LiveVillagesBlocks.CARPENTER_BENCH.defaultBlockState();
	}

	private static BlockState slabStateFor(StructureKind structureKind, String woodFamily, int up) {
		BlockState state = woodSlabBlock(woodFamily).defaultBlockState();

		if ((structureKind == StructureKind.HOUSING_SHELTER && up == 4)
			|| (structureKind == StructureKind.SIMPLE_HOUSING_SHELTER && up == 3)) {
			return state.setValue(SlabBlock.TYPE, SlabType.TOP);
		}

		if (isPalisadeGatehouseKind(structureKind) && up == 2) {
			return state.setValue(SlabBlock.TYPE, SlabType.TOP);
		}

		if ((structureKind == StructureKind.CARPENTER_WORKSHOP
			|| structureKind == StructureKind.ROADWRIGHT_WORKSHOP
			|| structureKind == StructureKind.MASON_WORKSHOP
			|| structureKind == StructureKind.FORESTER_WORKSHOP
			|| structureKind == StructureKind.FLETCHER_HUT
			|| structureKind == StructureKind.BEEKEEPER_APIARY
			|| structureKind == StructureKind.BUTCHER_SHOP
			|| structureKind == StructureKind.CLERIC_SHRINE
			|| structureKind == StructureKind.LEATHERWORKER_WORKSHOP
			|| structureKind == StructureKind.LIBRARY
			|| structureKind == StructureKind.GARDENER_SHED
			|| structureKind == StructureKind.GUARD_POST
			|| structureKind == StructureKind.SCRIBE_OFFICE
			|| structureKind == StructureKind.SHEPHERD_HUT
			|| structureKind == StructureKind.SMITHY) && up == 5) {
			return state.setValue(SlabBlock.TYPE, SlabType.TOP);
		}

		return state;
	}

	private static BlockState lanternStateFor() {
		return Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
	}

	private static BlockState lanternStateFor(StructureKind structureKind) {
		if (structureKind == StructureKind.MINE_ENTRANCE) {
			return Blocks.COPPER_LANTERN.unaffected().defaultBlockState().setValue(LanternBlock.HANGING, true);
		}

		return lanternStateFor();
	}

	private static BlockState torchStateFor(StructureBlueprint blueprint, Direction facing, int right, int forward, int up) {
		return Blocks.WALL_TORCH.defaultBlockState()
			.setValue(WallTorchBlock.FACING, blueprintTorchFacing(blueprint, facing, right, forward, up));
	}

	private static BlockState stairStateFor(
		StructureBlueprint blueprint,
		StructureKind structureKind,
		Direction facing,
		String woodFamily,
		int right,
		int forward,
		int up
	) {
		BlockState state = woodStairBlock(woodFamily).defaultBlockState()
			.setValue(StairBlock.FACING, blueprintStairFacing(blueprint, structureKind, facing, right, forward, up));

		if (explicitBlueprintTopHalf(blueprint, right, forward, up) || up == 3) {
			state = state.setValue(StairBlock.HALF, Half.TOP);
		}

		return state;
	}

	private static Direction blueprintStairFacing(StructureBlueprint blueprint, StructureKind structureKind, Direction facing, int right, int forward, int up) {
		Direction explicitFacing = explicitBlueprintFacing(blueprint, facing, right, forward, up);

		if (explicitFacing != null) {
			return explicitFacing;
		}

		if (usesFiveByEightGabledRoof(structureKind)) {
			if (up == 3 && (right == blueprint.minRight() || right == blueprint.maxRight())) {
				return right == blueprint.minRight() ? facing.getCounterClockWise() : facing.getClockWise();
			}

			if (up == 4 && (forward == blueprint.minForward() || forward == blueprint.maxForward())) {
				return forward == blueprint.minForward() ? facing.getOpposite() : facing;
			}

			if (up == 5 && right != 0) {
				return right < 0 ? facing.getClockWise() : facing.getCounterClockWise();
			}
		}

		if (right == blueprint.minRight()) {
			return facing.getClockWise();
		}

		if (right == blueprint.maxRight()) {
			return facing.getCounterClockWise();
		}

		if (forward == blueprint.minForward()) {
			return facing.getOpposite();
		}

		if (forward == blueprint.maxForward()) {
			return facing;
		}

		return facing;
	}

	private static boolean usesFiveByEightGabledRoof(StructureKind structureKind) {
		return structureKind == StructureKind.CARPENTER_WORKSHOP
			|| structureKind == StructureKind.ROADWRIGHT_WORKSHOP
			|| structureKind == StructureKind.MASON_WORKSHOP
			|| structureKind == StructureKind.FORESTER_WORKSHOP
			|| structureKind == StructureKind.FLETCHER_HUT
			|| structureKind == StructureKind.BUTCHER_SHOP
			|| structureKind == StructureKind.CLERIC_SHRINE
			|| structureKind == StructureKind.LEATHERWORKER_WORKSHOP
			|| structureKind == StructureKind.LIBRARY
			|| structureKind == StructureKind.SHEPHERD_HUT
			|| structureKind == StructureKind.SMITHY
			|| structureKind == StructureKind.SCRIBE_OFFICE
			|| structureKind == StructureKind.GUARD_POST
			|| structureKind == StructureKind.GARDENER_SHED
			|| structureKind == StructureKind.BEEKEEPER_APIARY;
	}

	private static Direction explicitBlueprintFacing(StructureBlueprint blueprint, Direction facing, int right, int forward, int up) {
		char orientation = blueprintOrientationAt(blueprint, right, forward, up);

		return switch (orientation) {
			case 'F', 'f' -> facing;
			case 'B', 'b' -> facing.getOpposite();
			case 'R', 'r' -> facing.getClockWise();
			case 'L', 'l' -> facing.getCounterClockWise();
			default -> null;
		};
	}

	private static boolean explicitBlueprintTopHalf(StructureBlueprint blueprint, int right, int forward, int up) {
		char orientation = blueprintOrientationAt(blueprint, right, forward, up);
		return orientation == 'f' || orientation == 'b' || orientation == 'r' || orientation == 'l';
	}

	private static Direction blueprintTorchFacing(StructureBlueprint blueprint, Direction facing, int right, int forward, int up) {
		Direction explicitFacing = explicitBlueprintFacing(blueprint, facing, right, forward, up);

		if (explicitFacing != null) {
			return explicitFacing;
		}

		Direction fallbackFacing = legacyBlueprintTorchFacing(blueprint, facing, right, forward);
		if (hasBlueprintTorchSupport(blueprint, facing, right, forward, up, fallbackFacing.getOpposite())) {
			return fallbackFacing;
		}

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			if (hasBlueprintTorchSupport(blueprint, facing, right, forward, up, direction)) {
				return direction.getOpposite();
			}
		}

		return fallbackFacing;
	}

	private static Direction legacyBlueprintTorchFacing(StructureBlueprint blueprint, Direction facing, int right, int forward) {
		if (forward == blueprint.minForward() + 1) {
			return facing;
		}

		if (right == blueprint.maxRight() - 1) {
			return facing.getCounterClockWise();
		}

		if (right == blueprint.minRight() + 1) {
			return facing.getClockWise();
		}

		if (forward == blueprint.maxForward() - 1) {
			return facing.getOpposite();
		}

		return facing;
	}

	private static boolean hasBlueprintTorchSupport(StructureBlueprint blueprint, Direction facing, int right, int forward, int up, Direction supportDirection) {
		BlueprintRelativeStep step = relativeStepForDirection(facing, supportDirection);
		char adjacentSymbol = blueprintSymbolAt(blueprint, right + step.right(), forward + step.forward(), up);
		return switch (adjacentSymbol) {
			case 'C', 'L', 'P', 'S', 'H', 'W', 'B' -> true;
			default -> false;
		};
	}

	private static BlueprintRelativeStep relativeStepForDirection(Direction facing, Direction direction) {
		if (direction == facing) {
			return new BlueprintRelativeStep(0, 1);
		}

		if (direction == facing.getOpposite()) {
			return new BlueprintRelativeStep(0, -1);
		}

		if (direction == facing.getClockWise()) {
			return new BlueprintRelativeStep(1, 0);
		}

		return new BlueprintRelativeStep(-1, 0);
	}

	private static char blueprintSymbolAt(StructureBlueprint blueprint, int right, int forward, int up) {
		int layerIndex = up - blueprint.minUp();

		if (layerIndex < 0 || layerIndex >= blueprint.layers().length) {
			return 'A';
		}

		String[] rows = blueprint.layers()[layerIndex];
		int row = forward - blueprint.minForward();

		if (row < 0 || row >= rows.length) {
			return 'A';
		}

		String rowPattern = rows[row];
		int column = right - blueprint.minRight();

		if (column < 0 || column >= rowPattern.length()) {
			return 'A';
		}

		return rowPattern.charAt(column);
	}

	private static char blueprintOrientationAt(StructureBlueprint blueprint, int right, int forward, int up) {
		int layerIndex = up - blueprint.minUp();

		if (blueprint.orientations() == null || layerIndex < 0 || layerIndex >= blueprint.orientations().length) {
			return '.';
		}

		String[] rows = blueprint.orientations()[layerIndex];
		int row = forward - blueprint.minForward();

		if (row < 0 || row >= rows.length) {
			return '.';
		}

		String rowPattern = rows[row];
		int column = right - blueprint.minRight();

		if (column < 0 || column >= rowPattern.length()) {
			return '.';
		}

		return rowPattern.charAt(column);
	}

	private static boolean fenceConnectsToSymbol(StructureKind structureKind, char symbol, int up) {
		if (symbol == 'A' || symbol == 'D' || symbol == 'E') {
			return false;
		}

		return symbol == 'F'
			|| symbol == 'G'
			|| symbol == 'L'
			|| symbol == 'P'
			|| symbol == 'C'
			|| symbol == 'H'
			|| symbol == 'W'
			|| symbol == 'V'
			|| symbol == 'T'
			|| isBedSymbol(structureKind, symbol, up);
	}

	private static Direction blueprintDoorFacing(StructureKind structureKind, Direction facing) {
		if (structureKind == StructureKind.CARTOGRAPHER_HOUSE) {
			return facing.getCounterClockWise();
		}

		return facing;
	}

	private static boolean isBedSymbol(StructureKind structureKind, char symbol, int up) {
		return symbol == 'B' && up == 1 && (structureKind == StructureKind.BAKERY
			|| structureKind == StructureKind.CARPENTER_WORKSHOP
			|| structureKind == StructureKind.BUTCHER_SHOP
			|| structureKind == StructureKind.BEEKEEPER_APIARY
			|| structureKind == StructureKind.CLERIC_SHRINE
			|| structureKind == StructureKind.FLETCHER_HUT
			|| structureKind == StructureKind.LEATHERWORKER_WORKSHOP
			|| structureKind == StructureKind.LIBRARY
			|| structureKind == StructureKind.GARDENER_SHED
			|| structureKind == StructureKind.GUARD_POST
			|| structureKind == StructureKind.FORESTER_WORKSHOP
			|| structureKind == StructureKind.HOUSING_SHELTER
			|| structureKind == StructureKind.MASON_WORKSHOP
			|| structureKind == StructureKind.ROADWRIGHT_WORKSHOP
			|| structureKind == StructureKind.SCRIBE_OFFICE
			|| structureKind == StructureKind.SHEPHERD_HUT
			|| structureKind == StructureKind.SMITHY
			|| structureKind == StructureKind.SIMPLE_HOUSING_SHELTER
			|| structureKind == StructureKind.TRADING_POST);
	}

	private static BlockState bedStateFor(StructureKind structureKind, Direction facing, int right, int forward, int up) {
		if (!isBedSymbol(structureKind, 'B', up)) {
			return null;
		}

		if ((structureKind == StructureKind.CARPENTER_WORKSHOP
			|| structureKind == StructureKind.ROADWRIGHT_WORKSHOP
			|| structureKind == StructureKind.MASON_WORKSHOP
			|| structureKind == StructureKind.FORESTER_WORKSHOP)
			&& right == -1
			&& (forward == -3 || forward == -2)) {
			return Blocks.WHITE_BED.defaultBlockState()
				.setValue(BedBlock.PART, forward == -3 ? BedPart.HEAD : BedPart.FOOT)
				.setValue(BedBlock.FACING, facing.getOpposite());
		}

		if ((structureKind == StructureKind.FLETCHER_HUT
			|| structureKind == StructureKind.BUTCHER_SHOP
			|| structureKind == StructureKind.BEEKEEPER_APIARY
			|| structureKind == StructureKind.CLERIC_SHRINE
			|| structureKind == StructureKind.LEATHERWORKER_WORKSHOP
			|| structureKind == StructureKind.LIBRARY
			|| structureKind == StructureKind.GARDENER_SHED
			|| structureKind == StructureKind.GUARD_POST
			|| structureKind == StructureKind.SCRIBE_OFFICE
			|| structureKind == StructureKind.SHEPHERD_HUT
			|| structureKind == StructureKind.SMITHY)
			&& (right == -1 || right == 1)
			&& (forward == -3 || forward == -2)) {
			return Blocks.WHITE_BED.defaultBlockState()
				.setValue(BedBlock.PART, forward == -3 ? BedPart.HEAD : BedPart.FOOT)
				.setValue(BedBlock.FACING, facing.getOpposite());
		}

		if ((structureKind == StructureKind.BAKERY || structureKind == StructureKind.TRADING_POST)
			&& right == -1
			&& (forward == -3 || forward == -2)) {
			return Blocks.WHITE_BED.defaultBlockState()
				.setValue(BedBlock.PART, forward == -3 ? BedPart.HEAD : BedPart.FOOT)
				.setValue(BedBlock.FACING, facing.getOpposite());
		}

		if (structureKind == StructureKind.HOUSING_SHELTER
			&& (right == -1 || right == 1)
			&& (forward == -1 || forward == 0)) {
			return Blocks.WHITE_BED.defaultBlockState()
				.setValue(BedBlock.PART, forward == -1 ? BedPart.HEAD : BedPart.FOOT)
				.setValue(BedBlock.FACING, facing.getOpposite());
		}

		if (structureKind == StructureKind.SIMPLE_HOUSING_SHELTER
			&& right == -1
			&& (forward == 0 || forward == 1)) {
			return Blocks.WHITE_BED.defaultBlockState()
				.setValue(BedBlock.PART, forward == 0 ? BedPart.HEAD : BedPart.FOOT)
				.setValue(BedBlock.FACING, facing.getOpposite());
		}

		return null;
	}

	private static boolean isBedFootBlock(StructureKind structureKind, int right, int forward, int up) {
		return ((structureKind == StructureKind.CARPENTER_WORKSHOP
			|| structureKind == StructureKind.ROADWRIGHT_WORKSHOP
			|| structureKind == StructureKind.MASON_WORKSHOP
			|| structureKind == StructureKind.FORESTER_WORKSHOP) && right == -1 && forward == -2 && up == 1)
			|| ((structureKind == StructureKind.FLETCHER_HUT
				|| structureKind == StructureKind.BUTCHER_SHOP
				|| structureKind == StructureKind.BEEKEEPER_APIARY
				|| structureKind == StructureKind.CLERIC_SHRINE
				|| structureKind == StructureKind.LEATHERWORKER_WORKSHOP
				|| structureKind == StructureKind.LIBRARY
				|| structureKind == StructureKind.GARDENER_SHED
			|| structureKind == StructureKind.GUARD_POST
			|| structureKind == StructureKind.SCRIBE_OFFICE
			|| structureKind == StructureKind.SHEPHERD_HUT
			|| structureKind == StructureKind.SMITHY) && (right == -1 || right == 1) && forward == -2 && up == 1)
			|| (structureKind == StructureKind.HOUSING_SHELTER && (right == -1 || right == 1) && forward == 0 && up == 1)
			|| (structureKind == StructureKind.SIMPLE_HOUSING_SHELTER && right == -1 && forward == 1 && up == 1)
			|| ((structureKind == StructureKind.BAKERY || structureKind == StructureKind.TRADING_POST) && right == -1 && forward == -2 && up == 1);
	}

	private static void placeBlueprintBeds(ServerLevel level, BlockPos origin, Direction facing, StructureKind structureKind) {
		if (structureKind == StructureKind.CARPENTER_WORKSHOP
			|| structureKind == StructureKind.FORESTER_WORKSHOP
			|| structureKind == StructureKind.MASON_WORKSHOP) {
			placeBed(level, offset(origin, facing, -1, -3, 1), offset(origin, facing, -1, -2, 1), facing.getOpposite());
		} else if (structureKind == StructureKind.FLETCHER_HUT
			|| structureKind == StructureKind.BUTCHER_SHOP
			|| structureKind == StructureKind.BEEKEEPER_APIARY
			|| structureKind == StructureKind.CLERIC_SHRINE
			|| structureKind == StructureKind.LEATHERWORKER_WORKSHOP
			|| structureKind == StructureKind.LIBRARY
			|| structureKind == StructureKind.GARDENER_SHED
			|| structureKind == StructureKind.GUARD_POST
			|| structureKind == StructureKind.SCRIBE_OFFICE
			|| structureKind == StructureKind.SHEPHERD_HUT
			|| structureKind == StructureKind.SMITHY) {
			placeBed(level, offset(origin, facing, -1, -3, 1), offset(origin, facing, -1, -2, 1), facing.getOpposite());
			placeBed(level, offset(origin, facing, 1, -3, 1), offset(origin, facing, 1, -2, 1), facing.getOpposite());
		} else if (structureKind == StructureKind.HOUSING_SHELTER) {
			placeBed(level, offset(origin, facing, -1, -1, 1), offset(origin, facing, -1, 0, 1), facing.getOpposite());
			placeBed(level, offset(origin, facing, 1, -1, 1), offset(origin, facing, 1, 0, 1), facing.getOpposite());
		} else if (structureKind == StructureKind.SIMPLE_HOUSING_SHELTER) {
			placeBed(level, offset(origin, facing, -1, 0, 1), offset(origin, facing, -1, 1, 1), facing.getOpposite());
		} else if (structureKind == StructureKind.BAKERY || structureKind == StructureKind.TRADING_POST) {
			placeBed(level, offset(origin, facing, -1, -3, 1), offset(origin, facing, -1, -2, 1), facing.getOpposite());
		}
	}

	private static void placeBed(ServerLevel level, BlockPos headPos, BlockPos footPos, Direction bedFacing) {
		level.setBlock(
			headPos,
			Blocks.WHITE_BED.defaultBlockState()
				.setValue(BedBlock.PART, BedPart.HEAD)
				.setValue(BedBlock.FACING, bedFacing),
			BLOCK_UPDATE_FLAGS
		);
		level.setBlock(
			footPos,
			Blocks.WHITE_BED.defaultBlockState()
				.setValue(BedBlock.PART, BedPart.FOOT)
				.setValue(BedBlock.FACING, bedFacing),
			BLOCK_UPDATE_FLAGS
		);
	}

	private static void setBlueprintBlock(ServerLevel level, BlockPos pos, BlockState state, Map<String, Integer> stock) {
		BlockState currentState = level.getBlockState(pos);

		if (!currentState.isAir() && !currentState.is(state.getBlock())) {
			clearBlockToWarehouse(level, pos, stock);
		}

		level.setBlock(pos, state, BLOCK_UPDATE_FLAGS);
		updateChestStateAfterPlacement(level, pos);
	}

	public static void updateChestStateAfterPlacement(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);

		if (!(state.getBlock() instanceof ChestBlock) || !state.hasProperty(ChestBlock.FACING)) {
			return;
		}

		Direction facing = state.getValue(ChestBlock.FACING);
		Direction adjacentDirection = null;

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockState adjacentState = level.getBlockState(pos.relative(direction));

			if (adjacentState.getBlock() instanceof ChestBlock
				&& adjacentState.hasProperty(ChestBlock.FACING)
				&& adjacentState.getValue(ChestBlock.FACING) == facing) {
				adjacentDirection = direction;
				break;
			}
		}

		if (adjacentDirection == null) {
			if (state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
				level.setBlock(pos, state.setValue(ChestBlock.TYPE, ChestType.SINGLE), BLOCK_UPDATE_FLAGS);
			}

			return;
		}

		BlockPos adjacentPos = pos.relative(adjacentDirection);
		BlockState adjacentState = level.getBlockState(adjacentPos);
		ChestType chestType = chestTypeForFacingAndAdjacentDirection(facing, adjacentDirection);
		ChestType adjacentType = chestType == ChestType.LEFT ? ChestType.RIGHT : ChestType.LEFT;

		level.setBlock(pos, state.setValue(ChestBlock.TYPE, chestType), BLOCK_UPDATE_FLAGS);
		level.setBlock(adjacentPos, adjacentState.setValue(ChestBlock.TYPE, adjacentType), BLOCK_UPDATE_FLAGS);
	}

	private static ChestType chestTypeForFacingAndAdjacentDirection(Direction chestFacing, Direction adjacentDirection) {
		if (adjacentDirection == chestFacing.getClockWise()) {
			return ChestType.LEFT;
		}

		if (adjacentDirection == chestFacing.getCounterClockWise()) {
			return ChestType.RIGHT;
		}

		return ChestType.SINGLE;
	}

	private static SettlementBuildSite createPendingBuildSite(
		ServerLevel level,
		StructureKind structureKind,
		StructureBlueprint blueprint,
		String settlementId,
		BlockPos origin,
		BlockPos anchorPos,
		BlockPos workstationPos,
		Direction facing,
		long tick
	) {
		StructureMaterialPalette palette = materialPaletteFor(level, anchorPos);
		List<SettlementBuildBlockState> blocks = new ArrayList<>();
		Set<String> plannedPositions = new HashSet<>();
		String anchorPosition = relativeBlueprintPositionFromWorld(origin, facing, anchorPos);
		boolean anchorMappedInBlueprint = false;

		for (int layerIndex = 0; layerIndex < blueprint.layers().length; layerIndex++) {
			int up = blueprint.minUp() + layerIndex;
			String[] rows = blueprint.layers()[layerIndex];

			for (int row = 0; row < rows.length; row++) {
				String rowPattern = rows[row];
				int forward = blueprint.minForward() + row;

				for (int column = 0; column < rowPattern.length(); column++) {
					char symbol = rowPattern.charAt(column);

					if (symbol == 'A') {
						continue;
					}

					int right = blueprint.minRight() + column;
					String materialKey = blueprintMaterialKey(structureKind, symbol, right, forward, up);
					String position = relativeBlueprintPosition(right, forward, up);
					BlockPos worldPos = offset(origin, facing, right, forward, up);

					if (worldPos.equals(anchorPos)
						&& anchorPos.equals(workstationPos)
						&& !isAnchoredWorkstationSymbol(structureKind, symbol, up)
						&& structureKind != StructureKind.LIGHTHOUSE
						&& !isPalisadeGatehouseKind(structureKind)
						&& structureKind != StructureKind.HOUSING_SHELTER
						&& structureKind != StructureKind.SIMPLE_HOUSING_SHELTER) {
						continue;
					}

					if (worldPos.equals(anchorPos)) {
						anchorMappedInBlueprint = true;
					}

					if (isAnchoredWorkstationSymbol(structureKind, symbol, up)
						&& worldPos.equals(anchorPos)
						&& anchorPos.equals(workstationPos)) {
						blocks.add(SettlementBuildBlockState.playerPlaced(position, symbol, materialKey));
					} else {
						blocks.add(SettlementBuildBlockState.pending(position, symbol, materialKey));
					}

					plannedPositions.add(position);
				}
			}
		}

		if (!anchorPos.equals(workstationPos) && !anchorMappedInBlueprint) {
			blocks.add(SettlementBuildBlockState.pending(anchorPosition, 'A', ""));
			plannedPositions.add(anchorPosition);
		}

		addPlacementClearanceBlocks(level, structureKind, blueprint, origin, facing, blocks, plannedPositions);

		return new SettlementBuildSite(
			buildSiteId(settlementId, structureKind, anchorPos),
			settlementId,
			blueprintIdFor(structureKind),
			origin.immutable(),
			workstationPos.immutable(),
			anchorPos.immutable(),
			facing,
			palette.woodFamily(),
			palette.stoneMaterial(),
			Map.of(),
			blocks,
			false,
			tick,
			tick
		);
	}

	private static void addPlacementClearanceBlocks(
		ServerLevel level,
		StructureKind structureKind,
		StructureBlueprint blueprint,
		BlockPos origin,
		Direction facing,
		List<SettlementBuildBlockState> blocks,
		Set<String> plannedPositions
	) {
		if (structureKind == StructureKind.MINE_ENTRANCE) {
			addMineEntranceFrontAccessBlocks(level, origin, facing, blocks, plannedPositions);
			return;
		}

		for (int right = blueprint.minRight() - MIN_STRUCTURE_SPACING_BLOCKS; right <= blueprint.maxRight() + MIN_STRUCTURE_SPACING_BLOCKS; right++) {
			for (int forward = blueprint.minForward() - MIN_STRUCTURE_SPACING_BLOCKS; forward <= blueprint.maxForward() + MIN_STRUCTURE_SPACING_BLOCKS; forward++) {
				if (right >= blueprint.minRight() && right <= blueprint.maxRight() && forward >= blueprint.minForward() && forward <= blueprint.maxForward()) {
					continue;
				}

				BlockPos columnPos = offset(origin, facing, right, forward, 0);

				if (!level.hasChunkAt(columnPos)) {
					continue;
				}

				BlockPos groundPos = resolveStructureGroundPos(level, columnPos);

				if (groundPos == null) {
					continue;
				}

				BlockState groundState = level.getBlockState(groundPos);

				if (shouldHarvestStructureMarginGroundResource(groundState)) {
					addPendingMarginGroundBackfillBlock(blocks, plannedPositions, relativeBlueprintPositionFromWorld(origin, facing, groundPos));
				}

				int minClearY = groundPos.getY() + 1;
				int maxClearY = Math.max(origin.getY() + blueprint.clearHeight(), groundPos.getY() + blueprint.clearHeight());

				for (int y = minClearY; y <= maxClearY; y++) {
					BlockPos worldPos = new BlockPos(columnPos.getX(), y, columnPos.getZ());

					if (!level.hasChunkAt(worldPos)) {
						continue;
					}

					BlockState state = level.getBlockState(worldPos);

					if (!shouldClearStructureMarginBlock(level, worldPos, state)) {
						continue;
					}

					addPendingAirBlock(blocks, plannedPositions, right, forward, y - origin.getY());
				}
			}
		}
	}

	private static void addMineEntranceFrontAccessBlocks(
		ServerLevel level,
		BlockPos origin,
		Direction facing,
		List<SettlementBuildBlockState> blocks,
		Set<String> plannedPositions
	) {
		for (int forward = MINE_ENTRANCE_BLUEPRINT.maxForward() + 1; forward <= MINE_ENTRANCE_BLUEPRINT.maxForward() + MINE_ENTRANCE_FRONT_ACCESS_DEPTH_BLOCKS; forward++) {
			for (int right = 0; right <= 1; right++) {
				for (int up = 1; up <= MINE_ENTRANCE_FRONT_ACCESS_HEIGHT_BLOCKS; up++) {
					BlockPos worldPos = offset(origin, facing, right, forward, up);

					if (!level.hasChunkAt(worldPos)) {
						continue;
					}

					BlockState state = level.getBlockState(worldPos);

					if (!shouldClearMineEntranceAccessBlock(level, worldPos, state)) {
						continue;
					}

					addPendingAirBlock(blocks, plannedPositions, right, forward, up);
				}
			}
		}
	}

	private static void addPendingAirBlock(
		List<SettlementBuildBlockState> blocks,
		Set<String> plannedPositions,
		int right,
		int forward,
		int up
	) {
		String position = relativeBlueprintPosition(right, forward, up);

		if (!plannedPositions.add(position)) {
			return;
		}

		blocks.add(SettlementBuildBlockState.pending(position, 'A', ""));
	}

	private static void addPendingMarginGroundBackfillBlock(
		List<SettlementBuildBlockState> blocks,
		Set<String> plannedPositions,
		String position
	) {
		if (!plannedPositions.add(position)) {
			return;
		}

		blocks.add(SettlementBuildBlockState.pending(position, 'A', STRUCTURE_MARGIN_GROUND_BACKFILL_KEY));
	}

	private static boolean shouldClearStructureMarginBlock(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.isAir() || isShovelPathSurface(state) || isStructuredGardenBlock(level, pos, state)) {
			return false;
		}

		return isReplaceable(state) || canLandscapeRemove(level, pos, state);
	}

	private static boolean shouldHarvestStructureMarginGroundResource(BlockState state) {
		return isNaturalOreBlock(state);
	}

	private static boolean shouldClearMineEntranceAccessBlock(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.isAir()) {
			return false;
		}

		return canClearForConstruction(level, pos, state);
	}

	private static boolean anchoredWorkstationStillMapsToBlueprint(SettlementBuildSite buildSite, StructureKind structureKind, BlockPos workstationPos) {
		if (!buildSite.anchorPos().equals(workstationPos) || !buildSite.workstationPos().equals(workstationPos)) {
			return false;
		}

		String position = relativeBlueprintPositionFromWorld(buildSite.origin(), buildSite.facing(), workstationPos);
		BlueprintRelativePos relativePos = parseRelativeBlueprintPosition(position);

		if (relativePos == null) {
			return false;
		}

		StructureBlueprint blueprint = blueprintFor(structureKind);
		return isAnchoredWorkstationSymbol(structureKind, blueprintSymbolAt(blueprint, relativePos.right(), relativePos.forward(), relativePos.up()), relativePos.up());
	}

	public static SettlementBuildSite updateBuildSiteMaterialStatus(SettlementBuildSite buildSite, Map<String, Integer> stock, long tick) {
		buildSite = normalizeBuildSiteDefinition(buildSite, tick);
		Map<String, Integer> reservedStock = new java.util.LinkedHashMap<>(stock);
		Map<String, Integer> reservedSiteMaterials = new java.util.LinkedHashMap<>(buildSite.siteMaterials());
		List<SettlementBuildBlockState> updatedBlocks = new ArrayList<>();
		boolean changed = false;

		for (SettlementBuildBlockState block : buildSite.blocks()) {
			SettlementBuildBlockState normalizedBlock = normalizeBuildBlockMaterial(buildSite, block);

			if (!normalizedBlock.equals(block)) {
				changed = true;
			}

			if (normalizedBlock.status() == SettlementBuildBlockStatus.PLACED
				|| normalizedBlock.status() == SettlementBuildBlockStatus.PLAYER_PLACED
				|| normalizedBlock.status() == SettlementBuildBlockStatus.BLOCKED) {
				updatedBlocks.add(normalizedBlock);
				continue;
			}

			SettlementConstructionMaterials.ConstructionMaterialResult materialResult =
				SettlementConstructionMaterials.consumeForBlock(reservedStock, reservedSiteMaterials, normalizedBlock);
			SettlementBuildBlockState updatedBlock = materialResult.supplied()
				? normalizedBlock.withStatus(SettlementBuildBlockStatus.PENDING, "")
				: normalizedBlock.withStatus(SettlementBuildBlockStatus.MISSING_MATERIAL, materialResult.missingMaterialKey());

			if (!updatedBlock.equals(normalizedBlock)) {
				changed = true;
			}

			updatedBlocks.add(updatedBlock);
		}

		boolean complete = buildSiteComplete(updatedBlocks);

		if (!changed && complete == buildSite.complete()) {
			return buildSite;
		}

		return buildSite.withBlocks(updatedBlocks, complete, tick);
	}

	public static SettlementBuildSite applyBiomeMaterialPalette(ServerLevel level, SettlementState settlement, SettlementBuildSite buildSite, long tick) {
		SettlementBuildSite normalizedBuildSite = normalizeBuildSiteForLoadedWorld(level, buildSite, tick);
		StructureMaterialPalette palette = materialPaletteFor(level, normalizedBuildSite.anchorPos());
		String tierLimitedStoneMaterial = SettlementTiers.clampStoneMaterialForTier(SettlementTiers.unlockedTier(settlement), palette.stoneMaterial());
		if (palette.woodFamily().equals(normalizedBuildSite.woodFamily()) && tierLimitedStoneMaterial.equals(normalizedBuildSite.stoneMaterial())) {
			return normalizedBuildSite;
		}

		return normalizedBuildSite.withMaterials(palette.woodFamily(), tierLimitedStoneMaterial, tick);
	}

	private static SettlementBuildSite normalizeBuildSiteForLoadedWorld(ServerLevel level, SettlementBuildSite buildSite, long tick) {
		SettlementBuildSite normalizedBuildSite = normalizeBuildSiteDefinition(buildSite, tick);

		if (normalizedBuildSite.blueprintId() == SettlementBuildSiteType.PALISADE_WALL) {
			normalizedBuildSite = withPalisadePointEndpointColumns(level, normalizedBuildSite, tick);
		}

		return normalizedBuildSite;
	}

	private static SettlementBuildSite normalizeBuildSiteDefinition(SettlementBuildSite buildSite, long tick) {
		SettlementBuildSite normalizedBuildSite = buildSite;

		if (isPalisadeGatehouseBuildSite(normalizedBuildSite)) {
			normalizedBuildSite = withPalisadeGatehouseDoorBlocks(normalizedBuildSite, tick);
		}

		if (normalizedBuildSite.blueprintId() == SettlementBuildSiteType.PALISADE_WALL) {
			normalizedBuildSite = withNormalizedPalisadeWallPositions(normalizedBuildSite, tick);
		}

		return normalizedBuildSite;
	}

	private static boolean isPalisadeGatehouseBuildSite(SettlementBuildSite buildSite) {
		return buildSite.blueprintId() == SettlementBuildSiteType.PALISADE_GATEHOUSE
			|| buildSite.blueprintId() == SettlementBuildSiteType.COPPER_PALISADE_GATEHOUSE;
	}

	private static SettlementBuildSite withPalisadeGatehouseDoorBlocks(SettlementBuildSite buildSite, long tick) {
		List<SettlementBuildBlockState> blocks = new ArrayList<>(buildSite.blocks());
		boolean changed = ensurePalisadeGatehouseDoorBlock(blocks, relativeBlueprintPosition(0, 0, 0), "door");
		changed |= ensurePalisadeGatehouseDoorBlock(blocks, relativeBlueprintPosition(0, 0, 1), "");
		return changed ? buildSite.withBlocks(blocks, false, tick) : buildSite;
	}

	private static boolean ensurePalisadeGatehouseDoorBlock(List<SettlementBuildBlockState> blocks, String position, String materialKey) {
		for (int index = 0; index < blocks.size(); index++) {
			SettlementBuildBlockState block = blocks.get(index);

			if (!position.equals(block.position())) {
				continue;
			}

			if ("D".equals(block.blueprintSymbol()) && materialKey.equals(block.expectedMaterialKey())) {
				return false;
			}

			blocks.set(index, SettlementBuildBlockState.pending(position, 'D', materialKey));
			return true;
		}

		blocks.add(SettlementBuildBlockState.pending(position, 'D', materialKey));
		return true;
	}

	private static SettlementBuildSite withPalisadePointEndpointColumns(ServerLevel level, SettlementBuildSite buildSite, long tick) {
		Map<String, SettlementBuildBlockState> blocks = new LinkedHashMap<>();
		for (SettlementBuildBlockState block : buildSite.blocks()) {
			blocks.put(block.position(), block);
		}

		boolean changed = addPalisadePointEndpointColumn(level, buildSite, blocks, buildSite.anchorPos());
		changed |= addPalisadePointEndpointColumn(level, buildSite, blocks, buildSite.workstationPos());

		return changed ? buildSite.withBlocks(List.copyOf(blocks.values()), false, tick) : buildSite;
	}

	private static boolean addPalisadePointEndpointColumn(
		ServerLevel level,
		SettlementBuildSite buildSite,
		Map<String, SettlementBuildBlockState> blocks,
		BlockPos endpointPos
	) {
		if (!level.hasChunkAt(endpointPos) || !level.getBlockState(endpointPos).is(LiveVillagesBlocks.PALISADE_POINT)) {
			return false;
		}

		boolean changed = false;
		for (int up = 0; up < PALISADE_WALL_HEIGHT_BLOCKS; up++) {
			String position = relativeBlueprintPositionFromWorld(buildSite.origin(), buildSite.facing(), endpointPos.above(up));
			if (blocks.containsKey(position)) {
				continue;
			}

			blocks.put(position, SettlementBuildBlockState.pending(position, 'L', "logs"));
			changed = true;
		}

		return changed;
	}

	private static SettlementBuildSite withNormalizedPalisadeWallPositions(SettlementBuildSite buildSite, long tick) {
		Map<String, SettlementBuildBlockState> blocks = new LinkedHashMap<>();
		boolean changed = false;

		for (SettlementBuildBlockState block : buildSite.blocks()) {
			SettlementBuildBlockState normalizedBlock = withNormalizedPalisadeWallPosition(buildSite, block);
			changed |= !normalizedBlock.equals(block);
			blocks.putIfAbsent(normalizedBlock.position(), normalizedBlock);
		}

		return changed ? buildSite.withBlocks(List.copyOf(blocks.values()), buildSite.complete(), tick) : buildSite;
	}

	private static SettlementBuildBlockState withNormalizedPalisadeWallPosition(SettlementBuildSite buildSite, SettlementBuildBlockState block) {
		BlueprintRelativePos relativePos = parseRelativeBlueprintPosition(block.position());

		if (relativePos == null) {
			return block;
		}

		BlueprintRelativePos normalizedRelativePos = normalizedPalisadeWallRelativePos(buildSite, block, relativePos);
		if (normalizedRelativePos.equals(relativePos)) {
			return block;
		}

		return new SettlementBuildBlockState(
			relativeBlueprintPosition(normalizedRelativePos.right(), normalizedRelativePos.forward(), normalizedRelativePos.up()),
			block.blueprintSymbol(),
			block.expectedMaterialKey(),
			block.status(),
			block.blocker()
		);
	}

	private static boolean buildSiteComplete(List<SettlementBuildBlockState> blocks) {
		for (SettlementBuildBlockState block : blocks) {
			if (block.status() != SettlementBuildBlockStatus.PLACED && block.status() != SettlementBuildBlockStatus.PLAYER_PLACED) {
				return false;
			}
		}

		return true;
	}

	private static SettlementBuildBlockState normalizeBuildBlockMaterial(SettlementBuildSite buildSite, SettlementBuildBlockState block) {
		BlueprintRelativePos relativePos = parseRelativeBlueprintPosition(block.position());

		if (relativePos == null || block.blueprintSymbol().isBlank()) {
			return block;
		}

		if (buildSite.blueprintId() == SettlementBuildSiteType.DOCK) {
			String materialKey = dockMaterialKey(block.blueprintSymbol().charAt(0));
			return materialKey.equals(block.expectedMaterialKey()) ? block : block.withExpectedMaterialKey(materialKey);
		}

		if (buildSite.blueprintId() == SettlementBuildSiteType.PALISADE_WALL) {
			String materialKey = palisadeWallMaterialKey(block.blueprintSymbol().charAt(0));
			return materialKey.equals(block.expectedMaterialKey()) ? block : block.withExpectedMaterialKey(materialKey);
		}

		String materialKey = blueprintMaterialKey(
			structureKindFor(buildSite.blueprintId()),
			block.blueprintSymbol().charAt(0),
			relativePos.right(),
			relativePos.forward(),
			relativePos.up()
		);
		return materialKey.equals(block.expectedMaterialKey()) ? block : block.withExpectedMaterialKey(materialKey);
	}

	private static boolean isAnchoredWorkstationSymbol(StructureKind structureKind, char symbol, int up) {
		return symbol == 'W' && (structureKind == StructureKind.BAKERY
			|| structureKind == StructureKind.CARPENTER_WORKSHOP
			|| structureKind == StructureKind.BUTCHER_SHOP
			|| structureKind == StructureKind.CARTOGRAPHER_HOUSE
			|| structureKind == StructureKind.BEEKEEPER_APIARY
			|| structureKind == StructureKind.CLERIC_SHRINE
			|| structureKind == StructureKind.FLETCHER_HUT
			|| structureKind == StructureKind.LEATHERWORKER_WORKSHOP
			|| structureKind == StructureKind.LIBRARY
			|| structureKind == StructureKind.GARDENER_SHED
			|| structureKind == StructureKind.GUARD_POST
			|| structureKind == StructureKind.FORESTER_WORKSHOP
			|| structureKind == StructureKind.LIGHTHOUSE
			|| structureKind == StructureKind.MASON_WORKSHOP
			|| structureKind == StructureKind.MINE_ENTRANCE
			|| structureKind == StructureKind.ROADWRIGHT_WORKSHOP
			|| structureKind == StructureKind.SCRIBE_OFFICE
			|| structureKind == StructureKind.SHEPHERD_HUT
			|| structureKind == StructureKind.SMITHY
			|| structureKind == StructureKind.TRADING_POST);
	}

	public static boolean isAnchoredWorkstationBlock(SettlementBuildSite buildSite, SettlementBuildBlockState block) {
		if (block.blueprintSymbol().isBlank()) {
			return false;
		}

		if (buildSite.blueprintId() == SettlementBuildSiteType.PALISADE_WALL) {
			return false;
		}

		BlueprintRelativePos relativePos = parseRelativeBlueprintPosition(block.position());
		return relativePos != null
			&& isAnchoredWorkstationSymbol(structureKindFor(buildSite.blueprintId()), block.blueprintSymbol().charAt(0), relativePos.up());
	}

	private static String blueprintMaterialKey(StructureKind structureKind, char symbol, int right, int forward, int up) {
		return switch (symbol) {
			case 'B' -> isBedSymbol(structureKind, symbol, up) ? (isBedFootBlock(structureKind, right, forward, up) ? "bed" : "") : "slab";
			case 'C' -> structureKind == StructureKind.FORESTER_WORKSHOP ? "logs" : (structureKind == StructureKind.CARPENTER_WORKSHOP || structureKind == StructureKind.ROADWRIGHT_WORKSHOP) ? "planks" : "cobblestone";
			case 'D' -> isPalisadeGatehouseKind(structureKind) ? (up == 0 ? "door" : "") : (up == 1 ? "door" : "");
			case 'E' -> "";
			case 'F' -> "fence";
			case 'G' -> "fence_gate";
			case 'H' -> "chest";
			case 'I' -> structureKind == StructureKind.COPPER_PALISADE_GATEHOUSE ? "copper_bars" : "iron_bars";
			case 'K' -> "campfire";
			case 'L' -> "logs";
			case 'M' -> "cobblestone";
			case 'N' -> "lantern";
			case 'P' -> "planks";
			case 'Q' -> "glass_display_case";
			case 'R' -> "ladder";
			case 'S' -> "stairs";
			case 'T' -> "torch";
			case 'V' -> "glass";
			case 'W' -> switch (structureKind) {
				case BAKERY -> "bakers_counter";
				case BEEKEEPER_APIARY -> "honey_separator";
				case TRADING_POST -> "trade_board";
				case FORESTER_WORKSHOP -> "forester_table";
				case MASON_WORKSHOP -> "stonecutter";
				case MINE_ENTRANCE -> "miner_workstation";
				case FLETCHER_HUT -> "fletching_table";
				case BUTCHER_SHOP -> "smoker";
				case CLERIC_SHRINE -> "brewing_stand";
				case LEATHERWORKER_WORKSHOP -> "cauldron";
				case LIBRARY -> "lectern";
				case GARDENER_SHED -> "gardener_workstation";
				case GUARD_POST -> "guard_post";
				case SHEPHERD_HUT -> "loom";
				case SMITHY -> "smithing_station";
				case ROADWRIGHT_WORKSHOP -> "surveyor_table";
				case SCRIBE_OFFICE -> "scribe_desk";
				case CARTOGRAPHER_HOUSE -> "cartography_table";
				case CARPENTER_WORKSHOP -> "carpenter_bench";
				case LIGHTHOUSE -> "lighthouse_marker";
				case HOUSING_SHELTER, PALISADE_GATEHOUSE, COPPER_PALISADE_GATEHOUSE, SIMPLE_HOUSING_SHELTER -> "";
				case DOCK -> "";
			};
			default -> "";
		};
	}

	private static boolean lighthouseMarkerRecipeCreditMissing(SettlementBuildSite buildSite) {
		if (buildSite.blueprintId() != SettlementBuildSiteType.LIGHTHOUSE) {
			return false;
		}

		return buildSite.blocks().stream()
			.anyMatch(block -> "lighthouse_marker".equals(block.expectedMaterialKey()));
	}

	private static String relativeBlueprintPosition(int right, int forward, int up) {
		return right + "," + forward + "," + up;
	}

	private static String relativeBlueprintPositionFromWorld(BlockPos origin, Direction facing, BlockPos worldPos) {
		int dx = worldPos.getX() - origin.getX();
		int dy = worldPos.getY() - origin.getY();
		int dz = worldPos.getZ() - origin.getZ();
		int right;
		int forward;

		if (facing == Direction.NORTH) {
			right = dx;
			forward = -dz;
		} else if (facing == Direction.SOUTH) {
			right = -dx;
			forward = dz;
		} else if (facing == Direction.EAST) {
			right = dz;
			forward = dx;
		} else {
			right = -dz;
			forward = -dx;
		}

		return relativeBlueprintPosition(right, forward, dy);
	}

	private static BlueprintRelativePos parseRelativeBlueprintPosition(String position) {
		String[] parts = position.split(",");

		if (parts.length != 3) {
			return null;
		}

		try {
			return new BlueprintRelativePos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	private static BlockState plannedBlueprintBlockState(
		StructureKind structureKind,
		StructureBlueprint blueprint,
		Direction facing,
		String woodFamily,
		String stoneMaterial,
		char symbol,
		int right,
		int forward,
		int up
	) {
		if (symbol == 'A') {
			return Blocks.AIR.defaultBlockState();
		}

		if (symbol == 'D') {
			boolean palisadeGatehouse = structureKind == StructureKind.PALISADE_GATEHOUSE
				|| structureKind == StructureKind.COPPER_PALISADE_GATEHOUSE;
			int lowerUp = palisadeGatehouse ? 0 : 1;
			int upperUp = palisadeGatehouse ? 1 : 2;

			if (up != lowerUp && up != upperUp) {
				return null;
			}

			return woodDoorBlock(woodFamily).defaultBlockState()
				.setValue(DoorBlock.FACING, blueprintDoorFacing(structureKind, facing))
				.setValue(DoorBlock.HALF, up == lowerUp ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
		}

		if (isBedSymbol(structureKind, symbol, up)) {
			return bedStateFor(structureKind, facing, right, forward, up);
		}

		return blueprintStateFor(facing, blueprint, structureKind, woodFamily, stoneMaterial, symbol, right, forward, up);
	}

	private static StructureMaterialPalette materialPaletteFor(ServerLevel level, BlockPos pos) {
		return StructurePaletteTable.paletteFor(level.getBiome(pos));
	}

	private static Block woodLogBlock(String woodFamily) {
		return switch (woodFamily) {
			case "spruce" -> Blocks.SPRUCE_LOG;
			case "birch" -> Blocks.BIRCH_LOG;
			case "jungle" -> Blocks.JUNGLE_LOG;
			case "acacia" -> Blocks.ACACIA_LOG;
			case "cherry" -> Blocks.CHERRY_LOG;
			case "dark_oak" -> Blocks.DARK_OAK_LOG;
			case "pale_oak" -> Blocks.PALE_OAK_LOG;
			case "mangrove" -> Blocks.MANGROVE_LOG;
			default -> Blocks.OAK_LOG;
		};
	}

	private static Block woodPlankBlock(String woodFamily) {
		return switch (woodFamily) {
			case "spruce" -> Blocks.SPRUCE_PLANKS;
			case "birch" -> Blocks.BIRCH_PLANKS;
			case "jungle" -> Blocks.JUNGLE_PLANKS;
			case "acacia" -> Blocks.ACACIA_PLANKS;
			case "cherry" -> Blocks.CHERRY_PLANKS;
			case "dark_oak" -> Blocks.DARK_OAK_PLANKS;
			case "pale_oak" -> Blocks.PALE_OAK_PLANKS;
			case "mangrove" -> Blocks.MANGROVE_PLANKS;
			default -> Blocks.OAK_PLANKS;
		};
	}

	private static Block woodStairBlock(String woodFamily) {
		return switch (woodFamily) {
			case "spruce" -> Blocks.SPRUCE_STAIRS;
			case "birch" -> Blocks.BIRCH_STAIRS;
			case "jungle" -> Blocks.JUNGLE_STAIRS;
			case "acacia" -> Blocks.ACACIA_STAIRS;
			case "cherry" -> Blocks.CHERRY_STAIRS;
			case "dark_oak" -> Blocks.DARK_OAK_STAIRS;
			case "pale_oak" -> Blocks.PALE_OAK_STAIRS;
			case "mangrove" -> Blocks.MANGROVE_STAIRS;
			default -> Blocks.OAK_STAIRS;
		};
	}

	private static Block woodSlabBlock(String woodFamily) {
		return switch (woodFamily) {
			case "spruce" -> Blocks.SPRUCE_SLAB;
			case "birch" -> Blocks.BIRCH_SLAB;
			case "jungle" -> Blocks.JUNGLE_SLAB;
			case "acacia" -> Blocks.ACACIA_SLAB;
			case "cherry" -> Blocks.CHERRY_SLAB;
			case "dark_oak" -> Blocks.DARK_OAK_SLAB;
			case "pale_oak" -> Blocks.PALE_OAK_SLAB;
			case "mangrove" -> Blocks.MANGROVE_SLAB;
			default -> Blocks.OAK_SLAB;
		};
	}

	private static Block woodDoorBlock(String woodFamily) {
		return switch (woodFamily) {
			case "spruce" -> Blocks.SPRUCE_DOOR;
			case "birch" -> Blocks.BIRCH_DOOR;
			case "jungle" -> Blocks.JUNGLE_DOOR;
			case "acacia" -> Blocks.ACACIA_DOOR;
			case "cherry" -> Blocks.CHERRY_DOOR;
			case "dark_oak" -> Blocks.DARK_OAK_DOOR;
			case "pale_oak" -> Blocks.PALE_OAK_DOOR;
			case "mangrove" -> Blocks.MANGROVE_DOOR;
			default -> Blocks.OAK_DOOR;
		};
	}

	private static Block woodFenceBlock(String woodFamily) {
		return switch (woodFamily) {
			case "spruce" -> Blocks.SPRUCE_FENCE;
			case "birch" -> Blocks.BIRCH_FENCE;
			case "jungle" -> Blocks.JUNGLE_FENCE;
			case "acacia" -> Blocks.ACACIA_FENCE;
			case "cherry" -> Blocks.CHERRY_FENCE;
			case "dark_oak" -> Blocks.DARK_OAK_FENCE;
			case "pale_oak" -> Blocks.PALE_OAK_FENCE;
			case "mangrove" -> Blocks.MANGROVE_FENCE;
			default -> Blocks.OAK_FENCE;
		};
	}

	private static Block woodFenceGateBlock(String woodFamily) {
		return switch (woodFamily) {
			case "spruce" -> Blocks.SPRUCE_FENCE_GATE;
			case "birch" -> Blocks.BIRCH_FENCE_GATE;
			case "jungle" -> Blocks.JUNGLE_FENCE_GATE;
			case "acacia" -> Blocks.ACACIA_FENCE_GATE;
			case "cherry" -> Blocks.CHERRY_FENCE_GATE;
			case "dark_oak" -> Blocks.DARK_OAK_FENCE_GATE;
			case "pale_oak" -> Blocks.PALE_OAK_FENCE_GATE;
			case "mangrove" -> Blocks.MANGROVE_FENCE_GATE;
			default -> Blocks.OAK_FENCE_GATE;
		};
	}

	private static Block stoneBlock(String stoneMaterial) {
		return switch (stoneMaterial) {
			case "sandstone" -> Blocks.SANDSTONE;
			case "red_sandstone" -> Blocks.RED_SANDSTONE;
			case "smooth_stone" -> Blocks.SMOOTH_STONE;
			case "stone_bricks" -> Blocks.STONE_BRICKS;
			default -> Blocks.COBBLESTONE;
		};
	}

	private static StructureBlueprint blueprintFor(StructureKind structureKind) {
		return switch (structureKind) {
			case BAKERY -> BAKERY_BLUEPRINT;
			case BEEKEEPER_APIARY -> BEEKEEPER_APIARY_BLUEPRINT;
			case BUTCHER_SHOP -> FLETCHER_HUT_BLUEPRINT;
			case CARTOGRAPHER_HOUSE -> CARTOGRAPHER_HOUSE_BLUEPRINT;
			case CARPENTER_WORKSHOP -> CARPENTER_WORKSHOP_BLUEPRINT;
			case CLERIC_SHRINE -> FLETCHER_HUT_BLUEPRINT;
			case DOCK -> throw new IllegalStateException("Dock build sites use custom block generation");
			case FLETCHER_HUT -> FLETCHER_HUT_BLUEPRINT;
			case FORESTER_WORKSHOP -> FORESTER_WORKSHOP_BLUEPRINT;
			case GARDENER_SHED -> GARDENER_SHED_BLUEPRINT;
			case GUARD_POST -> GUARD_POST_BLUEPRINT;
			case HOUSING_SHELTER -> HOUSING_SHELTER_BLUEPRINT;
			case LEATHERWORKER_WORKSHOP -> FLETCHER_HUT_BLUEPRINT;
			case LIGHTHOUSE -> LIGHTHOUSE_BLUEPRINT;
			case LIBRARY -> FLETCHER_HUT_BLUEPRINT;
			case MASON_WORKSHOP -> MASON_WORKSHOP_BLUEPRINT;
			case MINE_ENTRANCE -> MINE_ENTRANCE_BLUEPRINT;
			case PALISADE_GATEHOUSE, COPPER_PALISADE_GATEHOUSE -> PALISADE_GATEHOUSE_BLUEPRINT;
			case ROADWRIGHT_WORKSHOP -> ROADWRIGHT_WORKSHOP_BLUEPRINT;
			case SCRIBE_OFFICE -> SCRIBE_OFFICE_BLUEPRINT;
			case SHEPHERD_HUT -> FLETCHER_HUT_BLUEPRINT;
			case SIMPLE_HOUSING_SHELTER -> SIMPLE_HOUSING_SHELTER_BLUEPRINT;
			case SMITHY -> FLETCHER_HUT_BLUEPRINT;
			case TRADING_POST -> TRADING_POST_BLUEPRINT;
		};
	}

	private static StructureKind structureKindFor(SettlementBuildSiteType blueprintId) {
		return switch (blueprintId) {
			case BAKERY -> StructureKind.BAKERY;
			case BEEKEEPER_APIARY -> StructureKind.BEEKEEPER_APIARY;
			case BUTCHER_SHOP -> StructureKind.BUTCHER_SHOP;
			case CARTOGRAPHER_HOUSE -> StructureKind.CARTOGRAPHER_HOUSE;
			case CARPENTER_WORKSHOP -> StructureKind.CARPENTER_WORKSHOP;
			case CLERIC_SHRINE -> StructureKind.CLERIC_SHRINE;
			case DOCK -> StructureKind.DOCK;
			case FLETCHER_HUT -> StructureKind.FLETCHER_HUT;
			case FORESTER_WORKSHOP -> StructureKind.FORESTER_WORKSHOP;
			case GARDENER_SHED -> StructureKind.GARDENER_SHED;
			case GUARD_POST -> StructureKind.GUARD_POST;
			case HOUSING_SHELTER -> StructureKind.HOUSING_SHELTER;
			case LEATHERWORKER_WORKSHOP -> StructureKind.LEATHERWORKER_WORKSHOP;
			case LIGHTHOUSE -> StructureKind.LIGHTHOUSE;
			case LIBRARY -> StructureKind.LIBRARY;
			case MASON_WORKSHOP -> StructureKind.MASON_WORKSHOP;
			case MINE_ENTRANCE -> StructureKind.MINE_ENTRANCE;
			case PALISADE_GATEHOUSE -> StructureKind.PALISADE_GATEHOUSE;
			case COPPER_PALISADE_GATEHOUSE -> StructureKind.COPPER_PALISADE_GATEHOUSE;
			case PALISADE_WALL -> throw new IllegalArgumentException("Palisade wall build sites use custom block generation");
			case ROADWRIGHT_WORKSHOP -> StructureKind.ROADWRIGHT_WORKSHOP;
			case SCRIBE_OFFICE -> StructureKind.SCRIBE_OFFICE;
			case SHEPHERD_HUT -> StructureKind.SHEPHERD_HUT;
			case SIMPLE_HOUSING_SHELTER -> StructureKind.SIMPLE_HOUSING_SHELTER;
			case SMITHY -> StructureKind.SMITHY;
			case TRADING_POST -> StructureKind.TRADING_POST;
		};
	}

	private static SettlementBuildSiteType blueprintIdFor(StructureKind structureKind) {
		return switch (structureKind) {
			case BAKERY -> SettlementBuildSiteType.BAKERY;
			case BEEKEEPER_APIARY -> SettlementBuildSiteType.BEEKEEPER_APIARY;
			case BUTCHER_SHOP -> SettlementBuildSiteType.BUTCHER_SHOP;
			case CARTOGRAPHER_HOUSE -> SettlementBuildSiteType.CARTOGRAPHER_HOUSE;
			case CARPENTER_WORKSHOP -> SettlementBuildSiteType.CARPENTER_WORKSHOP;
			case CLERIC_SHRINE -> SettlementBuildSiteType.CLERIC_SHRINE;
			case DOCK -> SettlementBuildSiteType.DOCK;
			case FLETCHER_HUT -> SettlementBuildSiteType.FLETCHER_HUT;
			case FORESTER_WORKSHOP -> SettlementBuildSiteType.FORESTER_WORKSHOP;
			case GARDENER_SHED -> SettlementBuildSiteType.GARDENER_SHED;
			case GUARD_POST -> SettlementBuildSiteType.GUARD_POST;
			case HOUSING_SHELTER -> SettlementBuildSiteType.HOUSING_SHELTER;
			case LEATHERWORKER_WORKSHOP -> SettlementBuildSiteType.LEATHERWORKER_WORKSHOP;
			case LIGHTHOUSE -> SettlementBuildSiteType.LIGHTHOUSE;
			case LIBRARY -> SettlementBuildSiteType.LIBRARY;
			case MASON_WORKSHOP -> SettlementBuildSiteType.MASON_WORKSHOP;
			case MINE_ENTRANCE -> SettlementBuildSiteType.MINE_ENTRANCE;
			case PALISADE_GATEHOUSE -> SettlementBuildSiteType.PALISADE_GATEHOUSE;
			case COPPER_PALISADE_GATEHOUSE -> SettlementBuildSiteType.COPPER_PALISADE_GATEHOUSE;
			case ROADWRIGHT_WORKSHOP -> SettlementBuildSiteType.ROADWRIGHT_WORKSHOP;
			case SCRIBE_OFFICE -> SettlementBuildSiteType.SCRIBE_OFFICE;
			case SHEPHERD_HUT -> SettlementBuildSiteType.SHEPHERD_HUT;
			case SIMPLE_HOUSING_SHELTER -> SettlementBuildSiteType.SIMPLE_HOUSING_SHELTER;
			case SMITHY -> SettlementBuildSiteType.SMITHY;
			case TRADING_POST -> SettlementBuildSiteType.TRADING_POST;
		};
	}

	private static String buildSiteId(String settlementId, StructureKind structureKind, BlockPos workstationPos) {
		return buildSiteId(settlementId, blueprintIdFor(structureKind), workstationPos);
	}

	private static String buildSiteId(String settlementId, SettlementBuildSiteType blueprintId, BlockPos workstationPos) {
		return settlementId
			+ ":"
			+ blueprintId.getSerializedName()
			+ ":"
			+ workstationPos.getX()
			+ "_"
			+ workstationPos.getY()
			+ "_"
			+ workstationPos.getZ();
	}

	private static String palisadeWallBuildSiteId(String settlementId, BlockPos startPos, BlockPos endPos) {
		BlockPos first = compareBlockPos(startPos, endPos) <= 0 ? startPos : endPos;
		BlockPos second = first.equals(startPos) ? endPos : startPos;
		return settlementId
			+ ":"
			+ SettlementBuildSiteType.PALISADE_WALL.getSerializedName()
			+ ":"
			+ first.getX()
			+ "_"
			+ first.getY()
			+ "_"
			+ first.getZ()
			+ ":"
			+ second.getX()
			+ "_"
			+ second.getY()
			+ "_"
			+ second.getZ();
	}

	private static int compareBlockPos(BlockPos first, BlockPos second) {
		if (first.getX() != second.getX()) {
			return Integer.compare(first.getX(), second.getX());
		}

		if (first.getY() != second.getY()) {
			return Integer.compare(first.getY(), second.getY());
		}

		return Integer.compare(first.getZ(), second.getZ());
	}

	private static boolean hasBuildSiteWithId(List<SettlementBuildSite> buildSites, String id) {
		for (SettlementBuildSite buildSite : buildSites) {
			if (buildSite.id().equals(id)) {
				return true;
			}
		}

		return false;
	}

	private static List<PalisadeControlPoint> palisadeControlPoints(
		ServerLevel level,
		SettlementState settlement,
		BlockPos pointPos,
		List<SettlementBuildSite> existingBuildSites
	) {
		List<PalisadeControlPoint> controls = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();

		for (SettlementBuildSite buildSite : existingBuildSites) {
			if (buildSite.blueprintId() == SettlementBuildSiteType.PALISADE_GATEHOUSE
				|| buildSite.blueprintId() == SettlementBuildSiteType.COPPER_PALISADE_GATEHOUSE) {
				BlockPos pos = buildSite.anchorPos().immutable();
				if (!sameColumn(pos, pointPos) && seen.add(pos)) {
					controls.add(new PalisadeControlPoint(pos));
				}
			}
		}

		for (BlockPos placedPoint : findPlacedPalisadePoints(level, settlement)) {
			if (!sameColumn(placedPoint, pointPos) && seen.add(placedPoint)) {
				controls.add(new PalisadeControlPoint(placedPoint));
			}
		}

		return controls;
	}

	private static boolean sameColumn(BlockPos first, BlockPos second) {
		return first.getX() == second.getX() && first.getZ() == second.getZ();
	}

	private static List<PalisadeControlPoint> nearestAngularPalisadeControls(BlockPos center, BlockPos pointPos, List<PalisadeControlPoint> controls) {
		if (controls.size() == 1) {
			return List.of(controls.get(0));
		}

		double pointAngle = angleFromSettlementCenter(center, pointPos);
		PalisadeControlPoint clockwise = null;
		PalisadeControlPoint counterClockwise = null;
		double clockwiseDistance = Double.POSITIVE_INFINITY;
		double counterClockwiseDistance = Double.NEGATIVE_INFINITY;

		for (PalisadeControlPoint control : controls) {
			double distance = positiveAngleDistance(pointAngle, angleFromSettlementCenter(center, control.pos()));
			if (distance <= 0.000001D) {
				continue;
			}

			if (distance < clockwiseDistance) {
				clockwise = control;
				clockwiseDistance = distance;
			}

			if (distance > counterClockwiseDistance) {
				counterClockwise = control;
				counterClockwiseDistance = distance;
			}
		}

		List<PalisadeControlPoint> selected = new ArrayList<>();
		if (clockwise != null) {
			selected.add(clockwise);
		}

		if (counterClockwise != null && !counterClockwise.equals(clockwise)) {
			selected.add(counterClockwise);
		}

		return selected;
	}

	private static BlockPos palisadeWallConnectionEndpoint(BlockPos controlPos, BlockPos targetPos, List<SettlementBuildSite> buildSites) {
		BlockPos bestEndpoint = controlPos;
		double bestDistance = targetPos.distSqr(controlPos);

		for (SettlementBuildSite buildSite : buildSites) {
			if (buildSite.blueprintId() != SettlementBuildSiteType.PALISADE_WALL) {
				continue;
			}

			boolean touchesControl = buildSite.anchorPos().distSqr(controlPos) <= PALISADE_CONTROL_TOUCH_DISTANCE_BLOCKS * PALISADE_CONTROL_TOUCH_DISTANCE_BLOCKS
				|| buildSite.workstationPos().distSqr(controlPos) <= PALISADE_CONTROL_TOUCH_DISTANCE_BLOCKS * PALISADE_CONTROL_TOUCH_DISTANCE_BLOCKS;

			if (!touchesControl) {
				continue;
			}

			double anchorDistance = targetPos.distSqr(buildSite.anchorPos());
			if (anchorDistance < bestDistance) {
				bestEndpoint = buildSite.anchorPos();
				bestDistance = anchorDistance;
			}

			double workstationDistance = targetPos.distSqr(buildSite.workstationPos());
			if (workstationDistance < bestDistance) {
				bestEndpoint = buildSite.workstationPos();
				bestDistance = workstationDistance;
			}
		}

		return bestEndpoint.immutable();
	}

	private static Set<String> obsoletePalisadeWallBuildSiteIds(BlockPos pointPos, List<PalisadeControlPoint> selectedControls, List<SettlementBuildSite> buildSites) {
		Set<String> obsoleteIds = new HashSet<>();
		PalisadeControlPoint firstControl = selectedControls.isEmpty() ? null : selectedControls.get(0);
		PalisadeControlPoint secondControl = selectedControls.size() < 2 ? null : selectedControls.get(1);

		for (SettlementBuildSite buildSite : buildSites) {
			if (buildSite.complete()
				|| buildSite.blueprintId() != SettlementBuildSiteType.PALISADE_WALL) {
				continue;
			}

			boolean directSegmentBetweenSelectedControls = firstControl != null
				&& secondControl != null
				&& palisadeWallTouchesControl(buildSite, firstControl.pos())
				&& palisadeWallTouchesControl(buildSite, secondControl.pos());
			if (directSegmentBetweenSelectedControls || palisadeWallPassesNearPoint(buildSite, pointPos)) {
				obsoleteIds.add(buildSite.id());
			}
		}

		return obsoleteIds;
	}

	public static Set<String> duplicatePalisadeWallBuildSiteIds(ServerLevel level, List<SettlementBuildSite> buildSites) {
		Set<String> duplicateIds = new HashSet<>();

		for (SettlementBuildSite buildSite : buildSites) {
			if (buildSite.complete()
				|| buildSite.blueprintId() != SettlementBuildSiteType.PALISADE_WALL
				|| !palisadeWallAlreadyCovered(level, buildSite, buildSites)) {
				continue;
			}

			duplicateIds.add(buildSite.id());
		}

		return duplicateIds;
	}

	public static Set<String> loadedWorldObsoletePalisadeWallBuildSiteIds(ServerLevel level, List<SettlementBuildSite> buildSites) {
		Set<String> obsoleteIds = new HashSet<>();

		for (SettlementBuildSite buildSite : buildSites) {
			if (buildSite.complete()
				|| buildSite.blueprintId() != SettlementBuildSiteType.PALISADE_WALL) {
				continue;
			}

			if (palisadeWallAlreadyCovered(level, buildSite, buildSites)
				|| palisadeWallInvalidInLoadedWorld(level, buildSite)) {
				obsoleteIds.add(buildSite.id());
			}
		}

		return obsoleteIds;
	}

	private static boolean palisadeWallTouchesControl(SettlementBuildSite buildSite, BlockPos controlPos) {
		int touchDistanceSquared = PALISADE_CONTROL_TOUCH_DISTANCE_BLOCKS * PALISADE_CONTROL_TOUCH_DISTANCE_BLOCKS;
		return buildSite.anchorPos().distSqr(controlPos) <= touchDistanceSquared
			|| buildSite.workstationPos().distSqr(controlPos) <= touchDistanceSquared;
	}

	private static boolean palisadeWallPassesNearPoint(SettlementBuildSite buildSite, BlockPos pointPos) {
		int radiusSquared = PALISADE_POINT_REPLAN_RADIUS_BLOCKS * PALISADE_POINT_REPLAN_RADIUS_BLOCKS;

		if (horizontalDistanceSquared(buildSite.anchorPos(), pointPos) <= radiusSquared
			|| horizontalDistanceSquared(buildSite.workstationPos(), pointPos) <= radiusSquared) {
			return true;
		}

		for (SettlementBuildBlockState block : buildSite.blocks()) {
			if (block.blueprintSymbol().isBlank()
				|| block.blueprintSymbol().charAt(0) != 'L') {
				continue;
			}

			Optional<BlockPos> blockPos = buildSiteBlockPos(buildSite, block);
			if (blockPos.isPresent() && horizontalDistanceSquared(blockPos.get(), pointPos) <= radiusSquared) {
				return true;
			}
		}

		return false;
	}

	private static int horizontalDistanceSquared(BlockPos first, BlockPos second) {
		int dx = first.getX() - second.getX();
		int dz = first.getZ() - second.getZ();
		return dx * dx + dz * dz;
	}

	private static boolean palisadeWallAlreadyCovered(ServerLevel level, SettlementBuildSite buildSite, List<SettlementBuildSite> existingBuildSites) {
		List<BlockPos> plannedColumns = palisadeWallLogColumns(buildSite);
		if (plannedColumns.size() < PALISADE_DUPLICATE_WALL_MIN_COLUMNS) {
			return false;
		}

		Set<String> plannedColumnKeys = plannedColumns.stream()
			.map(SettlementConstruction::columnKey)
			.collect(java.util.stream.Collectors.toSet());
		int coveredColumns = 0;

		for (BlockPos plannedColumn : plannedColumns) {
			if (nearCompletedPalisadeWallColumn(buildSite, existingBuildSites, plannedColumn)
				|| nearBuiltWorldPalisadeColumn(level, plannedColumn, plannedColumnKeys)) {
				coveredColumns++;
			}
		}

		return coveredColumns * 100 >= plannedColumns.size() * PALISADE_DUPLICATE_WALL_COVERAGE_PERCENT;
	}

	private static boolean palisadeWallInvalidInLoadedWorld(ServerLevel level, SettlementBuildSite buildSite) {
		List<BlockPos> plannedColumns = palisadeWallLogColumns(buildSite);
		return palisadeWallHasLargeColumnGap(plannedColumns)
			|| palisadeWallCrossesWater(level, plannedColumns);
	}

	private static boolean palisadeWallHasLargeColumnGap(List<BlockPos> columns) {
		if (columns.size() < 2) {
			return false;
		}

		BlockPos previousColumn = columns.get(0);
		for (int index = 1; index < columns.size(); index++) {
			BlockPos column = columns.get(index);
			if (horizontalDistanceSquared(previousColumn, column) > PALISADE_ROUTE_GAP_MAX_DISTANCE_SQUARED) {
				return true;
			}
			previousColumn = column;
		}

		return false;
	}

	private static boolean palisadeWallCrossesWater(ServerLevel level, List<BlockPos> columns) {
		if (columns.size() < 2) {
			return false;
		}

		int waterColumns = 0;
		for (BlockPos column : columns) {
			if (palisadeColumnIntersectsWater(level, column)) {
				waterColumns++;
			}
		}

		return waterColumns >= PALISADE_INVALID_WATER_MIN_COLUMNS
			|| waterColumns * 100 >= columns.size() * PALISADE_INVALID_WATER_COVERAGE_PERCENT;
	}

	private static boolean palisadeColumnIntersectsWater(ServerLevel level, BlockPos basePos) {
		for (int up = -1; up < PALISADE_WALL_HEIGHT_BLOCKS; up++) {
			BlockPos pos = basePos.above(up);
			if (level.hasChunkAt(pos) && level.getFluidState(pos).is(FluidTags.WATER)) {
				return true;
			}
		}

		return false;
	}

	private static boolean nearCompletedPalisadeWallColumn(
		SettlementBuildSite buildSite,
		List<SettlementBuildSite> existingBuildSites,
		BlockPos plannedColumn
	) {
		int radiusSquared = PALISADE_DUPLICATE_WALL_RADIUS_BLOCKS * PALISADE_DUPLICATE_WALL_RADIUS_BLOCKS;

		for (SettlementBuildSite existingBuildSite : existingBuildSites) {
			if (existingBuildSite.id().equals(buildSite.id())
				|| !existingBuildSite.complete()
				|| existingBuildSite.blueprintId() != SettlementBuildSiteType.PALISADE_WALL) {
				continue;
			}

			for (BlockPos existingColumn : palisadeWallLogColumns(existingBuildSite)) {
				if (horizontalDistanceSquared(existingColumn, plannedColumn) <= radiusSquared) {
					return true;
				}
			}
		}

		return false;
	}

	private static boolean nearBuiltWorldPalisadeColumn(ServerLevel level, BlockPos plannedColumn, Set<String> plannedColumnKeys) {
		int radius = PALISADE_DUPLICATE_WALL_RADIUS_BLOCKS;
		int radiusSquared = radius * radius;

		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (dx == 0 && dz == 0) {
					continue;
				}

				BlockPos candidateColumn = plannedColumn.offset(dx, 0, dz);
				if (horizontalDistanceSquared(candidateColumn, plannedColumn) > radiusSquared
					|| plannedColumnKeys.contains(columnKey(candidateColumn))) {
					continue;
				}

				for (int dy = -4; dy <= 4; dy++) {
					BlockPos candidatePos = candidateColumn.offset(0, dy, 0);
					if (level.hasChunkAt(candidatePos) && isLogColumnAtLeast(level, candidatePos, PALISADE_WALL_HEIGHT_BLOCKS)) {
						return true;
					}
				}
			}
		}

		return false;
	}

	private static List<BlockPos> palisadeWallLogColumns(SettlementBuildSite buildSite) {
		Map<String, BlockPos> columns = new LinkedHashMap<>();

		for (SettlementBuildBlockState block : buildSite.blocks()) {
			if (block.blueprintSymbol().isBlank()
				|| block.blueprintSymbol().charAt(0) != 'L') {
				continue;
			}

			Optional<BlockPos> blockPos = buildSiteBlockPos(buildSite, block);
			if (blockPos.isEmpty()) {
				continue;
			}

			String key = columnKey(blockPos.get());
			BlockPos previous = columns.get(key);
			if (previous == null || blockPos.get().getY() < previous.getY()) {
				columns.put(key, blockPos.get().immutable());
			}
		}

		return List.copyOf(columns.values());
	}

	private static String columnKey(BlockPos pos) {
		return pos.getX() + "," + pos.getZ();
	}

	private static SettlementBuildSite createPendingPalisadeWallBuildSite(
		ServerLevel level,
		SettlementState settlement,
		BlockPos startPos,
		BlockPos endPos,
		Map<String, Integer> stock,
		List<SettlementBuildSite> existingBuildSites,
		long tick
	) {
		List<PalisadeWallColumn> columns = palisadeWallColumns(level, settlement.center(), startPos, endPos);
		if (columns.size() < 2) {
			return null;
		}
		List<BlockPos> columnPositions = columns.stream()
			.map(PalisadeWallColumn::basePos)
			.toList();
		if (palisadeWallHasLargeColumnGap(columnPositions)
			|| palisadeWallCrossesWater(level, columnPositions)) {
			return null;
		}

		BlockPos origin = columns.get(0).basePos();
		StructureMaterialPalette palette = materialPaletteFor(level, origin);
		Map<String, SettlementBuildBlockState> blocks = new LinkedHashMap<>();
		int[] slabHeights = smoothedPalisadeSlabHeights(columns);

		for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
			PalisadeWallColumn column = columns.get(columnIndex);
			BlockPos basePos = column.basePos();

			for (int up = 0; up < PALISADE_WALL_HEIGHT_BLOCKS; up++) {
				addPalisadeWallBlock(blocks, origin, basePos.above(up), 'L', "logs");
			}

			BlockPos slabPos = basePos.offset(column.inwardStep().right(), 0, column.inwardStep().forward());
			int halfHeight = slabHeights[columnIndex];
			int slabY = halfHeight / 2;
			char slabSymbol = (halfHeight % 2) == 0 ? 'C' : 'B';
			addPalisadeWallBlock(blocks, origin, new BlockPos(slabPos.getX(), slabY, slabPos.getZ()), slabSymbol, "slab");
		}

		if (blocks.isEmpty()) {
			return null;
		}

		SettlementBuildSite buildSite = new SettlementBuildSite(
			palisadeWallBuildSiteId(settlement.id(), startPos, endPos),
			settlement.id(),
			SettlementBuildSiteType.PALISADE_WALL,
			origin.immutable(),
			endPos.immutable(),
			startPos.immutable(),
			Direction.NORTH,
			palette.woodFamily(),
			palette.stoneMaterial(),
			Map.of(),
			List.copyOf(blocks.values()),
			false,
			tick,
			tick
		);

		if (palisadeWallAlreadyCovered(level, buildSite, existingBuildSites)) {
			return null;
		}

		return buildSite.withBlocks(reconciledInitialPalisadeWallBlocks(level, buildSite, stock), false, tick);
	}

	private static List<SettlementBuildBlockState> reconciledInitialPalisadeWallBlocks(
		ServerLevel level,
		SettlementBuildSite buildSite,
		Map<String, Integer> stock
	) {
		List<SettlementBuildBlockState> blocks = new ArrayList<>();

		for (SettlementBuildBlockState block : buildSite.blocks()) {
			Optional<BlockPos> blockPos = buildSiteBlockPos(buildSite, block);
			BlockState plannedState = plannedBuildSiteBlockState(buildSite, block);

			if (blockPos.isPresent()
				&& plannedState != null
				&& level.hasChunkAt(blockPos.get())
				&& isFlexibleMaterialMatch(level.getBlockState(blockPos.get()), plannedState, block.expectedMaterialKey())) {
				blocks.add(block.withStatus(SettlementBuildBlockStatus.PLAYER_PLACED, ""));
			} else {
				blocks.add(block);
			}
		}

		return blocks;
	}

	private static void addPalisadeWallBlock(
		Map<String, SettlementBuildBlockState> blocks,
		BlockPos origin,
		BlockPos worldPos,
		char symbol,
		String materialKey
	) {
		String position = relativeBlueprintPositionFromWorld(origin, Direction.NORTH, worldPos);
		blocks.putIfAbsent(position, SettlementBuildBlockState.pending(position, symbol, materialKey));
	}

	private static List<PalisadeWallColumn> palisadeWallColumns(ServerLevel level, BlockPos center, BlockPos startPos, BlockPos endPos) {
		double startAngle = angleFromSettlementCenter(center, startPos);
		double endAngle = angleFromSettlementCenter(center, endPos);
		double angleDelta = signedAngleDelta(startAngle, endAngle);
		double startRadius = horizontalDistance(center, startPos);
		double endRadius = horizontalDistance(center, endPos);
		int steps = Math.max(1, (int) Math.ceil(Math.abs(angleDelta) * Math.max(startRadius, endRadius)));
		List<PalisadeWallColumn> columns = new ArrayList<>();
		Set<String> seenColumns = new HashSet<>();

		for (int step = 0; step <= steps; step++) {
			double t = step / (double) steps;
			double angle = startAngle + angleDelta * t;
			double radius = startRadius + (endRadius - startRadius) * t;
			int x = step == 0
				? startPos.getX()
				: step == steps
				? endPos.getX()
				: center.getX() + (int) Math.round(Math.sin(angle) * radius);
			int z = step == 0
				? startPos.getZ()
				: step == steps
				? endPos.getZ()
				: center.getZ() - (int) Math.round(Math.cos(angle) * radius);
			String key = x + "," + z;

			if (!seenColumns.add(key)) {
				continue;
			}

			BlockPos basePos = palisadeWallBasePos(level, new BlockPos(x, center.getY(), z));
			if (basePos == null) {
				continue;
			}

			BlueprintRelativeStep inwardStep = inwardStepTowardCenter(center, basePos);
			columns.add(new PalisadeWallColumn(basePos, inwardStep));
		}

		return columns;
	}

	private static BlockPos palisadeWallBasePos(ServerLevel level, BlockPos columnPos) {
		if (!level.hasChunkAt(columnPos)) {
			return null;
		}

		int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, columnPos.getX(), columnPos.getZ()) - 1;
		if (groundY < level.getMinY()) {
			return null;
		}

		BlockPos groundPos = new BlockPos(columnPos.getX(), groundY, columnPos.getZ());
		if (level.getBlockState(groundPos).is(LiveVillagesBlocks.PALISADE_POINT)) {
			return groundPos;
		}

		if (!hasStableBuildGround(level, groundPos)) {
			return null;
		}

		BlockPos basePos = groundPos.above();
		if (level.getFluidState(basePos).is(FluidTags.WATER)) {
			return null;
		}

		return basePos;
	}

	private static BlueprintRelativeStep inwardStepTowardCenter(BlockPos center, BlockPos wallPos) {
		int dx = center.getX() - wallPos.getX();
		int dz = center.getZ() - wallPos.getZ();

		if (Math.abs(dx) >= Math.abs(dz)) {
			return new BlueprintRelativeStep(dx >= 0 ? 1 : -1, 0);
		}

		return new BlueprintRelativeStep(0, dz >= 0 ? 1 : -1);
	}

	private static int[] smoothedPalisadeSlabHeights(List<PalisadeWallColumn> columns) {
		int[] heights = new int[columns.size()];

		for (int index = 0; index < columns.size(); index++) {
			heights[index] = columns.get(index).basePos().getY() * 2 + 5;
			if (index > 0 && heights[index] > heights[index - 1] + 1) {
				heights[index] = heights[index - 1] + 1;
			}
		}

		for (int index = columns.size() - 2; index >= 0; index--) {
			if (heights[index] > heights[index + 1] + 1) {
				heights[index] = heights[index + 1] + 1;
			}
		}

		for (int index = 0; index < columns.size(); index++) {
			int minHalfHeight = columns.get(index).basePos().getY() * 2;
			int maxHalfHeight = columns.get(index).basePos().getY() * 2 + 5;
			heights[index] = clamp(heights[index], minHalfHeight, maxHalfHeight);
		}

		return heights;
	}

	private static double angleFromSettlementCenter(BlockPos center, BlockPos pos) {
		return normalizeAngle(Math.atan2(pos.getX() - center.getX(), center.getZ() - pos.getZ()));
	}

	private static double horizontalDistance(BlockPos center, BlockPos pos) {
		double dx = pos.getX() - center.getX();
		double dz = pos.getZ() - center.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static double positiveAngleDistance(double fromAngle, double toAngle) {
		double distance = normalizeAngle(toAngle - fromAngle);
		return distance == 0.0D ? Math.PI * 2.0D : distance;
	}

	private static double signedAngleDelta(double fromAngle, double toAngle) {
		double delta = normalizeAngle(toAngle - fromAngle);
		if (delta > Math.PI) {
			delta -= Math.PI * 2.0D;
		}
		return delta;
	}

	private static double normalizeAngle(double angle) {
		double fullTurn = Math.PI * 2.0D;
		double normalized = angle % fullTurn;
		return normalized < 0.0D ? normalized + fullTurn : normalized;
	}

	private static CompletionResult tryBuildDock(ServerLevel level, SettlementState settlement, Map<String, Integer> stock) {
		Map<String, Integer> cost = SettlementProjectType.DOCK.stockCost();

		if (!canAfford(stock, cost)) {
			return CompletionResult.notCompleted();
		}

		DockSite site = findDockSite(level, settlement);

		if (site == null) {
			return CompletionResult.notCompleted();
		}

		consumeCost(stock, cost);
		placeDock(level, site, stock);
		return CompletionResult.completed(0);
	}

	private static CompletionResult tryBuildLighthouse(ServerLevel level, SettlementState settlement, Map<String, Integer> stock) {
		Map<String, Integer> cost = SettlementProjectType.LIGHTHOUSE.stockCost();

		if (!canAfford(stock, cost)) {
			return CompletionResult.notCompleted();
		}

		LighthouseSite site = findLighthouseSite(level, settlement);

		if (site == null) {
			return CompletionResult.notCompleted();
		}

		consumeCost(stock, cost);
		placeLighthouse(level, site, stock);
		return CompletionResult.completed(0);
	}

	private static DockSite findDockSite(ServerLevel level, SettlementState settlement) {
		List<BlockPos> anchorPositions = findPlacedPortmasterAnchors(level, settlement);

		for (BlockPos anchorPos : anchorPositions) {
			DockSite anchoredSite = findDockSiteNearPortmasterAnchor(level, anchorPos, portmasterAnchorFacingFor(level, anchorPos));

			if (anchoredSite != null) {
				return anchoredSite;
			}
		}

		if (!anchorPositions.isEmpty()) {
			return null;
		}

		for (int radius = 3; radius <= buildRadius(settlement); radius++) {
			for (int offsetX = -radius; offsetX <= radius; offsetX++) {
				for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
					if (Math.max(Math.abs(offsetX), Math.abs(offsetZ)) != radius) {
						continue;
					}

					BlockPos columnPos = settlement.center().offset(offsetX, 0, offsetZ);

					if (!level.hasChunkAt(columnPos)) {
						continue;
					}

					WaterColumnSurvey waterColumn = surveyWaterColumn(level, columnPos.getX(), columnPos.getZ());

					if (waterColumn.surfaceY() < 0) {
						continue;
					}

					BlockPos origin = new BlockPos(columnPos.getX(), waterColumn.surfaceY(), columnPos.getZ());

					for (Direction facing : Direction.Plane.HORIZONTAL) {
						DockSite site = evaluateDockSite(level, origin, facing);

						if (site != null) {
							return site;
						}
					}
				}
			}
		}

		return null;
	}

	private static DockSite findDockSiteNearPortmasterAnchor(ServerLevel level, BlockPos anchorPos, Direction facing) {
		return findDockSiteNearPortmasterAnchor(level, anchorPos, facing, false);
	}

	private static DockSite findDockPreviewSiteNearPortmasterAnchor(ServerLevel level, BlockPos anchorPos, Direction facing) {
		return findDockSiteNearPortmasterAnchor(level, anchorPos, facing, true);
	}

	private static DockSite findDockSiteNearPortmasterAnchor(ServerLevel level, BlockPos anchorPos, Direction facing, boolean allowExistingDockBlocks) {
		Direction horizontalFacing = facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : facing;
		Direction dockFacing = horizontalFacing.getOpposite();

		for (int step = 1; step <= 10; step++) {
			BlockPos columnPos = anchorPos.relative(dockFacing, step);

			if (!level.hasChunkAt(columnPos)) {
				continue;
			}

			WaterColumnSurvey waterColumn = surveyWaterColumn(level, columnPos.getX(), columnPos.getZ());
				if (waterColumn.surfaceY() < 0) {
					continue;
				}

				DockSite site = evaluateDockSite(level, new BlockPos(columnPos.getX(), waterColumn.surfaceY(), columnPos.getZ()), dockFacing, allowExistingDockBlocks);
				if (site != null) {
					return site;
				}
			}

			return null;
		}

	private static DockSite evaluateDockSite(ServerLevel level, BlockPos origin, Direction facing) {
		return evaluateDockSite(level, origin, facing, false);
	}

	private static DockSite evaluateDockSite(ServerLevel level, BlockPos origin, Direction facing, boolean allowExistingDockBlocks) {
		if (!hasDockShoreAccess(level, origin.relative(facing.getOpposite()), origin.getY())) {
			return null;
		}

		boolean foundDeepWaterAtEnd = false;
		int shorelineReplacementBlocks = 0;

		for (int forward = 0; forward < DOCK_LENGTH_BLOCKS; forward++) {
			for (int right = -DOCK_HALF_WIDTH_BLOCKS; right <= DOCK_HALF_WIDTH_BLOCKS; right++) {
				BlockPos deckPos = offset(origin, facing, right, forward, 0);

				if (!level.hasChunkAt(deckPos)) {
					return null;
				}

				BlockState deckState = level.getBlockState(deckPos);
				WaterColumnSurvey waterColumn = surveyWaterColumn(level, deckPos.getX(), deckPos.getZ());

				if (allowExistingDockBlocks && isDockDeck(deckState)) {
					if (!existingDockDeckFitsPreview(level, deckPos, forward)) {
						return null;
					}

					if (right == 0
						&& forward >= DOCK_LENGTH_BLOCKS - DOCK_DEEP_WATER_END_ROWS
						&& existingDockDeckWaterDepth(level, deckPos) >= 2) {
						foundDeepWaterAtEnd = true;
					}
				} else if (waterColumn.surfaceY() == deckPos.getY()) {
					if (!canPlaceDockDeck(level, deckPos)) {
						return null;
					}

					if (right == 0
						&& forward >= DOCK_LENGTH_BLOCKS - DOCK_DEEP_WATER_END_ROWS
						&& waterColumn.depth() >= 2) {
						foundDeepWaterAtEnd = true;
					}
				} else {
					if (forward >= DOCK_SHORE_REPLACEMENT_ROWS
						|| shorelineReplacementBlocks >= MAX_DOCK_SHORE_REPLACEMENT_BLOCKS
						|| !canReplaceDockShoreBlock(level, deckPos)) {
						return null;
					}

						shorelineReplacementBlocks++;
				}

				if (isDockSupportRow(forward) && Math.abs(right) == DOCK_HALF_WIDTH_BLOCKS) {
					if (allowExistingDockBlocks && hasDockLogSupport(level, deckPos)) {
						continue;
					}

					if (findDockFooting(level, deckPos) == null) {
						return null;
					}
				}
			}
		}

		return foundDeepWaterAtEnd ? new DockSite(origin, facing) : null;
	}

	private static void placeDock(ServerLevel level, DockSite site, Map<String, Integer> stock) {
		String woodFamily = dockWoodFamilyForSite(level, site);
		for (int forward = 0; forward < DOCK_LENGTH_BLOCKS; forward++) {
			for (int right = -DOCK_HALF_WIDTH_BLOCKS; right <= DOCK_HALF_WIDTH_BLOCKS; right++) {
				BlockPos deckPos = offset(site.origin(), site.facing(), right, forward, 0);
				BlockPos abovePos = deckPos.above();

				if (!level.getBlockState(abovePos).isAir()) {
					clearBlockToWarehouse(level, abovePos, stock);
				}

				clearBlockToWarehouse(level, deckPos, stock);
				level.setBlock(deckPos, woodPlankBlock(woodFamily).defaultBlockState(), BLOCK_UPDATE_FLAGS);

				if (isDockSupportRow(forward) && Math.abs(right) == DOCK_HALF_WIDTH_BLOCKS) {
					BlockPos footing = findDockFooting(level, deckPos);

					if (footing == null) {
						continue;
					}

					for (int y = deckPos.getY() - 1; y > footing.getY(); y--) {
						level.setBlock(new BlockPos(deckPos.getX(), y, deckPos.getZ()), woodLogBlock(woodFamily).defaultBlockState(), BLOCK_UPDATE_FLAGS);
					}
				}
			}
		}
	}

	private static LighthouseSite findLighthouseSite(ServerLevel level, SettlementState settlement) {
		for (BlockPos markerPos : findPlacedLighthouses(level, settlement)) {
			LighthouseSite anchoredSite = evaluateLighthouseMarkerSite(level, markerPos);

			if (anchoredSite != null) {
				return anchoredSite;
			}
		}

		for (int radius = 4; radius <= buildRadius(settlement); radius++) {
			for (int offsetX = -radius; offsetX <= radius; offsetX++) {
				for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
					if (Math.max(Math.abs(offsetX), Math.abs(offsetZ)) != radius) {
						continue;
					}

					BlockPos candidate = settlement.center().offset(offsetX, 0, offsetZ);

					if (!level.hasChunkAt(candidate)) {
						continue;
					}

					LighthouseSite site = evaluateLighthouseSite(level, candidate);

					if (site != null) {
						return site;
					}
				}
			}
		}

		return null;
	}

	private static LighthouseSite evaluateLighthouseSite(ServerLevel level, BlockPos candidateCenter) {
		if (!hasNearbyWater(level, candidateCenter, LIGHTHOUSE_WATER_RADIUS_BLOCKS)) {
			return null;
		}

		int minGroundY = Integer.MAX_VALUE;
		int maxGroundY = Integer.MIN_VALUE;

		for (int offsetX = -1; offsetX <= 1; offsetX++) {
			for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
				BlockPos columnPos = candidateCenter.offset(offsetX, 0, offsetZ);

				if (!level.hasChunkAt(columnPos)) {
					return null;
				}

				int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, columnPos.getX(), columnPos.getZ()) - 1;

				if (groundY < level.getMinY()) {
					return null;
				}

				BlockPos groundPos = new BlockPos(columnPos.getX(), groundY, columnPos.getZ());

				if (!hasStableBuildGround(level, groundPos)) {
					return null;
				}

				minGroundY = Math.min(minGroundY, groundY);
				maxGroundY = Math.max(maxGroundY, groundY);
			}
		}

		if (maxGroundY - minGroundY > 1) {
			return null;
		}

		BlockPos baseCenter = new BlockPos(candidateCenter.getX(), maxGroundY + 1, candidateCenter.getZ());

		for (int offsetX = -1; offsetX <= 1; offsetX++) {
			for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
				for (int y = baseCenter.getY(); y < baseCenter.getY() + LIGHTHOUSE_BASE_LEVELS; y++) {
					BlockPos blockPos = new BlockPos(baseCenter.getX() + offsetX, y, baseCenter.getZ() + offsetZ);
					BlockState state = level.getBlockState(blockPos);

					if (!isReplaceable(state) && !canLandscapeRemove(level, blockPos, state)) {
						return null;
					}
				}
			}
		}

		for (int y = baseCenter.getY() + LIGHTHOUSE_BASE_LEVELS; y < baseCenter.getY() + LIGHTHOUSE_BASE_LEVELS + LIGHTHOUSE_TOWER_LEVELS + 1; y++) {
			BlockPos blockPos = new BlockPos(baseCenter.getX(), y, baseCenter.getZ());
			BlockState state = level.getBlockState(blockPos);

			if (!isReplaceable(state) && !canLandscapeRemove(level, blockPos, state)) {
				return null;
			}
		}

		return new LighthouseSite(baseCenter);
	}

	private static LighthouseSite evaluateLighthouseMarkerSite(ServerLevel level, BlockPos markerPos) {
		if (!hasNearbyWater(level, markerPos, LIGHTHOUSE_WATER_RADIUS_BLOCKS)) {
			return null;
		}

		int minGroundY = Integer.MAX_VALUE;
		int maxGroundY = Integer.MIN_VALUE;

		for (int offsetX = -1; offsetX <= 1; offsetX++) {
			for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
				BlockPos columnPos = markerPos.offset(offsetX, 0, offsetZ);

				if (!level.hasChunkAt(columnPos)) {
					return null;
				}

				int groundY;

				if (offsetX == 0 && offsetZ == 0 && level.getBlockState(markerPos).is(LiveVillagesBlocks.LIGHTHOUSE)) {
					groundY = markerPos.getY() - 1;
				} else {
					groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, columnPos.getX(), columnPos.getZ()) - 1;
				}

				if (groundY < level.getMinY()) {
					return null;
				}

				BlockPos groundPos = new BlockPos(columnPos.getX(), groundY, columnPos.getZ());

				if (!hasStableBuildGround(level, groundPos)) {
					return null;
				}

				minGroundY = Math.min(minGroundY, groundY);
				maxGroundY = Math.max(maxGroundY, groundY);
			}
		}

		if (maxGroundY - minGroundY > 1 || maxGroundY + 1 != markerPos.getY()) {
			return null;
		}

		for (int offsetX = -1; offsetX <= 1; offsetX++) {
			for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
				for (int y = markerPos.getY(); y < markerPos.getY() + LIGHTHOUSE_BASE_LEVELS; y++) {
					BlockPos blockPos = new BlockPos(markerPos.getX() + offsetX, y, markerPos.getZ() + offsetZ);
					BlockState state = level.getBlockState(blockPos);

					if (blockPos.equals(markerPos) && state.is(LiveVillagesBlocks.LIGHTHOUSE)) {
						continue;
					}

					if (!isReplaceable(state) && !canLandscapeRemove(level, blockPos, state)) {
						return null;
					}
				}
			}
		}

		for (int y = markerPos.getY() + LIGHTHOUSE_BASE_LEVELS; y < markerPos.getY() + LIGHTHOUSE_BASE_LEVELS + LIGHTHOUSE_TOWER_LEVELS + 1; y++) {
			BlockPos blockPos = new BlockPos(markerPos.getX(), y, markerPos.getZ());
			BlockState state = level.getBlockState(blockPos);

			if (!isReplaceable(state) && !canLandscapeRemove(level, blockPos, state)) {
				return null;
			}
		}

		return new LighthouseSite(markerPos.immutable());
	}

	private static void placeLighthouse(ServerLevel level, LighthouseSite site, Map<String, Integer> stock) {
		for (int offsetX = -1; offsetX <= 1; offsetX++) {
			for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
				int x = site.baseCenter().getX() + offsetX;
				int z = site.baseCenter().getZ() + offsetZ;
				int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;

				for (int y = groundY + 1; y < site.baseCenter().getY(); y++) {
					level.setBlock(new BlockPos(x, y, z), Blocks.COBBLESTONE.defaultBlockState(), BLOCK_UPDATE_FLAGS);
				}

				for (int y = site.baseCenter().getY(); y < site.baseCenter().getY() + LIGHTHOUSE_BASE_LEVELS; y++) {
					BlockPos blockPos = new BlockPos(x, y, z);
					clearBlockToWarehouse(level, blockPos, stock);
					level.setBlock(blockPos, Blocks.COBBLESTONE.defaultBlockState(), BLOCK_UPDATE_FLAGS);
				}
			}
		}

		for (int y = site.baseCenter().getY() + LIGHTHOUSE_BASE_LEVELS; y < site.baseCenter().getY() + LIGHTHOUSE_BASE_LEVELS + LIGHTHOUSE_TOWER_LEVELS; y++) {
			BlockPos blockPos = new BlockPos(site.baseCenter().getX(), y, site.baseCenter().getZ());
			clearBlockToWarehouse(level, blockPos, stock);
			level.setBlock(blockPos, Blocks.COBBLESTONE.defaultBlockState(), BLOCK_UPDATE_FLAGS);
		}

		BlockPos campfirePos = site.baseCenter().above(LIGHTHOUSE_BASE_LEVELS + LIGHTHOUSE_TOWER_LEVELS);
		clearBlockToWarehouse(level, campfirePos, stock);
		level.setBlock(campfirePos, Blocks.CAMPFIRE.defaultBlockState(), BLOCK_UPDATE_FLAGS);
	}

	private static BlockPos findComposterSite(ServerLevel level, SettlementState settlement) {
		for (int radius = 2; radius <= buildRadius(settlement); radius++) {
			for (int offsetX = -radius; offsetX <= radius; offsetX++) {
				for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
					if (Math.max(Math.abs(offsetX), Math.abs(offsetZ)) != radius) {
						continue;
					}

					BlockPos surfacePos = topPlacementPos(level, settlement.center().offset(offsetX, 0, offsetZ));

					if (surfacePos == null || !hasNearbyFarmBlock(level, surfacePos)) {
						continue;
					}

					return surfacePos;
				}
			}
		}

		return null;
	}

	private static StorageSite findStorageSite(ServerLevel level, SettlementState settlement) {
		for (int radius = 2; radius <= Math.min(12, buildRadius(settlement)); radius++) {
			for (int offsetX = -radius; offsetX <= radius; offsetX++) {
				for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
					if (Math.max(Math.abs(offsetX), Math.abs(offsetZ)) != radius) {
						continue;
					}

					BlockPos surfacePos = topPlacementPos(level, settlement.center().offset(offsetX, 0, offsetZ));

					if (surfacePos == null || !level.getBlockState(surfacePos.above()).isAir()) {
						continue;
					}

					return new StorageSite(surfacePos, facingToward(settlement.center(), surfacePos));
				}
			}
		}

		return null;
	}

	private static BlockPos topPlacementPos(ServerLevel level, BlockPos columnPos) {
		if (!level.hasChunkAt(columnPos)) {
			return null;
		}

		int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, columnPos.getX(), columnPos.getZ()) - 1;

		if (groundY < level.getMinY()) {
			return null;
		}

		BlockPos groundPos = new BlockPos(columnPos.getX(), groundY, columnPos.getZ());
		BlockPos placementPos = groundPos.above();

		if (!hasStableBuildGround(level, groundPos) || !isReplaceable(level.getBlockState(placementPos))) {
			return null;
		}

		return placementPos;
	}

	private static boolean hasNearbyFarmBlock(ServerLevel level, BlockPos pos) {
		for (int offsetX = -2; offsetX <= 2; offsetX++) {
			for (int offsetZ = -2; offsetZ <= 2; offsetZ++) {
				BlockPos checkPos = pos.offset(offsetX, 0, offsetZ);
				BlockState state = level.getBlockState(checkPos);
				BlockState belowState = level.getBlockState(checkPos.below());

				if (state.is(Blocks.WHEAT) || state.is(Blocks.CARROTS) || state.is(Blocks.POTATOES) || belowState.is(Blocks.FARMLAND)) {
					return true;
				}
			}
		}

		return false;
	}

	private static boolean hasNearbyWater(ServerLevel level, BlockPos center, int radiusBlocks) {
		int nearbyWaterColumns = 0;

		for (int offsetX = -radiusBlocks; offsetX <= radiusBlocks; offsetX++) {
			for (int offsetZ = -radiusBlocks; offsetZ <= radiusBlocks; offsetZ++) {
				BlockPos columnPos = center.offset(offsetX, 0, offsetZ);

				if (!level.hasChunkAt(columnPos)) {
					continue;
				}

				if (surveyWaterColumn(level, columnPos.getX(), columnPos.getZ()).surfaceY() >= 0) {
					nearbyWaterColumns++;

					if (nearbyWaterColumns >= 6) {
						return true;
					}
				}
			}
		}

		return false;
	}

	private static WaterColumnSurvey surveyWaterColumn(ServerLevel level, int x, int z) {
		int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;

		if (surfaceY < level.getMinY() || surfaceY > level.getMaxY() - 1) {
			return WaterColumnSurvey.empty();
		}

		BlockPos surfacePos = new BlockPos(x, surfaceY, z);

		if (!level.getBlockState(surfacePos).is(Blocks.WATER)) {
			return WaterColumnSurvey.empty();
		}

		int depth = 0;

		for (int y = surfaceY; y >= level.getMinY(); y--) {
			BlockState state = level.getBlockState(new BlockPos(x, y, z));

			if (state.is(Blocks.WATER)) {
				depth++;
				continue;
			}

			if (isReplaceable(state)) {
				continue;
			}

			break;
		}

		return new WaterColumnSurvey(surfaceY, depth);
	}

	private static boolean hasDockShoreAccess(ServerLevel level, BlockPos shoreColumnPos, int dockY) {
		if (!level.hasChunkAt(shoreColumnPos)) {
			return false;
		}

		int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, shoreColumnPos.getX(), shoreColumnPos.getZ()) - 1;

		if (groundY < level.getMinY()) {
			return false;
		}

		BlockPos groundPos = new BlockPos(shoreColumnPos.getX(), groundY, shoreColumnPos.getZ());
		return !level.getBlockState(groundPos).is(Blocks.WATER) && hasStableBuildGround(level, groundPos) && Math.abs((groundY + 1) - dockY) <= 2;
	}

	private static boolean canPlaceDockDeck(ServerLevel level, BlockPos deckPos) {
		BlockState deckState = level.getBlockState(deckPos);
		BlockState aboveState = level.getBlockState(deckPos.above());
		return (deckState.is(Blocks.WATER) || isReplaceable(deckState)) && (aboveState.isAir() || isReplaceable(aboveState));
	}

	private static boolean canReplaceDockShoreBlock(ServerLevel level, BlockPos deckPos) {
		BlockState deckState = level.getBlockState(deckPos);
		BlockState aboveState = level.getBlockState(deckPos.above());

		if (deckState.is(Blocks.WATER) || isReplaceable(deckState)) {
			return false;
		}

		if (!(aboveState.isAir() || isReplaceable(aboveState))) {
			return false;
		}

		return canLandscapeRemove(level, deckPos, deckState);
	}

	private static BlockPos findDockFooting(ServerLevel level, BlockPos deckPos) {
		for (int y = deckPos.getY() - 1; y >= level.getMinY(); y--) {
			BlockPos checkPos = new BlockPos(deckPos.getX(), y, deckPos.getZ());
			BlockState state = level.getBlockState(checkPos);

			if (state.is(Blocks.WATER) || isReplaceable(state)) {
				continue;
			}

			return hasStableBuildGround(level, checkPos) ? checkPos : null;
		}

		return null;
	}

	private static boolean existingDockDeckFitsPreview(ServerLevel level, BlockPos deckPos, int forward) {
		BlockState belowState = level.getBlockState(deckPos.below());
		return belowState.liquid() || isDockSupportLog(belowState) || forward < DOCK_SHORE_REPLACEMENT_ROWS;
	}

	private static int existingDockDeckWaterDepth(ServerLevel level, BlockPos deckPos) {
		int depth = 1;

		for (int y = deckPos.getY() - 1; y >= level.getMinY(); y--) {
			BlockState state = level.getBlockState(new BlockPos(deckPos.getX(), y, deckPos.getZ()));

			if (state.is(Blocks.WATER)) {
				depth++;
				continue;
			}

			if (isReplaceable(state)) {
				continue;
			}

			break;
		}

		return depth;
	}

	private static void repairFletcherHutFrontAccess(ServerLevel level, SettlementBuildSite buildSite, Map<String, Integer> stock) {
		if (buildSite.blueprintId() != SettlementBuildSiteType.FLETCHER_HUT) {
			return;
		}

		clearFletcherHutAccessBlock(level, buildSite, stock, -1, 3, 1);
		clearFletcherHutAccessBlock(level, buildSite, stock, 1, 3, 1);
	}

	private static void clearFletcherHutAccessBlock(
		ServerLevel level,
		SettlementBuildSite buildSite,
		Map<String, Integer> stock,
		int right,
		int forward,
		int up
	) {
		BlockPos pos = offset(buildSite.origin(), buildSite.facing(), right, forward, up);
		BlockState state = level.getBlockState(pos);

		if (state.is(Blocks.OAK_PLANKS)) {
			clearBlockToWarehouse(level, pos, stock);
		}
	}

	private static SettlementBuildSite createPendingDockBuildSite(
		ServerLevel level,
		String settlementId,
		BlockPos anchorPos,
		DockSite site,
		long tick
	) {
		StructureMaterialPalette palette = materialPaletteFor(level, anchorPos);
		List<SettlementBuildBlockState> blocks = new ArrayList<>();

		for (int forward = 0; forward < DOCK_LENGTH_BLOCKS; forward++) {
			for (int right = -DOCK_HALF_WIDTH_BLOCKS; right <= DOCK_HALF_WIDTH_BLOCKS; right++) {
				blocks.add(SettlementBuildBlockState.pending(relativeBlueprintPosition(right, forward, 0), 'P', "planks"));

				if (isDockSupportRow(forward) && Math.abs(right) == DOCK_HALF_WIDTH_BLOCKS) {
					BlockPos deckPos = offset(site.origin(), site.facing(), right, forward, 0);
					BlockPos footing = findDockFooting(level, deckPos);

					if (footing == null) {
						continue;
					}

					for (int y = deckPos.getY() - 1; y > footing.getY(); y--) {
						blocks.add(SettlementBuildBlockState.pending(
							relativeBlueprintPosition(right, forward, y - site.origin().getY()),
							'L',
							"logs"
						));
					}
				}
			}
		}

		return new SettlementBuildSite(
			buildSiteId(settlementId, SettlementBuildSiteType.DOCK, anchorPos),
			settlementId,
			SettlementBuildSiteType.DOCK,
			site.origin().immutable(),
			anchorPos.immutable(),
			anchorPos.immutable(),
			site.facing(),
			palette.woodFamily(),
			"",
			Map.of(),
			blocks,
			false,
			tick,
			tick
		);
	}

	private static BlockState dockBuildState(char symbol, String woodFamily) {
		return switch (symbol) {
			case 'L' -> woodLogBlock(woodFamily).defaultBlockState();
			case 'P' -> woodPlankBlock(woodFamily).defaultBlockState();
			default -> null;
		};
	}

	private static BlockState palisadeWallBuildState(char symbol, String woodFamily) {
		return switch (symbol) {
			case 'L' -> woodLogBlock(woodFamily).defaultBlockState();
			case 'B' -> woodSlabBlock(woodFamily).defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
			case 'C' -> woodSlabBlock(woodFamily).defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
			default -> null;
		};
	}

	private static String dockWoodFamilyForSite(ServerLevel level, DockSite site) {
		BlockPos shorelinePos = site.origin().relative(site.facing().getOpposite());
		return materialPaletteFor(level, shorelinePos).woodFamily();
	}

	private static String dockMaterialKey(char symbol) {
		return switch (symbol) {
			case 'L' -> "logs";
			case 'P' -> "planks";
			default -> "";
		};
	}

	private static String palisadeWallMaterialKey(char symbol) {
		return switch (symbol) {
			case 'L' -> "logs";
			case 'B', 'C' -> "slab";
			default -> "";
		};
	}

	public static boolean isPalisadeWallBuildSite(SettlementBuildSite buildSite) {
		return buildSite.blueprintId() == SettlementBuildSiteType.PALISADE_WALL;
	}

	public static boolean isPalisadePointWallControlBlock(SettlementBuildSite buildSite, SettlementBuildBlockState block, BlockState currentState) {
		return isPalisadeWallBuildSite(buildSite)
			&& !block.blueprintSymbol().isBlank()
			&& block.blueprintSymbol().charAt(0) == 'L'
			&& currentState.is(LiveVillagesBlocks.PALISADE_POINT);
	}

	private static BlueprintRelativePos normalizedPalisadeWallRelativePos(
		SettlementBuildSite buildSite,
		SettlementBuildBlockState block,
		BlueprintRelativePos relativePos
	) {
		if (!isPalisadeWallBuildSite(buildSite)
			|| block.blueprintSymbol().isBlank()
			|| relativePos.up() <= 32) {
			return relativePos;
		}

		char symbol = block.blueprintSymbol().charAt(0);
		if (symbol != 'B' && symbol != 'C') {
			return relativePos;
		}

		int correctedUp = relativePos.up() - buildSite.origin().getY();
		if (correctedUp < -16 || correctedUp > 32) {
			return relativePos;
		}

		return new BlueprintRelativePos(relativePos.right(), relativePos.forward(), correctedUp);
	}

	public static boolean isPalisadeWallLogOverride(ServerLevel level, SettlementBuildSite buildSite, SettlementBuildBlockState block, BlockPos plannedPos) {
		char symbol = currentBlueprintSymbol(buildSite, block);
		if (!isPalisadeWallBuildSite(buildSite)
			|| (symbol != 'L' && symbol != 'B' && symbol != 'C')) {
			return false;
		}

		for (int dx = -PALISADE_PLAYER_OVERRIDE_RADIUS_BLOCKS; dx <= PALISADE_PLAYER_OVERRIDE_RADIUS_BLOCKS; dx++) {
			for (int dz = -PALISADE_PLAYER_OVERRIDE_RADIUS_BLOCKS; dz <= PALISADE_PLAYER_OVERRIDE_RADIUS_BLOCKS; dz++) {
				if (dx == 0 && dz == 0) {
					continue;
				}

				if (Math.max(Math.abs(dx), Math.abs(dz)) > PALISADE_PLAYER_OVERRIDE_RADIUS_BLOCKS) {
					continue;
				}

				BlockPos candidatePos = plannedPos.offset(dx, 0, dz);
				if (!level.hasChunkAt(candidatePos) || !isPalisadeLogColumnPart(level, candidatePos)) {
					continue;
				}

				return true;
			}
		}

		return false;
	}

	private static boolean isPalisadeLogColumnPart(ServerLevel level, BlockPos pos) {
		return isLogColumnAtLeast(level, pos, PALISADE_PLAYER_OVERRIDE_MIN_LOGS);
	}

	private static boolean isLogColumnAtLeast(ServerLevel level, BlockPos pos, int minimumLogs) {
		if (!isInTag(level.getBlockState(pos), BlockTags.LOGS)) {
			return false;
		}

		int logBlocks = 1;
		BlockPos scanPos = pos.below();
		while (scanPos.getY() >= level.getMinY() && isInTag(level.getBlockState(scanPos), BlockTags.LOGS)) {
			logBlocks++;
			scanPos = scanPos.below();
		}

		scanPos = pos.above();
		while (scanPos.getY() < level.getMaxY() && isInTag(level.getBlockState(scanPos), BlockTags.LOGS)) {
			logBlocks++;
			scanPos = scanPos.above();
		}

		return logBlocks >= minimumLogs;
	}

	private static boolean isDockSupportRow(int forward) {
		return forward % DOCK_SUPPORT_SPACING_BLOCKS == 0;
	}

	private static boolean isMinimalDock(ServerLevel level, BlockPos origin, Direction facing) {
		if (!isDockDeck(level.getBlockState(origin)) || isDockDeck(level.getBlockState(origin.relative(facing.getOpposite())))) {
			return false;
		}

		for (int forward = 0; forward < DOCK_LENGTH_BLOCKS; forward++) {
			for (int right = -DOCK_HALF_WIDTH_BLOCKS; right <= DOCK_HALF_WIDTH_BLOCKS; right++) {
				BlockPos deckPos = offset(origin, facing, right, forward, 0);

				if (!level.hasChunkAt(deckPos) || !isDockDeck(level.getBlockState(deckPos))) {
					return false;
				}

				BlockState belowState = level.getBlockState(deckPos.below());

				if (isDockSupportRow(forward) && Math.abs(right) == DOCK_HALF_WIDTH_BLOCKS) {
					if (!hasDockLogSupport(level, deckPos)) {
						return false;
					}
				} else if (!belowState.liquid() && !isDockSupportLog(belowState)) {
					return false;
				}
			}
		}

		return true;
	}

	private static boolean hasDockLogSupport(ServerLevel level, BlockPos deckPos) {
		int logBlocks = 0;
		BlockPos checkPos = deckPos.below();

		while (checkPos.getY() >= level.getMinY() && isDockSupportLog(level.getBlockState(checkPos))) {
			logBlocks++;
			checkPos = checkPos.below();
		}

		return logBlocks > 0 && hasStableBuildGround(level, checkPos);
	}

	private static boolean isDockDeck(BlockState state) {
		return isInTag(state, BlockTags.PLANKS);
	}

	private static boolean isDockSupportLog(BlockState state) {
		return isInTag(state, BlockTags.LOGS);
	}

	private static boolean isMinimalLighthouse(ServerLevel level, BlockPos campfirePos) {
		if (!level.getBlockState(campfirePos).is(Blocks.CAMPFIRE)) {
			return false;
		}

		BlockPos topBaseCenter = campfirePos.below(LIGHTHOUSE_TOWER_LEVELS + 1);

		for (int y = 0; y < LIGHTHOUSE_TOWER_LEVELS; y++) {
			if (!level.getBlockState(campfirePos.below(y + 1)).is(Blocks.COBBLESTONE)) {
				return false;
			}
		}

		for (int levelIndex = 0; levelIndex < LIGHTHOUSE_BASE_LEVELS; levelIndex++) {
			int y = topBaseCenter.getY() - levelIndex;

			for (int offsetX = -1; offsetX <= 1; offsetX++) {
				for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
					BlockPos blockPos = new BlockPos(topBaseCenter.getX() + offsetX, y, topBaseCenter.getZ() + offsetZ);
					BlockState state = level.getBlockState(blockPos);
					boolean markerBase = offsetX == 0
						&& offsetZ == 0
						&& levelIndex == LIGHTHOUSE_BASE_LEVELS - 1
						&& state.is(LiveVillagesBlocks.LIGHTHOUSE);

					if (!markerBase && !state.is(Blocks.COBBLESTONE)) {
						return false;
					}
				}
			}
		}

		return true;
	}

	private static Direction facingToward(BlockPos center, BlockPos pos) {
		int deltaX = center.getX() - pos.getX();
		int deltaZ = center.getZ() - pos.getZ();

		if (Math.abs(deltaX) > Math.abs(deltaZ)) {
			return deltaX >= 0 ? Direction.EAST : Direction.WEST;
		}

		return deltaZ >= 0 ? Direction.SOUTH : Direction.NORTH;
	}

	private static BlockPos offset(BlockPos origin, Direction facing, int right, int forward, int up) {
		return origin.relative(facing.getClockWise(), right).relative(facing, forward).above(up);
	}

	private static void placeOakDoor(ServerLevel level, BlockPos lowerPos, Direction facing) {
		level.setBlock(
			lowerPos,
			Blocks.OAK_DOOR.defaultBlockState()
				.setValue(DoorBlock.FACING, facing)
				.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER),
			BLOCK_UPDATE_FLAGS
		);
		level.setBlock(
			lowerPos.above(),
			Blocks.OAK_DOOR.defaultBlockState()
				.setValue(DoorBlock.FACING, facing)
				.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER),
			BLOCK_UPDATE_FLAGS
		);
	}

	private static void placeCantBuildHereSign(ServerLevel level, BlockPos workstationPos, Direction facing, String statusMessage) {
		for (Direction direction : new Direction[] {facing.getClockWise(), facing.getCounterClockWise(), facing, facing.getOpposite()}) {
			BlockPos signPos = workstationPos.relative(direction);

			if (updateExistingCantBuildHereSign(level, signPos, statusMessage)) {
				return;
			}
		}

		for (Direction direction : new Direction[] {facing.getClockWise(), facing.getCounterClockWise(), facing, facing.getOpposite()}) {
			BlockPos signPos = workstationPos.relative(direction);

			if (!level.hasChunkAt(signPos) || !isReplaceable(level.getBlockState(signPos)) || !level.getBlockState(signPos.below()).isSolid()) {
				continue;
			}

			BlockState signState = Blocks.OAK_SIGN.defaultBlockState()
				.setValue(StandingSignBlock.ROTATION, signRotationForFacing(direction.getOpposite()));
			level.setBlock(signPos, signState, BLOCK_UPDATE_FLAGS);

			if (level.getBlockEntity(signPos) instanceof SignBlockEntity signBlockEntity) {
				writeCantBuildHereSign(level, signPos, signState, signBlockEntity, statusMessage);
			}

			return;
		}
	}

	public static void removeCantBuildHereSignsAroundWorkstation(ServerLevel level, BlockPos workstationPos) {
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos signPos = workstationPos.relative(direction);

			if (!level.hasChunkAt(signPos) || !(level.getBlockState(signPos).getBlock() instanceof StandingSignBlock)) {
				continue;
			}

			if (level.getBlockEntity(signPos) instanceof SignBlockEntity signBlockEntity && isCantBuildHereSign(signBlockEntity)) {
				level.setBlock(signPos, Blocks.AIR.defaultBlockState(), BLOCK_UPDATE_FLAGS);
			}
		}
	}

	private static boolean updateExistingCantBuildHereSign(ServerLevel level, BlockPos signPos, String statusMessage) {
		if (!level.hasChunkAt(signPos) || !(level.getBlockState(signPos).getBlock() instanceof StandingSignBlock)) {
			return false;
		}

		if (!(level.getBlockEntity(signPos) instanceof SignBlockEntity signBlockEntity) || !isCantBuildHereSign(signBlockEntity)) {
			return false;
		}

		BlockState signState = level.getBlockState(signPos);
		writeCantBuildHereSign(level, signPos, signState, signBlockEntity, statusMessage);
		return true;
	}

	private static boolean isCantBuildHereSign(SignBlockEntity signBlockEntity) {
		for (boolean front : List.of(true, false)) {
			SignText text = signBlockEntity.getText(front);

			if (text.getMessage(0, false).getString().equalsIgnoreCase("can't build")) {
				return true;
			}
		}

		return false;
	}

	private static void writeCantBuildHereSign(ServerLevel level, BlockPos signPos, BlockState signState, SignBlockEntity signBlockEntity, String statusMessage) {
		List<String> lines = cantBuildHereSignLines(statusMessage);
		SignText text = new SignText()
			.setMessage(0, Component.literal(lines.get(0)))
			.setMessage(1, Component.literal(lines.get(1)))
			.setMessage(2, Component.literal(lines.get(2)))
			.setMessage(3, Component.literal(lines.get(3)));
		signBlockEntity.setText(text, true);
		signBlockEntity.setChanged();
		level.sendBlockUpdated(signPos, signState, signState, BLOCK_UPDATE_FLAGS);
	}

	private static List<String> cantBuildHereSignLines(String statusMessage) {
		String reason = cantBuildHereReason(statusMessage);
		return List.of("can't build", "blocked by", reason, "clear area");
	}

	private static String cantBuildHereReason(String statusMessage) {
		String normalized = statusMessage == null ? "" : statusMessage.toLowerCase();

		if (normalized.contains("unloaded")) {
			return "unloaded land";
		}

		if (normalized.contains("water") || normalized.contains("lava")) {
			return "fluid";
		}

		if (normalized.contains("garden") || normalized.contains("planting")) {
			return "garden";
		}

		if (normalized.contains("terrain") || normalized.contains("ground")) {
			return "terrain";
		}

		if (normalized.contains("clearing") || normalized.contains("fill")) {
			return "too much work";
		}

		if (normalized.contains("entry") || normalized.contains("approach")) {
			return "entry";
		}

		if (normalized.contains("blocked")) {
			return "blocks";
		}

		return "site";
	}

	public static boolean isPositionInExistingShelteredStructure(ServerLevel level, BlockPos pos) {
		if (!level.hasChunkAt(pos) || !isExistingStructureFloor(level, pos.below())) {
			return false;
		}

		if (!hasExistingStructureRoof(level, pos)) {
			return false;
		}

		int boundedDirections = 0;

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			if (hasExistingStructureBoundary(level, pos, direction)) {
				boundedDirections++;
			}
		}

		return boundedDirections >= 2;
	}

	private static boolean hasExistingStructureRoof(ServerLevel level, BlockPos workstationPos) {
		for (int dy = 2; dy <= 6; dy++) {
			BlockPos scanPos = workstationPos.above(dy);

			if (!level.hasChunkAt(scanPos)) {
				return false;
			}

			BlockState state = level.getBlockState(scanPos);
			if (state.isSolid() && isExistingStructureBlock(level, scanPos, state)) {
				return true;
			}
		}

		return false;
	}

	private static boolean hasExistingStructureBoundary(ServerLevel level, BlockPos workstationPos, Direction direction) {
		for (int distance = 1; distance <= 5; distance++) {
			BlockPos basePos = workstationPos.relative(direction, distance);

			for (int dy = 0; dy <= 2; dy++) {
				BlockPos scanPos = basePos.above(dy);

				if (!level.hasChunkAt(scanPos)) {
					return false;
				}

				BlockState state = level.getBlockState(scanPos);
				if (isExistingStructureBoundaryBlock(level, scanPos, state)) {
					return true;
				}
			}
		}

		return false;
	}

	private static boolean isExistingStructureFloor(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.isSolid() && isExistingStructureBlock(level, pos, state);
	}

	private static boolean isExistingStructureBoundaryBlock(ServerLevel level, BlockPos pos, BlockState state) {
		return isExistingStructureBlock(level, pos, state)
			&& (state.isSolid()
				|| state.getBlock() instanceof CrossCollisionBlock
				|| state.getBlock() instanceof DoorBlock
				|| state.getBlock() instanceof FenceGateBlock
				|| state.getBlock() instanceof TrapDoorBlock);
	}

	private static boolean isExistingStructureBlock(ServerLevel level, BlockPos pos, BlockState state) {
		return !isReplaceable(state)
			&& !state.liquid()
			&& !canLandscapeRemove(level, pos, state);
	}

	private static int signRotationForFacing(Direction facing) {
		return switch (facing) {
			case EAST -> 12;
			case NORTH -> 8;
			case WEST -> 4;
			default -> 0;
		};
	}

	private static boolean hasStableBuildGround(ServerLevel level, BlockPos groundPos) {
		BlockState groundState = level.getBlockState(groundPos);

		if (!groundState.isSolid() || !isBuildGroundSurface(groundState)) {
			return false;
		}

		return hasStableSubgrade(level, groundPos.below());
	}

	private static boolean hasStableSubgrade(ServerLevel level, BlockPos startPos) {
		BlockPos.MutableBlockPos scanPos = startPos.mutable();
		int removableDepth = 0;

		while (scanPos.getY() >= level.getMinY()) {
			BlockState scanState = level.getBlockState(scanPos);

			if (!scanState.isSolid()) {
				return false;
			}

			if (isBuildGroundSurface(scanState)) {
				return true;
			}

			if (!canLandscapeRemove(level, scanPos, scanState) || removableDepth >= MAX_TERRAIN_FILL_BLOCKS + 1) {
				return false;
			}

			removableDepth++;
			scanPos.move(Direction.DOWN);
		}

		return true;
	}

	private static BlockPos resolveStructureGroundPos(ServerLevel level, BlockPos columnPos) {
		int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, columnPos.getX(), columnPos.getZ()) - 1;

		if (groundY < level.getMinY() || groundY > level.getMaxY() - 1) {
			return null;
		}

		BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos(columnPos.getX(), groundY, columnPos.getZ());

		while (scanPos.getY() >= level.getMinY()) {
			BlockState state = level.getBlockState(scanPos);

			// Treat ordinary visible terrain as valid ground. Only drill downward through
			// removable clutter such as trees or brush, plus any empty space directly
			// beneath that clutter, until we reach the real terrain surface.
			if (state.isSolid() && isBuildGroundSurface(state)) {
				return scanPos.immutable();
			}

			if (isReplaceable(state) || canLandscapeRemove(level, scanPos, state)) {
				scanPos.move(Direction.DOWN);
				continue;
			}

			return scanPos.immutable();
		}

		return null;
	}

	private static boolean canAfford(Map<String, Integer> stock, Map<String, Integer> cost) {
		for (Map.Entry<String, Integer> entry : cost.entrySet()) {
			if (stock.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
				return false;
			}
		}

		return true;
	}

	private static void consumeCost(Map<String, Integer> stock, Map<String, Integer> cost) {
		for (Map.Entry<String, Integer> entry : cost.entrySet()) {
			stock.put(entry.getKey(), stock.getOrDefault(entry.getKey(), 0) - entry.getValue());
		}
	}

	private static boolean isReplaceable(BlockState state) {
		return state.isAir() || state.canBeReplaced();
	}

	private static boolean canLandscapeRemove(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.isAir() || state.liquid() || state.hasBlockEntity() || level.getBlockEntity(pos) != null || isStructuredGardenBlock(level, pos, state)) {
			return false;
		}

		return isNaturalLandscapeBlock(state);
	}

	private static boolean canClearForConstruction(ServerLevel level, BlockPos pos, BlockState state) {
		if (isShovelPathSurface(state)) {
			return true;
		}

		return canLandscapeRemove(level, pos, state) || canRecoverConstructionBlock(level, pos, state);
	}

	private static boolean canRecoverConstructionBlock(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.isAir() || state.liquid()) {
			return false;
		}

		if (isDisposableConstructionAnchorBlock(state)) {
			return true;
		}

		if (recoveredGoodsKey(state) == null) {
			return false;
		}

		return isRecoverableWorkstationBlock(state)
			|| (!state.hasBlockEntity() && level.getBlockEntity(pos) == null);
	}

	private static void clearBlockToWarehouse(ServerLevel level, BlockPos pos, Map<String, Integer> stock) {
		BlockState state = level.getBlockState(pos);

		if (state.isAir()) {
			return;
		}

		if (isConstructionTreeBlock(state) && isNaturalTreeCluster(level, pos)) {
			clearNaturalTreeToWarehouse(level, pos, stock);
			return;
		}

		String goodsKey = recoveredGoodsKey(state);

		if (goodsKey != null) {
			stock.merge(goodsKey, 1, Integer::sum);
		}

		level.setBlock(pos, Blocks.AIR.defaultBlockState(), BLOCK_UPDATE_FLAGS);
	}

	private static void clearNaturalTreeToWarehouse(ServerLevel level, BlockPos startPos, Map<String, Integer> stock) {
		Set<BlockPos> treeBlocks = connectedNaturalTreeBlocks(level, startPos);

		if (treeBlocks.isEmpty()) {
			return;
		}

		for (BlockPos treePos : treeBlocks) {
			BlockState treeState = level.getBlockState(treePos);

			if (!isConstructionTreeBlock(treeState)) {
				continue;
			}

			String goodsKey = recoveredGoodsKey(treeState);

			if (goodsKey != null) {
				stock.merge(goodsKey, 1, Integer::sum);
			}

			level.setBlock(treePos, Blocks.AIR.defaultBlockState(), BLOCK_UPDATE_FLAGS);
		}
	}

	private static Set<BlockPos> connectedNaturalTreeBlocks(ServerLevel level, BlockPos startPos) {
		Set<BlockPos> visited = new LinkedHashSet<>();
		ArrayDeque<BlockPos> open = new ArrayDeque<>();
		open.add(startPos.immutable());

		while (!open.isEmpty() && visited.size() < MAX_CONSTRUCTION_TREE_BLOCKS) {
			BlockPos current = open.removeFirst();

			if (visited.contains(current) || current.distSqr(startPos) > 196.0D) {
				continue;
			}

			BlockState currentState = level.getBlockState(current);

			if (!isConstructionTreeBlock(currentState)) {
				continue;
			}

			visited.add(current.immutable());

			for (Direction direction : Direction.values()) {
				open.add(current.relative(direction));
			}
		}

		return visited;
	}

	private static boolean isNaturalTreeCluster(ServerLevel level, BlockPos startPos) {
		Set<BlockPos> treeBlocks = connectedNaturalTreeBlocks(level, startPos);

		if (treeBlocks.isEmpty()) {
			return false;
		}

		boolean hasLeaves = false;
		boolean hasNaturalRoot = false;

		for (BlockPos treePos : treeBlocks) {
			BlockState treeState = level.getBlockState(treePos);

			if (isInTag(treeState, BlockTags.LEAVES)) {
				hasLeaves = true;
			}

			if (isInTag(treeState, BlockTags.LOGS) && isBuildGroundSurface(level.getBlockState(treePos.below()))) {
				hasNaturalRoot = true;
			}

			if (hasLeaves && hasNaturalRoot) {
				return true;
			}
		}

		return false;
	}

	private static String recoveredGoodsKey(BlockState state) {
		if (state.is(Blocks.CAMPFIRE)) {
			return "campfire";
		}

		if (state.is(Blocks.COBBLESTONE)
			|| state.is(Blocks.MOSSY_COBBLESTONE)
			|| state.is(Blocks.STONE)
			|| state.is(Blocks.SMOOTH_STONE)
			|| state.is(Blocks.GRANITE)
			|| state.is(Blocks.DIORITE)
			|| state.is(Blocks.ANDESITE)
			|| state.is(Blocks.TUFF)
			|| state.is(Blocks.CALCITE)
			|| state.is(Blocks.DEEPSLATE)
			|| state.is(Blocks.COBBLED_DEEPSLATE)
			|| isInTag(state, BlockTags.BASE_STONE_OVERWORLD)) {
			return "cobblestone";
		}

		if (state.is(Blocks.IRON_ORE) || state.is(Blocks.DEEPSLATE_IRON_ORE) || isInTag(state, BlockTags.IRON_ORES)) {
			return "iron_ingot";
		}

		if (state.is(Blocks.COPPER_ORE) || state.is(Blocks.DEEPSLATE_COPPER_ORE) || isInTag(state, BlockTags.COPPER_ORES)) {
			return "copper_ingot";
		}

		if (state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE) || isInTag(state, BlockTags.COAL_ORES)) {
			return "coal";
		}

		if (state.is(Blocks.REDSTONE_ORE) || state.is(Blocks.DEEPSLATE_REDSTONE_ORE) || isInTag(state, BlockTags.REDSTONE_ORES)) {
			return "redstone";
		}

		if (state.is(Blocks.LAPIS_ORE) || state.is(Blocks.DEEPSLATE_LAPIS_ORE) || isInTag(state, BlockTags.LAPIS_ORES)) {
			return "lapis";
		}

		if (state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE) || isInTag(state, BlockTags.DIAMOND_ORES)) {
			return "diamond";
		}

		if (state.is(Blocks.EMERALD_ORE) || state.is(Blocks.DEEPSLATE_EMERALD_ORE) || isInTag(state, BlockTags.EMERALD_ORES)) {
			return "emerald";
		}

		if (state.is(Blocks.GOLD_ORE) || state.is(Blocks.DEEPSLATE_GOLD_ORE) || isInTag(state, BlockTags.GOLD_ORES)) {
			return "raw_gold";
		}

		if (isInTag(state, BlockTags.LOGS)) {
			return "logs";
		}

		if (isInTag(state, BlockTags.PLANKS)
			|| isInTag(state, BlockTags.WOODEN_SLABS)
			|| isInTag(state, BlockTags.WOODEN_STAIRS)
			|| isInTag(state, BlockTags.WOODEN_FENCES)
			|| isInTag(state, BlockTags.FENCE_GATES)
			|| isInTag(state, BlockTags.WOODEN_DOORS)) {
			return "planks";
		}

		if (state.is(Blocks.GLASS) || state.is(Blocks.GLASS_PANE)) {
			return "glass";
		}

		if (state.is(Blocks.LANTERN) || state.is(Blocks.COPPER_LANTERN.unaffected())) {
			return "lantern";
		}

		if (state.is(Blocks.IRON_BARS)) {
			return "iron_bars";
		}

		if (isCopperBars(state)) {
			return "copper_bars";
		}

		if (state.is(Blocks.CHEST)) {
			return "chest";
		}

		if (state.is(Blocks.CARTOGRAPHY_TABLE)) {
			return "cartography_table";
		}

		if (state.is(Blocks.FLETCHING_TABLE)) {
			return "fletching_table";
		}

		if (state.is(Blocks.SMOKER)) {
			return "smoker";
		}

		if (state.is(Blocks.STONECUTTER)) {
			return "stonecutter";
		}

		if (state.is(LiveVillagesBlocks.TRADE_BOARD)) {
			return "trade_board";
		}

		if (state.is(LiveVillagesBlocks.CARPENTER_BENCH)) {
			return "carpenter_bench";
		}

		if (state.is(LiveVillagesBlocks.SCRIBE_DESK)) {
			return "scribe_desk";
		}

		if (state.is(LiveVillagesBlocks.GUARD_POST)) {
			return "guard_post";
		}

		if (state.is(LiveVillagesBlocks.GARDENER_WORKSTATION)) {
			return "gardener_workstation";
		}

		if (state.is(LiveVillagesBlocks.HONEY_SEPARATOR)) {
			return "honey_separator";
		}

		if (state.is(LiveVillagesBlocks.SURVEYOR_TABLE)) {
			return "surveyor_table";
		}

		if (state.is(LiveVillagesBlocks.FORESTER_TABLE)) {
			return "forester_table";
		}

		if (state.is(LiveVillagesBlocks.BAKERS_COUNTER)) {
			return "bakers_counter";
		}

		if (state.is(LiveVillagesBlocks.MINER_WORKSTATION)) {
			return "miner_workstation";
		}

		if (isInTag(state, BlockTags.WOOL)) {
			return "wool";
		}

		if (state.getBlock() instanceof BedBlock) {
			return "bed";
		}

		if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH) || state.is(Blocks.SOUL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH)) {
			return "torch";
		}

		if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.ROOTED_DIRT) || isInTag(state, BlockTags.DIRT)) {
			return "dirt";
		}

		if (state.is(Blocks.GRAVEL)) {
			return "gravel";
		}

		if (isInTag(state, BlockTags.SAND) || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE)) {
			return "sand";
		}

		if (isInTag(state, BlockTags.LEAVES)) {
			return "leaves";
		}

		return null;
	}

	private static boolean isRecoverableWorkstationBlock(BlockState state) {
		return state.is(Blocks.CARTOGRAPHY_TABLE)
			|| state.is(Blocks.FLETCHING_TABLE)
			|| state.is(Blocks.SMOKER)
			|| state.is(Blocks.STONECUTTER)
			|| state.is(LiveVillagesBlocks.BAKERS_COUNTER)
			|| state.is(LiveVillagesBlocks.TRADE_BOARD)
			|| state.is(LiveVillagesBlocks.CARPENTER_BENCH)
			|| state.is(LiveVillagesBlocks.SCRIBE_DESK)
			|| state.is(LiveVillagesBlocks.GUARD_POST)
			|| state.is(LiveVillagesBlocks.GARDENER_WORKSTATION)
			|| state.is(LiveVillagesBlocks.HONEY_SEPARATOR)
			|| state.is(LiveVillagesBlocks.MINER_WORKSTATION)
			|| state.is(LiveVillagesBlocks.SURVEYOR_TABLE)
			|| state.is(LiveVillagesBlocks.FORESTER_TABLE);
	}

	private static boolean isDisposableConstructionAnchorBlock(BlockState state) {
		return state.getBlock() instanceof ShelterAnchorBlock
			|| state.getBlock() instanceof PalisadeGatehouseBlock;
	}

	private static boolean isNaturalLandscapeBlock(BlockState state) {
		return isInTag(state, BlockTags.BASE_STONE_OVERWORLD)
			|| isInTag(state, BlockTags.IRON_ORES)
			|| isInTag(state, BlockTags.LOGS)
			|| isInTag(state, BlockTags.LEAVES)
			|| isDecorativePlant(state)
			|| isInTag(state, BlockTags.DIRT)
			|| isInTag(state, BlockTags.SAND)
			|| state.is(Blocks.GRASS_BLOCK)
			|| state.is(Blocks.DIRT)
			|| state.is(Blocks.COARSE_DIRT)
			|| state.is(Blocks.ROOTED_DIRT)
			|| state.is(Blocks.GRAVEL)
			|| state.is(Blocks.RED_MUSHROOM)
			|| state.is(Blocks.BROWN_MUSHROOM)
			|| state.is(Blocks.RED_MUSHROOM_BLOCK)
			|| state.is(Blocks.BROWN_MUSHROOM_BLOCK)
			|| state.is(Blocks.MUSHROOM_STEM);
	}

	private static boolean isNaturalOreBlock(BlockState state) {
		return isInTag(state, BlockTags.COAL_ORES)
			|| isInTag(state, BlockTags.COPPER_ORES)
			|| isInTag(state, BlockTags.DIAMOND_ORES)
			|| isInTag(state, BlockTags.EMERALD_ORES)
			|| isInTag(state, BlockTags.GOLD_ORES)
			|| isInTag(state, BlockTags.IRON_ORES)
			|| isInTag(state, BlockTags.LAPIS_ORES)
			|| isInTag(state, BlockTags.REDSTONE_ORES);
	}

	private static boolean isConstructionTreeBlock(BlockState state) {
		return isInTag(state, BlockTags.LOGS) || isInTag(state, BlockTags.LEAVES);
	}

	private static boolean isBuildGroundSurface(BlockState state) {
		return isInTag(state, BlockTags.BASE_STONE_OVERWORLD)
			|| isNaturalOreBlock(state)
			|| isInTag(state, BlockTags.DIRT)
			|| isInTag(state, BlockTags.SAND)
			|| state.is(Blocks.GRASS_BLOCK)
			|| state.is(Blocks.DIRT)
			|| state.is(Blocks.COARSE_DIRT)
			|| state.is(Blocks.ROOTED_DIRT)
			|| state.is(Blocks.GRAVEL)
			|| state.is(Blocks.CLAY)
			|| state.is(Blocks.MOSS_BLOCK)
			|| state.is(Blocks.PODZOL)
			|| state.is(Blocks.MYCELIUM)
			|| state.is(Blocks.MUD)
			|| state.is(Blocks.PACKED_MUD)
			|| state.is(Blocks.DIRT_PATH);
	}

	private static boolean isInTag(BlockState state, TagKey<Block> tag) {
		return state.is(tag, blockState -> true);
	}

	private static boolean isCopperBars(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().endsWith("copper_bars");
	}

	private static boolean isShovelPathSurface(BlockState state) {
		return state.is(Blocks.DIRT_PATH);
	}

	private static boolean isImprovedRouteSurface(BlockState state) {
		return state.is(Blocks.COBBLESTONE)
			|| state.is(Blocks.SMOOTH_STONE)
			|| state.is(Blocks.POLISHED_GRANITE)
			|| state.is(Blocks.POLISHED_DIORITE)
			|| state.is(Blocks.POLISHED_ANDESITE)
			|| state.is(Blocks.STONE_BRICKS)
			|| state.is(Blocks.BRICKS)
			|| isImprovedRouteSlope(state);
	}

	private static boolean isImprovedRouteSlope(BlockState state) {
		return state.is(Blocks.COBBLESTONE_STAIRS)
			|| state.is(Blocks.COBBLESTONE_SLAB)
			|| state.is(Blocks.STONE_STAIRS)
			|| state.is(Blocks.STONE_SLAB)
			|| state.is(Blocks.STONE_BRICK_STAIRS)
			|| state.is(Blocks.STONE_BRICK_SLAB)
			|| state.is(Blocks.BRICK_STAIRS)
			|| state.is(Blocks.BRICK_SLAB)
			|| state.is(Blocks.SMOOTH_STONE_SLAB);
	}

	private static boolean isRouteFloorReplacementSurface(BlockState state) {
		return state.is(Blocks.COBBLESTONE)
			|| state.is(Blocks.SMOOTH_STONE)
			|| state.is(Blocks.POLISHED_GRANITE)
			|| state.is(Blocks.POLISHED_DIORITE)
			|| state.is(Blocks.POLISHED_ANDESITE)
			|| state.is(Blocks.STONE_BRICKS)
			|| state.is(Blocks.BRICKS)
			|| state.is(Blocks.DIRT_PATH);
	}

	private static BlockState structureMarginGroundReplacementState(ServerLevel level, BlockPos pos) {
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockState neighborState = level.getBlockState(pos.relative(direction));

			if (neighborState.is(Blocks.DIRT_PATH)) {
				return Blocks.DIRT_PATH.defaultBlockState();
			}
		}

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockState neighborState = level.getBlockState(pos.relative(direction));

			if (isRouteFloorReplacementSurface(neighborState) && !neighborState.is(Blocks.DIRT_PATH)) {
				return neighborState.getBlock().defaultBlockState();
			}
		}

		return Blocks.DIRT.defaultBlockState();
	}

	private static boolean isStructuredGardenBlock(ServerLevel level, BlockPos pos, BlockState state) {
		return isPottedDecorativePlant(state) || isBorderedGardenPlant(level, pos, state) || isBorderedGardenSupport(level, pos, state);
	}

	private static boolean isBorderedGardenPlant(ServerLevel level, BlockPos pos, BlockState state) {
		return isDecorativePlant(state) && isBorderedGardenSupport(level, pos.below(), level.getBlockState(pos.below()));
	}

	private static boolean isBorderedGardenSupport(ServerLevel level, BlockPos pos, BlockState state) {
		if (!isGardenSoil(state)) {
			return false;
		}

		if (!isDecorativePlant(level.getBlockState(pos.above()))) {
			return false;
		}

		int borderBlocks = 0;

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockState neighborState = level.getBlockState(pos.relative(direction));

			if (neighborState.getBlock() instanceof TrapDoorBlock || isInTag(neighborState, BlockTags.LOGS)) {
				borderBlocks++;
			}
		}

		return borderBlocks >= 2;
	}

	private static boolean isGardenSoil(BlockState state) {
		return state.is(Blocks.FARMLAND)
			|| state.is(Blocks.DIRT)
			|| state.is(Blocks.GRASS_BLOCK)
			|| state.is(Blocks.COARSE_DIRT)
			|| state.is(Blocks.ROOTED_DIRT);
	}

	private static boolean isDecorativePlant(BlockState state) {
		return isInTag(state, BlockTags.FLOWERS)
			|| state.is(Blocks.SHORT_GRASS)
			|| state.is(Blocks.FERN)
			|| state.is(Blocks.TALL_GRASS)
			|| state.is(Blocks.LARGE_FERN)
			|| state.is(Blocks.DEAD_BUSH);
	}

	private static boolean isPottedDecorativePlant(BlockState state) {
		return state.getBlock() instanceof FlowerPotBlock || state.is(Blocks.DECORATED_POT);
	}

	private static boolean hasStructureSpacingClearance(
		ServerLevel level,
		BlockPos origin,
		Direction facing,
		int minRight,
		int maxRight,
		int minForward,
		int maxForward,
		int clearHeight,
		Set<BlockPos> protectedPositions
	) {
		return evaluateStructureSpacingClearance(level, origin, facing, minRight, maxRight, minForward, maxForward, clearHeight, protectedPositions).valid();
	}

	private static PlacementPreviewResult evaluateStructureSpacingClearance(
		ServerLevel level,
		BlockPos origin,
		Direction facing,
		int minRight,
		int maxRight,
		int minForward,
		int maxForward,
		int clearHeight,
		Set<BlockPos> protectedPositions
	) {
		for (int right = minRight - MIN_STRUCTURE_SPACING_BLOCKS; right <= maxRight + MIN_STRUCTURE_SPACING_BLOCKS; right++) {
			for (int forward = minForward - MIN_STRUCTURE_SPACING_BLOCKS; forward <= maxForward + MIN_STRUCTURE_SPACING_BLOCKS; forward++) {
				if (right >= minRight && right <= maxRight && forward >= minForward && forward <= maxForward) {
					continue;
				}

				BlockPos columnPos = offset(origin, facing, right, forward, 0);
				if (!level.hasChunkAt(columnPos)) {
					return PlacementPreviewResult.invalid("Site extends into unloaded terrain.", columnPos);
				}

				int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, columnPos.getX(), columnPos.getZ()) - 1;
				int minY = Math.max(level.getMinY(), Math.min(origin.getY(), groundY));
				int maxY = Math.min(level.getMaxY() - 1, Math.max(origin.getY() + clearHeight, groundY + clearHeight));

				for (int y = minY; y <= maxY; y++) {
					BlockPos checkPos = new BlockPos(columnPos.getX(), y, columnPos.getZ());
					if (protectedPositions.contains(checkPos)) {
						continue;
					}

					if (isStructureSpacingObstacle(level, checkPos, groundY)) {
						return PlacementPreviewResult.invalid(structureSpacingFailureMessage(level, checkPos), checkPos);
					}
				}
			}
		}

		return PlacementPreviewResult.valid(null);
	}

	private static boolean isStructureSpacingObstacle(ServerLevel level, BlockPos pos, int groundY) {
		BlockState state = level.getBlockState(pos);

		if (isStructuredGardenBlock(level, pos, state)) {
			return true;
		}

		if (state.liquid()) {
			return true;
		}

		if (isShovelPathSurface(state) || isImprovedRouteSurface(state)) {
			return false;
		}

		if (isReplaceable(state)) {
			return false;
		}

		if (pos.getY() <= groundY && isBuildGroundSurface(state)) {
			return false;
		}

		return !canLandscapeRemove(level, pos, state);
	}

	private static boolean isAllowedFootprintBaseBlock(
		ServerLevel level,
		BlockPos pos,
		BlockState state,
		StructureKind structureKind,
		char baseSymbol
	) {
		if (isStructuredGardenBlock(level, pos, state)) {
			return false;
		}

		if (isShovelPathSurface(state)) {
			return true;
		}

		if (isImprovedRouteSurface(state)) {
			return false;
		}

		if (canLandscapeRemove(level, pos, state)) {
			return true;
		}

		if ((baseSymbol == 'A' || baseSymbol == 'E') && isBuildGroundSurface(state)) {
			return true;
		}

		if (structureKind == StructureKind.MINE_ENTRANCE && (baseSymbol == 'M' || baseSymbol == 'C') && isMineEntranceIntegratedStone(state)) {
			return true;
		}

		return isBuildGroundSurface(state);
	}

	private static boolean isAllowedFootprintFillBlock(ServerLevel level, BlockPos pos, BlockState state) {
		if (isStructuredGardenBlock(level, pos, state) || isImprovedRouteSurface(state)) {
			return false;
		}

		if (isReplaceable(state) || isShovelPathSurface(state)) {
			return true;
		}

		return canLandscapeRemove(level, pos, state);
	}

	private static String structureSpacingFailureMessage(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);

		if (isStructuredGardenBlock(level, pos, state)) {
			return "Blocked by an existing structured garden or decorative planting within the 3-block clearance.";
		}

		if (state.liquid()) {
			return "Blocked by water or lava within the 3-block clearance.";
		}

		return "Blocked by existing " + blockingBlockName(state) + " within the 3-block clearance.";
	}

	private static String footprintFillFailureMessage(ServerLevel level, BlockPos pos, BlockState state) {
		if (isStructuredGardenBlock(level, pos, state)) {
			return "Blocked by an existing structured garden or decorative planting inside the structure footprint.";
		}

		if (isImprovedRouteSurface(state)) {
			return "Blocked by an existing improved road surface inside the structure footprint.";
		}

		return "Blocked by " + blockingBlockName(state) + " inside the structure footprint.";
	}

	private static String footprintBaseFailureMessage(ServerLevel level, BlockPos pos, BlockState state) {
		if (isStructuredGardenBlock(level, pos, state)) {
			return "Blocked by an existing structured garden or decorative planting inside the structure footprint.";
		}

		if (isImprovedRouteSurface(state)) {
			return "Blocked by an existing improved road surface inside the structure footprint.";
		}

		return "Ground block " + blockingBlockName(state) + " cannot support this structure footprint.";
	}

	private static String clearanceFailureMessage(ServerLevel level, BlockPos pos, BlockState state) {
		if (isStructuredGardenBlock(level, pos, state)) {
			return "Blocked by an existing structured garden or decorative planting.";
		}

		if (state.liquid()) {
			return "Blocked by water or lava inside the required structure volume.";
		}

		return "Blocked by " + blockingBlockName(state) + " inside the required structure volume.";
	}

	private static String blockingBlockName(BlockState state) {
		return state.getBlock().getName().getString();
	}

	private static boolean hasMineEntranceFrontAccessClearance(
		ServerLevel level,
		BlockPos origin,
		Direction facing,
		Set<BlockPos> protectedPositions
	) {
		return evaluateMineEntranceFrontAccessClearance(level, origin, facing, protectedPositions).valid();
	}

	private static PlacementPreviewResult evaluateMineEntranceFrontAccessClearance(
		ServerLevel level,
		BlockPos origin,
		Direction facing,
		Set<BlockPos> protectedPositions
	) {
		for (int forward = MINE_ENTRANCE_BLUEPRINT.maxForward() + 1; forward <= MINE_ENTRANCE_BLUEPRINT.maxForward() + MINE_ENTRANCE_FRONT_ACCESS_DEPTH_BLOCKS; forward++) {
			for (int right = 0; right <= 1; right++) {
				for (int up = 1; up <= MINE_ENTRANCE_FRONT_ACCESS_HEIGHT_BLOCKS; up++) {
					BlockPos checkPos = offset(origin, facing, right, forward, up);

					if (protectedPositions.contains(checkPos)) {
						continue;
					}

					if (!level.hasChunkAt(checkPos)) {
						return PlacementPreviewResult.invalid("Mine Entrance approach extends into unloaded terrain.", checkPos);
					}

					BlockState state = level.getBlockState(checkPos);

					if (state.isAir()) {
						continue;
					}

					if (!canClearForConstruction(level, checkPos, state)) {
						return PlacementPreviewResult.invalid("Mine Entrance needs a clear front entry; blocked by " + blockingBlockName(state) + ".", checkPos);
					}
				}
			}
		}

		return PlacementPreviewResult.valid(null);
	}

	private static int buildRadius(SettlementState settlement) {
		return switch (settlement.kind()) {
			case VILLAGE, HARBOR, CUSTOM -> VILLAGE_BUILD_RADIUS_BLOCKS;
			case OUTPOST -> STANDARD_BUILD_RADIUS_BLOCKS;
		};
	}

	public static int buildRadiusBlocks(SettlementState settlement) {
		return buildRadius(settlement);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	public record CompletionResult(boolean completed, int housingCapacityDelta) {
		public static CompletionResult completed(int housingCapacityDelta) {
			return new CompletionResult(true, housingCapacityDelta);
		}

		public static CompletionResult notCompleted() {
			return new CompletionResult(false, 0);
		}
	}

	public record WorkstationBuildResult(boolean isStarted, boolean isBlocked, boolean isResumed, boolean isCompleted, SettlementBuildSite buildSite) {
		public static WorkstationBuildResult started(SettlementBuildSite buildSite) {
			return new WorkstationBuildResult(true, false, false, false, buildSite);
		}

		public static WorkstationBuildResult resumed(SettlementBuildSite buildSite) {
			return new WorkstationBuildResult(false, false, true, false, buildSite);
		}

		public static WorkstationBuildResult blocked() {
			return new WorkstationBuildResult(false, true, false, false, null);
		}

		public static WorkstationBuildResult completed() {
			return new WorkstationBuildResult(false, false, false, true, null);
		}
	}

	public record TradeBoardPlacementDecision(
		Optional<SettlementState> settlement,
		boolean foundsNewSettlement,
		boolean blocked,
		String statusMessage
	) {
		public TradeBoardPlacementDecision {
			settlement = settlement == null ? Optional.empty() : settlement;
			statusMessage = statusMessage == null ? "" : statusMessage;
		}

		public static TradeBoardPlacementDecision linked(SettlementState settlement, String statusMessage) {
			return new TradeBoardPlacementDecision(Optional.of(settlement), false, false, statusMessage);
		}

		public static TradeBoardPlacementDecision founding(String statusMessage) {
			return new TradeBoardPlacementDecision(Optional.empty(), true, false, statusMessage);
		}

		public static TradeBoardPlacementDecision blocked(String statusMessage) {
			return new TradeBoardPlacementDecision(Optional.empty(), false, true, statusMessage);
		}
	}

	public record StructurePreview(
		String previewId,
		String previewType,
		boolean placementValid,
		String statusMessage,
		List<BlockPos> blockerPositions,
		List<StructurePreviewBlock> blocks
	) {
		public StructurePreview {
			statusMessage = statusMessage == null ? "" : statusMessage;
			blockerPositions = blockerPositions.stream().map(BlockPos::immutable).toList();
			blocks = List.copyOf(blocks);
		}
	}

	public record StructurePreviewBlock(BlockPos pos, String materialKey, String blockId) {
		public StructurePreviewBlock {
			pos = pos.immutable();
		}
	}

	private record PlacementPreviewResult(AnchoredStructureSite site, boolean valid, String statusMessage, List<BlockPos> blockerPositions) {
		private PlacementPreviewResult {
			statusMessage = statusMessage == null ? "" : statusMessage;
			blockerPositions = blockerPositions.stream().map(BlockPos::immutable).toList();
		}

		private static PlacementPreviewResult valid(AnchoredStructureSite site) {
			return new PlacementPreviewResult(site, true, "", List.of());
		}

		private static PlacementPreviewResult invalid(String statusMessage, BlockPos blockerPos) {
			return new PlacementPreviewResult(null, false, statusMessage, blockerPos == null ? List.of() : List.of(blockerPos.immutable()));
		}
	}

	public record InfrastructureSurvey(
		boolean available,
		int housingCapacity,
		int composters,
		int storageBlocks,
		int carpenterBenches,
		int docks,
		int lighthouses,
		int waterSurfaceColumns,
		int deepWaterColumns,
		int incompleteDocks,
		int incompleteLighthouses,
		int tradingPosts,
		int incompleteTradingPosts,
		int incompleteCarpenterWorkshops,
		int palisadeGatehouses,
		int palisadeWallColumns,
		int expectedPalisadeWallColumns
	) {
		public static InfrastructureSurvey empty() {
			return new InfrastructureSurvey(false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1);
		}

		public boolean hasLargeWaterBody() {
			int dockFootprintColumns = docks * DOCK_LENGTH_BLOCKS * (DOCK_HALF_WIDTH_BLOCKS * 2 + 1);
			int adjustedSurfaceColumns = waterSurfaceColumns + dockFootprintColumns;
			int adjustedDeepWaterColumns = deepWaterColumns + docks * DOCK_LENGTH_BLOCKS;
			return adjustedSurfaceColumns >= MIN_HARBOR_WATER_SURFACE_COLUMNS && adjustedDeepWaterColumns >= MIN_HARBOR_DEEP_WATER_COLUMNS;
		}

		public double palisadeCoverage() {
			return expectedPalisadeWallColumns <= 0 ? 0.0D : Math.min(1.0D, palisadeWallColumns / (double) expectedPalisadeWallColumns);
		}
	}

	private record HousingSite(BlockPos origin, Direction facing) {
	}

	private record AnchoredStructureSite(BlockPos origin, Direction facing) {
	}

	private record BuildSiteInfrastructure(
		Set<BlockPos> incompleteCarpenterWorkshopWorkstations,
		int completedDocks,
		int completedLighthouses,
		int completedTradingPosts,
		int incompleteDocks,
		int incompleteLighthouses,
		int incompleteTradingPosts,
		int incompleteCarpenterWorkshops,
		int completedPalisadeGatehouses,
		int completedPalisadeWallColumns,
		int expectedPalisadeWallColumns
	) {
	}

	private record BlueprintRelativePos(int right, int forward, int up) {
	}

	private record BlueprintRelativeStep(int right, int forward) {
	}

	private record StructureBlueprint(int minRight, int minForward, int minUp, int clearHeight, String[][] layers, String[][] orientations) {
		private StructureBlueprint(int minRight, int minForward, int clearHeight, String[][] layers) {
			this(minRight, minForward, 0, clearHeight, layers, null);
		}

		private StructureBlueprint(int minRight, int minForward, int clearHeight, String[][] layers, String[][] orientations) {
			this(minRight, minForward, 0, clearHeight, layers, orientations);
		}

		private StructureBlueprint(int minRight, int minForward, int minUp, int clearHeight, String[][] layers) {
			this(minRight, minForward, minUp, clearHeight, layers, null);
		}

		private int maxRight() {
			return minRight + layers[0][0].length() - 1;
		}

		private int maxForward() {
			return minForward + layers[0].length - 1;
		}
	}

	private enum StructureKind {
		BAKERY,
		BEEKEEPER_APIARY,
		BUTCHER_SHOP,
		CARTOGRAPHER_HOUSE,
		CARPENTER_WORKSHOP,
		CLERIC_SHRINE,
		DOCK,
		FLETCHER_HUT,
		FORESTER_WORKSHOP,
		HOUSING_SHELTER,
		LEATHERWORKER_WORKSHOP,
		LIGHTHOUSE,
		LIBRARY,
		MASON_WORKSHOP,
		MINE_ENTRANCE,
		PALISADE_GATEHOUSE,
		COPPER_PALISADE_GATEHOUSE,
		ROADWRIGHT_WORKSHOP,
		SCRIBE_OFFICE,
		GARDENER_SHED,
		GUARD_POST,
		SHEPHERD_HUT,
		SIMPLE_HOUSING_SHELTER,
		SMITHY,
		TRADING_POST
	}

	private record PalisadeControlPoint(BlockPos pos) {
		private PalisadeControlPoint {
			pos = pos.immutable();
		}
	}

	public record PalisadeWallReplan(List<SettlementBuildSite> plannedSites, Set<String> obsoleteBuildSiteIds) {
		public static PalisadeWallReplan empty() {
			return new PalisadeWallReplan(List.of(), Set.of());
		}

		public PalisadeWallReplan {
			plannedSites = plannedSites == null ? List.of() : List.copyOf(plannedSites);
			obsoleteBuildSiteIds = obsoleteBuildSiteIds == null ? Set.of() : Set.copyOf(obsoleteBuildSiteIds);
		}

		public boolean emptyPlan() {
			return plannedSites.isEmpty() && obsoleteBuildSiteIds.isEmpty();
		}
	}

	private record PalisadeWallColumn(BlockPos basePos, BlueprintRelativeStep inwardStep) {
		private PalisadeWallColumn {
			basePos = basePos.immutable();
		}
	}

	private record StorageSite(BlockPos pos, Direction facing) {
	}

	private record DockSite(BlockPos origin, Direction facing) {
	}

	private record LighthouseSite(BlockPos baseCenter) {
	}

	private record WaterColumnSurvey(int surfaceY, int depth) {
		private static WaterColumnSurvey empty() {
			return new WaterColumnSurvey(-1, 0);
		}
	}
}
