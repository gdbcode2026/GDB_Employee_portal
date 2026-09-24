package com.growdigitalbridge.expense.api;

import com.growdigitalbridge.expense.api.dto.ExpenseClaimDtos;
import com.growdigitalbridge.expense.api.dto.PageResponse;
import com.growdigitalbridge.expense.domain.ExpenseClaimStatus;
import com.growdigitalbridge.expense.security.CurrentActor;
import com.growdigitalbridge.expense.security.CurrentCorrelation;
import com.growdigitalbridge.expense.service.ExpenseClaimService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/expenses/claims")
class ExpenseClaimController {

    private final ExpenseClaimService service;

    ExpenseClaimController(ExpenseClaimService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse<ExpenseClaimDtos.Response> list(Authentication authentication,
                                                  @RequestParam(required = false) UUID employeeId,
                                                  @RequestParam(required = false) ExpenseClaimStatus status,
                                                  @RequestParam(required = false) LocalDate from,
                                                  @RequestParam(required = false) LocalDate to,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size,
                                                  @RequestParam(required = false) String sort) {
        return service.list(authentication, employeeId, status, from, to, PagingSupport.of(page, size, sort, "createdAt"));
    }

    @GetMapping("/{id}")
    ExpenseClaimDtos.Response getById(@PathVariable UUID id, Authentication authentication) {
        return service.getById(id, authentication);
    }

    @PostMapping
    ResponseEntity<ExpenseClaimDtos.Response> create(Authentication authentication, @Valid @RequestBody ExpenseClaimDtos.CreateRequest request) {
        ExpenseClaimDtos.Response created = service.create(authentication, request, CurrentActor.resolve());
        return ResponseEntity.created(URI.create("/api/v1/expenses/claims/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    ExpenseClaimDtos.Response update(@PathVariable UUID id, Authentication authentication,
                                      @Valid @RequestBody ExpenseClaimDtos.UpdateRequest request) {
        return service.update(id, authentication, request, CurrentActor.resolve());
    }

    @PostMapping("/{id}/submit")
    ExpenseClaimDtos.Response submit(@PathVariable UUID id, Authentication authentication) {
        return service.submit(id, authentication, CurrentActor.resolve(), CurrentCorrelation.resolve());
    }

    @PostMapping("/{id}/decisions")
    ExpenseClaimDtos.Response decide(@PathVariable UUID id, Authentication authentication,
                                      @Valid @RequestBody ExpenseClaimDtos.DecisionRequest request) {
        return service.decide(id, authentication, request, CurrentActor.resolve(), CurrentCorrelation.resolve());
    }

    @PostMapping("/{id}/reimbursements")
    ExpenseClaimDtos.Response reimburse(@PathVariable UUID id) {
        return service.reimburse(id, CurrentActor.resolve());
    }
}
