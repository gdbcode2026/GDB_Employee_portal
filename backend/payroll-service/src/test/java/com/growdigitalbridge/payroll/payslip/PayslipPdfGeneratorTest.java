package com.growdigitalbridge.payroll.payslip;

import com.growdigitalbridge.payroll.api.dto.PayslipDtos;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PayslipPdfGeneratorTest {

    private final PayslipPdfGenerator generator = new PayslipPdfGenerator();

    private PayslipDtos.Detail sample() {
        return new PayslipDtos.Detail(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                2026, 3, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 1),
                "EMP-0042", "Asha Rao", "Software Engineer", null, LocalDate.of(2022, 6, 1),
                List.of(new PayslipDtos.ComponentLine("BASIC_SALARY", new BigDecimal("50000.00")),
                        new PayslipDtos.ComponentLine("HRA", new BigDecimal("20000.00"))),
                List.of(new PayslipDtos.ComponentLine("PF", new BigDecimal("6000.00"))),
                List.of(new PayslipDtos.ComponentLine("EMPLOYER_PF", new BigDecimal("6000.00"))),
                new BigDecimal("70000.00"), new BigDecimal("6000.00"), new BigDecimal("64000.00"),
                "Sixty Four Thousand Rupees Only",
                new PayslipDtos.YtdInfo(true, new BigDecimal("140000.00"), new BigDecimal("12000.00")),
                new PayslipDtos.TaxInfo(false, null, null),
                Instant.parse("2026-04-02T10:15:30Z"));
    }

    @Test
    void rendersASinglePageA4PdfContainingEveryDocumentedSection() throws Exception {
        byte[] pdf = generator.render(sample());
        assertThat(pdf).isNotEmpty();

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            String text = new PDFTextStripper().getText(document);

            assertThat(text).contains("Grow Digital Bridge");
            assertThat(text).contains("Employee Information");
            assertThat(text).contains("Asha Rao");
            assertThat(text).contains("EMP-0042");
            assertThat(text).contains("Software Engineer");
            assertThat(text).contains("Earnings and Deductions");
            assertThat(text).contains("BASIC_SALARY");
            assertThat(text).contains("HRA");
            assertThat(text).contains("PF");
            assertThat(text).contains("Employer Contributions");
            assertThat(text).contains("EMPLOYER_PF");
            assertThat(text).contains("Gross Pay");
            assertThat(text).contains("Net Pay");
            assertThat(text).contains("Sixty Four Thousand Rupees Only");
            assertThat(text).contains("Year-to-Date Summary");
            assertThat(text).contains("Not configured");
            assertThat(text).contains("Page 1 of 1");
            // Standard-14/WinAnsi fonts cannot encode the rupee sign; every amount must use "Rs." instead.
            assertThat(text).doesNotContain("₹");
            assertThat(text).contains("Rs. 70000.00");
        }
    }

    @Test
    void unavailableEmployeeFieldsAreRenderedSafelyRatherThanFabricated() throws Exception {
        PayslipDtos.Detail withoutProfile = new PayslipDtos.Detail(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), 2026, 1, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), null,
                null, null, null, null, null,
                List.of(new PayslipDtos.ComponentLine("BASIC_SALARY", new BigDecimal("30000.00"))),
                List.of(), List.of(),
                new BigDecimal("30000.00"), BigDecimal.ZERO, new BigDecimal("30000.00"),
                "Thirty Thousand Rupees Only",
                new PayslipDtos.YtdInfo(true, new BigDecimal("30000.00"), BigDecimal.ZERO),
                new PayslipDtos.TaxInfo(false, null, null),
                Instant.parse("2026-02-01T00:00:00Z"));

        byte[] pdf = generator.render(withoutProfile);
        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Not available");
        }
    }
}
