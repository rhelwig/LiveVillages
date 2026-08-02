# Live Villages Version Notes

This document tracks short, user-facing notes for releases after the initial published version. Keep the newest release first and write for players rather than developers. These notes can be adapted for Modrinth, CurseForge, GitHub releases, and announcement posts.

## 1.0.1 — Upcoming

Live Villages `1.0.1` focuses on making settlement growth more dependable, visible, and easier on the server.

### What’s new

- Settlements now learn and use a wider range of lighting as they grow, including tier-aware lights for interiors, storefronts, farms, roads, docks, and guard spaces.
- Scribes can preserve discoveries such as Glow Berries, Shroomlights, and Froglights and gradually share them along supported trade routes.
- Eligible farms can gain Jack o’Lantern corner lights, docks can gain Sea Lanterns, and Roadwrights can add properly spaced roadside lights.
- Structure lighting choices are saved when construction begins, so an unfinished building does not unexpectedly change design midway through the job.

### Construction and settlement fixes

- Emergency/simple housing and larger autonomous housing shelters are now built progressively instead of appearing instantly.
- Structures can build dirt foundations across modest slopes, while still rejecting sites with deep unsupported gaps.
- Build previews, construction material accounting, and support checks have been improved for the new lighting and foundation plans.
- Settlement deletion now cleans up its routes, construction records, assignments, homes, Scribe knowledge, and other dependent saved data more completely.
- Background village discovery no longer loads or generates chunks merely to inspect them.

### Balance, reliability, and performance

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
