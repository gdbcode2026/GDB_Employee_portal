package com.growdigitalbridge.payroll.api.dto;

import com.growdigitalbridge.payroll.domain.CompensationComponentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * Structural catalogue-management DTOs only (item 2): {@code code}/{@code name}/{@code type}/
 * {@code active}. No rate, amount, or threshold field exists anywhere here - GDB's actual
 * catalogue content remains PENDING_GDB_APPROVAL (Section X).
 */
public final class PayComponentDtos {

    private PayComponentDtos() { }

    public record CreateRequest(@NotBlank @Size(max = 64) String code, @NotBlank @Size(max = 120) String name,
                                 @NotNull CompensationComponentType type) { }

    /** {@code code}/{@code type} are never updatable once created - only display name and active status. */
    public record UpdateRequest(@NotBlank @Size(max = 120) String name, @NotNull Boolean active) { }

    public record Response(UUID id, String code, String name, CompensationComponentType type, boolean active,
                            Instant createdAt, Instant updatedAt) { }
}
