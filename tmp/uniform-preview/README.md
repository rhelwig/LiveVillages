# Uniform redesign preview

Design study only. These portraits are **not** in-game textures.

Open [`index.html`](index.html) in a browser. It is one large infographic:
24 jobs × 4 civic tiers on the same villager body.

## What you are looking at

- **Tier 1** founding workwear: rough, muted, patched.
- **Tier 2** established village: cleaner cloth, cobble-gray trim, brass.
- **Tier 3** dressed civic: tailored cuts, silver-stone and copper.
- **Tier 4** prestige: gold embroidery, brick-red lining, ceremonial version of the same job.

Jobs are grouped as food & land, wood & stone, craft & arms, and civic.
Unemployed villagers, Nitwits, and children are omitted on purpose.

Preview tweaks after review:

- Farmer T2 is now solid and intact, not torn.
- Butcher, Beekeeper, and Carpenter T3 use a standing dress-collar so they no longer read as T2 plus a metal apron frame.
- Fletcher now wears a forward-pointed green forester's cap with a red feather; its finish advances from patched Tier 1 felt to restrained gold Tier 4 trim.
- All in-game Tier 1 overlays now translate the founding-workwear concepts into readable patches, repair stitching, abrasion, frayed hems, and job-specific flour, soil, soot, salt, or leather wear. `tier1-wear-reference.png` records the enlarged reusable material direction.

## Known preview defects

- The Portmaster Tier 1 portrait drifted onto a dock background. Later tiers
  were pulled back toward the parchment studio.
- These are full-body concept renders. In-game overlays are still UV atlases.
  128 and 256 sheets now exist for play, but they cannot carry every prestige
  stitch from these portraits.
- Outpost illagers have the same four-tier treatment in this preview.
- A few prestige pieces (especially Guard T4) read a little more
  ceremonial than the in-game model can carry. Treat them as direction,
  not pixel-ready art.

## Files

- `index.html` — labeled matrix
- `portraits/` — 96 job-tier stills plus the nude-robe base
