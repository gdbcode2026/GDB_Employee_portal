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

    /**
     * Reporting V1 authorization review Part A verification
     * (docs/REPORTING_AUTHORIZATION_REVIEW.md) found that the original {@code (:param is null or
     * ...)} pattern leaves PostgreSQL's extended query protocol unable to determine a type for
     * the standalone {@code is null} occurrence, raising "could not determine data type of
     * parameter" for real callers that omit a filter - including Expense Summary's own call
     * pattern (date range set, {@code status} omitted). An explicit {@code cast(:param as
     * type) is null} rewrite fixed this for {@code searchAll} directly, but - confirmed by
     * reading the exact generated SQL and reproducing against real Postgres - the identical cast
     * pattern combined with an {@code employeeRef} equality/{@code IN} clause in the same
     * statement (as {@code searchForEmployee}/{@code searchWithinScope} both have) instead
     * raised a *different* error, "cannot cast type bytea to date", for the same previously-null
     * parameters. Both symptoms are PostgreSQL/Hibernate parameter-type-inference failures on a
     * parameter used *only* in a standalone {@code is null} check with no other type-anchoring
     * context - the fix here removes that standalone usage entirely by replacing it with {@code
     * coalesce(:param, <always-true sentinel>)}, so every parameter occurrence is always paired
     * with a concretely-typed value and Postgres never needs to resolve an unanchored type. Status
     * is given the same treatment via {@code coalesce(:status, c.status)} - a self-referential
     * fallback so an absent status filter compares {@code c.status} to itself (always true for
     * every row) rather than ever checking {@code :status is null} directly. None of this changes
     * filtering semantics: no filter still means no filtering, in every one of these methods.
     */
    @Query("""
            select c from ExpenseClaim c
            where c.employeeRef = :employeeRef
              and cast(c.status as string) = cast(coalesce(:status, c.status) as string)
              and cast(c.createdAt as date) >= coalesce(:from, cast('0001-01-01' as date))
              and cast(c.createdAt as date) <= coalesce(:to, cast('9999-12-31' as date))
            """)
    Page<ExpenseClaim> searchForEmployee(@Param("employeeRef") UUID employeeRef, @Param("status") ExpenseClaimStatus status,
                                          @Param("from") LocalDate from, @Param("to") LocalDate to, Pageable pageable);

    @Query("""
            select c from ExpenseClaim c
            where c.employeeRef in :allowedIds
              and cast(c.status as string) = cast(coalesce(:status, c.status) as string)
              and cast(c.createdAt as date) >= coalesce(:from, cast('0001-01-01' as date))
              and cast(c.createdAt as date) <= coalesce(:to, cast('9999-12-31' as date))
            """)
    Page<ExpenseClaim> searchWithinScope(@Param("allowedIds") Collection<UUID> allowedIds, @Param("status") ExpenseClaimStatus status,
                                          @Param("from") LocalDate from, @Param("to") LocalDate to, Pageable pageable);

    @Query("""
            select c from ExpenseClaim c
            where cast(c.status as string) = cast(coalesce(:status, c.status) as string)
              and cast(c.createdAt as date) >= coalesce(:from, cast('0001-01-01' as date))
              and cast(c.createdAt as date) <= coalesce(:to, cast('9999-12-31' as date))
            """)
    Page<ExpenseClaim> searchAll(@Param("status") ExpenseClaimStatus status, @Param("from") LocalDate from,
                                  @Param("to") LocalDate to, Pageable pageable);
}
