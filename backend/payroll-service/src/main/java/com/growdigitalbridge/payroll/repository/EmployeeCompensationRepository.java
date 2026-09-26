package com.growdigitalbridge.payroll.repository;

import com.growdigitalbridge.payroll.domain.EmployeeCompensation;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeCompensationRepository extends JpaRepository<EmployeeCompensation, UUID> {

    /** Resolves "the compensation effective for this employee during this period" (Section G). */
    @Query("""
            select c from EmployeeCompensation c
            where c.employeeRef = :employeeRef
              and c.effectiveFrom <= :date
              and (c.effectiveTo is null or c.effectiveTo >= :date)
            """)
    Optional<EmployeeCompensation> findEffectiveForEmployee(@Param("employeeRef") UUID employeeRef, @Param("date") LocalDate date);
}
