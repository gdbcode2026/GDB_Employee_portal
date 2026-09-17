package com.growdigitalbridge.organization.repository;

import com.growdigitalbridge.organization.domain.ReportingRelation;
import com.growdigitalbridge.organization.domain.ReportingRelationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportingRelationRepository extends JpaRepository<ReportingRelation, UUID> {

    Optional<ReportingRelation> findByEmployeeRefAndStatus(UUID employeeRef, ReportingRelationStatus status);

    boolean existsByEmployeeRefAndStatus(UUID employeeRef, ReportingRelationStatus status);

    @Query("""
            select r from ReportingRelation r
            where (:employeeRef is null or r.employeeRef = :employeeRef)
              and (:managerEmployeeRef is null or r.managerEmployeeRef = :managerEmployeeRef)
              and (:status is null or r.status = :status)
            """)
    Page<ReportingRelation> search(@Param("employeeRef") UUID employeeRef,
                                    @Param("managerEmployeeRef") UUID managerEmployeeRef,
                                    @Param("status") ReportingRelationStatus status,
                                    Pageable pageable);

    /**
     * Resolves every employee reporting to {@code managerRef}, directly or transitively,
     * from currently ACTIVE relations only. This is the authoritative "team scope" source -
     * callers must never substitute a client-supplied team identifier for this result.
     */
    @Query(value = """
            WITH RECURSIVE subtree AS (
                SELECT employee_ref FROM reporting_relations
                WHERE manager_employee_ref = :managerRef AND status = 'ACTIVE'
                UNION
                SELECT rr.employee_ref FROM reporting_relations rr
                JOIN subtree s ON rr.manager_employee_ref = s.employee_ref
                WHERE rr.status = 'ACTIVE'
            )
            SELECT employee_ref FROM subtree
            """, nativeQuery = true)
    List<UUID> findReportingScope(@Param("managerRef") UUID managerRef);
}
