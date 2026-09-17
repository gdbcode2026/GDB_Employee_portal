package com.growdigitalbridge.employee.service.exception;

/** A business-rule violation on a lifecycle/date transition, e.g. attempting to reactivate a deactivated employee. */
public class InvalidLifecycleTransitionException extends RuntimeException {
    public InvalidLifecycleTransitionException(String message) { super(message); }
}
