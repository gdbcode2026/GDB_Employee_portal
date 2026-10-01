package com.growdigitalbridge.payroll.repository;

import com.growdigitalbridge.payroll.domain.PayrollRunLine;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollRunLineRepository extends JpaRepository<PayrollRunLine, UUID> {

    List<PayrollRunLine> findByRunId(UUID runId);

    Optional<PayrollRunLine> findByRunIdAndEmployeeRef(UUID runId, UUID employeeRef);

    List<PayrollRunLine> findByEmployeeRefAndRunIdIn(UUID employeeRef, Collection<UUID> runIds);

    /** Used to determine whether a compensation record has ever been used by a finalized run (Section K known limitation). */
    List<PayrollRunLine> findByEmployeeRef(UUID employeeRef);

    long countByRunId(UUID runId);

    void deleteByRunId(UUID runId);
}
