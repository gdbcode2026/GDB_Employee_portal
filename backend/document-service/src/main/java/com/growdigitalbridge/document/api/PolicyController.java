package com.growdigitalbridge.document.api;

import com.growdigitalbridge.document.api.dto.PolicyDtos;
import com.growdigitalbridge.document.security.CurrentActor;
import com.growdigitalbridge.document.service.PolicyService;
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
@RequestMapping("/api/v1/policies")
class PolicyController {

    private final PolicyService service;

    PolicyController(PolicyService service) {
        this.service = service;
    }

    @GetMapping
    List<PolicyDtos.Response> list() {
        return service.list();
    }

    @PostMapping
    ResponseEntity<PolicyDtos.Response> create(@Valid @RequestBody PolicyDtos.CreateRequest request) {
        PolicyDtos.Response created = service.create(request, CurrentActor.resolve());
        return ResponseEntity.created(URI.create("/api/v1/policies/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    PolicyDtos.Response update(@PathVariable UUID id, @Valid @RequestBody PolicyDtos.UpdateRequest request) {
        return service.update(id, request, CurrentActor.resolve());
    }
}
