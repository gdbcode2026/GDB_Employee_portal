package com.growdigitalbridge.payroll.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.growdigitalbridge.payroll.security.CurrentBearerToken;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
 * I), and one employee's profile at payslip-generation time (Section M). The caller's own bearer
 * token is relayed unmodified (on-behalf-of), exactly like every other service's own
 * EmployeeClient. If Employee Service is unreachable, this fails closed (empty set/{@link
 * Optional#empty()}) rather than guessing.
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

    /**
     * Resolves the CURRENT caller's own employee reference, exactly like every other service's
     * {@code EmployeeClient.resolveSelfEmployeeRef()} (Document Service's own copy is the direct
     * precedent) - used by {@code PayslipService} to scope {@code GET /payroll/payslips/me} and
     * to authorize self-only access on the by-id/download endpoints. Fails closed (empty) if
     * unreachable or the caller has no linked employee record.
     */
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

    /**
     * Resolves one employee's profile for payslip generation (Section M), relaying the CURRENT
     * request's own bearer token - i.e. the identity that called {@code POST .../finalize}, not a
     * workload identity. This is a plain read (`GET /employees/{id}`), unlike Document Service's
     * upload/ownership model, so the existing on-behalf-of relay pattern applies unchanged; no
     * new workload-identity mechanism is introduced for Employee Service. If the finalizer lacks
     * sufficient Employee Service read scope for a given employee, or the call otherwise fails,
     * this returns empty (fails closed) rather than inventing a profile.
     */
    public Optional<EmployeeProfile> resolveEmployeeById(UUID employeeRef) {
        String token = CurrentBearerToken.resolve();
        if (token == null) {
            return Optional.empty();
        }
        try {
            EmployeeProfile profile = restClient.get()
                    .uri("/api/v1/employees/{id}", employeeRef)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(EmployeeProfile.class);
            return Optional.ofNullable(profile);
        } catch (RestClientException e) {
            log.warn("Failed to resolve employee {} from Employee Service: {}", employeeRef, e.getMessage());
            return Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EmployeeProfile(UUID id, String employeeNumber, String firstName, String lastName,
                                   EmploymentSummary employment) {

        public String fullName() {
            return (firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EmploymentSummary(String jobTitle, LocalDate startDate) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SelfResponse(UUID id) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmployeeSummary(UUID id, String status) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PageMeta(int number, int size, long total) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PageResponse(List<EmployeeSummary> items, PageMeta page) { }
}
