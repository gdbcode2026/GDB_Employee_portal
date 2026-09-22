package com.growdigitalbridge.workflow.repository;

import com.growdigitalbridge.workflow.domain.Delegation;
import com.growdigitalbridge.workflow.domain.DelegationStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DelegationRepository extends JpaRepository<Delegation, UUID> {

    List<Delegation> findByTaskIdAndStatus(UUID taskId, DelegationStatus status);
}
