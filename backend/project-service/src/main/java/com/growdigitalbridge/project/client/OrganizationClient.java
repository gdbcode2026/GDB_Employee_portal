package com.growdigitalbridge.project.client;

import com.growdigitalbridge.project.security.CurrentBearerToken;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Calls Organization Service directly (bounded, synchronous, one hop) to resolve the
 * authoritative set of employees reporting to a manager. This is the ONLY source of team
 * scope used anywhere in Project Service - no endpoint accepts a client-supplied team or
 * department identifier as an authorization input.
 *
 * The caller's own validated bearer token is relayed unmodified (on-behalf-of), never a
 * service credential or bypass. If Organization Service is unreachable or denies the
 * request, this fails closed (empty scope) rather than granting access.
 */
@Component
public class OrganizationClient {

    private static final Logger log = LoggerFactory.getLogger(OrganizationClient.class);

    private final RestClient restClient;

    public OrganizationClient(RestClient organizationRestClient) {
        this.restClient = organizationRestClient;
    }

    public Set<UUID> resolveTeamScope(UUID managerEmployeeRef) {
        String token = CurrentBearerToken.resolve();
        if (token == null) {
            return Set.of();
        }
        try {
            ScopeResponse response = restClient.get()
                    .uri("/api/v1/organization/reporting-relations/scope/{managerEmployeeRef}", managerEmployeeRef)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(ScopeResponse.class);
            return response == null || response.employeeRefs() == null ? Set.of() : Set.copyOf(response.employeeRefs());
        } catch (RestClientException e) {
            log.warn("Failed to resolve team scope from Organization Service for manager {}: {}", managerEmployeeRef, e.getMessage());
            return Set.of();
        }
    }

    private record ScopeResponse(UUID managerEmployeeRef, List<UUID> employeeRefs) { }
}
