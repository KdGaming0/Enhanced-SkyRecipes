package com.github.kdgaming0.skyrecipes.client.gui;

import com.github.kdgaming0.skyrecipes.client.config.SkyRecipesConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CalculatorResultFormatterTest {

    @Test
    void fullFormattingGroupsAndTrimsDecimals() {
        assertEquals("1,234,567.89", CalculatorResultFormatter.full(new BigDecimal("1234567.89000"), 5));
        assertEquals("0", CalculatorResultFormatter.full(new BigDecimal("-0.00001"), 2));
    }

    @Test
    void compactFormattingUsesSkyBlockSuffixes() {
        assertEquals("2.5m", CalculatorResultFormatter.compact(new BigDecimal("2500000"), 3));
        assertEquals("1.235b", CalculatorResultFormatter.compact(new BigDecimal("1234567890"), 3));
    }

    @Test
    void adaptiveFallsBackWhenFullTextDoesNotFit() {
        String result = CalculatorResultFormatter.format(
                new BigDecimal("2500000"), SkyRecipesConfig.CalculatorResultFormat.ADAPTIVE,
                3, text -> text.length() <= 5);
        assertEquals("2.5m", result);
    }

    @Test
    void hugeValuesUseScientificNotationWithoutMaterializingFixedPointText() {
        BigDecimal huge = new BigDecimal("1e10000");
        assertEquals("1e10000", CalculatorResultFormatter.full(huge, 5));
        assertEquals("1e10000", CalculatorResultFormatter.compact(huge, 5));
        assertEquals("1E+10000", CalculatorResultFormatter.exact(huge));
    }

    @Test
    void exactFormattingIsUngroupedAndNonScientific() {
        assertEquals("2500000", CalculatorResultFormatter.exact(new BigDecimal("2.5E+6")));
        assertEquals("0.125", CalculatorResultFormatter.exact(new BigDecimal("0.12500")));
    }
}
