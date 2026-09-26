import type { DemoPayslip } from "@/lib/demoPayslip";
import { formatCurrency, formatDate } from "@/lib/format";
import { PrintButton } from "@/components/PrintButton";

export function PayslipDocument({ payslip }: { payslip: DemoPayslip }) {
  const grossPay = payslip.earnings.reduce((sum, item) => sum + item.amount, 0);
  const totalDeductions = payslip.deductions.reduce((sum, item) => sum + item.amount, 0);
  const netPay = grossPay - totalDeductions;

  return (
    <div>
      <div className="payslip-actions">
        <PrintButton />
        <button type="button" className="btn btn-secondary" disabled title="Available once the Payroll Service is implemented">
          Download PDF
        </button>
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
            <h2>{payslip.payPeriod}</h2>
            <div className="muted">{payslip.financialYear}</div>
          </div>
        </div>

        <dl className="payslip-meta-grid">
          <div>
            <dt>Employee name</dt>
            <dd>{payslip.employeeName}</dd>
          </div>
          <div>
            <dt>Employee number</dt>
            <dd>{payslip.employeeNumber}</dd>
          </div>
          <div>
            <dt>Department</dt>
            <dd>{payslip.department}</dd>
          </div>
          <div>
            <dt>Designation</dt>
            <dd>{payslip.designation}</dd>
          </div>
          <div>
            <dt>Payment date</dt>
            <dd>{formatDate(payslip.paymentDate)}</dd>
          </div>
          <div>
            <dt>PAN</dt>
            <dd>{payslip.panMasked}</dd>
          </div>
          <div>
            <dt>Bank account</dt>
            <dd>{payslip.bankAccountMasked}</dd>
          </div>
        </dl>

        <div className="payslip-columns">
          <div>
            <h4>Earnings</h4>
            {payslip.earnings.map((item) => (
              <div className="payslip-line" key={item.label}>
                <span>{item.label}</span>
                <span>{formatCurrency(item.amount, "INR")}</span>
              </div>
            ))}
            <div className="payslip-line" style={{ fontWeight: 700, borderTop: "1px solid var(--color-border)", marginTop: "0.4rem", paddingTop: "0.5rem" }}>
              <span>Gross pay</span>
              <span>{formatCurrency(grossPay, "INR")}</span>
            </div>
          </div>
          <div>
            <h4>Deductions</h4>
            {payslip.deductions.map((item) => (
              <div className="payslip-line" key={item.label}>
                <span>{item.label}</span>
                <span>{formatCurrency(item.amount, "INR")}</span>
              </div>
            ))}
            <div className="payslip-line" style={{ fontWeight: 700, borderTop: "1px solid var(--color-border)", marginTop: "0.4rem", paddingTop: "0.5rem" }}>
              <span>Total deductions</span>
              <span>{formatCurrency(totalDeductions, "INR")}</span>
            </div>
          </div>
        </div>

        <div>
          <h4>Employer contributions</h4>
          {payslip.employerContributions.map((item) => (
            <div className="payslip-line" key={item.label}>
              <span>{item.label}</span>
              <span>{formatCurrency(item.amount, "INR")}</span>
            </div>
          ))}
        </div>

        <div className="payslip-totals">
          <div className="item">
            <dt>Gross pay</dt>
            <dd>{formatCurrency(grossPay, "INR")}</dd>
          </div>
          <div className="item">
            <dt>Total deductions</dt>
            <dd>{formatCurrency(totalDeductions, "INR")}</dd>
          </div>
          <div className="item net">
            <dt>Net pay</dt>
            <dd>{formatCurrency(netPay, "INR")}</dd>
          </div>
        </div>

        <p className="payslip-words">{payslip.amountInWords}</p>

        <div className="payslip-columns">
          <div>
            <h4>Year to date</h4>
            <div className="payslip-line">
              <span>YTD gross</span>
              <span>{formatCurrency(payslip.ytdGross, "INR")}</span>
            </div>
            <div className="payslip-line">
              <span>YTD tax deducted</span>
              <span>{formatCurrency(payslip.ytdTax, "INR")}</span>
            </div>
          </div>
          <div>
            <h4>Applicable tax information</h4>
            <div className="payslip-line">
              <span>Regime</span>
              <span>Not yet configured</span>
            </div>
            <div className="payslip-line">
              <span>Tax deducted this period</span>
              <span>
                {formatCurrency(
                  payslip.deductions.find((item) => item.label.includes("Tax"))?.amount ?? 0,
                  "INR",
                )}
              </span>
            </div>
          </div>
        </div>

        <div className="payslip-footer">
          <span>This is a system-generated payslip preview and does not represent an actual payment.</span>
          <span>Grow Digital Bridge &middot; Confidential</span>
        </div>
      </div>
    </div>
  );
}
