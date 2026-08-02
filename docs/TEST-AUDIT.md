# Code and Specification Test Audit

This audit covers the implementation present in July 2026. It checks current behavior against the existing specifications; it does not add planned features.

## Automated coverage

The plain-JUnit suite contains 69 tests in 18 test classes and covers:

- persistent codecs, defaults, immutable state updates, region math, and overflow boundaries
- settlement tiers, Scribe persistence, economy scaling, refining atomicity, trade range, and Trade Board valuation
- lighting recipe/discovery knowledge, semantic fixture selection, construction accounting, and Roadwright light spacing
- horizontal loaded-villager census distance and settlement-removal cascade behavior

`./gradlew test` passes. Minecraft registry bootstrap is centralized in `MinecraftBootstrapTestSupport`.

## Specification findings

- Tier thresholds, Bakery gates, worker scaling, refining fuel order, Carpenter/Fletcher recipe catalogs, trade range bonuses, and Trade Board bundle/value rules match their current specifications.
- The deep-miner census rule is implemented using horizontal distance; a regression test now protects it.
- Successful Trade Board donations and trades refresh build-site material status before rebuilding the view. This matches the immediate `Need` refresh requirement.
- Loaded Beekeepers now produce honey or honeycomb only from an actual ready, smoked hive or nest. The previous fallback contradicted the apiary specification and was removed.
- Settlement deletion now removes dependent routes, construction state, Scribe ledgers, saved survey/roadwork state, villager assignments and homes, and transient per-settlement bookkeeping.

## Performance findings

Completed during this audit:

- Background village discovery examines only already-loaded chunks; it no longer creates chunk tickets or generates terrain while advancing its cursor.
- Loaded villager observation uses a bounded settlement AABB rather than starting with a dimension-wide entity query.
- Short-lived villager and expensive Roadwright POI caches are capped, expired, and given a useful reuse interval.

Deferred loaded-world work:

- Begin construction time budgeting before full build-site reconciliation and resume reconciliation from a cursor.
- Phase defense maintenance across settlements instead of processing all settlements on the same 20-tick boundary.
- Replace the Forester's nested candidate search with a bounded cursor or cached candidate pool.
- Consolidate remaining profession workstation searches behind the existing batched discovery result.
- Add explicit bounds/expiry to the remaining static worker and roadwork caches.

## Test boundary

Plain JUnit is appropriate for deterministic rules, accounting, transformations, and codecs. Item component binding, real inventories, entity AI/pathfinding, block placement, chunk lifecycle, and full save/reload behavior require Fabric game tests or an integration-test world. Those should be added incrementally around defects found during playtesting rather than mocked into brittle unit tests.
