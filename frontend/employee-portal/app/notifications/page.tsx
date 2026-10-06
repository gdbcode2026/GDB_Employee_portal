import { apiClient, ApiError } from "@/lib/api/client";
import type { NotificationListResponse } from "@/lib/api/types";
import { AuthRequiredNotice } from "@/components/AuthRequiredNotice";
import { PageHeader } from "@/components/PageHeader";
import { UnavailableNotice } from "@/components/UnavailableNotice";
import { EmptyState } from "@/components/EmptyState";
import { StatusBadge } from "@/components/StatusBadge";
import { formatDateTime, titleCase } from "@/lib/format";
import { MarkReadButton } from "./MarkReadButton";
import { MarkAllReadButton } from "./MarkAllReadButton";

export default async function NotificationsPage() {
  let data: NotificationListResponse;
  try {
    data = await apiClient.get<NotificationListResponse>("/api/v1/notifications?size=50");
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
    return (
      <section aria-labelledby="notifications-heading">
        <PageHeader title="Notifications" description="Stay on top of approvals, reminders, and updates." />
        <UnavailableNotice
          title="Notifications could not be loaded"
          message="Something went wrong while loading your notifications. Please try again shortly."
        />
      </section>
    );
  }

  return (
    <section aria-labelledby="notifications-heading">
      <PageHeader title="Notifications" description="Stay on top of approvals, reminders, and updates." />

      <div className="card">
        <div className="card-header">
          <h2>
            Your notifications{" "}
            <span className="muted" aria-live="polite">
              ({data.unreadCount} unread)
            </span>
          </h2>
          {data.unreadCount > 0 && <MarkAllReadButton />}
        </div>

        {data.items.length === 0 ? (
          <EmptyState message="No notifications yet." icon="notifications" />
        ) : (
          <ul style={{ listStyle: "none", padding: 0, margin: 0 }} aria-label="Notifications">
            {data.items.map((notification) => (
              <li key={notification.id} className="list-row">
                <div className="list-row-main">
                  <div>
                    <div className="list-row-title">
                      {notification.title} <StatusBadge status={notification.read ? "READ" : "UNREAD"} />
                    </div>
                    <div className="list-row-sub">{notification.message}</div>
                    <div className="list-row-sub">
                      {titleCase(notification.type)} &middot; {formatDateTime(notification.createdAt)}
                    </div>
                  </div>
                </div>
                <div className="list-row-end">{!notification.read && <MarkReadButton id={notification.id} />}</div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}
