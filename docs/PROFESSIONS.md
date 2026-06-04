# Live Villages Professions and Workstations

This document expands the worker-role section in [SPECS.md](SPECS.md). It exists to keep one clear reference for:

- vanilla villager professions and their vanilla workstations
- Live Villages custom professions and workstations
- loaded-world job behavior
- settlement-ledger and inventory behavior

Ordered implementation work is tracked in [IMPLEMENTATION-PLAN.md](IMPLEMENTATION-PLAN.md).

## Design Rules

- Every profession should matter in two layers: visible work in loaded chunks and abstract contribution to settlement simulation.
- Workstations are both job-site anchors and infrastructure signals. A settlement should care whether the right stations exist, and when a player places a recognized workstation the settlement should try to grow the matching structure around or beside that exact block.
- If a player placed recognized workstation cannot support its associated structure because the site is blocked or invalid, villagers should leave a nearby temporary sign saying `can't build here` instead of silently failing. First-pass loaded maintenance now retries placed custom workstation structures such as Carpenter's Workshops, Scribe Offices, Guard Posts, Gardener's Sheds, Beekeeper's Apiaries, Bakeries, Mine Entrances, Roadwright Workshops, and Forester Workshops when no matching build site exists, so clearing a blocked footprint can let the settlement start the staged build later.
- When a workstation-associated structure includes beds, matching professionals should have first claim on those beds and should be able to reclaim them from non-matching residents when needed, while still letting players displace or reassign beds through normal play.
- Bulk goods stay in settlement stock. Villagers should carry only small task loads, tools, finished deliveries, or equipment.
- Vanilla professions should remain useful even before every one receives bespoke AI.
- New professions should be introduced only when vanilla professions cannot cleanly cover the required behavior.
- Profession behavior should prefer shared task logic plus data-driven priorities and material preferences where practical; do not assume a full embedded scripting language is needed unless tuning needs outgrow declarative data.
- Loaded-world profession work must be bounded and measurable. Cache broad scans, keep villagers committed to a selected task for a short interval, and add timing logs around new tick-driven systems so server lag can be diagnosed before it becomes visible in play.
- Field professions can work beyond the base settlement radius when their job requires it, but assigned villagers should still keep settlement membership for census, gathering, and bed assignment. Current bounds are `150%` radius for `Miner`, `Fisherman`, and `Forester`, and `200%` radius for `Roadwright`.
- Terrain-changing workers should share greenspace constraints. Roads, forestry, farming expansion, and future gardening should leave usable grass, flowers, saplings, and trees inside the village instead of optimizing every nearby block for production or travel.
- Loaded settlement navigation should prefer established roads, paths, bridges, and gate approaches when the detour is reasonable, especially for routine work, gathering, patrols, and route traffic. First-pass routine worker and Guard escort/patrol movement now biases toward nearby established road/path/bridge waypoints that still make progress toward the target.
- Villagers and settlement-owned illagers may open doors or gates in palisades and other defensive walls only for their own settlement, and should close those doors after passing through. First-pass loaded maintenance opens and recloses completed palisade gatehouse access blocks for nearby own-settlement members.

## Vanilla Baseline

The vanilla profession list is still the starting point. Live Villages should not throw it away; it should reinterpret and expand it.

### Vanilla Profession and Workstation Mapping

| Profession | Vanilla workstation | Live Villages purpose | Current code note |
| --- | --- | --- | --- |
| Armorer | Blast Furnace | Metal gear, heavy craft support, and defensive equipment supply | First-pass iron gear production, distribution, and workstation recruitment |
| Butcher | Smoker | Herd growth, culling, meat and leather supply, and livestock trade surplus | Counted as a distinct food-production role in actual-villager census |
| Cartographer | Cartography Table | Route intelligence, long-range surveying, and far-distance trade support | First-pass route-intelligence task and house recruitment |
| Cleric | Brewing Stand | Alchemy, healing support, and rare-goods handling | First-pass daily production and shrine structure anchoring |
| Farmer | Composter | Garden management, crop rotation, seed handling, and soil fertility | First-pass loaded garden work and composter recruitment |
| Fisherman | Barrel | Fishing, shoreline food supply, and harbor-adjacent production | Counted as a distinct food-production role in actual-villager census |
| Fletcher | Fletching Table | Arrow supply, special ammunition, ranged-support goods, and wood byproducts | Loaded work now crafts arrows for town stock, provides ranged defense around the settlement, and anchors a two-bed hut build |
| Leatherworker | Cauldron | Hides, utility gear, and transport-adjacent goods | First-pass daily production and workshop structure anchoring |
| Librarian | Lectern | Books, records, and early knowledge work | First-pass daily production and library structure anchoring |
| Mason | Stonecutter | Stone shaping, masonry, shared construction labor, and structural repair | First-pass workshop, loaded masonry loop, and recruitment |
| Shepherd | Loom | Wool, cloth, beds, decoration, and pasture output | First-pass daily production and hut structure anchoring |
| Toolsmith | Smithing Table | Tool production and workshop support | First-pass tool production, distribution, and workstation recruitment |
| Weaponsmith | Grindstone | Arms, repairs, and guard equipment support | First-pass weapon production, distribution, and workstation recruitment |

### Non-Profession Villager States

| State | Workstation | Live Villages use |
| --- | --- | --- |
| Unemployed | None | Fallback labor pool, promotion candidates, and shortage relief |
| Nitwit | None | Mostly outside skilled labor; may remain low-agency population flavor unless future hauling behavior justifies more |
| Baby | None | Future workforce only; should affect growth, not current production |

### Current Functional Grouping

Until every vanilla profession has distinct job AI, the simulation can group them into broader settlement functions:

- Food and husbandry workers: `Farmer`, `Butcher`, `Fisherman`, `Shepherd`
- Core construction workers: `Mason`, plus the custom `Carpenter`
- Supporting craft workers: `Armorer`, `Toolsmith`, `Weaponsmith`
- Trade and knowledge workers: `Cartographer`, `Cleric`, `Librarian`, plus the custom `Trademaster`
- Recent expansion targets: `Farmer`, `Cleric`, `Librarian`, `Leatherworker`, `Shepherd`, `Armorer`, `Toolsmith`, and `Weaponsmith` now have first-pass daily production, garden work, or equipment loops and loaded-workforce recruitment from matching reachable vanilla job sites, with richer role-specific work still planned

### Outpost Role Bias

- Pillager outposts should favor offensive or coercive roles such as `Guard`, `Armorer`, `Weaponsmith`, `Fletcher`, and other raid-support labor.
- Outposts generally should not spend *scarce* labor on civilian comfort or village-beautification roles such as `Farmer` or `Gardener` unless a future special-case variant explicitly needs them.
- Outposts may still build required structures without the exact matching profession, but that fallback construction should be slower and less efficient than specialist village labor.

### Shared Equipment and Defense Rules

- Combat-capable field workers should carry profession tools or weapons when defined for their role.
- Field workers without a more specific weapon rule should fall back to at least a `Wooden Sword`.
- Deskworker professions `Scribe`, `Trademaster`, `Cartographer`, `Cleric`, and `Librarian` are intentionally unarmed and should flee from hostile mobs rather than stand and fight.
- `Sling`: a weaker bow-style ranged weapon with built-in unlimited ammunition. First-pass code registers a lightweight craftable item, uses it for visible Gardener and Shepherd presentation, and gives loaded Gardeners and Shepherds weak line-of-sight bell-response sling shots.
- `Crooked Staff`: an improvised melee weapon better than a stick but weaker than any proper vanilla weapon. First-pass code registers a craftable item, uses it for visible Shepherd excess-flock culling presentation, and switches Shepherds to it for close bell-response defense.
- `Scythe`: a tool-weapon better than a `Crooked Staff` but still weaker than any proper vanilla weapon. First-pass code registers a craftable item and uses it for visible Farmer harvest and grass-trimming presentation.
- `Guards` and `Fletchers` should receive the first priority for combat upgrades, then `Roadwrights`, then other armed workers. `Fletchers` only wear leather armor at best.
- `Leatherworkers`, `Weaponsmiths`, and `Armorers` should equip themselves appropriately before distributing spare upgrades to others.

### Priority Vanilla Role Expansions

These roles already exist in vanilla and should be expanded before adding unnecessary duplicate professions.

### Cartographer

- Workstation: `Cartography Table`
- Workstation structure: `Cartographer's House`, anchored from a player-placed vanilla `Cartography Table`
- Structure baseline: use vanilla cartographer houses where practical; the first staged build blueprint is a simplified plains cartographer house using the existing visible construction pipeline and explicit roof-stair orientation metadata
- Loaded-world behavior: surveys routes, records settlement knowledge, and refreshes route intelligence for distant trade; first-pass code recruits Cartographers from reachable `Cartography Table` job sites with house or sheltered-bed capacity and records a daily loaded route-intelligence refresh at the table
- Settlement behavior: nearby trade can be coordinated by a `Trademaster`, but first-pass land routes beyond about `32` chunks / `512` blocks require a `Cartographer` at one endpoint before the route can be established; `Scribe` support can stack extra range and quality after that cartographic gate is met
- Notes: `Cartographer` improves route knowledge and long-range trade quality, does not replace the worker physically building roads, and should behave as an unarmed deskworker in danger

### Farmer

- Workstation: `Composter`
- Equipment: carries hoes and scythes
- Tier 1 staffing note: reachable composters provide first-pass Farmer recruitment and job-site maintenance; no special Farmer housing structure is required in this pass
- Loaded-world behavior: manages a composter-anchored farm territory, prioritizes ripe crops first, harvests mature crops into settlement stock, replants empty farmland from available seed stock based on current shortages, collects loose crop and seed drops, collects composter or loose `bone_meal` and applies it to the least-ready high-need crops, gathers both floating and flat ground `leaf_litter`, treats wheat harvest as a small virtual litter source for composting, and trims short or tall grass for cleanup and compost input
- Settlement behavior: turns accessible farm territory, seed availability, compost output, and labor into stable food output; requests or trades for missing seed stock such as potatoes or beetroot seeds, then folds those crops into rotation when under-supplied
- Farm-territory definition: the default territory can start as the raised-bed footprint around the composter, but the system should treat nearby farmland, dropped farm goods, loose leaf litter, and natural grass around that composter as part of the same managed work area so the player can extend it naturally

### Butcher

- Workstation: `Smoker`
- Associated structure: `Butcher Shop`, using the current small workstation-house footprint family for the first pass and staging around the placed `Smoker`
- Loaded-world behavior: manages fenced pig and cow herd space across the whole settlement, feeds breeding stock, breeds cows and pigs when below target, culls adults when above target, can build first-pass fenced pens with gates from settlement materials, and can herd nearby stray cattle or pigs toward those pens
- Settlement behavior: aims for cattle and pig herd sizes that scale with settlement population, then grows them up to a capped trade buffer when active routes exist; first pass should allow as much as about `50%` surplus herd capacity above local needs when the village can actually export the excess. Produces beef, pork, leather, and husbandry value from culling and herd management, and should be able to export meat surpluses to other villages over active routes
- Player-use behavior: right-clicking a settlement-linked `Smoker` with wheat trades one wheat into settlement stock for one stocked Butcher output, currently preferring beef, then pork, then leather when available
- Territory rule: the `Smoker` is the profession anchor, not the boundary of the work area; butchers should be able to feed animals, breed herds, and cull cattle or pigs anywhere inside the settlement's managed livestock space
- Pen definition: player-built fenced pasture expansions anywhere in the settlement should be claimable by the livestock-management system when they connect cleanly to an existing managed herd area or otherwise qualify as village pasture
- Migration note: sheep shearing, sheep breeding, wool output, mutton from managed sheep culling, and sheep flock protection now belong to `Shepherd`; `Butcher` keeps meat-animal handling for cattle and pigs

### Mason

- Workstation: `Stonecutter`
- Status: first-pass `Mason's Workshop` structure and loaded masonry-support loop are in place
- Associated structure: `Mason's Workshop`, using a compact live-build footprint with a visible stonecutter work area, bed, and stone-material storage; a closer vanilla-house adaptation can still replace or refine this later
- Tier 1 staffing note: completed workshop beds or sheltered vanilla stonecutters with nearby beds provide first-pass Mason recruitment and job-site maintenance
- Loaded-world behavior: builds foundations, walls, chimneys, bridges, defensive works, masonry-heavy repairs, and stone `Mileposts`; first-pass task selection should prefer stone-heavy staged construction blocks over wood-heavy ones, idle `Mason`s should gravitate toward the `Stonecutter` to top up low `Tier 1` cobblestone reserves, and surplus stone can be cut there into finished `milepost` stock for `Roadwright` placement
- Settlement behavior: acts as shared construction labor alongside the custom `Carpenter`, with a focus on stone durability, defensive value, route-marker construction, and structural upgrades
- Player-use behavior: right-clicking a settlement-linked `Stonecutter` with `8` cobblestone, granite, diorite, or andesite trades those rough stone-family blocks into settlement stone stock for one stocked `Milepost`; sneak-use passes through to normal `Stonecutter` behavior
- Notes: `Mason` should be a first-class settlement construction worker, not just a background vanilla crafter; completed staged structures should reopen as repairs when damaged, and future autonomous masonry upgrades must stay within the settlement's currently allowed tier materials until the fuller later-tier upgrade ladder is authored

### Cleric

- Workstation: `Brewing Stand`
- Associated structure: `Cleric Shrine`, using the first-pass bedded hut structure family anchored by the placed `Brewing Stand`
- Loaded-world behavior: brews and maintains healing potions when materials allow, produces at least one healing potion per day when ingredients are available, runs from hostiles by default, and uses stored healing-potion stock to briefly rush in and heal injured priority defenders before retreating again
- Player-use behavior: right-clicking a settlement-linked `Brewing Stand` with a glass bottle trades one glass bottle, one nether wart, and one glistering melon slice into settlement stock for one stocked healing potion
- Settlement behavior: converts brewing materials into healing support, rare alchemy, and defender recovery
- Notes: `Cleric` is a deskworker profession and should stay unarmed even when healing in danger

### Fisherman

- Workstation: `Barrel`
- Equipment: carries axes for shore defense and fishing gear such as a rod, spear, or trident for fishing work
- Loaded-world behavior: works shorelines, rivers, docks, and harbor edges up to `150%` of the owning settlement radius; periodically adds real `cod` to settlement stock while the loaded villager is actually fishing; mounts a docked boat that it pilots for a visible fishing trip when one is available; returns from that boat in time for the village gathering; helps defend against hostile mobs approaching by water with an axe; and carries a fishing rod or spear/trident for fishing work
- Settlement behavior: adds fish and shoreline food output, especially in water-linked settlements; `Docks` and `Lighthouses` should both improve catch volume
- Structure note: keep `Fisherman` close to vanilla for now. The `Barrel` remains the workstation, and no special Fisherman housing or bespoke staged workstation structure is required in this pass

### Librarian

- Workstation: `Lectern`
- Associated structure: `Library`, using the first-pass bedded hut structure family anchored by the placed `Lectern`
- Loaded-world behavior: studies and copies records, produces at least one `book` or `bookshelf` per day when ingredients are available, and flees from hostile mobs rather than fighting
- Player-use behavior: right-clicking a settlement-linked `Lectern` with paper trades three paper and one leather into settlement stock for one stocked book; right-clicking with a book trades three books and six planks into settlement stock for one stocked bookshelf
- Settlement behavior: converts paper, leather, planks, and written knowledge into books, bookshelves, recordkeeping value, and early knowledge support
- Notes: `Librarian` remains the baseline vanilla knowledge deskworker for books, bookshelves, and records. `Scribe` should be treated as an advanced Librarian path that enhances settlement knowledge with recipe exchange, civic memory, route intelligence, and settlement-to-settlement knowledge trade instead of replacing baseline book production.

### Fletcher

- Workstation: `Fletching Table`
- Loaded-world behavior: crafts arrow supply from stocked `stick`, `feather`, and arrowhead materials, preferring `Copperhead Arrow` stock when copper nuggets or divisible copper ingots are available and falling back to vanilla arrows from flint, supports ranged defense with skeleton-strength bow fire against visible nearby threats, and carries a bow with profession-supplied unlimited arrows
- Settlement behavior: turns wood, feathers, and arrowhead materials into ranged gear and ammunition support; ordinary villages should prefer efficient `Copperhead Arrow` output, with `Ironhead Arrow` production gated behind real iron surplus or heightened defense demand
- Housing/workstation note: a placed `Fletching Table` should anchor a `Fletcher's Hut` with `2` beds and can support up to `2` Fletchers in the same settlement
- Player-use note: the `Fletching Table` should become a real arrow workstation for the player, with higher-yield batch recipes and a separate material-arrow family such as `Copperhead Arrow`, `Ironhead Arrow`, and `Diamondhead Arrow`; final item naming should avoid collision with vanilla potion `Tipped Arrow` terminology

### Leatherworker

- Workstation: `Cauldron`
- Associated structure: `Leatherworker's Workshop`, using the first-pass bedded hut structure family anchored by the placed `Cauldron`
- Loaded-world behavior: processes hides, wears a full set of leather armor when stock allows, produces at least one piece of leather armor per day when ingredients are available, and equips villagers who lack basic armor when spare leather armor stock allows
- Player-use behavior: right-clicking a settlement-linked empty or water `Cauldron` with enough leather trades that leather into settlement stock for stocked leather armor, currently preferring chestplate, then leggings, helmet, and boots
- Settlement behavior: turns hides into early armor, utility goods, and transport-adjacent leather equipment

### Shepherd

- Workstation: `Loom`
- Associated structure: `Shepherd's Hut`, using the first-pass bedded hut structure family anchored by the placed `Loom`
- Loaded-world behavior: first-pass code produces wool and beds through the `Loom`, and actual loaded Shepherds now own sheep-specific flock work: they can build or continue fenced sheep pens, herd stray sheep toward those pens, breed sheep with wheat, shear ready sheep, and cull excess adult sheep into mutton and wool when the flock is above target. Fuller combat presentation should defend the flock from wolves or hostile mobs with a sling at range and a crooked staff up close.
- Player-use behavior: right-clicking a settlement-linked `Loom` with wheat trades one wheat into settlement stock for one stocked wool; right-clicking with wool trades three wool and three planks into settlement stock for one stocked bed
- Settlement behavior: adds wool, mutton from managed sheep, flock stability, and future textile support while protecting pasture productivity
- Migration note: sheep-related functionality has moved from `Butcher` to `Shepherd`, leaving `Butcher` focused on cattle, pigs, leather, and food trade

### Armorer

- Workstation: `Blast Furnace`
- Associated structure: `Smithy`, using the first-pass bedded hut structure family anchored by a placed smithing station
- Tier 1 housing/workstation note: first-pass Smithy support can anchor on a placed `Blast Furnace`, `Smithing Table`, or `Grindstone`; sibling smithing stations inside the completed shared Smithy use the same linked beds for capacity and bed preference, with one worker per station and total smith staffing bounded by the Smithy beds
- Loaded-world behavior: wears available iron armor and shields in first pass, rotates daily production across iron chestplates, leggings, helmets, boots, and shields when ingredients are available, and distributes spare iron armor or shields to priority combat-capable workers with `Guards` first and `Roadwrights` next; later passes should expand this into finer equipment tiers
- Player-use behavior: right-clicking a settlement-linked `Blast Furnace` with iron ingots trades the needed iron and, for shields, planks into settlement stock for stocked iron armor or shields, currently preferring chestplate, leggings, helmet, boots, then shield when available and payable
- Settlement behavior: converts metal stock into defensive gear and raises settlement survivability over time
- Notes: upgrade priority should be `Guards` first, then `Roadwrights`, then other villagers

### Toolsmith

- Workstation: `Smithing Table`
- Associated structure: `Smithy`, using the first-pass bedded hut structure family anchored by a placed smithing station
- Tier 1 housing/workstation note: first-pass Smithy support can anchor on a placed `Blast Furnace`, `Smithing Table`, or `Grindstone`; sibling smithing stations inside the completed shared Smithy use the same linked beds for capacity and bed preference, with one worker per station and total smith staffing bounded by the Smithy beds
- Loaded-world behavior: carries the best pickaxe available in first pass, produces iron pickaxes when ingredients are available, and distributes spare iron pickaxes to tool-using workers such as `Miners`, `Roadwrights`, `Foresters`, `Carpenters`, and `Masons`; later passes should expand this into richer tool tiers and repairs
- Player-use behavior: right-clicking a settlement-linked `Smithing Table` with iron ingots trades three iron ingots and two sticks into settlement stock for one stocked iron pickaxe
- Settlement behavior: converts metal stock into tool upgrades that improve extraction, roadwork, forestry, and construction support
- Notes: upgrade priority should be `Miners` and `Roadwrights` first, then other tool-using workers

### Weaponsmith

- Workstation: `Grindstone`
- Associated structure: `Smithy`, using the first-pass bedded hut structure family anchored by a placed smithing station
- Tier 1 housing/workstation note: first-pass Smithy support can anchor on a placed `Blast Furnace`, `Smithing Table`, or `Grindstone`; sibling smithing stations inside the completed shared Smithy use the same linked beds for capacity and bed preference, with one worker per station and total smith staffing bounded by the Smithy beds
- Loaded-world behavior: carries the best sword available in first pass, produces iron swords when ingredients are available, and distributes spare iron swords to sword-using combat workers with `Guards` first and `Roadwrights` next; later passes should upgrade through richer weapon tiers
- Player-use behavior: right-clicking a settlement-linked `Grindstone` with iron ingots trades two iron ingots and one stick into settlement stock for one stocked iron sword
- Settlement behavior: converts metal stock into weapon upgrades for defenders and patrol workers
- Notes: upgrade priority should be `Guards` first, then `Roadwrights`, then other armed villagers

## Live Villages Custom Professions

These are the professions the mod adds or explicitly plans beyond vanilla's baseline.

### Trademaster

- Workstation: `Trade Board`
- Associated structure: `Trading Post` / `Trade Post`, with a later `Shopping Mall` upgrade path on the same site
- Status: Implemented in code
- Loaded-world behavior: visits the board, checks public priorities, surveys stock-related infrastructure, deposits and retrieves settlement stock when nearby villagers visibly trade with the board, and acts as the visible settlement coordinator
- Settlement behavior: manages shortages, surpluses, pricing pressure, trade approvals, founding logic, public project priorities, and the market-facing role of a connected `Trading Post`
- Notes: one active Trademaster per settlement; this is a civic planner role, not a bulk-hauling role, and it should remain unarmed like other deskworkers

### Beekeeper

- Workstation: `Honey Separator`
- Associated structure: `Beekeeper's Apiary`
- Workstation capacity: supports `1` Beekeeper by default; a second Beekeeper should only be useful for an unusually large village with many managed hives
- Outfit: wears a mostly white beekeeper protective suit, including a pale hood or veil silhouette, white tunic and leggings, and light gloves or boots; the texture should read as protective workwear rather than ordinary robes
- Equipment: carries shears when available; should prefer carrying glass bottles for honey collection and flowers when breeding or relocating bees
- Status: First-pass implementation in code
- Loaded-world behavior: visits the `Honey Separator`, visibly carries shears, shares a pass-local per-separator task plan so multiple Beekeepers do not repeat the same broad apiary scans in one maintenance pass, consumes stocked `bee_hive` and `campfire` goods to place up to `3` managed hive-over-campfire setups near each separator when space is available, scores valid hive sites toward flower-rich and open-air apiary space, consumes stocked flowers to plant a bounded set of apiary flowers near each separator with bias toward nearby hives or nests, feeds nearby breedable adult bees only when nearby visible bees plus bees inside nearby hives or nests are below local hive capacity, inspects nearby smoked full bee hives or bee nests and harvests them into honey bottles or honeycomb, keeps managed hive campfires lit or replaces missing campfires from settlement stock, converts stocked glass bottles into honey bottles when no full hive is ready, uses shears-gated work to add honeycomb, crafts candles from honeycomb, and crafts additional bee hives from honeycomb and planks; richer long-term apiary ecology remains follow-up work
- Settlement behavior: produces honey bottles, honeycomb, candles, and modest food or comfort value; requests glass bottles, shears, campfires, flowers, wood, and hive-building supplies when those are missing
- Player-use behavior: right-clicking a settlement-linked `Honey Separator` with an empty glass bottle trades that bottle to the Beekeeper stock for one stored honey bottle when the settlement has honey bottles available, giving the Beekeeper a first-pass profession-specific outlet outside the central `Trade Board`
- Structure behavior: the first-pass `Beekeeper's Apiary` is a staged, bedded workstation structure anchored by the `Honey Separator` with a distinct fenced apiary-yard hint instead of a generic hut layout; loaded Beekeepers can now grow and maintain nearby managed hive/campfire/flower infrastructure from stock, while stronger village-edge siting and richer apiary layout remain follow-up work
- Safety and ecology rules: Beekeepers should not break natural bee nests unless a future relocation task explicitly supports preserving the bees; Foresters should continue avoiding trees that contain bee nests or bee hives, including when a Beekeeper is present
- Notes: most villages should need at most one Beekeeper, and settlements without reachable bee nests, bee hives, or a placed `Honey Separator` should not prioritize creating the profession

### Carpenter

- Workstation: `Carpenter's Bench`
- Status: Functional custom profession and workstation pass complete
- Loaded-world behavior: handles timber framing, fences, doors, roofs, storage carpentry, wood-heavy repairs, interior upgrades, turns excess village logs into planks, slabs, stairs, and sticks at the bench, and should prefer the bed in the workshop house as profession lodging; if another villager takes that bed, the carpenter should be able to reclaim it, while the player remains able to displace the carpenter
- Settlement behavior: acts as shared construction labor alongside `Mason`, visibly placing or removing individual blocks for construction, repair, and upgrade work with a wood-material focus
- Workstation behavior: the `Carpenter's Bench` should function like a wood-specialized optimized crafting station, analogous to the `Stonecutter`, for recipes where wood is the dominant material
- Notes: the bench has a first-pass functional wood-conversion UI, loaded settlements try to maintain an actual `Carpenter` for reachable benches, the `Carpenter` gets priority over nearby non-Carpenter home claims for the workshop bed, and loaded Carpenters preserve the village's log reserve before processing surplus logs

### Construction Category

- Construction planning should use explicit `Carpenter` and `Mason` roles rather than a single generic profession
- Shared construction support from armorers, toolsmiths, and weaponsmiths should contribute through `construction_support`
- Workstation-associated construction should be a staged job. Villagers and helping players complete one planned block at a time, and the associated structure should not affect trade or profession bonuses until complete.
- Loaded settlement maintenance should retry recognized workstation-associated structures that do not yet have a build site, including custom workstation structures, so an initially blocked placement can recover after the player clears space.
- A workstation structure may move the workstation from the player's original placement block to the blueprint's intended final block, including elevation changes, without canceling the build site. The original anchor block should remain linked to that build until villagers remove or relocate it as part of construction.
- Worker-profession workstations generally support one or two villagers rather than exactly one; their associated structures should prefer assigning internal beds to matching professionals and can use a second bed when the village needs another worker. Guard Posts are the larger exception and should support up to five Guards.
- Construction material should come from settlement stock or villager inventories, with basic conversion from adaptable raw materials such as logs into planks, stairs, slabs, fences, gates, or doors.
- Adult non-Nitwit villagers may help with general construction once higher-priority profession work is satisfied. Unemployed villagers should be available immediately, while specialists can later get faster placement or carry bonuses for matching materials.
- Professional high-priority work outranks generic construction, except that a villager should finish the current single-block action before switching. Guards and Fletchers may interrupt immediately for imminent hostile threats.
- Villagers in loaded chunks should visibly visit an adjacent access tile at the `Trade Board` or completed `Trading Post` to deposit collected items or retrieve needed construction materials, especially when their personal inventory is close to full. End-of-day deposits should happen before the evening gathering and can appear as `Depositing into Trading Post`. Villagers should not target the board block itself as a walking destination.
- Loaded-settlement daily reports are optional and default off. They can be enabled globally with `daily_settlement_reports` in `config/live-villages.json`, or per world with the `live-villages:daily_settlement_reports` game rule. When enabled, they should be written near the end of each in-game day to the `livevillages_exports` folder, named `<village-name>-report-<day number>.txt`; if sleep skips the write window, the previous loaded day should still be written after the day rolls over. Reports should include a section for every vanilla and Live Villages profession, including professions with no assigned villagers or no matching workstation in the settlement. Assigned villagers should list actual completed work observed that day, include any custom nametag name, and summarize exact blocks mined or harvested, produced goods, mined drops, recovered goods, and work-consumed goods. Reports should also include inter-settlement trade batches that affected the loaded village, such as what it sent and what it received. Reports should not include observed intent states or simulated stock credits as villager work. Death/status notes should also appear.
- Villages should not queue duplicate staged structures, upgrades, or repairs while an equivalent build site is already in progress, and damaged completed structures should reopen as repair work rather than creating replacement duplicates.
- Later housing expansion can add staged second-floor apartment upgrades to compatible completed workstation structures, with accessible stairs, a small door platform, and housing credit only after completion.

### Profession-Specific Trade

- The `Trade Board` should focus on the highest-priority settlement-wide wants, not every possible item the village can use.
- Profession structures should expose narrower trades for lower-priority but still useful goods.
- `Baker` structures can trade baked goods for inputs such as eggs, wheat, milk, sugar, or fruit.
- `Beekeeper` structures can trade honey bottles, honeycomb, candles, or related hive goods for glass bottles, shears, flowers, campfires, or wood inputs. First-pass `Honey Separator` interaction supports glass-bottle-for-honey-bottle trades against settlement stock.
- `Carpenter` structures can trade wood outputs such as stairs, slabs, fences, gates, and doors for logs or planks, with deals at least as good as direct crafting and faster for the player.
- `Cleric` structures can trade complete healing-potion ingredient bundles for stocked healing potions. First-pass `Brewing Stand` interaction supports glass-bottle, nether-wart, and glistering-melon-slice bundles for stocked healing potions.
- `Librarian` structures can trade book materials for stored books and bookshelf materials for stored bookshelves. First-pass `Lectern` interaction supports paper/leather-for-book and book/planks-for-bookshelf trades.
- `Butcher` structures can trade animal outputs such as beef, pork, or leather for feed such as wheat. First-pass `Smoker` interaction supports wheat-for-stocked-beef/pork/leather trades against settlement stock.
- `Leatherworker` structures can trade leather into settlement stock for stored leather armor. First-pass `Cauldron` interaction supports leather-for-stocked-armor trades using the vanilla armor leather costs.
- `Shepherd` structures can trade feed or textile materials for stored wool and beds. First-pass `Loom` interaction supports wheat-for-wool and wool/planks-for-bed trades.
- `Fletcher` structures can trade arrow ingredient bundles for stocked arrows. First-pass `Fletching Table` interaction supports stick/feather plus flint, copper nugget, iron nugget, or diamond bundles for stocked vanilla, Copperhead, Ironhead, or Diamondhead arrows, while empty-hand and sneak-use still open the player crafting UI.
- `Armorer`, `Toolsmith`, and `Weaponsmith` structures can trade recipe-shaped iron and stick or plank bundles for stored gear. First-pass `Blast Furnace`, `Smithing Table`, and `Grindstone` interactions support stocked iron armor or shields, iron pickaxes, and iron swords.
- `Mason` structures can trade processed stone outputs such as polished blocks, stairs, or slabs for rough stone-family inputs such as granite or diorite. First-pass `Stonecutter` interaction supports `8` rough stone-family blocks for one stocked `Milepost`, with the input credited to the settlement's rough stone stock.

## Additional Custom Profession Details

These roles have first-pass implementation where noted, but still carry richer later-tier design details and polish notes. Workstation names such as `Miner Workstation` are current working names carried forward from [SPECS.md](SPECS.md). Final block names can be revised later without changing the role definitions here.

### Baker

- Workstation: `Baker's Counter`
- Associated structure: `Bakery`
- Status: first-pass profession, workstation, bakery structure, display stock, baking loop, bakery-side trade pass, and prompt bakery ingredient display reconciliation are implemented
- Loaded-world behavior: works in the bakery, collects or requests food inputs, bakes bread, cakes, pies, cookies, and similar baked goods when supplies are available, and visibly stocks bakery counters/display cases with baked goods as sale inventory
- Settlement behavior: converts farm and livestock supplies into higher-value or more varied food stock, improving food security and trade options without overloading the central `Trade Board`
- Physical form: the `Bakery` should use the same `5x8` overall footprint and broad layout as the `Trading Post`, but replace fence openings with glass panes behind vertical shelves of baked goods if that presentation works; gates should remain
- Change introduced by Live Villages: creates a specialist food-processing and food-trade role instead of treating all prepared food as generic farmer output

### Forester

- Workstation: `Forester's Table`, a wood-and-log work table with axe visual language
- Associated structure: `Forester's Workshop`, using the Carpenter/Roadwright workshop layout family but built almost entirely from wood with heavy log use
- Equipment: carries axes and hoes
- Status: First loaded-world forestry pass implemented
- Loaded-world behavior: manages groves beyond the village core up to `150%` of the owning settlement radius, chops mature natural trees only when enough nearby trees remain, avoids trees with bee nests or bee hives, replants from carried or village seedling stock, keeps village interiors relatively sparse while allowing thicker exterior woods, collects surface forest drops such as saplings, apples, sticks, and leaf litter, opportunistically picks up nearby surface dropped logs and saplings left by the player, never chooses underground or mine-shaft work, moves out of loaded mine shaft columns when rescue logic can return it to the surface, and uses hoes where ground preparation helps managed groves
- Settlement behavior: adds logs, sticks, apples, saplings, and similar wood outputs to stock; keeps a small reserve of diverse seedlings; requests tools and saplings when needed; and can surface trade demand for missing saplings through the Trademaster / Trade Board economy
- Change introduced by Live Villages: gives timber its own dedicated production role instead of treating wood as passive world scenery

### Miner

- Workstation: `Miner Workstation`, a stone-forward bench or block with pickaxe visual language on the sides and top
- Associated structure: `Mine Entrance`, a functional extraction-site entrance rather than housing; first pass is a compact stone-and-log portal with a `2`-wide, `2`-deep starter shaft where the back pair of shaft cells carry ladders
- Equipment: carries pickaxes
- Loaded-world behavior: first-pass staged construction covers the Mine Entrance and starter shaft, and the first loaded Miner loop can work mine sites up to `150%` of the owning settlement radius, prioritizes readily reachable exposed ore and vein follow-up over strict shaft deepening when that yields more useful goods, extends the completed shaft one level at a time while consuming ladders when no safer higher-value work is available, descends through the shaft to the current reachable ladder rung in loaded villages before working, lights the shaft about every `8` levels on the wall opposite the ladder with `Copper Torches` when available or ordinary torches otherwise, treats those shaft lights as part of a valid finished shaft level, explores bounded cave breaches off the active shaft only when the cave is not quarantined for hostiles, the cave stand position can return to a known mine ladder, and the stand is not below the completed ladder-bottom safety band, lights those reachable cave pockets with torches, mines easily exposed nearby ore there, stops deepening the shaft and places a `Cavern closed due to monsters` warning sign when a shaft breach opens into a hostile cavern, then switches to primary and secondary tunnel work until a player clears the cavern and removes the sign, applies the same hostile-cavern closure and tunnel-only fallback to primary and secondary tunnel breaches, cuts primary side tunnels from the ladder walls as eligible shaft levels are reached instead of waiting for the whole shaft to hit bedrock. Primary tunnels are `2` blocks wide and `3` blocks tall, separated vertically by `2` solid rock levels, extend to the settlement radius, start from the upper eligible levels before proceeding downward, and scan all exposed tunnel faces for useful ore such as redstone. Once a level's primary tunnels are full length, Miners cut perpendicular secondary tunnels that are `1` block wide by `2` blocks tall, extend to the settlement radius, and use wall torches every `9` blocks when available. Miner lights are spawn-prevention infrastructure for mined and explored spaces, not decoration. Miners may place `dirt` or `cobblestone` floor supports when a tunnel mouth or floor lacks footing, preferring dirt before cobblestone, close a primary branch with a `Tunnel closed due to monsters` sign when hostiles are present in that tunnel branch, mark discoveries such as geodes, sculk sites, or ancient-city indicators with wall signs, mine reachable exposed copper, redstone, and geode resources such as amethyst clusters, amethyst blocks, and calcite from the existing ladder/tunnel network, continue mining newly exposed adjacent ore from the current vein before wandering to distant targets, fall back to mining reachable shaft-wall stone or ore when they cannot safely deepen yet or no branch task is available, replace ladder-support blocks with low-value solid support such as `dirt` before cobblestone when ore extraction would undermine them, rescue miners stranded below the planned completed shaft back toward known ladders before selecting new work, snap or step back onto nearby shaft ladders during end-of-day return when pathfinding cannot enter the ladder cleanly, treat both top ladder columns as valid exit columns, step out when within a few rungs of the ladder mouth, search a broad rough-lip area for a valid surface exit, use scripted overworld steps toward gathering or home after surfacing when vanilla pathfinding fails, clear fall distance during scripted rescue movement, and return up the shaft toward the surface at the end of the workday; later quarries and richer refining behavior still remain follow-up work, and upgraded primary tunnels should prefer `Copper Lantern` lighting before falling back to torches
- Visual identity: the Miner profession overlay should read as a slate-gray work coat with copper-and-leather tool accents rather than another woodworker palette
- Settlement behavior: adds cobblestone, stone, ore, coal, and other underground materials to stock; ore and ore-bearing finds should be refinable into useful derived goods such as ingots when the settlement needs them; consumes tools and support materials
- Change introduced by Live Villages: separates raw extraction from smithing and building support jobs

### Gardener

- Workstation: `Gardener Workstation`
- Associated structure: first-pass code anchors a staged bedded `Gardener's Shed` with a distinct fenced work-yard hint from the `Gardener Workstation`, recruits actual `Gardeners` from unemployed villagers when linked beds and job-site capacity are available, and gives Gardeners priority for those beds
- Equipment: carries a sling; first-pass loaded workers use the craftable `Sling` item for visible role presentation and may still show seeds while herding chickens
- Loaded-world behavior: tends decorative plots, orchards, flowers, hedges, and comfort-oriented greenery; builds trapdoor-edged raised dirt flower beds around houses, especially under windows and near doors when space allows; places small `3`-block raised beds near paths, route edges, and places of interest without obstructing road use or obvious future structure placement; seeds and refreshes those beds with flowers; uses seed stock to attract and manage chickens; feeds them seeds; and collects eggs
- First-pass loaded behavior: actual `Gardeners` visit their workstation, visibly carry a sling or task seeds, share a pass-local per-workstation task plan so multiple Gardeners do not repeat the same broad chicken, pen, and flower-bed scans in one maintenance pass, consume dirt and bone meal to place bounded raised dirt-and-flower beds near the workstation when room remains, bias those beds toward nearby paths, exterior walls, under-window and beside-window placements, nearby doors without blocking them, and places of interest, add optional trapdoor edging from settlement stock, reuse or continue nearby partial chicken pens when present, otherwise choose a small fenced chicken pen site near the workstation with bias toward nearby chickens and village-visible areas, build that pen from fence/gate materials one block at a time, herd stray adult chickens toward that pen when seed stock exists, target nearby real adult chickens around the workstation before consuming wheat seed stock, feed those chickens, put breedable chickens into love mode, produce modest egg stock from that visible work, convert bone meal into seed stock when no placement is available, improve settlement comfort through the settlement ledger, report consumed/produced goods when daily reports are enabled, and join bell-response defense with weak line-of-sight sling shots
- Settlement behavior: improves comfort, village legibility, flowers, eggs, and other decorative or husbandry outputs, and consumes beautification supplies such as dirt, trapdoors, flowers, bone meal, saplings, fence/gate materials, and seed stock; abstract unloaded simulation produces modest egg and flower stock from Gardeners instead of treating them as extra staple-crop Farmers
- Change introduced by Live Villages: splits village beauty and comfort work away from the main `Farmer` food loop

### Guard

- Workstation: `Guard Post`
- Workstation capacity: one `Guard Post` should support up to `5` Guards as a shared defensive job-site anchor instead of requiring one workstation per Guard
- Associated structure: first-pass code registers `Guard Posts`, anchors staged bedded Guard Post structures with a distinct fenced defensive apron instead of the generic hut layout, recruits actual `Guards` from unemployed villagers when linked beds and job-site capacity are available, and gives Guards priority for those beds; the internal beds are preferred first homes, but the shared job-site capacity can support additional Guards who sleep in other village housing
- Donations: first-pass code lets players donate swords, armor, shields, ranged weapons, ammunition, banners, and similar spoils of war directly to a `Guard Post`; banner donations enter settlement stock as `Desecrated Enemy Banner` trophies for later display
- Loaded-world behavior: patrols gates, warehouses, roads, docks, and raid approaches; escorts high-value traffic when needed; and fights with swords, armor, and shields when available
- First-pass loaded behavior: actual `Guards` join loaded bell-response defense, consume donated swords, armor, and shields from settlement stock for visible combat equipment upgrades, consume donated bows or crossbows plus arrows for first-pass ranged shots when a bell-response target is in line-of-sight range, fall back to a wooden sword when no donated melee weapon is available, deal melee or ranged damage based on the equipped/claimed weapon, escort nearby loaded workers who are carrying construction supplies or collected goods or performing higher-risk forestry, fishing, harbor, or mining work, prefer higher-risk escort tasks, bias escort selection toward workers near forecast Roadwright route corridors when route traces are available, avoid assigning multiple Guards to the same escort target during the same maintenance pass, perform simple loaded patrols around Guard Posts when no escort or bell alarm is active, and materialize optional exterior `Desecrated Enemy Banner` trophies on completed Guard Posts without consuming the trophy ledger; broader route-security policy remains planned
- Settlement behavior: raises security, improves route safety, reduces raid losses, consumes weapons, armor, food, shield, and ammunition support, and displays `Desecrated Enemy Banners` as a visible record of defended raids or defeated pillager bands; first-pass banner trophy stock provides a capped security and comfort benefit and can appear as optional exterior wall banners on completed Guard Posts
- Decoration rule: `Desecrated Enemy Banners` should look like regular pillager banners with a red circle and red slash painted over the existing design; Guard Posts may place them as optional exterior decoration in prominent locations that do not interfere with doors, paths, firing positions, storage, or other functionality
- Change introduced by Live Villages: turns village defense into an explicit staffed profession

### Roadwright

- Workstation: `Surveyor's Table`
- Associated structure: `Roadwright's Workshop`, a stone-walled variant of the `Carpenter's Workshop` footprint with a bed, work bay, corrected roof profile, and a third-level wall torch that can later upgrade to a lantern
- Workstation capacity: supports `1` Roadwright by default and can support a second Roadwright when village demand and housing justify it; the workshop bed or beds should prefer Roadwright residents
- Loaded-world behavior: builds local paths to nearby settlements up to `200%` of the owning settlement radius, upgrades route surfaces, maintains bridges, improves internal village paths with terrain-aware route choices instead of strict straight lines, repairs established path corridors without flood-filling adjacent greenspace, collects cleared `Leaf Litter` into settlement stock for later composting, discovers local path targets from staged structures, workstations, Trade Boards, composters/gardens, and exterior doors, connects workstation-associated buildings from their outside doors back to the main settlement path network, visibly uses a shovel for dirt-path work, and places `Mileposts` along established land routes, especially near village edges and route junctions while keeping them outside settlement bounds and roughly `100` blocks apart when spacing allows
- Path target rule: Roadwrights should connect exterior building entrances, workstations, and outdoor POIs; beds and ordinary storage are map POIs only, not automatic path destinations
- Loaded-world priority: internal village paths first, then construction or repairs when path work is not immediately available, then paths to known nearby settlements
- Workstation interaction: right-clicking a `Surveyor's Table` opens a village survey map with the settlement boundary, current loaded-world buildings and POIs, path quality shown in discrete color bands, Roadwright positions, and planned or in-progress path work
- Surveying priority: Roadwrights begin from a standable tile beside their own `Surveyor's Table`, route first toward the village center, then fan out to other POIs and path repairs
- Settlement behavior: turns route intent into actual land routes, spends road materials to improve quality and capacity, uses placed `Mileposts` to reinforce route legibility, adds a settlement-comfort benefit for better internal paths, and makes connected workstation buildings more likely to influence which related goods the settlement trades
- Notes: `Roadwright` replaces the earlier split `Pathwright` / `Roadwright` concept; unemployed villagers may help a present Roadwright with visible loaded path work, but should not independently plan road changes without one; advanced long-distance surveying and higher-tier route planning should still depend on `Cartographer` support; this role should be the second priority after `Guards` for breastplate and sword upgrades

### Portmaster

- Workstation: `Portmaster's Anchor`
- Associated structure note: no dedicated house or office; the anchor is the job-site and shoreline placement signal, and nearby `Dock` construction should prefer that anchor's facing when the harbor allows it
- Equipment: carries a sword and should be treated as harbor-defense capable
- Loaded-world behavior: patrols between the anchor, docks, and lighthouses; validates harbors; keeps dock-and-lighthouse infrastructure visibly staffed; defends with a sword against mobs entering the settlement via water; extinguishes lighthouse fires in the morning; relights them shortly before the village gathering period; toggles lighthouse-top campfires from ground level without needing to navigate up the lighthouse; and uses threatened lighthouses as harbor-warning points when nearby hostiles approach
- Settlement behavior: improves harbor trade value and route throughput where docks or lighthouses already exist, and prepares the settlement-side role needed for later true water-route logic
- Visual identity: should read immediately as harbor staff, with a blue-and-brass maritime palette that works with the wide hat and spyglass silhouette already used for the role
- Change introduced by Live Villages: creates a true harbor-logistics role instead of treating water trade as ordinary villager travel

### Scribe

- Workstation: `Scribe Desk`
- Physical form: a wooden desk `3` blocks wide, `1` block tall, and `1` block deep
- Loaded-world behavior: works in archives or offices, studies books and maps, records settlement needs, exchanges knowledge with visitors, and supports a player-facing recipe-trade interface at the `Scribe Desk`; first-pass code registers `Scribes`, `Scribe Desks`, staged `Scribe Office` anchoring with storage-heavy office details instead of the generic hut layout, persistent settlement-known starter recipes, a selectable desk interface where players can learn unknown settlement-known recipes for tiered paper or book payments or contribute player-known recipes into the settlement ledger for support rewards, server-revalidated recipe-row fingerprints so stale row clicks cannot teach or contribute the wrong recipe, and route-cycle recipe exchange between settlements when Scribe support is present
- Settlement behavior: stores and trades recipe knowledge, profession techniques, route intelligence, and civic records; may consume paper, books, or mapped data instead of bulk raw materials; can trade recipe knowledge with players and other settlements through Scribe-supported route exchanges
- Player-use behavior: using a `Scribe Desk` opens a recipe-trading interface. If the settlement knows a recipe, the player can obtain that recipe through the Scribe even without triggering the vanilla discovery condition, such as learning a boat recipe from a settlement before ever stepping into water. First-pass behavior stores a bounded starter recipe set in the settlement's persistent Scribe recipe ledger, provides `Learn` and `Contribute` modes, teaches selected settlement-known recipes server-side for tiered paper or book payments, lets the player contribute player-known non-special recipes into the settlement ledger for support rewards shown in the contribution tooltip, shows display-derived recipe result icons when available, removes successful recipe rows from both the client and server open-menu lists, rejects stale recipe-row clicks by recipe fingerprint, revalidates the live desk block and settlement link before server-side learn/contribute actions, and lets Scribe-supported route trade cycles exchange recipes between settlement ledgers; richer exchange rules and playtest price/reward tuning remain planned.
- Change introduced by Live Villages: gives knowledge exchange an explicit worker and workstation rather than leaving it implied inside other trade roles
- Notes: `Scribe` is an advanced Librarian role. It inherits the deskworker/noncombatant posture and knowledge theme from `Librarian`, then adds recipe trading, settlement-to-settlement knowledge exchange, and civic-memory effects such as tier-regression protection.

## Shared Civic Structures

Not every important workstation-like block needs to represent a separate villager profession.

### Trading Post

- Role: market-facing trade structure anchored by the `Trade Board`, not a separate villager workstation beyond that board
- Primary users: `Trademaster`, visiting traders, and nearby residents
- Physical form: should use the `5x8` multi-level blueprint in [SPECS.md](SPECS.md), with an open-air marketplace anchored by the `Trade Board` and small anti-climb details where needed so villagers use the intended gates and doors
- Material rules: wood should be a consistent local family; stone quality should influence trade value and may be upgraded later through visible block replacement
- Upgrade path: should later grow into a `Shopping Mall` tier rather than being replaced with a different core trade workstation
- Use: makes the settlement's commerce visible in-world, gives the `Trade Board` a full associated building, and should count as a major trade improvement for route and overlay summaries

### Warehouse

- Primary users: `Trademaster`, `Carpenter`, `Mason`, and future hauling or loading behavior
- Role in the simulation: stock capacity, storage visualization, route efficiency, and a physical loading point for settlement goods
- Current direction: keep the warehouse as a major settlement structure first, not a separate profession unless later testing proves a dedicated warehouse worker is necessary

### Bakery

- Primary user: `Baker`
- Role in the simulation: food processing, food variety, food-security support, and profession-specific food trades
- Physical form: should start from the same `5x8` layout family as the `Trading Post`, with glass-pane display openings, retained gates, and vertical baked-good shelves if shelves can be made readable in-game
- Use: gives egg, wheat, milk, sugar, fruit, and similar food inputs a specialist trade outlet even when those goods are not high enough priority to appear on the central `Trade Board`

### Milepost

- Role: route marker and reinforcement object, not a villager workstation
- Primary builders: `Roadwright` selects placement and `Mason` builds or upgrades the marker through `Stonecutter` work
- First-pass implementation note: `Mason` owns production of the finished `milepost` stock good, and loaded `Roadwright`s consume that stock to place markers beside established external land routes when spacing allows, skipping duplicate placements if an existing nearby `Milepost` already covers that interval
- Placement rule: should appear along established roads at regular intervals, such as about every `100` blocks, and also near village entries or major route forks, especially between settlements, without blocking pathing or obvious future structures
- Physical form: first pass should use a `3`-block-tall `1x1` obelisk marker so destination text can read vertically on the face; later material tiers can keep that silhouette or refine it
- Materials: start with `Stone`; allow upgrades such as `Smooth Stone`, `Polished Granite`, `Polished Andesite`, and `Obsidian`
- Use: helps make routes legible to the player, can display destination names on the face a traveler to that destination would see, may render that text vertically when it fits best, can include distance when still readable, and gives the settlement a modest route-quality or trade-throughput bonus that improves with material tier; first pass may use the nearest non-outpost settlement in each direction as the displayed face label, and diagonal destinations may appear on both relevant faces when no closer settlement clearly owns one of them

### Lighthouse

- Role: harbor landmark and water-trade amplifier rather than a villager workstation
- Placement block: players place a dedicated `Lighthouse` marker block; villagers then build the real tower around it with that marker kept at the center of the bottom `3x3` level
- Primary builders: `Mason` handles the cobblestone tower and `Portmaster` treats it as harbor infrastructure
- Physical form: the minimal tower is four `3x3` cobblestone levels, then four `1x1` cobblestone levels, with a `Campfire` on top
- Placement rule: should appear near a dock or shoreline vantage point with enough nearby water to justify harbor traffic
- Use: makes harbor settlements legible, signals the water-facing side of town, widens the `Portmaster's Anchor` map, adds a significant bonus to water-route throughput, quality, and trade distance compared with a dock alone, and contributes a modest harbor-security and raid-warning benefit
- Daily behavior: the lighthouse fire should be off during the daytime after morning harbor work, then relit shortly before villagers head to the village gathering
- Trade-range stacking: trade extensions should be cumulative. `Dock` unlocks water-route trade support, `Cartographer` unlocks efficient long-range land routes past the local `512`-block range and extends land and water trade range, `Scribe` extends land and water trade range, and `Lighthouse` extends water-route trade range
- Multiple lighthouses: a settlement may have more than one lighthouse, but only the first should provide the large harbor-range and trade bonus. Additional lighthouses should add only small extra water-trade, map-range, and security value

## Immediate Planning Takeaways

- Finish vanilla profession expansion before inventing duplicate custom roles: `Cleric`, `Librarian`, `Leatherworker`, `Shepherd`, `Armorer`, `Toolsmith`, and `Weaponsmith` now have concrete Tier 1 daily work loops; the four bedded vanilla workstation structures now need richer per-profession polish rather than basic anchoring.
- Keep bed-bearing profession structures coupled to staffing and home assignment: matching workers get priority for those beds, and structure capacity should follow actual completed linked beds.
- Make loaded movement settlement-aware: first-pass defensive-wall door use now opens and recloses completed palisade gatehouse access blocks only for nearby own-settlement villagers or illagers, and routine worker movement now biases toward nearby roads, paths, and bridge-like surfaces when the waypoint still makes target progress.
- Keep defense visible and place-based: `Guard Posts` now exist as staged bedded profession structures with shared Guard job-site capacity, loaded bell-defense participation, simple patrols, and optional exterior `Desecrated Enemy Banner` trophy placement. `Fishermen` and `Portmasters` help against waterborne threats, and harbor lighting can be operated from practical ground-level work.
- Keep comfort work visible and bounded: `Gardener Workstations` now anchor staged `Gardener's Sheds`, recruit actual Gardeners, run a first-pass seed/egg/bone-meal stock loop that feeds nearby real chickens before producing eggs, place a small capped set of raised dirt-and-flower beds near the workstation with path, exterior wall, under-window, beside-window, door-adjacent, and POI placement bias, add optional trapdoor edging, and build/herd toward a small chicken pen; deeper landscape design polish remains later work.
- Make reporting opt-in: daily settlement reports are controlled by the default-off `daily_settlement_reports` config flag or the default-off `live-villages:daily_settlement_reports` game rule, even though the report format remains the same when enabled.
- Treat `Scribe` as the advanced Librarian recipe-knowledge role: the `Scribe Desk` is a three-block wooden desk that now anchors staged `Scribe Offices`, seeds persistent settlement-known starter recipes, exposes a first-pass player-facing recipe-trade interface with `Learn` and `Contribute` modes plus stale-row protection, and enables bounded settlement-to-settlement recipe exchange during route trade cycles; ordinary `Librarians` keep baseline book and bookshelf production.
- Every profession design should still answer the same two questions: what the villager visibly does in the world, and what that job changes in the settlement ledger.
