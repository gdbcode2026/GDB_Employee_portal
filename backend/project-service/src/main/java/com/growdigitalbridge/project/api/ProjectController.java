package com.growdigitalbridge.project.api;

import com.growdigitalbridge.project.api.dto.PageResponse;
import com.growdigitalbridge.project.api.dto.ProjectDtos;
import com.growdigitalbridge.project.domain.ProjectStatus;
import com.growdigitalbridge.project.security.CurrentActor;
import com.growdigitalbridge.project.security.CurrentCorrelation;
import com.growdigitalbridge.project.service.ProjectService;
import jakarta.validation.Valid;
import java.net.URI;
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
@RequestMapping("/api/v1/projects")
class ProjectController {

    private final ProjectService service;

    ProjectController(ProjectService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse<ProjectDtos.Response> list(Authentication authentication,
                                             @RequestParam(required = false) ProjectStatus status,
                                             @RequestParam(required = false) String query,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             @RequestParam(required = false) String sort) {
        return service.list(authentication, status, query, PagingSupport.of(page, size, sort, "name"));
    }

    @PostMapping
    ResponseEntity<ProjectDtos.Response> create(Authentication authentication, @Valid @RequestBody ProjectDtos.CreateRequest request) {
        ProjectDtos.Response created = service.create(authentication, request, CurrentActor.resolve(), CurrentCorrelation.resolve());
        return ResponseEntity.created(URI.create("/api/v1/projects/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    ProjectDtos.Response getById(@PathVariable UUID id, Authentication authentication) {
        return service.getById(id, authentication);
    }

    @PatchMapping("/{id}")
    ProjectDtos.Response update(@PathVariable UUID id, @Valid @RequestBody ProjectDtos.UpdateRequest request) {
        return service.update(id, request, CurrentActor.resolve(), CurrentCorrelation.resolve());
    }
}
