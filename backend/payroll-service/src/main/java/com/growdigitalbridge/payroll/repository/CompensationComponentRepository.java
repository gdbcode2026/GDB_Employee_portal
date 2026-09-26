package com.growdigitalbridge.payroll.repository;

import com.growdigitalbridge.payroll.domain.CompensationComponent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompensationComponentRepository extends JpaRepository<CompensationComponent, UUID> {

    List<CompensationComponent> findByCompensationId(UUID compensationId);
}
