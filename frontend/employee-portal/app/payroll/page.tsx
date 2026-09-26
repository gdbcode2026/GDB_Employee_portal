import { PageHeader } from "@/components/PageHeader";
import { StatCard } from "@/components/StatCard";
import { DemoDataBanner } from "@/components/DemoDataBanner";
import { UnavailableNotice } from "@/components/UnavailableNotice";
import { StatusBadge } from "@/components/StatusBadge";
import { PayslipDocument } from "@/components/PayslipDocument";
import { DEMO_PAYSLIP, DEMO_PAYSLIP_HISTORY } from "@/lib/demoPayslip";
import { formatCurrency } from "@/lib/format";

export default function PayrollPage() {
  const grossPay = DEMO_PAYSLIP.earnings.reduce((sum, item) => sum + item.amount, 0);
  const totalDeductions = DEMO_PAYSLIP.deductions.reduce((sum, item) => sum + item.amount, 0);
  const netPay = grossPay - totalDeductions;

  return (
    <section aria-labelledby="payroll-heading">
      <PageHeader title="Payroll" description="Your payslips and payroll summary." />

      <UnavailableNotice
        title="Payroll Service is not yet implemented"
        message="Payroll requirements have been fully specified in docs/PAYROLL_REQUIREMENTS.md, but no Payroll Service backend exists yet. The layout below previews how your real payslips will appear once it ships."
      />

      <DemoDataBanner message="Everything below this line is DEMO DATA for layout preview only. No real salary, tax, or payment information is shown." />

      <div className="card-grid grid-3">
        <StatCard label="Latest net pay" value={formatCurrency(netPay, "INR")} hint={DEMO_PAYSLIP.payPeriod} icon="payroll" />
        <StatCard label="YTD gross" value={formatCurrency(DEMO_PAYSLIP.ytdGross, "INR")} icon="payroll" />
        <StatCard label="YTD tax deducted" value={formatCurrency(DEMO_PAYSLIP.ytdTax, "INR")} icon="payroll" />
      </div>

      <div className="card" style={{ marginTop: "1.25rem" }}>
        <div className="card-header">
          <h2>Filters</h2>
        </div>
        <form className="form-row inline-form" aria-disabled="true">
          <div>
            <label htmlFor="financialYear">Financial year</label>
            <select id="financialYear" name="financialYear" disabled defaultValue={DEMO_PAYSLIP.financialYear}>
              <option>{DEMO_PAYSLIP.financialYear}</option>
            </select>
          </div>
          <div>
            <label htmlFor="payPeriod">Pay period</label>
            <select id="payPeriod" name="payPeriod" disabled defaultValue={DEMO_PAYSLIP.payPeriod}>
              <option>{DEMO_PAYSLIP.payPeriod}</option>
            </select>
          </div>
        </form>
        <p className="muted" style={{ marginTop: "0.5rem" }}>
          Filters will become interactive once payslip data is available from the Payroll Service.
        </p>
      </div>

      <div className="card" style={{ marginTop: "1.25rem" }}>
        <div className="card-header">
          <h2>Latest payslip</h2>
          <span className="badge badge-demo">Demo data</span>
        </div>
        <PayslipDocument payslip={DEMO_PAYSLIP} />
      </div>

      <div className="card" style={{ marginTop: "1.25rem" }}>
        <div className="card-header">
          <h2>Payslip history</h2>
          <span className="badge badge-demo">Demo data</span>
        </div>
        <table className="data-table">
          <thead>
            <tr>
              <th>Period</th>
              <th>Net pay</th>
              <th>Status</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {DEMO_PAYSLIP_HISTORY.map((entry) => (
              <tr key={entry.period}>
                <td>{entry.period}</td>
                <td>{formatCurrency(entry.netPay, "INR")}</td>
                <td>
                  <StatusBadge status={entry.status} />
                </td>
                <td>
                  <button type="button" className="btn btn-ghost btn-sm" disabled>
                    View
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
