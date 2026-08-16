# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

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

## graphify

This project has a knowledge graph at `graphify-out/` with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when `graphify-out/graph.json` exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than `GRAPH_REPORT.md` or raw grep output.
- If `graphify-out/wiki/index.md` exists, use it for broad navigation instead of raw source browsing.
- Read `graphify-out/GRAPH_REPORT.md` only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).

# Development Workflow

## Documentation

- `AGENTS.md`: Project architecture and design overview.
- `IMPLEMENTATION_LOG.md`: Record of implementation decisions and reasoning. Keep entries concise (maximum 500 lines total).

## Workflow

### 1. Investigate First

Before writing any code, investigate the requested feature or reported issue.

- If any requirement is unclear, **stop and ask for clarification** before proceeding. Do not make assumptions. It is better to clarify early than to implement the wrong solution.
- Explain your findings briefly after the investigation.
- If the investigation naturally splits into independent tasks (for example, tracing a rendering bug, auditing mixin order, and checking overlay lifecycle), suggest running parallel sub-agents, with one agent handling each independent task. Only suggest this when it provides a meaningful speed or quality improvement.

### 2. Present a Plan

After completing the investigation:

- Present a short implementation plan or specification.
- Explain how you intend to solve the problem.
- Wait for explicit approval before writing any code.

### 3. Implement

Once approval has been given:

- Implement the planned changes.
- Keep the implementation focused on the approved scope.
- Avoid unrelated refactoring unless it is required to complete the task safely.

### 4. Validate

Before considering the work complete:

- Run all relevant tests.
- Run:

```bash
./gradlew build
```

to ensure the project builds successfully and all automated checks pass.

If manual in-game testing is required, clearly describe:

- what should be tested
- how to reproduce it
- what the expected result is

Wait for the user to complete manual testing and report back with the results before finalizing the task.

### 5. Documentation

After all testing has passed:

- Add a concise entry to `IMPLEMENTATION_LOG.md` describing:
    - what changed
    - why the change was made
    - any notable implementation decisions

- Run:

```bash
graphify update .
```

to keep the project graph up to date.

- If the work fixes a bug or introduces a user-visible feature, update `CHANGELOG.md` with a short, non-technical description suitable for end users. Avoid implementation details and internal terminology.

## General Principles

- Investigate before implementing.
- Ask for clarification instead of assuming.
- Do not write code before approval.
- Validate all changes before considering the task complete.
- Keep documentation up to date with every completed change.

### Scope Control

Only modify files that are necessary for the requested change. Avoid unrelated formatting changes, refactoring, or file reorganizations unless explicitly requested or required to complete the task safely.

### Preserve Existing Behavior

Unless the request explicitly changes existing functionality, preserve current behavior. If a proposed implementation requires changing existing behavior or introduces trade-offs, explain them in the implementation plan and wait for approval.
