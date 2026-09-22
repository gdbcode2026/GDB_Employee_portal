package com.growdigitalbridge.workflow.domain;

/** Exactly the six supported request types docs/workflows/WORKFLOWS.md names - no others are invented. */
public enum RequestType {
    LEAVE,
    WFH,
    ATTENDANCE_REGULARIZATION,
    EXPENSE,
    ASSET_REQUEST,
    DOCUMENT_REQUEST
}
