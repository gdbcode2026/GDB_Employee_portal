package com.growdigitalbridge.payroll.api;

import com.growdigitalbridge.payroll.api.dto.PageResponse;
import com.growdigitalbridge.payroll.api.dto.StatutoryRuleDtos;
import com.growdigitalbridge.payroll.security.CurrentActor;
import com.growdigitalbridge.payroll.security.CurrentCorrelation;
import com.growdigitalbridge.payroll.service.StatutoryRuleService;
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

/**
 * Versioned statutory/tax rule management (Rule Engine task, item 13). Create/update-draft
 * requires {@code payroll.process} (configuration, maker side); activate/deactivate requires
 * {@code payroll.approve} (making a version live is an approval-weight action, checker side) -
 * the same maker-checker split {@code SecurityConfig}/Section J already establish for runs,
 * reused here rather than inventing a parallel permission.
 */
@RestController
@RequestMapping("/api/v1/payroll/statutory-rules")
class StatutoryRuleController {

    private final StatutoryRuleService service;

    StatutoryRuleController(StatutoryRuleService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<StatutoryRuleDtos.Response> create(@Valid @RequestBody StatutoryRuleDtos.CreateRequest request) {
        StatutoryRuleDtos.Response created = service.create(request, CurrentActor.resolve(), CurrentCorrelation.resolve());
        return ResponseEntity.created(URI.create("/api/v1/payroll/statutory-rules/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    StatutoryRuleDtos.Response getById(@PathVariable UUID id) {
        return service.getById(id, CurrentActor.resolve(), CurrentCorrelation.resolve());
    }

    @GetMapping
    PageResponse<StatutoryRuleDtos.Response> list(@RequestParam(required = false) String code,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size,
                                                    @RequestParam(required = false) String sort) {
        return service.list(code, PagingSupport.of(page, size, sort, "code"));
    }

    @PatchMapping("/{id}")
    StatutoryRuleDtos.Response updateDraft(@PathVariable UUID id, @Valid @RequestBody StatutoryRuleDtos.UpdateRequest request) {
        return service.updateDraft(id, request, CurrentActor.resolve(), CurrentCorrelation.resolve());
    }

    @PostMapping("/{id}/activate")
    StatutoryRuleDtos.Response activate(@PathVariable UUID id) {
        return service.activate(id, CurrentActor.resolve(), CurrentCorrelation.resolve());
    }

    @PostMapping("/{id}/deactivate")
    StatutoryRuleDtos.Response deactivate(@PathVariable UUID id) {
        return service.deactivate(id, CurrentActor.resolve(), CurrentCorrelation.resolve());
    }
}
