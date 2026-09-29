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

// Mirrors backend/attendance-service response DTOs field for field.
export type AttendanceStatus = "DRAFT" | "FINALIZED";
export type RegularizationStatus = "SUBMITTED" | "APPROVED" | "REJECTED";
export type ApprovalDecision = "APPROVED" | "REJECTED";

export interface AttendanceRecord {
  id: string;
  employeeRef: string;
  workDate: string;
  checkInAt: string | null;
  checkOutAt: string | null;
  status: AttendanceStatus;
}

export interface RegularizationRequest {
  id: string;
  employeeRef: string;
  workDate: string;
  requestedCheckInAt: string | null;
  requestedCheckOutAt: string | null;
  reason: string;
  status: RegularizationStatus;
  decidedBy: string | null;
  decidedAt: string | null;
}

export interface RegularizationCreateRequest {
  workDate: string;
  requestedCheckInAt?: string;
  requestedCheckOutAt?: string;
  reason: string;
}

// Mirrors backend/leave-service response DTOs field for field.
export type LeaveRequestStatus = "PENDING" | "APPROVED" | "REJECTED" | "CANCELLED";

export interface LeaveType {
  id: string;
  code: string;
  name: string;
}

export interface LeaveBalance {
  id: string;
  employeeRef: string;
  leaveTypeId: string;
  periodYear: number;
  allocated: number;
  used: number;
  reserved: number;
  available: number;
}

export interface LeaveRequest {
  id: string;
  employeeRef: string;
  leaveTypeId: string;
  startDate: string;
  endDate: string;
  units: number;
  reason: string | null;
  status: LeaveRequestStatus;
  decidedBy: string | null;
  decidedAt: string | null;
}

export interface LeaveRequestCreateRequest {
  leaveTypeId: string;
  startDate: string;
  endDate: string;
  reason?: string;
}

// Mirrors backend/expense-service response DTOs field for field.
export type ExpenseClaimStatus = "DRAFT" | "SUBMITTED" | "APPROVED" | "REJECTED" | "REIMBURSED" | "CANCELLED";

export interface ExpenseLineItem {
  date: string;
  category: string;
  amount: number;
  description: string | null;
}

export interface ExpenseReceiptRef {
  documentRef: string;
}

export interface ExpenseClaim {
  id: string;
  employeeRef: string;
  currency: string;
  total: number;
  status: ExpenseClaimStatus;
  workflowRef: string | null;
  lines: ExpenseLineItem[];
  receipts: ExpenseReceiptRef[];
  decidedBy: string | null;
  decidedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ExpenseClaimCreateRequest {
  currency: string;
  lines: ExpenseLineItem[];
  receipts?: ExpenseReceiptRef[];
}

// Mirrors backend/asset-service response DTOs field for field.
export type AssetStatus = "AVAILABLE" | "ASSIGNED" | "RETIRED";

export interface Asset {
  id: string;
  tag: string;
  type: string;
  serial: string | null;
  status: AssetStatus;
  createdAt: string;
  updatedAt: string;
}

// Mirrors backend/performance-service response DTOs field for field.
export type GoalStatus = "OPEN" | "IN_PROGRESS" | "COMPLETED" | "CANCELLED";

export interface Goal {
  id: string;
  employeeRef: string;
  title: string;
  description: string | null;
  target: string | null;
  status: GoalStatus;
  createdAt: string;
  updatedAt: string;
}

export type ReviewStatus = "DRAFT" | "SUBMITTED";

export interface PerformanceReview {
  id: string;
  cycleId: string;
  employeeRef: string;
  reviewerRef: string;
  rating: string | null;
  comments: string | null;
  status: ReviewStatus;
  submittedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

// Mirrors backend/workflow-service ApprovalTaskDtos field for field.
export type ApprovalTaskStatus = "PENDING" | "DECIDED" | "CANCELLED";

export interface ApprovalTask {
  id: string;
  instanceId: string;
  sequenceNumber: number;
  assigneeRef: string;
  status: ApprovalTaskStatus;
  decision: ApprovalDecision | null;
  comment: string | null;
  decidedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

// Mirrors backend/project-service response DTOs field for field.
export type ProjectStatus = "ACTIVE" | "INACTIVE";
export type ProjectTaskStatus = "TODO" | "IN_PROGRESS" | "DONE" | "CANCELLED";
export type TaskPriority = "LOW" | "MEDIUM" | "HIGH";

export interface Project {
  id: string;
  code: string;
  name: string;
  ownerRef: string;
  status: ProjectStatus;
  createdAt: string;
  updatedAt: string;
}

export interface ProjectTask {
  id: string;
  projectId: string;
  assigneeRef: string | null;
  title: string;
  description: string | null;
  status: ProjectTaskStatus;
  priority: TaskPriority;
  dueDate: string | null;
  createdAt: string;
  updatedAt: string;
}

// Mirrors backend/payroll-service PayslipDtos field for field.
export interface PayslipComponentLine {
  code: string;
  amount: number;
}

export interface PayslipYtdInfo {
  available: boolean;
  grossPay: number;
  totalDeductions: number;
}

export interface PayslipTaxInfo {
  configured: boolean;
  periodAmount: number | null;
  ytdAmount: number | null;
}

export interface PayslipSummary {
  id: string;
  runId: string;
  periodId: string;
  periodYear: number;
  periodMonth: number;
  generatedAt: string;
}

export interface PayslipDetail {
  id: string;
  employeeRef: string;
  runId: string;
  periodId: string;
  documentRef: string;
  periodYear: number;
  periodMonth: number;
  periodStart: string;
  periodEnd: string;
  paymentDate: string | null;
  employeeNumber: string | null;
  employeeName: string | null;
  designation: string | null;
  department: string | null;
  joiningDate: string | null;
  earnings: PayslipComponentLine[];
  deductions: PayslipComponentLine[];
  employerContributions: PayslipComponentLine[];
  grossPay: number;
  totalDeductions: number;
  netPay: number;
  amountInWords: string;
  ytd: PayslipYtdInfo;
  tax: PayslipTaxInfo;
  generatedAt: string;
}

export interface PayslipDownloadResponse {
  payslipId: string;
  documentId: string;
  objectKey: string;
  checksum: string;
  mimeType: string;
  sizeBytes: number;
}

// Mirrors backend/document-service PolicyDtos field for field.
export type PolicyStatus = "DRAFT" | "PUBLISHED";

export interface Policy {
  id: string;
  documentId: string;
  title: string;
  status: PolicyStatus;
  createdAt: string;
  updatedAt: string;
}
