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
            return ListScope.restrictedToTeam(organizationClient.resolveTeamScope(self.get()));
        }
        if (hasAuthority(authentication, "expense.read.self")) {
            Optional<UUID> self = resolveSelf(authentication);
            return self.map(id -> ListScope.restrictedToSelf(Set.of(id))).orElseGet(ListScope::denied);
        }
        return ListScope.denied();
    }

    public boolean hasAuthority(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream().anyMatch(granted -> granted.getAuthority().equals(authority));
    }

    /**
     * {@code tier} records exactly which branch above produced this scope - SELF/TEAM/ALL/{@code
     * null} for denied - purely as a descriptive tag for Reporting V1 authorization review Part A
     * (docs/REPORTING_AUTHORIZATION_REVIEW.md): it does not change what {@code unrestricted}/
     * {@code allowedIds} compute, only adds a way to report which of the three tiers (previously
     * indistinguishable once collapsed into "restricted") actually authorized this request.
     */
    public record ListScope(boolean allowed, boolean unrestricted, Set<UUID> allowedIds, Tier tier) {
        public static ListScope all() { return new ListScope(true, true, Set.of(), Tier.ALL); }
        public static ListScope restrictedToTeam(Set<UUID> ids) { return new ListScope(true, false, ids, Tier.TEAM); }
        public static ListScope restrictedToSelf(Set<UUID> ids) { return new ListScope(true, false, ids, Tier.SELF); }
        public static ListScope denied() { return new ListScope(false, false, Set.of(), null); }

        public enum Tier { SELF, TEAM, ALL }
    }
}
