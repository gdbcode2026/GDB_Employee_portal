package com.growdigitalbridge.workflow.api;

import com.growdigitalbridge.workflow.api.dto.PageResponse;
import com.growdigitalbridge.workflow.api.dto.WorkflowInstanceDtos;
import com.growdigitalbridge.workflow.security.CurrentActor;
import com.growdigitalbridge.workflow.security.CurrentCorrelation;
import com.growdigitalbridge.workflow.service.WorkflowInstanceService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workflows")
class WorkflowInstanceController {

    private final WorkflowInstanceService service;

    WorkflowInstanceController(WorkflowInstanceService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse<WorkflowInstanceDtos.Response> list(Authentication authentication,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size,
                                                      @RequestParam(required = false) String sort) {
        return service.list(authentication, PagingSupport.of(page, size, sort, "createdAt"));
    }

    @PostMapping
    ResponseEntity<WorkflowInstanceDtos.Response> start(@Valid @RequestBody WorkflowInstanceDtos.StartRequest request) {
        WorkflowInstanceDtos.Response created = service.start(request, CurrentActor.resolve());
        return ResponseEntity.created(URI.create("/api/v1/workflows/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    WorkflowInstanceDtos.Response getById(@PathVariable UUID id, Authentication authentication) {
        return service.getById(id, authentication);
    }

    @PostMapping("/{id}/cancel")
    WorkflowInstanceDtos.Response cancel(@PathVariable UUID id, Authentication authentication) {
        return service.cancel(id, authentication, CurrentActor.resolve(), CurrentCorrelation.resolve());
    }
}
