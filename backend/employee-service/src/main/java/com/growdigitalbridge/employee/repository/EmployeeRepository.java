package com.growdigitalbridge.employee.repository;

import com.growdigitalbridge.employee.domain.Employee;
import com.growdigitalbridge.employee.domain.EmployeeStatus;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    boolean existsByEmployeeNumber(String employeeNumber);

    boolean existsByEmail(String email);

    boolean existsByIdentitySubject(String identitySubject);

    boolean existsByEmailAndIdNot(String email, UUID id);

    Optional<Employee> findByIdentitySubject(String identitySubject);

    @Query("""
            select e from Employee e
            where (:status is null or e.status = :status)
              and (:query is null or lower(e.firstName) like lower(concat('%', :query, '%'))
                                   or lower(e.lastName) like lower(concat('%', :query, '%'))
                                   or lower(e.employeeNumber) like lower(concat('%', :query, '%')))
            """)
    Page<Employee> searchAll(@Param("status") EmployeeStatus status, @Param("query") String query, Pageable pageable);

    @Query("""
            select e from Employee e
            where e.id in :allowedIds
              and (:status is null or e.status = :status)
              and (:query is null or lower(e.firstName) like lower(concat('%', :query, '%'))
                                   or lower(e.lastName) like lower(concat('%', :query, '%'))
                                   or lower(e.employeeNumber) like lower(concat('%', :query, '%')))
            """)
    Page<Employee> searchWithinScope(@Param("allowedIds") Collection<UUID> allowedIds,
                                      @Param("status") EmployeeStatus status,
                                      @Param("query") String query,
                                      Pageable pageable);
}
