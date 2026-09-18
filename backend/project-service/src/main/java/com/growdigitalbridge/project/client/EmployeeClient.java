package com.growdigitalbridge.project.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.growdigitalbridge.project.security.CurrentBearerToken;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Calls Employee Service directly (bounded, synchronous, one hop - the "Employee lookup while
 * creating a domain record" case documented in docs/architecture/COMMUNICATION.md) to map the
 * caller's own validated identity to their employee reference. Project owns no employee
 * profile data itself, so this is the only way any endpoint here learns "who is the caller"
 * as an employee ID.
 *
 * The caller's own bearer token is relayed unmodified (on-behalf-of). If Employee Service is
 * unreachable or the caller has no linked employee record, this fails closed (empty result).
 */
@Component
public class EmployeeClient {

    private static final Logger log = LoggerFactory.getLogger(EmployeeClient.class);

    private final RestClient restClient;

    public EmployeeClient(RestClient employeeRestClient) {
        this.restClient = employeeRestClient;
    }

    public Optional<UUID> resolveSelfEmployeeRef() {
        String token = CurrentBearerToken.resolve();
        if (token == null) {
            return Optional.empty();
        }
        try {
            SelfResponse response = restClient.get()
                    .uri("/api/v1/employees/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(SelfResponse.class);
            return response == null ? Optional.empty() : Optional.ofNullable(response.id());
        } catch (RestClientException e) {
            log.warn("Failed to resolve caller's employee reference from Employee Service: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SelfResponse(UUID id) { }
}
