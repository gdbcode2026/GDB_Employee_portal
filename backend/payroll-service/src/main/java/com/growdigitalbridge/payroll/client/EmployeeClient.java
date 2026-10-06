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
 * the employee set eligible for a payroll run at run-creation time (PAYROLL_REQUIREMENTS.md
 * Section I), and one employee's profile at payslip-generation time (Section M). The caller's own
 * bearer token is relayed unmodified (on-behalf-of), exactly like every other service's own
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
            return Set.copyOf(fetchEmployeeRefsByStatus(token, "ACTIVE"));
        } catch (RestClientException e) {
            log.warn("Failed to resolve the active-employee set from Employee Service: {}", e.getMessage());
            return Set.of();
        }
    }

    /**
     * Resolves every employee eligible for a payroll run covering {@code [periodStart,
     * periodEnd]} (Payroll V1 terminated-employee handling, GDB business decision Option A: "a
     * terminated employee must remain eligible for the payroll period in which they worked").
     * Eligibility is: every currently {@code ACTIVE} employee (unchanged - a still-active
     * employee is always included), plus any {@code INACTIVE} employee whose employment
     * ({@code GET /employees/{id}}'s existing {@code employment.startDate}/{@code endDate} -
     * already-existing fields, nothing new invented) overlapped the period: {@code startDate <=
     * periodEnd} and ({@code endDate} is {@code null} or {@code endDate >= periodStart}). An
     * employee who terminated before the period began, or who joined after it ended, is
     * correctly excluded either way.
     *
     * <p>Uses only Employee Service's already-existing {@code GET /employees?status=...} and
     * {@code GET /employees/{id}} endpoints - no new Employee Service capability, no cross-service
     * database access. Fails closed per candidate: an {@code INACTIVE} employee whose employment
     * detail cannot be resolved (Employee Service error, missing employment) is excluded, never
     * guessed into eligibility.
     */
    public Set<UUID> resolveEmployeeRefsEligibleForPeriod(LocalDate periodStart, LocalDate periodEnd) {
        String token = CurrentBearerToken.resolve();
        if (token == null) {
            return Set.of();
        }
        try {
            List<UUID> eligible = new ArrayList<>(fetchEmployeeRefsByStatus(token, "ACTIVE"));
            for (UUID candidate : fetchEmployeeRefsByStatus(token, "INACTIVE")) {
                resolveEmployeeById(candidate)
                        .map(EmployeeProfile::employment)
                        .filter(employment -> overlapsPeriod(employment, periodStart, periodEnd))
                        .ifPresent(employment -> eligible.add(candidate));
            }
            return Set.copyOf(eligible);
        } catch (RestClientException e) {
            log.warn("Failed to resolve the payroll-eligible employee set from Employee Service: {}", e.getMessage());
            return Set.of();
        }
    }

    private boolean overlapsPeriod(EmploymentSummary employment, LocalDate periodStart, LocalDate periodEnd) {
        if (employment == null || employment.startDate() == null) {
            return false;
        }
        boolean startedOnOrBeforePeriodEnd = !employment.startDate().isAfter(periodEnd);
        boolean stillOngoingOrEndedOnOrAfterPeriodStart =
                employment.endDate() == null || !employment.endDate().isBefore(periodStart);
        return startedOnOrBeforePeriodEnd && stillOngoingOrEndedOnOrAfterPeriodStart;
    }

    private List<UUID> fetchEmployeeRefsByStatus(String token, String status) {
        List<UUID> refs = new ArrayList<>();
        int page = 0;
        long total;
        do {
            PageResponse response = restClient.get()
                    .uri("/api/v1/employees?status={status}&page={page}&size={size}", status, page, PAGE_SIZE)
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
        return refs;
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
    public record EmploymentSummary(String jobTitle, LocalDate startDate, LocalDate endDate) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SelfResponse(UUID id) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmployeeSummary(UUID id, String status) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PageMeta(int number, int size, long total) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PageResponse(List<EmployeeSummary> items, PageMeta page) { }
}
