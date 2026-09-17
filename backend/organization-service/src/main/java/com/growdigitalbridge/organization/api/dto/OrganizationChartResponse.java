package com.growdigitalbridge.organization.api.dto;

import java.util.List;
import java.util.UUID;

public record OrganizationChartResponse(List<DepartmentNode> departments) {

    public record DepartmentNode(UUID id, String name, String code, List<TeamSummary> teams) { }

    public record TeamSummary(UUID id, String name, String code) { }
}
