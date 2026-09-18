package com.growdigitalbridge.leave.service;

import com.growdigitalbridge.leave.api.dto.LeaveTypeDtos;
import com.growdigitalbridge.leave.domain.LeaveType;
import com.growdigitalbridge.leave.repository.LeaveTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only access to Flyway-seeded leave type reference data; see V1 migration for why there is no write path yet. */
@Service
public class LeaveTypeService {

    private final LeaveTypeRepository repository;

    public LeaveTypeService(LeaveTypeRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public java.util.List<LeaveTypeDtos.Response> listActive() {
        return repository.findByActiveTrue().stream().map(this::toResponse).toList();
    }

    private LeaveTypeDtos.Response toResponse(LeaveType type) {
        return new LeaveTypeDtos.Response(type.getId(), type.getCode(), type.getName());
    }
}
