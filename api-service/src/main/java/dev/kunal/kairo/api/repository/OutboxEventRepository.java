package dev.kunal.kairo.api.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import dev.kunal.kairo.common.entity.OutboxEvent;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query(value = "SELECT * FROM outbox_events ORDER BY created_at ASC LIMIT 100 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEvent> findTop100ByOrderByCreatedAtAsc();
}
