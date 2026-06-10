package com.github.kdgaming0.skyrecipes.rrv.plugin;

import cc.cassian.rrv.api.ReliableRecipeViewerClientPlugin;
import cc.cassian.rrv.api.recipe.ItemView;
import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.client.recipe.ClientRecipeCache;
import cc.cassian.rrv.common.config.Configs;
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

/**
 * RRV client-side plugin entrypoint.
 *
 * <p>Uses internal cache injection for speed, with runtime discovery of RRV's
 * private API so the game never crashes on version mismatch — it degrades to
 * provider-only mode and logs a loud warning instead.</p>
 *
 * <p><b>Startup flow:</b></p>
 * <ol>
 *   <li>Recipe provider is registered as a fallback.</li>
 *   <li>NEU data loads in the background (warm or cold start).</li>
 *   <li>When the Minecraft client is fully initialized ({@code CLIENT_STARTED} event),
 *       a background retry loop waits until data components are bound, then starts
 *       recipe generation and stack building in parallel on the ForkJoinPool.</li>
 *   <li>If data arrives after {@code CLIENT_STARTED}, the same component check runs
 *       before work begins.</li>
 *   <li>All heavy CPU work (recipe generation, stack building, search-index creation,
 *       family resolution, and SkyBlock recipe caching) happens on background threads.</li>
 *   <li>When all background prep is complete, a lightweight batched injector runs on
 *       the render thread, spreading RRV cache mutations across many ticks so no
 *       single frame drops.</li>
 *   <li>Injection happens exactly once per data load. World joins do not trigger re-injection.
 *       A mixin cancels redundant RRV {@code buildRecipeCache(true)} calls when our recipes
 *       are already loaded.</li>
 * </ol>
 */
public class SkyRecipesClientPlugin implements ReliableRecipeViewerClientPlugin {

    /**
     * Alias map exposed for {@link com.github.kdgaming0.skyrecipes.core.search.SearchAutocomplete}.
     */
    public static final Map<String, String> ALIASES;
    private static final Logger LOGGER = LoggerFactory.getLogger(SkyRecipesClientPlugin.class);
    /**
     * ----------------------------------------------------------------------
     * Runtime discovery of RRV's private handleClientRecipe method.
     * If this fails (RRV renamed/refactored it), we fall back to the stable
     * provider path and the game continues to work.
     * ----------------------------------------------------------------------
     */
    private static final MethodHandle INJECT_RECIPE;
    private static final boolean DIRECT_INJECTION_AVAILABLE;
    /**
     * True after the RRV recipe cache has been successfully built with SkyRecipes recipes.
     */
    private static volatile boolean recipesReady = false;
    /**
     * Search index for SkyBlock item filtering. Built after stacks. Invalidated on data change.
     */
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

    // ---- Batched-injection state ------------------------------------------------

    private static final int RECIPES_PER_TICK = 500;
    private static final int STACKS_PER_TICK = 250;

    /**
     * True after stack-sensitives have been registered.
     */
    private volatile boolean stacksReady = false;
    /**
     * True after startup finalization to prevent duplicate work.
     */
    private volatile boolean startupFinalized = false;
    /**
     * True for the very first injection; false after a data reload.
     */
    private volatile boolean firstInjection = true;
    /**
     * Cache for the raw (unfiltered) recipe generation result. Invalidated on data change.
     */
    private volatile RecipeResult cachedResult = null;
    /**
     * Cached ItemStacks for stack-sensitive registration. Built lazily. Invalidated on data change.
     */
    private volatile List<ItemStack> cachedStacks = null;
    /**
     * Tracks the background recipe-generation task so we never start two in parallel.
     */
    private volatile CompletableFuture<?> pendingRecipeGen = null;
    /**
     * Tracks the background stack-building task so we never start two in parallel.
     */
    private volatile CompletableFuture<?> pendingStackBuild = null;
    /**
     * True after {@link ClientLifecycleEvents#CLIENT_STARTED} fires.
     */
    private volatile boolean clientStarted = false;
    /**
     * True while a background retry loop is waiting for components to bind.
     */
    private volatile boolean startupRetryInProgress = false;

    // ---- Coordinated background-completion state --------------------------------

    private volatile RecipeResult pendingRecipeResult = null;
    private volatile List<ItemStack> pendingStacks = null;
    private volatile SkyblockSearchIndex pendingSearchIndex = null;
    private volatile boolean backgroundPrepComplete = false;
    private volatile boolean injectionStarted = false;
    private StartupBatcher startupBatcher = null;

    private boolean wasRightArrowDown = false;
    private boolean wasTabDown = false;

    /**
     * Check if Minecraft data components are bound and safe to use.
     *
     * <p>During early client init, {@link net.minecraft.core.Holder.Reference#components()}
     * may throw because component binding happens after registry freeze. This test creates
     * a dummy stack to verify the component system is fully initialized.</p>
     */
    private static boolean areComponentsBound() {
        try {
            ItemStack test = new ItemStack(Items.DIAMOND);
            test.set(DataComponents.CUSTOM_NAME, Component.literal("test"));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns the current SkyBlock search index, or {@code null} if it has not been
     * built yet (data not loaded or still processing).
     */
    public static SkyblockSearchIndex getSearchIndex() {
        return searchIndex;
    }

    private static Item resolveAliasItem(com.github.kdgaming0.skyrecipes.core.model.NeuItem neuItem) {
        String itemId = neuItem.itemId();
        if (itemId == null || itemId.isEmpty()) {
            return null;
        }
        net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(itemId);
        if (id == null) {
            return null;
        }
        return BuiltInRegistries.ITEM.getOptional(id).orElse(null);
    }

    /**
     * Exposed to the mixin that skips redundant RRV cache rebuilds.
     */
    public static boolean areRecipesReady() {
        return recipesReady;
    }

    @Override
    public void onIntegrationInitialize() {
        LOGGER.info("SkyRecipes RRV client plugin initializing...");
        if (!DIRECT_INJECTION_AVAILABLE) {
            LOGGER.warn("================================================================================");
            LOGGER.warn("  SkyRecipes is running in PROVIDER-ONLY mode.");
            LOGGER.warn("  Internal cache injection is UNAVAILABLE.");
            LOGGER.warn("  Recipes will still work, but startup may be slower and world-join");
            LOGGER.warn("  cache rebuilds will not be suppressed.");
            LOGGER.warn("  This usually means RRV was updated and its internal API changed.");
            LOGGER.warn("  Please report this to the SkyRecipes issue tracker.");
            LOGGER.warn("================================================================================");
        }

        // Exclude all built-in RRV / vanilla recipe categories.
        // SkyRecipes registers its own types (skyrecipes:*) so these are redundant.
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

        // Fallback provider — RRV calls this on every buildRecipeCache.
        ItemView.addClientRecipeProvider(recipeList -> {
            if (recipesReady) {
                RecipeResult result = cachedResult;
                if (result != null) {
                    recipeList.addAll(result.recipes());
                }
            }
        });

        // Register for data arrival (fires immediately if data is already ready).
        SkyRecipes.addDataReadyListener(result -> {
            invalidateCaches();
            startHypixelFetch();
            startWorkIfReady();

            // Alias registration and autocomplete touch RRV client state that is not
            // thread-safe; marshal to the render thread.
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> {
                    SkyRecipes.buildSearchAutocomplete();
                    registerAliases();
                });
            }
        });

        // Defer all heavy RRV work until the client is fully initialized.
        // This guarantees components are bound and avoids races during early startup.
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            clientStarted = true;
            startWorkIfReady();
        });

        // Right-arrow / Tab commits the autocomplete suggestion in the RRV search bar.
        // Also drives the batched startup injector so the render thread never freezes.
        ClientTickEvents.END_CLIENT_TICK.register(this::handleEndClientTick);

        LOGGER.info("SkyRecipes RRV client plugin initialized");
    }

    /**
     * Invalidate all caches and reset state. Called when data changes.
     */
    private void invalidateCaches() {
        cachedResult = null;
        cachedStacks = null;
        searchIndex = null;
        recipesReady = false;
        stacksReady = false;
        startupFinalized = false;
        pendingRecipeGen = null;
        pendingStackBuild = null;

        pendingRecipeResult = null;
        pendingStacks = null;
        pendingSearchIndex = null;
        backgroundPrepComplete = false;
        injectionStarted = false;
        startupBatcher = null;
    }

    /**
     * Start background work if data is ready, the client has started, and components are bound.
     *
     * <p>If components are not yet bound (e.g. {@code CLIENT_STARTED} fired before the first
     * resource reload completed), a single background retry loop polls every 50 ms until
     * binding is complete. This keeps all waiting off the render thread.</p>
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
                startBackgroundRecipes();
                startBackgroundStacks();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.debug("Startup retry loop interrupted");
            } finally {
                startupRetryInProgress = false;
            }
        });
    }

    /**
     * Fetch Hypixel API items data in the background for accurate essence upgrade stats.
     * Falls back to disk cache if the network request fails.
     */
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

    // ---- Coordinated background completion --------------------------------------

    /**
     * Start generating recipes on a background thread if not already running.
     * When complete, stores the result and triggers coordinated prep.
     */
    private void startBackgroundRecipes() {
        if (!SkyRecipes.isDataReady()) return;
        if (cachedResult != null) return;
        if (pendingRecipeGen != null && !pendingRecipeGen.isDone()) return;

        CompletableFuture<RecipeResult> future = CompletableFuture.supplyAsync(this::generateRecipes);
        pendingRecipeGen = future;
        future.thenAccept(result -> {
            // If invalidateCaches() ran while we were generating, discard this result.
            if (pendingRecipeGen != future) return;

            if (result != null) {
                pendingRecipeResult = result;
                LOGGER.info("Background recipe generation complete: {} recipes",
                        result.recipes().size());
                maybeStartBackgroundPrep();
            }
        });
    }

    /**
     * Start building ItemStacks and the search index on a background thread.
     * When complete, stores the results and triggers coordinated prep.
     */
    private void startBackgroundStacks() {
        if (!SkyRecipes.isDataReady()) return;
        if (cachedStacks != null) return;
        if (pendingStackBuild != null && !pendingStackBuild.isDone()) return;

        CompletableFuture<StackBuildResult> future = CompletableFuture.supplyAsync(this::buildAllStacksAndIndex);
        pendingStackBuild = future;
        future.thenAccept(result -> {
            // If invalidateCaches() ran while we were building, discard this result.
            if (pendingStackBuild != future) return;

            if (result != null) {
                pendingStacks = result.stacks();
                pendingSearchIndex = result.index();
                LOGGER.info("Background stack building complete: {} stacks",
                        result.stacks().size());
                maybeStartBackgroundPrep();
            }
        });
    }

    /**
     * Once both recipe generation and stack building have finished, build the
     * remaining heavy indexes (FamilyResolver, SkyblockRecipeCache) on a background
     * thread, then marshal to the render thread to begin batched injection.
     */
    private void maybeStartBackgroundPrep() {
        if (pendingRecipeResult == null || pendingStacks == null) return;
        if (backgroundPrepComplete) return;
        backgroundPrepComplete = true;

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
            if (mc != null) {
                mc.execute(() -> beginBatchedInjection());
            }
        }).exceptionally(throwable -> {
            LOGGER.error("Background prep (FamilyResolver/SkyblockRecipeCache) failed", throwable);
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> beginBatchedInjection());
            }
            return null;
        });
    }

    /**
     * Begin batched injection on the render thread. Called once all background prep
     * is complete. Clears stale caches and initialises the {@link StartupBatcher}.
     */
    private void beginBatchedInjection() {
        if (injectionStarted) return;
        injectionStarted = true;

        RecipeResult result = pendingRecipeResult;
        List<ItemStack> stacks = pendingStacks;
        if (result == null || stacks == null) {
            LOGGER.warn("Cannot begin batched injection: missing result or stacks");
            return;
        }

        // Publish volatile fields for provider and external access
        cachedResult = result;
        cachedStacks = stacks;
        searchIndex = pendingSearchIndex;

        List<ReliableClientRecipe> recipes = result.recipes();

        // Clear old caches before batching
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
     * Called every client tick. Handles the autocomplete suggestion commit and,
     * when active, drives the batched injector forward one batch at a time.
     */
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

    private void finishStartup(Minecraft client, List<ReliableClientRecipe> recipes) {
        recipesReady = true;
        startupFinalized = true;
        firstInjection = false;
        LOGGER.info("SkyRecipes startup complete: {} recipes injected into RRV",
                recipes.size());
    }

    // ---- Core generation / build helpers ----------------------------------------

    /**
     * Generate all recipes. Returns null on failure.
     */
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

    /**
     * Build ItemStacks for all NeuItems and the search index in one background pass.
     */
    private StackBuildResult buildAllStacksAndIndex() {
        List<ItemStack> stacks = buildAllStacks();
        SkyblockSearchIndex index = buildSearchIndex(stacks);
        return new StackBuildResult(stacks, index);
    }

    /**
     * Build ItemStacks for all NeuItems. Safe to call from any thread once components are bound.
     */
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

    // ── Autocomplete suggestion commit (Right Arrow / Tab) ─────────────────────

    private void registerAliases() {
        ItemRegistry registry = SkyRecipes.getItemRegistry();
        if (registry == null) return;

        for (Map.Entry<String, String> entry : ALIASES.entrySet()) {
            registry.getByInternalName(entry.getValue()).ifPresent(neuItem -> {
                try {
                    Item item = resolveAliasItem(neuItem);
                    if (item != null) {
                        ItemView.addAlias(item, entry.getKey());
                    }
                } catch (Exception e) {
                    LOGGER.debug("Failed to register alias for {}", entry.getValue());
                }
            });
        }
    }

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

    // ---- Supporting records -----------------------------------------------------

    /**
     * Result of building stacks and search index together on a background thread.
     */
    private record StackBuildResult(List<ItemStack> stacks, SkyblockSearchIndex index) {
    }

    /**
     * Pre-computed sort key for NeuItems. Groups family members together and
     * orders tiered items numerically (e.g. Minion I before Minion XII).
     */
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

    // ---- Batched injector -------------------------------------------------------

    /**
     * Injects recipes and stack-sensitives into RRV in small batches across many
     * ticks so no single frame is dropped.
     *
     * <p>The batcher runs through four phases:</p>
     * <ol>
     *   <li>Recipe injection (batched)</li>
     *   <li>Fallback cache rebuild (if direct injection is unavailable)</li>
     *   <li>Category registration</li>
     *   <li>Stack-sensitive registration (batched)</li>
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

        /**
         * Process one batch. Returns {@code true} when all phases are complete.
         */
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
                    // Continue with remaining recipes on next ticks
                }
                recipeIndex = end;
                if (recipeIndex >= recipes.size()) {
                    LOGGER.info("Recipe injection batch complete: {} recipes", recipes.size());
                }
                return false;
            }

            // Phase 2: fallback rebuild if direct injection is unavailable
            if (!DIRECT_INJECTION_AVAILABLE && !fallbackDone) {
                try {
                    ClientRecipeCache.INSTANCE.buildRecipeCache(false);
                } catch (Exception e) {
                    LOGGER.error("Fallback recipe cache rebuild failed", e);
                }
                fallbackDone = true;
                return false;
            }

            // Phase 3: register new categories once recipes are in
            if (!categoriesAdded) {
                try {
                    Configs.CATEGORIES.addNewCategories();
                } catch (Exception e) {
                    LOGGER.warn("Failed to add new RRV categories", e);
                }
                categoriesAdded = true;
                return false;
            }

            // Phase 4: batch-register stack-sensitives
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

            // All phases complete
            return true;
        }
    }
}
