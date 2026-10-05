package com.growdigitalbridge.payroll.repository;

import com.growdigitalbridge.payroll.domain.StatutoryRule;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StatutoryRuleRepository extends JpaRepository<StatutoryRule, UUID> {

    /** Every version of one rule family, newest version first - used for overlap checks and version history. */
    List<StatutoryRule> findByCodeOrderByRuleVersionDesc(String code);

    Page<StatutoryRule> findByCodeOrderByRuleVersionDesc(String code, Pageable pageable);

    Page<StatutoryRule> findAllByOrderByCodeAscRuleVersionDesc(Pageable pageable);

    /**
     * Resolves "the {@code ACTIVE} version of this rule family covering this date" (Rule Engine
     * task, item 7/9) - the same effective-dated, status-gated resolution pattern
     * {@code EmployeeCompensationRepository.findEffectiveForEmployee} already establishes.
     * {@code jurisdiction} is matched by equality, or by both being {@code null} for a
     * non-jurisdictional rule (PF/ESI/TDS). Returns a {@link List} rather than throwing on
     * ambiguity (unlike that precedent) because an ambiguous/missing rule here must never fail an
     * employee's entire line - the caller treats anything other than exactly one match as "no
     * usable rule" and raises a {@link com.growdigitalbridge.payroll.domain.PayrollException}
     * instead (never a guessed amount).
     */
    @Query("""
            select r from StatutoryRule r
            where r.code = :code
              and ((:jurisdiction is null and r.jurisdiction is null) or r.jurisdiction = :jurisdiction)
              and r.status = com.growdigitalbridge.payroll.domain.StatutoryRuleStatus.ACTIVE
              and r.effectiveFrom <= :date
              and (r.effectiveTo is null or r.effectiveTo >= :date)
            """)
    List<StatutoryRule> findActiveCovering(@Param("code") String code, @Param("jurisdiction") String jurisdiction,
                                            @Param("date") LocalDate date);
}
