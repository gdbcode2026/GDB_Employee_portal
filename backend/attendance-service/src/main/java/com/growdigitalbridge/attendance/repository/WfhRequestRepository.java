package com.growdigitalbridge.attendance.repository;

import com.growdigitalbridge.attendance.domain.WfhRequest;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WfhRequestRepository extends JpaRepository<WfhRequest, UUID> {

    @Query("select w from WfhRequest w where w.employeeRef = :employeeRef")
    Page<WfhRequest> searchForEmployee(@Param("employeeRef") UUID employeeRef, Pageable pageable);

    @Query("select w from WfhRequest w where w.employeeRef in :allowedIds")
    Page<WfhRequest> searchWithinScope(@Param("allowedIds") Collection<UUID> allowedIds, Pageable pageable);
}
