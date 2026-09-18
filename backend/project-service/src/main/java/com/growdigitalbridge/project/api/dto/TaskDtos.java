package com.growdigitalbridge.project.api.dto;

import com.growdigitalbridge.project.domain.TaskPriority;
import com.growdigitalbridge.project.domain.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class TaskDtos {

    private TaskDtos() { }

    public record CreateRequest(
            UUID assigneeRef,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2000) String description,
            TaskPriority priority,
            LocalDate dueDate) { }

    /** Partial update: any field left null is unchanged. Self-scoped callers may only set status/priority (see TaskService). */
    public record UpdateRequest(
            UUID assigneeRef,
            @Size(max = 200) String title,
            @Size(max = 2000) String description,
            TaskStatus status,
            TaskPriority priority,
            LocalDate dueDate) { }

    public record Response(UUID id, UUID projectId, UUID assigneeRef, String title, String description,
                            TaskStatus status, TaskPriority priority, LocalDate dueDate, Instant createdAt, Instant updatedAt) { }
}
