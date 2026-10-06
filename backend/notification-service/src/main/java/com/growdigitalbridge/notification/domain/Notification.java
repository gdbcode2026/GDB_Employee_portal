package com.growdigitalbridge.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * One in-app notification for exactly one employee (item 1). {@code recipientEmployeeRef} is
 * always set from the originating domain event's own employee reference - never a
 * client-supplied value - and every read/update path scopes its query by this column plus the
 * caller's server-resolved own employee reference (see {@code NotificationService}), so there is
 * no code path where one employee's row can be returned for another employee's request.
 *
 * <p>{@code sourceEventId} is the originating {@link com.growdigitalbridge.platform.common.event.DomainEvent}'s
 * own {@code eventId} - the "source/event reference" item 1 asks for - kept distinct from the
 * idempotency guard ({@code ProcessedEvent}), which exists purely to prevent duplicate creation.
 */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    private UUID id;

    @Column(name = "recipient_employee_ref", nullable = false)
    private UUID recipientEmployeeRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 48)
    private NotificationType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 2000)
    private String message;

    @Column(name = "source_event_id", nullable = false)
    private UUID sourceEventId;

    @Column(nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Version
    private long version;

    protected Notification() { }

    public Notification(UUID id, UUID recipientEmployeeRef, NotificationType type, String title, String message,
                         UUID sourceEventId, Instant createdAt) {
        this.id = id;
        this.recipientEmployeeRef = recipientEmployeeRef;
        this.type = type;
        this.title = title;
        this.message = message;
        this.sourceEventId = sourceEventId;
        this.read = false;
        this.createdAt = createdAt;
    }

    /** Idempotent: marking an already-read notification read again leaves {@code readAt} unchanged. */
    public void markRead(Instant now) {
        if (read) {
            return;
        }
        this.read = true;
        this.readAt = now;
    }

    public UUID getId() { return id; }
    public UUID getRecipientEmployeeRef() { return recipientEmployeeRef; }
    public NotificationType getType() { return type; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public UUID getSourceEventId() { return sourceEventId; }
    public boolean isRead() { return read; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReadAt() { return readAt; }
    public long getVersion() { return version; }
}
