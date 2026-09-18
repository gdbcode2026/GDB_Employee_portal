package com.growdigitalbridge.project.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tasks")
public class Task {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "assignee_ref")
    private UUID assigneeRef;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TaskPriority priority;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(length = 128, updatable = false)
    private String createdBy;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(length = 128)
    private String updatedBy;

    @Version
    private long version;

    protected Task() { }

    public Task(UUID id, UUID projectId, UUID assigneeRef, String title, String description,
                TaskPriority priority, LocalDate dueDate, String actor, Instant now) {
        this.id = id;
        this.projectId = projectId;
        this.assigneeRef = assigneeRef;
        this.title = title;
        this.description = description;
        this.status = TaskStatus.TODO;
        this.priority = priority;
        this.dueDate = dueDate;
        this.createdAt = now;
        this.createdBy = actor;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public void reassign(UUID assigneeRef, String actor, Instant now) {
        this.assigneeRef = assigneeRef;
        touch(actor, now);
    }

    public void updateDetails(String title, String description, LocalDate dueDate, String actor, Instant now) {
        this.title = title;
        this.description = description;
        this.dueDate = dueDate;
        touch(actor, now);
    }

    public void changeStatus(TaskStatus status, String actor, Instant now) {
        this.status = status;
        touch(actor, now);
    }

    public void changePriority(TaskPriority priority, String actor, Instant now) {
        this.priority = priority;
        touch(actor, now);
    }

    private void touch(String actor, Instant now) {
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public UUID getAssigneeRef() { return assigneeRef; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public TaskStatus getStatus() { return status; }
    public TaskPriority getPriority() { return priority; }
    public LocalDate getDueDate() { return dueDate; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
