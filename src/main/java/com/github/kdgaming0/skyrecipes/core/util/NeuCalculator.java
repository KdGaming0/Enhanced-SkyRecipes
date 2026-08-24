/*
 * Copyright (C) 2022 NotEnoughUpdates contributors
 *
 * This file is part of NotEnoughUpdates.
 *
 * NotEnoughUpdates is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Adapted for SkyRecipes from NotEnoughUpdates.
 */

package com.github.kdgaming0.skyrecipes.core.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Parser and evaluator for the calculator syntax used by SkyRecipes.
 *
 * <p>The compatibility {@code calculate} methods deliberately accept a display
 * precision, but arithmetic always uses {@link #MATH_CONTEXT}. Formatting is a
 * UI concern and must not change a calculated value.</p>
 */
public final class NeuCalculator {

    /** A stable arithmetic precision, independent of any UI display setting. */
    public static final MathContext MATH_CONTEXT = new MathContext(50, RoundingMode.HALF_EVEN);
    public static final List<String> SUPPORTED_FUNCTIONS =
            List.of("abs", "ceil", "floor", "max", "min", "round", "sqrt");

    private static final int MAX_SOURCE_LENGTH = 4_096;
    private static final int MAX_LITERAL_DIGITS = 1_024;
    private static final int MAX_NESTING = 128;
    private static final int MAX_SCIENTIFIC_EXPONENT = 10_000;
    private static final int MAX_POWER = 1_000;
    private static final int MAX_RESULT_EXPONENT = 100_000;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1_000);
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);
    private static final BigDecimal BILLION = BigDecimal.valueOf(1_000_000_000L);
    private static final BigDecimal TRILLION = BigDecimal.valueOf(1_000_000_000_000L);
    private static final BigDecimal STACK_SIZE = BigDecimal.valueOf(64);
    private static final BigDecimal ENCHANTED_STACK_SIZE = BigDecimal.valueOf(160);

    private NeuCalculator() {
    }

    public static boolean isSupportedFunction(String identifier) {
        return identifier != null && SUPPORTED_FUNCTIONS.contains(identifier.toLowerCase(Locale.ROOT));
    }

    /**
     * Evaluates an expression without variables.
     *
     * @return a result whose status and source span can be used directly by UI code
     */
    public static EvaluationResult evaluate(String source) {
        return evaluate(source, ignored -> Optional.empty());
    }

    /**
     * Evaluates an expression and exposes success, incomplete input, and errors
     * without requiring UI code to use exceptions for normal typing states.
     */
    public static EvaluationResult evaluate(String source, VariableProvider variables) {
        try {
            if (source == null) {
                throw error("Expression is null", 0, 0);
            }
            if (source.length() > MAX_SOURCE_LENGTH) {
                throw error("Expression is too long", MAX_SOURCE_LENGTH, source.length() - MAX_SOURCE_LENGTH);
            }

            List<Token> tokens = new Tokenizer(source).tokenize();
            BigDecimal result = new Parser(tokens, source.length(), variables == null ? ignored -> Optional.empty() : variables).parse();
            validateResultMagnitude(result, source.length());
            return EvaluationResult.success(result);
        } catch (Diagnostic diagnostic) {
            return diagnostic.incomplete
                    ? EvaluationResult.incomplete(diagnostic.getMessage(), diagnostic.offset, diagnostic.length)
                    : EvaluationResult.error(diagnostic.getMessage(), diagnostic.offset, diagnostic.length);
        } catch (CalculatorException exception) {
            return EvaluationResult.error(exception.getMessage(), exception.getOffset(), exception.getLength());
        }
    }

    /**
     * Compatibility facade. The {@code precision} parameter is retained for
     * binary/source compatibility but does not affect arithmetic.
     */
    public static BigDecimal calculate(String source, int precision) throws CalculatorException {
        return calculate(source, ignored -> Optional.empty(), precision);
    }

    /**
     * Compatibility facade. The {@code precision} parameter is intentionally
     * ignored; callers should apply display rounding only when formatting.
     */
    public static BigDecimal calculate(String source, VariableProvider variables, int precision) throws CalculatorException {
        EvaluationResult evaluation = evaluate(source, variables);
        if (evaluation.isSuccess()) {
            return evaluation.result();
        }
        throw new CalculatorException(evaluation.message(), evaluation.offset(), evaluation.length());
    }

    private static void validateResultMagnitude(BigDecimal result, int sourceLength) throws Diagnostic {
        if (result.signum() == 0) {
            return;
        }
        long exponent = (long) result.precision() - result.scale() - 1L;
        if (Math.abs(exponent) > MAX_RESULT_EXPONENT || Math.abs((long) result.scale()) > MAX_RESULT_EXPONENT) {
            throw error("Result magnitude is too large", Math.max(0, sourceLength - 1), sourceLength == 0 ? 0 : 1);
        }
    }

    private static Diagnostic error(String message, int offset, int length) {
        return new Diagnostic(message, offset, length, false);
    }

    private static Diagnostic incomplete(String message, int offset) {
        return new Diagnostic(message, offset, 0, true);
    }

    /** The structured state returned by {@link #evaluate(String)}. */
    public enum EvaluationStatus {
        SUCCESS,
        INCOMPLETE,
        ERROR
    }

    /**
     * Structured calculator outcome. {@code result} is non-null only for
     * {@link EvaluationStatus#SUCCESS}; {@code message}, {@code offset}, and
     * {@code length} identify an input diagnostic otherwise.
     */
    public record EvaluationResult(
            EvaluationStatus status,
            BigDecimal result,
            String message,
            int offset,
            int length
    ) {
        private static EvaluationResult success(BigDecimal result) {
            return new EvaluationResult(EvaluationStatus.SUCCESS, result, null, 0, 0);
        }

        private static EvaluationResult incomplete(String message, int offset, int length) {
            return new EvaluationResult(EvaluationStatus.INCOMPLETE, null, message, offset, length);
        }

        private static EvaluationResult error(String message, int offset, int length) {
            return new EvaluationResult(EvaluationStatus.ERROR, null, message, offset, length);
        }

        public boolean isSuccess() {
            return status == EvaluationStatus.SUCCESS;
        }

        public boolean isIncomplete() {
            return status == EvaluationStatus.INCOMPLETE;
        }

        public boolean isError() {
            return status == EvaluationStatus.ERROR;
        }
    }

    /** Resolves legacy {@code $name} variables and the bare {@code ans} variable. */
    public interface VariableProvider {
        Optional<BigDecimal> provideVariable(String name) throws CalculatorException;
    }

    public static class CalculatorException extends Exception {
        private final int offset;
        private final int length;

        public CalculatorException(String message, int offset, int length) {
            super(message);
            this.offset = offset;
            this.length = length;
        }

        public int getOffset() {
            return offset;
        }

        public int getLength() {
            return length;
        }
    }

    private enum TokenType {
        NUMBER,
        IDENTIFIER,
        VARIABLE,
        OPERATOR,
        POSTFIX,
        LEFT_PAREN,
        RIGHT_PAREN,
        COMMA,
        END
    }

    private record Token(TokenType type, String text, int offset, int length) {
        private int end() {
            return offset + length;
        }
    }

    private static final class Tokenizer {
        private final String source;
        private final List<Token> tokens = new ArrayList<>();
        private int position;

        private Tokenizer(String source) {
            this.source = source;
        }

        private List<Token> tokenize() throws Diagnostic {
            while (position < source.length()) {
                char character = source.charAt(position);
                if (Character.isWhitespace(character)) {
                    position++;
                } else if (Character.isDigit(character) || character == '.') {
                    readNumber();
                } else if (character == '$') {
                    readVariable();
                } else if (isPostfixLetter(character) && previousTokenCanEndValue()) {
                    int start = position++;
                    tokens.add(new Token(TokenType.POSTFIX,
                            String.valueOf(Character.toLowerCase(character)), start, 1));
                } else if ((character == 'x' || character == 'X') && previousTokenCanEndValue()) {
                    int start = position++;
                    tokens.add(new Token(TokenType.OPERATOR, "x", start, 1));
                } else if (isIdentifierStart(character)) {
                    readIdentifierOrPostfix();
                } else {
                    int start = position++;
                    switch (character) {
                        case '+', '-', '/', '^' -> tokens.add(new Token(TokenType.OPERATOR, String.valueOf(character), start, 1));
                        case '*' -> {
                            if (position < source.length() && source.charAt(position) == '*') {
                                position++;
                                tokens.add(new Token(TokenType.OPERATOR, "^", start, 2));
                            } else {
                                tokens.add(new Token(TokenType.OPERATOR, "*", start, 1));
                            }
                        }
                        case '(' -> tokens.add(new Token(TokenType.LEFT_PAREN, "(", start, 1));
                        case ')' -> tokens.add(new Token(TokenType.RIGHT_PAREN, ")", start, 1));
                        case ',' -> tokens.add(new Token(TokenType.COMMA, ",", start, 1));
                        case '%' -> tokens.add(new Token(TokenType.POSTFIX, "%", start, 1));
                        default -> throw error("Unexpected character '" + character + "'", start, 1);
                    }
                }
            }
            tokens.add(new Token(TokenType.END, "", source.length(), 0));
            return tokens;
        }

        private void readNumber() throws Diagnostic {
            int start = position;
            int digits = 0;
            boolean sawDecimalPoint = false;

            if (source.charAt(position) == '.') {
                sawDecimalPoint = true;
                position++;
                if (position == source.length() || !Character.isDigit(source.charAt(position))) {
                    throw error("Invalid number literal", start, 1);
                }
            }

            while (position < source.length() && Character.isDigit(source.charAt(position))) {
                position++;
                digits++;
            }
            if (position < source.length() && source.charAt(position) == '.') {
                if (sawDecimalPoint) {
                    throw error("Invalid number literal", position, 1);
                }
                sawDecimalPoint = true;
                position++;
                while (position < source.length() && Character.isDigit(source.charAt(position))) {
                    position++;
                    digits++;
                }
            }
            if (digits > MAX_LITERAL_DIGITS) {
                throw error("Number literal has too many digits", start, position - start);
            }

            int mantissaEnd = position;
            if (position < source.length() && (source.charAt(position) == 'e' || source.charAt(position) == 'E')) {
                int exponentStart = position;
                int probe = position + 1;
                if (probe < source.length() && (source.charAt(probe) == '+' || source.charAt(probe) == '-')) {
                    probe++;
                }
                int exponentDigitsStart = probe;
                while (probe < source.length() && Character.isDigit(source.charAt(probe))) {
                    probe++;
                }
                if (probe > exponentDigitsStart) {
                    validateScientificExponent(exponentStart, exponentDigitsStart, probe);
                    position = probe;
                }
            }

            String literal = source.substring(start, position);
            tokens.add(new Token(TokenType.NUMBER, literal, start, position - start));

            // A trailing e/E deliberately remains a postfix operator (times 160).
            if (position == mantissaEnd && mantissaEnd == start) {
                throw error("Invalid number literal", start, 1);
            }
        }

        private void validateScientificExponent(int exponentMarker, int digitsStart, int end) throws Diagnostic {
            int exponent = 0;
            for (int index = digitsStart; index < end; index++) {
                int digit = source.charAt(index) - '0';
                if (exponent > (MAX_SCIENTIFIC_EXPONENT - digit) / 10) {
                    throw error("Scientific exponent is too large", exponentMarker, end - exponentMarker);
                }
                exponent = exponent * 10 + digit;
            }
        }

        private void readVariable() throws Diagnostic {
            int start = position++;
            String name;
            if (position < source.length() && source.charAt(position) == '{') {
                position++;
                int nameStart = position;
                while (position < source.length() && source.charAt(position) != '}') {
                    if (!isIdentifierPart(source.charAt(position))) {
                        throw error("Invalid variable name", position, 1);
                    }
                    position++;
                }
                if (position == source.length()) {
                    throw error("Unterminated variable literal", start, source.length() - start);
                }
                if (position == nameStart) {
                    throw error("Variable name is empty", start, position - start + 1);
                }
                name = source.substring(nameStart, position++);
            } else {
                int nameStart = position;
                if (position >= source.length() || !isIdentifierStart(source.charAt(position))) {
                    throw error("Invalid variable literal", start, 1);
                }
                position++;
                while (position < source.length() && isIdentifierPart(source.charAt(position))) {
                    position++;
                }
                name = source.substring(nameStart, position);
            }
            tokens.add(new Token(TokenType.VARIABLE, name, start, position - start));
        }

        private void readIdentifierOrPostfix() {
            int start = position++;
            while (position < source.length() && isIdentifierPart(source.charAt(position))) {
                position++;
            }
            String text = source.substring(start, position);
            if (text.length() == 1 && "kmbtse".indexOf(Character.toLowerCase(text.charAt(0))) >= 0) {
                tokens.add(new Token(TokenType.POSTFIX, text.toLowerCase(Locale.ROOT), start, 1));
            } else if (text.equalsIgnoreCase("x")) {
                tokens.add(new Token(TokenType.OPERATOR, "x", start, 1));
            } else {
                tokens.add(new Token(TokenType.IDENTIFIER, text, start, position - start));
            }
        }

        private boolean previousTokenCanEndValue() {
            if (tokens.isEmpty()) {
                return false;
            }
            return switch (tokens.getLast().type) {
                case NUMBER, IDENTIFIER, VARIABLE, POSTFIX, RIGHT_PAREN -> true;
                default -> false;
            };
        }

        private static boolean isPostfixLetter(char character) {
            return "kmbtse".indexOf(Character.toLowerCase(character)) >= 0;
        }

        private static boolean isIdentifierStart(char character) {
            return Character.isLetter(character) || character == '_';
        }

        private static boolean isIdentifierPart(char character) {
            return Character.isLetterOrDigit(character) || character == '_';
        }
    }

    private static final class Parser {
        private final List<Token> tokens;
        private final int sourceLength;
        private final VariableProvider variables;
        private int index;
        private int nesting;

        private Parser(List<Token> tokens, int sourceLength, VariableProvider variables) {
            this.tokens = tokens;
            this.sourceLength = sourceLength;
            this.variables = variables;
        }

        private BigDecimal parse() throws Diagnostic, CalculatorException {
            if (peek().type == TokenType.END) {
                throw incomplete("Expression is incomplete", sourceLength);
            }
            BigDecimal result = parseAddition();
            if (peek().type != TokenType.END) {
                Token token = peek();
                if (token.type == TokenType.LEFT_PAREN || token.type == TokenType.NUMBER || token.type == TokenType.IDENTIFIER
                        || token.type == TokenType.VARIABLE) {
                    throw error("Implicit multiplication is not supported", token.offset, token.length);
                }
                throw error("Unexpected token '" + token.text + "'", token.offset, token.length);
            }
            return result;
        }

        private BigDecimal parseAddition() throws Diagnostic, CalculatorException {
            BigDecimal value = parseMultiplication();
            while (isOperator("+") || isOperator("-")) {
                Token operator = consume();
                BigDecimal right = parseRequiredMultiplication();
                value = operator.text.equals("+")
                        ? value.add(right, MATH_CONTEXT)
                        : value.subtract(right, MATH_CONTEXT);
            }
            return value;
        }

        private BigDecimal parseMultiplication() throws Diagnostic, CalculatorException {
            BigDecimal value = parseUnary();
            while (isOperator("*") || isOperator("/") || isOperator("x")) {
                Token operator = consume();
                BigDecimal right = parseRequiredValue();
                if (operator.text.equals("/")) {
                    if (right.signum() == 0) {
                        throw error("Division by zero", operator.offset, operator.length);
                    }
                    value = value.divide(right, MATH_CONTEXT);
                } else {
                    value = value.multiply(right, MATH_CONTEXT);
                }
            }
            return value;
        }

        // Unary signs intentionally call back into unary so -2^2 is -(2^2), while 2^-2 is valid.
        private BigDecimal parseUnary() throws Diagnostic, CalculatorException {
            if (isOperator("+")) {
                consume();
                return parseRequiredValue();
            }
            if (isOperator("-")) {
                consume();
                return parseRequiredValue().negate(MATH_CONTEXT);
            }
            return parsePower();
        }

        private BigDecimal parsePower() throws Diagnostic, CalculatorException {
            BigDecimal value = parsePostfix();
            if (isOperator("^")) {
                Token operator = consume();
                BigDecimal exponent = parseRequiredValue();
                value = power(value, exponent, operator);
            }
            return value;
        }

        private BigDecimal parsePostfix() throws Diagnostic, CalculatorException {
            BigDecimal value = parsePrimary();
            while (peek().type == TokenType.POSTFIX) {
                Token postfix = consume();
                value = switch (postfix.text) {
                    case "k" -> value.multiply(THOUSAND, MATH_CONTEXT);
                    case "m" -> value.multiply(MILLION, MATH_CONTEXT);
                    case "b" -> value.multiply(BILLION, MATH_CONTEXT);
                    case "t" -> value.multiply(TRILLION, MATH_CONTEXT);
                    case "s" -> value.multiply(STACK_SIZE, MATH_CONTEXT);
                    case "e" -> value.multiply(ENCHANTED_STACK_SIZE, MATH_CONTEXT);
                    case "%" -> value.divide(ONE_HUNDRED, MATH_CONTEXT);
                    default -> throw error("Unknown postfix operator '" + postfix.text + "'", postfix.offset, postfix.length);
                };
            }
            return value;
        }

        private BigDecimal parsePrimary() throws Diagnostic, CalculatorException {
            Token token = peek();
            return switch (token.type) {
                case NUMBER -> {
                    consume();
                    try {
                        yield new BigDecimal(token.text);
                    } catch (NumberFormatException exception) {
                        throw error("Invalid number literal", token.offset, token.length);
                    }
                }
                case VARIABLE -> variable(consume());
                case IDENTIFIER -> identifier(consume());
                case LEFT_PAREN -> parenthesized();
                case END -> throw incomplete("Expected a value", sourceLength);
                default -> throw error("Expected a value", token.offset, Math.max(1, token.length));
            };
        }

        private BigDecimal identifier(Token identifier) throws Diagnostic, CalculatorException {
            if (identifier.text.equalsIgnoreCase("ans")) {
                return variable(identifier);
            }
            String function = identifier.text.toLowerCase(Locale.ROOT);
            if (isSupportedFunction(function)) {
                return function(function, identifier);
            }
            throw error("Unknown identifier '" + identifier.text + "'", identifier.offset, identifier.length);
        }

        private BigDecimal variable(Token variable) throws Diagnostic, CalculatorException {
            Optional<BigDecimal> value = variables.provideVariable(variable.text);
            if (value == null || value.isEmpty()) {
                throw error("Unknown variable '" + variable.text + "'", variable.offset, variable.length);
            }
            if (value.get() == null) {
                throw error("Variable '" + variable.text + "' has no value", variable.offset, variable.length);
            }
            return value.get();
        }

        private BigDecimal parenthesized() throws Diagnostic, CalculatorException {
            Token opening = consume();
            enterNesting(opening);
            try {
                if (peek().type == TokenType.RIGHT_PAREN) {
                    throw error("Parentheses cannot be empty", peek().offset, peek().length);
                }
                BigDecimal value = parseAddition();
                if (peek().type == TokenType.END) {
                    throw incomplete("Missing closing parenthesis", sourceLength);
                }
                if (peek().type != TokenType.RIGHT_PAREN) {
                    throw error("Expected closing parenthesis", peek().offset, peek().length);
                }
                consume();
                return value;
            } finally {
                nesting--;
            }
        }

        private BigDecimal function(String name, Token function) throws Diagnostic, CalculatorException {
            if (peek().type != TokenType.LEFT_PAREN) {
                throw error("Function '" + name + "' requires parentheses", function.offset, function.length);
            }
            consume();
            enterNesting(function);
            try {
                if (peek().type == TokenType.END) {
                    throw incomplete("Expected function argument", sourceLength);
                }
                if (peek().type == TokenType.RIGHT_PAREN) {
                    throw error("Function '" + name + "' requires an argument", peek().offset, peek().length);
                }
                if (peek().type == TokenType.COMMA) {
                    throw error("Missing function argument", peek().offset, peek().length);
                }

                List<BigDecimal> arguments = new ArrayList<>();
                arguments.add(parseAddition());
                while (peek().type == TokenType.COMMA) {
                    Token comma = consume();
                    if (peek().type == TokenType.END) {
                        throw incomplete("Expected function argument", sourceLength);
                    }
                    if (peek().type == TokenType.RIGHT_PAREN || peek().type == TokenType.COMMA) {
                        throw error("Missing function argument", comma.offset, comma.length);
                    }
                    arguments.add(parseAddition());
                }
                if (peek().type == TokenType.END) {
                    throw incomplete("Missing closing parenthesis", sourceLength);
                }
                if (peek().type != TokenType.RIGHT_PAREN) {
                    throw error("Expected ',' or closing parenthesis", peek().offset, peek().length);
                }
                consume();
                return applyFunction(name, arguments, function);
            } finally {
                nesting--;
            }
        }

        private BigDecimal applyFunction(String name, List<BigDecimal> arguments, Token function) throws Diagnostic {
            return switch (name) {
                case "abs" -> oneArgument(name, arguments, function).abs(MATH_CONTEXT);
                case "round" -> round(arguments, function);
                case "floor" -> oneArgument(name, arguments, function).setScale(0, RoundingMode.FLOOR);
                case "ceil" -> oneArgument(name, arguments, function).setScale(0, RoundingMode.CEILING);
                case "sqrt" -> sqrt(oneArgument(name, arguments, function), function);
                case "min" -> minMax(name, arguments, function, true);
                case "max" -> minMax(name, arguments, function, false);
                default -> throw error("Unknown function '" + name + "'", function.offset, function.length);
            };
        }

        private BigDecimal round(List<BigDecimal> arguments, Token function) throws Diagnostic {
            if (arguments.size() < 1 || arguments.size() > 2) {
                throw error("Function 'round' requires one or two arguments", function.offset, function.length);
            }
            int scale = 0;
            if (arguments.size() == 2) {
                try {
                    scale = arguments.get(1).intValueExact();
                } catch (ArithmeticException exception) {
                    throw error("round scale must be a whole number", function.offset, function.length);
                }
                if (scale < 0 || scale > 100) {
                    throw error("round scale must be between 0 and 100", function.offset, function.length);
                }
            }
            return arguments.getFirst().setScale(scale, RoundingMode.HALF_UP);
        }

        private BigDecimal sqrt(BigDecimal value, Token function) throws Diagnostic {
            if (value.signum() < 0) {
                throw error("sqrt requires a non-negative value", function.offset, function.length);
            }
            return value.sqrt(MATH_CONTEXT);
        }

        private BigDecimal minMax(String name, List<BigDecimal> arguments, Token function, boolean minimum) throws Diagnostic {
            if (arguments.size() < 2) {
                throw error("Function '" + name + "' requires at least two arguments", function.offset, function.length);
            }
            BigDecimal result = arguments.getFirst();
            for (int argument = 1; argument < arguments.size(); argument++) {
                BigDecimal candidate = arguments.get(argument);
                if ((minimum && candidate.compareTo(result) < 0) || (!minimum && candidate.compareTo(result) > 0)) {
                    result = candidate;
                }
            }
            return result;
        }

        private BigDecimal oneArgument(String name, List<BigDecimal> arguments, Token function) throws Diagnostic {
            if (arguments.size() != 1) {
                throw error("Function '" + name + "' requires exactly one argument", function.offset, function.length);
            }
            return arguments.getFirst();
        }

        private BigDecimal power(BigDecimal base, BigDecimal exponent, Token operator) throws Diagnostic {
            final BigInteger integerExponent;
            try {
                integerExponent = exponent.toBigIntegerExact();
            } catch (ArithmeticException exception) {
                throw error("Power exponent must be an integer", operator.offset, operator.length);
            }
            if (integerExponent.abs().compareTo(BigInteger.valueOf(MAX_POWER)) > 0) {
                throw error("Power exponent is too large", operator.offset, operator.length);
            }
            int value = integerExponent.intValue();
            try {
                if (value >= 0) {
                    return base.pow(value, MATH_CONTEXT);
                }
                if (base.signum() == 0) {
                    throw error("Division by zero", operator.offset, operator.length);
                }
                return BigDecimal.ONE.divide(base.pow(-value, MATH_CONTEXT), MATH_CONTEXT);
            } catch (ArithmeticException exception) {
                throw error("Invalid power operation", operator.offset, operator.length);
            }
        }

        private BigDecimal parseRequiredValue() throws Diagnostic, CalculatorException {
            if (peek().type == TokenType.END) {
                throw incomplete("Expected a value", sourceLength);
            }
            return parseUnary();
        }

        private BigDecimal parseRequiredMultiplication() throws Diagnostic, CalculatorException {
            if (peek().type == TokenType.END) {
                throw incomplete("Expected a value", sourceLength);
            }
            return parseMultiplication();
        }

        private void enterNesting(Token token) throws Diagnostic {
            nesting++;
            if (nesting > MAX_NESTING) {
                throw error("Expression nesting is too deep", token.offset, token.length);
            }
        }

        private boolean isOperator(String operator) {
            return peek().type == TokenType.OPERATOR && peek().text.equals(operator);
        }

        private Token peek() {
            return tokens.get(index);
        }

        private Token consume() {
            return tokens.get(index++);
        }

    }

    private static final class Diagnostic extends Exception {
        private final int offset;
        private final int length;
        private final boolean incomplete;

        private Diagnostic(String message, int offset, int length, boolean incomplete) {
            super(message, null, false, false);
            this.offset = offset;
            this.length = length;
            this.incomplete = incomplete;
        }
    }
}
