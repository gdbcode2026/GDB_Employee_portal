import Link from "next/link";
import { apiClient, ApiError } from "@/lib/api/client";
import type { EmployeeResponse } from "@/lib/api/types";
import { AuthRequiredNotice } from "@/components/AuthRequiredNotice";

export default async function DashboardPage() {
  try {
    const employee = await apiClient.get<EmployeeResponse>("/api/v1/employees/me");
    return (
      <section aria-labelledby="dashboard-heading">
        <h2 id="dashboard-heading">Welcome back, {employee.firstName}</h2>
        <dl className="summary-list">
          <div>
            <dt>Employee number</dt>
            <dd>{employee.employeeNumber}</dd>
          </div>
          <div>
            <dt>Status</dt>
            <dd>{employee.status}</dd>
          </div>
          {employee.employment && (
            <div>
              <dt>Job title</dt>
              <dd>{employee.employment.jobTitle}</dd>
            </div>
          )}
        </dl>
        <nav aria-label="Quick links" className="quick-links">
          <Link href="/profile">View full profile</Link>
          <Link href="/directory">Employee directory</Link>
          <Link href="/organization">Organization</Link>
        </nav>
      </section>
    );
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return (
        <section aria-labelledby="dashboard-heading">
          <h2 id="dashboard-heading">Dashboard</h2>
          <AuthRequiredNotice />
        </section>
      );
    }
    if (error instanceof ApiError && error.status === 404) {
      return (
        <section aria-labelledby="dashboard-heading">
          <h2 id="dashboard-heading">Dashboard</h2>
          <p>No employee profile is linked to your account yet. Contact HR to get set up.</p>
          <nav aria-label="Quick links" className="quick-links">
            <Link href="/directory">Employee directory</Link>
            <Link href="/organization">Organization</Link>
          </nav>
        </section>
      );
    }
    throw error;
  }
}
