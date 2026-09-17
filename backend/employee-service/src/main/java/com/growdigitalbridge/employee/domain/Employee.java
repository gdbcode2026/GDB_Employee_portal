package com.growdigitalbridge.employee.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Employee identity, profile, and lifecycle status. Deliberately holds no team hierarchy,
 * credentials, or payroll data - reporting/team structure is Organization's responsibility
 * and is never duplicated here.
 */
@Entity
@Table(name = "employees")
public class Employee {

    @Id
    private UUID id;

    @Column(name = "employee_number", nullable = false, length = 64)
    private String employeeNumber;

    @Column(name = "first_name", nullable = false, length = 120)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 120)
    private String lastName;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(length = 32)
    private String phone;

    @Column(name = "identity_subject", length = 255)
    private String identitySubject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EmployeeStatus status;

    @Column(name = "deactivated_at")
    private Instant deactivatedAt;

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

    protected Employee() { }

    public Employee(UUID id, String employeeNumber, String firstName, String lastName, String email,
                     String phone, String identitySubject, String actor, Instant now) {
        this.id = id;
        this.employeeNumber = employeeNumber;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.identitySubject = identitySubject;
        this.status = EmployeeStatus.ACTIVE;
        this.createdAt = now;
        this.createdBy = actor;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public void updateProfile(String firstName, String lastName, String email, String phone, String actor, Instant now) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        touch(actor, now);
    }

    public void updatePhone(String phone, String actor, Instant now) {
        this.phone = phone;
        touch(actor, now);
    }

    public void deactivate(String actor, Instant now) {
        this.status = EmployeeStatus.INACTIVE;
        this.deactivatedAt = now;
        touch(actor, now);
    }

    private void touch(String actor, Instant now) {
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getEmployeeNumber() { return employeeNumber; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getIdentitySubject() { return identitySubject; }
    public EmployeeStatus getStatus() { return status; }
    public Instant getDeactivatedAt() { return deactivatedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
