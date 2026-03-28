package dev.kunal.kairo.scheduler.consumer;

import java.util.UUID;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.kunal.kairo.scheduler.service.TaskSchedulingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskResultConsumer {

    private final TaskSchedulingService taskSchedulingService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "task-results", groupId = "scheduler-group")
    public void onTaskResult(String message) {
        try {
            JsonNode result = objectMapper.readTree(message);
            UUID taskId = UUID.fromString(result.get("taskId").asText());
            String status = result.get("status").asText();

            log.info("Received task result: taskId={}, status={}", taskId, status);

            switch (status) {
                case "COMPLETED" -> taskSchedulingService.onTaskCompleted(taskId);
                case "FAILED" -> taskSchedulingService.onTaskFailed(taskId);
                default -> log.warn("Unknown task result status: {}", status);
            }
        } catch (Exception e) {
            log.error("Failed to process task result: {}", message, e);
        }
    }
}
