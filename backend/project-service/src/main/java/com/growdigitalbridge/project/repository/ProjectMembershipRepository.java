package com.growdigitalbridge.project.repository;

import com.growdigitalbridge.project.domain.MembershipStatus;
import com.growdigitalbridge.project.domain.ProjectMembership;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectMembershipRepository extends JpaRepository<ProjectMembership, UUID> {

    Optional<ProjectMembership> findByProjectIdAndEmployeeRef(UUID projectId, UUID employeeRef);

    List<ProjectMembership> findByProjectId(UUID projectId);

    boolean existsByProjectIdAndEmployeeRefAndStatus(UUID projectId, UUID employeeRef, MembershipStatus status);

    @Query("select m.projectId from ProjectMembership m where m.employeeRef = :employeeRef and m.status = :status")
    Set<UUID> findProjectIdsByEmployeeRefAndStatus(@Param("employeeRef") UUID employeeRef, @Param("status") MembershipStatus status);
}
