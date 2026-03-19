package dev.kunal.kairo.common.dto;

import java.util.UUID;

import lombok.Builder;

@Builder
public record WorkflowEvent(
        UUID workflowId,
        String status,
        String eventType
) {}
