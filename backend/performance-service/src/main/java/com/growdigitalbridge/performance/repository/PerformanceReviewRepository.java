package com.growdigitalbridge.performance.repository;

import com.growdigitalbridge.performance.domain.PerformanceReview;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PerformanceReviewRepository extends JpaRepository<PerformanceReview, UUID> {

    boolean existsByCycleIdAndEmployeeRefAndReviewerRef(UUID cycleId, UUID employeeRef, UUID reviewerRef);

    /** "self" scope: reviews about the caller, or reviews the caller is assigned to give. */
    @Query("""
            select r from PerformanceReview r
            where (r.employeeRef = :self or r.reviewerRef = :self)
              and (:cycleId is null or r.cycleId = :cycleId)
            """)
    Page<PerformanceReview> searchForSelf(@Param("self") UUID self, @Param("cycleId") UUID cycleId, Pageable pageable);

    @Query("""
            select r from PerformanceReview r
            where r.employeeRef in :employeeRefs
              and (:cycleId is null or r.cycleId = :cycleId)
            """)
    Page<PerformanceReview> searchWithinScope(@Param("employeeRefs") Collection<UUID> employeeRefs,
                                               @Param("cycleId") UUID cycleId, Pageable pageable);

    @Query("select r from PerformanceReview r where (:cycleId is null or r.cycleId = :cycleId)")
    Page<PerformanceReview> searchAll(@Param("cycleId") UUID cycleId, Pageable pageable);
}
