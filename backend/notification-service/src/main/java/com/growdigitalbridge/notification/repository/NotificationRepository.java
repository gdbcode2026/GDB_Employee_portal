package com.growdigitalbridge.notification.repository;

import com.growdigitalbridge.notification.domain.Notification;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Every lookup is scoped by {@code recipientEmployeeRef} at the query level, not by a
 * fetch-then-check in the service layer - a notification belonging to a different employee is
 * indistinguishable from a nonexistent one, which is exactly the "no client-supplied recipient
 * may override authorization" / deny-by-default posture item 3 requires.
 */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByRecipientEmployeeRefOrderByCreatedAtDesc(UUID recipientEmployeeRef, Pageable pageable);

    Optional<Notification> findByIdAndRecipientEmployeeRef(UUID id, UUID recipientEmployeeRef);

    long countByRecipientEmployeeRefAndReadFalse(UUID recipientEmployeeRef);

    @Modifying
    @Query("update Notification n set n.read = true, n.readAt = :now where n.recipientEmployeeRef = :recipientEmployeeRef and n.read = false")
    int markAllReadForRecipient(@Param("recipientEmployeeRef") UUID recipientEmployeeRef, @Param("now") Instant now);
}
