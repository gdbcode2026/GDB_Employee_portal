import { apiClient, ApiError } from "@/lib/api/client";
import type { EmployeeSummary, LeaveBalance, LeaveType, PageResponse } from "@/lib/api/types";
import { AuthRequiredNotice } from "@/components/AuthRequiredNotice";
import { PermissionDeniedNotice } from "@/components/PermissionDeniedNotice";
import { PageHeader } from "@/components/PageHeader";
import { UnavailableNotice } from "@/components/UnavailableNotice";
import { StatCard } from "@/components/StatCard";
import { EmptyState } from "@/components/EmptyState";

async function safe<T>(promise: Promise<T>): Promise<T | null> {
  try {
    return await promise;
  } catch {
    return null;
  }
}

function days(value: number): string {
  return `${value} day${value === 1 ? "" : "s"}`;
}

interface LeaveSummarySearchParams {
  leaveTypeId?: string;
  year?: string;
  status?: string;
}

function FilterForm({
  leaveTypes,
  years,
  leaveTypeId,
  year,
  status,
}: {
  leaveTypes: LeaveType[];
  years: number[];
  leaveTypeId: string;
  year: string;
  status: string;
}) {
  return (
    <form method="get" role="search" aria-label="Filter leave balances" className="search-form">
      <div>
        <label htmlFor="leaveTypeId">Leave type</label>
        <select id="leaveTypeId" name="leaveTypeId" defaultValue={leaveTypeId}>
          <option value="">All leave types</option>
          {leaveTypes.map((type) => (
            <option key={type.id} value={type.id}>
              {type.name}
            </option>
          ))}
        </select>
      </div>
      <div>
        <label htmlFor="year">Period year</label>
        <select id="year" name="year" defaultValue={year}>
          {years.map((y) => (
            <option key={y} value={y}>
              {y}
            </option>
          ))}
        </select>
      </div>
      <div>
        <label htmlFor="status">Employee status</label>
        <select id="status" name="status" defaultValue={status}>
          <option value="ACTIVE">Active</option>
          <option value="INACTIVE">Inactive</option>
        </select>
      </div>
      <button type="submit" className="btn btn-primary">
        Apply filters
      </button>
    </form>
  );
}

/**
 * HR (org-wide) / Manager (own team) report (Reporting V1, docs/REPORTING_V1_REQUIREMENTS.md
 * Section D2). Composes existing Leave + Employee APIs exactly as Team Overview (D5) already
 * does - no reporting backend, no new metric, no client-supplied employee/team identifier.
 *
 * Authorization gate: identical technique to Team Overview. `GET /employees` (no
 * departmentId/teamId parameter exists) 401s if unauthenticated and 403s for any caller without
 * `employee.read.team`/`.all` (plain employees and Team Leads included, per RBAC.md) - both HR
 * and Manager hold this alongside their own `leave.read.team`/`.all` grant (RBAC.md bundles
 * them per role), so gating on Employee's own authorization reliably predicts Leave's. The
 * actual leave data itself is still independently scoped by Leave Service's own
 * `LeaveAccessGuard.resolveListScope` on every call below - this page never overrides it.
 *
 * Data source is Leave Service's `GET /leave/balances` ONLY (no `employeeId` - same
 * team/all auto-resolution as Team Overview's attendance/leave/expense calls). This single
 * endpoint already carries every metric D2 asks for: `reserved` is, per LeaveBalance's own
 * Javadoc, "units held by SUBMITTED requests (not yet decided)" and `used` is "units consumed by
 * APPROVED requests" - i.e. the exact "current balance" and "utilization" figures, with no need
 * to separately call/aggregate `GET /leave/requests`. See the doc's D2 "Implementation note" for
 * the full reasoning, including why the known `LeaveRequestStatus` frontend/backend status
 * mismatch does not need correcting for this report.
 */
export default async function LeaveSummaryPage({ searchParams }: { searchParams: Promise<LeaveSummarySearchParams> }) {
  const params = await searchParams;
  const statusFilter = params.status === "INACTIVE" ? "INACTIVE" : "ACTIVE";

  let roster: PageResponse<EmployeeSummary>;
  try {
    roster = await apiClient.get<PageResponse<EmployeeSummary>>(`/api/v1/employees?status=${statusFilter}&size=200`);
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return (
        <section aria-labelledby="leave-summary-heading">
          <PageHeader title="Leave Balance & Utilization Summary" />
          <AuthRequiredNotice />
        </section>
      );
    }
    if (error instanceof ApiError && error.status === 403) {
      return (
        <section aria-labelledby="leave-summary-heading">
          <PageHeader title="Leave Balance & Utilization Summary" description="HR and manager view of leave balances and utilization." />
          <PermissionDeniedNotice message="Leave Balance & Utilization Summary is available to HR and to managers with team-level leave access. Contact HR if you believe this is incorrect." />
        </section>
      );
    }
    return (
      <section aria-labelledby="leave-summary-heading">
        <PageHeader title="Leave Balance & Utilization Summary" />
        <UnavailableNotice
          title="Leave summary could not be loaded"
          message="Something went wrong while loading leave data. Please try again shortly."
        />
      </section>
    );
  }

  const [balances, leaveTypes] = await Promise.all([
    safe(apiClient.get<LeaveBalance[]>("/api/v1/leave/balances")),
    safe(apiClient.get<LeaveType[]>("/api/v1/leave/types")),
  ]);

  const rosterIds = new Set(roster.items.map((member) => member.id));
  const nameByEmployeeRef = new Map(roster.items.map((member) => [member.id, `${member.firstName} ${member.lastName}`]));
  const leaveTypeNameById = new Map((leaveTypes ?? []).map((type) => [type.id, type.name]));

  // Defensive intersection with the roster's own scope (both Employee's and Leave's team
  // resolution independently call Organization for the same caller, so this should already be a
  // no-op) - never widens what the backend already authorized, only ever narrows for display.
  const scopedBalances = (balances ?? []).filter((balance) => rosterIds.has(balance.employeeRef));

  const availableYears = Array.from(new Set(scopedBalances.map((balance) => balance.periodYear))).sort((a, b) => b - a);
  const requestedYear = params.year ? Number.parseInt(params.year, 10) : NaN;
  const selectedYear = availableYears.includes(requestedYear) ? requestedYear : availableYears[0] ?? new Date().getFullYear();
  const yearsForFilter = availableYears.length > 0 ? availableYears : [selectedYear];

  const yearBalances = scopedBalances.filter((balance) => balance.periodYear === selectedYear);
  const leaveTypeFilter = params.leaveTypeId ?? "";
  const displayedBalances = leaveTypeFilter
    ? yearBalances.filter((balance) => balance.leaveTypeId === leaveTypeFilter)
    : yearBalances;

  const totals = yearBalances.reduce(
    (acc, balance) => ({
      allocated: acc.allocated + balance.allocated,
      used: acc.used + balance.used,
      reserved: acc.reserved + balance.reserved,
      available: acc.available + balance.available,
    }),
    { allocated: 0, used: 0, reserved: 0, available: 0 },
  );

  const perLeaveType = new Map<
    string,
    { allocated: number; used: number; reserved: number; available: number; employeeCount: number }
  >();
  for (const balance of yearBalances) {
    const existing = perLeaveType.get(balance.leaveTypeId) ?? {
      allocated: 0,
      used: 0,
      reserved: 0,
      available: 0,
      employeeCount: 0,
    };
    existing.allocated += balance.allocated;
    existing.used += balance.used;
    existing.reserved += balance.reserved;
    existing.available += balance.available;
    existing.employeeCount += 1;
    perLeaveType.set(balance.leaveTypeId, existing);
  }

  const sortedDisplayedBalances = [...displayedBalances].sort((a, b) => {
    const nameCompare = nameFor(a.employeeRef).localeCompare(nameFor(b.employeeRef));
    return nameCompare !== 0 ? nameCompare : leaveTypeNameFor(a.leaveTypeId).localeCompare(leaveTypeNameFor(b.leaveTypeId));
  });

  function nameFor(employeeRef: string): string {
    return nameByEmployeeRef.get(employeeRef) ?? "Former or unlisted team member";
  }

  function leaveTypeNameFor(leaveTypeId: string): string {
    return leaveTypeNameById.get(leaveTypeId) ?? "Unknown leave type";
  }

  return (
    <section aria-labelledby="leave-summary-heading">
      <PageHeader
        title="Leave Balance & Utilization Summary"
        description="Allocated, used, and pending leave for your team, by leave type and period year."
      />

      <div className="card" style={{ marginBottom: "1.25rem" }}>
        <FilterForm
          leaveTypes={leaveTypes ?? []}
          years={yearsForFilter}
          leaveTypeId={leaveTypeFilter}
          year={String(selectedYear)}
          status={statusFilter}
        />
      </div>

      {!balances ? (
        <UnavailableNotice
          title="Leave balance data is not available right now"
          message="Please try again shortly."
        />
      ) : yearBalances.length === 0 ? (
        <EmptyState message={`No leave balances recorded for ${selectedYear}.`} icon="leave" />
      ) : (
        <>
          <div className="card-grid grid-4">
            <StatCard label="Employees with balances" value={String(yearBalances.length)} hint={`Period year ${selectedYear}`} icon="organization" />
            <StatCard label="Total allocated" value={days(totals.allocated)} icon="leave" />
            <StatCard label="Total used" value={days(totals.used)} icon="checkCircle" />
            <StatCard label="Total pending" value={days(totals.reserved)} hint="Submitted, not yet decided" icon="clock" />
          </div>

          <div className="card" style={{ marginTop: "1.25rem" }}>
            <div className="card-header">
              <h2>By leave type ({selectedYear})</h2>
            </div>
            <table className="data-table">
              <thead>
                <tr>
                  <th>Leave type</th>
                  <th>Employees</th>
                  <th>Allocated</th>
                  <th>Used</th>
                  <th>Pending</th>
                  <th>Available</th>
                </tr>
              </thead>
              <tbody>
                {Array.from(perLeaveType.entries()).map(([leaveTypeId, totalsForType]) => (
                  <tr key={leaveTypeId}>
                    <td>{leaveTypeNameFor(leaveTypeId)}</td>
                    <td>{totalsForType.employeeCount}</td>
                    <td>{days(totalsForType.allocated)}</td>
                    <td>{days(totalsForType.used)}</td>
                    <td>{days(totalsForType.reserved)}</td>
                    <td>{days(totalsForType.available)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="card" style={{ marginTop: "1.25rem" }}>
            <div className="card-header">
              <h2>By employee</h2>
            </div>
            {sortedDisplayedBalances.length === 0 ? (
              <EmptyState message="No balances match the selected leave type." icon="leave" />
            ) : (
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Employee</th>
                    <th>Leave type</th>
                    <th>Allocated</th>
                    <th>Used</th>
                    <th>Pending</th>
                    <th>Available</th>
                  </tr>
                </thead>
                <tbody>
                  {sortedDisplayedBalances.map((balance) => (
                    <tr key={balance.id}>
                      <td>{nameFor(balance.employeeRef)}</td>
                      <td>{leaveTypeNameFor(balance.leaveTypeId)}</td>
                      <td>{days(balance.allocated)}</td>
                      <td>{days(balance.used)}</td>
                      <td>{days(balance.reserved)}</td>
                      <td>{days(balance.available)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </>
      )}
    </section>
  );
}
