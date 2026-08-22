# Live Villages Version Notes

This document tracks short, user-facing notes for releases after the initial published version. Keep the newest release first and write for players rather than developers. These notes can be adapted for Modrinth, CurseForge, GitHub releases, and announcement posts.

## 1.0.3 — Upcoming

Live Villages `1.0.3` focuses on making settlement growth more dependable, visible, and easier on the server.

### What’s new

- Settlements now learn and use a wider range of lighting as they grow, including tier-aware lights for interiors, storefronts, farms, roads, docks, and guard spaces.
- Scribes can preserve discoveries such as Glow Berries, Shroomlights, and Froglights and gradually share them along supported trade routes.
- Eligible farms can gain Jack o’Lantern corner lights, docks can gain Sea Lanterns, and Roadwrights can add properly spaced roadside lights.
- Structure lighting choices are saved when construction begins, so an unfinished building does not unexpectedly change design midway through the job.

### Construction and settlement fixes

- Active settlements can now reclaim generated vanilla village buildings gradually, with up to six workers renovating at once. Workers remove cobwebs first; when housing is short, they prioritize incomplete doors and restore only beds that belonged to the intact vanilla building, after verifying support, overhead clearance, and walking access. Donated beds are used directly before crafting replacements. Workers then use each abandoned building's intact vanilla template to replace missing safety-related walls, roofs, stairs, slabs, fences, doors, and window panes as materials become available, and retill damaged vanilla farm plots. Renovation never overwrites solid player changes.
- Finished buildings now keep their completed state on the Info screen. Leftover foundation fill, optional church tiles, and later blueprint edits no longer leave a standing structure stuck at 99%.
- Copper stairs and waxed copper stairs are now craftable (vanilla only has cut copper stairs). Church tower roofs use the waxed ones.
- Emergency/simple housing and larger autonomous housing shelters are now built progressively instead of appearing instantly.
- Structures can build dirt foundations across modest slopes, while still rejecting sites with deep unsupported gaps.
- Build previews, construction material accounting, and support checks have been improved for the new lighting and foundation plans.
- Settlement deletion now cleans up its routes, construction records, assignments, homes, Scribe knowledge, and other dependent saved data more completely.
- Background village discovery no longer loads or generates chunks merely to inspect them.

### Balance, reliability, and performance

- Worker productivity is now a world setting. The default stays `2.0` (twice the original design baseline), and it can be changed in the world game-rules screen or with `/gamerule live-villages:worker_productivity`. The allowed range is now `0.25` to `50.0`.
- Villager, zombie villager, and illager skins can now use `64`, `128`, or `256` pixel sheets. The world game rule is `live-villages:villager_texture_scale`, with a cycling world-setting button and matching command autocomplete choices. Higher scales keep the Minecraft look while making job marks easier to read, and new worlds default to `256` for the strongest presentation.
- All founding-tier profession uniforms now show clearer role-specific wear, repairs, grime, and frayed hems: flour for Bakers, soil for field workers, soot and ore dust for heavy trades, salt fading for maritime work, and darker leather repairs for martial and craft roles. Fletchers now wear a forward-pointed green cap with a red feather across all civic tiers.
- One wool can now be crafted into four string. Settlement stock can spin wool the same way when string is needed for slings, candles, or loom work, which helps Peaceful worlds that never see spiders.
- Settlements now keep stairs, slabs, and other parts needed by unfinished buildings instead of offering them as Trade Board surplus.
- Emergency beds prefer a planned indoor bed spot, including finishing the floor under that spot when materials allow, instead of dropping beds on an unfinished trade-post floor or porch.
- Beekeepers only produce hive goods after a real successful harvest.
- Loaded villager observation and several short-lived simulation caches are now more tightly bounded.
- Negative-coordinate region calculations and extreme support-score calculations have been corrected.
- Trade Board values and material handling now recognize the expanded lighting resources and finished fixtures.

### Testing

- Added a broad automated regression suite covering settlement persistence, economy and tier rules, trade values, refining, lighting knowledge and selection, route-light spacing, census behavior, and settlement cleanup.

### Compatibility

- Fabric for Minecraft `26.1.1`.
- Existing worlds remain supported. As always, back up important worlds before updating a gameplay mod.

## 1.0.0 — Initial release

- First public release of Live Villages.
- Added persistent settlements with shared needs, stock, projects, professions, staged construction, trade routes, harbors, guards, bakeries, and early outpost systems.
