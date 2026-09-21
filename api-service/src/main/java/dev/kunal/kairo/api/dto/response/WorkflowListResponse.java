package dev.kunal.kairo.api.dto.response;

import java.time.Instant;
import java.util.UUID;

import dev.kunal.kairo.common.enums.WorkflowStatus;

public record WorkflowListResponse(
        UUID id,
        String name,
        WorkflowStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
