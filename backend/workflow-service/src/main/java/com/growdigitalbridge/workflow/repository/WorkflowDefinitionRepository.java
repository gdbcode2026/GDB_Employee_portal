package com.growdigitalbridge.workflow.repository;

import com.growdigitalbridge.workflow.domain.RequestType;
import com.growdigitalbridge.workflow.domain.WorkflowDefinition;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinition, UUID> {

    boolean existsByRequestTypeAndDefinitionVersion(RequestType requestType, int definitionVersion);
}
