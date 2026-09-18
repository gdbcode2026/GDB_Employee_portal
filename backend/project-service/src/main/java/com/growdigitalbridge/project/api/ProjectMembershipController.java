package com.growdigitalbridge.project.api;

import com.growdigitalbridge.project.api.dto.ProjectMembershipDtos;
import com.growdigitalbridge.project.security.CurrentActor;
import com.growdigitalbridge.project.security.CurrentCorrelation;
import com.growdigitalbridge.project.service.ProjectMembershipService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/members")
class ProjectMembershipController {

    private final ProjectMembershipService service;

    ProjectMembershipController(ProjectMembershipService service) {
        this.service = service;
    }

    @GetMapping
    List<ProjectMembershipDtos.Response> list(@PathVariable UUID projectId) {
        return service.list(projectId);
    }

    @PostMapping
    ResponseEntity<ProjectMembershipDtos.Response> add(@PathVariable UUID projectId, @Valid @RequestBody ProjectMembershipDtos.AddRequest request) {
        ProjectMembershipDtos.Response created = service.add(projectId, request, CurrentActor.resolve(), CurrentCorrelation.resolve());
        return ResponseEntity.created(URI.create("/api/v1/projects/" + projectId + "/members/" + created.id())).body(created);
    }

    @PatchMapping("/{memberId}")
    ProjectMembershipDtos.Response update(@PathVariable UUID projectId, @PathVariable UUID memberId,
                                           @Valid @RequestBody ProjectMembershipDtos.UpdateRequest request) {
        return service.update(projectId, memberId, request, CurrentActor.resolve(), CurrentCorrelation.resolve());
    }
}
