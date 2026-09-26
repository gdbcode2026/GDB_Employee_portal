package com.growdigitalbridge.document.repository;

import com.growdigitalbridge.document.domain.Document;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    /**
     * Natural idempotency guard for {@code POST /documents/workload-uploads}: the same owner
     * re-submitting a file with the exact same content checksum is treated as a duplicate
     * request, mirroring Attendance Service's own "natural idempotency guard" precedent rather
     * than inventing a client-supplied idempotency-key header nothing else in this platform uses.
     */
    @Query("select d from Document d, DocumentVersion v where v.documentId = d.id and d.ownerRef = :ownerRef and v.checksum = :checksum")
    Optional<Document> findByOwnerRefAndChecksum(@Param("ownerRef") UUID ownerRef, @Param("checksum") String checksum);
}
