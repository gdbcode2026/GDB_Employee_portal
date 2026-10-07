import { apiClient, ApiError } from "@/lib/api/client";
import type { AttendanceRecord, EmployeeSummary, ExpenseClaim, LeaveRequest, PageResponse } from "@/lib/api/types";
import { AuthRequiredNotice } from "@/components/AuthRequiredNotice";
import { PageHeader } from "@/components/PageHeader";
import { UnavailableNotice } from "@/components/UnavailableNotice";
import { StatCard } from "@/components/StatCard";
import { StatusBadge } from "@/components/StatusBadge";
import { EmptyState } from "@/components/EmptyState";
import { formatCurrency, formatDate, formatTime } from "@/lib/format";

async function safe<T>(promise: Promise<T>): Promise<T | null> {
  try {
    return await promise;
  } catch {
    return null;
  }
}

function todayIso(): string {
  return new Date().toLocaleDateString("en-CA");
}

/**
 * Manager-only team snapshot (Reporting V1, docs/REPORTING_V1_REQUIREMENTS.md Section D5).
 * Composes existing Attendance/Leave/Expense/Employee team-scoped APIs exactly as the personal
 * Dashboard (app/page.tsx) already composes self-scoped ones - there is no reporting backend,
 * no new metric, and no client-supplied team/manager identifier anywhere on this page. "Who is
 * on my team" and "am I even a manager" are both decided entirely server-side: `GET /employees`
 * (no departmentId/teamId parameter exists or is needed) returns 403 for any caller without
 * `employee.read.team`/`.all`, and otherwise returns exactly the caller's own team, resolved by
 * Employee Service calling Organization Service internally (EmployeeAccessGuard.resolveListScope).
 * The same self/team/all scope resolution - confirmed by reading AttendanceService,
 * LeaveRequestService, and ExpenseClaimService directly - applies identically to every other call
 * on this page.
 */
export default async function TeamOverviewPage() {
  let roster: PageResponse<EmployeeSummary>;
  try {
    roster = await apiClient.get<PageResponse<EmployeeSummary>>("/api/v1/employees?status=ACTIVE&size=200");
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return (
        <section aria-labelledby="team-overview-heading">
          <PageHeader title="Team Overview" />
          <AuthRequiredNotice />
        </section>
      );
    }
    if (error instanceof ApiError && error.status === 403) {
      return (
        <section aria-labelledby="team-overview-heading">
          <PageHeader title="Team Overview" description="A manager's snapshot of their own team." />
          <UnavailableNotice
            title="Managers only"
            message="Team Overview is available to employees with team-level read permissions (attendance, leave, and expense team access). Contact HR if you believe this is incorrect."
          />
        </section>
      );
    }
    return (
      <section aria-labelledby="team-overview-heading">
        <PageHeader title="Team Overview" />
        <UnavailableNotice
          title="Team Overview could not be loaded"
          message="Something went wrong while loading your team's data. Please try again shortly."
        />
      </section>
    );
  }

  const today = todayIso();
  const [attendance, pendingLeave, pendingExpenses] = await Promise.all([
    safe(apiClient.get<PageResponse<AttendanceRecord>>(`/api/v1/attendance?from=${today}&to=${today}&size=200`)),
    safe(apiClient.get<PageResponse<LeaveRequest>>("/api/v1/leave/requests?status=SUBMITTED&size=100")),
    safe(apiClient.get<PageResponse<ExpenseClaim>>("/api/v1/expenses/claims?status=SUBMITTED&size=100")),
  ]);

  const nameByEmployeeRef = new Map(roster.items.map((member) => [member.id, `${member.firstName} ${member.lastName}`]));
  function nameFor(employeeRef: string): string {
    return nameByEmployeeRef.get(employeeRef) ?? "Former or unlisted team member";
  }

  const attendanceByEmployee = new Map((attendance?.items ?? []).map((record) => [record.employeeRef, record]));
  const checkedInCount = roster.items.filter((member) => {
    const record = attendanceByEmployee.get(member.id);
    return Boolean(record?.checkInAt && !record?.checkOutAt);
  }).length;
  const checkedOutCount = roster.items.filter((member) => attendanceByEmployee.get(member.id)?.checkOutAt).length;
  const notCheckedInCount = roster.items.length - checkedInCount - checkedOutCount;

  return (
    <section aria-labelledby="team-overview-heading">
      <PageHeader title="Team Overview" description="A snapshot of your team's attendance, leave, and expense activity today." />

      <div className="card-grid grid-4">
        <StatCard label="Team size" value={String(roster.page.total)} hint="Active team members" icon="organization" />
        <StatCard
          label="Checked in today"
          value={attendance ? String(checkedInCount) : "Unavailable"}
          hint={attendance ? `${checkedOutCount} checked out · ${notCheckedInCount} not checked in` : undefined}
          icon="attendance"
        />
        <StatCard
          label="Pending leave requests"
          value={pendingLeave ? String(pendingLeave.items.length) : "Unavailable"}
          hint="Awaiting a decision"
          icon="leave"
        />
        <StatCard
          label="Pending expense claims"
          value={pendingExpenses ? String(pendingExpenses.items.length) : "Unavailable"}
          hint="Awaiting a decision"
          icon="expenses"
        />
      </div>

      <div className="card" style={{ marginTop: "1.25rem" }}>
        <div className="card-header">
          <h2>Today&apos;s attendance</h2>
        </div>
        {!attendance ? (
          <EmptyState message="Attendance data is not available right now." icon="attendance" />
        ) : roster.items.length === 0 ? (
          <EmptyState message="No active team members." icon="organization" />
        ) : (
          <ul style={{ listStyle: "none", padding: 0, margin: 0 }} aria-label="Team attendance today">
            {roster.items.map((member) => {
              const record = attendanceByEmployee.get(member.id);
              const label = record?.checkOutAt
                ? `Checked out ${formatTime(record.checkOutAt)}`
                : record?.checkInAt
                  ? `Checked in ${formatTime(record.checkInAt)}`
                  : "Not checked in";
              return (
                <li key={member.id} className="list-row">
                  <div className="list-row-main">
                    <div>
                      <div className="list-row-title">
                        {member.firstName} {member.lastName}
                      </div>
                      <div className="list-row-sub">{member.employeeNumber}</div>
                    </div>
                  </div>
                  <div className="list-row-end">{label}</div>
                </li>
              );
            })}
          </ul>
        )}
      </div>

      <div className="card-grid grid-2" style={{ marginTop: "1.25rem" }}>
        <div className="card">
          <div className="card-header">
            <h2>Pending leave requests</h2>
          </div>
          {!pendingLeave ? (
            <EmptyState message="Leave data is not available right now." icon="leave" />
          ) : pendingLeave.items.length === 0 ? (
            <EmptyState message="No pending leave requests." icon="leave" />
          ) : (
            <ul style={{ listStyle: "none", padding: 0, margin: 0 }} aria-label="Pending leave requests">
              {pendingLeave.items.map((request) => (
                <li key={request.id} className="list-row">
                  <div className="list-row-main">
                    <div>
                      <div className="list-row-title">{nameFor(request.employeeRef)}</div>
                      <div className="list-row-sub">
                        {formatDate(request.startDate)} &ndash; {formatDate(request.endDate)} &middot; {request.units} day(s)
                      </div>
                    </div>
                  </div>
                  <div className="list-row-end">
                    <StatusBadge status={request.status} />
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="card">
          <div className="card-header">
            <h2>Pending expense claims</h2>
          </div>
          {!pendingExpenses ? (
            <EmptyState message="Expense data is not available right now." icon="expenses" />
          ) : pendingExpenses.items.length === 0 ? (
            <EmptyState message="No pending expense claims." icon="expenses" />
          ) : (
            <ul style={{ listStyle: "none", padding: 0, margin: 0 }} aria-label="Pending expense claims">
              {pendingExpenses.items.map((claim) => (
                <li key={claim.id} className="list-row">
                  <div className="list-row-main">
                    <div>
                      <div className="list-row-title">{nameFor(claim.employeeRef)}</div>
                      <div className="list-row-sub">{formatCurrency(claim.total, claim.currency)}</div>
                    </div>
                  </div>
                  <div className="list-row-end">
                    <StatusBadge status={claim.status} />
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </section>
  );
}
