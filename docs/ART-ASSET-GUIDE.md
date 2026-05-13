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
- check GitHub issues for tracked visual bugs
- then make the file changes

Discord is for coordination.

GitHub is for tracked issues and reviewable changes.

## If You Are Not Sure

If you do not know where a file belongs:

1. Look for a similar existing asset in the same category.
2. Match that folder and naming pattern.
3. Ask on Discord if it is still unclear.
