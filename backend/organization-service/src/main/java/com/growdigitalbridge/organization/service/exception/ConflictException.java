package com.growdigitalbridge.organization.service.exception;

/** A state conflict: duplicate code, or an active reporting relation already exists. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) { super(message); }
}
