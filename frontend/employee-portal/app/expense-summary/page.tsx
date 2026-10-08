import { apiClient, ApiError } from "@/lib/api/client";
import type { ExpenseClaim, PageResponse } from "@/lib/api/types";
import { AuthRequiredNotice } from "@/components/AuthRequiredNotice";
import { PermissionDeniedNotice } from "@/components/PermissionDeniedNotice";
import { PageHeader } from "@/components/PageHeader";
import { UnavailableNotice } from "@/components/UnavailableNotice";
import { StatCard } from "@/components/StatCard";
import { EmptyState } from "@/components/EmptyState";
import { formatCurrency } from "@/lib/format";

interface ExpenseSummarySearchParams {
  from?: string;
  to?: string;
}

function isoDate(date: Date): string {
  return date.toLocaleDateString("en-CA");
}

function FilterForm({ from, to }: { from: string; to: string }) {
  return (
    <form method="get" role="search" aria-label="Filter expense claims by date" className="search-form">
      <div>
        <label htmlFor="from">From</label>
        <input id="from" name="from" type="date" defaultValue={from} />
      </div>
      <div>
        <label htmlFor="to">To</label>
        <input id="to" name="to" type="date" defaultValue={to} />
      </div>
      <button type="submit" className="btn btn-primary">
        Apply filters
      </button>
    </form>
  );
}

interface CurrencyTotals {
  submittedCount: number;
  submittedTotal: number;
  approvedCount: number;
  approvedTotal: number;
  reimbursedCount: number;
  reimbursedTotal: number;
  rejectedCount: number;
}

function emptyTotals(): CurrencyTotals {
  return {
    submittedCount: 0,
    submittedTotal: 0,
    approvedCount: 0,
    approvedTotal: 0,
    reimbursedCount: 0,
    reimbursedTotal: 0,
    rejectedCount: 0,
  };
}

/**
 * Finance-facing report (Reporting V1, docs/REPORTING_V1_REQUIREMENTS.md Section D4). Composes
 * Expense Service only - no Employee Service call, since D4's metrics are aggregate totals/counts
 * (per status/currency/date-range), never per-employee, so there is no name to resolve.
 *
 * Authorization: `GET /expenses/claims` (no `employeeId`) 401s if unauthenticated.
 * `ExpenseAccessGuard.resolveListScope` grants `expense.read.self` equal standing with
 * `.team`/`.all` - confirmed directly in `ExpenseAccessGuard`/`SecurityConfig` - so this endpoint
 * never 403s a plain employee; it simply returns their own claims. As of Reporting V1
 * authorization review Part A (docs/REPORTING_AUTHORIZATION_REVIEW.md), the response also carries
 * `page.scope` ("SELF"/"TEAM"/"ALL"), taken directly from that same guard decision - computed
 * before the query runs, so it is correct even when zero claims match (the exact case that made
 * a content-based heuristic unsafe to use here previously). This page now requires
 * `scope === "ALL"` before rendering, closing the gap the D4 implementation note originally
 * flagged, without ever accepting a client-supplied employee/team identifier.
 */
export default async function ExpenseSummaryPage({
  searchParams,
}: {
  searchParams: Promise<ExpenseSummarySearchParams>;
}) {
  const params = await searchParams;
  const now = new Date();
  const defaultFrom = isoDate(new Date(now.getFullYear(), 0, 1));
  const defaultTo = isoDate(new Date(now.getFullYear(), 11, 31));
  const from = params.from ?? defaultFrom;
  const to = params.to ?? defaultTo;

  let claims: PageResponse<ExpenseClaim>;
  try {
    claims = await apiClient.get<PageResponse<ExpenseClaim>>(
      `/api/v1/expenses/claims?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}&size=200`,
    );
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return (
        <section aria-labelledby="expense-summary-heading">
          <PageHeader title="Expense Summary" />
          <AuthRequiredNotice />
        </section>
      );
    }
    if (error instanceof ApiError && error.status === 403) {
      return (
        <section aria-labelledby="expense-summary-heading">
          <PageHeader title="Expense Summary" description="Finance view of claimed, approved, and reimbursed expenses." />
          <PermissionDeniedNotice message="Expense Summary requires expense read access. Contact Finance if you believe this is incorrect." />
        </section>
      );
    }
    return (
      <section aria-labelledby="expense-summary-heading">
        <PageHeader title="Expense Summary" />
        <UnavailableNotice
          title="Expense summary could not be loaded"
          message="Something went wrong while loading expense data. Please try again shortly."
        />
      </section>
    );
  }

  // Fail closed: SELF (any employee's own claims) and TEAM (a Manager's own reports) are both
  // legitimate, real 200 responses from this endpoint, but neither is Finance-wide access - only
  // "ALL" satisfies this report. Missing/unrecognized scope is also treated as not authorized.
  if (claims.page.scope !== "ALL") {
    return (
      <section aria-labelledby="expense-summary-heading">
        <PageHeader title="Expense Summary" description="Finance view of claimed, approved, and reimbursed expenses." />
        <PermissionDeniedNotice message="Expense Summary requires organization-wide expense read access. Contact Finance if you believe this is incorrect." />
      </section>
    );
  }

  const totalsByCurrency = new Map<string, CurrencyTotals>();
  for (const claim of claims.items) {
    const totals = totalsByCurrency.get(claim.currency) ?? emptyTotals();
    if (claim.status === "SUBMITTED") {
      totals.submittedCount += 1;
      totals.submittedTotal += claim.total;
    } else if (claim.status === "APPROVED") {
      totals.approvedCount += 1;
      totals.approvedTotal += claim.total;
    } else if (claim.status === "REIMBURSED") {
      totals.reimbursedCount += 1;
      totals.reimbursedTotal += claim.total;
    } else if (claim.status === "REJECTED") {
      totals.rejectedCount += 1;
    }
    totalsByCurrency.set(claim.currency, totals);
  }

  const rejectedCount = claims.items.filter((claim) => claim.status === "REJECTED").length;
  const truncated = claims.page.total > claims.items.length;

  return (
    <section aria-labelledby="expense-summary-heading">
      <PageHeader title="Expense Summary" description="Claimed, approved, and reimbursed expense totals by currency, within a date range." />

      <div className="card" style={{ marginBottom: "1.25rem" }}>
        <FilterForm from={from} to={to} />
      </div>

      {claims.items.length === 0 ? (
        <EmptyState message={`No expense claims found between ${from} and ${to}.`} icon="expenses" />
      ) : (
        <>
          <div className="card-grid grid-2">
            <StatCard label="Claims in range" value={String(claims.items.length)} hint={`${from} to ${to}`} icon="expenses" />
            <StatCard label="Rejected" value={String(rejectedCount)} icon="alertTriangle" />
          </div>

          <div className="card" style={{ marginTop: "1.25rem" }}>
            <div className="card-header">
              <h2>By currency</h2>
            </div>
            <table className="data-table">
              <thead>
                <tr>
                  <th>Currency</th>
                  <th>Submitted</th>
                  <th>Approved</th>
                  <th>Reimbursed</th>
                  <th>Rejected</th>
                </tr>
              </thead>
              <tbody>
                {Array.from(totalsByCurrency.entries()).map(([currency, totals]) => (
                  <tr key={currency}>
                    <td>{currency}</td>
                    <td>
                      {formatCurrency(totals.submittedTotal, currency)} ({totals.submittedCount})
                    </td>
                    <td>
                      {formatCurrency(totals.approvedTotal, currency)} ({totals.approvedCount})
                    </td>
                    <td>
                      {formatCurrency(totals.reimbursedTotal, currency)} ({totals.reimbursedCount})
                    </td>
                    <td>{totals.rejectedCount}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {truncated && (
            <div className="notice notice-neutral" style={{ marginTop: "1.25rem" }}>
              <h2>Showing a partial result</h2>
              <p>
                {claims.page.total} claims match this date range, but only the first {claims.items.length} are
                summarized here. Narrow the date range for a complete total.
              </p>
            </div>
          )}
        </>
      )}
    </section>
  );
}
