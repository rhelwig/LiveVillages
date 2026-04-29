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
- If a recognized workstation cannot support its associated structure because the site is blocked or invalid, villagers should leave a nearby temporary sign saying `can't build here` instead of silently failing.
- Bulk goods stay in settlement stock. Villagers should carry only small task loads, tools, finished deliveries, or equipment.
- Vanilla professions should remain useful even before every one receives bespoke AI.
- New professions should be introduced only when vanilla professions cannot cleanly cover the required behavior.
- Profession behavior should prefer shared task logic plus data-driven priorities and material preferences where practical; do not assume a full embedded scripting language is needed unless tuning needs outgrow declarative data.
- Loaded-world profession work must be bounded and measurable. Cache broad scans, keep villagers committed to a selected task for a short interval, and add timing logs around new tick-driven systems so server lag can be diagnosed before it becomes visible in play.
- Terrain-changing workers should share greenspace constraints. Roads, forestry, farming expansion, and future gardening should leave usable grass, flowers, saplings, and trees inside the village instead of optimizing every nearby block for production or travel.

## Vanilla Baseline

The vanilla profession list is still the starting point. Live Villages should not throw it away; it should reinterpret and expand it.

### Vanilla Profession and Workstation Mapping

| Profession | Vanilla workstation | Live Villages purpose | Current code note |
| --- | --- | --- | --- |
| Armorer | Blast Furnace | Metal gear, heavy craft support, and defensive equipment supply | Counted with construction-support labor today |
| Butcher | Smoker | Herd growth, culling, meat and leather supply, and livestock trade surplus | Counted with food labor today |
| Cartographer | Cartography Table | Route intelligence, long-range surveying, and far-distance trade support | Counted with trade/knowledge labor today |
| Cleric | Brewing Stand | Alchemy, healing support, and rare-goods handling | Counted with trade/knowledge labor today |
| Farmer | Composter | Garden management, crop rotation, seed handling, and soil fertility | Counted with food labor today |
| Fisherman | Barrel | Fishing, shoreline food supply, and harbor-adjacent production | Counted with food labor today |
| Fletcher | Fletching Table | Arrow supply, special ammunition, ranged-support goods, and wood byproducts | Loaded work now crafts arrows for town stock, provides ranged defense around the settlement, and anchors a two-bed hut build |
| Leatherworker | Cauldron | Hides, utility gear, and transport-adjacent goods | Not yet grouped beyond vanilla behavior |
| Librarian | Lectern | Books, records, and early knowledge work | Counted with trade/knowledge labor today |
| Mason | Stonecutter | Stone shaping, masonry, shared construction labor, and structural repair | Counted with construction labor today |
| Shepherd | Loom | Wool, cloth, beds, decoration, and pasture output | Counted with food labor today |
| Toolsmith | Smithing Table | Tool production and workshop support | Counted with construction-support labor today |
| Weaponsmith | Grindstone | Arms, repairs, and guard equipment support | Counted with construction-support labor today |

### Non-Profession Villager States

| State | Workstation | Live Villages use |
| --- | --- | --- |
| Unemployed | None | Fallback labor pool, promotion candidates, and shortage relief |
| Nitwit | None | Mostly outside skilled labor; may remain low-agency population flavor unless future hauling behavior justifies more |
| Baby | None | Future workforce only; should affect growth, not current production |

### Current Functional Grouping

Until every vanilla profession has distinct job AI, the simulation can group them into broader settlement functions:

- Food and husbandry workers: `Farmer`, `Butcher`, `Fisherman`, `Shepherd`
- Core construction workers: `Mason`, plus the planned custom `Carpenter`
- Supporting craft workers: `Armorer`, `Toolsmith`, `Weaponsmith`
- Trade and knowledge workers: `Cartographer`, `Cleric`, `Librarian`, plus the custom `Trademaster`
- Open expansion targets: `Leatherworker` should eventually receive clearer settlement jobs instead of falling through to generic labor

### Outpost Role Bias

- Pillager outposts should favor offensive or coercive roles such as `Guard`, `Armorer`, `Weaponsmith`, and other raid-support labor.
- Outposts generally should not spend scarce labor on civilian comfort or village-beautification roles such as `Farmer` or `Gardener` unless a future special-case variant explicitly needs them.
- Outposts may still build required structures without the exact matching profession, but that fallback construction should be slower and less efficient than specialist village labor.

### Shared Equipment and Defense Rules

- Combat-capable field workers should carry profession tools or weapons when defined for their role.
- Field workers without a more specific weapon rule should fall back to at least a `Wooden Sword`.
- Deskworker professions `Scribe`, `Trademaster`, `Cartographer`, `Cleric`, and `Librarian` are intentionally unarmed and should flee from hostile mobs rather than stand and fight.
- `Sling`: a weaker bow-style ranged weapon with built-in unlimited ammunition.
- `Crooked Staff`: an improvised melee weapon better than a stick but weaker than any proper vanilla weapon.
- `Scythe`: a tool-weapon better than a `Crooked Staff` but still weaker than any proper vanilla weapon.
- `Guards` should receive the first priority for combat upgrades, then `Roadwrights`, then other armed workers.
- `Leatherworkers`, `Weaponsmiths`, and `Armorers` should equip themselves appropriately before distributing spare upgrades to others.

### Priority Vanilla Role Expansions

These roles already exist in vanilla and should be expanded before adding unnecessary duplicate professions.

### Cartographer

- Workstation: `Cartography Table`
- Workstation structure: `Cartographer's House`, anchored from a player-placed vanilla `Cartography Table`
- Structure baseline: use vanilla cartographer houses where practical; the first staged build blueprint is a simplified plains cartographer house using the existing visible construction pipeline and explicit roof-stair orientation metadata
- Loaded-world behavior: surveys routes, records settlement knowledge, and refreshes route intelligence for distant trade
- Settlement behavior: nearby trade can be coordinated by a `Trademaster`, but routes beyond about `32` chunks / `512` blocks should require cartographic support to be established or maintained efficiently
- Notes: `Cartographer` improves route knowledge and long-range trade quality, does not replace the worker physically building roads, and should behave as an unarmed deskworker in danger

### Farmer

- Workstation: `Composter`
- Equipment: carries hoes and scythes
- Loaded-world behavior: manages a composter-anchored farm territory, prioritizes ripe crops first, harvests mature crops into settlement stock, replants empty farmland from available seed stock based on current shortages, collects loose crop and seed drops, collects composter or loose `bone_meal` and applies it to the least-ready high-need crops, gathers both floating and flat ground `leaf_litter`, treats wheat harvest as a small virtual litter source for composting, and trims short or tall grass for cleanup and compost input
- Settlement behavior: turns accessible farm territory, seed availability, compost output, and labor into stable food output; requests or trades for missing seed stock such as potatoes or beetroot seeds, then folds those crops into rotation when under-supplied
- Farm-territory definition: the default territory can start as the raised-bed footprint around the composter, but the system should treat nearby farmland, dropped farm goods, loose leaf litter, and natural grass around that composter as part of the same managed work area so the player can extend it naturally

### Butcher

- Workstation: `Smoker`
- Associated structure: `Butcher Shop`, using the current small workstation-house footprint family for the first pass and staging around the placed `Smoker`
- Loaded-world behavior: manages fenced pig and cow herd space across the whole settlement, shears sheep across the settlement's livestock territory, feeds breeding stock, breeds animals when below target, culls adults when above target, and expands pens when crowding and terrain allow
- Settlement behavior: aims for enough herd capacity to satisfy village food needs plus useful trade surplus; produces beef, mutton, leather, and husbandry value from culling and herd management, and should be able to export meat surpluses to other villages over active routes
- Territory rule: the `Smoker` is the profession anchor, not the boundary of the work area; butchers should be able to shear sheep, feed animals, breed herds, and cull stock anywhere inside the settlement's managed livestock space
- Pen definition: player-built fenced pasture expansions anywhere in the settlement should be claimable by the livestock-management system when they connect cleanly to an existing managed herd area or otherwise qualify as village pasture

### Mason

- Workstation: `Stonecutter`
- Associated structure: `Mason's House` or `Mason's Workshop`, preferably based on the vanilla plains/stone mason house footprint or a close Live Villages equivalent with a visible stonecutter work area and stone-material storage
- Loaded-world behavior: builds foundations, walls, chimneys, bridges, defensive works, masonry-heavy repairs, and stone `Mileposts`; upgrades existing stonework toward better materials when no higher-priority construction or repair task is waiting
- Settlement behavior: acts as shared construction labor alongside the custom `Carpenter`, with a focus on stone durability, defensive value, route-marker construction, and structural upgrades
- Notes: `Mason` should become a first-class settlement construction worker, not just a background vanilla crafter; new construction and urgent repairs should outrank prestige upgrades such as cobblestone-to-stone or rough-stone-to-polished passes

### Cleric

- Workstation: `Brewing Stand`
- Loaded-world behavior: brews and maintains healing potions when materials allow, runs from hostiles by default, but will briefly rush in to heal injured defenders before retreating again
- Settlement behavior: converts brewing materials into healing support, rare alchemy, and defender recovery
- Notes: `Cleric` is a deskworker profession and should stay unarmed even when healing in danger

### Fisherman

- Workstation: `Barrel`
- Loaded-world behavior: works shorelines, rivers, docks, and harbor edges; gathers fish; and carries a spear or trident
- Settlement behavior: adds fish and shoreline food output, especially in water-linked settlements

### Fletcher

- Workstation: `Fletching Table`
- Loaded-world behavior: crafts arrow supply from stocked `stick`, `feather`, and arrowhead materials, supports ranged defense with skeleton-strength bow fire against visible nearby threats, and carries a bow with profession-supplied unlimited arrows
- Settlement behavior: turns wood, feathers, and arrowhead materials into ranged gear and ammunition support; ordinary villages should prefer efficient `Copperhead Arrow` output, with `Ironhead Arrow` production gated behind real iron surplus or heightened defense demand
- Housing/workstation note: a placed `Fletching Table` should anchor a `Fletcher's Hut` with `2` beds and can support up to `2` Fletchers in the same settlement
- Player-use note: the `Fletching Table` should become a real arrow workstation for the player, with higher-yield batch recipes and a separate material-arrow family such as `Copperhead Arrow`, `Ironhead Arrow`, and `Diamondhead Arrow`; final item naming should avoid collision with vanilla potion `Tipped Arrow` terminology

### Leatherworker

- Workstation: `Cauldron`
- Loaded-world behavior: processes hides, wears a full set of leather armor, and once per week equips one villager who lacks a breastplate with a leather breastplate when leather stock allows
- Settlement behavior: turns hides into early armor, utility goods, and transport-adjacent leather equipment

### Shepherd

- Workstation: `Loom`
- Loaded-world behavior: manages sheep flocks, maintains fences, feeds sheep, collects wool, and defends the flock from wolves or hostile mobs with a sling at range and a crooked staff up close
- Settlement behavior: adds wool, flock stability, and future textile support while protecting pasture productivity

### Armorer

- Workstation: `Blast Furnace`
- Loaded-world behavior: wears a full suit of the best armor available and once per week upgrades one villager breastplate to the next available tier; provides guards shields as soon as enough material exists
- Settlement behavior: converts metal stock into defensive gear and raises settlement survivability over time
- Notes: upgrade priority should be `Guards` first, then `Roadwrights`, then other villagers

### Weaponsmith

- Workstation: `Grindstone`
- Loaded-world behavior: carries the best sword available and upgrades one villager sword per day to the next available tier, starting with the weakest current weapon
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
- Status: Planned
- Loaded-world behavior: visits bee nests and bee hives, maintains safe campfires below managed hives, harvests honey bottles when bottles are available, harvests honeycomb with shears, breeds bees with flowers when hive population is low, and expands toward at least `3` managed hives by placing crafted bee hives in dispersed, flower-rich locations near the village edge
- Settlement behavior: produces honey bottles, honeycomb, candles, and modest food or comfort value; requests glass bottles, shears, campfires, flowers, wood, and hive-building supplies when those are missing
- Structure behavior: the `Beekeeper's Apiary` should be a small staged workstation structure anchored by the `Honey Separator`, with a covered work area, nearby managed hives, flowers, and protected smoke sources; it should prefer village-edge sites with enough open air and should avoid crowding central paths or other building footprints
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
- A workstation structure may move the workstation from the player's original placement block to the blueprint's intended final block, including elevation changes, without canceling the build site. The original anchor block should remain linked to that build until villagers remove or relocate it as part of construction.
- Worker-profession workstations generally support one or two villagers rather than exactly one; their associated structures should prefer assigning internal beds to matching professionals and can use a second bed when the village needs another worker. Guard Posts are the larger exception and should support up to five Guards.
- Construction material should come from settlement stock or villager inventories, with basic conversion from adaptable raw materials such as logs into planks, stairs, slabs, fences, gates, or doors.
- Adult non-Nitwit villagers may help with general construction once higher-priority profession work is satisfied. Unemployed villagers should be available immediately, while specialists can later get faster placement or carry bonuses for matching materials.
- Professional high-priority work outranks generic construction, except that a villager should finish the current single-block action before switching. Guards may interrupt immediately for imminent hostile threats.
- Villagers in loaded chunks should visibly visit an adjacent access tile at the `Trade Board` or completed `Trading Post` to deposit collected items or retrieve needed construction materials, especially when their personal inventory is close to full. End-of-day deposits should happen before the evening gathering and can appear as `Depositing into Trading Post`. Villagers should not target the board block itself as a walking destination.
- Villages should not queue duplicate staged structures, upgrades, or repairs while an equivalent build site is already in progress, and damaged completed structures should reopen as repair work rather than creating replacement duplicates.
- Later housing expansion can add staged second-floor apartment upgrades to compatible completed workstation structures, with accessible stairs, a small door platform, and housing credit only after completion.

### Profession-Specific Trade

- The `Trade Board` should focus on the highest-priority settlement-wide wants, not every possible item the village can use.
- Profession structures should expose narrower trades for lower-priority but still useful goods.
- `Baker` structures can trade baked goods for inputs such as eggs, wheat, milk, sugar, or fruit.
- `Beekeeper` structures can trade honey bottles, honeycomb, candles, or related hive goods for glass bottles, shears, flowers, campfires, or wood inputs.
- `Carpenter` structures can trade wood outputs such as stairs, slabs, fences, gates, and doors for logs or planks, with deals at least as good as direct crafting and faster for the player.
- `Butcher` structures can trade animal outputs such as beef, pork, or leather for feed such as wheat.
- `Mason` structures can trade processed stone outputs such as polished blocks, stairs, or slabs for rough stone-family inputs such as granite or diorite.

## Planned Professions

Workstation names such as `Miner Workstation` are current working names carried forward from [SPECS.md](SPECS.md). Final block names can be revised later without changing the role definitions here.

### Baker

- Workstation: `Baker Workstation` / `Bakery` block name to be finalized
- Associated structure: `Bakery`
- Loaded-world behavior: works in the bakery, collects or requests food inputs, bakes bread, cakes, pies, cookies, and similar baked goods when supplies are available, and visibly trades food with visitors through the profession structure
- Settlement behavior: converts farm and livestock supplies into higher-value or more varied food stock, improving food security and trade options without overloading the central `Trade Board`
- Physical form: the `Bakery` should use the same `5x8` overall footprint and broad layout as the `Trading Post`, but replace fence openings with glass panes behind vertical shelves of baked goods if that presentation works; gates should remain
- Change introduced by Live Villages: creates a specialist food-processing and food-trade role instead of treating all prepared food as generic farmer output

### Forester

- Workstation: `Forester's Table`, a wood-and-log work table with axe visual language
- Associated structure: `Forester's Workshop`, using the Carpenter/Roadwright workshop layout family but built almost entirely from wood with heavy log use
- Equipment: carries axes and hoes
- Status: First loaded-world forestry pass implemented
- Loaded-world behavior: manages groves beyond the village core, chops mature natural trees only when enough nearby trees remain, avoids trees with bee nests or bee hives, replants from carried or village seedling stock, keeps village interiors relatively sparse while allowing thicker exterior woods, collects forest drops such as saplings, apples, sticks, and leaf litter, opportunistically picks up nearby dropped logs and saplings left by the player, and uses hoes where ground preparation helps managed groves
- Settlement behavior: adds logs, sticks, apples, saplings, and similar wood outputs to stock; keeps a small reserve of diverse seedlings; requests tools and saplings when needed; and can surface trade demand for missing saplings through the Trademaster / Trade Board economy
- Change introduced by Live Villages: gives timber its own dedicated production role instead of treating wood as passive world scenery

### Miner

- Workstation: `Miner Workstation`
- Equipment: carries pickaxes
- Loaded-world behavior: works quarries, mine entrances, and underground extraction points; gathers raw stone and ore materials
- Settlement behavior: adds cobblestone, stone, ore, coal, and other underground materials to stock; consumes tools and support materials
- Change introduced by Live Villages: separates raw extraction from smithing and building support jobs

### Gardener

- Workstation: `Gardener Workstation`
- Equipment: carries a sling
- Loaded-world behavior: tends decorative plots, orchards, flowers, hedges, and comfort-oriented greenery; builds trapdoor-edged raised dirt flower beds around houses, especially under windows and near doors when space allows; places small `3`-block raised beds near paths, route edges, and places of interest without obstructing road use or obvious future structure placement; seeds and refreshes those beds with flowers; uses seed stock to attract and manage chickens; feeds them seeds; and collects eggs
- Settlement behavior: improves comfort, village legibility, flowers, eggs, and other decorative or husbandry outputs, and consumes beautification supplies such as dirt, trapdoors, flowers, bone meal, saplings, and seed stock
- Change introduced by Live Villages: splits village beauty and comfort work away from the main `Farmer` food loop

### Guard

- Workstation: `Guard Post`
- Workstation capacity: one `Guard Post` should support up to `5` Guards as a shared defensive job-site anchor instead of requiring one workstation per Guard
- Donations: accepts weapons, armor, shields, ammunition, pillager banners, and similar spoils of war; donated pillager banners should become `Desecrated Enemy Banner` trophies for the associated Guard structure
- Loaded-world behavior: patrols gates, warehouses, roads, docks, and raid approaches; escorts high-value traffic when needed; and fights with swords, armor, and shields when available
- Settlement behavior: raises security, improves route safety, reduces raid losses, consumes weapons, armor, food, shield, and ammunition support, and displays `Desecrated Enemy Banners` as a visible record of defended raids or defeated pillager bands
- Change introduced by Live Villages: turns village defense into an explicit staffed profession

### Roadwright

- Workstation: `Surveyor's Table`
- Associated structure: `Roadwright's Workshop`, a stone-walled variant of the `Carpenter's Workshop` footprint with a bed, work bay, corrected roof profile, and a third-level wall torch that can later upgrade to a lantern
- Workstation capacity: supports `1` Roadwright by default and can support a second Roadwright when village demand and housing justify it; the workshop bed or beds should prefer Roadwright residents
- Loaded-world behavior: builds local paths to nearby settlements, upgrades route surfaces, maintains bridges, improves internal village paths with terrain-aware route choices instead of strict straight lines, repairs established path corridors without flood-filling adjacent greenspace, collects cleared `Leaf Litter` into settlement stock for later composting, discovers local path targets from staged structures, workstations, Trade Boards, composters/gardens, and exterior doors, connects workstation-associated buildings from their outside doors back to the main settlement path network, visibly uses a shovel for dirt-path work, and places `Mileposts` along established routes, especially near village edges and route junctions
- Path target rule: Roadwrights should connect exterior building entrances, workstations, and outdoor POIs; beds and ordinary storage are map POIs only, not automatic path destinations
- Loaded-world priority: internal village paths first, then construction or repairs when path work is not immediately available, then paths to known nearby settlements
- Workstation interaction: right-clicking a `Surveyor's Table` opens a basic village survey map with the settlement boundary, known buildings and POIs, path quality shown in discrete color bands, Roadwright positions, planned or in-progress path work, and fog-of-war style coverage for less-surveyed areas
- Surveying priority: Roadwrights begin from a standable tile beside their own `Surveyor's Table`, route first toward the village center, then fan out to other POIs and path repairs
- Settlement behavior: turns route intent into actual land routes, spends road materials to improve quality and capacity, uses placed `Mileposts` to reinforce route legibility, adds a settlement-comfort benefit for better internal paths, and makes connected workstation buildings more likely to influence which related goods the settlement trades
- Notes: `Roadwright` replaces the earlier split `Pathwright` / `Roadwright` concept; unemployed villagers may help a present Roadwright with visible loaded path work, but should not independently plan road changes without one; advanced long-distance surveying and higher-tier route planning should still depend on `Cartographer` support; this role should be the second priority after `Guards` for breastplate and sword upgrades

### Portmaster

- Workstation: `Portmaster's Anchor`
- Associated structure note: no dedicated house or office; the anchor is the job-site and shoreline placement signal, and nearby `Dock` construction should prefer that anchor's facing when the harbor allows it
- Loaded-world behavior: patrols between the anchor, docks, and lighthouses; validates harbors; and keeps dock-and-lighthouse infrastructure visibly staffed while deeper cargo, dredging, and canal work remain future expansion
- Settlement behavior: improves harbor trade value and route throughput where docks or lighthouses already exist, and prepares the settlement-side role needed for later true water-route logic
- Visual identity: should read immediately as harbor staff, with a blue-and-brass maritime palette that works with the wide hat and spyglass silhouette already used for the role
- Change introduced by Live Villages: creates a true harbor-logistics role instead of treating water trade as ordinary villager travel

### Scribe

- Workstation: `Scribe Desk`
- Loaded-world behavior: works in archives or offices, studies books and maps, records settlement needs, and exchanges knowledge with visitors
- Settlement behavior: stores and trades recipe knowledge, profession techniques, route intelligence, and civic records; may consume paper, books, or mapped data instead of bulk raw materials
- Change introduced by Live Villages: gives knowledge exchange an explicit worker and workstation rather than leaving it implied inside other trade roles
- Notes: `Scribe` should remain unarmed and flee like other deskworkers

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
- Placement rule: should appear along established roads at regular intervals, such as about every `100` blocks, and also near village entries or major route forks, especially between settlements, without blocking pathing or obvious future structures
- Physical form: first pass should use a `3`-block-tall `1x1` obelisk marker so destination text can read vertically on the face; later material tiers can keep that silhouette or refine it
- Materials: start with `Stone`; allow upgrades such as `Smooth Stone`, `Polished Granite`, `Polished Andesite`, and `Obsidian`
- Use: helps make routes legible to the player, can display destination names on the face a traveler to that destination would see, may render that text vertically when it fits best, can include distance when still readable, and gives the settlement a modest route-quality or trade-throughput bonus that improves with material tier; first pass may use the nearest non-outpost settlement in each direction as the displayed face label, and diagonal destinations may appear on both relevant faces when no closer settlement clearly owns one of them

### Lighthouse

- Role: harbor landmark and water-trade amplifier rather than a villager workstation
- Primary builders: `Mason` handles the cobblestone tower and `Portmaster` treats it as harbor infrastructure
- Physical form: the minimal tower is four `3x3` cobblestone levels, then four `1x1` cobblestone levels, with a `Campfire` on top
- Placement rule: should appear near a dock or shoreline vantage point with enough nearby water to justify harbor traffic
- Use: makes harbor settlements legible, signals the water-facing side of town, and adds a significant bonus to water-route throughput or route quality compared with a dock alone

## Immediate Planning Takeaways

- Vanilla professions remain part of the workforce and should be mapped into useful settlement labor instead of being ignored.
- `Trademaster` and `Carpenter` are real custom professions; `Mason` should still take on more explicit shared construction labor.
- `Cartographer`, `Farmer`, `Butcher`, and `Mason` should all gain stronger settlement-specific behavior before adding redundant replacement professions.
- `Roadwright` should own both route creation and route improvement, while `Cartographer` provides the long-range survey gate for distant trade.
- The next wave of custom roles should cover the missing settlement-scale jobs that vanilla does not model well: timber, mining, beauty/comfort work, security, roads, harbors, and knowledge.
- Every profession design should answer the same two questions: what the villager visibly does in the world, and what that job changes in the settlement ledger.
