# Live Villages Implementation Plan

This file tracks the intended implementation order for the next major profession and world-behavior changes. It should stay aligned with [SPECS.md](SPECS.md) and [PROFESSIONS.md](PROFESSIONS.md).

## Current Foundations

Already in code:

- `Trade Board`, `Trademaster`, settlement persistence, routes, and Trade Board UI
- world construction for housing, storage, composters, and `Carpenter` / `Carpenter's Bench` content
- loaded farm scanning for crops, hay, cows, and sheep
- land-route surveying based on blocks already present in the world
- current Trade Board visual pass: syncing a client-rendered settlement-name label onto the placed board faces
- the `Portmaster's Anchor` now opens a first-pass harbor trade map centered on the anchor, showing rotated land/sea context plus known ports with fitted default scale, client-side zoom, Cartographer-enabled inland settlement markers, broader Cartographer terrain charting beyond the smaller local harbor view, and a saved shared terrain cache that improves as loaded exploration observes more coast and sea
- the `Portmaster's Anchor` harbor map now shows a footer hint to hire a `Cartographer` when the village has not unlocked the wider charted view yet
- Surveyor fog-of-war observations and remembered Roadwright route-plan intent now have save/restore hooks so those map views do not need to rebuild entirely from scratch after a world reload
- staged construction now preserves the anchored workstation while replacing the support block directly beneath it, so the Trade Board stays usable during Trading Post construction
- Trading Post front-cap stair orientation is now explicit in the blueprint, and Trade Boards now periodically re-sync their settlement label server-side so the face text survives construction/state normalization
- workshop roof crest rows now use a symmetric top-stair pattern, and the Trading Post front cap now uses outward-facing stair orientation instead of sideways rotation
- simple fallback housing shelters now keep the center bed lane open and use top slabs on the door-to-bed roof strip so villagers can enter and sleep without the ceiling blocking the bed
- village structure siting now enforces a shared `3`-block spacing buffer around housing and workstation-linked buildings, and both shelter layouts now use a lower center roof lane plus an upside-down stair lintel above the door for better headroom

Known design shifts now locked in:

- `Carpenter` is the custom wood-focused construction profession
- `Mason` becomes shared construction labor instead of only background craft support
- `Pathwright` and `Roadwright` are merged into one `Roadwright` profession
- `Surveyor's Table` is the `Roadwright` workstation
- `Milepost` is route infrastructure, not a workstation
- `Cartographer` gates efficient long-range route planning and trade beyond about `32` chunks / `512` blocks
- player-placed vanilla `Cartography Table` blocks should anchor a staged `Cartographer's House`; the first implementation uses a simplified plains vanilla cartographer house blueprint with explicit orientation rows for roof stairs
- `Farmer` uses composter-anchored, player-extensible gardens with harvesting and reseeding
- `Rancher` is the design-facing name for the vanilla `Butcher` / `Smoker` role and manages herd growth, culling, pasture expansion, meat, and leather output
- `Baker` is a planned food-processing profession with a bakery structure based on the `Trading Post` footprint and specialist baked-good trades
- `Beekeeper` is a planned honey and hive-management profession with a `Honey Separator`, a village-edge `Beekeeper's Apiary`, and an all-white protective suit outfit
- recognized placed workstations should anchor their associated profession structures when possible, with a nearby `can't build here` sign when the site is blocked
- the `Trade Board`'s associated structure is a `Trading Post` / `Trade Post`, with a future `Shopping Mall` upgrade on the same site
- the `Trade Board` should list the highest-priority settlement-wide wants; profession structures should handle narrower lower-priority trades such as eggs to bakers, logs to carpenters, feed to butchers, or raw stone to masons
- the `Trade Board` trading screen now uses two selection flows: `Your Goods`, where the player selects a tradable inventory good and then chooses one settlement payout option, and `Village Goods`, where the player selects a settlement offer and then chooses which wanted player good to pay with
- the `Your Goods` flow should also let the player donate a chosen amount of the selected good directly into settlement stock without requiring the village to pay anything out
- settlement stock needed for active shortages, construction blockers, or normal target reserves must not be offered back to the player as trade payout; settlement trades should only spend stock above the protected reserve for that good
- the vanilla `Fletching Table` should become a real player-use arrow workstation with higher-yield batch recipes, vanilla `Copper Nugget` support, and a separate material-arrow family such as `Copperhead`, `Ironhead`, and `Diamondhead` arrows rather than only flint-crafted generic arrows
- the `Carpenter's Bench` should get a follow-up efficiency pass so it offers a clearer material benefit over ordinary crafting, similar to the `Fletching Table` throughput advantage
- loaded settlement maintenance should retry existing placed `Trade Board` anchors that do not yet have a `Trading Post` build site, so blocked or older boards can start construction after the world state changes without requiring the player to break and replace the board
- workstation-associated structures should be visible staged construction jobs, not instant block placement, and they should count as trade or profession improvements only after complete
- staged construction can begin with missing materials, then pause on unavailable block types until stock, villager inventory, player help, or basic auto-crafting supplies the missing blocks
- loaded villagers should visibly visit a standable adjacent access tile at the `Trade Board` or completed `Trading Post` to deposit gathered items or retrieve needed materials
- once a settlement has a `Roadwright`, internal paths should connect workstation buildings into the trade network and connected specialty buildings should bias the kinds of goods traded
- profession heuristics should move toward data-driven priorities and thresholds rather than a bespoke scripting language in the first pass
- field-worker loadouts matter, while deskworker professions stay unarmed and flee
- `Guards` are the first priority for combat gear upgrades, with `Roadwrights` next
- the `I` settlement inspection overlay should rotate through defined screens and then turn off after the last screen
- pillager outposts raid instead of trade, favor offensive professions, and can self-build inefficiently without requiring exact village profession coverage

## Performance Optimizations

### Completed Optimizations (April 2026)

Major performance bottlenecks have been identified and resolved to ensure smooth villager movement and server performance, even with larger settlements (40-50 villagers).

#### Construction Work Optimizations

- **Actionable block evaluation limiting**: `chooseConstructionTask` now scans past already completed early blueprint blocks and only runs expensive stand-position checks on the first 20 actionable candidates per build site, avoiding floor-only stalls while keeping the per-worker search bounded
- **Stand position optimization**: `standPosFor` collects all candidate positions, sorts by distance to worker, and only performs expensive pathfinding on the 5 closest positions instead of all possible positions
- **Maintenance frequency**: Construction maintenance reduced from every 40 ticks (2 seconds) to every 160 ticks (8 seconds)

#### Roadwork Optimizations  

- **A* pathfinding limits**: Reduced search node limits from 6,000/9,000 to 1,500/2,250 nodes for internal/external paths
- **Search area reduction**: Decreased search margins by ~50% and spans from 160 to 80 blocks
- **Task caching**: Increased cache time from 100 to 400 ticks to reduce redundant pathfinding, and cached both found tasks and "no task" results so repeated empty Roadwright scans do not re-run every maintenance pass
- **Roadwork helper budget**: Loaded Roadwright maintenance now limits each pass to the Roadwrights plus a small helper budget, preventing unemployed helper planning from scaling with every adult villager in the settlement
- **Instrumentation**: Added timing logs for performance monitoring

#### General Frequency Adjustments

- **Farmer maintenance**: 40 → 160 ticks (every 2s → 8s)
- **Construction maintenance**: 40 → 160 ticks (every 2s → 8s)  
- **Economy cycle**: 100 → 200 ticks (every 5s → 10s)

#### Performance Results

- **Before**: Server falling behind by 2-5 seconds, construction taking 4+ seconds, roadwork taking 0.7-5+ seconds
- **After**: No server lag, construction ~160-230ms, roadwork ~160-200ms, villagers move smoothly with minimal hesitation
- **Item pickup**: Now nearly as fast as vanilla Minecraft

#### Future Scaling Considerations

- Current optimizations support settlements with 40-50 villagers
- Additional professions will require monitoring performance impact
- Pathfinding optimizations may need further tuning for very large settlements
- Consider caching more pathfinding results or precomputing common routes

### OODA and Planning Load Review (April 2026)

Status: First pass implemented

Goal:
Keep loaded villager behavior in an explicit Observe, Orient, Decide, Act shape so expensive observation and planning work is bounded, staggered, and measurable instead of every villager reconsidering every job on the same tick.

Planned first pass:

- stagger loaded settlement maintenance across each maintenance interval instead of processing all loaded settlements on one global tick
- add short staggered daytime breaks so only a fraction of workers are actively planning at the same time
- throttle per-villager Decide work for construction, farming, forestry, roadwork, carpentry, and item pickup while allowing active construction deliveries to continue
- preserve existing timing logs and keep adding narrower logs where planning still spikes
- later, move toward persistent per-villager task state and shared observed-world caches so Observe and Orient happen once per settlement slice, not once per worker

Shipped so far:

- loaded farmer, resource, and construction maintenance now run through a per-settlement phase gate checked every second, spreading work across each maintenance interval instead of bunching all settlements onto one tick
- added `SettlementVillagerWorkSchedule` for short staggered daytime breaks and deterministic per-villager Decide throttling
- farming, construction, roadwork, forestry, carpentry, and item pickup now skip new planning while a villager is off duty or outside its Decide slot
- active construction deliveries continue moving even when new construction planning is throttled, avoiding stranded carried supplies
- the settlement overlay can report villagers as `taking a break`

Next shared-scan pass shipped:

- added a shared loaded observation cache for nearby villager scans so role filters, overlays, and worker loops can reuse the same short-lived entity observation
- added in-memory Surveyor map observation from ordinary villager positions, plus center/build-site anchors, so Roadwright map data can be refreshed by fellow villagers instead of full-map rescans or Roadwright-only travel
- Surveyor maps now read from that observed memory, while opening a `Surveyor's Table` refreshes observations on demand and Roadwright settlements refresh observations during loaded construction/roadwork maintenance
- observed villager positions are included in the map snapshot, with the newest observations preferred under the cap, so commonly visited areas visibly clear fog even when the villagers did not encounter a road surface or POI
- Roadwright overlay task labels now use fresh cached path tasks instead of triggering path planning during overlay refreshes
- Surveyor observation refreshes are now capped to a rotating villager subset and fixed scanned-column budget, with `loaded_surveyor_observation` timing logs that include settlement tick, center, and population context
- construction task selection now scores candidate blocks with cheap standable-position checks and only runs navigation path checks for the final chosen task, reducing repeated `standPosFor` pathfinding during multi-worker build-site planning
- active construction deliveries held by unavailable workers or workers reserved for higher-priority Roadwright work are now returned to stock and unclaimed after the short active-delivery timeout, so stalled build-site blocks can be reassigned instead of remaining stuck behind a missing or busy worker
- Surveyor map fog now retains more observed villager travel points and reveals the full bounded observation radius, making gradual map updates more visible without broad synchronous map scans
- Roadwright task caching now lasts longer while the cached task remains useful, reducing repeated 100ms+ path-search bursts in loaded roadwork maintenance
- build-site preview blocks now include material metadata; matching held blocks are highlighted, and preview-assisted placement writes the planned block state server-side so player help uses the structure's intended orientation while ordinary non-preview placement remains vanilla; toggling the preview off sends an explicit inactive signal so assisted placement stops immediately
- construction deliveries now carry up to `32` matching materials per stock trip and retarget the next matching pending block after each placement until the carried batch is exhausted
- construction delivery cleanup now returns unused batch remainders to stock and persists completed delivery removals, preventing stale claimed blocks or silently lost carried materials after worker or player placement
- Surveyor observation memory now lasts `72,000` ticks, so map discoveries persist for several in-game days while retained road, point, and observed-area caps still bound memory use
- added a `live-villages:surveyor_map_fog` gamerule flag that defaults off for playtesting; with fog disabled, opening the `Surveyor's Table` now does an immediate fully revealed map snapshot from loaded-world data, while background observed-area maintenance stays off and `surveying` remains a Roadwright status label rather than a requirement for map refresh
- Surveyor maps now show the current in-game day and an approximate time label in the title bar, and the Roadwright overlays are split into a short-horizon `Planned work` forecast based on nearby Roadwright/helper capacity versus a longer-horizon `Route trace` view of broader multi-week corridor intent
- Roadwright cached and remembered route plans now recompute from their original start and target when local terrain changes, so flattening or lightly reshaping a corridor is less likely to make the route disappear from the map

## Ordered Work

### 1. Farmer and Garden Behavior

Status: Complete

Goal:
Make loaded farmers actually work composter-anchored gardens in the world instead of only contributing through crop surveys.

Primary changes:

- composter-anchored farm territories now drive loaded farmer task selection instead of only tiny connected starter beds
- loaded farmers now harvest ripe crops first, collect loose crop and seed drops, and plant empty farmland from settlement stock
- villager farmland trampling is now blocked so composting behavior does not destroy managed crops
- replanting should follow settlement food shortages instead of blindly restoring the harvested crop
- imported starter crops such as `potato` or `beetroot` should enter farm rotation as soon as the settlement has seed stock
- loaded farmers should bias their pathing toward ripe crops, loose farm goods, empty farmland, ready composter output, and grass or leaf-litter cleanup tasks inside their territory
- ready composter output and loose nearby `bone_meal` should be collected and spent on the least-ready high-priority crops in the territory
- loose `leaf_litter`, including flat ground litter blocks, should be gathered into the composter, and trimming grass should be able to yield both seeds and a small randomized amount of fresh leaf litter for composting
- define a farmer territory as the farmland and cleanup area anchored to a specific `Composter`
- support raised-bed starter gardens while allowing player-built farmland and surrounding cleanup space near that composter to join the same territory
- reserve seed stock for each active garden
- harvest mature crops and reseed empty garden beds
- use actual available seeds and crop types instead of assuming all farmland is always productive
- expose garden shortage or under-capacity information through settlement logic if useful
- add short-term stuck-path memory so farmers stop retrying unreachable task targets every few seconds
- add plot-quality awareness so dry, blocked, or otherwise unhealthy farmland is deprioritized until fixed

First playtest target:

- the first village with `2` active Farmers and `2` Composters in raised beds
- verify reseeding works
- verify extending the beds by the player increases usable garden area

### 2. Rancher and Herd Management

Status: Planned

Goal:
Split livestock management out of generic farmer output and make the vanilla `Butcher` slot, presented as `Rancher`, maintain herd size for both local needs and trade surplus.

Primary changes:

- detect rancher-managed pig and cow pens and pasture capacity
- feed breeding stock from settlement goods
- breed when herd size is below target
- cull adults for `beef`, `pork`, and `leather` when above target
- expand fenced pasture when crowding is high and valid terrain is adjacent
- tune target herd size from settlement population, food demand, and export goals

### 3. Carpenter and Mason Construction Refactor

Status: Carpenter complete; staged construction and exact blueprints now in progress; Mason-specific behavior still planned

Goal:
Use a clearer shared construction model with `Carpenter` as the wood-focused custom profession and `Mason` as the stone-focused vanilla profession.

Primary changes:

- split that category at least into `Carpenter` and `Mason`
- use `Carpenter` in content, professions, lang, UI text, registry ids, resource ids, and serialized project ids
- use `Carpenter's Bench` in block, item, recipe, loot, model, texture, and POI content
- use `Carpenter's Workshop` for the workshop-house construction project
- give the `Carpenter` first claim on the workshop-house bed, allow the `Carpenter` to reclaim it from other villagers, and keep player override authority
- make the `Carpenter's Bench` function like a wood-optimized crafting station similar to the `Stonecutter`
- add a `Mason's House` / `Mason's Workshop` staged structure for placed `Stonecutter` workstations, preferably based on the vanilla mason building or a close stone-heavy equivalent
- make recognized placed workstations trigger associated-structure construction attempts around the placed block, and leave a nearby `can't build here` sign when the footprint is invalid
- give worker-profession workstations one or two supported villager slots, assign associated-structure beds to matching professionals when possible, and make `Guard Post` support up to five Guards
- let Guard structures accept donated weapons, armor, shields, ammunition, pillager banners, and similar spoils of war; convert donated pillager banners into visible `Desecrated Enemy Banner` trophies on the Guard shack
- define the `Trade Board`'s associated `Trading Post` structure from the exact `5x8` blueprint in `SPECS.md`, with a future `Shopping Mall` upgrade path
- define the `Carpenter's Workshop` structure from the exact `5x8` blueprint in `SPECS.md`
- replace instant anchored builds with staged build sites that store planned block positions, completion state, selected wood family, stone quality, workstation anchor, and missing-material blockers
- make loaded workers place or remove one block per construction task action, allowing players to place correct blocks into the same footprint to advance completion
- require completed associated structures before they provide trade enhancements or profession-building bonuses
- route construction materials through settlement stock or villager inventories, with basic auto-crafting from raw materials such as logs into planks, stairs, slabs, fences, gates, and doors
- prevent duplicate structure queue entries while any equivalent build, upgrade, or repair site is already in progress; this should apply to all staged structures rather than only the current `Carpenter's Workshop` first pass
- reopen damaged completed staged structures as repair work sites instead of queuing duplicate replacement structures
- make incomplete structures resume when the missing material appears through production, gathering, trade, or player deposits
- make professional high-priority work outrank generic construction while allowing non-guard villagers to finish the current one-block construction action before switching tasks
- make guards interrupt construction immediately for imminent hostile-mob threats
- show loaded villagers periodically visiting the `Trade Board` or completed `Trading Post` to deposit collected items or retrieve work materials
- later, replace assumed automatic crafting with visible workstation logistics: workers pick up raw inputs from the `Trade Board` or completed `Trading Post`, travel to the best workstation for the output, craft there, then bring batched supplies to the build site; specialists should carry more specialty materials and place specialty blocks faster
- give `Mason` explicit shared labor in construction, repairs, and upgrades
- make `Mason` handle stone `Milepost` construction and stone-material upgrade passes while `Roadwright` still owns route placement
- prioritize new structures and urgent repairs ahead of prestige material upgrades such as cobblestone-to-stone or rough-stone-to-polished passes
- split wood-heavy and stone-heavy build tasks where practical
- saved worlds from before the final `carpenter` id cutover are not expected to remain compatible

Shipped so far:

- registry ids, serialized project ids, data files, resource files, language keys, POI ids, profession ids, recipe ids, loot ids, and villager profession textures now use `carpenter` / `carpenter_bench` / `carpenter_workshop`
- settlement population and construction labor use explicit `carpenter`, `mason`, and `construction_support` counts
- internal Java constants, construction project naming, infrastructure surveys, and workshop placement helpers use `Carpenter` naming
- loaded-villager census emits `construction_support` for armorer/toolsmith/weaponsmith-style craft support
- the `Carpenter's Bench` opens a dedicated Stonecutter-style interface and a first-pass wood recipe list for logs, stems, hyphae, bamboo blocks, planks, wood-family building parts, and generic wood-heavy outputs such as chests, barrels, crafting tables, composters, ladders, and sticks
- loaded settlements now try to keep one actual `Carpenter` when a reachable `Carpenter's Bench` exists, assign existing `Carpenters` to reachable benches, and give the `Carpenter` first claim on the nearby workshop bed by evicting non-Carpenter home memories from that bed
- player-placed `Carpenter's Bench` and `Trade Board` blocks now trigger first-pass anchored structure attempts for `Carpenter's Workshop` and `Trading Post`; blocked footprints place a nearby `can't build here` sign when a sign spot is available
- staged-construction groundwork now has persistent build-site records saved at the top level of the economy state, including settlement id, blueprint id, origin, workstation anchor, facing, selected material defaults, per-planned-block status, and completion state
- placed workstation handling now creates or resumes those build-site records instead of instantly placing the associated structure; the player-placed workstation block is recorded as `player_placed`, while the remaining blueprint blocks start as `pending`
- construction material accounting now supports direct settlement stock, lightweight villager-carried goods maps, and auto-crafting from adaptable raw materials such as logs into planks, stairs, slabs, fences, gates, doors, and beds; build sites are dry-run checked on start or resume so blocked planned blocks can be marked `missing_material`
- loaded construction maintenance now assigns nearby Carpenters, Masons, construction-support villagers, and unemployed adults to staged build sites; each worker claims one pending planned block, walks toward it, clears natural obstructions or spends stock, and places the planned block when within reach
- first-pass player-assist reconciliation marks correctly placed build blocks as `player_placed`, requeues missing placed blocks if they are broken, and marks build sites complete when every planned block is placed
- completed staged `Trading Post` sites now feed the infrastructure survey and add local/route emerald trade output, while incomplete `Trading Post` sites provide no trade bonus; incomplete `Carpenter's Workshop` sites are tracked as in-progress so they do not satisfy completed workshop effects but also do not cause duplicate autonomous workshop project planning
- construction workers now use persistent delivery assignments for visible stock retrieval: they claim a planned block, visit a known `Trade Board` or `Trading Post` anchor to take or auto-craft that block from settlement stock, then carry the assignment back to place it

Deferred polish:

- the first-pass `Carpenter's Bench` recipe list is code-local and should be tuned or moved to data-driven rules later
- construction retrieval now has visible stock visits, but gathered-good deposits and richer villager personal inventories still need a separate pass
- if no `Trade Board` or `Trading Post` stock access point is known, construction retrieval temporarily falls back to the settlement center so builds do not stall; replace that fallback with a real physical stock access rule
- profession-priority rescoring is still coarse: adult non-Nitwit villagers can currently help construction, while Farmer/Guard interruption rules and specialist speed differences still need a broader task arbiter
- structure duplicate-queue suppression and repair handling should become generic helpers before more staged structures are added
- automatic construction crafting should eventually be replaced by batched supply and workstation-crafting logistics, where specialists carry and craft more of their specialty materials
- second-floor apartment additions for completed workstation structures are deferred until staged upgrades can safely add accessible multi-story housing without reviving shelter-stacking behavior

Current staged-construction implementation plan:

1. Completed: introduce explicit structure blueprints for the `Carpenter's Workshop` and `Trading Post`, update anchored footprint validation to their real `5x8` dimensions, and route the current immediate builder through those blueprints as a temporary compatibility step.
2. Completed: add persistent build-site state with anchor position, facing, blueprint id, selected material families, placed/missing block state, and completion status.
3. Completed: change placed workstation handling so it creates or resumes a build site instead of immediately placing the whole structure.
4. Completed: add construction material accounting that can spend village stock, lightweight villager-carried goods, and basic auto-crafted outputs from raw materials.
5. Completed: add loaded villager construction tasks that claim one pending block, walk to it, clear natural obstructions or spend stock, place the planned block, and re-evaluate the build site on the next maintenance tick.
6. Completed: add first-pass player-assist recognition so correctly placed blocks advance the build site and completed sites are marked complete.
7. Completed: add completed-site effects so complete staged structures unlock the intended trade or profession enhancements and incomplete sites do not.
8. Completed: add first-pass visible Trade Board or completed Trading Post stock visits for construction material retrieval, with automatic crafting still assumed at pickup time.
9. Completed: add a rebindable build-site preview key that toggles red wireframes for active planned blocks that are not built yet, using server build-site state rather than a client-only blueprint guess.
10. Completed: fix first playtest issues in staged `Trading Post` construction: protect the workstation anchor, correct second-level window positions, rotate top roof stairs into a longitudinal roofline, and place fences with blueprint-derived connections.
11. Completed: tune `Trading Post` roof stair orientation, add its final roof slab row, remove the stray `Carpenter's Workshop` roof stair while preserving its final slab ridge, and make active construction blockers add trade needs such as glass, sand, beds, and wool to the `Trade Board`.
12. Add visible deposit visits for gathered goods and fuller lightweight villager-carried inventories.
13. Generalize in-progress duplicate suppression and damaged-structure repair behavior across all staged structure types.
14. Replace automatic construction crafting with batched supply and workstation-crafting logistics.
15. Add stone-quality upgrade jobs for completed `Trading Post` structures using the same remove-and-replace task loop.
16. Later second-floor apartment upgrade pass: let compatible completed workstation structures add staged, accessible apartment floors when housing demand exceeds capacity.
17. Later profession trade pass: keep the central `Trade Board` focused on top settlement priorities, then add specialist trades at completed profession structures.
18. Later Baker pass: add `Baker`, a bakery workstation, and a `5x8` bakery structure derived from the `Trading Post` layout with baked-good production and display cases or glass-fronted shelves. Do not rely on vanilla panes as horizontal shelf lids; investigate a custom display-case block or block-entity renderer, with a plain shelf-behind-glass fallback if needed.
19. Later Beekeeper pass: add `Beekeeper`, `Honey Separator`, and a staged `Beekeeper's Apiary` with managed hives, flowers, safe campfire smoke, honey bottle and honeycomb collection, and a mostly white protective-suit villager outfit.

### 4. Roadwright and Route Infrastructure

Status: Partially Started

Goal:
Make one road profession responsible for local route creation, route upgrades, internal-path improvements, and marker placement.

Implemented first:

- add a placeable first-pass `Milepost` route marker block
- switch the first-pass `Milepost` to a `3`-block-tall `1x1` obelisk silhouette with editable block and item textures
- change the placed `Milepost` to a true stacked three-block structure so the obelisk renders correctly in-world instead of clipping to one cube
- render first-pass vertical `Milepost` labels showing the nearest non-outpost settlement on the face that points toward it
- simplify first-pass `Milepost` destination assignment so diagonal settlements label both toward-facing cardinal sides and away-facing sides stay blank
- allow a diagonal destination to claim both relevant faces in the first pass when no nearer settlement should displace one side
- correct the `Milepost` block-entity renderer so east-west destination labels land on the intended physical faces of the obelisk
- align the `Milepost` text renderer with observed in-game face placement by rendering each computed label on the opposite physical face transform
- avoid eager `Milepost` label refresh during block-entity level attach so world startup does not stall in `Loading Terrain`
- prioritize `Milepost` road targets ahead of ordinary internal expansion once the Roadwright has a center route task available
- make nearby loaded `Roadwright` planning treat placed `Mileposts` as guidance targets between internal path work and inter-settlement route extension
- show `Mileposts` on the `Surveyor's Table` map and add a corner compass rose for map orientation
- rotate the `Surveyor's Table` map to the player's current facing direction, move the compass rose into the legend column, and render off-map `Milepost` edge indicators for nearby markers beyond the current map square
- draw current loaded `Roadwright` route traces on the `Surveyor's Table` map so planned corridors are visible beyond the next edited block
- rename the overlay `Projects` section to `Queued Projects` so it does not read as contradictory with active build-site counts

Primary changes:

- add `Roadwright` and `Surveyor's Table`
- add a first-pass `Roadwright's Workshop` staged structure using the `Carpenter's Workshop` footprint with stone-family enclosed side walls, the same bed/work-bay language, and the same corrected roof profile
- remove the separate `Pathwright` concept from the design and future code
- let Roadwright establish nearby settlement connections
- let Roadwright improve both inter-settlement roads and internal town paths
- let Roadwright connect workstation buildings back into the settlement path network so those structures are included in trade logistics
- let right-clicking the `Surveyor's Table` open a basic village survey map with the settlement boundary, buildings/POIs, path quality, Roadwright positions, fog-of-war style survey coverage, and planned or in-progress path work
- make Roadwright path work start from the Roadwright's own workstation access tile, then target the village center before other POIs
- first loaded-world Roadwright priority: lay internal `Dirt Path` trails from the Roadwright workstation toward the village center, then to the outside door of known workstation structures and scanned local POIs such as placed workstations, Trade Boards, composters/gardens, Stonecutters, and exterior doors; ordinary beds and storage should remain map POIs rather than automatic route targets; reserve busy Roadwrights away from generic construction while internal path work exists; preserve existing upgraded path materials such as smooth stone; use a bounded terrain-aware route search instead of only a straight-line sampler; then extend simple trails toward known nearby settlements
- later road maturation should widen important internal routes toward a `3`-block walking surface instead of expanding every adjacent grass block into path
- make connected workstation buildings and trade improvements bias the types of goods the settlement is likely to exchange
- add comfort or civic-quality benefits for better internal path networks
- add bridge-building and maintenance rules
- place `Mileposts` along established routes at regular intervals
- add `Milepost` placement rules for village entries, junctions, and clear non-blocking roadside spaces
- add direction-facing destination labels on `Mileposts`, with optional distance display when the marker has enough readable space
- make `Milepost` material tiers such as `Stone`, `Smooth Stone`, `Polished Granite`, `Polished Andesite`, and `Obsidian` modestly improve marked-route trade value

### 5. Cartographer Long-Range Route Gate

Status: Partially Started

Goal:
Use vanilla `Cartographer` as the survey and knowledge gate for distant trade instead of inventing a second route-planning profession.

Implemented first:

- discovered the vanilla cartographer house templates in the Minecraft data set: plains, desert, savanna, snowy, and taiga
- added the first staged `Cartographer's House` from the plains template shape, anchored by a player-placed vanilla `Cartography Table` in a loaded settlement
- added optional per-cell blueprint orientation rows so stairs and other directional blocks can be specified directly instead of inferred only from footprint edges

Primary changes:

- improve or biome-select the staged house blueprint after playtesting
- after `1.0`, expand staged structures to biome-specific plans that match local vanilla village styles and remove similar-block family replacement so each plan requires its exact material set
- allow nearby routes without cartographic support
- require cartographic support for efficient route establishment or maintenance beyond about `32` chunks / `512` blocks
- improve survey freshness, route quality, or routing confidence when a `Cartographer` is present
- decide whether one cartographer is enough or whether both route endpoints should benefit

### 6. Worker Routine and Presentation Pass

Status: Planned

Goal:
Make loaded workers look deliberate, readable, and less repetitive when the player is nearby.

Primary changes:

- add short-lived memory for unreachable or repeatedly failing targets so workers do not thrash
- make non-urgent chores respect time of day and poor weather
- add more visible representative work presentation such as explicit pickup moments, composter visits, or short carried-task loops before goods return to abstract settlement stock
- review where visible world actions should stay explicit versus where abstraction is still acceptable
- move profession task weights, thresholds, and material preferences into reloadable data or config where practical instead of hard-coding every heuristic

### 7. Worker Equipment and Gear Support

Status: Planned

Goal:
Give professions stable loadouts, improvised village weapons, and slow gear-upgrade support loops.

Primary changes:

- add `Sling`, `Crooked Staff`, `Scythe`, and `Spear` design and implementation rules
- assign baseline profession loadouts such as farmer hoes and scythes, gardener slings, shepherd slings and crooked staffs, fisherman spears or tridents, forester axes and hoes, miner pickaxes, beekeeper shears plus task-carried bottles or flowers, and fletcher bows with abstract unlimited arrows
- add `Forester's Table` as a custom workstation and use it to stage a log-heavy `Forester's Workshop` based on the Carpenter/Roadwright layout family
- keep deskworker roles such as `Trademaster`, `Scribe`, `Cartographer`, `Cleric`, and `Librarian` unarmed and fleeing
- implement fallback `Wooden Sword` rules for field workers without a more specific weapon
- make `Leatherworker`, `Weaponsmith`, and `Armorer` slowly equip or upgrade other villagers over time
- enforce defender-first upgrade priority for `Guards`, then `Roadwrights`

### 8. Settlement Debug and Inspection Tools

Status: In Progress

Goal:
Add fast playtest tooling for checking village stock and role state in-world.

Primary changes:

- add a build-site preview key that toggles a red wireframe over planned structure blocks that still need to be built
- feed the wireframe from server build-site state so it matches selected material families, orientation, player-placed progress, missing materials, and blocked blocks
- add a debug command that reports the nearest or targeted settlement inventory or stock
- add or evaluate an optional F3-style keybind or overlay for the same data
- use a rebindable default key such as `I` for the settlement overlay instead of relying on a function key
- cycle repeated `I` presses through `Overview`, `Inventory`, `Population`, `Workers`, and `Trades With Villages`, then hide the overlay on the next press
- keep the screen list extensible so future pages can be inserted before the final off state
- show at least settlement name or id, population, stock, shortages or surpluses, profession counts, current task categories, construction availability, active construction block counts, and route summaries
- add an `I` overlay screen for village-known recipes or craftable outputs so players can inspect what the settlement can currently make

Shipped so far:

- added a rebindable F3-style client HUD overlay that defaults to `I`, requests the nearest settlement inventory from the server, and shows stock, shortages, surpluses, and live profession counts without opening the `Trade Board`
- added a rebindable build-site preview key that defaults to `O`, requests the nearest or targeted active build site from the server, and renders red wireframes for blocks whose build-site status is not placed or player-placed
- split the `I` overlay into `Overview`, `Inventory`, `Population`, `Workers`, and `Trades With Villages`; the `Population` page now includes current task categories and construction worker/build-site counts
- add a `Workers` overlay page for per-villager observability, including current task labels plus profession-specific target/detail fields where available
- feed Roadwright-specific debug intent into both views: aggregate task counts use cached per-villager roadwork decisions, worker detail rows can show target and route summaries, and the Surveyor map can render the corresponding full route trace
- keep loaded Roadwrights acting on their cached route between Decide windows instead of only during the replan window, and keep overlay/map debug views tied to cached active plans so opening observability tools does not invent hypothetical roadwork
- extend Surveyor observation retention and observed-area caps so visible map coverage persists longer during real-time playtests instead of fading after about one hour of continuous play
- keep Surveyor route traces visible for a longer Roadwright-specific memory window so route intent remains legible across short pauses, breaks, and replan gaps until the route segment is actually resolved
- relax Roadwright greenspace blocking so selected corridors can still form near ordinary tree cover instead of idling in `surveying village` whenever nearby leaves/logs make the previous veto too strict
- protected placed workstation anchors during staged builds, allowed wood-stock crafting of replacement workstations, corrected `Trading Post` second-level windows, rotated the top roof stairs, and gave placed fence blocks blueprint-derived connection states
- expanded the `Trade Board` trade tab to six rows per side, removed stock from the trade tab, made sand and glass tradable, and added active build-site material blockers to village needs
- added beds as tradable goods, made bed construction blockers request finished beds plus wool and planks, kept refreshed Trade Board screens on the active tab, and added in-screen trade success/failure feedback
- made both staged roofs use a final slab ridge, removed the stray `Carpenter's Workshop` roof stair, added third-level sleeping-room wall torches with torch/coal/stick construction demand, and recorded the later Bakery display-case approach
- added first-pass `Roadwright` support: `Surveyor's Table` block/item/recipe/POI/profession/outfit assets and a staged `Roadwright's Workshop` derived from the Carpenter footprint with stone-family side walls
- added blueprint migration during loaded construction maintenance so existing build sites drop obsolete planned blocks, add newly introduced planned blocks such as torches, and stop rebuilding stale roof stair cells
- added a first visible Roadwright work loop that lays `Dirt Path` blocks from the settlement core to internal workstation build sites before falling back to nearby-settlement trail work
- changed Roadwright route selection to search a bounded terrain-aware path, prefer existing road surfaces, and avoid blocked or steep columns instead of only sampling a direct straight line
- changed construction stock visits to target adjacent Trade Board access tiles and added a Trading Post stair cap above the board to reduce workstation-climbing pathing
- moved the Trading Post anti-climb stair cap up into the soffit/roof layer and widened it to three stairs across the front
- expanded Roadwright internal path targets beyond staged build sites by scanning loaded settlements for workstations, Trade Boards, composters/gardens, storage, beds, and doors
- changed carrying construction workers to choose reachable nearby stand positions and time out stale active deliveries so they do not spin forever with supplies; road route planning now requires the merged `Roadwright` role and the old `Pathwright` code hook has been removed
- expanded loaded Roadwright work from simple `Dirt Path` placement to path-gap repair, one-block slope stair placement, and short steep-step terrain reshaping on internal village paths
- added profession-aware current-task overlay lines, explicit late-day village gathering / return-home labels, child-attending labels, stable loaded bed claims, and stricter custom-role promotion so existing vanilla professions are not repurposed for missing custom roles

Deferred UX:

- add a bulk `Donate` flow that lets players move arbitrary stacks from player inventory into settlement stock safely
- overhaul trading discovery: consider letting the player select an item or stack from their inventory, then have the village show what it is willing to trade for that item or quantity instead of relying only on prelisted offers

Next construction-debug step:

- use the red build-site wireframe during broad construction testing to verify placement, orientation, composition, and player-assist gaps without relying on a text debug readout

### 9. Remaining Profession Rollout

Status: Planned

Goal:
Finish the remaining role/workstation gaps after the first agriculture, husbandry, construction, and route loops are working.

Primary changes:

- `Forester`; forestry tasks must skip trees that contain bee nests or bee hives, including when an assistant is helping
- `Beekeeper`; add the `Honey Separator`, a staged `Beekeeper's Apiary`, bounded hive scans, honey bottle and honeycomb production, safe campfire placement, hive expansion toward at least `3` dispersed village-edge hives, and an all-white protective suit texture
- `Miner`
- `Gardener`
- `Guard`
- `Fisherman`
- `Fletcher`; implemented vanilla `Fletching Table` workstation assignment, bounded loaded arrow-crafting stock work, active ranged village defense with visible-threat targeting plus edge-of-settlement hostile scanning, explicit arrow-entity firing, faster ranged cadence, and a workstation-anchored `Fletcher's Hut` with `2` beds while each table can support up to `2` Fletchers; `Fletchers` now also contribute a partial abstract security bonus
- first-pass `Fletching Table` gameplay pass: added a player-facing workstation menu on the vanilla `Fletching Table`, batch crafting for efficient vanilla `Arrow` output plus `Copperhead`, `Ironhead`, and `Diamondhead` arrow items, vanilla `Copper Nugget` support, a wider readable screen layout, ghost slot impressions for the expected materials, first-pass head-colored item textures, and first-pass custom projectile damage behavior with explicit iron-versus-diamond target tags
- deferred `Fletching Table` follow-up: switch ordinary settlement arrow production from flint-only recipes to the same workstation recipe family, define first-pass armor-bypass versus raw-damage rules plus entity tags for iron-versus-diamond target differentiation, and give the `Carpenter's Bench` a matching efficiency-benefit pass
- Fletcher huts now leave side access around the front workstation instead of sealing the stall shut, and existing saved Fletcher huts clear those two front-side plank blocks during loaded maintenance so trapped villagers can get out
- completed workstation structures now get maintenance-time retrofit refreshes from their saved build sites, and workstation scans reopen/update recognized structure plans instead of treating completed sites as permanently frozen
- Trade Board / settlement overlay project labels now call road projects out explicitly as `Road to <settlement>`, and shortage presentation treats spare wheat above the wheat reserve target as effective bread supply so the village does not falsely ask for bread it can already bake
- `Leatherworker`
- `Shepherd`
- `Cleric`
- `Armorer`
- `Weaponsmith`
- `Portmaster`; implemented a custom `Portmaster's Anchor` workstation and profession assignment, loaded harbor-patrol task memory around anchors/docks/lighthouses, dock placement preference from anchor facing, first-pass harbor-trade bonus scaling from staffed Portmasters, and a simpler regular-`Oak Log` plus `Iron Bars` recipe for the anchor
- autonomous dock planning now treats a placed `Portmaster's Anchor` as authoritative: if anchors exist, villages only build docks from valid anchor-adjacent sites instead of falling back to unrelated shoreline candidates elsewhere in the harbor
- dock validation for the `Portmaster's Anchor` now tolerates up to `2` near-shore footprint cells being solid ground that get replaced by dock decking, and it now requires the far end of the dock to reach `2`-deep water instead of expecting the whole footprint to behave like uniform water frontage
- `O` preview now prefers a targeted placed `Portmaster's Anchor` dock preview even while other staged village construction is active, and assisted-placement green/red matching now consistently treats wood-family stairs, slabs, doors, fences, fence gates, and beds the same way logs/planks were already handled
- placed `Portmaster's Anchor` rediscovery now scans downward from each terrain column's local surface instead of only within a narrow settlement-center Y band, and targeted dock previews can resolve directly from the placed anchor block so steep shoreline anchors keep their `O` wireframe after placement
- the client build-preview targeter now keeps a directly aimed `Portmaster's Anchor` as the request target instead of shifting to the adjacent water/air block, and the server now also falls back to neighboring anchors when a shoreline-adjacent target block is sent
- dock previews now stay targetable through the dock footprint columns rather than only exact deck/support blocks, and preview validation now treats matching player-placed dock planks/logs as progress instead of turning the whole dock red
- placed `Portmaster's Anchor` blocks now start or resume a real `DOCK` build site instead of only exposing a prospective overlay, so dock wireframes follow the same active-build preview and player-assisted construction path as the other workstation-anchored structures
- custom villager profession overlays now include a real `Portmaster` texture, and cartographer-house wall torches now resolve against an actual supporting wall instead of relying only on edge heuristics
- `Scribe`
- warehouse behavior
- dock and harbor behavior
- the `O` build-preview toggle now also previews held workstation footprints before placement, with green valid previews and red blocked previews for workstation-linked buildings and dock anchors
- when `Gardener` lands, make it build trapdoor-edged raised flower beds around homes, under windows, near doors, and in small roadside or civic `3`-block beds without blocking paths or future structures

### 10. Later Designation Blocks and Civic Structures

Status: Notes only

Goal:
Track possible future player-directed construction anchors that are not normal profession workstations.

Backlog notes:

- consider special designation blocks that tell village builders where to create non-workstation structures, such as an apartment building
- consider a `Keep` designation block for a small medieval tower that can serve as a player base, with several floors and a parapet; evaluate square versus round variants when design starts
- Roadwrights should eventually own better village street-light placement and maintenance, not only path surfaces
- evaluate whether Blacksmith-family professions should repair iron golems
- evaluate whether Clerics should heal players and villagers
- evaluate a Beekeeper defensive rule where Beekeepers do not directly attack mobs, but nearby bees defend them when the Beekeeper is attacked near a hive

### 11. Outpost Raiding and Self-Building

Status: Planned

Goal:
Make pillager outposts behave like hostile settlements that raid to grow instead of participating in normal village trade.

Primary changes:

- keep outposts out of normal village trade matching and route exchange rules
- bias outpost staffing toward offensive and support roles such as `Guard`, `Armorer`, and `Weaponsmith`, and away from comfort roles such as `Farmer` or `Gardener`
- let outposts construct needed structures without the exact peaceful profession match, but slower and less efficiently than specialist village labor
- make outposts fund building, reinforcement, and growth primarily through raids, pillaging, and route attacks

## Tracking Notes

- When a phase starts, update its `Status`.
- When a phase lands, add a short note under it describing what shipped.
- If implementation diverges from the plan, update this file and the corresponding spec documents together.
