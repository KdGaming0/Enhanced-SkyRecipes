package com.github.kdgaming0.skyrecipes.core.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Background update checker that compares the local binary's NEU commit hash
 * against the latest GitHub ETag to determine if new data is available.
 *
 * <p>Runs on a background thread to avoid blocking the render thread.
 */
public class DataUpdateChecker {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataUpdateChecker.class);

    private static final String NEU_REPO_URL =
        "https://codeload.github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/zip/refs/heads/master";

    private final long embeddedCommitHash;
    private final Path cacheDir;

    public DataUpdateChecker(long embeddedCommitHash, Path cacheDir) {
        this.embeddedCommitHash = embeddedCommitHash;
        this.cacheDir = cacheDir;
    }

    /**
     * Schedules a background check after the specified delay.
     */
    public void scheduleCheck(long delayMs) {
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(delayMs);
                performCheck();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "SkyRecipes-DataUpdateChecker");
        thread.setDaemon(true);
        thread.start();
    }

    private void performCheck() {
        try {
            LOGGER.info("Checking for NEU repo updates...");

            URL url = new URL(NEU_REPO_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setInstanceFollowRedirects(true);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                LOGGER.warn("Update check failed: HTTP {}", responseCode);
                return;
            }

            String etag = conn.getHeaderField("ETag");
            conn.disconnect();

            if (etag == null) {
                LOGGER.warn("No ETag received from GitHub, cannot determine if update is needed");
                return;
            }

            // Simple hash of ETag for comparison
            long etagHash = hashEtag(etag);
            if (etagHash == embeddedCommitHash) {
                LOGGER.info("Data is up to date (ETag match)");
                return;
            }

            LOGGER.info("New NEU repo data detected (ETag changed). Consider updating the mod for latest recipes.");

            // Future: trigger background download + recompile here
            // For Milestone 1, we only log the detection

        } catch (IOException e) {
            LOGGER.warn("Update check failed due to network error", e);
        }
    }

    private long hashEtag(String etag) {
        // Use first 8 bytes of SHA-256 of ETag as pseudo-commit-hash
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
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
}
