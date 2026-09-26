import { apiClient, ApiError } from "@/lib/api/client";
import type { ExpenseClaim, PageResponse } from "@/lib/api/types";
import { AuthRequiredNotice } from "@/components/AuthRequiredNotice";
import { PageHeader } from "@/components/PageHeader";
import { StatCard } from "@/components/StatCard";
import { StatusBadge } from "@/components/StatusBadge";
import { EmptyState } from "@/components/EmptyState";
import { formatCurrency, formatDate } from "@/lib/format";
import { ExpenseClaimForm } from "./ExpenseClaimForm";
import { ClaimActions } from "./ClaimActions";

export default async function ExpensesPage() {
  let claims: PageResponse<ExpenseClaim>;
  try {
    claims = await apiClient.get<PageResponse<ExpenseClaim>>("/api/v1/expenses/claims?size=20&sort=createdAt,desc");
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return (
        <section aria-labelledby="expenses-heading">
          <PageHeader title="Expenses" />
          <AuthRequiredNotice />
        </section>
      );
    }
    throw error;
  }

  const pendingTotal = claims.items
    .filter((claim) => claim.status === "SUBMITTED")
    .reduce((sum, claim) => sum + claim.total, 0);
  const reimbursedTotal = claims.items
    .filter((claim) => claim.status === "REIMBURSED")
    .reduce((sum, claim) => sum + claim.total, 0);
  const currency = claims.items[0]?.currency ?? "INR";

  return (
    <section aria-labelledby="expenses-heading">
      <PageHeader title="Expenses" description="Submit and track your reimbursement claims." />

      <div className="card-grid grid-3">
        <StatCard label="Total claims" value={String(claims.page.total)} icon="expenses" />
        <StatCard label="Awaiting decision" value={formatCurrency(pendingTotal, currency)} icon="clock" />
        <StatCard label="Reimbursed" value={formatCurrency(reimbursedTotal, currency)} icon="checkCircle" />
      </div>

      <div className="card" style={{ marginTop: "1.25rem" }}>
        <div className="card-header">
          <h2>Your claims</h2>
        </div>
        {claims.items.length === 0 ? (
          <EmptyState message="No expense claims yet." icon="expenses" />
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>Created</th>
                <th>Lines</th>
                <th>Total</th>
                <th>Status</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {claims.items.map((claim) => (
                <tr key={claim.id}>
                  <td>{formatDate(claim.createdAt)}</td>
                  <td>{claim.lines.length}</td>
                  <td>{formatCurrency(claim.total, claim.currency)}</td>
                  <td>
                    <StatusBadge status={claim.status} />
                  </td>
                  <td>
                    <ClaimActions id={claim.id} status={claim.status} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div className="card" style={{ marginTop: "1.25rem" }}>
        <div className="card-header">
          <h2>Submit a new claim</h2>
        </div>
        <ExpenseClaimForm />
      </div>
    </section>
  );
}
