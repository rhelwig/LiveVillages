# Known Issues

This file is the temporary issue tracker for the project until repository-backed issue tracking is available.

## Trade Board Display

Status: Open

Summary:
The Trade Board item texture is working, but the placed block still does not visually match the intended oak sign look.

Current expected behavior:

- The placed Trade Board should look like an oak sign with two side posts.
- The posts should use the darker support texture.
- The board surface should use the lighter sign-board texture.

Current actual behavior:

- The placed Trade Board shape is correct.
- The previous transparency/"hole in the ground" artifact was fixed.
- The board surface still appears too dark in-game and looks unchanged after texture/model adjustments.

Relevant files:

- `src/main/resources/assets/live-villages/models/block/trade_board.json`
- `src/main/resources/assets/live-villages/textures/block/trade_board_face.png`
- `src/main/resources/assets/live-villages/textures/block/trade_board_support.png`
- `src/main/java/com/ronhelwig/livevillages/block/TradeBoardBlock.java`

What has already been tried:

- Added proper item definition under `assets/live-villages/items/trade_board.json`.
- Added local item and block textures under `assets/live-villages/textures/...`.
- Reworked the block model from one center post to two side posts.
- Fixed block occlusion/collision behavior so the support block no longer renders as a transparent hole.
- Repointed the board faces to the local `trade_board_face.png`.
- Added explicit UVs for the board faces.
- Replaced `trade_board_face.png` with a crop from the vanilla oak sign entity texture.
- Disabled per-element shading in the block model.

Observed result after those changes:

- The placed block still appears darker than expected.
- East-west placement now looks correct to the user.
- North-south placement still appears too dark.
- Near sunset, more detail becomes visible, which suggests a lighting/shading interaction in addition to orientation.
- In a later test, a too-dark Trade Board had two nearby torches. Breaking one torch caused the board to immediately render correctly. Replacing the torch did not reintroduce the problem, which suggests the visuals may have been correct already but stuck until a local lighting update forced a refresh.

Likely next debugging angles:

- Verify whether the placed model is actually the JSON model being rendered, rather than some sign-style special-case rendering path.
- Re-check the in-game loaded asset contents after a full client restart, not only a resource reload.
- Try an exaggerated test texture for `trade_board_face.png` to confirm whether the face texture is being sampled at all.
- If the face texture is being sampled, revisit the model proportions/UVs with a deliberately asymmetric texture to make orientation differences obvious.
- Inspect whether face culling, light sampling, or element-depth choices on the thin board panel are causing direction-dependent darkening.
- Specifically test whether block-light or relight updates near the board clear the issue without any asset changes, to determine whether this was a stale lighting state rather than a remaining model or texture bug.

Notes:

- This issue is cosmetic only and has been parked temporarily while simulation behavior is being tested.
- Keep tracking it for now in case the lighting-triggered recovery was only masking an intermittent regression.

## Portmaster Anchor Texture

Status: Open

Summary:
The `Portmaster's Anchor` workstation is visible and usable, but its current model/textures are placeholder-quality and render with missing-texture style magenta/black surfaces in playtests.

Current expected behavior:

- The `Portmaster's Anchor` should have a readable anchor-themed appearance.
- The workstation should remain visually distinct from other wood tables or boards.

Current actual behavior:

- The block is distinct and placeable.
- The current model is acceptable as a temporary silhouette only.
- In playtesting it shows missing-texture coloration on part or all of the block.

Relevant files:

- `src/main/resources/assets/live-villages/models/block/portmaster_anchor.json`
- `src/main/resources/assets/live-villages/blockstates/portmaster_anchor.json`
- `src/main/resources/assets/live-villages/items/portmaster_anchor.json`

What has already been tried:

- Added a simple custom block model using vanilla wood/metal texture references.
- Kept the silhouette intentionally simple so the workstation was at least usable for gameplay testing.

Likely next debugging angles:

- Confirm which texture reference in the model is resolving to missing-texture output.
- Decide whether to keep using vanilla texture references or add explicit local textures for the anchor.
- Revisit the silhouette and orientation once the dock-placement behavior is settled in play.

Notes:

- This issue is cosmetic only for now and has been deferred while workstation placement behavior is being tested.

## Housing Shelter Stacking And Access

Status: Open

Summary:
Simple housing fallback placement was able to add inaccessible upper floors onto existing shelter towers, and those fallback shelters did not include doors.

Current expected behavior:

- Starter housing shelters should build from stable ground, not on top of previous shelter roofs or shallow artificial platforms.
- Even the minimal fallback shelter should have a usable ground-level door.
- Housing should not create inaccessible stacked floors unless a future dedicated multi-story structure type explicitly supports stairs and access.

Current actual behavior:

- A playtest settlement produced third- and fourth-floor shelter additions on an existing tower-like structure.
- The newly added fallback floor had no door.

Relevant files:

- `src/main/java/com/ronhelwig/livevillages/sim/SettlementConstruction.java`
- `SPECS.md`

What has already been tried:

- Added ground-level door placement to the main shelter layout.
- Added another code pass that gives the simple fallback shelter a door and front footprint.
- Tightened both shelter site-selection paths to require more stable terrain-like support instead of treating shallow artificial platforms as valid building ground.
- Lowered the simple shelter's center roof lane by replacing the door-to-bed roof strip with top slabs and removing the low full-block ceiling directly over the bed path.

Notes:

- Existing stacked shelters already present in a world will remain until rebuilt or removed.
- Await the next in-world playtest before deciding whether this issue can be closed or whether housing needs a more explicit bunkhouse/motel structure type.

## Construction Worker Congestion And Roof Stranding

Status: Open

Summary:
Loaded villagers can crowd each other in tight staged-structure spaces, and at least one Trademaster was observed standing on a workshop roof during construction.

Current expected behavior:

- Workers should be able to approach build tasks without repeatedly running into each other in narrow doorways or work bays.
- Villagers should not get stranded on roofs after roof work, material shortages, or pathfinding detours.
- Construction stand positions should favor reachable ground or interior positions when possible.

Current actual behavior:

- Workers sometimes appear to collide or stall in tight spaces while multiple villagers are assigned to one structure.
- A Trademaster was observed on top of the Roadwright/Carpenter-style roof during construction.

Relevant files:

- `src/main/java/com/ronhelwig/livevillages/sim/SettlementConstructionWork.java`
- `src/main/java/com/ronhelwig/livevillages/sim/SettlementRoadwrightWork.java`

What has already been tried:

- Construction stand positions are already clamped down near the structure origin instead of directly targeting roof height.
- Wall-torch placement now waits until the support wall exists so workers do not attempt unsupported block placement.
- Roadwright work now reserves busy Roadwrights away from generic construction while internal path work is available, reducing some task competition.
- Carrying workers now pick reachable nearby stand positions for the actual villager instead of always aiming at the target block column, which could be inside a wall or under a roof edge.
- Active deliveries now time out and return their carried item to stock if the worker remains unable to finish after a short loaded-world interval.
- Construction stock visits now navigate to an adjacent standable access tile beside the Trade Board / Trading Post workstation instead of targeting the Trade Board block itself.
- The Trading Post blueprint now adds a stair cap above the Trade Board to discourage villagers from using the board top as a path surface.
- The initial stair-cap attempt was one level too low; it has been moved up into the soffit/roof layer and widened to three stairs across the front.
- Construction deliveries held by unavailable workers or workers reserved for higher-priority Roadwright work now return their carried item to stock and release the claimed build-site block so another worker can continue the structure.
- Build-site preview assisted placement now highlights matching held materials and places the planned block state server-side, reducing the chance that player help creates wrong-orientation stalls.
- Construction workers now pick up batched deliveries of up to `32` matching materials and continue to the next matching planned block after each placement.
- Construction delivery cleanup now returns unused batch remainders to stock and persists completed delivery removals, so worker or player placement should not leave stale claims on already handled blocks.

Likely next debugging angles:

- Watch whether reachable stand-position selection fixes the spinning/carrying case in play. If not, add per-task approach offsets so multiple villagers do not use the same stand position.
- Add a bounded unstuck/return-to-ground behavior for villagers detected on completed or unreachable roof surfaces.
- Consider limiting simultaneous workers per small structure or per layer when the target area is narrow.
- Watch whether villagers still try to enter or leave structures by climbing workstations, fences, benches, or other one-block obstructions; if so, add explicit outside-door/gate access points and anti-climb caps to the affected blueprints.

## Roadwright Greenspace And Server Tick Budget

Status: Watching

Summary:
Roadwrights were observed converting too much loaded village terrain into paths, and loaded-world simulation showed severe server lag during playtesting.

Current expected behavior:

- Roadwrights should connect meaningful destinations and repair real path corridors without flood-filling adjacent greenspace.
- Terrain-changing workers should preserve grass, flowers, saplings, and enough trees for the village to look alive.
- Loaded-world scan and planning work should be cached, bounded, and measurable so villager work does not stall server ticks.

Current actual behavior:

- A playtest showed the village interior dominated by paths with too little greenspace.
- Animals and villagers were nearly motionless during lag spikes.
- A player-cut log reappeared several seconds later, suggesting server-side block processing was badly delayed.

Relevant files:

- `src/main/java/com/ronhelwig/livevillages/sim/SettlementRoadwrightWork.java`
- `src/main/java/com/ronhelwig/livevillages/sim/SettlementForesterWork.java`
- `src/main/java/com/ronhelwig/livevillages/sim/SettlementGreenspace.java`
- `src/main/java/com/ronhelwig/livevillages/sim/SettlementPerformanceLog.java`
- `src/main/java/com/ronhelwig/livevillages/sim/LiveVillagesSavedData.java`
- `src/main/java/com/ronhelwig/livevillages/sim/LiveVillagesScheduler.java`

What has already been tried:

- Added shared greenspace checks for path conversion and tree cutting.
- Disabled broad Roadwright touch-up behavior that treated adjacent grass near paths as path work.
- Cached Roadwright and Forester task selection for short intervals.
- Cached shared stock-access lookup so deposit and carpentry work do not repeatedly rescan for Trade Boards.
- Reduced resource-maintenance frequency and kept construction/farming maintenance bounded to lower tick pressure.
- Added slow-operation timing logs around loaded farmer, resource, and construction maintenance.
- Added project guidance to prefer cached scans, bounded per-tick work queues, and measurable performance logs.
- Phased loaded farmer, resource, and construction maintenance by settlement so loaded settlements do not all run the same maintenance phase on one global tick.
- Added a shared villager work schedule with staggered daytime breaks and per-villager Decide throttles for farming, construction, roadwork, forestry, carpentry, and item pickup; active construction deliveries can still continue so supplies are not stranded.
- Re-enabled loaded resource maintenance under the new phasing and worker-duty gates so Forester, Carpenter, and item pickup behavior can run without every villager planning every pass.
- Added shared loaded observation caching for nearby villager scans and changed Surveyor map data to accumulate from bounded observations around ordinary villagers, center/build-site anchors, and on-demand Surveyor Table opens instead of full synchronous map-area scans.
- Changed construction task choice to scan past already placed early blueprint blocks while still limiting expensive stand-position checks to the first actionable candidates, which should prevent projects such as a Forester's hut from stalling after the floor is complete.
- Cached negative Roadwright planning results, capped loaded Roadwright helper planning per maintenance pass, and changed overlay Roadwright task labels to use fresh cached tasks instead of triggering path searches.
- Included observed villager positions in Surveyor map snapshots, preferring newest observations under the cap, so ordinary villager travel visibly clears fog even when no road or POI is found nearby.
- Capped Surveyor observation refreshes to a rotating villager subset and fixed scanned-column budget, and added a dedicated `loaded_surveyor_observation` slow-operation log with tick, center, and population context.
- Changed construction task selection so candidate blocks use cheap standable-position scoring first, then run expensive navigation path checks only for the final chosen block; this targets the repeated `standPosFor` / `chooseConstructionTask` spikes seen in `run/logs/latest.log`.
- Increased retained Surveyor observed-area points and map reveal radius so ordinary villager travel should uncover map fog more visibly while keeping the bounded observation scan.
- Increased Surveyor observation memory to `72,000` ticks so map data should not go stale after only half an in-game day.
- Increased Surveyor observation memory again to `144,000` ticks and doubled the retained observed-area cap so map coverage persists better across longer real-time playtests.
- Increased the Roadwright task cache duration while cached tasks remain useful, reducing repeated `chooseRoadworkTask` path searches that were still showing 100ms+ warnings in `run/logs/latest.log`.
- Changed loaded Roadwright execution so cached roadwork continues between Decide windows, and changed worker/map observability to read only cached active Roadwright plans instead of triggering fresh hypothetical route planning when the player opens a debug screen.
- Relaxed Roadwright greenspace vetoes so a chosen route corridor can still convert normal terrain near tree cover or village-edge woods, while direct flowers, saplings, and denser protected greenspace clusters remain protected.

Likely next debugging angles:

- Confirm the Forester Workshop resumes after the carried-log delivery claims are released from busy Roadwrights.
- First priority next performance session: confirm the current timing logs are easy to find, read, and correlate with villager phases before adding more refactors.
- Playtest with timing logs enabled and note which loaded maintenance operation reports slow ticks.
- Compare timing logs before and after the OODA scheduling pass, especially `loaded_construction`, `loaded_resource`, `loaded_farmer`, `chooseConstructionTask`, and Roadwork task selection.
- Check Surveyor map behavior in a loaded village: frequently traveled areas should populate first, while unvisited areas should no longer require a full square scan just to open the map.
- Replace any remaining expensive broad scans with persistent chunked work queues if logs still show stalls.
- Tune greenspace thresholds after observing whether paths, grass, flowers, and trees now balance correctly in real villages.

## Route, Construction, And Trade Coupling

Status: Watching

Summary:
Visible Roadwright path work, staged workstation construction, and abstract inter-settlement trade can all progress on different cadences, which makes the village feel less coherent than the player expects.

Current expected behavior:

- Once a Roadwright has chosen a meaningful route, the Surveyor map should keep that route visible until the current route segment is actually resolved, not only while the worker is in an active replan/action window.
- A player-placed `Trade Board` should lead fairly promptly to visible `Trading Post` construction when the settlement is loaded and materials are available.
- When two villages are already trading, the visible route-building story should broadly match the settlement/trade story instead of feeling like two disconnected systems.

Current actual behavior:

- Surveyor route traces were previously able to disappear during short task or observability gaps even when the Roadwright still had a meaningful path corridor to work.
- A custom village with a `Trade Board`, donated materials, and a nearby larger settlement was observed to build only the small fallback shelter for a long time while the `Trading Post` still had not visibly started.
- Route trading can happen abstractly on its own cadence even when visible Roadwright progress toward a Milepost or corridor feels slow.

Relevant files:

- `src/main/java/com/ronhelwig/livevillages/sim/SettlementRoadwrightWork.java`
- `src/main/java/com/ronhelwig/livevillages/sim/SettlementConstructionWork.java`
- `src/main/java/com/ronhelwig/livevillages/sim/LiveVillagesSavedData.java`
- `src/main/java/com/ronhelwig/livevillages/sim/SettlementEconomySimulator.java`
- `src/main/java/com/ronhelwig/livevillages/sim/RouteNetworkSimulator.java`

What has already been tried:

- Changed loaded Roadwright execution so cached roadwork continues between Decide windows instead of only during the replan window.
- Changed worker and map observability to stop inventing fresh hypothetical Roadwright plans when the player opens debug views.
- Added a longer-lived Surveyor route-trace memory so recently chosen Roadwright corridors remain visible across short pauses and breaks until that route segment is actually resolved.

Likely next debugging angles:

- Expose Trading Post build-site presence and per-site progress more clearly in the overlay so it is obvious whether the site exists, is blocked, or is simply starved of active worker time.
- Show whether the Roadwright is working an internal village route, a Milepost-guided route, or a settlement-to-settlement route, and whether that route is the same one contributing to abstract trade quality.
- Review whether early custom settlements are over-prioritizing housing fallback relative to the first `Trading Post` build site.
- Revisit route-trade cadence and throughput expectations; current route trade is still abstract and can feel too infrequent for close settlements.
