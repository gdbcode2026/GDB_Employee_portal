package com.growdigitalbridge.asset.service.exception;

/** A structurally valid request that violates a documented business rule. */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) { super(message); }
}
