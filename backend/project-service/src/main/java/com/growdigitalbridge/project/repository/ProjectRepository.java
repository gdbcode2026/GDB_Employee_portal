package com.growdigitalbridge.project.repository;

import com.growdigitalbridge.project.domain.Project;
import com.growdigitalbridge.project.domain.ProjectStatus;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID id);

    @Query("""
            select p from Project p
            where p.id in :projectIds
              and (:status is null or p.status = :status)
              and (:query is null or lower(p.name) like lower(concat('%', :query, '%'))
                                   or lower(p.code) like lower(concat('%', :query, '%')))
            """)
    Page<Project> searchWithinScope(@Param("projectIds") Collection<UUID> projectIds, @Param("status") ProjectStatus status,
                                     @Param("query") String query, Pageable pageable);

    @Query("""
            select p from Project p
            where (:status is null or p.status = :status)
              and (:query is null or lower(p.name) like lower(concat('%', :query, '%'))
                                   or lower(p.code) like lower(concat('%', :query, '%')))
            """)
    Page<Project> searchAll(@Param("status") ProjectStatus status, @Param("query") String query, Pageable pageable);
}
