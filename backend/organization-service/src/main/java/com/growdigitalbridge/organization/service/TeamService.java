package com.growdigitalbridge.organization.service;

import com.growdigitalbridge.organization.api.dto.PageResponse;
import com.growdigitalbridge.organization.api.dto.TeamDtos;
import com.growdigitalbridge.organization.domain.Team;
import com.growdigitalbridge.organization.domain.TeamStatus;
import com.growdigitalbridge.organization.repository.DepartmentRepository;
import com.growdigitalbridge.organization.repository.TeamRepository;
import com.growdigitalbridge.organization.service.exception.ConflictException;
import com.growdigitalbridge.organization.service.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeamService {

    private final TeamRepository teamRepository;
    private final DepartmentRepository departmentRepository;

    public TeamService(TeamRepository teamRepository, DepartmentRepository departmentRepository) {
        this.teamRepository = teamRepository;
        this.departmentRepository = departmentRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<TeamDtos.Response> list(UUID departmentId, TeamStatus status, Pageable pageable) {
        return PageResponse.of(teamRepository.search(departmentId, status, pageable).map(this::toResponse));
    }

    @Transactional
    public TeamDtos.Response create(TeamDtos.CreateRequest request, String actor) {
        if (!departmentRepository.existsById(request.departmentId())) {
            throw new ResourceNotFoundException("Department " + request.departmentId() + " was not found.");
        }
        if (teamRepository.existsByDepartmentIdAndCode(request.departmentId(), request.code())) {
            throw new ConflictException("Team code '" + request.code() + "' already exists in this department.");
        }
        Team team = new Team(UUID.randomUUID(), request.departmentId(), request.name(), request.code(), actor, Instant.now());
        return toResponse(teamRepository.save(team));
    }

    @Transactional
    public TeamDtos.Response update(UUID id, TeamDtos.UpdateRequest request, String actor) {
        Team team = teamRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Team " + id + " was not found."));
        Instant now = Instant.now();
        if (request.code() != null && !request.code().equals(team.getCode())) {
            if (teamRepository.existsByDepartmentIdAndCodeAndIdNot(team.getDepartmentId(), request.code(), id)) {
                throw new ConflictException("Team code '" + request.code() + "' already exists in this department.");
            }
            team.recode(request.code(), actor, now);
        }
        if (request.name() != null) {
            team.rename(request.name(), actor, now);
        }
        if (request.status() != null) {
            team.changeStatus(request.status(), actor, now);
        }
        return toResponse(team);
    }

    private TeamDtos.Response toResponse(Team team) {
        return new TeamDtos.Response(team.getId(), team.getDepartmentId(), team.getName(), team.getCode(),
                team.getStatus(), team.getCreatedAt(), team.getUpdatedAt());
    }
}
