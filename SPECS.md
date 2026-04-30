# Live Villages Specification

Live Villages is a Fabric mod for Minecraft Java `26.1.x`. The goal is to make villages, harbors, and hostile outposts behave like self-directed settlements with their own needs, plans, and trade networks. The player should influence those systems by building infrastructure and changing incentives, not by micromanaging every villager action.

## Design Goals

- Villages should feel alive even when the player is not standing inside them.
- The main simulation unit should be the settlement, not a constantly pathfinding mob swarm.
- Infrastructure should matter: workstations, warehouses, roads, docks, walls, and housing should all change outcomes.
- Loaded-world simulation should protect server tick time. Broad scans and route planning should be cached, throttled, and converted into bounded work queues rather than recomputed every few ticks.
- Trade should work over both land and water, with meaningful distance, safety, and route quality.
- The system should scale through abstract simulation, round-robin updates, and catch-up logic rather than full real-time processing everywhere.
- Core systems should be data-driven and compatible with optional addons, but should not require third-party mods to function.

## Core Simulation Model

- Each settlement stores persistent state for population, stock, wealth, housing, comfort, security, construction queues, and trade routes.
- Bulk resources live in a virtual warehouse ledger rather than in fully simulated per-villager inventories.
- Individual villagers can still have identity, profession, home, tools, family goals, and a small amount of personal state, but settlement logistics should be ledger-driven.
- When chunks are loaded, villagers can perform visible representative work such as hauling, building, guarding, and traveling.
- When chunks are unloaded, the simulation advances abstractly and catches up later.

This should be implemented with Fabric `PersistentState` data for settlements and routes.

## Platform Target

- Initial target: Minecraft Java `26.1.x`.
- Loader/toolchain target: Fabric on the current `26.1+` toolchain.
- Development assumptions for `26.1+`:
  - Use Mojang mappings.
  - Use the current non-remapping Fabric Loom plugin.
  - Prefer vanilla's new data-driven trade systems where possible.

## Settlement Types

- `Village`: standard civilian settlement.
- `Harbor`: a village with strong dock and water-route support.
- `Outpost`: hostile pillager-controlled settlement.
- `Custom`: player-founded settlement created through infrastructure even outside vanilla generation.

Example settlement state:

```json
{
  "id": "village:123_456",
  "center": [123, 68, 456],
  "type": "village|harbor|outpost|custom",
  "population": {
    "unemployed": 2,
    "farmer": 5,
    "forester": 1,
    "miner": 1,
    "guard": 2,
    "trademaster": 1
  },
  "housingCapacity": 18,
  "comfort": 1.10,
  "security": 0.35,
  "wealth": {
    "emerald": 23,
    "coin": 180
  },
  "stock": {
    "wheat": 320,
    "logs": 128,
    "brick": 96
  },
  "queues": {
    "build": [],
    "road": [],
    "dock": [],
    "knowledge": []
  },
  "lastUpdateTime": 1234567
}
```

## Trade Board

The Trade Board is the central management workstation for a settlement. Visually it should feel like a community posting board or sign-like civic block, not just another chest menu.

When a player right-clicks the Trade Board they should see:

- A list of goods the settlement does not have enough of.
- A list of goods the settlement has in surplus.
- Basic settlement status: population, security, housing, and key projects.
- Route and trade summaries.
- Important construction or knowledge goals.

The player trades against settlement stock rather than only a single villager inventory.

- Goods in shortage are bought above baseline price.
- Goods in surplus are sold below baseline price.
- The player can resolve shortages indirectly by building missing workstations or infrastructure and letting the settlement adapt.
- The Trade Board should show the highest-priority settlement-wide wants rather than every possible tradable item. Lower-priority or profession-specific trades should be handled by profession structures so the global board stays readable.
- Any new feature that creates demand for a good, block, or item should also account for that good in the settlement wish/desire/trade model. If the village can use it, the player should eventually have a discoverable way to donate or trade it.
- Active build-site blockers should raise matching goods onto the Trade Board's needs list. For example, unfinished glass windows should make glass and adaptable raw materials such as sand available as player trade or donation options; unfinished beds should make finished beds, wool, and supporting materials such as planks visible.
- The Trade Board trading screen should support two direct selection flows:
  - `Your Goods`: the player selects a tradable item from their inventory, then chooses from the village payout options that are valid for that good.
  - `Village Goods`: the player selects a village stock item the village is willing to offer, then chooses which currently wanted player good to pay with.
- The village must not offer goods that are currently needed for active shortages, construction blockers, or normal stock targets. Trade payouts should only spend stock above the protected reserve for that good.
- The `Your Goods` flow should let the player donate the selected good directly to settlement stock without taking a payout. The first-pass controls support donating one item, one normal trade bundle, or all matching items from the player's inventory.
- The Trade Board trade screens should focus on actionable goods and offers. Broader stock/status details belong on overview, inventory, or route screens.
- After a successful or failed trade, the UI should keep the player on the current tab and provide immediate in-screen feedback in addition to chat.
- A later bulk-donation flow may still open a transfer surface and move arbitrary stacks from player inventory into village stock. This flow will likely need iteration so it is fast without accidentally taking items the player meant to keep.

### Profession-Specific Trade

Profession structures can expose targeted trades that fill gaps left by the Trade Board's highest-priority list.

- Profession trades should let the player trade useful but not currently high-priority goods to the specialist most likely to want them.
- A `Baker` might accept eggs, wheat, milk, sugar, or fruit in exchange for bread, cakes, pies, cookies, or other baked goods.
- A `Beekeeper` might accept glass bottles, shears, campfires, flowers, or wood inputs in exchange for honey bottles, honeycomb, candles, or other hive products.
- A `Carpenter` might accept logs or planks in exchange for stairs, slabs, fences, gates, doors, or other wood outputs, with terms at least as good as manual crafting and with convenience as the main benefit.
- A `Butcher` might accept wheat or other feed in exchange for beef, pork, leather, or herd-related outputs.
- A `Mason` might accept rough stone-family blocks such as granite or diorite in exchange for polished blocks, stairs, slabs, or other stone variants.
- These trades should still respect settlement stock and local production. The profession structure should not create goods from nothing unless future recipe/knowledge rules explicitly allow it.

### Trading Post Structure

`Trading Post` is the current code-facing name for the first-tier `Trade Post` structure.

- The `Trade Board` is the workstation anchor for a `Trading Post`.
- When a settlement claims a placed `Trade Board`, villagers should start a visible construction project for the associated `Trading Post` around or beside that board when the footprint is valid.
- Loaded settlement maintenance should retry valid placed `Trade Board` anchors that do not yet have a `Trading Post` build site, so older boards or boards that were temporarily blocked can start construction after conditions change.
- The structure must not appear all at once. Villagers should visibly place and remove individual blocks while doing construction work.
- The player may help by placing valid missing blocks in the planned footprint. A structure only counts as a trade enhancement when every required block is complete.
- When the build-site preview is active and the player is holding a block accepted by the structure plan, matching placement positions should be highlighted. Assisted placement through that preview should place the planned block state, including orientation-sensitive blocks such as stairs, doors, beds, fences, gates, and logs, so player help advances construction instead of creating orientation stalls.
- When the build-site preview is not active, player placement remains ordinary Minecraft placement and should not be coerced by the settlement construction system.
- Construction may start before every material is available. Missing material leaves unfinished positions until the settlement gains or crafts the needed blocks.
- Loaded construction workers should carry batched supplies from stock access points when possible instead of one block per trip, with at least a half-stack target for common block materials.
- A settlement should not queue or start a duplicate associated structure while an existing build site of that role is in progress. This applies generally to workstation-associated structures, civic structures, upgrades, and repairs, not only to `Carpenter's Workshop` sites.
- Workstation-associated structures may relocate the anchored workstation to the blueprint's intended final block, including vertical moves, without canceling the build site. Villagers should be able to remove the original placed workstation, recover it into construction supply, and place it at the planned workstation position as part of the same staged build.
- If a completed staged structure is damaged or has required blocks removed, it should reopen as a repair build site instead of causing the settlement to queue a duplicate replacement structure. The repair site should follow the same visible work, stock, and material rules as initial construction.
- The placed workstation anchor is a protected planned block. Workers should not remove it while building around it; if it is missing or damaged, replacing it is part of finishing or repairing the structure.
- Wood in the structure may be any local family but should be consistent within the structure. For example, a spruce-heavy region should prefer spruce logs, planks, stairs, slabs, fences, gates, and doors.
- Fence blocks in explicit structure blueprints should be placed with connection state derived from neighboring planned blocks so market posts connect cleanly to adjacent logs, fence gates, and solid supports.
- The `Trading Post` front should discourage villager pathing over the `Trade Board` or fence line. A small three-stair cap above the board, placed in the soffit/roof layer rather than directly on the board level, is acceptable when it preserves the market frontage and keeps villagers using doors or side gates.
- Stone material quality affects trade value. Cobblestone is the lowest tier, stone is better, smooth stone is better again, granite and diorite are better than plain stone, polished granite and polished diorite are better than their raw forms, and stone bricks or similar blocks should be categorized into comparable quality tiers.
- A completed `Trading Post` may later be upgraded to better stone. Upgrades follow the same visible construction rules: villagers remove old blocks and replace them one block at a time when the settlement has enough better material.
- The same site should be upgradeable later into a larger `Shopping Mall` tier rather than requiring a different workstation block.

The first-tier footprint is `5x8`. These layer diagrams are authoritative for placement and composition. `A` means air or an intentionally empty block.

Floor:

```text
C = cobblestone or better

CCCCCCCC
CCCCCCCC
CCCCCCCC
CCCCCCCC
CCCCCCCC
```

1st level:

```text
C = cobblestone or better, W = Trade Board, L = log, P = planks, B = bed, D = door, F = fence, G = fence gate, A = air

LFGLCCCL
FAACABBC
WAADAAAC
FAACAAAC
LFGLCCCL
```

2nd level:

```text
L = log, P = planks, D = door, V = glass block, A = air

LAALPVPL
AAAVAAAV
AAADAAAV
AAAVAAAV
LAALPVPL
```

3rd level:

```text
L = log, P = planks, S = upside-down stair, T = wall torch, A = air

LSSLPPPL
AAAPAAAP
AAAPAATP
AAAPAAAP
LSSLPPPL
```

4th level:

```text
P = planks, B = slab, S = stair, A = air

BBBSPPPS
BBBSAAAS
BBBSAAAS
BBBSAAAS
BBBSPPPS
```

5th level:

```text
P = planks, S = stair, A = air

AAAASPSA
AAAASPSA
AAAASPSA
AAAASPSA
AAAASPSA
```

The stairs on this top roof level should be oriented to form a longitudinal roofline rather than short crosswise ridges.

The sleeping area should include a wall torch on the wall opposite the door at the third block level. When the settlement has enough wealth and materials, the torch should later be eligible for an upgrade to a lantern.

6th level:

```text
B = slab, A = air

AAAAABAA
AAAAABAA
AAAAABAA
AAAAABAA
AAAAABAA
```

### Visible Settlement Name

The Trade Board should eventually display the linked settlement name directly on the placed board, similar in purpose to sign text but not necessarily implemented as a vanilla sign.

- The name should render on both broad faces of the board so it can be read from either side.
- The displayed text should come from the linked settlement name and update when the board links to a settlement or the settlement is renamed.
- The text should be non-editable board labeling, not a player-written sign UI.
- Long settlement names should be truncated, wrapped, or scaled so they remain readable on the board face.
- An unlinked board may show no name, a generic "Trade Board" label, or a temporary founding label until it resolves to a settlement.
- This is feasible because the Trade Board is already a block entity with horizontal facing; a future client block-entity renderer can draw text on the two board faces.

### Founding New Settlements

- If the player places a Trade Board outside the radius of an existing settlement, it can seed a new one.
- Tentative minimum spacing: about `100` blocks, subject to tuning.
- The new settlement starts with only the board and minimal virtual state.
- Within roughly one in-game day, a founding villager should spawn nearby.
- The first founder becomes the settlement's Trademaster and begins trying to establish warehousing, housing, food, and basic production.

## Trademaster

The Trademaster is the settlement's quartermaster, planner, and routing manager.

- The Trademaster seeks to establish a warehouse.
- The Trademaster commissions public projects through settlement queues rather than doing everything personally.
- The Trademaster can still work, but is less efficient at non-specialist labor than dedicated workers.
- The Trademaster handles inter-settlement trading and knowledge exchange. A village without a Trademaster cannot trade with other villages.
- Autonomous public expansion should be capped by settlement advancement or knowledge tier rather than by one fixed rate.
- Example guideline: before copper-tool knowledge, a settlement may autonomously complete about one major workstation or public project per in-game week; after copper-tool knowledge, about two per week; after iron-tool knowledge, a higher tuned rate.
- The actual gates should use settlement knowledge/recipe state once that system exists, not player advancements alone, so settlements can improve by learning from trade, local infrastructure, or direct player help.

## Warehouse and Ledger Economy

Every settlement should have a virtual warehouse ledger, even before a literal warehouse building exists.

- A physical warehouse should improve capacity, route efficiency, visual feedback, and player interaction.
- The ledger should remain the authoritative source for bulk goods.
- This avoids relying on large numbers of loaded containers and constant villager item transfers.

### Goods Flow

- Producers add goods to settlement stock.
- Population consumes food and other necessities over time.
- Build queues consume materials and create completed structures or stat increases once enough progress has accrued.
- Trade moves goods between settlements through routes with limited throughput.
- Prices vary based on local shortages and surpluses.

Useful baseline formulas:

```text
route_throughput = base * quality * security * capacity / (1 + distance_km)
population_growth = f(free_housing, comfort, food_supply, security)
```

### Personal Goals vs Settlement Accounting

Individual villagers should still want individual things:

- A bed and better housing.
- Food security.
- Better tools and safer conditions.
- Family growth.
- Wealth, decorations, status goods, or profession-specific upgrades.

### Loaded Construction and Inventory Flow

Loaded settlements should reconcile abstract stock with visible villager behavior whenever the player is nearby.

- Villagers performing any work may collect blocks or items into personal inventory.
- When the villager is in a loaded chunk, they should periodically visit the `Trade Board` or its completed `Trading Post` to deposit gathered goods or retrieve needed work materials. Villagers should navigate to a standable adjacent access tile, not to the exact workstation block, so they do not climb onto the board, bench, or other workstation.
- Villagers that have picked up dropped blocks or items should make an end-of-day deposit trip to the `Trade Board` / `Trading Post` before the evening gathering. The visible task label for that behavior may be `Depositing into Trading Post`.
- Depositing transfers items out of the villager's inventory and into the settlement warehouse ledger. Retrieving transfers needed materials from the ledger or physical storage into the villager's inventory.
- A villager nearing a full inventory should treat depositing as high-priority work. For example, a miner carrying too much cobblestone should visit the `Trade Board` before continuing low-priority work.
- End-of-day deposits are valid visible behavior. For example, a farmer may harvest wheat and carrots during the day, then visit the `Trading Post` so settlement stock increases and personal inventory decreases at that moment.
- Basic crafting from raw materials may happen automatically for construction. If a project needs oak stairs but stock has only oak logs, villagers may craft logs into planks and planks into stairs at a rate based on available villagers and relevant professions.
- A loaded Carpenter may also process excess log stock at the `Carpenter's Bench`, maintaining useful reserves of planks, slabs, stairs, and sticks without consuming logs below the village's log reserve target.
- Raw materials should be valuable in trade because they can satisfy more future construction recipes than finished parts.
- Later logistics should replace automatic construction crafting with visible material-processing trips. A worker should pick up raw supplies from the `Trade Board` or completed `Trading Post`, visit the best available workstation for the needed output, craft the block there, then carry it to the build site.
- Workers should batch construction supplies rather than making a separate stock trip for every block. Specialists should carry more of their specialty materials, carry a wider set of specialty outputs, and place matching specialty blocks faster than non-specialists.
- For example, if a construction worker needs stairs and the settlement has planks but no stairs, the later logistics model should have the worker take planks from the `Trading Post`, use the `Carpenter's Bench` at a `Carpenter's Workshop`, then carry the stairs to the planned block. Until that model exists, construction may assume automatic crafting from raw materials at stock pickup time.
- Adult non-Nitwit villagers may help with general construction after their profession's higher-priority work is satisfied. Unemployed villagers should be available first, while specialists can become faster or more efficient at matching construction work in later passes.
- Profession-specific high-priority work outranks general construction. A farmer harvests ready crops before helping build a `Trading Post`, but helps build before cutting grass or picking up leaf litter.
- A villager normally completes the current atomic work action, such as placing or removing one block, before switching back to newly available high-priority profession work.
- Guards are the exception: they may immediately interrupt their current action to answer imminent hostile-mob threats.
- After profession high-priority work is exhausted, villagers should return to construction or other settlement tasks they are needed for.
- All visible terrain-changing workers should respect village greenspace. Paths, forestry, farming expansion, and future gardening/mining surface work should preserve some grass, flowers, saplings, and trees inside the village instead of converting every reachable surface into utility blocks.

However, full economic accounting should stay at the settlement level for performance. If personal inventories exist, they should stay lightweight and focus on carried goods, tools, equipment, and flavor rather than entire supply-chain simulation.

Workers should trade their output for what they need or desire. For example, a farmer does not need to hoard all food personally and may prefer better housing, safety, decoration, or wealth.

## Trading Between Settlements

- Any settlement with a Trademaster can trade with any other compatible settlement.
- `Outposts` are not compatible village trade partners; they interact with civilian settlements through raiding, extortion, and theft rather than negotiated commerce.
- In theory all discovered settlements can be connected economically, but practical trade depends on route quality, safety, and distance.
- Longer or more dangerous routes should add both time and cost.
- Better roads, better docks, safer corridors, and better vehicles should reduce those penalties.

Settlements can trade both goods and knowledge.

- Nearby trade can be coordinated by a `Trademaster` alone.
- Routes beyond about `32` chunks / `512` blocks should require cartographic support to be established or maintained efficiently.

### Knowledge and Recipe Trading

- Villages should be able to trade recipes, techniques, and profession knowledge as well as physical items.
- Example: one settlement can teach another how to make iron pickaxes or brick.
- A knowledge workstation such as a `Scribe Desk` can gate higher-level knowledge exchange.
- A settlement may need matching knowledge infrastructure on both ends before some recipe trades are allowed.
- This implies that villages track their known recipes. All villages should start out with enough recipes known to function and grow.

## Routes, Roads, and Water Trade

Routes are first-class simulation objects.

Example route state:

```json
{
  "id": "village:a|village:b",
  "type": "land|water",
  "distance": 1320,
  "tier": "none|trail|gravel|cobble|brick|river|canal|sea_lane",
  "quality": 0.80,
  "security": 0.72,
  "capacity": 64,
  "lastSurvey": 1231000
}
```

### Land Routes

Roads should start crude and improve over time.

Roadwrights should connect meaningful destinations and repair established corridors, but they should not flood-fill every adjacent grass patch into path. Internal paths should preserve village greenspace, decorative plants, saplings, trees, and coherent grass patches unless a specific route or access point needs that block.

Proposed land tiers:

- `none`
- `trail`
- `gravel`
- `cobble`
- `brick`

Additional road improvements should be allowed, such as using Smooth Stone (better than cobble) and bridges made out of different materials (stone being better than wood).

Proposed workflow:

- `Roadwrights` establish nearby route corridors and improve them over time.
- Once a settlement has a `Roadwright`, it should also extend internal paths from the settlement core to workstation-associated buildings so those structures become part of the local trade network instead of sitting as isolated job sites.
- Roadwright path placement should use a terrain-aware route search rather than a strict straight-line sampler. First-pass routes may still be simple, but they should prefer existing road surfaces, preserve player-upgraded paths such as smooth stone, repair small missing path gaps, avoid blocked columns where practical, reshape short steep jumps into intermediate terrain, and bend naturally around building placement.
- One-block path height changes should receive a stair or slab treatment on the lower side of the step so the path reads as a developed slope instead of a raw block jump. Two-or-more-block jumps on an intended or existing internal path should be reshaped into one-block increments before being treated with stairs or slabs.
- Roadwrights should discover local internal path targets from more than staged build sites. First-pass targets include known workstation structures, placed workstations, Trade Boards, composters/garden anchors, storage blocks, beds, and doors.
- Loaded Roadwright work priority should be: internal village paths first, then helping with village construction or repairs when no internal path work is available, then extending paths toward known nearby settlements and route corridors.
- Established routes should exchange goods in batched trade cycles rather than only opportunistic single-item drips, with small crude settlements trading roughly every several in-game days and larger better-connected settlements approaching daily or better cadence.
- Route trade should prefer mutually beneficial exchanges when both endpoints have meaningful shortages and useful surpluses, and goods should be able to move in either direction during the same trade cycle when that is warranted.
- Connected workstation buildings should bias trade composition toward their specialty goods. For example, a `Leatherworker` building connected to the settlement path network should make leather goods more likely to appear in route exchanges.
- `Mileposts` mark established routes at regular intervals and help make corridors legible and maintainable.
- `Mileposts` should prefer regular intervals, village entries, and route junctions while avoiding obvious path blockage or high-value future build space.
- The first-pass `Milepost` should use a `3`-block-tall, `1x1`-footprint obelisk form so vertical destination text fits cleanly.
- When destination text is shown on a `Milepost`, the face for a given village should point toward travelers heading to that village; vertical text is acceptable if that is the cleanest fit, and distance can be added when the face remains readable.
- First-pass destination labels may use the nearest non-outpost settlement in each facing direction rather than full route-distance logic, as long as each named face points toward that settlement.
- If a destination is meaningfully diagonal from the `Milepost`, the first pass may show that same settlement on both relevant faces so the sign remains readable from the obvious approach directions, unless a closer settlement meaningfully owns one of those faces.
- `Cartographers` provide the long-range survey knowledge needed for efficient distant routes.
- Better road materials and maintenance raise route quality and throughput.
- `Mileposts` should begin as `Stone` route markers, then allow upgrades such as `Smooth Stone`, `Polished Granite`, `Polished Andesite`, or `Obsidian`; better materials should add a modest route-quality or throughput bonus instead of acting as purely cosmetic decoration.

### Water Routes

Water trade should be the best long-distance bulk transport if a settlement has proper harbor infrastructure.

- Add a `Portmaster` role.
- Add dock tiers and upgrades.
- Add dredging or canal projects to improve poor waterways.
- Support route types such as `river`, `canal`, and `sea_lane`.
- Boats should be virtual at long distance and only spawned cosmetically when players are nearby.

This allows harbors to matter without requiring every cargo trip to exist as a continuously simulated entity.

#### Docks

A dock consists of planks extending from land into the water until the water is at least two blocks deep. The width of the dock must be at least two blocks wide, and the basic authored form should be a simple working pier rather than a decorative marina piece.

- When a settlement has one or more placed `Portmaster's Anchor` workstations, autonomous dock construction should only use valid dock sites near one of those anchors rather than picking an unrelated generic shoreline location elsewhere.
- The minimal/basic dock should use plank blocks at water level.
- The minimal/basic dock should usually be about `3` blocks wide and about `8` blocks long when space allows.
- In the water there must be logs supporting the planks at least every `4` blocks. An `8`-block dock therefore needs two pairs of support logs.
- Villages with a large enough body of water inside their footprint should want at least one dock.
- Docks should improve `Fisherman` catch and add a modest water-trade bonus.
- Dock, `Cartographer`, `Scribe`, and `Lighthouse` trade-range improvements should also improve actual trade cadence and trade quality, not only the maximum reachable distance.

#### Lighthouse

A lighthouse is a harbor structure rather than a workstation. It should be visually simple in the early tier and readable from a distance.

- Players should be able to craft and place a dedicated `Lighthouse` marker block that looks like a stone block with a lighthouse emblem or miniature on it.
- Placing that marker should start or resume a staged lighthouse build with the placed marker kept at the center of the bottom `3x3` level.
- The basic lighthouse must be at least `8` cobblestone blocks tall with a `Campfire` on top.
- The minimal/basic form is four levels of `3x3` cobblestone, then four levels of a single cobblestone block, then the `Campfire` on the highest block.
- The basic version should use only `Cobblestone` for the tower body.
- Lighthouses should be placed near docks or shoreline vantage points.
- A dock should unlock water-route trading support for a harbor settlement.
- A lighthouse should significantly improve water-route quality, throughput, and reachable trade distance compared with a dock alone.
- A lighthouse should also improve `Fisherman` catch for nearby harbor settlements, though less dramatically than it improves water trade.
- Trade-range extensions should be cumulative so future harbor or knowledge infrastructure can stack with existing range bonuses.
- Multiple lighthouses should be allowed in the same settlement, but only the first lighthouse should provide the major harbor-range and trade boost. Additional lighthouses should add only minor extra water-range and trade-value gains.
- Lighthouses should also add a modest security benefit for harbor settlements and act as a visible harbor-warning point during nearby hostile pressure.
- Loaded `Portmasters` should extinguish lighthouse fires in the morning, relight them shortly before the daily gathering window, and use threatened lighthouses as a raid-warning point when hostiles approach the harbor.

## Worker Roles and Workstations

Detailed role and workstation planning lives in [PROFESSIONS.md](PROFESSIONS.md). That document tracks vanilla profession/workstation mapping, current custom roles already implemented in code, planned new roles, and the intended split between visible world behavior and settlement-ledger behavior.

Ordered implementation work lives in [IMPLEMENTATION-PLAN.md](IMPLEMENTATION-PLAN.md).

Vanilla workstations may need modification, and new workstations should be added so all major settlement jobs exist. Vanilla professions should remain useful even before every one receives bespoke AI.

- When a player places a recognized workstation inside or near a settlement, villagers should try to use that exact block as the anchor for the associated profession structure if a valid footprint exists.
- If the settlement cannot fit the associated structure around that workstation, villagers should place a nearby temporary sign that says `can't build here` rather than silently ignoring the station.
- While the player is holding a recognized workstation item whose placement would anchor an associated structure or dock, the existing `O` build-preview toggle should also show the prospective footprint before placement: green when valid, red when blocked.
- That held-item preview flow should also work for dedicated craftable structure-anchor blocks such as `Simple Housing Shelter` and `Housing Shelter`, not only for vanilla or workstation job-site anchors.
- Those craftable structure-anchor blocks should also render correctly as normal held and inventory items rather than relying on missing-model placeholders.
- Workstation placement and village structure planning should require at least `3` blocks of separation between one building footprint and the next, so player-anchored workshops and emergency shelters do not crowd into each other.
- Workstations are not always one-villager objects. A `Guard Post` should support up to `5` Guards as their shared job-site anchor. Worker-profession stations such as `Carpenter's Bench`, `Surveyor's Table`, and later Forester, Miner, Mason, and similar stations should usually support `1` or `2` villagers, with the second slot used when the village has enough population and demand.
- Associated workstation structures should assign their internal beds to villagers of the matching profession where possible. Worker structures that have room for a second resident should gain or use a second bed when the settlement needs another worker of that profession.
- Completed workstation-associated structures should not be treated as permanently frozen. If their saved blueprint changes, a block is removed, or a new bed / repair target is added, loaded construction maintenance should reopen that same build site promptly and stage the retrofit or repair instead of requiring a fresh workstation placement.
- Post-`1.0`, structure selection should prefer biome-specific plans in the style of the local vanilla village set where practical, rather than reusing one cross-biome blueprint everywhere.
- That biome-specific pass should also remove similar-block family replacement for staged construction. Build sites should require the exact block family called for by the selected biome plan instead of silently converting `oak` materials into `jungle`, `spruce`, or other local variants.

Current implemented custom role and workstation pairs:

- `Trademaster` <-> `Trade Board`
- `Carpenter` <-> `Carpenter's Bench`
- Construction planning should distinguish `Carpenter` for wood-heavy work and `Mason` for stone-heavy work
- The current `Carpenter's Workshop` world layout should use the authoritative `5x8` structure below, with an enclosed rear room and a covered front work bay for the `Carpenter's Bench`

`Carpenter's Workshop` wood may be any local family but should be consistent within the structure.

Floor:

```text
C = planks

CCCCC
CCCCC
CCCCC
CCCCC
CCCCC
CCCCC
CCCCC
CCCCC
```

1st level:

```text
W = Carpenter's Bench, L = log, P = planks, B = bed, D = door, A = air

LPPPL
PBAAP
PBAAP
PAAAP
LPDPL
AAAAA
AAAAA
LAWAL
```

2nd level:

```text
L = log, P = planks, D = door, V = glass block, A = air

LPVPL
PAAAP
VAAAV
PAAAP
LVDVL
AAAAA
AAAAA
LAAAL
```

3rd level:

```text
L = log, P = planks, S = upside-down stair, T = wall torch, A = air

LPPPL
PATAP
PAAAP
PAAAP
LPPPL
SAAAS
SAAAS
LSSSL
```

4th level:

```text
P = planks, S = stair, A = air

SPPPS
SAAAS
SAAAS
SAAAS
SPPPS
SAAAS
SAAAS
SPPPS
```

5th level:

```text
P = planks, B = top slab, S = stair, A = air

ASPSA
ASBSA
ASBSA
ASBSA
ASBSA
ASASA
ASASA
ASPAA
```

The wall torch should be mounted on the wall opposite the door at the third block level. The upside-down third-level side stairs should have their long outside face on the building exterior. The upper roof should avoid one-off extra stair blocks that break the roofline.

6th level:

```text
B = slab, A = air

AABAA
AABAA
AABAA
AABAA
AABAA
AABAA
AABAA
AABAA
```

Expanded vanilla roles with stronger settlement behavior:

- `Cartographer`: long-range survey support and far-distance trade enablement.
  - Workstation structure: player-placed vanilla `Cartography Table` blocks should anchor a staged `Cartographer's House` when a valid nearby footprint is available.
  - Vanilla baseline: Minecraft provides vanilla cartographer house templates for plains, desert, savanna, snowy, and taiga villages; the first staged implementation uses the plains cartographer house as a simplified build blueprint rather than inventing a new unrelated structure.
  - A later post-`1.0` structure pass should choose biome-specific building plans that follow the local vanilla village style when available.
  - Structure blueprints may include optional per-cell orientation metadata for orientation-sensitive blocks such as stairs, doors, torches, logs, and future display blocks. Material symbols should stay separate from orientation symbols so the structure shape remains readable.
- `Farmer`: composter-anchored farm-territory management, harvest-first crop tending, dropped-item cleanup, leaf-litter composting, bone-meal use, grass trimming, and shortage-driven replanting.
- `Butcher`: the vanilla `Smoker` profession slot, expanded for herd growth, culling, leather production, sheep shearing, pig and cow management, pasture expansion, and settlement-wide livestock husbandry rather than only near the `Smoker`; player-placed `Smoker` blocks should anchor a staged `Butcher Shop` when a valid nearby footprint is available, and first-pass trade outputs should include beef, mutton, and pork surpluses when the settlement has enough managed herd capacity. Herd targets should scale from settlement population and may grow by up to about `50%` above local needs when active routes let the village export the surplus.
- `Mason`: shared construction, repairs, fortification work, and structural upgrades.

Key planned settlement-specific roles:

- `Carpenter`: wood-focused construction, repairs, and upgrades, paired with `Mason`.
- `Baker`: turns farm and livestock supplies into baked foods such as bread, cakes, pies, and cookies, and offers specialist food trades.
- `Beekeeper`: manages bee nests and bee hives, produces honey bottles and honeycomb, maintains safe smoked hives, and grows dispersed apiary capacity from a `Honey Separator`.
- `Forester`: harvests and replants wood resources, must not cut down trees that contain bee nests or bee hives, maintains a sparse tree presence inside the village, manages denser woodland beyond the village core, and keeps a diverse seedling reserve that can create sapling trade demand.
- `Miner`: gathers stone, ore, and underground materials.
- `Gardener`: improves village appearance and comfort by building decorative raised beds, planting flowers and grass, and managing chickens and eggs.
- `Guard`: protects the settlement and its corridors.
- `Roadwright`: creates and upgrades land routes, improves internal town paths, connects workstation structures to the path network, visibly uses a shovel for dirt-path work, and places mileposts from a `Surveyor's Table`.
- `Portmaster`: uses a `Portmaster's Anchor`, keeps docks and lighthouses staffed, biases dock placement toward its anchor, and prepares later boats, dredging, and water-trade route logic.
  - Right-clicking the `Portmaster's Anchor` should open a harbor trade map, analogous to the `Surveyor's Table` map but limited to land, sea, and known ports.
  - The harbor trade map should default to a fitted scale that shows all currently known port or settlement markers, and it should support zooming in and back out with mouse-wheel and `+/-` controls.
  - The harbor trade map should be centered on the active `Portmaster's Anchor`, not on the broader settlement center.
  - The harbor trade map should use the same player-facing rotation and compass-rose behavior as the survey map.
  - Known ports should use a recognizable port-chart symbol and should show the port name plus distance when hovered.
  - The linked local port does not need to repeat a `0`-distance readout in its hover label.
  - Settlements with lighthouses should render lighthouse-specific harbor-map markers distinct from ordinary ports, with the local lighthouse marker prioritized ahead of other harbor symbols.
  - If the settlement has a `Cartographer`, the harbor trade map should also show known settlements that do not yet have ports.
  - Cartographer knowledge should also expand broader land/sea charting beyond the shorter local harbor view, while non-Cartographer maps may keep a smaller terrain knowledge radius.
  - Harbor-range bonuses from docks, cartographic knowledge, scribal knowledge, and lighthouses should widen the map's known-port coverage cumulatively.
  - Harbor terrain knowledge should accumulate over time from loaded exploration instead of being rebuilt only from the chunks currently loaded when the map opens.
  - Shared harbor charting should come from a saved dimension-wide knowledge cache so different anchors can benefit from previously explored coastlines and sea lanes.
- `Scribe`: handles recipe and knowledge exchange.

Additional shared or planned workstations and civic structures include:

- `Trading Post`
- `Cartographer's House`
- `Bakery`
- `Warehouse`
- `Carpenter's Bench`
- `Honey Separator`
- `Beekeeper's Apiary`
- `Guard Post`
- `Miner Workstation`
- `Forester's Table`
- `Gardener Workstation`
- `Surveyor's Table`
- `Milepost` as route marker infrastructure rather than a workstation
- `Dock`
- `Lighthouse`
- `Scribe Desk`

### Carpenter Details

- `Carpenter` is the wood-focused member of the broader settlement construction category, while `Mason` handles stone-heavy work
- The `Carpenter` should prefer the bed in the workshop house, should get first claim on it over other villagers, and should be able to evict another villager from that bed when reclaiming it; only a player should be able to force the `Carpenter` out
- The `Carpenter's Bench` should act like a wood-specialized optimized crafting station, similar in spirit to the `Stonecutter`
- The `Carpenter's Bench` should eventually offer at least a modest efficiency edge over ordinary manual crafting on common wood outputs, so using the bench is materially beneficial instead of only faster to navigate.
- The `Carpenter's Bench` should cover recipes where wood is the dominant material, such as logs to planks, planks to stairs or slabs, fences, doors, trapdoors, signs, composters, and similar wood-heavy conversions
- Loaded Carpenters should turn excess village log stock into useful reserves such as planks, slabs, stairs, and sticks while leaving enough logs for direct construction and upkeep.
- Mixed recipes should be allowed only when wood is still clearly the primary material rather than turning the bench into a universal crafting-table replacement

### Roadwright Details

- `Roadwright` is the merged road-building profession that replaces the earlier split `Pathwright` / `Roadwright` concept.
- The Roadwright workstation is the `Surveyor's Table`. Its first-pass block should read like a workbench related to the `Carpenter's Bench`, but with shovel/surveying visual language.
- Placing a `Surveyor's Table` in a settlement should create or resume a staged `Roadwright's Workshop` build site and allow one eligible villager to become a `Roadwright`.
- The `Roadwright's Workshop` should use the same `5x8` footprint, bed placement, work-bay shape, roof language, third-level wall torch, and later lantern-upgrade intent as the `Carpenter's Workshop`.
- The key visual difference is material: the enclosed room side walls should be made from stone-family blocks instead of planks, while the roof, logs, floor, and work bay can remain wood-family materials.
- Completed Roadwright infrastructure should later unlock internal path connectors from the settlement core to workstation-associated buildings, route corridor creation, road upgrades, bridge maintenance, and `Milepost` placement.
- Right-clicking a `Surveyor's Table` should open a basic village survey map showing the settlement boundary, known buildings and POIs, loaded path surfaces with discrete quality colors and a legend, current Roadwright positions, planned or in-progress path work, and nearby `Mileposts`, including an edge indicator when a nearby `Milepost` falls outside the current map square.
- The survey map title bar should also show the current in-game day and an approximate time-of-day label, right-justified so it reads as ambient context rather than a loud debug widget.
- The survey map should support a player-oriented view where the top of the map follows the player's current facing direction, and its compass rose should rotate accordingly while staying in a non-obscuring UI position.
- The survey map should be able to show Roadwright plan intent at two levels: `Planned work` should be a short-horizon forecast of what nearby Roadwrights and available helpers are likely to complete within about the next in-game day, while `Route trace` should show a broader multi-day or multi-week corridor intent for the routes they are working toward.
- The survey map should support fog-of-war style coverage: actively surveyed or commonly visited areas are clearest, while unvisited areas appear fogged. Roadwrights should not have to personally visit every location; ordinary villager movement should refresh nearby map observations, reveal visited areas even when no road or POI is present, and let Roadwrights benefit from shared settlement knowledge. Survey observations and remembered Roadwright route intent should persist across world reloads, though stale areas may still later trigger renewed surveying.
- Fog-of-war coverage should be independently toggleable for playtesting. With the flag off, opening the survey map should still refresh the displayed roads and POIs from current loaded-world data without requiring an active `surveying` task, while Roadwrights may still report `surveying` as their current status when no immediate path-improvement task is cached.
- Small local terrain changes near an intended Roadwright corridor should cause the route plan to be recomputed from the same start and target before the map drops that intent; flattening or slightly reshaping a corridor should usually preserve the planned route rather than making it disappear outright.
- Roadwrights should start route surveying and path improvement from a standable block beside their own `Surveyor's Table`, then work toward the village center before fanning out to other POIs.
- Roadwrights should route new internal paths between exterior building entrances, workstations, and outdoor places of interest, rather than treating every bed, chest, barrel, or other interior utility block as a path destination. Mature internal roads should generally aim for a `3`-block-wide walking surface, while early rough trails may begin narrower and later widen or upgrade.
- Player-placed `Mileposts` should also act as route hints: nearby settlements with a `Roadwright` should try to connect their path network to reachable `Mileposts` even before another settlement exists at the far end.
- The first visible Roadwright work loop may lay simple `Dirt Path` blocks one at a time from the settlement core toward known workstation-associated structures, patch small gaps in existing loaded path surfaces, add basic stairs or slabs at one-block rises, and reshape short two-or-more-block jumps into one-block increments before treating the slope. Later road upgrades can replace these trails with gravel, cobble, brick, or other higher-quality materials.
- Roadwrights should prioritize obvious loaded-path touch-ups before broader surveying or long-route planning. Grass, dirt, podzol, or similar blocks sitting inside an established village path corridor should receive a quick shovel pass instead of waiting for a full route plan.
- If Roadwright path repair clears `Leaf Litter`, it should be collected into settlement stock as `leaf_litter` so Farmers can later compost it.
- Unemployed villagers may help a present Roadwright with visible loaded path work, but should not independently plan road changes without at least one Roadwright in the settlement; helper planning should be bounded per maintenance pass so path work cannot scale into one expensive planning request per adult villager.

### Baker Details

- `Baker` is a planned food-processing profession with a dedicated workstation and associated `Bakery` structure.
- The `Bakery` should use the same `5x8` overall footprint and broad layout language as the `Trading Post`, but replace the fence market openings with display cases or glass-fronted vertical shelves for baked goods.
- Vanilla glass panes should not be relied on as horizontal display-case lids; panes are vertical connection blocks and do not create the shelf-top case shape by themselves. Prefer a later custom `Bakery Display Case` block or block-entity model with shelf geometry, transparent glass faces, and rendered baked goods. A lower-cost fallback is a vanilla-looking shelf behind normal glass blocks or panes.
- Gates should remain in the market-facing openings so the structure still feels like an accessible shopfront.
- Settlement behavior should convert village supplies into baked goods, including bread, cakes, pies, cookies, and similar foods when the required ingredients are available.
- Baker profession trades should let players trade relevant inputs such as eggs, wheat, milk, sugar, or fruit for baked goods, and should integrate with the broader profession-specific trade model rather than crowding every possible food trade onto the `Trade Board`.

### Beekeeper Details

- `Beekeeper` is a planned custom profession for managed hive production and bee-safe village ecology.
- The Beekeeper workstation is the `Honey Separator`. Placing one in or near a settlement should create or resume a staged `Beekeeper's Apiary` when a valid village-edge footprint is available.
- The `Beekeeper's Apiary` should be a small outdoor or semi-covered structure with a covered work area, flower planting, managed bee hives, protected campfire smoke under harvestable hives, and enough open air that bees can move naturally.
- Apiaries should prefer dispersed village-edge sites rather than dense central plazas, path corridors, or obvious future structure footprints.
- Most villages should support only one Beekeeper unless local hive capacity is unusually high. A settlement should not prioritize a Beekeeper when it has no reachable bee nests, bee hives, or placed `Honey Separator`.
- Loaded Beekeepers should visit bee nests and hives, place or maintain safe campfires below managed harvest hives, collect honey into glass bottles, collect honeycomb with shears, breed bees with flowers when hive population is low, and grow toward at least `3` managed hives by placing crafted hives in flower-rich locations.
- Beekeepers should not destroy natural bee nests during ordinary work. Any future relocation behavior must preserve the bees rather than treating nests as raw blocks.
- Settlement behavior should add honey bottles, honeycomb, candles, and modest food or comfort value, while creating discoverable demand for glass bottles, shears, campfires, flowers, wood, and hive-building supplies.
- The Beekeeper villager texture should be mostly white, reading as a protective beekeeper suit: pale hood or veil silhouette, white tunic and leggings, and light gloves or boots.

### Debug and Inspection Tools

- For staged construction validation, prefer a rebindable client key that toggles a red wireframe preview of planned blocks that still need to be built over a text-only debug command or overlay. The first-pass default key is `O`.
- The build-site wireframe should use the nearest or targeted active build site in the current dimension, render only unbuilt planned blocks, and hide blocks already correctly placed by villagers or players.
- The red preview should include pending, missing-material, and blocked build-site blocks so footprint, orientation, composition, and player-assist opportunities can be checked in-world before the structure is complete.
- The wireframe should come from server build-site state rather than client-side blueprint guessing, and it should stay lightweight enough to use while watching villagers work.
- The lightweight settlement overlay should default to `I`, remain fully rebindable through vanilla Controls, query the nearest settlement in the current dimension rather than requiring the player to open a workstation first, and render as a lightweight F3-style text overlay rather than a boxed menu.
- Settlement info screens and overlays should label queued road projects explicitly as roads to named partner settlements, rather than showing only the partner settlement name and a percent with no context.
- Settlement shortage views should account for obvious near-term internal conversions when presenting needs. For example, large spare `wheat` reserves above the settlement's wheat target should count toward effective `bread` supply so the UI does not ask the player for bread the village can already bake for itself.
- Repeated presses of `I` should cycle through the defined inspection screens and then hide the overlay on the next press after the last screen.
- The initial screen order should be `Overview`, then `Inventory`, then `Population`, then `Workers`, then `Trades With Villages`, then off.
- Future inspection screens can be added later, but they should slot into that rotation before the final off state instead of replacing the toggle behavior with a separate control.
- The inspection output should show at least the settlement name or id, population, stock or inventory, key shortages or surpluses, current profession counts, current visible task categories, construction worker availability, active construction block counts, and trade route summaries.
- The overlay should support both aggregate and per-worker observability. Aggregate pages can summarize role counts and task categories, while a dedicated worker-detail page should list individual villagers with their current visible task, and optionally profession-specific target or route detail when available.
- The tool should be fast enough for playtesting simulation behavior while walking through a loaded village.

### Worker Equipment and Self-Defense

- Combat-capable field workers should carry profession-specific tools or weapons when defined.
- Field workers without a more specific weapon rule should fall back to at least a `Wooden Sword`.
- Deskworker professions such as `Scribe`, `Trademaster`, `Cartographer`, `Cleric`, and `Librarian` should stay unarmed and run from hostile mobs.
- `Sling`: a weaker bow-class ranged weapon with built-in unlimited low-power ammunition.
- `Crooked Staff`: an improvised melee weapon better than a stick but weaker than any proper vanilla weapon.
- `Scythe`: a tool-weapon better than a `Crooked Staff` but still weaker than any proper vanilla weapon.
- `Farmers` carry hoes and scythes.
- `Gardeners` carry slings.
- `Shepherds` carry slings and crooked staffs.
- `Fishermen` carry spears or tridents.
- `Foresters` carry axes and hoes.
- `Miners` carry pickaxes.
- `Beekeepers` wear mostly white protective suits and carry shears when available; their task loadout should also account for glass bottles and flowers.
- `Fletchers` carry bows with profession-supplied unlimited arrows.
- Loaded `Fletchers` should treat nearby hostile mobs as a village-defense priority, firing skeleton-strength bow shots while hostiles are inside the settlement.
- Loaded `Fletchers` should use stocked `stick`, `feather`, and arrowhead materials at a `Fletching Table` to convert them into town `arrow` stock when no immediate defense target is present.
- When a village bell rings, civilians and other non-combatants should keep the default vanilla panic/hide response, while combat-capable villagers inside `max(25, half settlement radius)` of that bell should rally toward the detected nearby hostile instead of fleeing.
- In the current first pass, that bell-rally group should at least include loaded `Fletchers`, `Butchers`, `Foresters`, and `Roadwrights`, with other armed field workers joining as their role behavior comes online.
- A placed vanilla `Fletching Table` in a settlement should start a `Fletcher's Hut` build site when terrain allows, using cobblestone flooring, plank siding, and `2` beds.
- Each settlement `Fletching Table` can support up to `2` Fletchers assigned to that workstation.
- `Fletchers` should also contribute a smaller abstract security benefit than a dedicated `Guard`, so having one improves the settlement security rating even before the loaded-world combat loop is perfect.
- `Leatherworkers` should wear leather armor and slowly distribute spare leather breastplates when stock allows.
- `Weaponsmiths` and `Armorers` should equip the best gear available to their role and then upgrade other villagers over time.
- `Guards` should be first priority for sword, breastplate, shield, and similar combat upgrades; `Roadwrights` should be the next priority after guards.

### Fletching Table Use

- The vanilla `Fletching Table` should become a real player-use workstation in Live Villages rather than a decorative placeholder.
- The station should be the arrow-specialist equivalent of a `Stonecutter`: better throughput for ordinary ammunition and access to special arrow variants that are not available from the normal crafting table.
- The vanilla crafting-table `flint` arrow recipe may remain for compatibility, but `Fletching Table` recipes should be materially more efficient so players have a reason to use the station.
- The `Fletching Table` should also craft ordinary vanilla `Arrow` batches, not only the new material-arrow family, and the workstation version should clearly outperform the ordinary crafting-table recipe.
- Since vanilla `Copper Nugget` exists in the target game version, the ordinary metal-arrow recipe family should use vanilla `copper nugget` instead of `flint` at the `Fletching Table`.
- First-pass `Fletching Table` recipes should be batch recipes rather than shaped `3x3` crafting-grid recipes. Initial tuning target:
  - `Arrow` batch: `1 stick`, `1 feather`, `1 flint` -> `8` arrows
  - `Copperhead Arrow` batch: `1 stick`, `1 feather`, `1 copper nugget` -> `8` arrows
  - `Ironhead Arrow` batch: `1 stick`, `1 feather`, `1 iron nugget` -> `8` arrows
  - `Diamondhead Arrow` batch: `1 stick`, `1 feather`, `1 diamond` -> `4` arrows
- Final item names should avoid colliding with vanilla potion `Tipped Arrow` terminology. `Copperhead Arrow`, `Ironhead Arrow`, and `Diamondhead Arrow` are acceptable working names until a naming pass decides otherwise.
- `Copperhead Arrow` is the efficient general-purpose baseline and should be the default arrow stock a normal settlement `Fletcher` prefers to produce.
- `Ironhead Arrow` should perform better against armored targets and naturally tough-skinned or thick-hided mobs. First-pass implementation should use partial armor bypass or a damage bonus keyed from explicit entity tags rather than a vague hardcoded mob list.
- `Diamondhead Arrow` should deal substantially more raw damage to unarmored targets, but should not also be the best armor-piercing choice. It should be the premium flesh-damage arrow, not a universal upgrade in every combat case.
- First-pass special arrows should be a separate material-arrow family from vanilla potion arrows. They should not also carry potion effects unless a later design explicitly adds combination recipes.
- Settlement production should follow the same recipe family the player uses. Ordinary villages should prefer `Copperhead Arrow` output for stock, while `Ironhead Arrow` production should require actual iron surplus or elevated defense demand; `Diamondhead Arrow` production can remain player-crafted only in the first pass unless later economy rules justify automated use.

### Productivity Rules

- Generalist villagers should perform around player baseline.
- Specialists should outperform a player or generalist in their specialty.
- Specialists should be somewhat worse at unrelated jobs.

Example guideline:

- A forester cuts wood about `50%` faster than a baseline worker.
- That same forester is about `10%` worse at unrelated labor.

## Housing, Comfort, and Growth

Villagers should seek better living conditions over time.

- Villagers want their own bed and real housing.
- Starter housing shelters should read as small enclosed huts with a roof, solid wall coverage, a usable ground-level entrance, and enough headroom over the bed lane for villagers to enter and sleep successfully, not just a bed under an awning or a stacked roof platform.
- The shelter roof should keep the two center ceiling cells over the bed lane as upper slabs rather than full planks, and the block above the door should read as an upside-down stair lintel so the hut stays usable without looking flat or clipped.
- The simple fallback shelter should still leave a small interior standing area beside the bed and a clear step between the door and the bed, and it should include a basic interior light source.
- The larger `Housing Shelter` should preserve that same basic usability while acting as a true upgrade over the simple shelter: it should have windows, enough standing space in front of the beds, two single-villager beds placed along opposite walls so it can house two villagers, and a centered ceiling lantern rather than only a wall torch.
- Players should be able to craft dedicated `Simple Housing Shelter` and `Housing Shelter` placement blocks. When one is placed successfully, that block position becomes the shelter's lower door block and the placed facing becomes the shelter entry orientation. The placement should start or resume an ordinary staged build site rather than placing the whole shelter instantly, so villagers can construct it over time, pause on missing materials, and finish later. The crafted block's recipe components should be treated as contributed build materials for that shelter placement, with any remaining shortfall coming from settlement stock as available. Once door materials are available, villagers should clear the placed shelter marker promptly and replace it with the planned lower door block rather than leaving the marker in the completed doorway.
- Housing quality, decoration, safety, and food access should affect comfort and growth.
- Maintained decorative landscaping such as raised flower beds near homes, paths, and public spaces should contribute modestly to comfort.
- Better homes can raise comfort and support more population.
- Special comfort items or premium beds can exist as future extensions.
- Growth must be capped and tied to housing and food so villages do not expand without constraint.

### Second-Floor Apartment Upgrades

If a settlement needs more housing, completed workstation structures such as the `Carpenter's Workshop` may later add a second-floor apartment as a staged upgrade instead of always claiming a new footprint.

- The apartment should be built as its own build site on top of the completed structure and should only count as housing when complete.
- The added room should match the dimensions and content language of the room below it: a `3x3` interior space with at least one bed, walls, windows where appropriate, and a similar roof.
- The upgrade should include stairs up to a small platform at the door, and the door may move to a more convenient location when needed for pathing.
- These upgrades should obey the general duplicate-build and repair rules so a structure does not queue multiple apartment additions or repair jobs at the same time.
- The design should avoid reviving the old shelter-stacking issue; multi-story housing should be explicit, accessible, and tied to compatible completed structures.

Example later layout:

```text
[2nd floor 1st level]
LPPPL
PBAAPPP
PBAADPP
PAAAPPP
LPPPLS
AAASSP

[2nd floor 2nd level]
LPVPL
PAAAV
VAAAD
PAAAV
LPVPL
```

Possible effects of build queue completions:

- New house plot increases housing capacity.
- Better house or townhouse increases both capacity and comfort.
- Wall or defensive structure increases security.

## Security, Guards, and Hostile Outposts

Security is a settlement-level stat influenced by:

- Guards
- Walls and defensive structures
- Safe corridors
- Nearby hostile pressure

Guard infrastructure should make defended history visible:

- The `Guard Post` / Guard shack should accept donated weapons, armor, shields, ammunition, and spoils of war.
- Donated pillager banners and similar raid trophies should be converted into `Desecrated Enemy Banner` display trophies rather than used as normal decoration.
- Completed Guard structures should be able to mount `Desecrated Enemy Banners` as a visible count or record of raids and pillager bands the village has survived.
- Spoils-of-war donations should contribute to security, morale, or guard equipment support, with tuning kept modest so trophies are meaningful without becoming required progression.

Low-security settlements should suffer:

- Reduced route safety
- Slower or more expensive trade
- Greater raid losses
- Lower growth confidence

Pillager outposts should act like hostile settlements, not just mob spawners.

- Outposts maintain their own stock, wealth, and pressure.
- Outposts do not participate in normal village trade; they acquire goods by raiding settlements, attacking routes, extorting nearby traffic, and pillaging what they need.
- Outposts should skew toward offensive and coercive professions such as guards, armorers, weaponsmiths, and other raid-support labor rather than peaceful comfort roles such as farmers or gardeners.
- If an outpost needs a new structure, it can build without waiting for the exact matching profession to exist, but that fallback construction should be slower and less efficient than specialist village labor.
- They target weak settlements and poorly defended routes.
- Successful raids steal goods and wealth.
- Outposts can spend resources to reinforce themselves or recruit more hostile units.
- Players should be able to disrupt outposts by fortifying routes, intercepting raids, or attacking the outpost directly.

## World State vs Abstract Simulation

The real game world should be authoritative when it can be observed: actual buildings, workstations, beds, farms, animals, villagers, roads, docks, and defenses should define what a loaded settlement can do.

The abstract simulation still matters for unloaded or distant settlements, but it should act as a catch-up model rather than a permanently separate reality.

- When a settlement is loaded, scan the actual world and reconcile settlement state from real villagers and real infrastructure.
- Production should use real local conditions where practical. For example, farmers should produce based on actual gardens, crop state, seeds, composters, hay bales, nearby husbandry infrastructure, and the settlement's current food shortages; butchers should use actual herd and pen state.
- Loaded farmers should treat the `Composter` as a managed farm-territory anchor and roam that territory rather than idling near only a tiny starter bed.
- Loaded farmers should prioritize ripe crops first, then collect loose crop or seed drops, then plant empty farmland from settlement stock, then collect or apply bone meal, then collect leaf litter and trim grass when no higher-priority crop task is pending.
- If a settlement is missing a crop entirely, trade should be able to import starter stock such as potatoes, and loaded farmers should begin planting that crop once seed stock is available.
- Harvested crops, harvested seeds, and loose crop or seed drops recovered inside the managed farm territory should be added to settlement stock, and empty farmland should be replanted from that stock according to current shortages.
- Farmers should not trample managed farmland while moving through their territory.
- Farmers should recognize both floating `leaf_litter` item drops and flat ground `Leaf Litter` blocks inside their territory as collectible compost input.
- When a managed composter has produced bone meal, or loose nearby `bone_meal` is available in the farm territory, farmers should collect it and spend it on the least-ready eligible crops, with a bias toward crop types the settlement needs most.
- Loose nearby `leaf_litter` should be collected and deposited into the managed composter. Short and tall grass inside the farm territory should be trimmable by farmers; trimming can yield seeds and should also produce a small randomized amount of `leaf_litter` for composting. Zero remains a valid outcome if tuning lands there. Wheat harvests may also contribute a small virtual `leaf_litter` yield to keep the compost loop active.
- Loaded workers should remember recently unreachable tasks for a short time so they do not repeatedly path into the same blocked or invalid target.
- Non-urgent loaded-worker chores should respect time of day and bad weather rather than running with the same urgency at all times.
- Late in the village day, villagers should keep the vanilla-feeling bell gathering routine as an explicit social task before returning to their claimed home beds for the night.
- Loaded villagers should keep stable professions when possible. Custom-role promotion should prefer unassigned adults and should not repurpose existing vanilla professionals just because a custom role is missing.
- Loaded villagers should claim individual home beds when available and should keep returning to the same valid bed instead of fighting over beds every evening.
- Current-task overlays should show profession-aware visible intent such as tending crops, managing trades, improving paths, surveying the village, village gathering, returning home, or raising a nearby child, rather than only reporting construction availability.
- Loaded worker presentation should favor visible representative actions such as explicit composter visits, visible pickup moments, or small carried-task loops before goods return to abstract settlement stock.
- Loaded worker behavior should follow an explicit OODA shape: observe local world state in bounded slices, orient around cached settlement/workstation context, decide only on staggered per-worker intervals, then keep acting on the selected short task until it is done, invalid, or stale. Observations that many roles share, such as nearby villagers and loaded Surveyor map data, should be cached per settlement slice rather than rescanned by each worker, and repeated no-task planning results should be cached where absence of work is itself useful information.
- Villagers should take short staggered daytime breaks so loaded workers do not all plan at once. Breaks should be deterministic enough to avoid synchronized spikes but varied enough to make the village feel natural.
- Farmers should score plot quality and deprioritize dry, blocked, or otherwise unhealthy farmland until the player repairs it.
- Loaded gardeners should build and maintain real decorative raised beds near houses, under windows, near doors, along paths, and beside places of interest when valid space exists, using dirt beds with trapdoor edging and seeded flowers while avoiding road blockage or obvious future structure footprints.
- Loaded beekeepers should use bounded scans for nearby bee nests, bee hives, flowers, and safe campfire placements, then commit to short hive-management tasks so apiary work does not become a broad per-tick village scan.
- When an unloaded settlement catches up, advance the abstract state in bounded steps, then apply completed physical changes to loaded chunks when possible.
- After catch-up changes are applied to the world, resurvey and reset derived settlement state from reality so virtual state does not drift away from the actual village.
- Nearby workers should place path, wall, dock, workstation, or house blocks only when the related project is actually progressing or completing.
- Nearby caravans or boats appear only when a real shipment is occurring.
- Nearby guards patrol meaningful locations.
- Distant settlements continue progressing entirely through state updates.

This keeps the world believable without requiring full simulation everywhere.

## Performance Model

We cannot afford to have every village and every villager running full behavior constantly.

Planned approach:

- Partition the world into economy regions, for example `512 x 512` block areas.
- Maintain a round-robin list of regions or settlements.
- Every fixed interval, update only a limited number of them.
- Each settlement stores a `lastUpdateTime`.
- On update, simulate elapsed time since the last update.
- On chunk load, catch up progress in bounded steps rather than exploding work all at once.
- Favor queue advancement and ledger math over always-on pathfinding AI.
- Only place physical blocks in loaded areas, but fully implementing changes that were simulated.

This preserves the original idea of periodically updating the least-recently simulated village while expanding it into a region-based scheduler that should scale better.

## Technical Direction

- Fabric-only implementation.
- Internal modular structure is preferred even if this ships as one mod:
  - Core settlement and economy
  - Routes and logistics
  - Ports and water trade
  - Security and raids
  - Visual and cosmetic overlays
  - Optional compatibility hooks
- Use tags, datapacks, and config data instead of hard-coding every road material, currency, or trade category.
- Lean into data-driven villager trade definitions on `26.1+`.
- Profession behavior should move toward data-driven task priorities, thresholds, and material preferences that can be tuned or reloaded separately from the core engine where practical.
- A full embedded profession scripting language is not a current requirement; revisit that only if the data-driven layer proves too rigid or if future settlement-specific behavior profiles need more expressive power.

## Configuration and Compatibility

- Core features should work with no external mods installed.
- Optional compatibility layers can support:
  - alternate currencies
  - guard or villager-role mods
  - road or structure mods
  - ship or cargo mods
- During development, optional compat mods should be loaded as development/runtime dependencies in the dev client, not bundled into the core mod by default.

## Current Implementation Gap Checklist

Checked against the current codebase on April 12, 2026.

Already present in code:

- Trade Board presentation and Carpenter's Bench content on the `carpenter_bench` block, including blocks, items, recipes, model assets, language entries, a loot table, a dedicated Stonecutter-style Carpenter's Bench UI with first-pass wood conversion recipes, loaded-settlement Carpenter promotion, Carpenter job-site assignment, and Carpenter priority for the bed near the bench.
- Trademaster and Carpenter villager professions and POIs.
- Trade Board linking to the nearest non-outpost settlement or founding a custom settlement when no settlement is nearby.
- Placed-workstation anchoring for `Carpenter's Bench` -> `Carpenter's Workshop`, `Surveyor's Table` -> `Roadwright's Workshop`, `Trade Board` -> `Trading Post`, and player-placed vanilla `Cartography Table` -> `Cartographer's House`, including nearby `can't build here` signs for blocked footprints when a sign spot is available. Valid player-placed workstations now create or resume persistent build-site records instead of instantly placing the associated structure, and build sites can dry-run material availability so planned blocks can be marked `pending` or `missing_material`. Loaded construction maintenance assigns nearby adult non-Nitwit villagers to claim one pending planned block at a time, walk toward it, clear natural obstructions, visit a known `Trade Board` or `Trading Post` anchor to retrieve or auto-craft needed supplies, and place the block; correctly player-placed planned blocks are recognized as `player_placed`. Completed staged `Trading Post` sites provide local and route trade output bonuses, while incomplete `Trading Post` sites do not; incomplete `Carpenter's Workshop` build sites are tracked as in-progress rather than counted as completed workshop infrastructure. Structure blueprints can now carry optional orientation rows, currently used by the `Cartographer's House` roof stairs so stair rotation does not depend only on edge heuristics; orientation-sensitive blocks such as stairs, slabs, doors, torches, fences, fence gates, beds, and logs require exact blockstate matches during construction reconciliation.
- Generated settlement names, persistent settlement state, persistent route state, round-robin region scheduling, village autodetection, and debug commands.
- Trade Board UI tabs for overview, trades, and routes, with settlement name, population, housing, comfort, security, shortages, surpluses, stock, projects, and route summaries. The trade tab focuses on village needs and village offers, stays on the trade tab after trade refreshes, and construction material blockers can add needs such as glass, sand, beds, or wool.
- Settlement ledger basics: population, wealth, stock, housing capacity, comfort, security, defense level, growth progress, projects, production, food/upkeep consumption, price pressure, growth, and inter-settlement stock transfers.
- Loaded resource work: villagers can carry nearby useful dropped goods and deposit them at the `Trade Board` / `Trading Post` before evening gathering; Foresters can collect forest drops, opportunistically pick up nearby player-left logs and saplings, cut mature non-bee trees, replant seedlings, and maintain sapling reserves; Butchers can shear sheep, feed breeding stock, breed cows, sheep, and pigs, cull excess livestock, build first-pass fenced pens with gates from settlement stock, and herd nearby stray animals toward those pens across the settlement's livestock territory; Carpenters can turn excess logs into planks, slabs, stairs, and sticks.
- Partial world reconciliation: actual villager census, Trademaster promotion, infrastructure scans for beds, composters, storage blocks, and Carpenter's Benches on the `carpenter_bench` block id, loaded farm/cow/sheep-based food production, and simple world placement for housing shelters, Carpenter's Workshops, composters, and storage chests.
- Land route state and route surveying based on path, gravel, cobble, brick, smooth stone, and bridge-like materials. Loaded Roadwright work can connect known workstation structures to the internal path network by targeting the outside of structure doors first, then falling back to the workstation anchor. Roadwright route planning uses a bounded terrain-aware search for the next missing trail block instead of sampling only a straight line.

Remaining steps to close the spec/code gaps:

1. Finish the placed Trade Board visual work: resolve the known north-south dark-face issue, then add a client-side name renderer that displays the linked settlement name on both board faces.
2. Formalize settlement knowledge and advancement tiers: add recipe/knowledge state, starter knowledge, knowledge trading, and advancement-gated autonomous build rates.
3. Expand world-authoritative reconciliation: scan more infrastructure types, apply unloaded catch-up changes to real loaded chunks in bounded batches, extend placed-workstation anchoring beyond the current first pass, replace remaining instant autonomous builds with staged villager construction, reconcile completed build-site effects more deeply into UI/project summaries, add visible Trade Board stock retrieval/deposit visits and persistent villager-carried goods where useful, and add stronger loaded-world job behavior for gardens, herds, pens, roads, and other profession infrastructure before resurveying derived state.
4. Flesh out the Trade Board UX: expose knowledge goals, fuller project details, clearer price/offer information, route quality/safety/capacity, Trading Post upgrade state, profession-specific trade entry points, and any future settlement renaming or naming rules.
5. Complete founding behavior: tune minimum spacing and link radius, verify founder timing, ensure the founding villager reliably becomes Trademaster, and make the board/founder status clear to the player.
6. Tune or data-drive the Carpenter's Bench recipe set, add any missing wood-heavy outputs, then continue adding or refactoring the remaining roles and workstations from [PROFESSIONS.md](PROFESSIONS.md): Baker, Beekeeper, Forester, Miner, Gardener, Guard, Roadwright, Portmaster, Scribe, Bakery, Beekeeper's Apiary, Warehouse, Guard Post, Honey Separator, Miner Workstation, Forester's Table, Gardener Workstation, Surveyor's Table, Milepost, Dock, and Scribe Desk, while expanding vanilla Cartographer, Farmer, Butcher, Mason, and Carpenter behavior.
7. Replace hard-coded goods, target rules, and profession-priority heuristics with data-driven rules using tags, datapacks, and config where practical.
8. Build out physical warehouse behavior: capacity, interaction, storage visualization, route efficiency bonuses, and reconciliation between physical storage blocks and the ledger.
9. Expand construction queues: add real defensive works, road/path placement and upgrades, dock/harbor projects, warehouse projects, Trading Post and Shopping Mall projects, workstation-linked building jobs, maintenance, and advancement-tier build-rate caps.
10. Finish land-route gameplay: nearby Roadwright-built corridors, Surveyor's Table and Milepost rules, internal path upgrades with comfort impact, path connectors to workstation buildings so they count in the local trade network, trade-weight bonuses from connected workstations and civic trade improvements, material-based road upgrades, bridge rules, named and direction-facing Milepost presentation with optional distance text, route maintenance, cartographer support for long-distance trade, and route security from guards and hostile pressure.
11. Implement water trade: harbor detection or founding, dock validation, Portmaster role, river/canal/sea-lane routes, dredging/canal projects, water throughput, and nearby cosmetic boats.
12. Implement hostile outposts as settlements: detection/founding, stock and wealth, hostile pressure, raid planning, route attacks, stolen goods, offensive-biased staffing, inefficient fallback construction without exact profession matches, reinforcement spending, and player disruption hooks.
13. Add guards and security behavior beyond the current stat: guard posts, patrols, corridor protection, raid losses, and security effects on growth and trade costs.
14. Add personal villager goals where useful without turning the system into full per-villager supply-chain accounting.
15. Add compatibility hooks for optional currencies, guard/villager-role mods, road/structure mods, and ship/cargo mods without making them core dependencies.
16. Add focused test coverage and playtest checks for settlement persistence, catch-up behavior, Trade Board trades, route transfers, village autodetection, custom settlement founding, and construction placement.

## MVP Roadmap

1. Settlement and route `PersistentState` scaffolding plus round-robin scheduler.
2. Trade Board UI and warehouse ledger.
3. Production, consumption, price calculation, and settlement growth.
4. Worker queues for housing, roads, and defenses.
5. Land route quality and inter-settlement trade.
6. Harbor system, docks, water routes, and the Portmaster role.
7. Hostile outpost economy and raid behavior.
8. Cosmetic caravans, boats, and deeper villager personal behavior.
