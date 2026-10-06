# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

SkyRecipes is a Fabric client mod for Minecraft that adds a SkyBlock recipe/item database (8,000+ items, 10 recipe categories) displayed through **Reliable Recipe Viewer (RRV)**, a third-party recipe-viewer mod this project plugs into. Data is sourced from the [NotEnoughUpdates Repository](https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO) (vendored read-only under `NotEnoughUpdates-REPO-master/`), compiled into a binary cache at runtime, and kept refreshed in the background.

## Build system

Uses **Stonecutter** (multi-Minecraft-version Gradle plugin) + **Loom**. Don't run plain `./gradlew build` from repo root for iteration — Stonecutter manages per-version subprojects under `versions/<mc_version>/`.

- `./gradlew :26.1:build` — build the active version (currently only `26.1` is configured in `stonecutter.gradle.kts`'s `releaseVersions`)
- `./gradlew :26.1:runClient` — launch a dev client (shared `run/` dir across versions)
- `./gradlew :26.1:compileJava` — quick compile check while iterating
- `./gradlew publishToAllPlatforms` — publish all release versions to Modrinth + CurseForge sequentially (requires `MODRINTH_TOKEN`/`CURSEFORGE_TOKEN` env vars; dry-runs without them)
- Central version/dependency config lives in `stonecutter.properties.toml` (mod version, per-MC-version dependency versions) and `gradle.properties` (Gradle-only options, publish IDs). Bump `mod.version` in `stonecutter.properties.toml` when changing anything user-facing.
- There is no test suite in this repo — verify changes by compiling and, for UI/recipe changes, running the dev client.

## Architecture

### Package layout (`com.github.kdgaming0.skyrecipes`)

- **`core/`** — engine layer, has no dependency on RRV or Minecraft rendering APIs beyond items/text:
  - `data/` — the binary data pipeline: `RuntimeDataManager` (orchestrates warm start from disk cache → background update via `RuntimeUpdateService` → `BinaryDataCompiler` which parses the NEU repo JSON into a compact MessagePack binary → `BinaryDataLoader`/`MmapUtil` memory-maps it back in). `CacheLayout` resolves all on-disk paths under `gameDir/skyrecipes`. `PipelineStatus` tracks pipeline state for UI.
  - `registry/` — `ItemRegistry` and `ConstantsRegistry`, the in-memory lookup structures built from the loaded binary; both are god objects referenced from almost everywhere (recipe parsers, search, family resolution, mob preview).
  - `model/` — data classes for items, recipes, rarity, categories (`NeuItem`, `SkyblockRarity`, `SkyblockItemCategory`, `model/garden/` for garden mutations).
  - `recipe/parsers/`, `recipe/generators/`, `recipe/builders/` — turn raw NEU JSON fields into typed recipe records (one parser/generator per recipe category: crafting, forge, drops, npc shop, kat upgrade, trade, reforge, essence upgrade, info/wiki, garden mutation).
  - `search/` — the search index and query language (`SkyblockSearchIndex`, `SearchQueryParser`, clause types for keyword/phrase/regex/stat/rarity/type filters), plus `SearchAliases` and `SearchAutocomplete` for the ghost-text suggestions.
  - `family/` — groups tiered items (pet tiers, minion tiers, dungeon stars, accessory upgrades) so recipe lookups can expand across a whole family.
  - `hypixel/` — fetches/caches live Hypixel API item data (`HypixelItemsFetcher`, `HypixelItemsRegistry`, `HypixelItemsCache`) used to enrich NEU data (e.g. pet stats via `PetStatResolver`).
  - `mob/`, `render/` — mob preview rendering (NPC/pet 3D preview widget) and item render helpers.
  - `util/` — shared parsing/formatting helpers (`RarityExtractor`, `StatParser`, `PetStatResolver`, `SkyRecipesExecutors` for the shared thread/executor pools).

- **`rrv/`** — the integration layer against RRV's plugin API:
  - `plugin/SkyRecipesPlugin` and `plugin/SkyRecipesClientPlugin` are the two RRV entrypoints declared in `fabric.mod.json` (`rrv` and `rrv_client`). This is where recipe types get registered with RRV.
  - `recipe/type/Skyblock*RecipeType.java` — one `AbstractSkyblockRecipeType` subclass per recipe category, each defining its RRV slot layout (`SlotDefinition`).
  - `recipe/client/` — the widget/UI side: one client recipe class per category rendering the actual recipe-view screen (ingredients, costs, animations, etc.), all extending `AbstractSkyblockClientRecipe`.
  - `recipe/AbstractSkyblockClientRecipe.java` / `AbstractSkyblockRecipeType.java` — shared base classes bridging `core/` data into RRV's `ReliableClientRecipe`/`ReliableClientRecipeType` interfaces.
  - `recipe/SkyblockRecipeCache.java`, `StackGroupItemsCache.java` — caches used when building/serving recipes to RRV.

- **`mixin/`** — Fabric Mixins into Minecraft and RRV screens, split by target: `accessor/` (accessor/invoker mixins exposing private fields/methods), `overlay/` (RRV item-list/side-panel overlay mixins), `recipe/` (recipe view screen mixins), `rrv/` (other RRV screen mixins — category buttons, null-item guards), `skyblocker/` (interop with the Skyblocker mod, e.g. garden plots widget). `mixin/SkyRecipesMixinPlugin.java` is the `IMixinConfigPlugin` controlling conditional mixin application; mixin targets/config are declared in `src/main/resources/skyrecipes.mixins.json`.

- **`client/`** — `command/` (the `/skyrecipes` client command), `config/` (`SkyRecipesConfig` via MidnightLib, editable live through Mod Menu), `gui/` (standalone screens not tied to an RRV recipe view).

- **`SkyRecipes.java`** — the `ClientModInitializer` entrypoint. Owns the singleton `RuntimeDataManager`/`CacheLayout`/`SearchAutocomplete`, exposes static accessors (`getItemRegistry()`, `getConstantsRegistry()`, `isDataReady()`), and a listener mechanism (`addDataReadyListener`) other code uses to react when data finishes (re)loading — important since data loads asynchronously and much of the mod must tolerate a not-yet-ready state.

### Data flow

NEU repo JSON → `BinaryDataCompiler` → binary cache (MessagePack, mmap'd) → `ItemRegistry`/`ConstantsRegistry` → `core/recipe` parsers/generators produce typed recipe objects → `rrv/recipe/type` + `rrv/recipe/client` expose them to RRV as recipe categories → mixins adjust RRV/vanilla screens to integrate search, category filters, and overlays around them.

### Adding a new recipe category

Requires four coordinated pieces: a parser/generator in `core/recipe/`, an `AbstractSkyblockRecipeType` subclass in `rrv/recipe/type/`, an `AbstractSkyblockClientRecipe` subclass (widget) in `rrv/recipe/client/`, and registration in `rrv/plugin/SkyRecipesPlugin`/`SkyRecipesClientPlugin`. `vv_rrv_docs/` (vendored RRV docs) has the upstream tutorial for the RRV-plugin side of this (`docs/mods/client-recipes.mdx`, `client-recipe-type.mdx`, `finalizing-your-plugins.mdx`).


### Non-obvious contracts

- **No plain classes in the mixin package**: a non-mixin helper under `com.github.kdgaming0.skyrecipes.mixin.*` compiles fine but fails at runtime ("Mixin transformation of ... failed") once an injected handler references it. Put helpers called from handlers under `rrv/` (e.g. `rrv/overlay/`) and make them public. Compile success proves nothing here; check `run/logs/latest.log` for the mod's "integration disabled" warnings when a mixin feature does nothing.
- **Mojang mappings, no remap**: the mod ships against Mojang names with no intermediary and no refmap, so `remap = false` is correct even for `@At(target = ...)` strings naming Minecraft methods. Injectors use `require = 0`, so a mis-targeted one is a silent no-op, not a startup error. Verify every new target against the bytecode.
- **Skyblocker nested jars are off the compile classpath**: `modCompileOnly` Skyblocker gives `de.hysky.skyblocker.*` only, not its JiJ'd libs (e.g. `io.github.moulberry.repo.*`). Guard without naming those types (MixinExtras `@WrapMethod` + `try/catch`, or a Skyblocker signature that only uses vanilla types).
- **Data loads asynchronously**: anything touching `ItemRegistry`/`ConstantsRegistry` must tolerate a not-yet-ready state; use `SkyRecipes.isDataReady()` / `addDataReadyListener`.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).

## Verifying APIs

Your training data may predate MC 26.x and the current versions of RRV and Skyblocker. Don't rely on memory for Minecraft, Fabric or third-party class names, signatures, mapping names or injection targets. Verify them against the actual bytecode or source (`javap` on the Loom Minecraft jar or the dependency jar in `~/.gradle/caches`). For compat work, check the version I actually run, not just the `modCompileOnly` pin.

RRV has a real sources jar under the `cc.cassian.rrv` Gradle coordinate (version from `deps.rrv_version` in `stonecutter.properties.toml`). Read that instead of decompiling, and ignore the stale older copy under `maven.modrinth`.

# Workflow

Investigate → plan → get approval → implement → validate → document. Follow the `work-on-problem` skill; this file adds only the project specifics.

## When to keep going and when to stop

- **Before approval**: stop after the plan and wait. No code before I approve. If a requirement is unclear, ask instead of assuming.
- **After approval**: when a step doesn't need my input, keep going. Put status notes in the same message as your next action. Don't stop just to report progress or offer to continue.
- **Stop and ask** when you can't continue without me, when the approved plan turns out to be wrong or would change existing behavior, or before anything destructive: deleting files or data outside the plan, any `git push` or history rewrite, or writing anywhere outside this repository. Reading outside the repo (e.g. launcher profiles to find the mod jars I run) is fine.
- If an investigation splits into independent tasks, suggest parallel sub-agents only when it clearly speeds things up or improves quality.

For work with many steps, keep a checklist in `TASKS.md`, tick items as you finish them, and add anything new you find. Delete it once the task is documented.

## Validate

Run `./gradlew build` before considering the work complete and report the real result. It is the only automated gate (there is no test suite).

Most UI/recipe behavior can only be verified in game. When it is, describe what to test, how to reproduce it, and the expected result, then wait for me to report back before documenting.

## Reporting

End every run with these headings, omitting any that are empty:

- **Needs from you**: decisions, approvals, in-game tests to run
- **Changed**: each file touched and what the change does, in plain language
- **Found**: findings, surprises, and anything you couldn't confirm (say where you looked)

## Documentation

After testing has passed:

- Add a concise entry to `IMPLEMENTATION_LOG.md`: what changed, why, and notable decisions. Keep the entry short.
- Run `graphify update .`.
- If the work fixes a bug or adds a user-visible feature, bump `mod.version` and add a short, non-technical entry to `CHANGELOG.md` for end users, with no implementation details or internal terms.
- Update this file only if a convention or contract changed.

## Principles

- **Scope**: only modify files needed for the requested change. No unrelated formatting, refactoring or reorganization unless asked or required to complete the task safely.
- **Preserve behavior**: unless the request explicitly changes it, keep existing behavior. If a change or trade-off is needed, raise it in the plan and wait for approval.
