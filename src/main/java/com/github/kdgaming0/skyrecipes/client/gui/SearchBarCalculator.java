package com.github.kdgaming0.skyrecipes.client.gui;

import com.github.kdgaming0.skyrecipes.client.config.SkyRecipesConfig;
import com.github.kdgaming0.skyrecipes.core.util.NeuCalculator;

import java.math.BigDecimal;
import java.text.DecimalFormat;

/**
 * Evaluates mathematical expressions typed into the RRV search bar and formats
 * the result as ghost-text suggestion text.
 *
 * <p>Mirrors the behaviour of NotEnoughUpdates' {@code NeuSearchCalculator}.
 * Results are cached per input to avoid re-evaluating on every keystroke.</p>
 */
public final class SearchBarCalculator {

    private static String lastInput = "";
    private static String lastResult = null;

    private SearchBarCalculator() {
    }

    /**
     * Evaluates the input and returns a formatted result string suitable for
     * {@link net.minecraft.client.gui.components.EditBox#setSuggestion}.
     *
     * @param input the raw search-bar text
     * @return {@code " = <result>"} if evaluation succeeds, otherwise {@code null}
     */
    public static String calculateSuggestion(String input) {
        if (!SkyRecipesConfig.calculatorEnabled || input == null || input.isBlank()) {
            return null;
        }

        if (!lastInput.equals(input)) {
            lastInput = input;
            try {
                BigDecimal result = NeuCalculator.calculate(input, SkyRecipesConfig.calculatorPrecision);
                lastResult = " = " + formatResult(result, SkyRecipesConfig.calculatorPrecision);
            } catch (NeuCalculator.CalculatorException ignored) {
                lastResult = null;
            }
        }

        return lastResult;
    }

    private static String formatResult(BigDecimal value, int precision) {
        StringBuilder pattern = new StringBuilder("#,##0.");
        for (int i = 0; i < precision; i++) {
            pattern.append("#");
        }
        return new DecimalFormat(pattern.toString()).format(value);
    }
}
