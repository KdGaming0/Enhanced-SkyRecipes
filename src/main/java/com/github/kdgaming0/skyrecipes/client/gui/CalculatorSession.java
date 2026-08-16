package com.github.kdgaming0.skyrecipes.client.gui;

import com.github.kdgaming0.skyrecipes.client.config.SkyRecipesConfig;
import com.github.kdgaming0.skyrecipes.core.util.NeuCalculator;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Mutable calculator state owned by RRV's singleton item-view overlay. */
public final class CalculatorSession {

    private final List<HistoryEntry> history = new ArrayList<>();
    private boolean active;
    private boolean rebuilding;
    private String savedSearchQuery = "";
    private SearchBarCalculator.Calculation calculation;
    private String classifiedInput;
    private boolean classifiedActive;
    private BigDecimal classifiedAns;
    private SkyRecipesConfig.CalculatorInputMode classifiedInputMode;
    private boolean classifiedContextSuggestions;
    private SearchBarCalculator.Calculation classifiedCalculation;
    private BigDecimal ans;
    private String smartPrefixSavedQuery;
    private final CalculatorPanelRenderer.PresentationCache presentationCache =
            new CalculatorPanelRenderer.PresentationCache();
    private int historyCursor = -1;
    private String historyDraft = "";
    private boolean historySelectionPending;
    private long copiedFeedbackUntil;
    private SkyRecipesConfig.CalculatorInputMode evaluatedInputMode = SkyRecipesConfig.calculatorInputMode;
    private boolean evaluatedContextSuggestions = SkyRecipesConfig.calculatorContextSuggestions;
    private int evaluatedHistorySize = SkyRecipesConfig.calculatorHistorySize;

    public SearchBarCalculator.Calculation classifyAndEvaluate(String input) {
        SkyRecipesConfig.CalculatorInputMode inputMode = SkyRecipesConfig.calculatorInputMode;
        boolean contextSuggestions = SkyRecipesConfig.calculatorContextSuggestions;
        if (classifiedCalculation != null && Objects.equals(input, classifiedInput)
                && classifiedActive == active && classifiedAns == ans
                && classifiedInputMode == inputMode
                && classifiedContextSuggestions == contextSuggestions) {
            return classifiedCalculation;
        }

        classifiedInput = input;
        classifiedActive = active;
        classifiedAns = ans;
        classifiedInputMode = inputMode;
        classifiedContextSuggestions = contextSuggestions;
        classifiedCalculation = SearchBarCalculator.classifyAndEvaluate(input, active, ans);
        return classifiedCalculation;
    }

    public void update(String input, String currentQuery, SearchBarCalculator.Calculation calculation) {
        if (!active) {
            active = true;
            savedSearchQuery = consumeSmartPrefixBase(currentQuery);
        }
        if (!input().equals(input)) {
            copiedFeedbackUntil = 0L;
        }
        this.calculation = calculation;
        this.evaluatedInputMode = SkyRecipesConfig.calculatorInputMode;
        this.evaluatedContextSuggestions = SkyRecipesConfig.calculatorContextSuggestions;
        this.evaluatedHistorySize = SkyRecipesConfig.calculatorHistorySize;
        trimHistory(evaluatedHistorySize);
        if (historySelectionPending) {
            historySelectionPending = false;
        } else {
            historyCursor = -1;
            historyDraft = "";
        }
    }

    public void rememberSmartPrefix(String currentQuery) {
        if (smartPrefixSavedQuery == null) {
            smartPrefixSavedQuery = currentQuery;
        }
    }

    public void clearSmartPrefix() {
        smartPrefixSavedQuery = null;
    }

    private String consumeSmartPrefixBase(String currentQuery) {
        String base = smartPrefixSavedQuery != null ? smartPrefixSavedQuery : currentQuery;
        clearSmartPrefix();
        return base;
    }

    public String exitAndRestoreQuery() {
        String restore = savedSearchQuery;
        deactivate();
        return restore;
    }

    public void exitForNormalQuery() {
        deactivate();
    }

    private void deactivate() {
        active = false;
        rebuilding = false;
        savedSearchQuery = "";
        calculation = null;
        classifiedInput = null;
        classifiedAns = null;
        classifiedCalculation = null;
        historyCursor = -1;
        historyDraft = "";
        historySelectionPending = false;
        copiedFeedbackUntil = 0L;
        presentationCache.clear();
        clearSmartPrefix();
    }

    public @Nullable BigDecimal successfulResult() {
        if (calculation == null || calculation.evaluation() == null || !calculation.evaluation().isSuccess()) {
            return null;
        }
        return calculation.evaluation().result();
    }

    public boolean commitSuccessfulResult(int historyLimit) {
        BigDecimal result = successfulResult();
        if (result == null) {
            return false;
        }
        String committedInput = input();
        ans = result;
        HistoryEntry entry = new HistoryEntry(committedInput, result);
        if (history.isEmpty() || !history.getLast().equals(entry)) {
            history.add(entry);
        }
        trimHistory(historyLimit);
        calculation = classifyAndEvaluate(committedInput);
        presentationCache.clear();
        historyCursor = -1;
        historyDraft = "";
        return true;
    }

    private void trimHistory(int historyLimit) {
        int limit = Math.max(1, historyLimit);
        while (history.size() > limit) {
            history.removeFirst();
        }
    }

    public @Nullable String historyUp(String currentInput) {
        if (history.isEmpty()) {
            return null;
        }
        if (historyCursor < 0) {
            historyDraft = currentInput;
            historyCursor = history.size();
        }
        if (historyCursor == 0) {
            return null;
        }
        historyCursor--;
        historySelectionPending = true;
        return history.get(historyCursor).input();
    }

    public @Nullable String historyDown() {
        if (historyCursor < 0) {
            return null;
        }
        historyCursor++;
        historySelectionPending = true;
        if (historyCursor >= history.size()) {
            historyCursor = -1;
            String draft = historyDraft;
            historyDraft = "";
            return draft;
        }
        return history.get(historyCursor).input();
    }

    public void markCopied() {
        copiedFeedbackUntil = System.currentTimeMillis() + 1_500L;
    }

    public boolean isCopiedFeedbackVisible() {
        return System.currentTimeMillis() < copiedFeedbackUntil;
    }

    public boolean isActive() {
        return active;
    }

    public String input() {
        return calculation == null ? "" : calculation.input();
    }

    public String savedSearchQuery() {
        return savedSearchQuery;
    }

    public @Nullable SearchBarCalculator.Calculation calculation() {
        return calculation;
    }

    public @Nullable BigDecimal ans() {
        return ans;
    }

    CalculatorResultFormatter.PreparedResult preparedResult(BigDecimal value, int precision) {
        return presentationCache.preparedResult(value, precision);
    }

    CalculatorPanelRenderer.PresentationCache presentationCache() {
        return presentationCache;
    }

    public void invalidatePresentation() {
        presentationCache.clear();
    }

    public boolean needsConfigReconciliation() {
        return active && (!SkyRecipesConfig.calculatorEnabled
                || evaluatedInputMode != SkyRecipesConfig.calculatorInputMode
                || evaluatedContextSuggestions != SkyRecipesConfig.calculatorContextSuggestions
                || evaluatedHistorySize != SkyRecipesConfig.calculatorHistorySize);
    }

    public boolean isRebuilding() {
        return rebuilding;
    }

    public void setRebuilding(boolean rebuilding) {
        this.rebuilding = rebuilding;
    }

    public record HistoryEntry(String input, BigDecimal result) {
    }
}
