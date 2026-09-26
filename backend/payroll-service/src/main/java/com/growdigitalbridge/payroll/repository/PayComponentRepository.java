package com.growdigitalbridge.payroll.repository;

import com.growdigitalbridge.payroll.domain.PayComponent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayComponentRepository extends JpaRepository<PayComponent, UUID> { }
