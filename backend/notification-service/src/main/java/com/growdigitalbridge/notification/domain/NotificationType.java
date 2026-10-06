package com.growdigitalbridge.notification.domain;

/**
 * The category/type of an in-app notification (item 1). Exactly one value per consumed domain
 * event type (see {@code messaging.DomainEventListener}) - no value is invented beyond what an
 * actual, inspected event contract currently supports.
 */
public enum NotificationType {
    LEAVE_REQUESTED,
    LEAVE_APPROVED,
    LEAVE_REJECTED,
    EXPENSE_SUBMITTED,
    EXPENSE_APPROVED,
    ATTENDANCE_REGULARIZATION_APPROVED
}
