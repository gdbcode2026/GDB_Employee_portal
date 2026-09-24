package com.growdigitalbridge.expense.repository;

import com.growdigitalbridge.expense.domain.ExpenseClaim;
import com.growdigitalbridge.expense.domain.ExpenseClaimStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExpenseClaimRepository extends JpaRepository<ExpenseClaim, UUID> {

    @Query("""
            select c from ExpenseClaim c
            where c.employeeRef = :employeeRef
              and (:status is null or c.status = :status)
              and (:from is null or cast(c.createdAt as date) >= :from)
              and (:to is null or cast(c.createdAt as date) <= :to)
            """)
    Page<ExpenseClaim> searchForEmployee(@Param("employeeRef") UUID employeeRef, @Param("status") ExpenseClaimStatus status,
                                          @Param("from") LocalDate from, @Param("to") LocalDate to, Pageable pageable);

    @Query("""
            select c from ExpenseClaim c
            where c.employeeRef in :allowedIds
              and (:status is null or c.status = :status)
              and (:from is null or cast(c.createdAt as date) >= :from)
              and (:to is null or cast(c.createdAt as date) <= :to)
            """)
    Page<ExpenseClaim> searchWithinScope(@Param("allowedIds") Collection<UUID> allowedIds, @Param("status") ExpenseClaimStatus status,
                                          @Param("from") LocalDate from, @Param("to") LocalDate to, Pageable pageable);

    @Query("""
            select c from ExpenseClaim c
            where (:status is null or c.status = :status)
              and (:from is null or cast(c.createdAt as date) >= :from)
              and (:to is null or cast(c.createdAt as date) <= :to)
            """)
    Page<ExpenseClaim> searchAll(@Param("status") ExpenseClaimStatus status, @Param("from") LocalDate from,
                                  @Param("to") LocalDate to, Pageable pageable);
}
