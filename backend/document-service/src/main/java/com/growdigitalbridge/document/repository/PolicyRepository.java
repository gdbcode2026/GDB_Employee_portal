package com.growdigitalbridge.document.repository;

import com.growdigitalbridge.document.domain.Policy;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyRepository extends JpaRepository<Policy, UUID> { }
