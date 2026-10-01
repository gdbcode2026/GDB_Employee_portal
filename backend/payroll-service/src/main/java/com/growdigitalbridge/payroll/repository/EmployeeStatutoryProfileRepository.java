package com.growdigitalbridge.payroll.repository;

import com.growdigitalbridge.payroll.domain.EmployeeStatutoryProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeStatutoryProfileRepository extends JpaRepository<EmployeeStatutoryProfile, UUID> {

    Optional<EmployeeStatutoryProfile> findByEmployeeRef(UUID employeeRef);
}
