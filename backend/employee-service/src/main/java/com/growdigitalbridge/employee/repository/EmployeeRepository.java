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

    /**
     * {@code cast(:query as string)} appears in every occurrence of {@code :query}, including
     * the standalone {@code is null} check - not a behavior change, purely a PostgreSQL
     * parameter-typing fix (Reporting V1 authorization review Part A verification;
     * docs/REPORTING_AUTHORIZATION_REVIEW.md). Without it, Postgres's extended query protocol has
     * no type context for the lone {@code :query is null} occurrence and has been observed to
     * resolve the untyped parameter as {@code bytea} inside {@code concat(...)}, producing
     * "function lower(bytea) does not exist" for real callers that omit {@code query} - i.e.
     * every existing caller of this method today, including Directory and every Reporting V1
     * page that composes this endpoint. The cast forces a single, consistent, correct type for
     * the parameter across all of its uses regardless of whether the bound value is null.
     */
    @Query("""
            select e from Employee e
            where (cast(:status as string) is null or cast(e.status as string) = cast(:status as string))
              and (cast(:query as string) is null or lower(e.firstName) like lower(concat('%', cast(:query as string), '%'))
                                   or lower(e.lastName) like lower(concat('%', cast(:query as string), '%'))
                                   or lower(e.employeeNumber) like lower(concat('%', cast(:query as string), '%')))
            """)
    Page<Employee> searchAll(@Param("status") EmployeeStatus status, @Param("query") String query, Pageable pageable);

    @Query("""
            select e from Employee e
            where e.id in :allowedIds
              and (cast(:status as string) is null or cast(e.status as string) = cast(:status as string))
              and (cast(:query as string) is null or lower(e.firstName) like lower(concat('%', cast(:query as string), '%'))
                                   or lower(e.lastName) like lower(concat('%', cast(:query as string), '%'))
                                   or lower(e.employeeNumber) like lower(concat('%', cast(:query as string), '%')))
            """)
    Page<Employee> searchWithinScope(@Param("allowedIds") Collection<UUID> allowedIds,
                                      @Param("status") EmployeeStatus status,
                                      @Param("query") String query,
                                      Pageable pageable);
}
