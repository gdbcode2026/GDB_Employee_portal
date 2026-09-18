package com.growdigitalbridge.leave.domain;

/** Per docs/database/DATABASE.md; DRAFT is omitted because no documented endpoint transitions a draft. */
public enum LeaveRequestStatus {
    SUBMITTED,
    APPROVED,
    REJECTED,
    CANCELLED
}
