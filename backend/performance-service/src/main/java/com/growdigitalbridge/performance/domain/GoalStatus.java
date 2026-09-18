package com.growdigitalbridge.performance.domain;

/**
 * DATABASE.md documents a Goal "status" field with no enumerated vocabulary and no numeric
 * progress metric. Mirrors Task's minimal lifecycle (a comparable "individual work item"
 * concept already established in this codebase) rather than inventing a progress percentage
 * or scoring mechanism.
 */
public enum GoalStatus {
    OPEN,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
