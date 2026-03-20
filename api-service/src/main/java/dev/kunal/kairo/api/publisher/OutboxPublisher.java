package dev.kunal.kairo.api.publisher;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dev.kunal.kairo.api.repository.OutboxEventRepository;
import dev.kunal.kairo.common.entity.OutboxEvent;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Component
@EnableScheduling
@RequiredArgsConstructor
public class OutboxPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxEventRepository outboxEventRepository;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void pollOutbox() {
        List<OutboxEvent> outboxEvents = outboxEventRepository.findTop100ByOrderByCreatedAtAsc();
        List<UUID> eventIds = new ArrayList<>();
        for (OutboxEvent event : outboxEvents) {
            String topic = "workflow-events";
            String key = event.getAggregateId().toString();
            String value = event.getPayload().toString();

            try {
                // .get() makes the call synchronous. If Kafka is down, it throws an exception
                // and we don't delete the record from the DB, so it will be retried later.
                kafkaTemplate.send(topic, key, value).get();
                eventIds.add(event.getId());

            } catch (Exception e) {
                // Log and break to wait for the next polling cycle
                System.err.println("Failed to send message to Kafka: " + e.getMessage());
                break;
            }
        }
        outboxEventRepository.deleteAllByIdInBatch(eventIds);
    }
}
