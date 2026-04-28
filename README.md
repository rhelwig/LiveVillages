# Live Villages

Live Villages is a Fabric mod for Minecraft Java that makes settlements feel more self-directed. Villages, harbors, and hostile outposts are meant to have their own needs, projects, stored goods, and trade routes, while the player nudges those systems by building infrastructure and changing incentives.

The project currently targets Minecraft Java `26.1.1`, Fabric Loader `0.18.6`, Fabric API `0.145.3+26.1.1`, and Java `25`.

## What It Adds

- Settlement-level simulation for population, stock, wealth, housing, comfort, security, projects, and routes.
- A Trade Board block for settlement status, shortages, surpluses, project summaries, settlement-backed trading, and a future in-world `Trading Post` anchor.
- Trademaster and Carpenter villager professions tied to the Trade Board and Carpenter's Bench.
- Persistent settlement and route state so villages can continue progressing through abstract updates instead of only through loaded villager behavior.
- Route and economy foundations for land trade, water trade, settlement growth, construction queues, hostile outpost pressure, and settlement inspection tooling.

## How It Plays

Live Villages is designed around influence rather than micromanagement. Instead of telling each villager what to carry or craft, the player helps a settlement by improving its conditions: adding workstations, strengthening food supply, building housing, improving roads, making docks, defending trade corridors, or resolving shortages through trade. Placed workstations are meant to become anchors for real profession buildings rather than staying as isolated utility blocks.

The Trade Board is the main civic block for this loop. It is where a settlement exposes what it needs, what it has too much of, which routes and projects matter, and how the player can trade against the settlement's shared stock.

## Current Status

This repository is in active development. The implemented foundation includes the mod entrypoints, Trade Board presentation, a first-pass functional Carpenter's Bench, loaded-settlement Carpenter promotion and workshop-bed priority, custom villager professions, Trade Board UI/data plumbing, saved settlement data, economy rules, construction projects, route state, scheduler support, and village autodetection.

The full design and implementation specification lives in [SPECS.md](SPECS.md).

## Development

Build with the Gradle wrapper:

```sh
./gradlew build
```

Run a development client with:

```sh
./gradlew runClient
```
