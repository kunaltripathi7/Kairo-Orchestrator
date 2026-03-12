package dev.kunal.kairo.api.service;

import java.util.UUID;

import dev.kunal.kairo.api.dto.request.TaskUpdateRequest;
import dev.kunal.kairo.api.dto.response.TaskResponse;

public interface TaskService {
    TaskResponse updateTaskStatus(UUID taskId, TaskUpdateRequest request);
}
