package com.growdigitalbridge.performance.service;

import com.growdigitalbridge.performance.client.EmployeeClient;
import com.growdigitalbridge.performance.client.OrganizationClient;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * The single place that decides whether a caller may see or act on a given employee's goals
 * or performance reviews. "self" is always resolved by asking Employee Service for the
 * caller's own employee reference, and "team" is always resolved by asking Organization
 * Service for the caller's reporting subtree - there is no code path anywhere that accepts a
 * client-supplied employee or team identifier for these decisions.
 *
 * RBAC.md defines no distinct self-review permission and no goal-manage.team/all permission:
 * review creation/submission is gated purely by whether the caller IS the assigned reviewer
 * (which a caller can arrange for themselves via self-review) or by {@code performance.manage};
 * goal mutation is deliberately self-only.
 */
@Component
public class PerformanceAccessGuard {

    private final EmployeeClient employeeClient;
    private final OrganizationClient organizationClient;

    public PerformanceAccessGuard(EmployeeClient employeeClient, OrganizationClient organizationClient) {
        this.employeeClient = employeeClient;
        this.organizationClient = organizationClient;
    }

    public Optional<UUID> resolveSelf(Authentication authentication) {
        return employeeClient.resolveSelfEmployeeRef();
    }

    public boolean canManageAll(Authentication authentication) {
        return hasAuthority(authentication, "performance.manage");
    }

    /** Shared self/team/all resolution for reading goals, keyed purely on employeeRef. */
    public ReadScope resolveGoalReadScope(Authentication authentication) {
        if (hasAuthority(authentication, "performance.read.all")) {
            return ReadScope.all();
        }
        if (hasAuthority(authentication, "performance.read.team")) {
            Optional<UUID> self = resolveSelf(authentication);
            if (self.isEmpty()) {
                return ReadScope.denied();
            }
            return ReadScope.restrictedTo(organizationClient.resolveTeamScope(self.get()));
        }
        if (hasAuthority(authentication, "performance.read.self")) {
            Optional<UUID> self = resolveSelf(authentication);
            return self.map(id -> ReadScope.restrictedTo(Set.of(id))).orElseGet(ReadScope::denied);
        }
        return ReadScope.denied();
    }

    /**
     * Reviews additionally treat "self" as "about me OR assigned to me to give" (see
     * PerformanceReviewRepository#searchForSelf), which a flat employeeRef set cannot express,
     * so this is a distinct scope type from {@link ReadScope}.
     */
    public ReviewReadScope resolveReviewReadScope(Authentication authentication) {
        if (hasAuthority(authentication, "performance.read.all")) {
            return ReviewReadScope.all();
        }
        if (hasAuthority(authentication, "performance.read.team")) {
            Optional<UUID> self = resolveSelf(authentication);
            if (self.isEmpty()) {
                return ReviewReadScope.denied();
            }
            return ReviewReadScope.team(organizationClient.resolveTeamScope(self.get()));
        }
        if (hasAuthority(authentication, "performance.read.self")) {
            Optional<UUID> self = resolveSelf(authentication);
            return self.map(ReviewReadScope::self).orElseGet(ReviewReadScope::denied);
        }
        return ReviewReadScope.denied();
    }

    /** Whether the caller may create a review of {@code employeeRef}: self-review, team scope, or admin override. */
    public boolean canReview(Authentication authentication, UUID employeeRef) {
        if (canManageAll(authentication)) {
            return true;
        }
        Optional<UUID> self = resolveSelf(authentication);
        if (self.isEmpty()) {
            return false;
        }
        if (self.get().equals(employeeRef)) {
            return true;
        }
        return organizationClient.resolveTeamScope(self.get()).contains(employeeRef);
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream().anyMatch(granted -> granted.getAuthority().equals(authority));
    }

    public record ReadScope(boolean allowed, boolean unrestricted, Set<UUID> employeeRefs) {
        public static ReadScope all() { return new ReadScope(true, true, Set.of()); }
        public static ReadScope restrictedTo(Set<UUID> ids) { return new ReadScope(true, false, ids); }
        public static ReadScope denied() { return new ReadScope(false, false, Set.of()); }
    }

    public enum ReviewReadLevel { SELF, TEAM, ALL, DENIED }

    public record ReviewReadScope(ReviewReadLevel level, UUID self, Set<UUID> teamIds) {
        public static ReviewReadScope all() { return new ReviewReadScope(ReviewReadLevel.ALL, null, Set.of()); }
        public static ReviewReadScope team(Set<UUID> teamIds) { return new ReviewReadScope(ReviewReadLevel.TEAM, null, teamIds); }
        public static ReviewReadScope self(UUID self) { return new ReviewReadScope(ReviewReadLevel.SELF, self, Set.of()); }
        public static ReviewReadScope denied() { return new ReviewReadScope(ReviewReadLevel.DENIED, null, Set.of()); }
        public boolean allowed() { return level != ReviewReadLevel.DENIED; }
    }
}
