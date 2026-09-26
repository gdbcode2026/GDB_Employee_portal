import { apiClient, ApiError } from "@/lib/api/client";
import type { EmployeeResponse } from "@/lib/api/types";
import { AuthRequiredNotice } from "@/components/AuthRequiredNotice";
import { NotFoundNotice } from "@/components/NotFoundNotice";
import { PageHeader } from "@/components/PageHeader";
import { formatDate } from "@/lib/format";
import { PhoneForm } from "./PhoneForm";
import { EmergencyContactsPanel } from "./EmergencyContactsPanel";

function initialsFor(first: string, last: string): string {
  return `${first[0] ?? ""}${last[0] ?? ""}`.toUpperCase();
}

export default async function ProfilePage() {
  let employee: EmployeeResponse;
  try {
    employee = await apiClient.get<EmployeeResponse>("/api/v1/employees/me");
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return (
        <section aria-labelledby="profile-heading">
          <PageHeader title="My Profile" />
          <AuthRequiredNotice />
        </section>
      );
    }
    if (error instanceof ApiError && error.status === 404) {
      return (
        <section aria-labelledby="profile-heading">
          <PageHeader title="My Profile" />
          <NotFoundNotice message="No employee profile is linked to your account yet. Contact HR to get set up." />
        </section>
      );
    }
    throw error;
  }

  return (
    <section aria-labelledby="profile-heading">
      <PageHeader title="My Profile" description="Your personal, employment, and emergency contact information." />

      <div className="card" style={{ display: "flex", alignItems: "center", gap: "1.25rem", flexWrap: "wrap" }}>
        <span className="avatar avatar-lg">{initialsFor(employee.firstName, employee.lastName)}</span>
        <div>
          <h2 id="profile-heading" style={{ marginBottom: "0.15rem" }}>
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

      <div className="card-grid grid-2" style={{ marginTop: "1.25rem" }}>
        <div className="card">
          <div className="card-header">
            <h3>Personal information</h3>
          </div>
          <dl className="payslip-meta-grid" style={{ marginBottom: 0 }}>
            <div>
              <dt>Full name</dt>
              <dd>
                {employee.firstName} {employee.lastName}
              </dd>
            </div>
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
          </dl>
          <h4 style={{ marginTop: "1.5rem" }}>Contact details</h4>
          <PhoneForm currentPhone={employee.phone} />
        </div>

        <div className="card">
          <div className="card-header">
            <h3>Employment information</h3>
          </div>
          {employee.employment ? (
            <dl className="payslip-meta-grid" style={{ marginBottom: 0 }}>
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
              <div>
                <dt>Status</dt>
                <dd>{employee.employment.status}</dd>
              </div>
              {employee.employment.endDate && (
                <div>
                  <dt>End date</dt>
                  <dd>{formatDate(employee.employment.endDate)}</dd>
                </div>
              )}
            </dl>
          ) : (
            <p className="muted">No employment record on file.</p>
          )}
        </div>
      </div>

      <div className="card" style={{ marginTop: "1.25rem" }}>
        <div className="card-header">
          <h3>Emergency contacts</h3>
        </div>
        <EmergencyContactsPanel contacts={employee.emergencyContacts} />
      </div>
    </section>
  );
}
