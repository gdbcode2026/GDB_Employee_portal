package com.growdigitalbridge.payroll.service.exception;

/**
 * Thrown by {@code PayrollRunService.process} when {@code PayrollCalculationEngine.calculate}
 * fails. The run's status has already been safely recorded as {@code CALCULATION_FAILED} in its
 * own transaction before this is thrown - this exception only carries a safe, non-sensitive
 * message back to the API caller.
 */
public class CalculationFailedException extends RuntimeException {
    public CalculationFailedException(String message) { super(message); }
}
