package com.growdigitalbridge.workflow.service;

import com.growdigitalbridge.workflow.client.EmployeeClient;
import com.growdigitalbridge.workflow.client.OrganizationClient;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * The single place that resolves "self" and "team" for Workflow Service. "self" is always
 * resolved by asking Employee Service for the caller's own employee reference, and "team" is
 * always resolved by asking Organization Service for the caller's reporting subtree - there
 * is no code path anywhere that accepts a client-supplied employee or team identifier for
 * these decisions. Task-participant visibility (was the caller an assignee or delegate on a
 * specific instance's tasks?) is resolved in {@code WorkflowService} itself, since it needs
 * the task/delegation repositories rather than another remote call.
 */
@Component
public class WorkflowAccessGuard {

    private final EmployeeClient employeeClient;
    private final OrganizationClient organizationClient;

    public WorkflowAccessGuard(EmployeeClient employeeClient, OrganizationClient organizationClient) {
        this.employeeClient = employeeClient;
        this.organizationClient = organizationClient;
    }

    public Optional<UUID> resolveSelf(Authentication authentication) {
        return employeeClient.resolveSelfEmployeeRef();
    }

    public boolean isWithinCallersTeamScope(Authentication authentication, UUID targetEmployeeRef) {
        return resolveSelf(authentication)
                .map(self -> organizationClient.resolveTeamScope(self).contains(targetEmployeeRef))
                .orElse(false);
    }

    public boolean hasAuthority(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream().anyMatch(granted -> granted.getAuthority().equals(authority));
    }

    /** "self" here means "instances I started" - task-participant visibility is handled separately in WorkflowService. */
    public ReadScope resolveInstanceListScope(Authentication authentication) {
        if (hasAuthority(authentication, "workflow.read.all")) {
            return ReadScope.all();
        }
        if (hasAuthority(authentication, "workflow.read.team")) {
            Optional<UUID> self = resolveSelf(authentication);
            if (self.isEmpty()) {
                return ReadScope.denied();
            }
            return ReadScope.restrictedTo(organizationClient.resolveTeamScope(self.get()));
        }
        if (hasAuthority(authentication, "workflow.read.self")) {
            Optional<UUID> self = resolveSelf(authentication);
            return self.map(id -> ReadScope.restrictedTo(Set.of(id))).orElseGet(ReadScope::denied);
        }
        return ReadScope.denied();
    }

    public record ReadScope(boolean allowed, boolean unrestricted, Set<UUID> requesterRefs) {
        public static ReadScope all() { return new ReadScope(true, true, Set.of()); }
        public static ReadScope restrictedTo(Set<UUID> ids) { return new ReadScope(true, false, ids); }
        public static ReadScope denied() { return new ReadScope(false, false, Set.of()); }
    }
}
