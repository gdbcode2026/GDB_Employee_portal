package com.growdigitalbridge.organization.api;

import com.growdigitalbridge.organization.api.dto.DepartmentDtos;
import com.growdigitalbridge.organization.api.dto.PageResponse;
import com.growdigitalbridge.organization.domain.DepartmentStatus;
import com.growdigitalbridge.organization.security.CurrentActor;
import com.growdigitalbridge.organization.service.DepartmentService;
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
@RequestMapping("/api/v1/organization/departments")
class DepartmentController {

    private final DepartmentService service;

    DepartmentController(DepartmentService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse<DepartmentDtos.Response> list(@RequestParam(required = false) DepartmentStatus status,
                                                @RequestParam(required = false) String query,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size,
                                                @RequestParam(required = false) String sort) {
        return service.list(status, query, PagingSupport.of(page, size, sort, "name"));
    }

    @PostMapping
    ResponseEntity<DepartmentDtos.Response> create(@Valid @RequestBody DepartmentDtos.CreateRequest request) {
        DepartmentDtos.Response created = service.create(request, CurrentActor.resolve());
        return ResponseEntity.created(URI.create("/api/v1/organization/departments/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    DepartmentDtos.Response update(@PathVariable UUID id, @Valid @RequestBody DepartmentDtos.UpdateRequest request) {
        return service.update(id, request, CurrentActor.resolve());
    }
}
