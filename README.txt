ParanoiaPlus - improved skeleton (updated 2025-10-07T12:07:06.978849Z)
=================================================================

This package is an improved, more "Swiss-watch" quality skeleton of ParanoiaPlus.
Major changes in this update:
 - Stamina module removed (per your request).
 - CoreModule improved: TPS monitor, helper methods.
 - ShadowModule improved: lifecycle, cooldowns, spawn checks; hooks for ProtocolLib usage.
 - MobsModule improved: spawn-replacement skeleton and controller placeholders.
 - FakeModule improved: Bot manager, tick loop, spawn/remove API.
 - MLModule improved: real in-plugin event collection and simple top-k whitelist computation (heuristic).
 - plugin.yml updated (stamina command removed).

Notes:
 - All heavy packet-level logic (ProtocolLib packet building/sending), A* navigation and real block placement must still be implemented.
 - MLModule implements a stable heuristic (Top-K) instead of a neural net; it is fast, deterministic and safe.
 - The code is organized for clarity and easy incremental implementation into production-grade behavior.

How to build:
 1. Open project in IntelliJ or run `mvn package` in the project root.
 2. The produced jar will be in target/ (standard Maven workflow).

Next steps I can do immediately (pick any):
 - Implement ProtocolLib-based Shadow spawn/despawn packets (ADD_PLAYER, NAMED_ENTITY_SPAWN, metadata, head rotation).
 - Implement A* pathfinding and navigation for FakeModule (async, with unstuck).
 - Implement freeze-on-look controllers for MobsModule using raytrace/FOV checks.
 - Harden MLModule with EMA smoothing and persistence to disk.


Further improvements added:
 - Commands for shadow/fake/pmob registered and implemented as skeletons.
 - MLModule persistence (emaCounts -> ml_counts.json) and smoothing.
 - FakeModule: getBotCount, improved bot FSM stub.
 - MobsModule: freeze-on-look helpers and spawn replacement marking.
 - NavUtils: placeholder for A* navigation.


Enhanced changes (protocol & placement):
 - ShadowModule: ProtocolLib packet flow skeleton implemented (ADD player -> spawn -> metadata -> head -> destroy).
 - FakeModule: placeBlockReal uses MLModule whitelist and rate-limiting; fires BlockPlaceEvent and respects hard-deny.
 - Note: ProtocolLib must be installed on server for Shadow to work; otherwise fallback logs used.


---

CI / GitHub Actions build

I added a GitHub Actions workflow at `.github/workflows/maven.yml`. When you push this repo to GitHub (branch `main`),
Actions will run Maven, build the plugin, and upload the built JAR as an artifact named `paranoia-plus-jar`.

Local build (if you prefer to build locally):

1. Ensure you have Maven and JDK 11 installed.
2. Run `./build.sh` (Unix) or `mvn clean package -DskipTests`.
3. The assembled JAR will be in `target/paranoiaplus-1.0.0-SNAPSHOT-jar-with-dependencies.jar`.

If you want, I can also (attempt to) build the JAR here and include it in a ZIP — but this environment may not have Maven/JDK available.
Using GitHub Actions is the most reliable approach and produces a downloadable artifact automatically.
