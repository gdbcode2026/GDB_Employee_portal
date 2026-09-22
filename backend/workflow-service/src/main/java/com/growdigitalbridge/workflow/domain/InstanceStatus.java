package com.growdigitalbridge.workflow.domain;

/** Exactly the five states DATABASE.md documents for Workflow's Instance entity. */
public enum InstanceStatus {
    RUNNING,
    APPROVED,
    REJECTED,
    CANCELLED,
    EXPIRED
}
