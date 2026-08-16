package com.github.kdgaming0.skyrecipes.client.gui;

import com.github.kdgaming0.skyrecipes.client.config.SkyRecipesConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculatorSessionTest {

    @BeforeEach
    void enableSmartMode() {
        SkyRecipesConfig.calculatorEnabled = true;
        SkyRecipesConfig.calculatorInputMode = SkyRecipesConfig.CalculatorInputMode.SMART;
        SkyRecipesConfig.calculatorContextSuggestions = true;
        SkyRecipesConfig.calculatorHistorySize = 20;
    }

    @Test
    void smartPrefixRestoresTheQueryBeforeTheFirstNumber() {
        CalculatorSession session = new CalculatorSession();
        session.rememberSmartPrefix("aote");
        SearchBarCalculator.Calculation calculation = SearchBarCalculator.classifyAndEvaluate("2+2", false, null);

        session.update("2+2", "2", calculation);

        assertEquals("aote", session.savedSearchQuery());
        assertEquals("aote", session.exitAndRestoreQuery());
    }

    @Test
    void candidateEditsDoNotOverwriteTheOriginalSearchQuery() {
        CalculatorSession session = new CalculatorSession();
        session.rememberSmartPrefix("aote");
        session.rememberSmartPrefix("2");
        session.update("12+3", "12",
                SearchBarCalculator.classifyAndEvaluate("12+3", false, null));
        assertEquals("aote", session.savedSearchQuery());

        session.exitForNormalQuery();
        session.rememberSmartPrefix("hype");
        session.update("sqrt(9)", ".",
                SearchBarCalculator.classifyAndEvaluate("sqrt(9)", false, null));
        assertEquals("hype", session.savedSearchQuery());

        session.exitForNormalQuery();
        session.rememberSmartPrefix("juju");
        session.update("2 x", "2",
                SearchBarCalculator.classifyAndEvaluate("2 x", false, null));
        assertEquals("juju", session.savedSearchQuery());

        session.exitForNormalQuery();
        session.rememberSmartPrefix("term");
        session.rememberSmartPrefix("s");
        session.rememberSmartPrefix("sq");
        session.update("sqrt(9)", "sqrt",
                SearchBarCalculator.classifyAndEvaluate("sqrt(9)", false, null));
        assertEquals("term", session.savedSearchQuery());
    }

    @Test
    void copiedFeedbackDoesNotCarryIntoAnotherExpression() {
        CalculatorSession session = new CalculatorSession();
        update(session, "=2+2", "aote");
        session.markCopied();
        assertTrue(session.isCopiedFeedbackVisible());

        update(session, "=3+3", "aote");
        assertFalse(session.isCopiedFeedbackVisible());
    }

    @Test
    void detectsBehaviorConfigChangesDuringAnActiveSession() {
        CalculatorSession session = new CalculatorSession();
        update(session, "=2+2", "aote");
        assertTrue(!session.needsConfigReconciliation());

        SkyRecipesConfig.calculatorContextSuggestions = false;
        assertTrue(session.needsConfigReconciliation());
    }

    @Test
    void committingUpdatesAnsAndBoundedHistory() {
        CalculatorSession session = new CalculatorSession();
        update(session, "=1+1", "");
        assertTrue(session.commitSuccessfulResult(2));
        update(session, "=2+2", "");
        assertTrue(session.commitSuccessfulResult(2));
        update(session, "=3+3", "");
        assertTrue(session.commitSuccessfulResult(2));

        assertEquals(0, new BigDecimal("6").compareTo(session.ans()));
        assertEquals("=3+3", session.historyUp("=draft"));
        update(session, "=3+3", "");
        assertEquals("=2+2", session.historyUp("=3+3"));
        update(session, "=2+2", "");
        assertNull(session.historyUp("=2+2"));
        assertEquals("=3+3", session.historyDown());
        update(session, "=3+3", "");
        assertEquals("=draft", session.historyDown());
    }

    @Test
    void loweringTheConfiguredHistoryLimitTrimsExistingEntries() {
        CalculatorSession session = new CalculatorSession();
        update(session, "=1", "");
        session.commitSuccessfulResult(10);
        update(session, "=2", "");
        session.commitSuccessfulResult(10);
        update(session, "=3", "");
        session.commitSuccessfulResult(10);

        SkyRecipesConfig.calculatorHistorySize = 1;
        assertTrue(session.needsConfigReconciliation());
        update(session, "=3", "");

        assertEquals("=3", session.historyUp("=draft"));
        update(session, "=3", "");
        assertNull(session.historyUp("=3"));
    }

    @Test
    void committingAnAnsExpressionRefreshesItsNextResult() {
        CalculatorSession session = new CalculatorSession();
        update(session, "=1+1", "");
        assertTrue(session.commitSuccessfulResult(10));
        update(session, "=ans+1", "");
        assertEquals(0, new BigDecimal("3").compareTo(session.successfulResult()));

        assertTrue(session.commitSuccessfulResult(10));

        assertEquals(0, new BigDecimal("4").compareTo(session.successfulResult()));
        assertEquals(0, new BigDecimal("3").compareTo(session.ans()));
    }

    @Test
    void classificationIsReusedUntilEvaluationInputsChange() {
        CalculatorSession session = new CalculatorSession();
        SearchBarCalculator.Calculation initial = session.classifyAndEvaluate("=2+2");
        assertSame(initial, session.classifyAndEvaluate("=2+2"));

        session.update("=2+2", "aote", initial);
        SearchBarCalculator.Calculation active = session.classifyAndEvaluate("=2+2");
        assertNotSame(initial, active);
        assertSame(active, session.classifyAndEvaluate("=2+2"));

        SkyRecipesConfig.calculatorContextSuggestions = false;
        assertNotSame(active, session.classifyAndEvaluate("=2+2"));
    }

    @Test
    void preparedFormattingIsReusedUntilItsInputsChange() {
        CalculatorSession session = new CalculatorSession();
        BigDecimal value = new BigDecimal("1234567.89");

        CalculatorResultFormatter.PreparedResult first = session.preparedResult(value, 2);
        assertSame(first, session.preparedResult(value, 2));
        assertNotSame(first, session.preparedResult(value, 3));
        assertNotSame(session.preparedResult(value, 3),
                session.preparedResult(new BigDecimal("1234567.89"), 3));
    }

    private static void update(CalculatorSession session, String input, String query) {
        session.update(input, query,
                SearchBarCalculator.classifyAndEvaluate(input, session.isActive(), session.ans()));
    }
}
