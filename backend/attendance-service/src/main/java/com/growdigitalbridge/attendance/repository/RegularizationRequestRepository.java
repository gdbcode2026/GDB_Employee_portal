package com.growdigitalbridge.attendance.repository;

import com.growdigitalbridge.attendance.domain.RegularizationRequest;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegularizationRequestRepository extends JpaRepository<RegularizationRequest, UUID> {

    @Query("select r from RegularizationRequest r where r.employeeRef = :employeeRef")
    Page<RegularizationRequest> searchForEmployee(@Param("employeeRef") UUID employeeRef, Pageable pageable);

    @Query("select r from RegularizationRequest r where r.employeeRef in :allowedIds")
    Page<RegularizationRequest> searchWithinScope(@Param("allowedIds") Collection<UUID> allowedIds, Pageable pageable);
}
