package dev.kunal.kairo.api.dto.request;

import java.util.List;

import dev.kunal.kairo.api.dto.common.TaskRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public record WorkflowRequest(
        @NotBlank String name,
        @Min(0) Integer maxRetries,
        @Min(1) Integer taskTimeoutSeconds,
        @NotEmpty @Valid List<TaskRequest> tasks) {
}
