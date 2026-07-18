package com.github.kdgaming0.skyrecipes.core.data;

import com.github.kdgaming0.skyrecipes.core.data.codec.ConstantsCodec;
import com.github.kdgaming0.skyrecipes.core.data.codec.ItemCodec;
import com.github.kdgaming0.skyrecipes.core.mob.MobRenderDefinition;
import com.github.kdgaming0.skyrecipes.core.model.*;
import com.github.kdgaming0.skyrecipes.core.registry.ConstantsRegistry;
import com.github.kdgaming0.skyrecipes.core.util.AtomicFiles;
import com.github.kdgaming0.skyrecipes.core.util.JsonUtil;
import com.github.kdgaming0.skyrecipes.core.util.PathValidator;
import com.github.kdgaming0.skyrecipes.core.util.PetStatResolver;
import com.github.kdgaming0.skyrecipes.core.util.TextUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePacker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Compiler that downloads the NEU repository from GitHub,
 * parses all items and constants, and compiles them into a binary .mpk file.
 *
 * <p>Can be used at build time (via Gradle) or at runtime.</p>
 */
public class BinaryDataCompiler {

    private static final Logger LOGGER = LoggerFactory.getLogger("SkyRecipesCompiler");

    private static final String NEU_REPO_URL =
            "https://codeload.github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/zip/refs/heads/master";

    private static final byte[] MAGIC = new byte[]{'S', 'K', 'Y', '2'};
    private static final int SCHEMA_VERSION = 10;
    private static final int HEADER_SIZE = 96;
    private static final int SECTION_COUNT = 3; // items, constants, metadata
    /**
     * Same all-or-nothing budget as generation/injection: >5% parse failures aborts the compile.
     */
    private static final double MAX_PARSE_FAILURE_RATE = 0.05;
    private PetStatResolver petResolver;

    // ---- Legacy build-time entrypoint (kept for compatibility) ----

    static void main(String[] args) throws Exception {
        String outputDir = args.length > 0 ? args[0] : "build/generated/skyrecipes/data";
        String cacheDir = System.getProperty("skyrecipes.cacheDir",
                System.getProperty("user.home") + "/.gradle/skyrecipes-cache");

        // Validate at the entry point where untrusted command-line/system-property
        // input reaches filesystem operations.
        outputDir = PathValidator.requireSafePath(outputDir, "outputDir").toString();
        cacheDir = PathValidator.requireSafePath(cacheDir, "cacheDir").toString();

        new BinaryDataCompiler().compile(outputDir, cacheDir);
    }

    // ---- Runtime-friendly API ----

    private static String normalizeStatName(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            } else if (c == ' ') {
                sb.append('_');
            }
        }
        String normalized = sb.toString();
        if ("walk_speed".equals(normalized)) return "speed";
        return normalized;
    }

    private static DownloadResult cacheFallback(Path zipFile) {
        return Files.exists(zipFile) ? DownloadResult.CACHE_HIT : DownloadResult.FAILED_NO_CACHE;
    }

    // ---- ETag helpers ----

    /**
     * Reads the ZIP central directory (which a truncated transfer corrupts)
     * and requires at least one item entry, rejecting wrong or empty archives.
     */
    private static void verifyRepoZip(Path zip) throws IOException {
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(zip.toFile())) {
            boolean hasItems = zf.stream().anyMatch(e -> e.getName().contains("items/"));
            if (!hasItems) {
                throw new IOException("Downloaded ZIP contains no items/ entries");
            }
        }
    }

    public void compile(String outputDirPath, String cacheDirPath) throws Exception {
        Path cacheDir = PathValidator.requireSafePath(cacheDirPath, "cacheDirPath");
        Files.createDirectories(cacheDir);

        Path zipFile = cacheDir.resolve("neu-repo.zip");
        Path etagFile = cacheDir.resolve("neu-repo.etag");

        // ETag-based download
        String etag = readEtag(etagFile);
        DownloadResult download = downloadNeuRepo(zipFile, etagFile, etag);
        boolean downloaded = switch (download) {
            case DOWNLOADED -> {
                LOGGER.info("Downloaded fresh NEU repo from GitHub");
                yield true;
            }
            case CACHE_HIT -> {
                LOGGER.info("Using cached NEU repo (ETag match)");
                yield false;
            }
            case FAILED_NO_CACHE -> throw new IOException(
                    "NEU repo download failed and no cached copy exists");
        };

        String actualEtag = downloaded ? readEtag(etagFile) : etag;
        if (actualEtag == null) actualEtag = "";

        Path outputDir = PathValidator.requireSafePath(outputDirPath, "outputDirPath");
        Path outputPath = outputDir.resolve("skyrecipes_data_v" + SCHEMA_VERSION + ".mpk");
        Path metaPath = outputDir.resolve("skyrecipes_data_v" + SCHEMA_VERSION + ".meta.json");
        compileToPath(zipFile, outputPath, metaPath, actualEtag, null);
    }

    /**
     * Compile the NEU repository ZIP into a binary .mpk and metadata sidecar.
     *
     * @param zipPath    path to the NEU repo ZIP
     * @param outputPath destination for the .mpk file
     * @param metaPath   destination for the .meta.json file
     * @param etag       the ETag of the source ZIP (for metadata)
     * @param callback   optional progress callback
     * @return compile result
     * @throws Exception if parsing or serialization fails
     */
    public CompileResult compileToPath(Path zipPath, Path outputPath, Path metaPath,
                                       String etag, ProgressCallback callback) throws Exception {
        zipPath = PathValidator.requireSafePath(zipPath, "zipPath");
        outputPath = PathValidator.requireSafePath(outputPath, "outputPath");
        metaPath = PathValidator.requireSafePath(metaPath, "metaPath");

        long startTime = System.currentTimeMillis();

        if (callback != null) callback.onProgress("Parsing", 0);

        List<NeuItem> items = new ArrayList<>();
        Map<String, List<String>> parents = new LinkedHashMap<>();
        Map<String, EssenceUpgradeData> essenceCosts = new LinkedHashMap<>();
        Set<String> bazaarItems = new HashSet<>();
        Map<String, String> museumCategories = new LinkedHashMap<>();
        Map<String, String> museumChildren = new LinkedHashMap<>();
        Map<String, ReforgeData> reforges = new LinkedHashMap<>();
        Map<String, ReforgeStoneData> reforgeStones = new LinkedHashMap<>();
        Map<String, MobRenderDefinition> mobDefinitions = new LinkedHashMap<>();
        Map<String, byte[]> mobSkins = new LinkedHashMap<>();
        Map<String, AttributeShardData> attributeShards = new LinkedHashMap<>();
        this.petResolver = null;

        ParseStats parseStats = parseZip(zipPath, items, parents, essenceCosts, bazaarItems, museumCategories, museumChildren, reforges, reforgeStones, mobDefinitions, mobSkins, attributeShards);

        if (items.isEmpty()) {
            throw new IOException("Parsed 0 items from NEU repo ZIP — archive corrupt or format changed");
        }
        if (parseStats.itemFailures() > parseStats.itemAttempts() * MAX_PARSE_FAILURE_RATE) {
            throw new IOException(String.format(
                    "NEU item parse failure rate too high: %d of %d failed — possible NEU format change",
                    parseStats.itemFailures(), parseStats.itemAttempts()));
        }

        // Generate stat whitelist from gear item lore
        Set<String> knownStats = buildKnownStats(items);
        // Build reverse map: reforge name → stone internal name
        Map<String, String> reforgeNameToStone = buildReforgeNameToStone(reforgeStones);

        if (callback != null) callback.onProgress("Serializing", 50);

        LOGGER.info("Parsed items: {} ok / {} failed", items.size(), parseStats.itemFailures());
        LOGGER.info("Constants: {} parents, {} essence costs, {} bazaar items, {} museum entries, {} reforges, {} reforge stones, {} known stats, {} reforge name mappings, {} mob defs, {} mob skins, {} attribute shards",
                parents.size(), essenceCosts.size(), bazaarItems.size(), museumCategories.size(), reforges.size(), reforgeStones.size(), knownStats.size(), reforgeNameToStone.size(), mobDefinitions.size(), mobSkins.size(), attributeShards.size());

        // Write binary
        Files.createDirectories(outputPath.getParent());

        long commitHash = hashEtag(etag);
        long buildTimestamp = System.currentTimeMillis();
        BinaryMetadata metadata = new BinaryMetadata(
                SCHEMA_VERSION,
                buildTimestamp,
                items.size(),
                etag,
                Long.toHexString(commitHash),
                NEU_REPO_URL
        );
        byte[] metadataBytes = metadata.toBytes();

        long itemsLength;
        long constantsLength;
        long payloadCrc;
        try (OutputStream fos = Files.newOutputStream(outputPath);
             BufferedOutputStream bos = new BufferedOutputStream(fos)) {

            bos.write(new byte[HEADER_SIZE]); // placeholder, patched below; excluded from CRC

            java.util.zip.CRC32C crc = new java.util.zip.CRC32C();
            CountingOutputStream counter = new CountingOutputStream(
                    new java.util.zip.CheckedOutputStream(bos, crc));

            // One packer writes both msgpack sections; consecutive complete
            // values are byte-identical to two separately packed streams.
            MessagePacker packer = MessagePack.newDefaultPacker(counter);
            ItemCodec.packItems(packer, resolvePetPlaceholders(items));
            packer.flush();
            itemsLength = counter.count();

            ConstantsCodec.pack(packer, new ConstantsRegistry(
                    parents, essenceCosts, bazaarItems, museumCategories,
                    reforges, reforgeStones, knownStats, reforgeNameToStone,
                    mobDefinitions, mobSkins, museumChildren, attributeShards));
            packer.flush();
            constantsLength = counter.count() - itemsLength;

            counter.write(metadataBytes);
            counter.flush();
            payloadCrc = crc.getValue();
        }

        // Patch the real header over the placeholder
        try (RandomAccessFile raf = new RandomAccessFile(outputPath.toFile(), "rw")) {
            raf.write(MAGIC);
            raf.writeInt(SCHEMA_VERSION);
            raf.writeLong(buildTimestamp);
            raf.writeInt(items.size());
            raf.writeInt(SECTION_COUNT);
            raf.writeLong(commitHash);
            raf.writeLong(HEADER_SIZE);
            raf.writeLong(itemsLength);
            raf.writeLong(HEADER_SIZE + itemsLength);
            raf.writeLong(constantsLength);
            raf.writeLong(HEADER_SIZE + itemsLength + constantsLength);
            raf.writeLong(metadataBytes.length);
            raf.writeInt((int) payloadCrc);
            // bytes 84-95 remain zero (reserved)
        }

        // Sidecar is diagnostics only; the embedded metadata section is authoritative
        metadata.write(metaPath);

        long duration = System.currentTimeMillis() - startTime;
        LOGGER.info("Wrote binary: {} ({} bytes) in {} ms", outputPath, Files.size(outputPath), duration);

        if (callback != null) callback.onProgress("Complete", 100);

        return new CompileResult(outputPath, metaPath, items.size(), etag, duration);
    }

    // ---- Download ----

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

    // ---- Parsing (unchanged from original) ----

    private long hashEtag(String etag) {
        if (etag == null || etag.isEmpty()) return 0L;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(etag.getBytes(StandardCharsets.UTF_8));
            long value = 0;
            for (int i = 0; i < 8; i++) {
                value = (value << 8) | (hash[i] & 0xFFL);
            }
            return value;
        } catch (Exception e) {
            return etag.hashCode();
        }
    }

    public DownloadResult downloadNeuRepo(Path zipFile, Path etagFile, String existingEtag) {
        zipFile = PathValidator.requireSafePath(zipFile, "zipFile");
        etagFile = PathValidator.requireSafePath(etagFile, "etagFile");

        try {
            URL url = URI.create(NEU_REPO_URL).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setInstanceFollowRedirects(true);

            String newEtag;
            try {
                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    LOGGER.warn("HEAD request returned {}, using cache if available", responseCode);
                    return cacheFallback(zipFile);
                }

                newEtag = conn.getHeaderField("ETag");
            } finally {
                conn.disconnect();
            }

            if (newEtag != null && Files.exists(zipFile)
                    && Objects.equals(RuntimeUpdateService.normalizeEtag(newEtag),
                    RuntimeUpdateService.normalizeEtag(existingEtag))) {
                return DownloadResult.CACHE_HIT;
            }

            IOException lastFailure = null;
            for (int attempt = 1; attempt <= 2; attempt++) {
                try {
                    downloadZipToTemp(url, zipFile);
                    if (newEtag != null) {
                        Files.writeString(etagFile, newEtag, StandardCharsets.UTF_8);
                    }
                    return DownloadResult.DOWNLOADED;
                } catch (IOException e) {
                    lastFailure = e;
                    LOGGER.warn("NEU repo download attempt {} failed: {}", attempt, e.getMessage());
                }
            }
            LOGGER.warn("NEU repo download failed after retries: {}",
                    lastFailure != null ? lastFailure.getMessage() : "unknown");
            LOGGER.debug("NEU repo download failure detail", lastFailure);
            return cacheFallback(zipFile);

        } catch (IOException e) {
            LOGGER.warn("Failed to check/download NEU repo, using cache if available: {}", e.getMessage());
            LOGGER.debug("NEU repo check/download failure detail", e);
            return cacheFallback(zipFile);
        }
    }

    /**
     * Runtime download path: GET the repo ZIP into {@code zipFile}, verifying integrity.
     * The caller compiles from it and then deletes it (stream-then-discard). The enclosing
     * update check already decided a download is needed, so no HEAD/cache-hit check is done
     * here. Returns the ETag from the GET response, or {@code ""} if the server omits one.
     */
    public String downloadNeuRepoStreaming(Path zipFile) throws IOException {
        zipFile = PathValidator.requireSafePath(zipFile, "zipFile");
        URL url = URI.create(NEU_REPO_URL).toURL();
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                String etag = downloadZipToTemp(url, zipFile);
                return etag != null ? etag : "";
            } catch (IOException e) {
                lastFailure = e;
                LOGGER.warn("NEU repo download attempt {} failed: {}", attempt, e.getMessage());
            }
        }
        throw new IOException("NEU repo download failed after retries", lastFailure);
    }

    /**
     * GET the repo ZIP to a sibling temp file, verify its integrity, and atomically
     * move it over {@code zipFile}. A failed or truncated transfer never replaces an
     * existing usable copy. Returns the ETag from the GET response, or {@code null}.
     */
    private String downloadZipToTemp(URL url, Path zipFile) throws IOException {
        LOGGER.info("Downloading NEU repo...");
        Path tmp = zipFile.resolveSibling(zipFile.getFileName() + ".tmp");

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);
        conn.setInstanceFollowRedirects(true);

        try {
            long expectedLength = conn.getContentLengthLong();
            String getEtag = conn.getHeaderField("ETag");

            long transferred;
            try (InputStream in = conn.getInputStream();
                 OutputStream out = Files.newOutputStream(tmp)) {
                transferred = in.transferTo(out);
            }

            if (expectedLength > 0 && transferred != expectedLength) {
                throw new IOException("Truncated download: got " + transferred
                        + " of " + expectedLength + " bytes");
            }
            verifyRepoZip(tmp);

            AtomicFiles.move(tmp, zipFile);
            return getEtag;
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException cleanupEx) {
                LOGGER.debug("Failed to delete temp download", cleanupEx);
            }
            throw e;
        } finally {
            conn.disconnect();
        }
    }

    private ParseStats parseZip(Path zipFile, List<NeuItem> items, Map<String, List<String>> parents,
                                Map<String, EssenceUpgradeData> essenceCosts, Set<String> bazaarItems,
                                Map<String, String> museumCategories,
                                Map<String, String> museumChildren,
                                Map<String, ReforgeData> reforges,
                                Map<String, ReforgeStoneData> reforgeStones,
                                Map<String, MobRenderDefinition> mobDefinitions,
                                Map<String, byte[]> mobSkins,
                                Map<String, AttributeShardData> attributeShards) throws IOException {

        String prefix = null;

        int workers = Math.max(2, Runtime.getRuntime().availableProcessors() - 2);
        @SuppressWarnings("resource")
        java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(workers, r -> {
                    Thread t = new Thread(r, "SkyRecipes-ParseWorker");
                    t.setDaemon(true);
                    return t;
                });
        List<java.util.concurrent.Future<NeuItem>> itemFutures = new ArrayList<>(9_000);

        try {
            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String name = entry.getName();

                    if (prefix == null && name.contains("/")) {
                        prefix = name.substring(0, name.indexOf('/') + 1);
                    }

                    if (entry.isDirectory()) continue;

                    byte[] bytes = zis.readAllBytes();

                    try {
                        if (name.startsWith(prefix + "items/") && name.endsWith(".json")) {
                            itemFutures.add(pool.submit(() -> parseItem(bytes)));
                        } else if (name.equals(prefix + "constants/parents.json")) {
                            parseParents(bytes, parents);
                        } else if (name.equals(prefix + "constants/essencecosts.json")) {
                            parseEssenceCosts(bytes, essenceCosts);
                        } else if (name.equals(prefix + "constants/bazaarstocks.json")) {
                            parseBazaarStocks(bytes, bazaarItems);
                        } else if (name.equals(prefix + "constants/museum.json")) {
                            parseMuseum(bytes, museumCategories, museumChildren);
                        } else if (name.equals(prefix + "constants/reforges.json")) {
                            parseReforges(bytes, reforges);
                        } else if (name.equals(prefix + "constants/reforgestones.json")) {
                            parseReforgeStones(bytes, reforgeStones);
                        } else if (name.equals(prefix + "constants/attribute_shards.json")) {
                            parseAttributeShards(bytes, attributeShards);
                        } else if (name.equals(prefix + "constants/petnums.json")) {
                            this.petResolver = PetStatResolver.load(JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject());
                        } else if (name.startsWith(prefix + "mobs/") && name.endsWith(".json")) {
                            //noinspection DataFlowIssue
                            parseMobJson(bytes, name, prefix, mobDefinitions);
                        } else if (name.startsWith(prefix + "mobs/") && name.endsWith(".png")) {
                            //noinspection DataFlowIssue
                            parseMobPng(bytes, name, prefix, mobSkins);
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Failed to parse {}", name, e);
                    }
                }
            }

            int itemFailures = 0;
            for (java.util.concurrent.Future<NeuItem> future : itemFutures) {
                try {
                    NeuItem item = future.get();
                    if (item != null) {
                        items.add(item);
                    } else {
                        itemFailures++;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while parsing NEU items", e);
                } catch (java.util.concurrent.ExecutionException e) {
                    itemFailures++;
                    LOGGER.warn("Item parse task failed", e.getCause());
                }
            }
            return new ParseStats(itemFutures.size(), itemFailures);
        } finally {
            pool.shutdown();
        }
    }

    private void parseMobJson(byte[] bytes, String entryName, String prefix,
                              Map<String, MobRenderDefinition> mobDefinitions) {
        String relativePath = entryName.substring(prefix.length());
        String ref = "@neurepo:" + relativePath;
        JsonObject obj = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        MobRenderDefinition def = MobRenderDefinition.parse(obj);
        if (def != null) {
            mobDefinitions.put(ref, def);
        }
    }

    private void parseMobPng(byte[] bytes, String entryName, String prefix,
                             Map<String, byte[]> mobSkins) {
        String relativePath = entryName.substring(prefix.length());
        String key = "neurepo:" + relativePath;
        mobSkins.put(key, bytes);
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
                int outputCount = JsonUtil.getInt(obj, "count", 1);
                for (Map.Entry<String, JsonElement> e : recipeObj.entrySet()) {
                    String key = e.getKey();
                    if (key.equals("count")) {
                        // NEU repo stores output count inside recipe object for some items
                        try {
                            outputCount = e.getValue().getAsInt();
                        } catch (NumberFormatException ignored) {
                        }
                        continue;
                    }
                    if (key.equals("overrideOutputId")) {
                        continue;
                    }
                    grid.put(key, e.getValue().getAsString());
                }
                crafting = new NeuRecipe.CraftingRecipe(
                        grid,
                        outputCount,
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

            String island = JsonUtil.getString(obj, "island");
            int x = JsonUtil.getInt(obj, "x", 0);
            int y = JsonUtil.getInt(obj, "y", 0);
            int z = JsonUtil.getInt(obj, "z", 0);

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
                    JsonUtil.getBoolean(obj, "vanilla", false),
                    island, x, y, z
            );

        } catch (Exception e) {
            LOGGER.warn("Failed to parse item", e);
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
                        } else if (ce.isJsonPrimitive() && ce.getAsJsonPrimitive().isString()) {
                            String costStr = ce.getAsString();
                            int colon = costStr.lastIndexOf(':');
                            if (colon != -1) {
                                String itemName = costStr.substring(0, colon);
                                String countStr = costStr.substring(colon + 1);
                                int count;
                                try {
                                    count = (int) Double.parseDouble(countStr);
                                } catch (NumberFormatException e2) {
                                    count = 1;
                                }
                                costs.add(new NeuRecipe.NpcShopRecipe.Cost(itemName, count));
                            } else {
                                costs.add(new NeuRecipe.NpcShopRecipe.Cost(costStr, 1));
                            }
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
                yield new NeuRecipe.DropsRecipe(
                        JsonUtil.getString(obj, "name"),
                        JsonUtil.getString(obj, "render"),
                        drops
                );
            }
            case "trade" -> new NeuRecipe.TradeRecipe(
                    JsonUtil.getString(obj, "cost"),
                    JsonUtil.getString(obj, "result"),
                    JsonUtil.getInt(obj, "count", 1),
                    JsonUtil.getInt(obj, "min", 0),
                    JsonUtil.getInt(obj, "max", 0)
            );
            case "crafting" -> {
                Map<String, String> grid = new LinkedHashMap<>();
                int outputCount = JsonUtil.getInt(obj, "count", 1);
                for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                    String key = e.getKey();
                    if (key.equals("type") || key.equals("count") || key.equals("overrideOutputId")) continue;
                    grid.put(key, e.getValue().getAsString());
                }
                yield new NeuRecipe.CraftingRecipe(grid, outputCount, JsonUtil.getString(obj, "overrideOutputId"));
            }
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
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            essenceCosts.put(e.getKey(), new EssenceUpgradeData(type, costs, items));
        }
    }

    private void parseBazaarStocks(byte[] bytes, Set<String> bazaarItems) {
        JsonElement elem = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
        if (elem.isJsonObject()) {
            JsonObject obj = elem.getAsJsonObject();
            for (String key : obj.keySet()) {
                if (key.startsWith("BAZAAR_")) {
                    bazaarItems.add(key.substring(7));
                }
            }
        } else if (elem.isJsonArray()) {
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

    private void parseAttributeShards(byte[] bytes, Map<String, AttributeShardData> attributeShards) {
        JsonObject obj = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonElement attributes = obj.get("attributes");
        if (attributes == null || !attributes.isJsonArray()) return;
        for (JsonElement e : attributes.getAsJsonArray()) {
            if (!e.isJsonObject()) continue;
            JsonObject shard = e.getAsJsonObject();
            String internalName = JsonUtil.getString(shard, "internalName");
            if (internalName.isEmpty()) continue;
            attributeShards.put(internalName, new AttributeShardData(
                    internalName,
                    JsonUtil.getString(shard, "displayName"),
                    JsonUtil.getString(shard, "abilityName"),
                    JsonUtil.getString(shard, "rarity"),
                    JsonUtil.getString(shard, "alignment"),
                    JsonUtil.getStringList(shard, "family"),
                    JsonUtil.getString(shard, "shardId"),
                    JsonUtil.getString(shard, "bazaarName")
            ));
        }
    }

    private void parseMuseum(byte[] bytes, Map<String, String> museumCategories,
                             Map<String, String> museumChildren) {
        JsonObject obj = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject itemsObj = JsonUtil.getObject(obj, "items");
        if (itemsObj != null) {
            for (Map.Entry<String, JsonElement> category : itemsObj.entrySet()) {
                String categoryName = category.getKey();
                if (category.getValue().isJsonArray()) {
                    for (JsonElement item : category.getValue().getAsJsonArray()) {
                        museumCategories.put(item.getAsString(), categoryName);
                    }
                }
            }
        }

        // "children" is a curated upgrade map: item → the item it upgrades from.
        JsonObject childrenObj = JsonUtil.getObject(obj, "children");
        if (childrenObj != null) {
            for (Map.Entry<String, JsonElement> e : childrenObj.entrySet()) {
                JsonElement value = e.getValue();
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                    museumChildren.put(e.getKey(), value.getAsString());
                }
            }
        }
    }

    private void parseReforges(byte[] bytes, Map<String, ReforgeData> reforges) {
        JsonObject obj = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            if (!e.getValue().isJsonObject()) continue;
            JsonObject r = e.getValue().getAsJsonObject();

            String reforgeName = JsonUtil.getString(r, "reforgeName", e.getKey());
            String itemTypes = parseItemTypes(r.get("itemTypes"));
            List<String> requiredRarities = JsonUtil.getStringList(r, "requiredRarities");
            Map<String, Map<String, Number>> stats = parseStatsMap(JsonUtil.getObject(r, "reforgeStats"));
            Map<String, String> ability = parseAbility(JsonUtil.getObject(r, "reforgeAbility"), r.get("reforgeAbility"));
            Map<String, Number> costs = parseStringNumberMap(JsonUtil.getObject(r, "reforgeCosts"));

            reforges.put(e.getKey(), new ReforgeData(reforgeName, itemTypes, requiredRarities, stats, ability, costs));
        }
    }

    private void parseReforgeStones(byte[] bytes, Map<String, ReforgeStoneData> reforgeStones) {
        JsonObject obj = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            if (!e.getValue().isJsonObject()) continue;
            JsonObject r = e.getValue().getAsJsonObject();

            String internalName = JsonUtil.getString(r, "internalName", e.getKey());
            String reforgeName = JsonUtil.getString(r, "reforgeName");
            String reforgeType = JsonUtil.getString(r, "reforgeType");
            String itemTypes = parseItemTypes(r.get("itemTypes"));
            List<String> requiredRarities = JsonUtil.getStringList(r, "requiredRarities");
            Map<String, String> ability = parseAbility(JsonUtil.getObject(r, "reforgeAbility"), r.get("reforgeAbility"));
            Map<String, Number> costs = parseStringNumberMap(JsonUtil.getObject(r, "reforgeCosts"));
            Map<String, Map<String, Number>> stats = parseStatsMap(JsonUtil.getObject(r, "reforgeStats"));

            reforgeStones.put(e.getKey(), new ReforgeStoneData(
                    internalName, reforgeName, reforgeType, itemTypes, requiredRarities, ability, costs, stats
            ));
        }
    }

    /**
     * Scans all parsed items and builds a whitelist of stat names that appear
     * in gear item lore (weapons, armor, tools, accessories, equipment, etc.).
     */
    private Set<String> buildKnownStats(List<NeuItem> items) {
        Set<String> stats = new HashSet<>(256);
        Set<String> gearTypes = Set.of(
                "SWORD", "BOW", "WAND", "LONGSWORD", "DUNGEON SWORD", "DUNGEON LONGSWORD",
                "DUNGEON BOW", "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS",
                "DUNGEON HELMET", "DUNGEON CHESTPLATE", "DUNGEON LEGGINGS", "DUNGEON BOOTS",
                "ACCESSORY", "TALISMAN", "RING", "ARTIFACT", "RELIC", "POWER STONE",
                "DUNGEON ACCESSORY", "HATCESSORY", "CARNIVAL MASK",
                "BELT", "NECKLACE", "CLOAK", "GLOVES", "BRACELET",
                "DUNGEON NECKLACE", "DUNGEON BELT", "DUNGEON CLOAK", "DUNGEON GLOVES",
                "PICKAXE", "DRILL", "AXE", "HOE", "SHOVEL", "SHEARS",
                "FARMING TOOL", "WATERING CAN", "DEPLOYABLE", "GARDEN CHIP", "VACUUM", "CHISEL",
                "ROD", "FISHING ROD", "FISHING NET"
        );

        for (NeuItem item : items) {
            if (item.lore() == null || item.lore().isEmpty()) continue;

            // Only scan gear items to avoid false positives from non-gear
            String last = TextUtil.stripColorCodes(item.lore().getLast()).toUpperCase().trim();
            boolean isGear = false;
            for (String gearType : gearTypes) {
                if (last.contains(gearType)) {
                    isGear = true;
                    break;
                }
            }
            if (!isGear) continue;

            for (String line : item.lore()) {
                String clean = TextUtil.stripColorCodes(line);
                int colonIdx = clean.indexOf(':');
                if (colonIdx <= 0) continue;
                String statName = clean.substring(0, colonIdx).trim().toLowerCase();
                statName = normalizeStatName(statName);
                if (statName.isEmpty()) continue;

                String valuePart = clean.substring(colonIdx + 1).trim();
                // Require a numeric value with + or - sign to reduce false positives
                if (!valuePart.matches(".*[+-]?\\d+.*")) continue;

                stats.add(statName);
            }
        }
        return stats;
    }

    /**
     * Builds a reverse map from reforge name to the stone item's internal name.
     */
    private Map<String, String> buildReforgeNameToStone(Map<String, ReforgeStoneData> reforgeStones) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Map.Entry<String, ReforgeStoneData> e : reforgeStones.entrySet()) {
            ReforgeStoneData stone = e.getValue();
            String reforgeName = stone.reforgeName();
            String internalName = stone.internalName();
            if (reforgeName != null && !reforgeName.isEmpty() && internalName != null && !internalName.isEmpty()) {
                map.putIfAbsent(reforgeName, internalName);
            }
        }
        return map;
    }

    private Map<String, Map<String, Number>> parseStatsMap(JsonObject obj) {
        Map<String, Map<String, Number>> result = new LinkedHashMap<>();
        if (obj == null) return result;
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            if (!e.getValue().isJsonObject()) continue;
            Map<String, Number> stats = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> se : e.getValue().getAsJsonObject().entrySet()) {
                stats.put(se.getKey(), se.getValue().getAsNumber());
            }
            result.put(e.getKey(), stats);
        }
        return result;
    }

    // -----------------------------------------------------------------
    // Compile-time generated metadata
    // -----------------------------------------------------------------

    /**
     * Parse itemTypes which can be either a string (e.g. "SWORD,HELMET")
     * or an object with internalName/itemId arrays.
     */
    private String parseItemTypes(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            // Object format: {"internalName": [...]} or {"itemId": [...]}
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                if (e.getValue().isJsonArray()) {
                    for (JsonElement item : e.getValue().getAsJsonArray()) {
                        if (!sb.isEmpty()) sb.append(",");
                        sb.append(item.getAsString());
                    }
                }
            }
            return sb.toString();
        }
        return "";
    }

    private Map<String, String> parseStringStringMap(JsonObject obj) {
        Map<String, String> result = new LinkedHashMap<>();
        if (obj == null) return result;
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            result.put(e.getKey(), e.getValue().getAsString());
        }
        return result;
    }

    /**
     * Parse reforgeAbility which can be either a JsonObject (rarity -> description map)
     * or a JsonPrimitive string (single description for all rarities).
     */
    private Map<String, String> parseAbility(JsonObject obj, JsonElement raw) {
        if (obj != null) {
            return parseStringStringMap(obj);
        }
        if (raw != null && raw.isJsonPrimitive()) {
            Map<String, String> result = new LinkedHashMap<>();
            result.put("ability", raw.getAsString());
            return result;
        }
        return new LinkedHashMap<>();
    }

    private Map<String, Number> parseStringNumberMap(JsonObject obj) {
        Map<String, Number> result = new LinkedHashMap<>();
        if (obj == null) return result;
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            result.put(e.getKey(), e.getValue().getAsNumber());
        }
        return result;
    }

    /**
     * Resolve pet stat placeholders at compile time so the packed displayName
     * and lore are final strings.
     */
    private List<NeuItem> resolvePetPlaceholders(List<NeuItem> items) {
        if (petResolver == null || !petResolver.isLoaded()) {
            return items;
        }
        List<NeuItem> resolved = new ArrayList<>(items.size());
        for (NeuItem item : items) {
            PetStatResolver.ResolvedStrings r = petResolver.resolve(item);
            if (r == null) {
                resolved.add(item);
            } else {
                resolved.add(new NeuItem(item.internalName(), item.itemId(), r.displayName(),
                        item.nbtTag(), r.lore(), item.damage(), item.clickCommand(), item.craftText(),
                        item.infoType(), item.info(), item.recipe(), item.recipes(), item.slayerReq(),
                        item.vanilla(), item.island(), item.x(), item.y(), item.z()));
            }
        }
        return resolved;
    }

    /**
     * Outcome of {@link #downloadNeuRepo}: distinguishes fresh data, usable cache, and hard failure.
     */
    public enum DownloadResult {DOWNLOADED, CACHE_HIT, FAILED_NO_CACHE}

    /**
     * Progress callback for long-running compiles.
     */
    public interface ProgressCallback {
        void onProgress(String stage, int percent);
    }

    /**
     * Tracks bytes written so section offsets can be recorded during a single streaming pass.
     */
    private static final class CountingOutputStream extends FilterOutputStream {
        private long count;

        CountingOutputStream(OutputStream out) {
            super(out);
        }

        long count() {
            return count;
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            count++;
        }

        @SuppressWarnings("NullableProblems")
        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            count += len;
        }
    }

    /**
     * Item-parse counters from a ZIP scan, used to detect systemic NEU format changes.
     */
    record ParseStats(int itemAttempts, int itemFailures) {
    }

    /**
     * Result of a successful compile.
     */
    public record CompileResult(
            Path outputPath,
            Path metaPath,
            int itemCount,
            String etag,
            long durationMs
    ) {
    }
}
