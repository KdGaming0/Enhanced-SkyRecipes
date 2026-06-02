package com.github.kdgaming0.skyrecipes.client.config;

import eu.midnightdust.lib.config.MidnightConfig;

/**
 * MidnightLib configuration for SkyRecipes.
 *
 * <p>All fields must be {@code public static} (not {@code final}) so MidnightLib
 * can read and write them. Defaults are set via inline initialisers.</p>
 */
public class SkyRecipesConfig extends MidnightConfig {

    // -----------------------------------------------------------------
    // Category toggles
    // -----------------------------------------------------------------
    @Entry(category = "categories")
    public static boolean categoryCraftingEnabled = true;

    @Entry(category = "categories")
    public static boolean categoryForgeEnabled = true;

    @Entry(category = "categories")
    public static boolean categoryDropsEnabled = true;

    @Entry(category = "categories")
    public static boolean categoryNpcShopEnabled = true;

    @Entry(category = "categories")
    public static boolean categoryNpcInfoEnabled = true;

    @Entry(category = "categories")
    public static boolean categoryKatUpgradeEnabled = true;

    @Entry(category = "categories")
    public static boolean categoryTradeEnabled = true;

    @Entry(category = "categories")
    public static boolean categoryWikiInfoEnabled = true;

    @Entry(category = "categories")
    public static boolean categoryEssenceEnabled = true;

    @Entry(category = "categories")
    public static boolean categoryReforgeEnabled = true;

    @Entry(category = "categories")
    public static boolean categoryGardenEnabled = true;

    // -----------------------------------------------------------------
    // UI settings
    // -----------------------------------------------------------------
    @Entry(category = "ui")
    public static boolean categoryButtonsVisible = true;

    @Entry(category = "ui")
    public static SearchBarMode searchBarMode = SearchBarMode.SHOW_WHEN_SEARCHING;

    @Entry(category = "ui", min = 0, max = 5)
    public static int fuzzyThreshold = 2;

    @Entry(category = "ui", min = 1, max = 20)
    public static int autocompleteMax = 10;

    @Entry(category = "ui")
    public static boolean calculatorEnabled = true;

    @Entry(category = "ui")
    public static boolean familyExpansionEnabled = true;

    // -----------------------------------------------------------------
    // Data & debug settings
    // -----------------------------------------------------------------
    @Entry(category = "data", min = 1, max = 168)
    public static int dataRefreshIntervalHours = 24;

    @Entry(category = "data", min = 1, max = 168)
    public static int hypixelCacheTtlHours = 24;

    @Entry(category = "debug")
    public static boolean recipeDiagnosticMode = false;

    @Entry(category = "debug")
    public static boolean debugLogging = false;

    /**
     * Returns true if the given recipe category is enabled in config.
     *
     * @param categoryId the RRV recipe type identifier path (e.g. "crafting", "forge")
     * @return true if enabled
     */
    public static boolean isCategoryEnabled(String categoryId) {
        return switch (categoryId) {
            case "crafting" -> categoryCraftingEnabled;
            case "forge" -> categoryForgeEnabled;
            case "drops" -> categoryDropsEnabled;
            case "npc_shop" -> categoryNpcShopEnabled;
            case "npc_info" -> categoryNpcInfoEnabled;
            case "kat_upgrade" -> categoryKatUpgradeEnabled;
            case "trade" -> categoryTradeEnabled;
            case "wiki_info" -> categoryWikiInfoEnabled;
            case "essence" -> categoryEssenceEnabled;
            case "reforge" -> categoryReforgeEnabled;
            case "garden" -> categoryGardenEnabled;
            default -> true;
        };
    }
}
