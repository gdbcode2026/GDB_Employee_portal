import { apiClient, ApiError } from "@/lib/api/client";
import type { Policy } from "@/lib/api/types";
import { AuthRequiredNotice } from "@/components/AuthRequiredNotice";
import { PageHeader } from "@/components/PageHeader";
import { StatCard } from "@/components/StatCard";
import { StatusBadge } from "@/components/StatusBadge";
import { EmptyState } from "@/components/EmptyState";
import { UnavailableNotice } from "@/components/UnavailableNotice";
import { Icon } from "@/components/icons";
import { formatDate } from "@/lib/format";

export default async function DocumentsPage() {
  let policies: Policy[];
  try {
    policies = await apiClient.get<Policy[]>("/api/v1/policies");
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return (
        <section aria-labelledby="documents-heading">
          <PageHeader title="Documents & Policies" />
          <AuthRequiredNotice />
        </section>
      );
    }
    throw error;
  }

  const published = policies.filter((policy) => policy.status === "PUBLISHED");

  return (
    <section aria-labelledby="documents-heading">
      <PageHeader title="Documents & Policies" description="Company policies and your personal documents." />

      <div className="card-grid grid-3">
        <StatCard label="Published policies" value={String(published.length)} icon="documents" />
      </div>

      <div className="card" style={{ marginTop: "1.25rem" }}>
        <div className="card-header">
          <h2>Company policies</h2>
        </div>
        {policies.length === 0 ? (
          <EmptyState message="No policies have been published yet." icon="documents" />
        ) : (
          <ul style={{ listStyle: "none", padding: 0, margin: 0 }}>
            {policies.map((policy) => (
              <li key={policy.id} className="list-row">
                <div className="list-row-main">
                  <span className="stat-icon">
                    <Icon name="documents" size={16} />
                  </span>
                  <div>
                    <div className="list-row-title">{policy.title}</div>
                    <div className="list-row-sub">Updated {formatDate(policy.updatedAt)}</div>
                  </div>
                </div>
                <div className="list-row-end">
                  <StatusBadge status={policy.status} />
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="card" style={{ marginTop: "1.25rem" }}>
        <div className="card-header">
          <h2>My documents</h2>
        </div>
        <UnavailableNotice
          title="Personal document listing is not yet available"
          message="The Document Service supports uploads and downloads by ID, but a documented endpoint to list your own documents does not exist yet. This section will populate once that capability is added."
        />
      </div>
    </section>
  );
}
