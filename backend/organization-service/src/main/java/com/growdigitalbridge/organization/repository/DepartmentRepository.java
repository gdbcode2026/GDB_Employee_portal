package com.growdigitalbridge.organization.repository;

import com.growdigitalbridge.organization.domain.Department;
import com.growdigitalbridge.organization.domain.DepartmentStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID id);

    List<Department> findByStatusOrderByNameAsc(DepartmentStatus status);

    @Query("""
            select d from Department d
            where (:status is null or d.status = :status)
              and (:query is null or lower(d.name) like lower(concat('%', :query, '%'))
                                   or lower(d.code) like lower(concat('%', :query, '%')))
            """)
    Page<Department> search(@Param("status") DepartmentStatus status, @Param("query") String query, Pageable pageable);
}
