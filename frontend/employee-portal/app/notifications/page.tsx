import { apiClient, ApiError } from "@/lib/api/client";
import { AuthRequiredNotice } from "@/components/AuthRequiredNotice";
import { PageHeader } from "@/components/PageHeader";
import { UnavailableNotice } from "@/components/UnavailableNotice";

export default async function NotificationsPage() {
  try {
    await apiClient.get("/api/v1/notifications");
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return (
        <section aria-labelledby="notifications-heading">
          <PageHeader title="Notifications" />
          <AuthRequiredNotice />
        </section>
      );
    }
    if (error instanceof ApiError && error.status === 501) {
      return (
        <section aria-labelledby="notifications-heading">
          <PageHeader title="Notifications" description="Stay on top of approvals, reminders, and updates." />
          <UnavailableNotice
            title="Notifications are not enabled yet"
            message="The Notification Service is a platform foundation stub and does not deliver notifications yet. This page will show your real notifications once that service is implemented."
          />
        </section>
      );
    }
    throw error;
  }

  return (
    <section aria-labelledby="notifications-heading">
      <PageHeader title="Notifications" />
    </section>
  );
}
