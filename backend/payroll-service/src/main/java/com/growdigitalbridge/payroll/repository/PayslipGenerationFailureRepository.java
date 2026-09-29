package com.growdigitalbridge.payroll.repository;

import com.growdigitalbridge.payroll.domain.PayslipGenerationFailure;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayslipGenerationFailureRepository extends JpaRepository<PayslipGenerationFailure, UUID> {

    Optional<PayslipGenerationFailure> findByRunIdAndEmployeeRef(UUID runId, UUID employeeRef);
}
