import type { Metadata } from "next";
import "./styles.css";
import { AppShell } from "@/components/AppShell";
import { apiClient } from "@/lib/api/client";
import type { EmployeeResponse, NotificationListResponse } from "@/lib/api/types";

export const metadata: Metadata = {
  title: "GDB Employee Portal",
  description: "Grow Digital Bridge employee portal",
};

async function currentEmployee(): Promise<EmployeeResponse | null> {
  try {
    return await apiClient.get<EmployeeResponse>("/api/v1/employees/me");
  } catch {
    return null;
  }
}

async function unreadNotificationCount(): Promise<number | null> {
  try {
    const response = await apiClient.get<NotificationListResponse>("/api/v1/notifications?size=1");
    return response.unreadCount;
  } catch {
    return null;
  }
}

export default async function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  const [employee, unreadCount] = await Promise.all([currentEmployee(), unreadNotificationCount()]);
  const displayName = employee ? `${employee.firstName} ${employee.lastName}` : null;
  const jobTitle = employee?.employment?.jobTitle ?? null;

  return (
    <html lang="en">
      <body>
        <a className="skip-link" href="#main">
          Skip to content
        </a>
        <AppShell displayName={displayName} jobTitle={jobTitle} unreadNotificationCount={unreadCount}>
          {children}
        </AppShell>
      </body>
    </html>
  );
}
