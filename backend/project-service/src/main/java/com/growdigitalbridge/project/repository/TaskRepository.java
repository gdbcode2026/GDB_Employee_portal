package com.growdigitalbridge.project.repository;

import com.growdigitalbridge.project.domain.Task;
import com.growdigitalbridge.project.domain.TaskPriority;
import com.growdigitalbridge.project.domain.TaskStatus;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    @Query("""
            select t from Task t
            where t.projectId = :projectId
              and (:status is null or t.status = :status)
              and (:priority is null or t.priority = :priority)
              and (:assigneeRef is null or t.assigneeRef = :assigneeRef)
            """)
    Page<Task> searchInProject(@Param("projectId") UUID projectId, @Param("status") TaskStatus status,
                                @Param("priority") TaskPriority priority, @Param("assigneeRef") UUID assigneeRef, Pageable pageable);

    @Query("""
            select t from Task t
            where t.projectId = :projectId
              and t.assigneeRef in :assigneeRefs
              and (:status is null or t.status = :status)
              and (:priority is null or t.priority = :priority)
            """)
    Page<Task> searchInProjectWithinScope(@Param("projectId") UUID projectId, @Param("assigneeRefs") Collection<UUID> assigneeRefs,
                                           @Param("status") TaskStatus status, @Param("priority") TaskPriority priority, Pageable pageable);

    @Query("""
            select t from Task t
            where t.assigneeRef = :assigneeRef
              and (:status is null or t.status = :status)
              and (:priority is null or t.priority = :priority)
            """)
    Page<Task> searchForAssignee(@Param("assigneeRef") UUID assigneeRef, @Param("status") TaskStatus status,
                                  @Param("priority") TaskPriority priority, Pageable pageable);

    @Query("""
            select t from Task t
            where t.assigneeRef in :assigneeRefs
              and (:status is null or t.status = :status)
              and (:priority is null or t.priority = :priority)
            """)
    Page<Task> searchWithinScope(@Param("assigneeRefs") Collection<UUID> assigneeRefs, @Param("status") TaskStatus status,
                                  @Param("priority") TaskPriority priority, Pageable pageable);

    @Query("""
            select t from Task t
            where (:status is null or t.status = :status)
              and (:priority is null or t.priority = :priority)
            """)
    Page<Task> searchAll(@Param("status") TaskStatus status, @Param("priority") TaskPriority priority, Pageable pageable);
}
