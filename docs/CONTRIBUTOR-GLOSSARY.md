# Contributor Glossary

This glossary explains common Live Villages and GitHub words in plain language.

If you are ever unsure which word to use, that is okay. Use your best guess and ask on Discord if needed.

## Live Villages Words

### Settlement

`Settlement` is the broad umbrella word.

Use `settlement` when you mean any community or site tracked by the mod, including:

- villages
- harbors
- outposts
- custom player-founded places

If you are talking about the whole system in a general way, `settlement` is usually the right word.

Examples:

- `The settlement was low on food.`
- `The settlement name looked wrong.`

### Village

`Village` means a normal civilian settlement.

Use `village` when you mean a regular villager town, especially one with villagers, homes, farms, and normal trade behavior.

Examples:

- `The village built a new road.`
- `The village had plenty of beds but no food.`

### Harbor

`Harbor` means a settlement that is strongly connected to water trade.

Use `harbor` when docks, shoreline trade, or lighthouse support are the important part of the report.

Examples:

- `The harbor should have better water trade routes.`
- `The harbor map did not show nearby water clearly.`

### Outpost

`Outpost` means a hostile settlement, such as a pillager outpost.

Use `outpost` when the place is meant to be threatening or enemy-controlled, not a normal village.

Examples:

- `The outpost name sounded too friendly.`
- `The outpost should not use village-style jobs.`

### Custom Settlement

`Custom settlement` means a player-founded settlement that started from mod infrastructure instead of vanilla village generation.

Examples:

- `I placed a Trade Board and started a custom settlement.`
- `The custom settlement did not found correctly.`

### Trade Board

The `Trade Board` is the main settlement block for checking status, needs, goods, routes, and projects.

If a problem involves settlement UI, trading, founding, or linked settlement names, it may involve the `Trade Board`.

### Workstation

A `workstation` is a block tied to a job or role.

Examples:

- `Carpenter's Bench`
- `Forester's Table`
- `Surveyor's Table`
- vanilla blocks such as `Cartography Table` or `Smoker`

### Anchor

An `anchor` is a placed block that marks where a structure or system should begin. Often these are workstations that when placed tell the settlement to start building a structure.

Examples:

- `Portmaster's Anchor`
- shelter anchors

### Blueprint

A `blueprint` is the structure plan for a building.

Blueprints describe what blocks go where so villagers can build them in stages.

### Build Site

A `build site` is the active in-world construction plan for a structure.

If blocks are missing, half-built, or being placed by villagers, that is usually a `build site` issue.

### Loaded

`Loaded` means the game currently has that area active in memory because a player is nearby enough or the area is otherwise being kept active.

Loaded behavior is the visible in-world behavior.

### Unloaded

`Unloaded` means the area is not currently active on screen or near the player.

The settlement may still simulate in the background in a more abstract way.

### Biome

A `biome` is the world environment type, such as desert, taiga, swamp, badlands, or jungle.

Biome matters for naming, materials, structure style, and visuals.

## GitHub Words

### Issue

An `issue` is a report or tracked task on GitHub.

Use an issue for:

- bug reports
- polish problems
- missing features
- questions that need a tracked answer

### Bug

A `bug` is something that works incorrectly.

Examples:

- a wrong texture
- a crash
- a road built into water
- a village screen that does not refresh

### Open Issue

An `open issue` is a problem or task that has been reported and not finished yet.

Before making a new issue, it is good to check open issues first.

### Comment

A `comment` is a reply on an issue or pull request.

Use a comment when someone already reported the same problem and you want to add more information.

### Pull Request

A `pull request` or `PR` is a proposed code or file change.

If you changed files and want the project to review them and actually include them in the project, that usually becomes a pull request.

A pull request is how you say:

`Please take the changes from my branch and add them to the real project if they are approved.`

Without a pull request, your changes may stay only on your computer or only on your own branch and never become part of Live Villages.

### Commit

A `commit` is one saved set of file changes in Git.

You can think of it as a labeled save point for work.

### Branch

A `branch` is a separate line of work.

People often make a branch so they can work on something without mixing it into the main version right away.

### Origin

`origin` is the GitHub copy of the repository that your local Git clone talks to by default.

When people say `origin/main`, they usually mean the latest main branch on GitHub.

### Sync

`Sync` usually means updating your local code with newer GitHub changes, pushing your own newer branch changes, or both.

If someone says your code should be synced first, they usually mean you should make sure you are working from the latest `origin/main`.

### Main

`main` is the main branch of the repository.

### Regression

A `regression` is a bug where something used to work but now does not.

Example:

- `The Trade Board updated correctly last week, but now it does not.`

### Reproduce

`Reproduce` means doing the same steps again and getting the same bug again.

Example:

- `I can reproduce it every time by placing a Trade Board near water.`

## Art Words

### Texture

A `texture` is an image used on a block, item, or entity.

Examples:

- wood grain on a block
- villager clothes
- item icons

### Model

A `model` is the shape definition for a block or item.

It says how the object is built in 3D and which textures go on which faces.

### Blockbench

`Blockbench` is the art tool many contributors use to make or edit Minecraft models.

For this project, `.bbmodel` files are source files for artists, not runtime files used directly by the mod.

## Discord And GitHub

For this project:

- use Discord for planning, coordination, quick questions, and checking what needs help
- use GitHub issues for bug reports and tracked work
- use GitHub pull requests for actual file changes you want reviewed

Discord is where people talk.

GitHub is where work gets tracked.
