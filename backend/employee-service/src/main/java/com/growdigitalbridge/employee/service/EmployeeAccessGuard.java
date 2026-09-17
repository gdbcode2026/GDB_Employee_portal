package com.growdigitalbridge.employee.service;

import com.growdigitalbridge.employee.client.OrganizationClient;
import com.growdigitalbridge.employee.domain.Employee;
import com.growdigitalbridge.employee.repository.EmployeeRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * The single place that decides whether a caller may see a given employee record.
 * {@code team} visibility is always resolved by asking Organization Service for the
 * caller's reporting subtree - there is no code path anywhere that accepts a
 * client-supplied team or department identifier for this decision.
 */
@Component
public class EmployeeAccessGuard {

    private final EmployeeRepository employeeRepository;
    private final OrganizationClient organizationClient;

    public EmployeeAccessGuard(EmployeeRepository employeeRepository, OrganizationClient organizationClient) {
        this.employeeRepository = employeeRepository;
        this.organizationClient = organizationClient;
    }

    /** Maps the caller's validated JWT subject to their own employee record, if one is linked. */
    public Optional<Employee> resolveSelf(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return employeeRepository.findByIdentitySubject(jwtAuthentication.getToken().getSubject());
        }
        return Optional.empty();
    }

    public boolean canRead(Authentication authentication, Employee target) {
        if (hasAuthority(authentication, "employee.read.all")) {
            return true;
        }
        Optional<Employee> self = resolveSelf(authentication);
        boolean isSelf = self.map(Employee::getId).map(id -> id.equals(target.getId())).orElse(false);
        if (isSelf && hasAuthority(authentication, "employee.read.self")) {
            return true;
        }
        if (hasAuthority(authentication, "employee.read.team") && self.isPresent()) {
            return organizationClient.resolveTeamScope(self.get().getId()).contains(target.getId());
        }
        return false;
    }

    public ListScope resolveListScope(Authentication authentication) {
        if (hasAuthority(authentication, "employee.read.all")) {
            return ListScope.all();
        }
        if (hasAuthority(authentication, "employee.read.team")) {
            Optional<Employee> self = resolveSelf(authentication);
            if (self.isEmpty()) {
                return ListScope.denied();
            }
            return ListScope.restrictedTo(organizationClient.resolveTeamScope(self.get().getId()));
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
