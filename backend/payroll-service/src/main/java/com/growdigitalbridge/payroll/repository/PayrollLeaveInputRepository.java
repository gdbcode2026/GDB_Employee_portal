package com.growdigitalbridge.payroll.repository;

import com.growdigitalbridge.payroll.domain.PayrollLeaveInput;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollLeaveInputRepository extends JpaRepository<PayrollLeaveInput, UUID> {

    Optional<PayrollLeaveInput> findByLeaveRequestRef(UUID leaveRequestRef);

    List<PayrollLeaveInput> findByEmployeeRef(UUID employeeRef);
}
