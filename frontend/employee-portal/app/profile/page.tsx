import { apiClient, ApiError } from "@/lib/api/client";
import type { EmployeeResponse } from "@/lib/api/types";
import { AuthRequiredNotice } from "@/components/AuthRequiredNotice";
import { NotFoundNotice } from "@/components/NotFoundNotice";
import { PhoneForm } from "./PhoneForm";
import { EmergencyContactsPanel } from "./EmergencyContactsPanel";

export default async function ProfilePage() {
  let employee: EmployeeResponse;
  try {
    employee = await apiClient.get<EmployeeResponse>("/api/v1/employees/me");
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return <AuthRequiredNotice />;
    }
    if (error instanceof ApiError && error.status === 404) {
      return (
        <NotFoundNotice message="No employee profile is linked to your account yet. Contact HR to get set up." />
      );
    }
    throw error;
  }

  return (
    <section aria-labelledby="profile-heading">
      <h2 id="profile-heading">My profile</h2>
      <dl className="summary-list">
        <div>
          <dt>Name</dt>
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
        {employee.employment && (
          <>
            <div>
              <dt>Job title</dt>
              <dd>{employee.employment.jobTitle}</dd>
            </div>
            <div>
              <dt>Employment type</dt>
              <dd>{employee.employment.employmentType}</dd>
            </div>
            <div>
              <dt>Start date</dt>
              <dd>{employee.employment.startDate}</dd>
            </div>
          </>
        )}
      </dl>

      <h3>Contact details</h3>
      <PhoneForm currentPhone={employee.phone} />

      <h3>Emergency contacts</h3>
      <EmergencyContactsPanel contacts={employee.emergencyContacts} />
    </section>
  );
}
