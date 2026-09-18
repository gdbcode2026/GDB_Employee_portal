package com.growdigitalbridge.attendance.domain;

/** Per docs/database/DATABASE.md: AttendanceRecord lifecycle is DRAFT until finalized. */
public enum AttendanceStatus {
    DRAFT,
    FINALIZED
}
