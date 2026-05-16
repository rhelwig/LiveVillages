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

- Each settlement stores persistent state for population, stock, wealth, advancement tier progress, housing, comfort, security, construction queues, and trade routes.
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

## Settlement Naming

- Settlement names should be unique within a dimension.
- Founding names should respect the local biome so obviously mismatched terrain or wood-family words do not show up in the result. Desert settlements, for example, should avoid forest-heavy names such as `Oak...`.
- Hostile settlements such as pillager outposts should use a more ominous naming pool than civilian villages, harbors, or custom settlements.
- `Harbor` settlements may still append a harbor-specific title, but the core name should remain biome-appropriate.

Example settlement state:

```json
{
  "id": "village:123_456",
  "center": [123, 68, 456],
  "type": "village|harbor|outpost|custom",
  "tier": 1,
  "highestUnlockedTier": 1,
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

## Settlement Tiers

Settlements should use a four-tier civic progression model that gates which upgrades, public projects, path materials, and structure variants they can pursue.

- `Tier 1`: the default founding tier. New settlements start here. They focus on survival, starter housing, rough `Dirt Path` routes, simple workshops, and first-pass defensive works such as wooden log palisades or fenced boundaries.
- `Tier 2`: unlocks when a settlement has at least `500` emeralds and at least `16` villagers. This tier enables more established village improvements such as `Cobblestone` path upgrades, stronger workshop finishes, larger storage, and cobblestone defensive walls.
- `Tier 3`: unlocks when a settlement has at least `5,000` emeralds and at least `32` villagers. This tier enables more substantial civic works, `Smooth Stone` route upgrades, larger or upgraded profession structures, and dressed-stone fortifications such as smooth stone or polished granite / polished diorite walls.
- `Tier 4`: the top planned tier for now. Treat the unlock gate as provisional and tuneable; first-pass target is at least `20,000` emeralds and at least `48` villagers. This tier should unlock prestige upgrades, major civic expansions, stronger gatehouses or towers, and the settlement's best polished-material variants, including elegant mixes of `Stone Bricks` and `Bricks` for major paths near high-value civic destinations.
- These gates should become data-driven later, but the above thresholds are the first-pass balancing targets.
- Tier unlocks should be persistent progression, not volatile moment-to-moment state. If a settlement reaches a tier and later spends emeralds or temporarily loses population, it should not immediately downgrade or tear down completed upgrades.
- The settlement should still track its current live wealth and population separately from unlocked tier progress so UI and planning can show both the present economy and the best tier reached.
- The `Trade Board`, overlays, and debug views should show the settlement's current tier and the next unlock requirement.
- Tier should affect more than cosmetics: it should gate structure blueprints, material choices, path upgrades, fortification projects, and future autonomous-build-rate caps.
- Autonomous repair or upgrade passes should clamp their material targets to what the settlement's current allowed tier supports; first-pass starter masonry should stay on local starter stone families until the fuller later-tier upgrade ladder is explicitly authored.
- Structure families should support tier-aware variants or staged upgrades. Not every tier needs a completely different footprint; some tiers may only swap materials, lighting, or small attachments, while later tiers may add a second floor, wings, walls, towers, or other larger expansions.
- The first-pass defensive material progression should be:
  - `Tier 1`: wooden log palisade / rough boundary
  - `Tier 2`: cobblestone or rough-stone wall
  - `Tier 3`: smooth-stone or polished-stone fortification
  - `Tier 4`: reinforced prestige fortification with upgraded gatehouse / tower language
- Fortification radius should also vary by tier:
  - `Tier 1`: aim for about `80%` of the settlement radius so a young village builds a meaningful but still affordable inner perimeter
  - `Tier 2` and above: aim for about `100%` of the settlement radius
- Tier-specific structure choices should still respect local wood family, stone palette, biome rules, and future style variants rather than forcing one universal block palette everywhere.
- Once a settlement reaches about `Tier 3`, a small number of profession structures may start desiring imported style variants from another biome set or material palette. This should be a selective luxury preference, not a full immediate village-wide reskin, and it should create new long-distance trade demand for prestige materials.

## Trade Board

The Trade Board is the central management workstation for a settlement. Visually it should feel like a community posting board or sign-like civic block, not just another chest menu.

When a player right-clicks the Trade Board they should see:

- A list of goods the settlement does not have enough of.
- A list of goods the settlement has in surplus.
- Basic settlement status: tier, population, security, housing, and key projects.
- Route and trade summaries.
- Important construction or knowledge goals.

The player trades against settlement stock rather than only a single villager inventory.

- Goods in shortage are bought above baseline price.
- Goods in surplus are sold below baseline price.
- The player can resolve shortages indirectly by building missing workstations or infrastructure and letting the settlement adapt.
- The Trade Board should show the highest-priority settlement-wide wants rather than every possible tradable item. Lower-priority or profession-specific trades should be handled by profession structures so the global board stays readable.
- Any new feature that creates demand for a good, block, or item should also account for that good in the settlement wish/desire/trade model. If the village can use it, the player should eventually have a discoverable way to donate or trade it.
- Miner-facing support and extraction goods should not stay permanently "unknown" once the settlement can realistically use them. `dirt`, `ladder`, and iron- or copper-bearing mining inputs should be recognized as known Trade Board goods at any tier, while `redstone` and `diamond` become known goods once the settlement reaches `Tier 2`.
- Active build-site blockers should raise matching goods onto the Trade Board's needs list. For example, unfinished glass windows should make glass and adaptable raw materials such as sand available as player trade or donation options; unfinished beds should make finished beds, wool, and supporting materials such as planks visible.
- Inter-settlement stock transfers should also respect outstanding active build-site material demand so settlements can import the exact goods their visible construction sites are still missing.
- Completed harbor and trade structures tracked through persistent build sites should continue to inform route planning and trade range even when those settlements are currently unloaded.
- Settlements should not have an arbitrary fixed cap on total trade routes; route count should emerge from reachable infrastructure, profession support, and whether worthwhile targets actually exist.
- Settlements can trade `emeralds` directly as one more route good and reserve asset, not only as an abstract tier-unlock counter.
- A settlement's want list may include a small number of brokered wants for goods that one of its active trading partners needs, even if the local settlement does not directly need them. This allows trade networks to relay materials toward deeper shortages instead of only handling strictly local demand.
- The Trade Board trading screen should support two direct selection flows:
  - `Your Goods`: the player selects an exact inventory item row from their carried inventory, with identical item types combined into one row, then chooses from the village payout options that are valid for that good when the board recognizes a normal trade category for it.
  - `Village Goods`: the player selects a village stock item the village is willing to offer, including arbitrary previously donated stock items when they have enough quantity for a bundle, then chooses which currently wanted player good to pay with.
- The village must not offer goods that are currently needed for active shortages, construction blockers, or normal stock targets. Trade payouts should only spend stock above the protected reserve for that good.
- The `Your Goods` flow should let the player donate the selected item row directly to settlement stock without taking a payout. The first-pass controls support donating one item, one trade bundle, or the full combined amount of the selected item row.
- The `Your Goods` list should surface every non-empty carried inventory item type, not only goods that already have settlement pricing. Unknown or not-yet-modeled items should still be donatable so settlements can hold them for future use, profession demand, or player-to-player barter through village stock.
- The `Your Goods` list should aggregate normal identical item types into one combined row, but non-empty storage containers such as `Bundle` should stay as separate rows so the player can choose which stored contents to release.
- If a `Your Goods` row is a non-empty carried storage container, the board should not offer the normal `Give 1`, `Bundle`, or `All` actions for that row. Instead it should expose `Donate contents`, which empties the container and merges its stored contents into settlement stock while leaving the emptied container in player inventory.
- Hovering a non-empty carried storage container row should preview its contents so the player can tell similar containers apart before donating their contents.
- Once a carried storage container is empty again, it may return to normal grouping and ordinary donation/trade handling as a regular item.
- The Trade Board should show the selected bundle size clearly enough that the player can tell what the `Bundle` action means before clicking it.
- The Trade Board trade screens should focus on actionable goods and offers. Broader stock/status details belong on overview, inventory, or route screens.
- After a successful or failed trade, the UI should keep the player on the current tab and provide immediate in-screen feedback in addition to chat.
- A later bulk-donation flow may still open a faster transfer surface for moving many arbitrary stacks from player inventory into village stock at once. This flow will likely need iteration so it is fast without accidentally taking items the player meant to keep.

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
- Visible staged construction should not place unsupported free-floating blocks in midair. A worker should only place a planned block once it has real neighboring support appropriate to that block, so partial builds still read like plausible construction rather than disconnected pieces appearing out of thin air.
- The player may help by placing valid missing blocks in the planned footprint. A structure only counts as a trade enhancement when every required block is complete.
- When the build-site preview is active and the player is holding a block accepted by the structure plan, matching placement positions should be highlighted.
- The build-site preview should use at least three colors:
  - `Green`: the held item is the exact planned block
  - `Amber`: the held item is the right structural family but the wrong material variant, such as `Spruce Planks` where `Dark Oak Planks` were planned
  - `Red`: the held item is not acceptable for that planned position
- When a prospective structure preview is invalid, it should also surface a short blocking reason to the player and highlight the first known offending world block or blocks in a distinct warning color so site-validation failures can be debugged in-world.
- Assisted placement through that preview should preserve the blueprint's orientation-sensitive properties such as stairs, doors, beds, fences, gates, and logs, but when the player is holding a compatible wrong-material block it should place that held material variant instead of silently converting it to the planned palette.
- A staged structure should count as functionally complete when a required block position has the right structural family and placement properties but the wrong material variant. Villagers may later normalize those cosmetic mismatches when settlement priorities allow, but the building should not stay nonfunctional just because a player used `Spruce` where the village originally planned `Dark Oak`.
- When the build-site preview is not active, player placement remains ordinary Minecraft placement and should not be coerced by the settlement construction system.
- Construction may start before every material is available. Missing material leaves unfinished positions until the settlement gains or crafts the needed blocks.
- Loaded construction workers should carry batched supplies from stock access points when possible instead of one block per trip, with at least a half-stack target for common block materials.
- A settlement should not queue or start a duplicate associated structure while an existing build site of that role is in progress. This applies generally to workstation-associated structures, civic structures, upgrades, and repairs, not only to `Carpenter's Workshop` sites.
- Workstation-associated structures may relocate the anchored workstation to the blueprint's intended final block, including vertical moves, without canceling the build site. Villagers should be able to remove the original placed workstation, recover it into construction supply, and place it at the planned workstation position as part of the same staged build.
- If a completed staged structure is damaged or has required blocks removed, it should reopen as a repair build site instead of causing the settlement to queue a duplicate replacement structure. The repair site should follow the same visible work, stock, and material rules as initial construction.
- Completed structures and civic projects should be eligible for later tier-based upgrades when the settlement unlocks a higher civic tier. These upgrades should reuse the same site where practical instead of spawning redundant replacement structures nearby.
- Trees, leaves, tall grass, flowers, saplings, giant mushroom blocks, and similar removable natural vegetation should not by themselves invalidate an otherwise legal structure site or turn the build preview red. If the terrain under them is suitable, the site is valid and workers may clear that vegetation as part of landscaping or early construction work.
- Blocks removed during valid structure clearing or landscaping should be recovered into settlement stock when the settlement has a meaningful recovered-good mapping for them, rather than being silently deleted.
- The placed workstation anchor is a protected planned block. Workers should not remove it while building around it; if it is missing or damaged, replacing it is part of finishing or repairing the structure.
- Wood in the structure may be any local family but should be consistent within the structure. For example, a spruce-heavy region should prefer spruce logs, planks, stairs, slabs, fences, gates, and doors.
- Stone-family structure materials should also prefer the local biome palette where it makes visual sense. Desert and beach builds should favor sandstone-family blocks; badlands builds should favor red-sandstone-family blocks; default village-style builds can stay cobblestone-oriented unless a later material system overrides them.
- Fence blocks in explicit structure blueprints should be placed with connection state derived from neighboring planned blocks so market posts connect cleanly to adjacent logs, fence gates, and solid supports.
- The `Trading Post` front should discourage villager pathing over the `Trade Board` or fence line. A small three-stair cap above the board, placed in the soffit/roof layer rather than directly on the board level, is acceptable when it preserves the market frontage and keeps villagers using doors or side gates.
- Stone material quality affects trade value. Cobblestone is the lowest tier, stone is better, smooth stone is better again, granite and diorite are better than plain stone, polished granite and polished diorite are better than their raw forms, and stone bricks or similar blocks should be categorized into comparable quality tiers.
- A completed `Trading Post` may later be upgraded to better stone. Upgrades follow the same visible construction rules: villagers remove old blocks and replace them one block at a time when the settlement has enough better material.
- The same site should be upgradeable later into a larger `Shopping Mall` tier rather than requiring a different workstation block.

The first-tier footprint is `5x8`. These layer diagrams are authoritative for placement and composition. `A` means air or an intentionally empty block. As with the other directional structure diagrams, the first row is the back of the building and the last row is the front.

Floor:

```text
M = cobblestone or better

MMMMM
MMMMM
MMMMM
MMMMM
MMMMM
MMMMM
MMMMM
MMMMM
```

1st level:

```text
M = cobblestone or better, W = Trade Board, L = log, P = planks, B = bed, D = door, F = fence, G = fence gate, H = chest, A = air

LMMML
MBAHM
MBAHM
MAAAM
LMDML
GAAAG
FAAAF
LFWFL
```

2nd level:

```text
L = log, P = planks, D = door, V = glass block, A = air

LVVVL
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
P = planks, B = slab, S = stair, A = air

SSSSS
PAAAP
PAAAP
PAAAP
SSSSS
BBBBB
BBBBB
BBBBB
```

5th level:

```text
P = planks, S = stair, A = air

AAAAA
SSSSS
PPPPP
SSSSS
AAAAA
AAAAA
AAAAA
AAAAA
```

The stairs on this top roof level should be oriented to form a longitudinal roofline rather than short crosswise ridges.

The sleeping area should include a wall torch on the wall opposite the door at the third block level. When the settlement has enough wealth and materials, the torch should later be eligible for an upgrade to a lantern.

6th level:

```text
B = slab, A = air

AAAAA
AAAAA
BBBBB
AAAAA
AAAAA
AAAAA
AAAAA
AAAAA
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
- Preview mode should still show the `Trading Post` wireframe for a founding board outside any settlement radius so the player can judge the footprint before placement.
- If a founding board is outside every settlement radius but still within the minimum spacing of another non-outpost settlement, placement should be rejected and the preview / placement feedback should clearly say that it is too close to found a new settlement there.
- The new settlement starts with only the board and minimal virtual state.
- The new settlement starts at `Tier 1`.
- Within roughly one in-game day, a founding villager should spawn nearby.
- The first founder becomes the settlement's Trademaster and begins trying to establish warehousing, housing, food, and basic production.

## Trademaster

The Trademaster is the settlement's quartermaster, planner, and routing manager.

- The Trademaster seeks to establish a warehouse.
- The Trademaster commissions public projects through settlement queues rather than doing everything personally.
- The Trademaster can still work, but is less efficient at non-specialist labor than dedicated workers.
- The Trademaster handles inter-settlement trading and knowledge exchange. A village without a Trademaster cannot trade with other villages.
- Autonomous public expansion should be capped by settlement tier and knowledge rather than by one fixed rate.
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

Additional road improvements should be allowed, such as progressing from `Dirt Path` to `Cobblestone`, then to `Smooth Stone`, and finally to carefully used `Stone Bricks` or `Bricks`, with bridges and slope pieces using matching quality tiers where practical.

Proposed workflow:

- `Roadwrights` establish nearby route corridors and improve them over time.
- A `route` is traversable navigation between meaningful settlement points of interest rather than only a decorative line on the ground. First-pass route targets include the settlement meeting place or bell, structure entrances, standalone workstations that are not already part of a structure, storage or civic anchors that should join the local network, nearby `Mileposts`, and neighboring settlements.
- A `path` or `road` is the visible terrain improvement built along a route. Routes may be logically one block wide even when the visible path later widens into a broader walking surface.
- Once a settlement has a `Roadwright`, it should also extend internal paths from the settlement core to workstation-associated buildings so those structures become part of the local trade network instead of sitting as isolated job sites.
- Roadwright path placement should use a terrain-aware route search rather than a strict straight-line sampler. First-pass routes may still be simple, but they should prefer existing route surfaces and nearby `Mileposts`, preserve player-upgraded paths such as smooth stone, repair small missing path gaps, avoid blocked columns where practical, reshape short steep jumps into intermediate terrain, and bend naturally around building placement.
- The `Surveyor's Table` map should reflect current loaded-world road surfaces with enough fidelity to be trustworthy during testing: dirt paths, gravel, stone-family road blocks, and lightly covered path columns should not disappear just because the topmost motion-blocking block is slightly above them, and larger villages should not silently lose most of their displayed road columns to an overly small map-road cap.
- One-block path height changes should receive a stair or slab treatment on the lower side of the step so the path reads as a developed slope instead of a raw block jump. Two-or-more-block jumps on an intended or existing internal path should be reshaped into one-block increments before being treated with stairs or slabs.
- Roadwrights should discover local internal path targets from more than staged build sites. First-pass targets include known workstation structures, placed workstations, Trade Boards, composters/garden anchors, storage blocks, beds, and doors.
- Loaded Roadwright work priority should be: internal village paths first, then helping with village construction or repairs when no internal path work is available, then extending paths toward known nearby settlements and route corridors.
- Established routes should exchange goods in batched trade cycles rather than only opportunistic single-item drips, with small crude settlements trading roughly every several in-game days and larger better-connected settlements approaching daily or better cadence.
- Route trade should prefer mutually beneficial exchanges when both endpoints have meaningful shortages and useful surpluses, and goods should be able to move in either direction during the same trade cycle when that is warranted.
- Route demand should not stop at direct local needs. Settlements should be able to reserve a small amount of trade capacity for brokered goods that their active partners are asking for, so multi-hop supply chains can emerge instead of every route acting like an isolated pairwise barter loop.
- Connected workstation buildings should bias trade composition toward their specialty goods. For example, a `Leatherworker` building connected to the settlement path network should make leather goods more likely to appear in route exchanges.
- `Mileposts` mark established routes at regular intervals and help make corridors legible and maintainable.
- `Mileposts` should prefer regular intervals, village entries, and route junctions while avoiding obvious path blockage or high-value future build space.
- First-pass loaded placement should keep `Mileposts` outside settlement bounds, allow markers at the settlement edge, and then prefer about `100` blocks between markers along land routes; if another `Milepost` is already within about `50` blocks of the desired placement, that interval should count as already served and the next logical interval should be considered instead.
- Loaded `Roadwright`s should place `Mileposts` beside established external land routes rather than in the walkable path itself, should consume finished `milepost` stock instead of crafting the marker directly on demand, and should keep extending the visible path network outward from the farthest already-established route anchor instead of only rebuilding the same short corridor from the village core.
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
- When a usable boat is sitting at a dock in a loaded settlement, a `Fisherman` may take that boat out for a visible daily trip and should receive an extra catch bonus from doing so, while still returning and dismounting before the evening village gathering.
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
- Structure site validation should treat existing non-natural crafted or infrastructure blocks as blockers inside the planned footprint and within the surrounding `3`-block clearance buffer, so new structures do not collide with prior village or player-built infrastructure even when that infrastructure was added by this mod.
- Simple tamped `Dirt Path` surfaces made by shovel use should not count as structure blockers, including inside the surrounding clearance buffer.
- Improved route surfaces and road materials should block the planned structure footprint itself, but they should not by themselves fail the surrounding `3`-block clearance buffer. This keeps existing developed roads from being consumed by a new footprint while still allowing structures to be built near established route edges.
- Recognized improved route surfaces should be limited to intentional crafted road materials such as `Cobblestone`, `Smooth Stone`, `Stone Bricks`, `Bricks`, and their matching stair / slab variants where used for slopes or edging. Plain natural `Stone` and `Gravel` should not count as improved route surfaces by block type alone.
- Autonomous village road upgrades should not choose plain `Stone` or `Gravel` as route-improvement materials. First-pass road tiers should be `Dirt Path` for `Tier 1`, `Cobblestone` for `Tier 2`, `Smooth Stone` for `Tier 3`, and a tasteful mix of `Stone Bricks` and `Bricks` for `Tier 4`.
- Ordinary structure-margin clearing should remove clutter above the surrounding moat ground level, but it should not excavate the moat floor itself merely to make the `3`-block clearance ring look empty.
- If the moat-floor block contains a useful natural resource such as exposed ore that villagers are allowed to recover during placement clearing, they may harvest it, but they should backfill that ground-level spot afterward with ordinary dirt or a matching route surface when that column is clearly part of a route.
- Removable natural terrain and vegetation may occupy the structure footprint and the surrounding `3`-block clearance volume during site validation as long as workers can clear them at ground level and above; they should not be treated as blockers merely for being natural.
- Decorative or cultivated plants attached to man-made support, contained in flower pots, or planted within obvious bordered garden beds should count as part of existing structure or infrastructure rather than as removable natural vegetation.
- Water and lava should remain blocking for ordinary land structure placement unless a structure type explicitly defines a water-facing exception such as a `Dock`.
- Workstations are not always one-villager objects. A `Guard Post` should support up to `5` Guards as their shared job-site anchor. Worker-profession stations such as `Carpenter's Bench`, `Surveyor's Table`, and later Forester, Miner, Mason, and similar stations should usually support `1` or `2` villagers, with the second slot used when the village has enough population and demand.
- Associated workstation structures should assign their internal beds to villagers of the matching profession where possible. Worker structures that have room for a second resident should gain or use a second bed when the settlement needs another worker of that profession.
- When a profession has a bed-bearing associated structure, matching villagers should prefer those nearby structure beds before taking generic housing beds. `Miner` is the exception: the `Mine Entrance` is a workplace only and Miners should sleep in ordinary village housing.
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
P = planks

PPPPP
PPPPP
PPPPP
PPPPP
PPPPP
PPPPP
PPPPP
PPPPP
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
- `Baker`: turns settlement food ingredients into tier-gated baked goods such as bread, baked potatoes, cookies, pies, cakes, and later premium foods like golden apples, while avoiding non-bakery meat cooking outputs.
- `Baker` should present with a distinct bakery-themed profession overlay in-world, reading as a traditional white baker uniform with light blue trim or accents rather than falling back to a missing-texture placeholder.
- `Beekeeper`: manages bee nests and bee hives, produces honey bottles and honeycomb, maintains safe smoked hives, and grows dispersed apiary capacity from a `Honey Separator`.
- `Forester`: harvests and replants wood resources, must not cut down trees that contain bee nests or bee hives, maintains a sparse tree presence inside the village, manages denser woodland beyond the village core, and keeps a diverse seedling reserve that can create sapling trade demand.
- `Miner`: gathers stone, ore, and underground materials, anchored by a stone-like `Miner Workstation` and later a functional mine-entrance structure rather than housing.
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
- The `Surveyor's Table` map should prefer loaded-world truth over remembered surveying. When a player opens the map in a loaded settlement, roads, POIs, visible improvements, and planned roadwork should be refreshed directly from current world state instead of waiting on separate villager surveying or map-update chores. If a fog-of-war style mode returns later, treat it as an optional presentation layer rather than the source of truth for loaded settlements.
- Fog-of-war coverage should be independently toggleable for playtesting. With the flag off, opening the survey map should still refresh the displayed roads and POIs from current loaded-world data without requiring an active `surveying` task, while Roadwrights may still report `surveying` as their current status when no immediate path-improvement task is cached.
- With fog-of-war off, the survey map should aim to be a complete loaded-world representation within map range rather than a sparse sample: authored structure footprints, in-progress projects, and obvious built-world fortifications such as palisades should be visible as coverage instead of only isolated anchor dots.
- Survey-road detection should prefer intentional settlement path materials over raw terrain. Dirt paths, cobblestone-family improvements, smooth-stone-family improvements, brick-family improvements, and genuinely connected gravel paths should appear; natural exposed `stone` or isolated gravel patches should not be painted as roads just because they are on the surface.
- Fortification detection should distinguish built palisades from ordinary trees. Adjacent or slab-detailed log walls should be allowed to read as structures, but isolated leaf-topped tree trunks should not create scattered fake building marks across the map.
- Water within the surveyed map area should be visible as terrain context so docks, shoreline paths, and coastal fortifications make sense in the same view.
- Small local terrain changes near an intended Roadwright corridor should cause the route plan to be recomputed from the same start and target before the map drops that intent; flattening or slightly reshaping a corridor should usually preserve the planned route rather than making it disappear outright.
- Roadwrights should start route surveying and path improvement from a standable block beside their own `Surveyor's Table`, then work toward the village center before fanning out to other POIs.
- Roadwrights should route new internal paths between exterior building entrances, workstations, and outdoor places of interest, rather than treating every bed, chest, barrel, or other interior utility block as a path destination. Mature internal roads should generally aim for a `3`-block-wide walking surface, while early rough trails may begin narrower and later widen or upgrade.
- Land Roadwright planning should only use dry standable land-path surfaces. Harbor or shoreline route intent should stop at a valid land-side access point rather than extending explicit `Planned work` or `Route trace` blocks out through water.
- Player-placed `Mileposts` should also act as route hints: nearby settlements with a `Roadwright` should try to connect their path network to reachable `Mileposts` even before another settlement exists at the far end.
- The first visible Roadwright work loop may lay simple `Dirt Path` blocks one at a time from the settlement core toward known workstation-associated structures, patch small gaps in existing loaded path surfaces, add basic stairs or slabs at one-block rises, and reshape short two-or-more-block jumps into one-block increments before treating the slope. Later road upgrades should progress through `Cobblestone`, then `Smooth Stone`, and finally selective `Stone Bricks` / `Bricks` rather than using plain `Gravel` or natural `Stone` as intentional road surfacing.
- Roadwrights should prioritize obvious loaded-path touch-ups before broader surveying or long-route planning. Grass, dirt, podzol, or similar blocks sitting inside an established village path corridor should receive a quick shovel pass instead of waiting for a full route plan.
- After the settlement core, active build-site access, and other high-value local connectors are covered, Roadwrights should prefer extending land routes toward external settlements and `Mileposts` before inventing extra low-value local spur paths.
- If Roadwright path repair clears `Leaf Litter`, it should be collected into settlement stock as `leaf_litter` so Farmers can later compost it.
- Unemployed villagers may help a present Roadwright with visible loaded path work, but should not independently plan road changes without at least one Roadwright in the settlement; helper planning should be bounded per maintenance pass so path work cannot scale into one expensive planning request per adult villager.

### Baker Details

- `Baker` is a first-pass food-processing profession with a dedicated workstation and associated `Bakery` structure.
- The `Baker` workstation is the `Baker's Counter`, a shelf-style counter that still reads as bakery work furniture while also acting as a standalone bakery sale display.
- `Glass Display Case` is a full-size clear display block intended first for bakery use. It should read visually as a glass-fronted case rather than a half-depth shelf, while still serving as the bakery's visible goods display furniture.
- Placing a `Baker's Counter` in or near a settlement should create or resume a staged `Bakery` build site when a valid footprint is available.
- The `Bakery` should use the same `5x8` overall footprint and broad layout language as the `Trading Post`, but replace the fence market openings with `Glass Display Case` runs or other glass-fronted baked-goods displays.
- Vanilla glass panes should not be relied on as horizontal display-case lids; panes are vertical connection blocks and do not create a convincing full display case by themselves. The `Glass Display Case` is the first-pass answer for bakery displays, while a later block-entity or rendered-goods pass can add richer visible baked-good presentation and direct purchase interaction from the displayed stock.
- Gates should remain in the market-facing openings so the structure still feels like an accessible shopfront.
- `Glass Display Case` contents should live in a block-local inventory that is separate from the broader settlement stock. Bakers may move surplus baked goods into those case slots for sale, and players may also donate their own items into empty or matching occupied slots.
- Players should also be able to donate additional matching items into an already occupied bakery display slot when that slot already contains the exact same item and still has room, so bakery displays behave like ordinary stacks rather than one-item-only placeholders.
- Bakery display inventories should double as a first-pass pantry for matching recipe ingredients. If players donate usable bakery ingredients such as `wheat`, `egg`, `sugar`, `pumpkin`, or `cocoa beans` into bakery sale displays, the `Baker` may consume those donated stacks while producing baked goods.
- `Baker's Counter` should also have its own block-local sale inventory, using the same sale-screen model as `Glass Display Case`, so it works both as bakery furniture inside a full structure and as a standalone workstation/display block.
- Settlements should track both baked-food outputs and the obvious bakery ingredient goods needed to produce them, including at least `wheat`, `potato`, `egg`, `milk`, `sugar`, `cocoa beans`, `pumpkin`, `apple`, and the metal or fuel inputs needed by the currently unlocked bakery recipes.
- Settlement behavior should convert village supplies into baked goods when the required ingredients are available, while limiting the baker to bakery-style foods rather than general cooked-meat outputs.
- Bakery production should be gated by settlement tier. First-pass targets are staple foods like `Bread` and `Baked Potato` at `Tier 1`, richer sweets like `Cookies` and `Pumpkin Pie` at `Tier 2`, `Cake` at `Tier 3`, and premium outputs such as `Golden Apple` at `Tier 4+`.
- Some portion of bakery output should return to shared settlement food reserves so it actually feeds the village population instead of existing only as player-trade stock.
- Excess baked goods should be visibly reflected at the `Baker's Counter` and nearby `Glass Display Case` blocks so players can see bakery activity affecting the world. When bakers restock those displays they should treat all bakery sale displays in the structure as one shared presentation set: top off matching stacks first, then prefer the least-filled display so multiple cases and counters show goods before any one display becomes crowded.
- Players should be able to use `Glass Display Case` and `Baker's Counter` through a more vanilla-style sale screen that sells the specific stacks stored in that block rather than the settlement's abstract goods inventory. Empty slots should remain available for player donations, and bakery-produced goods may also offer ingredient-themed barter options alongside emerald purchases when that keeps the trade feeling bakery-specific. The sale screen should keep the slot grid and trade actions visually separated enough that stack counts, prices, and action controls remain readable at a glance, including a compact single-item convenience trade at a worse rate when that helps players buy from a larger stack without paying for the full bundle, plus a bulk action capped at `12` items instead of always forcing a full displayed stack purchase.
- When a player kills a hostile mob that is actively threatening a real settlement, that settlement's bakery should add `1` free baked-good claim to that player's running total. Each claim may be spent on exactly `1` baked item from a bakery display or `Baker's Counter`; raw ingredients in the display are never eligible. The bakery UI should surface that state clearly, for example by showing `Free` on the single-item action when the player has an unused claim.
- Baker profession trades should let players trade relevant inputs such as eggs, wheat, milk, sugar, fruit, or other bakery ingredients for baked goods, and should integrate with the broader profession-specific trade model rather than crowding every possible food trade onto the `Trade Board`.

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

### Miner Details

- `Miner` is a planned custom extraction profession for stone, ore, and underground building supplies.
- The Miner workstation is the `Miner Workstation`. Its first-pass block should read like the same family as the `Carpenter's Bench` and `Surveyor's Table`, but with a heavier stone build and pickaxe visual language on the sides and top instead of a wood-first look.
- Placing a `Miner Workstation` in or near a settlement should allow one eligible villager to become a `Miner`.
- The associated Miner structure should be a functional `Mine Entrance`, not a house or bed-bearing workshop home.
- The first-pass `Mine Entrance` should be a compact `6x6` stone-and-log portal that can be embedded into a hillside or cliff face as long as the double-door front remains accessible.
- The `Mine Entrance` should still respect ordinary structure-to-structure clearance from other built infrastructure, but unlike most surface buildings it only requires interior excavation plus a clear front entry approach instead of clearing a full surrounding above-ground moat into the hillside.
- The entrance shell should use stone-family walls and floor with log supports, with the first three above-ground wall levels matching the authored layered blueprint and a capped roof layer above them.
- The entrance interior should be lit. When available in this game version, the Mine Entrance should prefer a ceiling-hung `Copper Lantern` rather than spending iron on a standard lantern.
- The floor should open into a `2`-wide starter shaft directly inside the entrance. Each starter shaft level should be `2` cells deep: the back `2` cells are ladder cells and the front `2` cells are open drop cells, giving an effective `2x2` shaft cross-section without leaving all four top cells empty.
- If the build site already contains solid natural stone where the Mine Entrance expects stone shell blocks, that stone may remain in place instead of being replaced block-for-block. The result should read as carved into the terrain rather than pasted on top of it.
- The first-pass staged structure is the entrance and starter shaft only. Loaded Miner work can then extend that into deeper shafts and first-pass primary side tunnels, while broader secondary tunnels, quarry cuts, and richer underground extraction remain later follow-up.
- Miners should add every mined block or mined output to settlement stock rather than keeping those resources as private hidden loot. Ore-bearing blocks or ores that still need processing should become useful settlement materials through later refining steps when the settlement needs the derived goods.
- Settlement refining should consume real fuel instead of conjuring smelted outputs for free. When a settlement converts `raw iron`, `raw copper`, `sand`, or similar smeltables into finished goods, it should spend stored fuel and prefer the most efficient available option: existing `coal` or `charcoal` first, then spare logs converted into `charcoal`, and only then lower-efficiency wood fuel such as `planks`.
- Fuel-aware refining should preserve higher-priority structural reserves where practical instead of burning through core building stock just because a smelting path exists. At minimum, spare-above-target wood reserves should be consumed before protected building stock, and miner-oriented refining should keep a small working reserve of `coal` / `charcoal` and `copper ingots` for underground lighting.
- The first loaded Miner loop should begin once the `Mine Entrance` has an operational starter shaft, even if the staged build site still has a small unresolved state elsewhere. From there, Miners should extend the shaft one level at a time, place ladders as they go, and refuse to dig the next deeper level when they cannot also supply the needed ladders.
- In loaded villages, Miners should work from the active shaft depth rather than faking all mining from the surface entrance. They should descend through the shaft to the current reachable ladder rung before digging, reinforcing, or lighting the next shaft work.
- When the shaft breaks into a cave or other open underground space, Miners should not freeze at the breach. They should treat that cave opening as a bounded side-exploration zone, light it with torches as needed, and harvest easily exposed nearby ore before resuming deeper shaft work.
- When a useful exposed ore block sits adjacent to the current shaft frontier, Miners should mine that ore before digging deeper so obvious veins get extracted as they are discovered instead of ignored until much later.
- As the shaft deepens, Miners should also cut simple primary side tunnels from the ladder sidewalls about every `5` vertical blocks so they can stand off the ladder run and work nearby exposed ore instead of leaving those blocks untouched beside the shaft. When a tunnel mouth would otherwise lack footing, they may place a temporary or permanent `dirt` / `cobblestone` floor block from settlement stock to create a standable work spot.
- If a Miner cannot currently deepen the shaft because ladder or support materials are unavailable, they should still keep working by mining useful stone blocks exposed at the current shaft frontier instead of idling into a long survey-only state.
- If extracting an ore block would remove the block that supports a shaft ladder, Miners should immediately replace that support with `cobblestone`, or `dirt` if no cobblestone is available, before continuing.
- As the shaft deepens, it should be lit at regular intervals. A good first-pass cadence is about every `8` levels, with the light mounted on the wall opposite the ladder. When available, Miners should prefer wall-mounted `Copper Torches`; otherwise ordinary wall torches are fine. These shaft lights are part of a valid finished shaft level and should not be treated as debris to mine back out.
- At the end of the village workday, Miners who are still underground should return back up the shaft, step clear of the ladder exit, and rejoin the village's evening gathering before heading to sleep in ordinary housing.
- Planned tunnel rules:
  - `Shaft`: `2` blocks wide and `2` cells deep, with ladders in the back pair of shaft cells and open drop space in the front pair
  - `Primary Tunnel`: first pass is `2` blocks tall, `1` block wide, branches left and right from the ladder side about every `5` vertical blocks, and may begin from a placed `dirt` / `cobblestone` floor support when natural footing is missing; later lighting and longer tunnel extension can elaborate this
  - `Secondary Tunnel`: `2` blocks tall, `1` block wide, using wall torches for lighting

### Debug and Inspection Tools

- For staged construction validation, prefer a rebindable client key that toggles a red wireframe preview of planned blocks that still need to be built over a text-only debug command or overlay. The first-pass default key is `O`.
- The build-site wireframe should use the nearest or targeted active build site in the current dimension, render only unbuilt planned blocks, and hide blocks already correctly placed by villagers or players.
- The red preview should include pending, missing-material, and blocked build-site blocks so footprint, orientation, composition, and player-assist opportunities can be checked in-world before the structure is complete.
- The wireframe should come from server build-site state rather than client-side blueprint guessing, and it should stay lightweight enough to use while watching villagers work.
- For blueprint authoring, prefer a separate rebindable client key that captures the bounded structure the player is looking at and saves a text export in the mod's blueprint-oriented format. The first-pass default key is `P`.
- The first-pass capture should stay bounded and curator-friendly rather than trying to be a full in-game editor: it may export exact block-state legends alongside normalized layer rows and orientation rows for later cleanup.
- Later, add a complementary blueprint-import workflow: the player should be able to choose one of those exported blueprint files through a file-selection dialog, import it into the game as a generic but named construction-anchor item or block, place that anchor, and let villagers help build the imported structure through the normal staged-construction system.
- Imported blueprint anchors should preserve an identifiable display name derived from the source file or embedded structure name so shared player blueprints are distinguishable in inventories, build previews, and project lists.
- If this workflow matures cleanly, keep the architecture modular enough that the blueprint import / packaged anchor / villager-assisted build loop could later ship as a standalone sharing-focused mod instead of remaining Live Villages specific.
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
- Loaded village-defense attacks should be legible to players. At minimum, ranged and melee combat-capable villagers should produce lightweight visible or audible attack cues when they actually fire or strike, so nearby players can tell they are contributing without adding expensive extra combat logic.
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
- Finished walls, gatehouses, towers, and similar visible fortifications should also provide a modest prestige value in gameplay terms. First pass may express that through a small comfort bonus, stronger project summaries, or similar settlement-status improvement rather than needing a separate dedicated prestige stat immediately.

## Tier 1 Palisade

The first-pass `Tier 1` settlement wall should be a simple wooden palisade that reads as a real fortification rather than a decorative fence.

- Primary wall body: vertical logs, at least `4` blocks high
- Interior firing walk: wood slabs attached only on the inside of the wall at the level just below the top log, forming a narrow defender walkway rather than exterior trim
- Access points: about every `30` blocks, add a small stair access up to a short plank landing connected to the interior slab walk
- Lighting: place a torch about every `10` blocks along the top of the wall
- Material family: use the settlement's local wood family where practical rather than forcing oak everywhere
- Gatehouses: first pass may be very small. A simple door opening in a slightly widened wall section is acceptable until fuller timber gatehouse variants exist
- Gate placement: prefer locations where established settlement paths or route corridors would naturally pass through the wall, rather than arbitrary cardinal positions
- Terrain handling: the wall should follow terrain in a readable stepped way and may use light landscaping, but should avoid absurd jagged one-block noise when a short smoothing pass can fix it
- Vegetation handling: trees, leaves, brush, and similar removable natural blocks should be cleared or cut back as needed instead of preventing a valid palisade segment
- Walkability: the stair access, landing, and interior slab run should be usable by guards or workers in loaded chunks so defenders can fire projectiles over the wall. The first pass does not need a perfectly continuous battlement-grade walkway everywhere, but it should clearly function as an interior fighting position
- Defense effect: the wall should physically slow or block ordinary hostile entry where the geometry is complete, not only raise an abstract security number
- Simulation effect: a completed palisade should add a meaningful security bonus and a modest visible-settlement prestige / comfort benefit

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
- Evening gathering should resolve its anchor in this order: a real `Bell` / `MEETING` POI when present, otherwise the settlement's `Trade Board` or completed `Trading Post`, otherwise another non-home POI near the settlement center, and only then the raw settlement center as a last resort.
- Loaded villagers should keep stable professions when possible. Custom-role promotion should prefer unassigned adults and should not repurpose existing vanilla professionals just because a custom role is missing.
- When multiple free workstations exist but unemployed adults are scarce, workstation claiming should be treated as a settlement-wide staffing decision rather than a fixed block-order race. The village should prefer filling the most useful open roles first, and newly available adults should claim an appropriate reachable workstation within a few seconds rather than waiting for long passive drift.
- Loaded villagers should claim individual home beds when available and should keep returning to the same valid bed instead of fighting over beds every evening.
- Home assignment should stabilize quickly into a favorite bed per villager. When a favorite bed becomes invalid or temporarily unavailable, villagers may fall back to another open bed, but they should prefer reclaiming their usual valid bed on later nights.
- Current-task overlays should show profession-aware visible intent such as tending crops, managing trades, improving paths, surveying the village, village gathering, returning home, or raising a nearby child, rather than only reporting construction availability.
- Loaded worker presentation should favor visible representative actions such as explicit composter visits, visible pickup moments, or small carried-task loops before goods return to abstract settlement stock.
- Loaded worker behavior should follow an explicit OODA shape: observe local world state in bounded slices, orient around cached settlement/workstation context, decide in bounded intervals, then spend most of the day continuing concrete work rather than repeatedly dropping back into survey or planning poses. Observations that many roles share, such as nearby villagers, should be cached per settlement slice rather than rescanned by each worker, and repeated no-task planning results should be cached where absence of work is itself useful information.
- Villagers should take short staggered daytime breaks, but active workers should spend the large majority of working hours on visible productive tasks when valid work exists nearby. Breaks should be deterministic enough to avoid synchronized spikes but varied enough to make the village feel natural.
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
- Trade Board joining the settlement whose actual radius contains the board, or founding a custom settlement when the board is outside all settlement radii and not within the custom-settlement spacing limit of another non-outpost settlement. Founding previews still render outside settlements, and too-close founding attempts surface a visible placement message instead of silently linking across open terrain.
- Placed-workstation anchoring for `Carpenter's Bench` -> `Carpenter's Workshop`, `Surveyor's Table` -> `Roadwright's Workshop`, `Trade Board` -> `Trading Post`, and player-placed vanilla `Cartography Table` -> `Cartographer's House`, including nearby `can't build here` signs for blocked footprints when a sign spot is available. Valid player-placed workstations now create or resume persistent build-site records instead of instantly placing the associated structure, and build sites can dry-run material availability so planned blocks can be marked `pending` or `missing_material`. Loaded construction maintenance assigns nearby adult non-Nitwit villagers to claim one pending planned block at a time, walk toward it, clear natural obstructions, visit a known `Trade Board` or `Trading Post` anchor to retrieve or auto-craft needed supplies, and place the block; correctly player-placed planned blocks are recognized as `player_placed`. Completed staged `Trading Post` sites provide local and route trade output bonuses, while incomplete `Trading Post` sites do not; incomplete `Carpenter's Workshop` build sites are tracked as in-progress rather than counted as completed workshop infrastructure. Structure blueprints can now carry optional orientation rows, currently used by the `Cartographer's House` roof stairs so stair rotation does not depend only on edge heuristics; orientation-sensitive blocks such as stairs, slabs, doors, torches, fences, fence gates, beds, and logs require exact blockstate matches during construction reconciliation.
- Generated settlement names, persistent settlement state, persistent route state, round-robin region scheduling, village autodetection, and debug commands.
- Trade Board UI tabs for overview, trades, and routes, with settlement name, population, housing, comfort, security, shortages, surpluses, stock, projects, and route summaries. The trade tab focuses on village needs and village offers, stays on the trade tab after trade refreshes, and construction material blockers can add needs such as glass, sand, beds, or wool.
- Settlement ledger basics: population, wealth, stock, housing capacity, comfort, security, defense level, growth progress, projects, production, food/upkeep consumption, price pressure, growth, and inter-settlement stock transfers.
- Loaded resource work: villagers can carry nearby useful dropped goods and deposit them at the `Trade Board` / `Trading Post` before evening gathering; Foresters can collect forest drops, opportunistically pick up nearby player-left logs and saplings, cut mature non-bee trees, replant seedlings, and maintain sapling reserves; Butchers can shear sheep, feed breeding stock, breed cows, sheep, and pigs, cull excess livestock, build first-pass fenced pens with gates from settlement stock, and herd nearby stray animals toward those pens across the settlement's livestock territory; Carpenters can turn excess logs into planks, slabs, stairs, and sticks; Masons can prefer stone-heavy staged construction, top up low `Tier 1` cobblestone reserves at the `Stonecutter`, and cut finished `milepost` stock there for `Roadwright` placement.
- Partial world reconciliation: actual villager census, Trademaster promotion, infrastructure scans for beds, composters, storage blocks, and Carpenter's Benches on the `carpenter_bench` block id, loaded farm/cow/sheep-based food production, and simple world placement for housing shelters, Carpenter's Workshops, composters, and storage chests.
- Land route state and route surveying based on path, gravel, cobble, brick, smooth stone, and bridge-like materials. Loaded Roadwright work can connect known workstation structures to the internal path network by targeting the outside of structure doors first, then falling back to the workstation anchor. Roadwright route planning uses a bounded terrain-aware search for the next missing trail block instead of sampling only a straight line.

Remaining steps to close the spec/code gaps:

1. Finish the placed Trade Board visual work: resolve the known north-south dark-face issue, then add a client-side name renderer that displays the linked settlement name on both board faces.
2. Formalize settlement advancement: add persistent settlement-tier state, unlock tracking, recipe/knowledge state, starter knowledge, knowledge trading, tier-aware autonomous build rates, and tier-aware structure/path/fortification upgrades.
3. Expand world-authoritative reconciliation: scan more infrastructure types, apply unloaded catch-up changes to real loaded chunks in bounded batches, extend placed-workstation anchoring beyond the current first pass, replace remaining instant autonomous builds with staged villager construction, reconcile completed build-site effects more deeply into UI/project summaries, add visible Trade Board stock retrieval/deposit visits and persistent villager-carried goods where useful, and add stronger loaded-world job behavior for gardens, herds, pens, roads, and other profession infrastructure before resurveying derived state.
4. Flesh out the Trade Board UX: expose knowledge goals, fuller project details, clearer price/offer information, route quality/safety/capacity, Trading Post upgrade state, profession-specific trade entry points, and any future settlement renaming or naming rules.
5. Complete founding behavior: tune minimum spacing and link radius, verify founder timing, ensure the founding villager reliably becomes Trademaster, and make the board/founder status clear to the player.
6. Tune or data-drive the Carpenter's Bench recipe set, add any missing wood-heavy outputs, then continue adding or refactoring the remaining roles and workstations from [PROFESSIONS.md](PROFESSIONS.md): Baker, Beekeeper, Forester, Miner, Gardener, Guard, Roadwright, Portmaster, Scribe, Bakery, Beekeeper's Apiary, Warehouse, Guard Post, Honey Separator, Miner Workstation, Forester's Table, Gardener Workstation, Surveyor's Table, Milepost, Dock, and Scribe Desk, while expanding vanilla Cartographer, Farmer, Butcher, Mason, and Carpenter behavior.
7. Replace hard-coded goods, target rules, and profession-priority heuristics with data-driven rules using tags, datapacks, and config where practical.
8. Build out physical warehouse behavior: capacity, interaction, storage visualization, route efficiency bonuses, and reconciliation between physical storage blocks and the ledger.
9. Expand construction queues: add real defensive works, road/path placement and upgrades, dock/harbor projects, warehouse projects, Trading Post and Shopping Mall projects, workstation-linked building jobs, maintenance, settlement-wall projects, and tier-aware build-rate caps and retrofit upgrades.
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
