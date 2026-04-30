package com.ronhelwig.livevillages.sim;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;

import com.ronhelwig.livevillages.block.PortmasterAnchorBlock;
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
	private static final int MIN_STRUCTURE_SPACING_BLOCKS = 3;
	private static final double WORKSTATION_SETTLEMENT_LINK_RADIUS_BLOCKS = 128.0D;
	private static final int VANILLA_WORKSTATION_SCAN_RADIUS_BLOCKS = 64;
	private static final int VANILLA_WORKSTATION_SCAN_DEPTH_BELOW_SURFACE_BLOCKS = 64;
	private static final int DOCK_LENGTH_BLOCKS = 8;
	private static final int DOCK_HALF_WIDTH_BLOCKS = 1;
	private static final int DOCK_SUPPORT_SPACING_BLOCKS = 4;
	private static final int MAX_DOCK_SHORE_REPLACEMENT_BLOCKS = 2;
	private static final int DOCK_SHORE_REPLACEMENT_ROWS = 2;
	private static final int DOCK_DEEP_WATER_END_ROWS = 2;
	private static final int LIGHTHOUSE_BASE_LEVELS = 4;
	private static final int LIGHTHOUSE_TOWER_LEVELS = 4;
	private static final int LIGHTHOUSE_WATER_RADIUS_BLOCKS = 6;
	private static final int MIN_HARBOR_WATER_SURFACE_COLUMNS = 32;
	private static final int MIN_HARBOR_DEEP_WATER_COLUMNS = 12;
	private static final int BLOCK_UPDATE_FLAGS = 3;
	/*
	 * Blueprint legend:
	 *
	 * Placement symbols:
	 * A = empty space
	 * B = bed at bed-designated coordinates, otherwise slab
	 * C = structure-specific wall/foundation material
	 * D = door
	 * F = fence
	 * G = fence gate
	 * H = chest
	 * L = log
	 * M = stone / cobblestone-family block
	 * N = hanging lantern
	 * P = planks
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
	private static final StructureBlueprint CARPENTER_WORKSHOP_BLUEPRINT = new StructureBlueprint(
		-2,
		-4,
		6,
		new String[][] {
			{
				"CCCCC",
				"CCCCC",
				"CCCCC",
				"CCCCC",
				"CCCCC",
				"CCCCC",
				"CCCCC",
				"CCCCC"
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
		}
	);
	private static final StructureBlueprint ROADWRIGHT_WORKSHOP_BLUEPRINT = new StructureBlueprint(
		-2,
		-4,
		6,
		new String[][] {
			{
				"CCCCC",
				"CCCCC",
				"CCCCC",
				"CCCCC",
				"CCCCC",
				"CCCCC",
				"CCCCC",
				"CCCCC"
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
		}
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
		}
	);
	private static final StructureBlueprint FLETCHER_HUT_BLUEPRINT = new StructureBlueprint(
		-2,
		-4,
		6,
		new String[][] {
			{
				"CCCCC",
				"CCCCC",
				"CCCCC",
				"CCCCC",
				"CCCCC",
				"CCCCC",
				"CCCCC",
				"CCCCC"
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
		}
	);
	private static final StructureBlueprint CARTOGRAPHER_HOUSE_BLUEPRINT = new StructureBlueprint(
		-6,
		-3,
		7,
		new String[][] {
			{
				"AAAAAAAAAA",
				"AACCCCCCCA",
				"AACPPPPPCA",
				"AACPPPPPCA",
				"AACPPPPPCA",
				"AACCCCCCCA",
				"AAAAAAAAAA"
			},
			{
				"AAAAAAAAAA",
				"AALCCCCCLA",
				"AACFAAAACA",
				"AADAAAWACA",
				"AACHAAAACA",
				"AALCCCCCLA",
				"AAAAAAAAAA"
			},
			{
				"AAAAAAAAAA",
				"AALCVCVCLA",
				"AACAAAAACA",
				"AADAAAAAVA",
				"AACAAAAACA",
				"AALCVCVCLA",
				"AAAAAAAAAA"
			},
			{
				"AAAAAAAAAA",
				"AALCCCCCLA",
				"AACAAAAACA",
				"AACAAAAACA",
				"AACAAAAACA",
				"AALCCCCCLA",
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
				"ASCAAAAACS",
				"AACAAAAACA",
				"ASCAAAAACS",
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
				"CCC",
				"CWC",
				"CCC"
			},
			{
				"CCC",
				"CCC",
				"CCC"
			},
			{
				"CCC",
				"CCC",
				"CCC"
			},
			{
				"CCC",
				"CCC",
				"CCC"
			},
			{
				"A",
				"C",
				"A"
			},
			{
				"A",
				"C",
				"A"
			},
			{
				"A",
				"C",
				"A"
			},
			{
				"A",
				"C",
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
		-4,
		-2,
		6,
		new String[][] {
			{
				"CCCCCCCC",
				"CCCCCCCC",
				"CCCCCCCC",
				"CCCCCCCC",
				"CCCCCCCC"
			},
			{
				"LFGLCCCL",
				"FAACABBC",
				"WAADAAAC",
				"FAACAHHC",
				"LFGLCCCL"
			},
			{
				"LAALPVPL",
				"AAAVAAAV",
				"AAADAAAV",
				"AAAVAAAV",
				"LAALPVPL"
			},
			{
				"LSSLPPPL",
				"SAAPAAAP",
				"SAAPAATP",
				"SAAPAAAP",
				"LSSLPPPL"
			},
			{
				"BBBSPPPS",
				"BBBSAAAS",
				"BBBSAAAS",
				"BBBSAAAS",
				"BBBSPPPS"
			},
			{
				"AAAASPSA",
				"AAAASPSA",
				"AAAASPSA",
				"AAAASPSA",
				"AAAASPSA"
			},
			{
				"AAAAABAA",
				"AAAAABAA",
				"AAAAABAA",
				"AAAAABAA",
				"AAAAABAA"
			}
		},
		new String[][] {
			{
				"........",
				"........",
				"........",
				"........",
				"........"
			},
			{
				"........",
				"........",
				"........",
				"........",
				"........"
			},
			{
				"........",
				"........",
				"........",
				"........",
				"........"
			},
			{
				"........",
				"L.......",
				"L.......",
				"L.......",
				"........"
			},
			{
				"........",
				"........",
				"........",
				"........",
				"........"
			},
			{
				"........",
				"........",
				"........",
				"........",
				"........"
			},
			{
				"........",
				"........",
				"........",
				"........",
				"........"
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
		int docks = 0;
		int lighthouses = 0;
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

					if (docks <= 0) {
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
			buildSiteInfrastructure.incompleteCarpenterWorkshops()
		);
	}

	public static CompletionResult tryCompleteProject(ServerLevel level, SettlementState settlement, SettlementProject project, Map<String, Integer> stock) {
		return switch (project.type()) {
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
		return LiveVillagesSavedData.get(level.getServer()).findNearestSettlement(
			level.dimension(),
			pos,
			WORKSTATION_SETTLEMENT_LINK_RADIUS_BLOCKS,
			settlement -> settlement.kind() != SettlementKind.OUTPOST
		);
	}

	private static BuildSiteInfrastructure buildSiteInfrastructure(ServerLevel level, SettlementState settlement) {
		Set<BlockPos> incompleteCarpenterWorkshopWorkstations = new HashSet<>();
		int completedTradingPosts = 0;
		int incompleteDocks = 0;
		int incompleteLighthouses = 0;
		int incompleteTradingPosts = 0;
		int incompleteCarpenterWorkshops = 0;

		for (SettlementBuildSite buildSite : LiveVillagesSavedData.get(level.getServer()).getBuildSitesForSettlement(settlement.id())) {
			if (buildSite.blueprintId() == SettlementBuildSiteType.DOCK) {
				if (!buildSite.complete()) {
					incompleteDocks++;
				}
			} else if (buildSite.blueprintId() == SettlementBuildSiteType.LIGHTHOUSE) {
				if (!buildSite.complete()) {
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
			}
		}

		return new BuildSiteInfrastructure(
			Set.copyOf(incompleteCarpenterWorkshopWorkstations),
			completedTradingPosts,
			incompleteDocks,
			incompleteLighthouses,
			incompleteTradingPosts,
			incompleteCarpenterWorkshops
		);
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

		Direction horizontalFacing = facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : facing;
		BlockPos origin = benchPos.relative(horizontalFacing.getOpposite(), 3).below();
		AnchoredStructureSite site = findAnchoredStructureSite(
			level,
			origin,
			horizontalFacing,
			CARPENTER_WORKSHOP_BLUEPRINT.minRight(),
			CARPENTER_WORKSHOP_BLUEPRINT.maxRight(),
			CARPENTER_WORKSHOP_BLUEPRINT.minForward(),
			CARPENTER_WORKSHOP_BLUEPRINT.maxForward(),
			CARPENTER_WORKSHOP_BLUEPRINT.clearHeight(),
			MAX_WORKSHOP_SITE_LANDSCAPING_BLOCKS,
			Set.of(benchPos.immutable())
		);

		if (site == null) {
			placeCantBuildHereSign(level, benchPos, horizontalFacing);
			return WorkstationBuildResult.blocked();
		}

		return WorkstationBuildResult.started(updateBuildSiteMaterialStatus(createPendingBuildSite(
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

		Direction horizontalFacing = facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : facing;
		BlockPos origin = tablePos.relative(horizontalFacing.getOpposite(), 3).below();
		AnchoredStructureSite site = findAnchoredStructureSite(
			level,
			origin,
			horizontalFacing,
			ROADWRIGHT_WORKSHOP_BLUEPRINT.minRight(),
			ROADWRIGHT_WORKSHOP_BLUEPRINT.maxRight(),
			ROADWRIGHT_WORKSHOP_BLUEPRINT.minForward(),
			ROADWRIGHT_WORKSHOP_BLUEPRINT.maxForward(),
			ROADWRIGHT_WORKSHOP_BLUEPRINT.clearHeight(),
			MAX_WORKSHOP_SITE_LANDSCAPING_BLOCKS,
			Set.of(tablePos.immutable())
		);

		if (site == null) {
			placeCantBuildHereSign(level, tablePos, horizontalFacing);
			return WorkstationBuildResult.blocked();
		}

		return WorkstationBuildResult.started(updateBuildSiteMaterialStatus(createPendingBuildSite(
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

		Direction horizontalFacing = facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : facing;
		BlockPos origin = tablePos.relative(horizontalFacing.getOpposite(), 3).below();
		AnchoredStructureSite site = findAnchoredStructureSite(
			level,
			origin,
			horizontalFacing,
			FORESTER_WORKSHOP_BLUEPRINT.minRight(),
			FORESTER_WORKSHOP_BLUEPRINT.maxRight(),
			FORESTER_WORKSHOP_BLUEPRINT.minForward(),
			FORESTER_WORKSHOP_BLUEPRINT.maxForward(),
			FORESTER_WORKSHOP_BLUEPRINT.clearHeight(),
			MAX_WORKSHOP_SITE_LANDSCAPING_BLOCKS,
			Set.of(tablePos.immutable())
		);

		if (site == null) {
			placeCantBuildHereSign(level, tablePos, horizontalFacing);
			return WorkstationBuildResult.blocked();
		}

		return WorkstationBuildResult.started(updateBuildSiteMaterialStatus(createPendingBuildSite(
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

		Direction horizontalFacing = facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : facing;
		BlockPos origin = tablePos.relative(horizontalFacing.getOpposite(), 3).below();
		AnchoredStructureSite site = findAnchoredStructureSite(
			level,
			origin,
			horizontalFacing,
			FLETCHER_HUT_BLUEPRINT.minRight(),
			FLETCHER_HUT_BLUEPRINT.maxRight(),
			FLETCHER_HUT_BLUEPRINT.minForward(),
			FLETCHER_HUT_BLUEPRINT.maxForward(),
			FLETCHER_HUT_BLUEPRINT.clearHeight(),
			MAX_WORKSHOP_SITE_LANDSCAPING_BLOCKS,
			Set.of(tablePos.immutable())
		);

		if (site == null) {
			placeCantBuildHereSign(level, tablePos, horizontalFacing);
			return WorkstationBuildResult.blocked();
		}

		return WorkstationBuildResult.started(updateBuildSiteMaterialStatus(createPendingBuildSite(
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

		Direction horizontalFacing = facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : facing;
		BlockPos origin = smokerPos.relative(horizontalFacing.getOpposite(), 3).below();
		AnchoredStructureSite site = findAnchoredStructureSite(
			level,
			origin,
			horizontalFacing,
			FLETCHER_HUT_BLUEPRINT.minRight(),
			FLETCHER_HUT_BLUEPRINT.maxRight(),
			FLETCHER_HUT_BLUEPRINT.minForward(),
			FLETCHER_HUT_BLUEPRINT.maxForward(),
			FLETCHER_HUT_BLUEPRINT.clearHeight(),
			MAX_WORKSHOP_SITE_LANDSCAPING_BLOCKS,
			Set.of(smokerPos.immutable())
		);

		if (site == null) {
			placeCantBuildHereSign(level, smokerPos, horizontalFacing);
			return WorkstationBuildResult.blocked();
		}

		return WorkstationBuildResult.started(updateBuildSiteMaterialStatus(createPendingBuildSite(
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

		Direction horizontalFacing = facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : facing;
		BlockPos origin = tablePos.below();
		AnchoredStructureSite site = findAnchoredStructureSite(
			level,
			origin,
			horizontalFacing,
			CARTOGRAPHER_HOUSE_BLUEPRINT.minRight(),
			CARTOGRAPHER_HOUSE_BLUEPRINT.maxRight(),
			CARTOGRAPHER_HOUSE_BLUEPRINT.minForward(),
			CARTOGRAPHER_HOUSE_BLUEPRINT.maxForward(),
			CARTOGRAPHER_HOUSE_BLUEPRINT.clearHeight(),
			MAX_WORKSHOP_SITE_LANDSCAPING_BLOCKS,
			Set.of(tablePos.immutable())
		);

		if (site == null) {
			placeCantBuildHereSign(level, tablePos, horizontalFacing);
			return WorkstationBuildResult.blocked();
		}

		return WorkstationBuildResult.started(updateBuildSiteMaterialStatus(createPendingBuildSite(
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
		if (existingBuildSite.isPresent()) {
			return WorkstationBuildResult.resumed(updateBuildSiteMaterialStatus(existingBuildSite.get(), stock, level.getServer().getTickCount()));
		}

		Direction boardFacing = facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : facing;
		Direction structureFacing = boardFacing.getClockWise();
		BlockPos origin = boardPos.relative(boardFacing.getOpposite(), 4).below();
		AnchoredStructureSite site = findAnchoredStructureSite(
			level,
			origin,
			structureFacing,
			TRADING_POST_BLUEPRINT.minRight(),
			TRADING_POST_BLUEPRINT.maxRight(),
			TRADING_POST_BLUEPRINT.minForward(),
			TRADING_POST_BLUEPRINT.maxForward(),
			TRADING_POST_BLUEPRINT.clearHeight(),
			MAX_WORKSHOP_SITE_LANDSCAPING_BLOCKS,
			Set.of(boardPos.immutable())
		);

		if (site == null) {
			if (placeBlockedSign) {
				placeCantBuildHereSign(level, boardPos, boardFacing);
			}
			return WorkstationBuildResult.blocked();
		}

		return WorkstationBuildResult.started(updateBuildSiteMaterialStatus(createPendingBuildSite(
			StructureKind.TRADING_POST,
			TRADING_POST_BLUEPRINT,
			settlementId,
			site.origin(),
			boardPos.immutable(),
			boardPos.immutable(),
			site.facing(),
			level.getServer().getTickCount()
		), stock, level.getServer().getTickCount()));
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
			return WorkstationBuildResult.resumed(updateBuildSiteMaterialStatus(existingBuildSite.get(), stock, level.getServer().getTickCount()));
		}

		LighthouseSite site = evaluateLighthouseMarkerSite(level, markerPos);

		if (site == null) {
			return WorkstationBuildResult.blocked();
		}

		return WorkstationBuildResult.started(updateBuildSiteMaterialStatus(createPendingBuildSite(
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
		Direction structureFacing = boardFacing.getClockWise();
		BlockPos origin = boardPos.relative(boardFacing.getOpposite(), 4).below();
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

	public static StructurePreview previewDockAtPortmasterAnchor(ServerLevel level, String settlementId, BlockPos anchorPos, Direction facing) {
		Direction horizontalFacing = facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : facing;
		Direction dockFacing = horizontalFacing.getOpposite();
		DockSite site = findDockPreviewSiteNearPortmasterAnchor(level, anchorPos, horizontalFacing);
		BlockPos origin = site != null ? site.origin() : previewDockOrigin(level, anchorPos, dockFacing);
		return new StructurePreview(
			settlementId + ":dock:" + anchorPos.getX() + "_" + anchorPos.getY() + "_" + anchorPos.getZ(),
			"Dock",
			site != null,
			buildDockPreviewBlocks(level, origin, dockFacing, site != null)
		);
	}

	public static StructurePreview previewLighthouseAtMarker(ServerLevel level, String settlementId, BlockPos markerPos) {
		SettlementBuildSite previewBuildSite = createPendingBuildSite(
			StructureKind.LIGHTHOUSE,
			LIGHTHOUSE_BLUEPRINT,
			settlementId,
			markerPos.immutable(),
			markerPos.immutable(),
			markerPos.immutable(),
			Direction.NORTH,
			0L
		);
		return previewBuildSite(previewBuildSite, "Lighthouse", evaluateLighthouseMarkerSite(level, markerPos) != null);
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

	public static List<BlockPos> findPlacedTradeBoards(ServerLevel level, SettlementState settlement) {
		return findPlacedWorkstations(level, settlement, state -> state.is(LiveVillagesBlocks.TRADE_BOARD));
	}

	public static List<BlockPos> findPlacedCarpenterBenches(ServerLevel level, SettlementState settlement) {
		return findPlacedWorkstations(level, settlement, state -> state.is(LiveVillagesBlocks.CARPENTER_BENCH));
	}

	public static List<BlockPos> findPlacedSurveyorTables(ServerLevel level, SettlementState settlement) {
		return findPlacedWorkstations(level, settlement, state -> state.is(LiveVillagesBlocks.SURVEYOR_TABLE));
	}

	public static List<BlockPos> findPlacedForesterTables(ServerLevel level, SettlementState settlement) {
		return findPlacedWorkstations(level, settlement, state -> state.is(LiveVillagesBlocks.FORESTER_TABLE));
	}

	public static List<BlockPos> findPlacedPortmasterAnchors(ServerLevel level, SettlementState settlement) {
		return findPlacedWorkstations(level, settlement, state -> state.is(LiveVillagesBlocks.PORTMASTER_ANCHOR));
	}

	public static List<BlockPos> findPlacedLighthouses(ServerLevel level, SettlementState settlement) {
		return findPlacedWorkstations(level, settlement, state -> state.is(LiveVillagesBlocks.LIGHTHOUSE));
	}

	private static List<BlockPos> findPlacedWorkstations(ServerLevel level, SettlementState settlement, Predicate<BlockState> matcher) {
		List<BlockPos> tablePositions = new ArrayList<>();
		BlockPos center = settlement.center();
		int radiusSquared = VANILLA_WORKSTATION_SCAN_RADIUS_BLOCKS * VANILLA_WORKSTATION_SCAN_RADIUS_BLOCKS;
		BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();

		for (int x = center.getX() - VANILLA_WORKSTATION_SCAN_RADIUS_BLOCKS; x <= center.getX() + VANILLA_WORKSTATION_SCAN_RADIUS_BLOCKS; x++) {
			for (int z = center.getZ() - VANILLA_WORKSTATION_SCAN_RADIUS_BLOCKS; z <= center.getZ() + VANILLA_WORKSTATION_SCAN_RADIUS_BLOCKS; z++) {
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

	public static Direction portmasterAnchorFacingFor(ServerLevel level, BlockPos anchorPos) {
		BlockState state = level.getBlockState(anchorPos);
		if (state.is(LiveVillagesBlocks.PORTMASTER_ANCHOR)) {
			return state.getValue(PortmasterAnchorBlock.FACING);
		}

		return Direction.NORTH;
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
			return dockBuildState(buildBlock.blueprintSymbol().charAt(0));
		}

		StructureKind structureKind = structureKindFor(buildSite.blueprintId());
		StructureBlueprint blueprint = blueprintFor(structureKind);
		return plannedBlueprintBlockState(
			structureKind,
			blueprint,
			buildSite.facing(),
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
		AnchoredStructureSite validSite = findAnchoredStructureSite(
			level,
			origin,
			facing,
			blueprint.minRight(),
			blueprint.maxRight(),
			blueprint.minForward(),
			blueprint.maxForward(),
			blueprint.clearHeight(),
			maxLandscapingBlocks,
			Set.of(anchorPos.immutable())
		);
		SettlementBuildSite previewBuildSite = createPendingBuildSite(
			structureKind,
			blueprint,
			settlementId,
			origin,
			anchorPos.immutable(),
			workstationPos.immutable(),
			facing,
			0L
		);
		return previewBuildSite(previewBuildSite, previewType, validSite != null);
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
		BlockPos origin = shelterOriginForDoor(doorPos, horizontalFacing);
		AnchoredStructureSite validSite = findAnchoredStructureSite(
			level,
			origin,
			horizontalFacing,
			blueprint.minRight(),
			blueprint.maxRight(),
			blueprint.minForward(),
			blueprint.maxForward(),
			blueprint.clearHeight(),
			maxLandscapingBlocks,
			Set.of(doorPos.immutable())
		);
		return new StructurePreview(
			settlementId + ":" + structureKind.name().toLowerCase() + ":" + doorPos.getX() + "_" + doorPos.getY() + "_" + doorPos.getZ(),
			previewType,
			validSite != null,
			previewStructureBlocks(origin, horizontalFacing, structureKind, blueprint)
		);
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
			return WorkstationBuildResult.resumed(updateBuildSiteMaterialStatus(existingBuildSite.get(), stock, level.getServer().getTickCount()));
		}

		Direction horizontalFacing = facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : facing;
		BlockPos origin = shelterOriginForDoor(doorPos, horizontalFacing);
		AnchoredStructureSite site = findAnchoredStructureSite(
			level,
			origin,
			horizontalFacing,
			blueprint.minRight(),
			blueprint.maxRight(),
			blueprint.minForward(),
			blueprint.maxForward(),
			blueprint.clearHeight(),
			maxLandscapingBlocks,
			Set.of(doorPos.immutable())
		);

		if (site == null) {
			return WorkstationBuildResult.blocked();
		}

		return WorkstationBuildResult.started(updateBuildSiteMaterialStatus(createPendingBuildSite(
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
		List<StructurePreviewBlock> blocks = new ArrayList<>();

		for (SettlementBuildBlockState block : buildSite.blocks()) {
			Optional<StructurePreviewBlock> previewBlock = structurePreviewBlock(buildSite, block);
			previewBlock.ifPresent(blocks::add);
		}

		return new StructurePreview(buildSite.id(), previewType, placementValid, blocks);
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

		for (int up = 0; up < blueprint.layers().length; up++) {
			String[] rows = blueprint.layers()[up];

			for (int row = 0; row < rows.length; row++) {
				String rowPattern = rows[row];
				int forward = blueprint.minForward() + row;

				for (int column = 0; column < rowPattern.length(); column++) {
					int right = blueprint.minRight() + column;
					char symbol = rowPattern.charAt(column);

					if (symbol == 'A') {
						continue;
					}

					BlockState plannedState = plannedBlueprintBlockState(structureKind, blueprint, facing, symbol, right, forward, up);

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

	private static List<StructurePreviewBlock> buildDockPreviewBlocks(ServerLevel level, BlockPos origin, Direction facing, boolean placementValid) {
		List<StructurePreviewBlock> blocks = new ArrayList<>();

		for (int forward = 0; forward < DOCK_LENGTH_BLOCKS; forward++) {
			for (int right = -DOCK_HALF_WIDTH_BLOCKS; right <= DOCK_HALF_WIDTH_BLOCKS; right++) {
				BlockPos deckPos = offset(origin, facing, right, forward, 0);
				blocks.add(new StructurePreviewBlock(
					deckPos,
					"planks",
					BuiltInRegistries.BLOCK.getKey(Blocks.OAK_PLANKS).toString()
				));

				if (isDockSupportRow(forward) && Math.abs(right) == DOCK_HALF_WIDTH_BLOCKS) {
					BlockPos footing = placementValid ? findDockFooting(level, deckPos) : null;
					int bottomY = footing != null ? footing.getY() + 1 : Math.max(level.getMinY(), deckPos.getY() - 2);

					for (int y = deckPos.getY() - 1; y >= bottomY; y--) {
						blocks.add(new StructurePreviewBlock(
							new BlockPos(deckPos.getX(), y, deckPos.getZ()),
							"logs",
							BuiltInRegistries.BLOCK.getKey(Blocks.OAK_LOG).toString()
						));
					}
				}
			}
		}

		return blocks;
	}

	public static char currentBlueprintSymbol(SettlementBuildSite buildSite, SettlementBuildBlockState buildBlock) {
		if (buildSite.blueprintId() == SettlementBuildSiteType.DOCK) {
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
		if (buildSite.blueprintId() == SettlementBuildSiteType.DOCK) {
			return List.copyOf(buildSite.blocks());
		}

		StructureKind structureKind = structureKindFor(buildSite.blueprintId());
		StructureBlueprint blueprint = blueprintFor(structureKind);
		List<SettlementBuildBlockState> blocks = new ArrayList<>();

		for (int up = 0; up < blueprint.layers().length; up++) {
			String[] rows = blueprint.layers()[up];

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
		return isReplaceable(state);
	}

	public static boolean tryClearBuildSiteBlock(ServerLevel level, BlockPos pos, Map<String, Integer> stock) {
		BlockState state = level.getBlockState(pos);

		if (isReplaceable(state)) {
			return true;
		}

		if (!canLandscapeRemove(level, pos, state) && !canRecoverConstructionBlock(level, pos, state)) {
			return false;
		}

		clearBlockToWarehouse(level, pos, stock);
		return true;
	}

	public static boolean tryReplaceBuildSiteBlock(ServerLevel level, BlockPos pos, BlockState plannedState, Map<String, Integer> stock) {
		BlockState currentState = level.getBlockState(pos);

		if (currentState.equals(plannedState)) {
			return true;
		}

		if (isReplaceable(currentState)) {
			level.setBlock(pos, plannedState, BLOCK_UPDATE_FLAGS);
			return true;
		}

		if (!canLandscapeRemove(level, pos, currentState) && !canRecoverConstructionBlock(level, pos, currentState)) {
			return false;
		}

		String goodsKey = recoveredGoodsKey(currentState);

		if (goodsKey != null) {
			stock.merge(goodsKey, 1, Integer::sum);
		}

		level.setBlock(pos, plannedState, BLOCK_UPDATE_FLAGS);
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
		int minRight,
		int maxRight,
		int minForward,
		int maxForward,
		int maxClearHeight,
		int maxLandscapingBlocks,
		Set<BlockPos> protectedPositions
	) {
		int baseY = origin.getY();
		int landscapingBlocks = 0;

		if (!hasStructureSpacingClearance(level, origin, facing, minRight, maxRight, minForward, maxForward, maxClearHeight, protectedPositions)) {
			return null;
		}

		for (int right = minRight; right <= maxRight; right++) {
			for (int forward = minForward; forward <= maxForward; forward++) {
				BlockPos floorPos = offset(origin, facing, right, forward, 0);

				if (!level.hasChunkAt(floorPos)) {
					return null;
				}

				int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, floorPos.getX(), floorPos.getZ()) - 1;

				if (groundY < level.getMinY() || groundY > level.getMaxY() - 1) {
					return null;
				}

				BlockPos groundPos = new BlockPos(floorPos.getX(), groundY, floorPos.getZ());

				while (protectedPositions.contains(groundPos) && groundY > level.getMinY()) {
					groundY--;
					groundPos = groundPos.below();
				}

				if (groundY > baseY + MAX_TERRAIN_CUT_BLOCKS || groundY < baseY - MAX_TERRAIN_FILL_BLOCKS) {
					return null;
				}

				if (!hasStableBuildGround(level, groundPos)) {
					return null;
				}

				if (groundY < baseY) {
					for (int y = groundY + 1; y <= baseY; y++) {
						BlockPos fillPos = new BlockPos(floorPos.getX(), y, floorPos.getZ());

						if (protectedPositions.contains(fillPos)) {
							continue;
						}

						if (!isReplaceable(level.getBlockState(fillPos))) {
							return null;
						}

						landscapingBlocks++;
					}
				} else {
					BlockPos supportPos = new BlockPos(floorPos.getX(), baseY, floorPos.getZ());
					BlockState supportState = level.getBlockState(supportPos);

					if (!supportState.isSolid()) {
						return null;
					}
				}

				for (int y = baseY + 1; y <= Math.max(baseY + maxClearHeight, groundY); y++) {
					BlockPos clearPos = new BlockPos(floorPos.getX(), y, floorPos.getZ());

					if (protectedPositions.contains(clearPos)) {
						continue;
					}

					BlockState clearState = level.getBlockState(clearPos);

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

		return new AnchoredStructureSite(origin, facing);
	}

	private static HousingSite findFreestandingStructureSite(
		ServerLevel level,
		BlockPos anchor,
		Direction facing,
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

				int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, worldPos.getX(), worldPos.getZ()) - 1;

				if (groundY < level.getMinY()) {
					return null;
				}

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
				int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, floorPos.getX(), floorPos.getZ()) - 1;

				if (groundY < level.getMinY() || groundY > level.getMaxY() - 1) {
					return null;
				}

				BlockPos groundPos = new BlockPos(floorPos.getX(), groundY, floorPos.getZ());

				if (!hasStableBuildGround(level, groundPos)) {
					return null;
				}

				if (groundY < baseY) {
					for (int y = groundY + 1; y <= baseY; y++) {
						BlockPos fillPos = new BlockPos(floorPos.getX(), y, floorPos.getZ());

						if (!isReplaceable(level.getBlockState(fillPos))) {
							return null;
						}

						landscapingBlocks++;
					}
				} else {
					BlockPos supportPos = new BlockPos(floorPos.getX(), baseY, floorPos.getZ());
					BlockState supportState = level.getBlockState(supportPos);

					if (!supportState.isSolid()) {
						return null;
					}
				}

				for (int y = baseY + 1; y <= Math.max(baseY + blueprint.clearHeight(), groundY); y++) {
					BlockPos clearPos = new BlockPos(floorPos.getX(), y, floorPos.getZ());
					BlockState clearState = level.getBlockState(clearPos);

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
		return findFreestandingStructureSite(level, anchor, facing, CARPENTER_WORKSHOP_BLUEPRINT, MAX_WORKSHOP_SITE_LANDSCAPING_BLOCKS);
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
		return findFreestandingStructureSite(level, anchor, facing, HOUSING_SHELTER_BLUEPRINT, MAX_SITE_LANDSCAPING_BLOCKS);
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

		for (int up = 0; up < blueprint.layers().length; up++) {
			String[] rows = blueprint.layers()[up];

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
				placeOakDoor(level, pos, blueprintDoorFacing(structureKind, facing));
			}

			return;
		}

		BlockState state = blueprintStateFor(facing, blueprint, structureKind, symbol, right, forward, up);

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
		char symbol,
		int right,
		int forward,
		int up
	) {
		return switch (symbol) {
			case 'C' -> structureKind == StructureKind.FORESTER_WORKSHOP
				? Blocks.OAK_LOG.defaultBlockState()
				: (structureKind == StructureKind.LIGHTHOUSE
					|| structureKind == StructureKind.TRADING_POST
					|| structureKind == StructureKind.CARTOGRAPHER_HOUSE
					|| structureKind == StructureKind.FLETCHER_HUT
					|| structureKind == StructureKind.BUTCHER_SHOP)
				? Blocks.COBBLESTONE.defaultBlockState()
				: Blocks.OAK_PLANKS.defaultBlockState();
			case 'H' -> Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing.getOpposite());
			case 'L' -> Blocks.OAK_LOG.defaultBlockState();
			case 'M' -> Blocks.STONE.defaultBlockState();
			case 'P' -> Blocks.OAK_PLANKS.defaultBlockState();
			case 'F' -> fenceStateFor(blueprint, structureKind, facing, right, forward, up);
			case 'G' -> Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, facing);
			case 'N' -> lanternStateFor();
			case 'T' -> torchStateFor(blueprint, facing, right, forward, up);
			case 'V' -> structureKind == StructureKind.CARTOGRAPHER_HOUSE ? Blocks.GLASS_PANE.defaultBlockState() : Blocks.GLASS.defaultBlockState();
			case 'K' -> Blocks.CAMPFIRE.defaultBlockState();
			case 'W' -> workstationStateFor(structureKind, facing);
			case 'B' -> slabStateFor(structureKind, up);
			case 'S' -> stairStateFor(blueprint, structureKind, facing, right, forward, up);
			default -> null;
		};
	}

	private static BlockState fenceStateFor(
		StructureBlueprint blueprint,
		StructureKind structureKind,
		Direction facing,
		int right,
		int forward,
		int up
	) {
		BlockState state = Blocks.OAK_FENCE.defaultBlockState();

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlueprintRelativeStep step = relativeStepForDirection(facing, direction);
			char adjacentSymbol = blueprintSymbolAt(blueprint, right + step.right(), forward + step.forward(), up);

			if (fenceConnectsToSymbol(structureKind, adjacentSymbol, up)) {
				state = state.setValue(CrossCollisionBlock.PROPERTY_BY_DIRECTION.get(direction), true);
			}
		}

		return state;
	}

	private static BlockState workstationStateFor(StructureKind structureKind, Direction facing) {
		if (structureKind == StructureKind.LIGHTHOUSE) {
			return LiveVillagesBlocks.LIGHTHOUSE.defaultBlockState();
		}

		if (structureKind == StructureKind.TRADING_POST) {
			return LiveVillagesBlocks.TRADE_BOARD.defaultBlockState().setValue(TradeBoardBlock.FACING, facing.getCounterClockWise());
		}

		if (structureKind == StructureKind.ROADWRIGHT_WORKSHOP) {
			return LiveVillagesBlocks.SURVEYOR_TABLE.defaultBlockState();
		}

		if (structureKind == StructureKind.FORESTER_WORKSHOP) {
			return LiveVillagesBlocks.FORESTER_TABLE.defaultBlockState();
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

		return LiveVillagesBlocks.CARPENTER_BENCH.defaultBlockState();
	}

	private static BlockState slabStateFor(StructureKind structureKind, int up) {
		BlockState state = Blocks.OAK_SLAB.defaultBlockState();

		if ((structureKind == StructureKind.HOUSING_SHELTER && up == 4)
			|| (structureKind == StructureKind.SIMPLE_HOUSING_SHELTER && up == 3)) {
			return state.setValue(SlabBlock.TYPE, SlabType.TOP);
		}

		if ((structureKind == StructureKind.CARPENTER_WORKSHOP
			|| structureKind == StructureKind.ROADWRIGHT_WORKSHOP
			|| structureKind == StructureKind.FORESTER_WORKSHOP
			|| structureKind == StructureKind.FLETCHER_HUT
			|| structureKind == StructureKind.BUTCHER_SHOP) && up == 5) {
			return state.setValue(SlabBlock.TYPE, SlabType.TOP);
		}

		return state;
	}

	private static BlockState lanternStateFor() {
		return Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
	}

	private static BlockState torchStateFor(StructureBlueprint blueprint, Direction facing, int right, int forward, int up) {
		return Blocks.WALL_TORCH.defaultBlockState()
			.setValue(WallTorchBlock.FACING, blueprintTorchFacing(blueprint, facing, right, forward, up));
	}

	private static BlockState stairStateFor(StructureBlueprint blueprint, StructureKind structureKind, Direction facing, int right, int forward, int up) {
		BlockState state = Blocks.OAK_STAIRS.defaultBlockState()
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

		if (structureKind == StructureKind.TRADING_POST && (up == 4 || up == 5)) {
			return right <= 0 ? facing.getClockWise() : facing.getCounterClockWise();
		}

		if (structureKind == StructureKind.TRADING_POST && up == 3 && forward == 0 && right >= -4 && right <= -2) {
			return facing.getClockWise();
		}

		if (structureKind == StructureKind.CARPENTER_WORKSHOP
			|| structureKind == StructureKind.ROADWRIGHT_WORKSHOP
			|| structureKind == StructureKind.FORESTER_WORKSHOP
			|| structureKind == StructureKind.FLETCHER_HUT
			|| structureKind == StructureKind.BUTCHER_SHOP) {
			if (up == 3 && (right == blueprint.minRight() || right == blueprint.maxRight())) {
				return right == blueprint.minRight() ? facing.getCounterClockWise() : facing.getClockWise();
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
		if (up < 0 || up >= blueprint.layers().length) {
			return 'A';
		}

		String[] rows = blueprint.layers()[up];
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
		if (blueprint.orientations() == null || up < 0 || up >= blueprint.orientations().length) {
			return '.';
		}

		String[] rows = blueprint.orientations()[up];
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
		if (symbol == 'A' || symbol == 'D') {
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
		if (structureKind == StructureKind.TRADING_POST) {
			return facing.getCounterClockWise();
		}

		if (structureKind == StructureKind.CARTOGRAPHER_HOUSE) {
			return facing.getCounterClockWise();
		}

		return facing;
	}

	private static boolean isBedSymbol(StructureKind structureKind, char symbol, int up) {
		return symbol == 'B' && up == 1 && (structureKind == StructureKind.CARPENTER_WORKSHOP
			|| structureKind == StructureKind.BUTCHER_SHOP
			|| structureKind == StructureKind.FLETCHER_HUT
			|| structureKind == StructureKind.FORESTER_WORKSHOP
			|| structureKind == StructureKind.HOUSING_SHELTER
			|| structureKind == StructureKind.ROADWRIGHT_WORKSHOP
			|| structureKind == StructureKind.SIMPLE_HOUSING_SHELTER
			|| structureKind == StructureKind.TRADING_POST);
	}

	private static BlockState bedStateFor(StructureKind structureKind, Direction facing, int right, int forward, int up) {
		if (!isBedSymbol(structureKind, 'B', up)) {
			return null;
		}

		if ((structureKind == StructureKind.CARPENTER_WORKSHOP || structureKind == StructureKind.ROADWRIGHT_WORKSHOP || structureKind == StructureKind.FORESTER_WORKSHOP)
			&& right == -1
			&& (forward == -3 || forward == -2)) {
			return Blocks.WHITE_BED.defaultBlockState()
				.setValue(BedBlock.PART, forward == -3 ? BedPart.HEAD : BedPart.FOOT)
				.setValue(BedBlock.FACING, facing.getOpposite());
		}

		if ((structureKind == StructureKind.FLETCHER_HUT || structureKind == StructureKind.BUTCHER_SHOP)
			&& (right == -1 || right == 1)
			&& (forward == -3 || forward == -2)) {
			return Blocks.WHITE_BED.defaultBlockState()
				.setValue(BedBlock.PART, forward == -3 ? BedPart.HEAD : BedPart.FOOT)
				.setValue(BedBlock.FACING, facing.getOpposite());
		}

		if (structureKind == StructureKind.TRADING_POST && forward == -1 && (right == 1 || right == 2)) {
			return Blocks.WHITE_BED.defaultBlockState()
				.setValue(BedBlock.PART, right == 2 ? BedPart.HEAD : BedPart.FOOT)
				.setValue(BedBlock.FACING, facing.getClockWise());
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
		return ((structureKind == StructureKind.CARPENTER_WORKSHOP || structureKind == StructureKind.ROADWRIGHT_WORKSHOP || structureKind == StructureKind.FORESTER_WORKSHOP) && right == -1 && forward == -2 && up == 1)
			|| ((structureKind == StructureKind.FLETCHER_HUT || structureKind == StructureKind.BUTCHER_SHOP) && (right == -1 || right == 1) && forward == -2 && up == 1)
			|| (structureKind == StructureKind.HOUSING_SHELTER && (right == -1 || right == 1) && forward == 0 && up == 1)
			|| (structureKind == StructureKind.SIMPLE_HOUSING_SHELTER && right == -1 && forward == 1 && up == 1)
			|| (structureKind == StructureKind.TRADING_POST && right == 1 && forward == -1 && up == 1);
	}

	private static void placeBlueprintBeds(ServerLevel level, BlockPos origin, Direction facing, StructureKind structureKind) {
		if (structureKind == StructureKind.CARPENTER_WORKSHOP || structureKind == StructureKind.FORESTER_WORKSHOP) {
			placeBed(level, offset(origin, facing, -1, -3, 1), offset(origin, facing, -1, -2, 1), facing.getOpposite());
		} else if (structureKind == StructureKind.FLETCHER_HUT || structureKind == StructureKind.BUTCHER_SHOP) {
			placeBed(level, offset(origin, facing, -1, -3, 1), offset(origin, facing, -1, -2, 1), facing.getOpposite());
			placeBed(level, offset(origin, facing, 1, -3, 1), offset(origin, facing, 1, -2, 1), facing.getOpposite());
		} else if (structureKind == StructureKind.HOUSING_SHELTER) {
			placeBed(level, offset(origin, facing, -1, -1, 1), offset(origin, facing, -1, 0, 1), facing.getOpposite());
			placeBed(level, offset(origin, facing, 1, -1, 1), offset(origin, facing, 1, 0, 1), facing.getOpposite());
		} else if (structureKind == StructureKind.SIMPLE_HOUSING_SHELTER) {
			placeBed(level, offset(origin, facing, -1, 0, 1), offset(origin, facing, -1, 1, 1), facing.getOpposite());
		} else if (structureKind == StructureKind.TRADING_POST) {
			placeBed(level, offset(origin, facing, 2, -1, 1), offset(origin, facing, 1, -1, 1), facing.getClockWise());
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
	}

	private static SettlementBuildSite createPendingBuildSite(
		StructureKind structureKind,
		StructureBlueprint blueprint,
		String settlementId,
		BlockPos origin,
		BlockPos anchorPos,
		BlockPos workstationPos,
		Direction facing,
		long tick
	) {
		List<SettlementBuildBlockState> blocks = new ArrayList<>();
		String anchorPosition = relativeBlueprintPositionFromWorld(origin, facing, anchorPos);
		boolean anchorMappedInBlueprint = false;

		for (int up = 0; up < blueprint.layers().length; up++) {
			String[] rows = blueprint.layers()[up];

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
				}
			}
		}

		if (!anchorPos.equals(workstationPos) && !anchorMappedInBlueprint) {
			blocks.add(SettlementBuildBlockState.pending(anchorPosition, 'A', ""));
		}

		return new SettlementBuildSite(
			buildSiteId(settlementId, structureKind, anchorPos),
			settlementId,
			blueprintIdFor(structureKind),
			origin.immutable(),
			workstationPos.immutable(),
			anchorPos.immutable(),
			facing,
			"oak",
			"cobblestone",
			blocks,
			false,
			tick,
			tick
		);
	}

	public static SettlementBuildSite updateBuildSiteMaterialStatus(SettlementBuildSite buildSite, Map<String, Integer> stock, long tick) {
		Map<String, Integer> reservedStock = new java.util.LinkedHashMap<>(stock);
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
				SettlementConstructionMaterials.consumeForBlock(reservedStock, new java.util.LinkedHashMap<>(), normalizedBlock);
			SettlementBuildBlockState updatedBlock = materialResult.supplied()
				? normalizedBlock.withStatus(SettlementBuildBlockStatus.PENDING, "")
				: normalizedBlock.withStatus(SettlementBuildBlockStatus.MISSING_MATERIAL, materialResult.missingMaterialKey());

			if (!updatedBlock.equals(normalizedBlock)) {
				changed = true;
			}

			updatedBlocks.add(updatedBlock);
		}

		if (!changed) {
			return buildSite;
		}

		return buildSite.withBlocks(updatedBlocks, buildSite.complete(), tick);
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
		return symbol == 'W' && (structureKind == StructureKind.CARPENTER_WORKSHOP
			|| structureKind == StructureKind.BUTCHER_SHOP
			|| structureKind == StructureKind.CARTOGRAPHER_HOUSE
			|| structureKind == StructureKind.FLETCHER_HUT
			|| structureKind == StructureKind.FORESTER_WORKSHOP
			|| structureKind == StructureKind.LIGHTHOUSE
			|| structureKind == StructureKind.ROADWRIGHT_WORKSHOP
			|| structureKind == StructureKind.TRADING_POST);
	}

	public static boolean isAnchoredWorkstationBlock(SettlementBuildSite buildSite, SettlementBuildBlockState block) {
		if (block.blueprintSymbol().isBlank()) {
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
			case 'D' -> up == 1 ? "door" : "";
			case 'F' -> "fence";
			case 'G' -> "fence_gate";
			case 'H' -> "chest";
			case 'K' -> "campfire";
			case 'L' -> "logs";
			case 'M' -> "cobblestone";
			case 'N' -> "lantern";
			case 'P' -> "planks";
			case 'S' -> "stairs";
			case 'T' -> "torch";
			case 'V' -> "glass";
			case 'W' -> switch (structureKind) {
				case TRADING_POST -> "trade_board";
				case FORESTER_WORKSHOP -> "forester_table";
				case FLETCHER_HUT -> "fletching_table";
				case BUTCHER_SHOP -> "smoker";
				case ROADWRIGHT_WORKSHOP -> "surveyor_table";
				case CARTOGRAPHER_HOUSE -> "cartography_table";
				case CARPENTER_WORKSHOP -> "carpenter_bench";
				case HOUSING_SHELTER, LIGHTHOUSE, SIMPLE_HOUSING_SHELTER -> "";
				case DOCK -> "";
			};
			default -> "";
		};
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
		char symbol,
		int right,
		int forward,
		int up
	) {
		if (symbol == 'A') {
			return Blocks.AIR.defaultBlockState();
		}

		if (symbol == 'D') {
			if (up != 1 && up != 2) {
				return null;
			}

			return Blocks.OAK_DOOR.defaultBlockState()
				.setValue(DoorBlock.FACING, blueprintDoorFacing(structureKind, facing))
				.setValue(DoorBlock.HALF, up == 1 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
		}

		if (isBedSymbol(structureKind, symbol, up)) {
			return bedStateFor(structureKind, facing, right, forward, up);
		}

		return blueprintStateFor(facing, blueprint, structureKind, symbol, right, forward, up);
	}

	private static StructureBlueprint blueprintFor(StructureKind structureKind) {
		return switch (structureKind) {
			case BUTCHER_SHOP -> FLETCHER_HUT_BLUEPRINT;
			case CARTOGRAPHER_HOUSE -> CARTOGRAPHER_HOUSE_BLUEPRINT;
			case CARPENTER_WORKSHOP -> CARPENTER_WORKSHOP_BLUEPRINT;
			case DOCK -> throw new IllegalStateException("Dock build sites use custom block generation");
			case FLETCHER_HUT -> FLETCHER_HUT_BLUEPRINT;
			case FORESTER_WORKSHOP -> FORESTER_WORKSHOP_BLUEPRINT;
			case HOUSING_SHELTER -> HOUSING_SHELTER_BLUEPRINT;
			case LIGHTHOUSE -> LIGHTHOUSE_BLUEPRINT;
			case ROADWRIGHT_WORKSHOP -> ROADWRIGHT_WORKSHOP_BLUEPRINT;
			case SIMPLE_HOUSING_SHELTER -> SIMPLE_HOUSING_SHELTER_BLUEPRINT;
			case TRADING_POST -> TRADING_POST_BLUEPRINT;
		};
	}

	private static StructureKind structureKindFor(SettlementBuildSiteType blueprintId) {
		return switch (blueprintId) {
			case BUTCHER_SHOP -> StructureKind.BUTCHER_SHOP;
			case CARTOGRAPHER_HOUSE -> StructureKind.CARTOGRAPHER_HOUSE;
			case CARPENTER_WORKSHOP -> StructureKind.CARPENTER_WORKSHOP;
			case DOCK -> StructureKind.DOCK;
			case FLETCHER_HUT -> StructureKind.FLETCHER_HUT;
			case FORESTER_WORKSHOP -> StructureKind.FORESTER_WORKSHOP;
			case HOUSING_SHELTER -> StructureKind.HOUSING_SHELTER;
			case LIGHTHOUSE -> StructureKind.LIGHTHOUSE;
			case ROADWRIGHT_WORKSHOP -> StructureKind.ROADWRIGHT_WORKSHOP;
			case SIMPLE_HOUSING_SHELTER -> StructureKind.SIMPLE_HOUSING_SHELTER;
			case TRADING_POST -> StructureKind.TRADING_POST;
		};
	}

	private static SettlementBuildSiteType blueprintIdFor(StructureKind structureKind) {
		return switch (structureKind) {
			case BUTCHER_SHOP -> SettlementBuildSiteType.BUTCHER_SHOP;
			case CARTOGRAPHER_HOUSE -> SettlementBuildSiteType.CARTOGRAPHER_HOUSE;
			case CARPENTER_WORKSHOP -> SettlementBuildSiteType.CARPENTER_WORKSHOP;
			case DOCK -> SettlementBuildSiteType.DOCK;
			case FLETCHER_HUT -> SettlementBuildSiteType.FLETCHER_HUT;
			case FORESTER_WORKSHOP -> SettlementBuildSiteType.FORESTER_WORKSHOP;
			case HOUSING_SHELTER -> SettlementBuildSiteType.HOUSING_SHELTER;
			case LIGHTHOUSE -> SettlementBuildSiteType.LIGHTHOUSE;
			case ROADWRIGHT_WORKSHOP -> SettlementBuildSiteType.ROADWRIGHT_WORKSHOP;
			case SIMPLE_HOUSING_SHELTER -> SettlementBuildSiteType.SIMPLE_HOUSING_SHELTER;
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
		for (int forward = 0; forward < DOCK_LENGTH_BLOCKS; forward++) {
			for (int right = -DOCK_HALF_WIDTH_BLOCKS; right <= DOCK_HALF_WIDTH_BLOCKS; right++) {
				BlockPos deckPos = offset(site.origin(), site.facing(), right, forward, 0);
				BlockPos abovePos = deckPos.above();

				if (!level.getBlockState(abovePos).isAir()) {
					clearBlockToWarehouse(level, abovePos, stock);
				}

				clearBlockToWarehouse(level, deckPos, stock);
				level.setBlock(deckPos, Blocks.OAK_PLANKS.defaultBlockState(), BLOCK_UPDATE_FLAGS);

				if (isDockSupportRow(forward) && Math.abs(right) == DOCK_HALF_WIDTH_BLOCKS) {
					BlockPos footing = findDockFooting(level, deckPos);

					if (footing == null) {
						continue;
					}

					for (int y = deckPos.getY() - 1; y > footing.getY(); y--) {
						level.setBlock(new BlockPos(deckPos.getX(), y, deckPos.getZ()), Blocks.OAK_LOG.defaultBlockState(), BLOCK_UPDATE_FLAGS);
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
			"oak",
			"",
			blocks,
			false,
			tick,
			tick
		);
	}

	private static BlockState dockBuildState(char symbol) {
		return switch (symbol) {
			case 'L' -> Blocks.OAK_LOG.defaultBlockState();
			case 'P' -> Blocks.OAK_PLANKS.defaultBlockState();
			default -> null;
		};
	}

	private static String dockMaterialKey(char symbol) {
		return switch (symbol) {
			case 'L' -> "logs";
			case 'P' -> "planks";
			default -> "";
		};
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

	private static void placeCantBuildHereSign(ServerLevel level, BlockPos workstationPos, Direction facing) {
		for (Direction direction : new Direction[] {facing.getClockWise(), facing.getCounterClockWise(), facing, facing.getOpposite()}) {
			BlockPos signPos = workstationPos.relative(direction);

			if (!level.hasChunkAt(signPos) || !isReplaceable(level.getBlockState(signPos)) || !level.getBlockState(signPos.below()).isSolid()) {
				continue;
			}

			BlockState signState = Blocks.OAK_SIGN.defaultBlockState()
				.setValue(StandingSignBlock.ROTATION, signRotationForFacing(direction.getOpposite()));
			level.setBlock(signPos, signState, BLOCK_UPDATE_FLAGS);

			if (level.getBlockEntity(signPos) instanceof SignBlockEntity signBlockEntity) {
				SignText text = new SignText()
					.setMessage(0, Component.literal("can't build"))
					.setMessage(1, Component.literal("here"));
				signBlockEntity.setText(text, true);
				signBlockEntity.setChanged();
				level.sendBlockUpdated(signPos, signState, signState, BLOCK_UPDATE_FLAGS);
			}

			return;
		}
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

		BlockPos belowPos = groundPos.below();

		if (belowPos.getY() < level.getMinY()) {
			return true;
		}

		BlockState belowState = level.getBlockState(belowPos);
		return belowState.isSolid() && isBuildGroundSurface(belowState);
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
		if (state.isAir() || state.liquid() || state.hasBlockEntity() || level.getBlockEntity(pos) != null) {
			return false;
		}

		return isNaturalLandscapeBlock(state);
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

		String goodsKey = recoveredGoodsKey(state);

		if (goodsKey != null) {
			stock.merge(goodsKey, 1, Integer::sum);
		}

		level.setBlock(pos, Blocks.AIR.defaultBlockState(), BLOCK_UPDATE_FLAGS);
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

		if (state.is(Blocks.LANTERN)) {
			return "lantern";
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

		if (state.is(LiveVillagesBlocks.SURVEYOR_TABLE)) {
			return "surveyor_table";
		}

		if (state.is(LiveVillagesBlocks.FORESTER_TABLE)) {
			return "forester_table";
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
			|| state.is(LiveVillagesBlocks.TRADE_BOARD)
			|| state.is(LiveVillagesBlocks.CARPENTER_BENCH)
			|| state.is(LiveVillagesBlocks.SURVEYOR_TABLE)
			|| state.is(LiveVillagesBlocks.FORESTER_TABLE);
	}

	private static boolean isDisposableConstructionAnchorBlock(BlockState state) {
		return state.getBlock() instanceof ShelterAnchorBlock;
	}

	private static boolean isNaturalLandscapeBlock(BlockState state) {
		return isInTag(state, BlockTags.BASE_STONE_OVERWORLD)
			|| isInTag(state, BlockTags.IRON_ORES)
			|| isInTag(state, BlockTags.LOGS)
			|| isInTag(state, BlockTags.LEAVES)
			|| isInTag(state, BlockTags.DIRT)
			|| isInTag(state, BlockTags.SAND)
			|| state.is(Blocks.GRASS_BLOCK)
			|| state.is(Blocks.DIRT)
			|| state.is(Blocks.COARSE_DIRT)
			|| state.is(Blocks.ROOTED_DIRT)
			|| state.is(Blocks.GRAVEL);
	}

	private static boolean isBuildGroundSurface(BlockState state) {
		return isInTag(state, BlockTags.BASE_STONE_OVERWORLD)
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
		for (int right = minRight - MIN_STRUCTURE_SPACING_BLOCKS; right <= maxRight + MIN_STRUCTURE_SPACING_BLOCKS; right++) {
			for (int forward = minForward - MIN_STRUCTURE_SPACING_BLOCKS; forward <= maxForward + MIN_STRUCTURE_SPACING_BLOCKS; forward++) {
				if (right >= minRight && right <= maxRight && forward >= minForward && forward <= maxForward) {
					continue;
				}

				BlockPos columnPos = offset(origin, facing, right, forward, 0);
				if (!level.hasChunkAt(columnPos)) {
					return false;
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
						return false;
					}
				}
			}
		}

		return true;
	}

	private static boolean isStructureSpacingObstacle(ServerLevel level, BlockPos pos, int groundY) {
		BlockState state = level.getBlockState(pos);
		if (isReplaceable(state) || state.liquid()) {
			return false;
		}

		if (pos.getY() <= groundY && isBuildGroundSurface(state)) {
			return false;
		}

		return !canLandscapeRemove(level, pos, state);
	}

	private static int buildRadius(SettlementState settlement) {
		return switch (settlement.kind()) {
			case VILLAGE, HARBOR -> VILLAGE_BUILD_RADIUS_BLOCKS;
			case CUSTOM, OUTPOST -> STANDARD_BUILD_RADIUS_BLOCKS;
		};
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

	public record WorkstationBuildResult(boolean isStarted, boolean isBlocked, boolean isResumed, SettlementBuildSite buildSite) {
		public static WorkstationBuildResult started(SettlementBuildSite buildSite) {
			return new WorkstationBuildResult(true, false, false, buildSite);
		}

		public static WorkstationBuildResult resumed(SettlementBuildSite buildSite) {
			return new WorkstationBuildResult(false, false, true, buildSite);
		}

		public static WorkstationBuildResult blocked() {
			return new WorkstationBuildResult(false, true, false, null);
		}
	}

	public record StructurePreview(String previewId, String previewType, boolean placementValid, List<StructurePreviewBlock> blocks) {
		public StructurePreview {
			blocks = List.copyOf(blocks);
		}
	}

	public record StructurePreviewBlock(BlockPos pos, String materialKey, String blockId) {
		public StructurePreviewBlock {
			pos = pos.immutable();
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
		int incompleteCarpenterWorkshops
	) {
		public static InfrastructureSurvey empty() {
			return new InfrastructureSurvey(false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
		}

		public boolean hasLargeWaterBody() {
			int dockFootprintColumns = docks * DOCK_LENGTH_BLOCKS * (DOCK_HALF_WIDTH_BLOCKS * 2 + 1);
			int adjustedSurfaceColumns = waterSurfaceColumns + dockFootprintColumns;
			int adjustedDeepWaterColumns = deepWaterColumns + docks * DOCK_LENGTH_BLOCKS;
			return adjustedSurfaceColumns >= MIN_HARBOR_WATER_SURFACE_COLUMNS && adjustedDeepWaterColumns >= MIN_HARBOR_DEEP_WATER_COLUMNS;
		}
	}

	private record HousingSite(BlockPos origin, Direction facing) {
	}

	private record AnchoredStructureSite(BlockPos origin, Direction facing) {
	}

	private record BuildSiteInfrastructure(
		Set<BlockPos> incompleteCarpenterWorkshopWorkstations,
		int completedTradingPosts,
		int incompleteDocks,
		int incompleteLighthouses,
		int incompleteTradingPosts,
		int incompleteCarpenterWorkshops
	) {
	}

	private record BlueprintRelativePos(int right, int forward, int up) {
	}

	private record BlueprintRelativeStep(int right, int forward) {
	}

	private record StructureBlueprint(int minRight, int minForward, int clearHeight, String[][] layers, String[][] orientations) {
		private StructureBlueprint(int minRight, int minForward, int clearHeight, String[][] layers) {
			this(minRight, minForward, clearHeight, layers, null);
		}

		private int maxRight() {
			return minRight + layers[0][0].length() - 1;
		}

		private int maxForward() {
			return minForward + layers[0].length - 1;
		}
	}

	private enum StructureKind {
		BUTCHER_SHOP,
		CARTOGRAPHER_HOUSE,
		CARPENTER_WORKSHOP,
		DOCK,
		FLETCHER_HUT,
		FORESTER_WORKSHOP,
		HOUSING_SHELTER,
		LIGHTHOUSE,
		ROADWRIGHT_WORKSHOP,
		SIMPLE_HOUSING_SHELTER,
		TRADING_POST
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
