package com.growdigitalbridge.organization.repository;

import com.growdigitalbridge.organization.domain.Team;
import com.growdigitalbridge.organization.domain.TeamStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamRepository extends JpaRepository<Team, UUID> {

    boolean existsByDepartmentIdAndCode(UUID departmentId, String code);

    boolean existsByDepartmentIdAndCodeAndIdNot(UUID departmentId, String code, UUID id);

    List<Team> findByStatus(TeamStatus status);

    @Query("""
            select t from Team t
            where (:departmentId is null or t.departmentId = :departmentId)
              and (:status is null or t.status = :status)
            """)
    Page<Team> search(@Param("departmentId") UUID departmentId, @Param("status") TeamStatus status, Pageable pageable);
}
