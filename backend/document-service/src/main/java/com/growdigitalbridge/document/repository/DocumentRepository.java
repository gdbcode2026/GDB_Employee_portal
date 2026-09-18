package com.growdigitalbridge.document.repository;

import com.growdigitalbridge.document.domain.Document;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, UUID> { }
