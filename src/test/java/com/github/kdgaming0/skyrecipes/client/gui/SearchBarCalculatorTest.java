package com.github.kdgaming0.skyrecipes.client.gui;

import com.github.kdgaming0.skyrecipes.client.config.SkyRecipesConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchBarCalculatorTest {

    @BeforeEach
    void resetConfig() {
        SkyRecipesConfig.calculatorInputMode = SkyRecipesConfig.CalculatorInputMode.SMART;
        SkyRecipesConfig.calculatorContextSuggestions = true;
    }

    @Test
    void smartModeRecognizesUnambiguousCalculations() {
        assertCalculator("2+2");
        assertCalculator("50m / 1.2k");
        assertCalculator("sqrt(9)");
        assertCalculator("1.5e6");
        assertCalculator("1.5e");
        assertCalculator("-5");
        assertCalculator("(2)");
        assertCalculator("2+");
        assertCalculator("2x2");
        assertCalculator("2 x 2");
        assertCalculator("2x 2");
        assertCalculator("2 x2");
        assertCalculator("2 x");
    }

    @Test
    void smartModeLeavesNormalSearchesAlone() {
        assertNormal("42");
        assertNormal("aote");
        assertNormal("damage>100");
        assertNormal("%ARMOR");
        assertNormal("rarity:legendary");
        assertNormal("1,234");
        assertNormal("100midas");
    }

    @Test
    void explicitModeAlwaysWinsAndProvidesHelp() {
        SearchBarCalculator.Calculation invalid = SearchBarCalculator.classifyAndEvaluate("=aote", false, null);
        assertTrue(invalid.isCalculator());
        assertTrue(invalid.evaluation().isError());

        assertTrue(SearchBarCalculator.classifyAndEvaluate("=?", false, null).isHelp());
        assertTrue(SearchBarCalculator.classifyAndEvaluate("=help", false, null).isHelp());
    }

    @Test
    void activeSessionKeepsBareIntermediateValuesInCalculatorMode() {
        assertTrue(SearchBarCalculator.classifyAndEvaluate("2", true, null).isCalculator());
        assertTrue(SearchBarCalculator.classifyAndEvaluate("2+sq", true, null).isCalculator());
        assertFalse(SearchBarCalculator.classifyAndEvaluate("aote", true, null).isCalculator());
    }

    @Test
    void smartCandidatePrefixesCoverNumbersDecimalsFunctionsAndAns() {
        assertTrue(SearchBarCalculator.isSmartPrefix("2"));
        assertTrue(SearchBarCalculator.isSmartPrefix("."));
        assertTrue(SearchBarCalculator.isSmartPrefix(".5"));
        assertTrue(SearchBarCalculator.isSmartPrefix("sq"));
        assertTrue(SearchBarCalculator.isSmartPrefix("an"));
        assertFalse(SearchBarCalculator.isSmartPrefix("aote"));
    }

    @Test
    void calculatorContextCompletionIsInputBoundSuffix() {
        SearchBarCalculator.Calculation calculation = SearchBarCalculator.classifyAndEvaluate("=sq", false, null);
        assertEquals("rt(", calculation.completionSuffix());

        assertEquals("rt(", SearchBarCalculator.classifyAndEvaluate("=2 + sq", false, null).completionSuffix());
        assertEquals("rt(", SearchBarCalculator.classifyAndEvaluate("=min(1, sq", false, null).completionSuffix());
        assertEquals("rt(", SearchBarCalculator.classifyAndEvaluate("=2 x sq", false, null).completionSuffix());
    }

    @Test
    void numericSuffixesAreResultsRatherThanFunctionCompletions() {
        assertSuccessfulCalculation("10s+10", "650");
        assertSuccessfulCalculation("10s+10s", "1280");
        assertSuccessfulCalculation("sqrt(9)s", "192");

        assertNull(SearchBarCalculator.classifyAndEvaluate("10s+10", false, null).completionSuffix());
        assertNull(SearchBarCalculator.classifyAndEvaluate("10s+10s", false, null).completionSuffix());
        assertNull(SearchBarCalculator.classifyAndEvaluate("sqrt(9)s", false, null).completionSuffix());
        assertNull(SearchBarCalculator.classifyAndEvaluate("=max sq", false, null).completionSuffix());
    }

    @Test
    void explicitOnlyDisablesUnprefixedSmartDetection() {
        SkyRecipesConfig.calculatorInputMode = SkyRecipesConfig.CalculatorInputMode.EXPLICIT_ONLY;
        assertNormal("2+2");
        assertCalculator("=2+2");
    }

    @Test
    void ansUsesTheSessionValue() {
        SearchBarCalculator.Calculation calculation = SearchBarCalculator.classifyAndEvaluate(
                "=ans * 2", false, BigDecimal.valueOf(21));
        assertEquals(0, new BigDecimal("42").compareTo(calculation.evaluation().result()));
    }

    private static void assertCalculator(String input) {
        assertTrue(SearchBarCalculator.classifyAndEvaluate(input, false, null).isCalculator(), input);
    }

    private static void assertNormal(String input) {
        assertFalse(SearchBarCalculator.classifyAndEvaluate(input, false, null).isCalculator(), input);
    }

    private static void assertSuccessfulCalculation(String input, String expected) {
        SearchBarCalculator.Calculation calculation =
                SearchBarCalculator.classifyAndEvaluate(input, false, null);
        assertTrue(calculation.isCalculator(), input);
        assertTrue(calculation.evaluation().isSuccess(), input);
        assertEquals(0, new BigDecimal(expected).compareTo(calculation.evaluation().result()), input);
    }
}
