package com.github.kdgaming0.skyrecipes.core.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Safe JSON parsing helpers with default values.
 */
public final class JsonUtil {

    private JsonUtil() {}

    public static String getString(JsonObject obj, String key, String defaultValue) {
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) {
            return defaultValue;
        }
        return element.getAsString();
    }

    public static String getString(JsonObject obj, String key) {
        return getString(obj, key, "");
    }

    public static int getInt(JsonObject obj, String key, int defaultValue) {
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) {
            return defaultValue;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            // Handle string representations of floats like "20.0"
            String str = element.getAsString();
            try {
                return (int) Double.parseDouble(str);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        try {
            return element.getAsInt();
        } catch (NumberFormatException e) {
            // Fallback for numbers stored as floats in JSON
            return (int) element.getAsDouble();
        }
    }

    public static boolean getBoolean(JsonObject obj, String key, boolean defaultValue) {
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) {
            return defaultValue;
        }
        return element.getAsBoolean();
    }

    public static List<String> getStringList(JsonObject obj, String key) {
        List<String> result = new ArrayList<>();
        JsonElement element = obj.get(key);
        if (element == null || !element.isJsonArray()) {
            return result;
        }
        for (JsonElement item : element.getAsJsonArray()) {
            if (item != null && !item.isJsonNull()) {
                result.add(item.getAsString());
            }
        }
        return result;
    }

    public static JsonObject getObject(JsonObject obj, String key) {
        JsonElement element = obj.get(key);
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        return element.getAsJsonObject();
    }
}
