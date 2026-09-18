package com.growdigitalbridge.attendance.repository;

import com.growdigitalbridge.attendance.domain.ProcessedEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> { }
