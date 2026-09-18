package com.growdigitalbridge.document.repository;

import com.growdigitalbridge.document.domain.DocumentVersion;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, UUID> {

    Optional<DocumentVersion> findFirstByDocumentIdOrderByVersionNumberDesc(UUID documentId);
}
