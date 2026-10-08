import { apiClient, ApiError } from "@/lib/api/client";
import type { PageResponse, PayrollPeriod, PayrollRunCostSummary, PayrollRunStatus } from "@/lib/api/types";
import { AuthRequiredNotice } from "@/components/AuthRequiredNotice";
import { PermissionDeniedNotice } from "@/components/PermissionDeniedNotice";
import { PageHeader } from "@/components/PageHeader";
import { UnavailableNotice } from "@/components/UnavailableNotice";
import { StatCard } from "@/components/StatCard";
import { StatusBadge } from "@/components/StatusBadge";
import { EmptyState } from "@/components/EmptyState";
import { formatCurrency, formatPeriodLabel, titleCase } from "@/lib/format";

interface PayrollSummarySearchParams {
  periodId?: string;
  status?: string;
}

const STATUS_OPTIONS: PayrollRunStatus[] = [
  "DRAFT",
  "PROCESSING",
  "CALCULATED",
  "CALCULATION_FAILED",
  "PENDING_APPROVAL",
  "APPROVED",
  "FINALIZED",
  "REJECTED",
  "CANCELLED",
];

async function safe<T>(promise: Promise<T>): Promise<T | null> {
  try {
    return await promise;
  } catch {
    return null;
  }
}

function FilterForm({
  periods,
  selectedPeriodId,
  selectedStatus,
}: {
  periods: PayrollPeriod[];
  selectedPeriodId: string;
  selectedStatus: string;
}) {
  return (
    <form method="get" role="search" aria-label="Filter payroll cost summary" className="form-row inline-form">
      <div>
        <label htmlFor="periodId">Payroll period</label>
        <select id="periodId" name="periodId" defaultValue={selectedPeriodId}>
          <option value="">All periods</option>
          {periods.map((period) => (
            <option key={period.id} value={period.id}>
              {formatPeriodLabel(period.year, period.month)}
            </option>
          ))}
        </select>
      </div>
      <div>
        <label htmlFor="status">Run status</label>
        <select id="status" name="status" defaultValue={selectedStatus}>
          {STATUS_OPTIONS.map((status) => (
            <option key={status} value={status}>
              {titleCase(status)}
            </option>
          ))}
        </select>
      </div>
      <button type="submit" className="btn btn-primary">
        Apply filters
      </button>
    </form>
  );
}

/**
 * Finance-facing report (Reporting V1, docs/REPORTING_V1_REQUIREMENTS.md Section D3). Composes
 * Payroll Service only - `GET /payroll/runs/cost-summary`, a dedicated reporting endpoint added
 * for this report (not a reuse of the general `GET /payroll/runs` contract).
 *
 * Authorization: this endpoint is gated by the existing flat `payroll.read.all` matcher (no
 * self/team tier exists for payroll runs at all - confirmed in SecurityConfig) - so, unlike
 * D1/D4, a 403 alone is already an unambiguous "not Finance" signal; no additional `page.scope`
 * check is needed or present on this response.
 *
 * REGULAR and ADJUSTMENT runs are never merged - each FINALIZED (or explicitly filtered) run is
 * its own row, so a period with a correction applied shows two rows rather than one netted total
 * (GDB decision, D3 Phase 1 review). There is deliberately no "Total Payroll Cost" metric: no
 * formula for it is documented anywhere in this codebase.
 */
export default async function PayrollSummaryPage({
  searchParams,
}: {
  searchParams: Promise<PayrollSummarySearchParams>;
}) {
  const params = await searchParams;
  const periodId = params.periodId?.trim() ?? "";
  const status = params.status?.trim() ?? "FINALIZED";

  const query = new URLSearchParams();
  if (periodId) query.set("periodId", periodId);
  query.set("status", status);
  query.set("size", "100");

  let runs: PageResponse<PayrollRunCostSummary>;
  try {
    runs = await apiClient.get<PageResponse<PayrollRunCostSummary>>(`/api/v1/payroll/runs/cost-summary?${query.toString()}`);
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return (
        <section aria-labelledby="payroll-summary-heading">
          <PageHeader title="Payroll Cost Summary" />
          <AuthRequiredNotice />
        </section>
      );
    }
    if (error instanceof ApiError && error.status === 403) {
      return (
        <section aria-labelledby="payroll-summary-heading">
          <PageHeader title="Payroll Cost Summary" description="Finance view of payroll cost by run." />
          <PermissionDeniedNotice message="Payroll Cost Summary requires organization-wide payroll read access. Contact Finance if you believe this is incorrect." />
        </section>
      );
    }
    return (
      <section aria-labelledby="payroll-summary-heading">
        <PageHeader title="Payroll Cost Summary" />
        <UnavailableNotice
          title="Payroll cost summary could not be loaded"
          message="Something went wrong while loading payroll data. Please try again shortly."
        />
      </section>
    );
  }

  const periodsResult = await safe(apiClient.get<PayrollPeriod[]>("/api/v1/payroll/periods"));
  const periods = periodsResult ?? [];

  const totals = runs.items.reduce(
    (acc, run) => ({
      grossPay: acc.grossPay + run.totalGrossPay,
      deductions: acc.deductions + run.totalDeductions,
      employerContributions: acc.employerContributions + run.totalEmployerContributions,
      netPay: acc.netPay + run.totalNetPay,
    }),
    { grossPay: 0, deductions: 0, employerContributions: 0, netPay: 0 },
  );

  return (
    <section aria-labelledby="payroll-summary-heading">
      <PageHeader title="Payroll Cost Summary" description="Gross pay, deductions, employer contributions, and net pay by payroll run." />

      <div className="card" style={{ marginBottom: "1.25rem" }}>
        <FilterForm periods={periods} selectedPeriodId={periodId} selectedStatus={status} />
      </div>

      {runs.items.length === 0 ? (
        <EmptyState message="No payroll runs match this filter." icon="payroll" />
      ) : (
        <>
          <div className="card-grid grid-2">
            <StatCard label="Gross pay" value={formatCurrency(totals.grossPay, "INR")} icon="payroll" />
            <StatCard label="Employee deductions" value={formatCurrency(totals.deductions, "INR")} icon="payroll" />
            <StatCard label="Employer contributions" value={formatCurrency(totals.employerContributions, "INR")} icon="payroll" />
            <StatCard label="Net pay" value={formatCurrency(totals.netPay, "INR")} icon="payroll" />
          </div>

          <div className="card" style={{ marginTop: "1.25rem" }}>
            <div className="card-header">
              <h2>By run</h2>
            </div>
            <table className="data-table">
              <thead>
                <tr>
                  <th>Period</th>
                  <th>Run type</th>
                  <th>Status</th>
                  <th>Employees</th>
                  <th>Gross pay</th>
                  <th>Deductions</th>
                  <th>Employer contributions</th>
                  <th>Net pay</th>
                </tr>
              </thead>
              <tbody>
                {runs.items.map((run) => (
                  <tr key={run.runId}>
                    <td>{formatPeriodLabel(run.periodYear, run.periodMonth)}</td>
                    <td>{titleCase(run.runType)}{run.correctsRunId && " (correction)"}</td>
                    <td>
                      <StatusBadge status={run.status} />
                    </td>
                    <td>{run.employeeCount}</td>
                    <td>{formatCurrency(run.totalGrossPay, "INR")}</td>
                    <td>{formatCurrency(run.totalDeductions, "INR")}</td>
                    <td>{formatCurrency(run.totalEmployerContributions, "INR")}</td>
                    <td>{formatCurrency(run.totalNetPay, "INR")}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {runs.page.total > runs.items.length && (
            <div className="notice notice-neutral" style={{ marginTop: "1.25rem" }}>
              <h2>Showing a partial result</h2>
              <p>
                {runs.page.total} runs match this filter, but only the first {runs.items.length} are summarized
                here. Narrow the filter for a complete total.
              </p>
            </div>
          )}

          <div className="notice notice-neutral" style={{ marginTop: "1.25rem" }}>
            <h2>About this report</h2>
            <p>
              REGULAR and ADJUSTMENT runs for the same period are shown as separate rows, never merged into one
              total - a correction run&apos;s figures are its own, not netted against the run it corrects. There is
              no combined &quot;total payroll cost&quot; figure: no such formula is documented for this system. See{" "}
              <code>docs/REPORTING_V1_REQUIREMENTS.md</code> (Section D3) for the full detail.
            </p>
          </div>
        </>
      )}
    </section>
  );
}
