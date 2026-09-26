package com.growdigitalbridge.payroll.repository;

import com.growdigitalbridge.payroll.domain.PayrollPeriod;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollPeriodRepository extends JpaRepository<PayrollPeriod, UUID> {

    Optional<PayrollPeriod> findByYearAndMonth(int year, int month);

    List<PayrollPeriod> findAllByOrderByYearDescMonthDesc();
}
