import { apiClient, ApiError } from "@/lib/api/client";
import type { EmployeeResponse } from "@/lib/api/types";
import { AuthRequiredNotice } from "@/components/AuthRequiredNotice";
import { NotFoundNotice } from "@/components/NotFoundNotice";
import { PageHeader } from "@/components/PageHeader";
import { formatDate } from "@/lib/format";

function initialsFor(first: string, last: string): string {
  return `${first[0] ?? ""}${last[0] ?? ""}`.toUpperCase();
}

export default async function EmployeeDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;

  try {
    const employee = await apiClient.get<EmployeeResponse>(`/api/v1/employees/${id}`);
    return (
      <section aria-labelledby="employee-detail-heading">
        <PageHeader title={`${employee.firstName} ${employee.lastName}`} />
        <div className="card" style={{ display: "flex", alignItems: "center", gap: "1.25rem", flexWrap: "wrap" }}>
          <span className="avatar avatar-lg">{initialsFor(employee.firstName, employee.lastName)}</span>
          <div>
            <h2 id="employee-detail-heading" style={{ marginBottom: "0.15rem" }}>
              {employee.firstName} {employee.lastName}
            </h2>
            <p className="muted" style={{ margin: 0 }}>
              {employee.employment?.jobTitle ?? "No job title on file"} &middot; {employee.employeeNumber}
            </p>
          </div>
          <span style={{ marginLeft: "auto" }}>
            <span className={`badge ${employee.status === "ACTIVE" ? "badge-success" : "badge-neutral"}`}>
              {employee.status}
            </span>
          </span>
        </div>

        <div className="card" style={{ marginTop: "1.25rem" }}>
          <div className="card-header">
            <h3>Details</h3>
          </div>
          <dl className="payslip-meta-grid" style={{ marginBottom: 0 }}>
            <div>
              <dt>Employee number</dt>
              <dd>{employee.employeeNumber}</dd>
            </div>
            <div>
              <dt>Email</dt>
              <dd>{employee.email}</dd>
            </div>
            <div>
              <dt>Status</dt>
              <dd>{employee.status}</dd>
            </div>
            {employee.employment && (
              <>
                <div>
                  <dt>Job title</dt>
                  <dd>{employee.employment.jobTitle}</dd>
                </div>
                <div>
                  <dt>Employment type</dt>
                  <dd>{employee.employment.employmentType.replace("_", " ")}</dd>
                </div>
                <div>
                  <dt>Start date</dt>
                  <dd>{formatDate(employee.employment.startDate)}</dd>
                </div>
              </>
            )}
          </dl>
        </div>
      </section>
    );
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return (
        <section aria-labelledby="employee-detail-heading">
          <PageHeader title="Employee" />
          <AuthRequiredNotice />
        </section>
      );
    }
    if (error instanceof ApiError && error.status === 404) {
      return (
        <section aria-labelledby="employee-detail-heading">
          <PageHeader title="Employee" />
          <NotFoundNotice message="This employee record was not found or is not visible to you." />
        </section>
      );
    }
    throw error;
  }
}
