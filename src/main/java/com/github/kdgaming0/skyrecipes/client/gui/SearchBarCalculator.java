package com.github.kdgaming0.skyrecipes.client.gui;

import com.github.kdgaming0.skyrecipes.client.config.SkyRecipesConfig;
import com.github.kdgaming0.skyrecipes.core.util.NeuCalculator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Classifies and evaluates calculator input from the RRV search bar. */
public final class SearchBarCalculator {

    private static final List<String> COMPLETIONS = createCompletions();
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_]+");
    private static final Pattern TRAILING_IDENTIFIER = Pattern.compile("[A-Za-z_]+$");
    private static final Pattern SCIENTIFIC = Pattern.compile("(?i)(?:^|[^A-Za-z_])(?:\\d+(?:\\.\\d*)?|\\.\\d+)e[+-]?\\d+");
    private static final Pattern SUFFIX = Pattern.compile("(?i)(?:\\d|\\))\\s*[kmbtse](?=$|\\s|[+\\-*/^%),])");

    private SearchBarCalculator() {
    }

    private static List<String> createCompletions() {
        List<String> completions = new ArrayList<>(NeuCalculator.SUPPORTED_FUNCTIONS.size() + 1);
        for (String function : NeuCalculator.SUPPORTED_FUNCTIONS) {
            completions.add(function + "(");
        }
        completions.add("ans");
        completions.sort(String::compareTo);
        return List.copyOf(completions);
    }

    public static Calculation classifyAndEvaluate(String input, boolean activeSession, BigDecimal ans) {
        if (input == null) {
            return Calculation.normal();
        }

        String stripped = input.stripLeading();
        boolean explicit = stripped.startsWith("=");
        String expression = explicit ? stripped.substring(1).stripLeading() : input.strip();
        if (explicit && (expression.equals("?") || expression.equalsIgnoreCase("help"))) {
            return Calculation.help(input);
        }

        boolean calculator = explicit || SkyRecipesConfig.calculatorInputMode == SkyRecipesConfig.CalculatorInputMode.SMART
                && isSmartExpression(expression, activeSession);
        if (!calculator) {
            return Calculation.normal();
        }

        NeuCalculator.EvaluationResult evaluation = NeuCalculator.evaluate(expression, name ->
                name.equalsIgnoreCase("ans") && ans != null ? Optional.of(ans) : Optional.empty());
        String completion = SkyRecipesConfig.calculatorContextSuggestions
                ? completionSuffix(expression)
                : null;
        return new Calculation(Kind.CALCULATOR, input, evaluation, completion);
    }

    /** Input that may become a Smart expression after another keystroke. */
    public static boolean isSmartPrefix(String input) {
        if (input == null) {
            return false;
        }
        String value = input.strip();
        if (value.isEmpty()) {
            return false;
        }

        String lower = value.toLowerCase(Locale.ROOT);
        if ("ans".startsWith(lower)) {
            return true;
        }
        for (String completion : COMPLETIONS) {
            String function = completion.endsWith("(")
                    ? completion.substring(0, completion.length() - 1)
                    : completion;
            if (function.startsWith(lower)) {
                return true;
            }
        }

        int start = value.charAt(0) == '+' || value.charAt(0) == '-' ? 1 : 0;
        if (start == value.length()) {
            return true;
        }
        boolean decimal = false;
        boolean digit = false;
        for (int i = start; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isDigit(c)) {
                digit = true;
            } else if (c == '.' && !decimal) {
                decimal = true;
            } else {
                return false;
            }
        }
        return digit || decimal;
    }

    private static boolean isSmartExpression(String expression, boolean activeSession) {
        if (expression.isBlank()) {
            return false;
        }
        String lower = expression.toLowerCase(Locale.ROOT);
        char first = lower.charAt(0);
        boolean startsLikeMath = Character.isDigit(first) || first == '.' || first == '('
                || first == '+' || first == '-'
                || lower.startsWith("ans") || startsWithFunction(lower);
        if (!startsLikeMath || hasSearchOnlySyntax(expression) || !identifiersAreCalculatorWords(lower, activeSession)) {
            return false;
        }

        if (activeSession && isPotentialExpression(lower)) {
            return true;
        }
        if (lower.startsWith("ans") || startsWithFunction(lower)) {
            return true;
        }
        if (first == '+' || first == '-' || first == '(') {
            return true;
        }
        if (SCIENTIFIC.matcher(lower).find() || SUFFIX.matcher(lower).find()) {
            return true;
        }
        return hasCommitmentOperator(lower);
    }

    private static boolean hasSearchOnlySyntax(String expression) {
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (c == ':' || c == ';' || c == '<' || c == '>' || c == '"' || c == '[' || c == ']') {
                return true;
            }
        }
        return expression.startsWith("/") || expression.startsWith("%");
    }

    private static boolean identifiersAreCalculatorWords(String expression, boolean activeSession) {
        Matcher matcher = IDENTIFIER.matcher(expression);
        while (matcher.find()) {
            String word = matcher.group().toLowerCase(Locale.ROOT);
            if (word.equals("ans") || word.equals("x") || word.length() == 1 && "kmbtse".contains(word)
                    || isFunction(word) || isFunctionPrefix(word) && (activeSession || matcher.start() > 0)) {
                continue;
            }
            return false;
        }
        return true;
    }

    private static boolean isPotentialExpression(String expression) {
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (Character.isLetterOrDigit(c) || Character.isWhitespace(c)
                    || ".,+-*/^%()_$ {}".indexOf(c) >= 0) {
                continue;
            }
            return false;
        }
        return true;
    }

    private static boolean hasCommitmentOperator(String expression) {
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (c == '*' || c == '/' || c == '^' || c == '%' || c == '(' || c == ')') {
                return true;
            }
            if ((c == '+' || c == '-') && i > 0 && expression.charAt(i - 1) != 'e') {
                return true;
            }
            if (c == 'x' || c == 'X') {
                int left = i - 1;
                while (left >= 0 && Character.isWhitespace(expression.charAt(left))) {
                    left--;
                }
                int right = i + 1;
                while (right < expression.length() && Character.isWhitespace(expression.charAt(right))) {
                    right++;
                }
                boolean validLeft = left >= 0
                        && (Character.isDigit(expression.charAt(left)) || expression.charAt(left) == ')');
                boolean validRight = right >= expression.length()
                        || Character.isDigit(expression.charAt(right)) || expression.charAt(right) == '('
                        || expression.charAt(right) == '.' || expression.charAt(right) == '+'
                        || expression.charAt(right) == '-';
                if (validLeft && validRight) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean startsWithFunction(String expression) {
        int parenthesis = expression.indexOf('(');
        if (parenthesis <= 0) {
            return false;
        }
        return isFunction(expression.substring(0, parenthesis).strip());
    }

    private static boolean isFunction(String word) {
        return NeuCalculator.isSupportedFunction(word);
    }

    private static boolean isFunctionPrefix(String word) {
        for (String candidate : COMPLETIONS) {
            if (candidate.startsWith(word)) {
                return true;
            }
        }
        return false;
    }

    private static String completionSuffix(String expression) {
        Matcher matcher = TRAILING_IDENTIFIER.matcher(expression);
        if (!matcher.find() || !canStartOperandAt(expression, matcher.start())) {
            return null;
        }
        String typed = matcher.group().toLowerCase(Locale.ROOT);
        String match = null;
        for (String candidate : COMPLETIONS) {
            if (candidate.startsWith(typed) && !candidate.equals(typed)) {
                match = candidate;
                break;
            }
        }
        if (match == null) {
            return null;
        }
        return match.substring(typed.length());
    }

    /**
     * Function suggestions only make sense where the parser is waiting for a
     * value. This also keeps numeric postfixes such as the {@code s} in
     * {@code 10s} from being mistaken for the start of {@code sqrt(}.
     */
    private static boolean canStartOperandAt(String expression, int identifierStart) {
        int previous = previousNonWhitespace(expression, identifierStart - 1);
        if (previous < 0) {
            return true;
        }

        char character = expression.charAt(previous);
        if ("(,+-*/^".indexOf(character) >= 0) {
            return true;
        }
        if (character != 'x' && character != 'X') {
            return false;
        }

        // x is multiplication only when it follows a completed value. This
        // rejects ordinary words such as "max sq" while allowing "2 x sq".
        int beforeX = previousNonWhitespace(expression, previous - 1);
        if (beforeX < 0) {
            return false;
        }
        char left = Character.toLowerCase(expression.charAt(beforeX));
        return Character.isDigit(left) || left == ')' || left == '%'
                || "kmbtse".indexOf(left) >= 0;
    }

    private static int previousNonWhitespace(String value, int index) {
        while (index >= 0 && Character.isWhitespace(value.charAt(index))) {
            index--;
        }
        return index;
    }

    public enum Kind {
        NORMAL,
        CALCULATOR,
        HELP
    }

    public record Calculation(
            Kind kind,
            String input,
            NeuCalculator.EvaluationResult evaluation,
            String completionSuffix
    ) {
        private static final Calculation NORMAL =
                new Calculation(Kind.NORMAL, "", null, null);

        private static Calculation normal() {
            return NORMAL;
        }

        private static Calculation help(String input) {
            return new Calculation(Kind.HELP, input, null, null);
        }

        public boolean isCalculator() {
            return kind != Kind.NORMAL;
        }

        public boolean isHelp() {
            return kind == Kind.HELP;
        }
    }
}
