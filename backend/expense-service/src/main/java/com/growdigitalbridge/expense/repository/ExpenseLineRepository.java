package com.growdigitalbridge.expense.repository;

import com.growdigitalbridge.expense.domain.ExpenseLine;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseLineRepository extends JpaRepository<ExpenseLine, UUID> {

    List<ExpenseLine> findByClaimId(UUID claimId);

    void deleteByClaimId(UUID claimId);
}
