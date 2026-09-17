package com.growdigitalbridge.employee.api.dto;

import com.growdigitalbridge.employee.domain.EmployeeStatus;
import com.growdigitalbridge.employee.domain.EmploymentStatus;
import com.growdigitalbridge.employee.domain.EmploymentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class EmployeeDtos {

    private EmployeeDtos() { }

    public record EmploymentDetails(
            @NotBlank @Size(max = 160) String jobTitle,
            @NotNull EmploymentType employmentType,
            @NotNull LocalDate startDate) { }

    public record EmploymentUpdate(
            @NotBlank @Size(max = 160) String jobTitle,
            @NotNull EmploymentType employmentType) { }

    public record EmploymentSummary(UUID id, String jobTitle, EmploymentType employmentType,
                                     LocalDate startDate, LocalDate endDate, EmploymentStatus status) { }

    public record EmergencyContactRequest(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(max = 32) String phone,
            @NotBlank @Size(max = 64) String relationship) { }

    public record EmergencyContactResponse(UUID id, String name, String phone, String relationship) { }

    public record CreateRequest(
            @NotBlank @Size(max = 64) String employeeNumber,
            @NotBlank @Size(max = 120) String firstName,
            @NotBlank @Size(max = 120) String lastName,
            @NotBlank @Email @Size(max = 255) String email,
            @Size(max = 32) String phone,
            @Size(max = 255) String identitySubject,
            @NotNull @Valid EmploymentDetails employment,
            List<@Valid EmergencyContactRequest> emergencyContacts) { }

    /** Self-service can only ever touch contact-detail fields - never identity, employment, or status. */
    public record SelfUpdateRequest(
            @Size(max = 32) String phone,
            List<@Valid EmergencyContactRequest> emergencyContacts) { }

    public record AdminUpdateRequest(
            @Size(max = 120) String firstName,
            @Size(max = 120) String lastName,
            @Email @Size(max = 255) String email,
            @Size(max = 32) String phone,
            EmployeeStatus status,
            @Valid EmploymentUpdate employment,
            List<@Valid EmergencyContactRequest> emergencyContacts) { }

    public record Response(UUID id, String employeeNumber, String firstName, String lastName, String email,
                            String phone, EmployeeStatus status, EmploymentSummary employment,
                            List<EmergencyContactResponse> emergencyContacts, Instant createdAt, Instant updatedAt) { }

    /** Lighter shape for collection results - avoids an employment/contacts fetch per row. */
    public record Summary(UUID id, String employeeNumber, String firstName, String lastName,
                           String email, EmployeeStatus status) { }
}
