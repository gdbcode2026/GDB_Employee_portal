package com.growdigitalbridge.performance.repository;

import com.growdigitalbridge.performance.domain.ReviewCycle;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewCycleRepository extends JpaRepository<ReviewCycle, UUID> { }
