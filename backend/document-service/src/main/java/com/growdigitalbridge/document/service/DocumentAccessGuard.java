package com.growdigitalbridge.document.service;

import com.growdigitalbridge.document.client.EmployeeClient;
import com.growdigitalbridge.document.client.OrganizationClient;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * The single place that decides whether a caller may see or act on a given document. "self"
 * is always resolved by asking Employee Service for the caller's own employee reference, and
 * "team" is always resolved by asking Organization Service for the caller's reporting
 * subtree - there is no code path anywhere that accepts a client-supplied employee or team
 * identifier for these decisions.
 */
@Component
public class DocumentAccessGuard {

    private final EmployeeClient employeeClient;
    private final OrganizationClient organizationClient;

    public DocumentAccessGuard(EmployeeClient employeeClient, OrganizationClient organizationClient) {
        this.employeeClient = employeeClient;
        this.organizationClient = organizationClient;
    }

    public Optional<UUID> resolveSelf(Authentication authentication) {
        return employeeClient.resolveSelfEmployeeRef();
    }

    /** {@code document.manage} grants unrestricted visibility, matching project.manage/performance.manage elsewhere. */
    public boolean canView(Authentication authentication, UUID ownerRef) {
        if (hasAuthority(authentication, "document.read.all") || hasAuthority(authentication, "document.manage")) {
            return true;
        }
        Optional<UUID> self = resolveSelf(authentication);
        if (self.isPresent() && self.get().equals(ownerRef) && hasAuthority(authentication, "document.read.self")) {
            return true;
        }
        if (hasAuthority(authentication, "document.read.team") && self.isPresent()) {
            return organizationClient.resolveTeamScope(self.get()).contains(ownerRef);
        }
        return false;
    }

    /**
     * Only the uploader may complete their own upload, unless the caller holds the {@code
     * document.manage} override, or the caller is the same authorized workload identity that
     * created this document via {@code POST /documents/workload-uploads} (there is no "self" to
     * resolve for a workload, so completion is instead authorized by comparing the acting
     * identity to the document's own recorded creator).
     */
    public boolean canComplete(Authentication authentication, UUID ownerRef, String actor, String documentCreatedBy) {
        if (hasAuthority(authentication, "document.manage")) {
            return true;
        }
        if (hasAuthority(authentication, "workload.document.upload") && Objects.equals(actor, documentCreatedBy)) {
            return true;
        }
        return resolveSelf(authentication).map(self -> self.equals(ownerRef)).orElse(false);
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream().anyMatch(granted -> granted.getAuthority().equals(authority));
    }
}
