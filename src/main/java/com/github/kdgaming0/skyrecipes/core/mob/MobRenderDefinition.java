package com.github.kdgaming0.skyrecipes.core.mob;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

/**
 * Parsed {@code mobs/*.json} entry from the NEU repo, reduced to the fields that matter for
 * drop-recipe preview rendering.
 *
 * <p>Supported entity kinds:</p>
 * <ul>
 *   <li>{@code Player} with {@code skin} path → rendered as player model with custom skin</li>
 *   <li>{@code Horse} with {@code kind} → skeleton or zombie horse</li>
 *   <li>{@code ArmorStand} with {@code helmet} → rendered as skull item</li>
 *   <li>Any vanilla entity name → resolved via {@link VanillaEntityNames}</li>
 * </ul>
 */
public record MobRenderDefinition(
        String entityKind,
        @Nullable String horseKind,
        @Nullable String skinPath,
        @Nullable String helmetItemId,
        @Nullable MobRenderDefinition rider) {

    @Nullable
    public static MobRenderDefinition parse(@Nullable JsonObject obj) {
        return parseRecursive(obj);
    }

    @Nullable
    private static MobRenderDefinition parseRecursive(@Nullable JsonObject obj) {
        if (obj == null) return null;
        String entity = readString(obj, "entity");
        if (entity == null) return null;

        JsonArray modifiers = readArray(obj);
        ModifierScan scan = scanModifiers(modifiers);

        return new MobRenderDefinition(entity, scan.horseKind, scan.skinPath, scan.helmetItemId, scan.rider);
    }

    private static ModifierScan scanModifiers(@Nullable JsonArray modifiers) {
        ModifierScan scan = new ModifierScan();
        if (modifiers == null) return scan;

        for (var element : modifiers) {
            if (!element.isJsonObject()) continue;
            JsonObject mod = element.getAsJsonObject();
            String type = readString(mod, "type");
            if (type == null) continue;

            switch (type) {
                case "playerdata" -> scan.skinPath = firstNonNull(scan.skinPath, readString(mod, "skin"));
                case "horse" -> scan.horseKind = firstNonNull(scan.horseKind, readString(mod, "kind"));
                case "equipment" -> scan.helmetItemId = firstNonNull(scan.helmetItemId, readString(mod, "helmet"));
                case "riding" -> scan.rider = firstNonNull(scan.rider, parseRecursive(mod));
                default -> { /* ignore */ }
            }
        }
        return scan;
    }

    @Nullable
    private static String readString(JsonObject obj, String key) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsString() : null;
    }

    @Nullable
    private static JsonArray readArray(JsonObject obj) {
        return obj.has("modifiers") && obj.get("modifiers").isJsonArray() ? obj.getAsJsonArray("modifiers") : null;
    }

    private static <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }

    public boolean isArmorStandSkull() {
        return "ArmorStand".equals(entityKind) && helmetItemId != null;
    }

    private static final class ModifierScan {
        @Nullable String horseKind;
        @Nullable String skinPath;
        @Nullable String helmetItemId;
        @Nullable MobRenderDefinition rider;
    }
}
