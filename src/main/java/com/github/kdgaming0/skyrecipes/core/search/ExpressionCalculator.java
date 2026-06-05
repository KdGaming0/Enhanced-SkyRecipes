package com.github.kdgaming0.skyrecipes.core.search;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Mathematical expression evaluator using the shunting-yard algorithm
 * with {@link BigDecimal} RPN evaluation for precision.
 *
 * <p>Supported syntax:</p>
 * <ul>
 *   <li>Binary operators: {@code + - * / ^ %}</li>
 *   <li>Parentheses: {@code ( )}</li>
 *   <li>Number suffixes: {@code k K} (thousand), {@code m M} (million), {@code b B} (billion)</li>
 * </ul>
 */
public final class ExpressionCalculator {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final int SCALE = 10;

    private ExpressionCalculator() {
    }

    // -----------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------

    /**
     * Evaluate a mathematical expression.
     *
     * @param expression the expression string
     * @return a {@link Result} containing either the value or an error message
     */
    public static Result evaluate(String expression) {
        if (expression == null || expression.isBlank()) {
            return Result.error("Empty expression");
        }

        try {
            List<Token> tokens = tokenize(expression);
            if (tokens.isEmpty()) {
                return Result.error("Empty expression");
            }
            List<Token> rpn = toRpn(tokens);
            BigDecimal value = evalRpn(rpn);
            return Result.ok(value);
        } catch (ParseException e) {
            return Result.error(e.getMessage());
        } catch (ArithmeticException e) {
            return Result.error("Arithmetic error: " + e.getMessage());
        }
    }

    private static List<Token> tokenize(String expr) throws ParseException {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        int len = expr.length();

        while (i < len) {
            char c = expr.charAt(i);

            // Skip whitespace
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            // Parentheses
            if (c == '(') {
                tokens.add(new Token(TokenType.LPAREN, "(", null));
                i++;
                continue;
            }
            if (c == ')') {
                tokens.add(new Token(TokenType.RPAREN, ")", null));
                i++;
                continue;
            }

            // Operators
            if (isOperatorChar(c)) {
                tokens.add(new Token(TokenType.OPERATOR, String.valueOf(c), null));
                i++;
                continue;
            }

            // Number (decimal)
            if (Character.isDigit(c) || c == '.') {
                int start = i;
                boolean dotSeen = c == '.';
                i++;
                while (i < len) {
                    char ch = expr.charAt(i);
                    if (Character.isDigit(ch)) {
                        i++;
                    } else if (ch == '.' && !dotSeen) {
                        dotSeen = true;
                        i++;
                    } else {
                        break;
                    }
                }
                String numStr = expr.substring(start, i);
                // Suffix immediately after number?
                if (i < len) {
                    char suffix = expr.charAt(i);
                    BigDecimal multiplier = suffixMultiplier(suffix);
                    if (multiplier != null) {
                        i++;
                        BigDecimal base = new BigDecimal(numStr, MC);
                        tokens.add(new Token(TokenType.NUMBER, numStr + suffix, base.multiply(multiplier, MC)));
                        continue;
                    }
                }
                tokens.add(new Token(TokenType.NUMBER, numStr, new BigDecimal(numStr, MC)));
                continue;
            }

            // Standalone suffix (e.g. "1.5m + 250k" — the k/m are handled above,
            // but if someone writes "k" alone we treat it as 1_000)
            BigDecimal suffixVal = suffixMultiplier(c);
            if (suffixVal != null) {
                tokens.add(new Token(TokenType.NUMBER, String.valueOf(c), suffixVal));
                i++;
                continue;
            }

            throw new ParseException("Unexpected character: '" + c + "' at position " + i);
        }

        return tokens;
    }

    private static boolean isOperatorChar(char c) {
        return c == '+' || c == '-' || c == '*' || c == '/' || c == '^' || c == '%';
    }

    private static BigDecimal suffixMultiplier(char c) {
        return switch (c) {
            case 'k', 'K' -> BigDecimal.valueOf(1_000);
            case 'm', 'M' -> BigDecimal.valueOf(1_000_000);
            case 'b', 'B' -> BigDecimal.valueOf(1_000_000_000);
            default -> null;
        };
    }

    // -----------------------------------------------------------------
    // Tokenizer
    // -----------------------------------------------------------------

    private static List<Token> toRpn(List<Token> tokens) throws ParseException {
        List<Token> output = new ArrayList<>();
        Deque<Token> opStack = new ArrayDeque<>();

        Token prev = null;
        for (Token token : tokens) {
            switch (token.type()) {
                case NUMBER -> output.add(token);
                case OPERATOR -> {
                    // Handle unary minus
                    if (token.text().equals("-") && (prev == null || prev.type() == TokenType.OPERATOR || prev.type() == TokenType.LPAREN)) {
                        // Unary minus: push 0 then binary minus
                        output.add(new Token(TokenType.NUMBER, "0", BigDecimal.ZERO));
                    }

                    while (!opStack.isEmpty() && opStack.peek().type() == TokenType.OPERATOR
                            && precedence(opStack.peek().text()) >= precedence(token.text())) {
                        output.add(opStack.pop());
                    }
                    opStack.push(token);
                }
                case LPAREN -> opStack.push(token);
                case RPAREN -> {
                    while (!opStack.isEmpty() && opStack.peek().type() != TokenType.LPAREN) {
                        output.add(opStack.pop());
                    }
                    if (opStack.isEmpty()) {
                        throw new ParseException("Mismatched parenthesis");
                    }
                    opStack.pop(); // discard '('
                }
                default -> throw new ParseException("Unexpected token: " + token.text());
            }
            prev = token;
        }

        while (!opStack.isEmpty()) {
            Token op = opStack.pop();
            if (op.type() == TokenType.LPAREN) {
                throw new ParseException("Mismatched parenthesis");
            }
            output.add(op);
        }

        return output;
    }

    private static int precedence(String op) {
        return switch (op) {
            case "+", "-" -> 1;
            case "*", "/", "%" -> 2;
            case "^" -> 3;
            default -> 0;
        };
    }

    private static BigDecimal evalRpn(List<Token> rpn) throws ParseException {
        Deque<BigDecimal> stack = new ArrayDeque<>();

        for (Token token : rpn) {
            if (token.type() == TokenType.NUMBER) {
                stack.push(token.value());
            } else if (token.type() == TokenType.OPERATOR) {
                if (stack.size() < 2) {
                    throw new ParseException("Invalid expression: not enough operands for '" + token.text() + "'");
                }
                BigDecimal b = stack.pop();
                BigDecimal a = stack.pop();
                stack.push(applyOperator(a, b, token.text()));
            }
        }

        if (stack.size() != 1) {
            throw new ParseException("Invalid expression: leftover operands");
        }

        return stack.pop();
    }

    private static BigDecimal applyOperator(BigDecimal a, BigDecimal b, String op) throws ParseException {
        return switch (op) {
            case "+" -> a.add(b, MC);
            case "-" -> a.subtract(b, MC);
            case "*" -> a.multiply(b, MC);
            case "/" -> {
                if (b.compareTo(BigDecimal.ZERO) == 0) {
                    throw new ParseException("Division by zero");
                }
                yield a.divide(b, SCALE, RoundingMode.HALF_UP);
            }
            case "%" -> {
                if (b.compareTo(BigDecimal.ZERO) == 0) {
                    throw new ParseException("Modulo by zero");
                }
                yield a.remainder(b, MC);
            }
            case "^" -> {
                try {
                    int exp = b.intValueExact();
                    yield a.pow(exp, MC);
                } catch (ArithmeticException e) {
                    // Fallback to double pow for non-integer exponents
                    yield BigDecimal.valueOf(Math.pow(a.doubleValue(), b.doubleValue()));
                }
            }
            default -> throw new ParseException("Unknown operator: " + op);
        };
    }

    private enum TokenType {
        NUMBER, OPERATOR, LPAREN, RPAREN, SUFFIX
    }

    // -----------------------------------------------------------------
    // Shunting-yard: infix -> RPN
    // -----------------------------------------------------------------

    /**
     * Result of an expression evaluation.
     */
    public sealed interface Result {
        static Result ok(BigDecimal value) {
            return new Ok(value);
        }

        static Result error(String message) {
            return new Err(message);
        }

        boolean success();

        BigDecimal value();

        String error();
    }

    private record Ok(BigDecimal value) implements Result {
        @Override
        public boolean success() {
            return true;
        }

        @Override
        public String error() {
            return null;
        }
    }

    // -----------------------------------------------------------------
    // RPN evaluation
    // -----------------------------------------------------------------

    private record Err(String error) implements Result {
        @Override
        public boolean success() {
            return false;
        }

        @Override
        public BigDecimal value() {
            return null;
        }
    }

    private record Token(TokenType type, String text, BigDecimal value) {
    }

    // -----------------------------------------------------------------
    // Exceptions
    // -----------------------------------------------------------------

    private static class ParseException extends Exception {
        ParseException(String message) {
            super(message);
        }
    }
}
