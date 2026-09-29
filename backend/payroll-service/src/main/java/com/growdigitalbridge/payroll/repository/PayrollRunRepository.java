package com.growdigitalbridge.payroll.repository;

import com.growdigitalbridge.payroll.domain.PayrollRun;
import com.growdigitalbridge.payroll.domain.PayrollRunStatus;
import com.growdigitalbridge.payroll.domain.PayrollRunType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollRunRepository extends JpaRepository<PayrollRun, UUID> {

    /**
     * Section T's idempotency key: {@code (period_id, run_type, corrects_run_id)}. Passing
     * {@code null} for {@code correctsRunId} matches Spring Data JPA's standard null-parameter
     * behavior (translated to {@code IS NULL}), which is exactly the REGULAR-run case this
     * Phase 1 foundation exercises - it never supplies a non-null {@code correctsRunId}.
     */
    Optional<PayrollRun> findByPeriodIdAndRunTypeAndCorrectsRunId(UUID periodId, PayrollRunType runType, UUID correctsRunId);

    /** Used to resolve a financial-year YTD (Section Q) from only FINALIZED runs. */
    List<PayrollRun> findByPeriodIdInAndStatus(Collection<UUID> periodIds, PayrollRunStatus status);
}
