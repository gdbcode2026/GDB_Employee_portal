package com.growdigitalbridge.notification.service;

import com.growdigitalbridge.notification.api.dto.NotificationDtos;
import com.growdigitalbridge.notification.client.EmployeeClient;
import com.growdigitalbridge.notification.domain.Notification;
import com.growdigitalbridge.notification.repository.NotificationRepository;
import com.growdigitalbridge.notification.service.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every method here resolves "self" by calling Employee Service for the caller's own employee
 * reference (item 3) - there is no code path that accepts a client-supplied recipient, and
 * {@link NotificationRepository}'s queries are scoped by that resolved reference at the
 * database level, not by a fetch-then-check in this class.
 */
@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final EmployeeClient employeeClient;

    public NotificationService(NotificationRepository repository, EmployeeClient employeeClient) {
        this.repository = repository;
        this.employeeClient = employeeClient;
    }

    @Transactional(readOnly = true)
    public NotificationDtos.ListResponse list(Pageable pageable) {
        UUID self = resolveSelf();
        Page<Notification> page = repository.findByRecipientEmployeeRefOrderByCreatedAtDesc(self, pageable);
        long unreadCount = repository.countByRecipientEmployeeRefAndReadFalse(self);
        return NotificationDtos.ListResponse.of(page, unreadCount);
    }

    @Transactional(readOnly = true)
    public NotificationDtos.Response getById(UUID id) {
        UUID self = resolveSelf();
        Notification notification = find(id, self);
        return NotificationDtos.Response.from(notification);
    }

    @Transactional
    public NotificationDtos.Response markRead(UUID id) {
        UUID self = resolveSelf();
        Notification notification = find(id, self);
        notification.markRead(Instant.now());
        repository.save(notification);
        return NotificationDtos.Response.from(notification);
    }

    @Transactional
    public void markAllRead() {
        UUID self = resolveSelf();
        repository.markAllReadForRecipient(self, Instant.now());
    }

    private Notification find(UUID id, UUID self) {
        return repository.findByIdAndRecipientEmployeeRef(id, self)
                .orElseThrow(() -> new ResourceNotFoundException("Notification " + id + " was not found."));
    }

    private UUID resolveSelf() {
        return employeeClient.resolveSelfEmployeeRef()
                .orElseThrow(() -> new AccessDeniedException("Unable to resolve the caller's own employee reference."));
    }
}
