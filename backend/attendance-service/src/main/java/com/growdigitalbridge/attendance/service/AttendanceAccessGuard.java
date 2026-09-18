package com.growdigitalbridge.attendance.service;

import com.growdigitalbridge.attendance.client.EmployeeClient;
import com.growdigitalbridge.attendance.client.OrganizationClient;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * The single place that decides whether a caller may see or act on a given employee's
 * attendance data. {@code team} visibility is always resolved by asking Organization Service
 * for the caller's reporting subtree, and "self" is always resolved by asking Employee
 * Service for the caller's own employee reference - there is no code path anywhere that
 * accepts a client-supplied employee, team, or department identifier for these decisions.
 */
@Component
public class AttendanceAccessGuard {

    private final EmployeeClient employeeClient;
    private final OrganizationClient organizationClient;

    public AttendanceAccessGuard(EmployeeClient employeeClient, OrganizationClient organizationClient) {
        this.employeeClient = employeeClient;
        this.organizationClient = organizationClient;
    }

    public Optional<UUID> resolveSelf(Authentication authentication) {
        return employeeClient.resolveSelfEmployeeRef();
    }

    public boolean canRead(Authentication authentication, UUID targetEmployeeRef) {
        if (hasAuthority(authentication, "attendance.read.all")) {
            return true;
        }
        Optional<UUID> self = resolveSelf(authentication);
        if (self.isPresent() && self.get().equals(targetEmployeeRef) && hasAuthority(authentication, "attendance.read.self")) {
            return true;
        }
        if (hasAuthority(authentication, "attendance.read.team") && self.isPresent()) {
            return organizationClient.resolveTeamScope(self.get()).contains(targetEmployeeRef);
        }
        return false;
    }

    /** {@code attendance.finalize.team} is the only documented finalize permission - no self/all variant exists. */
    public boolean canFinalize(Authentication authentication, UUID targetEmployeeRef) {
        return hasAuthority(authentication, "attendance.finalize.team") && isWithinCallersTeamScope(authentication, targetEmployeeRef);
    }

    /**
     * Whether {@code targetEmployeeRef} is in the caller's own reporting subtree per
     * Organization Service - independent of which specific ".team" authority the caller
     * holds, so callers such as {@link OrganizationScopeChecker} can reuse it for
     * approval permissions outside the {@code attendance.read.*} vocabulary.
     */
    public boolean isWithinCallersTeamScope(Authentication authentication, UUID targetEmployeeRef) {
        return resolveSelf(authentication)
                .map(self -> organizationClient.resolveTeamScope(self).contains(targetEmployeeRef))
                .orElse(false);
    }

    public ListScope resolveListScope(Authentication authentication) {
        if (hasAuthority(authentication, "attendance.read.all")) {
            return ListScope.all();
        }
        if (hasAuthority(authentication, "attendance.read.team")) {
            Optional<UUID> self = resolveSelf(authentication);
            if (self.isEmpty()) {
                return ListScope.denied();
            }
            return ListScope.restrictedTo(organizationClient.resolveTeamScope(self.get()));
        }
        if (hasAuthority(authentication, "attendance.read.self")) {
            Optional<UUID> self = resolveSelf(authentication);
            return self.map(id -> ListScope.restrictedTo(Set.of(id))).orElseGet(ListScope::denied);
        }
        return ListScope.denied();
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream().anyMatch(granted -> granted.getAuthority().equals(authority));
    }

    public record ListScope(boolean allowed, boolean unrestricted, Set<UUID> allowedIds) {
        public static ListScope all() { return new ListScope(true, true, Set.of()); }
        public static ListScope restrictedTo(Set<UUID> ids) { return new ListScope(true, false, ids); }
        public static ListScope denied() { return new ListScope(false, false, Set.of()); }
    }
}
