# Live Villages

Live Villages is a Fabric mod for Minecraft Java that turns villages into persistent, self-directed settlements. Villages track population, housing, stock, wealth, construction projects, routes, workstations, and professional labor, while the player influences those systems by building infrastructure, solving shortages, opening trade, and defending the settlement.

The project currently targets Minecraft Java `26.1.1`, Fabric Loader `0.18.6`, Fabric API `0.145.3+26.1.1`, and Java `25`.

## Current State

Live Villages is in active development and already has a playable foundation. The current focus is completing MVP tier 1, creating promotional material such as screenshots and videos, and publishing an initial version. It is not a finished release, but the current build includes real settlement state, visible loaded-world work, staged construction, and several custom blocks, items, and professions.

Implemented systems include:

- persistent settlement simulation for population, stock, wealth, housing, comfort, security, growth, projects, and routes
- village autodetection plus saved settlement, route, build-site, villager-home, and profession state
- a `Trade Board` UI for settlement status, wants, surplus goods, trade offers, workforce, routes, projects, and support
- player-assisted staged construction with build-site previews instead of instant full structures
- loaded-world profession behavior backed by abstract catch-up simulation when chunks are unloaded
- land and harbor trade support through routes, docks, `Portmaster's Anchor`, `Lighthouse`, terrain charting, and route quality
- settlement-aware profession work for farming, baking, forestry, fishing, mining, roadwork, beekeeping, gardening, herding, butchery, masonry, smithing, guards, scribes, and more
- bakery storefronts with `Baker's Counter`, `Glass Display Case` variants, visible display stock, ingredient bounties, and local bakery sales
- first-pass custom tools and weapons: `Sling`, `Crooked Staff`, `Scythe`, special arrows, and profession-specific equipment presentation
- hostile outpost foundations, standing/trust, raids, outpost stock, and martial/logistics planning in active development

The full design and implementation specification lives in [docs/SPECS.md](docs/SPECS.md).

## How It Plays

Live Villages is designed around influence rather than micromanagement. You do not assign every villager a task by hand. Instead, you place useful infrastructure, provide missing materials, improve safety, create roads and harbors, and trade with the settlement's shared stock.

A typical play loop:

- place or find a `Trade Board` to inspect a village's needs, projects, stock, workforce, routes, and trade offers
- add workstations or structure anchors so the village can recruit professions and start new builds
- use the `O` preview overlay to inspect staged build sites and see what is missing or blocked
- donate or trade materials to clear shortages and help villagers complete construction
- improve farms, bakeries, roads, defenses, forests, mines, livestock areas, docks, and lighthouses so the settlement becomes more capable
- watch loaded villagers perform representative work while unloaded settlements continue through abstract simulation

The goal is for a settlement to feel like a place with its own needs and plans. The player is important, but the village should not feel like a static decoration waiting for commands.

## Player-Facing Content

Current custom blocks and structure anchors include:

- `Trade Board`: the central civic block for settlement status, wants, routes, projects, and trade
- `Baker's Counter`: bakery workstation and shared bakery sales counter
- `Glass Display Case`: bakery display and storage block, including copper, iron, gold, and diamond variants
- `Carpenter's Bench`: custom workstation for the `Carpenter`
- `Forester's Table`: custom workstation for the `Forester`
- `Surveyor's Table`: route-planning and settlement-map workstation used by the `Roadwright`
- `Portmaster's Anchor`: harbor map and water-trade anchor
- `Lighthouse`: harbor infrastructure that extends water trade and navigation support
- `Milepost`: route infrastructure marker
- `Gardener Workstation`, `Honey Separator`, `Scribe Desk`, and `Guard Post`
- `Simple Housing Shelter` and `Housing Shelter`: placeable settlement housing anchors that start or resume staged shelter construction

Custom and expanded professions include:

- custom roles such as `Trademaster`, `Carpenter`, `Baker`, `Forester`, `Portmaster`, `Roadwright`, `Scribe`, `Guard`, `Gardener`, and `Beekeeper`
- expanded vanilla roles including `Farmer`, `Butcher`, `Fisherman`, `Shepherd`, `Mason`, `Cartographer`, `Cleric`, `Librarian`, `Leatherworker`, `Armorer`, `Toolsmith`, `Weaponsmith`, and `Fletcher`

Craftable custom items include:

- `Sling`: fast, low-power ranged weapon with built-in stone ammunition
- `Crooked Staff`: fast wooden improvised weapon with a first-pass herding use
- `Scythe`: slow iron farm tool and weapon with crop-harvest and trimming utility
- `Copperhead Arrow`, `Ironhead Arrow`, and `Diamondhead Arrow`

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
- `gamerule live-villages:daily_settlement_reports false|true` to toggle end-of-day report files; it defaults to `false`
- `gamerule live-villages:surveyor_map_fog false|true` to toggle the surveyor fog-of-war rule; it currently defaults to `false` for playtesting

## Project Docs

- [docs/PROMO.md](docs/PROMO.md): public-facing Modrinth/CurseForge style description copy
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
```

On Windows:

```bat
gradlew.bat build
```

Run a development client with:

```sh
./gradlew runClient
```

On Windows:

```bat
gradlew.bat runClient
```

The repository currently assumes Java `25`.
