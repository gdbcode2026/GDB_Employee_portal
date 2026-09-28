package com.growdigitalbridge.payroll.repository;

import com.growdigitalbridge.payroll.domain.PayrollAttendanceInput;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollAttendanceInputRepository extends JpaRepository<PayrollAttendanceInput, UUID> {

    Optional<PayrollAttendanceInput> findByEmployeeRefAndWorkDate(UUID employeeRef, LocalDate workDate);

    List<PayrollAttendanceInput> findByEmployeeRefAndWorkDateBetween(UUID employeeRef, LocalDate from, LocalDate to);
}
