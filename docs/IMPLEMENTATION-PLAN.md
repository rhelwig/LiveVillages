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
- Keep `Fisherman` output consistent between loaded behavior and abstract settlement simulation, with dock and lighthouse bonuses preserved and docked-boat trips returning before gathering.
- Keep `Baker` output consistent between loaded behavior and abstract settlement simulation, with settlement stock, tier-gated recipes, visible bakery displays, bakery-side trading, shared case-style bakery UI access, two-shelf `3x2` case rendering, and structure-wide display restocking all drawing from the same goods state.
- Split bell-triggered loaded defense cleanly between civilian panic behavior and combat-capable villager rally behavior.
- Finish migrating active issue tracking to GitHub issues and keep the placed `Trade Board` cosmetic display issue there as the source of truth.
- Temporarily run worker productivity at `2x` during feature development so construction, roadwork, and other visible villager loops are faster to evaluate in playtests; retune later for normal play balance.

## Current Foundations

Already solid enough to plan from:

- settlement persistence, village autodetection, route state, and Trade Board UI/trading
- staged construction with persistent build sites, assisted player placement, and preview overlays
- an in-game structure capture hotkey that exports the looked-at structure as a text blueprint draft for later curation
- custom anchors and workstations for `Trade Board`, `Carpenter's Bench`, `Forester's Table`, `Surveyor's Table`, `Portmaster's Anchor`, `Lighthouse`, `Milepost`, `Simple Housing Shelter`, and `Housing Shelter`
- a first-pass `Glass Display Case` block exists as a full-size clear bakery display block, with block-local sale inventory, a visibly glass top, direct purchase interaction, and authored-facing support that staged bakery construction should preserve
- `Glass Display Case` should now support copper, iron, gold, and diamond variants that behave like the base case for bakery display logic, sale menus, and break/drop handling
- `Glass Display Case` and `Baker's Counter` now share the same block-local case-style sale inventory/menu model, so bakery sale stock can diverge from the settlement's abstract food reserves and bakery goods can expose ingredient-themed barter alongside emerald buys
- only bakery-hosted `Glass Display Case` blocks should use the bakery sale / bounty behavior; loose non-bakery cases should fall back to plain `6`-slot storage and should not inherit bakery context just because they sit inside the same settlement radius
- loose non-bakery `Glass Display Case` variants should still sync visible in-world item displays from their local `6`-slot storage, while bakery-wide refreshes only manage the actual bakery host positions so standalone case visuals are not deleted by nearby bakery updates
- bakery sale screens should support both capped-bulk barter and a worse-rate single-item convenience barter, while keeping the emerald purchase as the clearer default full-width action
- bakery sale screens should allow top-offs into matching occupied display stacks, cap the bulk purchase action at `12` items, and let settlement-defense freebie claims convert the single-item bakery action into a `Free` baked-good pickup when owed
- bakery sale screens should now expose a bakery-specific `Bounties` view beside the `Shop`, with a compact two-column missing-ingredient list driven by currently unlocked bakery recipes; the Trade Board should reuse that same `Bounties` pattern for settlement wants while keeping player-side exchange in the `Trade` tab
- bakery sale screens should keep tab labels, explanatory copy, and hover details readable within the tighter bakery layout: `Shop` should explicitly teach left-click / right-click donation behavior, and `Bounties` should spend its hover detail space on `Could bake now: ...` style output guidance rather than repeating ingredient names
- bakery `Bounties` should behave as a read-only info tab and can reclaim the old slot/inventory screen area instead of preserving the `Shop` inventory layout underneath; hidden `Shop` slots and player inventory items should not keep rendering through the tab
- bakery display restocking should treat the structure's `Baker's Counter` and `Glass Display Case` blocks as one shared display pool: top off matching stacks first, then prefer the least-filled display so goods spread visibly across the storefront instead of piling into the first case
- donated bakery ingredients should be recognized from bakery display slots and workstation sale slots, then swept into the bakery's internal chest so they stay bakery-available without leaking into the settlement's general stock; equivalent ingredient variants such as modded egg items should still satisfy the matching bakery ingredient key when appropriate, and the sweep should apply to any bakery recipe ingredient rather than only ingredients for the currently unlocked tier
- bakery ingredient donations should reconcile promptly on player interaction instead of waiting for a later bakery work tick to clear out of visible display stock
- workstation staffing should use a shared settlement-wide hiring pass instead of fixed `ensure...` call order, so scarce unemployed adults are steered toward the highest-priority reachable open jobs and profession pickup happens on the short loaded-maintenance cadence rather than only on long economy cycles
- a first-pass `Baker's Counter` workstation exists as a wood-framed open-backed bakery display counter; it starts or resumes `Bakery` build sites, carries its own sale inventory even as a standalone block, and anchors the first-pass `Baker` profession
- the first-pass `Bakery` blueprint now exists as a `Trading Post`-derived copy with `Glass Display Case` runs in place of the original fence-only market openings
- custom professions for `Trademaster`, `Carpenter`, `Baker`, `Forester`, `Portmaster`, and `Roadwright`
- custom villager profession overlays should exist for active custom roles so loaded villagers do not fall back to missing-texture placeholders; `Baker` now needs to stay aligned with the established white-and-light-blue bakery presentation
- harbor map, terrain-memory cache, lighthouse support, and water-trade infrastructure bonuses
- loaded-world work loops already exist in first-pass form for construction, butchery, baking, harbor work, routes, and farming support
- staged construction placement should stay support-aware so workers do not place visibly floating slabs, stairs, or other disconnected pieces before their supporting neighbors exist
- debug and inspection support exists through overlays, build previews, and `/livevillages settlements ...` commands

## Active Workstreams

### 1. Economy, Harbors, And Trade

Status: In progress

Focus:

- keep abstract and loaded settlement outputs aligned, especially for harbor-adjacent production such as `Fisherman`
- keep loaded `Fisherman` boat trips bounded to dock-adjacent water and back so they stay visible without stranding villagers past the evening gathering
- make infrastructure bonuses visible in actual trade cadence, quality, and route usefulness, not only in hidden scoring
- surface active `Trading Post` and other build-site progress directly in the board/overlay project views so route/build/trade state is legible before retuning deeper priorities
- keep Trade Board goods panes usable with longer inventories by scrolling instead of truncating rows, and keep row selection visuals aligned with the actual click targets
- keep Trade Board inventory rows aggregated by exact item type so duplicate stacks do not crowd the list, while still letting village stock retain the abstract recognized-goods model where that supports settlement simulation
- keep Trade Board player-row refreshes authoritative after donations and trades so aggregated exact-item rows update counts immediately and do not break when the first contributing stack is exhausted
- keep Trade Board transaction refreshes recomputing build-site material demand immediately so `Give 1`, `Bundle`, `All`, and village-side trades always leave `Need` counts and stored stock in sync
- keep miner-facing support and extraction goods legible in the Trade Board: `dirt`, `ladder`, `raw iron`, and `raw copper` should be known early, while `redstone` and `diamond` unlock as known board goods at settlement `Tier 2`
- keep non-empty carried containers such as `Bundle` separate from aggregated rows, preview their contents on hover, and donate their contents into settlement stock without trading away the still-useful empty container
- keep Trade Board shortage/surplus output accurate when construction blockers, protected reserves, or build-site demand are involved
- keep automatic ore / glass refining fuel-aware so the village spends real `coal` / `charcoal` / wood fuel, prefers efficient fuel sources, and preserves protected wood reserves when possible
- keep route-transfer demand aware of active staged build-site materials so visible construction stalls can be supplied by trade
- preserve completed harbor/trade build-site effects for route planning even while the corresponding chunks are unloaded
- keep route growth limited by reachability and active planning state rather than a small hard cap on total connections
- keep Roadwright land-route planning dry and harbor-aware so survey/roadwork intent stops at shore access instead of extending explicit improvements over water
- push Roadwrights toward external settlement corridors and `Mileposts` once core local path connectors are already in place, instead of overbuilding low-value internal spurs
- let trade demand support limited brokered wants for downstream partner shortages so multi-hop trade networks can emerge
- unblock recipe/data-driven content so new recipes are dependable before more professions are added
- keep emerald accumulation and trade incentives compatible with future settlement-tier unlock thresholds

### 2. Settlement Advancement And Tiers

Status: Planned

Focus:

- add persistent settlement-tier state instead of a volatile wealth-only check, with `Scribe` support preventing regression after a tier has already been reached
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
- keep villager defense contributions legible in loaded combat with lightweight attack sounds or particles, rather than invisible help that players cannot verify
- keep evening behavior coherent: miners should surface in time for the social gathering, and home-bed assignment should settle into quick repeatable nightly returns instead of fresh bed scrambles
- keep bed-linked profession staffing driven by real built beds in the linked structure, so structures such as the `Bakery`, `Trading Post`, `Carpenter's Workshop`, `Roadwright's Workshop`, `Forester Workshop`, and `Fletcher Hut` open worker slots only as their own beds are actually built
- keep villager home ownership distinct from the actual nightly bed target, so a villager can keep a preferred assigned home bed yet temporarily sleep in another reachable bed when pathing to the preferred one fails
- keep forester planting conservative near settlement structures and existing trees: new saplings should stay at least a few blocks clear of structure footprints, nearby saplings, and mature tree bases so forestry reads as managed spacing rather than crowding
- treat underground worker settlement membership as a horizontal-radius cylinder for census and home-assignment purposes so deep miners still count and keep their village behavior
- extend loaded Miner work beyond the starter shaft with first-pass primary side tunnels about every `5` levels, using temporary `dirt` / `cobblestone` floor supports when needed so adjacent exposed ore is not left untouched beside the ladder run
- keep evening gathering anchored sensibly for bell-less settlements: prefer the `Trade Board` / `Trading Post`, then another central non-home POI, instead of dropping straight to an arbitrary center point, and prefer a standable nearby access tile over the raw POI block when choosing the loaded gathering target
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
- keep the Miner rollout split between the now-staged `Mine Entrance` build, the new first-pass loaded shaft-deepen / exposed-vein / shaft-lighting / cave-breach / stone-fallback loop with visible shaft descent and end-of-day ascent, the new fuel-aware raw-ore refining support for `iron` / `copper`, and later broader tunnel-expansion behavior
- keep profession-linked housing sticky where structures provide beds, while leaving Miners dependent on ordinary settlement housing rather than inventing beds in the `Mine Entrance`
- treat bed-bearing profession structures as the first home-bed source for villagers assigned to that workstation, so workforce capacity and home assignment stay coupled instead of competing with general settlement beds
- keep Mine Entrance preview readable for excavation-heavy footprints; planned shaft voids should be visible in the wireframe instead of disappearing, and the site rules should require a clear front entry approach without forcing the whole hillside facade to be carved back
- improve physical stock pickup and deposit behavior so visible worker logistics match settlement accounting more closely
- replace overly magical auto-crafting assumptions over time with clearer workstation-linked supply flow
- keep biome-aware structure palettes in sync across previews, staged builds, and contributor docs
- keep settlement naming biome-aware too, so local names fit the terrain and hostile sites read as threatening instead of pastoral
- add a later blueprint-import workflow that can take an exported blueprint file, let the player pick it through a file-selection dialog, package it into a generic named construction-anchor block, and start staged villager-assisted construction from that imported plan
- add a later `Tier 3+` luxury-upgrade pass where a few profession structures can prefer imported foreign-biome style variants to create prestige-material trade demand
- keep blueprint symbols explicit for contributors; reserve shorthand aliases like `C` for backward compatibility only
- keep directional blueprint row order standardized for contributors: first row is the rear, last row is the front unless an inline comment documents an intentional exception
- keep a contributor-friendly blueprint authoring guide so non-technical builders can supply layered structure designs without reading Java internals
- the first-pass `Mason` workshop and loaded masonry-support loop now exist; `Mason` now prefers stone-heavy staged construction blocks while `Carpenter` prefers wood-heavy ones, and idle `Mason`s can top up `Tier 1` cobblestone stock at the `Stonecutter`; keep follow-up work focused on richer fortification labor, broader stone-stock processing, and later-tier material-upgrade passes
- `Mason` now also cuts finished `milepost` goods at the `Stonecutter` from surplus stone stock, while `Roadwright` consumes that stock to place real roadside markers beside established external routes instead of conjuring markers directly
- first-pass loaded `Milepost` placement now keeps markers outside settlement bounds, prefers settlement-edge entry corridors and obvious established-road junctions, aims for roughly `100`-block spacing along land routes, skips duplicate placements when another `Milepost` is already within about `50` blocks of the desired spot, and lets outward roadwork resume from the farthest already-established route anchor instead of restarting every external push from the village core
- the `Surveyor's Table` road overlay now scans loaded path surfaces more faithfully instead of dropping most road columns or missing gravel / shallow-buried road blocks, and loaded `Roadwright` planning now fans out from the settlement core and best existing route anchors so local POIs, building entrances, mileposts, and nearby land-route settlements tighten into one visible network more reliably
- the fog-off `Surveyor's Table` map now needs to behave like a full loaded-world survey rather than a sparse debug sample: road/path caps should be high enough for larger settlements, authored build footprints and obvious world-built structures such as palisades should render as building/project coverage, and natural `stone` cliffs should not be mistaken for improved road surfaces
- survey fortification detection should stay strict enough to show adjacent log-and-slab palisades without turning ordinary tree trunks into fake building dots, and the map should also expose shoreline or harbor water so docks and coastal defenses read clearly
- `Trade Board` founding previews now work outside existing settlements: a board inside a settlement radius joins that settlement, a board outside all settlement radii can found a new `Tier 1` settlement when clear, and placement should reject too-close founding attempts with a visible message instead of silently linking across open terrain
- natural flowers and similar loose vegetation should stay clearable during structure placement, while bordered garden beds, potted flowers, and other obviously decorative planted structures still count as blockers

### 5. Roads, Maps, And Route Planning

Status: In progress

Focus:

- keep `Roadwright` responsible for both internal route quality and outward settlement connections
- keep route vocabulary explicit: routes are traversable links between POIs, while paths or roads are the visible terrain improvements laid along those routes
- keep the `Surveyor's Table` map sourced from current loaded-world truth so visible roads and improvements do not lag behind villager survey chores
- keep custom `Trade Board`-founded settlements on the same effective footprint as regular villages so shoreline anchors, lighthouses, and other edge infrastructure are not silently excluded by a smaller radius
- keep shoreline harbor structures using land-side access targets only; roadwright path planning should not treat air above water as a valid standable route endpoint
- extend route quality improvements without regressing the bounded-search and bounded-per-tick performance model
- add bounded catch-up for visible road/path improvement so revisiting a settlement shows progress without requiring constant player babysitting
- keep route planning biased toward existing path surfaces and nearby `Mileposts`, and keep first-pass route targets centered on bells, structure entrances, standalone workstations, civic anchors, and neighboring settlements
- make internal path materials and road-furniture upgrades respect settlement tier and unlocked civic quality
- keep `Surveyor's Table`, harbor map views, and `Milepost` guidance useful for both debugging and gameplay

### 6. Profession Rollout

Status: Planned

Next in line:

- finish broader loaded-world `Butcher` polish where current first-pass behavior is still coarse
- preserve the current harbor-focused `Portmaster` and boat-assisted `Fisherman` pass before adding new maritime scope
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
- Settlement advancement uses four civic tiers. Settlements should rise immediately when they meet a new tier gate, but regression protection should come from `Scribe` support rather than from a universally sticky never-downgrade rule.
- `Tier 3+` may unlock selective imported style preferences for a few structures, but those should behave like luxury upgrades rather than forced global palette swaps.
- Recognized workstations and structure anchors should start staged builds when possible; do not fall back to instant full structure placement.
- Craftable structure-anchor blocks are the preferred trigger for non-workstation civic structures unless a vanilla workstation is the better anchor.
- The `Trade Board` should show highest-priority settlement-wide wants; profession structures should handle narrower specialist trades.
- Trade Board trading stays centered on `Trade` and `Bounties`: `Trade` handles player-selected exchange and direct donation, while `Bounties` exposes the settlement's highest-priority wants without pretending that wants are a second full trade list.
- The Trade Board goods panes are a mixed widget surface: keep `ObjectSelectionList` for the left-side `Trade` inventory list, while `Bounties`, map screens, and recipe grids stay custom because they are not a good one-column list fit.
- Non-empty carried containers should use a dedicated `Donate contents` path instead of the normal donation buttons, and their contents should move into village stock rather than storing the full container as opaque stock.
- Inter-settlement trade should eventually support direct `emerald` movement and a small amount of brokered demand for goods wanted by downstream partner settlements.
- Keep refining how arbitrary donated inventory goods are priced, grouped, and explained once they enter the village-side trade inventory, but keep settlement wants visible through the dedicated `Bounties` tab even when the player currently lacks matching goods.
- Player-placed vanilla `Cartography Table` blocks should anchor a staged `Cartographer's House`.
- Player-placed vanilla `Smoker` blocks should anchor a staged `Butcher Shop`.
- The vanilla `Fletching Table` should remain a real player-use arrow workstation with material-arrow variants rather than staying decorative.
- Contributor onboarding docs should keep a beginner-friendly Windows path available: VS Code, GitHub, Temurin JDK, and repository clones under the user's home `Projects/` folder by default.
- Contributor docs should also keep GitHub bug reporting easy for young playtesters, visual contributors, and coders, with a short plain-language path instead of assuming heavy issue-template discipline.
- Contributor docs should define a plain-language glossary, a generic playtest guide, and a clear asset-layout guide, and source-art files such as Blockbench projects should live outside shipped runtime resources.
- Contributor docs should also explain, in plain language, how commits, syncing with `origin/main`, and pull requests fit together so contributors do not mistake a local commit for a project-submitted change.
- `AGENTS.md` should reinforce those contributor Git/GitHub best practices so AI-assisted contributors get branch-state checks, stale-branch rescue guidance, logical commits, and pull-request reminders by default instead of only when they remember to ask.

## Performance Guardrails

These matter enough to keep in the plan:

- Prefer cached scans, remembered intent, and bounded work queues over repeated broad rescans.
- Broad loaded-world analysis must have an invalidation or gradual refresh strategy.
- Avoid full-area scans inside hot tick paths unless the work is explicitly throttled and measured.
- When adding new loaded simulation, favor local surveys plus saved state over rebuilding the whole settlement picture each tick.
- If a feature risks becoming expensive, add timing logs or similarly measurable instrumentation early.

## Later Scope

Keep these visible, but not as active work:

- `Beekeeper`, later Miner behavior beyond the new first-pass shaft-deepening, ladder descent, exposed-vein, shaft-lighting loop, first-pass primary side tunnels, and first-pass fuel-aware `iron` / `copper` refining, including broader secondary tunnel expansion, richer mined-resource stock handling, broader ore refining, and tunnel-lighting preferences, `Gardener`, `Guard`, `Leatherworker`, `Shepherd`, `Cleric`, `Armorer`, `Weaponsmith`, `Scribe`, and fuller warehouse behavior
- later civic designation blocks such as apartment or keep anchors
- expanded street-light, bridge, and broader civic-improvement behavior
- a generalized blueprint-import feature that may be worth extracting into a standalone sharing/build-assist mod once the import, packaging, and staged-construction UX are mature
- hostile outposts as self-building raiding settlements with different staffing and growth rules

## Maintenance Notes

- Prune completed bullets aggressively once they are no longer decision-relevant.
- Do not turn this file back into a chronological shipped-feature ledger.
- If a completed feature still matters here, keep only the one-line outcome or the durable rule it established.
