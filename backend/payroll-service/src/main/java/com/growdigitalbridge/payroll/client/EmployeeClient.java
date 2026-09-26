package com.growdigitalbridge.payroll.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.growdigitalbridge.payroll.security.CurrentBearerToken;
import java.util.ArrayList;
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
 * Calls Employee Service directly (bounded, synchronous, one hop - the "Employee lookup while
 * creating a domain record" case documented in docs/architecture/COMMUNICATION.md) to resolve
 * the current active-employee set at payroll-run creation time (PAYROLL_REQUIREMENTS.md Section
 * I). The caller's own bearer token is relayed unmodified (on-behalf-of), exactly like every
 * other service's own EmployeeClient. If Employee Service is unreachable, this fails closed
 * (empty set) rather than guessing a population.
 */
@Component
public class EmployeeClient {

    private static final Logger log = LoggerFactory.getLogger(EmployeeClient.class);
    private static final int PAGE_SIZE = 200;

    private final RestClient restClient;

    public EmployeeClient(RestClient employeeRestClient) {
        this.restClient = employeeRestClient;
    }

    /** Pages through every {@code ACTIVE} employee. Returns an empty set on any failure (fail closed). */
    public Set<UUID> resolveActiveEmployeeRefs() {
        String token = CurrentBearerToken.resolve();
        if (token == null) {
            return Set.of();
        }
        try {
            List<UUID> refs = new ArrayList<>();
            int page = 0;
            long total;
            do {
                PageResponse response = restClient.get()
                        .uri("/api/v1/employees?status=ACTIVE&page={page}&size={size}", page, PAGE_SIZE)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .retrieve()
                        .body(PageResponse.class);
                if (response == null || response.items() == null) {
                    break;
                }
                response.items().forEach(item -> refs.add(item.id()));
                total = response.page() == null ? refs.size() : response.page().total();
                page++;
            } while ((long) refs.size() < total);
            return Set.copyOf(refs);
        } catch (RestClientException e) {
            log.warn("Failed to resolve the active-employee set from Employee Service: {}", e.getMessage());
            return Set.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmployeeSummary(UUID id, String status) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PageMeta(int number, int size, long total) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PageResponse(List<EmployeeSummary> items, PageMeta page) { }
}
