package com.growdigitalbridge.attendance.service.exception;

/** A structurally valid request that violates a documented business rule (date ranges, required alternatives). */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) { super(message); }
}
