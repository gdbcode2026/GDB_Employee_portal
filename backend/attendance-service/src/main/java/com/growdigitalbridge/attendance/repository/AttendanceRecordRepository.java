package com.growdigitalbridge.attendance.repository;

import com.growdigitalbridge.attendance.domain.AttendanceRecord;
import com.growdigitalbridge.attendance.domain.AttendanceStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, UUID> {

    Optional<AttendanceRecord> findByEmployeeRefAndWorkDate(UUID employeeRef, LocalDate workDate);

    @Query("""
            select a from AttendanceRecord a
            where a.employeeRef = :employeeRef
              and (:from is null or a.workDate >= :from)
              and (:to is null or a.workDate <= :to)
              and (:status is null or a.status = :status)
            """)
    Page<AttendanceRecord> searchForEmployee(@Param("employeeRef") UUID employeeRef, @Param("from") LocalDate from,
                                              @Param("to") LocalDate to, @Param("status") AttendanceStatus status, Pageable pageable);

    @Query("""
            select a from AttendanceRecord a
            where a.employeeRef in :allowedIds
              and (:from is null or a.workDate >= :from)
              and (:to is null or a.workDate <= :to)
              and (:status is null or a.status = :status)
            """)
    Page<AttendanceRecord> searchWithinScope(@Param("allowedIds") Collection<UUID> allowedIds, @Param("from") LocalDate from,
                                              @Param("to") LocalDate to, @Param("status") AttendanceStatus status, Pageable pageable);

    @Query("""
            select a from AttendanceRecord a
            where (:from is null or a.workDate >= :from)
              and (:to is null or a.workDate <= :to)
              and (:status is null or a.status = :status)
            """)
    Page<AttendanceRecord> searchAll(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                      @Param("status") AttendanceStatus status, Pageable pageable);
}
