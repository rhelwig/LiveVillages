# Live Villages Structure Blueprint Guide

This guide is for people who want to help design buildings for Live Villages without digging through Java code.

Right now, structure blueprints still live in code and get imported manually. This document explains the format we need from builders so a design can be turned into a real mod structure with minimal translation work.

## Quick Start

If you want to submit a structure design, give us:

- the structure name
- which side is the front
- one text grid per height layer, from bottom to top
- optional matching orientation grids for blocks that must face a certain way
- a few notes for anything unusual, especially beds, workstation placement, or intended materials

You do not need to worry about Java classes, saved-data coordinates, or internal IDs. The important part is making the layer diagrams clear and consistent.

## How A Blueprint Is Read

Think of each layer as a top-down slice of the building.

- Every layer must use the same width and depth.
- Layer `0` is the ground/foundation layer.
- Higher layer numbers are one block higher each time.
- In each text layer, the first row is the back of the building.
- In each text layer, the last row is the front of the building.
- In each row, the first character is the left side of the building.
- In each row, the last character is the right side of the building.

In other words: if the building's front is at the bottom of your diagram, the blueprint is aligned the way the mod expects.

## Placement Symbols

These are the symbols currently used by the codebase. We can add more explicit material symbols later if the structure set grows, but new designs should avoid ambiguous shorthand.

| Symbol | Meaning | Notes |
| --- | --- | --- |
| `A` | Air / intentionally empty space | No block is placed here. Use this for room interior, openings, and gaps. |
| `B` | Bed or slab | This is context-sensitive in the current code. On sleeping spots it becomes a white bed; in roof/cap positions it becomes a wood slab from the local biome palette. If you use `B`, include a note about which spots are meant to be beds. |
| `D` | Door | Doors are two blocks tall. Put `D` in the lower layer and another `D` directly above it. The actual door wood follows the local biome palette. |
| `F` | Fence | Uses the local biome's wood family in the current implementation. |
| `G` | Fence gate | Uses the local biome's wood family in the current implementation. |
| `H` | Chest | Faces automatically unless special handling is added later. |
| `K` | Campfire | Used where a structure needs a fire/cooking point. |
| `L` | Log | Uses the local biome's wood family in the current implementation. |
| `M` | Stone-family block | Uses the local stone palette in the current implementation. |
| `N` | Hanging lantern | This is a hanging lantern, not a floor lantern. |
| `P` | Planks | Uses the local biome's wood family in the current implementation. |
| `S` | Stairs | Uses the local biome's wood family and auto-facing unless an orientation layer overrides it. |
| `T` | Wall torch | Uses auto-facing unless an orientation layer overrides it. |
| `V` | Glass / window block | Usually glass blocks; the cartographer house currently uses panes for this symbol. |
| `W` | Workstation / anchor block | This is structure-specific, such as a `Trade Board`, `Carpenter's Bench`, `Forester's Table`, or vanilla workstation. |

## Orientation Symbols

Orientation layers are optional, but they are useful when a stair, torch, or similar block must face a specific direction.

- An orientation layer must match the size of its placement layer exactly.
- Use `.` for any spot that does not need an explicit orientation.
- Today, orientation overrides mainly matter for stairs and wall torches.

Use these symbols relative to the blueprint drawing:

| Symbol | Meaning |
| --- | --- |
| `.` | No explicit orientation override |
| `F` | Face toward the front of the building |
| `B` | Face toward the back of the building |
| `R` | Face toward the right side of the building |
| `L` | Face toward the left side of the building |
| `f` | Same as `F`, but use top-half stairs (i.e. upside-down) |
| `b` | Same as `B`, but use top-half stairs |
| `r` | Same as `R`, but use top-half stairs |
| `l` | Same as `L`, but use top-half stairs |

Lowercase only changes stair half. For torches, lowercase and uppercase point the same way.

## Important Gotchas

- `B` is the least designer-friendly symbol right now because it means two different things. If a design uses beds, tell us which `B` pairs are the actual beds.
- `C` is now a legacy alias and should not be used for new designs. Use `L`, `M`, or `P` explicitly instead.
- Using explicit symbols now leaves room to add more material categories later without redefining older blueprints.
- `W` is also not one exact block. It means "the workstation or anchor for this structure type."
- `D` only works as a real door when both halves are present.
- The final palette is now biome-aware. The same blueprint may become spruce in taiga, acacia in savanna, sandstone-heavy in desert/beach areas, or red-sandstone-heavy in badlands.
- Biome palette rules currently change material family, not symbol category. For example, a `P` may become spruce planks or acacia planks, but it does not become logs or stone just because the biome changed.
- The bundled biome palette table currently lives in `src/main/resources/data/live-villages/structure_palette_rules.json`.
- If you omit orientation layers, some blocks still work because the code has fallback rules, but explicit orientation is better for custom roofs and wall details.
- `Dock` structures are not normal layered blueprints right now. They are generated by special shoreline logic and should not be designed with this format, but they still use the local biome wood palette for logs and planks.

## Easiest Workflow For Builders

For a non-technical builder, this is the simplest process:

1. Build the structure in a creative world.
2. Stand in front of the side you want treated as the front and look at a solid block in the structure.
3. Press `P` to request a structure capture snapshot.
4. Open the exported text file in your world save's `livevillages_exports/` folder and use that as your starting point.
5. The snapshot uses the side facing you as the front, so its rows are already ordered back-to-front for that viewpoint. It does not currently infer the front from doors or other structure features.
6. Clean up or simplify the exported blueprint as needed, especially if the snapshot used fallback symbols for unusual blocks.
7. Add notes for intended materials, bed locations, workstation placement, or any spots where the captured structure should be interpreted rather than copied literally.

If you prefer, you can still author the layers by hand. In that manual workflow, keep the front of the building at the bottom of each text diagram and add an orientation diagram only where stairs, trapdoors, walls, or wall torches need a precise direction.

## Submission Template

You can hand over a design in this format:

```text
Structure name: Example Workshop
Intended role: Carpenter workshop / housing / trading post / etc.
Front side: south-facing front in my screenshots
Footprint: 5 wide x 6 deep x 4 tall
Notes:
- M should feel stone-heavy
- W should be centered on the front wall
- Two B pairs on layer 1 are beds

Layer 0
MMMMM
MPPPM
MPPPM
MPPPM
MPPPM
MMMMM

Layer 1
LPPPL
PAAAP
PAWAP
PAAAP
PADAP
LPPPL

Orientation 1
.....
.....
.....
.....
.....
.....
```

## Current Scope

This guide matches the current code-backed blueprint system used for:

- `Carpenter Workshop`
- `Roadwright Workshop`
- `Forester Workshop`
- `Fletcher Hut`
- `Butcher Shop`
- `Cartographer House`
- `Lighthouse`
- `Trading Post`
- `Housing Shelter`
- `Simple Housing Shelter`

If we later move blueprints into external data files, this guide should still be a good human-facing authoring reference, even if the exact file format changes.
