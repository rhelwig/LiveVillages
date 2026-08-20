# Cleric Church Design Guide

This is the builder brief for replacing the current `Cleric Shrine` (a reused 5×8 Fletcher hut) with a civic church that grows with the settlement.

Use [STRUCTURE-BLUEPRINT-GUIDE.md](STRUCTURE-BLUEPRINT-GUIDE.md) for layer order, symbols, and snapshot workflow. This file is the program: how big each church is, what it is made of, who it holds, and what it should do for the village.

The first implementation pass should land Tier 1 as a real chapel. Tiers 2–4 can follow as in-place upgrades on the same site.

## Design Goals

- Every cleric village should have a building people recognize as a church, not a workshop with a brewing stand.
- The chapel must be a gathering room. Villagers should be able to stand inside it for meetings, not only pass through a 3-block hut.
- The building starts small and becomes the settlement's tallest civic landmark by Tier 4.
- Civic pride / comfort rises at every completed tier, and the bonus should grow with the building.
- When the village chooses a church site, prefer the opposite side of the settlement from the dock. The church and the harbor lighthouse are the two tallest objects; splitting them keeps the skyline balanced from a distance.
- If the settlement has a graveyard, keep it next to the church as a churchyard rather than an isolated field of graves.
- Founding workstation is a Live Villages `Altar`: a stone block plus a gold nugget, wool, and red dye. The altar is slightly shorter than a full block, and a tapestry drapes over the top and down the front with a red heart.
- A `Pulpit` is lectern-shaped furniture with a slightly wider pedestal and the same red-heart tapestry hanging down the front. Recipe: four wooden slabs, one wooden plank, one wool, and one red dye. It is not the founding lock and should not recruit the first cleric by itself.
- In a well-outfitted chapel, the sanctuary front is a triad: `Altar` in the center, `Pulpit` on one side, reserved `Brewing Stand` on the other.
- Clerics **strongly want** a `Brewing Stand` as soon as one is possible (nether trade, blaze rods, a generated temple). Install it on the reserved altar-adjacent work tile. Potion work stays on the stand.
- When the church has lodging, cleric beds belong in a side room or vestry, not in the chapel aisle. The Tier 1 chapel has no vestry.

## Capacity Rules

Count **standing floor cells** in the chapel, not the whole footprint. A standing cell is an interior air block with a solid floor, at least two blocks of headroom, and no bed, workstation, chest, or pew occupying it.

A Minecraft villager occupies one floor cell. Aisles, the altar step, and a small choir/standing margin can be included in the count.

| Tier | Name | Chapel standing cells | Other rooms | Target height |
| --- | --- | --- | --- | --- |
| 1 | Chapel | Open 7×7 nave | None; clerics use village housing | 7 layers, 3-block nave |
| 2 | Parish church | Longer nave plus vestry | Vestry / sacristy with cleric bed | 8 layers, 4-block nave |
| 3 | Civic church | Longer nave plus vestry | Vestry kept; front facade spire | 14 layers to the front finial |
| 4 | Cathedral | Longer nave plus vestry | Vestry kept; spire at each end | 15 layers to the twin finials |

The current lighthouse is a 3×3 shaft about `9` blocks tall. Later lighthouse tiers grow to about `16–18`. Houses in the generic hut family are about `7` layers. The cathedral steeple (`20+`) must still be the tallest civic landmark.

## Shared Plan

Keep one recognizable church language across tiers so an upgrade reads as the same building grown up, not a different structure type.

- Nave / chapel on the long axis, door in the front gable.
- Center aisle to a raised `Altar` at the far end. That block is the founding workstation and church anchor.
- Reserve a stand tile beside or behind the altar for a `Brewing Stand`. Leave it empty until the village can get one. Do not put the brewing stand in the pews.
- Windows along both long walls.
- Lighting on every public wall and at the altar.
- Cleric lodging off the chapel starting at Tier 2: a vestry with door, chests, and beds. Tier 1 is nave-only.
- Exterior: stone-family base, wood or stone walls by tier, a front that reads as a church from the road.

Do not put the only door behind the altar. Do not make the brewing stand the only thing in the room.

## Tier 1 — Chapel

Founding village church. Small, but a real interior. No back bedroom.

**Footprint:** 9 wide × 9 deep, including walls. Interior chapel 7×7.

**Height:** 7 layers. Nave interior is 3 blocks tall. Simple gable. One fewer open-air nave layer than the Tier 2 parish church.

**Chapel capacity:** the open 7×7 floor minus the altar / pulpit / reserved brewing-stand triad.

**Rooms:**

- Chapel / nave only
- No vestry; clerics sleep in ordinary village housing until Tier 2

**Materials:**

- `M` cobble or local stone foundation and low wall
- `P` / `L` local wood walls and roof
- `V` clear glass panes; the top of each window is a plank lintel, not glass
- `t` + `U` nave sconces: closed top-half trapdoors with a lit candle on top, one layer below the old wall-torch line
- `D` front door pair only
- `W` founding `Altar`
- `p` pulpit on one side of the altar
- `b` reserved `Brewing Stand` on the other side; the chapel can finish without it

**Civic pride:** small completed-church comfort bonus. This is the first public indoor gathering room besides houses.

**Look:** patched founding workwear of buildings. Visible timber, modest porch, no stained glass yet.

## Tier 2 — Parish Church

The chapel that has arrived. The current authored 9×15 vestry church is this tier.

**Footprint:** 9 wide × 15 deep. Same 9-wide nave as Tier 1, plus the rear vestry and two extra aisle rows.

**Height:** 8 layers. Nave interior is 4 blocks tall. The vestry roof sits one block lower so that room is 3 blocks tall and reads as an attached side chamber.

**Chapel capacity:** the longer open nave minus the altar triad.

**Rooms:** vestry with one bed and a chest behind the sanctuary. The chapel should feel like the main room, not a hallway to beds.

**Materials:**

- More `M` on the lower walls and corners
- Brass-language accents through lanterns and a few polished or cut stone bits where the palette allows
- Extra `V` windows, still clear panes under plank lintels
- The vestry desk plank next to the chest holds a lit candle (`U`)
- Same trapdoor-and-candle nave sconces as Tier 1, one layer below the old wall-torch line

**Civic pride:** medium comfort bonus, larger than Tier 1. A village with a parish church should feel more established than one with only huts.

**Look:** the same church, raised walls, deeper nave, a front that can be seen over nearby roofs.

## Tier 3 — Civic Church

Dressed civic architecture. Silver-stone, copper clasps, real tower.

**Footprint:** the Tier 2 9×15 vestry church, plus a 3×3 front facade tower / spire.

**Height:** 14 layers to the front spire finial.

**Chapel capacity:** 32–36 standing cells.

**Rooms:**

- Chapel
- Vestry / sacristy with cleric beds
- Optional side aisle or small meeting alcove

**Materials:**

- Stone-family walls as the majority, wood for roof and trim
- Copper / lantern lighting language
- Every `V` window pane is stained: a repeating red / yellow / blue pattern
- Lighting on every exterior wall face and along the aisle

**Civic pride:** large comfort bonus. This is a town monument, not just a job site.

**Look:** dressed stone, taller nave, a front bell tower with a waxed copper-stair roof. A Mason-cast `Copper Bell` hangs in the window chamber when the village can make one.

**Bell tower:** the front 3×3 shaft is a real belfry. The optional `g` tile is a `Copper Bell`. Players can craft the bell; unattended villages need a `Mason` to cast it.

## Tier 4 — Cathedral

Prestige civic building. Gold and brick-red language, the tallest thing in the settlement.

**Footprint:** the same 9×15 church, with a spire at each end (front facade and rear / vestry).

**Height:** 15 layers to the taller twin-spire finials.

**Chapel capacity:** 50+ standing cells in the chapel alone.

**Rooms (required):**

- Chapel / nave that holds the 50
- At least one additional room: vestry, chapter, or side chapel
- Cleric lodging can stay in the vestry

**Materials:**

- Outer wall planks become stone-family blocks (`M`; smooth stone / stone bricks by civic palette)
- Dyed glass on every window pane, same red / yellow / blue pattern as Tier 3
- Lighting all around: wall lights on every exterior face, hanging nave lights, altar lights, tower light
- Gold-language trim through lanterns, blocks, or an authored accent the construction palette can actually place

**Civic pride:** the largest church comfort bonus. A cathedral should move settlement comfort more than a gardener or a trophy banner.

**Look:** people should be able to find the village from a hill by the steeple.

## Civic Pride

There is no separate prestige stat today. Express church pride as a **completed-structure comfort bonus** that scales by the church's civic tier.

Suggested first-pass weights, to tune after playtest:

| Completed church tier | Comfort add | Notes |
| --- | --- | --- |
| 1 | `+0.04` | Same order as two gardeners |
| 2 | `+0.07` | Noticeable on the Trade Board |
| 3 | `+0.10` | Civic monument |
| 4 | `+0.14` | Near the top of the comfort budget |

Only the highest completed church on the site should count. An upgrade replaces the previous bonus. An unfinished upgrade should keep the last completed bonus.

A church is public infrastructure. It should help growth the way housing quality and decoration already do, not replace food or beds.

## Sunday Service

If a settlement has a completed church and at least one `Cleric`, villagers hold a service every eight days on the full moon.

- Start about an hour after breakfast (`3600` day ticks) and last one hour (`1000` ticks).
- The first cleric stands behind the `Altar`. A second cleric stands behind the `Pulpit`. Both blocks are cleric job sites, one villager each. A brewing stand remains the potion workstation.
- The congregation fills the nave in front of the altar.
- If the church has a `Copper Bell` or vanilla bell in the tower, it rings three times at the start of the service. Those rings do not raise a defense alarm.
- After the service, villagers take the rest of the working day off and wander trees and gardens until ordinary evening gathering.

The comfort bonus from a completed church applies as in the table above. A staffed church (at least one cleric) adds a further `+0.03`.

## Sanctuary

The interior of a completed church is a no-violence zone.

- Nave, vestry, and (at Tier 3+) the front tower shaft. Not the steps, road, or churchyard.
- If the victim is inside, intentional harm does no damage: melee, arrows, mob attacks, player hits, and explosion *entity* damage.
- Fall, drowning, fire you walked into, freezing, starvation, and `/kill` still work.
- Block breaking is unchanged.
- Mobs lose their target at the door instead of standing in the aisle swinging.

## Construction And Upgrade Rules

- Anchor the church on an `Altar`. An unattended village should be able to place that altar from stone, a gold nugget, wool, and red dye and grow the chapel around it. Place a `Pulpit` on one side of the altar when the settlement can craft one. Leave the opposite cell empty until a `Brewing Stand` is possible.
- When the village itself picks the site, score candidates so the church sits on the landward / opposite side of the settlement from the primary dock. If there is no dock, ordinary civic placement is fine. Do not fail the project just because the ideal opposite-shore plot is blocked; fall back to the next-best valid site.
- A player-placed or temple-generated `Brewing Stand` is a valid shortcut and should still start or claim the church, then receive an altar if one is missing. Player placement may ignore the opposite-dock preference.
- Once a cleric or completed chapel exists, the settlement should treat a missing brewing stand as a high-priority want and install it on the reserved work tile.
- Later tiers should upgrade the same site: keep the altar, expand the footprint, raise the roof, then add the tower.
- Do not demolish a finished chapel because a higher-tier plan wants a bigger hole. Prefer reserved expansion cells around the Tier 1 plan from the start if the code allows a growing footprint.
- Gathering / meeting AI can come later. The first requirement is that the room is large enough for the stated headcount.
- The church is not a substitute for a palisade, lighthouse, or Trade Board.

## Founding Workstation

Use an `Altar`, not a pulpit, as the church job-site:

- An altar is already the visual heart of the nave in this brief.
- Recipe: one `Stone`, one gold nugget, one wool, and one red dye.
- The block is slightly shorter than a full cube. The tapestry drapes over the top and down the front, with a red heart on the cloth.
- A `Pulpit` is aisle/sanctuary furniture, not the thing that decides whether clergy exist. Craft it from four wooden slabs, one plank, one wool, and one red dye. It should read as a lectern with a wider pedestal and the same heart tapestry hanging down the front.

Clerics recruited at an altar should still path to a brewing stand for potion work when one is present. If both exist, the altar keeps church membership and the stand keeps alchemy.

## Churchyard / Cemetery

If the settlement has or starts a graveyard, keep it close to the church.

- Prefer a plot beside or immediately behind the chapel, inside the same civic cluster, not across town by the dock.
- When a church is placed first, later graves should extend that churchyard rather than founding a second isolated cemetery.
- When deaths happen before a church exists, site the first graveyard on the inland / opposite-dock side of the village so a later chapel can sit next to it. Do not move existing stones just to tidy the plan.
- Every settlement villager who dies, for any reason, should receive a grave marker in that cemetery. Named stones stay the default; unnamed members still get a marker. Larger or role-themed markers remain reserved for important citizens.

## What To Hand Over

For each tier, a designer should give:

- structure name and front
- one text grid per layer, or a `P`-key snapshot from the front
- bed cells called out
- altar cell called out, plus the reserved brewing-stand tile
- standing-capacity count for the chapel
- intended window colors at T3/T4
- a short note if any block is dyed glass, copper, or another symbol we do not have yet

A creative-world mockup of all four tiers in a line is more useful than a single cathedral.

## Current Code Stand-In

Tier 1 uses the authored 9×9 nave-only chapel with pane windows, plank lintels, and trapdoor-and-candle sconces. Tier 2 uses the authored 9×15 vestry chapel; the vestry desk candle is a real candle on the plank next to the chest. Tier 3 keeps that church and adds a front spire plus stained panes. Tier 4 turns the outer wall planks to stone and adds a matching rear spire. In-place growth on the same site is still future work.
