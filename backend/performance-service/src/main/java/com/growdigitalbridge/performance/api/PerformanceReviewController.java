package com.growdigitalbridge.performance.api;

import com.growdigitalbridge.performance.api.dto.PageResponse;
import com.growdigitalbridge.performance.api.dto.PerformanceReviewDtos;
import com.growdigitalbridge.performance.security.CurrentActor;
import com.growdigitalbridge.performance.security.CurrentCorrelation;
import com.growdigitalbridge.performance.service.PerformanceReviewService;
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
@RequestMapping("/api/v1/performance/reviews")
class PerformanceReviewController {

    private final PerformanceReviewService service;

    PerformanceReviewController(PerformanceReviewService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse<PerformanceReviewDtos.Response> list(Authentication authentication,
                                                        @RequestParam(required = false) UUID cycleId,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size,
                                                        @RequestParam(required = false) String sort) {
        return service.list(authentication, cycleId, PagingSupport.of(page, size, sort, "createdAt"));
    }

    @PostMapping
    ResponseEntity<PerformanceReviewDtos.Response> create(Authentication authentication,
                                                           @Valid @RequestBody PerformanceReviewDtos.CreateRequest request) {
        PerformanceReviewDtos.Response created = service.create(authentication, request, CurrentActor.resolve(), CurrentCorrelation.resolve());
        return ResponseEntity.created(URI.create("/api/v1/performance/reviews/" + created.id())).body(created);
    }

    @PostMapping("/{id}/submit")
    PerformanceReviewDtos.Response submit(@PathVariable UUID id, Authentication authentication,
                                           @Valid @RequestBody PerformanceReviewDtos.SubmitRequest request) {
        return service.submit(authentication, id, request, CurrentActor.resolve(), CurrentCorrelation.resolve());
    }
}
