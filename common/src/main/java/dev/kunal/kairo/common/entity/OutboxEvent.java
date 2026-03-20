package dev.kunal.kairo.common.entity;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import dev.kunal.kairo.common.enums.AggregateType;
import dev.kunal.kairo.common.enums.EventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Builder
@AllArgsConstructor
@Table(name = "outbox_events")
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AggregateType aggregateType;

    @Column(nullable = false)
    private UUID aggregateId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private EventType type;

    @JdbcTypeCode(SqlTypes.JSON) // telling hibernate at runtime -> json
    @Column(columnDefinition = "jsonb", nullable = false) // creating the table make it a jsonB column
    private JsonNode payload;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
