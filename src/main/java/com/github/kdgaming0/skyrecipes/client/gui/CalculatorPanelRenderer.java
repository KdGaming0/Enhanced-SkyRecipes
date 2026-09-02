package com.github.kdgaming0.skyrecipes.client.gui;

import cc.cassian.rrv.common.overlay.itemlist.view.SearchBar;
import com.github.kdgaming0.skyrecipes.client.config.SkyRecipesConfig;
import com.github.kdgaming0.skyrecipes.core.model.SkyblockItemCategory;
import com.github.kdgaming0.skyrecipes.core.util.NeuCalculator;
import com.github.kdgaming0.skyrecipes.mixin.accessor.EditBoxAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.math.BigDecimal;
import java.util.List;

/** Draws compact calculator feedback inside RRV's search widget. */
public final class CalculatorPanelRenderer {

    private static final int CATEGORY_BUTTON_SIZE = 16;
    private static final int CATEGORY_BUTTON_GAP = 2;
    private static final int RESULT_COLOR = 0xFF808080;
    private static final InlinePresentation INCOMPLETE_INLINE =
            new InlinePresentation(" …", 0xFFAAAAAA, null);
    private static final Component SUCCESS_CLOSE_HINT =
            Component.translatable("skyrecipes.calculator.hint.success.close");
    private static final Component SUCCESS_RETURN_HINT =
            Component.translatable("skyrecipes.calculator.hint.success.return");
    private static final List<PanelLine> HELP_LINES = List.of(
            new PanelLine(Component.translatable("skyrecipes.calculator.help.operators"), 0xFFFFFFFF),
            new PanelLine(Component.translatable("skyrecipes.calculator.help.suffixes"), 0xFFDDDDDD),
            new PanelLine(Component.translatable("skyrecipes.calculator.help.functions"), 0xFFDDDDDD),
            new PanelLine(Component.translatable("skyrecipes.calculator.help.scientific"), 0xFFAAAAAA));

    private CalculatorPanelRenderer() {
    }

    /** Native ghost text is reserved for calculator syntax completion. */
    public static void syncSuggestion(SearchBar searchbar, CalculatorSession session,
                                      SearchBarCalculator.Calculation calculation) {
        String value = searchbar.getValue();
        EditBoxAccessor accessor = (EditBoxAccessor) searchbar;
        if (!caretAtEndWithoutSelection(value.length(), searchbar.getCursorPosition(),
                accessor.skyrecipes$getHighlightPos()) || calculation.isHelp()) {
            clearSuggestion(searchbar);
            return;
        }

        String completion = calculation.completionSuffix();
        if (completion == null || completion.isEmpty()) {
            clearSuggestion(searchbar);
            return;
        }

        Font font = Minecraft.getInstance().font;
        InlineGeometry geometry = session.presentationCache().inlineGeometry(searchbar, font);
        if (geometry != null && font.width(completion) <= geometry.availableWidth()) {
            SearchSuggestionController.show(searchbar, calculation.input(), completion);
            return;
        }
        clearSuggestion(searchbar);
    }

    public static boolean caretAtEndWithoutSelection(int valueLength, int cursor, int highlight) {
        return selectionIsCollapsed(valueLength, cursor, highlight) && cursor == valueLength;
    }

    public static boolean selectionIsCollapsed(int valueLength, int cursor, int highlight) {
        return valueLength >= 0 && cursor >= 0 && cursor <= valueLength
                && highlight >= 0 && highlight <= valueLength && highlight == cursor;
    }

    public static void render(SearchBar searchbar, CalculatorSession session,
                              GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        if (!SkyRecipesConfig.calculatorEnabled || !session.isActive() || !searchbar.visible) {
            return;
        }
        SearchBarCalculator.Calculation calculation = session.calculation();
        if (calculation == null) {
            return;
        }

        syncSuggestion(searchbar, session, calculation);
        if (SkyRecipesConfig.calculatorDisplayMode == SkyRecipesConfig.CalculatorDisplayMode.PANEL) {
            renderExpandedPanel(searchbar, session, calculation, guiGraphics);
            return;
        }
        if (calculation.isHelp()) {
            renderHelp(searchbar, guiGraphics);
            return;
        }

        NeuCalculator.EvaluationResult evaluation = calculation.evaluation();
        String value = searchbar.getValue();
        EditBoxAccessor accessor = (EditBoxAccessor) searchbar;
        if (calculation.completionSuffix() != null || evaluation == null
                || !caretAtEndWithoutSelection(value.length(), searchbar.getCursorPosition(),
                accessor.skyrecipes$getHighlightPos())) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        InlineGeometry geometry = session.presentationCache().inlineGeometry(searchbar, font);
        if (geometry == null) {
            return;
        }

        InlinePresentation presentation;
        PresentationCache cache = session.presentationCache();
        if (evaluation.isSuccess()) {
            CalculatorResultFormatter.PreparedResult prepared = session.preparedResult(
                    evaluation.result(), SkyRecipesConfig.calculatorPrecision);
            presentation = cache.successInline(
                    prepared, SkyRecipesConfig.calculatorResultFormat,
                    session.isCopiedFeedbackVisible(), geometry.availableWidth(), font);
        } else if (evaluation.isIncomplete()) {
            presentation = INCOMPLETE_INLINE;
        } else {
            presentation = cache.errorInline(evaluation, geometry.availableWidth(), font);
        }

        int messageWidth = font.width(presentation.message());
        if (messageWidth > geometry.availableWidth()) {
            return;
        }
        guiGraphics.enableScissor(geometry.textStartX(), searchbar.getY() + 2,
                geometry.rightX(), searchbar.getY() + searchbar.getHeight() - 2);
        guiGraphics.text(font, presentation.message(), geometry.startX(), geometry.textY(), presentation.color());
        guiGraphics.disableScissor();

        int messageEnd = Math.min(geometry.rightX(), geometry.startX() + messageWidth);
        if (presentation.tooltip() != null && mouseX >= geometry.startX() && mouseX <= messageEnd
                && mouseY >= searchbar.getY() && mouseY <= searchbar.getY() + searchbar.getHeight()) {
            guiGraphics.setTooltipForNextFrame(presentation.tooltip(), mouseX, mouseY);
        }
    }

    private static void renderExpandedPanel(SearchBar searchbar, CalculatorSession session,
                                            SearchBarCalculator.Calculation calculation,
                                            GuiGraphicsExtractor guiGraphics) {
        if (calculation.isHelp()) {
            renderHelp(searchbar, guiGraphics);
            return;
        }
        NeuCalculator.EvaluationResult evaluation = calculation.evaluation();
        if (evaluation == null) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        int contentWidth = Math.max(20, searchbar.getWidth() - 8);
        List<PanelLine> lines;
        PresentationCache cache = session.presentationCache();
        if (evaluation.isSuccess()) {
            CalculatorResultFormatter.PreparedResult prepared = session.preparedResult(
                    evaluation.result(), SkyRecipesConfig.calculatorPrecision);
            lines = cache.successPanel(
                    prepared, SkyRecipesConfig.calculatorResultFormat,
                    session.isCopiedFeedbackVisible(), contentWidth, font);
        } else {
            lines = cache.diagnosticPanel(evaluation, calculation.completionSuffix() != null);
        }
        renderPanel(searchbar, guiGraphics, lines);
    }

    private static void renderHelp(SearchBar searchbar, GuiGraphicsExtractor guiGraphics) {
        renderPanel(searchbar, guiGraphics, HELP_LINES);
    }

    private static void renderPanel(SearchBar searchbar, GuiGraphicsExtractor guiGraphics,
                                    List<PanelLine> lines) {
        Font font = Minecraft.getInstance().font;
        int width = searchbar.getWidth();
        int height = lines.size() * 10 + 6;
        int x = searchbar.getX();
        int y = Math.max(2, searchbar.getY() - categoryRowsHeight(searchbar) - height - 2);
        guiGraphics.fill(x, y, x + width, y + height, 0xE0101010);
        guiGraphics.outline(x, y, width, height, 0xFF666666);
        for (int i = 0; i < lines.size(); i++) {
            PanelLine line = lines.get(i);
            drawScaledLine(guiGraphics, font, line.text(), x + 4, y + 4 + i * 10,
                    line.color(), width - 8);
        }
    }

    private static void clearSuggestion(SearchBar searchbar) {
        SearchSuggestionController.clear(searchbar);
    }

    private static int categoryRowsHeight(SearchBar searchbar) {
        if (SkyRecipesConfig.hideCategoryButtons) {
            return 0;
        }
        int count = SkyblockItemCategory.BUTTON_CATEGORIES.size();
        int buttonsPerRow = Math.max(1,
                (searchbar.getWidth() + CATEGORY_BUTTON_GAP) / (CATEGORY_BUTTON_SIZE + CATEGORY_BUTTON_GAP));
        int rows = (count + buttonsPerRow - 1) / buttonsPerRow;
        return rows * CATEGORY_BUTTON_SIZE + (rows - 1) * CATEGORY_BUTTON_GAP + 2;
    }

    private static void drawScaledLine(GuiGraphicsExtractor guiGraphics, Font font,
                                       Component line, int x, int y, int color, int maxWidth) {
        float scale = Math.min(1.0F, maxWidth / (float) Math.max(font.width(line), 1));
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(x, y);
        guiGraphics.pose().scale(scale, scale);
        guiGraphics.text(font, line, 0, 0, color);
        guiGraphics.pose().popMatrix();
    }

    static final class PresentationCache {
        private BigDecimal preparedValue;
        private int preparedPrecision = Integer.MIN_VALUE;
        private CalculatorResultFormatter.PreparedResult preparedResult;

        private String geometryValue;
        private int geometryDisplayPos;
        private int geometryCursor;
        private int geometryInnerWidth;
        private int geometryTextX;
        private int geometryTextY;
        private Font geometryFont;
        private boolean geometryCached;
        private InlineGeometry geometry;

        private CalculatorResultFormatter.PreparedResult inlinePrepared;
        private SkyRecipesConfig.CalculatorResultFormat inlineFormat;
        private boolean inlineCopied;
        private int inlineWidth = -1;
        private Font inlineFont;
        private InlinePresentation inlinePresentation;

        private NeuCalculator.EvaluationResult inlineError;
        private int inlineErrorWidth = -1;
        private Font inlineErrorFont;
        private InlinePresentation inlineErrorPresentation;

        private CalculatorResultFormatter.PreparedResult panelPrepared;
        private SkyRecipesConfig.CalculatorResultFormat panelFormat;
        private boolean panelCopied;
        private boolean panelEscapeCloses;
        private int panelWidth = -1;
        private Font panelFont;
        private List<PanelLine> panelLines;

        private NeuCalculator.EvaluationResult panelDiagnostic;
        private boolean panelHasCompletion;
        private List<PanelLine> diagnosticLines;

        CalculatorResultFormatter.PreparedResult preparedResult(BigDecimal value, int precision) {
            if (preparedResult == null || preparedValue != value || preparedPrecision != precision) {
                preparedValue = value;
                preparedPrecision = precision;
                preparedResult = CalculatorResultFormatter.prepare(value, precision);
            }
            return preparedResult;
        }

        private InlineGeometry inlineGeometry(SearchBar searchbar, Font font) {
            EditBoxAccessor accessor = (EditBoxAccessor) searchbar;
            String value = searchbar.getValue();
            int displayPos = Math.max(0, Math.min(accessor.skyrecipes$getDisplayPos(), value.length()));
            int cursor = searchbar.getCursorPosition();
            int innerWidth = searchbar.getInnerWidth();
            int textX = accessor.skyrecipes$getTextX();
            int textY = accessor.skyrecipes$getTextY();
            if (geometryCached && geometryValue == value && geometryDisplayPos == displayPos
                    && geometryCursor == cursor && geometryInnerWidth == innerWidth
                    && geometryTextX == textX && geometryTextY == textY && geometryFont == font) {
                return geometry;
            }

            String visible = font.plainSubstrByWidth(value.substring(displayPos), innerWidth);
            int relativeCursor = cursor - displayPos;
            InlineGeometry next = null;
            if (relativeCursor >= 0 && relativeCursor <= visible.length()) {
                int startX = textX + font.width(visible.substring(0, relativeCursor)) + 1;
                int rightX = textX + innerWidth;
                if (startX < rightX) {
                    next = new InlineGeometry(textX, startX, rightX, textY);
                }
            }

            geometryValue = value;
            geometryDisplayPos = displayPos;
            geometryCursor = cursor;
            geometryInnerWidth = innerWidth;
            geometryTextX = textX;
            geometryTextY = textY;
            geometryFont = font;
            geometryCached = true;
            geometry = next;
            return next;
        }

        private InlinePresentation successInline(CalculatorResultFormatter.PreparedResult prepared,
                                                 SkyRecipesConfig.CalculatorResultFormat format,
                                                 boolean copied, int availableWidth, Font font) {
            if (inlinePresentation != null && inlinePrepared == prepared && inlineFormat == format
                    && inlineCopied == copied && inlineWidth == availableWidth && inlineFont == font) {
                return inlinePresentation;
            }

            String message;
            int color;
            if (copied) {
                message = Component.translatable("skyrecipes.calculator.inline.copied").getString();
                color = 0xFF55FF55;
                if (font.width(message) > availableWidth) {
                    message = font.width(" ✓") <= availableWidth ? " ✓" : " =";
                }
            } else {
                String prefix = " = ";
                String formatted = prepared.format(format,
                        value -> font.width(prefix + value) <= availableWidth);
                message = prefix + formatted;
                color = RESULT_COLOR;
                if (font.width(message) > availableWidth) {
                    message = " =";
                }
            }

            inlinePrepared = prepared;
            inlineFormat = format;
            inlineCopied = copied;
            inlineWidth = availableWidth;
            inlineFont = font;
            inlinePresentation = new InlinePresentation(message, color, null);
            return inlinePresentation;
        }

        private InlinePresentation errorInline(NeuCalculator.EvaluationResult evaluation,
                                               int availableWidth, Font font) {
            if (inlineErrorPresentation != null && inlineError == evaluation
                    && inlineErrorWidth == availableWidth && inlineErrorFont == font) {
                return inlineErrorPresentation;
            }

            Component tooltip = Component.literal(evaluation.message());
            String message = " ! " + evaluation.message();
            if (font.width(message) > availableWidth) {
                message = " !";
            }
            inlineError = evaluation;
            inlineErrorWidth = availableWidth;
            inlineErrorFont = font;
            inlineErrorPresentation = new InlinePresentation(message, 0xFFFF5555, tooltip);
            return inlineErrorPresentation;
        }

        private List<PanelLine> successPanel(CalculatorResultFormatter.PreparedResult prepared,
                                             SkyRecipesConfig.CalculatorResultFormat format,
                                             boolean copied, int contentWidth, Font font) {
            boolean escapeCloses = SkyRecipesConfig.calculatorEscapeClosesMenu;
            if (panelLines != null && panelPrepared == prepared && panelFormat == format
                    && panelCopied == copied && panelEscapeCloses == escapeCloses
                    && panelWidth == contentWidth && panelFont == font) {
                return panelLines;
            }

            String formatted = prepared.format(format,
                    value -> font.width(Component.translatable("skyrecipes.calculator.result", value)) <= contentWidth);
            Component hint = copied
                    ? Component.translatable("skyrecipes.calculator.copied", formatted)
                    : escapeCloses ? SUCCESS_CLOSE_HINT : SUCCESS_RETURN_HINT;
            panelPrepared = prepared;
            panelFormat = format;
            panelCopied = copied;
            panelEscapeCloses = escapeCloses;
            panelWidth = contentWidth;
            panelFont = font;
            panelLines = List.of(
                    new PanelLine(Component.translatable("skyrecipes.calculator.result", formatted), 0xFF55FF55),
                    new PanelLine(hint, 0xFFAAAAAA));
            return panelLines;
        }

        private List<PanelLine> diagnosticPanel(NeuCalculator.EvaluationResult evaluation,
                                                boolean hasCompletion) {
            if (diagnosticLines != null && panelDiagnostic == evaluation
                    && panelHasCompletion == hasCompletion) {
                return diagnosticLines;
            }

            String message = evaluation.message();
            if (evaluation.offset() > 0) {
                message += " (at " + (evaluation.offset() + 1) + ")";
            }
            String key = evaluation.isIncomplete()
                    ? "skyrecipes.calculator.incomplete"
                    : "skyrecipes.calculator.error";
            int color = evaluation.isIncomplete() ? 0xFFFFFF55 : 0xFFFF5555;
            if (hasCompletion) {
                diagnosticLines = List.of(
                        new PanelLine(Component.translatable(key, message), color),
                        new PanelLine(Component.translatable("skyrecipes.calculator.hint.completion"), 0xFFAAAAAA));
            } else {
                diagnosticLines = List.of(new PanelLine(Component.translatable(key, message), color));
            }
            panelDiagnostic = evaluation;
            panelHasCompletion = hasCompletion;
            return diagnosticLines;
        }

        void clear() {
            preparedValue = null;
            preparedPrecision = Integer.MIN_VALUE;
            preparedResult = null;
            geometryValue = null;
            geometryFont = null;
            geometryCached = false;
            geometry = null;
            inlinePrepared = null;
            inlinePresentation = null;
            inlineError = null;
            inlineErrorPresentation = null;
            panelPrepared = null;
            panelLines = null;
            panelDiagnostic = null;
            diagnosticLines = null;
        }
    }

    private record InlineGeometry(int textStartX, int startX, int rightX, int textY) {
        int availableWidth() {
            return rightX - startX;
        }
    }

    private record InlinePresentation(String message, int color, Component tooltip) {
    }

    private record PanelLine(Component text, int color) {
    }
}
