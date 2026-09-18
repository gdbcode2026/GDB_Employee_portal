package com.growdigitalbridge.leave.api.dto;

import java.util.UUID;

public final class LeaveTypeDtos {

    private LeaveTypeDtos() { }

    public record Response(UUID id, String code, String name) { }
}
