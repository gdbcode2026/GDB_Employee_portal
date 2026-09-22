package com.growdigitalbridge.workflow.api;

import com.growdigitalbridge.workflow.api.dto.WorkflowDefinitionDtos;
import com.growdigitalbridge.workflow.security.CurrentActor;
import com.growdigitalbridge.workflow.service.WorkflowDefinitionService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workflows/definitions")
class WorkflowDefinitionController {

    private final WorkflowDefinitionService service;

    WorkflowDefinitionController(WorkflowDefinitionService service) {
        this.service = service;
    }

    @GetMapping
    List<WorkflowDefinitionDtos.Response> list() {
        return service.list();
    }

    @PostMapping
    ResponseEntity<WorkflowDefinitionDtos.Response> create(@Valid @RequestBody WorkflowDefinitionDtos.CreateRequest request) {
        WorkflowDefinitionDtos.Response created = service.create(request, CurrentActor.resolve());
        return ResponseEntity.created(URI.create("/api/v1/workflows/definitions/" + created.id())).body(created);
    }
}
