package com.growdigitalbridge.attendance.service;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Shared "team or all" decision-authorization check for WFH/regularization approval, reusing
 * {@link AttendanceAccessGuard}'s self/team resolution. An {@code .all} authority always
 * qualifies; a {@code .team} authority only qualifies when Organization Service confirms the
 * request's employee is within the caller's resolved reporting subtree.
 */
@Component
class OrganizationScopeChecker {

    private final AttendanceAccessGuard accessGuard;

    OrganizationScopeChecker(AttendanceAccessGuard accessGuard) {
        this.accessGuard = accessGuard;
    }

    boolean canDecide(Authentication authentication, UUID targetEmployeeRef, String teamAuthority, String allAuthority) {
        if (hasAuthority(authentication, allAuthority)) {
            return true;
        }
        if (!hasAuthority(authentication, teamAuthority)) {
            return false;
        }
        return accessGuard.isWithinCallersTeamScope(authentication, targetEmployeeRef);
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream().anyMatch(granted -> granted.getAuthority().equals(authority));
    }
}
