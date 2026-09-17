package com.growdigitalbridge.employee.repository;

import com.growdigitalbridge.employee.domain.Employment;
import com.growdigitalbridge.employee.domain.EmploymentStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmploymentRepository extends JpaRepository<Employment, UUID> {

    Optional<Employment> findByEmployeeIdAndStatus(UUID employeeId, EmploymentStatus status);

    Optional<Employment> findFirstByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);
}
