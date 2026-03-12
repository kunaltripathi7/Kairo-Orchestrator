package dev.kunal.kairo.api.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import dev.kunal.kairo.common.entity.TaskStatus;
import jakarta.validation.constraints.NotNull;

public record TaskUpdateRequest(
        @NotNull TaskStatus status,
        JsonNode result,
        String errorMessage
) {
}
