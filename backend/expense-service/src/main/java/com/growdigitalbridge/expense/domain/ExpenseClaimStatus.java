package com.growdigitalbridge.expense.domain;

/** Exactly the six states DATABASE.md documents for ExpenseClaim - no others are added. */
public enum ExpenseClaimStatus {
    DRAFT,
    SUBMITTED,
    APPROVED,
    REJECTED,
    REIMBURSED,
    CANCELLED
}
