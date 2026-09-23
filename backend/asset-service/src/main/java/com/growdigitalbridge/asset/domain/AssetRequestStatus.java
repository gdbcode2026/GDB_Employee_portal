package com.growdigitalbridge.asset.domain;

/**
 * Mirrors Workflow's own {@code InstanceStatus} terminal/non-terminal shape: SUBMITTED is the
 * only non-terminal state, and the remaining four are the exact outcomes a {@code
 * workflow.completed.v1} event (or requester cancellation) can apply.
 */
public enum AssetRequestStatus {
    SUBMITTED,
    APPROVED,
    REJECTED,
    CANCELLED,
    EXPIRED
}
