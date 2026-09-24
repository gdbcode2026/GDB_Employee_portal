package com.growdigitalbridge.expense.service.exception;

public class InvalidLifecycleTransitionException extends RuntimeException {
    public InvalidLifecycleTransitionException(String message) { super(message); }
}
