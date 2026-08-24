package com.github.kdgaming0.skyrecipes.core.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeuCalculatorTest {

    @Test
    void powersAreRightAssociativeAndBindMoreTightlyThanUnaryMinus() throws Exception {
        assertDecimal("512", NeuCalculator.calculate("2^3^2", 0));
        assertDecimal("512", NeuCalculator.calculate("2**3**2", 0));
        assertDecimal("-4", NeuCalculator.calculate("-2^2", 0));
        assertDecimal("4", NeuCalculator.calculate("(-2)^2", 0));
    }

    @Test
    void binaryOperatorsHonorPrecedenceInEitherOrder() throws Exception {
        assertDecimal("7", NeuCalculator.calculate("1+2*3", 0));
        assertDecimal("-5", NeuCalculator.calculate("1-2*3", 0));
        assertDecimal("1.5", NeuCalculator.calculate("1+2/4", 0));
        assertDecimal("0.5", NeuCalculator.calculate("1-2/4", 0));
        assertDecimal("7", NeuCalculator.calculate("1+2x3", 0));
        assertDecimal("-5", NeuCalculator.calculate("1-2x3", 0));

        assertDecimal("5", NeuCalculator.calculate("1*2+3", 0));
        assertDecimal("-1", NeuCalculator.calculate("1*2-3", 0));
        assertDecimal("3.5", NeuCalculator.calculate("1/2+3", 0));
        assertDecimal("-2.5", NeuCalculator.calculate("1/2-3", 0));
        assertDecimal("5", NeuCalculator.calculate("1x2+3", 0));
        assertDecimal("-1", NeuCalculator.calculate("1x2-3", 0));
    }

    @Test
    void xMultiplicationSupportsCompactAndSpacedForms() throws Exception {
        assertDecimal("6", NeuCalculator.calculate("2x3", 0));
        assertDecimal("6", NeuCalculator.calculate("2x 3", 0));
        assertDecimal("6", NeuCalculator.calculate("2 x3", 0));
        assertDecimal("6", NeuCalculator.calculate("2 x 3", 0));
        assertDecimal("6", NeuCalculator.calculate("2X3", 0));
        assertDecimal("8", NeuCalculator.calculate("2x(3+1)", 0));
        assertDecimal("6", NeuCalculator.calculate("sqrt(9)x2", 0));
        assertDecimal("6000", NeuCalculator.calculate("2kx3", 0));
    }

    @Test
    void mixedExpressionsPreserveEveryGrammarLevel() throws Exception {
        assertDecimal("154", NeuCalculator.calculate("10+12*12", 0));
        assertDecimal("156", NeuCalculator.calculate("12+12x12", 0));
        assertDecimal("6", NeuCalculator.calculate("10-12/3", 0));
        assertDecimal("11", NeuCalculator.calculate("2+3^2", 0));
        assertDecimal("8", NeuCalculator.calculate("2^(1+2)", 0));
        assertDecimal("7", NeuCalculator.calculate("max(1, 2*3)+1", 0));
        assertDecimal("1281", NeuCalculator.calculate("1+10s*2", 0));
    }

    @Test
    void supportsBigDecimalLiteralsScientificNotationAndPostfixes() throws Exception {
        assertDecimal("1500000", NeuCalculator.calculate("1.5e6", 2));
        assertDecimal("240", NeuCalculator.calculate("1.5e", 2));
        assertDecimal("2000", NeuCalculator.calculate("2K", 2));
        assertDecimal("650", NeuCalculator.calculate("10s+10", 2));
        assertDecimal("1280", NeuCalculator.calculate("10s+10s", 2));
        assertDecimal("0.125", NeuCalculator.calculate("12.5%", 2));
        assertDecimal("10000000000000000000000000000000000000000",
                NeuCalculator.calculate("10000000000000000000000000000000000000000", 2));
    }

    @Test
    void supportsFunctionsAndAnsVariable() throws Exception {
        assertDecimal("3", NeuCalculator.calculate("abs(-3)", 0));
        assertDecimal("2", NeuCalculator.calculate("min(7, 2, 9)", 0));
        assertDecimal("9", NeuCalculator.calculate("max(7, 2, 9)", 0));
        assertDecimal("3", NeuCalculator.calculate("round(2.5)", 0));
        assertDecimal("1.23", NeuCalculator.calculate("round(1.234, 2)", 0));
        assertDecimal("-2", NeuCalculator.calculate("floor(-1.1)", 0));
        assertDecimal("-1", NeuCalculator.calculate("ceil(-1.1)", 0));
        assertDecimal("4", NeuCalculator.calculate("sqrt(16)", 0));
        assertDecimal("43", NeuCalculator.calculate("ans + $bonus", name -> switch (name) {
            case "ans" -> Optional.of(BigDecimal.valueOf(40));
            case "bonus" -> Optional.of(BigDecimal.valueOf(3));
            default -> Optional.empty();
        }, 0));
    }

    @Test
    void reportsTypedDiagnosticsWithSourceSpans() {
        NeuCalculator.EvaluationResult trailingOperator = NeuCalculator.evaluate("1+");
        assertTrue(trailingOperator.isIncomplete());
        assertEquals(NeuCalculator.EvaluationStatus.INCOMPLETE, trailingOperator.status());
        assertEquals(2, trailingOperator.offset());
        assertEquals(0, trailingOperator.length());
        assertNull(trailingOperator.result());

        NeuCalculator.EvaluationResult surplusOperand = NeuCalculator.evaluate("1 2");
        assertTrue(surplusOperand.isError());
        assertEquals(2, surplusOperand.offset());
        assertEquals(1, surplusOperand.length());
        assertTrue(surplusOperand.message().contains("Implicit multiplication"));

        assertIncomplete("(1 + 2");
        assertError("min(1,)");
        assertError("1,2");
        assertError("1)");
    }

    @Test
    void rejectsInvalidAndBoundedInputs() {
        NeuCalculator.EvaluationResult divisionByZero = NeuCalculator.evaluate("1 / 0");
        assertTrue(divisionByZero.isError());
        assertEquals(2, divisionByZero.offset());
        assertEquals(1, divisionByZero.length());
        assertTrue(divisionByZero.message().contains("Division by zero"));

        String hugeLiteral = "9".repeat(1_025);
        NeuCalculator.EvaluationResult tooLarge = NeuCalculator.evaluate(hugeLiteral);
        assertTrue(tooLarge.isError());
        assertTrue(tooLarge.message().contains("too many digits"));

        assertThrows(NeuCalculator.CalculatorException.class, () -> NeuCalculator.calculate("1 / 0", 0));

        NeuCalculator.EvaluationResult excessiveResult = NeuCalculator.evaluate("1e10000^1000");
        assertTrue(excessiveResult.isError());
        assertTrue(excessiveResult.message().contains("magnitude"));
    }

    @Test
    void compatibilityPrecisionDoesNotChangeArithmetic() throws Exception {
        BigDecimal lowDisplayPrecision = NeuCalculator.calculate("1 / 8", 0);
        BigDecimal highDisplayPrecision = NeuCalculator.calculate("1 / 8", 99);

        assertEquals(0, lowDisplayPrecision.compareTo(highDisplayPrecision));
        assertDecimal("0.125", lowDisplayPrecision);
        assertFalse(lowDisplayPrecision.scale() == 0, "the result must not be display-rounded by the compatibility precision");
    }

    private static void assertError(String source) {
        assertTrue(NeuCalculator.evaluate(source).isError(), source);
    }

    private static void assertIncomplete(String source) {
        assertTrue(NeuCalculator.evaluate(source).isIncomplete(), source);
    }

    private static void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> "Expected " + expected + " but was " + actual);
    }
}
