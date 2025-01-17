/*
 * Copyright © 2021. Werner Randelshofer, Switzerland. MIT License.
 */

package org.fastdoubleparser.parser;

/**
 * This version contains optimizations that make it faster for the
 * canada.txt data set at the expense of other data sets.
 * <p>
 * This is a C++ to Java port of Daniel Lemire's fast_double_parser.
 * <p>
 * The code has been changed, so that it parses the same syntax as
 * {@link Double#parseDouble(String)}.
 * <p>
 * References:
 * <dl>
 *     <dt>Daniel Lemire, fast_double_parser, 4x faster than strtod.
 *     Apache License 2.0 or Boost Software License.</dt>
 *     <dd><a href="https://github.com/lemire/fast_double_parser">github.com</a></dd>
 *
 *     <dt>Daniel Lemire, fast_float number parsing library: 4x faster than strtod.
 *     Apache License 2.0.</dt>
 *     <dd><a href="https://github.com/fastfloat/fast_float">github.com</a></dd>
 *
 *     <dt>Daniel Lemire, Number Parsing at a Gigabyte per Second.
 *     arXiv.2101.11408v3 [cs.DS] 24 Feb 2021</dt>
 *     <dd><a href="https://arxiv.org/pdf/2101.11408.pdf">arxiv.org</a></dd>
 * </dl>
 * <p>
 * <pre>
 * Benchmark                                 Mode  Cnt   Score   Error  Units
 * FastDoubleParserZero                      avgt   25    2.837 ± 0.018  ns/op
 * FastDoubleParserOnePointZero              avgt   25   12.148 ± 0.213  ns/op
 * FastDoubleParser3Digits                   avgt   25   11.457 ± 0.037  ns/op
 * FastDoubleParser3DigitsWithDecimalPoint   avgt   25   13.834 ± 0.134  ns/op
 * FastDoubleParser14HexDigitsWith3DigitExp  avgt   25   39.241 ± 0.293  ns/op
 * FastDoubleParser17DigitsWith3DigitExp     avgt   25   33.619 ± 0.252  ns/op
 * FastDoubleParser19DigitsWith3DigitExp     avgt   25   34.794 ± 0.105  ns/op
 * FastDoubleParser19DigitsWithoutExp        avgt   25   29.315 ± 0.250  ns/op
 *
 * </pre>
 */
public class FastDoubleParserOptimizedForCanada {
    private final static long MINIMAL_NINETEEN_DIGIT_INTEGER = 1000_00000_00000_00000L;
    private final static int MINIMAL_EIGHT_DIGIT_INTEGER = 100_00000;

    /**
     * Prevents instantiation.
     */
    private FastDoubleParserOptimizedForCanada() {

    }

    private static boolean isInteger(char c) {
        return '0' <= c && c <= '9';
    }

    private static NumberFormatException newNumberFormatException(CharSequence str) {
        if (str.length() > 1024) {
            // str can be up to Integer.MAX_VALUE characters long
            return new NumberFormatException("For input string of length " + str.length());
        } else {
            return new NumberFormatException("For input string: \"" + str.toString().trim() + "\"");
        }
    }

    /**
     * Returns a Double object holding the double value represented by the
     * argument string s.
     * <p>
     * This method can be used as a drop in for method
     * {@link Double#valueOf(String)}. (Assuming that the API of this method
     * has not changed since Java SE 16).
     * <p>
     * Leading and trailing whitespace characters in {@code str} are ignored.
     * Whitespace is removed as if by the {@link String#trim()} method;
     * that is, characters in the range [U+0000,U+0020].
     * <p>
     * The rest of {@code str} should constitute a FloatValue as described by the
     * lexical syntax rules shown below:
     * <blockquote>
     * <dl>
     * <dt><i>FloatValue:</i>
     * <dd><i>[Sign]</i> {@code NaN}
     * <dd><i>[Sign]</i> {@code Infinity}
     * <dd><i>[Sign] DecimalFloatingPointLiteral</i>
     * <dd><i>[Sign] HexFloatingPointLiteral</i>
     * <dd><i>SignedInteger</i>
     * </dl>
     *
     * <dl>
     * <dt><i>HexFloatingPointLiteral</i>:
     * <dd><i>HexSignificand BinaryExponent</i>
     * </dl>
     *
     * <dl>
     * <dt><i>HexSignificand:</i>
     * <dd><i>HexNumeral</i>
     * <dd><i>HexNumeral</i> {@code .}
     * <dd>{@code 0x} <i>[HexDigits]</i> {@code .} <i>HexDigits</i>
     * <dd>{@code 0X} <i>[HexDigits]</i> {@code .} <i>HexDigits</i>
     * </dl>
     *
     * <dl>
     * <dt><i>HexSignificand:</i>
     * <dd><i>HexNumeral</i>
     * <dd><i>HexNumeral</i> {@code .}
     * <dd>{@code 0x} <i>[HexDigits]</i> {@code .} <i>HexDigits</i>
     * <dd>{@code 0X} <i>[HexDigits]</i> {@code .} <i>HexDigits</i>
     * </dl>
     *
     * <dl>
     * <dt><i>BinaryExponent:</i>
     * <dd><i>BinaryExponentIndicator SignedInteger</i>
     * </dl>
     *
     * <dl>
     * <dt><i>BinaryExponentIndicator:</i>
     * <dd>{@code p}
     * <dd>{@code P}
     * </dl>
     *
     * <dl>
     * <dt><i>DecimalFloatingPointLiteral:</i>
     * <dd><i>Digits {@code .} [Digits] [ExponentPart]</i>
     * <dd><i>{@code .} Digits [ExponentPart]</i>
     * <dd><i>Digits ExponentPart</i>
     * </dl>
     *
     * <dl>
     * <dt><i>ExponentPart:</i>
     * <dd><i>ExponentIndicator SignedInteger</i>
     * </dl>
     *
     * <dl>
     * <dt><i>ExponentIndicator:</i>
     * <dd><i>(one of)</i>
     * <dd><i>e E</i>
     * </dl>
     *
     * <dl>
     * <dt><i>SignedInteger:</i>
     * <dd><i>[Sign] Digits</i>
     * </dl>
     *
     * <dl>
     * <dt><i>Sign:</i>
     * <dd><i>(one of)</i>
     * <dd><i>+ -</i>
     * </dl>
     *
     * <dl>
     * <dt><i>Digits:</i>
     * <dd><i>Digit {Digit}</i>
     * </dl>
     *
     * <dl>
     * <dt><i>HexNumeral:</i>
     * <dd>{@code 0} {@code x} <i>HexDigits</i>
     * <dd>{@code 0} {@code X} <i>HexDigits</i>
     * </dl>
     *
     * <dl>
     * <dt><i>HexDigits:</i>
     * <dd><i>HexDigit {HexDigit}</i>
     * </dl>
     *
     * <dl>
     * <dt><i>HexDigit:</i>
     * <dd><i>(one of)</i>
     * <dd>{@code 0 1 2 3 4 5 6 7 8 9 a b c d e f A B C D E F}
     * </dl>
     * </blockquote>
     *
     * @param str the string to be parsed
     */
    public static double parseDouble(CharSequence str) throws NumberFormatException {
        final int strlen = str.length();

        // Skip leading whitespace
        // -------------------
        int index = skipWhitespace(str, strlen, 0);
        if (index == strlen) {
            throw new NumberFormatException("empty String");
        }
        char ch = str.charAt(index);

        // Parse optional sign
        // -------------------
        final boolean isNegative = ch == '-';
        if (isNegative || ch == '+') {
            ch = ++index < strlen ? str.charAt(index) : 0;
            if (ch == 0) {
                throw newNumberFormatException(str);
            }
        }

        // Parse NaN or Infinity
        // ---------------------
        if (ch == 'N') {
            return parseNaN(str, index, strlen);
        } else if (ch == 'I') {
            return parseInfinity(str, index, strlen, isNegative);
        }

        // Parse optional leading zero
        // ---------------------------
        final boolean hasLeadingZero = ch == '0';
        if (hasLeadingZero) {
            ch = ++index < strlen ? str.charAt(index) : 0;
            if (ch == 'x' || ch == 'X') {
                return parseRestOfHexFloatingPointLiteral(str, index + 1, strlen, isNegative);
            }
        }

        return parseRestOfDecimalFloatLiteral(str, strlen, index, ch, isNegative, hasLeadingZero);
    }

    /**
     * Parses the following rules
     * (more rules are defined in {@link #parseDouble(CharSequence)}):
     * <dl>
     * <dt><i>RestOfDecimalFloatingPointLiteral</i>:
     * <dd><i>[Digits] {@code .} [Digits] [ExponentPart]</i>
     * <dd><i>{@code .} Digits [ExponentPart]</i>
     * <dd><i>[Digits] ExponentPart</i>
     * </dl>
     *
     * @param str            the input string
     * @param index          index to the first character of RestOfHexFloatingPointLiteral
     * @param strlen         the length of the string
     * @param isNegative     if the resulting number is negative
     * @param hasLeadingZero if the digit '0' has been consumed
     * @return a double representation
     */
     private static double parseRestOfDecimalFloatLiteral(CharSequence str, int strlen, int index, char ch, boolean isNegative, boolean hasLeadingZero) {
        // Parse digits
        // ------------
        // Note: a multiplication by 10 is cheaper than an arbitrary integer
        //       multiplication.

        long digits = 0;// digits is treated as an unsigned long
        long exponent = 0;
        final int indexOfFirstDigit = index;
        final int digitCount;

        // Two simple loops perform better than one loop if we expect that
        // many numbers do not have a decimal point.
        for (; index < strlen; index++) {
            ch = str.charAt(index);
            if (isInteger(ch)) {
                digits = 10 * digits + ch - '0';// This might overflow, we deal with it later.
            } else {
                break;
            }
        }
        final int virtualIndexOfPoint = index;
        if (ch == '.') {
            index++;
            for (; index < strlen; index++) {
                ch = str.charAt(index);
                if (isInteger(ch)) {
                    digits = 10 * digits + ch - '0';// This might overflow, we deal with it later.
                } else {
                    break;
                }
            }
            digitCount = index - indexOfFirstDigit - 1;
            exponent = virtualIndexOfPoint - index + 1;
        } else {
            digitCount = index - indexOfFirstDigit;
        }
        final int indexAfterDigits = index;

        // Parse exponent number
        // ---------------------
        long exp_number = 0;
        final boolean hasExponent = (ch == 'e') || (ch == 'E');
        if (hasExponent) {
            ch = ++index < strlen ? str.charAt(index) : 0;
            boolean neg_exp = ch == '-';
            if (neg_exp || ch == '+') {
                ch = ++index < strlen ? str.charAt(index) : 0;
            }
            if (!isInteger(ch)) {
                throw newNumberFormatException(str);
            }
            while (isInteger(ch)) {
                // Guard against overflow of exp_number
                if (exp_number < MINIMAL_EIGHT_DIGIT_INTEGER) {
                    exp_number = 10 * exp_number + ch - '0';
                }
                ch = ++index < strlen ? str.charAt(index) : 0;
            }
            if (neg_exp) {
                exp_number = -exp_number;
            }
            exponent += exp_number;
        }

        // Skip trailing whitespace
        // ------------------------
        index = skipWhitespace(str, strlen, index);
        if (index < strlen
                || !hasLeadingZero && digitCount == 0 && str.charAt(virtualIndexOfPoint) != '.') {
            throw newNumberFormatException(str);
        }

        // Re-parse digits in case of a potential overflow
        // -----------------------------------------------
        final boolean isDigitsTruncated;
        int skipCountInTruncatedDigits = 0;//counts +1 if we skipped over the decimal point
        if (digitCount > 19) {
            digits = 0;
            for (index = indexOfFirstDigit; index < indexAfterDigits; index++) {
                ch = str.charAt(index);
                if (ch == '.') {
                    skipCountInTruncatedDigits++;
                } else {
                    if (Long.compareUnsigned(digits, MINIMAL_NINETEEN_DIGIT_INTEGER) < 0) {
                        digits = 10 * digits + ch - '0';
                    } else {
                        break;
                    }
                }
            }
            isDigitsTruncated = (index < indexAfterDigits);
        } else {
            isDigitsTruncated = false;
        }

        return FastDoubleMath.decFloatLiteralToDouble(str, index, isNegative, digits, exponent, virtualIndexOfPoint, exp_number, isDigitsTruncated, skipCountInTruncatedDigits);
    }


    /**
     * Special value in {@link #CHAR_TO_HEX_MAP} for
     * the decimal point character.
     */
    private static final byte DECIMAL_POINT_CLASS = -4;
    /**
     * Special value in {@link #CHAR_TO_HEX_MAP} for
     * characters that are neither a hex digit nor
     * a decimal point character..
     */
    private static final byte OTHER_CLASS = -1;
    /**
     * A table of 128 entries or of entries up to including
     * character 'p' would suffice.
     * <p>
     * However for some reason, performance is best,
     * if this table has exactly 256 entries.
     */
    private static final byte[] CHAR_TO_HEX_MAP = new byte[256];

    static {
        for (char ch = 0; ch < CHAR_TO_HEX_MAP.length; ch++) {
            CHAR_TO_HEX_MAP[ch] = OTHER_CLASS;
        }
        for (char ch = '0'; ch <= '9'; ch++) {
            CHAR_TO_HEX_MAP[ch] = (byte) (ch - '0');
        }
        for (char ch = 'A'; ch <= 'F'; ch++) {
            CHAR_TO_HEX_MAP[ch] = (byte) (ch - 'A' + 10);
        }
        for (char ch = 'a'; ch <= 'f'; ch++) {
            CHAR_TO_HEX_MAP[ch] = (byte) (ch - 'a' + 10);
        }
        for (char ch = '.'; ch <= '.'; ch++) {
            CHAR_TO_HEX_MAP[ch] = DECIMAL_POINT_CLASS;
        }
    }

    /**
     * Parses the following rules
     * (more rules are defined in {@link #parseDouble(CharSequence)}):
     * <dl>
     * <dt><i>RestOfHexFloatingPointLiteral</i>:
     * <dd><i>RestOfHexSignificand BinaryExponent</i>
     * </dl>
     *
     * <dl>
     * <dt><i>RestOfHexSignificand:</i>
     * <dd><i>HexDigits</i>
     * <dd><i>HexDigits</i> {@code .}
     * <dd><i>[HexDigits]</i> {@code .} <i>HexDigits</i>
     * </dl>
     *
     * @param str        the input string
     * @param index      index to the first character of RestOfHexFloatingPointLiteral
     * @param strlen     the length of the string
     * @param isNegative if the resulting number is negative
     * @return a double representation
     */
    private static double parseRestOfHexFloatingPointLiteral(
            CharSequence str, int index, int strlen, boolean isNegative) {
        if (index >= strlen) {
            throw newNumberFormatException(str);
        }
        char ch = str.charAt(index);

        // Parse digits
        // ------------

        long digits = 0;// digits is treated as an unsigned long
        long exponent = 0;
        final int indexOfFirstDigit = index;
        int virtualIndexOfPoint = -1;
        final int digitCount;
        for (; index < strlen; index++) {
            ch = str.charAt(index);
            // Table look up is faster than a sequence of if-else-branches.
            int hexValue = ch > 255 ? OTHER_CLASS : CHAR_TO_HEX_MAP[ch];
            if (hexValue >= 0) {
                digits = (digits << 4) | hexValue;// This might overflow, we deal with it later.
            } else if (hexValue == DECIMAL_POINT_CLASS) {
                if (virtualIndexOfPoint != -1) {
                    throw newNumberFormatException(str);
                }
                virtualIndexOfPoint = index;
            } else {
                break;
            }
        }
        final int indexAfterDigits = index;
        if (virtualIndexOfPoint == -1) {
            digitCount = indexAfterDigits - indexOfFirstDigit;
            virtualIndexOfPoint = indexAfterDigits;
        } else {
            digitCount = indexAfterDigits - indexOfFirstDigit - 1;
            exponent = (virtualIndexOfPoint - index + 1) * 4L;
        }

        // Parse exponent number
        // ---------------------
        long exp_number = 0;
        final boolean hasExponent = (ch == 'p') || (ch == 'P');
        if (hasExponent) {
            ch = ++index < strlen ? str.charAt(index) : 0;
            boolean neg_exp = ch == '-';
            if (neg_exp || ch == '+') {
                ch = ++index < strlen ? str.charAt(index) : 0;
            }
            if (!isInteger(ch)) {
                throw newNumberFormatException(str);
            }
            while (isInteger(ch)) {
                // Guard against overflow of exp_number
                if (exp_number < MINIMAL_EIGHT_DIGIT_INTEGER) {
                    exp_number = 10 * exp_number + ch - '0';
                }
                ch = ++index < strlen ? str.charAt(index) : 0;
            }
            if (neg_exp) {
                exp_number = -exp_number;
            }
            exponent += exp_number;
        }

        // Skip trailing whitespace
        // ------------------------
        index = skipWhitespace(str, strlen, index);
        if (index < strlen
                || digitCount == 0 && str.charAt(virtualIndexOfPoint) != '.'
                || !hasExponent) {
            throw newNumberFormatException(str);
        }

        // Re-parse digits in case of a potential overflow
        // -----------------------------------------------
        final boolean isDigitsTruncated;
        int skipCountInTruncatedDigits = 0;//counts +1 if we skipped over the decimal point
        if (digitCount > 16) {
            digits = 0;
            for (index = indexOfFirstDigit; index < indexAfterDigits; index++) {
                ch = str.charAt(index);
                // Table look up is faster than a sequence of if-else-branches.
                int hexValue = ch > 127 ? OTHER_CLASS : CHAR_TO_HEX_MAP[ch];
                if (hexValue >= 0) {
                    if (Long.compareUnsigned(digits, MINIMAL_NINETEEN_DIGIT_INTEGER) < 0) {
                        digits = (digits << 4) | hexValue;
                    } else {
                        break;
                    }
                } else {
                    skipCountInTruncatedDigits++;
                }
            }
            isDigitsTruncated = (index < indexAfterDigits);
        } else {
            isDigitsTruncated = false;
        }

        return FastDoubleMath.hexFloatLiteralToDouble(str, index, isNegative, digits, exponent, virtualIndexOfPoint, exp_number, isDigitsTruncated, skipCountInTruncatedDigits);

    }

    private static double parseInfinity(CharSequence str, int index, int strlen, boolean negative) {
        if (index + 7 < strlen
                //  && str.charAt(index) == 'I'
                && str.charAt(index + 1) == 'n'
                && str.charAt(index + 2) == 'f'
                && str.charAt(index + 3) == 'i'
                && str.charAt(index + 4) == 'n'
                && str.charAt(index + 5) == 'i'
                && str.charAt(index + 6) == 't'
                && str.charAt(index + 7) == 'y'
        ) {
            index = skipWhitespace(str, strlen, index + 8);
            if (index < strlen) {
                throw newNumberFormatException(str);
            }
            return negative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        } else {
            throw newNumberFormatException(str);
        }
    }

    private static double parseNaN(CharSequence str, int index, int strlen) {
        if (index + 2 < strlen
                //   && str.charAt(index) == 'N'
                && str.charAt(index + 1) == 'a'
                && str.charAt(index + 2) == 'N') {

            index = skipWhitespace(str, strlen, index + 3);
            if (index < strlen) {
                throw newNumberFormatException(str);
            }

            return Double.NaN;
        } else {
            throw newNumberFormatException(str);
        }
    }

    private static int skipWhitespace(CharSequence str, int strlen, int index) {
        for (; index < strlen; index++) {
            if (str.charAt(index) > 0x20) {
                break;
            }
        }
        return index;
    }


}