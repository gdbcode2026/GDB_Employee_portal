package com.growdigitalbridge.payroll.repository;

import com.growdigitalbridge.payroll.domain.PayrollException;
import com.growdigitalbridge.payroll.domain.PayrollExceptionReason;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollExceptionRepository extends JpaRepository<PayrollException, UUID> {

    List<PayrollException> findByRunId(UUID runId);

    long countByRunId(UUID runId);

    void deleteByRunId(UUID runId);

    Optional<PayrollException> findByRunIdAndEmployeeRefAndReason(UUID runId, UUID employeeRef, PayrollExceptionReason reason);

    Page<PayrollException> findByRunId(UUID runId, Pageable pageable);

    Page<PayrollException> findAllByOrderByDetectedAtDesc(Pageable pageable);
}
