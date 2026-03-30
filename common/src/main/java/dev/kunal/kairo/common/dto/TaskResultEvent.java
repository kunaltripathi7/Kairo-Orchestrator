package dev.kunal.kairo.common.dto;

import java.util.UUID;

import dev.kunal.kairo.common.enums.TaskStatus;

public record TaskResultEvent(UUID taskId,
        TaskStatus status,
        String message) {

}
