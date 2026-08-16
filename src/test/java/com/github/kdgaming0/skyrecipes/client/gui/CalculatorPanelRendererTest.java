package com.github.kdgaming0.skyrecipes.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculatorPanelRendererTest {

    @Test
    void acceptsOnlyAValidCollapsedCaretAtTheEnd() {
        assertTrue(CalculatorPanelRenderer.caretAtEndWithoutSelection(4, 4, 4));
        assertFalse(CalculatorPanelRenderer.caretAtEndWithoutSelection(4, 3, 3));
        assertFalse(CalculatorPanelRenderer.caretAtEndWithoutSelection(4, 4, 3));
    }

    @Test
    void rejectsTheTransientDeletionStateWithoutSlicingTheValue() {
        assertFalse(CalculatorPanelRenderer.caretAtEndWithoutSelection(4, 4, 5));
        assertFalse(CalculatorPanelRenderer.selectionIsCollapsed(4, 4, 5));
        assertFalse(CalculatorPanelRenderer.selectionIsCollapsed(4, -1, -1));
        assertFalse(CalculatorPanelRenderer.selectionIsCollapsed(4, 5, 5));
    }
}
