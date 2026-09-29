package com.growdigitalbridge.payroll.service;

import com.growdigitalbridge.payroll.client.EmployeeClient;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * The single place that decides whether a caller may see a given payslip (item 10). "self" is
 * always resolved by asking Employee Service for the caller's own employee reference - there is
 * no code path that accepts a client-supplied employee identifier for this decision. Unlike
 * documents, payslips have no "team" scope (item 8: "no team-wide visibility") - only self or
 * {@code payslip.read.all}, matching RBAC.md's "Employees can access only their own payslips."
 */
@Component
public class PayslipAccessGuard {

    private final EmployeeClient employeeClient;

    public PayslipAccessGuard(EmployeeClient employeeClient) {
        this.employeeClient = employeeClient;
    }

    public Optional<UUID> resolveSelf() {
        return employeeClient.resolveSelfEmployeeRef();
    }

    public boolean canView(Authentication authentication, UUID employeeRef) {
        if (hasAuthority(authentication, "payslip.read.all")) {
            return true;
        }
        return hasAuthority(authentication, "payslip.read.self")
                && resolveSelf().map(self -> self.equals(employeeRef)).orElse(false);
    }

    public boolean hasReadAll(Authentication authentication) {
        return hasAuthority(authentication, "payslip.read.all");
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(granted -> granted.getAuthority().equals(authority));
    }
}
