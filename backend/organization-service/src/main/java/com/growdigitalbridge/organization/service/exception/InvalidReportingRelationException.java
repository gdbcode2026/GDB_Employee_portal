package com.growdigitalbridge.organization.service.exception;

/** A business-rule violation: self-management, a reporting-hierarchy cycle, or an invalid date range. */
public class InvalidReportingRelationException extends RuntimeException {
    public InvalidReportingRelationException(String message) { super(message); }
}
