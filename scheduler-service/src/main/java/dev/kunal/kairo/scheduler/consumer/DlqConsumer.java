package dev.kunal.kairo.scheduler.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.kunal.kairo.common.dto.TaskMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DlqConsumer {

    private final ObjectMapper objectMapper;
    private final Counter dlqCounter;

    public DlqConsumer(ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.dlqCounter = Counter.builder("kairo_dlq_tasks_total")
                .description("Total number of tasks sent to the Dead Letter Queue")
                .register(meterRegistry);
    }

    @KafkaListener(topics = "dead-letter-queue", groupId = "scheduler-dlq-group")
    public void consume(String message) {
        try {
            TaskMessage taskMessage = objectMapper.readValue(message, TaskMessage.class);
            dlqCounter.increment();

            log.error("[DLQ] Task permanently failed | taskId={} | workflowId={} | handler={} | payload={}",
                    taskMessage.taskId(),
                    taskMessage.workflowId(),
                    taskMessage.handlerName(),
                    taskMessage.payload());
        } catch (Exception e) {
            dlqCounter.increment();
            log.error("[DLQ] Failed to deserialize DLQ message: {}", message, e);
        }
    }
}
