package com.growdigitalbridge.asset.api.dto;

import com.growdigitalbridge.asset.domain.AssetStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class AssetDtos {

    private AssetDtos() { }

    public record CreateRequest(@NotBlank @Size(max = 64) String tag, @NotBlank @Size(max = 120) String type,
                                 @Size(max = 120) String serial) { }

    public record Response(UUID id, String tag, String type, String serial, AssetStatus status,
                            Instant createdAt, Instant updatedAt) { }
}
