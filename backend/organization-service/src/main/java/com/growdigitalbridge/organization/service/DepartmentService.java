package com.growdigitalbridge.organization.service;

import com.growdigitalbridge.organization.api.dto.DepartmentDtos;
import com.growdigitalbridge.organization.api.dto.PageResponse;
import com.growdigitalbridge.organization.domain.Department;
import com.growdigitalbridge.organization.domain.DepartmentStatus;
import com.growdigitalbridge.organization.repository.DepartmentRepository;
import com.growdigitalbridge.organization.service.exception.ConflictException;
import com.growdigitalbridge.organization.service.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepartmentService {

    private final DepartmentRepository repository;

    public DepartmentService(DepartmentRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResponse<DepartmentDtos.Response> list(DepartmentStatus status, String query, Pageable pageable) {
        return PageResponse.of(repository.search(status, query, pageable).map(this::toResponse));
    }

    @Transactional
    public DepartmentDtos.Response create(DepartmentDtos.CreateRequest request, String actor) {
        if (repository.existsByCode(request.code())) {
            throw new ConflictException("Department code '" + request.code() + "' already exists.");
        }
        Department department = new Department(UUID.randomUUID(), request.name(), request.code(), actor, Instant.now());
        return toResponse(repository.save(department));
    }

    @Transactional
    public DepartmentDtos.Response update(UUID id, DepartmentDtos.UpdateRequest request, String actor) {
        Department department = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department " + id + " was not found."));
        Instant now = Instant.now();
        if (request.code() != null && !request.code().equals(department.getCode())) {
            if (repository.existsByCodeAndIdNot(request.code(), id)) {
                throw new ConflictException("Department code '" + request.code() + "' already exists.");
            }
            department.recode(request.code(), actor, now);
        }
        if (request.name() != null) {
            department.rename(request.name(), actor, now);
        }
        if (request.status() != null) {
            department.changeStatus(request.status(), actor, now);
        }
        return toResponse(department);
    }

    private DepartmentDtos.Response toResponse(Department department) {
        return new DepartmentDtos.Response(department.getId(), department.getName(), department.getCode(),
                department.getStatus(), department.getCreatedAt(), department.getUpdatedAt());
    }
}
