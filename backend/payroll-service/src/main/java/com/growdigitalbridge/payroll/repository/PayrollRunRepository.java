package com.growdigitalbridge.payroll.repository;

import com.growdigitalbridge.payroll.domain.PayrollRun;
import com.growdigitalbridge.payroll.domain.PayrollRunStatus;
import com.growdigitalbridge.payroll.domain.PayrollRunType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollRunRepository extends JpaRepository<PayrollRun, UUID> {

    /**
     * Reporting V1 D3 (Payroll Cost Summary) filters. Two separate derived-query methods, branched
     * on by the service layer depending on whether {@code periodId} was supplied, rather than one
     * JPQL query with a standalone {@code (:periodId is null or ...)} check - the exact pattern
     * that caused the PostgreSQL parameter-type-inference defect fixed in Employee/Expense
     * (docs/REPORTING_AUTHORIZATION_REVIEW.md). Neither method here ever binds a nullable
     * parameter, so that defect class cannot occur.
     */
    Page<PayrollRun> findByStatus(PayrollRunStatus status, Pageable pageable);

    Page<PayrollRun> findByPeriodIdAndStatus(UUID periodId, PayrollRunStatus status, Pageable pageable);

    /**
     * Section T's idempotency key: {@code (period_id, run_type, corrects_run_id)}. Passing
     * {@code null} for {@code correctsRunId} matches Spring Data JPA's standard null-parameter
     * behavior (translated to {@code IS NULL}), which is exactly the REGULAR-run case this
     * Phase 1 foundation exercises - it never supplies a non-null {@code correctsRunId}.
     */
    Optional<PayrollRun> findByPeriodIdAndRunTypeAndCorrectsRunId(UUID periodId, PayrollRunType runType, UUID correctsRunId);

    /** Used to resolve a financial-year YTD (Section Q) from only FINALIZED runs. */
    List<PayrollRun> findByPeriodIdInAndStatus(Collection<UUID> periodIds, PayrollRunStatus status);

    /**
     * Adjustment-run idempotency guard (Section K item 8): Section K explicitly leaves "any limit
     * on how many adjustment runs may reference the same original run" PENDING_GDB_APPROVAL, so
     * this does not forbid a second, later, genuinely-new correction - it only blocks starting a
     * new one while an earlier adjustment against the same original has not yet reached the
     * terminal {@code FINALIZED} state (i.e. an accidental double-submission while one is already
     * in flight), mirroring the spirit of the regular-run uniqueness check without inventing an
     * absolute "one adjustment ever" business rule nobody has decided.
     */
    boolean existsByCorrectsRunIdAndStatusNot(UUID correctsRunId, PayrollRunStatus status);
}
