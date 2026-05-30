package com.github.kdgaming0.skyrecipes.core.data;

import com.github.kdgaming0.skyrecipes.core.model.*;
import com.github.kdgaming0.skyrecipes.core.util.JsonUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePacker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Build-time compiler that downloads the NEU repository from GitHub,
 * parses all items and constants, and compiles them into a binary .mpk file.
 *
 * <p>This class is executed as a Gradle task (compileNeuData) and is not used at runtime.
 */
public class BinaryDataCompiler {

    private static final Logger LOGGER = LoggerFactory.getLogger("SkyRecipesCompiler");

    private static final String NEU_REPO_URL =
        "https://codeload.github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/zip/refs/heads/master";

    private static final byte[] MAGIC = new byte[] { 'S', 'K', 'Y', '1' };
    private static final int SCHEMA_VERSION = 1;

    public static void main(String[] args) throws Exception {
        String outputDir = args.length > 0 ? args[0] : "build/generated/skyrecipes/data";
        String cacheDir = System.getProperty("skyrecipes.cacheDir",
            System.getProperty("user.home") + "/.gradle/skyrecipes-cache");

        new BinaryDataCompiler().compile(outputDir, cacheDir);
    }

    public void compile(String outputDirPath, String cacheDirPath) throws Exception {
        Path cacheDir = Path.of(cacheDirPath);
        Files.createDirectories(cacheDir);

        Path zipFile = cacheDir.resolve("neu-repo.zip");
        Path etagFile = cacheDir.resolve("neu-repo.etag");

        // ETag-based download
        String etag = readEtag(etagFile);
        boolean downloaded = downloadNeuRepo(zipFile, etagFile, etag);
        if (downloaded) {
            LOGGER.info("Downloaded fresh NEU repo from GitHub");
        } else {
            LOGGER.info("Using cached NEU repo (ETag match)");
        }

        // Parse
        List<NeuItem> items = new ArrayList<>();
        Map<String, List<String>> parents = new LinkedHashMap<>();
        Map<String, EssenceUpgradeData> essenceCosts = new LinkedHashMap<>();
        Set<String> bazaarItems = new HashSet<>();
        Map<String, String> museumCategories = new LinkedHashMap<>();

        parseZip(zipFile, items, parents, essenceCosts, bazaarItems, museumCategories);

        LOGGER.info("Parsed {} items", items.size());
        LOGGER.info("Constants: {} parents, {} essence costs, {} bazaar items, {} museum entries",
            parents.size(), essenceCosts.size(), bazaarItems.size(), museumCategories.size());

        // Write binary
        Path outputPath = Path.of(outputDirPath, "skyrecipes_data_v" + SCHEMA_VERSION + ".mpk");
        Files.createDirectories(outputPath.getParent());

        try (OutputStream fos = Files.newOutputStream(outputPath);
             BufferedOutputStream bos = new BufferedOutputStream(fos)) {

            // Reserve space for header (we'll write it after we know section offsets)
            byte[] header = new byte[64];
            bos.write(header);

            long itemDataOffset = 64L; // header size

            // Write items section
            ByteArrayOutputStream itemsBaos = new ByteArrayOutputStream();
            try (MessagePacker packer = MessagePack.newDefaultPacker(itemsBaos)) {
                packItems(packer, items);
            }
            byte[] itemsBytes = itemsBaos.toByteArray();
            bos.write(itemsBytes);

            long itemDataLength = itemsBytes.length;

            // Write constants section
            ByteArrayOutputStream constantsBaos = new ByteArrayOutputStream();
            try (MessagePacker packer = MessagePack.newDefaultPacker(constantsBaos)) {
                packConstants(packer, parents, essenceCosts, bazaarItems, museumCategories);
            }
            byte[] constantsBytes = constantsBaos.toByteArray();
            bos.write(constantsBytes);

            long constantsLength = constantsBytes.length;

            // Write header at the beginning
            try (RandomAccessFile raf = new RandomAccessFile(outputPath.toFile(), "rw")) {
                raf.write(MAGIC);
                raf.writeInt(SCHEMA_VERSION);
                raf.writeLong(System.currentTimeMillis());
                raf.writeInt(items.size());
                raf.writeInt(2); // section count: items + constants
                raf.writeLong(0L); // commit hash placeholder
                raf.writeLong(64); // item data offset (right after header)
                raf.writeLong(itemDataLength);
                raf.writeLong(64 + itemDataLength); // constants offset
                raf.writeLong(constantsLength);
            }
        }

        LOGGER.info("Wrote binary: {} ({} bytes)", outputPath, Files.size(outputPath));
    }

    private String readEtag(Path etagFile) {
        try {
            if (Files.exists(etagFile)) {
                return Files.readString(etagFile, StandardCharsets.UTF_8).trim();
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to read ETag file", e);
        }
        return null;
    }

    private boolean downloadNeuRepo(Path zipFile, Path etagFile, String existingEtag) {
        try {
            URL url = new URL(NEU_REPO_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setInstanceFollowRedirects(true);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                LOGGER.warn("HEAD request returned {}, using cache if available", responseCode);
                return false;
            }

            String newEtag = conn.getHeaderField("ETag");
            conn.disconnect();

            if (newEtag != null && newEtag.equals(existingEtag) && Files.exists(zipFile)) {
                return false; // Cache hit
            }

            // Download full ZIP
            LOGGER.info("Downloading NEU repo...");
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);
            conn.setInstanceFollowRedirects(true);

            try (InputStream in = conn.getInputStream();
                 OutputStream out = Files.newOutputStream(zipFile)) {
                in.transferTo(out);
            }

            if (newEtag != null) {
                Files.writeString(etagFile, newEtag, StandardCharsets.UTF_8);
            }
            return true;

        } catch (IOException e) {
            LOGGER.warn("Failed to check/download NEU repo, using cache if available", e);
            return false;
        }
    }

    private void parseZip(Path zipFile, List<NeuItem> items, Map<String, List<String>> parents,
                          Map<String, EssenceUpgradeData> essenceCosts, Set<String> bazaarItems,
                          Map<String, String> museumCategories) throws IOException {

        String prefix = null;

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();

                // Determine the root prefix (e.g. "NotEnoughUpdates-REPO-master/")
                if (prefix == null && name.contains("/")) {
                    prefix = name.substring(0, name.indexOf('/') + 1);
                }

                if (entry.isDirectory()) continue;

                // Read entry bytes into memory to avoid closing the ZipInputStream
                byte[] bytes = zis.readAllBytes();

                try {
                    if (name.startsWith(prefix + "items/") && name.endsWith(".json")) {
                        NeuItem item = parseItem(bytes);
                        if (item != null) {
                            items.add(item);
                        }
                    } else if (name.equals(prefix + "constants/parents.json")) {
                        parseParents(bytes, parents);
                    } else if (name.equals(prefix + "constants/essencecosts.json")) {
                        parseEssenceCosts(bytes, essenceCosts);
                    } else if (name.equals(prefix + "constants/bazaarstocks.json")) {
                        parseBazaarStocks(bytes, bazaarItems);
                    } else if (name.equals(prefix + "constants/museum.json")) {
                        parseMuseum(bytes, museumCategories);
                    }
                } catch (Exception e) {
                    LOGGER.warn("Failed to parse {}: {}", name, e.getMessage());
                }
            }
        }
    }

    private NeuItem parseItem(byte[] bytes) {
        try {
            JsonObject obj = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();

            String internalName = JsonUtil.getString(obj, "internalname");
            if (internalName.isEmpty()) {
                LOGGER.warn("Item missing internalname, skipping");
                return null;
            }

            NeuRecipe.CraftingRecipe crafting = null;
            JsonObject recipeObj = JsonUtil.getObject(obj, "recipe");
            if (recipeObj != null) {
                Map<String, String> grid = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> e : recipeObj.entrySet()) {
                    grid.put(e.getKey(), e.getValue().getAsString());
                }
                crafting = new NeuRecipe.CraftingRecipe(
                    grid,
                    JsonUtil.getInt(obj, "count", 1),
                    JsonUtil.getString(obj, "overrideOutputId")
                );
            }

            List<NeuRecipe> otherRecipes = null;
            JsonElement recipesElem = obj.get("recipes");
            if (recipesElem != null && recipesElem.isJsonArray()) {
                otherRecipes = new ArrayList<>();
                for (JsonElement re : recipesElem.getAsJsonArray()) {
                    if (!re.isJsonObject()) continue;
                    NeuRecipe parsed = parseRecipe(re.getAsJsonObject());
                    if (parsed != null) {
                        otherRecipes.add(parsed);
                    }
                }
                if (otherRecipes.isEmpty()) {
                    otherRecipes = null;
                }
            }

            return new NeuItem(
                internalName,
                JsonUtil.getString(obj, "itemid"),
                JsonUtil.getString(obj, "displayname"),
                JsonUtil.getString(obj, "nbttag"),
                JsonUtil.getStringList(obj, "lore"),
                JsonUtil.getInt(obj, "damage", 0),
                JsonUtil.getString(obj, "clickcommand"),
                JsonUtil.getString(obj, "crafttext"),
                JsonUtil.getString(obj, "infoType"),
                JsonUtil.getStringList(obj, "info"),
                crafting,
                otherRecipes,
                obj.has("slayer_req") ? JsonUtil.getString(obj, "slayer_req") : null,
                JsonUtil.getBoolean(obj, "vanilla", false)
            );

        } catch (Exception e) {
            LOGGER.warn("Failed to parse item: {}", e.getMessage());
            return null;
        }
    }

    private NeuRecipe parseRecipe(JsonObject obj) {
        String type = JsonUtil.getString(obj, "type");
        return switch (type) {
            case "forge" -> new NeuRecipe.ForgeRecipe(
                JsonUtil.getStringList(obj, "inputs"),
                JsonUtil.getInt(obj, "count", 1),
                JsonUtil.getString(obj, "overrideOutputId"),
                JsonUtil.getInt(obj, "duration", 0)
            );
            case "katgrade" -> new NeuRecipe.KatGradeRecipe(
                JsonUtil.getInt(obj, "coins", 0),
                JsonUtil.getInt(obj, "time", 0),
                JsonUtil.getString(obj, "input"),
                JsonUtil.getString(obj, "output"),
                JsonUtil.getStringList(obj, "items")
            );
            case "npc_shop" -> {
                List<NeuRecipe.NpcShopRecipe.Cost> costs = new ArrayList<>();
                JsonElement costElem = obj.get("cost");
                if (costElem != null && costElem.isJsonArray()) {
                    for (JsonElement ce : costElem.getAsJsonArray()) {
                        if (ce.isJsonObject()) {
                            JsonObject co = ce.getAsJsonObject();
                            costs.add(new NeuRecipe.NpcShopRecipe.Cost(
                                JsonUtil.getString(co, "item"),
                                JsonUtil.getInt(co, "cost", 0)
                            ));
                        }
                    }
                }
                yield new NeuRecipe.NpcShopRecipe(
                    JsonUtil.getString(obj, "npc"),
                    costs,
                    JsonUtil.getString(obj, "result")
                );
            }
            case "drops" -> {
                List<NeuRecipe.DropsRecipe.Drop> drops = new ArrayList<>();
                JsonElement dropsElem = obj.get("drops");
                if (dropsElem != null && dropsElem.isJsonArray()) {
                    for (JsonElement de : dropsElem.getAsJsonArray()) {
                        if (de.isJsonObject()) {
                            JsonObject d = de.getAsJsonObject();
                            drops.add(new NeuRecipe.DropsRecipe.Drop(
                                JsonUtil.getString(d, "id"),
                                JsonUtil.getString(d, "chance")
                            ));
                        }
                    }
                }
                yield new NeuRecipe.DropsRecipe(drops);
            }
            case "trade" -> new NeuRecipe.TradeRecipe(
                JsonUtil.getStringList(obj, "inputs"),
                JsonUtil.getString(obj, "output"),
                JsonUtil.getInt(obj, "count", 1)
            );
            default -> null;
        };
    }

    private void parseParents(byte[] bytes, Map<String, List<String>> parents) {
        JsonObject obj = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            List<String> children = new ArrayList<>();
            if (e.getValue().isJsonArray()) {
                for (JsonElement ce : e.getValue().getAsJsonArray()) {
                    children.add(ce.getAsString());
                }
            }
            parents.put(e.getKey(), children);
        }
    }

    private void parseEssenceCosts(byte[] bytes, Map<String, EssenceUpgradeData> essenceCosts) {
        JsonObject obj = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            if (!e.getValue().isJsonObject()) continue;
            JsonObject itemObj = e.getValue().getAsJsonObject();

            String type = JsonUtil.getString(itemObj, "type");
            Map<Integer, Integer> costs = new LinkedHashMap<>();
            Map<Integer, List<String>> items = new LinkedHashMap<>();

            for (int i = 1; i <= 10; i++) {
                JsonElement costElem = itemObj.get(String.valueOf(i));
                if (costElem != null && costElem.isJsonPrimitive()) {
                    costs.put(i, costElem.getAsInt());
                }
            }

            JsonObject itemsObj = JsonUtil.getObject(itemObj, "items");
            if (itemsObj != null) {
                for (Map.Entry<String, JsonElement> ie : itemsObj.entrySet()) {
                    try {
                        int tier = Integer.parseInt(ie.getKey());
                        List<String> reqs = new ArrayList<>();
                        if (ie.getValue().isJsonArray()) {
                            for (JsonElement req : ie.getValue().getAsJsonArray()) {
                                reqs.add(req.getAsString());
                            }
                        }
                        items.put(tier, reqs);
                    } catch (NumberFormatException ignored) {}
                }
            }

            essenceCosts.put(e.getKey(), new EssenceUpgradeData(type, costs, items));
        }
    }

    private void parseBazaarStocks(byte[] bytes, Set<String> bazaarItems) {
        // bazaarstocks.json can be either an object or an array depending on NEU repo version
        JsonElement elem = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
        if (elem.isJsonObject()) {
            JsonObject obj = elem.getAsJsonObject();
            for (String key : obj.keySet()) {
                // Keys are like "BAZAAR_ENCHANTED_DIAMOND"
                if (key.startsWith("BAZAAR_")) {
                    bazaarItems.add(key.substring(7));
                }
            }
        } else if (elem.isJsonArray()) {
            // Array format: each element has "stock" and "id" fields
            for (JsonElement e : elem.getAsJsonArray()) {
                if (e.isJsonObject()) {
                    JsonObject obj = e.getAsJsonObject();
                    String id = JsonUtil.getString(obj, "id");
                    if (!id.isEmpty()) {
                        bazaarItems.add(id);
                    }
                }
            }
        }
    }

    private void parseMuseum(byte[] bytes, Map<String, String> museumCategories) {
        JsonObject obj = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject itemsObj = JsonUtil.getObject(obj, "items");
        if (itemsObj == null) return;

        for (Map.Entry<String, JsonElement> category : itemsObj.entrySet()) {
            String categoryName = category.getKey();
            if (category.getValue().isJsonArray()) {
                for (JsonElement item : category.getValue().getAsJsonArray()) {
                    museumCategories.put(item.getAsString(), categoryName);
                }
            }
        }
    }

    // ---- MessagePack serialization ----

    private void packItems(MessagePacker packer, List<NeuItem> items) throws IOException {
        packer.packArrayHeader(items.size());
        for (NeuItem item : items) {
            packer.packMapHeader(14);
            packer.packString("internalName"); packer.packString(item.internalName());
            packer.packString("itemId"); packer.packString(item.itemId());
            packer.packString("displayName"); packer.packString(item.displayName());
            packer.packString("nbtTag"); packer.packString(item.nbtTag());

            packer.packString("lore");
            packer.packArrayHeader(item.lore().size());
            for (String line : item.lore()) {
                packer.packString(line);
            }

            packer.packString("damage"); packer.packInt(item.damage());
            packer.packString("clickCommand"); packer.packString(item.clickCommand());
            packer.packString("craftText"); packer.packString(item.craftText());
            packer.packString("infoType"); packer.packString(item.infoType());

            packer.packString("info");
            packer.packArrayHeader(item.info().size());
            for (String url : item.info()) {
                packer.packString(url);
            }

            packer.packString("recipe");
            if (item.recipe() instanceof NeuRecipe.CraftingRecipe c) {
                packer.packMapHeader(3 + c.grid().size());
                packer.packString("_type"); packer.packString("crafting");
                packer.packString("count"); packer.packInt(c.count());
                packer.packString("overrideOutputId"); packer.packString(c.overrideOutputId());
                for (Map.Entry<String, String> slot : c.grid().entrySet()) {
                    packer.packString(slot.getKey());
                    packer.packString(slot.getValue());
                }
            } else {
                packer.packNil();
            }

            packer.packString("recipes");
            if (item.recipes() != null) {
                packer.packArrayHeader(item.recipes().size());
                for (NeuRecipe r : item.recipes()) {
                    packRecipe(packer, r);
                }
            } else {
                packer.packNil();
            }

            packer.packString("slayerReq");
            if (item.slayerReq() != null) {
                packer.packString(item.slayerReq());
            } else {
                packer.packNil();
            }

            packer.packString("vanilla"); packer.packBoolean(item.vanilla());
        }
    }

    private void packRecipe(MessagePacker packer, NeuRecipe recipe) throws IOException {
        switch (recipe) {
            case NeuRecipe.CraftingRecipe c -> {
                packer.packMapHeader(3 + c.grid().size());
                packer.packString("_type"); packer.packString("crafting");
                packer.packString("count"); packer.packInt(c.count());
                packer.packString("overrideOutputId"); packer.packString(c.overrideOutputId());
                for (Map.Entry<String, String> slot : c.grid().entrySet()) {
                    packer.packString(slot.getKey());
                    packer.packString(slot.getValue());
                }
            }
            case NeuRecipe.ForgeRecipe f -> {
                packer.packMapHeader(5);
                packer.packString("_type"); packer.packString("forge");
                packer.packString("count"); packer.packInt(f.count());
                packer.packString("overrideOutputId"); packer.packString(f.overrideOutputId());
                packer.packString("duration"); packer.packInt(f.duration());
                packer.packString("inputs");
                packer.packArrayHeader(f.inputs().size());
                for (String input : f.inputs()) {
                    packer.packString(input);
                }
            }
            case NeuRecipe.KatGradeRecipe k -> {
                packer.packMapHeader(6);
                packer.packString("_type"); packer.packString("katgrade");
                packer.packString("coins"); packer.packInt(k.coins());
                packer.packString("time"); packer.packInt(k.time());
                packer.packString("input"); packer.packString(k.input());
                packer.packString("output"); packer.packString(k.output());
                packer.packString("items");
                packer.packArrayHeader(k.items().size());
                for (String item : k.items()) {
                    packer.packString(item);
                }
            }
            case NeuRecipe.NpcShopRecipe n -> {
                packer.packMapHeader(4);
                packer.packString("_type"); packer.packString("npc_shop");
                packer.packString("npc"); packer.packString(n.npc());
                packer.packString("result"); packer.packString(n.result());
                packer.packString("cost");
                packer.packArrayHeader(n.costs().size());
                for (NeuRecipe.NpcShopRecipe.Cost cost : n.costs()) {
                    packer.packMapHeader(2);
                    packer.packString("item"); packer.packString(cost.item());
                    packer.packString("cost"); packer.packInt(cost.cost());
                }
            }
            case NeuRecipe.DropsRecipe d -> {
                packer.packMapHeader(2);
                packer.packString("_type"); packer.packString("drops");
                packer.packString("drops");
                packer.packArrayHeader(d.drops().size());
                for (NeuRecipe.DropsRecipe.Drop drop : d.drops()) {
                    packer.packMapHeader(2);
                    packer.packString("id"); packer.packString(drop.id());
                    packer.packString("chance"); packer.packString(drop.chance());
                }
            }
            case NeuRecipe.TradeRecipe t -> {
                packer.packMapHeader(4);
                packer.packString("_type"); packer.packString("trade");
                packer.packString("count"); packer.packInt(t.count());
                packer.packString("output"); packer.packString(t.output());
                packer.packString("inputs");
                packer.packArrayHeader(t.inputs().size());
                for (String input : t.inputs()) {
                    packer.packString(input);
                }
            }
        }
    }

    private void packConstants(MessagePacker packer,
                               Map<String, List<String>> parents,
                               Map<String, EssenceUpgradeData> essenceCosts,
                               Set<String> bazaarItems,
                               Map<String, String> museumCategories) throws IOException {
        packer.packMapHeader(4);

        // Parents
        packer.packString("parents");
        packer.packMapHeader(parents.size());
        for (Map.Entry<String, List<String>> e : parents.entrySet()) {
            packer.packString(e.getKey());
            packer.packArrayHeader(e.getValue().size());
            for (String child : e.getValue()) {
                packer.packString(child);
            }
        }

        // Essence costs
        packer.packString("essenceCosts");
        packer.packMapHeader(essenceCosts.size());
        for (Map.Entry<String, EssenceUpgradeData> e : essenceCosts.entrySet()) {
            packer.packString(e.getKey());
            EssenceUpgradeData data = e.getValue();
            int mapSize = 1 + data.costsPerStar().size();
            if (!data.extraItemsPerStar().isEmpty()) mapSize++;
            packer.packMapHeader(mapSize);
            packer.packString("type"); packer.packString(data.essenceType());
            for (Map.Entry<Integer, Integer> ce : data.costsPerStar().entrySet()) {
                packer.packString(String.valueOf(ce.getKey()));
                packer.packInt(ce.getValue());
            }
            if (!data.extraItemsPerStar().isEmpty()) {
                packer.packString("items");
                packer.packMapHeader(data.extraItemsPerStar().size());
                for (Map.Entry<Integer, List<String>> ie : data.extraItemsPerStar().entrySet()) {
                    packer.packString(String.valueOf(ie.getKey()));
                    packer.packArrayHeader(ie.getValue().size());
                    for (String req : ie.getValue()) {
                        packer.packString(req);
                    }
                }
            }
        }

        // Bazaar items
        packer.packString("bazaarItems");
        packer.packArrayHeader(bazaarItems.size());
        for (String item : bazaarItems) {
            packer.packString(item);
        }

        // Museum categories
        packer.packString("museum");
        packer.packMapHeader(museumCategories.size());
        for (Map.Entry<String, String> e : museumCategories.entrySet()) {
            packer.packString(e.getKey());
            packer.packString(e.getValue());
        }
    }
}
