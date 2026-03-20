package dev.kunal.kairo.api.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import dev.kunal.kairo.common.enums.TaskStatus;

public record TaskResponse(
                UUID id,
                int sequenceNumber,
                String handlerName,
                TaskStatus status,
                int attemptCount,
                JsonNode payload,
                JsonNode result,
                Instant createdAt,
                Instant updatedAt) {
}
