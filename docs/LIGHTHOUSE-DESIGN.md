# Lighthouse Design Guide

Builder brief for growing the current 3×3 cobble shaft into a civic-tier harbor landmark.

Use [STRUCTURE-BLUEPRINT-GUIDE.md](STRUCTURE-BLUEPRINT-GUIDE.md) for layers and symbols. This file is the program: footprint, height, bedroom, and what stays the same.

The Portmaster still sleeps in ordinary village housing. The lighthouse is harbor infrastructure. A ground-floor bedroom at Tier 3 is optional lodging, not a required Portmaster house.

## Design Goals

- Keep one recognizable tower: stone shaft, lantern room, fire on top, water-facing.
- Grow the *base* first. Do not turn the lighthouse into a church or a house with a stick on top.
- As the tower gets larger, it should get rounder. A starter 3×3 can stay square; later tiers should read as a real lighthouse, not a bigger cobble chimney.
- The fire stays operable from the ground. Portmasters already toggle the campfire without climbing.
- A church / cathedral, if present, remains the tallest civic building. A Tier 4 lighthouse can be stout and tall, but the cathedral steeple should still win the skyline.

The current stand-in is `LIGHTHOUSE_BLUEPRINT`: four 3×3 stone levels, four 1×1 shaft levels, campfire cap. About `9` blocks tall.

## Shared Plan

- Marker / foundation cell stays the center of the lowest solid layer.
- Door on the land side, lantern and windows on the water side.
- Ladder or stair core so a player can still climb; villagers should not need to.
- `K` campfire on the cap at every tier.
- Wall lights on the public faces so the tower reads at night even with the fire out.

## Tier 1 — Harbor light

The current tower, cleaned up only as needed.

| | |
| --- | --- |
| Footprint | 3×3 square |
| Height | 9, including campfire |
| Interior | Climb shaft only |
| Bedroom | None |
| Materials | `M` cobble / local stone, `K` campfire, a few `T` wall lights |

This is the unattended-village starter: a dock plus population 6 already wants one. A 3×3 cannot read as a circle; square is correct at this size.

## Tier 2 — Wider light

A little larger. Same tower language, easier to stand in, still no house.

| | |
| --- | --- |
| Footprint | 5×5 base with cut corners / octagonal drum, 3×3 shaft above |
| Height | 11–12 |
| Interior | Ground-floor stand room around the ladder, one chest |
| Bedroom | None |
| Materials | Thicker `M` walls, more `V` lantern-room windows, hanging or wall lights |

Look: the village got a real lighthouse, not a cobble chimney. Chamfer the 5×5 so the silhouette starts to round.

## Tier 3 — Staffed light

Larger still, with a ground-floor bedroom. A Portmaster *may* use that bed; they must not be homeless if it is occupied.

| | |
| --- | --- |
| Footprint | 7×7 more circular drum (octagon or circle approximation), 3×3 or 5×5 shaft |
| Height | 13–15 to the fire |
| Interior | Entry, ladder core, **one ground-floor bedroom** (1 bed, 1 chest, a window) |
| Bedroom | Required, on the ground floor, land-side, not in the climb shaft |
| Materials | Stone base, wood interior trim, copper / lantern language, more glass in the lantern room |

Look: a stout harbor drum, not a square keep. Corner cells should stay cut so the tower reads round from the water.

Keep the bedroom door off the entry, not as the only way up the tower.

## Tier 4 — Prestige harbor light

Largest harbor object short of a cathedral.

| | |
| --- | --- |
| Footprint | 9×9 roundest drum, generous circular lantern gallery |
| Height | 16–18. Do not pass a Tier 4 cathedral steeple (`20+`) |
| Interior | Entry, store / chart nook, ground-floor bedroom kept, gallery under the fire |
| Bedroom | Still one ground-floor bed, not a barracks |
| Materials | Dressed stone, brick-red or gold-language trim if the palette allows, lights all around the gallery |

Look: the largest, roundest harbor object. Ships should see it before they see houses. People looking for the village church should still find the steeple first.

## Construction And Upgrade Rules

- Start from the existing lighthouse marker / completed Tier 1 shaft.
- Upgrade the same site. Do not abandon a working fire to build a second tower beside it.
- Reserve expansion cells around the Tier 1 3×3 if the code can hold a growing footprint.
- Each upgrade should add both mass and roundness: thicken the drum, then cut or rebuild corners so the larger tower is less square than the one it replaces.
- Unattended harbor villages should still be able to start Tier 1 without a player-placed marker, as they do today once a dock exists.
- Later tiers follow civic tier, not a second Portmaster profession.

## What To Hand Over

For each tier: front, layer grids or a `P`-key snapshot, the fire cell, the door, and (from T3) the bed cells. A line of four towers on a creative shoreline is more useful than one giant gallery.