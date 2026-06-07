package com.github.kdgaming0.skyrecipes.core.render;

import com.github.kdgaming0.skyrecipes.SkyRecipes;
import com.github.kdgaming0.skyrecipes.core.registry.ConstantsRegistry;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lazy-loading registry for NEU mob skin PNG textures.
 *
 * <p>Raw PNG bytes are stored in {@link ConstantsRegistry} (loaded from the .mpk binary).
 * On first render access, the bytes are decoded into a {@link DynamicTexture} and uploaded
 * to the GPU. Failed loads are cached as {@code null} to avoid repeated decode attempts.</p>
 */
public final class MobSkinRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(MobSkinRegistry.class);
    private static final Map<String, Identifier> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> FAILED = new ConcurrentHashMap<>();
    private static final String PREFIX = "skyrecipes/mobskin/";

    private MobSkinRegistry() {}

    /**
     * Get or create a GPU texture for the given NEU skin path.
     *
     * @param skinPath NEU skin path (e.g. "neurepo:mobs/alligator.png")
     * @return an {@link Identifier} pointing to the uploaded texture, or {@code null} on failure
     */
    public static Identifier getOrLoad(String skinPath) {
        if (skinPath == null || skinPath.isEmpty()) {
            return null;
        }

        Identifier cached = CACHE.get(skinPath);
        if (cached != null) {
            return cached;
        }
        if (FAILED.containsKey(skinPath)) {
            return null;
        }

        ConstantsRegistry constants = SkyRecipes.getConstantsRegistry();
        if (constants == null) {
            return null;
        }

        byte[] pngBytes = constants.getMobSkin(skinPath);
        if (pngBytes == null || pngBytes.length == 0) {
            FAILED.put(skinPath, Boolean.TRUE);
            LOGGER.debug("No skin bytes found for path: {}", skinPath);
            return null;
        }

        try {
            NativeImage image;
            try (ByteArrayInputStream bais = new ByteArrayInputStream(pngBytes)) {
                image = NativeImage.read(bais);
            }
            image = processSkin(image);
            Identifier id = Identifier.fromNamespaceAndPath("skyrecipes", PREFIX + normalizePath(skinPath));
            DynamicTexture texture = new DynamicTexture(() -> "MobSkin[" + skinPath + "]", image);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            CACHE.put(skinPath, id);
            return id;
        } catch (Exception e) {
            FAILED.put(skinPath, Boolean.TRUE);
            LOGGER.debug("Failed to decode mob skin '{}': {}", skinPath, e.getMessage());
            return null;
        }
    }

    /**
     * Clear all cached textures and failure flags. Called on data reload.
     */
    public static void clear() {
        for (Identifier id : CACHE.values()) {
            Minecraft.getInstance().getTextureManager().release(id);
        }
        CACHE.clear();
        FAILED.clear();
    }

    private static String normalizePath(String path) {
        return path.replace(':', '/').replace('.', '_');
    }

    /**
     * Ensures the skin is in a renderable format:
     * <ul>
     * <li>64×32 legacy skins are converted to 64×64</li>
     * <li>The base head layer is made fully opaque</li>
     * </ul>
     */
    private static NativeImage processSkin(NativeImage image) {
        int w = image.getWidth();
        int h = image.getHeight();

        if (w == 64 && h == 32) {
            image = convertLegacySkin(image);
        }

        // Ensure base head layer is fully opaque so transparent pixels
        // in the source PNG don't create holes in the rendered model.
        setNoAlpha(image, 0, 0, 32, 16);

        return image;
    }

    /** Converts a 64×32 legacy skin to the modern 64×64 format. */
    private static NativeImage convertLegacySkin(NativeImage image) {
        NativeImage newImage = new NativeImage(64, 64, true);
        newImage.copyFrom(image);
        image.close();

        // Clear the overlay half so out-of-bounds UVs sample transparent.
        newImage.fillRect(0, 32, 64, 32, 0);

        // Copy old "armor" areas into the new overlay layer positions.
        newImage.copyRect(4, 16, 16, 32, 4, 4, true, false);
        newImage.copyRect(8, 16, 16, 32, 4, 4, true, false);
        newImage.copyRect(0, 20, 24, 32, 4, 12, true, false);
        newImage.copyRect(4, 20, 16, 32, 4, 12, true, false);
        newImage.copyRect(8, 20, 8, 32, 4, 12, true, false);
        newImage.copyRect(12, 20, 16, 32, 4, 12, true, false);
        newImage.copyRect(44, 16, -8, 32, 4, 4, true, false);
        newImage.copyRect(48, 20, -16, 32, 4, 12, true, false);
        newImage.copyRect(52, 20, -8, 32, 4, 12, true, false);

        return newImage;
    }

    /** Forces alpha = 255 for every pixel in the given rectangle. */
    private static void setNoAlpha(NativeImage image, int x1, int y1, int x2, int y2) {
        for (int x = x1; x < x2; x++) {
            for (int y = y1; y < y2; y++) {
                image.setPixel(x, y, ARGB.opaque(image.getPixel(x, y)));
            }
        }
    }
}
