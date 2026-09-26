import Link from "next/link";
import { apiClient, ApiError } from "@/lib/api/client";
import type {
  ApprovalTask,
  AttendanceRecord,
  EmployeeResponse,
  Goal,
  LeaveBalance,
  PageResponse,
} from "@/lib/api/types";
import { AuthRequiredNotice } from "@/components/AuthRequiredNotice";
import { PageHeader } from "@/components/PageHeader";
import { StatCard } from "@/components/StatCard";
import { StatusBadge } from "@/components/StatusBadge";
import { EmptyState } from "@/components/EmptyState";
import { CheckInOutActions } from "@/components/CheckInOutActions";
import { Icon } from "@/components/icons";
import { formatDate, formatTime } from "@/lib/format";

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

export default async function DashboardPage() {
  let employee: EmployeeResponse;
  try {
    employee = await apiClient.get<EmployeeResponse>("/api/v1/employees/me");
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return (
        <section aria-labelledby="dashboard-heading">
          <PageHeader title="Dashboard" />
          <AuthRequiredNotice />
        </section>
      );
    }
    if (error instanceof ApiError && error.status === 404) {
      return (
        <section aria-labelledby="dashboard-heading">
          <PageHeader title="Dashboard" />
          <div className="notice notice-neutral">
            <h2>No employee profile linked</h2>
            <p>No employee profile is linked to your account yet. Contact HR to get set up.</p>
          </div>
          <nav aria-label="Quick links" className="quick-links">
            <Link href="/directory">Employee directory</Link>
            <Link href="/organization">Organization</Link>
          </nav>
        </section>
      );
    }
    throw error;
  }

  const [attendance, balances, tasks, goals] = await Promise.all([
    safe(apiClient.get<PageResponse<AttendanceRecord>>("/api/v1/attendance/me?size=5&sort=workDate,desc")),
    safe(apiClient.get<LeaveBalance[]>("/api/v1/leave/balances/me")),
    safe(apiClient.get<PageResponse<ApprovalTask>>("/api/v1/workflows/tasks/me?size=5")),
    safe(apiClient.get<PageResponse<Goal>>("/api/v1/performance/goals?size=5")),
  ]);

  const today = todayIso();
  const todayRecord = attendance?.items.find((record) => record.workDate === today) ?? null;
  const canCheckOut = Boolean(todayRecord?.checkInAt && !todayRecord?.checkOutAt);
  const attendanceStatusLabel = !attendance
    ? "Unavailable"
    : todayRecord?.checkOutAt
      ? `Checked out ${formatTime(todayRecord.checkOutAt)}`
      : todayRecord?.checkInAt
        ? `Checked in ${formatTime(todayRecord.checkInAt)}`
        : "Not checked in";

  const totalLeaveAvailable = balances?.reduce((sum, balance) => sum + balance.available, 0) ?? null;
  const pendingApprovals = tasks?.items.filter((task) => task.status === "PENDING") ?? [];
  const openGoals = goals?.items.filter((goal) => goal.status === "OPEN" || goal.status === "IN_PROGRESS") ?? [];

  return (
    <section aria-labelledby="dashboard-heading">
      <PageHeader title="Dashboard" />

      <div className="hero">
        <h1 id="dashboard-heading">Welcome back, {employee.firstName}</h1>
        <p>
          {employee.employment?.jobTitle ?? "Employee"} &middot; {employee.employeeNumber}
        </p>
        <div className="hero-meta">
          <div className="item">
            <strong>{employee.status}</strong>
            Account status
          </div>
          {employee.employment && (
            <div className="item">
              <strong>{employee.employment.employmentType.replace("_", " ")}</strong>
              Employment type
            </div>
          )}
          <div className="item">
            <strong>{formatDate(employee.employment?.startDate)}</strong>
            Joined
          </div>
        </div>
      </div>

      <div className="card-grid grid-4" style={{ marginTop: "1.5rem" }}>
        <StatCard label="Today's attendance" value={attendanceStatusLabel} icon="attendance" />
        <StatCard
          label="Leave balance"
          value={totalLeaveAvailable !== null ? `${totalLeaveAvailable} days` : "Unavailable"}
          hint="Across all leave types"
          icon="leave"
        />
        <StatCard
          label="Pending approvals"
          value={tasks ? String(pendingApprovals.length) : "Unavailable"}
          hint="Awaiting your decision"
          icon="checkCircle"
        />
        <StatCard
          label="Active goals"
          value={goals ? String(openGoals.length) : "Unavailable"}
          hint="In progress or open"
          icon="target"
        />
      </div>

      <div className="card" style={{ marginTop: "1.25rem" }}>
        <div className="card-header">
          <h2>Quick actions</h2>
        </div>
        <div className="quick-actions">
          <CheckInOutActions canCheckOut={canCheckOut} />
          <Link href="/leave#apply" className="quick-action">
            <span className="stat-icon">
              <Icon name="leave" size={18} />
            </span>
            Apply Leave
          </Link>
          <Link href="/attendance#regularize" className="quick-action">
            <span className="stat-icon">
              <Icon name="calendar" size={18} />
            </span>
            Regularize Attendance
          </Link>
          <Link href="/payroll" className="quick-action">
            <span className="stat-icon">
              <Icon name="payroll" size={18} />
            </span>
            View Payslip
          </Link>
          <Link href="/expenses#new" className="quick-action">
            <span className="stat-icon">
              <Icon name="expenses" size={18} />
            </span>
            Submit Expense
          </Link>
          <Link href="/projects" className="quick-action">
            <span className="stat-icon">
              <Icon name="projects" size={18} />
            </span>
            View Tasks
          </Link>
        </div>
      </div>

      <div className="card-grid grid-2" style={{ marginTop: "1.25rem" }}>
        <div className="card">
          <div className="card-header">
            <h2>Recent attendance</h2>
            <Link href="/attendance" className="link">
              View all
            </Link>
          </div>
          {!attendance ? (
            <EmptyState message="Attendance data is not available right now." icon="attendance" />
          ) : attendance.items.length === 0 ? (
            <EmptyState message="No attendance records yet." icon="attendance" />
          ) : (
            <ul style={{ listStyle: "none", padding: 0, margin: 0 }}>
              {attendance.items.slice(0, 5).map((record) => (
                <li key={record.id} className="list-row">
                  <div className="list-row-main">
                    <span className="stat-icon">
                      <Icon name="calendar" size={16} />
                    </span>
                    <div>
                      <div className="list-row-title">{formatDate(record.workDate)}</div>
                      <div className="list-row-sub">
                        {formatTime(record.checkInAt)} &ndash; {formatTime(record.checkOutAt)}
                      </div>
                    </div>
                  </div>
                  <div className="list-row-end">
                    <StatusBadge status={record.status} />
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="card">
          <div className="card-header">
            <h2>Pending approvals</h2>
            <Link href="/projects" className="link">
              View all
            </Link>
          </div>
          {!tasks ? (
            <EmptyState message="Approval tasks are not available right now." icon="checkCircle" />
          ) : pendingApprovals.length === 0 ? (
            <EmptyState message="You have no pending approvals." icon="checkCircle" />
          ) : (
            <ul style={{ listStyle: "none", padding: 0, margin: 0 }}>
              {pendingApprovals.slice(0, 5).map((task) => (
                <li key={task.id} className="list-row">
                  <div className="list-row-main">
                    <span className="stat-icon">
                      <Icon name="inbox" size={16} />
                    </span>
                    <div>
                      <div className="list-row-title">Approval task #{task.sequenceNumber}</div>
                      <div className="list-row-sub">Requested {formatDate(task.createdAt)}</div>
                    </div>
                  </div>
                  <div className="list-row-end">
                    <StatusBadge status={task.status} />
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>

      <div className="card-grid grid-2" style={{ marginTop: "1.25rem" }}>
        <div className="card">
          <div className="card-header">
            <h2>Announcements</h2>
          </div>
          <EmptyState message="No announcements source is configured yet." icon="info" />
        </div>
        <div className="card">
          <div className="card-header">
            <h2>Upcoming holidays</h2>
          </div>
          <EmptyState message="No holiday calendar source is configured yet." icon="calendar" />
        </div>
      </div>
    </section>
  );
}
