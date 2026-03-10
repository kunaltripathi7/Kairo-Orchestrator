package dev.kunal.kairo.api.dto.common;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

public record TaskRequest(
        @NotBlank String handler,
        JsonNode payload) {
}
