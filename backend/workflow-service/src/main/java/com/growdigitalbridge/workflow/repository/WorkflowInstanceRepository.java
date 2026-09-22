package com.growdigitalbridge.workflow.repository;

import com.growdigitalbridge.workflow.domain.WorkflowInstance;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstance, UUID> {

    @Query("select i from WorkflowInstance i where i.requesterRef = :requesterRef")
    Page<WorkflowInstance> searchForRequester(@Param("requesterRef") UUID requesterRef, Pageable pageable);

    @Query("select i from WorkflowInstance i where i.requesterRef in :requesterRefs")
    Page<WorkflowInstance> searchWithinScope(@Param("requesterRefs") Collection<UUID> requesterRefs, Pageable pageable);
}
