package com.growdigitalbridge.expense.repository;

import com.growdigitalbridge.expense.domain.ReceiptReference;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReceiptReferenceRepository extends JpaRepository<ReceiptReference, UUID> {

    List<ReceiptReference> findByClaimId(UUID claimId);

    void deleteByClaimId(UUID claimId);
}
