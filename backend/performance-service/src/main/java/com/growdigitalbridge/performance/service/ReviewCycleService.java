package com.growdigitalbridge.performance.service;

import com.growdigitalbridge.performance.api.dto.ReviewCycleDtos;
import com.growdigitalbridge.performance.domain.ReviewCycle;
import com.growdigitalbridge.performance.repository.ReviewCycleRepository;
import com.growdigitalbridge.performance.service.exception.InvalidRequestException;
import com.growdigitalbridge.performance.service.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewCycleService {

    private final ReviewCycleRepository repository;

    public ReviewCycleService(ReviewCycleRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ReviewCycleDtos.Response create(ReviewCycleDtos.CreateRequest request, String actor) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new InvalidRequestException("The end date cannot precede the start date.");
        }
        Instant now = Instant.now();
        ReviewCycle cycle = new ReviewCycle(UUID.randomUUID(), request.name(), request.startDate(), request.endDate(), actor, now);
        repository.save(cycle);
        return toResponse(cycle);
    }

    @Transactional(readOnly = true)
    public List<ReviewCycleDtos.Response> list() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public ReviewCycleDtos.Response update(UUID id, ReviewCycleDtos.UpdateRequest request, String actor) {
        ReviewCycle cycle = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review cycle " + id + " was not found."));
        String name = request.name() != null ? request.name() : cycle.getName();
        var startDate = request.startDate() != null ? request.startDate() : cycle.getStartDate();
        var endDate = request.endDate() != null ? request.endDate() : cycle.getEndDate();
        if (endDate.isBefore(startDate)) {
            throw new InvalidRequestException("The end date cannot precede the start date.");
        }
        Instant now = Instant.now();
        cycle.updateDetails(name, startDate, endDate, actor, now);
        if (request.status() != null && request.status() != cycle.getStatus()) {
            cycle.changeStatus(request.status(), actor, now);
        }
        return toResponse(cycle);
    }

    private ReviewCycleDtos.Response toResponse(ReviewCycle cycle) {
        return new ReviewCycleDtos.Response(cycle.getId(), cycle.getName(), cycle.getStartDate(), cycle.getEndDate(),
                cycle.getStatus(), cycle.getCreatedAt(), cycle.getUpdatedAt());
    }
}
