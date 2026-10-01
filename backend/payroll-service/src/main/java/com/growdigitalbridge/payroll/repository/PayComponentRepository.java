package com.growdigitalbridge.payroll.repository;

import com.growdigitalbridge.payroll.domain.PayComponent;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayComponentRepository extends JpaRepository<PayComponent, UUID> {

    boolean existsByCode(String code);

    Optional<PayComponent> findByCode(String code);

    Page<PayComponent> findAllByOrderByCodeAsc(Pageable pageable);
}
