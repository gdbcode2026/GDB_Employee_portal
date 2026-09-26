import { apiClient, ApiError } from "@/lib/api/client";
import type { OrganizationChartResponse } from "@/lib/api/types";
import { AuthRequiredNotice } from "@/components/AuthRequiredNotice";
import { PermissionDeniedNotice } from "@/components/PermissionDeniedNotice";
import { EmptyState } from "@/components/EmptyState";
import { PageHeader } from "@/components/PageHeader";
import { Icon } from "@/components/icons";

export default async function OrganizationPage() {
  try {
    const chart = await apiClient.get<OrganizationChartResponse>("/api/v1/organization/chart");
    return (
      <section aria-labelledby="organization-heading">
        <PageHeader
          title="Organization"
          description="Departments and teams across Grow Digital Bridge."
        />
        {chart.departments.length === 0 ? (
          <div className="card">
            <EmptyState message="No departments have been set up yet." icon="organization" />
          </div>
        ) : (
          <ul className="org-chart">
            {chart.departments.map((department) => (
              <li key={department.id} className="org-dept-card">
                <div style={{ display: "flex", alignItems: "center", gap: "0.6rem" }}>
                  <span className="stat-icon">
                    <Icon name="building" size={17} />
                  </span>
                  <h3 style={{ margin: 0 }}>{department.name}</h3>
                  <span className="code-badge">{department.code}</span>
                </div>
                {department.teams.length === 0 ? (
                  <p className="muted" style={{ marginTop: "0.75rem" }}>
                    No teams in this department.
                  </p>
                ) : (
                  <ul className="team-list">
                    {department.teams.map((team) => (
                      <li key={team.id}>
                        <span>{team.name}</span>
                        <span className="code-badge">{team.code}</span>
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
      return (
        <section aria-labelledby="organization-heading">
          <PageHeader title="Organization" />
          <AuthRequiredNotice />
        </section>
      );
    }
    if (error instanceof ApiError && error.status === 403) {
      return (
        <section aria-labelledby="organization-heading">
          <PageHeader title="Organization" />
          <PermissionDeniedNotice />
        </section>
      );
    }
    throw error;
  }
}
