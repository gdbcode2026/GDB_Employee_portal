package com.growdigitalbridge.workflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * {@code rulesJson} is stored and returned verbatim, never parsed or enforced: approval-stage
 * structure, approver resolution, SLA durations, delegation eligibility, and escalation
 * targets are explicitly "configuration inputs for GDB - not assumed company policy"
 * (docs/workflows/WORKFLOWS.md). Definitions are immutable once created - a new
 * requestType/version pair is created for a revision, matching the documented "version" field
 * (no PATCH endpoint exists, since none is documented).
 */
@Entity
@Table(name = "workflow_definitions")
public class WorkflowDefinition {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 32)
    private RequestType requestType;

    @Column(name = "definition_version", nullable = false)
    private int definitionVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rules_json", nullable = false, columnDefinition = "jsonb")
    private String rulesJson;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(length = 128, updatable = false)
    private String createdBy;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(length = 128)
    private String updatedBy;

    @Version
    private long version;

    protected WorkflowDefinition() { }

    public WorkflowDefinition(UUID id, RequestType requestType, int definitionVersion, String rulesJson, String actor, Instant now) {
        this.id = id;
        this.requestType = requestType;
        this.definitionVersion = definitionVersion;
        this.rulesJson = rulesJson;
        this.createdAt = now;
        this.createdBy = actor;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public UUID getId() { return id; }
    public RequestType getRequestType() { return requestType; }
    public int getDefinitionVersion() { return definitionVersion; }
    public String getRulesJson() { return rulesJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
