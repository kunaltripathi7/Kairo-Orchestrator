package dev.kunal.kairo.api.publisher;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dev.kunal.kairo.api.repository.OutboxEventRepository;
import dev.kunal.kairo.common.entity.OutboxEvent;
import dev.kunal.kairo.common.enums.KafkaTopic;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxEventRepository outboxEventRepository;
    @Qualifier("outboxExecutor")
    private final Executor outboxExecutorService;

    @Scheduled(fixedDelay = 1000)
    public void pollOutbox() {
        List<OutboxEvent> outboxEvents = outboxEventRepository.findTop100ByOrderByCreatedAtAsc();

        if (outboxEvents.isEmpty())
            return;

        List<CompletableFuture<UUID>> futures = outboxEvents.stream()
                .map(event -> CompletableFuture.supplyAsync(() -> {
                    String topic = KafkaTopic.WORKFLOW_EVENTS.getTopicName();
                    String key = event.getAggregateId().toString();
                    String value = event.getPayload().toString();
                    
                    ProducerRecord<String, String> record = 
                            new ProducerRecord<>(topic, key, value);                   
                    try {
                        kafkaTemplate.send(record).get();
                        return event.getId();
                    } catch (Exception e) {
                        log.error("Failed to publish outbox event: {}", event.getId(), e);
                        return null;
                    }
                }, outboxExecutorService))
                .collect(Collectors.toList());

        // accepts varargs- array under the hood
        List<UUID> successfulIds = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join) // returns UUIDs
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList()))
                .join();

        if (!successfulIds.isEmpty()) {
            outboxEventRepository.deleteAllByIdInBatch(successfulIds);
            log.info("Published and deleted {} outbox events", successfulIds.size());
        }
    }
}
