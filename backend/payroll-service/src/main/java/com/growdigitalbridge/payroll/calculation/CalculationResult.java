package com.growdigitalbridge.payroll.calculation;

/** Counts only - never an amount - so the orchestrator can audit-log a completed calculation safely. */
public record CalculationResult(int lineCount, int exceptionCount) {
}
