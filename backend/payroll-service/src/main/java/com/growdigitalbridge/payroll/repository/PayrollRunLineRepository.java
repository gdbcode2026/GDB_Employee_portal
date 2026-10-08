package com.growdigitalbridge.payroll.repository;

import jakarta.persistence.Tuple;
import com.growdigitalbridge.payroll.domain.PayrollRunLine;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayrollRunLineRepository extends JpaRepository<PayrollRunLine, UUID> {

    List<PayrollRunLine> findByRunId(UUID runId);

    Optional<PayrollRunLine> findByRunIdAndEmployeeRef(UUID runId, UUID employeeRef);

    List<PayrollRunLine> findByEmployeeRefAndRunIdIn(UUID employeeRef, Collection<UUID> runIds);

    /** Used to determine whether a compensation record has ever been used by a finalized run (Section K known limitation). */
    List<PayrollRunLine> findByEmployeeRef(UUID employeeRef);

    long countByRunId(UUID runId);

    void deleteByRunId(UUID runId);

    /**
     * Reporting V1 D3 (Payroll Cost Summary): sums the four already-materialized per-line totals
     * for one run - no formula beyond a straight {@code sum(...)} of stored columns, never derived
     * from {@code componentBreakdown}, never a per-employee value. {@code coalesce(..., 0)} so a
     * run with no lines yet (DRAFT, never processed) reports zero rather than {@code null} - an
     * aggregate query with no {@code group by} always returns exactly one row regardless of how
     * many source rows matched, so this never returns empty.
     */
    @Query("""
            select coalesce(sum(l.grossPay), 0) as grossPay,
                   coalesce(sum(l.totalDeductions), 0) as totalDeductions,
                   coalesce(sum(l.totalEmployerContributions), 0) as totalEmployerContributions,
                   coalesce(sum(l.netPay), 0) as netPay
            from PayrollRunLine l where l.runId = :runId
            """)
    Tuple sumTotalsByRunId(@Param("runId") UUID runId);
}
