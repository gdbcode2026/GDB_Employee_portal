import { apiClient, ApiError } from "@/lib/api/client";
import type { AttendanceRecord, PageResponse, RegularizationRequest } from "@/lib/api/types";
import { AuthRequiredNotice } from "@/components/AuthRequiredNotice";
import { PageHeader } from "@/components/PageHeader";
import { EmptyState } from "@/components/EmptyState";
import { StatusBadge } from "@/components/StatusBadge";
import { CheckInOutActions } from "@/components/CheckInOutActions";
import { Pagination } from "@/components/Pagination";
import { formatDate, formatTime } from "@/lib/format";
import { RegularizationForm } from "./RegularizationForm";

async function safe<T>(promise: Promise<T>): Promise<T | null> {
  try {
    return await promise;
  } catch {
    return null;
  }
}

export default async function AttendancePage({
  searchParams,
}: {
  searchParams: Promise<{ page?: string }>;
}) {
  const { page: pageParam } = await searchParams;
  const page = Number.parseInt(pageParam ?? "0", 10) || 0;

  let attendance: PageResponse<AttendanceRecord>;
  try {
    attendance = await apiClient.get<PageResponse<AttendanceRecord>>(
      `/api/v1/attendance/me?page=${page}&size=15&sort=workDate,desc`,
    );
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return (
        <section aria-labelledby="attendance-heading">
          <PageHeader title="Attendance" />
          <AuthRequiredNotice />
        </section>
      );
    }
    throw error;
  }

  const regularizations = await safe(
    apiClient.get<PageResponse<RegularizationRequest>>("/api/v1/attendance/regularizations?size=10"),
  );

  const today = new Date().toLocaleDateString("en-CA");
  const todayRecord = attendance.items.find((record) => record.workDate === today) ?? null;
  const canCheckOut = Boolean(todayRecord?.checkInAt && !todayRecord?.checkOutAt);

  return (
    <section aria-labelledby="attendance-heading">
      <PageHeader title="Attendance" description="Track your daily check-ins and request corrections." />

      <div className="card">
        <div className="card-header">
          <h2>Today</h2>
        </div>
        <div className="quick-actions">
          <CheckInOutActions canCheckOut={canCheckOut} />
        </div>
      </div>

      <div className="card" style={{ marginTop: "1.25rem" }}>
        <div className="card-header">
          <h2>Attendance history</h2>
        </div>
        {attendance.items.length === 0 ? (
          <EmptyState message="No attendance records yet." icon="attendance" />
        ) : (
          <>
            <table className="data-table">
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Check-in</th>
                  <th>Check-out</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {attendance.items.map((record) => (
                  <tr key={record.id}>
                    <td>{formatDate(record.workDate)}</td>
                    <td>{formatTime(record.checkInAt)}</td>
                    <td>{formatTime(record.checkOutAt)}</td>
                    <td>
                      <StatusBadge status={record.status} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <Pagination
              basePath="/attendance"
              currentPage={attendance.page.number}
              pageSize={attendance.page.size}
              total={attendance.page.total}
            />
          </>
        )}
      </div>

      <div className="card" style={{ marginTop: "1.25rem" }}>
        <div className="card-header">
          <h2>Regularization requests</h2>
        </div>
        {!regularizations || regularizations.items.length === 0 ? (
          <EmptyState message="No regularization requests submitted yet." icon="calendar" />
        ) : (
          <ul style={{ listStyle: "none", padding: 0, margin: 0 }}>
            {regularizations.items.map((request) => (
              <li key={request.id} className="list-row">
                <div className="list-row-main">
                  <div>
                    <div className="list-row-title">{formatDate(request.workDate)}</div>
                    <div className="list-row-sub">{request.reason}</div>
                  </div>
                </div>
                <div className="list-row-end">
                  <StatusBadge status={request.status} />
                </div>
              </li>
            ))}
          </ul>
        )}
        <h3 style={{ marginTop: "1.5rem" }}>Request a correction</h3>
        <RegularizationForm />
      </div>
    </section>
  );
}
