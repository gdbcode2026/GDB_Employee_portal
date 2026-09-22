package com.growdigitalbridge.workflow.domain;

/** DATABASE.md documents ApprovalTask "status/decision" as separate fields without an enumerated status vocabulary; this is the minimal set. */
public enum TaskStatus {
    PENDING,
    DECIDED,
    CANCELLED
}
