// Mirrors backend/employee-service and backend/organization-service response DTOs field for
// field. Keep in sync by hand until a shared schema/codegen step exists - there is none yet.

export type EmployeeStatus = "ACTIVE" | "INACTIVE";
export type EmploymentStatus = "ACTIVE" | "ENDED";
export type EmploymentType = "FULL_TIME" | "PART_TIME" | "CONTRACT" | "INTERN";

export interface EmploymentSummary {
  id: string;
  jobTitle: string;
  employmentType: EmploymentType;
  startDate: string;
  endDate: string | null;
  status: EmploymentStatus;
}

export interface EmergencyContact {
  id: string;
  name: string;
  phone: string;
  relationship: string;
}

export interface EmergencyContactInput {
  name: string;
  phone: string;
  relationship: string;
}

export interface EmployeeResponse {
  id: string;
  employeeNumber: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  status: EmployeeStatus;
  employment: EmploymentSummary | null;
  emergencyContacts: EmergencyContact[];
  createdAt: string;
  updatedAt: string;
}

export interface EmployeeSummary {
  id: string;
  employeeNumber: string;
  firstName: string;
  lastName: string;
  email: string;
  status: EmployeeStatus;
}

export interface PageMeta {
  number: number;
  size: number;
  total: number;
}

export interface PageResponse<T> {
  items: T[];
  page: PageMeta;
}

export type DepartmentStatus = "ACTIVE" | "INACTIVE";
export type TeamStatus = "ACTIVE" | "INACTIVE";

export interface DepartmentResponse {
  id: string;
  name: string;
  code: string;
  status: DepartmentStatus;
  createdAt: string;
  updatedAt: string;
}

export interface TeamResponse {
  id: string;
  departmentId: string;
  name: string;
  code: string;
  status: TeamStatus;
  createdAt: string;
  updatedAt: string;
}

export interface OrganizationTeamSummary {
  id: string;
  name: string;
  code: string;
}

export interface OrganizationDepartmentNode {
  id: string;
  name: string;
  code: string;
  teams: OrganizationTeamSummary[];
}

export interface OrganizationChartResponse {
  departments: OrganizationDepartmentNode[];
}
