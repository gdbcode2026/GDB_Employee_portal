package com.growdigitalbridge.payroll.repository;

import com.growdigitalbridge.payroll.domain.Payslip;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayslipRepository extends JpaRepository<Payslip, UUID> {

    boolean existsByRunIdAndEmployeeRef(UUID runId, UUID employeeRef);

    Optional<Payslip> findByRunIdAndEmployeeRef(UUID runId, UUID employeeRef);

    Page<Payslip> findByEmployeeRef(UUID employeeRef, Pageable pageable);

    Page<Payslip> findByEmployeeRefAndPeriodId(UUID employeeRef, UUID periodId, Pageable pageable);

    /** Used for the financial-year filter on {@code GET /payroll/payslips/me} (item 9). */
    Page<Payslip> findByEmployeeRefAndPeriodIdIn(UUID employeeRef, Collection<UUID> periodIds, Pageable pageable);
}
