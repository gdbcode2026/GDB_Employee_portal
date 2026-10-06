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
     * Resolves "the compensation effective for this employee during this period" (Section G) by
     * date-range **overlap** with the full period, not merely a single point-in-time check against
     * the period's start date. This is a deliberate fix (Payroll V1 completion review, mid-period
     * compensation resolution defect): a record whose {@code effectiveFrom} falls anywhere within
     * the period - the normal case for a new joiner hired after the 1st of the month - now
     * correctly resolves, where previously it was silently skipped and the employee received no
     * line at all (`NO_EFFECTIVE_COMPENSATION`) merely because their compensation started after
     * the period's first day. No proration/LOP rule is introduced by this fix - a resolved record
     * is still used for its full configured amount, exactly as before.
     *
     * <p>Only {@code ACTIVE} records are ever resolved. Throws
     * {@link org.springframework.dao.IncorrectResultSizeDataAccessException} if more than one
     * matches - this is the pre-existing deliberate ambiguity signal, extended (not changed) by
     * this fix: it now also fires for two legitimately sequential, non-overlapping-with-each-other
     * records (e.g. an old compensation ending mid-month and a new one starting mid-month, both
     * created validly since {@code EmployeeCompensationService.assertNoOverlap} only rejects
     * records that overlap *each other*) that each independently overlap the *same* payroll
     * period. This is an intentional, unavoidable consequence of resolving by range-overlap rather
     * than a single point in time - deciding which of two such records (or how to split between
     * them) is itself a proration/business question this fix does not invent an answer to; the
     * run instead fails safely as {@code CALCULATION_FAILED}, exactly like any other ambiguous
     * compensation state, surfacing the need for HR/Finance to resolve the dates rather than
     * guessing. Write-time overlap prevention between two records' own ranges is unaffected.
     */
    @Query("""
            select c from EmployeeCompensation c
            where c.employeeRef = :employeeRef
              and c.status = com.growdigitalbridge.payroll.domain.CompensationStatus.ACTIVE
              and c.effectiveFrom <= :periodEnd
              and (c.effectiveTo is null or c.effectiveTo >= :periodStart)
            """)
    Optional<EmployeeCompensation> findEffectiveForEmployee(@Param("employeeRef") UUID employeeRef,
                                                              @Param("periodStart") LocalDate periodStart,
                                                              @Param("periodEnd") LocalDate periodEnd);

    /** All compensation records for an employee (any status), most recent first - used for overlap checks and detail listing. */
    List<EmployeeCompensation> findByEmployeeRefOrderByEffectiveFromDesc(UUID employeeRef);

    Page<EmployeeCompensation> findByEmployeeRef(UUID employeeRef, Pageable pageable);
}
