package dev.kunal.kairo.common.dto;

import java.util.UUID;

public record TaskMessage(UUID taskId, UUID workflowId, String handlerName, String payload) {
}
