import { apiClient, ApiError } from "@/lib/api/client";
import type { Goal, PageResponse, PerformanceReview } from "@/lib/api/types";
import { AuthRequiredNotice } from "@/components/AuthRequiredNotice";
import { PageHeader } from "@/components/PageHeader";
import { StatCard } from "@/components/StatCard";
import { StatusBadge } from "@/components/StatusBadge";
import { EmptyState } from "@/components/EmptyState";
import { Icon } from "@/components/icons";
import { formatDate } from "@/lib/format";
import { GoalForm } from "./GoalForm";
import { GoalStatusControl } from "./GoalStatusControl";

async function safe<T>(promise: Promise<T>): Promise<T | null> {
  try {
    return await promise;
  } catch {
    return null;
  }
}

export default async function PerformancePage() {
  let goals: PageResponse<Goal>;
  try {
    goals = await apiClient.get<PageResponse<Goal>>("/api/v1/performance/goals?size=25");
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return (
        <section aria-labelledby="performance-heading">
          <PageHeader title="Performance" />
          <AuthRequiredNotice />
        </section>
      );
    }
    throw error;
  }

  const reviews = await safe(apiClient.get<PageResponse<PerformanceReview>>("/api/v1/performance/reviews?size=10"));
  const openGoals = goals.items.filter((goal) => goal.status === "OPEN" || goal.status === "IN_PROGRESS");
  const completedGoals = goals.items.filter((goal) => goal.status === "COMPLETED");

  return (
    <section aria-labelledby="performance-heading">
      <PageHeader title="Performance" description="Track your goals and review history." />

      <div className="card-grid grid-3">
        <StatCard label="Active goals" value={String(openGoals.length)} icon="target" />
        <StatCard label="Completed goals" value={String(completedGoals.length)} icon="checkCircle" />
        <StatCard label="Reviews on file" value={reviews ? String(reviews.items.length) : "Unavailable"} icon="performance" />
      </div>

      <div className="card" style={{ marginTop: "1.25rem" }}>
        <div className="card-header">
          <h2>Your goals</h2>
        </div>
        {goals.items.length === 0 ? (
          <EmptyState message="No goals set yet." icon="target" />
        ) : (
          <ul style={{ listStyle: "none", padding: 0, margin: 0 }}>
            {goals.items.map((goal) => (
              <li key={goal.id} className="list-row">
                <div className="list-row-main">
                  <span className="stat-icon">
                    <Icon name="target" size={16} />
                  </span>
                  <div>
                    <div className="list-row-title">{goal.title}</div>
                    {goal.target && <div className="list-row-sub">Target: {goal.target}</div>}
                  </div>
                </div>
                <div className="list-row-end" style={{ display: "flex", alignItems: "center", gap: "0.6rem" }}>
                  <StatusBadge status={goal.status} />
                  <GoalStatusControl id={goal.id} status={goal.status} />
                </div>
              </li>
            ))}
          </ul>
        )}
        <h3 style={{ marginTop: "1.5rem" }}>Add a goal</h3>
        <GoalForm />
      </div>

      <div className="card" style={{ marginTop: "1.25rem" }}>
        <div className="card-header">
          <h2>Review history</h2>
        </div>
        {!reviews || reviews.items.length === 0 ? (
          <EmptyState message="No performance reviews on file yet." icon="performance" />
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>Cycle</th>
                <th>Rating</th>
                <th>Status</th>
                <th>Submitted</th>
              </tr>
            </thead>
            <tbody>
              {reviews.items.map((review) => (
                <tr key={review.id}>
                  <td>{review.cycleId}</td>
                  <td>{review.rating ?? "—"}</td>
                  <td>
                    <StatusBadge status={review.status} />
                  </td>
                  <td>{formatDate(review.submittedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </section>
  );
}
