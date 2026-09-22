package com.growdigitalbridge.workflow.api;

import com.growdigitalbridge.workflow.api.dto.ApprovalTaskDtos;
import com.growdigitalbridge.workflow.api.dto.DelegationDtos;
import com.growdigitalbridge.workflow.api.dto.PageResponse;
import com.growdigitalbridge.workflow.security.CurrentActor;
import com.growdigitalbridge.workflow.security.CurrentCorrelation;
import com.growdigitalbridge.workflow.service.ApprovalTaskService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
class ApprovalTaskController {

    private final ApprovalTaskService service;

    ApprovalTaskController(ApprovalTaskService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/workflows/tasks/me")
    PageResponse<ApprovalTaskDtos.Response> listMine(Authentication authentication,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size,
                                                      @RequestParam(required = false) String sort) {
        return service.listMine(authentication, PagingSupport.of(page, size, sort, "createdAt"));
    }

    @PostMapping("/api/v1/workflows/{id}/tasks/{taskId}/decisions")
    ApprovalTaskDtos.Response decide(@PathVariable UUID id, @PathVariable UUID taskId, Authentication authentication,
                                      @Valid @RequestBody ApprovalTaskDtos.DecisionRequest request) {
        return service.decide(id, taskId, authentication, request, CurrentActor.resolve(), CurrentCorrelation.resolve());
    }

    @PostMapping("/api/v1/workflows/{id}/tasks/{taskId}/delegations")
    ResponseEntity<DelegationDtos.Response> delegate(@PathVariable UUID id, @PathVariable UUID taskId, Authentication authentication,
                                                      @Valid @RequestBody DelegationDtos.CreateRequest request) {
        DelegationDtos.Response created = service.createDelegation(id, taskId, authentication, request, CurrentActor.resolve());
        return ResponseEntity.created(URI.create("/api/v1/workflows/" + id + "/tasks/" + taskId + "/delegations/" + created.id())).body(created);
    }
}
