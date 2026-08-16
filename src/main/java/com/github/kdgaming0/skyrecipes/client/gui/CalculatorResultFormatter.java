package com.github.kdgaming0.skyrecipes.client.gui;

import com.github.kdgaming0.skyrecipes.client.config.SkyRecipesConfig;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/** Formats calculator values without changing their mathematical precision. */
public final class CalculatorResultFormatter {

    private static final BigDecimal THOUSAND = new BigDecimal("1000");
    private static final BigDecimal MILLION = new BigDecimal("1000000");
    private static final BigDecimal BILLION = new BigDecimal("1000000000");
    private static final BigDecimal TRILLION = new BigDecimal("1000000000000");
    private static final int MAX_FIXED_DIGITS = 512;
    private static final int MAX_COMPACT_EXPONENT = 14;
    private static final ThreadLocal<Map<Integer, DecimalFormat>> GROUPED_FORMATS =
            ThreadLocal.withInitial(HashMap::new);
    private static final ThreadLocal<Map<Integer, DecimalFormat>> PLAIN_FORMATS =
            ThreadLocal.withInitial(HashMap::new);

    private CalculatorResultFormatter() {
    }

    public static String format(BigDecimal value, SkyRecipesConfig.CalculatorResultFormat mode,
                                int precision, Predicate<String> fits) {
        return prepare(value, precision).format(mode, fits);
    }

    public static PreparedResult prepare(BigDecimal value, int precision) {
        return new PreparedResult(value, Math.max(precision, 0));
    }

    public static String full(BigDecimal value, int precision) {
        if (!isSafeFixedPoint(value)) {
            return scientific(value, precision);
        }
        return normalizeNegativeZero(decimalFormat(true, precision).format(value));
    }

    public static String compact(BigDecimal value, int precision) {
        return prepare(value, precision).compact();
    }

    public static String scientific(BigDecimal value, int precision) {
        if (value.signum() == 0) {
            return "0";
        }
        int exponent = value.precision() - value.scale() - 1;
        BigDecimal mantissa = value.movePointLeft(exponent);
        String formatted = decimalFormat(false, precision).format(mantissa);
        return normalizeNegativeZero(formatted) + "e" + exponent;
    }

    public static String exact(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        if (!isSafeFixedPoint(normalized)) {
            return normalized.toString();
        }
        return normalized.toPlainString();
    }

    private static boolean isSafeFixedPoint(BigDecimal value) {
        if (value.signum() == 0) {
            return true;
        }
        long exponent = adjustedExponent(value);
        long integerDigits = Math.max(exponent + 1L, 1L);
        long fractionalDigits = Math.max(value.scale(), 0);
        return integerDigits + fractionalDigits <= MAX_FIXED_DIGITS;
    }

    private static long adjustedExponent(BigDecimal value) {
        return value.signum() == 0 ? 0L : (long) value.precision() - value.scale() - 1L;
    }

    private static DecimalFormat decimalFormat(boolean grouping, int precision) {
        int normalizedPrecision = Math.max(precision, 0);
        Map<Integer, DecimalFormat> formats = (grouping ? GROUPED_FORMATS : PLAIN_FORMATS).get();
        return formats.computeIfAbsent(normalizedPrecision, key -> createDecimalFormat(grouping, key));
    }

    private static DecimalFormat createDecimalFormat(boolean grouping, int precision) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.ROOT);
        DecimalFormat format = new DecimalFormat("0", symbols);
        format.setGroupingUsed(grouping);
        format.setGroupingSize(3);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(precision);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format;
    }

    public static final class PreparedResult {
        private final BigDecimal value;
        private final int precision;
        private String full;
        private String compact;
        private String scientific;

        private PreparedResult(BigDecimal value, int precision) {
            this.value = value;
            this.precision = precision;
        }

        public String full() {
            if (full == null) {
                full = CalculatorResultFormatter.full(value, precision);
            }
            return full;
        }

        public String compact() {
            if (compact != null) {
                return compact;
            }
            if (adjustedExponent(value) > MAX_COMPACT_EXPONENT || !isSafeFixedPoint(value)) {
                compact = scientific();
                return compact;
            }

            BigDecimal absolute = value.abs();
            BigDecimal divisor;
            String suffix;
            if (absolute.compareTo(TRILLION) >= 0) {
                divisor = TRILLION;
                suffix = "t";
            } else if (absolute.compareTo(BILLION) >= 0) {
                divisor = BILLION;
                suffix = "b";
            } else if (absolute.compareTo(MILLION) >= 0) {
                divisor = MILLION;
                suffix = "m";
            } else if (absolute.compareTo(THOUSAND) >= 0) {
                divisor = THOUSAND;
                suffix = "k";
            } else {
                compact = full();
                return compact;
            }

            BigDecimal scaled = value.divide(divisor, precision, RoundingMode.HALF_UP);
            compact = normalizeNegativeZero(decimalFormat(false, precision).format(scaled)) + suffix;
            return compact;
        }

        public String scientific() {
            if (scientific == null) {
                scientific = CalculatorResultFormatter.scientific(value, precision);
            }
            return scientific;
        }

        public String format(SkyRecipesConfig.CalculatorResultFormat mode, Predicate<String> fits) {
            return switch (mode) {
                case FULL -> full();
                case COMPACT -> compact();
                case ADAPTIVE -> fits.test(full()) ? full() : fits.test(compact()) ? compact() : scientific();
            };
        }
    }

    private static String normalizeNegativeZero(String value) {
        if (value.startsWith("-")) {
            for (int i = 1; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c != '0' && c != '.' && c != ',') {
                    return value;
                }
            }
            return value.substring(1);
        }
        return value;
    }
}
