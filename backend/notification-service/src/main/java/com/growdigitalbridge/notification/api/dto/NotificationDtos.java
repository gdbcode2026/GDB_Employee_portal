package com.growdigitalbridge.notification.api.dto;

import com.growdigitalbridge.notification.domain.Notification;
import com.growdigitalbridge.notification.domain.NotificationType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

public final class NotificationDtos {

    private NotificationDtos() { }

    public record Response(UUID id, NotificationType type, String title, String message, UUID sourceEventId,
                            boolean read, Instant createdAt, Instant readAt) {

        public static Response from(Notification notification) {
            return new Response(notification.getId(), notification.getType(), notification.getTitle(),
                    notification.getMessage(), notification.getSourceEventId(), notification.isRead(),
                    notification.getCreatedAt(), notification.getReadAt());
        }
    }

    public record PageMeta(int number, int size, long total) { }

    /**
     * Extends the platform's usual {@code items}/{@code page} list shape with {@code
     * unreadCount} - computed once alongside the page itself rather than exposed through a
     * separate endpoint, since item 2's API surface is exactly four endpoints and an unread
     * "badge" count (item 8) is a read of the same data, not a new capability.
     */
    public record ListResponse(List<Response> items, PageMeta page, long unreadCount) {

        public static ListResponse of(Page<Notification> page, long unreadCount) {
            return new ListResponse(page.getContent().stream().map(Response::from).toList(),
                    new PageMeta(page.getNumber(), page.getSize(), page.getTotalElements()), unreadCount);
        }
    }
}
