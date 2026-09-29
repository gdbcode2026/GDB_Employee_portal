package com.growdigitalbridge.payroll.payslip;

import com.growdigitalbridge.payroll.api.dto.PayslipDtos;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Component;

/**
 * Renders {@link PayslipDtos.Detail} as a professional, print-friendly A4 salary slip
 * (PAYROLL_REQUIREMENTS.md Section M/12): company header, employee information, earnings vs
 * deductions, totals, employer contributions, YTD, tax section, and footer - GDB's own layout,
 * not a copy of any third-party product's branding/UI. Content is bounded (a fixed component
 * list per employee), so this always fits one page; the footer still prints "Page 1 of 1" so a
 * future multi-page layout (e.g. many components) only needs to change the page-count value.
 */
@Component
public class PayslipPdfGenerator {

    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float MARGIN = 42f;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public byte[] render(PayslipDtos.Detail payslip) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                Writer w = new Writer(content, regular, bold);
                w.header(payslip);
                w.employeeInfo(payslip);
                w.earningsAndDeductions(payslip);
                w.employerContributions(payslip);
                w.totals(payslip);
                w.amountInWords(payslip);
                w.ytdAndTax(payslip);
                w.footer(payslip);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to render payslip PDF.", e);
        }
    }

    /** Stateful top-down cursor over one page's content stream - kept private, not reused across payslips. */
    private static final class Writer {
        private final PDPageContentStream content;
        private final PDFont regular;
        private final PDFont bold;
        private float y = PAGE_HEIGHT - MARGIN;

        Writer(PDPageContentStream content, PDFont regular, PDFont bold) {
            this.content = content;
            this.regular = regular;
            this.bold = bold;
        }

        void header(PayslipDtos.Detail p) throws IOException {
            text(bold, 16, MARGIN, y, "Grow Digital Bridge");
            text(regular, 9, MARGIN, y - 14, "Payslip - Strictly Private and Confidential");
            String periodLabel = java.time.Month.of(p.periodMonth()) + " " + p.periodYear();
            textRightAligned(bold, 12, PAGE_WIDTH - MARGIN, y, periodLabel);
            y -= 26;
            rule();
            y -= 16;
        }

        void employeeInfo(PayslipDtos.Detail p) throws IOException {
            sectionTitle("Employee Information");
            float leftX = MARGIN;
            float rightX = PAGE_WIDTH / 2 + 10;
            float rowY = y;
            labelValue(leftX, rowY, "Employee Name", orNotAvailable(p.employeeName()));
            labelValue(rightX, rowY, "Employee Number", orNotAvailable(p.employeeNumber()));
            rowY -= 16;
            labelValue(leftX, rowY, "Designation", orNotAvailable(p.designation()));
            labelValue(rightX, rowY, "Department", orNotAvailable(p.department()));
            rowY -= 16;
            labelValue(leftX, rowY, "Joining Date", p.joiningDate() == null ? "Not available" : DATE_FORMAT.format(p.joiningDate()));
            labelValue(rightX, rowY, "Payment Date", p.paymentDate() == null ? "Not available" : DATE_FORMAT.format(p.paymentDate()));
            rowY -= 16;
            labelValue(leftX, rowY, "Pay Period",
                    DATE_FORMAT.format(p.periodStart()) + " - " + DATE_FORMAT.format(p.periodEnd()));
            y = rowY - 22;
        }

        void earningsAndDeductions(PayslipDtos.Detail p) throws IOException {
            sectionTitle("Earnings and Deductions");
            float leftX = MARGIN;
            float rightX = PAGE_WIDTH / 2 + 10;
            float columnWidth = PAGE_WIDTH / 2 - MARGIN - 10;

            text(bold, 10, leftX, y, "Earnings");
            text(bold, 10, rightX, y, "Deductions");
            y -= 14;
            float startY = y;

            float earnY = startY;
            for (PayslipDtos.ComponentLine line : p.earnings()) {
                lineItem(leftX, earnY, columnWidth, line.code(), line.amount());
                earnY -= 14;
            }
            float dedY = startY;
            for (PayslipDtos.ComponentLine line : p.deductions()) {
                lineItem(rightX, dedY, columnWidth, line.code(), line.amount());
                dedY -= 14;
            }
            y = Math.min(earnY, dedY) - 8;
        }

        void employerContributions(PayslipDtos.Detail p) throws IOException {
            if (p.employerContributions().isEmpty()) {
                return;
            }
            sectionTitle("Employer Contributions (for information only - not part of net pay)");
            for (PayslipDtos.ComponentLine line : p.employerContributions()) {
                lineItem(MARGIN, y, PAGE_WIDTH - 2 * MARGIN, line.code(), line.amount());
                y -= 14;
            }
            y -= 8;
        }

        void totals(PayslipDtos.Detail p) throws IOException {
            rule();
            y -= 16;
            float colWidth = (PAGE_WIDTH - 2 * MARGIN) / 3;
            totalBox(MARGIN, "Gross Pay", p.grossPay());
            totalBox(MARGIN + colWidth, "Total Deductions", p.totalDeductions());
            totalBox(MARGIN + 2 * colWidth, "Net Pay", p.netPay());
            y -= 34;
        }

        void amountInWords(PayslipDtos.Detail p) throws IOException {
            text(bold, 9, MARGIN, y, "Net Pay in Words:");
            text(regular, 9, MARGIN + 100, y, p.amountInWords());
            y -= 20;
        }

        void ytdAndTax(PayslipDtos.Detail p) throws IOException {
            rule();
            y -= 16;
            sectionTitle("Year-to-Date Summary");
            labelValue(MARGIN, y, "YTD Gross Pay",
                    p.ytd().available() ? formatAmount(p.ytd().grossPay()) : "Not available");
            labelValue(PAGE_WIDTH / 2 + 10, y, "YTD Deductions",
                    p.ytd().available() ? formatAmount(p.ytd().totalDeductions()) : "Not available");
            y -= 22;

            sectionTitle("Applicable Tax Information");
            labelValue(MARGIN, y, "Tax Deducted (This Period)",
                    p.tax().configured() ? formatAmount(p.tax().periodAmount()) : "Not configured");
            labelValue(PAGE_WIDTH / 2 + 10, y, "Tax Deducted (Year-to-Date)",
                    p.tax().ytdAmount() != null ? formatAmount(p.tax().ytdAmount()) : "Not configured");
            y -= 22;
        }

        void footer(PayslipDtos.Detail p) throws IOException {
            float footerY = MARGIN;
            rule(footerY + 20);
            text(regular, 7, MARGIN, footerY + 8,
                    "This is a system-generated payslip and does not require a signature. Generated: "
                            + p.generatedAt() + ".");
            textRightAligned(regular, 7, PAGE_WIDTH - MARGIN, footerY + 8, "Page 1 of 1");
        }

        private void sectionTitle(String title) throws IOException {
            text(bold, 10, MARGIN, y, title);
            y -= 16;
        }

        private void labelValue(float x, float rowY, String label, String value) throws IOException {
            text(regular, 8, x, rowY, label);
            text(bold, 9, x, rowY - 11, value == null ? "-" : value);
        }

        private void lineItem(float x, float rowY, float width, String label, BigDecimal amount) throws IOException {
            text(regular, 9, x, rowY, label);
            textRightAligned(regular, 9, x + width, rowY, formatAmount(amount));
        }

        private void totalBox(float x, String label, BigDecimal amount) throws IOException {
            text(regular, 8, x, y, label);
            text(bold, 12, x, y - 15, formatAmount(amount));
        }

        private void rule() throws IOException {
            rule(y);
        }

        private void rule(float atY) throws IOException {
            content.setLineWidth(0.5f);
            content.moveTo(MARGIN, atY);
            content.lineTo(PAGE_WIDTH - MARGIN, atY);
            content.stroke();
        }

        private void text(PDFont font, float size, float x, float atY, String value) throws IOException {
            content.beginText();
            content.setFont(font, size);
            content.newLineAtOffset(x, atY);
            content.showText(sanitize(value));
            content.endText();
        }

        private void textRightAligned(PDFont font, float size, float rightX, float atY, String value) throws IOException {
            String safe = sanitize(value);
            float width = font.getStringWidth(safe) / 1000f * size;
            text(font, size, rightX - width, atY, safe);
        }

        private String sanitize(String value) {
            if (value == null) {
                return "-";
            }
            // Standard 14 (WinAnsi) fonts cannot encode the Rupee sign or other non-Latin-1 glyphs.
            return value.replace("₹", "Rs.");
        }

        private String orNotAvailable(String value) {
            return value == null || value.isBlank() ? "Not available" : value;
        }

        private String formatAmount(BigDecimal amount) {
            if (amount == null) {
                return "-";
            }
            return "Rs. " + amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
        }
    }
}
