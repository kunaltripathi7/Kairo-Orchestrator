package dev.kunal.kairo.api.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.kunal.kairo.api.dto.response.TaskResponse;
import dev.kunal.kairo.api.dto.response.WorkflowResponse;
import dev.kunal.kairo.common.entity.Task;
import dev.kunal.kairo.common.entity.Workflow;

@Component
public class WorkflowMapper {

    public WorkflowResponse toResponse(Workflow workflow, List<Task> tasks) {
        List<TaskResponse> taskResponses = tasks.stream()
                .map(this::toTaskResponse)
                .toList();

        return new WorkflowResponse(
                workflow.getId(),
                workflow.getName(),
                workflow.getStatus(),
                workflow.getMaxRetries(),
                workflow.getTaskTimeoutSeconds(),
                taskResponses,
                workflow.getCreatedAt(),
                workflow.getUpdatedAt());
    }

    public TaskResponse toTaskResponse(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getSequenceNumber(),
                task.getHandlerName(),
                task.getStatus(),
                task.getAttemptCount(),
                task.getPayload(),
                task.getResult(),
                task.getCreatedAt(),
                task.getUpdatedAt());
    }
}
