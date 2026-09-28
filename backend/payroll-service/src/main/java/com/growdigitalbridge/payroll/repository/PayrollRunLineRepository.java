package com.growdigitalbridge.payroll.repository;

import com.growdigitalbridge.payroll.domain.PayrollRunLine;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollRunLineRepository extends JpaRepository<PayrollRunLine, UUID> {

    List<PayrollRunLine> findByRunId(UUID runId);

    long countByRunId(UUID runId);

    void deleteByRunId(UUID runId);
}
