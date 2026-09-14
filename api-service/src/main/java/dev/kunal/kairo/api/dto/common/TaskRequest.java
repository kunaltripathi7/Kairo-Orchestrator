package dev.kunal.kairo.api.dto.common;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

public record TaskRequest(
        @NotBlank String name,
        @NotBlank String handler,
        List<String> dependsOn,
        JsonNode payload) {
}
