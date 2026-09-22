package com.growdigitalbridge.workflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growdigitalbridge.workflow.api.dto.WorkflowDefinitionDtos;
import com.growdigitalbridge.workflow.domain.WorkflowDefinition;
import com.growdigitalbridge.workflow.repository.WorkflowDefinitionRepository;
import com.growdigitalbridge.workflow.service.exception.ConflictException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns workflow definitions. {@code rulesJson} is round-tripped verbatim: this service never
 * parses or enforces its contents, since the actual approval-stage/SLA/delegation/escalation
 * policy it would encode is explicitly a GDB configuration decision, not something to invent
 * here (docs/workflows/WORKFLOWS.md).
 */
@Service
public class WorkflowDefinitionService {

    private final WorkflowDefinitionRepository repository;
    private final ObjectMapper objectMapper;

    public WorkflowDefinitionService(WorkflowDefinitionRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WorkflowDefinitionDtos.Response create(WorkflowDefinitionDtos.CreateRequest request, String actor) {
        if (repository.existsByRequestTypeAndDefinitionVersion(request.requestType(), request.definitionVersion())) {
            throw new ConflictException("A definition for " + request.requestType() + " version "
                    + request.definitionVersion() + " already exists.");
        }
        String rulesJson = writeJson(request.rulesJson());
        WorkflowDefinition definition = new WorkflowDefinition(UUID.randomUUID(), request.requestType(),
                request.definitionVersion(), rulesJson, actor, Instant.now());
        repository.save(definition);
        return toResponse(definition);
    }

    @Transactional(readOnly = true)
    public List<WorkflowDefinitionDtos.Response> list() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    private WorkflowDefinitionDtos.Response toResponse(WorkflowDefinition definition) {
        return new WorkflowDefinitionDtos.Response(definition.getId(), definition.getRequestType(),
                definition.getDefinitionVersion(), readJson(definition.getRulesJson()), definition.getCreatedAt(), definition.getUpdatedAt());
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize rulesJson", e);
        }
    }

    private Map<String, Object> readJson(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<Map<String, Object>>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize rulesJson", e);
        }
    }
}
