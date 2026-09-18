package com.growdigitalbridge.performance.api;

import com.growdigitalbridge.performance.api.dto.GoalDtos;
import com.growdigitalbridge.performance.api.dto.PageResponse;
import com.growdigitalbridge.performance.domain.GoalStatus;
import com.growdigitalbridge.performance.security.CurrentActor;
import com.growdigitalbridge.performance.security.CurrentCorrelation;
import com.growdigitalbridge.performance.service.GoalService;
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
@RequestMapping("/api/v1/performance/goals")
class GoalController {

    private final GoalService service;

    GoalController(GoalService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse<GoalDtos.Response> list(Authentication authentication,
                                          @RequestParam(required = false) GoalStatus status,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size,
                                          @RequestParam(required = false) String sort) {
        return service.list(authentication, status, PagingSupport.of(page, size, sort, "createdAt"));
    }

    @PostMapping
    ResponseEntity<GoalDtos.Response> create(Authentication authentication, @Valid @RequestBody GoalDtos.CreateRequest request) {
        GoalDtos.Response created = service.create(authentication, request, CurrentActor.resolve(), CurrentCorrelation.resolve());
        return ResponseEntity.created(URI.create("/api/v1/performance/goals/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    GoalDtos.Response update(@PathVariable UUID id, Authentication authentication, @Valid @RequestBody GoalDtos.UpdateRequest request) {
        return service.update(authentication, id, request, CurrentActor.resolve(), CurrentCorrelation.resolve());
    }
}
