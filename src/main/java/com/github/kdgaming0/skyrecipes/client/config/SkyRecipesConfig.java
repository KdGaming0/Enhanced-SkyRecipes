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
    // Calculator settings
    // -----------------------------------------------------------------
    @Entry(category = "calculator")
    public static boolean calculatorEnabled = true;

    @Entry(category = "calculator")
    public static CalculatorInputMode calculatorInputMode = CalculatorInputMode.SMART;

    @Entry(category = "calculator")
    public static CalculatorDisplayMode calculatorDisplayMode = CalculatorDisplayMode.INLINE;

    @Entry(category = "calculator", isSlider = true, min = 0, max = 10, precision = 0)
    public static int calculatorPrecision = 5;

    @Entry(category = "calculator")
    public static CalculatorResultFormat calculatorResultFormat = CalculatorResultFormat.ADAPTIVE;

    @Entry(category = "calculator", isSlider = true, min = 1, max = 20, precision = 0)
    public static int calculatorHistorySize = 10;

    @Entry(category = "calculator")
    public static boolean calculatorContextSuggestions = true;

    // -----------------------------------------------------------------
    // UI settings
    // -----------------------------------------------------------------
    @Entry(category = "ui")
    public static boolean familyExpansionEnabled = true;

    // -----------------------------------------------------------------
    // RRV integration settings
    // -----------------------------------------------------------------
    @Entry(category = "rrv")
    public static boolean groupTieredItems = true;

    @Entry(category = "rrv")
    public static boolean groupCraftedChains = true;

    @Entry(category = "rrv")
    public static boolean shardFusionRecipes = true;

    @Entry(category = "rrv")
    public static boolean hideCategoryButtons = false;

    @Entry(category = "rrv")
    public static boolean hideCategoryButtonsWhenNotSearching = true;

    @Entry(category = "rrv")
    public static boolean hideEmptyBookmarkPanel = true;

    @Entry(category = "rrv")
    public static boolean craftablesCountAware = true;

    @Entry(category = "rrv")
    public static boolean wideRrvSearchBar = true;

    @Entry(category = "rrv")
    public static boolean blockKeybindsWhileTyping = true;

    @Entry(category = "rrv", isSlider = true, min = 100, max = 300, precision = 1)
    public static int rrvSearchBarWidth = 200;

    @Entry(category = "rrv", isSlider = true, min = 25, max = 100, precision = 1)
    public static int rrvItemListWidthPercent = 100;

    @Entry(category = "rrv", isSlider = true, min = 25, max = 100, precision = 1)
    public static int rrvSidePanelWidthPercent = 100;

    // -----------------------------------------------------------------
    // Data settings
    // -----------------------------------------------------------------
    @Entry(category = "data", min = 15, max = 480, isSlider = true)
    public static int dataRefreshIntervalMinutes = 60;

    public enum CalculatorInputMode {
        SMART,
        EXPLICIT_ONLY
    }

    public enum CalculatorDisplayMode {
        INLINE,
        PANEL
    }

    public enum CalculatorResultFormat {
        ADAPTIVE,
        FULL,
        COMPACT
    }
}
