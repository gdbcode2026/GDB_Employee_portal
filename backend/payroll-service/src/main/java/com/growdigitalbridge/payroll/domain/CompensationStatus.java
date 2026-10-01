package com.growdigitalbridge.payroll.domain;

/**
 * Administrative on/off switch for an {@link EmployeeCompensation} record, independent of its
 * effective-dating (Section G). Effective-dating alone already answers "is this the record a
 * given date resolves to"; this status exists so HR/Finance can administratively deactivate a
 * record (e.g. created in error, superseded outside the normal effective-dating flow) without
 * deleting history or touching dates. {@link com.growdigitalbridge.payroll.calculation.CompensationResolver}
 * only ever resolves {@code ACTIVE} records.
 */
public enum CompensationStatus {
    ACTIVE,
    INACTIVE
}
