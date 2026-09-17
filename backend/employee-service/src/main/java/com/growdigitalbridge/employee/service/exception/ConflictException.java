package com.growdigitalbridge.employee.service.exception;

/** A state conflict: duplicate employee number/email/identity subject, or an already-ended employment. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) { super(message); }
}
