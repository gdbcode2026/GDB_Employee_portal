import { apiClient, ApiError } from "@/lib/api/client";
import type { PageResponse, EmployeeSummary } from "@/lib/api/types";
import { AuthRequiredNotice } from "@/components/AuthRequiredNotice";
import { PermissionDeniedNotice } from "@/components/PermissionDeniedNotice";
import { PageHeader } from "@/components/PageHeader";
import { UnavailableNotice } from "@/components/UnavailableNotice";
import { StatCard } from "@/components/StatCard";

async function safe<T>(promise: Promise<T>): Promise<T | null> {
  try {
    return await promise;
  } catch {
    return null;
  }
}

/**
 * HR-facing report (Reporting V1, docs/REPORTING_V1_REQUIREMENTS.md Section D1). Composes
 * Employee Service only - Organization Service was dropped during implementation; see the
 * "Scope correction" note below and the doc's own D1 update for the full reasoning.
 *
 * Authorization gate: `GET /employees` (no departmentId/teamId parameter exists - confirmed
 * again here) 401s if unauthenticated and 403s for any caller without `employee.read.team`/
 * `.all` (plain employees and Team Leads get `PermissionDeniedNotice`, never the report). As of
 * Reporting V1 authorization review Part A (docs/REPORTING_AUTHORIZATION_REVIEW.md), the response
 * also carries `page.scope` ("TEAM"/"ALL"), taken directly from the backend's own
 * `EmployeeAccessGuard` decision - this page now additionally requires `scope === "ALL"` before
 * rendering, so a Manager's legitimate 200 (team-scoped) no longer satisfies this HR-only report.
 * This is the fix for the gap the D1 implementation note originally flagged; no content-based
 * heuristic is used anywhere here.
 */
export default async function WorkforceSummaryPage() {
  let total: PageResponse<EmployeeSummary>;
  try {
    total = await apiClient.get<PageResponse<EmployeeSummary>>("/api/v1/employees?size=1");
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return (
        <section aria-labelledby="workforce-summary-heading">
          <PageHeader title="Workforce / Headcount Summary" />
          <AuthRequiredNotice />
        </section>
      );
    }
    if (error instanceof ApiError && error.status === 403) {
      return (
        <section aria-labelledby="workforce-summary-heading">
          <PageHeader title="Workforce / Headcount Summary" description="HR view of total headcount by employment status." />
          <PermissionDeniedNotice message="Workforce / Headcount Summary is available to HR with organization-wide employee access. Contact HR if you believe this is incorrect." />
        </section>
      );
    }
    return (
      <section aria-labelledby="workforce-summary-heading">
        <PageHeader title="Workforce / Headcount Summary" />
        <UnavailableNotice
          title="Workforce summary could not be loaded"
          message="Something went wrong while loading employee data. Please try again shortly."
        />
      </section>
    );
  }

  // Fail closed: anything other than exactly "ALL" (including a legitimate but team-scoped
  // Manager response, or a missing/unrecognized value) is treated as not authorized for this
  // specific, HR-only, organization-wide report.
  if (total.page.scope !== "ALL") {
    return (
      <section aria-labelledby="workforce-summary-heading">
        <PageHeader title="Workforce / Headcount Summary" description="HR view of total headcount by employment status." />
        <PermissionDeniedNotice message="Workforce / Headcount Summary requires organization-wide employee access. Contact HR if you believe this is incorrect." />
      </section>
    );
  }

  const active = await safe(apiClient.get<PageResponse<EmployeeSummary>>("/api/v1/employees?status=ACTIVE&size=1"));

  const totalHeadcount = total.page.total;
  const activeHeadcount = active?.page.total ?? null;
  const inactiveHeadcount = activeHeadcount !== null ? totalHeadcount - activeHeadcount : null;

  return (
    <section aria-labelledby="workforce-summary-heading">
      <PageHeader title="Workforce / Headcount Summary" description="Total headcount by employment status." />

      <div className="card-grid grid-3">
        <StatCard label="Total headcount" value={String(totalHeadcount)} icon="organization" />
        <StatCard
          label="Active"
          value={activeHeadcount !== null ? String(activeHeadcount) : "Unavailable"}
          icon="checkCircle"
        />
        <StatCard
          label="Inactive"
          value={inactiveHeadcount !== null ? String(inactiveHeadcount) : "Unavailable"}
          icon="alertTriangle"
        />
      </div>

      <div className="notice notice-neutral" style={{ marginTop: "1.25rem" }}>
        <h2>About this report</h2>
        <p>
          Breakdown by department, team, and employment type is not shown: no existing API links an
          employee record to a department or team, and employment type is only available per
          employee (not as a list-level count), so computing it here would require one call per
          employee - unsafe at any real headcount. See <code>docs/REPORTING_V1_REQUIREMENTS.md</code>{" "}
          (Section D1) for the full limitation.
        </p>
      </div>
    </section>
  );
}
