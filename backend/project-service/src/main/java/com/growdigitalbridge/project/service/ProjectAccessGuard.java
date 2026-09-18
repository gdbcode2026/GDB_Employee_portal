package com.growdigitalbridge.project.service;

import com.growdigitalbridge.project.client.EmployeeClient;
import com.growdigitalbridge.project.client.OrganizationClient;
import com.growdigitalbridge.project.domain.MembershipStatus;
import com.growdigitalbridge.project.repository.ProjectMembershipRepository;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * The single place that decides whether a caller may see or act on a given project/task.
 * "self" is always resolved by asking Employee Service for the caller's own employee
 * reference, and "team" is always resolved by asking Organization Service for the caller's
 * reporting subtree - there is no code path anywhere that accepts a client-supplied
 * employee, team, or project-membership identifier for these decisions.
 *
 * {@code project.manage} is treated as authority over every project (including its tasks),
 * since API.md's "Read/manage per membership" wording only qualifies plain {@code
 * project.read}, and RBAC.md defines no separate {@code task.manage.all} permission for an
 * administrator to fall back on.
 */
@Component
public class ProjectAccessGuard {

    private final EmployeeClient employeeClient;
    private final OrganizationClient organizationClient;
    private final ProjectMembershipRepository membershipRepository;

    public ProjectAccessGuard(EmployeeClient employeeClient, OrganizationClient organizationClient,
                               ProjectMembershipRepository membershipRepository) {
        this.employeeClient = employeeClient;
        this.organizationClient = organizationClient;
        this.membershipRepository = membershipRepository;
    }

    public Optional<UUID> resolveSelf(Authentication authentication) {
        return employeeClient.resolveSelfEmployeeRef();
    }

    public boolean canManageProjects(Authentication authentication) {
        return hasAuthority(authentication, "project.manage");
    }

    public boolean isActiveMember(UUID projectId, UUID employeeRef) {
        return membershipRepository.existsByProjectIdAndEmployeeRefAndStatus(projectId, employeeRef, MembershipStatus.ACTIVE);
    }

    /** Whether the caller may view a specific project: project.manage sees everything, project.read requires membership. */
    public boolean canViewProject(Authentication authentication, UUID projectId) {
        if (canManageProjects(authentication)) {
            return true;
        }
        if (!hasAuthority(authentication, "project.read")) {
            return false;
        }
        return resolveSelf(authentication).map(self -> isActiveMember(projectId, self)).orElse(false);
    }

    public ProjectListScope resolveProjectListScope(Authentication authentication) {
        if (canManageProjects(authentication)) {
            return ProjectListScope.all();
        }
        if (hasAuthority(authentication, "project.read")) {
            Optional<UUID> self = resolveSelf(authentication);
            if (self.isEmpty()) {
                return ProjectListScope.denied();
            }
            return ProjectListScope.restrictedTo(membershipRepository.findProjectIdsByEmployeeRefAndStatus(self.get(), MembershipStatus.ACTIVE));
        }
        return ProjectListScope.denied();
    }

    /**
     * Resolves the set of employee references whose tasks the caller may see/act on, from
     * whichever of task.manage.self/team the caller holds (a Team Lead typically holds both,
     * per RBAC.md's role mapping, so the sets are unioned). {@code project.manage} bypasses
     * this entirely (unrestricted).
     */
    public TaskScope resolveTaskScope(Authentication authentication) {
        if (canManageProjects(authentication)) {
            return TaskScope.all();
        }
        boolean hasSelf = hasAuthority(authentication, "task.manage.self");
        boolean hasTeam = hasAuthority(authentication, "task.manage.team");
        if (!hasSelf && !hasTeam) {
            return TaskScope.denied();
        }
        Optional<UUID> self = resolveSelf(authentication);
        if (self.isEmpty()) {
            return TaskScope.denied();
        }
        Set<UUID> assigneeRefs = new HashSet<>();
        if (hasSelf) {
            assigneeRefs.add(self.get());
        }
        if (hasTeam) {
            assigneeRefs.addAll(organizationClient.resolveTeamScope(self.get()));
        }
        return TaskScope.restrictedTo(assigneeRefs);
    }

    /** Whether the caller may act on a task assigned to {@code assigneeRef} (may be null for an unassigned task). */
    public boolean canAccessTask(Authentication authentication, UUID assigneeRef) {
        if (canManageProjects(authentication)) {
            return true;
        }
        TaskScope scope = resolveTaskScope(authentication);
        return scope.allowed() && assigneeRef != null && scope.assigneeRefs().contains(assigneeRef);
    }

    /**
     * Whether the caller may change any field of a task assigned to {@code assigneeRef} -
     * project.manage or a team-scope match. A self-only match (the assignee updating their
     * own task) is deliberately excluded here: TaskService restricts that case to status/
     * priority changes only, since an individual contributor reassigning or retitling their
     * own task is not a documented capability.
     */
    public boolean canFullyManageTask(Authentication authentication, UUID assigneeRef) {
        if (canManageProjects(authentication)) {
            return true;
        }
        if (!hasAuthority(authentication, "task.manage.team") || assigneeRef == null) {
            return false;
        }
        return resolveSelf(authentication)
                .map(self -> organizationClient.resolveTeamScope(self).contains(assigneeRef))
                .orElse(false);
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream().anyMatch(granted -> granted.getAuthority().equals(authority));
    }

    public record ProjectListScope(boolean allowed, boolean unrestricted, Set<UUID> allowedProjectIds) {
        public static ProjectListScope all() { return new ProjectListScope(true, true, Set.of()); }
        public static ProjectListScope restrictedTo(Set<UUID> ids) { return new ProjectListScope(true, false, ids); }
        public static ProjectListScope denied() { return new ProjectListScope(false, false, Set.of()); }
    }

    public record TaskScope(boolean allowed, boolean unrestricted, Set<UUID> assigneeRefs) {
        public static TaskScope all() { return new TaskScope(true, true, Set.of()); }
        public static TaskScope restrictedTo(Set<UUID> ids) { return new TaskScope(true, false, ids); }
        public static TaskScope denied() { return new TaskScope(false, false, Set.of()); }
    }
}
