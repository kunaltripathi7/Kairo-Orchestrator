package dev.kunal.kairo.scheduler.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.kunal.kairo.common.dto.WorkflowEvent;
import dev.kunal.kairo.common.enums.KafkaTopic;
import dev.kunal.kairo.scheduler.service.TaskSchedulingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowEventConsumer {

    private final TaskSchedulingService taskSchedulingService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopic.Topics.WORKFLOW_EVENTS)
    public void onWorkflowEvent(String message) {
        try {
            WorkflowEvent event = objectMapper.readValue(message, WorkflowEvent.class);
            log.info("Received workflow event: type={}, workflowId={}", event.type(), event.id());

            switch (event.type()) {
                case WORKFLOW_CREATED -> taskSchedulingService.scheduleFirstTask(event.id());
                default -> log.warn("Unhandled event type: {}", event.type());
            }
        } catch (Exception e) {
            log.error("Failed to process workflow event: {}", message, e);
        }
    }
}
