package com.github.kdgaming0.skyrecipes.rrv.overlay;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kdgaming0.skyrecipes.client.config.SkyRecipesConfig;
import com.github.kdgaming0.skyrecipes.core.util.SkyblockIdExtractor;
import com.github.kdgaming0.skyrecipes.rrv.recipe.type.SkyblockCraftingRecipeType;
import com.github.kdgaming0.skyrecipes.rrv.recipe.type.SkyblockForgeRecipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * SkyBlock-aware "Craftables" index for RRV's side panel.
 *
 * <p>RRV's own craftables computation matches ingredients by vanilla {@link net.minecraft.world.item.Item}
 * type and skips {@code isVisualOnly()} recipes, so SkyBlock recipes (all visual-only, and mostly
 * sharing a handful of base items) can never appear there. This class maintains a flattened
 * ingredient index over the crafting and forge recipe categories, keyed by SkyBlock internal ID,
 * and computes which results the player's current inventory can make.</p>
 *
 * <p><b>Rebuild</b> runs once per data cycle on a pipeline worker (same place
 * {@code SkyblockRecipeCache} rebuilds). <b>Lookup</b> runs on RRV's background executor inside
 * {@code SidePanelOverlay.updateSidePanelIndex}; the result is memoized against the owned-item
 * snapshot so repeated calls (search keystrokes) cost one inventory scan and a map comparison.</p>
 */
public final class SkyblockCraftablesIndex {

    private static final Logger LOGGER = LoggerFactory.getLogger(SkyblockCraftablesIndex.class);

    /**
     * One craftable recipe, flattened for matching: parallel ingredient id/count arrays
     * (counts summed across grid slots) and the result stack to display.
     */
    private record Entry(String[] ingredientIds, int[] ingredientCounts, ItemStack resultStack, String resultId) {
    }

    private static volatile List<Entry> entries = List.of();

    // Memoized last computation. Guarded by CACHE_LOCK; entries identity doubles as
    // the data-cycle invalidation token.
    private static final Object CACHE_LOCK = new Object();
    private static Map<String, Integer> cachedOwned;
    private static List<ItemStack> cachedResult;
    private static boolean cachedCountAware;
    private static List<Entry> cachedEntries;

    private SkyblockCraftablesIndex() {
    }

    /**
     * Rebuild the craftables index from the full client-recipe list. Thread-safe;
     * called from the pipeline worker after each data cycle.
     */
    public static void rebuild(List<ReliableClientRecipe> recipes) {
        if (recipes == null || recipes.isEmpty()) {
            publish(List.of());
            return;
        }
        List<Entry> built = new ArrayList<>();
        for (ReliableClientRecipe recipe : recipes) {
            try {
                Entry entry = flatten(recipe);
                if (entry != null) {
                    built.add(entry);
                }
            } catch (Exception e) {
                LOGGER.debug("Skipping recipe {} in craftables index", recipe.getId(), e);
            }
        }
        publish(List.copyOf(built));
        LOGGER.debug("Craftables index rebuilt: {} recipes", built.size());
    }

    private static void publish(List<Entry> built) {
        entries = built;
        synchronized (CACHE_LOCK) {
            cachedOwned = null;
            cachedResult = null;
            cachedEntries = null;
        }
    }

    private static Entry flatten(ReliableClientRecipe recipe) {
        ReliableClientRecipeType type = recipe.getType();
        if (type != SkyblockCraftingRecipeType.INSTANCE && type != SkyblockForgeRecipeType.INSTANCE) {
            return null;
        }

        List<SlotContent> results = recipe.getResults();
        if (results.isEmpty()) {
            return null;
        }
        ItemStack resultStack = firstContent(results.getFirst());
        if (resultStack == null) {
            return null;
        }
        String resultId = keyFor(resultStack);
        if (resultId == null) {
            return null;
        }

        Map<String, Integer> required = new HashMap<>(8);
        for (SlotContent slot : recipe.getIngredients()) {
            ItemStack ingredient = firstContent(slot);
            if (ingredient == null) {
                continue;
            }
            String key = keyFor(ingredient);
            if (key == null) {
                continue;
            }
            required.merge(key, Math.max(1, ingredient.getCount()), Integer::sum);
        }
        if (required.isEmpty()) {
            return null;
        }

        String[] ids = new String[required.size()];
        int[] counts = new int[required.size()];
        int i = 0;
        for (Map.Entry<String, Integer> e : required.entrySet()) {
            ids[i] = e.getKey();
            counts[i] = e.getValue();
            i++;
        }
        return new Entry(ids, counts, resultStack, resultId);
    }

    private static ItemStack firstContent(SlotContent slot) {
        if (slot == null || slot.isEmpty()) {
            return null;
        }
        List<ItemStack> contents = slot.getValidContents();
        if (contents.isEmpty()) {
            return null;
        }
        ItemStack stack = contents.getFirst();
        return stack == null || stack.isEmpty() ? null : stack;
    }

    /**
     * Matching key for a stack: the SkyBlock internal name when present, otherwise the
     * uppercased vanilla item path (NEU ids for plain materials follow the same convention,
     * e.g. {@code minecraft:diamond} → {@code DIAMOND}), so untagged vanilla inventory
     * stacks can still satisfy base-material ingredients.
     */
    private static String keyFor(ItemStack stack) {
        String id = SkyblockIdExtractor.extractInternalName(stack);
        if (id != null) {
            return id;
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toUpperCase(Locale.ROOT);
    }

    /**
     * Appends every SkyBlock item craftable from the player's current inventory to
     * {@code out}. Called on RRV's background executor from the side-panel mixin;
     * must never throw (an exception would kill RRV's whole side-panel task).
     */
    public static void appendCraftables(List<ItemStack> out) {
        try {
            List<Entry> index = entries;
            if (index.isEmpty()) {
                return;
            }
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) {
                return;
            }
            Map<String, Integer> owned = collectOwned(player);
            if (owned.isEmpty()) {
                return;
            }

            boolean countAware = SkyRecipesConfig.craftablesCountAware;
            List<ItemStack> result;
            synchronized (CACHE_LOCK) {
                if (cachedResult != null && cachedEntries == index
                        && cachedCountAware == countAware && owned.equals(cachedOwned)) {
                    result = cachedResult;
                } else {
                    result = compute(index, owned, countAware);
                    cachedOwned = owned;
                    cachedResult = result;
                    cachedCountAware = countAware;
                    cachedEntries = index;
                }
            }
            out.addAll(result);
        } catch (Exception e) {
            LOGGER.debug("SkyBlock craftables computation failed", e);
        }
    }

    private static Map<String, Integer> collectOwned(LocalPlayer player) {
        Map<String, Integer> owned = new HashMap<>(48);
        // Live inventory list, read off-thread exactly like RRV's own craftables scan;
        // index-bounded iteration plus the outer catch guard against concurrent resize.
        List<ItemStack> inventory = player.getInventory().getNonEquipmentItems();
        for (int i = 0, n = inventory.size(); i < n; i++) {
            ItemStack stack = inventory.get(i);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            owned.merge(keyFor(stack), stack.getCount(), Integer::sum);
        }
        return owned;
    }

    private static List<ItemStack> compute(List<Entry> index, Map<String, Integer> owned, boolean countAware) {
        List<ItemStack> result = new ArrayList<>();
        Set<String> seenResults = new HashSet<>();
        for (Entry entry : index) {
            String[] ids = entry.ingredientIds();
            int[] counts = entry.ingredientCounts();
            boolean craftable = true;
            for (int i = 0; i < ids.length; i++) {
                Integer have = owned.get(ids[i]);
                if (have == null || (countAware && have < counts[i])) {
                    craftable = false;
                    break;
                }
            }
            if (craftable && seenResults.add(entry.resultId())) {
                result.add(entry.resultStack());
            }
        }
        return List.copyOf(result);
    }
}
