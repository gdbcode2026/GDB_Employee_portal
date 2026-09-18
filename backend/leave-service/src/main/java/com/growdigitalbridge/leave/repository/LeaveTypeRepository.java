package com.growdigitalbridge.leave.repository;

import com.growdigitalbridge.leave.domain.LeaveType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, UUID> {

    List<LeaveType> findByActiveTrue();
}
