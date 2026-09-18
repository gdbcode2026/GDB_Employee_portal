package com.growdigitalbridge.leave.repository;

import com.growdigitalbridge.leave.domain.LeaveRequest;
import com.growdigitalbridge.leave.domain.LeaveRequestStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID> {

    @Query("""
            select r from LeaveRequest r
            where r.employeeRef = :employeeRef
              and (:status is null or r.status = :status)
              and (:leaveTypeId is null or r.leaveTypeId = :leaveTypeId)
              and (:from is null or r.endDate >= :from)
              and (:to is null or r.startDate <= :to)
            """)
    Page<LeaveRequest> searchForEmployee(@Param("employeeRef") UUID employeeRef, @Param("status") LeaveRequestStatus status,
                                          @Param("leaveTypeId") UUID leaveTypeId, @Param("from") LocalDate from,
                                          @Param("to") LocalDate to, Pageable pageable);

    @Query("""
            select r from LeaveRequest r
            where r.employeeRef in :allowedIds
              and (:status is null or r.status = :status)
              and (:leaveTypeId is null or r.leaveTypeId = :leaveTypeId)
              and (:from is null or r.endDate >= :from)
              and (:to is null or r.startDate <= :to)
            """)
    Page<LeaveRequest> searchWithinScope(@Param("allowedIds") Collection<UUID> allowedIds, @Param("status") LeaveRequestStatus status,
                                          @Param("leaveTypeId") UUID leaveTypeId, @Param("from") LocalDate from,
                                          @Param("to") LocalDate to, Pageable pageable);

    @Query("""
            select r from LeaveRequest r
            where (:status is null or r.status = :status)
              and (:leaveTypeId is null or r.leaveTypeId = :leaveTypeId)
              and (:from is null or r.endDate >= :from)
              and (:to is null or r.startDate <= :to)
            """)
    Page<LeaveRequest> searchAll(@Param("status") LeaveRequestStatus status, @Param("leaveTypeId") UUID leaveTypeId,
                                  @Param("from") LocalDate from, @Param("to") LocalDate to, Pageable pageable);
}
