package com.growdigitalbridge.performance.repository;

import com.growdigitalbridge.performance.domain.Goal;
import com.growdigitalbridge.performance.domain.GoalStatus;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoalRepository extends JpaRepository<Goal, UUID> {

    @Query("select g from Goal g where g.employeeRef = :employeeRef and (:status is null or g.status = :status)")
    Page<Goal> searchForEmployee(@Param("employeeRef") UUID employeeRef, @Param("status") GoalStatus status, Pageable pageable);

    @Query("select g from Goal g where g.employeeRef in :employeeRefs and (:status is null or g.status = :status)")
    Page<Goal> searchWithinScope(@Param("employeeRefs") Collection<UUID> employeeRefs, @Param("status") GoalStatus status, Pageable pageable);

    @Query("select g from Goal g where (:status is null or g.status = :status)")
    Page<Goal> searchAll(@Param("status") GoalStatus status, Pageable pageable);
}
