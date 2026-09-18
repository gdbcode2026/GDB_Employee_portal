package com.growdigitalbridge.leave.service.exception;

/** A structurally valid request that violates a documented business rule (date ranges, balance limits). */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) { super(message); }
}
