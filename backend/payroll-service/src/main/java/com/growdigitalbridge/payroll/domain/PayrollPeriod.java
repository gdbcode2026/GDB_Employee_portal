package com.growdigitalbridge.payroll.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One calendar month (decision 3: monthly payroll periods only). {@code startDate}/{@code
 * endDate} are always derived from {@code year}/{@code month} at creation - never accepted as
 * free input - so it is structurally impossible to create a non-monthly period.
 */
@Entity
@Table(name = "payroll_periods")
public class PayrollPeriod {

    @Id
    private UUID id;

    @Column(nullable = false)
    private int year;

    @Column(nullable = false)
    private int month;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "cut_off_date")
    private LocalDate cutOffDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PayrollPeriodStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(length = 128, updatable = false)
    private String createdBy;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(length = 128)
    private String updatedBy;

    @Version
    private long version;

    protected PayrollPeriod() { }

    public PayrollPeriod(UUID id, int year, int month, LocalDate startDate, LocalDate endDate,
                          LocalDate cutOffDate, String actor, Instant now) {
        this.id = id;
        this.year = year;
        this.month = month;
        this.startDate = startDate;
        this.endDate = endDate;
        this.cutOffDate = cutOffDate;
        this.status = PayrollPeriodStatus.OPEN;
        this.createdAt = now;
        this.createdBy = actor;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public UUID getId() { return id; }
    public int getYear() { return year; }
    public int getMonth() { return month; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public LocalDate getCutOffDate() { return cutOffDate; }
    public PayrollPeriodStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
