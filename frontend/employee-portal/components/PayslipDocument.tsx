import type { PayslipDetail } from "@/lib/api/types";
import { formatCurrency, formatDate, formatPeriodLabel } from "@/lib/format";
import { PrintButton } from "@/components/PrintButton";

export function PayslipDocument({ payslip, actions }: { payslip: PayslipDetail; actions?: React.ReactNode }) {
  return (
    <div>
      <div className="payslip-actions">
        <PrintButton />
        {actions}
      </div>

      <div className="payslip-sheet">
        <div className="payslip-header">
          <div className="payslip-brand">
            <span className="mark">GDB</span>
            <div>
              <div className="name">Grow Digital Bridge</div>
              <div className="tag">Payslip</div>
            </div>
          </div>
          <div className="payslip-title">
            <h2>{formatPeriodLabel(payslip.periodYear, payslip.periodMonth)}</h2>
            <div className="muted">Generated {formatDate(payslip.generatedAt)}</div>
          </div>
        </div>

        <dl className="payslip-meta-grid">
          <div>
            <dt>Employee name</dt>
            <dd>{payslip.employeeName ?? "Not available"}</dd>
          </div>
          <div>
            <dt>Employee number</dt>
            <dd>{payslip.employeeNumber ?? "Not available"}</dd>
          </div>
          <div>
            <dt>Designation</dt>
            <dd>{payslip.designation ?? "Not available"}</dd>
          </div>
          <div>
            <dt>Department</dt>
            <dd>{payslip.department ?? "Not available"}</dd>
          </div>
          <div>
            <dt>Payment date</dt>
            <dd>{formatDate(payslip.paymentDate)}</dd>
          </div>
          <div>
            <dt>Pay period</dt>
            <dd>
              {formatDate(payslip.periodStart)} – {formatDate(payslip.periodEnd)}
            </dd>
          </div>
        </dl>

        <div className="payslip-columns">
          <div>
            <h4>Earnings</h4>
            {payslip.earnings.map((item) => (
              <div className="payslip-line" key={item.code}>
                <span>{item.code}</span>
                <span>{formatCurrency(item.amount, "INR")}</span>
              </div>
            ))}
            <div className="payslip-line" style={{ fontWeight: 700, borderTop: "1px solid var(--color-border)", marginTop: "0.4rem", paddingTop: "0.5rem" }}>
              <span>Gross pay</span>
              <span>{formatCurrency(payslip.grossPay, "INR")}</span>
            </div>
          </div>
          <div>
            <h4>Deductions</h4>
            {payslip.deductions.map((item) => (
              <div className="payslip-line" key={item.code}>
                <span>{item.code}</span>
                <span>{formatCurrency(item.amount, "INR")}</span>
              </div>
            ))}
            <div className="payslip-line" style={{ fontWeight: 700, borderTop: "1px solid var(--color-border)", marginTop: "0.4rem", paddingTop: "0.5rem" }}>
              <span>Total deductions</span>
              <span>{formatCurrency(payslip.totalDeductions, "INR")}</span>
            </div>
          </div>
        </div>

        {payslip.employerContributions.length > 0 && (
          <div>
            <h4>Employer contributions (for information only)</h4>
            {payslip.employerContributions.map((item) => (
              <div className="payslip-line" key={item.code}>
                <span>{item.code}</span>
                <span>{formatCurrency(item.amount, "INR")}</span>
              </div>
            ))}
          </div>
        )}

        <div className="payslip-totals">
          <div className="item">
            <dt>Gross pay</dt>
            <dd>{formatCurrency(payslip.grossPay, "INR")}</dd>
          </div>
          <div className="item">
            <dt>Total deductions</dt>
            <dd>{formatCurrency(payslip.totalDeductions, "INR")}</dd>
          </div>
          <div className="item net">
            <dt>Net pay</dt>
            <dd>{formatCurrency(payslip.netPay, "INR")}</dd>
          </div>
        </div>

        <p className="payslip-words">{payslip.amountInWords}</p>

        <div className="payslip-columns">
          <div>
            <h4>Year to date</h4>
            {payslip.ytd.available ? (
              <>
                <div className="payslip-line">
                  <span>YTD gross</span>
                  <span>{formatCurrency(payslip.ytd.grossPay, "INR")}</span>
                </div>
                <div className="payslip-line">
                  <span>YTD deductions</span>
                  <span>{formatCurrency(payslip.ytd.totalDeductions, "INR")}</span>
                </div>
              </>
            ) : (
              <p className="muted">Not available.</p>
            )}
          </div>
          <div>
            <h4>Applicable tax information</h4>
            {payslip.tax.configured ? (
              <>
                <div className="payslip-line">
                  <span>Tax deducted this period</span>
                  <span>{formatCurrency(payslip.tax.periodAmount ?? 0, "INR")}</span>
                </div>
                <div className="payslip-line">
                  <span>Tax deducted (YTD)</span>
                  <span>{payslip.tax.ytdAmount === null ? "Not configured" : formatCurrency(payslip.tax.ytdAmount, "INR")}</span>
                </div>
              </>
            ) : (
              <p className="muted">Not configured for this employee/period.</p>
            )}
          </div>
        </div>

        <div className="payslip-footer">
          <span>This is a system-generated payslip and does not require a signature.</span>
          <span>Grow Digital Bridge &middot; Confidential</span>
        </div>
      </div>
    </div>
  );
}
