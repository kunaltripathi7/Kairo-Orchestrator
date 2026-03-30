package dev.kunal.kairo.common.dto;

import java.util.UUID;

import dev.kunal.kairo.common.enums.EventType;
import dev.kunal.kairo.common.enums.WorkflowStatus;

public record WorkflowEvent(
                UUID id,
                WorkflowStatus status,
                EventType type) {
}
