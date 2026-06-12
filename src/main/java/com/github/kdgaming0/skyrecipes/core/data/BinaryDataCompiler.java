package com.github.kdgaming0.skyrecipes.core.data;

import com.github.kdgaming0.skyrecipes.core.util.PetStatResolver;

import com.github.kdgaming0.skyrecipes.core.mob.MobRenderDefinition;
import com.github.kdgaming0.skyrecipes.core.model.*;
import com.github.kdgaming0.skyrecipes.core.util.JsonUtil;
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
    private static final int SCHEMA_VERSION = 8;
    private static final int HEADER_SIZE = 96;
    private static final int SECTION_COUNT = 3; // items, constants, metadata
    /** Same all-or-nothing budget as generation/injection: >5% parse failures aborts the compile. */
    private static final double MAX_PARSE_FAILURE_RATE = 0.05;
    private PetStatResolver petResolver;

    // ---- Legacy build-time entrypoint (kept for compatibility) ----

    static void main(String[] args) throws Exception {
        String outputDir = args.length > 0 ? args[0] : "build/generated/skyrecipes/data";
        String cacheDir = System.getProperty("skyrecipes.cacheDir",
                System.getProperty("user.home") + "/.gradle/skyrecipes-cache");

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

    public void compile(String outputDirPath, String cacheDirPath) throws Exception {
        Path cacheDir = Path.of(cacheDirPath);
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

        Path outputPath = Path.of(outputDirPath, "skyrecipes_data_v" + SCHEMA_VERSION + ".mpk");
        Path metaPath = Path.of(outputDirPath, "skyrecipes_data_v" + SCHEMA_VERSION + ".meta.json");
        compileToPath(zipFile, outputPath, metaPath, actualEtag, null);
    }

    // ---- ETag helpers ----

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
        long startTime = System.currentTimeMillis();

        if (callback != null) callback.onProgress("Parsing", 0);

        List<NeuItem> items = new ArrayList<>();
        Map<String, List<String>> parents = new LinkedHashMap<>();
        Map<String, EssenceUpgradeData> essenceCosts = new LinkedHashMap<>();
        Set<String> bazaarItems = new HashSet<>();
        Map<String, String> museumCategories = new LinkedHashMap<>();
        Map<String, ReforgeData> reforges = new LinkedHashMap<>();
        Map<String, ReforgeStoneData> reforgeStones = new LinkedHashMap<>();
        Map<String, MobRenderDefinition> mobDefinitions = new LinkedHashMap<>();
        Map<String, byte[]> mobSkins = new LinkedHashMap<>();
        this.petResolver = null;

        ParseStats parseStats = parseZip(zipPath, items, parents, essenceCosts, bazaarItems, museumCategories, reforges, reforgeStones, mobDefinitions, mobSkins);

        // All-or-nothing gate: an empty or mostly-failed parse means the
        // archive is corrupt or NEU changed format — refuse to write a binary
        // that would silently replace good data with a near-empty one.
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
        LOGGER.info("Constants: {} parents, {} essence costs, {} bazaar items, {} museum entries, {} reforges, {} reforge stones, {} known stats, {} reforge name mappings, {} mob defs, {} mob skins",
                parents.size(), essenceCosts.size(), bazaarItems.size(), museumCategories.size(), reforges.size(), reforgeStones.size(), knownStats.size(), reforgeNameToStone.size(), mobDefinitions.size(), mobSkins.size());

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

        // Stream all sections through one CRC32C-checked counter so the
        // payload checksum and section offsets fall out of a single pass,
        // with no intermediate in-memory copies of the sections.
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
            packItems(packer, items);
            packer.flush();
            itemsLength = counter.count();

            packConstants(packer, parents, essenceCosts, bazaarItems, museumCategories, reforges, reforgeStones, knownStats, reforgeNameToStone, mobDefinitions, mobSkins);
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

    /** Tracks bytes written so section offsets can be recorded during a single streaming pass. */
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

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            count += len;
        }
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

    // ---- Download ----

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

    // ---- Parsing (unchanged from original) ----

    /** Outcome of {@link #downloadNeuRepo}: distinguishes fresh data, usable cache, and hard failure. */
    public enum DownloadResult { DOWNLOADED, CACHE_HIT, FAILED_NO_CACHE }

    public DownloadResult downloadNeuRepo(Path zipFile, Path etagFile, String existingEtag) {
        try {
            URL url = new URL(NEU_REPO_URL);
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
                    downloadZipToTemp(url, zipFile, newEtag);
                    if (newEtag != null) {
                        Files.writeString(etagFile, newEtag, StandardCharsets.UTF_8);
                    }
                    return DownloadResult.DOWNLOADED;
                } catch (IOException e) {
                    lastFailure = e;
                    LOGGER.warn("NEU repo download attempt {} failed: {}", attempt, e.getMessage());
                }
            }
            LOGGER.warn("NEU repo download failed after retries", lastFailure);
            return cacheFallback(zipFile);

        } catch (IOException e) {
            LOGGER.warn("Failed to check/download NEU repo, using cache if available", e);
            return cacheFallback(zipFile);
        }
    }

    private static DownloadResult cacheFallback(Path zipFile) {
        return Files.exists(zipFile) ? DownloadResult.CACHE_HIT : DownloadResult.FAILED_NO_CACHE;
    }

    /**
     * Download the repo ZIP to a sibling temp file, verify its integrity,
     * and atomically move it over the cached copy. A failed or truncated
     * transfer never replaces an existing usable cache.
     */
    private void downloadZipToTemp(URL url, Path zipFile, String headEtag) throws IOException {
        LOGGER.info("Downloading NEU repo...");
        Path tmp = zipFile.resolveSibling(zipFile.getFileName() + ".tmp");

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);
        conn.setInstanceFollowRedirects(true);

        try {
            long expectedLength = conn.getContentLengthLong();
            String getEtag = conn.getHeaderField("ETag");
            LOGGER.debug("NEU repo ETags — HEAD: {}, GET: {}", headEtag, getEtag);

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

            try {
                Files.move(tmp, zipFile, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicEx) {
                Files.move(tmp, zipFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
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

    /** Item-parse counters from a ZIP scan, used to detect systemic NEU format changes. */
    record ParseStats(int itemAttempts, int itemFailures) {
    }

    private ParseStats parseZip(Path zipFile, List<NeuItem> items, Map<String, List<String>> parents,
                          Map<String, EssenceUpgradeData> essenceCosts, Set<String> bazaarItems,
                          Map<String, String> museumCategories,
                          Map<String, ReforgeData> reforges,
                          Map<String, ReforgeStoneData> reforgeStones,
                          Map<String, MobRenderDefinition> mobDefinitions,
                          Map<String, byte[]> mobSkins) throws IOException {

        String prefix = null;

        // The ZIP is read sequentially (ZipInputStream cannot be parallelized),
        // but item JSON parsing — the dominant compile cost — fans out to a
        // worker pool. Futures are joined in entry order so the resulting item
        // list, and therefore the binary, stays deterministic.
        int workers = Math.max(2, Runtime.getRuntime().availableProcessors() - 2);
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
                            parseMuseum(bytes, museumCategories);
                        } else if (name.equals(prefix + "constants/reforges.json")) {
                            parseReforges(bytes, reforges);
                        } else if (name.equals(prefix + "constants/reforgestones.json")) {
                            parseReforgeStones(bytes, reforgeStones);
                        } else if (name.equals(prefix + "constants/petnums.json")) {
                            this.petResolver = PetStatResolver.load(JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject());
                        } else if (name.startsWith(prefix + "mobs/") && name.endsWith(".json")) {
                            parseMobJson(bytes, name, prefix, mobDefinitions);
                        } else if (name.startsWith(prefix + "mobs/") && name.endsWith(".png")) {
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

    // -----------------------------------------------------------------
    // Compile-time generated metadata
    // -----------------------------------------------------------------

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
                        if (sb.length() > 0) sb.append(",");
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

    private void packItems(MessagePacker packer, List<NeuItem> items) throws IOException {
        packer.packArrayHeader(items.size());
        for (NeuItem item : items) {
            // Resolve pet placeholders at compile time
            PetStatResolver.ResolvedStrings resolved = petResolver != null && petResolver.isLoaded()
                    ? petResolver.resolve(item)
                    : null;
            String displayName = resolved != null ? resolved.displayName() : item.displayName();
            List<String> lore = resolved != null ? resolved.lore() : item.lore();

            packer.packMapHeader(18);
            packer.packString("internalName");
            packer.packString(item.internalName());
            packer.packString("itemId");
            packer.packString(item.itemId());
            packer.packString("displayName");
            packer.packString(displayName);
            packer.packString("nbtTag");
            packer.packString(item.nbtTag());

            packer.packString("lore");
            packer.packArrayHeader(lore.size());
            for (String line : lore) {
                packer.packString(line);
            }

            packer.packString("damage");
            packer.packInt(item.damage());
            packer.packString("clickCommand");
            packer.packString(item.clickCommand());
            packer.packString("craftText");
            packer.packString(item.craftText());
            packer.packString("infoType");
            packer.packString(item.infoType());

            packer.packString("info");
            packer.packArrayHeader(item.info().size());
            for (String url : item.info()) {
                packer.packString(url);
            }

            packer.packString("recipe");
            if (item.recipe() instanceof NeuRecipe.CraftingRecipe(
                    Map<String, String> grid, int count, String overrideOutputId
            )) {
                packer.packMapHeader(3 + grid.size());
                packer.packString("_type");
                packer.packString("crafting");
                packer.packString("count");
                packer.packInt(count);
                packer.packString("overrideOutputId");
                packer.packString(overrideOutputId);
                for (Map.Entry<String, String> slot : grid.entrySet()) {
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

            packer.packString("vanilla");
            packer.packBoolean(item.vanilla());
            packer.packString("island");
            packer.packString(item.island());
            packer.packString("x");
            packer.packInt(item.x());
            packer.packString("y");
            packer.packInt(item.y());
            packer.packString("z");
            packer.packInt(item.z());
        }
    }

    private void packRecipe(MessagePacker packer, NeuRecipe recipe) throws IOException {
        switch (recipe) {
            case NeuRecipe.CraftingRecipe c -> {
                packer.packMapHeader(3 + c.grid().size());
                packer.packString("_type");
                packer.packString("crafting");
                packer.packString("count");
                packer.packInt(c.count());
                packer.packString("overrideOutputId");
                packer.packString(c.overrideOutputId());
                for (Map.Entry<String, String> slot : c.grid().entrySet()) {
                    packer.packString(slot.getKey());
                    packer.packString(slot.getValue());
                }
            }
            case NeuRecipe.ForgeRecipe f -> {
                packer.packMapHeader(5);
                packer.packString("_type");
                packer.packString("forge");
                packer.packString("count");
                packer.packInt(f.count());
                packer.packString("overrideOutputId");
                packer.packString(f.overrideOutputId());
                packer.packString("duration");
                packer.packInt(f.duration());
                packer.packString("inputs");
                packer.packArrayHeader(f.inputs().size());
                for (String input : f.inputs()) {
                    packer.packString(input);
                }
            }
            case NeuRecipe.KatGradeRecipe k -> {
                packer.packMapHeader(6);
                packer.packString("_type");
                packer.packString("katgrade");
                packer.packString("coins");
                packer.packInt(k.coins());
                packer.packString("time");
                packer.packInt(k.time());
                packer.packString("input");
                packer.packString(k.input());
                packer.packString("output");
                packer.packString(k.output());
                packer.packString("items");
                packer.packArrayHeader(k.items().size());
                for (String item : k.items()) {
                    packer.packString(item);
                }
            }
            case NeuRecipe.NpcShopRecipe n -> {
                packer.packMapHeader(4);
                packer.packString("_type");
                packer.packString("npc_shop");
                packer.packString("npc");
                packer.packString(n.npc());
                packer.packString("result");
                packer.packString(n.result());
                packer.packString("cost");
                packer.packArrayHeader(n.costs().size());
                for (NeuRecipe.NpcShopRecipe.Cost cost : n.costs()) {
                    packer.packMapHeader(2);
                    packer.packString("item");
                    packer.packString(cost.item());
                    packer.packString("cost");
                    packer.packInt(cost.cost());
                }
            }
            case NeuRecipe.DropsRecipe d -> {
                packer.packMapHeader(4);
                packer.packString("_type");
                packer.packString("drops");
                packer.packString("name");
                packer.packString(d.name());
                packer.packString("render");
                packer.packString(d.render());
                packer.packString("drops");
                packer.packArrayHeader(d.drops().size());
                for (NeuRecipe.DropsRecipe.Drop drop : d.drops()) {
                    packer.packMapHeader(2);
                    packer.packString("id");
                    packer.packString(drop.id());
                    packer.packString("chance");
                    packer.packString(drop.chance());
                }
            }
            case NeuRecipe.TradeRecipe t -> {
                packer.packMapHeader(6);
                packer.packString("_type");
                packer.packString("trade");
                packer.packString("cost");
                packer.packString(t.cost());
                packer.packString("result");
                packer.packString(t.result());
                packer.packString("count");
                packer.packInt(t.count());
                packer.packString("min");
                packer.packInt(t.min());
                packer.packString("max");
                packer.packInt(t.max());
            }
        }
    }

    // ---- MessagePack serialization (unchanged) ----

    private void packConstants(MessagePacker packer,
                               Map<String, List<String>> parents,
                               Map<String, EssenceUpgradeData> essenceCosts,
                               Set<String> bazaarItems,
                               Map<String, String> museumCategories,
                               Map<String, ReforgeData> reforges,
                               Map<String, ReforgeStoneData> reforgeStones,
                               Set<String> knownStats,
                               Map<String, String> reforgeNameToStone,
                               Map<String, MobRenderDefinition> mobDefinitions,
                               Map<String, byte[]> mobSkins) throws IOException {
        packer.packMapHeader(10);

        packer.packString("parents");
        packer.packMapHeader(parents.size());
        for (Map.Entry<String, List<String>> e : parents.entrySet()) {
            packer.packString(e.getKey());
            packer.packArrayHeader(e.getValue().size());
            for (String child : e.getValue()) {
                packer.packString(child);
            }
        }

        packer.packString("essenceCosts");
        packer.packMapHeader(essenceCosts.size());
        for (Map.Entry<String, EssenceUpgradeData> e : essenceCosts.entrySet()) {
            packer.packString(e.getKey());
            EssenceUpgradeData data = e.getValue();
            int mapSize = 1 + data.costsPerStar().size();
            if (!data.extraItemsPerStar().isEmpty()) mapSize++;
            packer.packMapHeader(mapSize);
            packer.packString("type");
            packer.packString(data.essenceType());
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

        packer.packString("bazaarItems");
        packer.packArrayHeader(bazaarItems.size());
        for (String item : bazaarItems) {
            packer.packString(item);
        }

        packer.packString("museum");
        packer.packMapHeader(museumCategories.size());
        for (Map.Entry<String, String> e : museumCategories.entrySet()) {
            packer.packString(e.getKey());
            packer.packString(e.getValue());
        }

        packer.packString("reforges");
        packer.packMapHeader(reforges.size());
        for (Map.Entry<String, ReforgeData> e : reforges.entrySet()) {
            packer.packString(e.getKey());
            ReforgeData d = e.getValue();
            int mapSize = 3;
            if (!d.statsPerRarity().isEmpty()) mapSize++;
            if (!d.reforgeAbility().isEmpty()) mapSize++;
            if (!d.reforgeCosts().isEmpty()) mapSize++;
            packer.packMapHeader(mapSize);
            packer.packString("reforgeName");
            packer.packString(d.reforgeName());
            packer.packString("itemTypes");
            packer.packString(d.itemTypes());
            packer.packString("requiredRarities");
            packer.packArrayHeader(d.requiredRarities().size());
            for (String r : d.requiredRarities()) packer.packString(r);
            if (!d.statsPerRarity().isEmpty()) {
                packer.packString("reforgeStats");
                packer.packMapHeader(d.statsPerRarity().size());
                for (Map.Entry<String, Map<String, Number>> se : d.statsPerRarity().entrySet()) {
                    packer.packString(se.getKey());
                    packer.packMapHeader(se.getValue().size());
                    for (Map.Entry<String, Number> stat : se.getValue().entrySet()) {
                        packer.packString(stat.getKey());
                        packer.packDouble(stat.getValue().doubleValue());
                    }
                }
            }
            if (!d.reforgeAbility().isEmpty()) {
                packer.packString("reforgeAbility");
                packer.packMapHeader(d.reforgeAbility().size());
                for (Map.Entry<String, String> ae : d.reforgeAbility().entrySet()) {
                    packer.packString(ae.getKey());
                    packer.packString(ae.getValue());
                }
            }
            if (!d.reforgeCosts().isEmpty()) {
                packer.packString("reforgeCosts");
                packer.packMapHeader(d.reforgeCosts().size());
                for (Map.Entry<String, Number> ce : d.reforgeCosts().entrySet()) {
                    packer.packString(ce.getKey());
                    packer.packInt(ce.getValue().intValue());
                }
            }
        }

        packer.packString("reforgeStones");
        packer.packMapHeader(reforgeStones.size());
        for (Map.Entry<String, ReforgeStoneData> e : reforgeStones.entrySet()) {
            packer.packString(e.getKey());
            ReforgeStoneData d = e.getValue();
            int mapSize = 5;
            if (!d.reforgeAbility().isEmpty()) mapSize++;
            if (!d.reforgeCosts().isEmpty()) mapSize++;
            if (!d.reforgeStats().isEmpty()) mapSize++;
            packer.packMapHeader(mapSize);
            packer.packString("internalName");
            packer.packString(d.internalName());
            packer.packString("reforgeName");
            packer.packString(d.reforgeName());
            packer.packString("reforgeType");
            packer.packString(d.reforgeType());
            packer.packString("itemTypes");
            packer.packString(d.itemTypes());
            packer.packString("requiredRarities");
            packer.packArrayHeader(d.requiredRarities().size());
            for (String r : d.requiredRarities()) packer.packString(r);
            if (!d.reforgeAbility().isEmpty()) {
                packer.packString("reforgeAbility");
                packer.packMapHeader(d.reforgeAbility().size());
                for (Map.Entry<String, String> ae : d.reforgeAbility().entrySet()) {
                    packer.packString(ae.getKey());
                    packer.packString(ae.getValue());
                }
            }
            if (!d.reforgeCosts().isEmpty()) {
                packer.packString("reforgeCosts");
                packer.packMapHeader(d.reforgeCosts().size());
                for (Map.Entry<String, Number> ce : d.reforgeCosts().entrySet()) {
                    packer.packString(ce.getKey());
                    packer.packInt(ce.getValue().intValue());
                }
            }
            if (!d.reforgeStats().isEmpty()) {
                packer.packString("reforgeStats");
                packer.packMapHeader(d.reforgeStats().size());
                for (Map.Entry<String, Map<String, Number>> se : d.reforgeStats().entrySet()) {
                    packer.packString(se.getKey());
                    packer.packMapHeader(se.getValue().size());
                    for (Map.Entry<String, Number> stat : se.getValue().entrySet()) {
                        packer.packString(stat.getKey());
                        packer.packDouble(stat.getValue().doubleValue());
                    }
                }
            }
        }

        packer.packString("knownStats");
        packer.packArrayHeader(knownStats.size());
        for (String stat : knownStats) {
            packer.packString(stat);
        }

        packer.packString("reforgeNameToStone");
        packer.packMapHeader(reforgeNameToStone.size());
        for (Map.Entry<String, String> e : reforgeNameToStone.entrySet()) {
            packer.packString(e.getKey());
            packer.packString(e.getValue());
        }

        packer.packString("mobDefinitions");
        packer.packMapHeader(mobDefinitions.size());
        for (Map.Entry<String, MobRenderDefinition> e : mobDefinitions.entrySet()) {
            packer.packString(e.getKey());
            MobRenderDefinition d = e.getValue();
            int mapSize = 1;
            if (d.horseKind() != null) mapSize++;
            if (d.skinPath() != null) mapSize++;
            if (d.helmetItemId() != null) mapSize++;
            if (d.rider() != null) mapSize++;
            packer.packMapHeader(mapSize);
            packer.packString("entityKind");
            packer.packString(d.entityKind());
            if (d.horseKind() != null) {
                packer.packString("horseKind");
                packer.packString(d.horseKind());
            }
            if (d.skinPath() != null) {
                packer.packString("skinPath");
                packer.packString(d.skinPath());
            }
            if (d.helmetItemId() != null) {
                packer.packString("helmetItemId");
                packer.packString(d.helmetItemId());
            }
            if (d.rider() != null) {
                packer.packString("rider");
                packMobRenderDefinition(packer, d.rider());
            }
        }

        packer.packString("mobSkins");
        packer.packMapHeader(mobSkins.size());
        for (Map.Entry<String, byte[]> e : mobSkins.entrySet()) {
            packer.packString(e.getKey());
            packer.packBinaryHeader(e.getValue().length);
            packer.addPayload(e.getValue());
        }
    }

    private void packMobRenderDefinition(MessagePacker packer, MobRenderDefinition d) throws IOException {
        int mapSize = 1;
        if (d.horseKind() != null) mapSize++;
        if (d.skinPath() != null) mapSize++;
        if (d.helmetItemId() != null) mapSize++;
        if (d.rider() != null) mapSize++;
        packer.packMapHeader(mapSize);
        packer.packString("entityKind");
        packer.packString(d.entityKind());
        if (d.horseKind() != null) {
            packer.packString("horseKind");
            packer.packString(d.horseKind());
        }
        if (d.skinPath() != null) {
            packer.packString("skinPath");
            packer.packString(d.skinPath());
        }
        if (d.helmetItemId() != null) {
            packer.packString("helmetItemId");
            packer.packString(d.helmetItemId());
        }
        if (d.rider() != null) {
            packer.packString("rider");
            packMobRenderDefinition(packer, d.rider());
        }
    }

    /**
     * Progress callback for long-running compiles.
     */
    public interface ProgressCallback {
        void onProgress(String stage, int percent);
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
