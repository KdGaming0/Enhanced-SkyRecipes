package com.github.kdgaming0.skyrecipes.rrv.plugin;

import cc.cassian.rrv.api.ReliableRecipeViewerClientPlugin;
import cc.cassian.rrv.api.recipe.ItemView;
import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.client.recipe.ClientRecipeCache;
import cc.cassian.rrv.common.config.Configs;
import cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay;
import cc.cassian.rrv.common.overlay.itemlist.view.SearchBar;
import com.github.kdgaming0.skyrecipes.SkyRecipes;
import com.github.kdgaming0.skyrecipes.core.data.PipelineStatus;
import com.github.kdgaming0.skyrecipes.core.family.FamilyResolver;
import com.github.kdgaming0.skyrecipes.core.hypixel.HypixelItemsCache;
import com.github.kdgaming0.skyrecipes.core.hypixel.HypixelItemsFetcher;
import com.github.kdgaming0.skyrecipes.core.hypixel.HypixelItemsRegistry;
import com.github.kdgaming0.skyrecipes.core.hypixel.HypixelItemsSnapshot;
import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.recipe.RecipeGenerator;
import com.github.kdgaming0.skyrecipes.core.recipe.RecipeGenerator.RecipeResult;
import com.github.kdgaming0.skyrecipes.core.registry.ConstantsRegistry;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import com.github.kdgaming0.skyrecipes.core.render.item.ItemStackBuilder;
import com.github.kdgaming0.skyrecipes.core.search.SkyblockSearchIndex;
import com.github.kdgaming0.skyrecipes.core.util.TextUtil;
import com.github.kdgaming0.skyrecipes.mixin.accessor.EditBoxAccessor;
import com.github.kdgaming0.skyrecipes.mixin.accessor.ItemViewOverlayAccessor;
import com.github.kdgaming0.skyrecipes.rrv.recipe.SkyblockRecipeCache;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RRV client-side plugin entrypoint.
 *
 * <p>Uses internal cache injection for speed, with runtime discovery of RRV's
 * private API so the game never crashes on version mismatch — it degrades to
 * provider-only mode instead.</p>
 *
 * <p><b>Startup flow:</b></p>
 * <ol>
 *   <li>Recipe provider registered as fallback.</li>
 *   <li>NEU data loads in background (warm or cold start).</li>
 *   <li>On {@code CLIENT_STARTED}, a retry loop waits for data components to bind,
 *       then launches recipe generation and stack building in parallel.</li>
 *   <li>Once both complete, FamilyResolver and SkyblockRecipeCache are built on
 *       a background thread, then {@code beginBatchedInjection} is scheduled.</li>
 *   <li>Injection spreads RRV cache mutations across ticks (500 recipes / 250 stacks
 *       per tick) so no frame is dropped.</li>
 *   <li>On data reload, {@code invalidateCaches} resets pipeline state but preserves
 *       the live {@code searchIndex} so the item list stays visible during the gap.</li>
 * </ol>
 */
@SuppressWarnings("UnstableApiUsage")
public class SkyRecipesClientPlugin implements ReliableRecipeViewerClientPlugin {

    /**
     * Alias map exposed for {@link com.github.kdgaming0.skyrecipes.core.search.SearchAutocomplete}.
     */
    public static final Map<String, String> ALIASES;

    private static final Logger LOGGER = LoggerFactory.getLogger(SkyRecipesClientPlugin.class);

    /**
     * RRV's private handleClientRecipe, bound via reflection. Null = provider-only fallback.
     */
    private static final MethodHandle INJECT_RECIPE;
    private static final boolean DIRECT_INJECTION_AVAILABLE;
    private static final int RECIPES_PER_TICK = 500;
    private static final int STACKS_PER_TICK = 250;
    /**
     * Above this fraction of failures, a generation/build stage is treated as systemic and the cycle aborts.
     */
    private static final double MAX_FAILURE_RATE = 0.05;
    /**
     * How long generation waits for Hypixel stats before proceeding with fallback values.
     */
    private static final long HYPIXEL_WAIT_SECONDS = 5;
    private static volatile boolean recipesReady = false;
    /**
     * True from the injection commit point in {@link #beginBatchedInjection} until
     * {@link #finishStartup}. While set, RRV-initiated cache rebuilds are suppressed:
     * RRV's {@code clear()} scans every per-item bucket per entry (quadratic over the
     * partially injected recipes) and would silently drop everything injected so far —
     * the "first click on Hypixel" render-thread freeze.
     */
    private static volatile boolean injectionInProgress = false;

    // ---- Batched-injection constants ----------------------------------------
    /**
     * True only while SkyRecipes itself runs an intentional rebuild (provider-only
     * fallback). Lets that one call bypass the suppression in
     * {@code ClientRecipeCacheMixin}, which would otherwise cancel it because
     * {@code recipesReady} is already set at that point.
     */
    private static volatile boolean internalRebuildInProgress = false;
    private static volatile SkyblockSearchIndex searchIndex = null;

    static {
        MethodHandle h = null;
        try {
            java.lang.reflect.Method m = ClientRecipeCache.class.getDeclaredMethod(
                    "handleClientRecipe",
                    Identifier.class,
                    ReliableClientRecipe.class,
                    int.class,
                    boolean.class
            );
            m.setAccessible(true);
            h = MethodHandles.lookup().unreflect(m);
            LOGGER.debug("RRV internal API 'handleClientRecipe' bound successfully.");
        } catch (Exception e) {
            LOGGER.warn("SkyRecipes: RRV internal API 'handleClientRecipe' not found ({}). "
                            + "Falling back to provider-only mode. Recipe display may be delayed.",
                    e.getClass().getSimpleName());
        }
        INJECT_RECIPE = h;
        DIRECT_INJECTION_AVAILABLE = (h != null);
    }

    static {
        Map<String, String> map = new HashMap<>();
        map.put("aote", "ASPECT_OF_THE_END");
        map.put("aotv", "ASPECT_OF_THE_VOID");
        map.put("juju", "JUJU_SHORTBOW");
        map.put("livid", "LIVID_DAGGER");
        map.put("fs", "FLOWER_OF_TRUTH");
        map.put("yeti", "YETI_SWORD");
        map.put("term", "TERMINATOR");
        map.put("hype", "HYPERION");
        map.put("aotd", "ASPECT_OF_THE_DRAGON");
        map.put("bonemerang", "BONE_BOOMERANG");
        map.put("daed", "DAEDALUS_AXE");
        map.put("gdrag", "GOLDEN_DRAGON");
        map.put("edrag", "ENDER_DRAGON_PET");
        map.put("wither", "WITHER_SHIELD_SCROLL");
        map.put("sf", "SHADOW_FURY");
        map.put("valk", "VALKYRIE");
        map.put("astrea", "ASTREA");
        map.put("scs", "SCORPION_FOIL");
        map.put("spirit", "SPIRIT_SCEPTRE");
        map.put("giant", "GIANTS_SWORD");
        map.put("midas", "MIDAS_SWORD");
        map.put("pooch", "POOCH_SWORD");
        map.put("reef", "REEF_SCALES");
        map.put("rod", "SPEEDSTER_ROD");
        map.put("inferno", "INFERNO_ROD");
        map.put("hell", "HELLFIRE_ROD");
        map.put("soul", "SOUL_WHIP");
        map.put("wand", "WAND_OF_RESTORATION");
        map.put("ice", "ICE_SPRAY_WAND");
        map.put("plasma", "PLASMAFLUX_POWER_ORB");
        map.put("overflux", "OVERFLUX_POWER_ORB");
        map.put("manaflux", "MANAFLUX_POWER_ORB");
        map.put("rory", "RORY");
        map.put("boo", "BOO_STAFF");
        ALIASES = Collections.unmodifiableMap(map);
    }

    // ---- Per-instance volatile pipeline state -------------------------------

    /**
     * Guards {@link #maybeStartBackgroundPrep} so exactly one prep run launches
     * per pipeline cycle, even when recipe-gen and stack-build complete concurrently.
     */
    private final AtomicBoolean backgroundPrepLaunched = new AtomicBoolean(false);
    /**
     * Guards {@link #beginBatchedInjection} so it runs at most once per pipeline
     * cycle, even if {@code mc.execute} queues it twice.
     */
    private final AtomicBoolean injectionLaunched = new AtomicBoolean(false);
    @SuppressWarnings("unused")
    private volatile boolean stacksReady = false;
    @SuppressWarnings("unused")
    private volatile boolean startupFinalized = false;
    /**
     * True only for the very first injection; controls whether to clear the cache beforehand.
     */
    private volatile boolean firstInjection = true;
    private volatile RecipeResult cachedResult = null;
    @SuppressWarnings("unused")
    private volatile List<ItemStack> cachedStacks = null;
    private volatile CompletableFuture<?> pendingRecipeGen = null;
    private volatile CompletableFuture<?> pendingStackBuild = null;
    private volatile boolean clientStarted = false;
    private volatile boolean startupRetryInProgress = false;
    /**
     * Set when direct injection fails systemically at runtime; all later cycles use the provider fallback.
     */
    private volatile boolean directInjectionBroken = false;

    // ---- Coordinated background-completion state ----------------------------
    /**
     * True once any SkyRecipes entries may exist in RRV's cache (covers reloads that interrupt a running batcher).
     */
    private volatile boolean cacheHasSkyRecipesEntries = false;
    private volatile CompletableFuture<?> hypixelFetch = null;
    private volatile RecipeResult pendingRecipeResult = null;
    private volatile StackBuildResult pendingStackResult = null;
    private StartupBatcher startupBatcher = null;

    // ---- Search-bar suggestion state ----------------------------------------

    private boolean wasRightArrowDown = false;
    private boolean wasTabDown = false;

    // ---- Static helpers -----------------------------------------------------

    private static boolean areComponentsBound() {
        try {
            ItemStack test = new ItemStack(Items.DIAMOND);
            test.set(DataComponents.CUSTOM_NAME, Component.literal("test"));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static SkyblockSearchIndex getSearchIndex() {
        return searchIndex;
    }

    /**
     * Whether {@code ClientRecipeCacheMixin} should cancel an RRV-initiated
     * {@code buildRecipeCache} call.
     *
     * <p>Suppression covers two windows: after recipes are fully injected (a rebuild
     * would redundantly wipe and rebuild them on every lobby switch) and while a
     * batch is in flight (see {@link #injectionInProgress}). SkyRecipes' own
     * fallback rebuild bypasses the guard via {@link #runInternalRebuild()}.</p>
     */
    public static boolean shouldSuppressRrvRebuild() {
        return (recipesReady || injectionInProgress) && !internalRebuildInProgress;
    }

    /**
     * Whether {@code ClientRecipeCacheMixin} may skip the absent-element bucket
     * scans in RRV's {@code handleClientRecipe}.
     *
     * <p>Only true during direct batched injection, where the batcher passes its
     * global loop index as RRV's dedup id — every uniqueId is therefore distinct
     * within the batch, so a re-encountered id can only be the one this same call
     * just appended (i.e. at the bucket tail). The provider-fallback rebuild is
     * excluded: other mods' providers may produce colliding ids that need RRV's
     * full remove-before-add dedup.</p>
     */
    public static boolean isDirectInjectionInFlight() {
        return injectionInProgress && !internalRebuildInProgress;
    }

    /**
     * Runs RRV's {@code buildRecipeCache(false)} with the mixin guard bypassed.
     * Render thread only.
     */
    private static void runInternalRebuild() {
        internalRebuildInProgress = true;
        try {
            ClientRecipeCache.INSTANCE.buildRecipeCache(false);
        } finally {
            internalRebuildInProgress = false;
        }
    }

    private static Item resolveAliasItem(NeuItem neuItem) {
        String itemId = neuItem.itemId();
        if (itemId == null || itemId.isEmpty()) return null;
        Identifier id = Identifier.tryParse(itemId);
        if (id == null) return null;
        return BuiltInRegistries.ITEM.getOptional(id).orElse(null);
    }

    // =========================================================================
    // Plugin entry point
    // =========================================================================

    private static boolean exceedsFailureRate(int failures, int attempts) {
        return attempts > 0 && (double) failures / attempts > MAX_FAILURE_RATE;
    }

    // =========================================================================
    // Cache management
    // =========================================================================

    @Override
    public void onIntegrationInitialize() {
        LOGGER.info("SkyRecipes RRV client plugin initializing...");
        if (!DIRECT_INJECTION_AVAILABLE) {
            LOGGER.warn("================================================================================");
            LOGGER.warn("  SkyRecipes is running in PROVIDER-ONLY mode.");
            LOGGER.warn("  Internal cache injection is UNAVAILABLE.");
            LOGGER.warn("  Recipes will still work, but startup may be slower.");
            LOGGER.warn("  This usually means RRV was updated and its internal API changed.");
            LOGGER.warn("  Please report this to the SkyRecipes issue tracker.");
            LOGGER.warn("================================================================================");
        }

        ItemView.excludeRecipeTypes(
                Identifier.fromNamespaceAndPath("minecraft", "crafting"),
                Identifier.fromNamespaceAndPath("minecraft", "furnace_smelting"),
                Identifier.fromNamespaceAndPath("minecraft", "furnace_blasting"),
                Identifier.fromNamespaceAndPath("minecraft", "furnace_smoking"),
                Identifier.fromNamespaceAndPath("minecraft", "campfire_cooking"),
                Identifier.fromNamespaceAndPath("minecraft", "brewing"),
                Identifier.fromNamespaceAndPath("minecraft", "smithing"),
                Identifier.fromNamespaceAndPath("minecraft", "stonecutting"),
                Identifier.fromNamespaceAndPath("minecraft", "furnace_burning"),
                Identifier.fromNamespaceAndPath("rrv", "anvil_combining"),
                Identifier.fromNamespaceAndPath("minecraft", "entity_loot"),
                Identifier.fromNamespaceAndPath("minecraft", "villager_trading"),
                Identifier.fromNamespaceAndPath("rrv", "info"),
                Identifier.fromNamespaceAndPath("rrv", "world_interaction"),
                Identifier.fromNamespaceAndPath("rrv", "item_tag"),
                Identifier.fromNamespaceAndPath("rrv", "block_tag")
        );

        // Fallback provider: serves recipes once the pipeline has fully completed.
        ItemView.addClientRecipeProvider(recipeList -> {
            if (recipesReady) {
                RecipeResult result = cachedResult;
                if (result != null) recipeList.addAll(result.recipes());
            }
        });

        // Data-ready listener: reset and restart the pipeline on every load/reload.
        SkyRecipes.addDataReadyListener(_ -> {
            resetPipelineCycle();
            startHypixelFetch();
            startWorkIfReady();

            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> {
                    SkyRecipes.buildSearchAutocomplete();
                    registerAliases();
                });
            }
        });

        // Wait for full client initialization before touching Minecraft objects.
        ClientLifecycleEvents.CLIENT_STARTED.register(_ -> {
            clientStarted = true;
            startWorkIfReady();
        });

        // Per-tick: drive the batched injector and handle search-bar autocomplete.
        ClientTickEvents.END_CLIENT_TICK.register(this::handleEndClientTick);

        LOGGER.info("SkyRecipes RRV client plugin initialized");
    }

    // =========================================================================
    // Pipeline startup
    // =========================================================================

    /**
     * Reset cycle bookkeeping in preparation for a new load cycle.
     *
     * <p>Prepare-then-commit: live state ({@code cachedResult}, {@code cachedStacks},
     * {@code recipesReady}, {@code searchIndex}) is intentionally NOT touched here.
     * The previous data keeps serving recipes and search results until the new
     * cycle's results are complete, validated against failure thresholds, and
     * committed atomically inside {@link #beginBatchedInjection}. A cycle that
     * fails anywhere before that commit leaves the old data fully usable.</p>
     */
    private void resetPipelineCycle() {
        startupFinalized = false;
        pendingRecipeGen = null;
        pendingStackBuild = null;
        pendingRecipeResult = null;
        pendingStackResult = null;
        backgroundPrepLaunched.set(false);
        injectionLaunched.set(false);
        startupBatcher = null;
    }

    /**
     * Start background work when both data and the Minecraft client are ready.
     *
     * <p>If data components are not yet bound (can happen when data loads very early
     * during startup), a single background retry loop polls every 50 ms until binding
     * is confirmed, then marshals the final calls back to the render thread.</p>
     */
    private void startWorkIfReady() {
        if (!clientStarted) return;
        if (!SkyRecipes.isDataReady()) return;
        if (areComponentsBound()) {
            PipelineStatus.transition(PipelineStatus.State.GENERATING);
            startBackgroundRecipes();
            startBackgroundStacks();
            return;
        }
        if (startupRetryInProgress) return;
        startupRetryInProgress = true;
        CompletableFuture.runAsync(() -> {
            try {
                while (!areComponentsBound()) {
                    //noinspection BusyWait
                    Thread.sleep(50);
                }
                // Marshal back to render thread — startBackground* methods check
                // volatile fields; render-thread serialization keeps them race-free.
                Minecraft mc = Minecraft.getInstance();
                if (mc != null) {
                    mc.execute(() -> {
                        PipelineStatus.transition(PipelineStatus.State.GENERATING);
                        startBackgroundRecipes();
                        startBackgroundStacks();
                    });
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.debug("Startup retry loop interrupted");
            } finally {
                startupRetryInProgress = false;
            }
        });
    }

    // =========================================================================
    // Background work: phase 1 (parallel) — recipes and stacks
    // =========================================================================

    private void startHypixelFetch() {
        hypixelFetch = CompletableFuture.runAsync(() -> {
            try {
                Path cacheFile = FabricLoader.getInstance().getGameDir()
                        .resolve("skyblockdata").resolve("hypixel_items.json");

                if (HypixelItemsCache.isFresh(cacheFile)) {
                    HypixelItemsSnapshot cached = HypixelItemsCache.tryLoad(cacheFile);
                    if (cached != null) {
                        HypixelItemsRegistry.load(cached);
                        LOGGER.info("Loaded Hypixel items from cache: {} tiered, {} base stats",
                                cached.tieredStats().size(), cached.baseStats().size());
                        return;
                    }
                }

                HypixelItemsSnapshot fetched = HypixelItemsFetcher.fetch(
                        java.net.http.HttpClient.newHttpClient());
                if (fetched != null) {
                    HypixelItemsRegistry.load(fetched);
                    HypixelItemsCache.save(cacheFile, fetched);
                    LOGGER.info("Fetched Hypixel items API: {} tiered, {} base stats",
                            fetched.tieredStats().size(), fetched.baseStats().size());
                } else {
                    HypixelItemsSnapshot cached = HypixelItemsCache.tryLoad(cacheFile);
                    if (cached != null) {
                        HypixelItemsRegistry.load(cached);
                        LOGGER.warn("Hypixel API fetch failed; using stale cache");
                    } else {
                        LOGGER.warn("Hypixel API unavailable and no cache — essence stats will use NEU lore fallback");
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Hypixel fetch failed", e);
            }
        });
    }

    /**
     * Start recipe generation if not already running this cycle. Must be called on the render thread.
     */
    private void startBackgroundRecipes() {
        if (!SkyRecipes.isDataReady()) return;
        if (pendingRecipeResult != null) return; // already complete this cycle
        if (pendingRecipeGen != null && !pendingRecipeGen.isDone()) return;

        CompletableFuture<RecipeResult> future = CompletableFuture.supplyAsync(this::generateRecipes);
        pendingRecipeGen = future;
        future.thenAccept(result -> {
            if (pendingRecipeGen != future) return; // invalidated while running
            if (result != null) {
                pendingRecipeResult = result;
                LOGGER.info("Background recipe generation complete: {} recipes", result.recipes().size());
                maybeStartBackgroundPrep();
            } else {
                LOGGER.error("Recipe generation produced no result — previous data (if any) keeps serving");
                PipelineStatus.recordError("generate", "Recipe generation failed — see log for details", null);
            }
        }).exceptionally(t -> {
            LOGGER.error("Recipe generation crashed", t);
            PipelineStatus.recordError("generate", "Recipe generation crashed: " + t.getMessage(), t);
            return null;
        });
    }

    // =========================================================================
    // Background work: phase 2 — FamilyResolver + SkyblockRecipeCache
    // =========================================================================

    /**
     * Start stack and search-index building if not already running this cycle. Must be called on the render thread.
     */
    private void startBackgroundStacks() {
        if (!SkyRecipes.isDataReady()) return;
        if (pendingStackResult != null) return; // already complete this cycle
        if (pendingStackBuild != null && !pendingStackBuild.isDone()) return;

        CompletableFuture<StackBuildResult> future = CompletableFuture.supplyAsync(this::buildAllStacksAndIndex);
        pendingStackBuild = future;
        future.thenAccept(result -> {
            if (pendingStackBuild != future) return; // invalidated while running
            if (result != null) {
                pendingStackResult = result;
                LOGGER.info("Background stack building complete: {} stacks ({} failed)",
                        result.stacks().size(), result.failedItems());
                maybeStartBackgroundPrep();
            } else {
                LOGGER.error("Stack building produced no result — previous data (if any) keeps serving");
                PipelineStatus.recordError("stacks", "Item stack building failed — see log for details", null);
            }
        }).exceptionally(t -> {
            LOGGER.error("Stack building crashed", t);
            PipelineStatus.recordError("stacks", "Item stack building crashed: " + t.getMessage(), t);
            return null;
        });
    }

    /**
     * Called by both phase-1 futures on completion. The failure-threshold gate
     * runs first: a cycle that produced nothing (or failed systemically) is
     * aborted here, before any live state is touched. {@code compareAndSet}
     * ensures phase 2 launches exactly once even when both futures complete
     * concurrently.
     */
    private void maybeStartBackgroundPrep() {
        RecipeResult recipeResult = pendingRecipeResult;
        StackBuildResult stackResult = pendingStackResult;
        if (recipeResult == null || stackResult == null) return;
        if (!meetsGenerationThresholds(recipeResult, stackResult)) return;
        if (!backgroundPrepLaunched.compareAndSet(false, true)) return;

        PipelineStatus.recordGenerationCounts(recipeResult.recipes().size(),
                stackResult.stacks().size(), recipeResult.parseFailures(), stackResult.failedItems());

        CompletableFuture.supplyAsync(() -> {
            long start = System.currentTimeMillis();
            FamilyResolver resolver = new FamilyResolver(
                    SkyRecipes.getConstantsRegistry(),
                    SkyRecipes.getItemRegistry()
            );
            SkyblockRecipeCache.setFamilyResolver(resolver);
            SkyblockRecipeCache.rebuild(recipeResult.recipes());
            PipelineStatus.recordStageDuration("prep", System.currentTimeMillis() - start);
            return resolver;
        }).thenAccept(_ -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) mc.execute(this::beginBatchedInjection);
        }).exceptionally(throwable -> {
            LOGGER.error("Background prep (FamilyResolver/SkyblockRecipeCache) failed", throwable);
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) mc.execute(this::beginBatchedInjection);
            return null;
        });
    }

    /**
     * All-or-nothing gate between generation and injection. An empty result or
     * a failure rate above {@link #MAX_FAILURE_RATE} indicates a systemic
     * problem (e.g. a NEU format change), not bad individual entries — the
     * cycle aborts and the previous data keeps serving.
     */
    private boolean meetsGenerationThresholds(RecipeResult recipes, StackBuildResult stacks) {
        String problem = null;
        if (recipes.recipes().isEmpty()) {
            problem = "generated 0 recipes";
        } else if (stacks.stacks().isEmpty()) {
            problem = "built 0 item stacks";
        } else if (exceedsFailureRate(recipes.parseFailures(), recipes.parseAttempts())) {
            problem = String.format("recipe parse failure rate too high (%d of %d failed)",
                    recipes.parseFailures(), recipes.parseAttempts());
        } else if (exceedsFailureRate(stacks.failedItems(), stacks.attemptedItems())) {
            problem = String.format("stack build failure rate too high (%d of %d failed)",
                    stacks.failedItems(), stacks.attemptedItems());
        }

        if (problem != null) {
            LOGGER.error("Generation cycle aborted: {} — possible NEU format change. Previous data keeps serving.",
                    problem);
            PipelineStatus.recordError("generate", "SkyBlock data generation aborted: " + problem, null);
            return false;
        }

        LOGGER.info("Generation thresholds passed: recipes {} ok / {} failed; stacks {} ok / {} failed",
                recipes.recipes().size(), recipes.parseFailures(),
                stacks.stacks().size(), stacks.failedItems());
        return true;
    }

    // =========================================================================
    // Render-thread injection
    // =========================================================================

    /**
     * Publish all pipeline results and start the batched injector.
     *
     * <p>{@code compareAndSet} guards against the edge case where phase 2 schedules
     * this method twice (e.g. when {@code mc.execute} is called from both the normal
     * path and the exceptionally handler before the first call is processed).</p>
     *
     * <p>{@code searchIndex} is published before batching begins so the item list
     * becomes immediately visible. {@link #refreshOverlayQuery} then forces RRV to
     * re-filter if the overlay is already open.</p>
     */
    private void beginBatchedInjection() {
        if (!injectionLaunched.compareAndSet(false, true)) return;

        RecipeResult result = pendingRecipeResult;
        StackBuildResult stackResult = pendingStackResult;
        if (result == null || stackResult == null) {
            LOGGER.warn("Cannot begin batched injection: missing result or stacks");
            return;
        }

        PipelineStatus.transition(PipelineStatus.State.INJECTING);

        // Commit point: from here the new cycle's data replaces the old.
        injectionInProgress = true;
        cachedResult = result;
        cachedStacks = stackResult.stacks();
        searchIndex = stackResult.index();

        // If the overlay is open, force it to re-filter with the new index immediately.
        refreshOverlayQuery();

        // SkyRecipes now serves recipes on servers without RRV, so RRV's one-time
        // "cannot request recipes" chat warning no longer applies. Pre-mark it as
        // shown; if the pipeline fails before this point the native warning (and
        // RRV's local fallback trigger) keep working.
        ItemViewOverlay.INSTANCE.setWarned(true);

        List<ReliableClientRecipe> recipes = result.recipes();

        // Clear when any SkyRecipes entries may exist — also covers a reload
        // that interrupted a previous batcher mid-flight.
        if (!firstInjection || cacheHasSkyRecipesEntries) {
            ClientRecipeCache.INSTANCE.clear();
            LOGGER.debug("Cleared stale client recipes before re-injection");
        }
        ItemView.getStackSensitive().clear();
        ClientRecipeCache.INSTANCE.clearStackSensitives();
        cacheHasSkyRecipesEntries = true;

        LOGGER.info("Beginning batched RRV injection: {} recipes, {} stacks",
                recipes.size(), stackResult.stacks().size());
        this.startupBatcher = new StartupBatcher(recipes, stackResult.stacks());
    }

    /**
     * Forces the RRV item list overlay to re-evaluate its current query against
     * the newly published searchIndex.
     *
     * <p>Safe to call at any time: if the overlay has no active screen the call
     * completes quickly with no visible effect.</p>
     */
    private void refreshOverlayQuery() {
        if (searchIndex == null) return;
        String current = ItemViewOverlay.INSTANCE.getCurrentQuery();
        ((ItemViewOverlayAccessor) ItemViewOverlay.INSTANCE).skyrecipes$updateQuery(current);
    }

    @SuppressWarnings("unused")
    private void finishStartup(Minecraft client, List<ReliableClientRecipe> recipes, StartupBatcher batcher) {
        // Order matters for the mixin guard: recipesReady must be visible before
        // injectionInProgress drops so suppression never has a gap.
        recipesReady = true;
        injectionInProgress = false;
        startupFinalized = true;
        firstInjection = false;

        try {
            ((com.github.kdgaming0.skyrecipes.mixin.recipe.ClientRecipeCacheAccessor)
                    ClientRecipeCache.INSTANCE).skyrecipes$setLocalCacheBuilt(true);
        } catch (Exception e) {
            LOGGER.debug("Could not set RRV localCacheBuilt via accessor", e);
        }

        int skipped = batcher != null ? batcher.failedRecipes() : 0;
        int injected = recipes.size() - skipped;
        boolean providerOnly = !DIRECT_INJECTION_AVAILABLE || directInjectionBroken;
        if (batcher != null) {
            PipelineStatus.recordStageDuration("inject", batcher.workMillis());
        }
        PipelineStatus.recordInjectionResult(injected, skipped, providerOnly);
        PipelineStatus.transition(PipelineStatus.State.READY);

        PipelineStatus.Snapshot snap = PipelineStatus.snapshot();
        long totalMs = snap.stageDurationsMs().values().stream().mapToLong(Long::longValue).sum();
        LOGGER.info("SkyRecipes ready in {} ms: {} recipes injected ({} skipped){}{} — stages (ms): {}",
                totalMs, injected, skipped,
                providerOnly ? " [provider-only mode]" : "",
                batcher != null ? " across " + batcher.ticksUsed() + " ticks" : "",
                snap.stageDurationsMs());
    }

    // =========================================================================
    // Per-tick driver
    // =========================================================================

    private void handleEndClientTick(Minecraft client) {
        handleSearchBarSuggestionCommit(client);

        if (startupBatcher != null) {
            StartupBatcher batcher = startupBatcher;
            try {
                if (batcher.tick(client)) {
                    startupBatcher = null;
                    List<ReliableClientRecipe> recipes = cachedResult != null
                            ? cachedResult.recipes()
                            : List.of();
                    finishStartup(client, recipes, batcher);
                }
            } catch (Exception e) {
                LOGGER.error("Startup batcher tick failed", e);
                startupBatcher = null;
                List<ReliableClientRecipe> recipes = cachedResult != null
                        ? cachedResult.recipes()
                        : List.of();
                finishStartup(client, recipes, batcher);
            }
        }
    }

    // =========================================================================
    // Recipe and stack generation helpers
    // =========================================================================

    /**
     * Wait briefly for the Hypixel stats fetch so starred-item lore is built
     * from fresh values when possible. Proceeds with fallback values after
     * {@link #HYPIXEL_WAIT_SECONDS} — never blocks the pipeline indefinitely.
     */
    private void awaitHypixelFetch() {
        CompletableFuture<?> fetch = hypixelFetch;
        if (fetch == null) return;
        try {
            fetch.get(HYPIXEL_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOGGER.debug("Hypixel stats not ready after {} s — proceeding with fallback values",
                    HYPIXEL_WAIT_SECONDS);
        }
    }

    private RecipeResult generateRecipes() {
        ItemRegistry registry = SkyRecipes.getItemRegistry();
        if (registry == null) return null;
        try {
            awaitHypixelFetch();
            long start = System.currentTimeMillis();
            ConstantsRegistry constants = SkyRecipes.getConstantsRegistry();
            RecipeGenerator generator = new RecipeGenerator(registry, constants);
            RecipeResult result = generator.generate();
            PipelineStatus.recordStageDuration("generate", System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            LOGGER.error("Failed to generate recipes", e);
            return null;
        }
    }

    private StackBuildResult buildAllStacksAndIndex() {
        awaitHypixelFetch();

        ItemRegistry registry = SkyRecipes.getItemRegistry();
        if (registry == null) return null;

        long stacksStart = System.currentTimeMillis();
        List<NeuItem> items = registry.getAllItems().stream()
                .map(item -> new java.util.AbstractMap.SimpleEntry<>(ItemSortKey.of(item), item))
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(java.util.Map.Entry::getValue)
                .toList();

        // Stack building is CPU-bound and per-item independent (ItemStackBuilder
        // holds no mutable shared state), so fan out across the common pool.
        // The index-addressed array preserves the sorted order deterministically;
        // nulls (failures) are compacted out afterwards.
        ItemStack[] built = new ItemStack[items.size()];
        java.util.stream.IntStream.range(0, items.size()).parallel().forEach(i -> {
            NeuItem item = items.get(i);
            try {
                ItemStack stack = ItemStackBuilder.build(item);
                if (!stack.isEmpty() && stack.getItem() != Items.BARRIER) {
                    built[i] = stack;
                }
            } catch (Exception e) {
                LOGGER.debug("Failed to build stack for {}", item.internalName(), e);
            }
        });

        List<ItemStack> stacks = new ArrayList<>(items.size());
        int failed = 0;
        for (ItemStack stack : built) {
            if (stack != null) {
                stacks.add(stack);
            } else {
                failed++;
            }
        }
        PipelineStatus.recordStageDuration("stacks", System.currentTimeMillis() - stacksStart);
        LOGGER.info("Stack build: {} ok / {} failed", stacks.size(), failed);

        long indexStart = System.currentTimeMillis();
        SkyblockSearchIndex index = buildSearchIndex(stacks);
        PipelineStatus.recordStageDuration("index", System.currentTimeMillis() - indexStart);

        return new StackBuildResult(stacks, index, items.size(), failed);
    }

    private SkyblockSearchIndex buildSearchIndex(List<ItemStack> stacks) {
        ItemRegistry registry = SkyRecipes.getItemRegistry();
        ConstantsRegistry constants = SkyRecipes.getConstantsRegistry();
        if (registry == null || constants == null) {
            LOGGER.warn("Cannot build search index: registries not available");
            return null;
        }
        try {
            return new SkyblockSearchIndex(stacks, registry, constants, ALIASES);
        } catch (Exception e) {
            LOGGER.error("Failed to build SkyblockSearchIndex", e);
            return null;
        }
    }

    // =========================================================================
    // Alias registration
    // =========================================================================

    private void registerAliases() {
        ItemRegistry registry = SkyRecipes.getItemRegistry();
        if (registry == null) return;

        for (Map.Entry<String, String> entry : ALIASES.entrySet()) {
            registry.getByInternalName(entry.getValue()).ifPresent(neuItem -> {
                try {
                    Item item = resolveAliasItem(neuItem);
                    if (item != null) ItemView.addAlias(item, entry.getKey());
                } catch (Exception e) {
                    LOGGER.debug("Failed to register alias for {}", entry.getValue());
                }
            });
        }
    }

    // =========================================================================
    // Search-bar autocomplete (Right Arrow / Tab commit)
    // =========================================================================

    private void handleSearchBarSuggestionCommit(Minecraft client) {
        if (client.screen == null) return;
        if (!(client.screen.getFocused() instanceof SearchBar searchBar)) return;

        boolean rightDown = InputConstants.isKeyDown(client.getWindow(), InputConstants.KEY_RIGHT);
        boolean tabDown = InputConstants.isKeyDown(client.getWindow(), InputConstants.KEY_TAB);

        boolean rightPressed = rightDown && !wasRightArrowDown;
        boolean tabPressed = tabDown && !wasTabDown;

        if (rightPressed || tabPressed) {
            String suggestion = ((EditBoxAccessor) searchBar).skyrecipes$getSuggestion();
            if (suggestion != null && !suggestion.isEmpty()) {
                String current = searchBar.getValue();
                String fullText = current + suggestion;
                searchBar.setValue(fullText);
                searchBar.setCursorPosition(fullText.length());
                searchBar.setHighlightPos(fullText.length());
                searchBar.setSuggestion(null);
            }
        }

        wasRightArrowDown = rightDown;
        wasTabDown = tabDown;
    }

    // =========================================================================
    // Supporting types
    // =========================================================================

    private record StackBuildResult(List<ItemStack> stacks, SkyblockSearchIndex index,
                                    int attemptedItems, int failedItems) {
    }

    private record ItemSortKey(String familyBase, int tier, String cleanDisplayName, String internalName)
            implements Comparable<ItemSortKey> {

        static ItemSortKey of(NeuItem item) {
            String name = item.internalName() != null ? item.internalName() : "";
            String display = item.displayName() != null ? item.displayName() : "";
            return new ItemSortKey(
                    com.github.kdgaming0.skyrecipes.core.family.FamilyResolver.extractBaseName(name),
                    com.github.kdgaming0.skyrecipes.core.family.FamilyResolver.extractTier(name),
                    TextUtil.stripColorCodes(display),
                    name
            );
        }

        @Override
        public int compareTo(ItemSortKey other) {
            int c = this.familyBase.compareTo(other.familyBase);
            if (c != 0) return c;
            c = Integer.compare(this.tier, other.tier);
            if (c != 0) return c;
            c = this.cleanDisplayName.compareTo(other.cleanDisplayName);
            if (c != 0) return c;
            return this.internalName.compareTo(other.internalName);
        }
    }

    // =========================================================================
    // Batched injector
    // =========================================================================

    /**
     * Injects recipes and stack-sensitives across many ticks in four phases:
     * <ol>
     *   <li>Recipe injection via MethodHandle (500/tick)</li>
     *   <li>Fallback cache rebuild (provider-only mode only)</li>
     *   <li>Category registration</li>
     *   <li>Stack-sensitive registration (250/tick)</li>
     * </ol>
     */
    private final class StartupBatcher {

        private final List<ReliableClientRecipe> recipes;
        private final List<ItemStack> stacks;
        private int recipeIndex = 0;
        private int stackIndex = 0;
        private int failedRecipes = 0;
        private boolean categoriesAdded = false;
        private boolean fallbackDone = false;
        private long workNanos = 0;
        private int ticksUsed = 0;

        StartupBatcher(List<ReliableClientRecipe> recipes, List<ItemStack> stacks) {
            this.recipes = recipes;
            this.stacks = stacks;
        }

        int failedRecipes() {
            return failedRecipes;
        }

        long workMillis() {
            return workNanos / 1_000_000L;
        }

        int ticksUsed() {
            return ticksUsed;
        }

        /**
         * Failure count above which direct injection is considered systemically
         * broken (vs. individually malformed recipes, which are skipped).
         */
        private int systemicFailureThreshold() {
            return Math.max(50, recipes.size() / 100);
        }

        /**
         * Remove every partially injected entry and switch this and all future
         * cycles to the provider fallback. Safe because all directly injected
         * entries are tracked by RRV's clientEntryMap (fromNewSystem=true) and
         * removed by clear().
         */
        private void rollbackToProviderMode() {
            LOGGER.error("Direct recipe injection failing systemically ({} failures in first {} recipes). "
                            + "Rolling back partial injection and switching to provider-only mode.",
                    failedRecipes, recipeIndex);
            directInjectionBroken = true;
            recipeIndex = recipes.size(); // skip remaining direct injection
            try {
                ClientRecipeCache.INSTANCE.clear();
            } catch (Exception e) {
                LOGGER.error("Rollback of partial injection failed", e);
            }
        }

        /**
         * @return true when all phases are complete
         */
        boolean tick(Minecraft client) {
            ticksUsed++;
            long tickStart = System.nanoTime();
            try {
                return tickInternal();
            } finally {
                workNanos += System.nanoTime() - tickStart;
            }
        }

        private boolean tickInternal() {
            boolean useDirectInjection = DIRECT_INJECTION_AVAILABLE && !directInjectionBroken;

            // Phase 1: batch-inject recipes via direct MethodHandle
            if (useDirectInjection && recipeIndex < recipes.size()) {
                ClientRecipeCache cache = ClientRecipeCache.INSTANCE;
                int end = Math.min(recipeIndex + RECIPES_PER_TICK, recipes.size());
                for (int i = recipeIndex; i < end; i++) {
                    ReliableClientRecipe recipe = recipes.get(i);
                    try {
                        INJECT_RECIPE.invokeExact(cache, recipe.entryId(), recipe, i, true);
                    } catch (Throwable t) {
                        failedRecipes++;
                        if (failedRecipes <= 5) {
                            LOGGER.warn("Failed to inject recipe {}: {}", recipe.getId(), t.toString());
                        }
                    }
                }
                recipeIndex = end;
                if (failedRecipes > systemicFailureThreshold()) {
                    rollbackToProviderMode();
                    return false;
                }
                if (recipeIndex >= recipes.size()) {
                    LOGGER.info("Recipe injection batch complete: {} ok / {} skipped",
                            recipes.size() - failedRecipes, failedRecipes);
                }
                return false;
            }

            // Phase 2: fallback rebuild (provider-only mode, or after rollback)
            if (!useDirectInjection && !fallbackDone) {
                // The provider only serves recipes once recipesReady is set, and the
                // rebuild below is what invokes it — so the flag must go first here.
                recipesReady = true;
                try {
                    ((com.github.kdgaming0.skyrecipes.mixin.recipe.ClientRecipeCacheAccessor)
                            ClientRecipeCache.INSTANCE).skyrecipes$setLocalCacheBuilt(false);
                    runInternalRebuild();
                } catch (Exception e) {
                    LOGGER.error("Fallback recipe cache rebuild failed", e);
                }
                fallbackDone = true;
                return false;
            }

            // Phase 3: category registration
            if (!categoriesAdded) {
                try {
                    Configs.CATEGORIES.addNewCategories();
                } catch (Exception e) {
                    LOGGER.warn("Failed to add new RRV categories", e);
                }
                categoriesAdded = true;
                return false;
            }

            // Phase 4: stack-sensitive registration
            if (stackIndex < stacks.size()) {
                int end = Math.min(stackIndex + STACKS_PER_TICK, stacks.size());
                for (int i = stackIndex; i < end; i++) {
                    ItemStack stack = stacks.get(i);
                    ClientRecipeCache.INSTANCE.addStackSensitive(new ItemView.StackSensitive(stack));
                    ItemView.addStackSensitive(stack);
                }
                stackIndex = end;
                if (stackIndex >= stacks.size()) {
                    stacksReady = true;
                    LOGGER.info("Stack-sensitive registration batch complete: {} stacks", stacks.size());
                }
                return false;
            }

            return true;
        }
    }
}