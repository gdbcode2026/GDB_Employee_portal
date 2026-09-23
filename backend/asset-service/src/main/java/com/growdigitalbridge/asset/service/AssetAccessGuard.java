package com.growdigitalbridge.asset.service;

import com.growdigitalbridge.asset.client.EmployeeClient;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * The single place that resolves "self" for Asset Service. There is no team tier anywhere in
 * this domain (RBAC.md defines no {@code asset.read.team}), so unlike every other service's
 * access guard, this one never calls Organization Service and has no team-scope method.
 */
@Component
public class AssetAccessGuard {

    private final EmployeeClient employeeClient;

    public AssetAccessGuard(EmployeeClient employeeClient) {
        this.employeeClient = employeeClient;
    }

    public Optional<UUID> resolveSelf(Authentication authentication) {
        return employeeClient.resolveSelfEmployeeRef();
    }

    public boolean hasAuthority(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream().anyMatch(granted -> granted.getAuthority().equals(authority));
    }
}
