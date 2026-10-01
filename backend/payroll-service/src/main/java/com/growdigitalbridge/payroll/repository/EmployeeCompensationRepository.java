package com.growdigitalbridge.payroll.repository;

import com.growdigitalbridge.payroll.domain.EmployeeCompensation;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeCompensationRepository extends JpaRepository<EmployeeCompensation, UUID> {

    /**
     * Resolves "the compensation effective for this employee during this period" (Section G).
     * Only {@code ACTIVE} records are ever resolved. Throws
     * {@link org.springframework.dao.IncorrectResultSizeDataAccessException} if more than one
     * matches - this is the deliberate ambiguity signal two overlapping records produce; overlap
     * prevention at write time (Section K known limitation) is what should normally keep this
     * from ever happening.
     */
    @Query("""
            select c from EmployeeCompensation c
            where c.employeeRef = :employeeRef
              and c.status = com.growdigitalbridge.payroll.domain.CompensationStatus.ACTIVE
              and c.effectiveFrom <= :date
              and (c.effectiveTo is null or c.effectiveTo >= :date)
            """)
    Optional<EmployeeCompensation> findEffectiveForEmployee(@Param("employeeRef") UUID employeeRef, @Param("date") LocalDate date);

    /** All compensation records for an employee (any status), most recent first - used for overlap checks and detail listing. */
    List<EmployeeCompensation> findByEmployeeRefOrderByEffectiveFromDesc(UUID employeeRef);

    Page<EmployeeCompensation> findByEmployeeRef(UUID employeeRef, Pageable pageable);
}
