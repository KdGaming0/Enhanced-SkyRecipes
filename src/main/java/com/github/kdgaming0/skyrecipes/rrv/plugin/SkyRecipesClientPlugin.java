package com.github.kdgaming0.skyrecipes.rrv.plugin;

import cc.cassian.rrv.api.ReliableRecipeViewerClientPlugin;
import cc.cassian.rrv.api.recipe.ItemView;
import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.client.recipe.ClientRecipeCache;
import cc.cassian.rrv.common.config.Configs;
import cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay;
import cc.cassian.rrv.common.overlay.itemlist.view.SearchBar;
import com.github.kdgaming0.skyrecipes.SkyRecipes;
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
public class SkyRecipesClientPlugin implements ReliableRecipeViewerClientPlugin {

    /** Alias map exposed for {@link com.github.kdgaming0.skyrecipes.core.search.SearchAutocomplete}. */
    public static final Map<String, String> ALIASES;

    private static final Logger LOGGER = LoggerFactory.getLogger(SkyRecipesClientPlugin.class);

    /** RRV's private handleClientRecipe, bound via reflection. Null = provider-only fallback. */
    private static final MethodHandle INJECT_RECIPE;
    private static final boolean DIRECT_INJECTION_AVAILABLE;

    private static volatile boolean recipesReady = false;
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

    // ---- Batched-injection constants ----------------------------------------

    private static final int RECIPES_PER_TICK = 500;
    private static final int STACKS_PER_TICK = 250;

    // ---- Per-instance volatile pipeline state -------------------------------

    private volatile boolean stacksReady = false;
    private volatile boolean startupFinalized = false;
    /** True only for the very first injection; controls whether to clear the cache beforehand. */
    private volatile boolean firstInjection = true;
    private volatile RecipeResult cachedResult = null;
    private volatile List<ItemStack> cachedStacks = null;
    private volatile CompletableFuture<?> pendingRecipeGen = null;
    private volatile CompletableFuture<?> pendingStackBuild = null;
    private volatile boolean clientStarted = false;
    private volatile boolean startupRetryInProgress = false;

    // ---- Coordinated background-completion state ----------------------------

    private volatile RecipeResult pendingRecipeResult = null;
    private volatile List<ItemStack> pendingStacks = null;
    private volatile SkyblockSearchIndex pendingSearchIndex = null;

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

    public static boolean areRecipesReady() {
        return recipesReady;
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
        SkyRecipes.addDataReadyListener(result -> {
            invalidateCaches();
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
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            clientStarted = true;
            startWorkIfReady();
        });

        // Per-tick: drive the batched injector and handle search-bar autocomplete.
        ClientTickEvents.END_CLIENT_TICK.register(this::handleEndClientTick);

        LOGGER.info("SkyRecipes RRV client plugin initialized");
    }

    // =========================================================================
    // Cache management
    // =========================================================================

    /**
     * Reset all pipeline state in preparation for a new load cycle.
     *
     * <p>{@code searchIndex} is intentionally NOT cleared here. Keeping the previous
     * index active during the rebuild window (typically 1-3 s) ensures the item list
     * never goes blank during background data updates. The new index will replace it
     * atomically inside {@link #beginBatchedInjection}.</p>
     */
    private void invalidateCaches() {
        cachedResult = null;
        cachedStacks = null;
        recipesReady = false;
        stacksReady = false;
        startupFinalized = false;
        pendingRecipeGen = null;
        pendingStackBuild = null;
        pendingRecipeResult = null;
        pendingStacks = null;
        pendingSearchIndex = null;
        backgroundPrepLaunched.set(false);
        injectionLaunched.set(false);
        startupBatcher = null;
    }

    // =========================================================================
    // Pipeline startup
    // =========================================================================

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
            startBackgroundRecipes();
            startBackgroundStacks();
            return;
        }
        if (startupRetryInProgress) return;
        startupRetryInProgress = true;
        CompletableFuture.runAsync(() -> {
            try {
                while (!areComponentsBound()) {
                    Thread.sleep(50);
                }
                // Marshal back to render thread — startBackground* methods check
                // volatile fields; render-thread serialization keeps them race-free.
                Minecraft mc = Minecraft.getInstance();
                if (mc != null) {
                    mc.execute(() -> {
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

    private void startHypixelFetch() {
        CompletableFuture.runAsync(() -> {
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

    // =========================================================================
    // Background work: phase 1 (parallel) — recipes and stacks
    // =========================================================================

    /** Start recipe generation if not already running. Must be called on the render thread. */
    private void startBackgroundRecipes() {
        if (!SkyRecipes.isDataReady()) return;
        if (cachedResult != null) return;
        if (pendingRecipeGen != null && !pendingRecipeGen.isDone()) return;

        CompletableFuture<RecipeResult> future = CompletableFuture.supplyAsync(this::generateRecipes);
        pendingRecipeGen = future;
        future.thenAccept(result -> {
            if (pendingRecipeGen != future) return; // invalidated while running
            if (result != null) {
                pendingRecipeResult = result;
                LOGGER.info("Background recipe generation complete: {} recipes", result.recipes().size());
                maybeStartBackgroundPrep();
            }
        });
    }

    /** Start stack and search-index building if not already running. Must be called on the render thread. */
    private void startBackgroundStacks() {
        if (!SkyRecipes.isDataReady()) return;
        if (cachedStacks != null) return;
        if (pendingStackBuild != null && !pendingStackBuild.isDone()) return;

        CompletableFuture<StackBuildResult> future = CompletableFuture.supplyAsync(this::buildAllStacksAndIndex);
        pendingStackBuild = future;
        future.thenAccept(result -> {
            if (pendingStackBuild != future) return; // invalidated while running
            if (result != null) {
                pendingStacks = result.stacks();
                pendingSearchIndex = result.index();
                LOGGER.info("Background stack building complete: {} stacks", result.stacks().size());
                maybeStartBackgroundPrep();
            }
        });
    }

    // =========================================================================
    // Background work: phase 2 — FamilyResolver + SkyblockRecipeCache
    // =========================================================================

    /**
     * Called by both phase-1 futures on completion. {@code compareAndSet} ensures
     * phase 2 launches exactly once even when both futures complete concurrently.
     */
    private void maybeStartBackgroundPrep() {
        if (pendingRecipeResult == null || pendingStacks == null) return;
        if (!backgroundPrepLaunched.compareAndSet(false, true)) return;

        CompletableFuture.supplyAsync(() -> {
            FamilyResolver resolver = new FamilyResolver(
                    SkyRecipes.getConstantsRegistry(),
                    SkyRecipes.getItemRegistry()
            );
            SkyblockRecipeCache.setFamilyResolver(resolver);
            SkyblockRecipeCache.rebuild(pendingRecipeResult.recipes());
            return resolver;
        }).thenAccept(resolver -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) mc.execute(this::beginBatchedInjection);
        }).exceptionally(throwable -> {
            LOGGER.error("Background prep (FamilyResolver/SkyblockRecipeCache) failed", throwable);
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) mc.execute(this::beginBatchedInjection);
            return null;
        });
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
        List<ItemStack> stacks = pendingStacks;
        if (result == null || stacks == null) {
            LOGGER.warn("Cannot begin batched injection: missing result or stacks");
            return;
        }

        cachedResult = result;
        cachedStacks = stacks;
        searchIndex = pendingSearchIndex;

        // If the overlay is open, force it to re-filter with the new index immediately.
        refreshOverlayQuery();

        List<ReliableClientRecipe> recipes = result.recipes();

        if (!firstInjection) {
            ClientRecipeCache.INSTANCE.clear();
            LOGGER.debug("Cleared stale client recipes before re-injection");
        }
        ItemView.getStackSensitive().clear();
        ClientRecipeCache.INSTANCE.clearStackSensitives();

        LOGGER.info("Beginning batched RRV injection: {} recipes, {} stacks",
                recipes.size(), stacks.size());
        this.startupBatcher = new StartupBatcher(recipes, stacks);
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

    private void finishStartup(Minecraft client, List<ReliableClientRecipe> recipes) {
        recipesReady = true;
        startupFinalized = true;
        firstInjection = false;

        try {
            ((com.github.kdgaming0.skyrecipes.mixin.recipe.ClientRecipeCacheAccessor)
                    ClientRecipeCache.INSTANCE).skyrecipes$setLocalCacheBuilt(true);
        } catch (Exception e) {
            LOGGER.debug("Could not set RRV localCacheBuilt via accessor", e);
        }

        LOGGER.info("SkyRecipes startup complete: {} recipes injected into RRV", recipes.size());
    }

    // =========================================================================
    // Per-tick driver
    // =========================================================================

    private void handleEndClientTick(Minecraft client) {
        handleSearchBarSuggestionCommit(client);

        if (startupBatcher != null) {
            try {
                if (startupBatcher.tick(client)) {
                    startupBatcher = null;
                    List<ReliableClientRecipe> recipes = cachedResult != null
                            ? cachedResult.recipes()
                            : List.of();
                    finishStartup(client, recipes);
                }
            } catch (Exception e) {
                LOGGER.error("Startup batcher tick failed", e);
                startupBatcher = null;
                List<ReliableClientRecipe> recipes = cachedResult != null
                        ? cachedResult.recipes()
                        : List.of();
                finishStartup(client, recipes);
            }
        }
    }

    // =========================================================================
    // Recipe and stack generation helpers
    // =========================================================================

    private RecipeResult generateRecipes() {
        ItemRegistry registry = SkyRecipes.getItemRegistry();
        if (registry == null) return null;
        try {
            ConstantsRegistry constants = SkyRecipes.getConstantsRegistry();
            RecipeGenerator generator = new RecipeGenerator(registry, constants);
            return generator.generate();
        } catch (Exception e) {
            LOGGER.error("Failed to generate recipes", e);
            return null;
        }
    }

    private StackBuildResult buildAllStacksAndIndex() {
        List<ItemStack> stacks = buildAllStacks();
        SkyblockSearchIndex index = buildSearchIndex(stacks);
        return new StackBuildResult(stacks, index);
    }

    private List<ItemStack> buildAllStacks() {
        ItemRegistry registry = SkyRecipes.getItemRegistry();
        List<ItemStack> stacks = new ArrayList<>();
        if (registry == null) return stacks;

        List<NeuItem> items = registry.getAllItems().stream()
                .map(item -> new java.util.AbstractMap.SimpleEntry<>(ItemSortKey.of(item), item))
                .sorted(java.util.Comparator.comparing(java.util.Map.Entry::getKey))
                .map(java.util.Map.Entry::getValue)
                .toList();

        for (NeuItem item : items) {
            try {
                ItemStack stack = ItemStackBuilder.build(item);
                if (!stack.isEmpty() && stack.getItem() != Items.BARRIER) {
                    stacks.add(stack);
                }
            } catch (Exception e) {
                LOGGER.debug("Failed to build stack for {}", item.internalName(), e);
            }
        }
        return stacks;
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

    private record StackBuildResult(List<ItemStack> stacks, SkyblockSearchIndex index) {}

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
        private boolean categoriesAdded = false;
        private boolean fallbackDone = false;

        StartupBatcher(List<ReliableClientRecipe> recipes, List<ItemStack> stacks) {
            this.recipes = recipes;
            this.stacks = stacks;
        }

        /** @return true when all phases are complete */
        boolean tick(Minecraft client) {
            // Phase 1: batch-inject recipes via direct MethodHandle
            if (DIRECT_INJECTION_AVAILABLE && recipeIndex < recipes.size()) {
                ClientRecipeCache cache = ClientRecipeCache.INSTANCE;
                int end = Math.min(recipeIndex + RECIPES_PER_TICK, recipes.size());
                try {
                    for (int i = recipeIndex; i < end; i++) {
                        ReliableClientRecipe recipe = recipes.get(i);
                        INJECT_RECIPE.invokeExact(cache, recipe.entryId(), recipe, i, true);
                    }
                } catch (Throwable t) {
                    LOGGER.error("Direct recipe injection failed at index {}", recipeIndex, t);
                }
                recipeIndex = end;
                if (recipeIndex >= recipes.size()) {
                    LOGGER.info("Recipe injection batch complete: {} recipes", recipes.size());
                }
                return false;
            }

            // Phase 2: fallback rebuild
            if (!DIRECT_INJECTION_AVAILABLE && !fallbackDone) {
                try {
                    ClientRecipeCache.INSTANCE.buildRecipeCache(false);
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