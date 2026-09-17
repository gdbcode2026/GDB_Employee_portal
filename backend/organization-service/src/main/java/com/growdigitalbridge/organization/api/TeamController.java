package com.growdigitalbridge.organization.api;

import com.growdigitalbridge.organization.api.dto.PageResponse;
import com.growdigitalbridge.organization.api.dto.TeamDtos;
import com.growdigitalbridge.organization.domain.TeamStatus;
import com.growdigitalbridge.organization.security.CurrentActor;
import com.growdigitalbridge.organization.service.TeamService;
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
@RequestMapping("/api/v1/organization/teams")
class TeamController {

    private final TeamService service;

    TeamController(TeamService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse<TeamDtos.Response> list(@RequestParam(required = false) UUID departmentId,
                                          @RequestParam(required = false) TeamStatus status,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size,
                                          @RequestParam(required = false) String sort) {
        return service.list(departmentId, status, PagingSupport.of(page, size, sort, "name"));
    }

    @PostMapping
    ResponseEntity<TeamDtos.Response> create(@Valid @RequestBody TeamDtos.CreateRequest request) {
        TeamDtos.Response created = service.create(request, CurrentActor.resolve());
        return ResponseEntity.created(URI.create("/api/v1/organization/teams/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    TeamDtos.Response update(@PathVariable UUID id, @Valid @RequestBody TeamDtos.UpdateRequest request) {
        return service.update(id, request, CurrentActor.resolve());
    }
}
