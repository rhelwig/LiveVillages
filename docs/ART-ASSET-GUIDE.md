# Art Asset Guide

This guide is for contributors making textures, icons, models, or Blockbench files for Live Villages.

It focuses on one practical question:

Where do the files go?

## Quick Rule

Put files in one of two groups:

1. Runtime assets used by the mod go under `src/main/resources/assets/live-villages/`.
2. Source art files used by contributors, such as `.bbmodel`, go under `art/`.

Do not store Blockbench source files inside the runtime `textures/` folders.

## Standard Blockbench Folder

Blockbench project files should go here:

`art/blockbench/`

Why:

- they are contributor source files
- the built mod does not need them at runtime
- keeping them out of `src/main/resources` makes the asset layout clearer
- artists can find all `.bbmodel` files in one place

## Runtime Asset Folders

These are the main asset folders already used by the mod:

- `src/main/resources/assets/live-villages/textures/block/`
- `src/main/resources/assets/live-villages/textures/item/`
- `src/main/resources/assets/live-villages/textures/entity/`
- `src/main/resources/assets/live-villages/models/block/`
- `src/main/resources/assets/live-villages/models/item/`
- `src/main/resources/assets/live-villages/blockstates/`
- `src/main/resources/assets/live-villages/items/`
- `src/main/resources/assets/live-villages/lang/`

## What Goes Where

### Block Textures

Put block texture images in:

`src/main/resources/assets/live-villages/textures/block/`

Examples:

- `trade_board_face.png`
- `milepost_body.png`

### Item Textures

Put item texture images in:

`src/main/resources/assets/live-villages/textures/item/`

Examples:

- `trade_board.png`
- `ironhead_arrow.png`

### Entity Textures

Put villager or creature textures in:

- `src/main/resources/assets/live-villages/textures/entity/`
- `src/main/resources/assets/live-villages/textures/entity/villager/`
- `src/main/resources/assets/live-villages/textures/entity/zombie_villager/`

Profession overlays live under `villager/profession/` and the matching `zombie_villager/profession/` folder. The current file, such as `forester.png`, is the `Tier 1` / fallback look. When a higher-tier uniform exists, add it beside the base file as `forester_tier2.png`, `forester_tier3.png`, or `forester_tier4.png`, and keep the same name in the zombie-villager folder. If a higher-tier file is missing, the game should keep using the next lower authored look.

Optional 128 and 256 sheets live under `live-villages/textures/entity/scale128/` and `scale256/`, using the same relative paths (`villager/profession/forester.png`, `villager/type/plains.png`, `illager/pillager.png`). Generate them with `tmp/uniform-preview/generate_hd_entity_textures.py`. The world game rule `live-villages:villager_texture_scale` chooses which set to bind.

HD profession sheets must use the real 128/256 pixel grid for narrow seams, folds, stitches, rivets, and curved or diagonal marks rather than drawing every feature in 2x2 or 4x4 logical pixels. The green-coded uniforms use `tmp/uniform-preview/reference/generated-green-uniform-detail-guide.png` as their focused reference: diagonal sash and seal plaque for the Trademaster, paired flower pockets for the Gardener, and a fitted leather vest with diagonal fastening for the Guard.

Every profession also needs its own large, distance-readable signature rather than depending on color alone: job tools or emblems, portrait-derived apron/coat construction, and tier-specific trim. The Fletcher additionally uses `tmp/uniform-preview/reference/generated-fletcher-tier-guide.png`; its pointed green cap, red feather, cross straps, and arrow/fletching marks must remain readable while the fixed hat UV keeps the eyes unobstructed.

Tier 1 profession sheets should retain the concept showcase's founding-workwear language: visible repairs and fraying plus stains appropriate to the job rather than uniform random noise. The enlarged reusable material reference is `tmp/uniform-preview/tier1-wear-reference.png`. Run the generator without arguments for the complete uniform set, or pass profession names (for example `forester carpenter`) to refresh only a focused subset without rewriting unrelated sheets.

### Block Models

Put exported block model JSON files in:

`src/main/resources/assets/live-villages/models/block/`

These are the model files Minecraft actually loads.

### Item Models

Put exported item model JSON files in:

`src/main/resources/assets/live-villages/models/item/`

### Blockstates

Put blockstate files in:

`src/main/resources/assets/live-villages/blockstates/`

These files tell Minecraft which block model to use for a placed block.

### Item Definition Files

Put item definition JSON files in:

`src/main/resources/assets/live-villages/items/`

These files tell Minecraft which model to use for the item form.

### Blockbench Source Files

Put `.bbmodel` files in:

`art/blockbench/`

If a model was built in Blockbench, keep the source file there even after exporting the final JSON and textures.

## Suggested Naming

Try to keep names aligned across related files.

Example for a block called `portmaster_anchor`:

- Blockbench source: `art/blockbench/portmaster_anchor.bbmodel`
- Block model: `src/main/resources/assets/live-villages/models/block/portmaster_anchor.json`
- Blockstate: `src/main/resources/assets/live-villages/blockstates/portmaster_anchor.json`
- Main texture: `src/main/resources/assets/live-villages/textures/block/portmaster_anchor.png`

Using the same base name makes things easier for everyone.

## If You Are Making A Visual Change

A typical visual contribution might include:

- one or more `.png` textures
- one `.json` model file
- the `.bbmodel` source file if Blockbench was used
- maybe a blockstate or item-definition update if the asset wiring changed

If you only changed colors on an existing texture, you may only need the texture file.

## Before You Start A Big Art Task

Because planning happens mostly on Discord:

- ask on Discord if someone is already working on it
- check Forgejo issues for tracked visual bugs
- then make the file changes

Discord is for coordination.

GitHub is for tracked issues and reviewable changes.

## If You Are Not Sure

If you do not know where a file belongs:

1. Look for a similar existing asset in the same category.
2. Match that folder and naming pattern.
3. Ask on Discord if it is still unclear.
