package com.growdigitalbridge.document.service;

import com.growdigitalbridge.document.api.dto.PolicyDtos;
import com.growdigitalbridge.document.domain.Policy;
import com.growdigitalbridge.document.repository.DocumentRepository;
import com.growdigitalbridge.document.repository.PolicyRepository;
import com.growdigitalbridge.document.service.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PolicyService {

    private final PolicyRepository repository;
    private final DocumentRepository documentRepository;

    public PolicyService(PolicyRepository repository, DocumentRepository documentRepository) {
        this.repository = repository;
        this.documentRepository = documentRepository;
    }

    @Transactional
    public PolicyDtos.Response create(PolicyDtos.CreateRequest request, String actor) {
        if (!documentRepository.existsById(request.documentId())) {
            throw new ResourceNotFoundException("Document " + request.documentId() + " was not found.");
        }
        Policy policy = new Policy(UUID.randomUUID(), request.documentId(), request.title(), actor, Instant.now());
        repository.save(policy);
        return toResponse(policy);
    }

    @Transactional(readOnly = true)
    public List<PolicyDtos.Response> list() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public PolicyDtos.Response update(UUID id, PolicyDtos.UpdateRequest request, String actor) {
        Policy policy = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Policy " + id + " was not found."));
        String title = request.title() != null ? request.title() : policy.getTitle();
        var status = request.status() != null ? request.status() : policy.getStatus();
        policy.update(title, status, actor, Instant.now());
        return toResponse(policy);
    }

    private PolicyDtos.Response toResponse(Policy policy) {
        return new PolicyDtos.Response(policy.getId(), policy.getDocumentId(), policy.getTitle(), policy.getStatus(),
                policy.getCreatedAt(), policy.getUpdatedAt());
    }
}
