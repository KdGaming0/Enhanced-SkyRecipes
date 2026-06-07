package com.github.kdgaming0.skyrecipes.core.model.garden;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Loads and holds garden mutation data from the built-in {@code mutations.json} resource.
 */
public final class GardenMutationRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(GardenMutationRegistry.class);
    private static final String RESOURCE_PATH = "assets/skyrecipes/skyblock_data/mutations.json";

    private static final Map<String, GardenMutation> MUTATIONS = new LinkedHashMap<>();
    private static final Map<String, CropSize> CROP_SIZES = new HashMap<>();
    private static boolean loaded = false;

    private GardenMutationRegistry() {
    }

    /**
     * Size of a multi-block crop in the garden grid.
     */
    public record CropSize(int width, int height) {
    }

    /**
     * Load mutation data from the classpath resource. Safe to call multiple times.
     */
    public static synchronized void load() {
        if (loaded) return;
        loaded = true;

        try (var in = GardenMutationRegistry.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                LOGGER.warn("Garden mutation resource not found: {}", RESOURCE_PATH);
                return;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject mutations = root.getAsJsonObject("mutations");
            if (mutations == null) {
                LOGGER.warn("Garden mutation JSON missing 'mutations' object");
                return;
            }
            for (String key : mutations.keySet()) {
                JsonObject obj = mutations.getAsJsonObject(key);
                GardenMutation m = parseMutation(key, obj);
                if (m != null) {
                    MUTATIONS.put(key, m);
                }
            }

            JsonObject cropSizes = root.getAsJsonObject("cropSizes");
            if (cropSizes != null) {
                for (String key : cropSizes.keySet()) {
                    JsonObject obj = cropSizes.getAsJsonObject(key);
                    int w = obj.has("width") ? obj.get("width").getAsInt() : 1;
                    int h = obj.has("height") ? obj.get("height").getAsInt() : 1;
                    CROP_SIZES.put(key, new CropSize(w, h));
                }
            }

            LOGGER.info("Loaded {} garden mutations", MUTATIONS.size());
        } catch (Exception e) {
            LOGGER.error("Failed to load garden mutations", e);
        }
    }

    /**
     * All loaded mutations in insertion order.
     */
    public static Collection<GardenMutation> all() {
        return Collections.unmodifiableCollection(MUTATIONS.values());
    }

    /**
     * Lookup by mutation ID.
     */
    public static Optional<GardenMutation> get(String id) {
        return Optional.ofNullable(MUTATIONS.get(id));
    }

    /**
     * Crop size for a mutation, or {@code null} if it is a single-cell crop.
     */
    public static CropSize getCropSize(String id) {
        return CROP_SIZES.get(id);
    }

    private static GardenMutation parseMutation(String id, JsonObject obj) {
        try {
            String name = getString(obj, "name", id);
            String rarity = getString(obj, "rarity", "COMMON");
            int gridSize = obj.has("gridSize") ? obj.get("gridSize").getAsInt() : 1;
            String surface = getString(obj, "surface", "Farmland");
            boolean needsWater = obj.has("needsWater") && obj.get("needsWater").getAsBoolean();
            int stages = obj.has("stages") ? obj.get("stages").getAsInt() : 0;
            long costCoins = obj.has("costCoins") ? obj.get("costCoins").getAsLong() : 0L;
            int rewardCopper = obj.has("rewardCopper") ? obj.get("rewardCopper").getAsInt() : 0;

            List<List<String>> layout = parseLayout(obj.getAsJsonArray("layout"));
            List<GardenMutation.SpreadingCondition> conditions = parseConditions(obj.getAsJsonArray("spreadingConditions"));
            List<GardenMutation.Effect> effects = parseEffects(obj.getAsJsonArray("effects"));
            List<String> requiredFor = parseStringArray(obj.getAsJsonArray("requiredFor"));
            String specialMechanic = obj.has("specialMechanic") && !obj.get("specialMechanic").isJsonNull()
                    ? obj.get("specialMechanic").getAsString() : null;

            return new GardenMutation(id, name, rarity, gridSize, surface, needsWater,
                    stages, costCoins, rewardCopper, layout, conditions, effects, requiredFor, specialMechanic);
        } catch (Exception e) {
            LOGGER.warn("Failed to parse garden mutation {}: {}", id, e.getMessage());
            return null;
        }
    }

    private static List<List<String>> parseLayout(JsonArray array) {
        List<List<String>> result = new ArrayList<>();
        if (array == null) return result;
        for (JsonElement row : array) {
            List<String> rowList = new ArrayList<>();
            for (JsonElement cell : row.getAsJsonArray()) {
                rowList.add(cell.getAsString());
            }
            result.add(rowList);
        }
        return result;
    }

    private static List<GardenMutation.SpreadingCondition> parseConditions(JsonArray array) {
        List<GardenMutation.SpreadingCondition> result = new ArrayList<>();
        if (array == null) return result;
        for (JsonElement e : array) {
            JsonObject obj = e.getAsJsonObject();
            result.add(new GardenMutation.SpreadingCondition(
                    getString(obj, "itemId", ""),
                    obj.has("count") ? obj.get("count").getAsInt() : 0,
                    getString(obj, "text", "")
            ));
        }
        return result;
    }

    private static List<GardenMutation.Effect> parseEffects(JsonArray array) {
        List<GardenMutation.Effect> result = new ArrayList<>();
        if (array == null) return result;
        for (JsonElement e : array) {
            JsonObject obj = e.getAsJsonObject();
            result.add(new GardenMutation.Effect(
                    getString(obj, "name", ""),
                    getString(obj, "description", "")
            ));
        }
        return result;
    }

    private static List<String> parseStringArray(JsonArray array) {
        List<String> result = new ArrayList<>();
        if (array == null) return result;
        for (JsonElement e : array) {
            result.add(e.getAsString());
        }
        return result;
    }

    private static String getString(JsonObject obj, String key, String defaultValue) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : defaultValue;
    }
}
