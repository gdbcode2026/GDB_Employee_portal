package com.growdigitalbridge.payroll.repository;

import com.growdigitalbridge.payroll.domain.ProcessedEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> { }
