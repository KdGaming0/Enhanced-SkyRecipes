package com.github.kdgaming0.skyrecipes.rrv.plugin;

import cc.cassian.rrv.api.ReliableRecipeViewerClientPlugin;
import cc.cassian.rrv.api.recipe.ItemView;
import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.client.recipe.ClientRecipeCache;
import cc.cassian.rrv.common.config.Configs;
import com.github.kdgaming0.skyrecipes.SkyRecipes;
import com.github.kdgaming0.skyrecipes.client.config.SkyRecipesConfig;
import com.github.kdgaming0.skyrecipes.core.family.FamilyResolver;
import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.recipe.RecipeGenerator;
import com.github.kdgaming0.skyrecipes.core.recipe.RecipeGenerator.RecipeResult;
import com.github.kdgaming0.skyrecipes.core.registry.ConstantsRegistry;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import com.github.kdgaming0.skyrecipes.core.render.ItemStackBuilder;
import com.github.kdgaming0.skyrecipes.core.search.SkyblockSearchIndex;
import com.github.kdgaming0.skyrecipes.rrv.recipe.SkyblockRecipeCache;
import cc.cassian.rrv.common.overlay.itemlist.view.SearchBar;
import com.github.kdgaming0.skyrecipes.mixin.EditBoxAccessor;
import com.mojang.blaze3d.platform.InputConstants;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
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
 *   <li>When stack building finishes, stack-sensitives are registered on the main thread.</li>
 *   <li>When recipe generation finishes, recipes are injected directly into
 *       {@code ClientRecipeCache} via {@code MethodHandle} on the main thread.</li>
 *   <li>Injection happens exactly once per data load. World joins do not trigger re-injection.
 *       A mixin cancels redundant RRV {@code buildRecipeCache(true)} calls when our recipes
 *       are already loaded.</li>
 * </ol>
 */
public class SkyRecipesClientPlugin implements ReliableRecipeViewerClientPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger(SkyRecipesClientPlugin.class);

    /** ----------------------------------------------------------------------
     *  Runtime discovery of RRV's private handleClientRecipe method.
     *  If this fails (RRV renamed/refactored it), we fall back to the stable
     *  provider path and the game continues to work.
     * ---------------------------------------------------------------------- */
    private static final MethodHandle INJECT_RECIPE;
    private static final boolean DIRECT_INJECTION_AVAILABLE;
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

    /** True after the RRV recipe cache has been successfully built with SkyRecipes recipes. */
    private static volatile boolean recipesReady = false;

    /** True after stack-sensitives have been registered. */
    private volatile boolean stacksReady = false;

    /** True after startup finalization to prevent duplicate work. */
    private volatile boolean startupFinalized = false;

    /** True for the very first injection; false after a data reload. */
    private volatile boolean firstInjection = true;

    /** Cache for the raw (unfiltered) recipe generation result. Invalidated on data change. */
    private volatile RecipeResult cachedResult = null;

    /** Cached ItemStacks for stack-sensitive registration. Built lazily. Invalidated on data change. */
    private volatile List<ItemStack> cachedStacks = null;

    /** Search index for SkyBlock item filtering. Built after stacks. Invalidated on data change. */
    private static volatile SkyblockSearchIndex searchIndex = null;

    /** Tracks the background recipe-generation task so we never start two in parallel. */
    private volatile CompletableFuture<?> pendingRecipeGen = null;

    /** Tracks the background stack-building task so we never start two in parallel. */
    private volatile CompletableFuture<?> pendingStackBuild = null;

    /** True after {@link ClientLifecycleEvents#CLIENT_STARTED} fires. */
    private volatile boolean clientStarted = false;

    /** True while a background retry loop is waiting for components to bind. */
    private volatile boolean startupRetryInProgress = false;

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

        // Fallback provider — RRV calls this on every buildRecipeCache.
        ItemView.addClientRecipeProvider(recipeList -> {
            if (recipesReady) {
                RecipeResult result = cachedResult;
                if (result != null) {
                    recipeList.addAll(filterRecipesByConfig(result.recipes()));
                }
            }
        });

        // Register for data arrival (fires immediately if data is already ready).
        SkyRecipes.addDataReadyListener(result -> {
            invalidateCaches();
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
        ClientTickEvents.END_CLIENT_TICK.register(this::handleSearchBarSuggestionCommit);

        LOGGER.info("SkyRecipes RRV client plugin initialized");
    }

    /** Invalidate all caches and reset state. Called when data changes. */
    private void invalidateCaches() {
        cachedResult = null;
        cachedStacks = null;
        searchIndex = null;
        recipesReady = false;
        stacksReady = false;
        startupFinalized = false;
        pendingRecipeGen = null;
        pendingStackBuild = null;
    }

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

    /** Start generating recipes on a background thread if not already running. */
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
                Minecraft mc = Minecraft.getInstance();
                if (mc != null) {
                    mc.execute(() -> {
                        cachedResult = result;
                        LOGGER.info("Background recipe generation complete: {} recipes",
                            result.recipes().size());
                        tryFinalizeStartup(mc);
                    });
                }
            }
        });
    }

    /** Start building ItemStacks on a background thread if not already running. */
    private void startBackgroundStacks() {
        if (!SkyRecipes.isDataReady()) return;
        if (cachedStacks != null) return;
        if (pendingStackBuild != null && !pendingStackBuild.isDone()) return;

        CompletableFuture<List<ItemStack>> future = CompletableFuture.supplyAsync(this::buildAllStacks);
        pendingStackBuild = future;
        future.thenAccept(stacks -> {
            // If invalidateCaches() ran while we were building, discard this result.
            if (pendingStackBuild != future) return;

            cachedStacks = stacks;
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.execute(this::registerStackSensitives);
            }
        });
    }

    /**
     * Inject recipes into RRV's cache.
     *
     * <p>If direct injection is available, recipes are added one-by-one via
     * {@code handleClientRecipe}. Otherwise we fall back to calling
     * {@code buildRecipeCache(false)}.</p>
     */
    private void tryFinalizeStartup(Minecraft client) {
        if (startupFinalized) return;
        if (cachedResult == null) return;

        List<ReliableClientRecipe> recipes = filterRecipesByConfig(cachedResult.recipes());
        if (recipes.isEmpty()) {
            LOGGER.warn("Recipe generation produced zero recipes, skipping injection");
            return;
        }

        try {
            if (DIRECT_INJECTION_AVAILABLE) {
                injectDirectly(recipes);
            } else {
                // Fallback: use the stable rebuild path.
                ClientRecipeCache.INSTANCE.buildRecipeCache(false);
            }

            // Build family resolver and rebuild parallel SkyBlock-ID index
            FamilyResolver familyResolver = new FamilyResolver(
                SkyRecipes.getConstantsRegistry(),
                SkyRecipes.getItemRegistry()
            );
            SkyblockRecipeCache.setFamilyResolver(familyResolver);
            SkyblockRecipeCache.rebuild(recipes);

            recipesReady = true;
            startupFinalized = true;
            firstInjection = false;

            LOGGER.info("SkyRecipes startup complete: {} recipes injected into RRV",
                recipes.size());
        } catch (Throwable t) {
            LOGGER.error("Failed to inject recipes into RRV", t);
            // Even if injection fails, mark ready so the provider can serve recipes
            // on the next natural RRV rebuild.
            recipesReady = true;
            startupFinalized = true;
            firstInjection = false;
        }
    }

    /** Direct cache injection via MethodHandle. Must run on the main thread. */
    private void injectDirectly(List<ReliableClientRecipe> recipes) throws Throwable {
        ClientRecipeCache cache = ClientRecipeCache.INSTANCE;

        // On reload, clear stale client recipes before re-injecting.
        if (!firstInjection) {
            cache.clear();
            LOGGER.debug("Cleared stale client recipes before re-injection");
        }

        for (int i = 0; i < recipes.size(); i++) {
            ReliableClientRecipe recipe = recipes.get(i);
            INJECT_RECIPE.invokeExact(cache, recipe.entryId(), recipe, i, true);
        }

        Configs.CATEGORIES.addNewCategories();
    }

    /** Generate all recipes. Returns null on failure. */
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

    private List<ReliableClientRecipe> filterRecipesByConfig(List<ReliableClientRecipe> recipes) {
        List<ReliableClientRecipe> filtered = new ArrayList<>();
        for (ReliableClientRecipe recipe : recipes) {
            String categoryId = recipe.getType().getId().getPath();
            if (SkyRecipesConfig.isCategoryEnabled(categoryId)) {
                filtered.add(recipe);
            }
        }
        return filtered;
    }

    /** Build ItemStacks for all NeuItems. Safe to call from any thread once components are bound. */
    private List<ItemStack> buildAllStacks() {
        ItemRegistry registry = SkyRecipes.getItemRegistry();
        List<ItemStack> stacks = new ArrayList<>();
        if (registry == null) return stacks;
        for (NeuItem item : registry.getAllItems()) {
            try {
                ItemStack stack = ItemStackBuilder.build(item);
                if (!stack.isEmpty() && stack.getItem() != Items.BARRIER) {
                    stacks.add(stack);
                }
            } catch (Exception e) {
                LOGGER.debug("Failed to build stack for {}: {}", item.internalName(), e.getMessage());
            }
        }
        return stacks;
    }

    /**
     * Register all built ItemStacks as stack-sensitives with RRV.
     *
     * <p>Must run on the main thread because it touches RRV client state.</p>
     */
    private void registerStackSensitives() {
        ItemRegistry registry = SkyRecipes.getItemRegistry();
        if (registry == null) {
            LOGGER.warn("ItemRegistry not available, skipping stack-sensitive registration");
            return;
        }

        List<ItemStack> stacks = cachedStacks;
        if (stacks == null) {
            LOGGER.warn("Stack-sensitives not yet built, skipping registration");
            return;
        }

        ItemView.getStackSensitive().clear();
        ClientRecipeCache.INSTANCE.clearStackSensitives();

        for (ItemStack stack : stacks) {
            ClientRecipeCache.INSTANCE.addStackSensitive(new ItemView.StackSensitive(stack));
            ItemView.addStackSensitive(stack);
        }

        stacksReady = true;
        LOGGER.info("Registered {} stack-sensitives with RRV", stacks.size());

        // Build search index now that stacks are registered
        buildSearchIndex(stacks);
    }

    private void buildSearchIndex(List<ItemStack> stacks) {
        ItemRegistry registry = SkyRecipes.getItemRegistry();
        ConstantsRegistry constants = SkyRecipes.getConstantsRegistry();
        if (registry == null || constants == null) {
            LOGGER.warn("Cannot build search index: registries not available");
            return;
        }
        try {
            searchIndex = new SkyblockSearchIndex(stacks, registry, constants, ALIASES);
        } catch (Exception e) {
            LOGGER.error("Failed to build SkyblockSearchIndex", e);
        }
    }

    /**
     * Returns the current SkyBlock search index, or {@code null} if it has not been
     * built yet (data not loaded or still processing).
     */
    public static SkyblockSearchIndex getSearchIndex() {
        return searchIndex;
    }

    /** Alias map exposed for {@link com.github.kdgaming0.skyrecipes.core.search.SearchAutocomplete}. */
    public static final Map<String, String> ALIASES;
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

    /** Exposed to the mixin that skips redundant RRV cache rebuilds. */
    public static boolean areRecipesReady() {
        return recipesReady;
    }

    // ── Autocomplete suggestion commit (Right Arrow / Tab) ─────────────────────

    private boolean wasRightArrowDown = false;
    private boolean wasTabDown = false;

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
}
