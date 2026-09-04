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
    private static final int SCHEMA_VERSION = 2;

    private static final Map<String, GardenMutation> MUTATIONS = new LinkedHashMap<>();
    private static final Map<String, CropSize> CROP_SIZES = new HashMap<>();
    private static final Map<String, DisplayItem> DISPLAY_ITEMS = new HashMap<>();
    private static boolean loaded = false;

    private GardenMutationRegistry() {
    }

    /**
     * Load mutation data from the classpath resource. Safe to call multiple times.
     */
    public static synchronized void load() {
        if (loaded) return;

        try (var in = GardenMutationRegistry.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                LOGGER.warn("Garden mutation resource not found: {}", RESOURCE_PATH);
                return;
            }
            try (var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                int schemaVersion = root.has("schemaVersion") ? root.get("schemaVersion").getAsInt() : 0;
                if (schemaVersion != SCHEMA_VERSION) {
                    throw new IllegalArgumentException("Unsupported garden mutation schema " + schemaVersion
                            + "; expected " + SCHEMA_VERSION);
                }

                Map<String, CropSize> cropSizes = parseCropSizes(root.getAsJsonObject("cropSizes"));
                Map<String, DisplayItem> displayItems = parseDisplayItems(root.getAsJsonObject("displayItems"));
                Map<String, GardenMutation.Effect> effects = parseEffectCatalog(root.getAsJsonObject("effects"));
                JsonObject mutations = root.getAsJsonObject("mutations");
                if (mutations == null) {
                    throw new IllegalArgumentException("Garden mutation JSON missing 'mutations' object");
                }
                Map<String, GardenMutation> parsed = new LinkedHashMap<>();
                for (String key : mutations.keySet()) {
                    JsonObject obj = mutations.getAsJsonObject(key);
                    parsed.put(key, parseMutation(key, obj, effects));
                }

                Map<String, List<String>> requiredFor = new HashMap<>();
                for (GardenMutation mutation : parsed.values()) {
                    for (GardenMutation.SpreadingCondition condition : mutation.spreadingConditions()) {
                        for (String itemId : condition.itemIds()) {
                            if (parsed.containsKey(itemId)) {
                                requiredFor.computeIfAbsent(itemId, ignored -> new ArrayList<>()).add(mutation.id());
                            }
                        }
                    }
                }
                parsed.replaceAll((id, mutation) -> mutation.withRequiredFor(requiredFor.getOrDefault(id, List.of())));

                validateReferences(parsed, cropSizes);
                MUTATIONS.putAll(parsed);
                CROP_SIZES.putAll(cropSizes);
                DISPLAY_ITEMS.putAll(displayItems);
                loaded = true;
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

    public static DisplayItem getDisplayItem(String id) {
        return DISPLAY_ITEMS.get(id);
    }

    private static GardenMutation parseMutation(String id, JsonObject obj,
                                                Map<String, GardenMutation.Effect> effectCatalog) {
        String name = requireString(obj, "name", id);
        String rarity = requireString(obj, "rarity", id);
        String surface = getString(obj, "surface", "Farmland");
        boolean needsWater = obj.has("needsWater") && obj.get("needsWater").getAsBoolean();
        int stages = obj.has("stages") ? obj.get("stages").getAsInt() : 0;
        long costCoins = obj.has("costCoins") ? obj.get("costCoins").getAsLong() : 0L;
        int rewardCopper = obj.has("rewardCopper") ? obj.get("rewardCopper").getAsInt() : 0;

        List<List<String>> layout = parseLayout(id, obj.getAsJsonObject("layout"));
        List<GardenMutation.SpreadingCondition> conditions = parseConditions(id, obj.getAsJsonArray("requirements"));
        List<GardenMutation.Effect> effects = parseEffects(id, obj.getAsJsonArray("effects"), effectCatalog);
        String specialMechanic = obj.has("specialMechanic") && !obj.get("specialMechanic").isJsonNull()
                ? obj.get("specialMechanic").getAsString() : null;

        return new GardenMutation(id, name, rarity, surface, needsWater,
                stages, costCoins, rewardCopper, layout, conditions, effects, List.of(), specialMechanic);
    }

    private static List<List<String>> parseLayout(String mutationId, JsonObject object) {
        if (object == null) {
            throw new IllegalArgumentException(mutationId + " is missing its layout");
        }
        JsonArray rows = object.getAsJsonArray("rows");
        JsonObject legend = object.getAsJsonObject("legend");
        if (rows == null || rows.isEmpty() || legend == null) {
            throw new IllegalArgumentException(mutationId + " has an incomplete layout");
        }
        List<List<String>> result = new ArrayList<>();
        int width = -1;
        int targets = 0;
        for (JsonElement rowElement : rows) {
            String row = rowElement.getAsString();
            if (width < 0) width = row.length();
            if (row.length() != width || width < 1 || width > 6 || rows.size() > 6) {
                throw new IllegalArgumentException(mutationId + " layout must be a 1-6 cell rectangle");
            }
            List<String> rowList = new ArrayList<>();
            for (int i = 0; i < row.length(); i++) {
                String symbol = String.valueOf(row.charAt(i));
                if (".".equals(symbol)) {
                    rowList.add("EMPTY");
                } else if ("@".equals(symbol)) {
                    rowList.add("TARGET");
                    targets++;
                } else if (legend.has(symbol)) {
                    rowList.add("INGREDIENT:" + requireString(legend, symbol, mutationId + " legend"));
                } else {
                    throw new IllegalArgumentException(mutationId + " layout uses unknown symbol '" + symbol + "'");
                }
            }
            result.add(rowList);
        }
        if (targets == 0) {
            throw new IllegalArgumentException(mutationId + " layout has no target cells");
        }
        return result;
    }

    private static List<GardenMutation.SpreadingCondition> parseConditions(String mutationId, JsonArray array) {
        List<GardenMutation.SpreadingCondition> result = new ArrayList<>();
        if (array == null) return result;
        for (JsonElement e : array) {
            JsonObject obj = e.getAsJsonObject();
            List<String> itemIds = new ArrayList<>();
            String itemId = getString(obj, "item", "");
            if (!itemId.isBlank()) itemIds.add(itemId);
            JsonArray items = obj.getAsJsonArray("items");
            if (items != null) itemIds.addAll(parseStringArray(items));
            int count = obj.has("count") ? obj.get("count").getAsInt() : 0;
            boolean special = obj.has("special") && obj.get("special").getAsBoolean();
            if ((!special && itemIds.isEmpty()) || (obj.has("item") && count < 1)) {
                throw new IllegalArgumentException(mutationId + " has an invalid requirement");
            }
            result.add(new GardenMutation.SpreadingCondition(
                    itemIds, count, requireString(obj, "text", mutationId + " requirement")
            ));
        }
        return result;
    }

    private static List<GardenMutation.Effect> parseEffects(String mutationId, JsonArray array,
                                                            Map<String, GardenMutation.Effect> catalog) {
        List<GardenMutation.Effect> result = new ArrayList<>();
        if (array == null) return result;
        for (JsonElement e : array) {
            String effectId = e.getAsString();
            GardenMutation.Effect effect = catalog.get(effectId);
            if (effect == null) {
                throw new IllegalArgumentException(mutationId + " references unknown effect " + effectId);
            }
            result.add(effect);
        }
        return result;
    }

    private static Map<String, GardenMutation.Effect> parseEffectCatalog(JsonObject object) {
        if (object == null) throw new IllegalArgumentException("Garden mutation JSON missing effect catalog");
        Map<String, GardenMutation.Effect> result = new LinkedHashMap<>();
        for (String id : object.keySet()) {
            JsonObject effect = object.getAsJsonObject(id);
            result.put(id, new GardenMutation.Effect(
                    requireString(effect, "name", "effect " + id),
                    requireString(effect, "description", "effect " + id),
                    effect.has("negative") && effect.get("negative").getAsBoolean()));
        }
        return result;
    }

    private static Map<String, CropSize> parseCropSizes(JsonObject object) {
        Map<String, CropSize> result = new HashMap<>();
        if (object == null) return result;
        for (String id : object.keySet()) {
            JsonObject size = object.getAsJsonObject(id);
            int width = size.get("width").getAsInt();
            int height = size.get("height").getAsInt();
            if (width < 1 || height < 1 || width > 6 || height > 6) {
                throw new IllegalArgumentException("Invalid crop size for " + id);
            }
            result.put(id, new CropSize(width, height));
        }
        return result;
    }

    private static Map<String, DisplayItem> parseDisplayItems(JsonObject object) {
        Map<String, DisplayItem> result = new HashMap<>();
        if (object == null) return result;
        for (String id : object.keySet()) {
            JsonObject display = object.getAsJsonObject(id);
            result.put(id, new DisplayItem(requireString(display, "item", "display item " + id),
                    requireString(display, "name", "display item " + id)));
        }
        return result;
    }

    private static void validateReferences(Map<String, GardenMutation> mutations,
                                           Map<String, CropSize> cropSizes) {
        for (String id : cropSizes.keySet()) {
            if (!mutations.containsKey(id)) {
                throw new IllegalArgumentException("Crop size references unknown mutation " + id);
            }
        }
        for (GardenMutation mutation : mutations.values()) {
            CropSize size = cropSizes.get(mutation.id());
            if (size != null) {
                long targetCells = mutation.layout().stream().flatMap(Collection::stream)
                        .filter("TARGET"::equals).count();
                if (targetCells != size.width() * size.height()) {
                    throw new IllegalArgumentException(mutation.id() + " target occupies " + targetCells
                            + " cells, expected " + (size.width() * size.height()));
                }
            }
        }
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

    private static String requireString(JsonObject obj, String key, String context) {
        String value = getString(obj, key, "");
        if (value.isBlank()) throw new IllegalArgumentException(context + " is missing " + key);
        return value;
    }

    /**
     * Size of a multi-block crop in the garden grid.
     */
    public record CropSize(int width, int height) {
    }

    public record DisplayItem(String itemId, String name) {
    }
}
