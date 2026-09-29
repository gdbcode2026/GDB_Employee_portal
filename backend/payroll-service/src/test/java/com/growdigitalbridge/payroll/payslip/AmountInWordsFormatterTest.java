package com.growdigitalbridge.payroll.payslip;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AmountInWordsFormatterTest {

    @Test
    void tenThousandRendersExactlyAsTheDocumentedExample() {
        assertThat(AmountInWordsFormatter.toIndianRupeesWords(new BigDecimal("10000.00")))
                .isEqualTo("Ten Thousand Rupees Only");
    }

    @Test
    void zeroRendersAsZeroRupees() {
        assertThat(AmountInWordsFormatter.toIndianRupeesWords(BigDecimal.ZERO)).isEqualTo("Zero Rupees Only");
    }

    @Test
    void paiseAreRenderedWhenPresent() {
        assertThat(AmountInWordsFormatter.toIndianRupeesWords(new BigDecimal("100.50")))
                .isEqualTo("One Hundred Rupees and Fifty Paise Only");
    }

    @Test
    void lakhAndCroreGroupingUsesTheIndianNumberingSystem() {
        assertThat(AmountInWordsFormatter.toIndianRupeesWords(new BigDecimal("1234567.00")))
                .isEqualTo("Twelve Lakh Thirty Four Thousand Five Hundred Sixty Seven Rupees Only");
        assertThat(AmountInWordsFormatter.toIndianRupeesWords(new BigDecimal("100000000.00")))
                .isEqualTo("Ten Crore Rupees Only");
    }

    @Test
    void nullIsSafelyTreatedAsZero() {
        assertThat(AmountInWordsFormatter.toIndianRupeesWords(null)).isEqualTo("Zero Rupees Only");
    }
}
