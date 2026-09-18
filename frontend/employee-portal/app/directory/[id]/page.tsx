import { apiClient, ApiError } from "@/lib/api/client";
import type { EmployeeResponse } from "@/lib/api/types";
import { AuthRequiredNotice } from "@/components/AuthRequiredNotice";
import { NotFoundNotice } from "@/components/NotFoundNotice";

export default async function EmployeeDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;

  try {
    const employee = await apiClient.get<EmployeeResponse>(`/api/v1/employees/${id}`);
    return (
      <section aria-labelledby="employee-detail-heading">
        <h2 id="employee-detail-heading">
          {employee.firstName} {employee.lastName}
        </h2>
        <dl className="summary-list">
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
            <div>
              <dt>Job title</dt>
              <dd>{employee.employment.jobTitle}</dd>
            </div>
          )}
        </dl>
      </section>
    );
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return <AuthRequiredNotice />;
    }
    if (error instanceof ApiError && error.status === 404) {
      return <NotFoundNotice message="This employee record was not found or is not visible to you." />;
    }
    throw error;
  }
}
