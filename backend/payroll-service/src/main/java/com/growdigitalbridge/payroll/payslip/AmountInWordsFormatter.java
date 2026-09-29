package com.growdigitalbridge.payroll.payslip;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Indian-style (crore/lakh/thousand) INR amount-to-words formatting for the payslip's "amount in
 * words" field (PAYROLL_REQUIREMENTS.md Section M - a formatting/presentation requirement, not a
 * statutory rule). Pure presentation logic: it renders whatever {@code BigDecimal} it is given -
 * the actual calculated net pay - and never derives or assumes a value itself.
 */
public final class AmountInWordsFormatter {

    private static final String[] ONES = {
            "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
            "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"
    };
    private static final String[] TENS = {
            "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    };

    private AmountInWordsFormatter() { }

    /** e.g. {@code 10000.00} -&gt; {@code "Ten Thousand Rupees Only"}. */
    public static String toIndianRupeesWords(BigDecimal amount) {
        BigDecimal normalized = (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
        boolean negative = normalized.signum() < 0;
        normalized = normalized.abs();

        long rupees = normalized.longValue();
        int paise = normalized.subtract(BigDecimal.valueOf(rupees)).movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValue();

        StringBuilder result = new StringBuilder();
        if (negative) {
            result.append("Minus ");
        }
        result.append(convertRupees(rupees)).append(" Rupees");
        if (paise > 0) {
            result.append(" and ").append(convertUpToTwoDigits(paise)).append(" Paise");
        }
        result.append(" Only");
        return result.toString();
    }

    private static String convertRupees(long value) {
        if (value == 0) {
            return "Zero";
        }
        long crore = value / 10_000_000L;
        value %= 10_000_000L;
        long lakh = value / 100_000L;
        value %= 100_000L;
        long thousand = value / 1_000L;
        value %= 1_000L;
        long remainder = value;

        List<String> parts = new ArrayList<>();
        if (crore > 0) {
            parts.add(convertUpToThreeDigits((int) crore) + " Crore");
        }
        if (lakh > 0) {
            parts.add(convertUpToTwoDigits((int) lakh) + " Lakh");
        }
        if (thousand > 0) {
            parts.add(convertUpToTwoDigits((int) thousand) + " Thousand");
        }
        if (remainder > 0) {
            parts.add(convertUpToThreeDigits((int) remainder));
        }
        return String.join(" ", parts);
    }

    private static String convertUpToThreeDigits(int n) {
        int hundreds = n / 100;
        int rest = n % 100;
        StringBuilder sb = new StringBuilder();
        if (hundreds > 0) {
            sb.append(ONES[hundreds]).append(" Hundred");
            if (rest > 0) {
                sb.append(" ");
            }
        }
        if (rest > 0) {
            sb.append(convertUpToTwoDigits(rest));
        }
        return sb.toString();
    }

    private static String convertUpToTwoDigits(int n) {
        if (n < 20) {
            return ONES[n];
        }
        int tens = n / 10;
        int ones = n % 10;
        return TENS[tens] + (ones > 0 ? " " + ONES[ones] : "");
    }
}
