package com.github.kdgaming0.skyrecipes.client.config;

import eu.midnightdust.lib.config.MidnightConfig;

/**
 * MidnightLib configuration for SkyRecipes.
 *
 * <p>All fields must be {@code public static} (not {@code final}) so MidnightLib
 * can read and write them. Defaults are set via inline initialisers.</p>
 *
 * <p><b>Note on recipe categories:</b> Recipe category visibility is managed natively
 * by RRV through its Recipe Category Config screen. SkyRecipes no longer maintains
 * separate category toggles — all SkyBlock recipe categories are auto-discovered by
 * RRV and can be enabled or disabled through RRV's settings.</p>
 */
public class SkyRecipesConfig extends MidnightConfig {

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

    @Entry(category = "ui")
    public static boolean searchCategoryButtonsVisible = true;

    @Entry(category = "ui", min = 1, max = 100)
    public static int searchMaxResults = 50;

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
}
