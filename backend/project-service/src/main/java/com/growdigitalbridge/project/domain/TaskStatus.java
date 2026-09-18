package com.growdigitalbridge.project.domain;

/** API.md documents a task "status" field without an enumerated vocabulary; this is the minimal common set. */
public enum TaskStatus {
    TODO,
    IN_PROGRESS,
    DONE,
    CANCELLED
}
