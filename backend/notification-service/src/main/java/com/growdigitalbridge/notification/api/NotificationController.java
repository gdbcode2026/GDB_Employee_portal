package com.growdigitalbridge.notification.api;

import com.growdigitalbridge.notification.api.dto.NotificationDtos;
import com.growdigitalbridge.notification.service.NotificationService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Every endpoint here is self-only (item 3); see {@code SecurityConfig}/{@code NotificationService}. */
@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController {

    private final NotificationService service;

    NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    NotificationDtos.ListResponse list(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size,
                                        @RequestParam(required = false) String sort) {
        return service.list(PagingSupport.of(page, size, sort, "createdAt"));
    }

    @GetMapping("/{id}")
    NotificationDtos.Response getById(@PathVariable UUID id) {
        return service.getById(id);
    }

    @PostMapping("/{id}/read")
    NotificationDtos.Response markRead(@PathVariable UUID id) {
        return service.markRead(id);
    }

    @PostMapping("/read-all")
    void markAllRead() {
        service.markAllRead();
    }
}
