package dev.kunal.kairo.api.publisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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
        List<CompletableFuture<UUID>> futures = outboxEvents.stream()
                .map(event -> CompletableFuture.supplyAsync(() -> {
                    String topic = "workflow-events";
                    String key = event.getAggregateId().toString();
                    String value = event.getPayload().toString();
                    try {
                        kafkaTemplate.send(topic, key, value).get();
                        return event.getId();
                    } catch (Exception e) {
                        log.error("Failed to send event: {}", event.getId(), e);
                        return null;
                    }
                }))
                .collect(Collectors.toList());

        List<UUID> successfulIds = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList()))
                .join();

        outboxEventRepository.deleteAllByIdInBatch(successfulIds);
        // List<UUID> eventIds = new ArrayList<>();
        // List<CompletableFuture<>>
        // for (OutboxEvent event : outboxEvents) {
        // String topic = "workflow-events";
        // String key = event.getAggregateId().toString();
        // String value = event.getPayload().toString();

        // try {
        // // .get() makes the call synchronous. If Kafka is down, it throws an
        // exception
        // // and we don't delete the record from the DB, so it will be retried later.
        // kafkaTemplate.send(topic, key, value).get();
        // eventIds.add(event.getId());

        // } catch (Exception e) {
        // // Log and break to wait for the next polling cycle

        // break;
        // }
        // }
        outboxEventRepository.deleteAllByIdInBatch(eventIds);
    }
}
