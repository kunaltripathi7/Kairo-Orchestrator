package dev.kunal.kairo.api.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.kunal.kairo.common.entity.WorkflowStatus;

public record WorkflowResponse(
        UUID id,
        String name,
        WorkflowStatus status,
        Integer maxRetries,
        Integer taskTimeoutSeconds,
        List<TaskResponse> tasks,
        Instant createdAt,
        Instant updatedAt) {
}
