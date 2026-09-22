package com.growdigitalbridge.workflow.repository;

import com.growdigitalbridge.workflow.domain.ApprovalTask;
import com.growdigitalbridge.workflow.domain.TaskStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApprovalTaskRepository extends JpaRepository<ApprovalTask, UUID> {

    List<ApprovalTask> findByInstanceId(UUID instanceId);

    List<ApprovalTask> findByInstanceIdAndStatus(UUID instanceId, TaskStatus status);

    @Query("select t from ApprovalTask t where t.assigneeRef = :assigneeRef and t.status = :status")
    Page<ApprovalTask> searchForAssignee(@Param("assigneeRef") UUID assigneeRef, @Param("status") TaskStatus status, Pageable pageable);
}
