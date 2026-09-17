package com.growdigitalbridge.employee.service.exception;

/** Also thrown when a resource exists but is not visible to the caller, per the documented
 *  "404 = absent/not visible" convention, so existence is never leaked to an unauthorized caller. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) { super(message); }
}
