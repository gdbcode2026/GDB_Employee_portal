package com.growdigitalbridge.expense.service;

import com.growdigitalbridge.expense.client.EmployeeClient;
import com.growdigitalbridge.expense.client.OrganizationClient;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * The single place that decides whether a caller may see or act on a given employee's expense
 * data. {@code team} visibility is always resolved by asking Organization Service for the
 * caller's reporting subtree, and "self" is always resolved by asking Employee Service for
 * the caller's own employee reference - there is no code path anywhere that accepts a
 * client-supplied employee, team, or department identifier for these decisions.
 */
@Component
public class ExpenseAccessGuard {

    private final EmployeeClient employeeClient;
    private final OrganizationClient organizationClient;

    public ExpenseAccessGuard(EmployeeClient employeeClient, OrganizationClient organizationClient) {
        this.employeeClient = employeeClient;
        this.organizationClient = organizationClient;
    }

    public Optional<UUID> resolveSelf(Authentication authentication) {
        return employeeClient.resolveSelfEmployeeRef();
    }

    /** Whether {@code targetEmployeeRef} is in the caller's own reporting subtree per Organization Service. */
    public boolean isWithinCallersTeamScope(Authentication authentication, UUID targetEmployeeRef) {
        return resolveSelf(authentication)
                .map(self -> organizationClient.resolveTeamScope(self).contains(targetEmployeeRef))
                .orElse(false);
    }

    /** Shared "team or all" authorization check for approval-style actions (see LeaveAccessGuard for the identical pattern). */
    public boolean canActOnBehalfOf(Authentication authentication, UUID targetEmployeeRef, String teamAuthority, String allAuthority) {
        if (hasAuthority(authentication, allAuthority)) {
            return true;
        }
        return hasAuthority(authentication, teamAuthority) && isWithinCallersTeamScope(authentication, targetEmployeeRef);
    }

    public ListScope resolveListScope(Authentication authentication) {
        if (hasAuthority(authentication, "expense.read.all")) {
            return ListScope.all();
        }
        if (hasAuthority(authentication, "expense.read.team")) {
            Optional<UUID> self = resolveSelf(authentication);
            if (self.isEmpty()) {
                return ListScope.denied();
            }
            return ListScope.restrictedTo(organizationClient.resolveTeamScope(self.get()));
        }
        if (hasAuthority(authentication, "expense.read.self")) {
            Optional<UUID> self = resolveSelf(authentication);
            return self.map(id -> ListScope.restrictedTo(Set.of(id))).orElseGet(ListScope::denied);
        }
        return ListScope.denied();
    }

    public boolean hasAuthority(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream().anyMatch(granted -> granted.getAuthority().equals(authority));
    }

    public record ListScope(boolean allowed, boolean unrestricted, Set<UUID> allowedIds) {
        public static ListScope all() { return new ListScope(true, true, Set.of()); }
        public static ListScope restrictedTo(Set<UUID> ids) { return new ListScope(true, false, ids); }
        public static ListScope denied() { return new ListScope(false, false, Set.of()); }
    }
}
