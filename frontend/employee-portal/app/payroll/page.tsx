import Link from "next/link";
import { apiClient, ApiError } from "@/lib/api/client";
import type { PayslipDetail, PayslipSummary, PageResponse } from "@/lib/api/types";
import { AuthRequiredNotice } from "@/components/AuthRequiredNotice";
import { PermissionDeniedNotice } from "@/components/PermissionDeniedNotice";
import { EmptyState } from "@/components/EmptyState";
import { Pagination } from "@/components/Pagination";
import { PageHeader } from "@/components/PageHeader";
import { StatCard } from "@/components/StatCard";
import { PayslipDocument } from "@/components/PayslipDocument";
import { DownloadPayslipButton } from "./DownloadPayslipButton";
import { formatCurrency, formatDate, formatPeriodLabel } from "@/lib/format";

interface PayrollSearchParams {
  page?: string;
  financialYearStart?: string;
}

function FinancialYearFilter({ selected }: { selected: string }) {
  const currentYear = new Date().getFullYear();
  const options = [currentYear, currentYear - 1, currentYear - 2];

  return (
    <form method="get" className="form-row inline-form">
      <div>
        <label htmlFor="financialYearStart">Financial year</label>
        <select id="financialYearStart" name="financialYearStart" defaultValue={selected}>
          <option value="">All</option>
          {options.map((year) => (
            <option key={year} value={year}>
              FY {year}-{String(year + 1).slice(-2)}
            </option>
          ))}
        </select>
      </div>
      <button type="submit" className="btn btn-primary">
        Apply
      </button>
    </form>
  );
}

export default async function PayrollPage({ searchParams }: { searchParams: Promise<PayrollSearchParams> }) {
  const params = await searchParams;
  const page = Number.parseInt(params.page ?? "0", 10) || 0;
  const financialYearStart = params.financialYearStart?.trim() ?? "";

  const query = new URLSearchParams();
  if (financialYearStart) query.set("financialYearStart", financialYearStart);
  query.set("page", String(page));
  query.set("size", "10");
  query.set("sort", "generatedAt,desc");

  let payslips: PageResponse<PayslipSummary>;
  try {
    payslips = await apiClient.get<PageResponse<PayslipSummary>>(`/api/v1/payroll/payslips/me?${query.toString()}`);
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return (
        <section aria-labelledby="payroll-heading">
          <PageHeader title="Payroll" />
          <AuthRequiredNotice />
        </section>
      );
    }
    if (error instanceof ApiError && error.status === 403) {
      return (
        <section aria-labelledby="payroll-heading">
          <PageHeader title="Payroll" />
          <PermissionDeniedNotice message="You need payslip access to view your payroll information." />
        </section>
      );
    }
    throw error;
  }

  let latest: PayslipDetail | null = null;
  if (payslips.items.length > 0) {
    try {
      latest = await apiClient.get<PayslipDetail>(`/api/v1/payroll/payslips/${payslips.items[0].id}`);
    } catch {
      latest = null;
    }
  }

  return (
    <section aria-labelledby="payroll-heading">
      <PageHeader title="Payroll" description="Your payslips and payroll summary." />

      <div className="card-grid grid-3">
        <StatCard
          label="Latest net pay"
          value={latest ? formatCurrency(latest.netPay, "INR") : "—"}
          hint={latest ? formatPeriodLabel(latest.periodYear, latest.periodMonth) : undefined}
          icon="payroll"
        />
        <StatCard
          label="YTD gross"
          value={latest?.ytd.available ? formatCurrency(latest.ytd.grossPay, "INR") : "Not available"}
          icon="payroll"
        />
        <StatCard
          label="YTD deductions"
          value={latest?.ytd.available ? formatCurrency(latest.ytd.totalDeductions, "INR") : "Not available"}
          icon="payroll"
        />
      </div>

      <div className="card" style={{ marginTop: "1.25rem" }}>
        <div className="card-header">
          <h2>Filters</h2>
        </div>
        <FinancialYearFilter selected={financialYearStart} />
      </div>

      {latest && (
        <div className="card" style={{ marginTop: "1.25rem" }}>
          <div className="card-header">
            <h2>Latest payslip</h2>
          </div>
          <PayslipDocument payslip={latest} actions={<DownloadPayslipButton payslipId={latest.id} />} />
        </div>
      )}

      <div className="card" style={{ marginTop: "1.25rem" }}>
        <div className="card-header">
          <h2>Payslip history</h2>
        </div>
        {payslips.items.length === 0 ? (
          <EmptyState message="No payslips have been generated for you yet." icon="payroll" />
        ) : (
          <>
            <table className="data-table">
              <thead>
                <tr>
                  <th>Period</th>
                  <th>Generated</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {payslips.items.map((entry) => (
                  <tr key={entry.id}>
                    <td>{formatPeriodLabel(entry.periodYear, entry.periodMonth)}</td>
                    <td>{formatDate(entry.generatedAt)}</td>
                    <td>
                      <Link href={`/payroll/${entry.id}`} className="btn btn-ghost btn-sm">
                        View
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <Pagination
              basePath="/payroll"
              currentPage={payslips.page.number}
              pageSize={payslips.page.size}
              total={payslips.page.total}
              extraParams={{ financialYearStart }}
            />
          </>
        )}
      </div>
    </section>
  );
}
