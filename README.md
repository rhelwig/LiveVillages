# Live Villages

Live Villages is a Fabric mod for Minecraft Java that makes settlements feel more self-directed. Villages already track needs, projects, stored goods, and trade routes, while the player nudges those systems by building infrastructure and changing incentives. Harbors are supported today, and hostile outposts remain part of the longer-term scope.

The project currently targets Minecraft Java `26.1.1`, Fabric Loader `0.18.6`, Fabric API `0.145.3+26.1.1`, and Java `25`.

## Current State

This repository is in active development and already contains a playable foundation rather than only design scaffolding. Current implemented systems include:

- persistent settlement simulation for population, stock, wealth, housing, comfort, security, growth, projects, and routes
- village autodetection plus saved settlement and route state
- a working `Trade Board` UI for settlement overview, shortages, surpluses, trade pressure, workforce, routes, and project summaries
- staged village construction for settlement-linked structures instead of instant full builds
- harbor trade support through `Portmaster's Anchor`, `Lighthouse`, terrain charting, and land/water trade-range bonuses
- loaded-world villager work for roles such as `Carpenter`, `Butcher`, `Portmaster`, and `Roadwright`, alongside abstract catch-up simulation when chunks are unloaded
- custom professions, workstations, structure anchors, and support tooling for inspecting and debugging settlements

The full design and implementation specification lives in [docs/SPECS.md](docs/SPECS.md).

## Player-Facing Content

Blocks and anchors currently in the mod:

- `Trade Board`: the central settlement block for status, trading, shortages, surpluses, routes, and projects
- `Carpenter's Bench`: custom workstation for the `Carpenter`
- `Forester's Table`: custom workstation for the `Forester`
- `Surveyor's Table`: route-planning and settlement-map workstation used by the `Roadwright`
- `Portmaster's Anchor`: harbor map and water-trade anchor
- `Lighthouse`: harbor infrastructure that extends water trade and navigation support
- `Milepost`: route infrastructure marker
- `Simple Housing Shelter` and `Housing Shelter`: placeable settlement housing anchors that start or resume staged shelter construction

Custom villager professions currently registered:

- `Trademaster`
- `Carpenter`
- `Forester`
- `Portmaster`
- `Roadwright`

The simulation also extends vanilla profession behavior in several areas, including settlement farming, butchery, harbor support, trading, and cartography-adjacent route systems.

## How It Plays

Live Villages is designed around influence rather than micromanagement. Instead of telling each villager what to carry or craft, the player helps a settlement by improving its conditions: adding workstations, strengthening food supply, building housing, improving roads, making docks, defending trade corridors, or resolving shortages through trade. Placed workstations are meant to become anchors for real profession buildings rather than staying as isolated utility blocks.

The `Trade Board` is the main civic block for this loop. It is where a settlement exposes what it needs, what it has too much of, which routes and projects matter, and how the player can trade against the settlement's shared stock.

In the current build, a typical loop looks like this:

- place a `Trade Board` in or near a village to inspect the settlement and trade with its pooled stock
- place infrastructure such as workstations, shelter anchors, harbor anchors, or lighthouses so the settlement has more options to expand
- use staged construction previews to see what villagers plan to build and help complete missing blocks yourself
- check settlement overlays, route summaries, and harbor maps to see whether the village is stabilizing or bottlenecked

## Controls And Debugging

Client-side controls:

- `O`: toggle the build-site preview overlay
- `I`: cycle the settlement inventory/status overlay near a detected settlement
- `P`: take a snapshot of a blueprint

Server/debug utilities:

- `/livevillages settlements list`
- `/livevillages settlements inspect`
- `/livevillages settlements rescan [radiusChunks]`
- `/livevillages settlements validate`
- `gamerule live-villages:surveyor_map_fog false|true` to toggle the surveyor fog-of-war rule; it currently defaults to `false` for playtesting

## Project Docs

- [docs/SPECS.md](docs/SPECS.md): full gameplay and systems specification
- [docs/PROFESSIONS.md](docs/PROFESSIONS.md): profession-specific design notes
- [docs/IMPLEMENTATION-PLAN.md](docs/IMPLEMENTATION-PLAN.md): active work ordering and implementation notes
- [docs/WINDOWS-DEVELOPMENT-SETUP.md](docs/WINDOWS-DEVELOPMENT-SETUP.md): beginner-friendly Windows setup guide for building and running the mod in VS Code
- [docs/BUG-REPORTING.md](docs/BUG-REPORTING.md): short GitHub issue guide for playtesters, artists, and coders
- [docs/PLAYTEST-GUIDE.md](docs/PLAYTEST-GUIDE.md): simple playtesting guide for contributors who want to help without reading code
- [docs/CONTRIBUTOR-GLOSSARY.md](docs/CONTRIBUTOR-GLOSSARY.md): plain-language glossary for project and GitHub terms
- [docs/GIT-GITHUB-FOR-NEWBS.md](docs/GIT-GITHUB-FOR-NEWBS.md): beginner-friendly guide to commits, syncing, branches, and pull requests
- [docs/ART-ASSET-GUIDE.md](docs/ART-ASSET-GUIDE.md): where textures, models, and Blockbench source files should live
- [docs/STRUCTURE-BLUEPRINT-GUIDE.md](docs/STRUCTURE-BLUEPRINT-GUIDE.md): human-friendly guide for designing importable structure blueprints
- GitHub issues: active bug and polish tracking

## Development

Build with the Gradle wrapper:

```sh
./gradlew build

on Windows
./gradlew.bat build
```

Run a development client with:

```sh
./gradlew runClient

on Windows
./gradlew.bat runClient
```

The repository currently assumes Java `25`.
