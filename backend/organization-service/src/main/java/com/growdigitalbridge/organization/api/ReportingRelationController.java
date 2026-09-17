package com.growdigitalbridge.organization.api;

import com.growdigitalbridge.organization.api.dto.PageResponse;
import com.growdigitalbridge.organization.api.dto.ReportingRelationDtos;
import com.growdigitalbridge.organization.domain.ReportingRelationStatus;
import com.growdigitalbridge.organization.security.CurrentActor;
import com.growdigitalbridge.organization.service.ReportingRelationService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organization/reporting-relations")
class ReportingRelationController {

    private final ReportingRelationService service;

    ReportingRelationController(ReportingRelationService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse<ReportingRelationDtos.Response> list(@RequestParam(required = false) UUID employeeRef,
                                                        @RequestParam(required = false) UUID managerEmployeeRef,
                                                        @RequestParam(required = false) ReportingRelationStatus status,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size,
                                                        @RequestParam(required = false) String sort) {
        return service.list(employeeRef, managerEmployeeRef, status, PagingSupport.of(page, size, sort, "effectiveStartDate"));
    }

    @PostMapping
    ResponseEntity<ReportingRelationDtos.Response> create(@Valid @RequestBody ReportingRelationDtos.CreateRequest request) {
        ReportingRelationDtos.Response created = service.create(request, CurrentActor.resolve());
        return ResponseEntity.created(URI.create("/api/v1/organization/reporting-relations/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    ReportingRelationDtos.Response end(@PathVariable UUID id, @Valid @RequestBody ReportingRelationDtos.EndRequest request) {
        return service.end(id, request, CurrentActor.resolve());
    }

    /**
     * Authoritative team-scope resolver: the set of employees reporting (directly or
     * transitively) to {@code managerEmployeeRef}, computed from server-side reporting
     * data. Consumers must call this instead of trusting a client-supplied team identifier.
     */
    @GetMapping("/scope/{managerEmployeeRef}")
    ReportingRelationDtos.ScopeResponse scope(@PathVariable UUID managerEmployeeRef) {
        return service.resolveScope(managerEmployeeRef);
    }
}
