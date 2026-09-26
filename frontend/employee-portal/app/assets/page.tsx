import { apiClient, ApiError } from "@/lib/api/client";
import type { Asset } from "@/lib/api/types";
import { AuthRequiredNotice } from "@/components/AuthRequiredNotice";
import { PageHeader } from "@/components/PageHeader";
import { StatCard } from "@/components/StatCard";
import { StatusBadge } from "@/components/StatusBadge";
import { EmptyState } from "@/components/EmptyState";
import { Icon } from "@/components/icons";
import { AssetRequestForm } from "./AssetRequestForm";

export default async function AssetsPage() {
  let assets: Asset[];
  try {
    assets = await apiClient.get<Asset[]>("/api/v1/assets/me");
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return (
        <section aria-labelledby="assets-heading">
          <PageHeader title="Assets" />
          <AuthRequiredNotice />
        </section>
      );
    }
    throw error;
  }

  return (
    <section aria-labelledby="assets-heading">
      <PageHeader title="Assets" description="Equipment currently assigned to you." />

      <div className="card-grid grid-3">
        <StatCard label="Assigned to you" value={String(assets.length)} icon="assets" />
      </div>

      <div className="card" style={{ marginTop: "1.25rem" }}>
        <div className="card-header">
          <h2>Your assets</h2>
        </div>
        {assets.length === 0 ? (
          <EmptyState message="No assets are currently assigned to you." icon="assets" />
        ) : (
          <ul style={{ listStyle: "none", padding: 0, margin: 0 }}>
            {assets.map((asset) => (
              <li key={asset.id} className="list-row">
                <div className="list-row-main">
                  <span className="stat-icon">
                    <Icon name="assets" size={16} />
                  </span>
                  <div>
                    <div className="list-row-title">{asset.type}</div>
                    <div className="list-row-sub">
                      {asset.tag} {asset.serial ? `· ${asset.serial}` : ""}
                    </div>
                  </div>
                </div>
                <div className="list-row-end">
                  <StatusBadge status={asset.status} />
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="card" style={{ marginTop: "1.25rem" }}>
        <div className="card-header">
          <h2>Request an asset</h2>
        </div>
        <AssetRequestForm />
      </div>
    </section>
  );
}
