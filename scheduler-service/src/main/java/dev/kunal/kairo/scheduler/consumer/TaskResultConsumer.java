package dev.kunal.kairo.scheduler.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.kunal.kairo.common.dto.TaskResultEvent;
import dev.kunal.kairo.common.enums.KafkaTopic;
import dev.kunal.kairo.scheduler.service.TaskSchedulingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskResultConsumer {

    private final TaskSchedulingService taskSchedulingService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopic.Topics.TASK_RESULTS)
    public void onTaskResult(String message) {
        try {
            // JsonNode result = objectMapper.readTree(message); // navigate like a tree
            // (structure is unknown/dynamic)
            TaskResultEvent event = objectMapper.readValue(message, TaskResultEvent.class);
            log.info("Received task result: taskId={}, status={}", event.taskId(), event.status());

            switch (event.status()) {
                case COMPLETED -> taskSchedulingService.onTaskCompleted(event.taskId());
                case FAILED -> taskSchedulingService.onTaskFailed(event.taskId());
                default -> log.warn("Unexpected task result status: {}", event.status());
            }
        } catch (Exception e) {
            log.error("Failed to process task result: {}", message, e);
        }
    }
}
