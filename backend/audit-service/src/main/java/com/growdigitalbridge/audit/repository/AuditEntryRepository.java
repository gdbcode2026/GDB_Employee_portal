package com.growdigitalbridge.audit.repository;

import com.growdigitalbridge.audit.domain.AuditEntry;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, UUID> { }
