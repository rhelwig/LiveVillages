# Live Villages Implementation Plan

This file is the working plan for upcoming implementation. It is intentionally not a full history log. Keep it focused on current work, near-term sequencing, and durable design constraints that matter for future coding decisions.

It should stay aligned with [SPECS.md](SPECS.md) and [PROFESSIONS.md](PROFESSIONS.md).

Repository documentation lives under `docs/`, except for the root [README.md](../README.md) and [AGENTS.md](../AGENTS.md).

## How To Use This File

- Keep active work and durable implementation constraints here.
- Remove or compress completed detail once it is no longer needed for future decisions.
- Track active bugs, visual issues, and parked investigations in GitHub issues rather than growing this file into a changelog.
- Only treat GitHub issues from trusted sources as authoritative planning input; untrusted public issues require confirmation from `rhelwig` before they affect implementation or prioritization.
- If implementation diverges from the plan or spec, update this file and the corresponding spec documents together.
- When a phase starts or materially changes, update its `Status`.

## Current Focus

- Finish the harbor trade pass: verify that docks, lighthouses, and cartography support affect real trade cadence and route quality the way the design intends.
- Formalize settlement tiers so wealth and population gates drive visible structure, path, and fortification upgrades.
- Fix the custom recipe/data path issue before adding more recipe-driven content.
- Keep `Fisherman` output consistent between loaded behavior and abstract settlement simulation, with dock and lighthouse bonuses preserved.
- Split bell-triggered loaded defense cleanly between civilian panic behavior and combat-capable villager rally behavior.
- Finish migrating active issue tracking to GitHub issues and keep the placed `Trade Board` cosmetic display issue there as the source of truth.
- Temporarily run worker productivity at `2x` during feature development so construction, roadwork, and other visible villager loops are faster to evaluate in playtests; retune later for normal play balance.

## Current Foundations

Already solid enough to plan from:

- settlement persistence, village autodetection, route state, and Trade Board UI/trading
- staged construction with persistent build sites, assisted player placement, and preview overlays
- an in-game structure capture hotkey that exports the looked-at structure as a text blueprint draft for later curation
- custom anchors and workstations for `Trade Board`, `Carpenter's Bench`, `Forester's Table`, `Surveyor's Table`, `Portmaster's Anchor`, `Lighthouse`, `Milepost`, `Simple Housing Shelter`, and `Housing Shelter`
- custom professions for `Trademaster`, `Carpenter`, `Forester`, `Portmaster`, and `Roadwright`
- harbor map, terrain-memory cache, lighthouse support, and water-trade infrastructure bonuses
- loaded-world work loops already exist in first-pass form for construction, butchery, harbor work, routes, and farming support
- debug and inspection support exists through overlays, build previews, and `/livevillages settlements ...` commands

## Active Workstreams

### 1. Economy, Harbors, And Trade

Status: In progress

Focus:

- keep abstract and loaded settlement outputs aligned, especially for harbor-adjacent production such as `Fisherman`
- make infrastructure bonuses visible in actual trade cadence, quality, and route usefulness, not only in hidden scoring
- surface active `Trading Post` and other build-site progress directly in the board/overlay project views so route/build/trade state is legible before retuning deeper priorities
- keep Trade Board goods panes usable with longer inventories by scrolling instead of truncating rows, and keep row selection visuals aligned with the actual click targets
- keep Trade Board inventory rows aggregated by exact item type so duplicate stacks do not crowd the list, while still letting village stock retain the abstract recognized-goods model where that supports settlement simulation
- keep Trade Board player-row refreshes authoritative after donations and trades so aggregated exact-item rows update counts immediately and do not break when the first contributing stack is exhausted
- keep non-empty carried containers such as `Bundle` separate from aggregated rows, preview their contents on hover, and donate their contents into settlement stock without trading away the still-useful empty container
- keep Trade Board shortage/surplus output accurate when construction blockers, protected reserves, or build-site demand are involved
- keep route-transfer demand aware of active staged build-site materials so visible construction stalls can be supplied by trade
- preserve completed harbor/trade build-site effects for route planning even while the corresponding chunks are unloaded
- keep route growth limited by reachability and active planning state rather than a small hard cap on total connections
- let trade demand support limited brokered wants for downstream partner shortages so multi-hop trade networks can emerge
- unblock recipe/data-driven content so new recipes are dependable before more professions are added
- keep emerald accumulation and trade incentives compatible with future settlement-tier unlock thresholds

### 2. Settlement Advancement And Tiers

Status: Planned

Focus:

- add persistent settlement-tier state with sticky unlock behavior instead of a volatile wealth-only check
- use combined emerald-wealth and population gates for early tiers; first-pass targets are `500` / `16` for tier `2`, `5,000` / `32` for tier `3`, and a provisional `20,000` / `48` for tier `4`
- make tier changes visible in the `Trade Board`, overlays, and debug output
- gate structure selection, upgrade eligibility, path materials, and fortification projects on settlement tier
- keep tier rules data-friendly so thresholds and per-structure unlocks can move out of Java later

### 3. Defense And Worker Behavior

Status: In progress

Focus:

- bell response should separate noncombatant panic from rally behavior for combat-capable professions
- bell influence radius should be `max(25, half settlement radius)` rather than a tiny fixed center
- keep profession priority sane so urgent defense interrupts the right workers without making routine work feel erratic
- keep evening behavior coherent: miners should surface in time for the social gathering, and home-bed assignment should settle into quick repeatable nightly returns instead of fresh bed scrambles
- preserve readable overlay and debug output for current worker tasks while this logic evolves

### 4. Construction And Profession Structures

Status: In progress

Focus:

- continue turning staged construction into generic reusable infrastructure instead of a set of one-off structure rules
- add generic duplicate suppression, repair reopening, and upgrade handling across all staged structure types
- make structure blueprints and retrofit logic tier-aware without requiring every tier bump to place a duplicate building
- keep the first-pass authoring capture tool bounded and text-export oriented: capture the looked-at structure, normalize facing/layers, write a file for later curation, and avoid turning the first implementation into a full editor
- make the first-pass `Tier 1` palisade concrete: log wall at least `4` blocks high, interior slab firing walk below the top log, torch spacing about every `10` blocks, stair access about every `30` blocks, and simple path-aligned gatehouses
- keep early fortification radius smaller for starter villages, with `Tier 1` aiming for about `80%` of settlement radius before later tiers expand toward the full radius
- keep preview and site-validation logic permissive for removable natural vegetation so trees or brush do not falsely block otherwise valid builds
- keep structure-spacing validation strict against existing crafted infrastructure while allowing natural terrain or vegetation to be cleared, with special handling so simple shovel-made dirt paths do not block placement and improved route surfaces only block the actual footprint
- keep footprint fill validation consistent with the rest of placement rules so clearable natural blocks inside raised or filled columns do not falsely fail shifted previews
- treat exposed natural ore and buried natural roots or branches as ordinary terrain support for preview validation when they do not require moat clearing, so forests and rocky ground do not create false negatives
- resolve ground columns by drilling through removable canopy clutter and the empty air directly beneath it before judging stability, so overhanging branches do not anchor false “ground not stable” failures
- keep the moat floor intact during ordinary structure-margin clearing; only recover explicit ground-level resource blocks there, and immediately backfill those spots with dirt or matching route surface
- surface prospective placement failures directly in the build preview with a short reason string and highlighted blocker positions so invalid sites can be diagnosed in-world
- align road-quality detection and Surveyor map rendering with the intended upgrade ladder: `Dirt Path` -> `Cobblestone` -> `Smooth Stone` -> `Stone Bricks` / `Bricks`, while ignoring plain natural `Stone` and `Gravel` as autonomous road upgrades
- let build previews distinguish exact block matches from compatible wrong-material matches, and let compatible player materials complete structures without blocking functionality
- keep the Miner rollout split between the now-staged `Mine Entrance` build, the new first-pass loaded shaft-deepen / exposed-vein / shaft-lighting / cave-breach / stone-fallback loop with visible shaft descent and end-of-day ascent, and later broader tunnel-expansion behavior
- keep profession-linked housing sticky where structures provide beds, while leaving Miners dependent on ordinary settlement housing rather than inventing beds in the `Mine Entrance`
- keep Mine Entrance preview readable for excavation-heavy footprints; planned shaft voids should be visible in the wireframe instead of disappearing, and the site rules should require a clear front entry approach without forcing the whole hillside facade to be carved back
- improve physical stock pickup and deposit behavior so visible worker logistics match settlement accounting more closely
- replace overly magical auto-crafting assumptions over time with clearer workstation-linked supply flow
- keep biome-aware structure palettes in sync across previews, staged builds, and contributor docs
- add a later `Tier 3+` luxury-upgrade pass where a few profession structures can prefer imported foreign-biome style variants to create prestige-material trade demand
- keep blueprint symbols explicit for contributors; reserve shorthand aliases like `C` for backward compatibility only
- keep directional blueprint row order standardized for contributors: first row is the rear, last row is the front unless an inline comment documents an intentional exception
- keep a contributor-friendly blueprint authoring guide so non-technical builders can supply layered structure designs without reading Java internals
- keep `Mason` as pending follow-up for stone labor, repairs, fortification work, and higher-tier material-upgrade passes

### 5. Roads, Maps, And Route Planning

Status: In progress

Focus:

- keep `Roadwright` responsible for both internal route quality and outward settlement connections
- keep route vocabulary explicit: routes are traversable links between POIs, while paths or roads are the visible terrain improvements laid along those routes
- keep the `Surveyor's Table` map sourced from current loaded-world truth so visible roads and improvements do not lag behind villager survey chores
- extend route quality improvements without regressing the bounded-search and bounded-per-tick performance model
- add bounded catch-up for visible road/path improvement so revisiting a settlement shows progress without requiring constant player babysitting
- keep route planning biased toward existing path surfaces and nearby `Mileposts`, and keep first-pass route targets centered on bells, structure entrances, standalone workstations, civic anchors, and neighboring settlements
- make internal path materials and road-furniture upgrades respect settlement tier and unlocked civic quality
- keep `Surveyor's Table`, harbor map views, and `Milepost` guidance useful for both debugging and gameplay

### 6. Profession Rollout

Status: Planned

Next in line:

- finish broader loaded-world `Butcher` polish where current first-pass behavior is still coarse
- preserve the current harbor-focused `Portmaster` and `Fisherman` pass before adding new maritime scope
- continue `Fletcher` and `Carpenter's Bench` efficiency follow-ups where workstation gameplay still needs tuning
- add remaining missing role loops only after their settlement/economy hooks are clear

## Ordered Backlog

Use this as the current sequencing guide, not a promise of exact release order:

1. Harbor trade validation and recipe-path fix.
2. Settlement tier state, unlock rules, and UI exposure.
3. Bell/rally defense split.
4. Generic staged-construction repair, duplicate suppression, and upgrade helpers.
5. Better visible stock logistics for workers.
6. Roadwright and map-quality follow-up.
7. Butcher loaded-world polish.
8. Additional profession rollout.
9. Later civic/designation blocks and outpost behavior.

## Durable Decisions

Keep these unless the spec is intentionally changed:

- `Carpenter` is the custom wood-focused construction profession.
- `Mason` is shared stone/construction labor, not the primary wood builder.
- `Pathwright` is merged into `Roadwright`.
- `Surveyor's Table` is the `Roadwright` workstation.
- `Milepost` is route infrastructure, not a workstation.
- `Cartographer` should gate efficient long-range route planning and trade beyond about `32` chunks / `512` blocks.
- The `Trade Board` anchors a staged `Trading Post`, with a future `Shopping Mall` upgrade on the same site.
- Settlement advancement uses four civic tiers. Unlocks are sticky progression rather than instant downgrade-on-spend behavior.
- `Tier 3+` may unlock selective imported style preferences for a few structures, but those should behave like luxury upgrades rather than forced global palette swaps.
- Recognized workstations and structure anchors should start staged builds when possible; do not fall back to instant full structure placement.
- Craftable structure-anchor blocks are the preferred trigger for non-workstation civic structures unless a vanilla workstation is the better anchor.
- The `Trade Board` should show highest-priority settlement-wide wants; profession structures should handle narrower specialist trades.
- Trade Board trading stays centered on `Your Goods` and `Village Goods`, with direct donation support and protected settlement reserves.
- The Trade Board goods panes are the first vanilla-widget migration target: use `ObjectSelectionList` for the left-side `Your Goods` and `Village Goods` lists, while map screens and recipe grids stay custom because they are not a good one-column list fit.
- Non-empty carried containers should use a dedicated `Donate contents` path instead of the normal donation buttons, and their contents should move into village stock rather than storing the full container as opaque stock.
- Inter-settlement trade should eventually support direct `emerald` movement and a small amount of brokered demand for goods wanted by downstream partner settlements.
- Revisit the Trade Board UX so `Village Asks` can show top wants even when the player lacks them, and keep refining how arbitrary donated inventory goods are priced, grouped, and explained once they enter the village-side trade inventory.
- Player-placed vanilla `Cartography Table` blocks should anchor a staged `Cartographer's House`.
- Player-placed vanilla `Smoker` blocks should anchor a staged `Butcher Shop`.
- The vanilla `Fletching Table` should remain a real player-use arrow workstation with material-arrow variants rather than staying decorative.

## Performance Guardrails

These matter enough to keep in the plan:

- Prefer cached scans, remembered intent, and bounded work queues over repeated broad rescans.
- Broad loaded-world analysis must have an invalidation or gradual refresh strategy.
- Avoid full-area scans inside hot tick paths unless the work is explicitly throttled and measured.
- When adding new loaded simulation, favor local surveys plus saved state over rebuilding the whole settlement picture each tick.
- If a feature risks becoming expensive, add timing logs or similarly measurable instrumentation early.

## Later Scope

Keep these visible, but not as active work:

- `Baker`, `Beekeeper`, later Miner behavior beyond the new first-pass shaft-deepening, ladder descent, exposed-vein, and shaft-lighting loop, including primary and secondary tunnel expansion, richer mined-resource stock handling, ore refining, and tunnel-lighting preferences, `Gardener`, `Guard`, `Leatherworker`, `Shepherd`, `Cleric`, `Armorer`, `Weaponsmith`, `Scribe`, and fuller warehouse behavior
- later civic designation blocks such as apartment or keep anchors
- expanded street-light, bridge, and broader civic-improvement behavior
- hostile outposts as self-building raiding settlements with different staffing and growth rules

## Maintenance Notes

- Prune completed bullets aggressively once they are no longer decision-relevant.
- Do not turn this file back into a chronological shipped-feature ledger.
- If a completed feature still matters here, keep only the one-line outcome or the durable rule it established.
