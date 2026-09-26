import { apiClient, ApiError } from "@/lib/api/client";
import type { LeaveBalance, LeaveRequest, LeaveType, PageResponse } from "@/lib/api/types";
import { AuthRequiredNotice } from "@/components/AuthRequiredNotice";
import { PageHeader } from "@/components/PageHeader";
import { StatCard } from "@/components/StatCard";
import { StatusBadge } from "@/components/StatusBadge";
import { EmptyState } from "@/components/EmptyState";
import { formatDate } from "@/lib/format";
import { LeaveApplyForm } from "./LeaveApplyForm";
import { CancelLeaveButton } from "./CancelLeaveButton";

async function safe<T>(promise: Promise<T>): Promise<T | null> {
  try {
    return await promise;
  } catch {
    return null;
  }
}

export default async function LeavePage() {
  let balances: LeaveBalance[];
  try {
    balances = await apiClient.get<LeaveBalance[]>("/api/v1/leave/balances/me");
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return (
        <section aria-labelledby="leave-heading">
          <PageHeader title="Leave" />
          <AuthRequiredNotice />
        </section>
      );
    }
    throw error;
  }

  const [leaveTypes, requests] = await Promise.all([
    safe(apiClient.get<LeaveType[]>("/api/v1/leave/types")),
    safe(apiClient.get<PageResponse<LeaveRequest>>("/api/v1/leave/requests?size=15")),
  ]);

  const leaveTypeById = new Map((leaveTypes ?? []).map((type) => [type.id, type.name]));

  return (
    <section aria-labelledby="leave-heading">
      <PageHeader title="Leave" description="View your balances and manage leave requests." />

      <div className="card-grid grid-3">
        {balances.length === 0 ? (
          <div className="card">
            <EmptyState message="No leave balances have been allocated yet." icon="leave" />
          </div>
        ) : (
          balances.map((balance) => (
            <StatCard
              key={balance.id}
              label={leaveTypeById.get(balance.leaveTypeId) ?? "Leave balance"}
              value={`${balance.available} days`}
              hint={`${balance.used} used of ${balance.allocated} allocated`}
              icon="leave"
            />
          ))
        )}
      </div>

      <div className="card" style={{ marginTop: "1.25rem" }}>
        <div className="card-header">
          <h2>Your requests</h2>
        </div>
        {!requests || requests.items.length === 0 ? (
          <EmptyState message="No leave requests yet." icon="leave" />
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>Type</th>
                <th>Dates</th>
                <th>Units</th>
                <th>Status</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {requests.items.map((request) => (
                <tr key={request.id}>
                  <td>{leaveTypeById.get(request.leaveTypeId) ?? "Leave"}</td>
                  <td>
                    {formatDate(request.startDate)} &ndash; {formatDate(request.endDate)}
                  </td>
                  <td>{request.units}</td>
                  <td>
                    <StatusBadge status={request.status} />
                  </td>
                  <td>{request.status === "PENDING" && <CancelLeaveButton id={request.id} />}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div className="card" style={{ marginTop: "1.25rem" }}>
        <div className="card-header">
          <h2>Apply for leave</h2>
        </div>
        {!leaveTypes || leaveTypes.length === 0 ? (
          <EmptyState message="No leave types are configured yet." icon="leave" />
        ) : (
          <LeaveApplyForm leaveTypes={leaveTypes} />
        )}
      </div>
    </section>
  );
}
