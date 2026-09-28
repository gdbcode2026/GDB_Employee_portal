package com.growdigitalbridge.payroll.repository;

import com.growdigitalbridge.payroll.domain.PayrollException;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollExceptionRepository extends JpaRepository<PayrollException, UUID> {

    List<PayrollException> findByRunId(UUID runId);

    long countByRunId(UUID runId);

    void deleteByRunId(UUID runId);
}
