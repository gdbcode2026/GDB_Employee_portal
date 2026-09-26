package com.growdigitalbridge.payroll.service.exception;

public class InvalidLifecycleTransitionException extends RuntimeException {
    public InvalidLifecycleTransitionException(String message) { super(message); }
}
