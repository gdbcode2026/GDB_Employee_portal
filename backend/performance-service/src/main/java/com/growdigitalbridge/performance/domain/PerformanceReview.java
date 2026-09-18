package com.growdigitalbridge.performance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code employeeRef} (the person being reviewed) and {@code reviewerRef} may be equal - that
 * is how self-review is represented, since RBAC.md defines no separate self-review permission
 * or entity; see PerformanceAccessGuard for the authorization rationale. {@code rating} is
 * plain freeform text: no rating scale or scoring formula is documented anywhere in this
 * repository, and none is invented here.
 */
@Entity
@Table(name = "performance_reviews")
public class PerformanceReview {

    @Id
    private UUID id;

    @Column(name = "cycle_id", nullable = false)
    private UUID cycleId;

    @Column(name = "employee_ref", nullable = false)
    private UUID employeeRef;

    @Column(name = "reviewer_ref", nullable = false)
    private UUID reviewerRef;

    @Column(length = 100)
    private String rating;

    @Column(length = 4000)
    private String comments;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReviewStatus status;

    @Column(name = "submitted_at")
    private Instant submittedAt;

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

    protected PerformanceReview() { }

    public PerformanceReview(UUID id, UUID cycleId, UUID employeeRef, UUID reviewerRef, String actor, Instant now) {
        this.id = id;
        this.cycleId = cycleId;
        this.employeeRef = employeeRef;
        this.reviewerRef = reviewerRef;
        this.status = ReviewStatus.DRAFT;
        this.createdAt = now;
        this.createdBy = actor;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public void submit(String rating, String comments, String actor, Instant now) {
        this.rating = rating;
        this.comments = comments;
        this.status = ReviewStatus.SUBMITTED;
        this.submittedAt = now;
        touch(actor, now);
    }

    private void touch(String actor, Instant now) {
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getCycleId() { return cycleId; }
    public UUID getEmployeeRef() { return employeeRef; }
    public UUID getReviewerRef() { return reviewerRef; }
    public String getRating() { return rating; }
    public String getComments() { return comments; }
    public ReviewStatus getStatus() { return status; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
