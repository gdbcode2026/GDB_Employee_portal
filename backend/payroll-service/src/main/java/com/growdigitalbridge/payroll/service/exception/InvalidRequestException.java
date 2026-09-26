package com.growdigitalbridge.payroll.service.exception;

/** A structurally valid request that violates a documented business rule (date ranges, self-approval). */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) { super(message); }
}
