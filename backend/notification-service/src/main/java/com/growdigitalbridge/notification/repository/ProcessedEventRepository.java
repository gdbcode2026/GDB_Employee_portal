package com.growdigitalbridge.notification.repository;

import com.growdigitalbridge.notification.domain.ProcessedEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> { }
