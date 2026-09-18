import { apiClient, ApiError } from "@/lib/api/client";
import type { OrganizationChartResponse } from "@/lib/api/types";
import { AuthRequiredNotice } from "@/components/AuthRequiredNotice";
import { PermissionDeniedNotice } from "@/components/PermissionDeniedNotice";
import { EmptyState } from "@/components/EmptyState";

export default async function OrganizationPage() {
  try {
    const chart = await apiClient.get<OrganizationChartResponse>("/api/v1/organization/chart");
    return (
      <section aria-labelledby="organization-heading">
        <h2 id="organization-heading">Organization</h2>
        {chart.departments.length === 0 ? (
          <EmptyState message="No departments have been set up yet." />
        ) : (
          <ul className="org-chart">
            {chart.departments.map((department) => (
              <li key={department.id}>
                <h3>
                  {department.name} <span className="code-badge">{department.code}</span>
                </h3>
                {department.teams.length === 0 ? (
                  <p className="muted">No teams in this department.</p>
                ) : (
                  <ul className="team-list">
                    {department.teams.map((team) => (
                      <li key={team.id}>
                        {team.name} <span className="code-badge">{team.code}</span>
                      </li>
                    ))}
                  </ul>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>
    );
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return <AuthRequiredNotice />;
    }
    if (error instanceof ApiError && error.status === 403) {
      return <PermissionDeniedNotice />;
    }
    throw error;
  }
}
