package com.growdigitalbridge.performance.api;

import com.growdigitalbridge.performance.api.dto.ReviewCycleDtos;
import com.growdigitalbridge.performance.security.CurrentActor;
import com.growdigitalbridge.performance.service.ReviewCycleService;
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
@RequestMapping("/api/v1/performance/cycles")
class ReviewCycleController {

    private final ReviewCycleService service;

    ReviewCycleController(ReviewCycleService service) {
        this.service = service;
    }

    @GetMapping
    List<ReviewCycleDtos.Response> list() {
        return service.list();
    }

    @PostMapping
    ResponseEntity<ReviewCycleDtos.Response> create(@Valid @RequestBody ReviewCycleDtos.CreateRequest request) {
        ReviewCycleDtos.Response created = service.create(request, CurrentActor.resolve());
        return ResponseEntity.created(URI.create("/api/v1/performance/cycles/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    ReviewCycleDtos.Response update(@PathVariable UUID id, @Valid @RequestBody ReviewCycleDtos.UpdateRequest request) {
        return service.update(id, request, CurrentActor.resolve());
    }
}
