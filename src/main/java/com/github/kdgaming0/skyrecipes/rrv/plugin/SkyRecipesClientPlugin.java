package com.github.kdgaming0.skyrecipes.rrv.plugin;

import cc.cassian.rrv.api.ReliableRecipeViewerClientPlugin;
import cc.cassian.rrv.api.recipe.ItemView;
import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.client.recipe.ClientRecipeCache;
import cc.cassian.rrv.common.config.Configs;
import cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay;
import com.github.kdgaming0.skyrecipes.SkyRecipes;
import com.github.kdgaming0.skyrecipes.client.config.SkyRecipesConfig;
import com.github.kdgaming0.skyrecipes.client.gui.CalculatorSessionOwner;
import com.github.kdgaming0.skyrecipes.core.data.PipelineStatus;
import com.github.kdgaming0.skyrecipes.core.family.FamilyResolver;
import com.github.kdgaming0.skyrecipes.core.fusion.ShardFusionData;
import com.github.kdgaming0.skyrecipes.core.fusion.ShardFusionFetcher;
import com.github.kdgaming0.skyrecipes.core.fusion.ShardFusionMpkCache;
import com.github.kdgaming0.skyrecipes.core.fusion.ShardFusionRegistry;
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
import com.github.kdgaming0.skyrecipes.core.render.mob.MobSkinRegistry;
import com.github.kdgaming0.skyrecipes.rrv.overlay.SkyblockCraftablesIndex;
import com.github.kdgaming0.skyrecipes.core.search.SearchAliases;
import com.github.kdgaming0.skyrecipes.core.search.SkyblockSearchIndex;
import com.github.kdgaming0.skyrecipes.core.util.SkyRecipesExecutors;
import com.github.kdgaming0.skyrecipes.core.util.TextUtil;
import com.github.kdgaming0.skyrecipes.mixin.accessor.ItemViewOverlayAccessor;
import com.github.kdgaming0.skyrecipes.rrv.recipe.NpcInfoRegistry;
import com.github.kdgaming0.skyrecipes.rrv.recipe.SkyblockRecipeCache;
import com.github.kdgaming0.skyrecipes.rrv.recipe.StackGroupItemsCache;
import com.github.kdgaming0.skyrecipes.rrv.recipe.stackgroup.SkyblockStackGroups;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(SkyRecipesClientPlugin.class);

    /**
     * RRV's private handleClientRecipe, bound via reflection. Null = provider-only fallback.
     */
    private static final MethodHandle INJECT_RECIPE;
    private static final boolean DIRECT_INJECTION_AVAILABLE;
    private static final int RECIPES_PER_TICK = 500;
    private static final int STACKS_PER_TICK = 250;

    /**
     * Wall-clock ceiling for one {@link StartupBatcher} tick.
     *
     * <p>The per-tick counts above are a throughput ceiling, not a cost ceiling: what 500
     * recipe injections cost depends entirely on the machine, and on a slow one they land as
     * a visible hitch every tick for the whole injection run. The budget bounds the hitch
     * instead, and the counts stay as the upper bound so a fast machine finishes just as
     * quickly as before. 1.5 ms is roughly a third of a 20 TPS tick's 50 ms, well under the
     * frame budget the tick shares.</p>
     */
    private static final long BATCH_BUDGET_NANOS = 1_500_000L;

    /**
     * Items processed between clock reads. Amortizes {@code System.nanoTime()} (~25 ns) to
     * noise while keeping overshoot bounded, and guarantees at least one chunk of forward
     * progress per tick so the batch can never stall.
     */
    private static final int BATCH_BUDGET_STRIDE = 64;

    /**
     * Floor on per-tick progress, as a fraction of the nominal per-tick counts.
     *
     * <p>A pure time budget lets a slow machine fall back to one {@link #BATCH_BUDGET_STRIDE}
     * chunk per tick, stretching injection from a few seconds to tens of seconds — and the
     * stack-group memo is cold for that whole window, so a longer window is not a neutral
     * trade. This bounds the stretch to 4x the nominal tick count while still capping the
     * per-tick hitch for everyone above that floor.</p>
     */
    private static final int BATCH_MIN_FRACTION = 4;
    /**
     * Above this fraction of failures, a generation/build stage is treated as systemic and the cycle aborts.
     */
    private static final double MAX_FAILURE_RATE = 0.05;
    /**
     * How long generation waits for Hypixel stats before proceeding with fallback values.
     */
    private static final long HYPIXEL_WAIT_SECONDS = 5;
    /**
     * How long generation waits for the shard fusion fetch before proceeding without it.
     */
    private static final long SHARD_FUSION_WAIT_SECONDS = 10;
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
    /**
     * Stacks published at the last injection commit. The batcher hands these same
     * instances to RRV, so {@link SkyRecipesPlugin} may reuse them for the
     * integrated-server registration instead of rebuilding ~8k stacks.
     */
    private static volatile List<ItemStack> cachedStacks = null;

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
    private volatile CompletableFuture<?> pendingRecipeGen = null;
    private volatile CompletableFuture<?> pendingStackBuild = null;
    private volatile boolean clientStarted = false;
    private volatile boolean awaitingComponentBinding = false;
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
    private volatile CompletableFuture<?> shardFusionCacheLoad = null;
    private volatile CompletableFuture<?> shardFusionFetch = null;
    private volatile RecipeResult pendingRecipeResult = null;
    private volatile StackBuildResult pendingStackResult = null;
    private StartupBatcher startupBatcher = null;

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
     * Fully-built client stacks from the last committed pipeline cycle, or null
     * if no cycle has committed yet.
     */
    public static List<ItemStack> getCachedStacks() {
        return cachedStacks;
    }

    /**
     * True when the data pipeline failed before ever producing data (offline
     * first launch, blocked download). The item panel then explains the empty
     * list in place and points at {@code /skyrecipes status} and
     * {@code /skyrecipes import}.
     */
    public static boolean isPipelineFailed() {
        return PipelineStatus.isFailed();
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
            startShardFusionFetch();
            startWorkIfReady();

            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.execute(this::registerAliases);
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
     * <p>If RRV's data components are not yet bound (data can load very early during
     * startup), the launch is deferred to the per-tick poll in
     * {@link #handleEndClientTick}
     */
    private void startWorkIfReady() {
        if (!clientStarted) return;
        if (!SkyRecipes.isDataReady()) return;
        if (areComponentsBound()) {
            launchBackgroundGeneration();
        } else {
            awaitingComponentBinding = true;
        }
    }

    /**
     * Launch phase-1 recipe generation and stack building. Render thread only: the
     * {@code startBackground*} methods read volatile cycle state that render-thread
     * serialization keeps race-free.
     */
    private void launchBackgroundGeneration() {
        awaitingComponentBinding = false;
        PipelineStatus.transition(PipelineStatus.State.GENERATING);
        // Recipe gen and stack build rebuild the same items many times over;
        // the memo lives until the injection commit (or cycle abort).
        ItemStackBuilder.beginCycleCache();
        startBackgroundRecipes();
        startBackgroundStacks();
    }

    // =========================================================================
    // Background work: phase 1 (parallel) — recipes and stacks
    // =========================================================================

    private void startHypixelFetch() {
        hypixelFetch = CompletableFuture.runAsync(() -> {
            try {
                Path cacheFile = SkyRecipes.getCacheLayout().hypixelItemsFile();

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
                        SkyRecipesExecutors.httpClient());
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
        }, SkyRecipesExecutors.worker());
    }

    /**
     * Load the SkyShards fusion dataset: MPK disk cache first (instant — recipe
     * generation never waits on the network when a cache exists), then a
     * background ETag-conditional refresh, mirroring the NEU repo's
     * update-detection. A 304 costs a few hundred bytes; new data is saved back
     * to the MPK cache and picked up by the next generation cycle.
     */
    private void startShardFusionFetch() {
        if (!SkyRecipesConfig.shardFusionRecipes) return;
        CompletableFuture<?> inFlight = shardFusionFetch;
        if (inFlight != null && !inFlight.isDone()) return;

        CompletableFuture<?> cacheLoad =
                CompletableFuture.runAsync(this::loadShardFusionCache, SkyRecipesExecutors.worker());
        shardFusionCacheLoad = cacheLoad;
        shardFusionFetch =
                cacheLoad.thenRunAsync(this::refreshShardFusionsFromNetwork, SkyRecipesExecutors.worker());
    }

    private void loadShardFusionCache() {
        try {
            if (ShardFusionRegistry.get() == null) {
                ShardFusionMpkCache.Loaded cached =
                        ShardFusionMpkCache.load(SkyRecipes.getCacheLayout().shardFusionsFile());
                if (cached != null) {
                    ShardFusionRegistry.load(cached.data(), cached.etag());
                    LOGGER.info("Loaded shard fusion data from cache: {} shards, {} pairs",
                            cached.data().shardCount(), cached.data().totalPairs());
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Shard fusion cache load failed", e);
        }
    }

    private void refreshShardFusionsFromNetwork() {
        try {
            String etag = ShardFusionRegistry.get() != null ? ShardFusionRegistry.getEtag() : null;
            ShardFusionFetcher.FetchResult result =
                    ShardFusionFetcher.fetch(SkyRecipesExecutors.httpClient(), etag);
            if (result == null) {
                if (ShardFusionRegistry.get() == null) {
                    LOGGER.warn("Shard fusion data unavailable and no cache — fusion recipes will be absent");
                }
                return;
            }
            if (result.notModified()) {
                LOGGER.debug("Shard fusion data unchanged (ETag match)");
                return;
            }
            ShardFusionData parsed = ShardFusionFetcher.parse(result.body());
            if (parsed == null) return;
            ShardFusionRegistry.load(parsed, result.etag());
            ShardFusionMpkCache.save(SkyRecipes.getCacheLayout().shardFusionsFile(), parsed, result.etag());
            LOGGER.info("Fetched shard fusion data: {} shards, {} pairs",
                    parsed.shardCount(), parsed.totalPairs());
        } catch (Exception e) {
            LOGGER.warn("Shard fusion refresh failed", e);
        }
    }

    /**
     * Start recipe generation if not already running this cycle. Must be called on the render thread.
     */
    private void startBackgroundRecipes() {
        if (!SkyRecipes.isDataReady()) return;
        if (pendingRecipeResult != null) return; // already complete this cycle
        if (pendingRecipeGen != null && !pendingRecipeGen.isDone()) return;

        CompletableFuture<RecipeResult> future =
                CompletableFuture.supplyAsync(this::generateRecipes, SkyRecipesExecutors.worker());
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
                ItemStackBuilder.endCycleCache(); // cycle is dead; don't pin the templates
            }
        }).exceptionally(t -> {
            LOGGER.error("Recipe generation crashed", t);
            PipelineStatus.recordError("generate", "Recipe generation crashed: " + t.getMessage(), t);
            if (pendingRecipeGen == future) ItemStackBuilder.endCycleCache();
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

        CompletableFuture<StackBuildResult> future =
                CompletableFuture.supplyAsync(this::buildAllStacksAndIndex, SkyRecipesExecutors.worker());
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
                ItemStackBuilder.endCycleCache(); // cycle is dead; don't pin the templates
            }
        }).exceptionally(t -> {
            LOGGER.error("Stack building crashed", t);
            PipelineStatus.recordError("stacks", "Item stack building crashed: " + t.getMessage(), t);
            if (pendingStackBuild == future) ItemStackBuilder.endCycleCache();
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
        if (!meetsGenerationThresholds(recipeResult, stackResult)) {
            ItemStackBuilder.endCycleCache(); // cycle aborted; don't pin the templates
            return;
        }
        if (!backgroundPrepLaunched.compareAndSet(false, true)) return;

        PipelineStatus.recordGenerationCounts(recipeResult.recipes().size(),
                stackResult.stacks().size(), recipeResult.parseFailures(), stackResult.failedItems());

        CompletableFuture.supplyAsync(() -> {
            long start = System.currentTimeMillis();
            FamilyResolver resolver = new FamilyResolver(
                    SkyRecipes.getConstantsRegistry(),
                    SkyRecipes.getItemRegistry(),
                    SkyRecipesConfig.groupCraftedChains
            );
            SkyblockRecipeCache.setFamilyResolver(resolver);
            SkyblockRecipeCache.rebuild(recipeResult.recipes());
            SkyblockCraftablesIndex.rebuild(recipeResult.recipes());
            SkyblockStackGroups.rebuild(resolver, SkyRecipes.getItemRegistry());
            PipelineStatus.recordStageDuration("prep", System.currentTimeMillis() - start);
            return resolver;
        }, SkyRecipesExecutors.worker()).thenAccept(_ -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) mc.execute(this::beginBatchedInjection);
        }).exceptionally(throwable -> {
            // Injecting anyway would pair new recipes with a stale SkyblockRecipeCache
            // (R/U lookups against the previous dataset) — abort like a threshold failure.
            LOGGER.error("Background prep (FamilyResolver/SkyblockRecipeCache) failed — cycle aborted, previous data keeps serving", throwable);
            ItemStackBuilder.endCycleCache();
            PipelineStatus.recordError("prep", "SkyBlock recipe preparation failed — data reload aborted", throwable);
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
        NpcInfoRegistry.publish();
        // Render thread here, so releasing the GPU textures is safe; skins from the
        // old ConstantsRegistry are stale and rebuild lazily on next render.
        MobSkinRegistry.clear();

        // All stacks the batcher injects are pre-built; the memo has done its job.
        ItemStackBuilder.endCycleCache();

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
        StackGroupItemsCache.invalidate();
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
        if (ItemViewOverlay.INSTANCE instanceof CalculatorSessionOwner owner) {
            owner.skyrecipes$refreshEffectiveQuery();
            return;
        }
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

        // Publish this cycle's SkyBlock family groups before the prewarm below computes
        // group contents, so their members are included.
        SkyblockStackGroups.injectInto();

        // Group-content memo entries built mid-batch missed later-registered stacks;
        // prewarm so the first search keystroke never pays the group sweep.
        StackGroupItemsCache.invalidate();
        StackGroupItemsCache.prewarm();

        try {
            ((com.github.kdgaming0.skyrecipes.mixin.recipe.ClientRecipeCacheAccessor)
                    ClientRecipeCache.INSTANCE).skyrecipes$setLocalCacheBuilt(true);
        } catch (Exception e) {
            LOGGER.debug("Could not set RRV localCacheBuilt via accessor", e);
        }

        int skipped = batcher != null ? batcher.failedRecipes() : 0;
        int injected = batcher != null ? batcher.injectedRecipes() : recipes.size();
        boolean providerOnly = !DIRECT_INJECTION_AVAILABLE || directInjectionBroken;
        if (batcher != null) {
            PipelineStatus.recordStageDuration("inject", batcher.workMillis());
        }
        PipelineStatus.recordInjectionResult(injected, skipped, providerOnly);
        PipelineStatus.transition(PipelineStatus.State.READY);

        // After READY so the transition can't erase it: a whole generator category
        // failing (essence/reforge/garden) shows as DEGRADED instead of vanishing.
        RecipeResult committed = cachedResult;
        if (committed != null && !committed.generatorFailures().isEmpty()) {
            PipelineStatus.recordError("generate", "Some recipe categories failed to generate: "
                    + String.join(", ", committed.generatorFailures()), null);
        }

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
        // A throw from a Fabric tick handler crashes the client, and this runs
        // every tick — never let launch work escape.
        try {
            if (awaitingComponentBinding && SkyRecipes.isDataReady() && areComponentsBound()) {
                launchBackgroundGeneration();
            }
        } catch (Exception e) {
            LOGGER.error("SkyRecipes tick handler failed", e);
        }

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
                PipelineStatus.recordError("inject",
                        "Recipe injection crashed partway — some recipes may be missing until the next reload", e);
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
    /**
     * Waits up to {@code seconds} for {@code future}, logging {@code notReadyMsg}
     * at debug when it does not complete in time.
     *
     * @return {@code false} when the wait was interrupted (interrupt flag restored)
     */
    private static boolean awaitQuietly(@Nullable CompletableFuture<?> future, long seconds, String notReadyMsg) {
        if (future == null) return true;
        try {
            future.get(seconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            LOGGER.debug(notReadyMsg, e);
        }
        return true;
    }

    private void awaitHypixelFetch() {
        awaitQuietly(hypixelFetch, HYPIXEL_WAIT_SECONDS,
                "Hypixel stats not ready after " + HYPIXEL_WAIT_SECONDS + " s — proceeding with fallback values");
    }

    /**
     * Wait briefly for shard fusion data so fusion cards are part of this
     * generation cycle. The MPK cache load is near-instant; the network fetch
     * (first run only — a cache hit never blocks on the network) gets a longer
     * budget since the file is ~2 MB. On a miss the cycle simply generates
     * without fusion recipes.
     */
    private void awaitShardFusionFetch() {
        if (!awaitQuietly(shardFusionCacheLoad, 5, "Shard fusion cache load not ready")) return;
        if (ShardFusionRegistry.get() != null) return;

        awaitQuietly(shardFusionFetch, SHARD_FUSION_WAIT_SECONDS,
                "Shard fusion data not ready after " + SHARD_FUSION_WAIT_SECONDS
                        + " s — generating without fusion recipes");
    }

    private RecipeResult generateRecipes() {
        ItemRegistry registry = SkyRecipes.getItemRegistry();
        if (registry == null) return null;
        try {
            awaitHypixelFetch();
            awaitShardFusionFetch();
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

        // Stack building is CPU-bound and per-item independent (ItemStackBuilder holds no
        // mutable shared state), so fan it out. This method is submitted to
        // SkyRecipesExecutors.worker(), and a parallel stream started from inside a
        // ForkJoinPool worker runs in that same pool — so the fan-out stays on our bounded
        // pool and never competes with Minecraft's work on the common pool during startup.
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
            return new SkyblockSearchIndex(stacks, registry, constants, SearchAliases.MAP);
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

        for (Map.Entry<String, String> entry : SearchAliases.MAP.entrySet()) {
            NeuItem neuItem = registry.getOrNull(entry.getValue());
            if (neuItem == null) continue;
            try {
                Item item = resolveAliasItem(neuItem);
                if (item != null) ItemView.addAlias(item, entry.getKey());
            } catch (Exception e) {
                LOGGER.debug("Failed to register alias for {}", entry.getValue());
            }
        }
    }

    // =========================================================================
    // Supporting types
    // =========================================================================

    private record StackBuildResult(List<ItemStack> stacks, SkyblockSearchIndex index,
                                    int attemptedItems, int failedItems) {
    }

    private record ItemSortKey(String familyBase, int tier, int armorSlot,
                               String cleanDisplayName, String internalName)
            implements Comparable<ItemSortKey> {

        static ItemSortKey of(NeuItem item) {
            String name = item.internalName() != null ? item.internalName() : "";
            String display = item.displayName() != null ? item.displayName() : "";
            return new ItemSortKey(
                    com.github.kdgaming0.skyrecipes.core.family.FamilyResolver.extractBaseName(name),
                    com.github.kdgaming0.skyrecipes.core.family.FamilyResolver.extractTier(name),
                    com.github.kdgaming0.skyrecipes.core.family.FamilyResolver.armorSlotRank(name),
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
            // Pieces of one armor set share a family base and tier 0; without this
            // they'd list alphabetically (Boots, Chestplate, Helmet, Leggings).
            c = Integer.compare(this.armorSlot, other.armorSlot);
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

        /**
         * Recipes actually delivered to RRV so far: attempted minus failed for
         * direct injection; all-or-nothing for the provider fallback. Stays
         * correct when an exception ends the batch early.
         */
        int injectedRecipes() {
            if (DIRECT_INJECTION_AVAILABLE && !directInjectionBroken) {
                return recipeIndex - failedRecipes;
            }
            return fallbackDone ? recipes.size() : 0;
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
            long deadline = System.nanoTime() + BATCH_BUDGET_NANOS;

            // Phase 1: batch-inject recipes via direct MethodHandle
            if (useDirectInjection && recipeIndex < recipes.size()) {
                ClientRecipeCache cache = ClientRecipeCache.INSTANCE;
                int end = Math.min(recipeIndex + RECIPES_PER_TICK, recipes.size());
                int minThisTick = Math.max(BATCH_BUDGET_STRIDE, RECIPES_PER_TICK / BATCH_MIN_FRACTION);
                int i = recipeIndex;
                while (i < end) {
                    int chunkEnd = Math.min(i + BATCH_BUDGET_STRIDE, end);
                    for (; i < chunkEnd; i++) {
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
                    if (i - recipeIndex >= minThisTick && System.nanoTime() >= deadline) break;
                }
                recipeIndex = i;
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
                int minThisTick = Math.max(BATCH_BUDGET_STRIDE, STACKS_PER_TICK / BATCH_MIN_FRACTION);
                int i = stackIndex;
                while (i < end) {
                    int chunkEnd = Math.min(i + BATCH_BUDGET_STRIDE, end);
                    for (; i < chunkEnd; i++) {
                        ItemStack stack = stacks.get(i);
                        ClientRecipeCache.INSTANCE.addStackSensitive(new ItemView.StackSensitive(stack));
                        ItemView.addStackSensitive(stack);
                    }
                    if (i - stackIndex >= minThisTick && System.nanoTime() >= deadline) break;
                }
                stackIndex = i;
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