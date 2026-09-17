package com.growdigitalbridge.organization.service;

import com.growdigitalbridge.organization.api.dto.OrganizationChartResponse;
import com.growdigitalbridge.organization.api.dto.OrganizationChartResponse.DepartmentNode;
import com.growdigitalbridge.organization.api.dto.OrganizationChartResponse.TeamSummary;
import com.growdigitalbridge.organization.domain.Department;
import com.growdigitalbridge.organization.domain.DepartmentStatus;
import com.growdigitalbridge.organization.domain.Team;
import com.growdigitalbridge.organization.domain.TeamStatus;
import com.growdigitalbridge.organization.repository.DepartmentRepository;
import com.growdigitalbridge.organization.repository.TeamRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationChartService {

    private final DepartmentRepository departmentRepository;
    private final TeamRepository teamRepository;

    public OrganizationChartService(DepartmentRepository departmentRepository, TeamRepository teamRepository) {
        this.departmentRepository = departmentRepository;
        this.teamRepository = teamRepository;
    }

    @Transactional(readOnly = true)
    public OrganizationChartResponse buildChart() {
        List<Department> departments = departmentRepository.findByStatusOrderByNameAsc(DepartmentStatus.ACTIVE);
        Map<UUID, List<Team>> teamsByDepartment = teamRepository.findByStatus(TeamStatus.ACTIVE).stream()
                .collect(Collectors.groupingBy(Team::getDepartmentId));

        List<DepartmentNode> nodes = departments.stream()
                .map(department -> new DepartmentNode(
                        department.getId(),
                        department.getName(),
                        department.getCode(),
                        teamsByDepartment.getOrDefault(department.getId(), List.of()).stream()
                                .sorted(Comparator.comparing(Team::getName))
                                .map(team -> new TeamSummary(team.getId(), team.getName(), team.getCode()))
                                .toList()))
                .toList();

        return new OrganizationChartResponse(nodes);
    }
}
