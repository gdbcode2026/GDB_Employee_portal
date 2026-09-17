package com.growdigitalbridge.employee.repository;

import com.growdigitalbridge.employee.domain.EmergencyContact;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmergencyContactRepository extends JpaRepository<EmergencyContact, UUID> {

    List<EmergencyContact> findByEmployeeId(UUID employeeId);

    void deleteByEmployeeId(UUID employeeId);
}
